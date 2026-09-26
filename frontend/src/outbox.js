import { useEffect, useState } from 'react';
import { api } from './api.js';

/**
 * Orders that couldn't reach the server yet. Stored on the device and re-sent automatically.
 * Each carries a clientRequestId, so a resend never creates a duplicate in the kitchen.
 */
const KEY = 'tt_outbox';
const listeners = new Set();
let currentUserId = null; // queued orders are only sent by the waiter who took them

function load() {
  try { return JSON.parse(localStorage.getItem(KEY) || '[]'); } catch { return []; }
}
function save(items) {
  try { localStorage.setItem(KEY, JSON.stringify(items)); } catch { /* ignore */ }
  listeners.forEach((fn) => fn(items));
}

export const outbox = {
  list: load,
  setUser(id) { currentUserId = id; },
  add(item) { save([...load(), { ...item, userId: currentUserId, queuedAt: Date.now(), error: null }]); },
  remove(id) { save(load().filter((i) => i.id !== id)); },
  retry(id) { save(load().map((i) => (i.id === id ? { ...i, error: null } : i))); flushOutbox(); },
  clear() { save([]); },
};

let flushing = false;

/** Try to send everything waiting. Stops at the first connection failure. */
export async function flushOutbox() {
  if (flushing) return;
  flushing = true;
  try {
    for (const item of load().filter((i) => !i.error && i.userId === currentUserId)) {
      try {
        await api(`/restaurants/${item.rid}/orders`, { method: 'POST', body: item.body });
        outbox.remove(item.id);
      } catch (e) {
        if (e.offline) break; // still offline — try again later
        // rejected by the server (e.g. dish sold out, clocked out): keep it and tell the waiter
        save(load().map((i) => (i.id === item.id ? { ...i, error: e.message } : i)));
      }
    }
  } finally {
    flushing = false;
  }
}

export function useOutbox(userId) {
  const mine = (list) => list.filter((i) => i.userId === userId);
  const [items, setItems] = useState(() => mine(load()));
  useEffect(() => {
    const update = (list) => setItems(mine(list));
    update(load());
    listeners.add(update);
    const onStorage = (e) => { if (e.key === KEY) update(load()); }; // other tabs
    window.addEventListener('storage', onStorage);
    return () => { listeners.delete(update); window.removeEventListener('storage', onStorage); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId]);
  return items;
}

/** Keeps retrying in the background while the app is open. */
export function useOutboxFlusher(userId) {
  useEffect(() => {
    outbox.setUser(userId);
    flushOutbox();
    const t = setInterval(flushOutbox, 8000);
    window.addEventListener('online', flushOutbox);
    return () => { clearInterval(t); window.removeEventListener('online', flushOutbox); };
  }, [userId]);
}
