"""Turns results/<platform>-<profile>.csv from scripts/bench.sh into Markdown tables for FINDINGS.md.

Usage: python3 -I tools/bench_summary.py [results_dir]
"""
import csv
import statistics
import sys
from pathlib import Path

RUNS = [("android", "typical"), ("android", "heavy"), ("ios", "typical"), ("ios", "heavy")]


def col(rows, mode, name):
    return [float(r[name]) for r in rows if r["mode"] == mode and r["run"] != "migration"]


def med(values):
    """Median with range, e.g. '114 (89–201)'."""
    return f"{fmt(statistics.median(values))} ({fmt(min(values))}–{fmt(max(values))})"


def pair_ratio(rows):
    """Median over runs of old-store first read / DataStore first access, each pair from the same run.

    Runs alternate DataStore and old store, so a pair shares the host's state; the ratio survives a
    host that slows down part-way through, which a plain median does not.
    """
    by_run = {}
    for r in rows:
        if r["run"] != "migration":
            by_run.setdefault(r["run"], {})[r["mode"]] = float(r["load_ms"])
    ratios = [p["baseline"] / p["datastore"] for p in by_run.values() if len(p) == 2]
    return f"{statistics.median(ratios):.2f} ({min(ratios):.2f}–{max(ratios):.2f})"


def fmt(x):
    return f"{x:.0f}" if x >= 10 else f"{x:.1f}"


def ints(values):
    return f"{statistics.median(values):.0f} ({min(values):.0f}–{max(values):.0f})"


def kb(n):
    return f"{n / 1024:.0f} KB" if n < 1024 * 1024 else f"{n / 1024 / 1024:.1f} MB"


def main(results):
    out = ["| Platform, profile | Runs | First access, DataStore ms | Migration run ms | Key setup ms | "
           "First read, old store ms | One write, DataStore ms | One write, old store ms | "
           "100 quick writes ms | Saves for 100 writes | DataStore file | Old store file | "
           "Old read ÷ DataStore access, per run | Host load |",
           "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for platform, profile in RUNS:
        path = results / f"{platform}-{profile}.csv"
        if not path.exists():
            continue
        rows = list(csv.DictReader(path.open()))
        migration = next(r for r in rows if r["run"] == "migration")
        sizes = dict(kv.split("=") for kv in (results / f"{platform}-{profile}-sizes.txt")
                     .read_text().strip().split(",")[2:])
        n = len(col(rows, "datastore", "load_ms"))
        out.append("| " + " | ".join([
            f"{platform}, {profile}", str(n),
            med(col(rows, "datastore", "load_ms")), fmt(float(migration["load_ms"])),
            med(col(rows, "datastore", "keys_ms")),
            med(col(rows, "baseline", "load_ms")),
            med(col(rows, "datastore", "save_ms")), med(col(rows, "baseline", "save_ms")),
            med(col(rows, "datastore", "burst_ms")),
            ints(col(rows, "datastore", "burst_saves")),
            kb(int(sizes["new_store_bytes"])), kb(int(sizes["old_store_bytes"])),
            pair_ratio(rows),
            f"{min(float(r['host_load1']) for r in rows):.1f}–{max(float(r['host_load1']) for r in rows):.1f}",
        ]) + " |")
    print("\n".join(out))


if __name__ == "__main__":
    main(Path(sys.argv[1] if len(sys.argv) > 1 else "results"))
