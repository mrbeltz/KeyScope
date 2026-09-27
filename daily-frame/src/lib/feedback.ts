/** Haptics and synthesised sound cues. Both are best-effort and silent where unsupported. */

export function vibrate(pattern: number | number[]): void {
  try {
    navigator.vibrate?.(pattern);
  } catch {
    /* not supported */
  }
}

let ctx: AudioContext | null = null;

function audio(): AudioContext | null {
  try {
    ctx ??= new AudioContext();
    if (ctx.state === 'suspended') void ctx.resume();
    return ctx;
  } catch {
    return null;
  }
}

function tone(freq: number, start: number, dur: number, type: OscillatorType = 'square', gain = 0.06) {
  const a = audio();
  if (!a) return;
  const t = a.currentTime + start;
  const osc = a.createOscillator();
  const amp = a.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t);
  amp.gain.setValueAtTime(0.0001, t);
  amp.gain.exponentialRampToValueAtTime(gain, t + 0.01);
  amp.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  osc.connect(amp).connect(a.destination);
  osc.start(t);
  osc.stop(t + dur + 0.02);
}

export type Cue = 'correct' | 'close' | 'wrong' | 'skip' | 'lost';

const SOUNDS: Record<Cue, () => void> = {
  // A rising synth arpeggio: C–E–G–C.
  correct: () => [523.25, 659.25, 783.99, 1046.5].forEach((f, i) => tone(f, i * 0.09, 0.22, 'square')),
  close: () => [587.33, 698.46].forEach((f, i) => tone(f, i * 0.1, 0.18, 'triangle', 0.08)),
  wrong: () => [196, 146.83].forEach((f, i) => tone(f, i * 0.12, 0.22, 'sawtooth', 0.05)),
  skip: () => tone(330, 0, 0.1, 'triangle', 0.06),
  lost: () => [261.63, 220, 174.61, 130.81].forEach((f, i) => tone(f, i * 0.16, 0.3, 'sawtooth', 0.05)),
};

const HAPTICS: Record<Cue, number | number[]> = {
  correct: [30, 60, 30, 60, 120],
  close: [40, 50, 40],
  wrong: 140,
  skip: 25,
  lost: [200, 80, 200],
};

export function cue(kind: Cue, opts: { sound: boolean; haptics: boolean }): void {
  if (opts.haptics) vibrate(HAPTICS[kind]);
  if (opts.sound) SOUNDS[kind]();
}
