/**
 * localStorage behind try/catch. Private windows, blocked site data and full quotas all
 * throw; the game keeps working in memory and simply doesn't persist.
 */
const PREFIX = 'daily-frame:';

export function load<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(PREFIX + key);
    return raw === null ? fallback : (JSON.parse(raw) as T);
  } catch {
    return fallback;
  }
}

export function save<T>(key: string, value: T): void {
  try {
    localStorage.setItem(PREFIX + key, JSON.stringify(value));
  } catch {
    /* storage unavailable: stay in-memory */
  }
}

export const keys = {
  game: (date: string) => `game:${date}`,
  history: 'history',
  settings: 'settings',
  seenHelp: 'seen-help',
};
