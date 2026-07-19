// Usage-profile presentation + metadata (ProvUsage). A usage carries a rate
// (1–100 %), a commitment `duration` (months), a `start` offset and a set of
// "convertible" / reservation flags. The 3-state Boolean flags of the legacy
// model are surfaced here as plain booleans — checked means "required/true".

export const USAGE_MAX_RATE = 100

/** Quick rate presets (share of a 24×7 week). Labels are i18n keys. */
export const USAGE_RATE_TEMPLATES = [
  { rate: 29, key: 'prov.quote.usage.template.business1' }, // Mon–Fri 8:30–18:30
  { rate: 35, key: 'prov.quote.usage.template.business2' }, // Mon–Fri 8:00–20:00
  { rate: 100, key: 'prov.quote.usage.template.full' },
]

/** The boolean flags, in display order (each has `<field>`/`<field>Help` i18n). */
export const USAGE_FLAGS = [
  'convertibleType',
  'convertibleFamily',
  'convertibleOs',
  'convertibleEngine',
  'convertibleLocation',
  'reservation',
]

/**
 * Short summary shown beside a usage name (rate + commitment), e.g. "35% · 12mo".
 * Mirrors the legacy `formatUsage` detail.
 */
export function usageSummary(usage) {
  if (!usage) return ''
  const bits = []
  if (usage.rate != null) bits.push(`${usage.rate}%`)
  if (Number(usage.duration) > 1) bits.push(`${usage.duration}mo`)
  if (Number(usage.start) > 0) bits.push(`+${usage.start}mo`)
  return bits.join(' · ')
}

/** Build the REST edition payload (UsageEditionVo) from an editor form. */
export function usagePayload(form) {
  return {
    id: form.id ?? undefined,
    name: form.name,
    rate: Number(form.rate),
    duration: Number(form.duration),
    start: Number(form.start) || 0,
    convertibleOs: !!form.convertibleOs,
    convertibleEngine: !!form.convertibleEngine,
    convertibleLocation: !!form.convertibleLocation,
    convertibleFamily: !!form.convertibleFamily,
    convertibleType: !!form.convertibleType,
    reservation: !!form.reservation,
  }
}
