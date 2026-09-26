import { useState } from 'react';
import { api, money } from '../api.js';
import useAsync from './useAsync.js';
import Modal from './Modal.jsx';

const daysLeftThisMonth = () => {
  const now = new Date();
  const days = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  return { left: days - now.getDate() + 1, days };
};

/** Explains the paid add-on and its price before the owner turns it on. */
export function UpgradeModal({ rid, price, reason, onClose, onUpgraded }) {
  const [agree, setAgree] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);
  const { left, days } = daysLeftThisMonth();
  const thisMonth = (Number(price) * left) / days;

  const upgrade = async () => {
    setBusy(true); setErr(null);
    try {
      const plan = await api(`/restaurants/${rid}/floor/tier`, { method: 'POST', body: { advanced: true, acceptedMonthlyPrice: price } });
      onUpgraded(plan);
    } catch (e) { setErr(e.message); } finally { setBusy(false); }
  };

  return (
    <Modal title="Floor plan add-on" onClose={onClose}>
      {reason && <div className="notice">🔒 {reason}</div>}
      <div className="price-box">
        <div><span className="stat">{money(price)}</span><span className="muted"> / month per restaurant</span></div>
        <div className="muted small">This month you'll be charged {money(thisMonth)} ({left} of {days} days left). Cancel any time.</div>
      </div>
      <ul className="feature-list">
        <li>Design your restaurant: any number of areas and tables of any shape</li>
        <li>Curved walls and booths, bar, kitchen, doors</li>
        <li>Ready-made layouts for cafés, bistros, bars and fine dining</li>
        <li>Waiters tap tables to order and see live table status</li>
      </ul>
      <label className="check"><input type="checkbox" checked={agree} onChange={(e) => setAgree(e.target.checked)} />
        I agree to add {money(price)}/month to this restaurant's bill</label>
      {err && <div className="error">{err}</div>}
      <div className="actions">
        <button className="btn primary" disabled={!agree || busy} onClick={upgrade}>{busy ? 'Upgrading…' : `Upgrade for ${money(price)}/month`}</button>
        <button className="btn ghost" onClick={onClose}>Not now</button>
      </div>
    </Modal>
  );
}

/** Pick a ready-made layout. Advanced ones are locked until the add-on is on. */
export function LayoutsModal({ rid, advanced, price, dirty, onClose, onApplied, onNeedUpgrade }) {
  const { data: templates } = useAsync(() => api('/floor-templates'), []);
  const [err, setErr] = useState(null);

  const apply = async (t) => {
    if (t.advanced && !advanced) { onNeedUpgrade(`The “${t.name}” layout is part of Advanced floor plans`); return; }
    if (!confirm(`Replace your current floor plan with “${t.name}”?${dirty ? ' Your unsaved changes will be lost.' : ''} Past orders are kept.`)) return;
    try {
      onApplied(await api(`/restaurants/${rid}/floor/template`, { method: 'POST', body: { key: t.key } }));
    } catch (e) { setErr(e.message); }
  };

  return (
    <Modal title="Choose a layout" onClose={onClose}>
      <p className="muted small">Start from a layout that matches your restaurant, then adjust it.</p>
      {err && <div className="error">{err}</div>}
      <div className="list">
        {templates?.map((t) => (
          <button key={t.key} className="layout-option" onClick={() => apply(t)}>
            <div>
              <b>{t.name}</b>
              <div className="muted small">{t.description}</div>
            </div>
            <span className={`pill small ${t.advanced ? '' : 'green'}`}>
              {!t.advanced ? 'Free' : advanced ? 'Included' : `🔒 ${money(price)}/mo`}
            </span>
          </button>
        ))}
      </div>
    </Modal>
  );
}

/** Switch back to the free tier (only allowed when the layout fits basic limits — the server checks). */
export function DowngradeLink({ rid, onChanged }) {
  const [err, setErr] = useState(null);
  const go = async () => {
    if (!confirm('Cancel the floor plan add-on? Your floor plan will be deleted and waiters will go back to typing table numbers. Past orders are kept. You will stop being charged.')) return;
    try { onChanged(await api(`/restaurants/${rid}/floor/tier`, { method: 'POST', body: { advanced: false } })); setErr(null); }
    catch (e) { setErr(e.message); }
  };
  return (
    <>
      <button className="btn small ghost" onClick={go}>Cancel add-on</button>
      {err && <div className="error small">{err}</div>}
    </>
  );
}
