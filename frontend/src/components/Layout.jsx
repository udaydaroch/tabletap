import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { flushOutbox, outbox, useOutbox, useOutboxFlusher } from '../outbox.js';
import { useLive } from '../live.jsx';
import Modal from './Modal.jsx';
import { useAuth } from '../auth.jsx';
import { useLiveStatus } from '../live.jsx';

export default function Layout({ children }) {
  const { me, logout, stopImpersonating } = useAuth();
  const manager = me.role === 'OWNER' || me.role === 'ADMIN';
  const live = useLiveStatus();
  const queue = useOutbox(me.id);
  const [showQueue, setShowQueue] = useState(false);
  useOutboxFlusher(me.id);
  useLive(['RESYNC'], flushOutbox); // connection is back — send anything waiting

  // only show the offline banner if the connection has been down for a few seconds
  const [offline, setOffline] = useState(false);
  useEffect(() => {
    if (live) { setOffline(false); return undefined; }
    const t = setTimeout(() => setOffline(true), 4000);
    return () => clearTimeout(t);
  }, [live]);
  const failed = queue.filter((q) => q.error);
  return (
    <div className="app">
      {me.impersonatorId && (
        <div className="imp-banner">
          Viewing as <b>{me.fullName}</b> ({me.role.toLowerCase()})
          <button className="btn small light" onClick={stopImpersonating}>Back to admin</button>
        </div>
      )}
      <div className="app-top">
      {offline && (
        <div className="offline-banner" role="status">
          Offline — can't reach the TableTap server. New orders are saved on this device and sent automatically.
        </div>
      )}
      {queue.length > 0 && (
        <button className={`queue-banner ${failed.length ? 'failed' : ''}`} onClick={() => setShowQueue(true)}>
          {failed.length
            ? `⚠ ${failed.length} order${failed.length > 1 ? 's' : ''} couldn't be sent — tap to review`
            : `⏳ ${queue.length} order${queue.length > 1 ? 's' : ''} waiting to send`}
        </button>
      )}
      <header className="topbar">
        <NavLink to="/" className="brand">TableTap</NavLink>
        <nav>
          <NavLink to="/" end>Home</NavLink>
          {manager && <NavLink to="/tree">Team</NavLink>}
          {me.role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
        </nav>
        <div className="who">
          <span className={`live-dot ${live ? 'on' : ''}`} title={live ? 'Live updates on' : 'Reconnecting…'} />
          <span className="who-name">{me.fullName}</span>
          <button className="btn small ghost" onClick={() => (!queue.length || confirm(
            `${queue.length} order${queue.length > 1 ? 's are' : ' is'} still waiting to send. They stay on this phone and send when you log in again. Log out anyway?`)) && logout()}>Log out</button>
        </div>
      </header>
      </div>
      {showQueue && <QueueModal items={queue} onClose={() => setShowQueue(false)} />}
      <main className="container">{children}</main>
    </div>
  );
}

function QueueModal({ items, onClose }) {
  return (
    <Modal title="Orders on this device" onClose={onClose}>
      {items.length === 0 && <p className="muted">Everything has been sent.</p>}
      <div className="list">
        {items.map((q) => (
          <div className="row wrap" key={q.id}>
            <div>
              <b>Table {q.label}</b> <span className="muted small">{new Date(q.queuedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</span>
              <div className="muted small">{q.body.lines.reduce((s, l) => s + l.quantity, 0)} items</div>
              {q.error ? <div className="error small">{q.error}</div> : <div className="muted small">Waiting for connection…</div>}
            </div>
            {q.error && (
              <div className="actions">
                <button className="btn small" onClick={() => outbox.retry(q.id)}>Retry</button>
                <button className="btn small danger-ghost" onClick={() => confirm('Discard this order? It was never sent to the kitchen.') && outbox.remove(q.id)}>Discard</button>
              </div>
            )}
          </div>
        ))}
      </div>
      <button className="btn" onClick={flushOutbox}>Try sending now</button>
    </Modal>
  );
}
