"""Collect restaurant menu prices from Seoul's price-stable model stores (서울시 가격안정 모범업소).

Aggregates menu prices per dish name (median, count). The recommendation server attaches a
price only to MFDS foods whose normalized name or alias matches exactly, and only for the
'외식' environment. Model stores are designated for below-average prices, so the median is a
reference price, not what every restaurant charges.

Usage: SEOUL_API_KEY=... python -m fitfuture.import_seoul_prices
The Seoul open API is served over HTTP only, so the (free, read-only) key travels unencrypted.
"""
import argparse
import json
import os
import re
import statistics
import time
import urllib.request
from collections import defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "evidence/seoul-prices.json"
SERVICE = "ListPriceModelStoreProductService"
# Match restaurant industries by name (한식, 중식, 경양식·일식, 기타외식업), not by numeric code.
FOOD_INDUSTRY = re.compile(r"한식|중식|일식|양식|외식")


def clean_menu(name: str):
    text = re.sub(r"\[[^\]]*\]|\([^)]*\)", "", name)
    text = re.sub(r"[^0-9A-Za-z가-힣 ]", "", text).strip()
    if not re.search(r"[가-힣]", text) or re.search(r"\d", text):
        return None
    return text


def fetch(key, delay):
    rows, start, size = [], 1, 1000
    while True:
        url = f"http://openapi.seoul.go.kr:8088/{key}/json/{SERVICE}/{start}/{start + size - 1}/"
        with urllib.request.urlopen(url, timeout=30) as r:
            body = json.load(r)
        if SERVICE not in body:
            raise RuntimeError(f"Seoul API failed: {body.get('RESULT', {}).get('CODE')}")
        batch = body[SERVICE].get("row", [])
        rows += batch
        total = body[SERVICE]["list_total_count"]
        start += size
        time.sleep(delay)
        if not batch or start > total:
            return rows


def aggregate(rows):
    prices, stores = defaultdict(list), set()
    for row in rows:
        if not FOOD_INDUSTRY.search(row.get("INDUTY_CODE_SE_NAME") or ""):
            continue
        name, price = clean_menu(row.get("IM_NAME") or ""), row.get("IM_PRICE")
        if name and isinstance(price, (int, float)) and 500 <= price <= 100000:
            prices[name].append(int(price))
            stores.add(row.get("SH_ID"))
    return {
        "source": "서울시 가격안정 모범업소 메뉴 가격 (data.seoul.go.kr)",
        "collected": date.today().isoformat(), "environment": "외식", "stores": len(stores),
        "dishes": {name: {"count": len(p), "median_won": int(statistics.median(p)),
                          "min_won": min(p), "max_won": max(p)}
                   for name, p in sorted(prices.items())},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--delay", type=float, default=0.2)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    a = parser.parse_args()
    key = os.getenv("SEOUL_API_KEY", "")
    if not key:
        raise SystemExit("SEOUL_API_KEY is required (free key from data.seoul.go.kr)")
    result = aggregate(fetch(key, a.delay))
    a.output.parent.mkdir(parents=True, exist_ok=True)
    a.output.write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Wrote {len(result['dishes'])} priced dishes from {result['stores']} stores to {a.output}")


if __name__ == "__main__":
    main()
