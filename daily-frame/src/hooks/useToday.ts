import { useEffect, useState } from 'react';
import { localDateKey, msUntilNextLocalMidnight } from '../lib/dates';

/** Today's local date key, updated at local midnight and whenever the app comes back to the foreground. */
export function useToday(): string {
  const [today, setToday] = useState(localDateKey);
  useEffect(() => {
    let timer: number;
    const refresh = () => {
      setToday(localDateKey());
      window.clearTimeout(timer);
      // A little past midnight, so the new date is definitely the one we read.
      timer = window.setTimeout(refresh, msUntilNextLocalMidnight() + 250);
    };
    refresh();
    const onVisible = () => document.visibilityState === 'visible' && refresh();
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, []);
  return today;
}

export function useNow(intervalMs: number): Date {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
  return now;
}

export function usePrefersReducedMotion(): boolean {
  const query = '(prefers-reduced-motion: reduce)';
  const [reduced, setReduced] = useState(() => typeof matchMedia !== 'undefined' && matchMedia(query).matches);
  useEffect(() => {
    const mq = matchMedia(query);
    const on = () => setReduced(mq.matches);
    mq.addEventListener('change', on);
    return () => mq.removeEventListener('change', on);
  }, []);
  return reduced;
}
