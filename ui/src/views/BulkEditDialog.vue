<template>
  <v-dialog :model-value="modelValue" max-width="480" @update:model-value="close">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon color="primary">mdi-pencil-box-multiple-outline</v-icon>
        {{ t('prov.quote.bulk.title', { n: ids.length }) }}
        <v-spacer />
        <v-btn icon size="small" variant="text" @click="close"><v-icon>mdi-close</v-icon><v-tooltip activator="parent" location="bottom">{{ t('common.close') }}</v-tooltip></v-btn>
      </v-card-title>

      <v-card-text>
        <p class="text-body-2 text-medium-emphasis mb-4">{{ t('prov.quote.bulk.note') }}</p>

        <!-- Usage — same rendering as UsageField: name + rate/duration summary. -->
        <LigojAutocomplete v-model="usage" :items="usageItems" :label="t('prov.quote.fields.usage')"
          variant="outlined" density="compact" class="mb-3" hide-details>
          <template #item="{ props: itemProps, item }">
            <v-list-item v-bind="itemProps" :class="{ 'bulk-sentinel': !(item.raw || item).entity }">
              <template v-if="usageSummary((item.raw || item).entity)" #subtitle>
                {{ usageSummary((item.raw || item).entity) }}
              </template>
            </v-list-item>
          </template>
          <template #selection="{ item }">
            <span class="bulk-sel" :class="{ 'bulk-sentinel': !(item.raw || item).entity }">
              {{ (item.raw || item).title }}
              <span v-if="usageSummary((item.raw || item).entity)" class="bulk-sel-sub">{{ usageSummary((item.raw || item).entity) }}</span>
            </span>
          </template>
        </LigojAutocomplete>

        <!-- Budget — same rendering as BudgetField: name + available cash. -->
        <LigojAutocomplete v-model="budget" :items="budgetItems" :label="t('prov.quote.fields.budget')"
          variant="outlined" density="compact" class="mb-3" hide-details>
          <template #item="{ props: itemProps, item }">
            <v-list-item v-bind="itemProps" :class="{ 'bulk-sentinel': !(item.raw || item).entity }">
              <template v-if="budgetSummary((item.raw || item).entity, currency)" #subtitle>
                {{ budgetSummary((item.raw || item).entity, currency) }}
              </template>
            </v-list-item>
          </template>
          <template #selection="{ item }">
            <span class="bulk-sel" :class="{ 'bulk-sentinel': !(item.raw || item).entity }">
              {{ (item.raw || item).title }}
              <span v-if="budgetSummary((item.raw || item).entity, currency)" class="bulk-sel-sub">{{ budgetSummary((item.raw || item).entity, currency) }}</span>
            </span>
          </template>
        </LigojAutocomplete>

        <!-- Optimizer — same rendering as OptimizerField: mode icon + summary. -->
        <LigojAutocomplete v-model="optimizer" :items="optimizerItems" :label="t('prov.quote.fields.optimizer')"
          variant="outlined" density="compact" class="mb-3" hide-details>
          <template #item="{ props: itemProps, item }">
            <v-list-item v-bind="itemProps" :class="{ 'bulk-sentinel': !(item.raw || item).entity }">
              <template v-if="(item.raw || item).entity" #prepend>
                <v-icon size="16">{{ optimizerModeIcon((item.raw || item).entity.mode) }}</v-icon>
              </template>
              <template v-if="optimizerSummary((item.raw || item).entity, t)" #subtitle>
                {{ optimizerSummary((item.raw || item).entity, t) }}
              </template>
            </v-list-item>
          </template>
          <template #selection="{ item }">
            <span class="bulk-sel" :class="{ 'bulk-sentinel': !(item.raw || item).entity }">
              <v-icon v-if="(item.raw || item).entity" size="14" class="mr-1">{{ optimizerModeIcon((item.raw || item).entity.mode) }}</v-icon>
              {{ (item.raw || item).title }}
            </span>
          </template>
        </LigojAutocomplete>

        <!-- Location — same rendering as LocationField: flag + localized name. -->
        <LigojAutocomplete v-model="location" :items="locationItems" :label="t('prov.quote.cols.location')"
          :custom-filter="locationFilter" variant="outlined" density="compact" hide-details>
          <template #item="{ props: itemProps, item }">
            <v-list-item v-bind="itemProps" :class="{ 'bulk-sentinel': !(item.raw || item).entity }">
              <template v-if="(item.raw || item).entity" #title>
                <LocationLabel :location="(item.raw || item).entity" show-code :tooltip="false" />
              </template>
            </v-list-item>
          </template>
          <template #selection="{ item }">
            <LocationLabel v-if="(item.raw || item).entity" :location="(item.raw || item).entity" :tooltip="false" />
            <span v-else class="bulk-sel bulk-sentinel">{{ (item.raw || item).title }}</span>
          </template>
        </LigojAutocomplete>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="close">{{ t('common.cancel') }}</v-btn>
        <v-btn color="primary" variant="elevated" :disabled="!dirty" :loading="saving" @click="apply">
          {{ t('prov.quote.bulk.apply') }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
// Bulk edit (feature 12): apply usage / budget / optimizer / location to every
// selected compute resource in ONE server-side transaction (PUT …/bulk/{type}),
// with a single cost recompute. Each field is tri-state: keep (default), clear
// (back to the quote inheritance), or a named value — rendered exactly like the
// per-resource fields (usage rate, budget cash, optimizer mode, location flag).
import { ref, computed, watch } from 'vue'
import { useApi, useI18nStore, LigojAutocomplete } from '@ligoj/host'
import LocationLabel from './LocationLabel.vue'
import { usageSummary } from '../usageCatalog.js'
import { budgetSummary } from '../budgetCatalog.js'
import { optimizerSummary, optimizerModeIcon } from '../optimizerCatalog.js'
import { locationMatches } from '../locationCatalog.js'

const KEEP = '__keep__'
const CLEAR = '__clear__'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** The compute tab key (instance / database / container / function). */
  type: { type: String, default: 'instance' },
  /** Identifiers of the selected resources. */
  ids: { type: Array, default: () => [] },
  config: { type: Object, default: null },
  subscriptionId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const i18n = useI18nStore()
const t = i18n.t
const api = useApi()

const currency = computed(() => props.config?.currency || { unit: '$', rate: 1 })

const usage = ref(KEEP)
const budget = ref(KEEP)
const optimizer = ref(KEEP)
const location = ref(KEEP)
const saving = ref(false)

/** keep + clear + the quote's named entries, each carrying its source entity. */
function itemsOf(list) {
  return [
    { title: t('prov.quote.bulk.keep'), value: KEEP },
    { title: t('prov.quote.bulk.clear'), value: CLEAR },
    ...(list || []).filter((x) => x?.name).map((x) => ({ title: x.name, value: x.name, entity: x })),
  ]
}
const usageItems = computed(() => itemsOf(props.config?.usages))
const budgetItems = computed(() => itemsOf(props.config?.budgets))
const optimizerItems = computed(() => itemsOf(props.config?.optimizers))
const locationItems = computed(() => itemsOf(props.config?.locations))

/** Location search across code / country / continent — sentinels match by title. */
function locationFilter(_value, query, item) {
  const wrapper = item?.raw || item
  if (!wrapper?.entity) return String(wrapper?.title || '').toLowerCase().includes(String(query || '').toLowerCase())
  return locationMatches(wrapper.entity, query, i18n.locale)
}

const dirty = computed(() =>
  [usage.value, budget.value, optimizer.value, location.value].some((v) => v !== KEEP) && props.ids.length > 0)

/** Tri-state → API convention: keep = absent, clear = '', value = name. */
const patchOf = (v) => (v === KEEP ? undefined : v === CLEAR ? '' : v)

async function apply() {
  saving.value = true
  try {
    await api.put(`rest/service/prov/${props.subscriptionId}/bulk/${props.type}`, {
      ids: props.ids,
      usage: patchOf(usage.value),
      budget: patchOf(budget.value),
      optimizer: patchOf(optimizer.value),
      location: patchOf(location.value),
    })
    emit('saved')
    close()
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (open) => {
  if (open) {
    usage.value = KEEP
    budget.value = KEEP
    optimizer.value = KEEP
    location.value = KEEP
  }
})

defineExpose({ usage, budget, optimizer, location, dirty, apply, locationFilter })
</script>

<style scoped>
.bulk-sel {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}
.bulk-sel-sub {
  font-size: 0.78em;
  color: rgba(var(--v-theme-on-surface), 0.6);
  font-variant-numeric: tabular-nums;
}
/* The keep / clear pseudo-entries read as meta options, not values. */
.bulk-sentinel {
  font-style: italic;
  opacity: 0.75;
}
</style>
