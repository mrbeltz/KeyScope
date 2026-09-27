import { describe, expect, it } from 'vitest';
import { applyGuess, newGame } from '../hooks/useGame';
import { dailyIndex } from '../lib/dailySeed';
import { skipRecord } from '../lib/matching';

const pool = [10, 20, 30, 40, 50, 60, 70];
const opts = { hardMode: false, multipleChoice: false };

describe('game progression', () => {
  it('pins the day’s answer from the pool', () => {
    const g = newGame('2026-09-27', 'daily', pool, opts);
    expect(g.answerId).toBe(pool[dailyIndex('2026-09-27', pool.length)]);
    expect(g.status).toBe('playing');
  });

  it('is lost after six misses and skips count as guesses', () => {
    let g = newGame('2026-09-27', 'daily', pool, opts);
    for (let i = 0; i < 5; i++) g = applyGuess(g, skipRecord());
    expect(g.status).toBe('playing');
    g = applyGuess(g, { id: 1, title: 'x', year: 2000, result: 'wrong' });
    expect(g.status).toBe('lost');
    expect(applyGuess(g, { id: 2, title: 'y', year: 2000, result: 'correct' })).toBe(g);
  });

  it('is won on a correct guess', () => {
    const g = applyGuess(newGame('2026-09-27', 'daily', pool, opts), { id: 3, title: 'z', year: 2000, result: 'correct' });
    expect(g.status).toBe('won');
    expect(g.guesses).toHaveLength(1);
  });
});
