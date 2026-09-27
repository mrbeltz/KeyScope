import { useCallback, useEffect, useRef, useState } from 'react';
import { dailyIndex } from '../lib/dailySeed';
import { evaluateGuess, hasAlreadyGuessed, skipRecord } from '../lib/matching';
import { keys, load, save } from '../lib/storage';
import { fetchMovie } from '../lib/tmdb';
import type { GameRecord, GuessRecord, MovieFacts, MovieSuggestion } from '../lib/types';
import { MAX_GUESSES } from '../lib/types';

interface Options {
  date: string;
  mode: 'daily' | 'archive';
  pool: number[] | null;
  defaultHardMode: boolean;
  /** Fired once per guess, after it's been scored and saved. */
  onGuess?: (game: GameRecord, guess: GuessRecord) => void;
}

export function newGame(date: string, mode: GameRecord['mode'], pool: number[], hardMode: boolean): GameRecord {
  return { date, mode, hardMode, answerId: pool[dailyIndex(date, pool.length)], guesses: [], status: 'playing' };
}

export function applyGuess(game: GameRecord, guess: GuessRecord): GameRecord {
  if (game.status !== 'playing') return game;
  const guesses = [...game.guesses, guess];
  const status = guess.result === 'correct' ? 'won' : guesses.length >= MAX_GUESSES ? 'lost' : 'playing';
  return { ...game, guesses, status };
}

function withTimeout<T>(p: Promise<T>, ms: number): Promise<T> {
  return Promise.race([p, new Promise<T>((_, reject) => setTimeout(() => reject(new Error('timeout')), ms))]);
}

export function useGame({ date, mode, pool, defaultHardMode, onGuess }: Options) {
  const [game, setGame] = useState<GameRecord | null>(null);
  const [answer, setAnswer] = useState<MovieFacts | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const busyRef = useRef(false);
  const onGuessRef = useRef(onGuess);
  onGuessRef.current = onGuess;

  useEffect(() => {
    if (!pool || pool.length === 0) return;
    let record = load<GameRecord | null>(keys.game(date), null);
    if (!record || typeof record.answerId !== 'number') {
      record = newGame(date, mode, pool, defaultHardMode);
    } else if (mode === 'archive' && record.status === 'playing' && record.mode !== 'archive') {
      // An unfinished daily picked up later from the archive is a replay now; it can't
      // retroactively rescue a streak.
      record = { ...record, mode: 'archive' };
    }
    save(keys.game(date), record);
    setGame(record);
    setAnswer(null);
    setError(null);

    const ac = new AbortController();
    fetchMovie(record.answerId, ac.signal)
      .then(setAnswer)
      .catch((err: unknown) => {
        if (ac.signal.aborted) return;
        console.error(err);
        setError(navigator.onLine === false ? "You're offline and this frame isn't cached yet." : "Couldn't load the frame from TMDB.");
      });
    return () => ac.abort();
    // defaultHardMode only seeds brand-new games; changing it mustn't reload the current one.
  }, [date, mode, pool, attempt]);

  const commit = useCallback((next: GameRecord, guess: GuessRecord) => {
    save(keys.game(next.date), next);
    setGame(next);
    onGuessRef.current?.(next, guess);
  }, []);

  const guess = useCallback(
    async (s: MovieSuggestion) => {
      if (!game || !answer || game.status !== 'playing' || busyRef.current) return;
      if (hasAlreadyGuessed(game.guesses, s.id)) return;
      busyRef.current = true;
      setBusy(true);
      try {
        let facts: MovieFacts | null = null;
        if (s.id !== answer.id) {
          // Needed only to spot a shared director or franchise; a slow or failed lookup
          // just means the guess can't be marked "close".
          facts = await withTimeout(fetchMovie(s.id), 6000).catch(() => null);
        }
        const record = evaluateGuess(s, facts, answer);
        commit(applyGuess(game, record), record);
      } finally {
        busyRef.current = false;
        setBusy(false);
      }
    },
    [game, answer, commit],
  );

  const skip = useCallback(() => {
    if (!game || !answer || game.status !== 'playing' || busyRef.current) return;
    const record = skipRecord();
    commit(applyGuess(game, record), record);
  }, [game, answer, commit]);

  const setHardMode = useCallback(
    (hardMode: boolean) => {
      if (!game || game.guesses.length > 0 || game.status !== 'playing') return;
      const next = { ...game, hardMode };
      save(keys.game(next.date), next);
      setGame(next);
    },
    [game],
  );

  const retry = useCallback(() => setAttempt((n) => n + 1), []);

  return { game, answer, error, busy, guess, skip, setHardMode, retry };
}
