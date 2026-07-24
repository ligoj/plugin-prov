<template>
  <div v-if="show" class="carb-bar">
    <div class="carb-bar-row">
      <span class="carb-bar-label">{{ t('prov.quote.carbon.label') }}</span>
      <span class="carb-bar-pct">
        {{ pct(eff.overall) }}
        <span v-if="csEff" class="carb-cmp" :class="deltaClass">
          <v-icon size="10">mdi-arrow-right-thin</v-icon>{{ pct(csEff.overall) }}
        </span>
      </span>
    </div>
    <v-progress-linear :model-value="eff.overall * 100" :color="color" height="6" rounded bg-opacity="0.15" />
    <v-progress-linear v-if="csEff" :model-value="csEff.overall * 100" :color="colorOf(csEff.overall)" height="4"
      rounded bg-opacity="0.2" class="carb-cs-bar" />

    <!-- Lazy tooltip: explanation + per-type carbon efficiency (MS vs CS). -->
    <v-tooltip activator="parent" location="bottom" open-delay="150" content-class="carb-tip">
      <div class="carb-tip-body">
        <div class="carb-tip-explain">{{ t('prov.quote.carbon.explain') }}</div>
        <table class="carb-tip-table">
          <thead v-if="csEff">
            <tr><th></th><th>{{ t('prov.quote.compare.msCol') }}</th><th class="carb-cs-col">{{ csName }}</th></tr>
          </thead>
          <tbody>
            <tr v-for="row in eff.byType" :key="row.key">
              <td class="carb-tip-type">
                <v-icon size="12" :icon="iconFor(row.key)" />{{ t(`prov.quote.tabs.${row.key}`) }}
              </td>
              <td class="carb-tip-val">{{ pct(row.efficiency) }}</td>
              <td v-if="csEff" class="carb-tip-val carb-cs-col">{{ csByKey[row.key] != null ? pct(csByKey[row.key]) : '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </v-tooltip>
  </div>
</template>

<script setup>
// Carbon-efficiency gauge — the CO₂ twin of EfficiencyBar. Same utilisation
// ratios (requested vs best-matching capacity) but blended by each resource's
// emissions instead of its cost, so the biggest emitters drive the figure.
// Hidden at a perfect 100% fit (or when no emissions are reported).
import { computed } from 'vue'
import { useI18nStore } from '@ligoj/host'
import { computeEfficiency, TAB_TYPES } from '../quoteFormatters.js'

const props = defineProps({
  /** Quote configuration block (typically the filtered one). */
  config: { type: Object, default: null },
  /** Optional compared-subscription config to contrast the carbon efficiency against. */
  compare: { type: Object, default: null },
  /** Display name of the compared subscription. */
  csName: { type: String, default: '' },
})

const { t } = useI18nStore()

const eff = computed(() => computeEfficiency(props.config, { weight: 'co2' }))
const csEff = computed(() => (props.compare ? computeEfficiency(props.compare, { weight: 'co2' }) : null))
const csByKey = computed(() =>
  csEff.value ? Object.fromEntries(csEff.value.byType.map((r) => [r.key, r.efficiency])) : {},
)

// Show below 100% (there is headroom to report), or whenever comparing.
// `costNoSupport` carries the total CO₂ weight in this mode (see computeEfficiency).
const show = computed(
  () => eff.value.costNoSupport > 0 && (props.compare != null || Math.round(eff.value.overall * 100) < 100),
)

const colorOf = (v) => (v >= 0.9 ? 'success' : v >= 0.7 ? 'info' : v >= 0.5 ? 'warning' : 'error')
const color = computed(() => colorOf(eff.value.overall))

// Higher carbon efficiency is better, so a CS above the MS reads green.
const delta = computed(() => (csEff.value ? csEff.value.overall - eff.value.overall : null))
const deltaClass = computed(() => {
  const d = delta.value
  if (d == null || Math.abs(d) < 0.005) return 'text-medium-emphasis'
  return d > 0 ? 'text-success' : 'text-error'
})

const ICONS = Object.fromEntries(TAB_TYPES.map((x) => [x.key, x.icon]))
const iconFor = (key) => ICONS[key] || 'mdi-help-circle-outline'

const pct = (v) => `${Math.round((v || 0) * 100)}%`
</script>

<style scoped>
.carb-bar {
  width: 150px;
  cursor: help;
}
.carb-bar-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 2px;
}
.carb-bar-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ink-3, rgba(var(--v-theme-on-surface), 0.55));
}
.carb-bar-pct {
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: rgb(var(--v-theme-on-surface));
}
.carb-cmp {
  display: inline-flex;
  align-items: center;
  margin-left: 2px;
}
.carb-cs-bar {
  margin-top: 2px;
}
</style>

<style>
/* Tooltip content — unscoped (teleported outside the component). */
.carb-tip .carb-tip-body {
  max-width: 240px;
}
.carb-tip .carb-tip-explain {
  font-size: 0.82em;
  opacity: 0.85;
  margin-bottom: 6px;
  line-height: 1.35;
}
.carb-tip .carb-tip-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85em;
}
.carb-tip .carb-tip-table th {
  text-align: right;
  font-weight: 600;
  opacity: 0.75;
  padding: 0 0 2px 0.8rem;
}
.carb-tip .carb-cs-col {
  color: rgb(var(--v-theme-primary));
}
.carb-tip .carb-tip-type {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 1px 0;
}
.carb-tip .carb-tip-val {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  padding-left: 0.8rem;
}
</style>
