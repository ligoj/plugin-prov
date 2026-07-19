<template>
  <span class="wl-icon">
    <v-icon
      size="20"
      :color="configured ? 'primary' : undefined"
      role="button"
      :aria-label="t('prov.quote.compute.workload')"
      @click="emit('click')"
    >
      mdi-chart-bell-curve-cumulative
    </v-icon>
    <!-- Dot above the icon when a profile is configured. -->
    <span v-if="configured" class="wl-dot" />

    <v-tooltip activator="parent" location="top" open-delay="150" content-class="wl-tip">
      <div class="wl-tip-body">
        <div class="wl-tip-explain">{{ explanation }}</div>
        <template v-if="configured">
          <div class="wl-tip-line">
            {{ t('prov.quote.workload.baseline') }}: <strong>{{ Math.round(parsed.baseline) }}%</strong>
          </div>
          <div v-if="parsed.periods.length" class="wl-tip-parts">
            <span v-for="(p, i) in parsed.periods" :key="i" class="wl-tip-part">{{ p.duration }}%@{{ p.cpu }}%</span>
          </div>
          <!-- Sparkline: N bars simulating the workload + dashed baseline. -->
          <svg class="wl-tip-spark" :viewBox="`0 0 ${spark.bars.length} 100`" preserveAspectRatio="none">
            <rect
              v-for="(h, i) in spark.bars"
              :key="i"
              :x="i + 0.08"
              :y="100 - h"
              :width="0.84"
              :height="h"
              class="wl-bar"
            />
            <line :x1="0" :y1="100 - parsed.baseline" :x2="spark.bars.length" :y2="100 - parsed.baseline" class="wl-baseline" />
          </svg>
        </template>
      </div>
    </v-tooltip>
  </span>
</template>

<script setup>
// The workload trigger: a chart icon that opens the editor, a dot indicator
// when a profile is set, and a tooltip with the meaning, the configured parts
// and a sparkline (bars simulating the profile + dashed baseline).
import { computed } from 'vue'
import { useI18nStore } from '@ligoj/host'
import { parseWorkload, hasWorkload, workloadBars } from '../quoteFormatters.js'

const props = defineProps({
  /** Workload string (`Workload#from` form), empty = none. */
  workload: { type: String, default: '' },
  /** Meaning shown at the top of the tooltip. */
  explanation: { type: String, default: '' },
})
const emit = defineEmits(['click'])

const { t } = useI18nStore()

const parsed = computed(() => parseWorkload(props.workload))
const configured = computed(() => hasWorkload(props.workload))
const spark = computed(() => workloadBars(props.workload, 24))
</script>

<style scoped>
.wl-icon {
  position: relative;
  display: inline-flex;
  cursor: pointer;
}
.wl-icon .v-icon {
  cursor: pointer;
}
.wl-dot {
  position: absolute;
  top: -1px;
  left: 50%;
  transform: translateX(-50%);
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgb(var(--v-theme-primary));
  pointer-events: none;
}
</style>

<style>
/* Tooltip content — unscoped (teleported outside the component). */
.wl-tip .wl-tip-body {
  min-width: 170px;
  max-width: 240px;
  line-height: 1.4;
}
.wl-tip .wl-tip-explain {
  font-size: 0.82em;
  opacity: 0.85;
  margin-bottom: 6px;
}
.wl-tip .wl-tip-line {
  font-size: 0.85em;
}
.wl-tip .wl-tip-parts {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 4px 0;
}
.wl-tip .wl-tip-part {
  font-size: 0.75em;
  font-variant-numeric: tabular-nums;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(var(--v-theme-on-surface), 0.14);
}
/* The tooltip background is `surface-variant` (dark grey in both themes), so
 * the sparkline sits on its own opaque `surface` panel to keep the bars and
 * baseline readable — white in light mode, dark in dark mode. */
.wl-tip .wl-tip-spark {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  display: block;
  background: rgb(var(--v-theme-surface));
  border: 1px solid rgba(var(--v-theme-on-surface), 0.15);
  border-radius: 4px;
}
.wl-tip .wl-bar {
  fill: rgb(var(--v-theme-primary));
  opacity: 0.85;
}
.wl-tip .wl-baseline {
  stroke: rgb(var(--v-theme-error));
  stroke-width: 1.5;
  stroke-dasharray: 2 2;
  vector-effect: non-scaling-stroke;
}
</style>
