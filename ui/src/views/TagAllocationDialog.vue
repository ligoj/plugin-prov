<template>
  <v-dialog :model-value="modelValue" max-width="760" @update:model-value="close">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon color="primary">mdi-tag-multiple-outline</v-icon>
        {{ t('prov.quote.tagAlloc.title') }}
        <v-spacer />
        <v-btn icon size="small" variant="text" @click="close"><v-icon>mdi-close</v-icon><v-tooltip activator="parent" location="bottom">{{ t('common.close') }}</v-tooltip></v-btn>
      </v-card-title>

      <v-card-text>
        <v-alert v-if="!keys.length" type="info" variant="tonal" density="compact">
          {{ t('prov.quote.tagAlloc.empty') }}
        </v-alert>

        <template v-else>
          <!-- Controls: tag key + metric + coverage. -->
          <div class="ta-controls">
            <LigojAutocomplete
              v-model="selectedKey"
              :items="keys"
              :label="t('prov.quote.tagAlloc.key')"
              variant="outlined"
              density="compact"
              hide-details
              class="ta-key"
            />
            <LjSegmented v-model="metric" :options="[
              { value: 'cost', icon: 'mdi-currency-usd', label: t('prov.quote.viewMode.cost') },
              { value: 'co2', icon: 'mdi-leaf', label: t('prov.quote.viewMode.co2') },
            ]" />
            <v-spacer />
            <div class="ta-coverage">
              <span class="ta-coverage-label">{{ t('prov.quote.tagAlloc.coverage') }}</span>
              <span class="ta-coverage-pct" :class="`text-${coverageColor}`">{{ pct(allocation.coverage) }}</span>
              <v-tooltip activator="parent" location="bottom" max-width="280">{{ t('prov.quote.tagAlloc.coverageHelp') }}</v-tooltip>
            </div>
          </div>

          <!-- Horizontal bar breakdown. -->
          <div class="ta-bars">
            <div v-for="row in rows" :key="row.value ?? '__untagged__'" class="ta-row" :class="{ 'ta-row-untagged': row.untagged }">
              <span class="ta-label">
                <span class="ta-dot" :style="{ backgroundColor: row.color }" />
                {{ row.untagged ? t('prov.quote.tagAlloc.untagged') : (row.value || t('prov.quote.tagAlloc.noValue')) }}
              </span>
              <div class="ta-track">
                <div class="ta-bar" :style="{ width: barWidth(row.amount), backgroundColor: row.color }" />
              </div>
              <span class="ta-amount">{{ fmt(row.amount) }}</span>
              <span class="ta-pct">{{ pct(row.share) }}</span>
              <span class="ta-count">{{ t('prov.quote.tagAlloc.nRes', { n: row.count }) }}</span>
            </div>
          </div>

          <div class="ta-total">
            <span>{{ t('prov.quote.compare.total') }}</span>
            <strong>{{ fmt(allocation.total) }}</strong>
            <span class="ta-total-count">· {{ t('prov.quote.tagAlloc.nRes', { n: allocation.count }) }}</span>
          </div>
        </template>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<script setup>
// Tag-based cost allocation (feature 04). Purely client-side: breaks the quote's
// cost / CO₂ down by the values of a chosen tag key, with an untagged bucket and
// a coverage indicator. Aggregation lives in tagAllocation.js (unit-tested).
import { ref, computed, watch } from 'vue'
import { useI18nStore, LigojAutocomplete, LjSegmented } from '@ligoj/host'
import { formatCost, formatCo2 } from '../quoteFormatters.js'
import { tagKeys, tagAllocation } from '../tagAllocation.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** Quote configuration block. */
  config: { type: Object, default: null },
  currency: { type: Object, default: null },
  /** Initial metric, mirroring the quote's cost/CO₂ view. */
  viewMode: { type: String, default: 'cost' },
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18nStore()

const BAR_COLORS = [
  'rgb(var(--v-theme-primary))', 'rgb(var(--v-theme-info))', 'rgb(var(--v-theme-success))',
  'rgb(var(--v-theme-warning))', 'rgb(var(--v-theme-error))', 'rgb(var(--v-theme-secondary))',
]
const UNTAGGED_COLOR = 'rgba(var(--v-theme-on-surface), 0.3)'

const keys = computed(() => tagKeys(props.config))
const selectedKey = ref(null)
const metric = ref('cost')

// Pick a sensible default key + metric each time the dialog opens.
watch(() => props.modelValue, (open) => {
  if (!open) return
  metric.value = props.viewMode === 'co2' ? 'co2' : 'cost'
  if (selectedKey.value == null || !keys.value.includes(selectedKey.value)) {
    selectedKey.value = keys.value[0] ?? null
  }
})

const allocation = computed(() => tagAllocation(props.config, selectedKey.value, metric.value))

const rows = computed(() => {
  const a = allocation.value
  const out = a.buckets.map((b, i) => ({ ...b, color: BAR_COLORS[i % BAR_COLORS.length], untagged: false }))
  if (a.untagged.amount > 0) {
    out.push({ value: null, amount: a.untagged.amount, count: a.untagged.count, share: a.untagged.share, color: UNTAGGED_COLOR, untagged: true })
  }
  return out
})
const maxAmount = computed(() => Math.max(0, ...rows.value.map((r) => r.amount)))

const currency = computed(() => props.currency || { unit: '$', rate: 1 })
const fmt = (v) => (metric.value === 'co2' ? formatCo2(v) : formatCost(v, currency.value))
const pct = (v) => `${Math.round((v || 0) * 100)}%`
const barWidth = (v) => `${maxAmount.value > 0 ? (v / maxAmount.value) * 100 : 0}%`

const coverageColor = computed(() => {
  const c = allocation.value.coverage
  return c >= 0.9 ? 'success' : c >= 0.6 ? 'warning' : 'error'
})

function close() {
  emit('update:modelValue', false)
}

defineExpose({ keys, selectedKey, metric, allocation, rows })
</script>

<style scoped>
.ta-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.ta-key {
  min-width: 200px;
  max-width: 260px;
}
.ta-coverage {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  cursor: help;
}
.ta-coverage-label {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(var(--v-theme-on-surface), 0.55);
}
.ta-coverage-pct {
  font-size: 15px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.ta-bars {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ta-row {
  display: grid;
  grid-template-columns: 150px 1fr 90px 48px 92px;
  align-items: center;
  column-gap: 10px;
  font-size: 0.88rem;
}
.ta-row-untagged {
  opacity: 0.75;
  font-style: italic;
}
.ta-label {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
}
.ta-dot {
  width: 0.7rem;
  height: 0.7rem;
  border-radius: 3px;
  flex: none;
}
.ta-track {
  height: 12px;
  border-radius: 6px;
  background: rgba(var(--v-theme-on-surface), 0.06);
  overflow: hidden;
}
.ta-bar {
  height: 100%;
  border-radius: 6px;
  min-width: 2px;
  transition: width 200ms ease;
}
.ta-amount {
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}
.ta-pct {
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: rgba(var(--v-theme-on-surface), 0.6);
}
.ta-count {
  text-align: right;
  font-size: 0.78em;
  color: rgba(var(--v-theme-on-surface), 0.6);
}
.ta-total {
  display: flex;
  align-items: baseline;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 14px;
  padding-top: 10px;
  border-top: 1px solid rgba(var(--v-theme-on-surface), 0.12);
  font-variant-numeric: tabular-nums;
}
.ta-total-count {
  font-size: 0.82em;
  color: rgba(var(--v-theme-on-surface), 0.6);
}
</style>
