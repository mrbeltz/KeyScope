export function SetupNotice() {
  return (
    <div className="glass neon-frame mx-auto my-8 max-w-lg rounded-xl p-5 leading-relaxed">
      <h2 className="mb-2 text-lg text-pink">TMDB key needed</h2>
      <p className="text-sm">
        Daily Frame pulls its stills from The Movie Database. Add your key to a <code className="text-cyan">.env</code> file in the
        project root and restart the dev server (or set it in your host's environment variables and redeploy):
      </p>
      <pre className="my-3 overflow-x-auto rounded bg-night/80 p-3 text-sm text-cyan">VITE_TMDB_API_KEY=your_key_here</pre>
      <p className="text-sm">
        Get one free at{' '}
        <a className="text-cyan underline" href="https://www.themoviedb.org/settings/api" target="_blank" rel="noreferrer">
          themoviedb.org/settings/api
        </a>
        . The README walks through it.
      </p>
    </div>
  );
}
