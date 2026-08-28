/**
 * Row model of the per-resource network links dialog (NetworkDialog /
 * NetworkLinksTab). A peer is addressed by a `TYPE#id` key, `TYPE` being the
 * backend `ResourceType` name. The link `rate` is the workload FREQUENCY in
 * seconds (3600 = hourly); `0` / `null` mean a continuous workload, the
 * default — the rows keep `0` (a concrete value for the select), the REST
 * payload sends `null`.
 */

/** An empty, editable link row (continuous workload). */
export function emptyRow() {
  return { name: null, peer: null, port: null, rate: 0, throughput: null }
}

/** `INSTANCE#12` for a resource type name and identifier. */
export function peerKey(type, id) {
  return `${type}#${id}`
}

/** `{ type, id }` of a peer key, or null when the key is incomplete. */
export function parsePeerKey(key) {
  const [type, id] = String(key || '').split('#')
  return type && id ? { type, id: Number(id) } : null
}

/** Frequency presets, in seconds; `0` is the continuous workload. */
export const FREQUENCIES = Object.freeze([
  { key: 'continuous', seconds: 0 },
  { key: 'minutely', seconds: 60 },
  { key: 'hourly', seconds: 3600 },
  { key: 'daily', seconds: 86400 },
  { key: 'weekly', seconds: 604800 },
  { key: 'monthly', seconds: 2592000 },
  { key: 'yearly', seconds: 31536000 },
])

/**
 * Select items of the frequency presets (`{ value, title }`), plus a
 * "custom" entry when `value` is a non-preset number of seconds (a value set
 * through the API), so the select always shows the stored frequency.
 */
export function frequencyItems(value, t) {
  const items = FREQUENCIES.map((f) => ({ value: f.seconds, title: t(`prov.quote.network.frequency.${f.key}`) }))
  const seconds = normalizeRate(value)
  if (seconds != null && !FREQUENCIES.some((f) => f.seconds === seconds)) {
    items.push({ value: seconds, title: t('prov.quote.network.frequency.custom', { seconds }) })
  }
  return items
}

/** Frequency for the REST payload: `null` when continuous (null / 0 / blank), else the seconds. */
export function normalizeRate(value) {
  if (value === '' || value == null) return null
  const n = Number(value)
  return Number.isFinite(n) && n > 0 ? Math.round(n) : null
}

/**
 * `name:value` tags of one resource, read case-insensitively (the quote
 * `tags` map is keyed by `ResourceType`, upper or lower case).
 */
export function resourceTags(config, type, id) {
  const tags = config?.tags
  if (!tags) return []
  const byId = tags[type] ?? tags[String(type).toUpperCase()] ?? tags[String(type).toLowerCase()]
  const list = byId?.[id] ?? byId?.[String(id)] ?? []
  return list.map((tg) => (tg.value == null || tg.value === '' ? tg.name : `${tg.name}:${tg.value}`))
}

const sameType = (a, b) => String(a || '').toUpperCase() === String(b || '').toUpperCase()

/**
 * Inbound / outbound link counts of one resource in the quote `networks`,
 * whatever the enum casing of the REST JSON.
 */
export function linkCounts(config, resourceType, id) {
  let inbound = 0
  let outbound = 0
  for (const l of config?.networks || []) {
    if (sameType(l.targetType, resourceType) && l.target === id) inbound += 1
    if (sameType(l.sourceType, resourceType) && l.source === id) outbound += 1
  }
  return { inbound, outbound }
}

/**
 * Parts of the row-action chip summarizing the resource links (down = inbound,
 * up = outbound), or `null` when the resource has no link — the chip is only
 * shown when something is defined.
 */
export function networkChip(config, resourceType, id) {
  const { inbound, outbound } = linkCounts(config, resourceType, id)
  if (!inbound && !outbound) return null
  return [
    { icon: 'mdi-arrow-down', text: inbound },
    { icon: 'mdi-arrow-up', text: outbound },
  ]
}
