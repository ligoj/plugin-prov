/**
 * Formatting helpers for the quote view. Ported (compact form) from the
 * legacy `webjars/service/prov/prov.js` — only the formatters the
 * first-iteration QuoteView actually uses.
 *
 * The plugin owns these so the view has no dependency on private host
 * modules. Mirrors host-side helpers in
 * `app-ui/src/main/webapp/src/plugins/prov/provFormatters.js`.
 */

/**
 * Reduced-precision number formatting.
 *
 * The 2026 redesign asks every unit-bearing figure to drop useless
 * precision: never more than THREE digits before the decimal separator
 * and never more than TWO after it. In practice that means ~4 significant
 * figures, so `8248.6` becomes `8.25` (once scaled to tonnes) and
 * `163573` becomes `163.6` (once scaled to TB).
 *
 * Callers scale the value onto a unit ladder first (so the integer part
 * already fits in 3 digits); this helper only rounds + renders it,
 * locale-aware and with trailing zeros trimmed.
 */
export function formatReduced(value, locale) {
  if (!Number.isFinite(value)) return '0'
  const intDigits = Math.max(1, Math.trunc(Math.abs(value)).toString().length)
  const decimals = Math.max(0, Math.min(2, 4 - intDigits))
  return new Intl.NumberFormat(locale, { maximumFractionDigits: decimals }).format(value)
}

/**
 * Scales `value` (expressed in `units[0]`) up the ladder until its integer
 * part fits in three digits, then renders it with `formatReduced`.
 *   units  ordered unit labels, smallest first (e.g. ['g', 'kg', 't'])
 *   radix  1000 for SI units, 1024 for binary memory
 * Returns e.g. `"8.25 t"`.
 */
export function scaleUnit(value, units, { radix = 1000, locale } = {}) {
  let v = value
  let i = 0
  while (Math.abs(v) >= radix && i < units.length - 1) {
    v /= radix
    i += 1
  }
  return `${formatReduced(v, locale)} ${units[i]}`
}

/** SI magnitude prefixes for money — appended directly to the unit ("M$"). */
const AMOUNT_PREFIXES = ['', 'k', 'M', 'G', 'T', 'P']

/**
 * Currency-aware monthly cost. Accepts a number or a min/max object.
 *
 * Small amounts keep the familiar money precision (3 decimals below a
 * unit, cents up to 100, whole units below 1000). Anything larger is
 * SI-scaled so the header total reads `1.5 M$` instead of `1,500,622 $`
 * — honouring the "≤3 digits before the separator" rule.
 */
export function formatCost(value, currency = { unit: '$', rate: 1 }, locale) {
  if (value == null) return '-'
  const rate = currency.rate || 1
  const unit = currency.unit || '$'
  const v = value * rate
  const av = Math.abs(v)
  if (av < 1) return `${v.toFixed(3)} ${unit}`
  if (av < 100) return `${v.toFixed(2)} ${unit}`
  if (av < 1000) {
    return `${new Intl.NumberFormat(locale, { maximumFractionDigits: 0 }).format(v)} ${unit}`
  }
  let scaled = v
  let i = 0
  while (Math.abs(scaled) >= 1000 && i < AMOUNT_PREFIXES.length - 1) {
    scaled /= 1000
    i += 1
  }
  return `${formatReduced(scaled, locale)} ${AMOUNT_PREFIXES[i]}${unit}`
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

/** Memory in MB → most readable binary unit (memory is base-1024). */
export function formatRam(mb, locale) {
  if (mb == null) return ''
  return scaleUnit(mb, ['MB', 'GB', 'TB'], { radix: 1024, locale })
}

/** Disk storage in GB → most readable SI unit (`163573` → `163.6 TB`). */
export function formatStorage(gb, locale) {
  if (gb == null) return ''
  return scaleUnit(gb, ['GB', 'TB', 'PB'], { locale })
}

export function formatCpu(value) {
  if (value == null) return ''
  return value % 1 === 0 ? `${value}` : value.toFixed(1)
}

/** CO₂-equivalent emissions in grams → g / kg / t (`8248600` → `8.25 t`). */
export function formatCo2(grams, locale) {
  if (grams == null) return '-'
  return scaleUnit(grams, ['g', 'kg', 't'], { locale })
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
 * the cost-breakdown donut. Colours are Vuetify semantic theme tokens
 * (CSS variable expressions) so the donut follows the active theme —
 * they must be applied through inline `style` bindings (`fill` /
 * `background-color`), not SVG presentation attributes, because
 * attribute values don't resolve `var()`.
 */
export const TAB_TYPES = [
  { key: 'instance',  icon: 'mdi-server',   listField: 'instances',  color: 'rgb(var(--v-theme-primary))' },
  { key: 'database',  icon: 'mdi-database', listField: 'databases',  color: 'rgb(var(--v-theme-success))' },
  { key: 'container', icon: 'mdi-docker',   listField: 'containers', color: 'rgb(var(--v-theme-info))' },
  { key: 'function',  icon: 'mdi-lambda',   listField: 'functions',  color: 'rgb(var(--v-theme-secondary))' },
  { key: 'storage',   icon: 'mdi-harddisk', listField: 'storages',   color: 'rgb(var(--v-theme-warning))' },
  { key: 'support',   icon: 'mdi-lifebuoy', listField: 'supports',   color: 'rgb(var(--v-theme-error))' },
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
 * Sums the cost range over a list of costed resources — each contributes
 * its `cost` to the minimum and its `maxCost` (falling back to `cost`) to
 * the maximum; missing/non-numeric values count as 0. Used to apply the
 * filtered-out delta to the quote's authoritative total.
 */
export function sumCostRange(rows) {
  let min = 0
  let max = 0
  if (!Array.isArray(rows)) return { min, max }
  for (const r of rows) {
    if (!r) continue
    min += Number(r.cost) || 0
    max += Number(r.maxCost ?? r.cost) || 0
  }
  return { min, max }
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

/**
 * Next name for a "create another" clone: increments a trailing `-<number>`
 * suffix, or appends `-1` when there is none.
 *   "web"     → "web-1"
 *   "web-1"   → "web-2"
 *   "db-9"    → "db-10"
 *   "a-b-3"   → "a-b-4"
 */
export function nextName(name) {
  const s = String(name ?? '')
  const m = s.match(/-(\d+)$/)
  if (m) return `${s.slice(0, m.index)}-${parseInt(m[1], 10) + 1}`
  return `${s}-1`
}

/**
 * Number of months projected by the cost timeline — a fixed horizon
 * (matching the legacy 3-year `BARCHART_DURATION`).
 */
export const MONTH_HORIZON = 36

/**
 * Active `[start, end)` month window of a resource within the timeline
 * horizon. The window starts at the effective usage start (the resource's
 * own usage, else the quote default; past/negative starts clamp to 0 =
 * "now") and lasts for the usage duration — a duration of 0 or 1 means
 * "unbounded" and fills the horizon (the enum default is 1).
 */
export function resourceMonthRange(row, config, horizon = MONTH_HORIZON) {
  const usage = row?.usage || config?.usage || null
  const start = Math.min(horizon, Math.max(0, Math.round(Number(usage?.start) || 0)))
  const durRaw = Math.round(Number(usage?.duration) || 0)
  const dur = durRaw > 1 ? durRaw : horizon
  return { start, end: Math.min(horizon, start + dur) }
}

/** True when a resource is billed in the given (0-based) month of the horizon. */
export function rowInMonth(row, config, month, horizon = MONTH_HORIZON) {
  const { start, end } = resourceMonthRange(row, config, horizon)
  return month >= start && month < end
}

/**
 * Projects the quote's recurring cost over a fixed month horizon, stacked
 * by resource type — the data behind the cost-timeline bar chart.
 *
 * Each resource contributes both its minimal and maximal monthly value
 * (`cost`/`maxCost`, or `co2`/`maxCo2` in CO₂ mode) to every month of its
 * active window (see `resourceMonthRange`). Per month/type the chart shows
 * the minimum as a solid segment and the min→max gap as a faded segment.
 *
 * @param {object} config quote configuration block.
 * @param {object} [opts]
 * @param {'cost'|'co2'} [opts.field='cost'] metric to accumulate.
 * @param {number} [opts.horizon=MONTH_HORIZON] number of months.
 * @returns {{ horizon:number, series:Array<{key,color,values:Array<{min,max}>}>, totals:Array<{min,max}>, max:number }}
 */
export function costTimeline(config, { field = 'cost', horizon = MONTH_HORIZON } = {}) {
  const empty = { horizon: 0, series: [], totals: [], max: 0 }
  if (!config) return empty
  const maxField = field === 'co2' ? 'maxCo2' : 'maxCost'

  const perType = TAB_TYPES.map(() =>
    Array.from({ length: horizon }, () => ({ min: 0, max: 0 })),
  )
  let any = false
  TAB_TYPES.forEach((tab, ti) => {
    const rows = Array.isArray(config[tab.listField]) ? config[tab.listField] : []
    for (const row of rows) {
      const min = Number(row?.[field]) || 0
      const max = Math.max(min, Number(row?.[maxField] ?? row?.[field]) || 0)
      if (min <= 0 && max <= 0) continue
      any = true
      const { start, end } = resourceMonthRange(row, config, horizon)
      for (let m = start; m < end; m++) {
        perType[ti][m].min += min
        perType[ti][m].max += max
      }
    }
  })
  if (!any) return empty

  const totals = Array.from({ length: horizon }, () => ({ min: 0, max: 0 }))
  const series = []
  TAB_TYPES.forEach((tab, i) => {
    const values = perType[i]
    if (values.some((v) => v.max > 0)) {
      series.push({ key: tab.key, color: tab.color, values })
      for (let m = 0; m < horizon; m++) {
        totals[m].min += values[m].min
        totals[m].max += values[m].max
      }
    }
  })
  if (series.length === 0) return empty

  return { horizon, series, totals, max: Math.max(0, ...totals.map((t) => t.max)) }
}

/**
 * Provisioning efficiency — the cost-weighted share of the paid-for capacity
 * that the requested resources actually use, mirroring the legacy `updateGauge`.
 *
 * Per compute type: `0.8·(cpu reserved / cpu available) + 0.2·(ram reserved /
 * ram available)`, where "available" is the best-matching offer's capacity.
 * Storage: `size / max(size, type.minimal)` (the minimum billable block).
 * Overall: `Σ(cost · utilisation) / Σ(cost)` across compute + storage (support
 * excluded). 1 means a perfect fit; lower means paying for unused headroom.
 *
 * @returns {{ overall: number, byType: Array<{key,efficiency,cost}>, costNoSupport: number }}
 */
export function computeEfficiency(config) {
  if (!config) return { overall: 1, byType: [], costNoSupport: 0 }
  const clamp01 = (x) => (x < 0 ? 0 : x > 1 ? 1 : x)
  const ramAdj = (Number(config.ramAdjustedRate) || 100) / 100
  let weightCost = 0
  let costNoSupport = 0
  const byType = []

  for (const t of TAB_TYPES) {
    if (t.key === 'storage' || t.key === 'support') continue
    const rows = Array.isArray(config[t.listField]) ? config[t.listField] : []
    let cpuR = 0, cpuA = 0, ramR = 0, ramA = 0, cost = 0
    for (const r of rows) {
      if (!r) continue
      const nb = Number(r.minQuantity) || 1
      cpuR += (Number(r.cpu) || 0) * nb
      cpuA += (Number(r.price?.type?.cpu) || 0) * nb
      ramR += (Number(r.ram) || 0) * ramAdj * nb
      ramA += (Number(r.price?.type?.ram) || 0) * nb
      cost += Number(r.cost) || 0
    }
    costNoSupport += cost
    if (cpuA > 0) weightCost += cost * 0.8 * (cpuR / cpuA)
    if (ramA > 0) weightCost += cost * 0.2 * (ramR / ramA)
    const wCpu = cpuA > 0 ? 0.8 : 0
    const wRam = ramA > 0 ? 0.2 : 0
    const wTot = wCpu + wRam
    if (cost > 0 && wTot > 0) {
      const eff = (wCpu * (cpuR / cpuA) + wRam * (ramR / ramA)) / wTot
      byType.push({ key: t.key, efficiency: clamp01(eff), cost })
    }
  }

  // Storage: requested size vs the type's minimum billable size.
  const storages = Array.isArray(config.storages) ? config.storages : []
  let sR = 0, sA = 0, sCost = 0
  for (const s of storages) {
    if (!s) continue
    const size = Number(s.size) || 0
    const minimal = Number(s.price?.type?.minimal) || 0
    sR += size
    sA += Math.max(size, minimal)
    sCost += Number(s.cost) || 0
  }
  costNoSupport += sCost
  if (sA > 0) weightCost += sCost * (sR / sA)
  if (sCost > 0 && sA > 0) byType.push({ key: 'storage', efficiency: clamp01(sR / sA), cost: sCost })

  const overall = costNoSupport > 0 ? clamp01(weightCost / costNoSupport) : 1
  return { overall, byType, costNoSupport }
}
