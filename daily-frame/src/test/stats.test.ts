import { describe, expect, it } from 'vitest';
import { type DailyHistory, computeStats, currentStreak, recordResult } from '../lib/stats';

const win = (guesses: number) => ({ won: true, guesses });
const loss = { won: false, guesses: 6 };

describe('streaks', () => {
  it('counts consecutive wins ending today', () => {
    const h: DailyHistory = { '2026-09-25': win(2), '2026-09-26': win(4), '2026-09-27': win(1) };
    expect(currentStreak(h, '2026-09-27')).toBe(3);
  });

  it("keeps yesterday's streak alive while today is unplayed", () => {
    const h: DailyHistory = { '2026-09-25': win(2), '2026-09-26': win(4) };
    expect(currentStreak(h, '2026-09-27')).toBe(2);
  });

  it('breaks when a day is missed', () => {
    const h: DailyHistory = { '2026-09-24': win(2), '2026-09-25': win(3) };
    // 26th skipped entirely; on the 27th the streak is gone.
    expect(currentStreak(h, '2026-09-27')).toBe(0);
  });

  it('breaks on a loss, including a loss today', () => {
    expect(currentStreak({ '2026-09-25': win(2), '2026-09-26': loss }, '2026-09-27')).toBe(0);
    expect(currentStreak({ '2026-09-26': win(2), '2026-09-27': loss }, '2026-09-27')).toBe(0);
  });

  it('restarts after a break and tracks the best run', () => {
    const h: DailyHistory = {
      '2026-09-01': win(1),
      '2026-09-02': win(2),
      '2026-09-03': win(3),
      '2026-09-04': loss,
      '2026-09-05': win(2),
      '2026-09-07': win(2), // gap on the 6th
      '2026-09-08': win(5),
    };
    const s = computeStats(h, '2026-09-08');
    expect(s.currentStreak).toBe(2);
    expect(s.maxStreak).toBe(3);
  });

  it('crosses month and year boundaries', () => {
    const h: DailyHistory = { '2026-12-31': win(3), '2027-01-01': win(2) };
    expect(currentStreak(h, '2027-01-01')).toBe(2);
  });
});

describe('stats', () => {
  it('computes played, win % and the guess distribution', () => {
    const h: DailyHistory = { '2026-09-01': win(1), '2026-09-02': win(3), '2026-09-03': win(3), '2026-09-04': loss };
    const s = computeStats(h, '2026-09-04');
    expect(s.played).toBe(4);
    expect(s.wins).toBe(3);
    expect(s.winPct).toBe(75);
    expect(s.distribution).toEqual([1, 0, 2, 0, 0, 0]);
  });

  it('is all zeros with no history', () => {
    expect(computeStats({}, '2026-09-27')).toEqual({ played: 0, wins: 0, winPct: 0, currentStreak: 0, maxStreak: 0, distribution: [0, 0, 0, 0, 0, 0] });
  });

  it('records a day only once', () => {
    let h: DailyHistory = {};
    h = recordResult(h, '2026-09-27', win(2));
    const again = recordResult(h, '2026-09-27', loss);
    expect(again).toBe(h);
    expect(computeStats(again, '2026-09-27').played).toBe(1);
  });
});
