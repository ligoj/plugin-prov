<template>
  <div class="catalog-view">
    <v-alert type="info" variant="tonal" density="compact" class="mb-4">
      {{ t('catalog.intro') }}
    </v-alert>

    <div class="d-flex align-center mb-4 ga-2">
      <h1 class="text-h5 mb-0">{{ t('catalog.title') }}</h1>
      <v-chip v-if="catalogs.length" size="small" variant="tonal" class="ml-1">{{ catalogs.length }}</v-chip>
      <v-spacer />
      <v-btn icon size="small" variant="text" :loading="loading" @click="reload">
        <v-icon>mdi-refresh</v-icon>
        <v-tooltip activator="parent" location="top" :text="t('nav.refresh')" />
      </v-btn>
    </div>

    <v-skeleton-loader v-if="loading && catalogs.length === 0" type="table" />

    <v-alert v-else-if="error" type="warning" variant="tonal" class="mb-4">{{ error }}</v-alert>

    <v-alert v-else-if="!catalogs.length" type="info" variant="tonal" density="compact">
      {{ t('catalog.empty') }}
    </v-alert>

    <LigojDataTable
      v-else
      filename="catalogs.csv"
      :headers="headers"
      :items="catalogs"
      :items-per-page="-1"
      hide-default-footer
      density="compact"
      item-value="node.id"
    >
      <!-- Header icons. Catalog columns are non-sortable, so a custom
           header slot fully owns the cell; each shows a relevant mdi icon
           next to the column title. -->
      <template #header.node="{ column }"><span class="d-inline-flex align-center"><v-icon size="small" class="mr-1">mdi-cloud-outline</v-icon>{{ column.title }}<v-tooltip activator="parent" location="top" :text="column.title" /></span></template>
      <template #header.lastSuccess="{ column }"><span class="d-inline-flex align-center"><v-icon size="small" class="mr-1">mdi-calendar-clock</v-icon>{{ column.title }}<v-tooltip activator="parent" location="top" :text="column.title" /></span></template>
      <template #header.nbQuotes="{ column }">
        <v-icon size="small">mdi-file-document-multiple-outline</v-icon>
        <v-tooltip activator="parent" location="top" :text="column.title" />
      </template>
      <template #header.nbLocations="{ column }">
        <v-icon size="small">mdi-map-marker-outline</v-icon>
        <v-tooltip activator="parent" location="top" :text="column.title" />
      </template>
      <template #header.nbTypes="{ column }">
        <v-icon size="small">mdi-shape-outline</v-icon>
        <v-tooltip activator="parent" location="top" :text="column.title" />
      </template>
      <template #header.nbPrices="{ column }">
        <v-icon size="small">mdi-currency-usd</v-icon>
        <v-tooltip activator="parent" location="top" :text="column.title" />
      </template>
      <template #header.status="{ column }"><span class="d-inline-flex align-center"><v-icon size="small" class="mr-1">mdi-progress-check</v-icon>{{ column.title }}<v-tooltip activator="parent" location="top" :text="column.title" /></span></template>

      <template #item.node="{ item }">
        <div class="d-flex align-center ga-2">
          <NodeIcon :node="item.node" />
          <span class="text-body-2">{{ item.node?.name || item.node?.id }}</span>
        </div>
      </template>
      <template #item.lastSuccess="{ item }">
        {{ formatDate(item.status?.lastSuccess) }}
      </template>
      <template #item.nbQuotes="{ item }">{{ item.nbQuotes ?? 0 }}</template>
      <template #item.nbLocations="{ item }">{{ item.status?.nbLocations ?? '-' }}</template>
      <template #item.nbTypes="{ item }">{{ item.status?.nbTypes ?? '-' }}</template>
      <template #item.nbPrices="{ item }">{{ item.status?.nbPrices ?? '-' }}</template>
      <template #item.status="{ item }">
        <v-chip :color="statusColor(item)" size="x-small" variant="tonal">
          <v-icon size="x-small" start>{{ statusIcon(item) }}</v-icon>
          {{ statusLabel(item) }}
        </v-chip>
      </template>
      <template #item.actions="{ item }">
        <template v-if="isRunning(item)">
          <v-btn icon size="small" variant="text" color="error" @click="cancelImport(item)">
            <v-icon size="small">mdi-cancel</v-icon>
            <v-tooltip activator="parent" location="top" :text="t('catalog.cancel')" />
          </v-btn>
        </template>
        <template v-else>
          <v-btn icon size="small" variant="text" @click="runImport(item, false)">
            <v-icon size="small">mdi-download</v-icon>
            <v-tooltip activator="parent" location="top" :text="t('catalog.updateStandard')" />
          </v-btn>
          <v-btn icon size="small" variant="text" @click="runImport(item, true)">
            <v-icon size="small">mdi-download-multiple</v-icon>
            <v-tooltip activator="parent" location="top" :text="t('catalog.updateForce')" />
          </v-btn>
        </template>
      </template>
    </LigojDataTable>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useApi, useAppStore, useErrorStore, useI18nStore, LigojDataTable, NodeIcon } from '@ligoj/host'

const api = useApi()
const app = useAppStore()
const errorStore = useErrorStore()
const i18n = useI18nStore()
const t = i18n.t

const loading = ref(false)
const error = ref(null)
const catalogs = ref([])

/* Per-node polling timers. Catalog updates run server-side; we poll
 * the catalog list endpoint while any node has `status.start` without
 * `status.end` so progress shows up live. 5 s mirrors the legacy
 * `scheduleUploadStep` interval. */
const POLL_MS = 5000
const pollingTimers = {}

const headers = computed(() => [
  { title: t('catalog.cols.provider'), key: 'node', sortable: false },
  { title: t('catalog.lastSuccess'), key: 'lastSuccess', sortable: false, width: '160px' },
  { title: t('catalog.cols.quotes'), key: 'nbQuotes', sortable: false, width: '80px', align: 'end' },
  { title: t('catalog.cols.locations'), key: 'nbLocations', sortable: false, width: '80px', align: 'end' },
  { title: t('catalog.cols.types'), key: 'nbTypes', sortable: false, width: '80px', align: 'end' },
  { title: t('catalog.cols.prices'), key: 'nbPrices', sortable: false, width: '90px', align: 'end' },
  { title: t('catalog.status'), key: 'status', sortable: false, width: '160px' },
  { title: '', key: 'actions', sortable: false, width: '120px', align: 'center' },
])

async function reload() {
  loading.value = true
  error.value = null
  try {
    const data = await api.get('rest/service/prov/catalog')
    if (!Array.isArray(data)) {
      error.value = t('catalog.loadFailed') || 'Failed to load catalogs.'
      return
    }
    catalogs.value = data
    // Re-arm polling for any catalog that's still running.
    for (const c of data) {
      if (isRunning(c)) ensurePolling(c.node?.id)
    }
  } finally {
    loading.value = false
  }
}

function isRunning(c) {
  if (!c?.status) return false
  return c.status.start && !c.status.end
}

function statusColor(c) {
  if (!c?.status) return 'grey'
  if (isRunning(c)) return 'primary'
  if (c.status.end && c.status.failed) return 'error'
  if (c.status.end) return 'success'
  return 'grey'
}

function statusIcon(c) {
  if (!c?.status) return 'mdi-help-circle-outline'
  if (isRunning(c)) return 'mdi-progress-clock'
  if (c.status.end && c.status.failed) return 'mdi-alert'
  if (c.status.end) return 'mdi-check-circle'
  return 'mdi-clock-outline'
}

function statusLabel(c) {
  if (!c?.status) return t('catalog.status.unknown')
  if (isRunning(c)) {
    const pct = c.status.total ? Math.round((c.status.done / c.status.total) * 100) : null
    return pct != null ? `${pct}%` : t('catalog.status.running')
  }
  if (c.status.end && c.status.failed) return t('catalog.status.failed')
  if (c.status.end) return t('catalog.status.ok')
  return t('catalog.status.never')
}

function formatDate(ms) {
  if (!ms) return ''
  const d = new Date(ms)
  return isNaN(d.getTime()) ? '' : d.toISOString().slice(0, 16).replace('T', ' ')
}

/* ---------- Import / cancel ---------- */

async function runImport(catalog, force) {
  const id = catalog?.node?.id
  if (!id) return
  const url = `rest/service/prov/catalog/${encodeURIComponent(id)}${force ? '?force=true' : ''}`
  const ok = await api.post(url)
  if (ok == null) return
  errorStore.success(t('catalog.statusStarted', { name: catalog.node.name || id }))
  // Local optimistic update so the row shows "running" until the next
  // poll lands.
  if (catalog.status) {
    catalog.status.start = Date.now()
    catalog.status.end = null
    catalog.status.failed = false
  } else {
    catalog.status = { start: Date.now(), end: null }
  }
  ensurePolling(id)
}

async function cancelImport(catalog) {
  const id = catalog?.node?.id
  if (!id) return
  const ok = await api.del(`rest/service/prov/catalog/${encodeURIComponent(id)}`)
  if (ok == null) return
  errorStore.success(t('catalog.statusCanceled', { name: catalog.node.name || id }))
  await reload()
}

function ensurePolling(id) {
  if (!id || pollingTimers[id]) return
  pollingTimers[id] = setInterval(async () => {
    const data = await api.get('rest/service/prov/catalog')
    if (!Array.isArray(data)) return
    catalogs.value = data
    const c = data.find((x) => x.node?.id === id)
    if (!c || !isRunning(c)) {
      clearInterval(pollingTimers[id])
      delete pollingTimers[id]
    }
  }, POLL_MS)
}

function stopAllPolling() {
  for (const id of Object.keys(pollingTimers)) {
    clearInterval(pollingTimers[id])
    delete pollingTimers[id]
  }
}

onMounted(async () => {
  app.setBreadcrumbs(
    [
      { title: t('nav.home'), to: '/' },
      { title: t('prov.title') },
      { title: t('catalog.title') },
    ],
    { refresh: reload },
  )
  await reload()
})

onBeforeUnmount(stopAllPolling)
</script>

<style scoped>
.catalog-view {
  padding: 0.5rem;
}
</style>
