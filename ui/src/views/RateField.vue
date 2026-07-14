<template>
  <!-- Rate/latency selector (Rate.java enum). Same behaviour as the plain
       <v-select> it replaces, plus a star icon (glyph + colour) beside the
       selected value and every option. Pass-through attrs cover label, rules,
       cols, etc. -->
  <v-select
    v-bind="$attrs"
    :model-value="modelValue"
    :items="RATE_OPTIONS"
    variant="outlined"
    density="compact"
    clearable
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <template #selection="{ item }">
      <span class="rate-selection">
        <RateIcon :rate="item" class="mr-2" />{{ t(rateLabelKey(item)) }}
      </span>
    </template>
    <template #item="{ props: itemProps, item }">
      <v-list-item v-bind="itemProps" :title="t(rateLabelKey(item))">
        <template #prepend><RateIcon :rate="item" /></template>
      </v-list-item>
    </template>
  </v-select>
</template>

<script setup>
import RateIcon from './RateIcon.vue'
import { useI18nStore } from '@ligoj/host'
import { RATE_OPTIONS, rateLabelKey } from '../rateCatalog.js'

defineOptions({ inheritAttrs: false })

defineProps({
  modelValue: { type: String, default: null },
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18nStore()
</script>

<style scoped>
.rate-selection {
  display: inline-flex;
  align-items: center;
  white-space: nowrap;
}
</style>
