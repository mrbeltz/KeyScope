import { ArchiveIcon, HelpIcon, SettingsIcon, StatsIcon } from './Icons';

interface Props {
  subtitle: string;
  onHelp: () => void;
  onArchive: () => void;
  onStats: () => void;
  onSettings: () => void;
}

export function Header({ subtitle, onHelp, onArchive, onStats, onSettings }: Props) {
  return (
    <header className="flex items-center justify-between gap-2">
      <div className="flex">
        <button type="button" className="icon-btn" onClick={onHelp} aria-label="How to play">
          <HelpIcon />
        </button>
        <button type="button" className="icon-btn" onClick={onArchive} aria-label="Archive">
          <ArchiveIcon />
        </button>
      </div>
      <div className="min-w-0 text-center">
        <h1 className="logo animate-flicker text-[clamp(1.35rem,6vw,2.1rem)] leading-none whitespace-nowrap">Daily Frame</h1>
        <p className="mt-1 truncate text-[0.7rem] tracking-[0.12em] sm:tracking-[0.25em] text-cyan/80 uppercase">{subtitle}</p>
      </div>
      <div className="flex">
        <button type="button" className="icon-btn" onClick={onStats} aria-label="Statistics">
          <StatsIcon />
        </button>
        <button type="button" className="icon-btn" onClick={onSettings} aria-label="Settings">
          <SettingsIcon />
        </button>
      </div>
    </header>
  );
}
