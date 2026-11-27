#!/usr/bin/env python3
"""대안 추천 실험: 실행 중인 추천 서버(/recommend)에 시나리오를 보내고 결과를 자동 채점한다.

    서버를 먼저 켠다 (real 모드, Gemini 키는 서버 .env에만):
        .venv\\Scripts\\python scripts\\server.py --serve
    다른 터미널에서:
        .venv\\Scripts\\python scripts\\eval_recommend.py
        .venv\\Scripts\\python scripts\\eval_recommend.py --quick      # 시나리오 6개만 (호출 수 줄이기)

결과는 eval-results/ 아래 CSV 두 개와 요약 Markdown으로 저장된다 (Git에 올리지 않음).
Gemini를 실제로 호출하므로 API 한도를 사용한다. 키는 이 스크립트가 읽지도 보내지도 않는다.

채점 기준 (대안 1개마다)
  N  영양 재계산   앱과 같은 식약처 음식 DB로 직접 계산한 값과 일치 (±0.5kcal)
  K  열량 범위     300~1800kcal
  R  과도한 감량   원래 음식만 쓰는 대안은 원래 열량의 60% 이상
  B  예산          가격 ≤ 한 끼 예산
  A  알레르기      입력한 알레르기가 있으면 모든 음식에 NEIS 근거가 있고 해당 성분이 없음
  D  중복          다른 대안과 전체 중량 차이 5% 이상
  S  순위          점수 내림차순
  G  목표 방향     (품질 지표) 체중 감량 목표면 원래보다 열량이 낮음
요청 단위: 성공(대안 1~3개) / SHORTAGE(조건을 만족하는 대안 없음, 정상 응답) / 오류, 응답 시간
추가 실험: 선호 음식 유무에 따른 점수·순위 변화, 같은 요청 반복 시 결과 일관성
"""
import argparse
import csv
import json
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from fitfuture.allergens import conflicts  # noqa: E402  (판정 어휘만 공유, 근거 대조는 아래에서 직접)
from fitfuture.foodnames import NameIndex, load_aliases, load_mfds  # noqa: E402

MEALS = [
    ("돈가스 정식", [("돈가스", 150), ("밥", 200), ("양배추 샐러드", 50)]),
    ("비빔밥", [("비빔밥", 500)]),
    ("김치찌개 백반", [("김치찌개", 300), ("쌀밥", 210)]),
    ("짜장면", [("짜장면", 550)]),
    ("떡볶이와 김밥", [("떡볶이", 300), ("김밥", 230)]),
    ("라면과 김밥", [("라면", 500), ("김밥", 230)]),
    ("돼지국밥", [("돼지국밥", 600), ("쌀밥", 150)]),
    ("제육덮밥", [("제육덮밥", 450)]),
    ("카레라이스", [("카레라이스", 500)]),
    ("치킨", [("치킨", 350)]),
]

PROFILES = {
    "P1 감량·학식": dict(goal="체중 감량", gender="여성", age=21, height_cm=163, weight_kg=58,
                       target_weight_kg=55, activity="가벼운 활동", preferences=["일식"], avoided=[],
                       allergies=[], budget_won=8000, environment="학식"),
    "P2 증량·외식·유제품": dict(goal="근육 증가", gender="남성", age=23, height_cm=175, weight_kg=68,
                          target_weight_kg=72, activity="보통 활동", preferences=["한식"], avoided=[],
                          allergies=["유제품"], budget_won=12000, environment="외식"),
    "P3 유지·외식·갑각류": dict(goal="체중 유지", gender="여성", age=22, height_cm=160, weight_kg=55,
                          target_weight_kg=55, activity="가벼운 활동", preferences=[],
                          avoided=["기름진 음식"], allergies=["갑각류"], budget_won=7000, environment="외식"),
}

PREF_PAIRS = [(0, "돈가스"), (4, "떡볶이"), (6, "돼지국밥")]  # (식단 번호, 선호 음식)
REPEAT_MEALS = [0, 3]  # 같은 요청 3번


def load_index():
    return NameIndex(load_mfds(), load_aliases())


def load_allergen_labels(index):
    """서버와 같은 규칙: NEIS 표시 3건 이상인 음식의 성분 합집합 (식품 코드 → 성분 이름)."""
    data = json.loads((ROOT / "evidence/neis-allergens.json").read_text(encoding="utf-8"))
    numbers, out, count = data["numbers"], {}, {}
    for dish, entry in data["dishes"].items():
        row = index.lookup(dish)
        if row is None:
            continue
        out.setdefault(row[0], set()).update(numbers[n] for n in entry["allergens"])
        count[row[0]] = count.get(row[0], 0) + entry["count"]
    return {code: labels for code, labels in out.items() if count[code] >= 3}


def nutrition_of(index, foods):
    total = [0.0, 0.0, 0.0, 0.0]
    for name, grams in foods:
        row = index.lookup(name)
        if row is None:
            raise KeyError(name)
        for i in range(4):
            total[i] += row[3 + i] * grams / 100
    return total


def request_body(index, meal, profile):
    name, foods = meal
    n = nutrition_of(index, foods)
    return {
        "meal": {"name": name, "confirmed": True,
                 "foods": [{"name": f, "grams": g} for f, g in foods],
                 "nutrition": {"calories_kcal": round(n[0], 1), "carbohydrate_grams": round(n[1], 1),
                               "protein_grams": round(n[2], 1), "fat_grams": round(n[3], 1)}},
        "profile": profile, "feedback": []}


def post(url, body, timeout):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, json.load(resp), time.time() - start
    except urllib.error.HTTPError as e:
        try:
            payload = json.load(e)
        except ValueError:
            payload = {}
        return e.code, payload, time.time() - start
    except (urllib.error.URLError, TimeoutError) as e:
        return 0, {"error": {"code": "CONNECTION", "message": str(e)}}, time.time() - start


def grade(index, allergen_labels, body, resp):
    """대안별 채점 결과 목록."""
    profile, meal = body["profile"], body["meal"]
    original = {index.lookup(f["name"])[0]: f["grams"] for f in meal["foods"]}
    orig_kcal = nutrition_of(index, [(f["name"], f["grams"]) for f in meal["foods"]])[0]
    alts, rows = resp["alternatives"], []
    portions = []
    for alt in alts:
        portions.append({index.lookup(f["name"])[0]: f["grams"] for f in alt["foods"]})
    for i, alt in enumerate(alts):
        foods = [(f["name"], f["grams"]) for f in alt["foods"]]
        mine = nutrition_of(index, foods)
        kcal = alt["nutrition"]["calories_kcal"]
        codes = portions[i]
        own_only = set(codes) <= set(original)
        allergy_ok = True
        if profile["allergies"]:
            for code in codes:
                labels = allergen_labels.get(code)
                if labels is None or conflicts(profile["allergies"], labels) is not False:
                    allergy_ok = False
        def dist(a, b):
            return sum(abs(a.get(k, 0) - b.get(k, 0)) for k in a.keys() | b.keys()) / max(sum(a.values()), sum(b.values()))
        dup_ok = all(dist(codes, other) >= 0.05 for j, other in enumerate(portions) if j != i)
        checks = {
            "N": abs(mine[0] - kcal) <= 0.5,
            "K": 300 <= kcal <= 1800,
            "R": (not own_only) or kcal >= orig_kcal * 0.6 - 1e-6,
            "B": alt["price_won"] <= profile["budget_won"],
            "A": allergy_ok,
            "D": dup_ok,
            "S": i == 0 or alts[i - 1]["score"] >= alt["score"],
        }
        goal_dir = None
        if profile["goal"] == "체중 감량":
            goal_dir = kcal < orig_kcal
        rows.append(dict(rank=i + 1, title=alt["title"], kcal=round(kcal, 1), orig_kcal=round(orig_kcal, 1),
                         my_kcal=round(mine[0], 1), price=alt["price_won"], score=alt["score"],
                         goal_dir=goal_dir, **checks))
    return rows


def main():
    ap = argparse.ArgumentParser(description="FitFuture 대안 추천 실험")
    ap.add_argument("--url", default="http://127.0.0.1:8000")
    ap.add_argument("--quick", action="store_true", help="식단 2개 × 프로필 3개만")
    ap.add_argument("--delay", type=float, default=2.0, help="요청 사이 대기(초), Gemini 한도 보호")
    ap.add_argument("--timeout", type=float, default=60)
    ap.add_argument("--stop-after", type=int, default=3,
                    help="Gemini 한도 초과(AI_RATE_LIMIT)가 이 횟수만큼 연속이면 중단 (한도 낭비 방지)")
    args = ap.parse_args()

    index = load_index()
    allergen_labels = load_allergen_labels(index)
    for _, foods in MEALS:
        for name, _ in foods:
            if index.lookup(name) is None:
                sys.exit(f"식약처 DB에 없는 음식 이름: {name}")
    try:
        with urllib.request.urlopen(args.url + "/health", timeout=5) as r:
            health = json.load(r)
    except Exception as e:  # noqa: BLE001
        sys.exit(f"추천 서버에 연결하지 못했어요 ({args.url}). 서버를 먼저 켜 주세요. {e}")
    print("서버 상태:", health)

    meals = MEALS[:2] if args.quick else MEALS
    out_dir = ROOT / "eval-results"
    out_dir.mkdir(exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    req_rows, alt_rows = [], []
    url = args.url + "/recommend"
    streak = {"n": 0}

    class Stop(Exception):
        pass

    def run(label, meal_i, pname, profile, tag="main"):
        body = request_body(index, MEALS[meal_i], profile)
        status, resp, sec = post(url, body, args.timeout)
        code = "OK" if status == 200 else resp.get("error", {}).get("code", f"HTTP{status}")
        rejected = resp.get("rejected") if status == 200 else resp.get("error", {}).get("rejected")
        row = dict(tag=tag, label=label, meal=MEALS[meal_i][0], profile=pname, status=status, result=code,
                   n_alts=len(resp.get("alternatives", [])) if status == 200 else 0,
                   seconds=round(sec, 1), rejected=json.dumps(rejected or {}, ensure_ascii=False),
                   source=resp.get("generation_source", ""))
        graded = grade(index, allergen_labels, body, resp) if status == 200 else []
        for g in graded:
            alt_rows.append(dict(tag=tag, label=label, meal=row["meal"], profile=pname, **g))
        req_rows.append(row)
        mark = f"{row['n_alts']}개" if status == 200 else code
        bad = [k for g in graded for k in "NKRBADS" if not g[k]]
        print(f"[{len(req_rows):>2}] {label:<28} {mark:<10} {sec:5.1f}s" + (f"  위반: {','.join(bad)}" if bad else ""))
        streak["n"] = streak["n"] + 1 if code == "AI_RATE_LIMIT" else 0
        if streak["n"] >= args.stop_after:
            raise Stop()
        time.sleep(args.delay)
        return status, resp

    stopped = False
    pref_rows, rep_rows = [], []
    try:
        experiments(run, meals, pref_rows, rep_rows, args)
    except Stop:
        stopped = True
        print(f"\nGemini 한도 초과가 {args.stop_after}번 연속이라 중단했어요. 한도가 풀린 뒤 --delay를 늘려 다시 실행해 주세요.")
    if not req_rows:
        sys.exit("저장할 결과가 없어요.")
    _save(req_rows, alt_rows, pref_rows, rep_rows, out_dir, stamp, health, meals, stopped)


def experiments(run, meals, pref_rows, rep_rows, args):
    # 1) 본 실험: 식단 × 프로필
    for mi in range(len(meals)):
        for pname, profile in PROFILES.items():
            run(f"{MEALS[mi][0]} / {pname}", mi, pname, profile)

    # 2) 선호 반영: 같은 식단·프로필에서 선호 음식만 바꿈
    if not args.quick:
        base = dict(PROFILES["P1 감량·학식"])
        for mi, food in PREF_PAIRS:
            results = {}
            for pref in ([], [food]):
                p = dict(base, preferences=pref)
                status, resp = run(f"{MEALS[mi][0]} / 선호 {pref or '없음'}", mi, "P1 선호 비교", p, tag="pref")
                results[bool(pref)] = resp.get("alternatives", []) if status == 200 else []
            def top(alts):
                return [(a["title"], a["scores"].get("P")) for a in alts]
            both = bool(results[False]) and bool(results[True])
            changed = ([a["title"] for a in results[False]] != [a["title"] for a in results[True]]) if both else None
            p_up = (statistics.mean(a["scores"]["P"] for a in results[True]) >
                    statistics.mean(a["scores"]["P"] for a in results[False])) if results[False] and results[True] else None
            pref_rows.append(dict(meal=MEALS[mi][0], preferred=food, rank_changed=changed, p_score_up=p_up,
                                  without=top(results[False]), with_pref=top(results[True])))

    # 3) 일관성: 같은 요청 3번
    if not args.quick:
        for mi in REPEAT_MEALS:
            runs = []
            for k in range(3):
                status, resp = run(f"{MEALS[mi][0]} / 반복 {k + 1}", mi, "P1 반복", PROFILES["P1 감량·학식"], tag="repeat")
                runs.append([a["title"] for a in resp.get("alternatives", [])] if status == 200 else None)
            ok = [r for r in runs if r]
            same_top1 = (len({r[0] for r in ok}) == 1) if len(ok) == 3 else None
            overlap = len(set.intersection(*[set(r) for r in ok])) if len(ok) == 3 else None
            rep_rows.append(dict(meal=MEALS[mi][0], runs=runs, same_top1=same_top1, common_titles=overlap))



def _save(req_rows, alt_rows, pref_rows, rep_rows, out_dir, stamp, health, meals, stopped):
    # 저장
    with open(out_dir / f"requests-{stamp}.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=list(req_rows[0].keys()))
        w.writeheader(); w.writerows(req_rows)
    if alt_rows:
        with open(out_dir / f"alternatives-{stamp}.csv", "w", newline="", encoding="utf-8-sig") as f:
            w = csv.DictWriter(f, fieldnames=list(alt_rows[0].keys()))
            w.writeheader(); w.writerows(alt_rows)

    main_req = [r for r in req_rows if r["tag"] == "main"]
    main_alt = [r for r in alt_rows if r["tag"] == "main"]
    ok = [r for r in main_req if r["result"] == "OK"]
    shortage = [r for r in main_req if r["result"] == "SHORTAGE"]
    errors = [r for r in main_req if r["result"] not in ("OK", "SHORTAGE")]
    names = {"N": "영양 재계산 일치", "K": "열량 300~1800kcal", "R": "원래 열량 60% 이상",
             "B": "예산 이내", "A": "알레르기 근거·미포함", "D": "중복 없음", "S": "점수 순 정렬"}
    lines = [f"# 대안 추천 실험 결과 ({stamp})", "",
             f"- 서버: {health}", f"- 후보 생성: {ok[0]['source'] if ok else '-'}",
             "- **중간 중단: Gemini 한도 초과** (아래 숫자는 보낸 요청까지만)" if stopped else "- 끝까지 실행", "",
             "## 요청 결과", "",
             "| 항목 | 결과 |", "|---|---|",
             f"| 요청 | {len(main_req)}건 (계획: 식단 {len(meals)} × 프로필 {len(PROFILES)}) |",
             f"| 성공 (대안 1~3개) | {len(ok)} / {len(main_req)} |",
             f"| 대안 3개 모두 | {sum(r['n_alts'] == 3 for r in ok)} / {len(main_req)} |",
             f"| SHORTAGE (조건 맞는 대안 없음) | {len(shortage)} / {len(main_req)} |",
             f"| 오류 | {len(errors)} / {len(main_req)} {sorted({r['result'] for r in errors}) or ''} |",
             (f"| 응답 시간 | 평균 {statistics.mean(r['seconds'] for r in main_req):.1f}초, "
              f"최대 {max(r['seconds'] for r in main_req):.1f}초 |" if main_req else "| 응답 시간 | - |"), "",
             "## 대안별 검증 (통과 / 전체)", "", "| 기준 | 통과 |", "|---|---|"]
    for k, label in names.items():
        lines.append(f"| {label} | {sum(r[k] for r in main_alt)} / {len(main_alt)} |")
    goal = [r for r in main_alt if r["goal_dir"] is not None]
    if goal:
        lines.append(f"| (품질) 감량 목표 → 열량 감소 | {sum(r['goal_dir'] for r in goal)} / {len(goal)} |")
    if pref_rows:
        lines += ["", "## 선호 반영 (같은 식단, 선호 음식만 추가)", "",
                  "| 식단 | 선호 음식 | 순위 변화 | 선호 점수(P) 상승 |", "|---|---|---|---|"]
        for r in pref_rows:
            yn = lambda v: "-" if v is None else ("예" if v else "아니오")
            lines.append(f"| {r['meal']} | {r['preferred']} | {yn(r['rank_changed'])} | "
                         f"{'-' if r['p_score_up'] is None else ('예' if r['p_score_up'] else '아니오')} |")
    if rep_rows:
        lines += ["", "## 일관성 (같은 요청 3번)", "", "| 식단 | 1순위 동일 | 3번 모두 나온 대안 수 |", "|---|---|---|"]
        for r in rep_rows:
            lines.append(f"| {r['meal']} | {'-' if r['same_top1'] is None else ('예' if r['same_top1'] else '아니오')} | "
                         f"{'-' if r['common_titles'] is None else r['common_titles']} |")
    violations = [r for r in main_alt if not all(r[k] for k in names)]
    if violations:
        lines += ["", "## 위반 사례", ""]
        for r in violations:
            lines.append(f"- {r['label']} {r['rank']}순위 {r['title']}: "
                         + ", ".join(names[k] for k in names if not r[k]))
    summary = "\n".join(lines) + "\n"
    (out_dir / f"summary-{stamp}.md").write_text(summary, encoding="utf-8")
    print("\n" + summary)
    print(f"저장: {out_dir}")


if __name__ == "__main__":
    main()
