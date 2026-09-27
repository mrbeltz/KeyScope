import type { Stats } from '../lib/stats';
import type { GameRecord } from '../lib/types';
import { Countdown } from './Countdown';
import { Modal } from './Modal';
import { ShareIcon } from './Icons';

interface Props {
  stats: Stats;
  /** Today's game, to highlight its row and offer sharing once it's over. */
  game: GameRecord | null;
  onShare?: () => void;
  onClose: () => void;
}

export function StatsModal({ stats, game, onShare, onClose }: Props) {
  const max = Math.max(1, ...stats.distribution);
  const highlight = game?.mode === 'daily' && game.status === 'won' ? game.guesses.length : null;
  const finished = game && game.status !== 'playing';
  const tiles = [
    { label: 'Played', value: stats.played },
    { label: 'Win %', value: stats.winPct },
    { label: 'Streak', value: stats.currentStreak },
    { label: 'Best', value: stats.maxStreak },
  ];

  return (
    <Modal title="Statistics" onClose={onClose}>
      <div className="grid grid-cols-4 gap-2 text-center">
        {tiles.map((t) => (
          <div key={t.label} className="rounded-lg border border-violet/30 bg-night/50 py-2">
            <div className="text-2xl text-white tabular-nums">{t.value}</div>
            <div className="text-[0.65rem] tracking-widest text-mist/70 uppercase">{t.label}</div>
          </div>
        ))}
      </div>
      {stats.currentStreak > 0 && (
        <p className="mt-3 text-center text-sm text-amber">🔥 {stats.currentStreak}-day streak — come back tomorrow to keep it.</p>
      )}

      <h3 className="mt-5 mb-2 text-xs tracking-[0.3em] text-violet uppercase">Guess distribution</h3>
      <ol className="flex flex-col gap-1" aria-label="Wins by number of guesses">
        {stats.distribution.map((n, i) => (
          <li key={i} className="flex items-center gap-2 text-sm">
            <span className="w-3 text-mist/80">{i + 1}</span>
            <div className="h-6 flex-1">
              <div
                className={`dist-bar flex h-full min-w-7 items-center justify-end rounded px-2 text-white ${
                  highlight === i + 1 ? 'bg-pink shadow-[0_0_12px_rgb(255_42_109/0.6)]' : 'bg-plum'
                }`}
                style={{ width: `${Math.max(8, (n / max) * 100)}%` }}
              >
                <span className="sr-only">{i + 1} guesses: </span>
                {n}
              </div>
            </div>
          </li>
        ))}
      </ol>

      <div className="mt-5 grid grid-cols-1 items-center gap-4 sm:grid-cols-2">
        <Countdown />
        {finished && onShare && (
          <button type="button" className="btn btn-primary w-full" onClick={onShare}>
            <ShareIcon /> Share
          </button>
        )}
      </div>
    </Modal>
  );
}
