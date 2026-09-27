import { useEffect, useState } from 'react';
import { CURATED_POOL, loadPool } from '../lib/pool';
import { hasPlex } from '../lib/plex';

export interface Pool {
  ids: number[];
  source: 'plex' | 'curated';
}

/** The curated pool is available synchronously; a Plex pool arrives after a request. */
export function usePool(): Pool | null {
  const [pool, setPool] = useState<Pool | null>(hasPlex ? null : { ids: CURATED_POOL, source: 'curated' });
  useEffect(() => {
    if (!hasPlex) return;
    let live = true;
    loadPool().then((p) => live && setPool(p));
    return () => {
      live = false;
    };
  }, []);
  return pool;
}
