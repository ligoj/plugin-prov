<template>
  <LjDialog v-model="open" :title="t('prov.quote.network.title', { name: resource?.name || '' })" icon="mdi-lan" max-width="960">
    <p class="text-caption text-medium-emphasis mb-3">{{ t('prov.quote.network.help', { name: resource?.name || '' }) }}</p>
    <v-alert v-if="invalid" type="warning" variant="tonal" density="compact" class="mb-3">{{ t('prov.quote.network.invalid') }}</v-alert>

    <section v-for="kind in KINDS" :key="kind" class="net-section">
      <h4 class="net-title">
        <v-icon size="18" class="mr-1">{{ kind === 'inbound' ? 'mdi-arrow-down-bold-box-outline' : 'mdi-arrow-up-bold-box-outline' }}</v-icon>
        {{ t(`prov.quote.network.${kind}`) }}
      </h4>
      <v-table density="compact" class="net-table">
        <thead>
          <tr>
            <th class="net-col-name">{{ t('prov.quote.network.name') }}</th>
            <th class="net-col-peer">{{ t(kind === 'inbound' ? 'prov.quote.network.source' : 'prov.quote.network.target') }}</th>
            <th class="net-col-num">{{ t('prov.quote.network.port') }}</th>
            <th class="net-col-num">{{ t('prov.quote.network.rate') }}</th>
            <th class="net-col-num">{{ t('prov.quote.network.throughput') }}</th>
            <th class="net-col-icon" />
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in rows[kind]" :key="i">
            <td><v-text-field v-model="row.name" variant="outlined" density="compact" hide-details maxlength="100" /></td>
            <td>
              <LigojAutocomplete v-model="row.peer" :items="peers" item-title="name" item-value="key" variant="outlined" density="compact" hide-details
                :placeholder="t('prov.quote.network.noPeer')">
                <template #item="{ props: itemProps, item }">
                  <v-list-item v-bind="itemProps" :prepend-icon="item.raw.icon" />
                </template>
                <template #selection="{ item }">
                  <v-icon size="16" class="mr-1">{{ item.raw.icon }}</v-icon>{{ item.raw.name }}
                </template>
              </LigojAutocomplete>
            </td>
            <td><v-text-field v-model.number="row.port" type="number" min="1" max="65535" variant="outlined" density="compact" hide-details /></td>
            <td><v-text-field v-model.number="row.rate" type="number" min="0" variant="outlined" density="compact" hide-details /></td>
            <td><v-text-field v-model.number="row.throughput" type="number" min="0" variant="outlined" density="compact" hide-details /></td>
            <td>
              <v-btn icon size="x-small" variant="text" :aria-label="t('common.delete')" @click="removeRow(kind, i)"><v-icon size="16">mdi-delete-outline</v-icon></v-btn>
            </td>
          </tr>
          <tr v-if="!rows[kind].length">
            <td colspan="6" class="text-caption text-medium-emphasis">{{ t('prov.quote.network.empty') }}</td>
          </tr>
        </tbody>
      </v-table>
      <LjButton variant="ghost" icon="mdi-plus" :icon-size="16" class="mt-1" @click="addRow(kind)">{{ t('prov.quote.network.add') }}</LjButton>
    </section>

    <template #footer>
      <LjButton variant="ghost" @click="open = false">{{ t('common.cancel') }}</LjButton>
      <LjButton icon="mdi-content-save" :loading="saving" @click="save">{{ t('common.save') }}</LjButton>
    </template>
  </LjDialog>
</template>

<script setup>
/*
 * Network links of one quote resource (Vue port of the legacy
 * `network.js` popup): the inbound links (a source resource of the quote →
 * this resource) and the outbound ones (this resource → a target resource),
 * each with an optional name, a required port, and optional rate /
 * throughput figures. Peers are the network-capable resources of the quote
 * (instances, databases, containers, functions, and the storages whose type
 * supports network). Saving replaces every link of the resource through
 * `PUT rest/service/prov/{subscription}/network/{TYPE}/{id}` and the caller
 * reloads the configuration.
 */
import { ref, computed, watch } from 'vue'
import { useApi, useI18nStore, APP_BASE, LjDialog, LjButton, LigojAutocomplete } from '@ligoj/host'
import { TAB_TYPES, NETWORK_TYPES } from '../quoteFormatters.js'
import { resourceTypeName } from '../compareApi.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** The live quote configuration (`instances`, `databases`, ..., `networks`). */
  config: { type: Object, default: null },
  subscriptionId: { type: [String, Number], default: null },
  /** Tab key of the edited resource (`instance`, `database`, ...). */
  resourceType: { type: String, default: '' },
  /** The edited resource row. */
  resource: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const api = useApi()
const t = useI18nStore().t

const KINDS = ['inbound', 'outbound']
const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const saving = ref(false)
const invalid = ref(false)
const inbound = ref([])
const outbound = ref([])
const rows = { inbound, outbound }

/** Backend `ResourceType` name of the edited resource. */
const TYPE = computed(() => resourceTypeName(props.resourceType))
const peerKey = (type, id) => `${type}#${id}`

/** Network-capable resources of the quote, but the edited one. */
const peers = computed(() => {
  const out = []
  for (const tab of TAB_TYPES) {
    if (!NETWORK_TYPES.has(tab.key)) continue
    const type = resourceTypeName(tab.key)
    for (const r of props.config?.[tab.listField] || []) {
      if (tab.key === 'storage' && !r.price?.type?.network) continue
      if (type === TYPE.value && r.id === props.resource?.id) continue
      out.push({ key: peerKey(type, r.id), id: r.id, type, name: r.name, icon: tab.icon })
    }
  }
  return out
})

const numOrNull = (v) => (v === '' || v == null || Number.isNaN(Number(v)) ? null : Number(v))
const toRow = (link, peerType, peerId) => ({
  name: link.name || null,
  peer: peerKey(peerType, peerId),
  port: numOrNull(link.port),
  rate: numOrNull(link.rate),
  throughput: numOrNull(link.throughput),
})

/** Split the quote links related to the edited resource into the two tables. */
function load() {
  const id = props.resource?.id
  const type = TYPE.value
  const links = props.config?.networks || []
  inbound.value = links.filter((l) => l.targetType === type && l.target === id).map((l) => toRow(l, l.sourceType, l.source))
  outbound.value = links.filter((l) => l.sourceType === type && l.source === id).map((l) => toRow(l, l.targetType, l.target))
  invalid.value = false
}

watch(() => [props.modelValue, props.resource, props.resourceType], () => { if (props.modelValue) load() }, { immediate: true })

function addRow(kind) {
  rows[kind].value.push({ name: null, peer: null, port: null, rate: null, throughput: null })
}
function removeRow(kind, index) {
  rows[kind].value.splice(index, 1)
}

const validPort = (p) => Number.isInteger(p) && p >= 1 && p <= 65535

/** Rows → `NetworkVo` payload (`inbound`, `peer` + `peerType`), or null when a row is invalid. */
function payload() {
  const out = []
  for (const kind of KINDS) {
    for (const row of rows[kind].value) {
      const [peerType, peerId] = String(row.peer || '').split('#')
      const port = numOrNull(row.port)
      if (!peerType || !peerId || !validPort(port)) return null
      out.push({
        inbound: kind === 'inbound',
        name: row.name || null,
        port,
        rate: numOrNull(row.rate),
        throughput: numOrNull(row.throughput),
        peer: Number(peerId),
        peerType,
      })
    }
  }
  return out
}

async function save() {
  const io = payload()
  invalid.value = io === null
  if (!io) return
  saving.value = true
  try {
    const url = `${APP_BASE}rest/service/prov/${props.subscriptionId}/network/${TYPE.value}/${props.resource?.id}`
    const res = await api.put(url, io, { raw: true })
    if (res?.ok !== false) {
      emit('saved')
      open.value = false
    }
  } finally {
    saving.value = false
  }
}

defineExpose({ inbound, outbound, peers, addRow, removeRow, save })
</script>

<style scoped>
.net-section + .net-section { margin-top: 18px; }
.net-title {
  display: flex;
  align-items: center;
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 700;
}
.net-table :deep(td) { padding: 4px 6px; vertical-align: middle; }
.net-table :deep(th) { white-space: nowrap; }
.net-col-name { width: 22%; }
.net-col-peer { width: 34%; }
.net-col-num { width: 12%; }
.net-col-icon { width: 40px; }
</style>
