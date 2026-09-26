import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, hm, time } from '../api.js';
import { useAuth } from '../auth.jsx';
import useAsync from '../components/useAsync.js';
import Modal from '../components/Modal.jsx';
import { useLive } from '../live.jsx';

const ICON = { PLATFORM: '🏢', OWNER: '👤', RESTAURANT: '🍽️', STAFF: '🧑‍🍳' };

export default function OrgTree() {
  const { data: tree, error, reload } = useAsync(() => api('/tree'), []);
  useLive(['SHIFT_CHANGED', 'STAFF_CHANGED', 'RESTAURANT_CHANGED', 'OWNER_CHANGED'], reload);
  const [selected, setSelected] = useState(null);
  const nav = useNavigate();

  const open = (node) => {
    if (node.type === 'RESTAURANT') nav(`/restaurants/${node.entityId}`);
    else if (node.type === 'OWNER' || node.type === 'STAFF') setSelected(node);
  };

  return (
    <>
      <h2>Team</h2>
      <p className="muted small">Tap a person to see their details, or a restaurant to manage it.</p>
      {error && <div className="error">{error}</div>}
      {tree && <ul className="tree root"><Node node={tree} onOpen={open} depth={0} /></ul>}
      {selected && <UserPanel node={selected} onClose={() => setSelected(null)} />}
    </>
  );
}

function Node({ node, onOpen, depth }) {
  const [open, setOpen] = useState(depth < 3);
  const hasKids = node.children?.length > 0;
  return (
    <li>
      <div className={`tree-node type-${node.type.toLowerCase()} ${node.active ? '' : 'faded'}`}>
        {hasKids
          ? <button className="twisty" onClick={() => setOpen(!open)} aria-label={open ? 'Collapse' : 'Expand'}>{open ? '▾' : '▸'}</button>
          : <span className="twisty" />}
        <button className="node-body" onClick={() => onOpen(node)}>
          <span className="node-icon">{ICON[node.type]}</span>
          {node.type === 'STAFF' && <span className={`live-dot ${node.onShift ? 'on' : ''}`} title={node.onShift ? 'On shift' : 'Off shift'} />}
          <span>
            <span className="node-label">{node.label}</span>
            <span className="node-sub">{node.subtitle}</span>
          </span>
        </button>
      </div>
      {hasKids && open && (
        <ul className="tree">
          {node.children.map((c) => <Node key={c.key} node={c} onOpen={onOpen} depth={depth + 1} />)}
        </ul>
      )}
    </li>
  );
}

function UserPanel({ node, onClose }) {
  const { me, impersonate } = useAuth();
  const nav = useNavigate();
  const { data, error, reload } = useAsync(() => api(`/users/${node.entityId}`), [node.entityId]);
  useLive(['SHIFT_CHANGED', 'STAFF_CHANGED', 'ORDER_CREATED'], (ev) => { if (ev.userId === node.entityId) reload(); });
  const loginAs = async () => { await impersonate(node.entityId); onClose(); nav('/'); };

  return (
    <Modal title={node.label} onClose={onClose}>
      {error && <div className="error">{error}</div>}
      {data && (
        <div className="stack">
          <div className="muted small">{data.user.email}</div>
          <div className="row"><span>Role</span><b>{data.user.title || data.user.role}</b></div>
          {data.restaurantName && <div className="row"><span>Restaurant</span><b>{data.restaurantName}</b></div>}
          <div className="row"><span>Status</span><span className={`pill ${data.onShift ? 'green' : ''}`}>{data.user.active ? (data.onShift ? 'On shift' : 'Off shift') : 'Disabled'}</span></div>
          {data.user.role === 'WAITER' && <div className="row"><span>Orders (24h)</span><b>{data.ordersLast24h}</b></div>}
          {data.recentShifts.length > 0 && (
            <>
              <h4>Recent shifts</h4>
              {data.recentShifts.slice(0, 5).map((s) => (
                <div className="row small" key={s.id}><span>{time(s.clockIn)}</span><span>{s.clockOut ? hm(s.minutes) : 'in progress'}</span></div>
              ))}
            </>
          )}
          {me.role === 'ADMIN' && !me.impersonatorId && <button className="btn primary" onClick={loginAs}>Log in as {node.label.split(' ')[0]}</button>}
        </div>
      )}
    </Modal>
  );
}
