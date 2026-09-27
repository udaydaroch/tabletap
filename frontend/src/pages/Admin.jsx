import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Modal from '../components/Modal.jsx';
import { api, money, time } from '../api.js';
import { useAuth } from '../auth.jsx';
import useAsync from '../components/useAsync.js';
import { useLive } from '../live.jsx';

export default function Admin() {
  const { impersonate } = useAuth();
  const nav = useNavigate();
  const { data: owners, reload, error } = useAsync(() => api('/admin/owners'), []);
  useLive(['OWNER_CHANGED', 'RESTAURANT_CHANGED', 'ORDER_CREATED'], reload);

  const loginAs = async (id) => { await impersonate(id); nav('/'); };
  const toggle = async (o) => { await api(`/admin/users/${o.id}/active`, { method: 'PATCH', body: { active: !o.active } }); reload(); };

  return (
    <>
      <h2>Admin · Owners</h2>
      <p className="muted small">Every owner account on the platform. “Log in as” lets you act as that owner (audited).</p>
      {error && <div className="error">{error}</div>}
      <div className="card list">
        {owners?.map((o) => (
          <div className={`row wrap ${o.active ? '' : 'faded'}`} key={o.id}>
            <div>
              <b>{o.fullName}</b>
              <div className="muted small">{o.email} · {o.restaurantCount} restaurant{o.restaurantCount === 1 ? '' : 's'} · joined {time(o.createdAt)} · <b>{money(o.monthEstimate)}</b> this month</div>
            </div>
            <div className="actions">
              <button className="btn small primary" onClick={() => loginAs(o.id)} disabled={!o.active}>Log in as</button>
              <button className="btn small ghost" onClick={() => toggle(o)}>{o.active ? 'Suspend' : 'Reactivate'}</button>
            </div>
          </div>
        ))}
        {owners?.length === 0 && <p className="muted">No owners have signed up yet.</p>}
      </div>
      <CloudSync />
      <MonthlyBilling />
    </>
  );
}

/** This server's outgoing sync status + (when this server is the cloud) the restaurant computers allowed to send data. */
function CloudSync() {
  const { data: status } = useAsync(() => api('/admin/sync/status'), []);
  const { data: sites, reload } = useAsync(() => api('/admin/sites'), []);
  const [name, setName] = useState('');
  const [created, setCreated] = useState(null);
  const [err, setErr] = useState(null);

  const add = async (e) => {
    e.preventDefault();
    try { setCreated(await api('/admin/sites', { method: 'POST', body: { name } })); setName(''); reload(); } catch (e2) { setErr(e2.message); }
  };
  const remove = async (site) => {
    if (!confirm(`Remove ${site.name}? Its synced records are deleted and its key stops working.`)) return;
    await api(`/admin/sites/${site.id}`, { method: 'DELETE' });
    reload();
  };

  return (
    <>
      <h2>Cloud sync</h2>
      <div className="card">
        <h3>This server</h3>
        {status && (status.enabled ? (
          <p className="small">Sending changes to <b>{status.target}</b> · {status.pending} waiting ·
            last sent {status.lastSentAt ? time(status.lastSentAt) : 'never'}
            {status.lastError && <span className="error-text"> · last attempt failed: {status.lastError}</span>}</p>
        ) : (
          <p className="muted small">Off. To send this restaurant computer's orders, payments and shifts to a cloud TableTap,
            set CLOUD_SYNC_URL and CLOUD_SYNC_KEY (see the README).</p>
        ))}
      </div>
      <div className="card list">
        <h3>Restaurant computers sending data here</h3>
        {sites?.length === 0 && <p className="muted small">None yet. Add one to get its key.</p>}
        {sites?.map((s) => (
          <div className="row wrap" key={s.id}>
            <div>
              <b>{s.name}</b>
              <div className="muted small">{s.recordCount} records · last sync {s.lastSyncAt ? time(s.lastSyncAt) : 'never'}</div>
            </div>
            <button className="btn small danger-ghost" onClick={() => remove(s)}>Remove</button>
          </div>
        ))}
        <form className="inline-form" onSubmit={add}>
          <input placeholder="e.g. Demo Bistro – kitchen PC" value={name} onChange={(e) => setName(e.target.value)} required maxLength={80} />
          <button className="btn primary">Add computer</button>
        </form>
        {err && <div className="error">{err}</div>}
      </div>
      {created && (
        <Modal title="Key for this computer" onClose={() => setCreated(null)}>
          <p>Put this in the restaurant computer's <code>.env</code> as <code>CLOUD_SYNC_KEY</code>. <b>It is only shown once.</b></p>
          <div className="site-key">{created.key}</div>
          <button className="btn" onClick={() => navigator.clipboard?.writeText(created.key)}>Copy</button>
        </Modal>
      )}
    </>
  );
}

/** Charges owners for a month through the billing provider (runs automatically on the 1st). */
function MonthlyBilling() {
  const lastMonth = new Date(new Date().getFullYear(), new Date().getMonth() - 1, 1);
  const [month, setMonth] = useState(`${lastMonth.getFullYear()}-${String(lastMonth.getMonth() + 1).padStart(2, '0')}`);
  const [results, setResults] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);
  const run = async () => {
    if (!confirm(`Charge every owner for ${month}? Owners already charged for that month are skipped.`)) return;
    setBusy(true); setErr(null);
    try { setResults(await api(`/admin/billing/run?month=${month}`, { method: 'POST' })); } catch (e) { setErr(e.message); } finally { setBusy(false); }
  };
  return (
    <>
      <h2>Monthly billing</h2>
      <div className="card stack">
        <p className="muted small">Runs automatically on the 1st of each month for the previous month. Run it by hand to retry failures.
          Needs STRIPE_SECRET_KEY on the server — without it, charges are calculated but skipped.</p>
        <div className="inline-form">
          <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} aria-label="Month to bill" />
          <button className="btn primary" onClick={run} disabled={busy}>{busy ? 'Charging…' : 'Run billing'}</button>
        </div>
        {err && <div className="error">{err}</div>}
        {results && (
          <div className="list">
            {results.length === 0 && <p className="muted small">No active owners.</p>}
            {results.map((r) => (
              <div className="row wrap" key={r.owner}>
                <span>{r.owner}</span>
                <span><b>{money(r.amount)}</b> <span className={`pill small ${r.status === 'CHARGED' || r.status === 'ALREADY_CHARGED' ? 'green' : r.status === 'FAILED' ? 'warn' : ''}`}>{r.status.replace('_', ' ').toLowerCase()}</span>
                  {r.error && <span className="error-text small"> {r.error}</span>}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </>
  );
}
