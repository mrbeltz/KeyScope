import { hashString, mulberry32, seededPermutation } from './dailySeed';
import type { MovieFacts, MovieSuggestion } from './types';

export const CHOICE_COUNT = 4;
/** Decoys are picked from this many seeded candidates, so they can be matched to the answer's era. */
export const DECOY_CANDIDATES = 8;

/**
 * Candidate decoys for a date: a seeded sample of the pool, never the answer. Seeded by the
 * date, so everyone playing that day gets the same options.
 */
export function decoyCandidates(date: string, pool: number[], answerId: number, count = DECOY_CANDIDATES): number[] {
  const others = pool.filter((id) => id !== answerId);
  const order = seededPermutation(others.length, hashString(`choices:${date}`));
  return order.slice(0, Math.min(count, others.length)).map((i) => others[i]);
}

/**
 * The decoys closest in release year to the answer. An option from the wrong decade would be
 * a giveaway once the decade hint unlocks.
 */
export function pickDecoys(answer: Pick<MovieFacts, 'year'>, candidates: MovieFacts[], k = CHOICE_COUNT - 1): MovieFacts[] {
  const year = answer.year ?? 2000;
  return [...candidates]
    .sort((a, b) => Math.abs((a.year ?? year) - year) - Math.abs((b.year ?? year) - year) || a.id - b.id)
    .slice(0, k);
}

/** The answer and its decoys in a seeded order, so the right answer isn't always first. */
export function arrangeChoices(date: string, answer: MovieFacts, decoys: MovieFacts[]): MovieSuggestion[] {
  const all = [answer, ...decoys].map((m) => ({ id: m.id, title: m.title, originalTitle: m.originalTitle, year: m.year }));
  const rand = mulberry32(hashString(`order:${date}`));
  for (let i = all.length - 1; i > 0; i--) {
    const j = Math.floor(rand() * (i + 1));
    [all[i], all[j]] = [all[j], all[i]];
  }
  return all;
}
