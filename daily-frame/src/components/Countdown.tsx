import { formatCountdown, msUntilNextLocalMidnight } from '../lib/dates';
import { useNow } from '../hooks/useToday';

export function Countdown({ className = '' }: { className?: string }) {
  const now = useNow(1000);
  return (
    <div className={`text-center ${className}`}>
      <div className="text-xs tracking-[0.3em] text-violet uppercase">Next frame in</div>
      <div className="font-mono text-3xl text-cyan tabular-nums [text-shadow:0_0_12px_rgb(5_217_232/0.6)]" role="timer" aria-live="off">
        {formatCountdown(msUntilNextLocalMidnight(now))}
      </div>
    </div>
  );
}
