import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, clearCache, tokens } from './api.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [me, setMe] = useState(null);
  const [loading, setLoading] = useState(true);
  const [unreachable, setUnreachable] = useState(false);

  const refresh = useCallback(async () => {
    if (!tokens.get()) { setMe(null); setLoading(false); return; }
    try {
      setMe(await api('/auth/me')); // falls back to the copy saved on this device when offline
      setUnreachable(false);
      setLoading(false);
    } catch (e) {
      if (e.offline) {
        // server down and nothing saved yet: keep the session and keep trying
        setUnreachable(true);
        setTimeout(refresh, 5000);
        return;
      }
      tokens.clear();
      setMe(null);
      setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  const accept = (res) => { tokens.set(res.token); setMe(res.me); return res.me; };

  const value = {
    me,
    loading,
    unreachable,
    login: async (email, password) => accept(await api('/auth/login', { method: 'POST', body: { email, password } })),
    register: async (form) => accept(await api('/auth/register-owner', { method: 'POST', body: form })),
    logout: () => { tokens.clear(); setMe(null); },
    // Admin "log in as": keep the admin token aside so we can switch back.
    impersonate: async (userId) => {
      const res = await api(`/admin/impersonate/${userId}`, { method: 'POST' });
      tokens.setAdmin(tokens.get());
      clearCache();
      return accept(res);
    },
    stopImpersonating: async () => {
      tokens.set(tokens.getAdmin());
      tokens.setAdmin(null);
      clearCache();
      await refresh();
    },
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
