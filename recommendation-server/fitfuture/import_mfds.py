"""Fetch official I0750 rows over HTTPS or normalize a downloaded official JSON export.

No missing value is replaced with zero. Output requires reviewed aliases + independent recipe,
allergen/cross-contact, price and environment evidence before recommendations become available.
"""
import argparse
import asyncio
import json
import os
from pathlib import Path

import httpx

from .nutrition import Food
from .schemas import Nutrition


def convert(row):
    # Official I0750 nutrients are per SERVING_WT, not necessarily per 100 g.
    return Food(id=row["FOOD_CD"], name=row["DESC_KOR"], basis_grams=float(row["SERVING_WT"]),
                nutrition=Nutrition(calories_kcal=float(row["NUTR_CONT1"]),
                                    carbohydrate_grams=float(row["NUTR_CONT2"]),
                                    protein_grams=float(row["NUTR_CONT3"]), fat_grams=float(row["NUTR_CONT4"])),
                source="mfds", source_record_id=row["FOOD_CD"])


def normalize(payload):
    body = payload["I0750"]
    if body.get("RESULT", {}).get("CODE") != "INFO-000":
        raise ValueError("MFDS response was not successful; check the subscribed service")
    foods, rejected, duplicates = {}, 0, set()
    for row in body.get("row", []):
        try:
            f = convert(row)
            if f.id in foods or f.id in duplicates:
                # Do not silently choose a different year/version of the same code.
                if f.id in foods:
                    del foods[f.id]
                    rejected += 1
                duplicates.add(f.id)
                raise ValueError("duplicate record; select a reviewed year/version first")
            foods[f.id] = f.model_dump(mode="json")
        except (ValueError, KeyError, TypeError):
            rejected += 1
    if not foods:
        raise ValueError("No complete nutrition records; check source schema")
    return {"provenance": "mfds", "foods": list(foods.values()), "offers": []}, rejected


async def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, help="official I0750 JSON export instead of network")
    parser.add_argument("--output", type=Path, default=Path("data/mfds.json"))
    parser.add_argument("--start", type=int, default=1)
    parser.add_argument("--end", type=int, default=1000)
    args = parser.parse_args()
    if args.input:
        payload = json.loads(args.input.read_text())
    else:
        key = os.environ["MFDS_API_KEY"]
        # Fixed official host; credentials are not printed. No insecure HTTP fallback.
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.get(f"https://openapi.foodsafetykorea.go.kr/api/{key}/I0750/json/{args.start}/{args.end}")
            response.raise_for_status()
            payload = response.json()
    catalog, rejected = normalize(payload)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(catalog, ensure_ascii=False, indent=2))
    print(f"Imported {len(catalog['foods'])} rows, skipped {rejected} incomplete rows. No price/allergen evidence imported.")


if __name__ == "__main__":
    # Avoid displaying HTTP exceptions containing API keys in their request URLs.
    try:
        asyncio.run(main())
    except (httpx.HTTPError, KeyError, ValueError, OSError):
        raise SystemExit("MFDS import failed. Verify key, HTTPS availability, subscription and JSON schema.") from None
