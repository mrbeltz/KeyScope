import { useCallback, useState } from 'react';
import { keys, load, save } from '../lib/storage';

export interface Settings {
  sound: boolean;
  haptics: boolean;
  /** Default for new games. A game's own mode is fixed once its first guess is in. */
  hardMode: boolean;
  /** Default for new games: pick from four options instead of searching. */
  multipleChoice: boolean;
}

const DEFAULTS: Settings = { sound: false, haptics: true, hardMode: false, multipleChoice: true };

export function useSettings() {
  const [settings, setSettings] = useState<Settings>(() => ({ ...DEFAULTS, ...load<Partial<Settings>>(keys.settings, {}) }));
  const update = useCallback((patch: Partial<Settings>) => {
    setSettings((prev) => {
      const next = { ...prev, ...patch };
      save(keys.settings, next);
      return next;
    });
  }, []);
  return [settings, update] as const;
}
