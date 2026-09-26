import { useNavigate } from 'react-router-dom';
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
    </>
  );
}
