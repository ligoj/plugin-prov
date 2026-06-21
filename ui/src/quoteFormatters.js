/**
 * Formatting helpers for the quote view. Ported (compact form) from the
 * legacy `webjars/service/prov/prov.js` — only the formatters the
 * first-iteration QuoteView actually uses.
 *
 * The plugin owns these so the view has no dependency on private host
 * modules. Mirrors host-side helpers in
 * `app-ui/src/main/webapp/src/plugins/prov/provFormatters.js`.
 */

/** Currency-aware monthly cost. Accepts a number or a min/max object. */
export function formatCost(value, currency = { unit: '$', rate: 1 }) {
  if (value == null) return '-'
  const rate = currency.rate || 1
  const unit = currency.unit || '$'
  const fmt = (n) => {
    const v = n * rate
    if (v < 1) return v.toFixed(3)
    if (v < 100) return v.toFixed(2)
    return Math.round(v).toLocaleString()
  }
  return `${fmt(value)} ${unit}`
}

/**
 * Cost range — handles the legacy `{ min, max, unbound }` shape returned
 * by the configuration endpoint. Shows a single value when min === max.
 */
export function formatCostRange(cost, currency) {
  if (!cost) return '-'
  if (typeof cost === 'number') return formatCost(cost, currency)
  const { min, max, unbound } = cost
  if (min == null && max == null) return '-'
  const suffix = unbound ? '+' : ''
  if (min == null || min === max) return `${formatCost(max, currency)}${suffix}`
  if (max == null) return `${formatCost(min, currency)}${suffix}`
  return `${formatCost(min, currency)} – ${formatCost(max, currency)}${suffix}`
}

export function formatRam(mb) {
  if (mb == null) return ''
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${Math.round(mb)} MB`
}

export function formatStorage(gb) {
  if (gb == null) return ''
  return gb >= 1024 ? `${(gb / 1024).toFixed(1)} TB` : `${Math.round(gb)} GB`
}

export function formatCpu(value) {
  if (value == null) return ''
  return value % 1 === 0 ? `${value}` : value.toFixed(1)
}

/** CO₂-equivalent emissions; g when below 1 kg, kg above. */
export function formatCo2(grams) {
  if (grams == null) return '-'
  if (grams >= 1000) return `${(grams / 1000).toFixed(1)} kg`
  return `${Math.round(grams)} g`
}

/**
 * Cost-period factors. The backend stores everything in monthly units;
 * the view scales at render time. The hourly ratio of 730 h/month and
 * the daily ratio of 30 d/month match the legacy conventions.
 */
export const COST_PERIOD_FACTORS = Object.freeze({
  hour: 1 / 730,
  day:  1 / 30,
  month: 1,
  year:  12,
})

/** Period keys in display order. */
export const COST_PERIODS = Object.freeze(['hour', 'day', 'month', 'year'])

/**
 * Scales the legacy `{ min, max, unbound }` cost shape by the active
 * period factor. Plain numbers and `null` pass through. Unknown periods
 * fall back to monthly (factor = 1).
 */
export function scaleCost(cost, period) {
  if (cost == null) return cost
  const f = COST_PERIOD_FACTORS[period] ?? 1
  if (typeof cost === 'number') return cost * f
  return {
    ...cost,
    min: cost.min != null ? cost.min * f : cost.min,
    max: cost.max != null ? cost.max * f : cost.max,
  }
}

/**
 * Tab metadata — drives the v-tabs/v-window structure of QuoteView AND
 * the cost-breakdown donut. The colour is a fixed hex picked from
 * Vuetify's default palette so plugin-prov stays theme-agnostic (the
 * donut renders inside its own SVG, outside Vuetify's CSS chrome).
 */
export const TAB_TYPES = [
  { key: 'instance',  icon: 'mdi-server',   listField: 'instances',  color: '#1976D2' },
  { key: 'database',  icon: 'mdi-database', listField: 'databases',  color: '#388E3C' },
  { key: 'container', icon: 'mdi-docker',   listField: 'containers', color: '#00ACC1' },
  { key: 'function',  icon: 'mdi-lambda',   listField: 'functions',  color: '#7B1FA2' },
  { key: 'storage',   icon: 'mdi-harddisk', listField: 'storages',   color: '#F57C00' },
  { key: 'support',   icon: 'mdi-lifebuoy', listField: 'supports',   color: '#D32F2F' },
]

/**
 * Builds an SVG path for one donut slice.
 *   cx,cy      — donut centre
 *   r          — outer radius
 *   ri         — inner radius
 *   start,end  — angles in radians (start < end). Use `donutFullPath`
 *                instead when the slice would cover the whole circle —
 *                SVG arcs degenerate when start === end (mod 2π).
 */
export function donutPath(cx, cy, r, ri, start, end) {
  const sx = cx + r * Math.cos(start)
  const sy = cy + r * Math.sin(start)
  const ex = cx + r * Math.cos(end)
  const ey = cy + r * Math.sin(end)
  const eix = cx + ri * Math.cos(end)
  const eiy = cy + ri * Math.sin(end)
  const six = cx + ri * Math.cos(start)
  const siy = cy + ri * Math.sin(start)
  const large = (end - start) > Math.PI ? 1 : 0
  return `M ${sx} ${sy} A ${r} ${r} 0 ${large} 1 ${ex} ${ey} L ${eix} ${eiy} A ${ri} ${ri} 0 ${large} 0 ${six} ${siy} Z`
}

/**
 * Case-insensitive substring match across the fields a user typically
 * searches by in the quote tables. Returns true when the query is
 * empty so callers don't need to short-circuit themselves.
 */
export function rowMatches(row, query) {
  if (!query) return true
  if (!row) return false
  const q = String(query).toLowerCase()
  const haystack = [
    row.name,
    row.description,
    row.os || row.price?.os,
    row.engine || row.price?.engine,
    row.level || row.price?.level,
    row.price?.type?.name,
    row.price?.type?.code,
    row.location?.name || row.price?.location?.name,
    row.id != null ? String(row.id) : '',
  ]
  for (const v of haystack) {
    if (v && String(v).toLowerCase().includes(q)) return true
  }
  return false
}

/**
 * Highest value of a numeric field across a list of rows. Used to
 * normalise the efficiency micro-bars in the quote table — each row's
 * CPU/RAM cell shows `value / max(column)`. Missing rows contribute 0;
 * non-numeric values are coerced to 0.
 */
export function maxOfField(rows, get) {
  if (!Array.isArray(rows) || rows.length === 0) return 0
  let m = 0
  for (const r of rows) {
    if (!r) continue
    const v = Number(get(r)) || 0
    if (v > m) m = v
  }
  return m
}

/** A complete annulus, used when a single resource type carries 100% of the cost. */
export function donutFullPath(cx, cy, r, ri) {
  // Two opposing half-arcs + an inner-circle subtraction (even-odd fill).
  return (
    `M ${cx - r} ${cy} A ${r} ${r} 0 1 1 ${cx + r} ${cy} A ${r} ${r} 0 1 1 ${cx - r} ${cy} Z`
    + ` M ${cx - ri} ${cy} A ${ri} ${ri} 0 1 0 ${cx + ri} ${cy} A ${ri} ${ri} 0 1 0 ${cx - ri} ${cy} Z`
  )
}
