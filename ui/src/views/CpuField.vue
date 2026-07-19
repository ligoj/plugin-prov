<template>
  <!-- CPU capacity input bundled with the workload trigger icon to its right.
       Extra attrs (label, rules, min, step, …) pass through to CapacityField. -->
  <div class="cpu-field">
    <CapacityField
      class="cpu-field-input"
      :model-value="modelValue"
      kind="cpu"
      :provided="provided"
      :unit="unit"
      :explanation="explanation"
      v-bind="$attrs"
      @update:model-value="(v) => emit('update:modelValue', v)"
    />
    <WorkloadIcon
      class="cpu-field-workload"
      :workload="workload"
      :explanation="workloadExplanation"
      @click="emit('edit-workload')"
    />
  </div>
</template>

<script setup>
import CapacityField from './CapacityField.vue'
import WorkloadIcon from './WorkloadIcon.vue'

defineOptions({ inheritAttrs: false })

defineProps({
  /** CPU value (vCPU). */
  modelValue: { type: [Number, String, null], default: null },
  /** vCPU the selected type provides (for the utilisation bar). */
  provided: { type: Number, default: 0 },
  unit: { type: String, default: '' },
  explanation: { type: String, default: '' },
  /** Workload string + its tooltip meaning. */
  workload: { type: String, default: '' },
  workloadExplanation: { type: String, default: '' },
})
const emit = defineEmits(['update:modelValue', 'edit-workload'])
</script>

<style scoped>
.cpu-field {
  display: flex;
  align-items: flex-start;
  gap: 4px;
}
.cpu-field-input {
  flex: 1 1 auto;
  min-width: 0;
}
/* Vertically centre the icon on the input row (density=compact ≈ 40px). */
.cpu-field-workload {
  flex: none;
  margin-top: 9px;
}
</style>
