import type { GuessRecord, MovieSuggestion } from '../lib/types';

interface Props {
  choices: MovieSuggestion[] | null;
  guesses: GuessRecord[];
  disabled: boolean;
  busy: boolean;
  guessesLeft: number;
  onGuess: (s: MovieSuggestion) => void;
  onSkip: () => void;
}

/** Four options; a wrong pick is crossed out and costs a guess, the same as a typed miss. */
export function MultipleChoice({ choices, guesses, disabled, busy, guessesLeft, onGuess, onSkip }: Props) {
  const tried = new Map(guesses.flatMap((g) => (g.id === null ? [] : [[g.id, g] as const])));

  return (
    <div className="flex flex-col gap-2">
      <h2 className="text-xs tracking-[0.3em] text-violet uppercase">Which movie is it?</h2>
      {!choices ? (
        <div className="grid grid-cols-1 gap-2 min-[420px]:grid-cols-2" aria-busy="true">
          {Array.from({ length: 4 }, (_, i) => (
            <div key={i} className="min-h-14 animate-pulse rounded-lg border border-violet/25 bg-night/40" />
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-2 min-[420px]:grid-cols-2">
          {choices.map((c) => {
            const g = tried.get(c.id);
            const tone = g
              ? g.result === 'close'
                ? 'border-amber/60 bg-amber/10 text-amber line-through decoration-amber/60'
                : 'border-pink/40 bg-pink/5 text-mist/40 line-through'
              : 'border-cyan/50 bg-night/60 text-white hover:border-cyan hover:bg-cyan/10 active:scale-[0.98]';
            return (
              <button
                key={c.id}
                type="button"
                disabled={disabled || busy || !!g}
                onClick={() => onGuess(c)}
                className={`flex min-h-14 items-center justify-center rounded-lg border px-3 py-2 text-center leading-snug transition focus-visible:outline-2 focus-visible:outline-cyan ${tone}`}
              >
                <span>
                  {g && <span aria-hidden="true">{g.result === 'close' ? '🟨 ' : '❌ '}</span>}
                  {c.title}
                  {g && <span className="sr-only">{g.result === 'close' ? ` — close, ${g.reason}` : ' — wrong'}</span>}
                </span>
              </button>
            );
          })}
        </div>
      )}
      <button type="button" className="btn btn-ghost" disabled={disabled || busy} onClick={onSkip}>
        {busy ? 'Checking…' : 'Skip — sharpen the frame'}
      </button>
      <p className="text-center text-xs tracking-widest text-mist/60 uppercase">
        {guessesLeft} {guessesLeft === 1 ? 'guess' : 'guesses'} left
      </p>
    </div>
  );
}
