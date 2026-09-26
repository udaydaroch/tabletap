import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, localTz, money, ymd } from '../api.js';
import useAsync from '../components/useAsync.js';
import { useLive } from '../live.jsx';

const STATUS_FILTERS = ['ALL', 'OPEN', 'SERVED', 'CANCELLED'];
const clock = (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

/** Every order for a day — nothing is deleted when orders finish. Printable end-of-day sheet. */
export default function Today() {
  const { id } = useParams();
  const [date, setDate] = useState(() => ymd(new Date()));
  const [filter, setFilter] = useState('ALL');
  const { data, error, reload } = useAsync(
    () => api(`/restaurants/${id}/orders/day?date=${date}&tz=${encodeURIComponent(localTz())}`), [id, date]);
  useLive(['ORDER_CREATED', 'ORDER_UPDATED'], (ev) => { if (ev.restaurantId === Number(id)) reload(); });

  const shift = (days) => {
    const d = new Date(date + 'T12:00:00');
    d.setDate(d.getDate() + days);
    setDate(ymd(d));
  };
  const isToday = date === ymd(new Date());
  const orders = (data?.orders || []).filter((o) =>
    filter === 'ALL' ? true
      : filter === 'OPEN' ? !['SERVED', 'CANCELLED'].includes(o.status)
        : o.status === filter);
  const s = data?.summary;
  const pretty = new Date(date + 'T12:00:00').toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  return (
    <div className="today">
      <div className="page-head no-print">
        <h2>{isToday ? 'Today' : 'Orders'}</h2>
        <div className="actions">
          <Link className="btn" to={`/restaurants/${id}/kitchen`}>Kitchen</Link>
          <button className="btn primary" onClick={() => window.print()} disabled={!data}>Print day sheet</button>
        </div>
      </div>

      <div className="date-nav no-print">
        <button className="btn small" onClick={() => shift(-1)} aria-label="Previous day">←</button>
        <input type="date" value={date} max={ymd(new Date())} onChange={(e) => e.target.value && setDate(e.target.value)} />
        <button className="btn small" onClick={() => shift(1)} disabled={isToday} aria-label="Next day">→</button>
        {!isToday && <button className="btn small ghost" onClick={() => setDate(ymd(new Date()))}>Today</button>}
      </div>

      <div className="print-only print-head">
        <h1>{data?.restaurantName} — end of day</h1>
        <div>{pretty} · printed {new Date().toLocaleString()}</div>
      </div>

      {error && <div className="error">{error}</div>}

      {s && (
        <>
          <div className="stats">
            <div className="card"><div className="muted small">Revenue</div><div className="stat">{money(s.revenue)}</div></div>
            <div className="card"><div className="muted small">Orders</div><div className="stat">{s.totalOrders}</div></div>
            <div className="card"><div className="muted small">Average order</div><div className="stat">{money(s.averageOrder)}</div></div>
            <div className="card"><div className="muted small">Served / open / cancelled</div><div className="stat">{s.served} / {s.open} / {s.cancelled}</div></div>
          </div>
          <div className="grid-2">
            <section className="card">
              <h3>Items sold</h3>
              {s.items.length === 0 && <p className="muted small">Nothing yet.</p>}
              <table className="report">
                <tbody>
                  {s.items.map((i) => (
                    <tr key={i.itemName}><td>{i.quantity}×</td><td>{i.itemName}</td><td className="num">{money(i.revenue)}</td></tr>
                  ))}
                </tbody>
              </table>
            </section>
            <section className="card">
              <h3>By waiter</h3>
              {s.waiters.length === 0 && <p className="muted small">Nothing yet.</p>}
              <table className="report">
                <tbody>
                  {s.waiters.map((w) => (
                    <tr key={w.waiterName}><td>{w.waiterName}</td><td>{w.orders} orders</td><td className="num">{money(w.revenue)}</td></tr>
                  ))}
                </tbody>
              </table>
            </section>
          </div>
        </>
      )}

      <div className="chips no-print">
        {STATUS_FILTERS.map((f) => (
          <button key={f} className={`chip ${filter === f ? 'active' : ''}`} onClick={() => setFilter(f)}>
            {f[0] + f.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      <section className="card">
        <h3>All orders ({orders.length})</h3>
        {data && orders.length === 0 && <p className="muted">No orders {filter === 'ALL' ? 'on this day' : 'with this status'}.</p>}
        <div className="list">
          {orders.map((o) => (
            <div className={`order-row ${o.status === 'CANCELLED' ? 'faded' : ''}`} key={o.id}>
              <div className="order-row-head">
                <b>{clock(o.createdAt)} · Table {o.tableLabel}</b>
                <span className={`pill small status-pill-${o.status.toLowerCase()}`}>{o.status.replace('_', ' ')}</span>
              </div>
              <div className="muted small">#{o.id} · {o.waiterName}</div>
              <ul>
                {o.lines.map((l, idx) => (
                  <li key={idx}>
                    {l.quantity}× {l.itemName}
                    {l.options.length > 0 && <span className="muted"> ({l.options.join(', ')})</span>}
                    {l.note && <span className="muted"> “{l.note}”</span>}
                  </li>
                ))}
              </ul>
              <div className="order-total">{money(o.total)}</div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
