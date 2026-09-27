interface Props {
  label: string;
  detail: string;
  onPrev: (() => void) | null;
  onNext: (() => void) | null;
  onPick: () => void;
}

const Arrow = ({ dir }: { dir: 'left' | 'right' }) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={dir === 'left' ? 'M15 5l-7 7 7 7' : 'M9 5l7 7-7 7'} />
  </svg>
);

/** ‹ #269 · Sep 26 › — step through past frames one day at a time; the middle opens the full archive. */
export function DayNav({ label, detail, onPrev, onNext, onPick }: Props) {
  return (
    <nav className="flex items-center justify-between gap-1 rounded-xl border border-violet/30 bg-night/50" aria-label="Choose a day">
      <button type="button" className="icon-btn disabled:opacity-25" onClick={onPrev ?? undefined} disabled={!onPrev} aria-label="Previous day">
        <Arrow dir="left" />
      </button>
      <button
        type="button"
        onClick={onPick}
        className="flex min-h-11 min-w-0 flex-1 flex-col items-center justify-center rounded-lg leading-tight focus-visible:outline-2 focus-visible:outline-cyan"
        aria-label={`${label}, ${detail}. Open the archive`}
      >
        <span className="truncate text-sm tracking-[0.2em] text-cyan uppercase">{label}</span>
        <span className="truncate text-[0.7rem] tracking-[0.15em] text-mist/60 uppercase">{detail}</span>
      </button>
      <button type="button" className="icon-btn disabled:opacity-25" onClick={onNext ?? undefined} disabled={!onNext} aria-label="Next day">
        <Arrow dir="right" />
      </button>
    </nav>
  );
}
