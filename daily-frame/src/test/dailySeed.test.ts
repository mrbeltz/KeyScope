import { describe, expect, it } from 'vitest';
import { LAUNCH_DATE, dailyIndex, dateForPuzzleNumber, hashString, puzzleNumber, seededPermutation } from '../lib/dailySeed';
import { addDays, dateKeyToDayNumber, localDateKey, msUntilNextLocalMidnight } from '../lib/dates';

describe('dailyIndex', () => {
  it('gives the same index for the same date, every time', () => {
    const a = dailyIndex('2026-09-27', 365);
    for (let i = 0; i < 5; i++) expect(dailyIndex('2026-09-27', 365)).toBe(a);
  });

  it('stays inside the pool', () => {
    for (let d = 0; d < 1000; d++) {
      const i = dailyIndex(addDays('2026-01-01', d), 37);
      expect(i).toBeGreaterThanOrEqual(0);
      expect(i).toBeLessThan(37);
      expect(Number.isInteger(i)).toBe(true);
    }
  });

  it('never repeats a movie within one pass through the pool', () => {
    const n = 365;
    // Find the first day of a cycle, then walk the whole cycle.
    let day = dateKeyToDayNumber('2026-01-01')!;
    day = Math.ceil(day / n) * n;
    const start = addDays('1970-01-01', day);
    const seen = new Set<number>();
    for (let i = 0; i < n; i++) seen.add(dailyIndex(addDays(start, i), n));
    expect(seen.size).toBe(n);
  });

  it('does not simply march through the list in order', () => {
    const seq = Array.from({ length: 10 }, (_, i) => dailyIndex(addDays('2026-03-01', i), 365));
    const consecutive = seq.every((v, i) => i === 0 || v === seq[i - 1] + 1);
    expect(consecutive).toBe(false);
  });

  it('changes on consecutive days', () => {
    expect(dailyIndex('2026-09-27', 365)).not.toBe(dailyIndex('2026-09-28', 365));
  });

  it('rejects bad input', () => {
    expect(() => dailyIndex('2026-02-30', 365)).toThrow();
    expect(() => dailyIndex('not-a-date', 365)).toThrow();
    expect(() => dailyIndex('2026-09-27', 0)).toThrow();
  });
});

describe('local date keys', () => {
  it('uses the local calendar date, so the puzzle rolls over at local midnight', () => {
    expect(localDateKey(new Date(2026, 8, 27, 23, 59, 59))).toBe('2026-09-27');
    expect(localDateKey(new Date(2026, 8, 28, 0, 0, 0))).toBe('2026-09-28');
  });

  it('counts down to the next local midnight', () => {
    expect(msUntilNextLocalMidnight(new Date(2026, 8, 27, 23, 59, 0))).toBe(60_000);
    expect(msUntilNextLocalMidnight(new Date(2026, 8, 27, 0, 0, 0))).toBe(86_400_000);
  });

  it('treats a daylight-saving day as one calendar day', () => {
    // US DST starts 2026-03-08 and ends 2026-11-01; day numbers must stay contiguous.
    expect(dateKeyToDayNumber('2026-03-09')! - dateKeyToDayNumber('2026-03-08')!).toBe(1);
    expect(addDays('2026-11-01', 1)).toBe('2026-11-02');
  });
});

describe('puzzle numbers', () => {
  it('starts at #1 on launch day and round-trips', () => {
    expect(puzzleNumber(LAUNCH_DATE)).toBe(1);
    expect(puzzleNumber(addDays(LAUNCH_DATE, 141))).toBe(142);
    expect(dateForPuzzleNumber(142)).toBe(addDays(LAUNCH_DATE, 141));
  });
});

describe('seeded primitives', () => {
  it('hashString is stable', () => {
    expect(hashString('daily-frame')).toBe(hashString('daily-frame'));
    expect(hashString('a')).not.toBe(hashString('b'));
  });

  it('seededPermutation is a permutation and is deterministic', () => {
    const p = seededPermutation(50, 1234);
    expect([...p].sort((a, b) => a - b)).toEqual(Array.from({ length: 50 }, (_, i) => i));
    expect(seededPermutation(50, 1234)).toEqual(p);
    expect(seededPermutation(50, 1235)).not.toEqual(p);
  });
});
