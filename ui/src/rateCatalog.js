// Presentation metadata for the Rate.java enum (WORST < LOW < MEDIUM < GOOD < BEST).
// A single MDI star glyph + colour encodes each rate: the three star glyphs
// (outline → half → full) convey "fullness", and a red→green colour ramp pins
// the exact level. Kept in one place so tables and dialogs stay consistent.

/** Rates in ascending performance order (matches the enum ordinal). */
export const RATE_ORDER = Object.freeze(['WORST', 'LOW', 'MEDIUM', 'GOOD', 'BEST'])

/** Options in best-first display order, as the legacy selects presented them. */
export const RATE_OPTIONS = Object.freeze(['BEST', 'GOOD', 'MEDIUM', 'LOW', 'WORST'])

const RATE_VISUALS = Object.freeze({
  WORST:  { icon: 'mdi-star-outline',   color: 'red' },
  LOW:    { icon: 'mdi-star-outline',   color: 'deep-orange' },
  MEDIUM: { icon: 'mdi-star-half-full', color: 'amber' },
  GOOD:   { icon: 'mdi-star',           color: 'light-green' },
  BEST:   { icon: 'mdi-star',           color: 'green' },
})

/** `{ icon, color }` for a rate code (any case), or null when unknown/empty. */
export function rateVisual(rate) {
  return RATE_VISUALS[String(rate || '').toUpperCase()] || null
}

/** i18n key for a rate's short label (shown in the field). */
export function rateLabelKey(rate) {
  return `prov.quote.rate.${String(rate || '').toUpperCase()}`
}

/** i18n key for a rate's explanation (shown in the tooltip). */
export function rateDescKey(rate) {
  return `prov.quote.rate.${String(rate || '').toUpperCase()}.desc`
}
