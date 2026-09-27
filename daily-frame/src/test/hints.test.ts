import { describe, expect, it } from 'vitest';
import { buildHints, decadeLabel, redactTitle } from '../lib/hints';
import { stageFor, STAGES } from '../lib/reveal';
import { pickBackdrop } from '../lib/tmdb';
import type { MovieFacts } from '../lib/types';

describe('hints', () => {
  it('reveals decade, genre, lead, director and tagline in order', () => {
    const m: MovieFacts = {
      id: 603, title: 'The Matrix', originalTitle: 'The Matrix', year: 1999, genres: ['Action', 'Science Fiction', 'Thriller'],
      tagline: 'Welcome to the Real World.', directors: [{ id: 1, name: 'Lana Wachowski' }, { id: 2, name: 'Lilly Wachowski' }],
      leadActor: 'Keanu Reeves', collectionId: 2344, collectionName: 'The Matrix Collection', backdropPath: '/x.jpg', posterPath: null,
    };
    expect(buildHints(m).map((h) => h.value)).toEqual([
      '1990s', 'Action / Science Fiction', 'Keanu Reeves', 'Lana Wachowski & Lilly Wachowski', '“Welcome to the Real World.”',
    ]);
  });

  it('blanks the title out of a tagline', () => {
    expect(redactTitle('Just when you thought it was safe… Jaws!', 'Jaws')).toBe('Just when you thought it was safe… ▇▇▇▇!');
    expect(redactTitle('The Dark Knight rises', 'The Dark Knight Rises')).toBe('The ▇▇▇▇ ▇▇▇▇▇▇ ▇▇▇▇▇');
  });

  it('labels decades', () => {
    expect(decadeLabel(1972)).toBe('1970s');
    expect(decadeLabel(null)).toBe('Unknown');
  });
});

describe('reveal stages', () => {
  it('sharpens with each miss and is clear once the game ends', () => {
    expect(stageFor(0, false)).toBe(STAGES[0]);
    expect(stageFor(5, false)).toEqual({ blocks: null, blur: 0 });
    expect(stageFor(0, true)).toEqual({ blocks: null, blur: 0 });
    for (let i = 1; i < STAGES.length - 1; i++) {
      expect(STAGES[i].blur).toBeLessThan(STAGES[i - 1].blur);
      expect(STAGES[i].blocks!).toBeGreaterThan(STAGES[i - 1].blocks!);
    }
  });
});

describe('backdrop choice', () => {
  const img = (file_path: string, iso_639_1: string | null, vote_average = 5, width = 1920) => ({ file_path, iso_639_1, vote_average, vote_count: 10, width });

  it('only uses textless backdrops when there are any', () => {
    expect(pickBackdrop([img('/en.jpg', 'en', 9), img('/none.jpg', null, 5)], '/default.jpg')).toBe('/none.jpg');
  });

  it('falls back to the default backdrop', () => {
    expect(pickBackdrop([img('/en.jpg', 'en')], '/default.jpg')).toBe('/default.jpg');
    expect(pickBackdrop(undefined, null)).toBeNull();
  });
});
