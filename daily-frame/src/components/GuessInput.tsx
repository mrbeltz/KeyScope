import { useEffect, useId, useRef, useState } from 'react';
import { searchMovies } from '../lib/tmdb';
import type { MovieSuggestion } from '../lib/types';

interface Props {
  disabled: boolean;
  busy: boolean;
  guessedIds: Set<number>;
  guessesLeft: number;
  onGuess: (s: MovieSuggestion) => void;
  onSkip: () => void;
}

const DEBOUNCE_MS = 250;

/** Search box that only ever submits a movie picked from its suggestions. */
export function GuessInput({ disabled, busy, guessedIds, guessesLeft, onGuess, onSkip }: Props) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<MovieSuggestion[]>([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const [selected, setSelected] = useState<MovieSuggestion | null>(null);
  const [status, setStatus] = useState<'idle' | 'loading' | 'error'>('idle');
  const [shake, setShake] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const listId = useId();

  useEffect(() => {
    if (selected && query === label(selected)) return;
    setSelected(null);
    const q = query.trim();
    if (q.length < 2) {
      setResults([]);
      setStatus('idle');
      return;
    }
    const ac = new AbortController();
    const timer = window.setTimeout(() => {
      setStatus('loading');
      searchMovies(q, ac.signal)
        .then((r) => {
          setResults(r);
          setActive(r.length ? 0 : -1);
          setOpen(true);
          setStatus('idle');
        })
        .catch(() => !ac.signal.aborted && setStatus('error'));
    }, DEBOUNCE_MS);
    return () => {
      window.clearTimeout(timer);
      ac.abort();
    };
    // `selected` is read but not a dependency: picking a suggestion mustn't trigger a new search.
  }, [query]);

  const pick = (s: MovieSuggestion) => {
    if (guessedIds.has(s.id)) return;
    setSelected(s);
    setQuery(label(s));
    setOpen(false);
  };

  const submit = () => {
    if (!selected) {
      setShake(true);
      window.setTimeout(() => setShake(false), 400);
      inputRef.current?.focus();
      return;
    }
    onGuess(selected);
    setSelected(null);
    setQuery('');
    setResults([]);
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setOpen(true);
      setActive((i) => Math.min(results.length - 1, i + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(0, i - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (open && results[active]) pick(results[active]);
      else submit();
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  const showList = open && results.length > 0 && !selected;

  return (
    <div className="flex flex-col gap-2">
      <label className="sr-only" htmlFor={`${listId}-input`}>
        Guess the movie
      </label>
      <div className={shake ? 'animate-shake' : ''}>
        <input
          id={`${listId}-input`}
          ref={inputRef}
          className="field"
          type="search"
          inputMode="search"
          autoComplete="off"
          autoCorrect="off"
          spellCheck={false}
          enterKeyHint="go"
          placeholder={disabled ? 'Game over' : 'Search for a movie…'}
          disabled={disabled}
          value={query}
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showList && active >= 0 ? `${listId}-${active}` : undefined}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onKeyDown={onKeyDown}
          onFocus={() => results.length && setOpen(true)}
        />
      </div>

      {showList && (
        <ul id={listId} role="listbox" className="glass neon-frame max-h-72 overflow-y-auto rounded-[0.6rem] py-1">
          {results.map((r, i) => {
            const used = guessedIds.has(r.id);
            return (
              <li key={r.id} role="presentation">
                <button
                  type="button"
                  id={`${listId}-${i}`}
                  role="option"
                  aria-selected={i === active}
                  disabled={used}
                  className="option"
                  onMouseEnter={() => setActive(i)}
                  onClick={() => pick(r)}
                >
                  <span className="min-w-0">
                    <span className="block truncate text-white">{r.title}</span>
                    {r.originalTitle && r.originalTitle !== r.title && (
                      <span className="block truncate text-xs text-mist/60">{r.originalTitle}</span>
                    )}
                  </span>
                  <span className="shrink-0 text-sm text-cyan/80">{used ? 'guessed' : (r.year ?? '—')}</span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
      {status === 'error' && <p className="text-sm text-pink">Search is unavailable right now.</p>}
      {open && status === 'idle' && query.trim().length >= 2 && results.length === 0 && !selected && (
        <p className="text-sm text-mist/60">No matches. Try the original or a shorter title.</p>
      )}

      <div className="grid grid-cols-[1fr_auto] gap-2">
        <button type="button" className="btn btn-primary" disabled={disabled || busy} onClick={submit}>
          {busy ? 'Checking…' : 'Guess'}
        </button>
        <button type="button" className="btn btn-ghost" disabled={disabled || busy} onClick={onSkip}>
          Skip
        </button>
      </div>
      <p className="text-center text-xs tracking-widest text-mist/60 uppercase">
        {guessesLeft} {guessesLeft === 1 ? 'guess' : 'guesses'} left · skipping uses one
      </p>
    </div>
  );
}

function label(s: MovieSuggestion) {
  return s.year ? `${s.title} (${s.year})` : s.title;
}
