<template>
  <span class="loc-label">
    <span v-if="glyph" class="loc-flag">{{ glyph }}</span>
    <span v-if="!flagOnly" class="loc-name">{{ name }}</span>
    <span v-if="!flagOnly && showCode && code && code !== name" class="loc-code">{{ code }}</span>

    <!-- Detail tooltip: glyph, country/continent name + code, continent. -->
    <v-tooltip v-if="tooltip" activator="parent" location="top" open-delay="150" content-class="loc-tip">
      <div class="loc-tip-body">
        <div class="loc-tip-head">
          <span v-if="glyph" class="loc-tip-flag">{{ glyph }}</span>
          <strong>{{ name }}</strong>
          <span v-if="geo.a2" class="loc-tip-a2">{{ geo.a2 }}</span>
        </div>
        <div v-if="subRegion" class="loc-tip-line loc-tip-sub">📍 {{ subRegion }}</div>
        <div class="loc-tip-line">
          <span class="loc-tip-key">{{ t('prov.quote.location.code') }}</span>{{ code }}
        </div>
        <div v-if="continent && continent !== name" class="loc-tip-line">
          <span class="loc-tip-key">{{ t('prov.quote.location.continent') }}</span>{{ continent }}
        </div>
        <!-- Real map when GPS coordinates are present (OpenStreetMap embed, no
             API key, so nothing to bundle). Lazy — loads only while open. The
             iframe is taller than its frame and centre-clipped, hiding OSM's
             top zoom controls and bottom "Report a problem" attribution bar;
             we re-add the required attribution ourselves below. -->
        <div v-if="mapSrc" class="loc-tip-map-wrap">
          <iframe
            class="loc-tip-map"
            :src="mapSrc"
            :title="name"
            loading="lazy"
            referrerpolicy="no-referrer"
          />
          <span class="loc-tip-map-attr">© OpenStreetMap contributors</span>
        </div>
      </div>
    </v-tooltip>
  </span>
</template>

<script setup>
// Emoji glyph + localized name for a location — a country flag when a country
// is known, otherwise the region globe (🌍/🌎/🌏) for the resolved continent.
// Country / continent are resolved from the explicit fields or derived from the
// code name. Pure emoji + text, so nothing to fetch.
import { computed } from 'vue'
import { useI18nStore } from '@ligoj/host'
import { resolveGeo, geoGlyph, m49Name } from '../locationCatalog.js'

const props = defineProps({
  /** A ProvLocation object (name, countryA2, countryM49, continentM49, …). */
  location: { type: Object, default: null },
  /** Append the location code name (e.g. "us-east-1") in a muted style. */
  showCode: { type: Boolean, default: false },
  /** Render only the flag glyph (name/code hidden) — used in dense table cells. */
  flagOnly: { type: Boolean, default: false },
  /** Show the detail tooltip on hover. */
  tooltip: { type: Boolean, default: true },
})

const i18n = useI18nStore()
const t = i18n.t

const geo = computed(() => resolveGeo(props.location) || {})
const glyph = computed(() => geoGlyph(geo.value))
const continent = computed(() => m49Name(geo.value.continentM49, i18n.locale))
// Country name, else the continent (for country-less regions), else the code.
const name = computed(
  () => m49Name(geo.value.countryM49, i18n.locale) || continent.value || props.location?.name || '',
)
const code = computed(() => props.location?.name || '')
// A state or city (e.g. "Paris"), when the provider gives one.
const subRegion = computed(() => props.location?.subRegion || '')

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v))
/** OpenStreetMap embed URL centred on the location's coordinates, or null. */
const mapSrc = computed(() => {
  const { lat, lon } = geo.value
  if (lat == null || lon == null) return null
  const dLat = 2.2, dLon = 3.2
  const bbox = [
    clamp(lon - dLon, -180, 180), clamp(lat - dLat, -85, 85),
    clamp(lon + dLon, -180, 180), clamp(lat + dLat, -85, 85),
  ].map((n) => n.toFixed(4)).join(',')
  const marker = `${clamp(lat, -85, 85).toFixed(4)},${clamp(lon, -180, 180).toFixed(4)}`
  return `https://www.openstreetmap.org/export/embed.html?bbox=${encodeURIComponent(bbox)}&layer=mapnik&marker=${encodeURIComponent(marker)}`
})
</script>

<style scoped>
.loc-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.loc-flag {
  font-size: 1.05em;
  line-height: 1;
  flex: none;
}
.loc-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.loc-code {
  font-family: var(--mono, monospace);
  font-size: 0.82em;
  color: rgba(var(--v-theme-on-surface), 0.6);
}
</style>

<style>
/* Tooltip content — unscoped (teleported outside the component). */
.loc-tip .loc-tip-body {
  min-width: 150px;
  max-width: 260px;
  line-height: 1.4;
}
.loc-tip .loc-tip-sub {
  font-weight: 600;
}
.loc-tip .loc-tip-map-wrap {
  position: relative;
  height: 150px;
  margin-top: 6px;
  border-radius: 6px;
  overflow: hidden;
  background: rgba(var(--v-theme-on-surface), 0.06);
}
/* Taller than the frame + shifted up so the centre band shows: the marker
 * stays centred while OSM's top controls and bottom attribution are clipped. */
.loc-tip .loc-tip-map {
  width: 100%;
  height: 280px;
  margin-top: -65px;
  border: 0;
  display: block;
}
.loc-tip .loc-tip-map-attr {
  position: absolute;
  right: 2px;
  bottom: 1px;
  padding: 0 3px;
  border-radius: 3px;
  font-size: 9px;
  line-height: 1.4;
  color: rgba(0, 0, 0, 0.62);
  background: rgba(255, 255, 255, 0.72);
  pointer-events: none;
}
.loc-tip .loc-tip-head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 2px;
}
.loc-tip .loc-tip-flag {
  font-size: 1.15em;
}
.loc-tip .loc-tip-a2 {
  margin-left: auto;
  font-family: var(--mono, monospace);
  font-size: 0.8em;
  opacity: 0.7;
}
.loc-tip .loc-tip-line {
  font-size: 0.85em;
}
.loc-tip .loc-tip-key {
  opacity: 0.6;
  margin-right: 0.35rem;
}
</style>
