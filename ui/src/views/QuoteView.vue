<template>
  <div class="quote-view">
    <v-skeleton-loader v-if="loading && !config" type="article, table" />

    <v-alert v-if="error" type="warning" variant="tonal" class="mb-4">{{ error }}</v-alert>

    <template v-if="config">
      <!-- Header — provider icon + quote name + total cost summary. -->
      <div class="d-flex align-center flex-wrap mb-4 ga-2">
        <NodeIcon v-if="providerNode" :node="providerNode" />
        <h1 class="text-h5 mb-0">{{ config.name || subscriptionId }}</h1>
        <v-btn icon size="small" variant="text" :title="t('prov.quote.edit')" @click="openEdit">
          <v-icon size="small">mdi-pencil</v-icon>
        </v-btn>
        <v-chip v-if="config.description" size="small" variant="tonal" class="ml-2">
          {{ config.description }}
        </v-chip>
        <v-spacer />
        <div class="text-h6 d-flex align-center">
          <span class="text-medium-emphasis text-body-2 mr-2">{{ t('prov.quote.totalCost') }}:</span>
          <v-icon size="small" class="mr-1">mdi-currency-usd</v-icon>
          {{ formatCostRange(scaledCost(config.cost), config.currency) }}
          <span class="text-caption text-medium-emphasis ml-1">/{{ t(`prov.quote.period.${costPeriod}Suffix`) }}</span>
        </div>
        <!-- Cost-period selector. Pure display — the backend stores
             monthly numbers; we just scale at render time. -->
        <v-menu>
          <template #activator="{ props: actProps }">
            <v-btn v-bind="actProps" size="small" variant="text" :title="t('prov.quote.period.title')">
              <v-icon size="small">mdi-clock-outline</v-icon>
              <span class="text-caption ml-1">/{{ t(`prov.quote.period.${costPeriod}Suffix`) }}</span>
            </v-btn>
          </template>
          <v-list density="compact" min-width="160">
            <v-list-item v-for="p in COST_PERIODS" :key="p" :title="t(`prov.quote.period.${p}`)" @click="costPeriod = p">
              <template v-if="costPeriod === p" #append>
                <v-icon size="x-small">mdi-check</v-icon>
              </template>
            </v-list-item>
          </v-list>
        </v-menu>
        <v-btn icon size="small" variant="text" :loading="refreshing" :title="t('prov.quote.refresh')" @click="reload">
          <v-icon>mdi-refresh</v-icon>
        </v-btn>
        <!-- Recomputes prices against the latest provider catalog —
             the legacy `refreshCost` action. Distinct from "reload",
             which only re-fetches the configuration as-is. -->
        <v-btn icon size="small" variant="text" :loading="refreshingPrices" :title="t('prov.quote.refreshPrices')"
          @click="refreshPrices">
          <v-icon>mdi-cash-sync</v-icon>
        </v-btn>
        <!-- Exports — three pre-built backend endpoints. The path
             segment itself is the suggested filename so the backend
             can mirror it as Content-Disposition. -->
        <v-menu>
          <template #activator="{ props: actProps }">
            <v-btn v-bind="actProps" icon size="small" variant="text" :title="t('prov.quote.exports')" :disabled="!subscriptionId">
              <v-icon>mdi-download</v-icon>
            </v-btn>
          </template>
          <v-list density="compact" min-width="260">
            <v-list-item :href="exportUrl.inline" prepend-icon="mdi-file-table" :title="t('prov.quote.exports.inline')" />
            <v-list-item :href="exportUrl.split"  prepend-icon="mdi-file-table-outline" :title="t('prov.quote.exports.split')" />
            <v-list-item :href="exportUrl.json"   :download="jsonDownloadName" prepend-icon="mdi-code-json" :title="t('prov.quote.exports.json')" />
          </v-list>
        </v-menu>
      </div>

      <!-- Cost-breakdown donut. Renders only when something is in the
           quote (the component itself early-returns on zero total).
           The metric toggle (cost ↔ CO₂) mirrors the legacy
           `optimizer-view-mode` switch — see `viewMode` below. -->
      <v-card variant="tonal" class="mb-4">
        <v-card-text class="py-3">
          <div class="d-flex align-center justify-space-between mb-1">
            <span class="text-overline text-medium-emphasis">
              {{ viewMode === 'co2' ? t('prov.quote.breakdown.titleCo2') : t('prov.quote.breakdown.title') }}
            </span>
            <v-btn-toggle v-model="viewMode" mandatory density="compact" variant="outlined" divided>
              <v-btn size="small" value="cost" :title="t('prov.quote.viewMode.cost')">
                <v-icon size="small" start>mdi-currency-usd</v-icon>
                {{ t('prov.quote.viewMode.cost') }}
              </v-btn>
              <v-btn size="small" value="co2" :title="t('prov.quote.viewMode.co2')">
                <v-icon size="small" start>mdi-leaf</v-icon>
                {{ t('prov.quote.viewMode.co2') }}
              </v-btn>
            </v-btn-toggle>
          </div>
          <QuoteBreakdown :config="config" :mode="viewMode" />
        </v-card-text>
      </v-card>

      <v-alert type="info" variant="tonal" density="compact" class="mb-4">
        {{ t('prov.quote.notMigrated') }}
      </v-alert>

      <!-- Tabs — one per resource type. Counts come from the loaded config. -->
      <v-tabs v-model="activeTab" density="compact" show-arrows class="mb-2">
        <v-tab v-for="t in TAB_TYPES" :key="t.key" :value="t.key">
          <v-icon :icon="t.icon" start size="small" />
          {{ tabLabel(t.key) }}
          <v-chip v-if="counts[t.key]" size="x-small" class="ml-2" variant="tonal">{{ counts[t.key] }}</v-chip>
        </v-tab>
      </v-tabs>

      <v-window v-model="activeTab">
        <v-window-item v-for="tab in TAB_TYPES" :key="tab.key" :value="tab.key">
          <div class="d-flex align-center mb-2 ga-2 flex-wrap">
            <span class="text-subtitle-2 text-medium-emphasis">
              {{ tabLabel(tab.key) }}
              <span v-if="rowsByType[tab.key].length" class="ml-1">({{ filteredRowsByType[tab.key].length }} / {{ rowsByType[tab.key].length }})</span>
            </span>
            <v-spacer />
            <!-- Debounced text filter. Matches the legacy
                 `.subscribe-configuration-prov-search` input; one
                 query per tab so a search on Instances doesn't bleed
                 into Storage. -->
            <v-text-field v-if="rowsByType[tab.key].length" :model-value="searchByType[tab.key]" :label="t('common.search')"
              prepend-inner-icon="mdi-magnify" density="compact" hide-details variant="outlined" clearable
              class="quote-search" @update:model-value="(v) => onSearch(tab.key, v)" />
            <!-- Per-type create button. ComputeEditDialog covers all 4
                 compute types; storage + support each have their own. -->
            <v-btn size="small" color="primary" variant="elevated" prepend-icon="mdi-plus" @click="openResourceCreate(tab.key)">
              {{ t(`prov.quote.${tab.key}.new`) }}
            </v-btn>
            <!-- Instance-only: CSV bulk import. Stays out of the other
                 tabs since the import endpoint is instance-specific
                 (per the legacy `popup-prov-instance-import`). -->
            <v-btn v-if="tab.key === 'instance'" size="small" variant="outlined" prepend-icon="mdi-file-upload"
              @click="importDialog = true">
              {{ t('prov.quote.import.title') }}
            </v-btn>
            <v-btn v-if="rowsByType[tab.key].length" size="small" variant="text" color="error" prepend-icon="mdi-delete-sweep"
              @click="askDeleteAll(tab.key)">
              {{ t('prov.quote.delete.all.label') }}
            </v-btn>
          </div>
          <v-alert v-if="!rowsByType[tab.key].length" type="info" variant="tonal" density="compact">
            {{ t('prov.quote.empty') }}
          </v-alert>
          <!-- Bulk-action bar. Appears only when something is selected
               on the current tab — keeps the toolbar quiet otherwise. -->
          <v-slide-y-transition>
            <v-toolbar v-if="selectedByType[tab.key]?.length" density="compact" color="primary" rounded class="mb-2">
              <v-toolbar-title>
                {{ selectedByType[tab.key].length }} {{ t('common.selected') }}
              </v-toolbar-title>
              <v-spacer />
              <v-btn variant="elevated" color="error" prepend-icon="mdi-delete" @click="askDeleteBulk(tab.key)">
                {{ t('common.delete') }}
              </v-btn>
            </v-toolbar>
          </v-slide-y-transition>

          <LigojDataTable
            v-if="rowsByType[tab.key].length"
            v-model="selectedByType[tab.key]"
            show-select
            :filename="`prov-${tab.key}.csv`"
            :headers="headersByType[tab.key]"
            :items="filteredRowsByType[tab.key]"
            :items-per-page="-1"
            hide-default-footer
            density="compact"
            item-value="id"
          >
            <template #item.name="{ item }">
              <span>{{ item.name }}</span>
              <!-- Tags inherited from the legacy `conf.tags` map. Each
                   tag carries an optional value, rendered as
                   `name:value` when present. The lookup map is
                   case-folded once in `tagsByTypeAndId` so we never
                   re-create the lookup key per cell. -->
              <span v-if="tagsFor(tab.key, item.id).length" class="d-inline-flex flex-wrap ga-1 ml-1">
                <v-chip v-for="tag in tagsFor(tab.key, item.id)" :key="tag.name" size="x-small" variant="tonal">
                  {{ tag.value ? `${tag.name}:${tag.value}` : tag.name }}
                </v-chip>
              </span>
            </template>
            <template #item.cpu="{ item }">
              <ResourceMicroBar v-if="cpuMax(tab.key)" :value="item.cpu ?? item.price?.type?.cpu" :max="cpuMax(tab.key)"
                :label="formatCpu(item.cpu ?? item.price?.type?.cpu)" color="rgb(var(--v-theme-primary))" />
              <template v-else>{{ formatCpu(item.cpu ?? item.price?.type?.cpu) }}</template>
            </template>
            <template #item.ram="{ item }">
              <ResourceMicroBar v-if="ramMax(tab.key)" :value="(item.ram ?? item.price?.type?.ram)" :max="ramMax(tab.key)"
                :label="formatRam(item.ram ?? item.price?.type?.ram)" color="rgb(var(--v-theme-success))" />
              <template v-else>{{ formatRam(item.ram ?? item.price?.type?.ram) }}</template>
            </template>
            <template #item.size="{ item }">{{ formatStorage(item.size) }}</template>
            <template #item.cost="{ item }">
              <span v-if="viewMode === 'co2'">{{ formatCo2(item.co2 ?? item.maxCo2) }}</span>
              <span v-else>{{ formatCost(item.cost, config.currency) }}</span>
            </template>
            <template #item.os="{ item }">{{ item.os || item.price?.os || '' }}</template>
            <template #item.engine="{ item }">{{ item.engine || item.price?.engine || '' }}</template>
            <template #item.type="{ item }">{{ item.price?.type?.name || '' }}</template>
            <template #item.location="{ item }">{{ item.location?.name || item.price?.location?.name || '' }}</template>
            <template #item.level="{ item }">{{ item.level || item.price?.level || '' }}</template>
            <template #item.seats="{ item }">{{ item.seats ?? item.price?.seats ?? '' }}</template>
            <template #item.attachedTo="{ item }">
              <span v-if="attachedLabel(item)" class="text-caption text-medium-emphasis">
                {{ attachedLabel(item) }}
              </span>
            </template>
            <template #item.actions="{ item }">
              <v-btn icon size="small" variant="text" :title="t('common.edit')" @click="openResourceEdit(tab.key, item)">
                <v-icon size="small">mdi-pencil</v-icon>
              </v-btn>
              <v-btn icon size="small" variant="text" :title="t('prov.quote.duplicate')"
                @click="openResourceDuplicate(tab.key, item)">
                <v-icon size="small">mdi-content-duplicate</v-icon>
              </v-btn>
              <v-btn icon size="small" variant="text" color="error" :title="t('common.delete')"
                @click="askDeleteRow(tab.key, item)">
                <v-icon size="small">mdi-delete</v-icon>
              </v-btn>
            </template>
          </LigojDataTable>
        </v-window-item>
      </v-window>
    </template>

    <!-- Edit quote. Sources for location / usage / budget / optimizer
         come from the loaded config (no extra REST call needed); the
         backend stores them by NAME so item-value is 'name'. -->
    <v-dialog v-model="editDialog" max-width="780" scrollable>
      <v-card>
        <v-card-title>{{ t('prov.quote.edit') }}</v-card-title>
        <v-card-text>
          <v-form ref="formRef" @submit.prevent="saveEdit">
            <v-row density="comfortable">
              <v-col cols="12" md="6">
                <v-text-field v-model="editForm.name" :label="t('prov.quote.name')" :rules="REQUIRED_RULES" maxlength="50"
                  variant="outlined" density="compact" autofocus />
              </v-col>
              <v-col cols="12" md="6">
                <v-autocomplete v-model="editForm.location" :items="config?.locations || []" item-title="name" item-value="name"
                  :label="t('prov.quote.cols.location')" variant="outlined" density="compact" clearable />
              </v-col>
              <v-col cols="12">
                <v-text-field v-model="editForm.description" :label="t('prov.quote.description')" maxlength="250"
                  variant="outlined" density="compact" />
              </v-col>
              <v-col cols="12" md="6">
                <v-autocomplete v-model="editForm.usage" :items="config?.usages || []" item-title="name" item-value="name"
                  :label="t('prov.quote.fields.usage')" variant="outlined" density="compact" clearable />
              </v-col>
              <v-col cols="12" md="6">
                <v-autocomplete v-model="editForm.budget" :items="config?.budgets || []" item-title="name" item-value="name"
                  :label="t('prov.quote.fields.budget')" variant="outlined" density="compact" clearable />
              </v-col>
              <v-col cols="12" md="6">
                <v-autocomplete v-model="editForm.optimizer" :items="config?.optimizers || []" item-title="name" item-value="name"
                  :label="t('prov.quote.fields.optimizer')" variant="outlined" density="compact" clearable />
              </v-col>
              <v-col cols="12" md="6">
                <v-select v-model="editForm.reservationMode" :items="reservationOptions"
                  :label="t('prov.quote.fields.reservationMode')" variant="outlined" density="compact" />
              </v-col>
              <v-col cols="12" md="6">
                <v-select v-model="editForm.physical" :items="physicalOptions"
                  :label="t('prov.quote.fields.physical')" variant="outlined" density="compact" clearable />
              </v-col>
              <v-col cols="12" md="6">
                <div class="text-caption text-medium-emphasis mb-1">
                  {{ t('prov.quote.fields.ramAdjustedRate') }} ({{ editForm.ramAdjustedRate }}%)
                </div>
                <v-slider v-model="editForm.ramAdjustedRate" :min="50" :max="200" :step="5" thumb-label hide-details />
              </v-col>
            </v-row>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="editDialog = false">{{ t('common.cancel') }}</v-btn>
          <v-btn color="primary" variant="elevated" :loading="saving" @click="saveEdit">
            <v-icon start>mdi-content-save</v-icon>
            {{ t('common.save') }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <LigojConfirmDialog
      v-model="deleteRowDialog"
      :title="t('prov.quote.delete.row.title')"
      :confirm-label="t('common.delete')"
      confirm-color="error"
      :loading="deleting"
      @confirm="confirmDeleteRow"
    >
      {{ t('prov.quote.delete.row.body', { name: deleteRowTarget?.row?.name || `#${deleteRowTarget?.row?.id}` }) }}
    </LigojConfirmDialog>

    <!-- Compute dialog handles instance / container / function / database;
         storage and support have their own modals because their lookup
         and save shapes are too different to share a form. -->
    <ComputeEditDialog
      v-model="computeDialog"
      :type="editType && COMPUTE_TYPES.has(editType) ? editType : 'instance'"
      :subscription-id="subscriptionId"
      :config="config"
      :resource="editTarget"
      @saved="onResourceSaved"
      @tags-changed="onResourceSaved"
    />
    <StorageEditDialog
      v-model="storageDialog"
      :subscription-id="subscriptionId"
      :config="config"
      :resource="editTarget"
      @saved="onResourceSaved"
      @tags-changed="onResourceSaved"
    />
    <SupportEditDialog
      v-model="supportDialog"
      :subscription-id="subscriptionId"
      :config="config"
      :resource="editTarget"
      @saved="onResourceSaved"
      @tags-changed="onResourceSaved"
    />
    <InstanceImportDialog v-model="importDialog" :subscription-id="subscriptionId" @saved="onResourceSaved" />

    <LigojConfirmDialog
      v-model="deleteAllDialog"
      :title="t('prov.quote.delete.all.title', { type: deleteAllType ? tabLabel(deleteAllType) : '' })"
      :confirm-label="t('prov.quote.delete.all.label')"
      confirm-color="error"
      :loading="deleting"
      @confirm="confirmDeleteAll"
    >
      {{ t('prov.quote.delete.all.body', { type: deleteAllType ? tabLabel(deleteAllType) : '', count: deleteAllType ? rowsByType[deleteAllType].length : 0 }) }}
    </LigojConfirmDialog>

    <LigojConfirmDialog
      v-model="deleteBulkDialog"
      :title="t('prov.quote.delete.bulk.title')"
      :confirm-label="t('common.delete')"
      confirm-color="error"
      :loading="deleting"
      @confirm="confirmDeleteBulk"
    >
      {{ t('prov.quote.delete.bulk.body', { type: deleteBulkType ? tabLabel(deleteBulkType) : '', count: deleteBulkType ? selectedByType[deleteBulkType].length : 0 }) }}
    </LigojConfirmDialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  useApi,
  useAppStore,
  useErrorStore,
  useI18nStore,
  LigojConfirmDialog,
  LigojDataTable,
  NodeIcon,
  APP_BASE,
} from '@ligoj/host'
import {
  formatCo2,
  formatCost,
  formatCostRange,
  formatCpu,
  formatRam,
  formatStorage,
  scaleCost,
  COST_PERIODS,
  rowMatches,
  maxOfField,
  TAB_TYPES,
} from '../quoteFormatters.js'
import QuoteBreakdown from './QuoteBreakdown.vue'
import ComputeEditDialog from './ComputeEditDialog.vue'
import StorageEditDialog from './StorageEditDialog.vue'
import SupportEditDialog from './SupportEditDialog.vue'
import InstanceImportDialog from './InstanceImportDialog.vue'
import ResourceMicroBar from './ResourceMicroBar.vue'

const route = useRoute()
const api = useApi()
const app = useAppStore()
const errorStore = useErrorStore()
const i18n = useI18nStore()
const t = i18n.t

const loading = ref(false)
const refreshing = ref(false)
const refreshingPrices = ref(false)
const error = ref(null)

/* Cost period selector — persisted so the user's preference survives
 * a reload. The scaling math lives in `scaleCost` (quoteFormatters)
 * so it's covered by unit tests. */
const COST_PERIOD_KEY = 'ligoj-prov-quote-cost-period'
function readPersistedCostPeriod() {
  if (typeof localStorage === 'undefined') return 'month'
  const stored = localStorage.getItem(COST_PERIOD_KEY)
  return COST_PERIODS.includes(stored) ? stored : 'month'
}
const costPeriod = ref(readPersistedCostPeriod())
watch(costPeriod, (v) => {
  if (typeof localStorage !== 'undefined' && COST_PERIODS.includes(v)) {
    localStorage.setItem(COST_PERIOD_KEY, v)
  }
})

function scaledCost(cost) {
  return scaleCost(cost, costPeriod.value)
}
// `config` is the inner quote (the `configuration` block of the API
// response). Top-level fields from the response (subscription id,
// project, node) live in `meta` so the header can render the provider
// icon without a second round-trip.
const config = ref(null)
const meta = ref(null)
/* Active tab persisted in localStorage so reloading or following an
 * external link to this view keeps the user where they were. */
const ACTIVE_TAB_STORAGE_KEY = 'ligoj-prov-quote-active-tab'
const VALID_TAB_KEYS = new Set(TAB_TYPES.map((t) => t.key))
function readPersistedTab() {
  if (typeof localStorage === 'undefined') return 'instance'
  const stored = localStorage.getItem(ACTIVE_TAB_STORAGE_KEY)
  return VALID_TAB_KEYS.has(stored) ? stored : 'instance'
}
const activeTab = ref(readPersistedTab())
watch(activeTab, (v) => {
  if (typeof localStorage !== 'undefined' && VALID_TAB_KEYS.has(v)) {
    localStorage.setItem(ACTIVE_TAB_STORAGE_KEY, v)
  }
})

/* ---------- View mode (cost ↔ CO₂) ----------
 * Persisted in localStorage so the choice survives reloads — matches
 * the legacy `SETTINGS_OPTIMIZER_VIEW` key. */
const VIEW_MODE_STORAGE_KEY = 'ligoj-prov-quote-view-mode'
const viewMode = ref(
  (typeof localStorage !== 'undefined' && localStorage.getItem(VIEW_MODE_STORAGE_KEY)) || 'cost',
)
watch(viewMode, (v) => {
  if (typeof localStorage !== 'undefined') localStorage.setItem(VIEW_MODE_STORAGE_KEY, v)
})

/* ---------- Per-tab search ----------
 * One debounced query per resource type. Keeping the rendered map
 * shape stable (`{ instance: '', database: '', … }`) means the
 * `<v-text-field>` model never wanders into `undefined`. */
const searchByType = reactive(Object.fromEntries(TAB_TYPES.map((t) => [t.key, ''])))
const SEARCH_DEBOUNCE_MS = 200
const searchTimers = {}
function onSearch(type, value) {
  if (searchTimers[type]) clearTimeout(searchTimers[type])
  searchTimers[type] = setTimeout(() => {
    searchByType[type] = value || ''
  }, SEARCH_DEBOUNCE_MS)
}

// --- Edit-quote dialog state ---
const editDialog = ref(false)
const formRef = ref(null)
const saving = ref(false)
const editForm = reactive({
  name: '',
  description: '',
  // Backend stores these by NAME (location/usage/budget/optimizer) so
  // the autocomplete's item-value is 'name' and the model is a string.
  location: null,
  usage: null,
  budget: null,
  optimizer: null,
  reservationMode: 'reserved',
  physical: null,
  ramAdjustedRate: 100,
})
const rules = {
  required: (v) => (v != null && v !== '') || (t('common.required') || 'Required'),
}
// Stable rule array for the quote-edit modal — Vuetify 4 re-validates
// whenever `:rules` changes by reference, and inline arrays in templates
// cause a recursive-update loop inside transitioned panels.
const REQUIRED_RULES = [rules.required]

const reservationOptions = computed(() => [
  { value: 'reserved', title: t('prov.quote.fields.reservation.reserved') },
  { value: 'max',      title: t('prov.quote.fields.reservation.max') },
])
const physicalOptions = computed(() => [
  { value: true,  title: t('prov.quote.fields.physical.true') },
  { value: false, title: t('prov.quote.fields.physical.false') },
])

// --- Delete dialog state ---
const deleteRowDialog = ref(false)
const deleteRowTarget = ref(null)         // { type, row }
const deleteAllDialog = ref(false)
const deleteAllType = ref(null)            // tab key
const deleteBulkDialog = ref(false)
const deleteBulkType = ref(null)
const deleting = ref(false)

// --- Per-tab selection (drives the bulk-delete toolbar) ---
const selectedByType = reactive(Object.fromEntries(TAB_TYPES.map((t) => [t.key, []])))

// --- Instance CSV import dialog state ---
const importDialog = ref(false)

// --- Per-type create/edit dialog state ---
// Compute types (instance/container/function/database) share
// ComputeEditDialog; storage and support each have their own. A single
// `editType` ref tracks which dialog is currently open so the buttons
// can route the click to the right modal.
const computeDialog = ref(false)
const storageDialog = ref(false)
const supportDialog = ref(false)
const editType = ref(null)
const editTarget = ref(null)

const subscriptionId = computed(() => route.params.subscription)

/**
 * URLs for the three exports the legacy view exposed. The backend uses
 * the trailing path segment as the suggested filename for the CSVs,
 * which is why it's baked into the URL — preserve that contract.
 *
 * For the JSON the URL is the plain configuration endpoint, so the
 * download filename is set with the `download` attribute instead.
 */
const today = computed(() => new Date().toISOString().slice(0, 10))
const exportUrl = computed(() => {
  const id = subscriptionId.value
  const d = today.value
  if (!id) return { inline: '#', split: '#', json: '#' }
  return {
    inline: `${APP_BASE}rest/service/prov/${id}/ligoj-prov-instances-inline-storage-${id}-${d}.csv`,
    split:  `${APP_BASE}rest/service/prov/${id}/ligoj-prov-split-${id}-${d}.csv`,
    json:   `${APP_BASE}rest/subscription/${id}/configuration`,
  }
})
const jsonDownloadName = computed(() =>
  subscriptionId.value ? `ligoj-full-${subscriptionId.value}-${today.value}.json` : 'ligoj-full.json',
)

/**
 * Provider node displayed in the header (e.g. `service:prov:aws`).
 * The configuration endpoint already nests the full node chain, so we
 * walk `node.refined` to get to the tool node (`service:prov:<tool>`).
 */
const providerNode = computed(() => meta.value?.node?.refined || null)

/**
 * Rows per type, keyed by tab key. The config payload nests them under
 * plural keys (instances, databases, …) — TAB_TYPES.listField maps the
 * key to the actual array name. Returns `[]` when the field is absent
 * so v-data-table never sees `undefined`.
 */
const rowsByType = computed(() => {
  const out = {}
  for (const tab of TAB_TYPES) {
    out[tab.key] = Array.isArray(config.value?.[tab.listField])
      ? config.value[tab.listField]
      : []
  }
  return out
})

const counts = computed(() => {
  const out = {}
  for (const tab of TAB_TYPES) out[tab.key] = rowsByType.value[tab.key].length
  return out
})

/* `rowMatches` lives in quoteFormatters.js so the predicate is covered
 * by unit tests (and reusable if another view needs a similar filter). */

const filteredRowsByType = computed(() => {
  const out = {}
  for (const tab of TAB_TYPES) {
    const q = searchByType[tab.key] || ''
    const rows = rowsByType.value[tab.key]
    out[tab.key] = q ? rows.filter((r) => rowMatches(r, q)) : rows
  }
  return out
})

/* ---------- Tags ---------- *
 * The configuration's `tags` map is keyed by resource type (the legacy
 * lower-cases it once on load — same trick here) and then by resource
 * id. Each entry is an array of `{ name, value }`. */
const tagsByTypeAndId = computed(() => {
  const src = config.value?.tags
  if (!src || typeof src !== 'object') return {}
  const out = {}
  for (const [type, byId] of Object.entries(src)) {
    out[type.toLowerCase()] = byId || {}
  }
  return out
})

function tagsFor(type, id) {
  const byId = tagsByTypeAndId.value[type]
  if (!byId) return []
  const list = byId[id]
  return Array.isArray(list) ? list : []
}

/* ---------- Per-tab CPU / RAM max for micro-bars ---------- *
 * Storage/support don't carry CPU/RAM, so we early-return 0 to skip
 * the bar entirely. */
const COMPUTE_KEYS = new Set(['instance', 'container', 'function', 'database'])
function cpuMax(type) {
  if (!COMPUTE_KEYS.has(type)) return 0
  return maxOfField(rowsByType.value[type] || [], (r) => r.cpu ?? r.price?.type?.cpu)
}
function ramMax(type) {
  if (!COMPUTE_KEYS.has(type)) return 0
  return maxOfField(rowsByType.value[type] || [], (r) => r.ram ?? r.price?.type?.ram)
}

/**
 * Headers per type. Kept small and read-only for iteration 1; CRUD
 * affordances land in iteration 2 once the modal flow is migrated.
 */
const headersByType = computed(() => {
  const name = { title: t('prov.quote.cols.name'),     key: 'name',     sortable: true }
  const cpu  = { title: t('prov.quote.cols.cpu'),      key: 'cpu',      sortable: true, width: '90px',  align: 'end' }
  const ram  = { title: t('prov.quote.cols.ram'),      key: 'ram',      sortable: true, width: '110px', align: 'end' }
  const type = { title: t('prov.quote.cols.type'),     key: 'type',     sortable: true }
  const loc  = { title: t('prov.quote.cols.location'), key: 'location', sortable: true }
  const cost = { title: t('prov.quote.cols.cost'),     key: 'cost',     sortable: true, width: '140px', align: 'end' }
  // Every tab shows edit + duplicate + delete icons, so size the column accordingly.
  const actions = { title: '', key: 'actions', sortable: false, width: '150px', align: 'center' }
  const compute = [
    name,
    { title: t('prov.quote.cols.quantity'), key: 'minQuantity', sortable: true, width: '70px', align: 'end' },
    cpu, ram, type, loc, cost,
  ]

  return {
    instance:  [...compute.slice(0, 4), { title: t('prov.quote.cols.os'), key: 'os', sortable: true }, ...compute.slice(4), actions],
    container: [...compute, actions],
    function:  [...compute, actions],
    database:  [name, cpu, ram,
      { title: t('prov.quote.cols.engine'), key: 'engine', sortable: true },
      type, loc, cost, actions],
    storage:   [name,
      { title: t('prov.quote.cols.size'), key: 'size', sortable: true, width: '110px', align: 'end' },
      type, loc,
      { title: t('prov.quote.cols.attachedTo'), key: 'attachedTo', sortable: false },
      cost, actions],
    support:   [name,
      { title: t('prov.quote.cols.level'), key: 'level', sortable: true },
      { title: t('prov.quote.cols.seats'), key: 'seats', sortable: true, width: '90px', align: 'end' },
      type, cost, actions],
  }
})

function tabLabel(key) {
  return t(`prov.quote.tabs.${key}`)
}

/**
 * Storage rows reference their host resource via `quoteInstance`,
 * `quoteDatabase`, etc. Display the attachment name when present.
 */
function attachedLabel(storage) {
  return (
    storage?.quoteInstance?.name
    || storage?.quoteDatabase?.name
    || storage?.quoteContainer?.name
    || storage?.quoteFunction?.name
    || ''
  )
}

async function loadConfig() {
  if (!subscriptionId.value) return
  loading.value = true
  error.value = null
  try {
    const data = await api.get(`rest/subscription/${subscriptionId.value}/configuration`)
    if (!data) {
      error.value = t('common.loadFailed') || 'Failed to load quote configuration.'
      return
    }
    // The endpoint returns a wrapper: { subscription, project, node,
    // parameters, configuration: { … the quote … } }. Older shape
    // (legacy plain quote at the root) is handled defensively.
    if (data.configuration) {
      config.value = data.configuration
      meta.value = { subscription: data.subscription, project: data.project, node: data.node, parameters: data.parameters }
    } else {
      config.value = data
      meta.value = null
    }
  } finally {
    loading.value = false
  }
}

async function reload() {
  refreshing.value = true
  try {
    await loadConfig()
  } finally {
    refreshing.value = false
  }
}

/**
 * Calls the legacy `PUT service/prov/<sub>/refresh` which re-runs the
 * price discovery against the current provider catalog. The response
 * is the new aggregate cost. If anything moved we reload the whole
 * configuration to pick up the new per-resource costs; otherwise we
 * just inform the user nothing changed (matches legacy
 * `reloadAsNeed`). Reloading is the simplest path — the response
 * doesn't carry per-resource deltas. */
async function refreshPrices() {
  if (!subscriptionId.value) return
  refreshingPrices.value = true
  try {
    const newCost = await api.put(`rest/service/prov/${subscriptionId.value}/refresh`, null)
    if (newCost == null) return
    const conf = config.value
    const changed =
      !conf?.cost
      || newCost.min !== conf.cost.min
      || newCost.max !== conf.cost.max
      || newCost.unbound !== conf.cost.unbound
    if (changed) {
      errorStore.success(t('prov.quote.refreshPrices.changed'))
      await loadConfig()
    } else {
      errorStore.push({ message: t('prov.quote.refreshPrices.noChange'), status: 0 })
    }
  } finally {
    refreshingPrices.value = false
  }
}

/* ----------------- Edit-quote ---------------- */

function openEdit() {
  const conf = config.value || {}
  editForm.name            = conf.name || ''
  editForm.description     = conf.description || ''
  editForm.location        = conf.location?.name ?? null
  editForm.usage           = conf.usage?.name ?? null
  editForm.budget          = conf.budget?.name ?? null
  editForm.optimizer       = conf.optimizer?.name ?? null
  editForm.reservationMode = conf.reservationMode || 'reserved'
  editForm.physical        = conf.physical ?? null
  editForm.ramAdjustedRate = conf.ramAdjustedRate || 100
  editDialog.value = true
}

/**
 * Saves the quote. The backend PUT expects the FULL quote shape: any
 * field we omit is treated as a reset. So we send the form values plus
 * the current values of fields not exposed in the modal (license /
 * processor / architecture — those need per-tool catalog lookups that
 * aren't migrated yet and stay on iteration 4b+).
 *
 * The dropdown sources in the form bind to a string (the resource's
 * `name`); for `processor` / `architecture` the backend accepts a
 * string id directly. `physical` is a tri-state: `true` / `false` /
 * `null` (no constraint).
 */
async function saveEdit() {
  const { valid } = await formRef.value.validate()
  if (!valid) return
  saving.value = true
  try {
    const conf = config.value || {}
    const payload = {
      name: editForm.name,
      description: editForm.description,
      location: editForm.location,
      usage: editForm.usage,
      budget: editForm.budget,
      optimizer: editForm.optimizer,
      reservationMode: editForm.reservationMode,
      physical: editForm.physical,
      ramAdjustedRate: editForm.ramAdjustedRate,
      // Not in the form yet — keep current values to avoid wiping them.
      license: conf.license ?? null,
      processor: conf.processor?.id ?? conf.processor ?? null,
      architecture: conf.architecture?.id ?? conf.architecture ?? null,
    }
    const result = await api.put(`rest/service/prov/${subscriptionId.value}`, payload)
    if (result === null) return // useApi already surfaced the error
    errorStore.success(t('prov.quote.saved', { name: editForm.name }))
    editDialog.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

/* ----------------- Delete row & delete-all ---------------- */

function askDeleteRow(type, row) {
  deleteRowTarget.value = { type, row }
  deleteRowDialog.value = true
}

async function confirmDeleteRow() {
  const { type, row } = deleteRowTarget.value || {}
  if (!type || !row) return
  deleting.value = true
  try {
    const result = await api.del(`rest/service/prov/${type}/${row.id}`)
    if (result === null) return
    errorStore.success(t('prov.quote.delete.row.done', { name: row.name || `#${row.id}` }))
    deleteRowDialog.value = false
    deleteRowTarget.value = null
    await reload()
  } finally {
    deleting.value = false
  }
}

function askDeleteAll(type) {
  deleteAllType.value = type
  deleteAllDialog.value = true
}

function askDeleteBulk(type) {
  if (!selectedByType[type]?.length) return
  deleteBulkType.value = type
  deleteBulkDialog.value = true
}

/**
 * Fans out per-id DELETEs. The backend has no bulk endpoint for
 * partial subsets, so we serialise the calls one at a time — keeps
 * the error surface clean and lets the host's error store toast any
 * individual failure without aborting the rest.
 */
async function confirmDeleteBulk() {
  const type = deleteBulkType.value
  if (!type) return
  const ids = [...(selectedByType[type] || [])]
  if (ids.length === 0) {
    deleteBulkDialog.value = false
    deleteBulkType.value = null
    return
  }
  deleting.value = true
  try {
    for (const id of ids) {
      await api.del(`rest/service/prov/${type}/${id}`)
    }
    errorStore.success(t('prov.quote.delete.bulk.done', { type: tabLabel(type), count: ids.length }))
    deleteBulkDialog.value = false
    deleteBulkType.value = null
    selectedByType[type] = []
    await reload()
  } finally {
    deleting.value = false
  }
}

/* ----------------- Per-type create / edit ---------------- */

const COMPUTE_TYPES = new Set(['instance', 'container', 'function', 'database'])

function openResourceCreate(type) {
  editType.value = type
  editTarget.value = null
  if (type === 'storage') storageDialog.value = true
  else if (type === 'support') supportDialog.value = true
  else if (COMPUTE_TYPES.has(type)) computeDialog.value = true
}

function openResourceEdit(type, row) {
  editType.value = type
  editTarget.value = row
  if (type === 'storage') storageDialog.value = true
  else if (type === 'support') supportDialog.value = true
  else if (COMPUTE_TYPES.has(type)) computeDialog.value = true
}

/**
 * Opens the create dialog pre-populated from `row`. The dialog
 * detects `id == null` as "create mode" and skips the PUT path, so
 * stripping `id` is enough to turn an edit into a duplicate. Name
 * suffixed with " (copy)" to avoid a duplicate-name validation
 * collision; user can rename freely before saving.
 */
function openResourceDuplicate(type, row) {
  if (!row) return
  editType.value = type
  editTarget.value = { ...row, id: null, name: `${row.name || ''} (copy)`.trim() }
  if (type === 'storage') storageDialog.value = true
  else if (type === 'support') supportDialog.value = true
  else if (COMPUTE_TYPES.has(type)) computeDialog.value = true
}

async function onResourceSaved() {
  await reload()
}

async function confirmDeleteAll() {
  const type = deleteAllType.value
  if (!type) return
  const count = rowsByType.value[type]?.length || 0
  deleting.value = true
  try {
    const result = await api.del(`rest/service/prov/${subscriptionId.value}/${type}`)
    if (result === null) return
    errorStore.success(t('prov.quote.delete.all.done', { type: tabLabel(type), count }))
    deleteAllDialog.value = false
    deleteAllType.value = null
    await reload()
  } finally {
    deleting.value = false
  }
}

function setBreadcrumbs() {
  const id = subscriptionId.value
  app.setBreadcrumbs(
    [
      { title: t('nav.home'), to: '/' },
      { title: t('prov.title') },
      { title: config.value?.name || `#${id}` },
    ],
    { refresh: reload },
  )
}

watch([() => config.value?.name, subscriptionId], setBreadcrumbs)

onMounted(async () => {
  setBreadcrumbs()
  await loadConfig()
  setBreadcrumbs()
})
</script>

<style scoped>
.quote-view {
  padding: 0.5rem;
}
.quote-search {
  max-width: 320px;
}
</style>
