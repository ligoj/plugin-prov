<template>
  <!-- A numeric resource input with its utilisation micro-bar bundled
       directly beneath it (tight vertical gap). The bar appears once a
       type capacity is known and its tooltip shows requested / provided
       formatted for the metric. Extra <v-text-field> attributes (label,
       rules, min, step, …) pass straight through. -->
  <div class="capacity-field">
    <v-text-field
      v-bind="$attrs"
      :model-value="modelValue"
      type="number"
      variant="outlined"
      density="compact"
      hide-details="auto"
      @update:model-value="onInput"
    />
    <ResourceMicroBar
      v-if="hasBar"
      :value="barValue"
      :max="provided"
      :tooltip="explanation"
      :format="fmt"
      block
      class="capacity-bar"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ResourceMicroBar from './ResourceMicroBar.vue'
import { formatCpu, formatRam, formatStorage } from '../quoteFormatters.js'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { type: [Number, String, null], default: null },
  /** 'cpu' | 'ram' | 'storage' — drives unit scaling + tooltip formatting. */
  kind: { type: String, default: 'cpu' },
  /**
   * Capacity the selected type provides, in that type's native unit
   * (vCPU for cpu, MB for ram, GB for storage). Falsy → no bar.
   */
  provided: { type: Number, default: 0 },
  /** Unit label appended to cpu values in the tooltip (e.g. "vCPU"). */
  unit: { type: String, default: '' },
  /** Explanation shown under the requested/provided line in the tooltip. */
  explanation: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue'])

// Mirror `v-model.number`: empty → null, numeric string → number.
function onInput(v) {
  if (v === '' || v == null) return emit('update:modelValue', null)
  const n = Number(v)
  emit('update:modelValue', Number.isNaN(n) ? v : n)
}

// RAM is entered in GB but the type capacity is in MB — scale the reserved
// value up to the provided unit before comparing / formatting.
const scale = computed(() => (props.kind === 'ram' ? 1024 : 1))
const barValue = computed(() => (Number(props.modelValue) || 0) * scale.value)
const hasBar = computed(() => Number(props.provided) > 0)

function fmt(n) {
  if (props.kind === 'ram') return formatRam(n)
  if (props.kind === 'storage') return formatStorage(n)
  const s = formatCpu(n)
  return props.unit ? `${s} ${props.unit}` : s
}
</script>

<style scoped>
/* hide-details="auto" already drops the empty message row; the bar then
 * sits directly under the field with only a hairline gap. */
.capacity-bar {
  margin-top: 2px;
}
</style>
