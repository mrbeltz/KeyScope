import type { GameRecord, MovieFacts } from '../lib/types';
import { imageUrl } from '../lib/tmdb';
import { Countdown } from './Countdown';
import { ShareIcon, StatsIcon } from './Icons';

interface Props {
  game: GameRecord;
  answer: MovieFacts;
  shareNote: string | null;
  onShare: () => void;
  onStats: () => void;
  onBackToToday?: () => void;
  onPrevDay?: () => void;
}

export function GameOver({ game, answer, shareNote, onShare, onStats, onBackToToday, onPrevDay }: Props) {
  const won = game.status === 'won';
  return (
    <section className="animate-pop glass neon-frame flex flex-col gap-3 rounded-xl p-4" aria-live="polite">
      <div className="flex gap-3">
        {answer.posterPath && (
          <img src={imageUrl(answer.posterPath, 'w185')} alt="" width={72} height={108} className="h-27 w-18 shrink-0 rounded-md object-cover" />
        )}
        <div className="min-w-0">
          <p className={`text-xs tracking-[0.3em] uppercase ${won ? 'text-cyan' : 'text-pink'}`}>
            {won ? `Solved in ${game.guesses.length}/6` : 'Out of guesses'}
          </p>
          <h2 className="mt-1 text-xl leading-tight text-white">
            {answer.title} {answer.year && <span className="text-mist/60">({answer.year})</span>}
          </h2>
          {answer.directors.length > 0 && <p className="text-sm text-mist/75">Directed by {answer.directors.map((d) => d.name).join(' & ')}</p>}
          <a
            className="mt-1 inline-flex min-h-11 items-center text-sm text-cyan underline decoration-cyan/40 underline-offset-4"
            href={`https://www.themoviedb.org/movie/${answer.id}`}
            target="_blank"
            rel="noreferrer"
          >
            View on TMDB ↗
          </a>
        </div>
      </div>
      <div className="grid grid-cols-[1fr_auto] gap-2">
        <button type="button" className="btn btn-primary" onClick={onShare}>
          <ShareIcon /> Share
        </button>
        <button type="button" className="btn btn-ghost" onClick={onStats} aria-label="Statistics">
          <StatsIcon />
        </button>
      </div>
      {shareNote && <p className="text-center text-sm text-amber" role="status">{shareNote}</p>}
      {onPrevDay && (
        <button type="button" className="btn btn-ghost normal-case" onClick={onPrevDay}>
          ‹ Play the day before
        </button>
      )}
      {onBackToToday ? (
        <button type="button" className="btn btn-ghost" onClick={onBackToToday}>
          Back to today's frame
        </button>
      ) : (
        <Countdown />
      )}
    </section>
  );
}
