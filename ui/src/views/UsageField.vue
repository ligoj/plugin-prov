<template>
  <div class="usage-field">
    <LigojAutocomplete
      class="usage-field-input"
      :model-value="modelValue"
      :items="usages"
      item-title="name"
      item-value="name"
      variant="outlined"
      density="compact"
      clearable
      v-bind="$attrs"
      hide-details="auto"
      @update:model-value="(v) => emit('update:modelValue', v)"
    >
      <template #item="{ props: itemProps, item }">
        <v-list-item v-bind="itemProps">
          <template v-if="usageSummary(item.raw || item)" #subtitle>{{ usageSummary(item.raw || item) }}</template>
        </v-list-item>
      </template>
      <template #selection="{ item }">
        <span class="usage-sel">
          {{ (item.raw || item).name ?? item }}
          <span v-if="usageSummary(item.raw || item)" class="usage-sel-sub">{{ usageSummary(item.raw || item) }}</span>
        </span>
      </template>
    </LigojAutocomplete>

    <v-btn v-if="selectedUsage" icon size="small" variant="text" :title="t('prov.quote.usage.edit')" @click="editUsage">
      <v-icon>mdi-pencil</v-icon>
    </v-btn>
    <v-btn icon size="small" variant="text" :title="t('prov.quote.usage.new')" @click="newUsage">
      <v-icon>mdi-plus</v-icon>
    </v-btn>

    <UsageDialog
      v-model="dialog"
      :usage="editing"
      :subscription-id="subscriptionId"
      @saved="(name) => emit('update:modelValue', name)"
      @changed="emit('changed')"
    />
  </div>
</template>

<script setup>
// Usage-profile selector: an autocomplete showing each profile's rate +
// commitment, with pencil/plus actions that open the editor dialog. The model
// value stays the usage `name`, matching the REST payloads. `changed` bubbles
// up so the owner can refresh the quote after a create/edit/delete.
import { ref, computed } from 'vue'
import { useI18nStore, LigojAutocomplete } from '@ligoj/host'
import UsageDialog from './UsageDialog.vue'
import { usageSummary } from '../usageCatalog.js'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { type: String, default: null },
  /** ProvUsage objects (e.g. config.usages). */
  usages: { type: Array, default: () => [] },
  subscriptionId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'changed'])

const { t } = useI18nStore()

const dialog = ref(false)
const editing = ref(null)

const selectedUsage = computed(() => (props.usages || []).find((u) => u?.name === props.modelValue) || null)

function newUsage() {
  editing.value = null
  dialog.value = true
}
function editUsage() {
  editing.value = selectedUsage.value
  dialog.value = true
}
</script>

<style scoped>
.usage-field {
  display: flex;
  align-items: center;
  gap: 2px;
}
.usage-field-input {
  flex: 1 1 auto;
  min-width: 0;
}
.usage-sel {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}
.usage-sel-sub {
  font-size: 0.78em;
  color: rgba(var(--v-theme-on-surface), 0.6);
  font-variant-numeric: tabular-nums;
}
</style>
