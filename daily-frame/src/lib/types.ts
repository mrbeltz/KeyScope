/** Everything about a movie the game needs, normalised from TMDB. */
export interface MovieFacts {
  id: number;
  title: string;
  originalTitle: string;
  year: number | null;
  genres: string[];
  tagline: string;
  directors: { id: number; name: string }[];
  leadActor: string | null;
  collectionId: number | null;
  collectionName: string | null;
  /** TMDB file path of a backdrop with no language (i.e. no title text), e.g. "/abc.jpg". */
  backdropPath: string | null;
  posterPath: string | null;
}

/** A search suggestion. Only these can be submitted as guesses. */
export interface MovieSuggestion {
  id: number;
  title: string;
  originalTitle: string;
  year: number | null;
}

export type GuessResult = 'correct' | 'close' | 'wrong' | 'skip';

export interface GuessRecord {
  /** null for a skip. */
  id: number | null;
  title: string;
  year: number | null;
  result: GuessResult;
  /** Why a guess was close, e.g. "Same director". */
  reason?: string;
}

export type GameStatus = 'playing' | 'won' | 'lost';

export interface GameRecord {
  date: string;
  /** "daily" games count towards stats; "archive" replays never do. */
  mode: 'daily' | 'archive';
  /**
   * Pinned when the game starts, so a pool change (a redeploy, a Plex library update) can't
   * swap the movie out from under a game in progress.
   */
  answerId: number;
  hardMode: boolean;
  /** Pick from four options instead of searching. Missing on games saved before it existed. */
  multipleChoice?: boolean;
  guesses: GuessRecord[];
  status: GameStatus;
}

export const MAX_GUESSES = 6;
