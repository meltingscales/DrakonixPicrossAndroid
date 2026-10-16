#!/usr/bin/env python3
"""Render every level in app/src/main/assets/levels as a pixel-art gallery page
and open it in the default browser. Standard library only.

Parses the same format as Levels.kt: line 1 is the name, optional CHAR=#RRGGBB
color lines, then grid rows where '.' is empty and anything else is filled
('#' or an undefined char falls back to the foreground color).
"""
import html
import sys
import webbrowser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LEVELS = ROOT / "app/src/main/assets/levels"
OUT = ROOT / "build/gallery.html"
FG = "#f2f2f2"


def parse(path: Path):
    lines = [l.strip() for l in path.read_text().splitlines() if l.strip()]
    name, colors, grid = lines[0], {}, []
    for line in lines[1:]:
        if not grid and len(line) == 9 and line[1:3] == "=#":
            colors[line[0]] = line[2:]
        else:
            grid.append(line)
    return name, colors, grid


def svg(colors, grid, size=160):
    rows, cols = len(grid), len(grid[0])
    rects = "".join(
        f'<rect x="{c}" y="{r}" width="1" height="1" fill="{colors.get(ch, FG)}"/>'
        for r, row in enumerate(grid)
        for c, ch in enumerate(row)
        if ch != "."
    )
    return (
        f'<svg viewBox="0 0 {cols} {rows}" width="{size}" height="{size}" '
        f'shape-rendering="crispEdges"><rect width="{cols}" height="{rows}" fill="#1e1e1e"/>{rects}</svg>'
    )


def main():
    cards = []
    for i, path in enumerate(sorted(LEVELS.glob("*.txt")), 1):
        try:
            name, colors, grid = parse(path)
        except Exception as e:  # keep going so one bad file doesn't hide the rest
            print(f"skipping {path.name}: {e}", file=sys.stderr)
            continue
        size = f"{len(grid[0])}×{len(grid)}"
        cards.append(
            f'<figure>{svg(colors, grid)}<figcaption>{i}. {html.escape(name)}'
            f'<small>{size} · {html.escape(path.name)}</small></figcaption></figure>'
        )
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(f"""<!doctype html><meta charset="utf-8">
<title>Drakonix Picross gallery</title>
<style>
body {{ background:#0d0d0d; color:#f2f2f2; font-family:system-ui,sans-serif; margin:24px; }}
h1 {{ font-weight:700; }}
main {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(180px,1fr)); gap:20px; }}
figure {{ margin:0; text-align:center; }}
figcaption {{ margin-top:6px; }}
small {{ display:block; opacity:.55; font-size:12px; }}
</style>
<h1>Drakonix Picross — {len(cards)} levels</h1>
<main>{''.join(cards)}</main>
""")
    print(f"wrote {OUT} ({len(cards)} levels)")
    if "--no-open" not in sys.argv:
        webbrowser.open(OUT.as_uri())


if __name__ == "__main__":
    main()
