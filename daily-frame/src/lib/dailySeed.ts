import { type DateKey, dateKeyToDayNumber, dayNumberToDateKey } from './dates';

/** Puzzle #1 is this date. Everything before it is out of range for the archive. */
export const LAUNCH_DATE: DateKey = '2026-01-01';

/** FNV-1a, 32-bit. Small, fast and identical on every JS engine. */
export function hashString(input: string): number {
  let h = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    h ^= input.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return h >>> 0;
}

/** mulberry32: a seeded PRNG returning floats in [0, 1). */
export function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Deterministic Fisher–Yates permutation of 0..n-1. */
export function seededPermutation(n: number, seed: number): number[] {
  const out = Array.from({ length: n }, (_, i) => i);
  const rand = mulberry32(seed);
  for (let i = n - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [out[i], out[j]] = [out[j], out[i]];
  }
  return out;
}

/** 1-based puzzle number for a date ("Daily Frame #142"). Dates before launch return <= 0. */
export function puzzleNumber(date: DateKey): number {
  const day = dateKeyToDayNumber(date);
  const launch = dateKeyToDayNumber(LAUNCH_DATE)!;
  if (day === null) throw new Error(`Bad date key: ${date}`);
  return day - launch + 1;
}

export function dateForPuzzleNumber(n: number): DateKey {
  return dayNumberToDateKey(dateKeyToDayNumber(LAUNCH_DATE)! + n - 1);
}

/**
 * Maps a local date to an index into a pool of `poolSize` movies.
 *
 * The date is hashed into a seed for a shuffle of the pool, and days walk through that
 * shuffle in order. So every player gets the same movie on the same date, the order looks
 * random, and no movie repeats until the whole pool has been used. When a cycle ends the
 * next one is reshuffled with a fresh seed.
 */
export function dailyIndex(date: DateKey, poolSize: number): number {
  if (!Number.isInteger(poolSize) || poolSize <= 0) throw new Error('poolSize must be a positive integer');
  const day = dateKeyToDayNumber(date);
  if (day === null) throw new Error(`Bad date key: ${date}`);
  const cycle = Math.floor(day / poolSize);
  const position = day - cycle * poolSize;
  const order = seededPermutation(poolSize, hashString(`daily-frame:${poolSize}:${cycle}`));
  return order[position];
}
