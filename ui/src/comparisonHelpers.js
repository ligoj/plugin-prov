/**
 * Pure helpers for the cross-provider comparison (feature 02).
 *
 * A quote's resources describe provider-neutral *requirements* (cpu, ram, os,
 * engine …). Re-pricing them against another provider reuses the existing
 * per-type lookup endpoints (`{subscription}/{type}-lookup`), one call per
 * (resource × target subscription). The logic that turns a resource row into a
 * lookup query, and that reduces the raw per-cell results into a comparison
 * matrix, lives here so it is unit-tested and independent of the view.
 */

/** Compute resource types that can be re-priced (excludes storage/support). */
export const COMPARABLE_TYPES = Object.freeze([
  { key: 'instance', listField: 'instances' },
  { key: 'database', listField: 'databases' },
  { key: 'container', listField: 'containers' },
  { key: 'function', listField: 'functions' },
])

/**
 * Provider-neutral lookup query for a resource row, as a plain
 * `{ param: value }` map (string values, ready for URLSearchParams).
 *
 * Only requirements that mean the same thing in every catalog are included:
 * cpu / ram / os / engine / edition / software / runtime / gpu / *Rate and the
 * function workload params. Deliberately OMITTED — `location`, `usage`,
 * `budget`, `optimizer`, `type`, `processor`, `architecture`, `license`: these
 * are resolved by NAME against the *target* subscription's own catalog/profiles
 * and would either raise `EntityNotFoundException` (unknown region/profile) or
 * over-constrain the match. Region/usage/commitment alignment is phase 2.
 */
export function buildLookupParams(row, type) {
  const p = {}
  if (!row) return p
  const cpu = row.cpu ?? row.price?.type?.cpu
  const ram = row.ram ?? row.price?.type?.ram
  if (cpu != null) p.cpu = String(cpu)
  if (ram != null) p.ram = String(Math.round(Number(ram))) // row.ram is already MiB

  if (type === 'instance' || type === 'container') {
    const os = row.os || row.price?.os
    if (os) p.os = String(os).toUpperCase()
  }
  if (type === 'instance') {
    const software = row.software || row.price?.software
    if (software) p.software = String(software).toLowerCase()
  }
  if (type === 'database') {
    const engine = row.engine || row.price?.engine
    if (engine) p.engine = String(engine).toUpperCase()
    const edition = row.edition || row.price?.edition
    if (edition) p.edition = String(edition)
  }
  if (type === 'function') {
    if (row.nbRequests != null) p.nbRequests = String(row.nbRequests)
    if (row.duration != null) p.duration = String(row.duration)
    if (row.concurrency != null) p.concurrency = String(row.concurrency)
    const runtime = row.runtime || row.price?.runtime
    if (runtime) p.runtime = String(runtime)
  }
  if (row.gpu) p.gpu = String(row.gpu)
  for (const k of ['cpuRate', 'ramRate', 'networkRate', 'storageRate']) {
    if (row[k]) p[k] = String(row[k])
  }
  return p
}

/** Stable per-resource key (type + id, or type + index fallback). */
export function resourceKey(type, row, index) {
  return `${type}:${row?.id ?? `#${index}`}`
}

/**
 * Flattens a quote config into the comparable resource list — one entry per
 * instance / database / container / function, in tab order.
 * Each entry: `{ key, type, name, row }`.
 */
export function comparableResources(config) {
  const out = []
  if (!config) return out
  for (const { key, listField } of COMPARABLE_TYPES) {
    const rows = Array.isArray(config[listField]) ? config[listField] : []
    rows.forEach((row, i) => {
      out.push({ key: resourceKey(key, row, i), type: key, name: row?.name || `${key} #${i + 1}`, row })
    })
  }
  return out
}

/**
 * Reduces raw per-cell lookup results into the comparison summary for one
 * metric (`cost` or `co2`).
 *
 * @param {Array} rows        `[{ key, name, type, byProvider: { pid: {cost,co2}|null } }]`
 * @param {Array<string>} providerIds  provider ids, in column order.
 * @param {'cost'|'co2'} [field='cost']
 * @returns {{
 *   perRow: Array<{ key, name, type, bestPid: string|null }>,
 *   totals: Object<string,{ value:number, missing:number }>,
 *   bestOfBreed: { value:number, missing:number },
 *   bestPid: string|null,        // cheapest single provider that priced everything
 * }}
 */
export function summarizeComparison(rows, providerIds, field = 'cost') {
  const cellVal = (cell) => {
    const v = cell && cell[field]
    return typeof v === 'number' && Number.isFinite(v) ? v : null
  }

  const perRow = rows.map((r) => {
    let bestPid = null
    let bestVal = null
    for (const pid of providerIds) {
      const v = cellVal(r.byProvider?.[pid])
      if (v != null && (bestVal == null || v < bestVal)) { bestVal = v; bestPid = pid }
    }
    return { key: r.key, name: r.name, type: r.type, bestPid }
  })

  const totals = {}
  for (const pid of providerIds) {
    let value = 0
    let missing = 0
    for (const r of rows) {
      const v = cellVal(r.byProvider?.[pid])
      if (v == null) missing++
      else value += v
    }
    totals[pid] = { value, missing }
  }

  let bobValue = 0
  let bobMissing = 0
  for (const r of rows) {
    let m = null
    for (const pid of providerIds) {
      const v = cellVal(r.byProvider?.[pid])
      if (v != null && (m == null || v < m)) m = v
    }
    if (m == null) bobMissing++
    else bobValue += m
  }

  let bestPid = null
  for (const pid of providerIds) {
    const t = totals[pid]
    if (t.missing > 0) continue // only fully-priced providers can win the single-provider race
    if (bestPid == null || t.value < totals[bestPid].value) bestPid = pid
  }

  return { perRow, totals, bestOfBreed: { value: bobValue, missing: bobMissing }, bestPid }
}

/**
 * Runs `fn` over `items` with a bounded number of concurrent in-flight calls,
 * preserving input order in the returned results. Keeps N×M lookups from
 * hammering the backend all at once.
 */
export async function mapLimit(items, limit, fn) {
  const results = new Array(items.length)
  let next = 0
  const worker = async () => {
    while (next < items.length) {
      const i = next++
      results[i] = await fn(items[i], i)
    }
  }
  const n = Math.max(1, Math.min(limit, items.length))
  await Promise.all(Array.from({ length: n }, worker))
  return results
}
