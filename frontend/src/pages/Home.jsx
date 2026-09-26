import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, hm, money, time } from '../api.js';
import { useAuth } from '../auth.jsx';
import useAsync from '../components/useAsync.js';
import { useLive } from '../live.jsx';

export default function Home() {
  const { me } = useAuth();
  return me.role === 'WAITER' || me.role === 'CHEF' ? <StaffHome /> : <ManagerHome />;
}

/** Home for waiters and chefs: clock in/out plus shortcuts for their job. */
function StaffHome() {
  const { me } = useAuth();
  const { data, error, reload } = useAsync(() => api('/shifts/me'), []);
  useLive(['SHIFT_CHANGED'], (ev) => { if (ev.userId === me.id) reload(); });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);
  const onShift = !!data?.current;

  const toggle = async () => {
    setBusy(true); setErr(null);
    try { await api(`/shifts/${onShift ? 'clock-out' : 'clock-in'}`, { method: 'POST' }); await reload(); }
    catch (e) { setErr(e.message); } finally { setBusy(false); }
  };

  return (
    <>
      <h2>Hi {me.fullName.split(' ')[0]}</h2>
      <p className="muted">{me.title} at {me.restaurantName}</p>
      <div className="card clock">
        <div>
          <div className={`pill ${onShift ? 'green' : ''}`}>{onShift ? 'On shift' : 'Off shift'}</div>
          {onShift && <p className="muted small">Since {time(data.current.clockIn)} · {hm(data.current.minutes)}</p>}
        </div>
        <button className={`btn big ${onShift ? 'danger' : 'primary'}`} disabled={busy || !data} onClick={toggle}>
          {onShift ? 'Clock out' : 'Clock in'}
        </button>
      </div>
      {(err || error) && <div className="error">{err || error}</div>}
      <div className="grid-2">
        {me.role === 'WAITER' && (
          <Link className={`tile ${onShift ? '' : 'disabled'}`} to={`/restaurants/${me.restaurantId}/order`}>
            <span className="tile-icon">📝</span>Take order
          </Link>
        )}
        <Link className="tile" to={`/restaurants/${me.restaurantId}/kitchen`}>
          <span className="tile-icon">🍳</span>Kitchen queue
        </Link>
        <Link className="tile" to={`/restaurants/${me.restaurantId}/today`}>
          <span className="tile-icon">📋</span>Today's orders
        </Link>
      </div>
      <h3>Recent shifts</h3>
      <div className="card list">
        {data?.recent?.length ? data.recent.map((s) => (
          <div className="row" key={s.id}>
            <span>{time(s.clockIn)}</span>
            <span className="muted">{s.clockOut ? hm(s.minutes) : 'in progress'}</span>
          </div>
        )) : <p className="muted">No shifts yet.</p>}
      </div>
    </>
  );
}

function ManagerHome() {
  const { me } = useAuth();
  const { data: restaurants, reload } = useAsync(() => api('/restaurants'), []);
  useLive(['RESTAURANT_CHANGED', 'SHIFT_CHANGED', 'ORDER_CREATED', 'ORDER_UPDATED'], reload);
  const [name, setName] = useState('');
  const [error, setError] = useState(null);

  const create = async (e) => {
    e.preventDefault();
    try { await api('/restaurants', { method: 'POST', body: { name } }); setName(''); reload(); }
    catch (err) { setError(err.message); }
  };

  return (
    <>
      <h2>{me.role === 'ADMIN' ? 'All restaurants' : 'Your restaurants'}</h2>
      {me.role === 'OWNER' && <Billing />}
      <div className="grid-cards">
        {restaurants?.map((r) => (
          <Link key={r.id} to={`/restaurants/${r.id}`} className="card link-card">
            <h3>{r.name}</h3>
            <p className="muted small">{[r.cuisine, r.address].filter(Boolean).join(' · ') || 'No details yet'}</p>
            <div className="actions small">
              <span className={`pill ${r.onShift ? 'green' : ''}`}>{r.onShift} on shift</span>
              <span className="pill">{r.openOrders} open orders</span>
            </div>
            {me.role === 'ADMIN' && <p className="small">Owner: {r.ownerName}</p>}
          </Link>
        ))}
        {restaurants?.length === 0 && <p className="muted">No restaurants yet.</p>}
      </div>
      {me.role === 'OWNER' && (
        <form className="card inline-form" onSubmit={create}>
          <input placeholder="New restaurant name" value={name} onChange={(e) => setName(e.target.value)} required />
          <button className="btn primary">Add restaurant</button>
        </form>
      )}
      {error && <div className="error">{error}</div>}
    </>
  );
}

function Billing() {
  const { data, reload } = useAsync(() => api('/billing/usage'), []);
  const [open, setOpen] = useState(false);
  useLive(['RESTAURANT_CHANGED', 'ORDER_CREATED'], reload);
  if (!data) return null;
  return (
    <div className="card">
      <div className="billing">
        <div><div className="muted small">This month's bill (so far)</div><div className="stat">{money(data.estimatedTotal)}</div></div>
        <div className="muted small">
          {money(data.restaurantFees)} restaurant fees<br />
          {data.totalOrders} orders × {money(data.feePerOrder)} = {money(data.orderFees)}
          {Number(data.floorPlanFees) > 0 && <><br />{money(data.floorPlanFees)} floor-plan add-on</>}
        </div>
        <button className="btn small ghost" onClick={() => setOpen(!open)}>{open ? 'Hide' : 'Breakdown'}</button>
      </div>
      {open && (
        <div className="list">
          {data.lines.map((l) => (
            <div className="row" key={l.restaurantId}>
              <div>
                <b>{l.name}</b>
                <div className="muted small">
                  {money(data.feePerRestaurant)}/mo × {l.daysBilled}/{data.daysInPeriod} days = {money(l.restaurantFee)} · {l.orders} orders = {money(l.orderFees)}
                  {l.floorPlanFrom && <> · floor plan {money(l.floorPlanFee)}</>}
                </div>
              </div>
              <b>{money(l.subtotal)}</b>
            </div>
          ))}
          <p className="muted small">New restaurants and floor plans ({money(data.floorPlanFee)}/mo) are charged only for the days left in the month.</p>
        </div>
      )}
    </div>
  );
}
