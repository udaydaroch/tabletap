import { useEffect, useState } from 'react';
import { money } from '../api.js';
import { useAuth } from '../auth.jsx';
import Modal from '../components/Modal.jsx';
import { Link, useParams } from 'react-router-dom';
import { api, time } from '../api.js';
import useAsync from '../components/useAsync.js';
import { useLive } from '../live.jsx';

const NEXT = { SENT: 'IN_PROGRESS', IN_PROGRESS: 'READY', READY: 'SERVED' };
const LABEL = { SENT: 'Start', IN_PROGRESS: 'Mark ready', READY: 'Served' };

/** Live kitchen queue: pushed updates, plus a slow poll as a safety net. Finished orders move to "Today". */
export default function Kitchen() {
  const { id } = useParams();
  const { data: orders, reload, error } = useAsync(() => api(`/restaurants/${id}/orders?open=true`), [id]);

  useLive(['ORDER_CREATED', 'ORDER_UPDATED'], (ev) => { if (ev.restaurantId === Number(id)) reload(); });
  useEffect(() => {
    const t = setInterval(reload, 30000);
    return () => clearInterval(t);
  }, [reload]);

  const { me } = useAuth();
  const [showStock, setShowStock] = useState(false);
  const canEditStock = me.role !== 'WAITER';
  const move = async (o, status) => { await api(`/orders/${o.id}/status`, { method: 'PATCH', body: { status } }); reload(); };

  return (
    <>
      <div className="page-head">
        <h2>Kitchen queue</h2>
        <div className="actions">
          {canEditStock && <button className="btn" onClick={() => setShowStock(true)}>Sold out items</button>}
          <Link className="btn" to={`/restaurants/${id}/today`}>Today's history</Link>
          {me.role !== 'CHEF' && <Link className="btn" to={`/restaurants/${id}/order`}>Take order</Link>}
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      {orders?.length === 0 && <p className="muted">No open orders. 🎉</p>}
      <div className="docket-grid">
        {orders?.map((o) => (
          <article key={o.id} className={`docket status-${o.status.toLowerCase()}`}>
            <header>
              <b>Table {o.tableLabel}</b>
              <span className="muted small">#{o.id} · {time(o.createdAt)}</span>
            </header>
            <ul>
              {o.lines.map((l, idx) => (
                <li key={idx}>
                  <b>{l.quantity}×</b> {l.itemName}
                  {l.options.length > 0 && <div className="small">{l.options.join(', ')}</div>}
                  {l.note && <div className="small note">“{l.note}”</div>}
                </li>
              ))}
            </ul>
            <footer>
              <span className="pill small">{o.status.replace('_', ' ')}</span>
              <span className="muted small">{o.waiterName}</span>
            </footer>
            <div className="actions">
              <button className="btn primary small" onClick={() => move(o, NEXT[o.status])}>{LABEL[o.status]}</button>
              <button className="btn ghost small" onClick={() => confirm('Cancel this order?') && move(o, 'CANCELLED')}>Cancel</button>
            </div>
          </article>
        ))}
      </div>
      {showStock && <StockModal rid={id} onClose={() => setShowStock(false)} />}
    </>
  );
}

/** Chefs mark dishes sold out ("86") — waiters' menus update instantly. */
function StockModal({ rid, onClose }) {
  const { data: menu, reload } = useAsync(() => api(`/restaurants/${rid}/menu`), [rid]);
  useLive(['MENU_CHANGED'], (ev) => { if (ev.restaurantId === Number(rid)) reload(); });
  const toggle = async (i) => { await api(`/menu/items/${i.id}/available`, { method: 'PATCH', body: { available: !i.available } }); reload(); };
  return (
    <Modal title="Sold out items" onClose={onClose}>
      <p className="muted small">Switch a dish off when you run out. Waiters can't order it until you switch it back on.</p>
      {menu?.map((c) => (
        <div key={c.id}>
          <h4>{c.name}</h4>
          {c.items.map((i) => (
            <label key={i.id} className="row stock-row">
              <span className={i.available ? '' : 'faded'}>{i.name} <span className="muted small">{money(i.price)}</span></span>
              <span className="switch">
                <input type="checkbox" checked={i.available} onChange={() => toggle(i)} aria-label={`${i.name} available`} />
                <span>{i.available ? 'Available' : 'Sold out'}</span>
              </span>
            </label>
          ))}
        </div>
      ))}
    </Modal>
  );
}
