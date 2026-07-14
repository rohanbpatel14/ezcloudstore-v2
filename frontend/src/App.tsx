import { useEffect, useState } from 'react';
import { claims, completeLoginIfCallback, login, logout, signedIn } from './auth';
import { Dashboard } from './components/Dashboard';
import { AdminPanel } from './components/AdminPanel';

export default function App() {
  const [ready, setReady] = useState(false);
  const [authed, setAuthed] = useState(false);
  const [tab, setTab] = useState<'files' | 'admin'>('files');

  useEffect(() => {
    completeLoginIfCallback()
      .catch(() => {})
      .finally(() => {
        setAuthed(signedIn());
        setReady(true);
      });
  }, []);

  if (!ready) return null;

  const user = claims();
  const isAdmin = user?.groups.includes('admin') ?? false;

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          Ez<span>CloudStore</span>
        </div>
        <div>
          {authed ? (
            <>
              <span className="who">{user?.email ?? user?.sub}</span>
              <button className="btn" onClick={logout}>
                Sign out
              </button>
            </>
          ) : (
            <button className="btn primary" onClick={login}>
              Sign in
            </button>
          )}
        </div>
      </header>

      {!authed ? (
        <section className="hero">
          <h1>Your files, on your terms.</h1>
          <p>Upload, version, and share files — serverless, private, and fast.</p>
          <button className="btn primary" onClick={login}>
            Sign in to get started
          </button>
        </section>
      ) : (
        <>
          {isAdmin && (
            <nav className="tabs">
              <button className={tab === 'files' ? 'active' : ''} onClick={() => setTab('files')}>
                My files
              </button>
              <button className={tab === 'admin' ? 'active' : ''} onClick={() => setTab('admin')}>
                Admin
              </button>
            </nav>
          )}
          {tab === 'admin' && isAdmin ? <AdminPanel /> : <Dashboard />}
        </>
      )}
    </div>
  );
}
