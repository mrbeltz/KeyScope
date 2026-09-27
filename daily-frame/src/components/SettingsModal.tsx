import type { Settings } from '../hooks/useSettings';
import { Modal } from './Modal';

interface Props {
  settings: Settings;
  onChange: (patch: Partial<Settings>) => void;
  /** Whether the current game can still switch modes (no guesses yet). */
  canChangeOptions: boolean;
  current: { hardMode: boolean; multipleChoice: boolean };
  onOptionsNow: (patch: Partial<{ hardMode: boolean; multipleChoice: boolean }>) => void;
  poolSource: 'plex' | 'curated';
  poolSize: number;
  keySource: 'build' | 'device' | null;
  onForgetKey: () => void;
  onClose: () => void;
}

export function SettingsModal({ settings, onChange, canChangeOptions, current, onOptionsNow, poolSource, poolSize, keySource, onForgetKey, onClose }: Props) {
  // Once a game has a guess in it, its mode is fixed; a change then applies from the next game.
  const gameOption = (text: string, key: 'hardMode' | 'multipleChoice') =>
    canChangeOptions ? text : `${text} This game is locked ${current[key] ? 'on' : 'off'}; the change applies from your next game.`;

  return (
    <Modal title="Settings" onClose={onClose}>
      <div className="flex flex-col divide-y divide-violet/20">
        <Toggle
          label="Multiple choice"
          description={gameOption('Pick from four options instead of typing a title.', 'multipleChoice')}
          checked={canChangeOptions ? current.multipleChoice : settings.multipleChoice}
          onChange={(v) => {
            onChange({ multipleChoice: v });
            if (canChangeOptions) onOptionsNow({ multipleChoice: v });
          }}
        />
        <Toggle
          label="Hard mode"
          description={gameOption('No hints — the image is all you get.', 'hardMode')}
          checked={canChangeOptions ? current.hardMode : settings.hardMode}
          onChange={(v) => {
            onChange({ hardMode: v });
            if (canChangeOptions) onOptionsNow({ hardMode: v });
          }}
        />
        <Toggle label="Sound effects" description="Synth blips on right, close and wrong." checked={settings.sound} onChange={(v) => onChange({ sound: v })} />
        <Toggle
          label="Haptics"
          description="Vibrate on right and wrong answers."
          checked={settings.haptics}
          onChange={(v) => onChange({ haptics: v })}
          disabled={typeof navigator !== 'undefined' && !('vibrate' in navigator)}
        />
      </div>
      <p className="mt-4 text-xs text-mist/60">
        Puzzle pool: {poolSize} movies from {poolSource === 'plex' ? 'your Plex library' : 'the curated list'}.
      </p>
      {keySource === 'device' && (
        <button type="button" className="btn btn-ghost mt-3 w-full normal-case" onClick={onForgetKey}>
          Change TMDB key
        </button>
      )}
    </Modal>
  );
}

function Toggle(props: { label: string; description: string; checked: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  const { label, description, checked, onChange, disabled } = props;
  return (
    <label className={`flex min-h-14 cursor-pointer items-center justify-between gap-4 py-3 ${disabled ? 'opacity-40' : ''}`}>
      <span>
        <span className="block text-white">{label}</span>
        <span className="block text-xs text-mist/65">{description}</span>
      </span>
      <span className="relative inline-flex h-11 w-16 shrink-0 items-center">
        <input
          type="checkbox"
          role="switch"
          className="peer absolute inset-0 cursor-pointer opacity-0"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onChange(e.target.checked)}
        />
        <span className="pointer-events-none h-7 w-14 rounded-full border border-violet/60 bg-night transition-colors peer-checked:border-pink peer-checked:bg-pink/40 peer-focus-visible:outline-2 peer-focus-visible:outline-cyan" />
        <span className="pointer-events-none absolute left-1.5 h-5 w-5 rounded-full bg-mist transition-transform peer-checked:translate-x-7 peer-checked:bg-white peer-checked:shadow-[0_0_10px_#ff2a6d]" />
      </span>
    </label>
  );
}
