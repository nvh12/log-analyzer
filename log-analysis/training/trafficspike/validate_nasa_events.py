"""
Validate the STS / Perseid event windows used in
traffic_spike_seasonal_v1_vs_v2.ipynb.

What this checks:
1. Hard-coded dates against externally-verified historical facts (printed
   as a reference table you can eyeball against any source).
2. For each window: mean per-minute rate INSIDE the window vs. mean rate
   at the SAME HOURS on the SAME DAY-OF-WEEK in the surrounding weeks.
   Same-DOW pooling kills the weekend-vs-weekday confound that a naive
   +/- 6hr baseline has.

Usage:
    python validate_nasa_events.py /path/to/nasa_log.csv
"""

import sys
import pandas as pd
import numpy as np

# ---- Config -------------------------------------------------------------
TS_COL = "time"        # NASA log timestamp column
TS_IS_EPOCH = True     # NASA log stores unix epoch seconds

# Same-DOW baseline pooling: how many weeks before/after the event to pool.
BASELINE_WEEKS = 2

# Externally verified reference facts.
REFERENCE_FACTS = [
    ("STS-71 launch",  "1995-06-27 15:32 EDT", "Atlantis, Shuttle-Mir docking mission"),
    ("STS-71 landing", "1995-07-07 10:54 EDT", "KSC Runway 15"),
    ("STS-70 launch",  "1995-07-13 09:42 EDT", "Discovery, TDRS-G deploy"),
    ("STS-70 landing", "1995-07-22 08:02 EDT", "KSC Runway 33"),
    ("Perseid peak",   "1995-08-12/13",        "Peak night Aug 12 into Aug 13"),
]

# Windows used in the notebook.
EVENT_WINDOWS = [
    ("STS-71 landing",  "1995-07-07 08:00", "1995-07-07 18:00", "real"),
    ("STS-70 launch",   "1995-07-13 10:00", "1995-07-13 20:00", "real"),
    ("STS-70 landing",  "1995-07-22 08:00", "1995-07-22 18:00", "real"),
    ("Perseid peak",    "1995-08-12 00:00", "1995-08-13 23:59", "control"),
]


def print_reference_table():
    print("=" * 78)
    print("REFERENCE FACTS")
    print("=" * 78)
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


def all_event_minutes(df):
    """Mask of timestamps falling in ANY labeled event window -- excluded
    from baseline pools so we don't compare event-to-event."""
    mask = pd.Series(False, index=df.index)
    for _, start, end, _ in EVENT_WINDOWS:
        s, e = pd.Timestamp(start), pd.Timestamp(end)
        mask = mask | ((df.timestamp >= s) & (df.timestamp <= e))
    return mask


def check_window_same_dow(df, name, start, end, contaminated_mask,
                          baseline_weeks=BASELINE_WEEKS):
    """Compare window to same-hour, same-DOW minutes in surrounding weeks."""
    start_ts, end_ts = pd.Timestamp(start), pd.Timestamp(end)
    week = pd.Timedelta(days=7)

    in_window = df[(df.timestamp >= start_ts) & (df.timestamp <= end_ts)]
    if len(in_window) == 0:
        print(f"  [MISSING] {name}: no log rows in this window.")
        return

    baseline_frames = []
    used_offsets = []
    for k in range(1, baseline_weeks + 1):
        for sign in (-1, +1):
            offset = sign * k * week
            shifted_start = start_ts + offset
            shifted_end = end_ts + offset
            cand = df[(df.timestamp >= shifted_start) &
                      (df.timestamp <= shifted_end) &
                      ~contaminated_mask]
            if len(cand) > 0:
                baseline_frames.append(cand)
                used_offsets.append(f"{'+' if sign > 0 else '-'}{k}wk")

    if not baseline_frames:
        print(f"  {name:<24} window_mean={in_window['value'].mean():7.2f}  "
              f"[NO CLEAN BASELINE FOUND]")
        return

    baseline = pd.concat(baseline_frames)
    win_mean = in_window["value"].mean()
    base_mean = baseline["value"].mean()
    ratio = win_mean / base_mean if base_mean > 0 else np.nan
    n_pool = len(baseline)

    if np.isnan(ratio):
        flag = "[ZERO BASELINE]"
    elif ratio < 0.85:
        flag = "[BELOW baseline]"
    elif ratio < 1.10:
        flag = "[FLAT -- matches baseline]"
    elif ratio < 1.30:
        flag = "[MILD bump]"
    else:
        flag = "[CLEAR SPIKE]"

    print(f"  {name:<24} window_mean={win_mean:7.2f}  "
          f"baseline_mean={base_mean:7.2f}  ratio={ratio:5.2f}  {flag}")
    print(f"  {'':<24} pooled from {used_offsets} ({n_pool:,} baseline minutes)")


def main():
    print_reference_table()

    if len(sys.argv) < 2:
        print("No CSV path given -- only printed the reference table above.")
        print("Run as: python validate_nasa_events.py /path/to/nasa_log.csv")
        return

    path = sys.argv[1]
    print(f"Loading log: {path}")
    df = load_log(path)
    print(f"  {len(df):,} per-minute rows, "
          f"range {df.timestamp.min()} -> {df.timestamp.max()}\n")

    contaminated = all_event_minutes(df)

    print("=" * 78)
    print(f"SPIKE CHECK (same-DOW baseline, +/-{BASELINE_WEEKS} weeks, "
          f"excluding other events)")
    print("=" * 78)
    for name, start, end, kind in EVENT_WINDOWS:
        check_window_same_dow(df, f"{name} ({kind})", start, end, contaminated)

    print()
    print("Reading guide:")
    print("- Baseline pools the SAME hour-of-day and SAME day-of-week in")
    print("  surrounding weeks -- weekday/weekend is matched apples-to-apples.")
    print("- Perseid is a CONTROL event -- [FLAT] is the design intent.")
    print("- [BELOW baseline] for a real event = window may miss the surge")
    print("  (e.g. surge happens just BEFORE the window start), or the")
    print("  event genuinely didn't drive web traffic.")


if __name__ == "__main__":
    main()