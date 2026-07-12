#!/usr/bin/env python3
"""
update_badges.py
Fetches live Stars + total Downloads from GitHub API and writes
static SVG badge files to badges/ folder. Triggered by GitHub
Action on watch / release events so numbers update near-instantly.

No external deps — uses stdlib only.
"""

import json
import os
import sys
import urllib.request
import urllib.error
from pathlib import Path

REPO = os.environ.get("REPO", "se6or/al-hosan-checker")
TOKEN = os.environ.get("GH_TOKEN", "")
BADGES_DIR = Path("badges")
BADGES_DIR.mkdir(exist_ok=True)


def gh_api(path: str):
    url = f"https://api.github.com{path}"
    req = urllib.request.Request(url, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "badge-updater",
    })
    if TOKEN:
        req.add_header("Authorization", f"Bearer {TOKEN}")
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())


def make_svg(label: str, value: str, color: str, label_color: str = "#555") -> str:
    """Render a flat-style SVG badge (shields.io-compatible look)."""
    label_w = max(40, len(label) * 7 + 20)
    value_w = max(30, len(value) * 8 + 20)
    total_w = label_w + value_w
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{total_w}" height="20" role="img" aria-label="{label}: {value}">
<linearGradient id="b" x2="0" y2="100%">
<stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
<stop offset="1" stop-opacity=".1"/>
</linearGradient>
<mask id="a"><rect width="{total_w}" height="20" rx="3" fill="#fff"/></mask>
<g mask="url(#a)">
<path fill="#fff" d="M0 0h{total_w}v20H0z"/>
<path fill="{label_color}" d="M0 0h{label_w}v20H0z"/>
<path fill="{color}" d="M{label_w} 0h{value_w}v20H0z"/>
<path fill="url(#b)" d="M0 0h{total_w}v20H0z"/>
</g>
<g fill="#fff" text-anchor="middle" font-family="DejaVu Sans,Verdana,Geneva,sans-serif" font-size="11" text-rendering="geometricPrecision">
<text x="{label_w // 2}" y="15" fill="#010101" fill-opacity=".3">{label}</text>
<text x="{label_w // 2}" y="14">{label}</text>
<text x="{label_w + value_w // 2}" y="15" fill="#010101" fill-opacity=".3">{value}</text>
<text x="{label_w + value_w // 2}" y="14">{value}</text>
</g>
</svg>
'''


def main():
    try:
        repo_info = gh_api(f"/repos/{REPO}")
        stars = repo_info.get("stargazers_count", 0)
    except Exception as e:
        print(f"ERR fetching repo info: {e}", file=sys.stderr)
        stars = None

    if stars is not None:
        svg = make_svg("Stars", str(stars), "#dfb317")
        (BADGES_DIR / "stars.svg").write_text(svg, encoding="utf-8")
        print(f"stars.svg written: {stars}")

    try:
        releases = gh_api(f"/repos/{REPO}/releases?per_page=100")
        total = sum(
            asset.get("download_count", 0)
            for rel in releases
            for asset in rel.get("assets", [])
        )
    except Exception as e:
        print(f"ERR fetching releases: {e}", file=sys.stderr)
        total = None

    if total is not None:
        svg = make_svg("Downloads", str(total), "#007ec6")
        (BADGES_DIR / "downloads.svg").write_text(svg, encoding="utf-8")
        print(f"downloads.svg written: {total}")


if __name__ == "__main__":
    main()
