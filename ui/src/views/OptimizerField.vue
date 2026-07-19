<template>
  <div class="optimizer-field">
    <LigojAutocomplete
      class="optimizer-field-input"
      :model-value="modelValue"
      :items="optimizers"
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
          <template #prepend>
            <v-icon size="16">{{ optimizerModeIcon((item.raw || item).mode) }}</v-icon>
          </template>
          <template v-if="optimizerSummary(item.raw || item, t)" #subtitle>
            {{ optimizerSummary(item.raw || item, t) }}
          </template>
        </v-list-item>
      </template>
      <template #selection="{ item }">
        <span class="optimizer-sel">
          <v-icon size="14" class="mr-1">{{ optimizerModeIcon((item.raw || item).mode) }}</v-icon>
          {{ (item.raw || item).name ?? item }}
        </span>
      </template>
    </LigojAutocomplete>

    <v-btn v-if="selectedOptimizer" icon size="small" variant="text" :title="t('prov.quote.optimizer.edit')" @click="editOptimizer">
      <v-icon>mdi-pencil</v-icon>
    </v-btn>
    <v-btn icon size="small" variant="text" :title="t('prov.quote.optimizer.new')" @click="newOptimizer">
      <v-icon>mdi-plus</v-icon>
    </v-btn>

    <OptimizerDialog
      v-model="dialog"
      :optimizer="editing"
      :subscription-id="subscriptionId"
      @saved="(name) => emit('update:modelValue', name)"
      @changed="emit('changed')"
    />
  </div>
</template>

<script setup>
// Optimizer-profile selector: an autocomplete showing each profile's mode
// (cost / CO₂), with pencil/plus actions opening the editor. Model value is the
// optimizer `name`. `changed` bubbles up so the owner can refresh the quote.
import { ref, computed } from 'vue'
import { useI18nStore, LigojAutocomplete } from '@ligoj/host'
import OptimizerDialog from './OptimizerDialog.vue'
import { optimizerSummary, optimizerModeIcon } from '../optimizerCatalog.js'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { type: String, default: null },
  /** ProvOptimizer objects (e.g. config.optimizers). */
  optimizers: { type: Array, default: () => [] },
  subscriptionId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'changed'])

const { t } = useI18nStore()

const dialog = ref(false)
const editing = ref(null)

const selectedOptimizer = computed(() => (props.optimizers || []).find((o) => o?.name === props.modelValue) || null)

function newOptimizer() {
  editing.value = null
  dialog.value = true
}
function editOptimizer() {
  editing.value = selectedOptimizer.value
  dialog.value = true
}
</script>

<style scoped>
.optimizer-field {
  display: flex;
  align-items: center;
  gap: 2px;
}
.optimizer-field-input {
  flex: 1 1 auto;
  min-width: 0;
}
.optimizer-sel {
  display: inline-flex;
  align-items: center;
  min-width: 0;
}
</style>
