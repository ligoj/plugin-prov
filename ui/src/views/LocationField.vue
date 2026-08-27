<template>
  <!-- Location picker: shows a flag + localized country name for the selected
       value and every option, and searches across the location code name,
       country name, country code (A2/M49), continent and region. The model
       value stays the location `name` (the code), matching the REST payloads. -->
  <LigojAutocomplete
    v-bind="$attrs"
    :model-value="modelValue"
    :items="items"
    item-title="name"
    item-value="name"
    :custom-filter="filter"
    variant="outlined"
    density="compact"
    clearable
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <!-- Forward the caller slots (e.g. `append-inner` help icon), except the
         two rendered by this component. -->
    <template v-for="slot in forwardedSlots" :key="slot" #[slot]="slotProps">
      <slot :name="slot" v-bind="slotProps ?? {}" />
    </template>
    <template #item="{ props: itemProps, item }">
      <v-list-item v-bind="itemProps">
        <template #title>
          <LocationLabel :location="item.raw || item" show-code :tooltip="false" />
        </template>
      </v-list-item>
    </template>
    <template #selection="{ item }">
      <LocationLabel :location="item.raw || item" :tooltip="false" />
    </template>
  </LigojAutocomplete>
</template>

<script setup>
import { computed, useSlots } from 'vue'
import { useI18nStore, LigojAutocomplete } from '@ligoj/host'
import LocationLabel from './LocationLabel.vue'
import { locationMatches } from '../locationCatalog.js'

defineOptions({ inheritAttrs: false })

defineProps({
  modelValue: { type: [String, Number, Object], default: null },
  /** ProvLocation objects (e.g. `config.locations`). */
  items: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])

const i18n = useI18nStore()
const slots = useSlots()
const OWN_SLOTS = new Set(['item', 'selection'])
const forwardedSlots = computed(() => Object.keys(slots).filter((name) => !OWN_SLOTS.has(name)))

// Vuetify custom-filter: (itemText, query, internalItem) => boolean. The
// internal item carries the source location on `.raw`; fall back to the item
// itself in case a raw object is passed.
function filter(_value, query, item) {
  return locationMatches(item?.raw || item, query, i18n.locale)
}

defineExpose({ filter })
</script>
