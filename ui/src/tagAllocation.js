/**
 * Tag-based cost allocation (feature 04) — pure, client-side aggregation over
 * the already-loaded quote config. The config carries tags as
 * `{ TYPE: { resourceId: [{ name, value }] } }` (upper-case ResourceType keys)
 * and every resource carries its own `cost` / `co2`, so no backend call is
 * needed to break the cost down by a tag key.
 */

/** Colour cycle for tag-value groups (theme tokens, resolved via `style`). */
export const TAG_PALETTE = Object.freeze([
  'rgb(var(--v-theme-primary))', 'rgb(var(--v-theme-info))', 'rgb(var(--v-theme-success))',
  'rgb(var(--v-theme-warning))', 'rgb(var(--v-theme-error))', 'rgb(var(--v-theme-secondary))',
  'color-mix(in srgb, rgb(var(--v-theme-primary)) 55%, rgb(var(--v-theme-surface)))',
  'color-mix(in srgb, rgb(var(--v-theme-success)) 55%, rgb(var(--v-theme-surface)))',
])
export const TAG_OTHER_KEY = '__other__'
export const TAG_UNTAGGED_KEY = '__untagged__'
export const TAG_OTHER_COLOR = 'rgba(var(--v-theme-on-surface), 0.45)'
export const TAG_UNTAGGED_COLOR = 'rgba(var(--v-theme-on-surface), 0.22)'

/** Resource types that can carry cost + tags, as [TYPE, listField]. */
const TYPES = Object.freeze([
  ['INSTANCE', 'instances'],
  ['DATABASE', 'databases'],
  ['CONTAINER', 'containers'],
  ['FUNCTION', 'functions'],
  ['STORAGE', 'storages'],
  ['SUPPORT', 'supports'],
])

/** Distinct tag keys (names) present anywhere in the config, sorted. */
export function tagKeys(config) {
  const set = new Set()
  const tags = config?.tags
  if (tags && typeof tags === 'object') {
    for (const byId of Object.values(tags)) {
      for (const arr of Object.values(byId || {})) {
        for (const tag of arr || []) {
          if (tag?.name) set.add(tag.name)
        }
      }
    }
  }
  return [...set].sort((a, b) => a.localeCompare(b))
}

/**
 * Allocate the quote's cost (or CO₂) by the values of one tag key.
 *
 * Each distinct value becomes a bucket; resources lacking the key fall into a
 * separate "untagged" bucket (kept honest — not redistributed). A resource with
 * several values for the same key is counted under its first value (values are
 * expected to be single per key). Coverage is the share of the total carrying
 * the key.
 *
 * @param {object} config quote configuration block.
 * @param {string} key    the tag name to group by.
 * @param {'cost'|'co2'} [field='cost']
 * @returns {{
 *   buckets: Array<{ value:string, amount:number, count:number, share:number }>,
 *   untagged: { amount:number, count:number, share:number },
 *   total:number, tagged:number, coverage:number, count:number,
 * }}
 */
export function tagAllocation(config, key, field = 'cost') {
  const tags = config?.tags || {}
  const byValue = new Map()
  let total = 0
  let tagged = 0
  let count = 0
  const untagged = { amount: 0, count: 0 }

  const bump = (value, amount) => {
    const b = byValue.get(value) || { value, amount: 0, count: 0 }
    b.amount += amount
    b.count += 1
    byValue.set(value, b)
  }

  for (const [type, listField] of TYPES) {
    const rows = Array.isArray(config?.[listField]) ? config[listField] : []
    const resTags = tags[type] || {}
    for (const r of rows) {
      const amount = Number(r?.[field]) || 0
      total += amount
      count += 1
      const rowTags = resTags[r?.id] || []
      const match = key ? rowTags.find((tg) => tg?.name === key) : undefined
      if (match) {
        bump(match.value != null && match.value !== '' ? match.value : '', amount)
        tagged += amount
      } else {
        untagged.amount += amount
        untagged.count += 1
      }
    }
  }

  const share = (v) => (total > 0 ? v / total : 0)
  const buckets = [...byValue.values()]
    .map((b) => ({ ...b, share: share(b.amount) }))
    .sort((a, b) => b.amount - a.amount)

  return {
    buckets,
    untagged: { ...untagged, share: share(untagged.amount) },
    total,
    tagged,
    coverage: total > 0 ? tagged / total : 0,
    count,
  }
}

/**
 * Chart grouping by a tag key — the top `maxGroups` values by cost each get a
 * colour, everything else folds into a single "Other" group and unkeyed
 * resources into "Untagged", so the pie / bar chart stay readable.
 *
 * @returns {{ groups: Array<{key:string, color:string}>, groupOf: (type:string, resource:object)=>string }}
 */
export function tagGrouping(config, tagKey, field = 'cost', maxGroups = 8) {
  const alloc = tagAllocation(config, tagKey, field)
  const top = alloc.buckets.slice(0, maxGroups)
  const overflow = alloc.buckets.slice(maxGroups)
  const valueToGroup = new Map()
  const groups = []
  top.forEach((b, i) => {
    valueToGroup.set(b.value, b.value)
    groups.push({ key: b.value, color: TAG_PALETTE[i % TAG_PALETTE.length] })
  })
  if (overflow.length) {
    overflow.forEach((b) => valueToGroup.set(b.value, TAG_OTHER_KEY))
    groups.push({ key: TAG_OTHER_KEY, color: TAG_OTHER_COLOR })
  }
  if (alloc.untagged.amount > 0) {
    groups.push({ key: TAG_UNTAGGED_KEY, color: TAG_UNTAGGED_COLOR })
  }

  const tags = config?.tags || {}
  const groupOf = (type, resource) => {
    const rowTags = tags[String(type).toUpperCase()]?.[resource?.id] || []
    const match = tagKey ? rowTags.find((tg) => tg?.name === tagKey) : undefined
    if (!match) return TAG_UNTAGGED_KEY
    const value = match.value != null && match.value !== '' ? match.value : ''
    return valueToGroup.has(value) ? valueToGroup.get(value) : TAG_OTHER_KEY
  }
  return { groups, groupOf }
}
