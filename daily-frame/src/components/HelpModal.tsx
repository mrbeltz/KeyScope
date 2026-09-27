import { Modal } from './Modal';

export function HelpModal({ onClose }: { onClose: () => void }) {
  return (
    <Modal title="How to play" onClose={onClose}>
      <div className="flex flex-col gap-3 text-[0.95rem] leading-relaxed">
        <p>
          Name the movie from a single frame. Everyone gets the <span className="text-cyan">same still</span> each day, and a new one
          drops at local midnight.
        </p>
        <ul className="flex flex-col gap-2">
          <li>🎬 You have <b className="text-white">6 guesses</b>. Search, then pick a title from the list.</li>
          <li>🔍 Every miss (or skip) sharpens the image and unlocks a hint: decade → genre → lead actor → director → tagline.</li>
          <li>🟨 <span className="text-amber">Close</span> means your guess shares a director or a franchise with the answer.</li>
          <li>🔥 Solve it every day to build a streak. A loss or a missed day resets it.</li>
          <li>☠ Hard mode hides the hints. Switch it on in settings before your first guess.</li>
          <li>🗄️ The archive lets you replay past frames. Replays don't touch your stats.</li>
        </ul>
        <p className="text-xs text-mist/60">
          Movie data and images from TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.
        </p>
      </div>
    </Modal>
  );
}
