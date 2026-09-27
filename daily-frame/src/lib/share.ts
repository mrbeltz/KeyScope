import { isNative } from './native';
import type { GameRecord } from './types';
import { MAX_GUESSES } from './types';

const SQUARE = { correct: '🟩', close: '🟨', wrong: '⬛', skip: '⬛' } as const;
const UNUSED = '⬜';

/** One square per guess slot: 🟩 solved, 🟨 close, ⬛ miss or skip, ⬜ never needed. */
export function resultSquares(game: Pick<GameRecord, 'guesses'>): string {
  const used = game.guesses.slice(0, MAX_GUESSES).map((g) => SQUARE[g.result]);
  return used.join('') + UNUSED.repeat(MAX_GUESSES - used.length);
}

export function shareText(opts: {
  puzzleNumber: number;
  game: GameRecord;
  streak: number;
  url?: string;
}): string {
  const { puzzleNumber, game, streak, url } = opts;
  const score = game.status === 'won' ? `${game.guesses.length}/${MAX_GUESSES}` : `X/${MAX_GUESSES}`;
  const flags = (game.hardMode ? '*' : '') + (game.multipleChoice ? ' (multiple choice)' : '');
  const lines = [`Daily Frame #${puzzleNumber} 🎬 ${score}${flags}`, resultSquares(game)];
  if (game.mode === 'daily') lines.push(`🔥 Streak: ${streak}`);
  else lines.push('🗄️ Archive replay');
  if (url) lines.push(url);
  return lines.join('\n');
}

export type ShareOutcome = 'shared' | 'copied' | 'failed';

/** The Android share sheet in the app, the Web Share API in a browser, clipboard otherwise. */
export async function shareResult(text: string): Promise<ShareOutcome> {
  if (isNative) {
    try {
      const { Share } = await import('@capacitor/share');
      await Share.share({ text, dialogTitle: 'Share your Daily Frame' });
      return 'shared';
    } catch (err) {
      // Dismissing the sheet rejects too; treat that as done rather than falling back.
      if (String(err).toLowerCase().includes('cancel')) return 'shared';
    }
  }
  if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      await navigator.share({ text });
      return 'shared';
    } catch (err) {
      // The user closing the share sheet is not a failure worth falling back from.
      if (err instanceof DOMException && err.name === 'AbortError') return 'shared';
    }
  }
  try {
    await navigator.clipboard.writeText(text);
    return 'copied';
  } catch {
    return 'failed';
  }
}
