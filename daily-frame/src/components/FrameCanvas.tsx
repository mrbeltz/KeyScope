import { useEffect, useRef, useState } from 'react';
import type { RevealStage } from '../lib/reveal';

interface Props {
  src: string | null;
  stage: RevealStage;
  stageNumber: number;
  reducedMotion: boolean;
  /** Only set once the game is over — until then the canvas is labelled generically. */
  revealedTitle?: string;
}

/**
 * The puzzle image, drawn on a canvas. The pixelation and blur are baked into the pixels,
 * so there is no CSS filter to switch off in dev tools, and the <img> the pixels come from
 * never enters the DOM.
 */
export function FrameCanvas({ src, stage, stageNumber, reducedMotion, revealedTitle }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [img, setImg] = useState<HTMLImageElement | null>(null);
  const [failed, setFailed] = useState(false);
  // What's on screen right now, so a change animates from there rather than jumping.
  const shown = useRef<{ blocks: number; blur: number } | null>(null);
  const [size, setSize] = useState({ w: 0, h: 0, dpr: 1 });

  useEffect(() => {
    setImg(null);
    setFailed(false);
    shown.current = null;
    if (!src) return;
    const image = new Image();
    image.decoding = 'async';
    image.onload = () => setImg(image);
    image.onerror = () => setFailed(true);
    image.src = src;
    return () => {
      image.onload = null;
      image.onerror = null;
    };
  }, [src]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ro = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect;
      setSize({ w: width, h: height, dpr: Math.min(window.devicePixelRatio || 1, 3) });
    });
    ro.observe(canvas);
    return () => ro.disconnect();
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !img || size.w === 0) return;
    const W = Math.round(size.w * size.dpr);
    const H = Math.round(size.h * size.dpr);
    if (canvas.width !== W || canvas.height !== H) {
      canvas.width = W;
      canvas.height = H;
    }
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // "No pixelation" is expressed as one block per CSS pixel so it can be tweened.
    const target = { blocks: stage.blocks ?? size.w, blur: stage.blur };
    const from = shown.current ?? target;
    const off = document.createElement('canvas');
    const offCtx = off.getContext('2d');

    const paint = (blocks: number, blur: number) => {
      shown.current = { blocks, blur };
      ctx.save();
      ctx.clearRect(0, 0, W, H);
      ctx.fillStyle = '#06021a';
      ctx.fillRect(0, 0, W, H);
      let source: CanvasImageSource = img;
      const pixelated = blocks < size.w - 0.5 && offCtx;
      if (pixelated) {
        off.width = Math.max(2, Math.round(blocks));
        off.height = Math.max(2, Math.round((blocks * H) / W));
        offCtx.imageSmoothingEnabled = true;
        offCtx.imageSmoothingQuality = 'medium';
        offCtx.drawImage(img, 0, 0, off.width, off.height);
        source = off;
      }
      ctx.imageSmoothingEnabled = !pixelated;
      const blurPx = blur * size.dpr;
      // Blurring pulls in transparent pixels at the edges; overdraw a little to hide that.
      const bleed = blurPx * 2;
      if (blurPx > 0.05) ctx.filter = `blur(${blurPx.toFixed(2)}px)`;
      ctx.drawImage(source, -bleed, -bleed, W + bleed * 2, H + bleed * 2);
      ctx.restore();
    };

    const animate = !reducedMotion && (from.blocks !== target.blocks || from.blur !== target.blur);
    if (!animate) {
      paint(target.blocks, target.blur);
      return;
    }
    let raf = 0;
    const start = performance.now();
    const duration = 750;
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / duration);
      const e = 1 - Math.pow(1 - t, 3);
      // Blocks tween geometrically so each step feels like the same amount of sharpening.
      const blocks = from.blocks * Math.pow(target.blocks / from.blocks, e);
      paint(blocks, from.blur + (target.blur - from.blur) * e);
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [img, stage, size, reducedMotion]);

  const label = revealedTitle ? `Movie still from ${revealedTitle}` : `Mystery movie still, reveal stage ${stageNumber} of 6`;

  return (
    <div className="frame-box neon-frame relative rounded-[0.6rem]">
      <canvas ref={canvasRef} className="frame-canvas" role="img" aria-label={label} />
      <div className="frame-scanlines" aria-hidden="true" />
      {!img && (
        <div className="absolute inset-0 grid place-items-center text-center text-sm text-mist/70">
          {failed ? (
            <span>The frame didn't load. Check your connection.</span>
          ) : (
            <span className="animate-pulse tracking-[0.3em] uppercase">Rolling film…</span>
          )}
        </div>
      )}
      <div className="pointer-events-none absolute top-2 left-2 rounded bg-night/70 px-2 py-0.5 text-xs tracking-widest text-cyan">
        {revealedTitle ? 'REVEALED' : `STAGE ${stageNumber}/6`}
      </div>
    </div>
  );
}
