import { describe, expect, it } from 'vitest';
import { arrangeChoices, decoyCandidates, pickDecoys } from '../lib/choices';
import type { MovieFacts } from '../lib/types';

const m = (id: number, year: number | null): MovieFacts => ({
  id, title: `M${id}`, originalTitle: `M${id}`, year, genres: [], tagline: '', directors: [], leadActor: null,
  collectionId: null, collectionName: null, backdropPath: null, posterPath: null,
});
const pool = Array.from({ length: 50 }, (_, i) => i + 1);

describe('multiple choice', () => {
  it('draws the same decoy candidates for everyone on a date, never the answer', () => {
    const a = decoyCandidates('2026-09-27', pool, 7);
    expect(decoyCandidates('2026-09-27', pool, 7)).toEqual(a);
    expect(a).toHaveLength(8);
    expect(a).not.toContain(7);
    expect(new Set(a).size).toBe(8);
    expect(decoyCandidates('2026-09-28', pool, 7)).not.toEqual(a);
  });

  it('copes with a tiny pool', () => {
    expect(decoyCandidates('2026-09-27', [1, 2, 3], 2).sort()).toEqual([1, 3]);
  });

  it('prefers decoys from the answer’s era', () => {
    const picked = pickDecoys(m(1, 1994), [m(2, 1950), m(3, 1996), m(4, 2020), m(5, 1990), m(6, 1993)]);
    expect(picked.map((d) => d.id)).toEqual([6, 3, 5]);
  });

  it('includes the answer exactly once, in a stable seeded order', () => {
    const answer = m(1, 2000);
    const decoys = [m(2, 2001), m(3, 1999), m(4, 2002)];
    const a = arrangeChoices('2026-09-27', answer, decoys);
    expect(a.map((c) => c.id).sort()).toEqual([1, 2, 3, 4]);
    expect(arrangeChoices('2026-09-27', answer, decoys)).toEqual(a);
  });
});
