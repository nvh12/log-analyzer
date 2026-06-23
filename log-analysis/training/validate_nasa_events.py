"""
Validate the STS / Perseid event windows used in traffic_spike_seasonal_v1_vs_v2.ipynb.

What this checks:
1. The hard-coded dates match externally-verified historical facts
   (printed as a reference table you can eyeball against any source).
2. Each labeled window actually contains a measurable traffic spike in
   YOUR NASA log -- i.e. the mean per-minute request rate inside the
   window is meaningfully higher than the surrounding baseline. This
   catches the case where the date is "correct" per Wikipedia/NASA but
   doesn't line up with what's actually in this specific log file
   (timezone offset, log doesn't cover that day, etc).

Usage:
    python validate_nasa_events.py /path/to/nasa_log.csv

Expects a CSV with a timestamp column (edit TS_COL / TS_IS_EPOCH below
to match your actual file, same as in the notebook).
"""

import sys
import pandas as pd
import numpy as np

# ---- Config: match these to whatever the notebook uses -------------------
TS_COL = "ts"          # change to your actual timestamp column name
TS_IS_EPOCH = False    # True if timestamps are unix epoch seconds

# Externally verified reference facts (sources: NASA mission pages,
# Wikipedia, American Meteor Society / Old Farmer's Almanac -- checked
# 2026-06-22). Compare these against wherever you originally got your
# event list.
REFERENCE_FACTS = [
    ("STS-71 launch",  "1995-06-27 15:32 EDT", "Atlantis, Shuttle-Mir docking mission"),
    ("STS-71 landing", "1995-07-07 10:54 EDT", "KSC Runway 15"),
    ("STS-70 launch",  "1995-07-13 09:42 EDT", "Discovery, TDRS-G deploy"),
    ("STS-70 landing", "1995-07-22 08:02 EDT", "KSC Runway 33"),
    ("Perseid peak",   "1995-08-12/13",        "Peak night Aug 12 into Aug 13 (early-peak filament)"),
]

# The windows as currently used in the notebook.
EVENT_WINDOWS = [
    ("STS-71 landing",  "1995-07-07 08:00", "1995-07-07 18:00", "real"),
    ("STS-70 launch",   "1995-07-13 10:00", "1995-07-13 20:00", "real"),
    ("STS-70 landing",  "1995-07-22 08:00", "1995-07-22 18:00", "real"),
    ("Perseid peak",    "1995-08-12 00:00", "1995-08-13 23:59", "control"),
]


def print_reference_table():
    print("=" * 70)
    print("REFERENCE FACTS (verify these against your original source)")
    print("=" * 70)
    for name, when, note in REFERENCE_FACTS:
        print(f"  {name:<16} {when:<22} {note}")
    print()


def load_log(path):
    raw = pd.read_csv(path, usecols=[TS_COL])
    raw["ts"] = pd.to_datetime(
        raw[TS_COL], unit="s" if TS_IS_EPOCH else None, errors="coerce"
    )
    raw = raw.dropna(subset=["ts"]).sort_values("ts").reset_index(drop=True)
    per_min = (
        raw.set_index("ts").resample("1min").size()
        .rename("value").reset_index().rename(columns={"ts": "timestamp"})
    )
    return per_min


def check_window_has_spike(df, name, start, end, pad_hours=6):
    """Compare mean rate inside the window to mean rate in the padding
    just before/after it. Flags windows where there's no detectable bump,
    which would suggest a timezone/date mismatch rather than a labeling
    error per se."""
    start_ts, end_ts = pd.Timestamp(start), pd.Timestamp(end)
    pad = pd.Timedelta(hours=pad_hours)

    in_window = df[(df.timestamp >= start_ts) & (df.timestamp <= end_ts)]
    before = df[(df.timestamp >= start_ts - pad) & (df.timestamp < start_ts)]
    after = df[(df.timestamp > end_ts) & (df.timestamp <= end_ts + pad)]
    baseline = pd.concat([before, after])

    if len(in_window) == 0:
        print(f"  [MISSING] {name}: no log rows at all in this window "
              f"-- log may not cover this date, or timezone is off.")
        return None

    win_mean = in_window["value"].mean()
    base_mean = baseline["value"].mean() if len(baseline) else np.nan
    ratio = win_mean / base_mean if base_mean and base_mean > 0 else np.nan

    flag = ""
    if np.isnan(ratio):
        flag = "[NO BASELINE -- can't compare]"
    elif ratio < 1.05:
        flag = "[FLAT -- no detectable spike, check window/timezone]"
    elif ratio > 1.3:
        flag = "[CLEAR SPIKE]"
    else:
        flag = "[MILD -- present but modest]"

    print(f"  {name:<16} window_mean={win_mean:7.2f}  "
          f"baseline_mean={base_mean:7.2f}  ratio={ratio:5.2f}  {flag}")
    return ratio


def main():
    print_reference_table()

    if len(sys.argv) < 2:
        print("No CSV path given -- only printed the reference table above.")
        print("Run again as: python validate_nasa_events.py /path/to/nasa_log.csv")
        return

    path = sys.argv[1]
    print(f"Loading log: {path}")
    df = load_log(path)
    print(f"  {len(df):,} per-minute rows, "
          f"range {df.timestamp.min()} -> {df.timestamp.max()}\n")

    print("=" * 70)
    print("SPIKE CHECK (does each window actually show elevated traffic?)")
    print("=" * 70)
    for name, start, end, kind in EVENT_WINDOWS:
        check_window_has_spike(df, f"{name} ({kind})", start, end)

    print()
    print("Notes:")
    print("- [MISSING] or [FLAT] does NOT necessarily mean the date is wrong --")
    print("  it could be a timezone mismatch (log may be GMT, EDT, or local),")
    print("  or the window may be too tight/wide. Cross-check against the")
    print("  reference table above before changing anything.")
    print("- Perseid is a CONTROL event by design -- a flat/mild result there")
    print("  is expected and is not a bug.")


if __name__ == "__main__":
    main()
