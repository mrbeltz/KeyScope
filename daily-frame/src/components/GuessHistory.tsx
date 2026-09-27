import type { GuessRecord } from '../lib/types';
import { MAX_GUESSES } from '../lib/types';

const ICON: Record<GuessRecord['result'], string> = { correct: '✅', close: '🟨', wrong: '❌', skip: '⏭️' };

export function GuessHistory({ guesses }: { guesses: GuessRecord[] }) {
  return (
    <section aria-label="Your guesses">
      <h2 className="mb-2 text-xs tracking-[0.3em] text-violet uppercase">Guesses</h2>
      <ol className="flex flex-col gap-1.5">
        {Array.from({ length: MAX_GUESSES }, (_, i) => {
          const g = guesses[i];
          if (!g) {
            return (
              <li key={i} className="flex min-h-11 items-center rounded-lg border border-dashed border-violet/25 px-3 text-mist/30">
                {i + 1}
              </li>
            );
          }
          const tone =
            g.result === 'correct'
              ? 'border-cyan/70 bg-cyan/10 text-white'
              : g.result === 'close'
                ? 'border-amber/60 bg-amber/10'
                : 'border-pink/40 bg-pink/5';
          return (
            <li key={i} className={`animate-pop flex min-h-11 items-center gap-3 rounded-lg border px-3 py-1.5 ${tone}`}>
              <span aria-hidden="true">{ICON[g.result]}</span>
              <span className="min-w-0 flex-1">
                <span className={`block truncate ${g.result === 'skip' ? 'text-mist/60 italic' : ''}`}>
                  {g.title}
                  {g.year ? <span className="text-mist/50"> ({g.year})</span> : null}
                </span>
                {g.result === 'close' && <span className="block text-xs text-amber">Close — {g.reason?.toLowerCase()}</span>}
              </span>
              <span className="sr-only">
                {g.result === 'correct' ? 'correct' : g.result === 'close' ? `close, ${g.reason}` : g.result === 'skip' ? 'skipped' : 'wrong'}
              </span>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
