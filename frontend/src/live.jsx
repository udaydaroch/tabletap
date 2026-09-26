import { createContext, useContext, useEffect, useRef, useState } from 'react';
import { openLiveStream } from './api.js';
import { useAuth } from './auth.jsx';

const LiveContext = createContext({ connected: false, listeners: new Set() });

/** One live connection per logged-in session; pages subscribe with useLive(). */
export function LiveProvider({ children }) {
  const { me } = useAuth();
  const [connected, setConnected] = useState(false);
  const listeners = useRef(new Set());
  const sessionKey = me ? `${me.id}:${me.impersonatorId ?? ''}` : null;

  useEffect(() => {
    if (!sessionKey) return undefined;
    return openLiveStream((ev) => listeners.current.forEach((fn) => fn(ev)), setConnected);
  }, [sessionKey]);

  return <LiveContext.Provider value={{ connected, listeners: listeners.current }}>{children}</LiveContext.Provider>;
}

/** Run `callback` whenever one of `types` happens (or after a reconnect). */
export function useLive(types, callback) {
  const { listeners } = useContext(LiveContext);
  const cb = useRef(callback);
  cb.current = callback;
  const key = types.join(',');
  useEffect(() => {
    const wanted = new Set(key.split(','));
    const fn = (ev) => { if (ev.type === 'RESYNC' || wanted.has(ev.type)) cb.current(ev); };
    listeners.add(fn);
    return () => listeners.delete(fn);
  }, [key, listeners]);
}

export const useLiveStatus = () => useContext(LiveContext).connected;
