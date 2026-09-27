/**
 * Optional v2 source: build the puzzle pool from a Plex server's movie libraries, so
 * puzzles come from your own collection. Plex tags each item with its TMDB ID in the
 * Guid list ("tmdb://603"); that ID is all the game needs — everything else still comes
 * from TMDB.
 *
 * The browser talks to Plex directly, so the server must be reachable from the phone and,
 * when the app is served over HTTPS, over HTTPS too (a plex.direct URL works).
 */
const PLEX_URL = (import.meta.env.VITE_PLEX_URL ?? '').replace(/\/+$/, '');
const PLEX_TOKEN = import.meta.env.VITE_PLEX_TOKEN ?? '';
const PLEX_SECTION = (import.meta.env.VITE_PLEX_SECTION ?? '').trim();

export const hasPlex = Boolean(PLEX_URL && PLEX_TOKEN);

async function plexGet<T>(path: string, params: Record<string, string> = {}): Promise<T> {
  const url = new URL(PLEX_URL + path);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  url.searchParams.set('X-Plex-Token', PLEX_TOKEN);
  const res = await fetch(url, { headers: { accept: 'application/json' } });
  if (!res.ok) throw new Error(`Plex ${res.status} for ${path}`);
  return (await res.json()) as T;
}

interface PlexSections {
  MediaContainer: { Directory?: { key: string; type: string }[] };
}
interface PlexItems {
  MediaContainer: { Metadata?: { Guid?: { id: string }[] }[] };
}

export function tmdbIdsFromGuids(items: PlexItems['MediaContainer']['Metadata']): number[] {
  const ids = new Set<number>();
  for (const item of items ?? []) {
    for (const g of item.Guid ?? []) {
      const m = /^tmdb:\/\/(\d+)$/.exec(g.id);
      if (m) ids.add(Number(m[1]));
    }
  }
  // Sorted, so the pool — and therefore each day's puzzle — doesn't depend on Plex's ordering.
  return [...ids].sort((a, b) => a - b);
}

export async function fetchPlexPool(): Promise<number[]> {
  const sections = PLEX_SECTION
    ? [PLEX_SECTION]
    : ((await plexGet<PlexSections>('/library/sections')).MediaContainer.Directory ?? [])
        .filter((d) => d.type === 'movie')
        .map((d) => d.key);
  const all: number[] = [];
  for (const key of sections) {
    const res = await plexGet<PlexItems>(`/library/sections/${key}/all`, { type: '1', includeGuids: '1' });
    all.push(...tmdbIdsFromGuids(res.MediaContainer.Metadata));
  }
  return [...new Set(all)].sort((a, b) => a - b);
}
