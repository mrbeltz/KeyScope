import { type DateKey, addDays, dateKeyToDayNumber } from './dates';
import { MAX_GUESSES } from './types';

/** One finished daily puzzle. Archive replays are never written here. */
export interface DailyResult {
  won: boolean;
  /** Guesses used, 1–6. For a loss this is 6. */
  guesses: number;
}

export type DailyHistory = Record<DateKey, DailyResult>;

export interface Stats {
  played: number;
  wins: number;
  winPct: number;
  currentStreak: number;
  maxStreak: number;
  /** distribution[i] = wins in i+1 guesses. */
  distribution: number[];
}

/**
 * Adds a finished daily game to the history. Recording the same date twice keeps the first
 * result, so a reload or a double-fired effect can never count a game twice.
 */
export function recordResult(history: DailyHistory, date: DateKey, result: DailyResult): DailyHistory {
  if (history[date]) return history;
  return { ...history, [date]: result };
}

/**
 * Derives every stat from the history rather than keeping running counters, so there is no
 * state to drift out of sync.
 *
 * A streak is a run of won days on consecutive calendar dates. A loss ends it, and so does a
 * day with no result at all. The current streak survives until the end of the day after the
 * last win — you haven't missed today's puzzle until today is over.
 */
export function computeStats(history: DailyHistory, today: DateKey): Stats {
  const dates = Object.keys(history).filter((d) => dateKeyToDayNumber(d) !== null).sort();
  const distribution = new Array<number>(MAX_GUESSES).fill(0);
  let wins = 0;
  let maxStreak = 0;
  let run = 0;
  let prevDay: number | null = null;

  for (const date of dates) {
    const r = history[date];
    const day = dateKeyToDayNumber(date)!;
    if (r.won) {
      wins++;
      const g = Math.min(Math.max(r.guesses, 1), MAX_GUESSES);
      distribution[g - 1]++;
      run = prevDay !== null && day === prevDay + 1 && run > 0 ? run + 1 : 1;
      maxStreak = Math.max(maxStreak, run);
    } else {
      run = 0;
    }
    prevDay = day;
  }

  const played = dates.length;
  return {
    played,
    wins,
    winPct: played ? Math.round((wins / played) * 100) : 0,
    currentStreak: currentStreak(history, today),
    maxStreak,
    distribution,
  };
}

export function currentStreak(history: DailyHistory, today: DateKey): number {
  if (history[today] && !history[today].won) return 0;
  // Start from today if it's been won; otherwise from yesterday (today may still be in play).
  let cursor = history[today]?.won ? today : addDays(today, -1);
  let streak = 0;
  while (history[cursor]?.won) {
    streak++;
    cursor = addDays(cursor, -1);
  }
  return streak;
}
