<template>
  <div class="net-tab" :data-kind="kind">
    <v-table density="compact" class="net-table">
      <thead>
        <tr>
          <th class="net-col-name">{{ t('prov.quote.network.name') }}</th>
          <th class="net-col-peer">{{ t(kind === 'inbound' ? 'prov.quote.network.source' : 'prov.quote.network.target') }}</th>
          <th class="net-col-num">{{ t('prov.quote.network.port') }}</th>
          <th class="net-col-rate">
            {{ t('prov.quote.network.rate') }}
            <v-icon size="14" class="ml-1 net-help">mdi-help-circle-outline</v-icon>
            <v-tooltip activator="parent" location="top" max-width="320" :text="t('prov.quote.network.rateHelp')" />
          </th>
          <th class="net-col-num">
            {{ t('prov.quote.network.throughput') }}
            <v-icon size="14" class="ml-1 net-help">mdi-help-circle-outline</v-icon>
            <v-tooltip activator="parent" location="top" max-width="320" :text="t('prov.quote.network.throughputHelp')" />
          </th>
          <th class="net-col-icon" />
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, i) in modelValue" :key="i" class="net-row" :class="{ 'net-row-invalid': row.invalid }">
          <td><v-text-field v-model="row.name" variant="outlined" density="compact" hide-details maxlength="100" v-bind="fieldAttrs('name', i)" /></td>
          <td>
            <!-- Peer: one of the network-capable resources of the quote (icon + name) -->
            <LigojAutocomplete v-model="row.peer" :items="peers" item-title="name" item-value="key" variant="outlined" density="compact" hide-details
              :placeholder="t('prov.quote.network.noPeer')" class="net-peer" :error="!!row.invalid && !row.peer">
              <!-- Vuetify hands the internal item (`.raw` = the peer) or the peer itself, as in LocationField.
                   Suggestions show the resource tags; the edited resource is marked. -->
              <template #item="{ props: itemProps, item }">
                <v-list-item v-bind="itemProps" :prepend-icon="(item.raw || item).icon" :class="{ 'net-peer-current': (item.raw || item).current }">
                  <template v-if="(item.raw || item).tags.length" #subtitle>
                    <span class="net-peer-tags">
                      <v-chip v-for="tag in (item.raw || item).tags" :key="tag" size="x-small" variant="tonal" class="mr-1">{{ tag }}</v-chip>
                    </span>
                  </template>
                  <template v-if="(item.raw || item).current" #append>
                    <v-chip size="x-small" color="primary" variant="flat" class="net-current-chip">{{ t('prov.quote.network.current') }}</v-chip>
                  </template>
                </v-list-item>
              </template>
              <template #selection="{ item }">
                <v-icon size="16" class="mr-1">{{ (item.raw || item).icon }}</v-icon>{{ (item.raw || item).name }}
                <v-chip v-if="(item.raw || item).current" size="x-small" color="primary" variant="flat" class="ml-1">{{ t('prov.quote.network.current') }}</v-chip>
              </template>
            </LigojAutocomplete>
          </td>
          <td>
            <v-text-field v-model.number="row.port" type="number" min="1" max="65535" variant="outlined" density="compact" hide-details
              :error="!!row.invalid && row.port != null && row.port !== '' && !(row.port >= 1 && row.port <= 65535)" v-bind="fieldAttrs('port', i)" />
          </td>
          <td>
            <!-- Workload frequency: presets in seconds (continuous by default) -->
            <LigojSelect v-model="row.rate" :items="frequencyItems(row.rate, t)" item-title="title" item-value="value"
              variant="outlined" density="compact" hide-details class="net-rate" />
          </td>
          <td>
            <v-text-field v-model.number="row.throughput" type="number" min="0" variant="outlined" density="compact" hide-details
              suffix="KB/s" class="net-throughput" v-bind="fieldAttrs('throughput', i)" />
          </td>
          <td>
            <v-btn icon size="x-small" variant="text" class="net-remove" :aria-label="t('common.delete')" @click="remove(i)">
              <v-icon size="16">mdi-delete-outline</v-icon>
            </v-btn>
          </td>
        </tr>
        <tr v-if="!modelValue.length" class="net-empty">
          <td colspan="6" class="text-caption text-medium-emphasis">{{ t('prov.quote.network.empty') }}</td>
        </tr>
      </tbody>
    </v-table>
    <LjButton variant="ghost" icon="mdi-plus" :icon-size="16" class="mt-2 net-add" @click="add">{{ t('prov.quote.network.add') }}</LjButton>
  </div>
</template>

<script>
// Module-scoped counter → unique field names per tab instance (see LigojAutocomplete)
let tabSeq = 0
</script>

<script setup>
/*
 * One direction of the network links of a resource (a tab of NetworkDialog):
 * the editable rows (name, peer, port, workload frequency, throughput), an
 * add button and a remove button per row. `v-model` is the row list; the rows themselves are
 * edited in place.
 */
import { useI18nStore, LjButton, LigojAutocomplete, LigojSelect } from '@ligoj/host'
import { emptyRow, frequencyItems } from '../networkLinks.js'

const props = defineProps({
  /** The link rows: `[{ name, peer, port, rate, throughput }]`, `peer` being a `TYPE#id` key. */
  modelValue: { type: Array, default: () => [] },
  /** `inbound` (peer is the source) or `outbound` (peer is the target). */
  kind: { type: String, default: 'outbound' },
  /** Selectable peers: `[{ key, id, type, name, icon }]`. */
  peers: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])

const t = useI18nStore().t

// Native browser autofill / field-history never covers these inputs: unique,
// unmatchable name + autocomplete token and the password-manager opt-outs
// (LigojAutocomplete / LigojSelect apply the same recipe to the pickers)
// eslint-disable-next-line no-useless-assignment -- module-level counter, incremented across component instances
const uid = `net-${++tabSeq}`
function fieldAttrs(kind, index) {
  const token = `${uid}-${kind}-${index}`
  return { name: token, autocomplete: token, 'data-1p-ignore': 'true', 'data-lpignore': 'true', 'data-form-type': 'other' }
}

function add() {
  emit('update:modelValue', [...props.modelValue, emptyRow()])
}
function remove(index) {
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))
}
</script>

<style scoped>
.net-table :deep(td) { padding: 4px 6px; vertical-align: middle; }
.net-table :deep(th) { white-space: nowrap; }
.net-col-name { width: 22%; }
.net-col-peer { width: 34%; }
.net-col-num { width: 12%; }
.net-col-rate { width: 16%; }
.net-col-icon { width: 40px; }
.net-help { opacity: .55; vertical-align: middle; }
.net-row-invalid > td { background: rgba(var(--v-theme-error), .06); }
.net-peer-current { font-weight: 700; }
.net-peer-tags { display: inline-flex; flex-wrap: wrap; gap: 2px; }
</style>
