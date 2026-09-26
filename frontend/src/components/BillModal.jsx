import { useState } from 'react';
import { api, money } from '../api.js';
import useAsync from './useAsync.js';
import Modal from './Modal.jsx';

const SPLITS = [
  ['FULL', 'One bill'], ['EVEN', 'Split evenly'], ['BY_ITEM', 'By item'], ['CUSTOM', 'Custom amounts'],
];

/**
 * Settle a table: 1) choose how to split (server-side SplitStrategy), 2) choose how each part is paid
 * (PaymentMethod: cash with change, or card on the restaurant's terminal), 3) receipt.
 */
export default function BillModal({ rid, table, onClose, onPaid }) {
  const path = `/restaurants/${rid}/bills/${encodeURIComponent(table)}`;
  const { data: bill, error, reload } = useAsync(() => api(path), [path]);
  const [strategy, setStrategy] = useState('FULL');
  const [people, setPeople] = useState(2);
  const [assign, setAssign] = useState({});
  const [amounts, setAmounts] = useState(['', '']);
  const [parts, setParts] = useState(null);
  const [pays, setPays] = useState([]);
  const [receipt, setReceipt] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);

  const splitBody = () => ({
    strategy,
    people: strategy === 'EVEN' || strategy === 'BY_ITEM' ? people : null,
    assignments: strategy === 'BY_ITEM' ? assign : null,
    amounts: strategy === 'CUSTOM' ? amounts.map(Number) : null,
  });

  const toPayments = async () => {
    setErr(null);
    try {
      const p = await api(`${path}/split`, { method: 'POST', body: splitBody() });
      setParts(p);
      setPays(p.map(() => ({ method: 'CARD', tendered: '', reference: '' })));
    } catch (e) { setErr(e.message); }
  };

  const pay = async () => {
    setBusy(true); setErr(null);
    try {
      const r = await api(`${path}/pay`, { method: 'POST', body: {
        split: splitBody(), expectedTotal: bill.total,
        payments: pays.map((x) => ({ method: x.method, tendered: x.method === 'CASH' && x.tendered !== '' ? Number(x.tendered) : null,
          reference: x.method === 'CARD' ? x.reference || null : null })) } });
      setReceipt(r);
    } catch (e) {
      setErr(e.message);
      if (e.status === 409) { setParts(null); reload(); } // bill changed: back to step 1 with fresh numbers
    } finally { setBusy(false); }
  };

  const customLeft = bill ? Math.round((Number(bill.total) - amounts.reduce((s, a) => s + (Number(a) || 0), 0)) * 100) / 100 : 0;

  if (receipt) {
    const change = receipt.parts.reduce((s, p) => s + Number(p.change || 0), 0);
    return (
      <Modal title={`Table ${table} — paid`} onClose={() => onPaid(receipt)}>
        <div className="receipt">
          {receipt.parts.map((p, i) => (
            <div className="row" key={i}>
              <span>{p.label} · {p.method === 'CASH' ? 'Cash' : 'Card'}{p.reference ? ` #${p.reference}` : ''}</span>
              <b>{money(p.amount)}</b>
            </div>
          ))}
          <div className="row total"><b>Total</b><b>{money(receipt.total)}</b></div>
          {change > 0 && <div className="change-due">Change to give back: {money(change)}</div>}
        </div>
        <button className="btn primary block big" onClick={() => onPaid(receipt)}>Done</button>
      </Modal>
    );
  }

  return (
    <Modal title={`Bill — table ${table}`} onClose={onClose}>
      {error && <div className="error">{error}</div>}
      {bill && bill.lines.length === 0 && <p className="muted">Nothing to pay on this table.</p>}
      {bill && bill.lines.length > 0 && !parts && (
        <>
          <div className="list bill-lines">
            {bill.lines.map((l) => (
              <div className="row" key={l.lineId}>
                <span>{l.quantity}× {l.itemName}</span>
                <span className="actions">
                  {strategy === 'BY_ITEM' && (
                    <span className="guest-pick" role="group" aria-label={`Who had ${l.itemName}`}>
                      {Array.from({ length: people }, (_, g) => g + 1).map((g) => (
                        <button type="button" key={g} className={`chip small-chip ${assign[l.lineId] === g ? 'active' : ''}`}
                          onClick={() => setAssign({ ...assign, [l.lineId]: g })}>{g}</button>
                      ))}
                    </span>
                  )}
                  <b>{money(l.amount)}</b>
                </span>
              </div>
            ))}
            <div className="row total"><b>Total</b><b>{money(bill.total)}</b></div>
          </div>

          <div className="chips">
            {SPLITS.map(([k, label]) => (
              <button key={k} className={`chip ${strategy === k ? 'active' : ''}`} onClick={() => setStrategy(k)}>{label}</button>
            ))}
          </div>
          {(strategy === 'EVEN' || strategy === 'BY_ITEM') && (
            <div className="qty">
              <span>Guests</span>
              <button className="btn small" onClick={() => setPeople(Math.max(strategy === 'EVEN' ? 2 : 1, people - 1))}>−</button>
              <b>{people}</b>
              <button className="btn small" onClick={() => setPeople(Math.min(30, people + 1))}>+</button>
              {strategy === 'EVEN' && <span className="muted small">≈ {money(Number(bill.total) / people)} each</span>}
              {strategy === 'BY_ITEM' && <span className="muted small">tap a guest number on each item</span>}
            </div>
          )}
          {strategy === 'CUSTOM' && (
            <div className="stack">
              {amounts.map((a, i) => (
                <div className="recipe-row" key={i}>
                  <span>Part {i + 1}</span>
                  <input type="number" step="0.01" min="0" inputMode="decimal" value={a}
                    onChange={(e) => setAmounts(amounts.map((x, j) => (j === i ? e.target.value : x)))} aria-label={`Part ${i + 1} amount`} />
                  {amounts.length > 2 && <button className="btn small ghost" onClick={() => setAmounts(amounts.filter((_, j) => j !== i))} aria-label="Remove part">✕</button>}
                </div>
              ))}
              <div className="actions">
                <button className="btn small" onClick={() => setAmounts([...amounts, ''])}>+ Part</button>
                <span className={`small ${customLeft === 0 ? 'ok-text' : 'muted'}`}>{customLeft === 0 ? 'Adds up ✓' : `${money(customLeft)} left to assign`}</span>
              </div>
            </div>
          )}
          {err && <div className="error">{err}</div>}
          <button className="btn primary block big" onClick={toPayments}>Continue to payment</button>
        </>
      )}

      {parts && (
        <>
          <div className="list">
            {parts.map((p, i) => {
              const x = pays[i];
              const set = (patch) => setPays(pays.map((y, j) => (j === i ? { ...y, ...patch } : y)));
              const change = x.method === 'CASH' && x.tendered !== '' ? Number(x.tendered) - Number(p.amount) : null;
              return (
                <div className="pay-part" key={i}>
                  <div className="row"><b>{p.label}</b><b>{money(p.amount)}</b></div>
                  <div className="actions">
                    <button className={`chip ${x.method === 'CARD' ? 'active' : ''}`} onClick={() => set({ method: 'CARD' })}>Card</button>
                    <button className={`chip ${x.method === 'CASH' ? 'active' : ''}`} onClick={() => set({ method: 'CASH' })}>Cash</button>
                    {x.method === 'CASH' && (
                      <input className="qty-input" type="number" step="0.01" min="0" inputMode="decimal" placeholder="Handed over"
                        value={x.tendered} onChange={(e) => set({ tendered: e.target.value })} aria-label="Cash handed over" />
                    )}
                    {x.method === 'CARD' && (
                      <input className="qty-input" placeholder="Receipt # (optional)" maxLength={40}
                        value={x.reference} onChange={(e) => set({ reference: e.target.value })} aria-label="Card receipt number" />
                    )}
                  </div>
                  {change !== null && <div className={`small ${change < 0 ? 'error-text' : 'ok-text'}`}>{change < 0 ? `${money(-change)} short` : `Change: ${money(change)}`}</div>}
                </div>
              );
            })}
          </div>
          <p className="muted small">Card payments are taken on your card machine; TableTap records them.</p>
          {err && <div className="error">{err}</div>}
          <div className="actions">
            <button className="btn ghost" onClick={() => setParts(null)}>← Change split</button>
            <button className="btn primary big" onClick={pay} disabled={busy}>{busy ? 'Recording…' : `Take payment · ${money(bill.total)}`}</button>
          </div>
        </>
      )}
    </Modal>
  );
}
