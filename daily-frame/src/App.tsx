import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArchiveModal } from './components/ArchiveModal';
import { FrameCanvas } from './components/FrameCanvas';
import { GameOver } from './components/GameOver';
import { GuessHistory } from './components/GuessHistory';
import { GuessInput } from './components/GuessInput';
import { Header } from './components/Header';
import { HelpModal } from './components/HelpModal';
import { HintList } from './components/HintList';
import { SettingsModal } from './components/SettingsModal';
import { SetupNotice } from './components/SetupNotice';
import { StatsModal } from './components/StatsModal';
import { useGame } from './hooks/useGame';
import { usePool } from './hooks/usePool';
import { useSettings } from './hooks/useSettings';
import { usePrefersReducedMotion, useToday } from './hooks/useToday';
import { puzzleNumber } from './lib/dailySeed';
import { cue } from './lib/feedback';
import { buildHints } from './lib/hints';
import { isNative } from './lib/native';
import { stageFor } from './lib/reveal';
import { shareResult, shareText } from './lib/share';
import { type DailyHistory, computeStats, recordResult } from './lib/stats';
import { keys, load, save } from './lib/storage';
import { forgetTmdbKey, hasTmdbKey, imageUrl, tmdbKeySource } from './lib/tmdb';
import type { GameRecord, GuessRecord } from './lib/types';
import { MAX_GUESSES } from './lib/types';

type ModalName = 'help' | 'stats' | 'settings' | 'archive' | null;
type View = { date: string; mode: 'daily' | 'archive' };

export default function App() {
  const today = useToday();
  const [view, setView] = useState<View>({ date: today, mode: 'daily' });
  const [settings, updateSettings] = useSettings();
  const pool = usePool();
  const reducedMotion = usePrefersReducedMotion();
  const [history, setHistory] = useState<DailyHistory>(() => load<DailyHistory>(keys.history, {}));
  const stats = useMemo(() => computeStats(history, today), [history, today]);
  const [modal, setModal] = useState<ModalName>(() => (load(keys.seenHelp, false) ? null : 'help'));
  const [shareNote, setShareNote] = useState<string | null>(null);
  const [hasKey, setHasKey] = useState(hasTmdbKey);
  const statsTimer = useRef<number | undefined>(undefined);
  const settingsRef = useRef(settings);
  settingsRef.current = settings;

  const onGuess = useCallback((game: GameRecord, guess: GuessRecord) => {
    const s = settingsRef.current;
    const over = game.status !== 'playing';
    cue(game.status === 'lost' ? 'lost' : guess.result, s);
    if (over && game.mode === 'daily') {
      setHistory((h) => {
        const next = recordResult(h, game.date, { won: game.status === 'won', guesses: game.guesses.length });
        save(keys.history, next);
        return next;
      });
    }
    if (over) {
      window.clearTimeout(statsTimer.current);
      statsTimer.current = window.setTimeout(() => setModal('stats'), 1600);
    }
  }, []);

  const { game, answer, error, busy, guess, skip, setHardMode, retry } = useGame({
    date: view.date,
    mode: view.mode,
    pool: pool?.ids ?? null,
    defaultHardMode: settings.hardMode,
    onGuess,
  });

  // Local midnight: move to the new puzzle, unless the player is mid-game on yesterday's —
  // then let them finish and offer the new one.
  const rolledOver = view.mode === 'daily' && view.date !== today;
  useEffect(() => {
    if (!rolledOver) return;
    if (!game || game.status !== 'playing' || game.guesses.length === 0) setView({ date: today, mode: 'daily' });
  }, [rolledOver, today, game]);

  useEffect(() => () => window.clearTimeout(statsTimer.current), []);
  useEffect(() => setShareNote(null), [view.date]);

  const closeModal = useCallback(() => {
    setModal((m) => {
      if (m === 'help') save(keys.seenHelp, true);
      return null;
    });
  }, []);

  const finished = !!game && game.status !== 'playing';
  const misses = game ? game.guesses.filter((g) => g.result !== 'correct').length : 0;
  const stage = stageFor(misses, finished);
  const stageNumber = finished ? MAX_GUESSES : Math.min(misses + 1, MAX_GUESSES);
  const hints = useMemo(() => (answer ? buildHints(answer) : []), [answer]);
  const guessedIds = useMemo(() => new Set(game?.guesses.flatMap((g) => (g.id === null ? [] : [g.id])) ?? []), [game]);
  const number = puzzleNumber(view.date);

  const onShare = useCallback(async () => {
    if (!game) return;
    const text = shareText({ puzzleNumber: puzzleNumber(game.date), game, streak: stats.currentStreak, url: isNative ? undefined : location.origin });
    const outcome = await shareResult(text);
    setShareNote(outcome === 'copied' ? 'Copied to clipboard!' : outcome === 'failed' ? "Couldn't share — try a screenshot." : null);
  }, [game, stats.currentStreak]);

  const subtitle = view.mode === 'archive' ? `Archive · #${number}` : `#${number} · ${formatToday(view.date)}`;

  return (
    <>
      <div className="backdrop" aria-hidden="true" />
      <main className="shell">
        <section className="pane stage-pane" aria-label="Frame">
          <Header
            subtitle={subtitle}
            onHelp={() => setModal('help')}
            onArchive={() => setModal('archive')}
            onStats={() => setModal('stats')}
            onSettings={() => setModal('settings')}
          />
          {rolledOver && (
            <button type="button" className="btn btn-ghost flex-hide w-full normal-case" onClick={() => setView({ date: today, mode: 'daily' })}>
              ✨ Today's frame is ready — play it
            </button>
          )}
          {hasKey && (
            <div className="frame-area">
              <FrameCanvas
                src={answer?.backdropPath ? imageUrl(answer.backdropPath, 'w1280') : null}
                stage={stage}
                stageNumber={stageNumber}
                reducedMotion={reducedMotion}
                revealedTitle={finished && answer ? answer.title : undefined}
              />
            </div>
          )}
          {game && hasKey && !error && (
            <div className="history-under-frame">
              <GuessHistory guesses={game.guesses} />
            </div>
          )}
        </section>

        <section className="pane control-pane" aria-label="Guesses and hints">
          {!hasKey ? (
            <SetupNotice
              onSaved={() => {
                setHasKey(true);
                retry();
              }}
            />
          ) : error ? (
            <div className="area-input glass rounded-xl border border-pink/50 p-4 text-center">
              <p className="mb-3">{error}</p>
              <button type="button" className="btn btn-primary" onClick={retry}>
                Try again
              </button>
            </div>
          ) : (
            <>
              <div className="area-input">
                {finished && answer && game ? (
                  <GameOver
                    game={game}
                    answer={answer}
                    shareNote={shareNote}
                    onShare={onShare}
                    onStats={() => setModal('stats')}
                    onBackToToday={view.mode === 'archive' ? () => setView({ date: today, mode: 'daily' }) : undefined}
                  />
                ) : (
                  <GuessInput
                    disabled={!answer || !game || finished}
                    busy={busy}
                    guessedIds={guessedIds}
                    guessesLeft={MAX_GUESSES - (game?.guesses.length ?? 0)}
                    onGuess={guess}
                    onSkip={skip}
                  />
                )}
              </div>
              <div className="area-hints">
                {answer && game && <HintList hints={hints} unlocked={misses} hardMode={game.hardMode} finished={finished} />}
              </div>
              <div className="area-history">{game && <GuessHistory guesses={game.guesses} />}</div>
            </>
          )}
        </section>
      </main>

      {modal === 'help' && <HelpModal onClose={closeModal} />}
      {modal === 'stats' && (
        <StatsModal stats={stats} game={view.mode === 'daily' ? game : null} onShare={onShare} onClose={closeModal} />
      )}
      {modal === 'settings' && (
        <SettingsModal
          settings={settings}
          onChange={updateSettings}
          canChangeHardMode={!!game && game.status === 'playing' && game.guesses.length === 0}
          currentHardMode={game?.hardMode ?? settings.hardMode}
          onHardModeNow={setHardMode}
          poolSource={pool?.source ?? 'curated'}
          poolSize={pool?.ids.length ?? 0}
          keySource={hasKey ? tmdbKeySource() : null}
          onForgetKey={() => {
            forgetTmdbKey();
            setHasKey(false);
            setModal(null);
          }}
          onClose={closeModal}
        />
      )}
      {modal === 'archive' && (
        <ArchiveModal
          today={today}
          onPick={(date) => {
            setView({ date, mode: 'archive' });
            setModal(null);
          }}
          onClose={closeModal}
        />
      )}
    </>
  );
}

function formatToday(key: string) {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}
