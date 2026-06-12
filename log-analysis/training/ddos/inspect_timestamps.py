"""
Inspect raw Timestamp values across CICIDS2017 CSVs to find the true format.
Point DATA_DIR at the same folder the prep notebook uses.
"""
import os
import pandas as pd

DATA_DIR = './data/CIC-IDS-2017/'   # <-- match the prep notebook's DATA_DIR
FILES = {
    'monday':      'Monday-WorkingHours.pcap_ISCX.csv',
    'friday_ddos': 'Friday-WorkingHours-Afternoon-DDos.pcap_ISCX.csv',
}

def find_ts_col(cols):
    for c in cols:
        if c.strip().lower() == 'timestamp':
            return c
    return None

for key, fname in FILES.items():
    path = os.path.join(DATA_DIR, fname)
    print('=' * 78)
    print(f'{key}: {path}')
    print('=' * 78)
    if not os.path.exists(path):
        print('  FILE NOT FOUND\n')
        continue

    # Read only the header + a chunk; keep Timestamp as raw string.
    df = pd.read_csv(path, low_memory=False, dtype=str)
    df.columns = df.columns.str.strip()
    ts_col = find_ts_col(df.columns)
    if ts_col is None:
        print(f'  No Timestamp column. Columns: {list(df.columns)[:10]}...\n')
        continue

    ts = df[ts_col].astype(str)

    # 1. Raw repr of first/last few values — exposes hidden chars, spacing, AM/PM.
    print('\n  First 8 raw values (repr shows whitespace/hidden chars):')
    for v in ts.head(8):
        print(f'    {v!r}')
    print('\n  Last 4 raw values:')
    for v in ts.tail(4):
        print(f'    {v!r}')

    # 2. Distinct structural shapes: digits->#, letters->A, keep separators.
    def shape(s):
        out = []
        for ch in str(s):
            if ch.isdigit():
                out.append('#')
            elif ch.isalpha():
                out.append('A')
            else:
                out.append(ch)
        return ''.join(out)

    shapes = ts.map(shape).value_counts()
    print(f'\n  Distinct timestamp shapes ({len(shapes)} total, top 12):')
    for shp, cnt in shapes.head(12).items():
        print(f'    {cnt:>8,}  {shp}')

    # 3. Does it contain AM/PM at all?
    has_ampm = ts.str.contains(r'\b[AP]M\b', case=False, regex=True, na=False).mean()
    print(f'\n  Fraction containing AM/PM token: {has_ampm:.3f}')

    # 4. Separator audit on the date portion.
    has_hyphen = ts.str.contains('-', na=False).mean()
    has_slash  = ts.str.contains('/', na=False).mean()
    print(f'  Fraction with hyphen "-": {has_hyphen:.3f}')
    print(f'  Fraction with slash  "/": {has_slash:.3f}')

    # 5. Try a few candidate explicit formats, report parse success rate.
    candidates = [
        '%d/%m/%Y %I:%M:%S %p',
        '%d/%m/%Y %H:%M:%S',
        '%d/%m/%Y %H:%M',
        '%m/%d/%Y %I:%M:%S %p',
        '%m/%d/%Y %H:%M',
        '%d-%m-%Y %I:%M:%S %p',
        '%d-%m-%Y %H:%M:%S',
    ]
    norm = (ts.str.strip()
              .str.replace('-', '/', regex=False)
              .str.replace(r'\s+', ' ', regex=True))
    print('\n  Parse success rate by candidate format (on slash-normalized text):')
    for fmt in candidates:
        ok = pd.to_datetime(norm, format=fmt, errors='coerce').notna().mean()
        print(f'    {ok:6.3f}   {fmt}')

    # 6. Inference fallback rate (what dayfirst=True alone achieves).
    inf = pd.to_datetime(ts, dayfirst=True, errors='coerce').notna().mean()
    print(f'\n  dayfirst=True inference success rate: {inf:.3f}')
    print()
