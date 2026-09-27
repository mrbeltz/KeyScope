# Daily Frame 🎬

A daily movie-guessing game, packaged as an installable Progressive Web App and laid out for the
Samsung Galaxy Z Fold: the cover screen, the unfolded inner screen, and Flex Mode.

Every player gets the same movie still each day. It starts pixelated and blurred. Each wrong guess
or skip sharpens the image and unlocks a hint. You get six guesses, and a win keeps your streak going.

---

## How it plays

| Misses | Image                     | Hint unlocked |
| ------ | ------------------------- | ------------- |
| 0      | Heavy pixelation + blur   | —             |
| 1      | Less pixelation           | Decade        |
| 2      | Lighter blur              | Genre         |
| 3      | Mild blur                 | Lead actor    |
| 4      | Near-clear                | Director      |
| 5      | Clear                     | Tagline       |

- **Multiple choice (on by default).** Pick from four options: the answer plus three decoys
  drawn from the pool and matched to the answer's era, the same four for everyone on a given
  day. A wrong pick is crossed out and costs a guess. Turn it off in Settings to type titles
  instead. Like hard mode, it's fixed for a game once you've guessed.
- **Guessing by typing.** Type into the search box and pick a movie from the suggestions. Only suggestions
  can be submitted. Guesses are matched on the TMDB ID, so any title TMDB's search knows the
  movie by is accepted: the English title, the original title, or an alternate title. A remake
  with the same name is a different ID, so it doesn't count.
- **Close.** A wrong guess gets 🟨 when it shares a director or a franchise (a TMDB collection)
  with the answer.
- **Skip.** Uses up a guess.
- **Hard mode.** No hints, only the image. Turn it on in Settings before your first guess.
- **Previous days.** The ‹ › arrows under the title step back through every earlier frame one
  day at a time, and a finished game offers "Play the day before". Tapping the date between the
  arrows opens the full archive. Replays never change your stats or streak.
- **Extras.** Haptic feedback (on by default), synth sound effects (off by default), and a
  countdown to the next puzzle.

Share text:

```
Daily Frame #142 🎬 3/6
⬛⬛🟩⬜⬜⬜
🔥 Streak: 12
```

🟩 solved · 🟨 close · ⬛ miss or skip · ⬜ unused · a trailing `*` means hard mode.

---

## Setup

Needs Node 20 or newer.

```bash
cd daily-frame
npm install
cp .env.example .env        # then paste your TMDB key into .env
npm run dev                 # http://localhost:5173
```

Other scripts:

| Command                   | What it does                                               |
| ------------------------- | ---------------------------------------------------------- |
| `npm test`                | Unit tests (Vitest)                                        |
| `npm run typecheck`       | TypeScript                                                 |
| `npm run build`           | Typecheck + production build into `dist/` (with service worker) |
| `npm run preview`         | Serve the production build locally                          |
| `npm run generate:movies` | Rebuild the puzzle pool (see below)                        |

To try it on your Fold during development, run `npm run dev -- --host` and open the printed
network URL on the phone. The phone must be on the same Wi-Fi. The service worker and the
install prompt only work over HTTPS, so test those on a deployed build.

### Get a TMDB API key

1. Create a free account at [themoviedb.org](https://www.themoviedb.org/signup).
2. Go to **Settings → API** ([themoviedb.org/settings/api](https://www.themoviedb.org/settings/api))
   and request an API key. Choose **Developer**, fill in the short form, and describe it as a
   personal, non-commercial game.
3. Copy either the **API Key** (32 hex characters) or the **API Read Access Token** (a long
   string starting with `eyJ`). Both work. Put it in `.env`:

   ```
   VITE_TMDB_API_KEY=your_key_here
   ```

4. Restart `npm run dev`. Vite only reads `.env` at startup.

> The key is baked into the client bundle, which is normal for TMDB's read-only keys. Anyone can
> read it from the deployed JS. It can only read public movie data, and you can regenerate it
> from the same settings page at any time.

---

## The daily puzzle

- **Same puzzle for everyone, all day.** The local date (`YYYY-MM-DD`) is turned into a day
  number. Days walk through a seeded shuffle of the pool, and the shuffle is reseeded by hash for
  each pass through the pool. Every player with the same pool gets the same movie on the same
  date. The order looks random, and no movie repeats until the whole pool has been used
  (`src/lib/dailySeed.ts`).
- **Rolls over at local midnight.** If you're mid-game when midnight passes, you can finish that
  game (it still counts for its own date), and a banner offers the new puzzle.
- **Puzzle #1** is `2026-01-01` (`LAUNCH_DATE` in `dailySeed.ts`).
- **The answer is pinned** into the saved game when you start it. A redeploy with a new pool
  can't swap the movie out from under a game in progress.
- **The image** is a TMDB *backdrop*, never the poster. Only backdrops with no language tag are
  used, because those are TMDB's textless images with no title burned in.
- **No answer in the DOM.** The still is drawn onto a `<canvas>`, and the pixelation and blur are
  in the pixels themselves, so there's no CSS filter to switch off in dev tools. The source
  `<img>` never enters the document. The title, locked hints and the canvas label only appear
  once the game is over. The bundled pool is only a list of numeric IDs, never titles.
  (A determined player can still read TMDB responses in the Network tab. Closing that gap would
  need a backend, which v1 doesn't have.)

### Regenerate `movies.json`

`src/data/movies.json` is the pool: about 365 TMDB IDs of popular, recognisable films across
eras and genres. It's built from `scripts/seed-movies.json`, a hand-editable list of
`{ id, title, year }`.

```bash
# Check every seed entry against TMDB, fix any wrong IDs, drop films with no textless backdrop:
npm run generate:movies

# Or build the pool from TMDB's most-voted films instead of the seed list:
npm run generate:movies -- --discover --count 365 --min-year 1970
```

The script reads `VITE_TMDB_API_KEY` from `.env` or the environment. To add a film, append
`{ "title": "Heat", "year": 1995 }` to the seed file (the `id` is optional) and rerun the
script. The shipped IDs were compiled by hand, so **run the script once with your key before
launch**. It corrects any entry whose ID doesn't match its title and year.

⚠️ Changing the pool changes which movie every *future* date maps to. Do it before launch, or
accept a one-time reshuffle. Games already in progress keep their pinned answer.

### Optional: use your Plex library (v2)

Set these in `.env` and the pool comes from your own Plex movie libraries. Plex tags each item
with its TMDB ID, and everything else still comes from TMDB:

```
VITE_PLEX_URL=https://192-168-1-20.<hash>.plex.direct:32400
VITE_PLEX_TOKEN=xxxxxxxxxxxxxxxxxxxx
VITE_PLEX_SECTION=          # optional: one library section key; blank = every movie library
```

- The phone talks to Plex directly, so the server must be reachable from wherever you play.
  A deployed HTTPS app can only call an **HTTPS** Plex URL. The `*.plex.direct` address in
  Plex's network settings works for this.
- The pool is sorted by ID and cached. If the server is unreachable, the last copy is used,
  or the curated list if there's no copy yet.
- Your Plex token ends up in the client bundle. Only deploy a Plex build somewhere private.
- Everyone sharing that build gets the same puzzles. Players with different libraries get
  different ones.

---

## Foldable layouts

| Posture                        | Detected by                               | Layout                                                                |
| ------------------------------ | ----------------------------------------- | --------------------------------------------------------------------- |
| Folded / cover screen (<600px) | default                                   | One scrolling column: frame, guess box, hints, history                |
| Unfolded inner screen (≥600px) | `@media (min-width: 600px)`               | Two panes: frame and guess history on the left, guess box and hints on the right; each pane scrolls on its own |
| Flex Mode (half-folded)        | `@media (vertical-viewport-segments: 2)`  | Frame in the top segment, controls in the bottom segment, rows sized from `env(viewport-segment-*)` so nothing sits on the hinge. Dialogs open in the bottom segment |
| Book-style dual screen         | `@media (horizontal-viewport-segments: 2)` | One pane per segment, split at the hinge                              |

Browsers without the Viewport Segments API never match those queries and use the normal layouts.
Safe-area insets are respected throughout, and every tap target is at least 44px.

To test Flex Mode on a desktop, use Chrome DevTools: open the device toolbar, pick a foldable
(or *Edit… → add a custom device*), and use the posture control to set it to *Folded*.

---

## Deploy

The build is a static site in `dist/`. SPA routing isn't needed, but both configs rewrite unknown
paths to `index.html` anyway.

### Netlify

1. Push the repo to GitHub, then in Netlify choose **Add new site → Import an existing project**.
2. Set **Base directory** to `daily-frame`. `netlify.toml` supplies the build command
   (`npm run build`) and the publish directory (`dist`).
3. Under **Site configuration → Environment variables**, add `VITE_TMDB_API_KEY`.
4. Deploy. Environment variables are read at build time, so trigger a new deploy after changing one.

Or from the CLI: `cd daily-frame && npx netlify deploy --build --prod`.

### Vercel

1. **Add New → Project**, import the repo, and set **Root Directory** to `daily-frame`. The Vite
   preset is detected, and `vercel.json` supplies the headers.
2. Add `VITE_TMDB_API_KEY` under **Settings → Environment Variables**, then redeploy.

Or from the CLI: `cd daily-frame && npx vercel --prod`.

Both configs serve `sw.js` with `Cache-Control: no-cache`, so new versions roll out promptly.
The service worker is set to `autoUpdate`.

## Android APK

The repo builds a real Android app too. It wraps the same web build with
[Capacitor](https://capacitorjs.com) (`capacitor.config.ts`, `android/`).

**Download it:** every push that touches `daily-frame/` runs the **Daily Frame APK** workflow
(`.github/workflows/daily-frame-apk.yml`). The workflow attaches `daily-frame.apk` to the
**Daily Frame (Android)** release, tagged `daily-frame-latest`, on the repo's Releases page.

1. Open the Releases page on your phone and download `daily-frame.apk`.
2. Open it. The first time, Android asks you to allow installs from your browser: tap
   **Settings → Allow from this source**, then go back and tap **Install**.
3. The first time the app opens, paste your TMDB key (see above). It stays on the phone.
   To bake a key into the APK instead, add a repository secret named `TMDB_API_KEY`
   (**Settings → Secrets and variables → Actions**). Only do that if the repo is private,
   since anyone who downloads the APK can read a key that's baked into it.

Builds are signed with a fixed debug key (`android/app/debug.keystore`, committed on
purpose), so a new APK installs over the old one and keeps your streak and stats. It's a
debug build meant for your own phones, not the Play Store.

To build it yourself you need Android Studio (or JDK 21 and the Android SDK):

```bash
npm run build && npx cap sync android
cd android && ./gradlew assembleDebug   # → android/app/build/outputs/apk/debug/app-debug.apk
```

## Install on an Android home screen

1. Open the deployed HTTPS URL in **Chrome** on the Fold (Samsung Internet works too).
2. Chrome shows an **Install app** banner. If it doesn't, tap **⋮ → Add to home screen → Install**.
   In Samsung Internet, tap **☰ → Add page to → Home screen**.
3. Launch **Daily Frame** from the home screen. It opens full-screen with its own icon and
   works offline for anything already loaded.

What works offline after the first visit: the whole app shell (HTML, JS, CSS, icons and the movie pool) is
precached. Google Fonts, and the TMDB responses and images you've already seen, are cached at
runtime, so a puzzle you opened today still plays on a plane. A *new* day's image needs a
connection.

---

## Project layout

```
daily-frame/
├─ index.html
├─ vite.config.ts            Vite + React + Tailwind + PWA (manifest, precache, runtime caching)
├─ netlify.toml, vercel.json
├─ .env.example
├─ public/                   favicon.svg + PWA icons (192, 512, maskable, apple-touch)
├─ scripts/
│  ├─ generate-movies.mjs    builds src/data/movies.json from TMDB
│  └─ seed-movies.json       hand-curated { id, title, year } list
└─ src/
   ├─ main.tsx, App.tsx, index.css
   ├─ data/movies.json       the puzzle pool (IDs only)
   ├─ lib/                   pure logic, no React
   │  ├─ dailySeed.ts        date → puzzle index, puzzle numbers
   │  ├─ dates.ts            local date keys, midnight countdown
   │  ├─ matching.ts         ID matching, "close" detection
   │  ├─ stats.ts            streaks, win %, distribution
   │  ├─ share.ts            share text, Web Share / clipboard
   │  ├─ reveal.ts, hints.ts reveal stages and hint text
   │  ├─ tmdb.ts, plex.ts, pool.ts   data sources
   │  ├─ storage.ts          try/catch localStorage
   │  └─ feedback.ts         haptics + WebAudio sound cues
   ├─ hooks/                 useGame, useSettings, usePool, useToday
   ├─ components/            FrameCanvas, GuessInput, GuessHistory, HintList, modals…
   └─ test/                  Vitest unit tests
```

## Tests

`npm test` covers:

- **Daily seed:** determinism, range, no repeats within a cycle, rollover at local midnight,
  DST-safe day counting, and puzzle numbers.
- **Answer matching:** ID matching, alternate titles, remakes, and "close" by director (including
  co-directors) or franchise.
- **Streaks:** consecutive wins, a missed day or a loss breaking the streak, and month and year
  boundaries. Also max streak, win %, distribution, and a result never being counted twice.
- **Share text**, hints and title redaction, reveal stages, backdrop choice, and game progression.

---

Movie data and images come from [TMDB](https://www.themoviedb.org). This product uses the TMDB
API but is not endorsed or certified by TMDB.
