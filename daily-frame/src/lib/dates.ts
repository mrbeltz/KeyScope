/** Dates are handled as local-calendar keys ("YYYY-MM-DD") so the puzzle rolls over at local midnight. */

export type DateKey = string;

const pad = (n: number) => String(n).padStart(2, '0');

export function localDateKey(date: Date = new Date()): DateKey {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function isDateKey(value: string): value is DateKey {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && dateKeyToDayNumber(value) !== null;
}

/**
 * Whole days since 1970-01-01 for a calendar date. Computed in UTC so daylight-saving
 * shifts can never make two consecutive dates 0 or 2 days apart.
 */
export function dateKeyToDayNumber(key: DateKey): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(key);
  if (!m) return null;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const ms = Date.UTC(y, mo - 1, d);
  const check = new Date(ms);
  if (check.getUTCFullYear() !== y || check.getUTCMonth() !== mo - 1 || check.getUTCDate() !== d) return null;
  return Math.round(ms / 86_400_000);
}

export function dayNumberToDateKey(day: number): DateKey {
  const d = new Date(day * 86_400_000);
  return `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
}

export function addDays(key: DateKey, delta: number): DateKey {
  const day = dateKeyToDayNumber(key);
  if (day === null) throw new Error(`Bad date key: ${key}`);
  return dayNumberToDateKey(day + delta);
}

export function daysBetween(from: DateKey, to: DateKey): number {
  const a = dateKeyToDayNumber(from);
  const b = dateKeyToDayNumber(to);
  if (a === null || b === null) throw new Error(`Bad date key: ${from} / ${to}`);
  return b - a;
}

/** Milliseconds until the next local midnight. */
export function msUntilNextLocalMidnight(now: Date = new Date()): number {
  const next = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1, 0, 0, 0, 0);
  return Math.max(0, next.getTime() - now.getTime());
}

export function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return `${pad(h)}:${pad(m)}:${pad(s)}`;
}
