"""Collect dish-level allergen labels from NEIS school lunch menus (교육부 급식식단정보).

School dietitians label each dish with the national allergen numbers, e.g.
"돈육버섯불고기 (5.6.10.13)". This script aggregates those labels per dish name; the
recommendation server later attaches them only to MFDS foods whose normalized name or alias
matches exactly (see foodnames.py). School recipes can differ from a restaurant's.

Usage:
  python -m fitfuture.import_neis --from 20260901 --to 20261031 [--schools-per-office 6]
NEIS_API_KEY is optional; without it NEIS returns 5 rows per request, so collection is slow.
"""
import argparse
import json
import os
import re
import time
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "evidence/neis-allergens.json"
API = "https://open.neis.go.kr/hub/"
OFFICES = ["B10", "C10", "D10", "E10", "F10", "G10", "H10", "I10", "J10",
           "K10", "M10", "N10", "P10", "Q10", "R10", "S10", "T10"]
# 학교급식 알레르기 유발식품 표시 번호 → allergens.py vocabulary.
NUMBERS = {1: "달걀", 2: "우유", 3: "메밀", 4: "땅콩", 5: "대두", 6: "밀", 7: "고등어", 8: "게",
           9: "새우", 10: "돼지고기", 11: "복숭아", 12: "토마토", 13: "아황산류", 14: "호두",
           15: "닭고기", 16: "쇠고기", 17: "오징어", 18: "조개류", 19: "잣"}
PAREN_LABEL = re.compile(r"\(\s*(\d{1,2}(?:\s*\.\s*\d{1,2})*)\s*\.?\s*\)")
TRAILING_LABEL = re.compile(r"(\d{1,2}(?:\s*\.\s*\d{1,2})*)\s*\.?\s*$")


def parse_dish(item: str):
    """Return (name, allergen numbers) or None when the item cannot be read unambiguously.

    Labels may appear anywhere, e.g. "깍두기(9.13)(완)"; every numeric group is collected so
    that a trailing tag can never hide an allergen.
    """
    text = re.sub(r"<[^>]+>", "", item).strip()
    groups = PAREN_LABEL.findall(text)
    text = PAREN_LABEL.sub("", text)
    text = re.sub(r"\[[^\]]*\]|\([^)]*\)", "", text).strip()
    match = TRAILING_LABEL.search(text)
    # Unparenthesized trailing numbers count as a label only as a dotted list ("돈까스1.2.5.").
    if match and "." in match.group(0):
        groups.append(match.group(1))
        text = text[:match.start()]
    numbers = [int(n) for g in groups for n in re.split(r"\s*\.\s*", g) if n]
    if any(n not in NUMBERS for n in numbers):
        return None
    text = re.sub(r"[^0-9A-Za-z가-힣 ]", "", text).strip()
    if not re.search(r"[가-힣]", text) or re.search(r"\d", text):
        return None
    return text, sorted(set(numbers))


def get(service, params, key):
    query = {"Type": "json", **params, **({"KEY": key} if key else {})}
    with urllib.request.urlopen(API + service + "?" + urllib.parse.urlencode(query), timeout=20) as r:
        body = json.load(r)
    if service not in body:
        code = body.get("RESULT", {}).get("CODE")
        if code == "INFO-200":  # no data
            return 0, []
        raise RuntimeError(f"NEIS {service} failed: {code}")
    head, rows = body[service]
    return head["head"][0]["list_total_count"], rows["row"]


def paged(service, params, key, limit, delay):
    size = 1000 if key else 5
    page, rows = 1, []
    while len(rows) < limit:
        total, batch = get(service, {**params, "pIndex": page, "pSize": size}, key)
        rows += batch
        time.sleep(delay)
        if not batch or len(rows) >= total:
            break
        page += 1
    return rows[:limit]


def collect(start, end, schools_per_office, key, delay):
    dishes = defaultdict(lambda: {"count": 0, "allergens": Counter()})
    meals = schools = skipped = unlabelled = 0
    for office in OFFICES:
        found = paged("schoolInfo", {"ATPT_OFCDC_SC_CODE": office, "SCHUL_KND_SC_NM": "고등학교"},
                      key, schools_per_office + 5, delay)
        codes = [s["SD_SCHUL_CODE"].strip() for s in found if s["SD_SCHUL_CODE"].strip()]
        for code in codes[:schools_per_office]:
            rows = paged("mealServiceDietInfo", {"ATPT_OFCDC_SC_CODE": office, "SD_SCHUL_CODE": code,
                                                 "MMEAL_SC_CODE": "2", "MLSV_FROM_YMD": start,
                                                 "MLSV_TO_YMD": end}, key, 200, delay)
            schools += bool(rows)
            for row in rows:
                parsed = [parse_dish(item) for item in row["DDISH_NM"].split("<br/>")]
                skipped += parsed.count(None)
                parsed = [d for d in parsed if d is not None]
                # A lunch without any label means the school did not label that day; its dishes
                # must not count as "no allergens".
                if not any(numbers for _, numbers in parsed):
                    unlabelled += 1
                    continue
                meals += 1
                for name, numbers in parsed:
                    dishes[name]["count"] += 1
                    dishes[name]["allergens"].update(numbers)
        print(f"{office}: schools={schools} meals={meals} dishes={len(dishes)}", flush=True)
    return {
        "source": "교육부 NEIS 급식식단정보 (open.neis.go.kr) · 고등학교 중식 알레르기 표시",
        "collected": date.today().isoformat(), "period": [start, end],
        "schools": schools, "meals": meals, "unlabelled_meals_excluded": unlabelled,
        "skipped_items": skipped,
        "numbers": {str(k): v for k, v in NUMBERS.items()},
        "dishes": {name: {"count": d["count"], "allergens": {str(k): v for k, v in sorted(d["allergens"].items())}}
                   for name, d in sorted(dishes.items())},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--from", dest="start", required=True)
    parser.add_argument("--to", dest="end", required=True)
    parser.add_argument("--schools-per-office", type=int, default=6)
    parser.add_argument("--delay", type=float, default=0.2)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    a = parser.parse_args()
    result = collect(a.start, a.end, a.schools_per_office, os.getenv("NEIS_API_KEY", ""), a.delay)
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Wrote {len(result['dishes'])} dishes from {result['meals']} meals to {a.output}")


if __name__ == "__main__":
    main()
