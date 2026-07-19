<template>
  <div v-if="show" class="eff-bar">
    <div class="eff-bar-row">
      <span class="eff-bar-label">{{ t('prov.quote.efficiency.label') }}</span>
      <span class="eff-bar-pct">{{ pct(eff.overall) }}</span>
    </div>
    <v-progress-linear
      :model-value="eff.overall * 100"
      :color="color"
      height="6"
      rounded
      bg-opacity="0.15"
    />

    <!-- Lazy tooltip: explanation + per-type efficiency. -->
    <v-tooltip activator="parent" location="bottom" open-delay="150" content-class="eff-tip">
      <div class="eff-tip-body">
        <div class="eff-tip-explain">{{ t('prov.quote.efficiency.explain') }}</div>
        <table class="eff-tip-table">
          <tbody>
            <tr v-for="row in eff.byType" :key="row.key">
              <td class="eff-tip-type">
                <v-icon size="12" :icon="iconFor(row.key)" />{{ t(`prov.quote.tabs.${row.key}`) }}
              </td>
              <td class="eff-tip-val">{{ pct(row.efficiency) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </v-tooltip>
  </div>
</template>

<script setup>
// Provisioning efficiency gauge (successor to the legacy liquid-fill gauge):
// how much of the paid-for capacity the requested resources actually use.
// Hidden at a perfect 100% fit.
import { computed } from 'vue'
import { useI18nStore } from '@ligoj/host'
import { computeEfficiency, TAB_TYPES } from '../quoteFormatters.js'

const props = defineProps({
  /** Quote configuration block (typically the filtered one). */
  config: { type: Object, default: null },
})

const { t } = useI18nStore()

const eff = computed(() => computeEfficiency(props.config))

// Show only when there is cost and the fit is below 100% (rounded).
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
.eff-bar {
  width: 150px;
  cursor: help;
}
.eff-bar-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 2px;
}
.eff-bar-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--ink-3, rgba(var(--v-theme-on-surface), 0.55));
}
.eff-bar-pct {
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: rgb(var(--v-theme-on-surface));
}
</style>

<style>
/* Tooltip content — unscoped (teleported outside the component). */
.eff-tip .eff-tip-body {
  max-width: 240px;
}
.eff-tip .eff-tip-explain {
  font-size: 0.82em;
  opacity: 0.85;
  margin-bottom: 6px;
  line-height: 1.35;
}
.eff-tip .eff-tip-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85em;
}
.eff-tip .eff-tip-type {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 1px 0;
}
.eff-tip .eff-tip-val {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  padding-left: 0.8rem;
}
</style>
