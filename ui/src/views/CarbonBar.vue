<template>
  <div v-if="show" class="carb-bar">
    <div class="carb-bar-row">
      <span class="carb-bar-label">{{ t('prov.quote.carbon.label') }}</span>
      <span class="carb-bar-pct">{{ pct(eff.overall) }}</span>
    </div>
    <v-progress-linear
      :model-value="eff.overall * 100"
      :color="color"
      height="6"
      rounded
      bg-opacity="0.15"
    />

    <!-- Lazy tooltip: explanation + per-type carbon efficiency. -->
    <v-tooltip activator="parent" location="bottom" open-delay="150" content-class="carb-tip">
      <div class="carb-tip-body">
        <div class="carb-tip-explain">{{ t('prov.quote.carbon.explain') }}</div>
        <table class="carb-tip-table">
          <tbody>
            <tr v-for="row in eff.byType" :key="row.key">
              <td class="carb-tip-type">
                <v-icon size="12" :icon="iconFor(row.key)" />{{ t(`prov.quote.tabs.${row.key}`) }}
              </td>
              <td class="carb-tip-val">{{ pct(row.efficiency) }}</td>
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
})

const { t } = useI18nStore()

const eff = computed(() => computeEfficiency(props.config, { weight: 'co2' }))

// Show only when there are emissions and the fit is below 100% (rounded).
// `costNoSupport` carries the total CO₂ weight in this mode (see computeEfficiency).
const show = computed(
  () => eff.value.costNoSupport > 0 && Math.round(eff.value.overall * 100) < 100,
)

const color = computed(() => {
  const v = eff.value.overall
  if (v >= 0.9) return 'success'
  if (v >= 0.7) return 'info'
  if (v >= 0.5) return 'warning'
  return 'error'
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
