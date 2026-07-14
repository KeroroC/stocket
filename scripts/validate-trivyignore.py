#!/usr/bin/env python3
"""Validate time-bounded, documented Trivy vulnerability waivers."""

from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from pathlib import Path


CVE = re.compile(r"CVE-\d{4}-\d{4,}")
METADATA = re.compile(r"#\s*(impact|mitigation|owner|expires):\s*(.+)", re.IGNORECASE)
REQUIRED = {"impact", "mitigation", "owner", "expires"}


def validate(path: Path, today: dt.date) -> list[str]:
    errors: list[str] = []
    metadata: dict[str, str] = {}
    seen: set[str] = set()

    for line_number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line:
            metadata = {}
            continue
        match = METADATA.fullmatch(line)
        if match:
            metadata[match.group(1).lower()] = match.group(2).strip()
            continue
        if line.startswith("#"):
            continue
        if not CVE.fullmatch(line):
            errors.append(f"{path}:{line_number}: unsupported entry {line!r}")
            metadata = {}
            continue
        if line in seen:
            errors.append(f"{path}:{line_number}: duplicate waiver {line}")
        seen.add(line)
        missing = sorted(REQUIRED - metadata.keys())
        if missing:
            errors.append(f"{path}:{line_number}: {line} missing metadata: {', '.join(missing)}")
        else:
            try:
                expires = dt.date.fromisoformat(metadata["expires"])
            except ValueError:
                errors.append(f"{path}:{line_number}: {line} has invalid expires date")
            else:
                if expires < today:
                    errors.append(f"{path}:{line_number}: {line} waiver expired on {expires}")
                if expires > today + dt.timedelta(days=30):
                    errors.append(f"{path}:{line_number}: {line} waiver exceeds 30 days ({expires})")
        metadata = {}

    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", default=".trivyignore")
    parser.add_argument("--today", type=dt.date.fromisoformat, default=dt.date.today())
    args = parser.parse_args()
    path = Path(args.path)
    if not path.is_file():
        print(f"missing Trivy ignore file: {path}", file=sys.stderr)
        return 1
    errors = validate(path, args.today)
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    print(f"validated Trivy waivers: {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
