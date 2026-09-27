/**
 * The six reveal stages. Stage n is shown after n misses; the clear image is shown when the
 * game ends. `blocks` is how many pixel blocks span the image's width (null = no pixelation),
 * and `blur` is in CSS pixels at the image's displayed width.
 *
 * At a 360px-wide cover screen, 44 blocks is roughly 8px each.
 */
export interface RevealStage {
  blocks: number | null;
  blur: number;
}

export const STAGES: RevealStage[] = [
  { blocks: 44, blur: 2.5 },
  { blocks: 64, blur: 2 },
  { blocks: 96, blur: 1.5 },
  { blocks: 150, blur: 1 },
  { blocks: 260, blur: 0.5 },
  { blocks: null, blur: 0 },
];

export const CLEAR: RevealStage = { blocks: null, blur: 0 };

/** Hard mode keeps the image progression; it only withholds the text hints. */
export function stageFor(misses: number, finished: boolean): RevealStage {
  if (finished) return CLEAR;
  return STAGES[Math.min(Math.max(misses, 0), STAGES.length - 1)];
}
