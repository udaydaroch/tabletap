import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { api, hm, money, time } from '../api.js';
import useAsync from '../components/useAsync.js';
import Modal from '../components/Modal.jsx';
import { useLive } from '../live.jsx';
import FloorEditor from '../components/FloorEditor.jsx';
import StockPanel from '../components/StockPanel.jsx';
import PrintersPanel from '../components/PrintersPanel.jsx';

const TABS = ['Floor plan', 'Menu', 'Stock', 'Printers', 'Staff', 'Shifts', 'Details'];
const STATIONS = ['KITCHEN', 'GRILL', 'BAR', 'DESSERT'];

export default function RestaurantPage() {
  const { id } = useParams();
  const { data: r, reload } = useAsync(() => api(`/restaurants/${id}`), [id]);
  const [params, setParams] = useSearchParams();
  const tab = TABS.includes(params.get('tab')) ? params.get('tab') : 'Floor plan';
  const setTab = (t) => setParams({ tab: t }, { replace: true });
  useLive(['RESTAURANT_CHANGED'], (ev) => { if (ev.restaurantId === Number(id)) reload(); });
  if (!r) return <p className="muted">Loading…</p>;

  return (
    <>
      <div className="page-head">
        <div>
          <h2>{r.name}</h2>
          <p className="muted small">{[r.cuisine, r.address].filter(Boolean).join(' · ')}</p>
        </div>
        <div className="actions">
          <Link className="btn primary" to={`/restaurants/${id}/order`}>Take order</Link>
          <Link className="btn" to={`/restaurants/${id}/kitchen`}>Kitchen</Link>
          <Link className="btn" to={`/restaurants/${id}/today`}>Orders by day</Link>
        </div>
      </div>
      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button key={t} role="tab" aria-selected={tab === t} className={tab === t ? 'active' : ''} onClick={() => setTab(t)}>{t}</button>
        ))}
      </div>
      {tab === 'Floor plan' && <FloorEditor rid={id} />}
      {tab === 'Menu' && <MenuEditor rid={id} />}
      {tab === 'Stock' && <StockPanel rid={id} canManage />}
      {tab === 'Printers' && <PrintersPanel rid={id} />}
      {tab === 'Staff' && <Staff rid={id} />}
      {tab === 'Shifts' && <Shifts rid={id} />}
      {tab === 'Details' && <Details r={r} onSaved={reload} />}
    </>
  );
}

function MenuEditor({ rid }) {
  const { data: menu, reload, error } = useAsync(() => api(`/restaurants/${rid}/menu`), [rid]);
  useLive(['MENU_CHANGED'], (ev) => { if (ev.restaurantId === Number(rid)) reload(); });
  const [catName, setCatName] = useState('');
  const [editing, setEditing] = useState(null); // item object or {categoryId} for new
  const [err, setErr] = useState(null);
  const run = async (fn) => { try { setErr(null); await fn(); await reload(); } catch (e) { setErr(e.message); } };

  return (
    <>
      <p className="muted small">This is the menu template waiters use to build orders. Options are the extras/modifiers a waiter can tick.</p>
      {(err || error) && <div className="error">{err || error}</div>}
      {menu?.map((c) => (
        <section className="card" key={c.id}>
          <div className="section-head">
            <h3>{c.name}</h3>
            <div className="actions">
              <label className="inline-label small">Station
                <select value={c.station || ''} onChange={(e) => run(() => api(`/menu/categories/${c.id}`, { method: 'PUT', body: { name: c.name, sortOrder: c.sortOrder, station: e.target.value || null } }))}>
                  <option value="">KITCHEN (default)</option>
                  {[...new Set([...STATIONS.slice(1), ...(c.station ? [c.station] : [])])].map((s) => <option key={s} value={s}>{s}</option>)}
                </select>
              </label>
              <button className="btn small" onClick={() => setEditing({ categoryId: c.id, name: '', price: '', options: [], available: true })}>+ Item</button>
              <button className="btn small ghost" onClick={() => confirm(`Delete "${c.name}" and all its items?`) && run(() => api(`/menu/categories/${c.id}`, { method: 'DELETE' }))}>Delete</button>
            </div>
          </div>
          {c.items.length === 0 && <p className="muted small">No items yet.</p>}
          {c.items.map((i) => (
            <div className={`row menu-row ${i.available ? '' : 'faded'}`} key={i.id} onClick={() => setEditing(i)}>
              <div>
                <b>{i.name}</b>{!i.available && <span className="pill small">86'd</span>}
                {i.station && <span className="pill small">{i.station}</span>}
                {i.kitchenName && <span className="muted small"> · {i.kitchenName}</span>}
                {i.options.length > 0 && <div className="muted small">{i.options.join(' · ')}</div>}
              </div>
              <span>{money(i.price)}</span>
            </div>
          ))}
        </section>
      ))}
      <form className="card inline-form" onSubmit={(e) => { e.preventDefault(); run(async () => { await api(`/restaurants/${rid}/menu/categories`, { method: 'POST', body: { name: catName, sortOrder: (menu?.length || 0) + 1 } }); setCatName(''); }); }}>
        <input placeholder="New category (e.g. Desserts)" value={catName} onChange={(e) => setCatName(e.target.value)} required />
        <button className="btn primary">Add category</button>
      </form>
      {editing && <ItemModal item={editing} categories={menu || []} onClose={() => setEditing(null)} onSaved={() => { setEditing(null); reload(); }} />}
    </>
  );
}

function ItemModal({ item, categories, onClose, onSaved }) {
  const [f, setF] = useState({ ...item, options: (item.options || []).join(', '), station: item.station || '', kitchenName: item.kitchenName || '' });
  const [err, setErr] = useState(null);
  const rid = useParams().id;
  const { data: ingredients } = useAsync(() => api(`/restaurants/${rid}/ingredients`), [rid]);
  const [recipe, setRecipe] = useState(null);
  useEffect(() => {
    if (item.id) api(`/menu/items/${item.id}/recipe`).then(setRecipe).catch(() => setRecipe([]));
    else setRecipe([]);
  }, [item.id]);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  const save = async (e) => {
    e.preventDefault();
    const body = { categoryId: Number(f.categoryId), name: f.name, description: f.description, price: Number(f.price), available: f.available,
      options: f.options.split(',').map((s) => s.trim()).filter(Boolean), station: f.station || null, kitchenName: f.kitchenName || null };
    try {
      const saved = await api(item.id ? `/menu/items/${item.id}` : '/menu/items', { method: item.id ? 'PUT' : 'POST', body });
      if (recipe) {
        await api(`/menu/items/${saved.id}/recipe`, { method: 'PUT', body: {
          lines: recipe.filter((l) => l.ingredientId && Number(l.quantity) > 0).map((l) => ({ ingredientId: Number(l.ingredientId), quantity: Number(l.quantity) })) } });
      }
      onSaved();
    } catch (e2) { setErr(e2.message); }
  };
  const remove = async () => {
    if (!confirm(`Delete ${item.name}?`)) return;
    try { await api(`/menu/items/${item.id}`, { method: 'DELETE' }); onSaved(); } catch (e) { setErr(e.message); }
  };

  return (
    <Modal title={item.id ? 'Edit item' : 'New item'} onClose={onClose}>
      <form onSubmit={save} className="stack">
        <label>Name<input value={f.name} onChange={set('name')} required /></label>
        <label>Price<input type="number" step="0.01" min="0" inputMode="decimal" value={f.price} onChange={set('price')} required /></label>
        <label>Category
          <select value={f.categoryId} onChange={set('categoryId')}>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <label>Description<textarea rows={2} value={f.description || ''} onChange={set('description')} /></label>
        <label>Options <span className="muted small">(comma separated)</span><input value={f.options} onChange={set('options')} placeholder="Rare, Medium, Well done" /></label>
        <div className="grid-2 tight">
          <label>Kitchen station
            <input value={f.station} onChange={set('station')} list="item-stations" placeholder="Same as category" />
          </label>
          <label>Name for the kitchen <span className="muted small">(other language)</span>
            <input value={f.kitchenName} onChange={set('kitchenName')} maxLength={100} placeholder="e.g. रिबआई स्टेक" />
          </label>
        </div>
        <datalist id="item-stations">{STATIONS.map((s) => <option key={s}>{s}</option>)}</datalist>
        <fieldset className="recipe">
          <legend>Recipe <span className="muted small">(for stock tracking, optional)</span></legend>
          {recipe === null && <p className="muted small">Loading…</p>}
          {recipe?.map((l, idx) => (
            <div className="recipe-row" key={idx}>
              <select value={l.ingredientId || ''} onChange={(e) => setRecipe(recipe.map((x, j) => (j === idx ? { ...x, ingredientId: e.target.value } : x)))} aria-label="Ingredient">
                <option value="">Choose ingredient…</option>
                {ingredients?.map((g) => <option key={g.id} value={g.id}>{g.name} ({g.unit})</option>)}
              </select>
              <input type="number" step="any" min="0" value={l.quantity ?? ''} placeholder="Qty per portion" aria-label="Quantity per portion"
                onChange={(e) => setRecipe(recipe.map((x, j) => (j === idx ? { ...x, quantity: e.target.value } : x)))} />
              <button type="button" className="btn small ghost" onClick={() => setRecipe(recipe.filter((_, j) => j !== idx))} aria-label="Remove ingredient">✕</button>
            </div>
          ))}
          {ingredients?.length === 0
            ? <p className="muted small">Add ingredients in the Stock tab first.</p>
            : <button type="button" className="btn small" onClick={() => setRecipe([...(recipe || []), { ingredientId: '', quantity: 1 }])}>+ Ingredient</button>}
        </fieldset>
        <label className="check"><input type="checkbox" checked={f.available} onChange={set('available')} /> Available</label>
        {err && <div className="error">{err}</div>}
        <div className="actions">
          <button className="btn primary">Save</button>
          {item.id && <button type="button" className="btn danger" onClick={remove}>Delete</button>}
        </div>
      </form>
    </Modal>
  );
}

function Staff({ rid }) {
  const { data: staff, reload } = useAsync(() => api(`/restaurants/${rid}/staff`), [rid]);
  useLive(['STAFF_CHANGED'], (ev) => { if (ev.restaurantId === Number(rid)) reload(); });
  const blank = { fullName: '', email: '', password: '', role: 'WAITER', title: 'Waiter' };
  const [f, setF] = useState(blank);
  const [err, setErr] = useState(null);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const add = async (e) => {
    e.preventDefault();
    try { await api(`/restaurants/${rid}/staff`, { method: 'POST', body: f }); setF(blank); setErr(null); reload(); }
    catch (e2) { setErr(e2.message); }
  };
  const toggle = async (s) => { await api(`/staff/${s.id}`, { method: 'PATCH', body: { active: !s.active } }); reload(); };

  return (
    <>
      <div className="card list">
        {staff?.length === 0 && <p className="muted">No staff yet — add your first waiter below.</p>}
        {staff?.map((s) => (
          <div className={`row ${s.active ? '' : 'faded'}`} key={s.id}>
            <div><b>{s.fullName}</b> <span className="pill small">{s.role === 'CHEF' ? '🍳 ' : ''}{s.title}</span><div className="muted small">{s.email}</div></div>
            <button className="btn small ghost" onClick={() => toggle(s)}>{s.active ? 'Disable' : 'Enable'}</button>
          </div>
        ))}
      </div>
      <form className="card stack" onSubmit={add}>
        <h3>Add staff member</h3>
        <div className="grid-2">
          <label>Name<input value={f.fullName} onChange={set('fullName')} required /></label>
          <label>Email<input type="email" value={f.email} onChange={set('email')} required /></label>
          <label>Temporary password <span className="muted small">(10+ chars, letter + number)</span><input type="password" minLength={10} value={f.password} onChange={set('password')} required /></label>
          <label>Role
            <select value={f.role} onChange={(e) => setF({ ...f, role: e.target.value, title: e.target.value === 'CHEF' ? 'Chef' : 'Waiter' })}>
              <option value="WAITER">Front of house (takes orders)</option>
              <option value="CHEF">Kitchen (chef)</option>
            </select>
          </label>
          <label>Title
            <select value={f.title} onChange={set('title')}>
              {(f.role === 'CHEF'
                ? ['Chef', 'Head Chef', 'Sous Chef', 'Line Cook', 'Kitchen Hand']
                : ['Waiter', 'Manager', 'Head Waiter', 'Bartender', 'Host']).map((t) => <option key={t}>{t}</option>)}
            </select>
          </label>
        </div>
        {err && <div className="error">{err}</div>}
        <button className="btn primary">Add</button>
      </form>
    </>
  );
}

function Shifts({ rid }) {
  const { data, reload } = useAsync(() => api(`/restaurants/${rid}/shifts`), [rid]);
  useLive(['SHIFT_CHANGED'], (ev) => { if (ev.restaurantId === Number(rid)) reload(); });
  return (
    <div className="card list">
      {data?.length === 0 && <p className="muted">No shifts recorded yet.</p>}
      {data?.map((s) => (
        <div className="row" key={s.id}>
          <div><b>{s.userName}</b><div className="muted small">{time(s.clockIn)} → {s.clockOut ? time(s.clockOut) : <span className="pill green small">on shift</span>}</div></div>
          <span>{hm(s.minutes)}</span>
        </div>
      ))}
    </div>
  );
}

function Details({ r, onSaved }) {
  const [f, setF] = useState({ name: r.name, address: r.address || '', cuisine: r.cuisine || '',
    timeZone: r.timeZone || Intl.DateTimeFormat().resolvedOptions().timeZone });
  const [msg, setMsg] = useState(null);
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const save = async (e) => {
    e.preventDefault();
    try { await api(`/restaurants/${r.id}`, { method: 'PUT', body: f }); setMsg('Saved'); onSaved(); } catch (e2) { setMsg(e2.message); }
  };
  return (
    <form className="card stack" onSubmit={save}>
      <label>Name<input value={f.name} onChange={set('name')} required /></label>
      <label>Address<input value={f.address} onChange={set('address')} /></label>
      <label>Cuisine / type<input value={f.cuisine} onChange={set('cuisine')} /></label>
      <label>Time zone <span className="muted small">(used for docket times)</span>
        <input value={f.timeZone} onChange={set('timeZone')} list="tz-list" placeholder="Pacific/Auckland" />
      </label>
      <datalist id="tz-list">{['Pacific/Auckland', 'Australia/Sydney', 'Australia/Melbourne', 'Asia/Kolkata', 'Europe/London', 'America/New_York', 'UTC'].map((z) => <option key={z}>{z}</option>)}</datalist>
      {msg && <div className="muted">{msg}</div>}
      <button className="btn primary">Save</button>
    </form>
  );
}
