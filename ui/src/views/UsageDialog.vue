<template>
  <v-dialog :model-value="modelValue" max-width="560" @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon>mdi-clock-outline</v-icon>
        {{ form.id ? t('prov.quote.usage.edit') : t('prov.quote.usage.new') }}
      </v-card-title>

      <v-card-text>
        <v-form ref="formRef" @submit.prevent="save">
          <v-text-field v-model="form.name" :label="t('prov.quote.usage.name')" :rules="REQUIRED_RULES"
            maxlength="50" variant="outlined" density="compact" autofocus />

          <div class="usage-rate d-flex align-center ga-4 my-2">
            <!-- Doughnut of the usage percentage. -->
            <svg class="usage-donut" viewBox="0 0 120 120" role="img" :aria-label="t('prov.quote.usage.rate')">
              <path :d="bgPath" class="usage-donut-bg" fill-rule="evenodd" />
              <path v-if="ratePath" :d="ratePath" class="usage-donut-fill" fill-rule="evenodd" />
              <text x="60" y="60" text-anchor="middle" class="usage-donut-value">{{ Math.round(form.rate) }}%</text>
              <text x="60" y="76" text-anchor="middle" class="usage-donut-label">{{ t('prov.quote.usage.rate') }}</text>
            </svg>

            <div class="flex-grow-1">
              <div class="d-flex align-center ga-1 mb-1">
                <span class="text-caption text-medium-emphasis">{{ t('prov.quote.usage.rate') }}</span>
                <HelpTip :text="t('prov.quote.usage.rateHelp')" />
              </div>
              <v-slider v-model="form.rate" :min="1" :max="100" :step="1" hide-details thumb-label />
              <div class="d-flex flex-wrap ga-1 mt-1">
                <v-chip v-for="tpl in USAGE_RATE_TEMPLATES" :key="tpl.rate" size="x-small"
                  :variant="form.rate === tpl.rate ? 'flat' : 'tonal'"
                  :color="form.rate === tpl.rate ? 'primary' : undefined" @click="form.rate = tpl.rate">
                  {{ t(tpl.key) }}
                </v-chip>
              </div>

              <!-- Equivalent durations — edit any of them to set the rate; they
                   all follow the slider live. -->
              <div class="usage-units d-flex ga-2 mt-3">
                <v-text-field v-model.number="hoursMonth" type="number" min="0" :max="HOURS_PER_MONTH"
                  :label="t('prov.quote.usage.hoursPerMonth')" variant="outlined" density="compact" hide-details
                  @blur="commitHoursMonth" @keyup.enter="commitHoursMonth" />
                <v-text-field v-model.number="daysMonth" type="number" min="0" max="31" step="0.1"
                  :label="t('prov.quote.usage.daysPerMonth')" variant="outlined" density="compact" hide-details
                  @blur="commitDaysMonth" @keyup.enter="commitDaysMonth" />
                <v-text-field v-model.number="daysYear" type="number" min="0" max="365"
                  :label="t('prov.quote.usage.daysPerYear')" variant="outlined" density="compact" hide-details
                  @blur="commitDaysYear" @keyup.enter="commitDaysYear" />
              </div>
            </div>
          </div>

          <v-row density="compact">
            <v-col cols="6">
              <v-text-field v-model.number="form.duration" type="number" min="1"
                :label="t('prov.quote.usage.duration')" suffix="mo" variant="outlined" density="compact" hide-details>
                <template #append-inner>
                  <HelpTip :text="t('prov.quote.usage.durationHelp')" />
                </template>
              </v-text-field>
            </v-col>
            <v-col cols="6">
              <v-text-field v-model.number="form.start" type="number"
                :label="t('prov.quote.usage.start')" suffix="mo" variant="outlined" density="compact" hide-details>
                <template #append-inner>
                  <HelpTip :text="t('prov.quote.usage.startHelp')" />
                </template>
              </v-text-field>
            </v-col>
          </v-row>

          <!-- Convertible / reservation flags (checked = required). -->
          <div class="usage-flags mt-2">
            <div v-for="flag in USAGE_FLAGS" :key="flag" class="usage-flag">
              <v-checkbox v-model="form[flag]" :label="t(`prov.quote.usage.${flag}`)" density="compact" hide-details color="primary" />
              <HelpTip :text="t(`prov.quote.usage.${flag}Help`)" />
            </div>
          </div>
        </v-form>
      </v-card-text>

      <v-card-actions>
        <v-btn v-if="form.id != null" variant="text" color="error" :loading="deleting" @click="remove">
          {{ t('common.delete') }}
        </v-btn>
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</v-btn>
        <v-btn color="primary" variant="elevated" :loading="saving" @click="save">{{ t('common.save') }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
// Create / edit / delete a usage profile (ProvUsage) via ProvUsageResource.
import { ref, reactive, computed, watch } from 'vue'
import { useApi, useI18nStore, APP_BASE } from '@ligoj/host'
import { donutPath, donutFullPath } from '../quoteFormatters.js'
import { USAGE_RATE_TEMPLATES, USAGE_FLAGS, usagePayload } from '../usageCatalog.js'
import HelpTip from './HelpTip.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** Usage object to edit, or null to create a new one. */
  usage: { type: Object, default: null },
  subscriptionId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'saved', 'changed'])

const api = useApi()
const { t } = useI18nStore()

const REQUIRED_RULES = [(v) => (v != null && v !== '') || (t('common.required') || 'Required')]

const blank = () => ({
  id: null, name: '', rate: 100, duration: 1, start: 0,
  convertibleOs: false, convertibleEngine: false, convertibleLocation: false,
  convertibleFamily: false, convertibleType: false, reservation: false,
})
const form = reactive(blank())
const formRef = ref(null)
const saving = ref(false)
const deleting = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const u = props.usage
    Object.assign(form, blank())
    if (u) {
      Object.assign(form, {
        id: u.id ?? null, name: u.name ?? '', rate: u.rate ?? 100,
        duration: u.duration ?? 1, start: u.start ?? 0,
        convertibleOs: !!u.convertibleOs, convertibleEngine: !!u.convertibleEngine,
        convertibleLocation: !!u.convertibleLocation, convertibleFamily: !!u.convertibleFamily,
        convertibleType: !!u.convertibleType, reservation: !!u.reservation,
      })
    }
  },
  { immediate: true },
)

/* ---- Doughnut ---- */
const CX = 60, CY = 60, R = 50, RI = 34
const bgPath = donutFullPath(CX, CY, R, RI)
const ratePath = computed(() => {
  const rate = Math.min(100, Math.max(0, Number(form.rate) || 0))
  if (rate <= 0) return ''
  if (rate >= 100) return bgPath
  const start = -Math.PI / 2
  return donutPath(CX, CY, R, RI, start, start + (rate / 100) * Math.PI * 2)
})

/* ---- Equivalent-duration inputs ----
 * 100% ≙ every hour of a 24×7 month/year. The rate is an integer %, so the
 * unit fields snap to the nearest %; they follow the slider live (watch) and
 * commit back to the rate on blur/enter. */
const HOURS_PER_MONTH = 730 // 24 × 365 / 12
const DAYS_PER_MONTH = 365 / 12
const DAYS_PER_YEAR = 365
const clampRate = (r) => Math.min(100, Math.max(1, Math.round(r)))

const hoursMonth = ref(0)
const daysMonth = ref(0)
const daysYear = ref(0)

watch(
  () => form.rate,
  (r) => {
    const rate = Number(r) || 0
    hoursMonth.value = Math.round((rate / 100) * HOURS_PER_MONTH)
    daysMonth.value = Math.round((rate / 100) * DAYS_PER_MONTH * 10) / 10
    daysYear.value = Math.round((rate / 100) * DAYS_PER_YEAR)
  },
  { immediate: true },
)

function commitHoursMonth() {
  if (hoursMonth.value !== '' && hoursMonth.value != null) form.rate = clampRate((Number(hoursMonth.value) / HOURS_PER_MONTH) * 100)
}
function commitDaysMonth() {
  if (daysMonth.value !== '' && daysMonth.value != null) form.rate = clampRate((Number(daysMonth.value) / DAYS_PER_MONTH) * 100)
}
function commitDaysYear() {
  if (daysYear.value !== '' && daysYear.value != null) form.rate = clampRate((Number(daysYear.value) / DAYS_PER_YEAR) * 100)
}

async function save() {
  const ok = formRef.value ? (await formRef.value.validate()).valid : !!form.name
  if (!ok) return
  saving.value = true
  try {
    const vo = usagePayload(form)
    const url = `${APP_BASE}rest/service/prov/${props.subscriptionId}/usage`
    const created = form.id == null
    const res = created ? await api.post(url, vo) : await api.put(url, vo)
    if (res === null) return // useApi surfaced the error
    emit('saved', vo.name)
    emit('changed')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (form.id == null) return
  deleting.value = true
  try {
    const url = `${APP_BASE}rest/service/prov/${props.subscriptionId}/usage/${form.id}`
    const res = await api.del(url)
    if (res === null) return
    emit('changed')
    emit('update:modelValue', false)
  } finally {
    deleting.value = false
  }
}

defineExpose({
  form, ratePath, save, remove,
  hoursMonth, daysMonth, daysYear, commitHoursMonth, commitDaysMonth, commitDaysYear,
})
</script>

<style scoped>
.usage-donut {
  width: 120px;
  height: 120px;
  flex: none;
}
.usage-donut-bg {
  fill: rgba(var(--v-theme-on-surface), 0.1);
}
.usage-donut-fill {
  fill: rgb(var(--v-theme-primary));
}
.usage-donut-value {
  font-size: 20px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  fill: rgb(var(--v-theme-on-surface));
}
.usage-donut-label {
  font-size: 9px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  fill: rgba(var(--v-theme-on-surface), 0.55);
}
.usage-flags {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 12px;
}
.usage-flag {
  display: flex;
  align-items: center;
  gap: 2px;
}
</style>
