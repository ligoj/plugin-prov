<template>
  <span
    v-if="markup"
    class="os-icon"
    role="img"
    :aria-label="os"
    :title="os"
    v-html="markup"
  />
</template>

<script setup>
// Brand glyph for a VM operating system (icons from dashboardicons.com).
// Inlined SVG so it scales with the surrounding font size (em units) and needs
// no runtime asset request. Renders nothing for an OS with no known glyph,
// letting the caller's text label stand alone. NB: keep the <span> the single
// template root (no sibling comment) so `title`/attrs land on it.
import { computed } from 'vue'

// Eagerly inline every glyph as a raw SVG string. Raw imports become JS string
// literals in the bundle, so the library build ships them inside index.js
// instead of emitting separate `index.svg` assets that would collide.
const modules = import.meta.glob('../assets/os/*.svg', {
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

// OS values without a dedicated dashboardicons glyph borrow a sensible one.
// FreeBSD has no icon there; the enum already prices it as a Linux variant.
const ALIASES = { freebsd: 'linux' }

const props = defineProps({
  /** VmOs enum value, any case (e.g. "RHEL", "linux"). */
  os: { type: String, default: '' },
})

const markup = computed(() => {
  const key = String(props.os || '').toLowerCase()
  return ICONS[key] || ICONS[ALIASES[key]] || null
})
</script>

<style scoped>
.os-icon {
  display: inline-flex;
  align-items: center;
  vertical-align: -0.15em;
}
/* Uniform square footprint so every glyph aligns in lists/tables. Each SVG
 * carries a viewBox, so the default preserveAspectRatio="meet" scales the
 * artwork to fit the box (wide logos letterbox instead of clipping). */
.os-icon :deep(svg) {
  display: block;
  width: 1.35em;
  height: 1.35em;
}
</style>
