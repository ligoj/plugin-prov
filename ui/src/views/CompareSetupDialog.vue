<template>
  <v-dialog :model-value="modelValue" max-width="820" @update:model-value="close">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon color="primary">mdi-scale-balance</v-icon>
        {{ t('prov.quote.compare.title') }}
        <v-spacer />
        <v-btn icon size="small" variant="text" :title="t('common.close')" @click="close"><v-icon>mdi-close</v-icon></v-btn>
      </v-card-title>

      <v-card-text>
        <p class="text-body-2 text-medium-emphasis mb-4">{{ t('prov.quote.compare.setupNote') }}</p>

        <!-- Add a compared subscription. -->
        <div class="d-flex align-center ga-2 mb-4">
          <LigojAutocomplete
            v-model="toAdd"
            :items="addable"
            item-title="label"
            item-value="id"
            :label="t('prov.quote.compare.addLabel')"
            variant="outlined"
            density="compact"
            hide-details
            :loading="loadingSubs"
            :no-data-text="t('prov.quote.compare.noAddable')"
            class="flex-grow-1"
          >
            <template #item="{ props: itemProps, item }">
              <v-list-item v-bind="itemProps" :subtitle="subtitleOf(item.raw || item)">
                <template #prepend><NodeIcon :node="(item.raw || item).toolId" /></template>
              </v-list-item>
            </template>
          </LigojAutocomplete>
          <v-btn color="primary" variant="elevated" :disabled="!toAdd" :loading="adding" prepend-icon="mdi-plus" @click="add">
            {{ t('prov.quote.compare.add') }}
          </v-btn>
        </div>

        <v-alert v-if="cloneWarn" type="warning" variant="tonal" density="compact" class="mb-4">
          {{ t('prov.quote.compare.resetWarn') }}
        </v-alert>

        <!-- Current compared subscriptions. -->
        <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-2" />
        <v-alert v-else-if="!compared.length" type="info" variant="tonal" density="compact">
          {{ t('prov.quote.compare.noneYet') }}
        </v-alert>
        <table v-else class="cmp-tbl">
          <thead>
            <tr>
              <th>{{ t('prov.quote.compare.colName') }}</th>
              <th class="text-right">{{ t('prov.quote.cols.cost') }}</th>
              <th class="text-right">{{ t('prov.quote.compare.colCo2') }}</th>
              <th class="text-right">{{ t('prov.quote.compare.colErrors') }}</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cs in compared" :key="cs.subscription">
              <td class="cmp-name">{{ cs.name || cs.subscription }}</td>
              <td class="text-right">{{ formatCostRange(cs.cost, currency) }}</td>
              <td class="text-right">{{ formatCo2(cs.cost?.co2) }}</td>
              <td class="text-right">
                <v-chip v-if="cs.errors?.length" size="x-small" color="warning" variant="tonal" @click="showErrors(cs)">
                  {{ cs.errors.length }}
                </v-chip>
                <span v-else class="text-medium-emphasis">0</span>
              </td>
              <td class="text-right">
                <v-btn icon size="x-small" variant="text" :title="t('prov.quote.compare.remove')" :loading="busy === cs.subscription" @click="remove(cs)">
                  <v-icon>mdi-delete-outline</v-icon>
                </v-btn>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Expanded lookup errors for a CS. -->
        <div v-if="errorsFor" class="cmp-errors mt-3">
          <div class="d-flex align-center mb-1">
            <strong class="text-body-2">{{ t('prov.quote.compare.errorsTitle', { name: errorsFor.name || errorsFor.subscription }) }}</strong>
            <v-spacer />
            <v-btn icon size="x-small" variant="text" @click="errorsFor = null"><v-icon>mdi-close</v-icon></v-btn>
          </div>
          <div v-for="e in errorsFor.errors" :key="e.id" class="cmp-err-row">
            <v-icon size="12" class="me-1 text-medium-emphasis">{{ typeIcon(e.resourceType) }}</v-icon>
            <span class="cmp-err-name">{{ e.name }}</span>
            <span class="cmp-err-type text-medium-emphasis">{{ (e.resourceType || '').toLowerCase() }}</span>
          </div>
        </div>
      </v-card-text>

      <v-card-actions>
        <v-btn variant="text" prepend-icon="mdi-sync" :loading="resyncing" :disabled="!compared.length" @click="resync">
          {{ t('prov.quote.compare.resync') }}
        </v-btn>
        <v-spacer />
        <v-btn variant="text" @click="close">{{ t('common.close') }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
// Compare setup — manages the set of compared subscriptions (CS) of the main
// subscription (MS). Adding a CS clones the MS quote into it (backend
// ProvComparisonResource); the CS then tracks MS changes. Lists each CS with its
// aggregate cost / CO₂ and the resources it could not reproduce.
import { ref, computed, watch } from 'vue'
import { useApi, useI18nStore, NodeIcon, LigojAutocomplete } from '@ligoj/host'
import { formatCostRange, formatCo2, TAB_TYPES } from '../quoteFormatters.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** The main subscription (MS) id. */
  subscriptionId: { type: [Number, String], default: null },
  /** MS quote currency, for cost formatting. */
  currency: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'changed'])

const { t } = useI18nStore()
const api = useApi()

const TYPE_ICON = Object.fromEntries(TAB_TYPES.map((x) => [x.key.toUpperCase(), x.icon]))
const typeIcon = (rt) => TYPE_ICON[rt] || 'mdi-cube-outline'
const currency = computed(() => props.currency || { unit: '$', rate: 1 })

const loading = ref(false)
const loadingSubs = ref(false)
const adding = ref(false)
const resyncing = ref(false)
const busy = ref(null)
const compared = ref([])
const allProvSubs = ref([]) // [{ id, name, toolId, provider, configName }] — same project only
const toAdd = ref(null)
const errorsFor = ref(null)

const comparedIds = computed(() => new Set(compared.value.map((c) => String(c.subscription))))
const cloneWarn = computed(() => !!toAdd.value)
// allProvSubs already excludes the current subscription + is scoped to its project;
// here we only drop those already added. The label (selection chip) shows the quote name.
const addable = computed(() => allProvSubs.value
  .filter((s) => !comparedIds.value.has(String(s.id)))
  .map((s) => ({ ...s, label: s.configName || s.name })))

const toolOf = (nodeId) => String(nodeId).split(':').slice(0, 3).join(':')

// Dropdown line under the quote name: "<provider> — <account>".
const subtitleOf = (raw) => [raw?.provider, raw?.name].filter(Boolean).join(' — ')

async function loadCompared() {
  loading.value = true
  try {
    compared.value = (await api.get(`rest/service/prov/${props.subscriptionId}/compare`)) || []
  } catch {
    compared.value = []
  } finally {
    loading.value = false
  }
}

const nodeIdOf = (s) => (typeof s.node === 'string' ? s.node : s.node?.id)

async function loadSubs() {
  loadingSubs.value = true
  try {
    const data = await api.get('rest/subscription')
    const subs = Array.isArray(data?.subscriptions) ? data.subscriptions : []
    const nodes = Array.isArray(data?.nodes) ? data.nodes : []
    const nameById = Object.fromEntries(nodes.map((n) => [n.id, n.name]))
    const curId = String(props.subscriptionId)
    // Only provisioning subscriptions of the SAME project, excluding the current
    // one (a subscription cannot synchronize with itself).
    const projectId = subs.find((s) => String(s.id) === curId)?.project
    const candidates = subs
      .filter((s) => {
        const nodeId = nodeIdOf(s)
        return nodeId && nodeId.startsWith('service:prov:') && String(s.id) !== curId
          && (projectId == null || String(s.project) === String(projectId))
      })
      .map((s) => {
        const nodeId = nodeIdOf(s)
        const toolId = toolOf(nodeId)
        return { id: s.id, toolId, provider: nameById[toolId] || toolId.split(':')[2], name: nameById[nodeId] || `#${s.id}`, configName: '' }
      })
    // Resolve each candidate's quote (configuration) name — a fetch failure just leaves
    // the name blank, it never empties the list.
    await Promise.all(candidates.map((c) => api.get(`rest/subscription/${c.id}/configuration`, { silent: true })
      .then((cfg) => { c.configName = cfg?.configuration?.name || cfg?.name || '' })
      .catch(() => {})))
    allProvSubs.value = candidates
  } catch {
    allProvSubs.value = []
  } finally {
    loadingSubs.value = false
  }
}

async function add() {
  if (!toAdd.value) return
  adding.value = true
  try {
    await api.post(`rest/service/prov/${props.subscriptionId}/compare/${toAdd.value}`)
    toAdd.value = null
    await loadCompared()
    emit('changed')
  } finally {
    adding.value = false
  }
}

async function remove(cs) {
  busy.value = cs.subscription
  try {
    await api.del(`rest/service/prov/${props.subscriptionId}/compare/${cs.subscription}`)
    if (errorsFor.value?.subscription === cs.subscription) errorsFor.value = null
    await loadCompared()
    emit('changed')
  } finally {
    busy.value = null
  }
}

async function resync() {
  resyncing.value = true
  try {
    await api.post(`rest/service/prov/${props.subscriptionId}/compare/resync`)
    await loadCompared()
    emit('changed')
  } finally {
    resyncing.value = false
  }
}

function showErrors(cs) {
  errorsFor.value = errorsFor.value?.subscription === cs.subscription ? null : cs
}

function close() {
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (open) => {
  if (open) {
    errorsFor.value = null
    toAdd.value = null
    loadCompared()
    loadSubs()
  }
})

defineExpose({ compared, addable, toAdd, add, remove, resync, loadCompared })
</script>

<style scoped>
.cmp-tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}
.cmp-tbl th,
.cmp-tbl td {
  padding: 0.4rem 0.6rem;
  border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
}
.cmp-tbl th {
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: rgba(var(--v-theme-on-surface), 0.55);
  text-align: left;
}
.text-right { text-align: right; }
.cmp-name { font-weight: 500; }
.cmp-errors {
  border: 1px solid rgba(var(--v-theme-warning), 0.4);
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  background: rgba(var(--v-theme-warning), 0.06);
}
.cmp-err-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
  padding: 1px 0;
}
.cmp-err-type { font-size: 0.78em; margin-left: auto; }
</style>
