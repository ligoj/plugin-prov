<template>
  <LjDialog v-model="open" :title="t('prov.quote.network.title', { name: resource?.name || '' })" icon="mdi-lan" max-width="960">
    <p class="text-caption text-medium-emphasis mb-2">{{ t('prov.quote.network.help', { name: resource?.name || '' }) }}</p>
    <v-alert v-if="invalid" type="warning" variant="tonal" density="compact" class="mb-3">{{ t('prov.quote.network.invalid') }}</v-alert>

    <!-- One tab per direction; the badge counts the links of the direction. -->
    <v-tabs v-model="tab" density="compact" color="primary" class="net-tabs">
      <v-tab v-for="kind in KINDS" :key="kind" :value="kind" class="net-tab-btn">
        <v-icon start size="18">{{ kind === 'inbound' ? 'mdi-arrow-down-bold-box-outline' : 'mdi-arrow-up-bold-box-outline' }}</v-icon>
        {{ t(`prov.quote.network.${kind}`) }}
        <v-chip size="x-small" variant="tonal" class="ml-2">{{ rows[kind].length }}</v-chip>
      </v-tab>
    </v-tabs>
    <v-window v-model="tab" class="mt-3">
      <v-window-item v-for="kind in KINDS" :key="kind" :value="kind">
        <NetworkLinksTab v-model="rows[kind]" :kind="kind" :peers="peers" />
      </v-window-item>
    </v-window>

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
 * each with an optional name, an optional port, an optional workload
 * frequency (seconds, continuous by default) and an optional throughput. Peers are the network-capable resources of the quote
 * (instances, databases, containers, functions, and the storages whose type
 * supports network). Saving replaces every link of the resource through
 * `PUT rest/service/prov/{subscription}/network/{TYPE}/{id}` and the caller
 * reloads the configuration.
 */
import { ref, reactive, computed, watch, toRef } from 'vue'
import { useApi, useI18nStore, APP_BASE, LjDialog, LjButton } from '@ligoj/host'
import { TAB_TYPES, NETWORK_TYPES } from '../quoteFormatters.js'
import { resourceTypeName } from '../compareApi.js'
import NetworkLinksTab from './NetworkLinksTab.vue'
import { emptyRow, peerKey, parsePeerKey, normalizeRate, resourceTags } from '../networkLinks.js'

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

// Outgoing links first: it is the usual editing direction
const KINDS = ['outbound', 'inbound']
const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const saving = ref(false)
const invalid = ref(false)
const tab = ref('outbound')
// Rows per direction (reactive object, so the template and the tabs' v-model see the arrays)
const rows = reactive({ inbound: [], outbound: [] })

/** Backend `ResourceType` name of the edited resource. */
const TYPE = computed(() => resourceTypeName(props.resourceType))

/**
 * Network-capable resources of the quote, with their tags. The edited
 * resource is kept and flagged `current` so it stands out in the picker.
 */
const peers = computed(() => {
  const out = []
  for (const tab of TAB_TYPES) {
    if (!NETWORK_TYPES.has(tab.key)) continue
    const type = resourceTypeName(tab.key)
    for (const r of props.config?.[tab.listField] || []) {
      if (tab.key === 'storage' && !r.price?.type?.network) continue
      out.push({
        key: peerKey(type, r.id),
        id: r.id,
        type,
        name: r.name,
        icon: tab.icon,
        tags: resourceTags(props.config, type, r.id),
        current: type === TYPE.value && r.id === props.resource?.id,
      })
    }
  }
  return out
})

const numOrNull = (v) => (v === '' || v == null || Number.isNaN(Number(v)) ? null : Number(v))
const toRow = (link, peerType, peerId) => ({
  name: link.name || null,
  peer: peerKey(peerType, peerId),
  port: numOrNull(link.port),
  rate: normalizeRate(link.rate) ?? 0,
  throughput: numOrNull(link.throughput),
})

/** Backend `ResourceType` name whatever the JSON casing (the REST JSON serializes enums in lower case). */
const typeName = (v) => String(v || '').toUpperCase()

/** Split the quote links related to the edited resource into the two tables. */
function load() {
  const id = props.resource?.id
  const type = TYPE.value
  const links = props.config?.networks || []
  rows.inbound = links.filter((l) => typeName(l.targetType) === type && l.target === id).map((l) => toRow(l, typeName(l.sourceType), l.source))
  rows.outbound = links.filter((l) => typeName(l.sourceType) === type && l.source === id).map((l) => toRow(l, typeName(l.targetType), l.target))
  invalid.value = false
  tab.value = 'outbound'
}

watch(() => [props.modelValue, props.resource, props.resourceType], () => { if (props.modelValue) load() }, { immediate: true })

function addRow(kind) {
  rows[kind].push(emptyRow())
}
function removeRow(kind, index) {
  rows[kind].splice(index, 1)
}

// The port is optional (links imported by name have none); when set it must be a TCP/UDP port
const validPort = (p) => p == null || (Number.isInteger(p) && p >= 1 && p <= 65535)

/**
 * Rows → `NetworkVo` payload (`inbound`, `peer` + `peerType`), or null when a
 * row is invalid — the invalid rows are flagged (`row.invalid`) and the tab
 * holding the first one is shown so the user sees what blocks the save.
 */
function payload() {
  const out = []
  let firstInvalid = null
  for (const kind of KINDS) {
    for (const row of rows[kind]) {
      const peer = parsePeerKey(row.peer)
      const port = numOrNull(row.port)
      row.invalid = !peer || !validPort(port)
      if (row.invalid) {
        firstInvalid ??= kind
        continue
      }
      out.push({
        inbound: kind === 'inbound',
        name: row.name || null,
        port,
        rate: normalizeRate(row.rate),
        throughput: numOrNull(row.throughput),
        peer: peer.id,
        peerType: peer.type,
      })
    }
  }
  if (firstInvalid) {
    tab.value = firstInvalid
    return null
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

defineExpose({ inbound: toRef(rows, 'inbound'), outbound: toRef(rows, 'outbound'), peers, tab, addRow, removeRow, save })
</script>

<style scoped>
.net-tab-btn { text-transform: none; letter-spacing: 0; }
</style>
