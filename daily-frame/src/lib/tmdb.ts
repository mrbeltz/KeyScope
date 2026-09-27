import { keys, load, save } from './storage';
import type { MovieFacts, MovieSuggestion } from './types';

const API = 'https://api.themoviedb.org/3';
const IMAGES = 'https://image.tmdb.org/t/p';

const BUILD_KEY = (import.meta.env.VITE_TMDB_API_KEY ?? '').trim();

/**
 * The key baked in at build time wins; otherwise one the player pasted into the app, which
 * is how the APK works without the key ever being in the repo or the release.
 */
function currentKey(): string {
  return BUILD_KEY || load<string>(keys.tmdbKey, '').trim();
}

export const hasTmdbKey = (): boolean => currentKey().length > 0;
export const tmdbKeySource = (): 'build' | 'device' | null => (BUILD_KEY ? 'build' : currentKey() ? 'device' : null);

/** Checks a pasted key against TMDB, and keeps it on the device if it works. */
export async function saveTmdbKey(key: string): Promise<'ok' | 'invalid' | 'offline'> {
  const k = key.trim();
  try {
    await request('/configuration', {}, undefined, k);
  } catch (err) {
    return err instanceof TmdbError && (err.status === 401 || err.status === 404) ? 'invalid' : 'offline';
  }
  save(keys.tmdbKey, k);
  return 'ok';
}

export function forgetTmdbKey(): void {
  save(keys.tmdbKey, '');
}

export class TmdbError extends Error {
  constructor(
    message: string,
    readonly status?: number,
  ) {
    super(message);
  }
}

/** Accepts either a v3 API key (query param) or a v4 read access token (Bearer header). */
function get<T>(path: string, params: Record<string, string> = {}, signal?: AbortSignal): Promise<T> {
  return request<T>(path, params, signal, currentKey());
}

async function request<T>(path: string, params: Record<string, string>, signal: AbortSignal | undefined, KEY: string): Promise<T> {
  if (!KEY) throw new TmdbError('No TMDB API key configured');
  const url = new URL(API + path);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  const headers: HeadersInit = { accept: 'application/json' };
  if (KEY.startsWith('eyJ')) headers.authorization = `Bearer ${KEY}`;
  else url.searchParams.set('api_key', KEY);
  const res = await fetch(url, { headers, signal });
  if (!res.ok) throw new TmdbError(`TMDB ${res.status} for ${path}`, res.status);
  return (await res.json()) as T;
}

export function imageUrl(path: string, size: 'w300' | 'w780' | 'w1280' | 'original' | 'w92' | 'w185' = 'w1280'): string {
  return `${IMAGES}/${size}${path}`;
}

const yearOf = (date?: string | null) => (date && /^\d{4}/.test(date) ? Number(date.slice(0, 4)) : null);

interface RawImage {
  file_path: string;
  iso_639_1: string | null;
  vote_average: number;
  vote_count: number;
  width: number;
}

interface RawDetails {
  id: number;
  title: string;
  original_title: string;
  release_date?: string;
  genres?: { id: number; name: string }[];
  tagline?: string | null;
  backdrop_path?: string | null;
  poster_path?: string | null;
  belongs_to_collection?: { id: number; name: string } | null;
  credits?: {
    cast?: { name: string; order: number }[];
    crew?: { id: number; name: string; job: string }[];
  };
  images?: { backdrops?: RawImage[] };
}

/**
 * Picks the backdrop to use as the puzzle. Backdrops with no language tag are the textless
 * ones — no title treatment burned in — so only those are considered when any exist. Among
 * them, prefer wide, well-voted images; ties break on file path so the pick is stable.
 */
export function pickBackdrop(backdrops: RawImage[] | undefined, fallback: string | null | undefined): string | null {
  const textless = (backdrops ?? []).filter((b) => b.iso_639_1 === null || b.iso_639_1 === 'xx');
  if (textless.length === 0) return fallback ?? null;
  const score = (b: RawImage) => (b.width >= 1280 ? 1 : 0) * 100 + b.vote_average + Math.min(b.vote_count, 20) / 20;
  const sorted = [...textless].sort((a, b) => score(b) - score(a) || a.file_path.localeCompare(b.file_path));
  return sorted[0].file_path;
}

export function toFacts(d: RawDetails): MovieFacts {
  const cast = [...(d.credits?.cast ?? [])].sort((a, b) => a.order - b.order);
  const directors = (d.credits?.crew ?? []).filter((c) => c.job === 'Director').map((c) => ({ id: c.id, name: c.name }));
  return {
    id: d.id,
    title: d.title,
    originalTitle: d.original_title,
    year: yearOf(d.release_date),
    genres: (d.genres ?? []).map((g) => g.name),
    tagline: d.tagline ?? '',
    directors,
    leadActor: cast[0]?.name ?? null,
    collectionId: d.belongs_to_collection?.id ?? null,
    collectionName: d.belongs_to_collection?.name ?? null,
    backdropPath: pickBackdrop(d.images?.backdrops, d.backdrop_path),
    posterPath: d.poster_path ?? null,
  };
}

/** Full facts for a movie: details, credits and its textless backdrops in one request. */
export async function fetchMovie(id: number, signal?: AbortSignal): Promise<MovieFacts> {
  const raw = await get<RawDetails>(
    `/movie/${id}`,
    { append_to_response: 'credits,images', include_image_language: 'null,xx', language: 'en-US' },
    signal,
  );
  return toFacts(raw);
}

interface RawSearch {
  results: { id: number; title: string; original_title: string; release_date?: string; popularity: number; video?: boolean }[];
}

/**
 * Title search for the guess box. TMDB's search already matches alternate and translated
 * titles, which is how "Se7en" or "Léon" find their movies; the guess itself is the ID.
 */
export async function searchMovies(query: string, signal?: AbortSignal): Promise<MovieSuggestion[]> {
  const q = query.trim();
  if (q.length < 2) return [];
  const raw = await get<RawSearch>('/search/movie', { query: q, include_adult: 'false', language: 'en-US' }, signal);
  return raw.results
    .filter((r) => !r.video)
    .slice(0, 8)
    .map((r) => ({ id: r.id, title: r.title, originalTitle: r.original_title, year: yearOf(r.release_date) }));
}
