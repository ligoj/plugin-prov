<template>
  <div class="quote-view lj-surface">
    <v-skeleton-loader v-if="loading && !config" type="article, table" />

    <v-alert v-if="error" type="warning" variant="tonal" class="mb-4">{{ error }}</v-alert>

    <template v-if="config">
      <!-- Header — provider tile + quote identity on the left, the
           total-cost key figure + grouped tools on the right. Same
           actions as before (edit / period / refresh / refresh-prices /
           exports), only regrouped. -->
      <header class="q-head mb-4">
        <div class="q-head-id">
          <span v-if="providerNode" class="q-provider">
            <NodeIcon :node="providerNode" />
          </span>
          <div class="q-head-txt">
            <div class="d-flex align-center ga-1">
              <h1 class="q-name">{{ config.name || subscriptionId }}</h1>
              <v-btn icon size="small" variant="text" class="q-edit" :title="t('prov.quote.edit')" @click="openEdit">
                <v-icon size="small">mdi-pencil</v-icon>
              </v-btn>
            </div>
            <span v-if="config.description" class="q-desc" :title="config.description">{{ config.description }}</span>
          </div>
        </div>
        <v-spacer />
        <div class="q-cost" :class="{ 'q-cost--filtered': anyFilterActive }">
          <span class="q-cost-label">
            {{ t('prov.quote.totalCost') }}
            <v-icon v-if="anyFilterActive" size="12" class="q-cost-filter-ic" :title="t('prov.quote.totalFiltered')">mdi-filter-variant</v-icon>
          </span>
          <span class="q-cost-value">
            {{ formatCostRange(scaledCost(displayedQuoteCost), config.currency) }}
            <span class="q-cost-suffix">/{{ t(`prov.quote.period.${costPeriod}Suffix`) }}</span>
          </span>
        </div>
        <div class="q-tools">
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
          <v-btn icon size="small" variant="text" :loading="refreshingPrices" :title="t('prov.quote.refreshPrices')" @click="refreshPrices">
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
              <v-list-item :href="exportUrl.split" prepend-icon="mdi-file-table-outline" :title="t('prov.quote.exports.split')" />
              <v-list-item :href="exportUrl.json" :download="jsonDownloadName" prepend-icon="mdi-code-json" :title="t('prov.quote.exports.json')" />
            </v-list>
          </v-menu>
        </div>
      </header>

      <!-- Cost-breakdown card — modern ring + a row of stat tiles
           (already-computed aggregates). The donut renders only when
           something is in the quote (the component itself early-returns
           on zero total). The metric toggle (cost ↔ CO₂) mirrors the
           legacy `optimizer-view-mode` switch — see `viewMode` below. -->
      <v-card variant="flat" class="q-costcard mb-4">
        <v-card-text class="py-3">
          <div class="d-flex align-center justify-space-between flex-wrap ga-2 mb-2">
            <span class="q-card-title">
              {{ viewMode === 'co2' ? t('prov.quote.breakdown.titleCo2') : t('prov.quote.breakdown.title') }}
            </span>
            <v-btn-toggle v-model="viewMode" mandatory density="compact" variant="outlined" divided class="q-mode">
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
          <div class="q-costcard-body">
            <QuoteBreakdown :config="filteredConfig" :mode="viewMode" />
            <div class="q-stats">
              <div v-for="s in statTiles" :key="s.key" class="q-stat">
                <span class="q-stat-ic"><v-icon size="18">{{ s.icon }}</v-icon></span>
                <div class="q-stat-txt">
                  <div class="q-stat-num">{{ s.value }}</div>
                  <div class="q-stat-label">{{ s.label }}</div>
                </div>
              </div>
            </div>
          </div>
        </v-card-text>
      </v-card>

      <!-- Tabs — one per resource type. The chip shows the (filtered)
           resource count; the total is only appended when a search is
           actively hiding rows (e.g. "3/12"), never as a redundant "12/12". -->
      <v-tabs v-model="activeTab" density="compact" show-arrows class="q-tabs mb-3" color="primary">
        <v-tab v-for="t in TAB_TYPES" :key="t.key" :value="t.key">
          <v-icon :icon="t.icon" start size="small" />
          {{ tabLabel(t.key) }}
          <v-chip v-if="counts[t.key]" size="x-small" variant="tonal" :color="isTabFiltered(t.key) ? 'primary' : undefined"
            class="ml-2 q-count" :class="{ 'q-count-filtered': isTabFiltered(t.key) }">{{ tabCountLabel(t.key) }}</v-chip>
        </v-tab>
      </v-tabs>

      <v-window v-model="activeTab">
        <v-window-item v-for="tab in TAB_TYPES" :key="tab.key" :value="tab.key">
          <div class="q-toolbar d-flex align-center mb-3 ga-2 flex-wrap">
            <v-spacer />
            <!-- Debounced text filter. Matches the legacy
                 `.subscribe-configuration-prov-search` input; one
                 query per tab so a search on Instances doesn't bleed
                 into Storage. -->
            <v-text-field v-if="rowsByType[tab.key].length" :model-value="searchByType[tab.key]" :label="t('common.search')" prepend-inner-icon="mdi-magnify" density="compact" hide-details
              variant="outlined" clearable class="quote-search" @update:model-value="(v) => onSearch(tab.key, v)" />
            <!-- Per-type create button. ComputeEditDialog covers all 4
                 compute types; storage + support each have their own. -->
            <v-btn size="small" color="primary" variant="elevated" prepend-icon="mdi-plus" @click="openResourceCreate(tab.key)">
              {{ t(`prov.quote.${tab.key}.new`) }}
            </v-btn>
            <!-- Instance-only: CSV bulk import. Stays out of the other
                 tabs since the import endpoint is instance-specific
                 (per the legacy `popup-prov-instance-import`). -->
            <v-btn v-if="tab.key === 'instance'" size="small" variant="outlined" prepend-icon="mdi-file-upload" @click="importDialog = true">
              {{ t('prov.quote.import.title') }}
            </v-btn>
            <!-- "Delete all" and the column-visibility selector now live in
                 the table's header tools cog (standard LigojDataTable
                 features), keeping this toolbar to create/import/search. -->
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

          <LigojDataTable v-if="rowsByType[tab.key].length" v-model="selectedByType[tab.key]" show-select hover :filename="`prov-${tab.key}.csv`" :headers="headersByType[tab.key]"
            :pinned-columns="PINNED_COLUMNS" :columns-storage-key="`ligoj-prov-quote-cols-${tab.key}`" :columns-label="t('prov.quote.columns')"
            :tool-actions="tableToolActions" :items="filteredRowsByType[tab.key]" v-model:items-per-page="itemsPerPage" :items-per-page-options="ITEMS_PER_PAGE_OPTIONS"
            density="comfortable" item-value="id" class="q-table"
            @click:row="(e, { item }) => onRowClick(tab.key, e, item)" @tool-action="(key) => onToolAction(tab.key, key)">
            <template #item.name="{ item }">
              <span class="q-cell-name">{{ item.name }}</span>
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
              <ResourceMicroBar v-if="cpuMax(tab.key)" :value="item.cpu ?? item.price?.type?.cpu" :max="cpuMax(tab.key)" :label="formatCpu(item.cpu ?? item.price?.type?.cpu)"
                color="rgb(var(--v-theme-primary))" />
              <template v-else>{{ formatCpu(item.cpu ?? item.price?.type?.cpu) }}</template>
            </template>
            <template #item.ram="{ item }">
              <ResourceMicroBar v-if="ramMax(tab.key)" :value="(item.ram ?? item.price?.type?.ram)" :max="ramMax(tab.key)" :label="formatRam(item.ram ?? item.price?.type?.ram)"
                color="rgb(var(--v-theme-success))" />
              <template v-else>{{ formatRam(item.ram ?? item.price?.type?.ram) }}</template>
            </template>
            <template #item.size="{ item }">{{ formatStorage(item.size) }}</template>
            <template #item.cost="{ item }">
              <span v-if="viewMode === 'co2'" class="q-cell-cost">{{ formatCo2(item.co2 ?? item.maxCo2) }}</span>
              <span v-else class="q-cell-cost">{{ formatCost(item.cost, config.currency) }}</span>
            </template>
            <template #item.os="{ item }">{{ item.os || item.price?.os || '' }}</template>
            <template #item.engine="{ item }">{{ item.engine || item.price?.engine || '' }}</template>
            <template #item.type="{ item }">
              <span v-if="item.price?.type?.name" class="q-type">{{ item.price.type.name }}</span>
            </template>
            <template #item.location="{ item }">
              <span v-if="item.location?.name || item.price?.location?.name" class="q-loc">
                <v-icon size="12">mdi-map-marker-outline</v-icon>{{ item.location?.name || item.price?.location?.name }}
              </span>
            </template>
            <template #item.level="{ item }">{{ item.level || item.price?.level || '' }}</template>
            <template #item.seats="{ item }">{{ item.seats ?? item.price?.seats ?? '' }}</template>
            <template #item.attachedTo="{ item }">
              <span v-if="attachedLabel(item)" class="text-caption text-medium-emphasis">
                {{ attachedLabel(item) }}
              </span>
            </template>
            <!-- All row actions grouped behind a single cog, mirroring the
                 header tools menu (standard RowActionsMenu). Edit is also
                 reachable by clicking anywhere on the row. -->
            <template #item.actions="{ item }">
              <RowActionsMenu :actions="rowActions" :label="t('common.actions')" @select="(key) => onRowAction(tab.key, item, key)" />
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
                <v-text-field v-model="editForm.name" :label="t('prov.quote.name')" :rules="REQUIRED_RULES" maxlength="50" variant="outlined" density="compact" autofocus />
              </v-col>
              <v-col cols="12" md="6">
                <LigojAutocomplete v-model="editForm.location" :items="config?.locations || []" item-title="name" item-value="name" :label="t('prov.quote.cols.location')" variant="outlined"
                  density="compact" clearable />
              </v-col>
              <v-col cols="12">
                <v-text-field v-model="editForm.description" :label="t('prov.quote.description')" maxlength="250" variant="outlined" density="compact" />
              </v-col>
              <v-col cols="12" md="6">
                <LigojAutocomplete v-model="editForm.usage" :items="config?.usages || []" item-title="name" item-value="name" :label="t('prov.quote.fields.usage')" variant="outlined" density="compact"
                  clearable />
              </v-col>
              <v-col cols="12" md="6">
                <LigojAutocomplete v-model="editForm.budget" :items="config?.budgets || []" item-title="name" item-value="name" :label="t('prov.quote.fields.budget')" variant="outlined" density="compact"
                  clearable />
              </v-col>
              <v-col cols="12" md="6">
                <LigojAutocomplete v-model="editForm.optimizer" :items="config?.optimizers || []" item-title="name" item-value="name" :label="t('prov.quote.fields.optimizer')" variant="outlined"
                  density="compact" clearable />
              </v-col>
              <v-col cols="12" md="6">
                <v-select v-model="editForm.reservationMode" :items="reservationOptions" :label="t('prov.quote.fields.reservationMode')" variant="outlined" density="compact" />
              </v-col>
              <v-col cols="12" md="6">
                <v-select v-model="editForm.physical" :items="physicalOptions" :label="t('prov.quote.fields.physical')" variant="outlined" density="compact" clearable />
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

    <LigojConfirmDialog v-model="deleteRowDialog" :title="t('prov.quote.delete.row.title')" :confirm-label="t('common.delete')" confirm-color="error" :loading="deleting" @confirm="confirmDeleteRow">
      {{ t('prov.quote.delete.row.body', { name: deleteRowTarget?.row?.name || `#${deleteRowTarget?.row?.id}` }) }}
    </LigojConfirmDialog>

    <!-- Compute dialog handles instance / container / function / database;
         storage and support have their own modals because their lookup
         and save shapes are too different to share a form. -->
    <ComputeEditDialog v-model="computeDialog" :type="editType && COMPUTE_TYPES.has(editType) ? editType : 'instance'" :subscription-id="subscriptionId" :config="config" :resource="editTarget"
      @saved="onResourceSaved" @tags-changed="onResourceSaved" />
    <StorageEditDialog v-model="storageDialog" :subscription-id="subscriptionId" :config="config" :resource="editTarget" @saved="onResourceSaved" @tags-changed="onResourceSaved" />
    <SupportEditDialog v-model="supportDialog" :subscription-id="subscriptionId" :config="config" :resource="editTarget" @saved="onResourceSaved" @tags-changed="onResourceSaved" />
    <InstanceImportDialog v-model="importDialog" :subscription-id="subscriptionId" @saved="onResourceSaved" />

    <LigojConfirmDialog v-model="deleteAllDialog" :title="t('prov.quote.delete.all.title', { type: deleteAllType ? tabLabel(deleteAllType) : '' })" :confirm-label="t('prov.quote.delete.all.label')"
      confirm-color="error" :loading="deleting" @confirm="confirmDeleteAll">
      {{ t('prov.quote.delete.all.body', { type: deleteAllType ? tabLabel(deleteAllType) : '', count: deleteAllType ? rowsByType[deleteAllType].length : 0 }) }}
    </LigojConfirmDialog>

    <LigojConfirmDialog v-model="deleteBulkDialog" :title="t('prov.quote.delete.bulk.title')" :confirm-label="t('common.delete')" confirm-color="error" :loading="deleting"
      @confirm="confirmDeleteBulk">
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
  LigojAutocomplete,
  RowActionsMenu,
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
  sumCostRange,
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

/* ---------- Items-per-page (client-side pagination) ----------
 * Each tab's data table now paginates in the browser to keep render
 * cost bounded even for quotes with hundreds of resources. The
 * default of 15 mirrors Ligoj's other paginated views; `-1` shows
 * everything for users who want the legacy "all on one page" view.
 * Persisted globally — switching tabs keeps the same page size. */
const ITEMS_PER_PAGE_KEY = 'ligoj-prov-quote-items-per-page'
const ITEMS_PER_PAGE_OPTIONS = [
  { value: 15, title: '15' },
  { value: 30, title: '30' },
  { value: 50, title: '50' },
  { value: 100, title: '100' },
  { value: -1, title: '∞' },
]
const VALID_PAGE_SIZES = new Set(ITEMS_PER_PAGE_OPTIONS.map((o) => o.value))
function readPersistedItemsPerPage() {
  if (typeof localStorage === 'undefined') return 15
  const stored = Number(localStorage.getItem(ITEMS_PER_PAGE_KEY))
  return VALID_PAGE_SIZES.has(stored) ? stored : 15
}
const itemsPerPage = ref(readPersistedItemsPerPage())
watch(itemsPerPage, (v) => {
  if (typeof localStorage !== 'undefined' && VALID_PAGE_SIZES.has(v)) {
    localStorage.setItem(ITEMS_PER_PAGE_KEY, String(v))
  }
})

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
  { value: 'max', title: t('prov.quote.fields.reservation.max') },
])
const physicalOptions = computed(() => [
  { value: true, title: t('prov.quote.fields.physical.true') },
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

/* ----- Column visibility -----
 * Column show/hide is now a standard LigojDataTable feature: the table
 * owns the selector (in its header tools cog) and persists the hidden set
 * per tab under `ligoj-prov-quote-cols-<tab>`. `name` and `actions` stay
 * pinned so the user can always identify a row and act on it. */
const PINNED_COLUMNS = ['name', 'actions']

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
    split: `${APP_BASE}rest/service/prov/${id}/ligoj-prov-split-${id}-${d}.csv`,
    json: `${APP_BASE}rest/subscription/${id}/configuration`,
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

/* ---------- Stat tiles (cost card) ----------
 * Presentation aggregates over the currently VISIBLE rows (so they track
 * the active search alongside the total cost and the donut): instance
 * count plus total vCPU / RAM across the compute types. Same fallback
 * chain as the table cells (own value, else the price type's). */
const statTiles = computed(() => {
  let cpu = 0
  let ram = 0
  for (const key of COMPUTE_KEYS) {
    for (const row of filteredRowsByType.value[key]) {
      cpu += Number(row.cpu ?? row.price?.type?.cpu) || 0
      ram += Number(row.ram ?? row.price?.type?.ram) || 0
    }
  }
  return [
    { key: 'instances', icon: 'mdi-server', label: t('prov.quote.tabs.instance'), value: filteredRowsByType.value.instance.length },
    { key: 'cpu', icon: 'mdi-chip', label: t('prov.quote.cols.cpu'), value: formatCpu(cpu) || '0' },
    { key: 'ram', icon: 'mdi-memory', label: t('prov.quote.cols.ram'), value: formatRam(ram) || '0' },
  ]
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

/* ---------- Filtered totals ----------
 * A search hides rows on one or more tabs. So the header total and the
 * breakdown reflect what the user is actually looking at, we apply the
 * DELTA of the hidden resources' costs to the authoritative aggregate
 * (`config.cost`) rather than recomputing from scratch — support/initial
 * coupling in the backend total is preserved, only the removed rows'
 * direct cost/maxCost is subtracted. */
const anyFilterActive = computed(() => TAB_TYPES.some((tab) => isTabFiltered(tab.key)))

/** Sum of the hidden rows' costs across every tab: `{ min, max }`. */
const filteredDeltaCost = computed(() => {
  let min = 0
  let max = 0
  if (!anyFilterActive.value) return { min, max }
  for (const tab of TAB_TYPES) {
    if (!isTabFiltered(tab.key)) continue
    const visible = new Set(filteredRowsByType.value[tab.key])
    const hidden = rowsByType.value[tab.key].filter((r) => !visible.has(r))
    const d = sumCostRange(hidden)
    min += d.min
    max += d.max
  }
  return { min, max }
})

/**
 * Header total, adjusted for the active filter. Passes `config.cost`
 * through untouched when nothing is filtered; otherwise subtracts the
 * hidden-rows delta from `min`/`max` (leaving a null bound null).
 */
const displayedQuoteCost = computed(() => {
  const base = config.value?.cost
  if (!base || !anyFilterActive.value) return base
  const { min: dMin, max: dMax } = filteredDeltaCost.value
  return {
    ...base,
    // clamp: float drift / support coupling must never yield a negative total.
    min: base.min != null ? Math.max(0, base.min - dMin) : base.min,
    max: base.max != null ? Math.max(0, base.max - dMax) : base.max,
  }
})

/**
 * Config clone whose resource lists hold only the filtered rows, so the
 * cost-breakdown donut/legend recompute over the visible set. Returns the
 * original config (same identity) when no filter is active.
 */
const filteredConfig = computed(() => {
  if (!config.value || !anyFilterActive.value) return config.value
  const clone = { ...config.value }
  for (const tab of TAB_TYPES) {
    clone[tab.listField] = filteredRowsByType.value[tab.key]
  }
  return clone
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
  const name = { title: t('prov.quote.cols.name'), key: 'name', sortable: true }
  const cpu = { title: t('prov.quote.cols.cpu'), key: 'cpu', sortable: true, width: '90px', align: 'end' }
  const ram = { title: t('prov.quote.cols.ram'), key: 'ram', sortable: true, width: '110px', align: 'end' }
  const type = { title: t('prov.quote.cols.type'), key: 'type', sortable: true }
  const loc = { title: t('prov.quote.cols.location'), key: 'location', sortable: true }
  const cost = { title: t('prov.quote.cols.cost'), key: 'cost', sortable: true, width: '140px', align: 'end' }
  // Single per-row cog (RowActionsMenu) + the header tools cog live in
  // this column, so it only needs room for one icon button. `minWidth`
  // keeps Vuetify from collapsing it when other columns claim the space.
  const actions = {
    title: '', key: 'actions', sortable: false, align: 'end',
    width: '72px', minWidth: '72px',
    cellProps: { class: 'actions-cell' },
  }
  const compute = [
    name,
    { title: t('prov.quote.cols.quantity'), key: 'minQuantity', sortable: true, width: '70px', align: 'end' },
    cpu, ram, type, loc, cost,
  ]

  return {
    instance: [...compute.slice(0, 4), { title: t('prov.quote.cols.os'), key: 'os', sortable: true }, ...compute.slice(4), actions],
    container: [...compute, actions],
    function: [...compute, actions],
    database: [name, cpu, ram,
      { title: t('prov.quote.cols.engine'), key: 'engine', sortable: true },
      type, loc, cost, actions],
    storage: [name,
      { title: t('prov.quote.cols.size'), key: 'size', sortable: true, width: '110px', align: 'end' },
      type, loc,
      { title: t('prov.quote.cols.attachedTo'), key: 'attachedTo', sortable: false },
      cost, actions],
    support: [name,
      { title: t('prov.quote.cols.level'), key: 'level', sortable: true },
      { title: t('prov.quote.cols.seats'), key: 'seats', sortable: true, width: '90px', align: 'end' },
      type, cost, actions],
  }
})

function tabLabel(key) {
  return t(`prov.quote.tabs.${key}`)
}

/** True when the search is actively hiding rows in this tab. */
function isTabFiltered(key) {
  return filteredRowsByType.value[key].length !== rowsByType.value[key].length
}

/**
 * Count shown in a tab's chip. Renders the filtered count alone when no
 * search is hiding rows (`12`), and only appends the total when a filter
 * is active (`3/12`) — so an unfiltered tab never shows a redundant
 * `12/12`.
 */
function tabCountLabel(key) {
  const total = rowsByType.value[key].length
  const filtered = filteredRowsByType.value[key].length
  return isTabFiltered(key) ? `${filtered}/${total}` : String(total)
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
  editForm.name = conf.name || ''
  editForm.description = conf.description || ''
  editForm.location = conf.location?.name ?? null
  editForm.usage = conf.usage?.name ?? null
  editForm.budget = conf.budget?.name ?? null
  editForm.optimizer = conf.optimizer?.name ?? null
  editForm.reservationMode = conf.reservationMode || 'reserved'
  editForm.physical = conf.physical ?? null
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

/* ----- Row actions (grouped in the per-row cog) ----- *
 * Same three actions on every row; the labels are reactive to the locale
 * so this is a computed rather than a module constant. */
const rowActions = computed(() => [
  { key: 'edit',      title: t('common.edit'),          icon: 'mdi-pencil' },
  { key: 'duplicate', title: t('prov.quote.duplicate'), icon: 'mdi-content-duplicate' },
  { key: 'delete',    title: t('common.delete'),        icon: 'mdi-delete', color: 'error' },
])

function onRowAction(type, row, key) {
  if (key === 'edit') openResourceEdit(type, row)
  else if (key === 'duplicate') openResourceDuplicate(type, row)
  else if (key === 'delete') askDeleteRow(type, row)
}

/* ----- Table-level actions (header tools cog) ----- *
 * "Delete all" now lives in the table header cog instead of the toolbar. */
const tableToolActions = computed(() => [
  { key: 'delete-all', title: t('prov.quote.delete.all.label'), icon: 'mdi-delete-sweep', color: 'error' },
])

function onToolAction(type, key) {
  if (key === 'delete-all') askDeleteAll(type)
}

/**
 * Row-click opens the editor (replacing the old pencil icon). Clicks that
 * land on an interactive control — the selection checkbox, the row-action
 * cog, links, inputs — are ignored so those keep their own behaviour.
 * `item` is Vuetify 4's raw row (guard the `.raw` shape just in case).
 */
function onRowClick(type, event, item) {
  if (event?.target?.closest?.('button, a, input, .v-selection-control, .no-row-edit')) return
  const row = item?.raw ?? item
  if (row) openResourceEdit(type, row)
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
  /* Passed as a FACTORY so the host can re-run it on a locale change (the crumb
   * titles use `t()`); it also re-reads the latest reactive data each call.
   * Full path: Home → Projects → <project name> → Provisioning → <quote>.
   * The project segment is only emitted once the configuration has landed
   * (read from `meta.value.project`, populated by loadConfig). */
  app.setBreadcrumbs(() => {
    const id = subscriptionId.value
    const project = meta.value?.project
    const crumbs = [
      { title: t('nav.home'), to: '/' },
      { title: t('nav.projects'), to: '/home/project' },
    ]
    if (project?.id) {
      crumbs.push({
        title: project.name || `#${project.id}`,
        to: `/home/project/${project.id}`,
      })
    }
    crumbs.push({ title: t('prov.title') })
    crumbs.push({ title: config.value?.name || `#${id}` })
    return crumbs
  }, { refresh: reload })
}

// Re-run when the quote name, the subscription id, or the parent
// project (id + name) change. The watch fires once for the empty
// initial state and once more after `loadConfig` lands.
watch(
  [() => config.value?.name, subscriptionId, () => meta.value?.project?.id, () => meta.value?.project?.name],
  setBreadcrumbs,
)

onMounted(async () => {
  setBreadcrumbs()
  await loadConfig()
  setBreadcrumbs()
})
</script>

<style scoped>
/* All colours below come from theme tokens only: Vuetify semantic
 * variables (rgb/rgba(var(--v-theme-…))) and the shared `.lj-surface`
 * design variables (--ink / --card / --border / --pill / --accent /
 * --radius / --mono …) set by the host for the 2026 views. */
.quote-view {
  padding: 0.5rem;
}

/* ---------- Header ---------- */
.q-head {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.q-head-id {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.q-provider {
  width: 46px;
  height: 46px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--pill);
  border: var(--border-w) var(--lj-border-style, solid) var(--border-c);
}

.q-provider :deep(img),
.q-provider :deep(.v-icon) {
  max-width: 26px;
  max-height: 26px;
}

.q-name {
  font-size: 22px;
  font-weight: var(--bold);
  color: var(--ink);
  line-height: 1.2;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.q-edit {
  color: var(--ink-3);
}

.q-desc {
  display: inline-block;
  align-self: flex-start;
  margin-top: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-3);
  background: var(--pill);
  border-radius: 999px;
  padding: 2px 10px;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.q-head-txt {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.q-cost {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.q-cost-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--ink-3);
}

/* A search narrows the total: tint the label + value with the accent
 * colour and surface a small filter glyph so the figure reads as a
 * filtered subtotal, not the whole quote. */
.q-cost--filtered .q-cost-label,
.q-cost-filter-ic {
  color: rgb(var(--v-theme-primary));
}

.q-cost-filter-ic {
  margin-left: 3px;
  vertical-align: text-top;
}

.q-cost--filtered .q-cost-value {
  color: rgb(var(--v-theme-primary));
}

.q-cost-value {
  font-size: 25px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  font-family: var(--mono);
  color: var(--ink);
  line-height: 1.25;
  white-space: nowrap;
}

.q-cost-suffix {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-3);
  font-family: var(--font);
}

.q-tools {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px 6px;
  border-radius: 999px;
  border: var(--border-w) var(--lj-border-style, solid) var(--border-c);
  background: var(--card);
}

.q-tools .v-btn {
  color: var(--ink-2);
}

/* ---------- Cost card ---------- */
.q-costcard {
  border-radius: var(--radius);
  border: var(--border-w) var(--lj-border-style, solid) var(--border-c);
  background: var(--card);
  box-shadow: var(--shadow);
}

.q-card-title {
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ink-3);
}

.q-costcard-body {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}

.q-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
  flex: 1;
  min-width: 260px;
}

.q-stat {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  border: var(--border-w) var(--lj-border-style, solid) var(--border-c);
  background: var(--surface);
}

.q-stat-ic {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--pill);
  color: var(--accent);
}

.q-stat-num {
  font-family: var(--mono);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  font-size: 19px;
  line-height: 1.1;
  color: var(--ink);
}

.q-stat-label {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-3);
  margin-top: 2px;
}

/* ---------- Tabs ---------- */
.q-tabs {
  border-bottom: var(--border-w) var(--lj-border-style, solid) var(--border-c);
}

.q-tabs :deep(.v-tab) {
  text-transform: none;
  letter-spacing: 0;
  font-weight: 600;
}

.q-tabs :deep(.v-tab__slider) {
  height: 3px;
  border-radius: 3px 3px 0 0;
}

.q-count {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

/* When a search narrows the tab, the chip switches to the accent colour
 * (via the `primary` chip color) and gains a subtle ring so the filtered
 * "3/12" reads as an active-filter state rather than a plain total. */
.q-count-filtered {
  font-weight: 800;
  box-shadow: 0 0 0 1px rgba(var(--v-theme-primary), 0.45);
}

/* ---------- Per-tab toolbar ---------- */
.quote-search {
  max-width: 300px;
}

.quote-search :deep(.v-field) {
  border-radius: 999px;
}

/* ---------- Table ---------- */
.q-table :deep(.v-table) {
  background: transparent;
}

.q-table :deep(thead th) {
  font-size: 11.5px !important;
  font-weight: 700 !important;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ink-3) !important;
  white-space: nowrap;
}

.q-table :deep(tbody td) {
  font-size: 13.5px;
  color: var(--ink-2);
}

.q-table :deep(tbody tr:hover td) {
  background: var(--hover);
}

/* Numeric columns line up on tabular figures. */
.q-table :deep(td.v-data-table-column--align-end) {
  font-variant-numeric: tabular-nums;
}

.q-cell-name {
  font-weight: 600;
  color: var(--ink);
}

.q-cell-cost {
  font-family: var(--mono);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--ink);
  font-size: 12.5px;
}

.q-type {
  font-family: var(--mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-2);
  background: var(--pill);
  border-radius: var(--radius-sm);
  padding: 2px 8px;
  white-space: nowrap;
}

.q-loc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--ink-2);
  background: var(--pill);
  border-radius: 999px;
  padding: 3px 10px;
  white-space: nowrap;
}

.q-loc :deep(.v-icon) {
  color: var(--ink-3);
}

/* Row-click opens the editor, so make the whole row read as clickable. */
.q-table :deep(tbody tr) {
  cursor: pointer;
}

/* The per-row actions cog stays quiet until the row is hovered or
 * focused. Pointer devices only — touch users keep it always visible. */
@media (hover: hover) {
  .q-table :deep(.actions-cell .v-btn) {
    opacity: 0.35;
    transition: opacity 120ms ease;
  }

  .q-table :deep(tbody tr:hover .actions-cell .v-btn),
  .q-table :deep(tbody tr:focus-within .actions-cell .v-btn) {
    opacity: 1;
  }
}
</style>

<!--
  Unscoped: the v-data-table renders cells via a render function, so
  `<td>` elements live outside this component's scoped class. The
  selector is unique enough not to bleed into other tables.
-->
<style>
.v-data-table td.actions-cell {
  white-space: nowrap;
}
</style>
