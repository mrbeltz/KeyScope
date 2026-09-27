import type { MovieFacts } from './types';

export interface Hint {
  label: string;
  value: string;
}

const STOPWORDS = new Set(['the', 'and', 'of', 'a', 'an', 'in', 'on', 'to', 'for', 'with', 'part']);

/** Blanks out the title's words inside a hint (taglines sometimes quote the title). */
export function redactTitle(text: string, title: string): string {
  const all = title.split(/[^\p{L}\p{N}']+/u).filter(Boolean);
  const words = all.length === 1 ? all : all.filter((w) => w.length >= 3 && !STOPWORDS.has(w.toLowerCase()));
  let out = text;
  for (const w of words) {
    const escaped = w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    out = out.replace(new RegExp(`(?<![\\p{L}\\p{N}])${escaped}(?![\\p{L}\\p{N}])`, 'giu'), '▇'.repeat(Math.min(w.length, 6)));
  }
  return out;
}

export function decadeLabel(year: number | null): string {
  if (!year) return 'Unknown';
  return `${Math.floor(year / 10) * 10}s`;
}

/**
 * The five hints, in reveal order. Hint i unlocks after i+1 misses. Missing data falls back
 * to something still useful rather than a blank.
 */
export function buildHints(m: MovieFacts): Hint[] {
  const tagline = m.tagline.trim()
    ? `“${redactTitle(m.tagline.trim(), m.title)}”`
    : m.year
      ? `No tagline — it came out in ${m.year}`
      : 'No tagline on record';
  return [
    { label: 'Decade', value: decadeLabel(m.year) },
    { label: 'Genre', value: m.genres.slice(0, 2).join(' / ') || 'Unlisted' },
    { label: 'Lead actor', value: m.leadActor ?? 'Unlisted' },
    { label: 'Director', value: m.directors.map((d) => d.name).join(' & ') || 'Unlisted' },
    { label: 'Tagline', value: tagline },
  ];
}
