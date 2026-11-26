"""Extract MFDS foods XLSX to Android TSV; Python standard library only.

Usage: python scripts/import_mfds.py downloaded.xlsx
Only complete, nonnegative, finite energy/macros with explicit 100g basis
are retained. Never interpret 100ml as 100g, or blanks as zero.
"""
import math
import pathlib
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

NS = {'m': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}


def extract(path):
    rows = []
    with zipfile.ZipFile(path) as archive:
        strings = [''.join(x.itertext()) for x in
                   ET.fromstring(archive.read('xl/sharedStrings.xml'))]
        for _, row in ET.iterparse(archive.open('xl/worksheets/sheet1.xml'), events=['end']):
            if row.tag != '{' + NS['m'] + '}row':
                continue
            cells = {re.sub(r'\d', '', c.attrib['r']):
                     strings[int(c.find('m:v', NS).text)] if c.attrib.get('t') == 's'
                     else ''.join(c.itertext()) for c in row}
            if cells.get('Q') == '100g':
                try:
                    values = [float(cells[k]) for k in ['R', 'W', 'T', 'U']]
                    if all(math.isfinite(v) and v >= 0 for v in values):
                        rows.append([cells['A'], cells['B'], cells['F'], *values])
                except (KeyError, ValueError):
                    pass
            row.clear()
    # Eating-out measured, home measured, other measured, then calculated.
    def priority(row):
        origin = row[2]
        return (0 if origin.startswith('외식(분석') else
                1 if origin.startswith('가정식(분석') else
                2 if '분석' in origin else 3, row[0])
    return sorted(rows, key=priority)


if __name__ == '__main__':
    rows = extract(sys.argv[1])
    destination = pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/assets/nutrition/mfds-foods.tsv'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(''.join('\t'.join(map(str, row)) + '\n' for row in rows), encoding='utf-8')
    print(f'Exported {len(rows)} complete 100g records')
