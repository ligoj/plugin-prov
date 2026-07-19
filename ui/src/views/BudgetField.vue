<template>
  <div class="budget-field">
    <LigojAutocomplete
      class="budget-field-input"
      :model-value="modelValue"
      :items="budgets"
      item-title="name"
      item-value="name"
      variant="outlined"
      density="compact"
      clearable
      :placeholder="placeholderNote"
      persistent-placeholder
      v-bind="$attrs"
      hide-details="auto"
      @update:model-value="(v) => emit('update:modelValue', v)"
    >
      <template #item="{ props: itemProps, item }">
        <v-list-item v-bind="itemProps">
          <template v-if="budgetSummary(item.raw || item, currency)" #subtitle>
            {{ budgetSummary(item.raw || item, currency) }}
          </template>
        </v-list-item>
      </template>
      <template #selection="{ item }">
        <span class="budget-sel">
          {{ (item.raw || item).name ?? item }}
          <span v-if="budgetSummary(item.raw || item, currency)" class="budget-sel-sub">
            {{ budgetSummary(item.raw || item, currency) }}
          </span>
        </span>
      </template>
    </LigojAutocomplete>

    <v-btn v-if="selectedBudget" icon size="small" variant="text" :title="t('prov.quote.budget.edit')" @click="editBudget">
      <v-icon>mdi-pencil</v-icon>
    </v-btn>
    <v-btn icon size="small" variant="text" :title="t('prov.quote.budget.new')" @click="newBudget">
      <v-icon>mdi-plus</v-icon>
    </v-btn>
    <HelpTip :text="t('prov.quote.budget.about')" />

    <BudgetDialog
      v-model="dialog"
      :budget="editing"
      :subscription-id="subscriptionId"
      :currency="currency"
      @saved="(name) => emit('update:modelValue', name)"
      @changed="emit('changed')"
    />
  </div>
</template>

<script setup>
// Budget-profile selector: an autocomplete showing each profile's available
// cash, with pencil/plus actions opening the editor dialog. Model value is the
// budget `name`. `changed` bubbles up so the owner can refresh the quote.
import { ref, computed } from 'vue'
import { useI18nStore, LigojAutocomplete } from '@ligoj/host'
import BudgetDialog from './BudgetDialog.vue'
import HelpTip from './HelpTip.vue'
import { budgetSummary } from '../budgetCatalog.js'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  modelValue: { type: String, default: null },
  /** ProvBudget objects (e.g. config.budgets). */
  budgets: { type: Array, default: () => [] },
  subscriptionId: { type: [Number, String], default: null },
  currency: { type: Object, default: null },
  /** 'config' (quote-wide default) or 'resource' (inherits the quote default). */
  scope: { type: String, default: 'resource' },
  /** The quote's default budget, shown in the placeholder at resource scope. */
  quoteDefault: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'changed'])

const { t } = useI18nStore()

const placeholderNote = computed(() => {
  if (props.scope === 'config') return t('prov.quote.budget.defaultNote')
  const name = props.quoteDefault?.name
  return name ? t('prov.quote.budget.inherit', { name }) : t('prov.quote.budget.defaultNote')
})

const dialog = ref(false)
const editing = ref(null)

const selectedBudget = computed(() => (props.budgets || []).find((b) => b?.name === props.modelValue) || null)

function newBudget() {
  editing.value = null
  dialog.value = true
}
function editBudget() {
  editing.value = selectedBudget.value
  dialog.value = true
}
</script>

<style scoped>
.budget-field {
  display: flex;
  align-items: center;
  gap: 2px;
}
.budget-field-input {
  flex: 1 1 auto;
  min-width: 0;
}
.budget-sel {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
}
.budget-sel-sub {
  font-size: 0.78em;
  color: rgba(var(--v-theme-on-surface), 0.6);
  font-variant-numeric: tabular-nums;
}
</style>
