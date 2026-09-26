const TOKEN = 'tt_token';
const CACHE_PREFIX = 'tt_cache:';
const ADMIN_TOKEN = 'tt_admin_token';

function read(key) { try { return localStorage.getItem(key); } catch { return null; } }
function write(key, v) { try { v ? localStorage.setItem(key, v) : localStorage.removeItem(key); } catch { /* ignore */ } }

export const tokens = {
  get: () => read(TOKEN),
  set: (t) => write(TOKEN, t),
  getAdmin: () => read(ADMIN_TOKEN),
  setAdmin: (t) => write(ADMIN_TOKEN, t),
  clear: () => { write(TOKEN, null); write(ADMIN_TOKEN, null); clearCache(); },
};

/** Thrown when the TableTap server can't be reached (Wi-Fi drop, server restarting). */
export class OfflineError extends Error {
  constructor() {
    super("Can't reach the TableTap server");
    this.offline = true;
  }
}

/*
 * Last good response of every GET, kept on the device so screens still work while the
 * server is unreachable. Cleared on logout / switching user.
 */
function readCache(path) {
  try { const v = localStorage.getItem(CACHE_PREFIX + path); return v === null ? undefined : JSON.parse(v); } catch { return undefined; }
}
function writeCache(path, data) {
  try { localStorage.setItem(CACHE_PREFIX + path, JSON.stringify(data)); } catch { /* quota full — not critical */ }
}
export function clearCache() {
  try {
    Object.keys(localStorage).filter((k) => k.startsWith(CACHE_PREFIX)).forEach((k) => localStorage.removeItem(k));
  } catch { /* ignore */ }
}

/** Random id that also works on plain-http LAN addresses (where crypto.randomUUID is unavailable). */
export function randomId() {
  if (window.crypto?.randomUUID && window.isSecureContext) return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('').replace(/(.{8})(.{4})(.{4})(.{4})(.{12})/, '$1-$2-$3-$4-$5');
}

export async function api(path, { method = 'GET', body } = {}) {
  const token = tokens.get();
  let res;
  try {
    res = await fetch('/api' + path, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    res = null;
  }
  if (!res || [502, 503, 504].includes(res.status)) {
    if (method === 'GET') {
      const cached = readCache(path);
      if (cached !== undefined) return cached;
    }
    throw new OfflineError();
  }
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  if (res.status === 401 && token) {
    // session revoked (disabled, password changed, owner suspended) or expired
    tokens.clear();
    window.location.assign('/login');
  }
  if (!res.ok) {
    const err = new Error(data?.message || `Request failed (${res.status})`);
    err.status = res.status;
    throw err;
  }
  if (method === 'GET') writeCache(path, data);
  return data;
}

export const money = (n) => `$${Number(n ?? 0).toFixed(2)}`;
export const time = (iso) => iso ? new Date(iso).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' }) : '—';
export const hm = (mins) => `${Math.floor(mins / 60)}h ${mins % 60}m`;

export const localTz = () => Intl.DateTimeFormat().resolvedOptions().timeZone;
export const ymd = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/**
 * Server-Sent Events over fetch (so we can send the Authorization header).
 * Reconnects with backoff; calls onEvent({type,...}) for each change.
 */
export function openLiveStream(onEvent, onStatus) {
  let stopped = false;
  let ctrl;
  (async () => {
    let delay = 1000;
    let first = true;
    while (!stopped) {
      try {
        ctrl = new AbortController();
        const res = await fetch('/api/events', {
          headers: { Authorization: `Bearer ${tokens.get()}`, Accept: 'text/event-stream' },
          signal: ctrl.signal,
        });
        if (res.status === 401) { tokens.clear(); window.location.assign('/login'); return; }
        if (!res.ok || !res.body) throw new Error('stream failed');
        onStatus(true);
        if (!first) onEvent({ type: 'RESYNC' }); // we may have missed events while disconnected
        first = false;
        delay = 1000;
        const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
        let buf = '';
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          buf += value;
          let i;
          while ((i = buf.indexOf('\n\n')) >= 0) {
            const chunk = buf.slice(0, i);
            buf = buf.slice(i + 2);
            let name = 'message', data = '';
            for (const line of chunk.split('\n')) {
              if (line.startsWith('event:')) name = line.slice(6).trim();
              else if (line.startsWith('data:')) data += line.slice(5).trim();
            }
            if (name === 'change' && data) onEvent(JSON.parse(data));
          }
        }
      } catch { /* fall through to reconnect */ }
      if (stopped) return;
      onStatus(false);
      await new Promise((r) => setTimeout(r, delay));
      delay = Math.min(delay * 2, 30000);
    }
  })();
  return () => { stopped = true; ctrl?.abort(); };
}
