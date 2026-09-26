import { useState } from 'react';
import { api } from '../api.js';
import useAsync from './useAsync.js';
import { useLive } from '../live.jsx';

const fmt = (n) => Number(n).toLocaleString(undefined, { maximumFractionDigits: 3 });

/** Ingredients, restocking and low-stock alerts. Owners manage ingredients; chefs can restock. */
export default function StockPanel({ rid, canManage }) {
  const { data: items, reload, error } = useAsync(() => api(`/restaurants/${rid}/ingredients`), [rid]);
  const { data: alerts, reload: reloadAlerts } = useAsync(() => api(`/restaurants/${rid}/stock-alerts`), [rid]);
  useLive(['STOCK_CHANGED', 'ORDER_CREATED'], (ev) => { if (ev.restaurantId === Number(rid)) { reload(); reloadAlerts(); } });
  const [amounts, setAmounts] = useState({});
  const [form, setForm] = useState({ name: '', unit: 'portions', stock: '', lowThreshold: '' });
  const [editing, setEditing] = useState(null);
  const [err, setErr] = useState(null);
  const run = async (fn) => { try { setErr(null); await fn(); reload(); reloadAlerts(); } catch (e) { setErr(e.message); } };

  const restock = (i, sign = 1) => run(async () => {
    const amount = Number(amounts[i.id]);
    if (!amount) throw new Error('Enter an amount first');
    await api(`/ingredients/${i.id}/restock`, { method: 'POST', body: { amount: sign * amount } });
    setAmounts({ ...amounts, [i.id]: '' });
  });

  return (
    <>
      <p className="muted small">
        Link ingredients to dishes in the menu (edit a dish → Recipe). Every order uses them up; you get an alert when
        something runs low, and a dish switches itself off when it can't be made — and back on when you restock.
      </p>
      {(err || error) && <div className="error">{err || error}</div>}

      {alerts?.length > 0 && (
        <div className="card alert-card">
          <h3>Stock alerts</h3>
          {alerts.map((a) => (
            <div className="row" key={a.id}>
              <span>⚠ {a.message}</span>
              <button className="btn small ghost" onClick={() => run(() => api(`/stock-alerts/${a.id}/acknowledge`, { method: 'POST' }))}>Dismiss</button>
            </div>
          ))}
        </div>
      )}

      <div className="card list">
        {items?.length === 0 && <p className="muted">No ingredients yet.</p>}
        {items?.map((i) => (
          <div className={`row wrap stock-item ${i.low ? 'low' : ''}`} key={i.id}>
            <div>
              <b>{i.name}</b> {i.low && <span className="pill small warn">{Number(i.stock) <= 0 ? 'Out' : 'Low'}</span>}
              <div className="muted small">{fmt(i.stock)} {i.unit} in stock · alert at {fmt(i.lowThreshold)}</div>
            </div>
            <div className="actions">
              <input className="qty-input" type="number" step="any" min="0" inputMode="decimal" placeholder="Amount"
                value={amounts[i.id] || ''} onChange={(e) => setAmounts({ ...amounts, [i.id]: e.target.value })} aria-label={`Amount of ${i.name}`} />
              <button className="btn small primary" onClick={() => restock(i)}>+ Restock</button>
              <button className="btn small ghost" onClick={() => restock(i, -1)} title="Correct the count down (waste, spillage)">− Remove</button>
              {canManage && <button className="btn small ghost" onClick={() => setEditing(i)}>Edit</button>}
            </div>
          </div>
        ))}
      </div>

      {canManage && editing && (
        <form className="card stack" onSubmit={(e) => { e.preventDefault(); run(async () => {
          await api(`/ingredients/${editing.id}`, { method: 'PUT', body: { name: editing.name, unit: editing.unit, lowThreshold: Number(editing.lowThreshold) } });
          setEditing(null); }); }}>
          <h3>Edit {editing.name}</h3>
          <div className="grid-2">
            <label>Name<input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} required /></label>
            <label>Unit<input value={editing.unit} onChange={(e) => setEditing({ ...editing, unit: e.target.value })} /></label>
            <label>Alert when at or below<input type="number" step="any" min="0" value={editing.lowThreshold} onChange={(e) => setEditing({ ...editing, lowThreshold: e.target.value })} required /></label>
          </div>
          <div className="actions">
            <button className="btn primary">Save</button>
            <button type="button" className="btn ghost" onClick={() => setEditing(null)}>Cancel</button>
            <button type="button" className="btn danger-ghost" onClick={() => confirm(`Delete ${editing.name}? It is removed from every recipe.`)
              && run(async () => { await api(`/ingredients/${editing.id}`, { method: 'DELETE' }); setEditing(null); })}>Delete</button>
          </div>
        </form>
      )}

      {canManage && (
        <form className="card stack" onSubmit={(e) => { e.preventDefault(); run(async () => {
          await api(`/restaurants/${rid}/ingredients`, { method: 'POST', body: { ...form, stock: Number(form.stock || 0), lowThreshold: Number(form.lowThreshold || 0) } });
          setForm({ name: '', unit: 'portions', stock: '', lowThreshold: '' }); }); }}>
          <h3>Add ingredient</h3>
          <div className="grid-2">
            <label>Name<input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Ribeye" required /></label>
            <label>Unit<input value={form.unit} onChange={(e) => setForm({ ...form, unit: e.target.value })} placeholder="portions, kg, L" /></label>
            <label>In stock now<input type="number" step="any" min="0" value={form.stock} onChange={(e) => setForm({ ...form, stock: e.target.value })} /></label>
            <label>Alert when at or below<input type="number" step="any" min="0" value={form.lowThreshold} onChange={(e) => setForm({ ...form, lowThreshold: e.target.value })} /></label>
          </div>
          <button className="btn primary">Add</button>
        </form>
      )}
    </>
  );
}
