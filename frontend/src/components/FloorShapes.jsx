/** Shared SVG rendering for floor-plan elements (used by the designer and the waiter's floor view). */

export const TABLE_PRESETS = [
  { shape: 'ROUND', name: 'Round', w: 100, h: 100, seats: 4 },
  { shape: 'SQUARE', name: 'Square', w: 100, h: 100, seats: 4 },
  { shape: 'RECTANGLE', name: 'Long', w: 200, h: 90, seats: 6 },
  { shape: 'TRIANGLE', name: 'Triangle', w: 110, h: 100, seats: 3 },
  { shape: 'HEXAGON', name: 'Hexagon', w: 120, h: 110, seats: 6 },
  { shape: 'ARC', name: 'Curved booth', w: 220, h: 220, seats: 6 },
];

export const FIXTURE_PRESETS = [
  { shape: 'RECTANGLE', name: 'Bar', label: 'Bar', w: 300, h: 70 },
  { shape: 'RECTANGLE', name: 'Kitchen', label: 'Kitchen', w: 200, h: 130 },
  { shape: 'RECTANGLE', name: 'Wall', label: '', w: 300, h: 12 },
  { shape: 'ARC', name: 'Curved wall', label: '', w: 250, h: 250 },
  { shape: 'RECTANGLE', name: 'Door', label: 'Door', w: 90, h: 16 },
  { shape: 'ROUND', name: 'Plant', label: 'Plant', w: 60, h: 60 },
];

/** Thickness of a quarter-circle: thin for walls, a seat-width for booths. */
const arcThickness = (el) => (el.kind === 'FIXTURE' ? Math.min(14, el.w / 4, el.h / 4) : Math.max(24, Math.min(el.w, el.h) * 0.38));

/** Quarter ring centred on the element's bottom-left corner, sweeping from top to right. */
export function arcPath(el) {
  const { w, h } = el;
  const t = arcThickness(el);
  return `M 0 0 A ${w} ${h} 0 0 1 ${w} ${h} L ${w - t} ${h} A ${w - t} ${h - t} 0 0 0 0 ${t} Z`;
}

/** Polygon points in the element's own box (px), or null for ellipse/rect shapes. */
export function localPoints(el) {
  const { w, h } = el;
  switch (el.shape) {
    case 'TRIANGLE': return `${w / 2},0 ${w},${h} 0,${h}`;
    case 'HEXAGON': return `${w * 0.25},0 ${w * 0.75},0 ${w},${h / 2} ${w * 0.75},${h} ${w * 0.25},${h} 0,${h / 2}`;
    case 'POLYGON':
      return (el.points || '').trim().split(/\s+/)
        .map((p) => p.split(',').map(Number))
        .map(([x, y]) => `${x * w},${y * h}`).join(' ');
    default: return null;
  }
}

export function ShapeBody({ el, className, ...rest }) {
  const pts = localPoints(el);
  if (el.shape === 'ARC') return <path d={arcPath(el)} className={className} {...rest} />;
  if (el.shape === 'ROUND') return <ellipse cx={el.w / 2} cy={el.h / 2} rx={el.w / 2} ry={el.h / 2} className={className} {...rest} />;
  if (pts) return <polygon points={pts} className={className} {...rest} />;
  const r = el.kind === 'TABLE' ? Math.min(12, el.w / 6, el.h / 6) : 3;
  return <rect width={el.w} height={el.h} rx={r} className={className} {...rest} />;
}

export const transformOf = (el) => `translate(${el.x} ${el.y}) rotate(${el.rotation || 0} ${el.w / 2} ${el.h / 2})`;

/** Text kept upright regardless of the element's rotation; shrinks to fit small shapes. */
export function ElementLabel({ el, sub }) {
  if (!el.label && !sub) return null;
  // label anchor in the element's own box, then rotated with it (text itself stays upright)
  let lx = el.w / 2, ly = el.h / 2, room = Math.min(el.w, el.h);
  if (el.shape === 'TRIANGLE') ly = el.h * 0.66;
  if (el.shape === 'ARC') {
    const t = arcThickness(el);
    lx = (el.w - t / 2) * Math.SQRT1_2;
    ly = el.h - (el.h - t / 2) * Math.SQRT1_2;
    room = t * 1.6;
  }
  const a = ((el.rotation || 0) * Math.PI) / 180;
  const dx = lx - el.w / 2, dy = ly - el.h / 2;
  const cx = el.x + el.w / 2 + dx * Math.cos(a) - dy * Math.sin(a);
  const cy = el.y + el.h / 2 + dx * Math.sin(a) + dy * Math.cos(a);
  const size = Math.max(11, Math.min(28, room / 3));
  return (
    <g className="el-label" pointerEvents="none">
      {el.label && <text x={cx} y={sub ? cy - size * 0.2 : cy} fontSize={size} dominantBaseline="middle" textAnchor="middle">{el.label}</text>}
      {sub && <text x={cx} y={cy + size * 0.75} fontSize={size * 0.55} dominantBaseline="middle" textAnchor="middle" className="el-sub">{sub}</text>}
    </g>
  );
}

export function GridDefs({ id = 'grid', step = 20 }) {
  return (
    <defs>
      <pattern id={id} width={step} height={step} patternUnits="userSpaceOnUse">
        <path d={`M ${step} 0 L 0 0 0 ${step}`} className="grid-line" />
      </pattern>
    </defs>
  );
}
