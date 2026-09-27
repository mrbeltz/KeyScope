import { describe, expect, it } from 'vitest';
import { resultSquares, shareText } from '../lib/share';
import type { GameRecord, GuessRecord } from '../lib/types';

const miss: GuessRecord = { id: 1, title: 'A', year: 2000, result: 'wrong' };
const close: GuessRecord = { id: 2, title: 'B', year: 2000, result: 'close', reason: 'Same director' };
const hit: GuessRecord = { id: 3, title: 'C', year: 2000, result: 'correct' };
const game = (guesses: GuessRecord[], status: GameRecord['status'], extra: Partial<GameRecord> = {}): GameRecord => ({
  date: '2026-05-22',
  mode: 'daily',
  hardMode: false,
  answerId: 3,
  guesses,
  status,
  ...extra,
});

describe('share text', () => {
  it('matches the spec format', () => {
    expect(shareText({ puzzleNumber: 142, game: game([miss, miss, hit], 'won'), streak: 12 })).toBe(
      'Daily Frame #142 🎬 3/6\n⬛⬛🟩⬜⬜⬜\n🔥 Streak: 12',
    );
  });

  it('shows X/6 for a loss and yellow for close guesses', () => {
    const t = shareText({ puzzleNumber: 7, game: game([miss, close, miss, miss, miss, miss], 'lost'), streak: 0 });
    expect(t.split('\n')[0]).toBe('Daily Frame #7 🎬 X/6');
    expect(resultSquares(game([miss, close, miss, miss, miss, miss], 'lost'))).toBe('⬛🟨⬛⬛⬛⬛');
  });

  it('flags hard mode and archive replays', () => {
    const t = shareText({ puzzleNumber: 9, game: game([hit], 'won', { hardMode: true, mode: 'archive' }), streak: 4 });
    expect(t).toBe('Daily Frame #9 🎬 1/6*\n🟩⬜⬜⬜⬜⬜\n🗄️ Archive replay');
  });

  it('never includes the answer', () => {
    const t = shareText({ puzzleNumber: 1, game: game([hit], 'won'), streak: 1, url: 'https://example.com' });
    expect(t).not.toContain('C');
  });
});

describe('multiple choice share text', () => {
  it('says the game was multiple choice', () => {
    const t = shareText({ puzzleNumber: 3, game: game([hit], 'won', { multipleChoice: true }), streak: 2 });
    expect(t.split('\n')[0]).toBe('Daily Frame #3 🎬 1/6 (multiple choice)');
  });
});
