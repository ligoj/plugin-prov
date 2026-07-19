<template>
  <span
    v-if="markup"
    class="engine-icon"
    role="img"
    :aria-label="engine"
    :title="engine"
    v-html="markup"
  />
</template>

<script setup>
// Brand glyph for a database engine (icons from dashboardicons.com), mirroring
// OsIcon. SVGs are inlined as raw strings so the library build ships them
// inside index.js (no separate assets to serve). Renders nothing for an unknown
// engine, letting the caller's text/tooltip stand alone.
import { computed } from 'vue'
import { engineKey } from '../engineCatalog.js'

const modules = import.meta.glob('../assets/engine/*.svg', {
  query: '?raw',
  import: 'default',
  eager: true,
})
const ICONS = Object.fromEntries(
  Object.entries(modules).map(([path, svg]) => [
    path.split('/').pop().replace('.svg', ''),
    svg,
  ]),
)

const props = defineProps({
  /** Engine value, any case, incl. compound (e.g. "MYSQL", "AURORA MYSQL"). */
  engine: { type: String, default: '' },
})

const markup = computed(() => ICONS[engineKey(props.engine)] || null)
</script>

<style scoped>
.engine-icon {
  display: inline-flex;
  align-items: center;
  vertical-align: -0.15em;
}
/* Uniform square footprint; each SVG carries a viewBox so it scales to fit. */
.engine-icon :deep(svg) {
  display: block;
  width: 1.35em;
  height: 1.35em;
}
</style>
