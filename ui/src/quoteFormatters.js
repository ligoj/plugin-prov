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

/** A complete annulus, used when a single resource type carries 100% of the cost. */
export function donutFullPath(cx, cy, r, ri) {
  // Two opposing half-arcs + an inner-circle subtraction (even-odd fill).
  return (
    `M ${cx - r} ${cy} A ${r} ${r} 0 1 1 ${cx + r} ${cy} A ${r} ${r} 0 1 1 ${cx - r} ${cy} Z`
    + ` M ${cx - ri} ${cy} A ${ri} ${ri} 0 1 0 ${cx + ri} ${cy} A ${ri} ${ri} 0 1 0 ${cx - ri} ${cy} Z`
  )
}
