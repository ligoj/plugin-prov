<template>
  <!-- Compact two-row cell: a small horizontal bar above the formatted
       value. Mirrors the legacy efficiency cells in the quote table.
       The bar normalises against the max value across the same tab so
       resources within a category can be compared at a glance. -->
  <div class="micro-bar-cell">
    <div class="micro-bar-track">
      <div class="micro-bar-fill" :style="fillStyle" />
    </div>
    <div class="micro-bar-label">{{ label }}</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** Resource's own value. */
  value: { type: [Number, null], default: 0 },
  /** Highest comparable value across the same column. */
  max: { type: Number, required: true },
  /** Pre-formatted text rendered below the bar. */
  label: { type: String, default: '' },
  /** Any CSS colour expression — usually `rgb(var(--v-theme-…))`. */
  color: { type: String, default: 'rgb(var(--v-theme-primary))' },
})

const fillStyle = computed(() => {
  const v = Number(props.value) || 0
  const m = Number(props.max) || 1
  const pct = Math.max(0, Math.min(100, (v / m) * 100))
  return { width: `${pct}%`, backgroundColor: props.color }
})
</script>

<style scoped>
.micro-bar-cell {
  display: inline-flex;
  flex-direction: column;
  align-items: stretch;
  min-width: 60px;
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
