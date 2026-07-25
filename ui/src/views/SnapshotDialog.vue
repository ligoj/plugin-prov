<template>
  <v-dialog :model-value="modelValue" max-width="920" @update:model-value="close">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon color="primary">mdi-history</v-icon>
        {{ t('prov.quote.snap.title') }}
        <v-spacer />
        <v-btn icon size="small" variant="text" @click="close"><v-icon>mdi-close</v-icon><v-tooltip activator="parent" location="bottom">{{ t('common.close') }}</v-tooltip></v-btn>
      </v-card-title>

      <v-card-text>
        <!-- Create bar. -->
        <div class="d-flex align-center ga-2 mb-4">
          <v-text-field
            v-model="label"
            :label="t('prov.quote.snap.label')"
            variant="outlined"
            density="compact"
            hide-details
            class="flex-grow-1"
            @keyup.enter="create"
          />
          <v-btn color="primary" variant="elevated" :disabled="!label?.trim()" :loading="creating"
            prepend-icon="mdi-camera-plus-outline" @click="create">
            {{ t('prov.quote.snap.create') }}
          </v-btn>
        </div>

        <!-- Restore result warning (rows without a matching offer any more). -->
        <v-alert v-if="restoreFailed?.length" type="warning" variant="tonal" density="compact" class="mb-3" closable
          @click:close="restoreFailed = null">
          {{ t('prov.quote.snap.restoreFailed', { names: restoreFailed.join(', ') }) }}
        </v-alert>

        <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-2" />
        <v-alert v-else-if="!snapshots.length" type="info" variant="tonal" density="compact">
          {{ t('prov.quote.snap.empty') }}
        </v-alert>

        <table v-else class="snap-tbl">
          <thead>
            <tr>
              <th>{{ t('prov.quote.snap.colName') }}</th>
              <th>{{ t('prov.quote.snap.colCreated') }}</th>
              <th class="text-right">{{ t('prov.quote.snap.colResources') }}</th>
              <th class="text-right">{{ t('prov.quote.cols.cost') }}</th>
              <th class="text-right">CO₂</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in snapshots" :key="s.id" :class="{ 'snap-active': diffId === s.id }">
              <td class="snap-name">{{ s.name }}</td>
              <td class="snap-date">{{ formatDate(s.createdDate) }}<span v-if="s.createdBy" class="snap-author"> · {{ s.createdBy }}</span></td>
              <td class="text-right">{{ s.nbResources }}</td>
              <td class="text-right">{{ formatCost(s.cost, currency) }}</td>
              <td class="text-right">{{ formatCo2(s.co2) }}</td>
              <td class="text-right snap-actions">
                <v-btn icon size="x-small" variant="text" :color="diffId === s.id ? 'primary' : undefined" @click="toggleDiff(s)">
                  <v-icon>mdi-vector-difference</v-icon>
                <v-tooltip activator="parent" location="bottom">{{ t('prov.quote.snap.diff') }}</v-tooltip></v-btn>
                <v-btn icon size="x-small" variant="text" :loading="busy === s.id"
                  @click="askRestore(s)">
                  <v-icon>mdi-backup-restore</v-icon>
                <v-tooltip activator="parent" location="bottom">{{ t('prov.quote.snap.restore') }}</v-tooltip></v-btn>
                <v-btn icon size="x-small" variant="text" @click="remove(s)">
                  <v-icon>mdi-delete-outline</v-icon>
                <v-tooltip activator="parent" location="bottom">{{ t('common.delete') }}</v-tooltip></v-btn>
              </td>
            </tr>
          </tbody>
        </table>

        <!-- Diff panel: snapshot (reference) → current quote or another snapshot. -->
        <div v-if="diffId != null" class="snap-diff mt-4">
          <div class="d-flex align-center ga-2 flex-wrap mb-2">
            <strong class="text-body-2">
              {{ t('prov.quote.snap.diffTitle', { name: diffSnapshot?.name || diffId }) }}
            </strong>
            <v-icon size="14">mdi-arrow-right-thin</v-icon>
            <v-select v-model="diffTarget" :items="diffTargets" item-title="label" item-value="id" density="compact"
              variant="outlined" hide-details class="snap-diff-target" />
            <LjSegmented v-model="metric" :options="[
              { value: 'cost', icon: 'mdi-currency-usd', label: t('prov.quote.viewMode.cost') },
              { value: 'co2', icon: 'mdi-leaf', label: t('prov.quote.viewMode.co2') },
            ]" />
            <v-spacer />
            <span v-if="diff" class="snap-diff-total" :class="`text-${diffMeta(diff.totals.pct).color}`">
              {{ fmt(diff.totals.from) }} → {{ fmt(diff.totals.to) }}
              <strong>{{ formatDiffPct(diff.totals.pct) }}</strong>
            </span>
          </div>

          <v-progress-linear v-if="diffLoading" indeterminate color="primary" />
          <template v-else-if="diff">
            <v-alert v-if="!diff.added.length && !diff.removed.length && !diff.changed.length" type="success"
              variant="tonal" density="compact">
              {{ t('prov.quote.snap.identical', { n: diff.unchanged }) }}
            </v-alert>
            <table v-else class="snap-diff-tbl">
              <tbody>
                <tr v-for="x in diff.added" :key="`a-${x.row.resourceType}-${x.row.name}`" class="snap-add">
                  <td class="snap-op"><v-icon size="13">mdi-plus</v-icon></td>
                  <td class="snap-res"><v-icon size="12" class="me-1">{{ typeIcon(x.row.resourceType) }}</v-icon>{{ x.row.name }}</td>
                  <td class="snap-detail">{{ x.row.typeName }}<span v-if="x.row.term"> · {{ x.row.term }}</span></td>
                  <td class="text-right snap-val">+{{ fmt(x.value) }}</td>
                </tr>
                <tr v-for="x in diff.removed" :key="`r-${x.row.resourceType}-${x.row.name}`" class="snap-del">
                  <td class="snap-op"><v-icon size="13">mdi-minus</v-icon></td>
                  <td class="snap-res"><v-icon size="12" class="me-1">{{ typeIcon(x.row.resourceType) }}</v-icon>{{ x.row.name }}</td>
                  <td class="snap-detail">{{ x.row.typeName }}<span v-if="x.row.term"> · {{ x.row.term }}</span></td>
                  <td class="text-right snap-val">−{{ fmt(x.value) }}</td>
                </tr>
                <tr v-for="x in diff.changed" :key="`c-${x.a.resourceType}-${x.a.name}`" class="snap-chg">
                  <td class="snap-op"><v-icon size="13">mdi-pencil</v-icon></td>
                  <td class="snap-res"><v-icon size="12" class="me-1">{{ typeIcon(x.a.resourceType) }}</v-icon>{{ x.a.name }}</td>
                  <td class="snap-detail">
                    <template v-if="x.priceChanged">{{ x.a.typeName }} · {{ x.a.term }} → {{ x.b.typeName }} · {{ x.b.term }}</template>
                  </td>
                  <td class="text-right snap-val">
                    {{ fmt(x.from) }} → {{ fmt(x.to) }}
                    <span :class="`text-${diffMeta(x.pct).color}`">{{ formatDiffPct(x.pct) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-if="diff.unchanged" class="snap-unchanged">{{ t('prov.quote.snap.unchanged', { n: diff.unchanged }) }}</div>
          </template>
        </div>
      </v-card-text>
    </v-card>

    <!-- Restore confirmation — destructive: the current quote is replaced. -->
    <v-dialog v-model="confirmRestore" max-width="440">
      <v-card>
        <v-card-title>{{ t('prov.quote.snap.restoreTitle') }}</v-card-title>
        <v-card-text>{{ t('prov.quote.snap.restoreBody', { name: restoreTarget?.name || '' }) }}</v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="confirmRestore = false">{{ t('common.cancel') }}</v-btn>
          <v-btn color="warning" variant="elevated" :loading="busy != null" @click="doRestore">
            {{ t('prov.quote.snap.restore') }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-dialog>
</template>

<script setup>
// Quote snapshots (feature 08): named, immutable copies of the quote that can be
// created, diffed (snapshot → current, or snapshot → snapshot, computed
// client-side by quoteDiff.js) and restored. Restore is destructive and
// confirmed; rows the current catalog cannot price any more are reported.
import { ref, computed, watch } from 'vue'
import { useApi, useI18nStore, LjSegmented } from '@ligoj/host'
import { formatCost, formatCo2, TAB_TYPES } from '../quoteFormatters.js'
import { diffMeta, formatDiffPct } from '../compareApi.js'
import { normalizeConfig, snapshotRows, quoteDiff } from '../quoteDiff.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  subscriptionId: { type: [Number, String], default: null },
  /** Live quote configuration — the default diff target. */
  config: { type: Object, default: null },
  currency: { type: Object, default: null },
  /** Initial metric, mirroring the quote's cost/CO₂ view. */
  viewMode: { type: String, default: 'cost' },
})
const emit = defineEmits(['update:modelValue', 'restored'])

const i18n = useI18nStore()
const t = i18n.t
const api = useApi()

const TYPE_ICON = Object.fromEntries(TAB_TYPES.map((x) => [x.key, x.icon]))
const typeIcon = (key) => TYPE_ICON[key] || 'mdi-cube-outline'
const currency = computed(() => props.currency || { unit: '$', rate: 1 })

const base = () => `rest/service/prov/${props.subscriptionId}/snapshot`

const loading = ref(false)
const creating = ref(false)
const busy = ref(null)
const snapshots = ref([])
const label = ref('')
const restoreFailed = ref(null)
const confirmRestore = ref(false)
const restoreTarget = ref(null)

/* ---- Diff state ---- */
const metric = ref('cost')
const diffId = ref(null) // reference snapshot id
const diffTarget = ref('current') // 'current' or another snapshot id
const diffLoading = ref(false)
const documents = ref({}) // id → snapshot document (cached)

const diffSnapshot = computed(() => snapshots.value.find((s) => s.id === diffId.value))
const diffTargets = computed(() => [
  { id: 'current', label: t('prov.quote.snap.current') },
  ...snapshots.value.filter((s) => s.id !== diffId.value).map((s) => ({ id: s.id, label: s.name })),
])

const fmt = (v) => (metric.value === 'co2' ? formatCo2(v) : formatCost(v, currency.value))
const formatDate = (d) => (d ? new Date(d).toLocaleString(i18n.locale || undefined) : '')

async function load() {
  loading.value = true
  try {
    snapshots.value = (await api.get(base())) || []
  } catch {
    snapshots.value = []
  } finally {
    loading.value = false
  }
}

async function create() {
  const name = label.value?.trim()
  if (!name) return
  creating.value = true
  try {
    await api.post(base(), { name })
    label.value = ''
    await load()
  } finally {
    creating.value = false
  }
}

async function remove(s) {
  await api.del(`${base()}/${s.id}`)
  if (diffId.value === s.id) diffId.value = null
  delete documents.value[s.id]
  await load()
}

function askRestore(s) {
  restoreTarget.value = s
  confirmRestore.value = true
}

async function doRestore() {
  const s = restoreTarget.value
  if (!s) return
  busy.value = s.id
  try {
    const failed = await api.post(`${base()}/${s.id}/restore`)
    restoreFailed.value = Array.isArray(failed) && failed.length ? failed : null
    confirmRestore.value = false
    emit('restored')
  } finally {
    busy.value = null
  }
}

function toggleDiff(s) {
  diffId.value = diffId.value === s.id ? null : s.id
  diffTarget.value = 'current'
}

/** Fetch (and cache) a snapshot document. */
async function documentOf(id) {
  if (!documents.value[id]) {
    documents.value = { ...documents.value, [id]: await api.get(`${base()}/${id}`) }
  }
  return documents.value[id]
}

const diffRows = ref(null) // { a, b } canonical rows
watch([diffId, diffTarget, () => props.config], async () => {
  diffRows.value = null
  if (diffId.value == null) return
  diffLoading.value = true
  try {
    const a = snapshotRows(await documentOf(diffId.value))
    const b = diffTarget.value === 'current'
      ? normalizeConfig(props.config)
      : snapshotRows(await documentOf(diffTarget.value))
    diffRows.value = { a, b }
  } catch {
    diffRows.value = null
  } finally {
    diffLoading.value = false
  }
}, { immediate: false })

const diff = computed(() => (diffRows.value ? quoteDiff(diffRows.value.a, diffRows.value.b, metric.value) : null))

function close() {
  emit('update:modelValue', false)
}

watch(() => props.modelValue, (open) => {
  if (open) {
    metric.value = props.viewMode === 'co2' ? 'co2' : 'cost'
    restoreFailed.value = null
    diffId.value = null
    documents.value = {}
    load()
  }
})

defineExpose({ snapshots, label, create, remove, doRestore, askRestore, toggleDiff, diff, diffId, diffTarget, metric })
</script>

<style scoped>
.snap-tbl,
.snap-diff-tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.88rem;
}
.snap-tbl th,
.snap-tbl td {
  padding: 0.35rem 0.6rem;
  border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
}
.snap-tbl th {
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: rgba(var(--v-theme-on-surface), 0.55);
  text-align: left;
}
.text-right { text-align: right; }
.snap-name { font-weight: 600; }
.snap-date { font-size: 0.82em; color: rgba(var(--v-theme-on-surface), 0.65); white-space: nowrap; }
.snap-author { opacity: 0.8; }
.snap-actions { white-space: nowrap; }
.snap-active td { background: rgba(var(--v-theme-primary), 0.06); }

.snap-diff {
  border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
  border-radius: 8px;
  padding: 0.75rem 0.9rem;
}
.snap-diff-target { max-width: 220px; min-width: 150px; }
.snap-diff-total { font-variant-numeric: tabular-nums; font-size: 0.9rem; }
.snap-diff-tbl td { padding: 0.25rem 0.5rem; }
.snap-op { width: 26px; }
.snap-res { font-weight: 500; white-space: nowrap; }
.snap-detail { font-size: 0.8em; color: rgba(var(--v-theme-on-surface), 0.6); }
.snap-val { font-variant-numeric: tabular-nums; white-space: nowrap; }
.snap-add .snap-op, .snap-add .snap-val { color: rgb(var(--v-theme-success)); }
.snap-del .snap-op, .snap-del .snap-val { color: rgb(var(--v-theme-error)); }
.snap-chg .snap-op { color: rgb(var(--v-theme-warning)); }
.snap-unchanged {
  margin-top: 6px;
  font-size: 0.8em;
  color: rgba(var(--v-theme-on-surface), 0.55);
}
</style>
