import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, money, randomId } from '../api.js';
import { outbox } from '../outbox.js';
import { useAuth } from '../auth.jsx';
import useAsync from '../components/useAsync.js';
import Modal from '../components/Modal.jsx';
import { ElementLabel, ShapeBody, transformOf } from '../components/FloorShapes.jsx';
import { useLive } from '../live.jsx';

const minutesSince = (iso) => Math.max(0, Math.round((Date.now() - new Date(iso)) / 60000));

/**
 * Waiter flow: 1) tap a table on the restaurant's floor plan  2) add items  3) send.
 * Each table keeps its own unsent cart, so a waiter can juggle several tables.
 */
export default function TakeOrder() {
  const { id } = useParams();
  const { me } = useAuth();
  const { data: floor, reload: reloadFloor } = useAsync(() => api(`/restaurants/${id}/floor`), [id]);
  const { data: menu, error, reload: reloadMenu } = useAsync(() => api(`/restaurants/${id}/menu`), [id]);
  useLive(['FLOOR_CHANGED', 'ORDER_CREATED', 'ORDER_UPDATED'], (ev) => { if (ev.restaurantId === Number(id)) reloadFloor(); });
  useLive(['MENU_CHANGED'], (ev) => { if (ev.restaurantId === Number(id)) reloadMenu(); });

  const [table, setTable] = useState(null);      // { key, id?, label, area? }
  const [carts, setCarts] = useState({});        // tableKey -> lines[]
  const [toast, setToast] = useState(null);

  const cartFor = (key) => carts[key] || [];
  const setCart = (key, fn) => setCarts((c) => ({ ...c, [key]: fn(c[key] || []) }));

  const sent = (tableKey, label, queued) => { setToast(queued
    ? `No connection — Table ${label} saved on this phone, it will go to the kitchen automatically`
    : `Sent to kitchen · Table ${label}`); setCarts((c) => ({ ...c, [tableKey]: [] })); setTable(null); reloadFloor(); setTimeout(() => setToast(null), 3500); };

  if (!table && floor && floor.areas.length === 0) {
    return (
      <QuickTablePicker rid={id} carts={carts} toast={toast} backTo={me.role === 'WAITER' ? '/' : `/restaurants/${id}`}
        onPick={setTable} canEdit={me.role !== 'WAITER'} />
    );
  }
  if (!table) {
    return (
      <FloorPicker floor={floor} carts={carts} toast={toast} backTo={me.role === 'WAITER' ? '/' : `/restaurants/${id}`}
        onPick={setTable} canEdit={me.role !== 'WAITER'} rid={id} />
    );
  }
  return (
    <OrderPad rid={id} table={table} menu={menu} error={error} floor={floor}
      cart={cartFor(table.key)} setCart={(fn) => setCart(table.key, fn)}
      onBack={() => setTable(null)} onSent={(queued) => sent(table.key, table.label, queued)} />
  );
}

/* ---------------- step 1: pick a table ---------------- */

function FloorPicker({ floor, carts, toast, onPick, backTo, canEdit, rid }) {
  const [areaIdx, setAreaIdx] = useState(0);
  const [manual, setManual] = useState('');
  const areas = floor?.areas || [];
  const area = areas[Math.min(areaIdx, areas.length - 1)];
  const hasTables = areas.some((a) => a.elements.some((e) => e.kind === 'TABLE'));

  const pickManual = (e) => {
    e.preventDefault();
    if (manual.trim()) onPick({ key: `m:${manual.trim()}`, label: manual.trim() });
  };

  return (
    <div className="order-page">
      <div className="page-head">
        <div className="actions"><Link to={backTo} className="btn small ghost">←</Link><h2>Choose a table</h2></div>
        {canEdit && <Link className="btn small" to={`/restaurants/${rid}?tab=Floor plan`}>Edit floor plan</Link>}
      </div>
      {toast && <div className="success">{toast}</div>}

      {floor && !hasTables && (
        <div className="card">
          <p className="muted">{canEdit ? 'Draw your restaurant in “Edit floor plan” so waiters can tap tables.' : 'No floor plan yet — ask the owner to draw one.'} You can still type a table number:</p>
        </div>
      )}

      {areas.length > 1 && (
        <div className="chips">
          {areas.map((a, i) => {
            const busy = a.elements.filter((e) => e.status?.openOrders).length;
            return (
              <button key={a.id} className={`chip ${a === area ? 'active' : ''}`} onClick={() => setAreaIdx(i)}>
                {a.name}{busy > 0 && <span className="chip-count">{busy}</span>}
              </button>
            );
          })}
        </div>
      )}

      {area && (
        <div className="floor-view card">
          <svg className="floor-canvas" viewBox={`0 0 ${area.width} ${area.height}`} role="group" aria-label={`${area.name} floor plan`}>
            <rect width={area.width} height={area.height} className="floor-bg" />
            {area.elements.filter((e) => e.kind === 'FIXTURE').map((el) => (
              <g key={el.id}>
                <g transform={transformOf(el)}><ShapeBody el={el} className="shape-fixture" /></g>
                <ElementLabel el={el} />
              </g>
            ))}
            {area.elements.filter((e) => e.kind === 'TABLE').map((el) => {
              const st = el.status || {};
              const pending = (carts[`t:${el.id}`] || []).length > 0;
              const state = st.ready ? 'ready' : st.openOrders ? 'busy' : 'free';
              const sub = st.openOrders ? `${money(st.openTotal)} · ${minutesSince(st.since)}m` : `${el.seats} seats`;
              const pick = () => onPick({ key: `t:${el.id}`, id: el.id, label: el.label, area: area.name, status: st });
              return (
                <g key={el.id} className={`table-btn state-${state}`} role="button" tabIndex={0} aria-label={`Table ${el.label}, ${state}`}
                  onClick={pick} onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && pick()}>
                  <g transform={transformOf(el)}><ShapeBody el={el} className="shape-table" /></g>
                  <ElementLabel el={el} sub={sub} />
                  {st.ready > 0 && <text x={el.x + el.w / 2} y={el.y - 8} textAnchor="middle" className="badge-text">READY</text>}
                  {pending && <circle cx={el.x + el.w - 6} cy={el.y + 6} r={10} className="pending-dot" />}
                </g>
              );
            })}
          </svg>
          <div className="legend small">
            <span><i className="lg free" />Free</span><span><i className="lg busy" />Ordered</span>
            <span><i className="lg ready" />Food ready</span><span><i className="lg pending" />Unsent items</span>
          </div>
        </div>
      )}

      <form className="card inline-form" onSubmit={pickManual}>
        <input placeholder="Other table / takeaway name" value={manual} onChange={(e) => setManual(e.target.value)} maxLength={20} />
        <button className="btn">Start order</button>
      </form>
    </div>
  );
}

/** No floor plan: type (or tap) a table number. Shows tables that already have open orders. */
function QuickTablePicker({ rid, carts, toast, onPick, backTo, canEdit }) {
  const [num, setNum] = useState('');
  const { data: open, reload } = useAsync(() => api(`/restaurants/${rid}/orders?open=true`), [rid]);
  useLive(['ORDER_CREATED', 'ORDER_UPDATED'], (ev) => { if (ev.restaurantId === Number(rid)) reload(); });

  // group open orders by table label
  const busy = {};
  (open || []).forEach((o) => {
    const t = (busy[o.tableLabel] ||= { label: o.tableLabel, total: 0, ready: 0, since: o.createdAt });
    t.total += Number(o.total);
    if (o.status === 'READY') t.ready += 1;
    if (o.createdAt < t.since) t.since = o.createdAt;
  });
  const pending = Object.keys(carts).filter((k) => k.startsWith('m:') && carts[k].length).map((k) => k.replace(/^m:/, ''));
  const labels = [...new Set([...Object.keys(busy), ...pending])].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));

  const go = (label) => { const l = String(label).trim(); if (l) onPick({ key: `m:${l}`, label: l }); };
  const press = (k) => setNum((n) => (k === '⌫' ? n.slice(0, -1) : (n + k).slice(0, 6)));

  return (
    <div className="order-page">
      <div className="page-head">
        <div className="actions"><Link to={backTo} className="btn small ghost">←</Link><h2>Which table?</h2></div>
        {canEdit && <Link className="btn small" to={`/restaurants/${rid}?tab=Floor plan`}>Get a floor plan</Link>}
      </div>
      {toast && <div className="success">{toast}</div>}

      <form className="card quick-table" onSubmit={(e) => { e.preventDefault(); go(num); }}>
        <input className="table-input big-input" inputMode="numeric" placeholder="Table no." value={num} maxLength={20}
          onChange={(e) => setNum(e.target.value)} aria-label="Table number" autoFocus />
        <div className="keypad">
          {['1', '2', '3', '4', '5', '6', '7', '8', '9', '⌫', '0'].map((k) => (
            <button type="button" key={k} onClick={() => press(k)}>{k}</button>
          ))}
          <button type="submit" className="go" disabled={!num.trim()}>Go</button>
        </div>
      </form>

      {labels.length > 0 && (
        <>
          <h3>Open tables</h3>
          <div className="open-tables">
            {labels.map((l) => {
              const b = busy[l];
              const state = b?.ready ? 'ready' : b ? 'busy' : 'pending';
              return (
                <button key={l} className={`open-table state-${state}`} onClick={() => go(l)}>
                  <b>{l}</b>
                  <span className="small">{b ? `${money(b.total)} · ${minutesSince(b.since)}m` : 'Unsent items'}</span>
                  {b?.ready > 0 && <span className="small ready-tag">Food ready</span>}
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}

/* ---------------- step 2: build the order ---------------- */

function OrderPad({ rid, table, menu, error, floor, cart, setCart, onBack, onSent }) {
  const [activeCat, setActiveCat] = useState(null);
  const [query, setQuery] = useState('');
  const [picking, setPicking] = useState(null);
  const [review, setReview] = useState(false);
  const [notes, setNotes] = useState('');
  const [err, setErr] = useState(null);
  const [sending, setSending] = useState(false);

  const cats = menu || [];
  const q = query.trim().toLowerCase();
  const sections = q
    ? [{ id: 'results', name: 'Search results', items: cats.flatMap((c) => c.items)
        .filter((i) => i.name.toLowerCase().includes(q) || (i.description || '').toLowerCase().includes(q)) }]
    : cats;

  // highlight the category tab for the section currently on screen
  useEffect(() => {
    if (q || typeof IntersectionObserver === 'undefined') return undefined;
    const obs = new IntersectionObserver((entries) => {
      const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
      if (visible[0]) setActiveCat(Number(visible[0].target.dataset.cat));
    }, { rootMargin: '-140px 0px -55% 0px' });
    document.querySelectorAll('.menu-section[data-cat]').forEach((el) => obs.observe(el));
    return () => obs.disconnect();
  }, [cats, q]);
  useEffect(() => { if (!activeCat && cats[0]) setActiveCat(cats[0].id); }, [cats, activeCat]);
  const jump = (catId) => {
    setActiveCat(catId);
    document.getElementById(`cat-${catId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const countOf = useMemo(() => {
    const m = {};
    cart.forEach((l) => { m[l.menuItemId] = (m[l.menuItemId] || 0) + l.quantity; });
    return m;
  }, [cart]);
  const total = cart.reduce((s, l) => s + l.price * l.quantity, 0);
  const count = cart.reduce((s, l) => s + l.quantity, 0);

  // live status for this table from the floor data
  const liveStatus = floor?.areas.flatMap((a) => a.elements).find((e) => e.id === table.id)?.status;

  const add = (item, options = [], quantity = 1, note = '') => setCart((c) => {
    const key = `${item.id}|${[...options].sort().join(',')}|${note}`;
    const found = c.find((l) => l.key === key);
    if (found) return c.map((l) => (l.key === key ? { ...l, quantity: l.quantity + quantity } : l));
    return [...c, { key, menuItemId: item.id, name: item.name, price: Number(item.price), options, quantity, note }];
  });
  const bump = (key, d) => setCart((c) => c.map((l) => (l.key === key ? { ...l, quantity: l.quantity + d } : l)).filter((l) => l.quantity > 0));
  const removeOne = (item) => setCart((c) => {
    const idx = c.map((l) => l.menuItemId).lastIndexOf(item.id);
    if (idx < 0) return c;
    return c.map((l, i) => (i === idx ? { ...l, quantity: l.quantity - 1 } : l)).filter((l) => l.quantity > 0);
  });
  const tap = (item) => (item.options.length ? setPicking(item) : add(item));

  const send = async () => {
    setSending(true); setErr(null);
    const body = {
      tableId: table.id ?? null, tableLabel: table.id ? null : table.label, notes: notes.trim() || null,
      lines: cart.map(({ menuItemId, quantity, options, note }) => ({ menuItemId, quantity, options, note })),
      clientRequestId: randomId(), // lets the server ignore duplicate resends
    };
    try {
      await api(`/restaurants/${rid}/orders`, { method: 'POST', body });
      setNotes('');
      onSent(false);
    } catch (e) {
      if (e.offline) {
        outbox.add({ id: body.clientRequestId, rid, label: table.label, body });
        setNotes('');
        onSent(true);
      } else {
        setErr(e.message);
      }
    } finally { setSending(false); }
  };

  return (
    <div className="order-page">
      <div className="pad-head">
        <button className="btn small ghost" onClick={onBack} aria-label="Back to tables">← Tables</button>
        <div className="pad-title">
          <b>Table {table.label}</b>
          <span className="muted small">{table.area || 'Manual'}{liveStatus?.openOrders ? ` · ${liveStatus.openOrders} sent · ${money(liveStatus.openTotal)}` : ''}</span>
        </div>
      </div>

      <div className="menu-search">
        <span aria-hidden="true">🔍</span>
        <input type="search" placeholder="Search dishes and drinks" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search the menu" />
        {query && <button onClick={() => setQuery('')} aria-label="Clear search">✕</button>}
      </div>

      {!q && cats.length > 1 && (
        <nav className="cat-tabs" aria-label="Menu categories">
          {cats.map((c) => {
            const n = c.items.reduce((sum, i) => sum + (countOf[i.id] || 0), 0);
            return (
              <button key={c.id} className={activeCat === c.id ? 'active' : ''} onClick={() => jump(c.id)}>
                {c.name}{n > 0 && <span className="chip-count">{n}</span>}
              </button>
            );
          })}
        </nav>
      )}
      {(error || err) && <div className="error">{error || err}</div>}

      <div className="menu-list">
        {sections.map((sec) => (
          <section key={sec.id} id={`cat-${sec.id}`} data-cat={sec.id} className="menu-section">
            <h3 className="menu-section-title">{sec.name}</h3>
            {sec.items.map((i) => {
              const n = countOf[i.id] || 0;
              return (
                <div key={i.id} className={`menu-item ${n ? 'in-cart' : ''} ${i.available ? '' : 'unavailable'}`}>
                  <button className="mi-main" disabled={!i.available} onClick={() => tap(i)}>
                    <span className="mi-name">{i.name}</span>
                    {i.description && <span className="mi-desc">{i.description}</span>}
                    {i.options.length > 0 && <span className="mi-opts">{i.options.length} options</span>}
                  </button>
                  <div className="mi-side">
                    <span className="mi-price">{i.available ? money(i.price) : 'Sold out'}</span>
                    {i.available && (n > 0 ? (
                      <div className="stepper">
                        <button onClick={() => removeOne(i)} aria-label={`Remove one ${i.name}`}>−</button>
                        <span>{n}</span>
                        <button onClick={() => tap(i)} aria-label={`Add one ${i.name}`}>+</button>
                      </div>
                    ) : (
                      <button className="add-btn" onClick={() => tap(i)} aria-label={`Add ${i.name}`}>+</button>
                    ))}
                  </div>
                </div>
              );
            })}
          </section>
        ))}
        {q && sections[0].items.length === 0 && <p className="muted">Nothing matches “{query}”.</p>}
        {!q && cats.length === 0 && <p className="muted">No menu yet — the owner needs to set one up.</p>}
      </div>

      {count > 0 && (
        <button className="cart-bar" onClick={() => setReview(true)}>
          <span>{count} item{count > 1 ? 's' : ''} · Table {table.label}</span><span>Review & send · {money(total)}</span>
        </button>
      )}

      {picking && <OptionPicker item={picking} onClose={() => setPicking(null)} onAdd={(o, qn, n) => { add(picking, o, qn, n); setPicking(null); }} />}

      {review && (
        <Modal title={`Table ${table.label}`} onClose={() => setReview(false)}>
          <div className="list">
            {cart.map((l) => (
              <div className="row" key={l.key}>
                <div>
                  <b>{l.name}</b>
                  {l.options.length > 0 && <div className="muted small">{l.options.join(', ')}</div>}
                  {l.note && <div className="muted small">“{l.note}”</div>}
                </div>
                <div className="qty">
                  <button className="btn small" onClick={() => bump(l.key, -1)}>−</button>
                  <span>{l.quantity}</span>
                  <button className="btn small" onClick={() => bump(l.key, 1)}>+</button>
                </div>
              </div>
            ))}
          </div>
          <label>Note for the kitchen<input value={notes} maxLength={500} onChange={(e) => setNotes(e.target.value)} placeholder="e.g. birthday, bring mains together" /></label>
          <div className="row total"><b>Total</b><b>{money(total)}</b></div>
          {err && <div className="error">{err}</div>}
          <button className="btn primary block big" onClick={send} disabled={!cart.length || sending}>{sending ? 'Sending…' : 'Send to kitchen'}</button>
        </Modal>
      )}
    </div>
  );
}

function OptionPicker({ item, onClose, onAdd }) {
  const [sel, setSel] = useState([]);
  const [qty, setQty] = useState(1);
  const [note, setNote] = useState('');
  const toggle = (o) => setSel((s) => (s.includes(o) ? s.filter((x) => x !== o) : [...s, o]));
  return (
    <Modal title={item.name} onClose={onClose}>
      {item.description && <p className="muted small">{item.description}</p>}
      <div className="option-list">
        {item.options.map((o) => (
          <button key={o} className={`chip ${sel.includes(o) ? 'active' : ''}`} onClick={() => toggle(o)}>{sel.includes(o) ? '✓ ' : ''}{o}</button>
        ))}
      </div>
      <label>Note<input value={note} maxLength={200} onChange={(e) => setNote(e.target.value)} placeholder="e.g. allergy, no salt" /></label>
      <div className="row">
        <div className="qty">
          <button className="btn small" onClick={() => setQty(Math.max(1, qty - 1))}>−</button>
          <span>{qty}</span>
          <button className="btn small" onClick={() => setQty(qty + 1)}>+</button>
        </div>
        <button className="btn primary" onClick={() => onAdd(sel, qty, note.trim())}>Add · {money(item.price * qty)}</button>
      </div>
    </Modal>
  );
}
