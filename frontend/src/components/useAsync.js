import { useCallback, useEffect, useState } from 'react';

/** Tiny data-loading hook: const { data, error, reload } = useAsync(() => api('/x'), [deps]) */
export default function useAsync(fn, deps) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(fn, deps);
  const reload = useCallback(async () => {
    try { setData(await load()); setError(null); } catch (e) { setError(e.message); }
  }, [load]);
  useEffect(() => { reload(); }, [reload]);
  return { data, error, reload, setData };
}
