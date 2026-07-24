/**
 * Cross-provider comparison — frontend helpers (feature 02, backend-driven).
 *
 * A main subscription (MS) keeps one or more compared subscriptions (CS) as
 * synchronized clones (see the backend `ProvComparisonResource`). These helpers
 * compute the MS-vs-CS price / CO₂ difference the quote table and summary render
 * (percentage + direction + colour). Pure functions — unit tested.
 */

/** Uppercase REST enum name for a tab key (the backend `ResourceType`). */
export function resourceTypeName(tabKey) {
  return String(tabKey || '').toUpperCase()
}

/**
 * Signed ratio `(cs - ms) / ms`. `null` when there is nothing meaningful to
 * compare (missing values, or MS is zero). Negative means the CS is cheaper /
 * greener than the MS.
 */
export function diffRatio(ms, cs) {
  const m = Number(ms)
  const c = Number(cs)
  if (!Number.isFinite(m) || !Number.isFinite(c) || m <= 0) return null
  return (c - m) / m
}

/**
 * Visual metadata for a diff ratio: `dir` (down = CS better, up = CS worse,
 * same, na), a Vuetify semantic `color`, and an `icon`. A small dead-band keeps
 * rounding noise from flipping the indicator.
 */
export function diffMeta(ratio, { threshold = 0.005 } = {}) {
  if (ratio == null) return { dir: 'na', color: '', icon: '' }
  if (Math.abs(ratio) < threshold) {
    return { dir: 'same', color: 'medium-emphasis', icon: 'mdi-approximately-equal' }
  }
  return ratio < 0
    ? { dir: 'down', color: 'success', icon: 'mdi-arrow-down-thin' } // CS cheaper / greener
    : { dir: 'up', color: 'error', icon: 'mdi-arrow-up-thin' } // CS pricier / dirtier
}

/** Signed percentage label for a ratio, e.g. `-12%`, `+4.5%`, `0%`. */
export function formatDiffPct(ratio) {
  if (ratio == null) return ''
  const pct = ratio * 100
  const digits = Math.abs(pct) < 10 ? 1 : 0
  const rounded = Number(pct.toFixed(digits)) // drops trailing .0 (0.0 → 0)
  return `${rounded > 0 ? '+' : ''}${rounded}%`
}

/** Resource types carried in a comparison, in tab order. */
const INDEXED = [
  ['instance', 'instances'],
  ['database', 'databases'],
  ['container', 'containers'],
  ['function', 'functions'],
  ['storage', 'storages'],
  ['support', 'supports'],
]

/**
 * Index a config's resources by `"type:name"` → metric value (the min figure).
 * `field` is `'cost'` or `'co2'`. Resources are matched between MS and CS by
 * name (the clone preserves names), so this is the join key for the table diff.
 */
export function valueIndex(config, field = 'cost') {
  const out = new Map()
  for (const [type, listField] of INDEXED) {
    for (const r of config?.[listField] || []) {
      if (r?.name != null) out.set(`${type}:${r.name}`, Number(r[field]) || 0)
    }
  }
  return out
}

/** Look up one resource's value in an index built by {@link valueIndex}. */
export function indexValue(index, tabKey, name) {
  if (!index || name == null) return null
  const v = index.get(`${tabKey}:${name}`)
  return v == null ? null : v
}

/** Sum a metric over all compared resources of a config. */
export function totalValue(config, field = 'cost') {
  let sum = 0
  for (const v of valueIndex(config, field).values()) sum += v
  return sum
}

/** The compute types a comparison covers (storage / support are out of scope). */
export const SUMMARY_TYPES = Object.freeze([
  ['instance', 'instances'],
  ['database', 'databases'],
  ['container', 'containers'],
  ['function', 'functions'],
])

/**
 * MS-vs-CS summary over the compared compute types. A resource the CS could not
 * reproduce ("unmatched") contributes the **MS** value to the CS side (not 0),
 * so an unavailable price reads as "no change" rather than a free saving.
 *
 * @param {object} msConfig the main-subscription quote config.
 * @param {Map<string,number>} csIndex CS values keyed "type:name" ({@link valueIndex}).
 * @param {'cost'|'co2'} [field='cost']
 * @returns {{
 *   byType: Object<string,{ count:number, ms:number, cs:number, unmatched:number }>,
 *   msTotal:number, csTotal:number, unmatched:number, unmatchedCost:number, pct:number|null,
 * }}
 */
export function comparisonSummary(msConfig, csIndex, field = 'cost') {
  const byType = {}
  let msTotal = 0
  let csTotal = 0
  let unmatched = 0
  let unmatchedCost = 0
  for (const [type, listField] of SUMMARY_TYPES) {
    const rows = Array.isArray(msConfig?.[listField]) ? msConfig[listField] : []
    if (!rows.length) continue
    let count = 0
    let ms = 0
    let cs = 0
    let unm = 0
    for (const r of rows) {
      const msVal = Number(r?.[field]) || 0
      const csVal = csIndex ? csIndex.get(`${type}:${r?.name}`) : undefined
      const matched = csVal != null
      count += 1
      ms += msVal
      cs += matched ? csVal : msVal // unmatched → MS value
      if (!matched) { unm += 1; unmatched += 1; unmatchedCost += msVal }
    }
    byType[type] = { count, ms, cs, unmatched: unm }
    msTotal += ms
    csTotal += cs
  }
  return { byType, msTotal, csTotal, unmatched, unmatchedCost, pct: diffRatio(msTotal, csTotal) }
}
