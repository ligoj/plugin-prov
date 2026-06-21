<template>
  <div v-if="total > 0" class="quote-breakdown">
    <!-- Drill header — when the user has clicked a top-level slice we
         show the parent label + a back button. Otherwise the row is
         empty so layout doesn't shift. -->
    <div v-if="drill" class="d-flex align-center mb-1 ga-1 quote-breakdown-back">
      <button type="button" class="back-btn" @click="drill = null" :title="t('prov.quote.breakdown.back')">
        <span class="dot" :style="{ backgroundColor: parentColor }" />
        ← {{ drillTitle }}
      </button>
    </div>
    <div class="d-flex align-center flex-wrap ga-3">
      <!-- Donut. Plain SVG, no Vuetify chrome, so it can sit inside the
           header without worrying about table styling. -->
      <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" role="img" :aria-label="ariaLabel">
        <template v-if="segments.length === 1">
          <path :d="fullPath" :fill="segments[0].color" fill-rule="evenodd" :class="{ clickable: !drill && isDrillable(segments[0].key) }"
            @click="onSliceClick(segments[0])" />
        </template>
        <template v-else>
          <path v-for="seg in segments" :key="seg.key" :d="seg.path" :fill="seg.color"
            :class="{ clickable: !drill && isDrillable(seg.key) }" @click="onSliceClick(seg)">
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
          <tr v-for="seg in segments" :key="seg.key" :class="{ clickable: !drill && isDrillable(seg.key) }"
            @click="onSliceClick(seg)">
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
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
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

/* ----------------- Drill-down ----------------- *
 * When the user clicks a top-level slice we drill into its
 * sub-categorisation:
 *   - instance / container → per-OS
 *   - database             → per-engine
 *   - function             → per-runtime
 *   - storage              → per-price-type
 * `drill = null` shows the root view; otherwise it holds the parent
 * tab key.
 */
const drill = ref(null)

const DRILL_DIMENSIONS = {
  instance:  { field: 'os',     getter: (r) => r.os || r.price?.os },
  container: { field: 'os',     getter: (r) => r.os || r.price?.os },
  database:  { field: 'engine', getter: (r) => r.engine || r.price?.engine },
  function:  { field: 'runtime', getter: (r) => r.runtime || r.price?.runtime },
  storage:   { field: 'type',   getter: (r) => r.price?.type?.name || r.price?.type?.code },
}

/* Sub-segment palette. Picked to be distinct against any of the
 * top-level colors so the eye can tell drilled-down from root. */
const SUB_PALETTE = [
  '#1976D2', '#0288D1', '#00897B', '#43A047', '#7CB342',
  '#C0CA33', '#FDD835', '#FFB300', '#FB8C00', '#F4511E',
  '#E53935', '#D81B60', '#8E24AA', '#5E35B1', '#3949AB',
]

function isDrillable(key) {
  return !!DRILL_DIMENSIONS[key]
}

function onSliceClick(seg) {
  if (drill.value) {
    // Already drilled — clicking goes back to root.
    drill.value = null
    return
  }
  if (isDrillable(seg.key)) drill.value = seg.key
}

/* Reset drill if the config changes type-of-data (e.g. user navigates
 * to a different quote). The drilled key may no longer be relevant. */
watch(() => props.config, () => { drill.value = null })

const parentColor = computed(() => {
  const tab = TAB_TYPES.find((t) => t.key === drill.value)
  return tab ? tab.color : '#888'
})

const drillTitle = computed(() => {
  if (!drill.value) return ''
  const parentLabel = t(`prov.quote.tabs.${drill.value}`)
  const dim = DRILL_DIMENSIONS[drill.value]?.field
  const dimLabel = dim ? t(`prov.quote.breakdown.drill.${dim}`) : ''
  return dimLabel ? t('prov.quote.breakdown.drillTitle', { type: parentLabel, by: dimLabel }) : parentLabel
})

const ariaLabel = computed(() => drill.value ? drillTitle.value : t('prov.quote.breakdown.title'))

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

/**
 * Sub-breakdown for a drilled parent type. Groups rows by the
 * dimension defined in `DRILL_DIMENSIONS` and sums the active metric
 * per bucket. Falls back to `'?'` for resources missing the field so
 * the user sees how much cost is uncategorised.
 */
const subBreakdown = computed(() => {
  const key = drill.value
  if (!key || !props.config) return []
  const tab = TAB_TYPES.find((tt) => tt.key === key)
  const dim = DRILL_DIMENSIONS[key]
  if (!tab || !dim) return []
  const rows = Array.isArray(props.config[tab.listField]) ? props.config[tab.listField] : []
  const field = metricField.value
  const acc = new Map()
  for (const row of rows) {
    if (!row) continue
    const raw = dim.getter(row)
    const bucket = raw ? String(raw).toUpperCase() : '?'
    const v = Number(row[field]) || 0
    acc.set(bucket, (acc.get(bucket) || 0) + v)
  }
  // Drop buckets with zero cost. Sort by descending cost so the
  // dominant sub-category appears first in the legend.
  return [...acc.entries()]
    .filter(([, c]) => c > 0)
    .sort((a, b) => b[1] - a[1])
    .map(([label, cost], i) => ({
      key: `${key}:${label}`,
      label,
      cost,
      color: SUB_PALETTE[i % SUB_PALETTE.length],
    }))
})

/** The active breakdown — root or drilled. */
const activeBreakdown = computed(() => (drill.value ? subBreakdown.value : breakdown.value))

const totalLabel = computed(() =>
  props.mode === 'co2' ? formatCo2(total.value) : formatCost(total.value, currency.value),
)

const total = computed(() => activeBreakdown.value.reduce((s, b) => s + b.cost, 0))

/** Pre-computes the SVG arc paths for each non-zero slice. */
const segments = computed(() => {
  const segs = activeBreakdown.value
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

/* Expose drill state so tests can drive it without simulating SVG
 * clicks (which jsdom doesn't dispatch reliably for inline SVG). */
defineExpose({ drill, isDrillable, onSliceClick, breakdown, subBreakdown, activeBreakdown })
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

.quote-breakdown-legend tr.clickable td {
  cursor: pointer;
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

path.clickable {
  cursor: pointer;
  transition: opacity 120ms ease;
}
path.clickable:hover {
  opacity: 0.85;
}

.back-btn {
  background: transparent;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.18);
  border-radius: 4px;
  padding: 0.1rem 0.5rem;
  font-size: 0.8rem;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  color: inherit;
}
.back-btn:hover {
  background: rgba(var(--v-theme-on-surface), 0.04);
}
</style>
