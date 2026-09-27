#!/usr/bin/env node
/**
 * Builds src/data/movies.json — the pool of TMDB IDs the daily puzzle is drawn from.
 *
 *   npm run generate:movies                 verify + repair scripts/seed-movies.json
 *   npm run generate:movies -- --discover   rebuild the pool from TMDB's most-voted films
 *   npm run generate:movies -- --discover --count 500 --min-year 1970
 *
 * Seed mode checks every seed entry against TMDB. An ID whose title/year doesn't match is
 * re-resolved by searching for the title and year, so the seed list can be edited by hand
 * with just a title and year (the "id" field is optional). Discover mode ignores the seed
 * and takes the top films by vote count.
 *
 * Either way a film only makes the pool if it has a textless backdrop (no language tag, so
 * no title burned into the image) — posters and title-card stills would give the answer away.
 *
 * Reads VITE_TMDB_API_KEY from the environment or from .env / .env.local.
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SEED = resolve(root, 'scripts/seed-movies.json');
const OUT = resolve(root, 'src/data/movies.json');

const args = process.argv.slice(2);
const flag = (name) => args.includes(`--${name}`);
const opt = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : fallback;
};

function readKey() {
  if (process.env.VITE_TMDB_API_KEY) return process.env.VITE_TMDB_API_KEY.trim();
  for (const f of ['.env.local', '.env']) {
    const p = resolve(root, f);
    if (!existsSync(p)) continue;
    const m = /^VITE_TMDB_API_KEY=(.*)$/m.exec(readFileSync(p, 'utf8'));
    if (m && m[1].trim()) return m[1].trim().replace(/^["']|["']$/g, '');
  }
  return '';
}

const KEY = readKey();
if (!KEY) {
  console.error('Set VITE_TMDB_API_KEY (in .env or the environment) first. See README → "Get a TMDB API key".');
  process.exit(1);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function tmdb(path, params = {}) {
  const url = new URL(`https://api.themoviedb.org/3${path}`);
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, String(v));
  const headers = { accept: 'application/json' };
  if (KEY.startsWith('eyJ')) headers.authorization = `Bearer ${KEY}`;
  else url.searchParams.set('api_key', KEY);
  for (let attempt = 0; attempt < 5; attempt++) {
    const res = await fetch(url, { headers });
    if (res.status === 429) {
      await sleep(1000 * (attempt + 1));
      continue;
    }
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`TMDB ${res.status} for ${path}`);
    return res.json();
  }
  throw new Error(`TMDB kept rate-limiting ${path}`);
}

const yearOf = (d) => (d && /^\d{4}/.test(d) ? Number(d.slice(0, 4)) : null);
const norm = (s) => s.toLowerCase().normalize('NFKD').replace(/[^\p{L}\p{N}]+/gu, '');

async function hasTextlessBackdrop(id) {
  const images = await tmdb(`/movie/${id}/images`, { include_image_language: 'null,xx' });
  return (images?.backdrops ?? []).some((b) => b.iso_639_1 === null || b.iso_639_1 === 'xx');
}

/** Run `fn` over `items` with a little concurrency, staying well inside TMDB's rate limit. */
async function mapLimit(items, limit, fn) {
  const out = new Array(items.length);
  let next = 0;
  await Promise.all(
    Array.from({ length: limit }, async () => {
      while (next < items.length) {
        const i = next++;
        out[i] = await fn(items[i], i);
      }
    }),
  );
  return out;
}

async function resolveSeed(entry) {
  if (entry.id) {
    const d = await tmdb(`/movie/${entry.id}`);
    if (d && norm(d.title) === norm(entry.title) && Math.abs((yearOf(d.release_date) ?? 0) - entry.year) <= 1) {
      return { id: d.id, title: d.title, year: yearOf(d.release_date) };
    }
  }
  const search = await tmdb('/search/movie', { query: entry.title, primary_release_year: entry.year });
  let hit = search?.results?.[0];
  if (!hit) hit = (await tmdb('/search/movie', { query: entry.title }))?.results?.[0];
  if (!hit) return null;
  return { id: hit.id, title: hit.title, year: yearOf(hit.release_date), fixed: entry.id !== hit.id };
}

async function fromSeed() {
  const seed = JSON.parse(readFileSync(SEED, 'utf8'));
  console.log(`Checking ${seed.length} seed movies against TMDB…`);
  const resolved = await mapLimit(seed, 4, async (entry) => {
    const r = await resolveSeed(entry);
    if (!r) {
      console.warn(`  ✗ not found: ${entry.title} (${entry.year})`);
      return null;
    }
    if (r.fixed) console.log(`  ↻ ${entry.title} (${entry.year}): id ${entry.id ?? '—'} → ${r.id}`);
    if (!(await hasTextlessBackdrop(r.id))) {
      console.warn(`  ✗ no textless backdrop: ${r.title}`);
      return null;
    }
    return r;
  });
  const kept = dedupe(resolved.filter(Boolean));
  writeFileSync(SEED, `[\n${kept.map((m) => '  ' + JSON.stringify({ id: m.id, title: m.title, year: m.year })).join(',\n')}\n]\n`);
  return kept;
}

async function fromDiscover() {
  const count = Number(opt('count', 365));
  const minYear = Number(opt('min-year', 1930));
  console.log(`Discovering the ${count} most-voted films since ${minYear}…`);
  const kept = [];
  for (let page = 1; kept.length < count && page <= 100; page++) {
    const res = await tmdb('/discover/movie', {
      sort_by: 'vote_count.desc',
      include_adult: false,
      'primary_release_date.gte': `${minYear}-01-01`,
      'vote_count.gte': 2000,
      page,
    });
    if (!res?.results?.length) break;
    const checked = await mapLimit(res.results, 4, async (r) =>
      (await hasTextlessBackdrop(r.id)) ? { id: r.id, title: r.title, year: yearOf(r.release_date) } : null,
    );
    kept.push(...checked.filter(Boolean));
  }
  return dedupe(kept).slice(0, count);
}

function dedupe(list) {
  const seen = new Set();
  return list.filter((m) => (seen.has(m.id) ? false : (seen.add(m.id), true)));
}

const movies = flag('discover') ? await fromDiscover() : await fromSeed();
const ids = movies.map((m) => m.id);
const rows = [];
for (let i = 0; i < ids.length; i += 10) rows.push('  ' + ids.slice(i, i + 10).join(', '));
writeFileSync(OUT, `[\n${rows.join(',\n')}\n]\n`);
console.log(`\nWrote ${ids.length} IDs to src/data/movies.json`);
if (ids.length < 365) console.log('Fewer than 365: some days of the year will repeat sooner. Add titles to the seed to top it up.');
console.log('Note: changing the pool changes which movie every future date maps to. Do it before launch, or accept a reshuffle.');
