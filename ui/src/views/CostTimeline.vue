<template>
  <div v-if="timeline.horizon > 0 && timeline.max > 0" class="cost-timeline">
    <div class="cost-timeline-title">
      {{ mode === 'co2' ? t('prov.quote.timeline.titleCo2') : t('prov.quote.timeline.title') }}
    </div>
    <svg
      :viewBox="`0 0 ${W} ${H}`"
      class="cost-timeline-svg"
      role="img"
      :aria-label="ariaLabel"
      preserveAspectRatio="xMidYMid meet"
    >
      <!-- Upper gridline + max value, faint baseline. -->
      <line :x1="padL" :y1="top" :x2="W - padR" :y2="top" class="ct-grid" />
      <line :x1="padL" :y1="baseline" :x2="W - padR" :y2="baseline" class="ct-axis" />
      <text :x="padL" :y="top - 4" class="ct-max">{{ fmt(timeline.max) }}</text>

      <!-- One clickable column per month: solid (min) + faded (gap) segments. -->
      <g
        v-for="bar in bars"
        :key="bar.index"
        class="ct-col"
        @click="onMonthClick(bar.index)"
      >
        <rect
          v-if="selectedMonth === bar.index"
          class="ct-selected"
          :x="bar.slotX"
          :y="top"
          :width="barSlot"
          :height="H - top"
        />
        <rect class="ct-hit" :x="bar.slotX" :y="top" :width="barSlot" :height="baseline - top" />

        <!-- Solid (min) + faded (gap) rects for a type, wrapped so hovering
             either drives the same tooltip. -->
        <g
          v-for="seg in bar.segments"
          :key="seg.key"
          class="ct-group"
          @mouseenter="onEnter($event, bar)"
          @mousemove="onMove"
          @mouseleave="onLeave"
        >
          <rect
            class="ct-min"
            :x="bar.x"
            :y="seg.yMin"
            :width="barWidth"
            :height="seg.hMin"
            :style="{ fill: seg.color }"
          />
          <rect
            v-if="seg.hGap > 0.01"
            class="ct-gap"
            :x="bar.x"
            :y="seg.yGap"
            :width="barWidth"
            :height="seg.hGap"
            :style="{ fill: seg.color }"
          />
        </g>

        <!-- Regular month tick. -->
        <text
          v-if="bar.showLabel && selectedMonth !== bar.index"
          :x="bar.x + barWidth / 2"
          :y="H - 4"
          text-anchor="middle"
          class="ct-xlabel"
        >
          {{ bar.month }}
        </text>
        <!-- Selected month: a bigger, bordered, high-contrast badge drawn last
             so it sits above the vertical selection band. -->
        <template v-else-if="selectedMonth === bar.index">
          <rect
            class="ct-badge"
            :x="bar.x + barWidth / 2 - badgeW(bar.month) / 2"
            :y="H - 16"
            :width="badgeW(bar.month)"
            :height="14"
            :rx="3"
          />
          <text class="ct-badge-label" :x="bar.x + barWidth / 2" :y="H - 5" text-anchor="middle">
            {{ bar.month }}
          </text>
        </template>
      </g>
    </svg>

    <!-- Cursor-following tooltip. A teleported fixed div (not v-tooltip):
         Vuetify's activator doesn't bind reliably to SVG nodes, and a plain
         div is immune to open/close state getting stuck. -->
    <Teleport to="body">
      <div v-if="hover" class="ct-float" role="tooltip" :style="{ left: `${pos.x}px`, top: `${pos.y}px` }">
        <div class="ct-tip-title">{{ t('prov.quote.timeline.month', { n: hover.month }) }}</div>
        <div class="ct-rows">
          <template v-for="seg in hover.segments" :key="seg.key">
            <span class="ct-dot" :style="{ backgroundColor: seg.color }" />
            <span class="ct-name">{{ seg.label }}</span>
            <span class="ct-cost">{{ costLabel(seg) }}</span>
            <span class="ct-pct">{{ pct(seg.share) }}</span>
          </template>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18nStore } from '@ligoj/host'
import { costTimeline, formatCost, formatCo2 } from '../quoteFormatters.js'

const props = defineProps({
  /** Quote configuration block (search-filtered — NOT month-filtered). */
  config: { type: Object, default: null },
  /** 'cost' (default) or 'co2' — which metric to project. */
  mode: { type: String, default: 'cost' },
  /** Currently filtered month (0-based) or null. Highlights that column. */
  selectedMonth: { type: Number, default: null },
})
const emit = defineEmits(['month-click'])

const { t } = useI18nStore()

// Plain-SVG geometry (viewBox units; the element scales to its container).
const W = 380
const H = 176
const padL = 6
const padR = 6
const top = 20
const baseline = 150
const plotH = baseline - top

const currency = computed(() => props.config?.currency || { unit: '$', rate: 1 })
const fmt = (v) => (props.mode === 'co2' ? formatCo2(v) : formatCost(v, currency.value))

const timeline = computed(() =>
  costTimeline(props.config, { field: props.mode === 'co2' ? 'co2' : 'cost' }),
)

const barSlot = computed(() => (W - padL - padR) / Math.max(1, timeline.value.horizon))
const barWidth = computed(() => Math.max(1, barSlot.value * 0.72))

const bars = computed(() => {
  const { horizon, series, totals, max } = timeline.value
  if (!horizon || !max) return []
  const step = Math.max(1, Math.ceil(horizon / 8))
  const out = []
  for (let m = 0; m < horizon; m++) {
    let cumY = baseline
    const segments = []
    for (const s of series) {
      const v = s.values[m]
      if (v.max <= 0) continue
      const hMin = (v.min / max) * plotH
      const hGap = ((v.max - v.min) / max) * plotH
      const yMin = cumY - hMin
      const yGap = yMin - hGap
      cumY = yGap
      segments.push({
        key: s.key,
        color: s.color,
        label: t(`prov.quote.tabs.${s.key}`),
        min: v.min,
        max: v.max,
        share: totals[m].max ? v.max / totals[m].max : 0,
        yMin, hMin, yGap, hGap,
      })
    }
    out.push({
      index: m,
      month: m + 1,
      slotX: padL + m * barSlot.value,
      x: padL + m * barSlot.value + (barSlot.value - barWidth.value) / 2,
      segments,
      showLabel: m % step === 0,
    })
  }
  return out
})

function onMonthClick(index) {
  emit('month-click', index)
}

/** Badge width for the selected-month number (wider for two digits). */
function badgeW(month) {
  return String(month).length > 1 ? 17 : 13
}

/* ---- Cursor-following tooltip state ---- */
const hover = ref(null) // the hovered bar (whole month)
const pos = ref({ x: 0, y: 0 })

function onEnter(e, bar) {
  hover.value = bar
  pos.value = { x: e.clientX, y: e.clientY }
}
function onMove(e) {
  pos.value = { x: e.clientX, y: e.clientY }
}
function onLeave() {
  hover.value = null
}

function pct(share) {
  if (!share) return '0%'
  if (share < 0.001) return '<0.1%'
  return `${(share * 100).toFixed(share < 0.1 ? 1 : 0)}%`
}

function costLabel(seg) {
  return seg.max > seg.min ? `${fmt(seg.min)} – ${fmt(seg.max)}` : fmt(seg.min)
}

const ariaLabel = computed(() =>
  props.mode === 'co2' ? t('prov.quote.timeline.titleCo2') : t('prov.quote.timeline.title'),
)

defineExpose({ timeline, bars })
</script>

<style scoped>
.cost-timeline {
  flex: 1 1 320px;
  min-width: 280px;
  max-width: 460px;
}

.cost-timeline-title {
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(var(--v-theme-on-surface), 0.55);
  margin-bottom: 4px;
}

.cost-timeline-svg {
  width: 100%;
  height: auto;
  display: block;
  /* The bars are interactive, not content — never let a click/drag select
     the rects or the axis labels. */
  user-select: none;
  -webkit-user-select: none;
}

.ct-axis {
  stroke: rgba(var(--v-theme-on-surface), 0.25);
  stroke-width: 1;
}

.ct-grid {
  stroke: rgba(var(--v-theme-on-surface), 0.1);
  stroke-width: 1;
  stroke-dasharray: 3 3;
}

.ct-max {
  font-size: 9px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  fill: rgba(var(--v-theme-on-surface), 0.55);
}

.ct-xlabel {
  font-size: 8.5px;
  font-variant-numeric: tabular-nums;
  fill: rgba(var(--v-theme-on-surface), 0.5);
}

/* Selected month number: a bordered, high-contrast badge over the highlight. */
.ct-badge {
  fill: rgb(var(--v-theme-primary));
  stroke: rgb(var(--v-theme-surface));
  stroke-width: 1.25;
}
.ct-badge-label {
  font-size: 11px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  fill: rgb(var(--v-theme-on-primary));
}

.ct-col {
  cursor: pointer;
}

/* Transparent full-column hit area so clicks anywhere in the column select
 * the month; the coloured segments sit on top and drive the tooltip. */
.ct-hit {
  fill: transparent;
  pointer-events: all;
}

.ct-selected {
  fill: rgba(var(--v-theme-primary), 0.2);
  stroke: rgb(var(--v-theme-primary));
  stroke-width: 2;
  pointer-events: none;
}

/* Solid = minimal cost; faded = gap up to the maximal cost. The gap stays
 * close to the solid tone so the two read as one bar with a soft cap. */
.ct-min {
  stroke: rgb(var(--v-theme-surface));
  stroke-width: 0.5;
}
.ct-gap {
  fill-opacity: 0.6;
  stroke: rgb(var(--v-theme-surface));
  stroke-width: 0.5;
}
</style>

<style>
/* Floating tooltip — unscoped because it is teleported to <body>. Inverts the
 * surface tokens for the usual dark-on-light / light-on-dark tooltip look. */
.ct-float {
  position: fixed;
  z-index: 2400;
  transform: translate(-50%, calc(-100% - 14px));
  pointer-events: none;
  background: rgb(var(--v-theme-on-surface));
  color: rgb(var(--v-theme-surface));
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.35;
  white-space: nowrap;
  box-shadow: 0 3px 10px rgba(0, 0, 0, 0.35);
}
.ct-float .ct-tip-title {
  font-weight: 700;
  margin-bottom: 4px;
}
.ct-float .ct-rows {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  align-items: center;
  column-gap: 0.6rem;
  row-gap: 2px;
}
.ct-float .ct-dot {
  width: 0.6rem;
  height: 0.6rem;
  border-radius: 50%;
  display: inline-block;
  flex: none;
}
.ct-float .ct-cost,
.ct-float .ct-pct {
  font-variant-numeric: tabular-nums;
  text-align: right;
}
.ct-float .ct-pct {
  opacity: 0.7;
}
</style>
