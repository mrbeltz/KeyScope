import curated from '../data/movies.json';
import { fetchPlexPool, hasPlex } from './plex';
import { load, save } from './storage';

/** The curated pool is IDs only, so the bundle never contains a list of answers by name. */
export const CURATED_POOL: number[] = curated as number[];

/**
 * The pool puzzles are drawn from. With Plex configured this is your library (cached, so a
 * flaky server or an offline phone still gets a puzzle); otherwise the curated list.
 */
export async function loadPool(): Promise<{ ids: number[]; source: 'plex' | 'curated' }> {
  if (!hasPlex) return { ids: CURATED_POOL, source: 'curated' };
  try {
    const ids = await fetchPlexPool();
    if (ids.length >= 7) {
      save('plex-pool', ids);
      return { ids, source: 'plex' };
    }
  } catch (err) {
    console.warn('Plex pool unavailable, falling back', err);
  }
  const cached = load<number[]>('plex-pool', []);
  if (cached.length >= 7) return { ids: cached, source: 'plex' };
  return { ids: CURATED_POOL, source: 'curated' };
}
