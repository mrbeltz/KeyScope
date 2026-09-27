import { useState } from 'react';
import { saveTmdbKey } from '../lib/tmdb';

/** First run without a built-in key: paste one in, and it's checked and kept on this device. */
export function SetupNotice({ onSaved }: { onSaved: () => void }) {
  const [key, setKey] = useState('');
  const [status, setStatus] = useState<'idle' | 'checking' | 'invalid' | 'offline'>('idle');

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!key.trim()) return;
    setStatus('checking');
    const result = await saveTmdbKey(key);
    if (result === 'ok') onSaved();
    else setStatus(result);
  };

  return (
    <div className="glass neon-frame mx-auto my-4 w-full max-w-lg rounded-xl p-5 leading-relaxed">
      <h2 className="mb-2 text-lg text-pink">One-time setup: TMDB key</h2>
      <p className="text-sm">
        Daily Frame gets its movie stills from The Movie Database. Keys are free:
      </p>
      <ol className="my-3 list-decimal space-y-1 pl-5 text-sm">
        <li>
          Sign up at{' '}
          <a className="text-cyan underline" href="https://www.themoviedb.org/signup" target="_blank" rel="noreferrer">
            themoviedb.org
          </a>
          .
        </li>
        <li>
          Open{' '}
          <a className="text-cyan underline" href="https://www.themoviedb.org/settings/api" target="_blank" rel="noreferrer">
            Settings → API
          </a>
          , request a Developer key, and copy the <b className="text-white">API Key</b>.
        </li>
        <li>Paste it below. It stays on this device.</li>
      </ol>
      <form onSubmit={submit} className="flex flex-col gap-2">
        <label className="sr-only" htmlFor="tmdb-key">
          TMDB API key
        </label>
        <input
          id="tmdb-key"
          className="field"
          autoComplete="off"
          autoCorrect="off"
          spellCheck={false}
          placeholder="Paste your TMDB API key"
          value={key}
          onChange={(e) => {
            setKey(e.target.value);
            setStatus('idle');
          }}
        />
        <button type="submit" className="btn btn-primary" disabled={!key.trim() || status === 'checking'}>
          {status === 'checking' ? 'Checking…' : 'Save and play'}
        </button>
        {status === 'invalid' && <p className="text-sm text-pink">TMDB didn't accept that key. Check you copied all of it.</p>}
        {status === 'offline' && <p className="text-sm text-pink">Couldn't reach TMDB. Check your connection and try again.</p>}
      </form>
    </div>
  );
}
