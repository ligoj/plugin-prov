<template>
  <div>
    <div class="d-flex flex-wrap align-center mb-4 ga-2">
      <v-spacer />
      <v-text-field v-model="dt.search.value" prepend-inner-icon="mdi-magnify" :label="t('common.search')" variant="outlined" density="compact" hide-details class="search-field"
        @update:model-value="onSearch" />
      <v-btn color="primary" prepend-icon="mdi-plus" @click="openCreate">
        {{ t('currency.new') }}
      </v-btn>
    </div>

    <v-alert v-if="dt.error.value" type="warning" variant="tonal" class="mb-4">
      {{ dt.error.value }}
    </v-alert>

    <v-skeleton-loader v-if="dt.loading.value && dt.items.value.length === 0" type="table-heading, table-row@5" class="mb-4" />

    <LigojDataTableServer filename="currencies.csv" :fetch-all="dt.loadAll" v-if="!dt.error.value" v-show="dt.items.value.length > 0 || !dt.loading.value"
      :headers="headers" :items="dt.items.value" :items-length="dt.totalItems.value" :loading="dt.loading.value" item-value="id" hover
      v-model:items-per-page="itemsPerPage" @update:options="loadData" @click:row="(_, { item }) => openEdit(item)">
      <!-- "Number of quotes using this currency" is a long label, so the
           header shows an icon only; the full text is the tooltip. The
           column stays sortable — the slot toggles sort on click and
           renders the sort indicator, mirroring LigojDataTable's own
           tooltip-header markup. The header `title` is kept (empty cell
           text aside) so CSV export still carries a meaningful label. -->
      <template #header.nbQuotes="{ column, getSortIcon, toggleSort }">
        <span class="currency-quotes-header" @click="column.sortable && toggleSort?.(column)">
          <v-icon size="small">mdi-file-document-multiple-outline</v-icon>
          <v-icon v-if="column.sortable && getSortIcon" :icon="getSortIcon(column)" size="x-small" class="ml-1" />
          <v-tooltip activator="parent" location="top" :text="t('currency.quotes')" />
        </span>
      </template>
      <template #item.rate="{ item }">
        {{ formatRate(item.rate) }}
      </template>
      <template #item.actions="{ item }">
        <v-btn icon size="small" variant="text" @click.stop="openEdit(item)">
          <v-icon size="small">mdi-pencil</v-icon>
          <v-tooltip activator="parent" location="top" :text="t('common.edit')" />
        </v-btn>
        <v-btn v-if="!item.nbQuotes" icon size="small" variant="text" color="error" @click.stop="startDelete(item)">
          <v-icon size="small">mdi-delete</v-icon>
          <v-tooltip activator="parent" location="top" :text="t('common.delete')" />
        </v-btn>
      </template>
    </LigojDataTableServer>

    <!-- Create / edit dialog. Mirrors the legacy currency.html modal but
         driven by Vuetify state. v-dialog is not persistent (host-wide
         rule) so ESC and scrim-click cancel without saving. -->
    <v-dialog v-model="editDialog" max-width="560">
      <v-card>
        <v-card-title>
          {{ form.id ? t('currency.edit') : t('currency.new') }}
        </v-card-title>
        <v-card-text>
          <v-alert v-if="form.id && form.nbQuotes" type="warning" variant="tonal" density="compact" class="mb-4">
            {{ t('currency.editingUsed') }}
          </v-alert>
          <v-form ref="formRef" @submit.prevent="save">
            <v-text-field v-model="form.name" prepend-inner-icon="mdi-cash-multiple" :label="t('currency.name')" :rules="[rules.required]" variant="outlined" class="mb-2" autofocus />
            <v-textarea v-model="form.description" prepend-inner-icon="mdi-text-long" :label="t('currency.description')" variant="outlined" rows="3" class="mb-2" />
            <v-text-field v-model="form.unit" prepend-inner-icon="mdi-tag-outline" :label="t('currency.unit')" :rules="[rules.required]" variant="outlined" class="mb-2" />
            <v-text-field v-model.number="form.rate" prepend-inner-icon="mdi-currency-usd" :label="t('currency.rate')" :rules="[rules.required, rules.positive]" type="number" step="0.01" variant="outlined" />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="editDialog = false">{{ t('common.cancel') }}</v-btn>
          <v-btn color="primary" variant="elevated" :loading="saving" @click="save">
            <v-icon start>mdi-content-save</v-icon>
            {{ form.id ? t('common.save') : t('common.create') }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <LigojConfirmDialog
      v-model="deleteDialog"
      :title="t('currency.deleteTitle')"
      :confirm-label="t('common.delete')"
      confirm-color="error"
      :loading="deleting"
      @confirm="confirmDelete"
    >
      {{ t('currency.deleteConfirmBefore') }}<strong class="text-error">{{ deleteTarget?.name }}</strong>{{ t('currency.deleteConfirmAfter') }}
    </LigojConfirmDialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  useApi,
  useAppStore,
  useDataTable,
  useErrorStore,
  useI18nStore,
  LigojConfirmDialog,
  LigojDataTableServer,
} from '@ligoj/host'

const api = useApi()
const appStore = useAppStore()
const errorStore = useErrorStore()
const i18n = useI18nStore()
const t = i18n.t

const dt = useDataTable('service/prov/currency', { defaultSort: 'name' })
const itemsPerPage = ref(25)
let searchTimeout = null
let lastOptions = {}

const editDialog = ref(false)
const formRef = ref(null)
const saving = ref(false)
const form = ref({ id: null, name: '', description: '', unit: '', rate: 1, nbQuotes: 0 })

const deleteDialog = ref(false)
const deleteTarget = ref(null)
const deleting = ref(false)

const rules = {
  required: v => (v !== null && v !== undefined && v !== '') || t('common.required'),
  positive: v => (typeof v === 'number' && v > 0) || t('common.positive'),
}

const headers = computed(() => [
  { title: t('currency.name'), key: 'name', sortable: true },
  { title: t('currency.description'), key: 'description', sortable: false },
  { title: t('currency.unit'), key: 'unit', sortable: true, width: '80px' },
  { title: t('currency.rate'), key: 'rate', sortable: true, width: '100px' },
  { title: t('currency.quotes'), key: 'nbQuotes', sortable: true, width: '100px' },
  { title: '', key: 'actions', sortable: false, width: '120px', align: 'center' },
])

function formatRate(rate) {
  if (rate === null || rate === undefined) return ''
  return Number(rate).toLocaleString(i18n.locale, { maximumFractionDigits: 4 })
}

function loadData(options) {
  lastOptions = options
  dt.load(options)
}

function onSearch() {
  clearTimeout(searchTimeout)
  searchTimeout = setTimeout(
    () => dt.load({ page: 1, itemsPerPage: itemsPerPage.value }),
    300,
  )
}

function openCreate() {
  form.value = { id: null, name: '', description: '', unit: '', rate: 1, nbQuotes: 0 }
  editDialog.value = true
}

function openEdit(item) {
  form.value = {
    id: item.id,
    name: item.name ?? '',
    description: item.description ?? '',
    unit: item.unit ?? '',
    rate: item.rate ?? 1,
    nbQuotes: item.nbQuotes ?? 0,
  }
  editDialog.value = true
}

async function save() {
  const validation = await formRef.value?.validate()
  if (validation && !validation.valid) return
  saving.value = true
  try {
    const payload = {
      id: form.value.id,
      name: form.value.name,
      description: form.value.description,
      unit: form.value.unit,
      rate: Number(form.value.rate),
    }
    if (form.value.id) {
      await api.put('rest/service/prov/currency', payload)
      errorStore.success(t('currency.updated', { name: payload.name }))
    } else {
      await api.post('rest/service/prov/currency', payload)
      errorStore.success(t('currency.created', { name: payload.name }))
    }
    editDialog.value = false
    dt.load(lastOptions)
  } finally {
    saving.value = false
  }
}

function startDelete(item) {
  deleteTarget.value = item
  deleteDialog.value = true
}

async function confirmDelete() {
  deleting.value = true
  try {
    await api.del(`rest/service/prov/currency/${deleteTarget.value.id}`)
    errorStore.success(t('currency.deleted', { name: deleteTarget.value.name }))
    deleteDialog.value = false
    deleteTarget.value = null
    dt.load(lastOptions)
  } finally {
    deleting.value = false
  }
}

onMounted(() => {
  appStore.setBreadcrumbs(
    [
      { title: t('nav.home'), to: '/' },
      { title: t('prov.title') },
      { title: t('currency.title') },
    ],
    { refresh: () => dt.load(lastOptions) },
  )
})
</script>

<style scoped>
.search-field {
  min-width: 200px;
  max-width: 300px;
  flex: 1 1 200px;
}
.currency-quotes-header {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
}
</style>
