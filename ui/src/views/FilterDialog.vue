<template>
  <v-dialog :model-value="modelValue" max-width="760" @update:model-value="close">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon color="primary">mdi-filter-variant</v-icon>
        {{ t('prov.quote.filter.title') }}
        <v-spacer />
        <v-btn icon size="small" variant="text" :title="t('common.close')" @click="close"><v-icon>mdi-close</v-icon></v-btn>
      </v-card-title>

      <v-card-text>
        <div class="d-flex align-center ga-3 mb-4 flex-wrap">
          <p class="text-body-2 text-medium-emphasis mb-0 flex-grow-1">{{ t('prov.quote.filter.note') }}</p>
          <!-- Combination mode: every filter (AND) or at least one (OR). -->
          <v-btn-toggle v-model="localMode" mandatory density="compact" variant="outlined" divided>
            <v-btn size="small" value="AND">{{ t('prov.quote.filter.and') }}</v-btn>
            <v-btn size="small" value="OR">{{ t('prov.quote.filter.or') }}</v-btn>
          </v-btn-toggle>
        </div>

        <div v-for="(f, i) in localFilters" :key="f.id" class="flt-row">
          <v-select :model-value="f.field" :items="fieldItems" item-title="label" item-value="key"
            :label="t('prov.quote.filter.field')" variant="outlined" density="compact" hide-details class="flt-field"
            @update:model-value="(v) => onFieldChange(f, v)" />

          <!-- Numeric: operator + value with the metric unit. -->
          <template v-if="kindOf(f.field) === 'number'">
            <v-select v-model="f.op" :items="NUMBER_OPS" variant="outlined" density="compact" hide-details class="flt-op" />
            <v-text-field v-model="f.value" type="number" :suffix="f.field === 'co2' ? 'g' : currency.unit"
              :label="t('prov.quote.filter.value')" variant="outlined" density="compact" hide-details class="flt-value" />
          </template>

          <!-- Location: the shared flag + localized-name picker. -->
          <LocationField v-else-if="f.field === 'location'" v-model="f.value" :items="config?.locations || []"
            :label="t('prov.quote.cols.location')" density="compact" hide-details class="flt-value" />

          <!-- Profiles: shared summaries (usage rate, budget cash, optimizer mode). -->
          <LigojAutocomplete v-else-if="kindOf(f.field) === 'profile'" v-model="f.value" :items="profileItems(f.field)"
            :label="labelOf(f.field)" variant="outlined" density="compact" hide-details class="flt-value">
            <template #item="{ props: itemProps, item }">
              <v-list-item v-bind="itemProps">
                <template v-if="f.field === 'optimizer' && (item.raw || item).entity" #prepend>
                  <v-icon size="16">{{ optimizerModeIcon((item.raw || item).entity.mode) }}</v-icon>
                </template>
                <template v-if="profileSummary(f.field, (item.raw || item).entity)" #subtitle>
                  {{ profileSummary(f.field, (item.raw || item).entity) }}
                </template>
              </v-list-item>
            </template>
          </LigojAutocomplete>

          <!-- OS / engine: distinct values present in the quote, with their icon. -->
          <v-select v-else-if="kindOf(f.field) === 'enum'" v-model="f.value" :items="f.field === 'os' ? osItems : engineItems"
            :label="labelOf(f.field)" variant="outlined" density="compact" hide-details class="flt-value">
            <template #item="{ props: itemProps, item }">
              <v-list-item v-bind="itemProps">
                <template #prepend>
                  <OsIcon v-if="f.field === 'os'" :os="item.raw ?? item" />
                  <EngineIcon v-else :engine="item.raw ?? item" />
                </template>
              </v-list-item>
            </template>
          </v-select>

          <!-- Tag: key + optional value. -->
          <template v-else-if="f.field === 'tag'">
            <LigojAutocomplete v-model="f.value" :items="tagKeyItems" :label="t('prov.quote.filter.tagKey')"
              variant="outlined" density="compact" hide-details class="flt-value" />
            <LigojAutocomplete v-model="f.value2" :items="tagValueItems(f.value)" :label="t('prov.quote.filter.tagValue')"
              variant="outlined" density="compact" hide-details clearable class="flt-value" />
          </template>

          <!-- Free text (name / type / term / any field) — regex supported. -->
          <v-text-field v-else v-model="f.value" :label="labelOf(f.field)"
            :placeholder="t('prov.quote.filter.textHint')" variant="outlined" density="compact" hide-details
            class="flt-value" />

          <v-btn icon size="x-small" variant="text" :title="t('common.delete')" @click="localFilters.splice(i, 1)">
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </div>

        <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" class="mt-1" @click="addFilter">
          {{ t('prov.quote.filter.add') }}
        </v-btn>
      </v-card-text>

      <v-card-actions>
        <v-btn variant="text" :disabled="!localFilters.length" @click="clearAll">{{ t('prov.quote.filter.clear') }}</v-btn>
        <v-spacer />
        <v-btn variant="text" @click="close">{{ t('common.cancel') }}</v-btn>
        <v-btn color="primary" variant="elevated" @click="apply">{{ t('prov.quote.filter.apply') }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
// Advanced search filters (feature 13 phase 2) — the dedicated manager dialog.
// Each filter row picks a dimension and edits its value with the SAME rendering
// used elsewhere: location → flag picker, usage/budget/optimizer → summaries,
// OS/engine → icons, cost/CO₂ → numeric compare (>/</=). Text values are
// regex-aware. Filters combine with AND / OR; the evaluation engine lives in
// searchFilters.js (pure, tested) — this dialog only edits the state.
import { ref, computed, watch } from 'vue'
import { useI18nStore, LigojAutocomplete } from '@ligoj/host'
import LocationField from './LocationField.vue'
import OsIcon from './OsIcon.vue'
import EngineIcon from './EngineIcon.vue'
import { FILTER_FIELDS, NUMBER_OPS } from '../searchFilters.js'
import { usageSummary } from '../usageCatalog.js'
import { budgetSummary } from '../budgetCatalog.js'
import { optimizerSummary, optimizerModeIcon } from '../optimizerCatalog.js'
import { tagKeys } from '../tagAllocation.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** Applied filter list (`{ id, field, op, value, value2 }`). */
  filters: { type: Array, default: () => [] },
  /** Combination mode: 'AND' | 'OR'. */
  mode: { type: String, default: 'AND' },
  config: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'update:filters', 'update:mode'])

const { t } = useI18nStore()

let seq = 0
const localFilters = ref([])
const localMode = ref('AND')

const currency = computed(() => props.config?.currency || { unit: '$', rate: 1 })

const KIND_BY_FIELD = Object.fromEntries(FILTER_FIELDS.map((f) => [f.key, f.kind]))
const kindOf = (field) => KIND_BY_FIELD[field] || 'text'

function labelOf(field) {
  switch (field) {
    case 'text': return t('prov.quote.filter.anyField')
    case 'name': return t('prov.quote.cols.name')
    case 'type': return t('prov.quote.cols.type')
    case 'term': return t('prov.quote.cols.term')
    case 'os': return t('prov.quote.cols.os')
    case 'engine': return t('prov.quote.cols.engine')
    case 'location': return t('prov.quote.cols.location')
    case 'usage': return t('prov.quote.fields.usage')
    case 'budget': return t('prov.quote.fields.budget')
    case 'optimizer': return t('prov.quote.fields.optimizer')
    case 'tag': return t('prov.quote.filter.tag')
    case 'cost': return t('prov.quote.cols.cost')
    default: return 'CO₂'
  }
}
const fieldItems = computed(() => FILTER_FIELDS.map((f) => ({ key: f.key, label: labelOf(f.key) })))

/* ---- Value editors data ---- */
const profileItems = (field) => {
  const list = field === 'usage' ? props.config?.usages : field === 'budget' ? props.config?.budgets : props.config?.optimizers
  return (list || []).filter((x) => x?.name).map((x) => ({ title: x.name, value: x.name, entity: x }))
}
function profileSummary(field, entity) {
  if (!entity) return ''
  if (field === 'usage') return usageSummary(entity)
  if (field === 'budget') return budgetSummary(entity, currency.value)
  return optimizerSummary(entity, t)
}

/** Distinct values present in the quote, so the enum filters stay relevant. */
const distinct = (lists, getter) => {
  const set = new Set()
  for (const list of lists) for (const r of list || []) {
    const v = getter(r)
    if (v) set.add(String(v).toUpperCase())
  }
  return [...set].sort()
}
const osItems = computed(() => distinct([props.config?.instances, props.config?.containers], (r) => r.os || r.price?.os))
const engineItems = computed(() => distinct([props.config?.databases], (r) => r.engine || r.price?.engine))

const tagKeyItems = computed(() => tagKeys(props.config))
function tagValueItems(key) {
  const set = new Set()
  for (const byId of Object.values(props.config?.tags || {})) {
    for (const arr of Object.values(byId || {})) {
      for (const tg of arr || []) {
        if (tg?.name === key && tg.value) set.add(tg.value)
      }
    }
  }
  return [...set].sort()
}

/* ---- Edition ---- */
function addFilter() {
  localFilters.value.push({ id: ++seq, field: 'text', op: '>', value: '', value2: '' })
}
function onFieldChange(f, field) {
  f.field = field
  f.value = ''
  f.value2 = ''
  f.op = '>'
}
function clearAll() {
  localFilters.value = []
  apply()
}
function apply() {
  emit('update:filters', localFilters.value.map((f) => ({ ...f })))
  emit('update:mode', localMode.value)
  close()
}
function close() {
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (open) => {
  if (open) {
    localMode.value = props.mode
    localFilters.value = props.filters.map((f) => ({ ...f, id: f.id ?? ++seq }))
    if (!localFilters.value.length) addFilter()
  }
})

defineExpose({ localFilters, localMode, addFilter, apply, clearAll, kindOf, onFieldChange,
  osItems, engineItems, tagKeyItems, tagValueItems })
</script>

<style scoped>
.flt-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.flt-field {
  flex: 0 0 190px;
}
.flt-op {
  flex: 0 0 84px;
}
.flt-value {
  flex: 1 1 200px;
  min-width: 160px;
}
</style>
