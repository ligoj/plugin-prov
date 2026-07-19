<template>
  <v-dialog :model-value="modelValue" max-width="560" @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon>mdi-chart-bell-curve-cumulative</v-icon>
        {{ t('prov.quote.workload.title') }}
      </v-card-title>

      <v-card-text>
        <!-- Baseline: the weighted-average CPU the profile represents. -->
        <div class="d-flex align-center ga-3 mb-2">
          <span class="wl-baseline-label">{{ t('prov.quote.workload.baseline') }}</span>
          <v-slider v-model="baseline" :min="0" :max="100" :step="1" hide-details thumb-label class="flex-grow-1" />
          <span class="wl-baseline-val">{{ Math.round(baseline) }}%</span>
        </div>

        <!-- Preview: each period's width ∝ its duration, height ∝ its CPU. -->
        <svg class="wl-preview mb-3" viewBox="0 0 100 40" preserveAspectRatio="none" role="img" :aria-label="t('prov.quote.workload.title')">
          <line :x1="0" :y1="baselineY" :x2="100" :y2="baselineY" class="wl-baseline-line" />
          <rect v-for="(seg, i) in preview" :key="i" :x="seg.x" :y="40 - seg.h" :width="Math.max(0, seg.w - 0.4)" :height="seg.h" class="wl-seg" />
        </svg>

        <!-- Periods -->
        <div class="wl-periods">
          <div v-for="(p, i) in periods" :key="i" class="wl-period">
            <v-text-field v-model.number="p.duration" type="number" min="1" max="100"
              :label="t('prov.quote.workload.duration')" suffix="%" variant="outlined" density="compact" hide-details />
            <span class="wl-at">@</span>
            <v-text-field v-model.number="p.cpu" type="number" min="0" max="100"
              :label="t('prov.quote.workload.cpu')" suffix="%" variant="outlined" density="compact" hide-details />
            <v-btn icon size="small" variant="text" :title="t('prov.quote.workload.removePeriod')" @click="removePeriod(i)">
              <v-icon>mdi-close</v-icon>
            </v-btn>
          </div>
        </div>

        <div class="d-flex align-center ga-2 mt-2 flex-wrap">
          <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" @click="addPeriod">
            {{ t('prov.quote.workload.addPeriod') }}
          </v-btn>
          <v-spacer />
          <span class="text-caption" :class="{ 'text-warning': durationTotal > 100 }">
            {{ t('prov.quote.workload.total') }}: {{ durationTotal }}%
          </span>
        </div>
        <div v-if="durationTotal > 100" class="text-caption text-warning mt-1">
          {{ t('prov.quote.workload.totalWarn') }}
        </div>
      </v-card-text>

      <v-card-actions>
        <v-btn variant="text" color="error" @click="clearWorkload">{{ t('prov.quote.workload.clear') }}</v-btn>
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</v-btn>
        <v-btn color="primary" variant="elevated" @click="save">{{ t('common.save') }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
// Visual editor for a resource's CPU workload profile — add/remove/update the
// `duration@cpu` periods and the baseline. Reads/writes the `Workload#from`
// string (`$baseline(,$duration@$cpu)*`) via the shared parse/serialize.
import { ref, computed, watch } from 'vue'
import { useI18nStore } from '@ligoj/host'
import { parseWorkload, serializeWorkload } from '../quoteFormatters.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** The current workload string (empty = none). */
  workload: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue', 'save'])

const { t } = useI18nStore()

const baseline = ref(50)
const periods = ref([])

// (Re)load from the incoming string each time the dialog opens.
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const w = parseWorkload(props.workload)
    baseline.value = w.baseline ?? 50
    periods.value = w.periods.map((p) => ({ duration: p.duration, cpu: p.cpu }))
  },
  { immediate: true },
)

const durationTotal = computed(() =>
  periods.value.reduce((s, p) => s + (Number(p.duration) || 0), 0),
)

const baselineY = computed(() => 40 * (1 - Math.min(100, Math.max(0, baseline.value)) / 100))

const preview = computed(() => {
  const total = durationTotal.value || 100
  let x = 0
  return periods.value.map((p) => {
    const w = ((Number(p.duration) || 0) / total) * 100
    const cpu = Math.min(100, Math.max(0, Number(p.cpu) || 0))
    const seg = { x, w, h: (cpu / 100) * 40 }
    x += w
    return seg
  })
})

function addPeriod() {
  periods.value.push({ duration: 10, cpu: 50 })
}
function removePeriod(i) {
  periods.value.splice(i, 1)
}
function save() {
  emit('save', serializeWorkload({ baseline: baseline.value, periods: periods.value }))
  emit('update:modelValue', false)
}
function clearWorkload() {
  emit('save', '')
  emit('update:modelValue', false)
}

defineExpose({ baseline, periods, addPeriod, removePeriod, save, clearWorkload, durationTotal })
</script>

<style scoped>
.wl-baseline-label {
  min-width: 5.5rem;
  font-size: 0.85rem;
  color: rgba(var(--v-theme-on-surface), 0.7);
}
.wl-baseline-val {
  min-width: 2.6rem;
  text-align: right;
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}
.wl-preview {
  width: 100%;
  height: 54px;
  background: rgba(var(--v-theme-on-surface), 0.05);
  border-radius: 6px;
}
.wl-seg {
  fill: rgb(var(--v-theme-primary));
  opacity: 0.75;
}
.wl-baseline-line {
  stroke: rgb(var(--v-theme-error));
  stroke-width: 1;
  stroke-dasharray: 2 2;
  vector-effect: non-scaling-stroke;
}
.wl-periods {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.wl-period {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wl-at {
  font-weight: 700;
  opacity: 0.6;
}
</style>
