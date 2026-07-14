<template>
  <!-- Compact two-row cell: a small horizontal bar above the formatted
       value, showing how fully the resource uses its type's capacity
       (value / max). The colour signals utilisation quality and the bar
       is dropped entirely once the reservation is essentially optimal. -->
  <div class="micro-bar-cell" :class="{ 'micro-bar-block': block }">
    <div v-if="showBar" class="micro-bar-track">
      <div class="micro-bar-fill" :style="fillStyle" />
    </div>
    <div v-if="label" class="micro-bar-label">{{ label }}</div>
    <v-tooltip v-if="tooltipText" activator="parent" location="top">
      <span style="white-space: pre-line;">{{ tooltipText }}</span>
    </v-tooltip>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** Resource's own value (the bar's numerator). */
  value: { type: [Number, null], default: 0 },
  /** Value that represents a full bar (100%). */
  max: { type: Number, required: true },
  /** Pre-formatted text rendered below the bar. Omit for a bar-only cell. */
  label: { type: String, default: '' },
  /** Explanation shown on hover (via v-tooltip). Omit for no tooltip. */
  tooltip: { type: String, default: '' },
  /**
   * Optional formatter for the value/max. When set, the tooltip leads with
   * a "requested / provided" line — `format(value) / format(max)` — above
   * the explanation. Callers pass the right per-metric unit formatter.
   */
  format: { type: Function, default: null },
  /** Stretch to fill the parent width (e.g. under a dialog input). */
  block: { type: Boolean, default: false },
})

/** Tooltip text: an optional "requested / provided" line + the explanation. */
const tooltipText = computed(() => {
  if (typeof props.format === 'function') {
    const line = `${props.format(Number(props.value) || 0)} / ${props.format(Number(props.max) || 0)}`
    return props.tooltip ? `${line}\n${props.tooltip}` : line
  }
  return props.tooltip
})

/** Fill ratio as a percentage, clamped to 0–100. */
const pct = computed(() => {
  const v = Number(props.value) || 0
  const m = Number(props.max) || 1
  return Math.max(0, Math.min(100, (v / m) * 100))
})

/**
 * A fuller reservation is better, so the bar is hidden once utilisation
 * reaches 90% (optimal — the number alone suffices) and otherwise escalates
 * from success (high) down to error (low, wasted capacity):
 *   [80,90) success · [60,80) info · [40,60) warning · [0,40) error
 */
const showBar = computed(() => pct.value < 90)

const barColor = computed(() => {
  const p = pct.value
  if (p >= 80) return 'rgb(var(--v-theme-success))'
  if (p >= 60) return 'rgb(var(--v-theme-info))'
  if (p >= 40) return 'rgb(var(--v-theme-warning))'
  return 'rgb(var(--v-theme-error))'
})

const fillStyle = computed(() => ({ width: `${pct.value}%`, backgroundColor: barColor.value }))
</script>

<style scoped>
.micro-bar-cell {
  display: inline-flex;
  flex-direction: column;
  align-items: stretch;
  min-width: 60px;
}
.micro-bar-cell.micro-bar-block {
  display: flex;
  width: 100%;
}
.micro-bar-track {
  height: 4px;
  border-radius: 2px;
  background: rgba(var(--v-theme-on-surface), 0.08);
  overflow: hidden;
}
.micro-bar-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 200ms ease;
}
.micro-bar-label {
  font-size: 0.85em;
  line-height: 1.2;
  margin-top: 2px;
  text-align: right;
}
</style>
