import type { Hint } from '../lib/hints';

interface Props {
  hints: Hint[];
  /** How many hints are unlocked (one per miss). */
  unlocked: number;
  hardMode: boolean;
  finished: boolean;
}

export function HintList({ hints, unlocked, hardMode, finished }: Props) {
  if (hardMode && !finished) {
    return (
      <section aria-label="Hints" className="rounded-lg border border-pink/40 px-3 py-2 text-sm text-pink">
        ☠ Hard mode — no hints. Just you and the frame.
      </section>
    );
  }
  const shown = finished ? hints.length : unlocked;
  return (
    <section aria-label="Hints">
      <h2 className="mb-2 text-xs tracking-[0.3em] text-violet uppercase">Hints</h2>
      <ul className="grid grid-cols-1 gap-1.5">
        {hints.map((h, i) => {
          const open = i < shown;
          return (
            <li
              key={h.label}
              className={`flex min-h-11 items-baseline gap-3 rounded-lg px-3 py-2 ${
                open ? 'animate-pop glass border border-cyan/40' : 'border border-violet/20 text-mist/35'
              }`}
            >
              <span className="w-24 shrink-0 text-xs tracking-widest text-cyan uppercase">{h.label}</span>
              {open ? (
                <span className="text-white">{h.value}</span>
              ) : (
                <span className="text-sm">🔒 after miss {i + 1}</span>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
