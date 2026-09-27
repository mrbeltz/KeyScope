import { describe, expect, it } from 'vitest';
import { closeness, evaluateGuess, hasAlreadyGuessed, isCorrectGuess, skipRecord } from '../lib/matching';
import type { MovieFacts } from '../lib/types';

const movie = (over: Partial<MovieFacts>): MovieFacts => ({
  id: 1,
  title: 'Untitled',
  originalTitle: 'Untitled',
  year: 2000,
  genres: [],
  tagline: '',
  directors: [],
  leadActor: null,
  collectionId: null,
  collectionName: null,
  backdropPath: null,
  posterPath: null,
  ...over,
});

const nolan = { id: 525, name: 'Christopher Nolan' };
const darkKnight = movie({ id: 155, title: 'The Dark Knight', directors: [nolan], collectionId: 263, collectionName: 'The Dark Knight Collection' });

describe('answer matching', () => {
  it('matches on TMDB ID, not the title text', () => {
    expect(isCorrectGuess(155, 155)).toBe(true);
    expect(isCorrectGuess(272, 155)).toBe(false);
  });

  it('accepts an alternate or original-language title that resolves to the same ID', () => {
    // "Léon" and "Léon: The Professional" are the same TMDB movie.
    const leon = movie({ id: 101, title: 'Léon: The Professional' });
    const r = evaluateGuess({ id: 101, title: 'Léon', originalTitle: 'Léon', year: 1994 }, null, leon);
    expect(r.result).toBe('correct');
  });

  it('rejects a different movie with an identical title (remakes)', () => {
    const lionKing1994 = movie({ id: 8587, title: 'The Lion King', year: 1994 });
    const r = evaluateGuess({ id: 420818, title: 'The Lion King', originalTitle: 'The Lion King', year: 2019 }, null, lionKing1994);
    expect(r.result).toBe('wrong');
  });

  it('marks a same-franchise guess as close', () => {
    const begins = movie({ id: 272, directors: [nolan], collectionId: 263 });
    expect(closeness(begins, darkKnight)).toEqual({ close: true, reason: 'Same franchise' });
  });

  it('marks a same-director guess as close', () => {
    const inception = movie({ id: 27205, directors: [nolan] });
    const r = evaluateGuess({ id: 27205, title: 'Inception', originalTitle: 'Inception', year: 2010 }, inception, darkKnight);
    expect(r).toMatchObject({ result: 'close', reason: 'Same director' });
  });

  it('handles co-directors', () => {
    const matrix = movie({ id: 603, directors: [{ id: 9339, name: 'Lilly Wachowski' }, { id: 9340, name: 'Lana Wachowski' }] });
    const cloudAtlas = movie({ id: 83542, directors: [{ id: 9340, name: 'Lana Wachowski' }, { id: 1071, name: 'Tom Tykwer' }] });
    expect(closeness(cloudAtlas, matrix).close).toBe(true);
  });

  it('is plain wrong with nothing shared, or when the guess details are unavailable', () => {
    const titanic = movie({ id: 597, directors: [{ id: 2710, name: 'James Cameron' }] });
    expect(evaluateGuess({ id: 597, title: 'Titanic', originalTitle: 'Titanic', year: 1997 }, titanic, darkKnight).result).toBe('wrong');
    expect(evaluateGuess({ id: 272, title: 'Batman Begins', originalTitle: 'Batman Begins', year: 2005 }, null, darkKnight).result).toBe('wrong');
  });

  it('does not treat two movies with no collection as the same franchise', () => {
    expect(closeness(movie({ collectionId: null }), movie({ collectionId: null })).close).toBe(false);
  });

  it('records skips and detects repeat guesses', () => {
    expect(skipRecord()).toMatchObject({ id: null, result: 'skip' });
    const guesses = [skipRecord(), { id: 5, title: 'X', year: 2000, result: 'wrong' as const }];
    expect(hasAlreadyGuessed(guesses, 5)).toBe(true);
    expect(hasAlreadyGuessed(guesses, 6)).toBe(false);
  });
});
