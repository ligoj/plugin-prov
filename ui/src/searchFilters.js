/**
 * Advanced search filters (feature 13 phase 2) — pure engine.
 *
 * A filter is `{ field, op, value, value2? }`; a filter set is
 * `{ mode: 'AND'|'OR', filters: [...] }` combined on top of the quick text
 * query. Text matching is regex-aware: an explicit `/pattern/` or a query
 * containing regex metacharacters that compiles is used as a case-insensitive
 * regular expression, anything else as a plain substring.
 *
 * The engine is context-driven (`ctx`) so it stays pure: the view provides the
 * resolvers that know how a row links to its profiles (ids → names), tags and
 * location object.
 */
import { rowSearchValues } from './quoteFormatters.js'

/** Field catalog: `kind` drives both the value editor and the match. */
export const FILTER_FIELDS = Object.freeze([
  { key: 'text', kind: 'text' },      // any searched value
  { key: 'name', kind: 'text' },
  { key: 'type', kind: 'text' },      // resolved instance/storage type name or code
  { key: 'term', kind: 'text' },
  { key: 'os', kind: 'enum' },
  { key: 'engine', kind: 'enum' },
  { key: 'location', kind: 'location' },
  { key: 'usage', kind: 'profile' },
  { key: 'budget', kind: 'profile' },
  { key: 'optimizer', kind: 'profile' },
  { key: 'tag', kind: 'tag' },
  { key: 'cost', kind: 'number' },
  { key: 'co2', kind: 'number' },
])

/** Numeric comparison operators. */
export const NUMBER_OPS = Object.freeze(['>', '<', '='])

/**
 * Build a text matcher from a query. Regex detection: an explicit
 * `/pattern/` form always tries regex; otherwise a query containing a regex
 * metacharacter that compiles is used as one. Fallback: case-insensitive
 * substring. An empty query matches everything.
 */
export function textMatcher(query) {
  const q = String(query ?? '').trim()
  if (!q) return () => true
  let re = null
  if (q.length > 2 && q.startsWith('/') && q.endsWith('/')) {
    try {
      re = new RegExp(q.slice(1, -1), 'i')
    } catch {
      re = null
    }
  } else if (/[\\^$.*+?()[\]{}|]/.test(q)) {
    try {
      re = new RegExp(q, 'i')
    } catch {
      re = null
    }
  }
  const lower = q.toLowerCase()
  return (v) => {
    const s = String(v ?? '')
    return re ? re.test(s) : s.toLowerCase().includes(lower)
  }
}

/** Regex-aware quick match over every searched value of a row. */
export function quickMatch(row, matcher) {
  return rowSearchValues(row).some((v) => v != null && matcher(v))
}

const num = (v) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

/**
 * Evaluate one filter against a row.
 *
 * @param {object} row     the quote resource row.
 * @param {string} tabKey  the row's resource type key ('instance', …).
 * @param {object} filter  `{ field, op, value, value2? }`.
 * @param {object} ctx     `{ profileName(field, row), locationName(row), tagsFor(tabKey, id) }`.
 */
export function matchesFilter(row, tabKey, filter, ctx) {
  const { field, op, value, value2 } = filter || {}
  if (!field || value == null || value === '') return true
  switch (field) {
    case 'cost':
    case 'co2': {
      const actual = num(row?.[field])
      const wanted = num(value)
      if (actual == null || wanted == null) return false
      if (op === '<') return actual < wanted
      if (op === '=') return Math.abs(actual - wanted) < 0.005
      return actual > wanted // default '>'
    }
    case 'name':
      return textMatcher(value)(row?.name)
    case 'type':
      return textMatcher(value)(row?.price?.type?.name ?? row?.price?.type?.code)
    case 'term':
      return textMatcher(value)(row?.price?.term?.name)
    case 'os':
      return String(row?.os || row?.price?.os || '').toUpperCase() === String(value).toUpperCase()
    case 'engine':
      return String(row?.engine || row?.price?.engine || '').toUpperCase() === String(value).toUpperCase()
    case 'location':
      return (ctx?.locationName ? ctx.locationName(row) : row?.location?.name) === value
    case 'usage':
    case 'budget':
    case 'optimizer':
      return (ctx?.profileName ? ctx.profileName(field, row) : null) === value
    case 'tag': {
      const tags = ctx?.tagsFor ? ctx.tagsFor(tabKey, row?.id) : []
      return (tags || []).some((tg) => tg?.name === value && (!value2 || tg?.value === value2))
    }
    default:
      return quickMatch(row, textMatcher(value))
  }
}

/**
 * Evaluate a whole filter set (`{ mode, filters }`) against a row. An empty
 * set matches everything; `mode` combines the filters with AND (default) / OR.
 */
export function rowPasses(row, tabKey, state, ctx) {
  const filters = (state?.filters || []).filter((f) => f?.field && f.value != null && f.value !== '')
  if (!filters.length) return true
  return state.mode === 'OR'
    ? filters.some((f) => matchesFilter(row, tabKey, f, ctx))
    : filters.every((f) => matchesFilter(row, tabKey, f, ctx))
}
