/**
 * Quote snapshot diff (feature 08) — pure helpers.
 *
 * Both sides of a diff are normalized to the same canonical row shape
 * (`resourceType`, `name`, cost / co2 figures, resolved `typeName` / `term`,
 * `location`, `size`) so a snapshot document (already stored normalized by the
 * backend) and the live quote configuration can be compared directly. Rows are
 * matched by the stable `resourceType:name` identity.
 */
import { diffRatio } from './compareApi.js'

const LISTS = Object.freeze([
  ['instance', 'instances'],
  ['database', 'databases'],
  ['container', 'containers'],
  ['function', 'functions'],
  ['storage', 'storages'],
  ['support', 'supports'],
])

/** Canonical rows from a live quote configuration. */
export function normalizeConfig(config) {
  const out = []
  for (const [resourceType, listField] of LISTS) {
    for (const r of config?.[listField] || []) {
      if (!r) continue
      out.push({
        resourceType,
        name: r.name,
        cost: Number(r.cost) || 0,
        maxCost: Number(r.maxCost ?? r.cost) || 0,
        co2: Number(r.co2) || 0,
        maxCo2: Number(r.maxCo2 ?? r.co2) || 0,
        typeName: r.price?.type?.name ?? null,
        term: r.price?.term?.name ?? null,
        location: r.location?.name || r.price?.location?.name || null,
        size: r.size ?? null,
      })
    }
  }
  return out
}

/** Canonical rows from a snapshot document (stored normalized by the backend). */
export function snapshotRows(document) {
  return (document?.resources || []).map((r) => ({
    resourceType: r.resourceType,
    name: r.name,
    cost: Number(r.cost) || 0,
    maxCost: Number(r.maxCost ?? r.cost) || 0,
    co2: Number(r.co2) || 0,
    maxCo2: Number(r.maxCo2 ?? r.co2) || 0,
    typeName: r.typeName ?? null,
    term: r.term ?? null,
    location: r.location ?? null,
    size: r.size ?? null,
  }))
}

const keyOf = (r) => `${r.resourceType}:${r.name}`

/**
 * Structured diff from rows `a` (the reference, e.g. a snapshot) to rows `b`
 * (the target, e.g. the current quote).
 *
 * @param {Array} a       canonical rows of the reference side.
 * @param {Array} b       canonical rows of the target side.
 * @param {'cost'|'co2'} [field='cost'] metric driving values and totals.
 * @returns {{
 *   added:   Array<{row, value}>,
 *   removed: Array<{row, value}>,
 *   changed: Array<{a, b, from:number, to:number, delta:number, pct:number|null, priceChanged:boolean}>,
 *   unchanged: number,
 *   totals: { from:number, to:number, delta:number, pct:number|null },
 * }}
 */
export function quoteDiff(a, b, field = 'cost') {
  const byKeyA = new Map((a || []).map((r) => [keyOf(r), r]))
  const byKeyB = new Map((b || []).map((r) => [keyOf(r), r]))
  const value = (r) => Number(r?.[field]) || 0

  const added = []
  const removed = []
  const changed = []
  let unchanged = 0
  let fromTotal = 0
  let toTotal = 0

  for (const [key, rowA] of byKeyA) {
    fromTotal += value(rowA)
    const rowB = byKeyB.get(key)
    if (!rowB) {
      removed.push({ row: rowA, value: value(rowA) })
      continue
    }
    const from = value(rowA)
    const to = value(rowB)
    const priceChanged = rowA.typeName !== rowB.typeName || rowA.term !== rowB.term
    if (Math.abs(to - from) > 1e-9 || priceChanged) {
      changed.push({ a: rowA, b: rowB, from, to, delta: to - from, pct: diffRatio(from, to), priceChanged })
    } else {
      unchanged++
    }
  }
  for (const [key, rowB] of byKeyB) {
    toTotal += value(rowB)
    if (!byKeyA.has(key)) {
      added.push({ row: rowB, value: value(rowB) })
    }
  }

  // Big movers first, so the report leads with what matters.
  added.sort((x, y) => y.value - x.value)
  removed.sort((x, y) => y.value - x.value)
  changed.sort((x, y) => Math.abs(y.delta) - Math.abs(x.delta))

  return {
    added,
    removed,
    changed,
    unchanged,
    totals: { from: fromTotal, to: toTotal, delta: toTotal - fromTotal, pct: diffRatio(fromTotal, toTotal) },
  }
}
