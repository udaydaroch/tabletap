import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../api.js';
import { useLive } from '../live.jsx';
import { ElementLabel, FIXTURE_PRESETS, GridDefs, ShapeBody, TABLE_PRESETS, transformOf } from './FloorShapes.jsx';
import { DowngradeLink, LayoutsModal, UpgradeModal } from './FloorPlanModals.jsx';
import { FloorPlanOriginator, UndoCaretaker } from './memento.js';
import { money } from '../api.js';

const SNAP = 10;
const snap = (v) => Math.round(v / SNAP) * SNAP;
let tmpId = 0;
const withKeys = (areas) => areas.map((a) => ({ ...a, key: a.id ?? `a${++tmpId}`, elements: a.elements.map((e) => ({ ...e, key: e.id ?? `e${++tmpId}` })) }));

/** Owner's floor-plan designer: areas (rooms), tables of any shape, fixtures. Drag / resize / rotate. */
export default function FloorEditor({ rid }) {
  const [areas, setAreas] = useState(null);
  const [active, setActive] = useState(0);
  const [selected, setSelected] = useState(null);
  const [dirty, setDirty] = useState(false);
  const [drawing, setDrawing] = useState(null); // array of points while drawing a custom shape
  const [msg, setMsg] = useState(null);
  const [saving, setSaving] = useState(false);
  const [tier, setTier] = useState({ advanced: false, price: 0 });
  const [upgradeReason, setUpgradeReason] = useState(null); // non-null = show upgrade modal
  const [showLayouts, setShowLayouts] = useState(false);
  const svgRef = useRef(null);
  const drag = useRef(null);
  const history = useRef(new UndoCaretaker());      // caretaker: stacks of saved floor plans
  const [, rerender] = useState(0);
  const areasRef = useRef(null);
  areasRef.current = areas;
  const lastDragEnd = useRef(0);

  const load = useCallback(async () => {
    const plan = await api(`/restaurants/${rid}/floor`);
    applyPlan(plan);
  }, [rid]);

  function applyPlan(plan) {
    history.current.clear();
    setAreas(withKeys(plan.areas));
    setTier({ advanced: plan.advanced, price: plan.advancedMonthlyPrice });
    setDirty(false);
    setActive((i) => Math.min(i, Math.max(0, plan.areas.length - 1)));
  }
  const locked = () => false; // everything is unlocked once the add-on is paid for
  const needUpgrade = (reason) => setUpgradeReason(reason);
  useEffect(() => { load().catch((e) => setMsg({ error: e.message })); }, [load]);
  useLive(['FLOOR_CHANGED'], (ev) => { if (ev.restaurantId === Number(rid) && !dirty) load(); });

  useEffect(() => {
    if (!dirty) return undefined;
    const warn = (e) => { e.preventDefault(); e.returnValue = ''; };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const area = areas?.[active];
  const sel = area?.elements.find((e) => e.key === selected);

  // Memento: before every edit the originator saves the current plan and the caretaker keeps it
  const applyState = (next) => {
    setAreas(next);
    setDirty(true);
    setActive((i) => Math.min(i, Math.max(0, next.length - 1)));
  };
  const originator = useRef(null);
  if (!originator.current) originator.current = new FloorPlanOriginator(() => areasRef.current, applyState);
  const update = (fn, label = 'Edit') => {
    history.current.checkpoint(originator.current.save(label));
    applyState(fn(structuredClone(areasRef.current)));
    rerender((n) => n + 1);
  };
  const undo = () => { if (history.current.undo(originator.current)) rerender((n) => n + 1); };
  const redo = () => { if (history.current.redo(originator.current)) rerender((n) => n + 1); };
  const updateSel = (patch, label = 'Edit') => update((as) => {
    const el = as[active].elements.find((e) => e.key === selected);
    Object.assign(el, patch);
    return as;
  });

  const nextTableLabel = () => {
    const nums = areas.flatMap((a) => a.elements).filter((e) => e.kind === 'TABLE').map((e) => parseInt(e.label, 10)).filter((n) => !Number.isNaN(n));
    return String((nums.length ? Math.max(...nums) : 0) + 1);
  };

  const add = (preset, kind) => {
    if (locked(preset.shape)) { needUpgrade(`${preset.name} shapes are part of Advanced floor plans`); return; }
    const el = {
      key: `e${++tmpId}`, kind, shape: preset.shape, w: preset.w, h: preset.h, rotation: 0,
      seats: kind === 'TABLE' ? preset.seats : 0,
      label: kind === 'TABLE' ? nextTableLabel() : preset.label,
      x: snap(area.width / 2 - preset.w / 2), y: snap(area.height / 2 - preset.h / 2),
    };
    update((as) => { as[active].elements.push(el); return as; }, `Add ${preset.name.toLowerCase()}`);
    setSelected(el.key);
  };

  const remove = () => {
    update((as) => { as[active].elements = as[active].elements.filter((e) => e.key !== selected); return as; }, `Delete ${sel?.label || 'item'}`);
    setSelected(null);
  };
  const duplicate = () => {
    const copy = { ...structuredClone(sel), key: `e${++tmpId}`, id: undefined, x: sel.x + 30, y: sel.y + 30 };
    if (copy.kind === 'TABLE') copy.label = nextTableLabel();
    update((as) => { as[active].elements.push(copy); return as; }, 'Duplicate');
    setSelected(copy.key);
  };

  // ---- pointer handling (mouse, touch, pen) ----
  const toSvg = (evt) => {
    const svg = svgRef.current;
    const pt = svg.createSVGPoint();
    pt.x = evt.clientX; pt.y = evt.clientY;
    return pt.matrixTransform(svg.getScreenCTM().inverse());
  };

  const startMove = (evt, el) => {
    if (drawing) return;
    evt.stopPropagation();
    setSelected(el.key);
    const p = toSvg(evt);
    drag.current = { mode: 'move', key: el.key, label: el.label, sx: p.x, sy: p.y, ox: el.x, oy: el.y, snapshot: originator.current.save(`Move ${el.label || 'item'}`) };
    svgRef.current.setPointerCapture(evt.pointerId);
  };
  const startResize = (evt, el) => {
    evt.stopPropagation();
    const p = toSvg(evt);
    drag.current = { mode: 'resize', key: el.key, label: el.label, sx: p.x, sy: p.y, ow: el.w, oh: el.h, rot: (el.rotation || 0) * Math.PI / 180, snapshot: originator.current.save(`Resize ${el.label || 'item'}`) };
    svgRef.current.setPointerCapture(evt.pointerId);
  };
  const onMove = (evt) => {
    const d = drag.current;
    if (!d) return;
    const p = toSvg(evt);
    const dx = p.x - d.sx, dy = p.y - d.sy;
    setAreas((as) => {
      const next = [...as];
      const a = { ...next[active], elements: next[active].elements.map((e) => {
        if (e.key !== d.key) return e;
        if (d.mode === 'move') return { ...e, x: snap(d.ox + dx), y: snap(d.oy + dy) };
        // resize in the element's own (rotated) axes
        const lx = dx * Math.cos(-d.rot) - dy * Math.sin(-d.rot);
        const ly = dx * Math.sin(-d.rot) + dy * Math.cos(-d.rot);
        let w = Math.max(20, snap(d.ow + lx)), h = Math.max(10, snap(d.oh + ly));
        if (e.shape === 'SQUARE') { w = h = Math.max(w, h); }
        return { ...e, w, h };
      }) };
      next[active] = a;
      return next;
    });
    setDirty(true);
  };
  const endDrag = () => {
    const d = drag.current;
    if (d) {
      lastDragEnd.current = Date.now();
      // the memento taken when the drag started = one undo step for the whole drag
      if (!originator.current.isUnchangedSince(d.snapshot)) {
        history.current.checkpoint(d.snapshot);
        rerender((n) => n + 1);
      }
    }
    drag.current = null;
  };

  const onCanvasClick = (evt) => {
    if (Date.now() - lastDragEnd.current < 300) return; // the click that ends a drag
    if (!drawing) { setSelected(null); return; }
    const p = toSvg(evt);
    setDrawing([...drawing, [snap(p.x), snap(p.y)]]);
  };

  const finishDrawing = () => {
    if (drawing.length < 3) { setDrawing(null); return; }
    const xs = drawing.map((p) => p[0]), ys = drawing.map((p) => p[1]);
    const minX = Math.min(...xs), minY = Math.min(...ys);
    const w = Math.max(20, Math.max(...xs) - minX), h = Math.max(20, Math.max(...ys) - minY);
    const points = drawing.map(([x, y]) => `${+((x - minX) / w).toFixed(3)},${+((y - minY) / h).toFixed(3)}`).join(' ');
    const el = { key: `e${++tmpId}`, kind: 'TABLE', shape: 'POLYGON', x: minX, y: minY, w, h, rotation: 0, seats: 4, label: nextTableLabel(), points };
    update((as) => { as[active].elements.push(el); return as; }, 'Draw shape');
    setSelected(el.key);
    setDrawing(null);
  };

  // keyboard: delete, arrows to nudge
  useEffect(() => {
    const onKey = (e) => {
      if (['INPUT', 'SELECT', 'TEXTAREA'].includes(document.activeElement?.tagName)) return;
      const mod = e.metaKey || e.ctrlKey;
      if (mod && e.key.toLowerCase() === 'z') { e.preventDefault(); e.shiftKey ? redo() : undo(); return; }
      if (mod && e.key.toLowerCase() === 'y') { e.preventDefault(); redo(); return; }
      if (e.key === 'Escape') { setDrawing(null); setSelected(null); }
      if (!sel) return;
      const step = e.shiftKey ? 50 : SNAP;
      const moves = { ArrowLeft: [-step, 0], ArrowRight: [step, 0], ArrowUp: [0, -step], ArrowDown: [0, step] };
      if (moves[e.key]) { e.preventDefault(); updateSel({ x: sel.x + moves[e.key][0], y: sel.y + moves[e.key][1] }, `Move ${sel.label || 'item'}`); }
      if (e.key === 'Delete' || e.key === 'Backspace') { e.preventDefault(); remove(); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  // ---- areas ----
  const addArea = () => {
    const name = prompt('Name of the new area (e.g. Patio, Upstairs)');
    if (!name) return;
    update((as) => [...as, { key: `a${++tmpId}`, name, width: 1000, height: 700, elements: [] }], `Add area ${name}`);
    setActive(areas.length);
  };
  const renameArea = () => {
    const name = prompt('Rename area', area.name);
    if (name) update((as) => { as[active].name = name; return as; }, 'Rename area');
  };
  const deleteArea = () => {
    const tables = area.elements.filter((e) => e.kind === 'TABLE').length;
    if (!confirm(`Delete the area "${area.name}"${tables ? ` and its ${tables} table${tables > 1 ? 's' : ''}` : ''}? Past orders are kept. Press Save to confirm.`)) return;
    setSelected(null);
    update((as) => as.filter((_, i) => i !== active), `Delete area ${area.name}`);
    setActive(0);
  };

  const save = async () => {
    setSaving(true); setMsg(null);
    const body = { areas: areas.map(({ id, name, width, height, elements }) => ({
      id, name, width, height,
      elements: elements.map(({ id: eid, kind, shape, label, x, y, w, h, rotation, seats, points }) =>
        ({ id: eid, kind, shape, label, x, y, w, h, rotation: rotation || 0, seats: seats || 0, points })),
    })) };
    try {
      const plan = await api(`/restaurants/${rid}/floor`, { method: 'PUT', body });
      const selIdx = sel ? area.elements.indexOf(sel) : -1;
      const next = withKeys(plan.areas);
      setAreas(next);
      history.current.clear(); // saved: ids changed, start a fresh history
      setSelected(selIdx >= 0 ? next[active]?.elements[selIdx]?.key ?? null : null);
      setDirty(false);
      setMsg({ ok: 'Floor plan saved — waiters see it now.' });
    } catch (e) {
      if (e.status === 402) needUpgrade(e.message);
      setMsg({ error: e.message });
    } finally { setSaving(false); }
  };

  if (!areas) return <p className="muted">{msg?.error || 'Loading…'}</p>;

  if (!tier.advanced) {
    return (
      <>
        <FloorUpsell price={tier.price} onUpgrade={() => needUpgrade('')} />
        {upgradeReason !== null && (
          <UpgradeModal rid={rid} price={tier.price} reason={upgradeReason} onClose={() => setUpgradeReason(null)}
            onUpgraded={(plan) => { applyPlan(plan); setUpgradeReason(null); setShowLayouts(true); }} />
        )}
      </>
    );
  }

  return (
    <div className="floor-editor">
      <p className="muted small">Draw your restaurant. Waiters take orders by tapping these tables. Drag to move, pull the corner dot to resize.</p>

      <div className="editor-bar">
        <div className="chips">
          {areas.map((a, i) => (
            <button key={a.key} className={`chip ${i === active ? 'active' : ''}`} onClick={() => { setActive(i); setSelected(null); }}>{a.name}</button>
          ))}
          <button className="chip" onClick={addArea}>+ Area</button>
        </div>
        <div className="actions">
          {area && <button className="btn small" onClick={renameArea}>Rename area</button>}
          {area && <button className="btn small danger-ghost" onClick={deleteArea}>Delete area</button>}
          <button className="btn small" onClick={() => setShowLayouts(true)}>Layouts</button>
          <button className="btn small" onClick={undo} disabled={!history.current.undoLabel}
            title={history.current.undoLabel ? `Undo ${history.current.undoLabel} (⌘Z)` : 'Nothing to undo'} aria-label="Undo">↶ Undo</button>
          <button className="btn small" onClick={redo} disabled={!history.current.redoLabel}
            title={history.current.redoLabel ? `Redo ${history.current.redoLabel} (⇧⌘Z)` : 'Nothing to redo'} aria-label="Redo">↷ Redo</button>
          {dirty && <span className="pill">Unsaved changes</span>}
          <button className="btn primary" onClick={save} disabled={!dirty || saving}>{saving ? 'Saving…' : 'Save floor plan'}</button>
        </div>
      </div>
      <div className="tier-bar adv">
        <span><b>Floor plan add-on</b> · {money(tier.price)}/month</span>
        <DowngradeLink rid={rid} onChanged={applyPlan} />
      </div>
      {msg?.ok && <div className="success">{msg.ok}</div>}
      {msg?.error && <div className="error">{msg.error}</div>}

      {!area ? (
        <div className="card empty-floor">
          <p>No areas yet.</p>
          <div className="actions" style={{ justifyContent: 'center' }}>
            <button className="btn primary" onClick={() => setShowLayouts(true)}>Start from a layout</button>
            <button className="btn" onClick={addArea}>Blank area</button>
          </div>
        </div>
      ) : (
        <div className="editor-layout">
          <aside className="palette card">
            <h4>Tables</h4>
            <div className="palette-grid">
              {TABLE_PRESETS.map((p) => (
                <button key={p.shape} className={`palette-btn ${locked(p.shape) ? 'locked' : ''}`} onClick={() => add(p, 'TABLE')} title={`Add ${p.name.toLowerCase()} table`}>
                  <svg viewBox={`-4 -4 ${p.w + 8} ${p.h + 8}`}><ShapeBody el={{ ...p, kind: 'TABLE' }} className="shape-table" /></svg>
                  <span>{p.name}</span>
                </button>
              ))}
              <button className={`palette-btn ${drawing ? 'active' : ''} ${locked('POLYGON') ? 'locked' : ''}`}
                onClick={() => { if (locked('POLYGON')) { needUpgrade('Drawing custom shapes is part of Advanced floor plans'); return; } setSelected(null); setDrawing(drawing ? null : []); }}>
                <svg viewBox="0 0 100 100"><polygon points="10,10 90,10 90,90 50,90 50,50 10,50" className="shape-table dashed" /></svg>
                <span>{drawing ? 'Cancel' : 'Draw any shape'}</span>
              </button>
            </div>
            <h4>Room</h4>
            <div className="palette-grid">
              {FIXTURE_PRESETS.map((p) => (
                <button key={p.name} className={`palette-btn ${locked(p.shape) ? 'locked' : ''}`} onClick={() => add(p, 'FIXTURE')}>
                  <svg viewBox={`-4 -4 ${p.w + 8} ${Math.max(p.h, 40) + 8}`}><ShapeBody el={{ ...p, kind: 'FIXTURE' }} className="shape-fixture" /></svg>
                  <span>{p.name}</span>
                </button>
              ))}
            </div>
            <h4>Area size</h4>
            <div className="grid-2 tight">
              <label className="small">Width<input type="number" min={200} max={5000} step={50} value={area.width}
                onChange={(e) => update((as) => { as[active].width = Number(e.target.value); return as; })} /></label>
              <label className="small">Height<input type="number" min={200} max={5000} step={50} value={area.height}
                onChange={(e) => update((as) => { as[active].height = Number(e.target.value); return as; })} /></label>
            </div>
          </aside>

          <div className="canvas-wrap">
            {drawing && (
              <div className="draw-hint">
                Tap the canvas to place corners ({drawing.length} so far).
                <button className="btn small primary" onClick={finishDrawing} disabled={drawing.length < 3}>Finish shape</button>
                <button className="btn small ghost" onClick={() => setDrawing([])}>Restart</button>
              </div>
            )}
            <svg ref={svgRef} className={`floor-canvas editing ${drawing ? 'drawing' : ''}`} viewBox={`0 0 ${area.width} ${area.height}`}
              onPointerMove={onMove} onPointerUp={endDrag} onPointerCancel={endDrag} onClick={onCanvasClick}>
              <GridDefs />
              <rect width={area.width} height={area.height} fill="url(#grid)" className="floor-bg" />
              {area.elements.map((el) => (
                <g key={el.key}>
                  <g transform={transformOf(el)} onPointerDown={(e) => startMove(e, el)} onClick={(e) => e.stopPropagation()} className="el-hit">
                    <ShapeBody el={el} className={`${el.kind === 'TABLE' ? 'shape-table' : 'shape-fixture'} ${el.key === selected ? 'selected' : ''}`} />
                  </g>
                  <ElementLabel el={el} sub={el.kind === 'TABLE' && el.seats ? `${el.seats} seats` : null} />
                  {el.key === selected && (
                    <g transform={transformOf(el)}>
                      <rect x={-4} y={-4} width={el.w + 8} height={el.h + 8} className="sel-box" pointerEvents="none" />
                      <circle cx={el.w} cy={el.h} r={14} className="handle" onPointerDown={(e) => startResize(e, el)} />
                    </g>
                  )}
                </g>
              ))}
              {drawing && drawing.length > 0 && (
                <g pointerEvents="none">
                  <polyline points={drawing.map((p) => p.join(',')).join(' ')} className="draw-line" />
                  {drawing.map(([x, y], i) => <circle key={i} cx={x} cy={y} r={6} className="draw-pt" />)}
                </g>
              )}
            </svg>
          </div>

          <aside className="props card">
            {!sel ? <p className="muted small">Select something on the plan to edit it.</p> : (
              <div className="stack">
                <h4>{sel.kind === 'TABLE' ? 'Table' : 'Room item'}</h4>
                <label>{sel.kind === 'TABLE' ? 'Table name / number' : 'Label'}
                  <input value={sel.label || ''} maxLength={20} onChange={(e) => updateSel({ label: e.target.value })} />
                </label>
                {sel.kind === 'TABLE' && (
                  <label>Seats<input type="number" min={0} max={100} value={sel.seats} onChange={(e) => updateSel({ seats: Number(e.target.value) })} /></label>
                )}
                {sel.shape !== 'POLYGON' && (
                  <label>Shape
                    <select value={sel.shape} onChange={(e) => (locked(e.target.value)
                      ? needUpgrade('That shape is part of Advanced floor plans') : updateSel({ shape: e.target.value }))}>
                      <option value="ROUND">Round / oval</option><option value="SQUARE">Square</option>
                      <option value="RECTANGLE">Rectangle</option>
                      <option value="TRIANGLE">Triangle{locked('TRIANGLE') ? ' 🔒' : ''}</option>
                      <option value="HEXAGON">Hexagon{locked('HEXAGON') ? ' 🔒' : ''}</option>
                      <option value="ARC">Quarter circle{locked('ARC') ? ' 🔒' : ''}</option>
                    </select>
                  </label>
                )}
                <div className="grid-2 tight">
                  <label className="small">Width<input type="number" min={20} step={10} value={sel.w} onChange={(e) => updateSel({ w: Number(e.target.value) })} /></label>
                  <label className="small">Height<input type="number" min={10} step={10} value={sel.h} onChange={(e) => updateSel({ h: Number(e.target.value) })} /></label>
                </div>
                <label>Rotation: {Math.round(sel.rotation || 0)}°
                  <input type="range" min={0} max={359} step={5} value={sel.rotation || 0} onChange={(e) => updateSel({ rotation: Number(e.target.value) })} />
                </label>
                <div className="actions">
                  <button className="btn small" onClick={() => updateSel({ rotation: ((sel.rotation || 0) + 45) % 360 })}>Rotate 45°</button>
                  <button className="btn small" onClick={duplicate}>Duplicate</button>
                  <button className="btn small danger" onClick={remove}>Delete</button>
                </div>
                <label className="check small">
                  <input type="checkbox" checked={sel.kind === 'TABLE'} onChange={(e) => updateSel(e.target.checked
                    ? { kind: 'TABLE', label: sel.label || nextTableLabel(), seats: sel.seats || 4 }
                    : { kind: 'FIXTURE', seats: 0 })} />
                  Customers sit here (orderable table)
                </label>
              </div>
            )}
          </aside>
        </div>
      )}
      {upgradeReason !== null && (
        <UpgradeModal rid={rid} price={tier.price} reason={upgradeReason} onClose={() => setUpgradeReason(null)}
          onUpgraded={(plan) => {
            setTier({ advanced: plan.advanced, price: plan.advancedMonthlyPrice });
            setUpgradeReason(null);
            setMsg({ ok: 'Advanced floor plans unlocked.' });
          }} />
      )}
      {showLayouts && (
        <LayoutsModal rid={rid} advanced={tier.advanced} price={tier.price} dirty={dirty} onClose={() => setShowLayouts(false)}
          onNeedUpgrade={(r) => { setShowLayouts(false); needUpgrade(r); }}
          onApplied={(plan) => { applyPlan(plan); setActive(0); setSelected(null); setShowLayouts(false); setMsg({ ok: 'Layout applied — waiters see it now.' }); }} />
      )}
    </div>
  );
}

/** Shown to restaurants without the add-on: what they get and what it costs. */
function FloorUpsell({ price, onUpgrade }) {
  const demo = [
    { kind: 'FIXTURE', shape: 'RECTANGLE', label: 'Bar', x: 20, y: 15, w: 170, h: 36 },
    { kind: 'FIXTURE', shape: 'ARC', label: '', x: 300, y: 15, w: 120, h: 120, rotation: 90 },
    { kind: 'TABLE', shape: 'ROUND', label: '1', x: 30, y: 90, w: 60, h: 60 },
    { kind: 'TABLE', shape: 'SQUARE', label: '2', x: 120, y: 95, w: 52, h: 52, rotation: 45 },
    { kind: 'TABLE', shape: 'HEXAGON', label: '3', x: 210, y: 90, w: 66, h: 60 },
    { kind: 'TABLE', shape: 'RECTANGLE', label: '4', x: 40, y: 185, w: 130, h: 50 },
    { kind: 'TABLE', shape: 'TRIANGLE', label: '5', x: 215, y: 180, w: 64, h: 58 },
    { kind: 'TABLE', shape: 'ARC', label: '6', x: 310, y: 150, w: 110, h: 110 },
  ];
  return (
    <div className="card upsell">
      <div className="upsell-text">
        <span className="pill">Add-on</span>
        <h3>Draw your restaurant, tap a table to order</h3>
        <p className="muted">Right now waiters type a table number. With a floor plan they see your actual room,
          which tables are busy, and which food is ready.</p>
        <ul className="feature-list">
          <li>Any number of areas: main floor, patio, upstairs, private rooms</li>
          <li>Tables of any shape: round, square, long, triangle, hexagon, curved booths, or draw your own</li>
          <li>Walls, curved walls, bar, kitchen and doors</li>
          <li>Ready-made layouts for cafés, bistros, bars and fine dining</li>
          <li>Live table status for waiters: free, ordered, food ready</li>
        </ul>
        <button className="btn primary big" onClick={onUpgrade}>Get floor plans · {money(price)}/month</button>
      </div>
      <svg className="upsell-preview floor-canvas" viewBox="0 0 440 270" aria-hidden="true">
        <rect width="440" height="270" className="floor-bg" rx="10" />
        {demo.map((el, i) => (
          <g key={i}>
            <g transform={transformOf(el)}><ShapeBody el={el} className={el.kind === 'TABLE' ? `shape-table${i === 4 ? ' busy-demo' : ''}` : 'shape-fixture'} /></g>
            <ElementLabel el={el} />
          </g>
        ))}
      </svg>
    </div>
  );
}
