// Location presentation + search + geo-resolution helpers, mirroring the legacy
// jQuery `locationMatcher` / `locationToHtml`. A location carries a code name
// (`name`, e.g. "us-east-1"), an ISO alpha-2 country (`countryA2`) and UN M49
// numeric codes for its country / region / continent — but any of those may be
// absent, in which case we derive them from the code name.
import { M49_NAMES } from './i18n/m49.js'
import { A2_CONTINENT, A2_M49, CLOUD_CONTINENT_TOKENS } from './geoData.js'

// Continent M49 code → Earth-globe emoji facing that region. Sub-regions map to
// their parent continent's globe.
const CONTINENT_GLOBE = {
  2: '🌍', 18: '🌍',                       // Africa (Europe-Africa globe)
  150: '🌍', 39: '🌍', 151: '🌍', 154: '🌍', 155: '🌍', // Europe
  19: '🌎', 5: '🌎', 21: '🌎',             // Americas
  142: '🌏', 30: '🌏', 34: '🌏', 35: '🌏', 143: '🌏', 145: '🌏', // Asia
  9: '🌏', 36: '🌏', 53: '🌏',             // Oceania (Asia-Australia globe)
}

const num = (v) => {
  if (v == null || v === '') return null
  const n = parseInt(v, 10)
  return Number.isNaN(n) ? null : n
}

/** Normalized upper-case alpha-2 (UK → GB), or null. */
export function normA2(a2) {
  if (!a2) return null
  const u = String(a2).toUpperCase()
  return u === 'UK' ? 'GB' : u
}

/** Localized name for a UN M49 numeric code, or null (English fallback). */
export function m49Name(code, locale = 'en') {
  const n = num(code)
  if (n == null) return null
  return (M49_NAMES[locale] || M49_NAMES.en)[n] || M49_NAMES.en[n] || null
}

/**
 * Emoji flag for an alpha-2 code (two regional-indicator symbols). Renders a
 * flag on macOS/iOS/Linux/Android; Windows shows the two letters. No asset
 * files, so it always resolves.
 */
export function flagEmoji(a2) {
  const u = normA2(a2)
  if (!u || u.length !== 2) return ''
  const A = 0x1f1e6
  const c0 = u.charCodeAt(0)
  const c1 = u.charCodeAt(1)
  if (c0 < 65 || c0 > 90 || c1 < 65 || c1 > 90) return ''
  return String.fromCodePoint(A + (c0 - 65), A + (c1 - 65))
}

/**
 * Derive a country / continent from a location code by splitting on `-`/`_` and
 * taking the first meaningful token: a cloud continent prefix (eu/ap/sa/af/me)
 * wins over the ISO lookup, otherwise a valid ISO alpha-2 gives the country.
 */
export function geoFromCode(name) {
  if (!name) return null
  for (const tok of String(name).toLowerCase().split(/[-_]/)) {
    if (!tok) continue
    if (CLOUD_CONTINENT_TOKENS[tok] != null) return { continentM49: CLOUD_CONTINENT_TOKENS[tok] }
    if (A2_M49[tok] != null) {
      return { a2: tok.toUpperCase(), countryM49: A2_M49[tok], continentM49: A2_CONTINENT[tok] ?? null }
    }
  }
  return null
}

/**
 * Resolve a location to `{ a2, countryM49, continentM49, lat, lon }`, using the
 * explicit fields when present and falling back to the code-name derivation and
 * ISO tables. `a2` is upper-case (UK normalized to GB); numeric codes are ints.
 */
export function resolveGeo(location) {
  if (!location) return null
  let a2 = normA2(location.countryA2)
  let countryM49 = num(location.countryM49)
  let continentM49 = num(location.continentM49) ?? num(location.regionM49)

  if (!a2 && countryM49 == null) {
    const d = geoFromCode(location.name)
    if (d) {
      a2 = d.a2 || a2
      countryM49 = d.countryM49 ?? countryM49
      continentM49 = continentM49 ?? d.continentM49 ?? null
    }
  }
  if (a2) {
    const lc = a2.toLowerCase()
    if (countryM49 == null) countryM49 = A2_M49[lc] ?? null
    if (continentM49 == null) continentM49 = A2_CONTINENT[lc] ?? null
  }
  return {
    a2,
    countryM49,
    continentM49,
    lat: location.latitude != null ? Number(location.latitude) : null,
    lon: location.longitude != null ? Number(location.longitude) : null,
  }
}

/** Earth-globe emoji facing a continent's region, or '' when unknown. */
export function globeEmoji(continentM49) {
  const n = num(continentM49)
  if (n == null) return ''
  return CONTINENT_GLOBE[n] || '🌍'
}

/**
 * The glyph for a resolved location: the country flag when a country is known,
 * else the region globe when only a continent is — otherwise ''.
 */
export function geoGlyph(geo) {
  if (!geo) return ''
  return geo.a2 ? flagEmoji(geo.a2) : globeEmoji(geo.continentM49)
}

/**
 * True when `query` matches the location by any of: code name, localized
 * country name, country code (A2 with the UK/GB alias, or M49), continent /
 * region name, or description. Country / continent are resolved (explicit or
 * derived) and matched in both the active locale and English.
 */
export function locationMatches(location, query, locale = 'en') {
  if (!query) return true
  if (!location) return false
  const q = String(query).toLowerCase().trim()
  if (!q) return true
  const geo = resolveGeo(location)
  const haystack = [
    location.name,
    location.description,
    location.countryA2,
    geo.a2,
    geo.countryM49,
    m49Name(geo.countryM49, locale),
    m49Name(geo.countryM49, 'en'),
    m49Name(geo.continentM49, locale),
    m49Name(geo.continentM49, 'en'),
    m49Name(location.regionM49, locale),
    location.subRegion,
  ]
  for (const v of haystack) {
    if (v != null && String(v).toLowerCase().includes(q)) return true
  }
  return false
}
