<template>
  <div v-if="total > 0" class="quote-breakdown d-flex align-center flex-wrap ga-3">
    <!-- Donut. Plain SVG, no Vuetify chrome, so it can sit inside the
         header without worrying about table styling. -->
    <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" role="img" :aria-label="t('prov.quote.breakdown.title')">
      <template v-if="segments.length === 1">
        <path :d="fullPath" :fill="segments[0].color" fill-rule="evenodd" />
      </template>
      <template v-else>
        <path v-for="seg in segments" :key="seg.key" :d="seg.path" :fill="seg.color">
          <title>{{ seg.label }} — {{ mode === 'co2' ? formatCo2(seg.cost) : formatCost(seg.cost, currency) }} ({{ pct(seg.share) }})</title>
        </path>
      </template>
      <!-- Centre label: total cost OR CO₂ depending on view mode. -->
      <text :x="size / 2" :y="size / 2 - 6" text-anchor="middle" class="donut-total-value">
        {{ totalLabel }}
      </text>
      <text :x="size / 2" :y="size / 2 + 12" text-anchor="middle" class="donut-total-label">
        {{ mode === 'co2' ? t('prov.quote.breakdown.totalCo2') : t('prov.quote.breakdown.total') }}
      </text>
    </svg>

    <!-- Legend. -->
    <table class="quote-breakdown-legend">
      <tbody>
        <tr v-for="seg in segments" :key="seg.key">
          <td>
            <span class="dot" :style="{ backgroundColor: seg.color }" />
            {{ seg.label }}
          </td>
          <td class="text-right">{{ mode === 'co2' ? formatCo2(seg.cost) : formatCost(seg.cost, currency) }}</td>
          <td class="text-right text-medium-emphasis">{{ pct(seg.share) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18nStore } from '@ligoj/host'
import {
  TAB_TYPES,
  donutPath,
  donutFullPath,
  formatCo2,
  formatCost,
} from '../quoteFormatters.js'

const props = defineProps({
  /** Quote configuration (the inner `configuration` block of the API response). */
  config: { type: Object, default: null },
  /** 'cost' (default) or 'co2'. Switches the metric used for the slice
   *  sizes, the legend values, and the centre label. */
  mode: { type: String, default: 'cost' },
})

const { t } = useI18nStore()

const size = 140
const r = 60
const ri = 36
const cx = size / 2
const cy = size / 2

const currency = computed(() => props.config?.currency || { unit: '$', rate: 1 })

/**
 * Sum the active metric per resource type. The output's `cost` field
 * keeps its historical name regardless of mode — downstream code uses
 * it for slice sizing and legend rendering and only the formatter
 * differs. Resources without a value for the active metric contribute
 * 0; types absent or empty are filtered so the legend stays tight.
 */
const metricField = computed(() => (props.mode === 'co2' ? 'co2' : 'cost'))

const breakdown = computed(() => {
  if (!props.config) return []
  const field = metricField.value
  return TAB_TYPES
    .map((tab) => {
      const rows = Array.isArray(props.config[tab.listField]) ? props.config[tab.listField] : []
      const cost = rows.reduce((s, r) => s + (Number(r[field]) || 0), 0)
      return { key: tab.key, label: t(`prov.quote.tabs.${tab.key}`), color: tab.color, cost }
    })
    .filter((s) => s.cost > 0)
})

const totalLabel = computed(() =>
  props.mode === 'co2' ? formatCo2(total.value) : formatCost(total.value, currency.value),
)

const total = computed(() => breakdown.value.reduce((s, b) => s + b.cost, 0))

/** Pre-computes the SVG arc paths for each non-zero slice. */
const segments = computed(() => {
  const segs = breakdown.value
  const tot = total.value
  if (!tot || segs.length === 0) return []
  let angle = -Math.PI / 2 // start at 12 o'clock
  return segs.map((s) => {
    const share = s.cost / tot
    const next = angle + share * Math.PI * 2
    const path = donutPath(cx, cy, r, ri, angle, next)
    const out = { ...s, share, path }
    angle = next
    return out
  })
})

const fullPath = computed(() => donutFullPath(cx, cy, r, ri))

function pct(share) {
  if (!share) return '0%'
  if (share < 0.001) return '<0.1%'
  return `${(share * 100).toFixed(share < 0.1 ? 1 : 0)}%`
}
</script>

<style scoped>
.quote-breakdown {
  /* Aligns the inline display with the surrounding header items. */
  max-width: 480px;
}

.quote-breakdown-legend {
  border-collapse: collapse;
  font-size: 0.85rem;
  line-height: 1.4;
}

.quote-breakdown-legend td {
  padding: 0.1rem 0.5rem;
  white-space: nowrap;
}

.quote-breakdown-legend td:first-child {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.dot {
  display: inline-block;
  width: 0.7rem;
  height: 0.7rem;
  border-radius: 50%;
}

.text-right {
  text-align: right;
}

.donut-total-value {
  font-size: 0.85rem;
  font-weight: 500;
  fill: rgb(var(--v-theme-on-surface, 0 0 0));
}

.donut-total-label {
  font-size: 0.65rem;
  fill: rgb(var(--v-theme-on-surface, 0 0 0));
  opacity: 0.6;
}
</style>
