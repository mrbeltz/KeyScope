import { useMemo, useState } from 'react';
import { LAUNCH_DATE, puzzleNumber } from '../lib/dailySeed';
import { addDays, daysBetween } from '../lib/dates';
import { keys, load } from '../lib/storage';
import type { GameRecord } from '../lib/types';
import { Modal } from './Modal';

interface Props {
  today: string;
  onPick: (date: string) => void;
  onClose: () => void;
}

const PAGE = 30;

export function ArchiveModal({ today, onPick, onClose }: Props) {
  const [limit, setLimit] = useState(PAGE);
  const total = Math.max(0, daysBetween(LAUNCH_DATE, today));
  const dates = useMemo(
    () => Array.from({ length: Math.min(limit, total) }, (_, i) => addDays(today, -(i + 1))),
    [today, limit, total],
  );

  return (
    <Modal title="Archive" onClose={onClose}>
      <p className="mb-3 text-sm text-mist/70">Replay any past frame. Archive games never change your stats or streak.</p>
      {total === 0 && <p className="text-mist/60">Nothing here yet — the archive fills up from tomorrow.</p>}
      <ul className="grid grid-cols-2 gap-2">
        {dates.map((d) => {
          const g = load<GameRecord | null>(keys.game(d), null);
          const badge = !g || g.guesses.length === 0 ? '' : g.status === 'won' ? `✅ ${g.guesses.length}/6` : g.status === 'lost' ? '❌' : '⏳';
          return (
            <li key={d}>
              <button
                type="button"
                className="flex min-h-12 w-full flex-col items-start justify-center rounded-lg border border-violet/30 bg-night/50 px-3 py-1.5 text-left hover:border-cyan/60 focus-visible:outline-2 focus-visible:outline-cyan"
                onClick={() => onPick(d)}
              >
                <span className="text-white">#{puzzleNumber(d)}</span>
                <span className="flex w-full justify-between text-xs text-mist/65">
                  <span>{formatDate(d)}</span>
                  <span>{badge}</span>
                </span>
              </button>
            </li>
          );
        })}
      </ul>
      {limit < total && (
        <button type="button" className="btn btn-ghost mt-3 w-full" onClick={() => setLimit((n) => n + PAGE)}>
          Show older
        </button>
      )}
    </Modal>
  );
}

function formatDate(key: string) {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}
