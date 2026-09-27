import type { GuessRecord, MovieFacts, MovieSuggestion } from './types';

/**
 * Answers are matched on TMDB ID, never on text. Whatever title the player picked from the
 * suggestions — the English release title, the original-language title, an alternate
 * title TMDB's search matched on — resolves to one ID, and that is what gets compared.
 */
export function isCorrectGuess(guessId: number, answerId: number): boolean {
  return guessId === answerId;
}

export interface Closeness {
  close: boolean;
  reason?: string;
}

/** A wrong guess is "close" when it shares a director or a franchise with the answer. */
export function closeness(
  guess: Pick<MovieFacts, 'directors' | 'collectionId'>,
  answer: Pick<MovieFacts, 'directors' | 'collectionId'>,
): Closeness {
  if (guess.collectionId !== null && guess.collectionId === answer.collectionId) {
    return { close: true, reason: 'Same franchise' };
  }
  const answerDirectors = new Set(answer.directors.map((d) => d.id));
  const shared = guess.directors.find((d) => answerDirectors.has(d.id));
  if (shared) return { close: true, reason: 'Same director' };
  return { close: false };
}

/**
 * Scores a guess. `guessFacts` may be null when the guessed movie's details could not be
 * fetched (offline, say); the guess is then judged on ID alone and can't be "close".
 */
export function evaluateGuess(
  guess: MovieSuggestion,
  guessFacts: MovieFacts | null,
  answer: MovieFacts,
): GuessRecord {
  const base = { id: guess.id, title: guess.title, year: guess.year };
  if (isCorrectGuess(guess.id, answer.id)) return { ...base, result: 'correct' };
  if (guessFacts) {
    const c = closeness(guessFacts, answer);
    if (c.close) return { ...base, result: 'close', reason: c.reason };
  }
  return { ...base, result: 'wrong' };
}

export function skipRecord(): GuessRecord {
  return { id: null, title: 'Skipped', year: null, result: 'skip' };
}

export function hasAlreadyGuessed(guesses: GuessRecord[], id: number): boolean {
  return guesses.some((g) => g.id === id);
}

