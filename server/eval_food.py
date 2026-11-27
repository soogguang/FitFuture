#!/usr/bin/env python3
"""음식 인식 실험: 실행 중인 음식 인식 서버(/food/recognize)에 사진·음식명을 보내 자동 채점한다.

    서버를 먼저 켠다 (Gemini 키는 서버 터미널 환경변수에만):
        python server\\food_server.py --port 8765
    다른 터미널에서 (프로젝트 루트):
        python server\\eval_food.py --photos 사진폴더
        python server\\eval_food.py --text-only          # 사진 없이 음식명 10개만

사진 이름 = 정답 음식명.  예) 비빔밥.jpg, 김밥_2.jpg(같은 음식 두 번째), 돈가스 정식.png
비음식 사진은 이름을 '비음식'으로 시작 (예: 비음식_책상.jpg) → 인식 실패(422)가 나와야 정답.

채점
  성공      HTTP 200 (비음식은 422)
  정답 포함  인식된 식사 이름 또는 구성 음식 중 정답과 같은 음식이 있음 (식약처 DB 이름·동의어 기준)
  DB 매칭   인식된 구성 음식이 모두 앱의 식약처 음식 DB에 있음 → 수정 없이 영양 계산 가능
  중량      모든 추정 g이 0 초과 5000 이하
결과: server/eval-results/ 아래 CSV와 요약 Markdown (Git에 올리지 않음). Python 표준 라이브러리만 사용.
"""
import argparse
import base64
import csv
import json
import re
import statistics
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NUTRITION = ROOT / "app/src/main/assets/nutrition"
TEXT_CASES = ["비빔밥", "김밥", "떡볶이", "라면", "짜장면", "김치찌개", "돼지국밥", "돈가스", "계란덮밥", "카레라이스"]
MIME = {".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png", ".webp": "image/webp"}


def key(name):
    return re.sub(r"[\s_]", "", name).lower()


def load_db():
    names = {}
    for line in (NUTRITION / "mfds-foods.tsv").read_text(encoding="utf-8").splitlines():
        v = line.split("\t")
        names.setdefault(key(v[1]), v[1])
    aliases = {}
    for line in (NUTRITION / "aliases.tsv").read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.startswith("#"):
            a, b = line.split("\t")
            aliases[key(a)] = b.strip()

    def resolve(name):
        k = key(name)
        return names.get(key(aliases.get(k, k)))
    return resolve


def post(url, body, timeout):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    start = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, json.load(r), time.time() - start
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.load(e), time.time() - start
        except ValueError:
            return e.code, {}, time.time() - start
    except (urllib.error.URLError, TimeoutError) as e:
        return 0, {"error": {"code": "CONNECTION", "message": str(e)}}, time.time() - start


def expected_name(path):
    stem = path.stem
    return re.sub(r"_\d+$", "", stem).strip()


def main():
    ap = argparse.ArgumentParser(description="FitFuture 음식 인식 실험")
    ap.add_argument("--url", default="http://127.0.0.1:8765")
    ap.add_argument("--photos", help="정답 음식명으로 이름 붙인 사진 폴더")
    ap.add_argument("--text-only", action="store_true", help="사진 없이 음식명 10개만")
    ap.add_argument("--no-text", action="store_true", help="음식명 실험은 건너뜀")
    ap.add_argument("--delay", type=float, default=5)
    ap.add_argument("--timeout", type=float, default=60)
    ap.add_argument("--stop-after", type=int, default=3, help="한도 초과가 이 횟수만큼 연속이면 중단")
    args = ap.parse_args()

    resolve = load_db()
    try:
        with urllib.request.urlopen(args.url + "/health", timeout=5) as r:
            json.load(r)
    except Exception as e:  # noqa: BLE001
        sys.exit(f"음식 인식 서버에 연결하지 못했어요 ({args.url}). 서버를 먼저 켜 주세요. {e}")

    cases = []  # (구분, 정답, 요청 body, 비음식 여부)
    if not args.no_text:
        cases += [("음식명", n, {"text": n}, False) for n in TEXT_CASES]
    if args.photos and not args.text_only:
        folder = Path(args.photos)
        files = sorted(p for p in folder.iterdir() if p.suffix.lower() in MIME)
        if not files:
            sys.exit(f"사진이 없어요: {folder}")
        for p in files:
            raw = p.read_bytes()
            if len(raw) > 4 * 1024 * 1024:
                print(f"건너뜀 (4MB 초과): {p.name}")
                continue
            exp = expected_name(p)
            body = {"text": "", "image": {"mimeType": MIME[p.suffix.lower()],
                                          "data": base64.b64encode(raw).decode("ascii")}}
            cases.append(("사진", exp if not exp.startswith("비음식") else "비음식", body, exp.startswith("비음식")))
            cases[-1] = cases[-1] + (p.name,)
    for i, c in enumerate(cases):
        if len(c) == 4:
            cases[i] = c + (c[1],)
    for kind, exp, _, nonfood, _ in cases:
        if not nonfood and resolve(exp) is None:
            print(f"참고: 정답 '{exp}'가 식약처 DB 이름이 아니에요. 정답 비교는 이름 글자로만 해요.")

    rows, streak, stopped = [], 0, False
    for n, (kind, exp, body, nonfood, label) in enumerate(cases, 1):
        status, resp, sec = post(args.url + "/food/recognize", body, args.timeout)
        code = "OK" if status == 200 else resp.get("error", {}).get("code", f"HTTP{status}")
        foods = resp.get("foods", []) if status == 200 else []
        name = resp.get("name", "") if status == 200 else ""
        if nonfood:
            success = status == 422
            correct = success
            db_ok = grams_ok = None
        else:
            success = status == 200
            want = resolve(exp)
            got = [resolve(f["name"]) for f in foods] + [resolve(name)]
            correct = success and ((want is not None and want in got)
                                   or key(exp) in key(name) or any(key(exp) in key(f["name"]) for f in foods))
            db_ok = success and all(resolve(f["name"]) is not None for f in foods)
            grams_ok = success and all(0 < f["grams"] <= 5000 for f in foods)
        rows.append(dict(kind=kind, input=label, expected=exp, status=status, result=code,
                         recognized=name, foods=" · ".join(f"{f['name']} {f['grams']:g}g" for f in foods),
                         success=success, correct=correct, db_match=db_ok, grams_ok=grams_ok,
                         seconds=round(sec, 1)))
        mark = "정답" if correct else ("오답" if success else code)
        print(f"[{n:>2}] {kind} {label:<16} → {mark:<6} {name} | {rows[-1]['foods']} ({sec:.1f}s)")
        streak = streak + 1 if code == "RATE_LIMIT" else 0
        if streak >= args.stop_after:
            stopped = True
            print(f"\n한도 초과가 {args.stop_after}번 연속이라 중단했어요.")
            break
        time.sleep(args.delay)

    out = ROOT / "server/eval-results"
    out.mkdir(exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    with open(out / f"food-{stamp}.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader(); w.writerows(rows)

    def section(kind):
        rs = [r for r in rows if r["kind"] == kind]
        if not rs:
            return []
        food = [r for r in rs if r["expected"] != "비음식"]
        non = [r for r in rs if r["expected"] == "비음식"]
        lines = [f"## {kind} 입력 ({len(rs)}건)", "", "| 항목 | 결과 |", "|---|---|",
                 f"| 인식 성공 | {sum(r['success'] for r in food)} / {len(food)} |",
                 f"| 정답 포함 | {sum(r['correct'] for r in food)} / {len(food)} |",
                 f"| 식약처 DB 바로 매칭 | {sum(bool(r['db_match']) for r in food)} / {len(food)} |",
                 f"| 추정 중량 범위 | {sum(bool(r['grams_ok']) for r in food)} / {len(food)} |"]
        if non:
            lines.append(f"| 비음식 → 인식 실패 처리 | {sum(r['correct'] for r in non)} / {len(non)} |")
        lines.append(f"| 응답 시간 | 평균 {statistics.mean(r['seconds'] for r in rs):.1f}초, "
                     f"최대 {max(r['seconds'] for r in rs):.1f}초 |")
        lines += ["", "| 입력 | 정답 | 인식 결과 | 판정 |", "|---|---|---|---|"]
        for r in rs:
            verdict = ("정답" if r["correct"] else "오답" if r["success"] else r["result"])
            if r["db_match"] is False and r["success"]:
                verdict += " · DB 수정 필요"
            lines.append(f"| {r['input']} | {r['expected']} | {r['recognized']} — {r['foods']} | {verdict} |")
        return lines + [""]

    summary = "\n".join([f"# 음식 인식 실험 결과 ({stamp})", "",
                         "- 중간 중단: 한도 초과" if stopped else "- 끝까지 실행", ""]
                        + section("음식명") + section("사진")) + "\n"
    (out / f"food-{stamp}.md").write_text(summary, encoding="utf-8")
    print("\n" + summary)
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
