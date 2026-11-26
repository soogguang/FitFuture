"""Food-name matching shared with the Android app (NutritionDatabase.java).

Both sides read the same MFDS TSV and alias file and normalize names identically:
remove whitespace and '_', then lowercase. Only exact normalized names or listed aliases
match; there is no fuzzy matching, so evidence is never attached to a different dish.
"""
import re
from pathlib import Path

NUTRITION_DIR = Path(__file__).resolve().parents[2] / "app/src/main/assets/nutrition"
MFDS_TSV = NUTRITION_DIR / "mfds-foods.tsv"
ALIASES_TSV = NUTRITION_DIR / "aliases.tsv"


def key(name: str) -> str:
    return re.sub(r"[\s_]", "", name).lower()


def load_aliases(path: Path = ALIASES_TSV) -> dict[str, str]:
    aliases = {}
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip() or line.startswith("#"):
            continue
        parts = line.split("\t")
        if len(parts) != 2 or not parts[0].strip() or not parts[1].strip():
            raise ValueError(f"Invalid alias line {number}")
        aliases[key(parts[0])] = parts[1].strip()
    return aliases


def load_mfds(path: Path = MFDS_TSV) -> list[tuple[str, str, str, float, float, float, float]]:
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        v = line.split("\t")
        if len(v) != 7:
            raise ValueError("Invalid nutrition database")
        rows.append((v[0], v[1], v[2], *map(float, v[3:])))
    return rows


class NameIndex:
    """First row wins per normalized name, matching the app's putIfAbsent order."""

    def __init__(self, rows, aliases):
        self.aliases = aliases
        self.by_key = {}
        for row in rows:
            self.by_key.setdefault(key(row[1]), row)

    def lookup(self, name: str):
        k = key(name)
        return self.by_key.get(key(self.aliases.get(k, k)))
