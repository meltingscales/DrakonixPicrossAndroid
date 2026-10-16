#!/usr/bin/env python3
"""Check that every level's clues have exactly one solution.

Lives mode and per-number clue fading judge moves against the stored picture,
so a level with a second valid answer would punish correct play. Standard
library only. Exits non-zero if any level is ambiguous or malformed.
"""
import sys
from functools import lru_cache
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from gallery import LEVELS, parse  # noqa: E402

UNKNOWN, EMPTY, FILLED = -1, 0, 1


def clues(line):
    runs, n = [], 0
    for v in line:
        if v:
            n += 1
        elif n:
            runs.append(n)
            n = 0
    if n:
        runs.append(n)
    return tuple(runs)


@lru_cache(maxsize=None)
def placements(clue, length):
    """All fillings of a line of `length` matching `clue`, as tuples of 0/1."""
    if not clue:
        return [(0,) * length]
    first, rest = clue[0], clue[1:]
    tail_min = sum(rest) + len(rest)
    out = []
    for start in range(length - first - tail_min + 1):
        head = (0,) * start + (1,) * first
        if rest:
            for t in placements(rest, length - len(head) - 1):
                out.append(head + (0,) + t)
        else:
            out.append(head + (0,) * (length - len(head)))
    return out


def solve_line(clue, cells):
    """Narrow a line given known cells; None if contradictory."""
    fits = [p for p in placements(clue, len(cells))
            if all(c == UNKNOWN or c == v for c, v in zip(cells, p))]
    if not fits:
        return None
    return [fits[0][i] if all(p[i] == fits[0][i] for p in fits) else UNKNOWN
            for i in range(len(cells))]


def propagate(grid, rclues, cclues):
    rows, cols = len(grid), len(grid[0])
    changed = True
    while changed:
        changed = False
        for r in range(rows):
            new = solve_line(rclues[r], grid[r])
            if new is None:
                return False
            if new != grid[r]:
                grid[r], changed = new, True
        for c in range(cols):
            col = [grid[r][c] for r in range(rows)]
            new = solve_line(cclues[c], col)
            if new is None:
                return False
            if new != col:
                for r in range(rows):
                    grid[r][c] = new[r]
                changed = True
    return True


def count_solutions(grid, rclues, cclues, limit=2):
    grid = [row[:] for row in grid]
    if not propagate(grid, rclues, cclues):
        return 0
    for r, row in enumerate(grid):
        for c, v in enumerate(row):
            if v == UNKNOWN:
                total = 0
                for guess in (FILLED, EMPTY):
                    g = [x[:] for x in grid]
                    g[r][c] = guess
                    total += count_solutions(g, rclues, cclues, limit - total)
                    if total >= limit:
                        return total
                return total
    return 1


def main(paths):
    bad = 0
    for path in paths:
        name, _, rows = parse(path)
        sol = [[0 if ch == "." else 1 for ch in row] for row in rows]
        rclues = [clues(row) for row in sol]
        cclues = [clues(col) for col in zip(*sol)]
        blank = [[UNKNOWN] * len(sol[0]) for _ in sol]
        n = count_solutions(blank, rclues, cclues)
        if n != 1:
            bad += 1
            print(f"AMBIGUOUS  {path.name} ({name}): more than one solution")
    print(f"checked {len(paths)} levels, {bad} ambiguous")
    return 1 if bad else 0


if __name__ == "__main__":
    args = [Path(a) for a in sys.argv[1:]] or sorted(LEVELS.glob("*.txt"))
    sys.exit(main(args))
