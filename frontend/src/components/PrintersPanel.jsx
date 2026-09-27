import { useState } from 'react';
import { api, time } from '../api.js';
import useAsync from './useAsync.js';
import { useLive } from '../live.jsx';

const blank = { name: '', host: '', port: 9100, station: '', active: true, utf8: false };

/** Kitchen/bar receipt printers. Each can print the whole order or just one station's dishes. */
export default function PrintersPanel({ rid }) {
  const { data: printers, reload, error } = useAsync(() => api(`/restaurants/${rid}/printers`), [rid]);
  useLive(['PRINTER_CHANGED'], (ev) => { if (ev.restaurantId === Number(rid)) reload(); });
  const [form, setForm] = useState(blank);
  const [editingId, setEditingId] = useState(null);
  const [msg, setMsg] = useState(null);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });
  const run = async (fn, ok) => { try { setMsg(null); await fn(); reload(); if (ok) setMsg({ ok }); } catch (e) { setMsg({ error: e.message }); } };

  const save = (e) => {
    e.preventDefault();
    const body = { ...form, port: Number(form.port), station: form.station || null };
    run(async () => {
      await api(editingId ? `/printers/${editingId}` : `/restaurants/${rid}/printers`, { method: editingId ? 'PUT' : 'POST', body });
      setForm(blank); setEditingId(null);
    }, 'Printer saved');
  };

  return (
    <>
      <p className="muted small">
        Add the network receipt printers in your kitchen and bar (ESC/POS, like Epson TM printers — usually port 9100).
        Give a printer a station to print only those dishes, e.g. a BAR printer for drinks. Dockets print automatically
        when an order is sent.
      </p>
      {(msg?.error || error) && <div className="error">{msg?.error || error}</div>}
      {msg?.ok && <div className="success">{msg.ok}</div>}
      <div className="card list">
        {printers?.length === 0 && <p className="muted">No printers yet — dockets only show on the kitchen screen.</p>}
        {printers?.map((p) => (
          <div className={`row wrap ${p.active ? '' : 'faded'}`} key={p.id}>
            <div>
              <b>{p.name}</b> <span className="pill small">{p.station || 'All stations'}</span>
              <div className="muted small">{p.host}:{p.port}{p.utf8 ? ' · UTF-8' : ''} · last printed {p.lastPrintedAt ? time(p.lastPrintedAt) : 'never'}</div>
              {p.lastError && <div className="error small">Last attempt failed: {p.lastError}</div>}
            </div>
            <div className="actions">
              <button className="btn small" onClick={() => run(async () => {
                const r = await api(`/printers/${p.id}/test`, { method: 'POST' });
                if (r.lastError) throw new Error(`Test failed: ${r.lastError}`);
              }, `Test docket sent to ${p.name}`)}>Test print</button>
              <button className="btn small ghost" onClick={() => { setEditingId(p.id); setForm({ ...p, station: p.station || '' }); }}>Edit</button>
              <button className="btn small danger-ghost" onClick={() => confirm(`Remove ${p.name}?`) && run(() => api(`/printers/${p.id}`, { method: 'DELETE' }))}>Remove</button>
            </div>
          </div>
        ))}
      </div>
      <form className="card stack" onSubmit={save}>
        <h3>{editingId ? 'Edit printer' : 'Add printer'}</h3>
        <div className="grid-2">
          <label>Name<input value={form.name} onChange={set('name')} placeholder="Kitchen printer" required /></label>
          <label>IP address<input value={form.host} onChange={set('host')} placeholder="192.168.1.50" required /></label>
          <label>Port<input type="number" min={9100} max={9199} value={form.port} onChange={set('port')} required /></label>
          <label>Station <span className="muted small">(empty = whole order)</span>
            <input value={form.station} onChange={set('station')} list="station-list" placeholder="KITCHEN, GRILL, BAR…" />
          </label>
        </div>
        <datalist id="station-list"><option>KITCHEN</option><option>GRILL</option><option>BAR</option><option>DESSERT</option></datalist>
        <label className="check small"><input type="checkbox" checked={form.active} onChange={set('active')} /> Active</label>
        <label className="check small"><input type="checkbox" checked={form.utf8} onChange={set('utf8')} /> Printer supports UTF-8 (needed to print kitchen-language names)</label>
        <div className="actions">
          <button className="btn primary">{editingId ? 'Save' : 'Add printer'}</button>
          {editingId && <button type="button" className="btn ghost" onClick={() => { setEditingId(null); setForm(blank); }}>Cancel</button>}
        </div>
      </form>
    </>
  );
}
