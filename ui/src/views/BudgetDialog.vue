<template>
  <v-dialog :model-value="modelValue" max-width="480" @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon>mdi-wallet-outline</v-icon>
        {{ form.id ? t('prov.quote.budget.edit') : t('prov.quote.budget.new') }}
      </v-card-title>

      <v-card-text>
        <v-form ref="formRef" @submit.prevent="save">
          <v-text-field v-model="form.name" :label="t('prov.quote.budget.name')" :rules="REQUIRED_RULES"
            maxlength="50" variant="outlined" density="compact" autofocus />

          <v-text-field v-model.number="form.initialCost" type="number" min="0" step="1"
            :label="t('prov.quote.budget.initialCost')" :suffix="currencyUnit" variant="outlined" density="compact">
            <template #append-inner>
              <HelpTip :text="t('prov.quote.budget.initialCostHelp')" />
            </template>
          </v-text-field>

          <div v-if="Number(budget?.requiredInitialCost) > 0" class="text-caption text-medium-emphasis">
            {{ t('prov.quote.budget.requiredInitialCost') }}: {{ formatCost(budget.requiredInitialCost, currency) }}
          </div>
        </v-form>
      </v-card-text>

      <v-card-actions>
        <v-btn v-if="form.id != null" variant="text" color="error" :loading="deleting" @click="remove">
          {{ t('common.delete') }}
        </v-btn>
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</v-btn>
        <v-btn color="primary" variant="elevated" :loading="saving" @click="save">{{ t('common.save') }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
// Create / edit / delete a budget profile (ProvBudget) via ProvBudgetResource.
import { ref, reactive, computed, watch } from 'vue'
import { useApi, useI18nStore, APP_BASE } from '@ligoj/host'
import { formatCost } from '../quoteFormatters.js'
import { budgetPayload } from '../budgetCatalog.js'
import HelpTip from './HelpTip.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** Budget object to edit, or null to create a new one. */
  budget: { type: Object, default: null },
  subscriptionId: { type: [Number, String], default: null },
  currency: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved', 'changed'])

const api = useApi()
const { t } = useI18nStore()

const REQUIRED_RULES = [(v) => (v != null && v !== '') || (t('common.required') || 'Required')]
const currencyUnit = computed(() => props.currency?.unit || '$')

const blank = () => ({ id: null, name: '', initialCost: 0 })
const form = reactive(blank())
const formRef = ref(null)
const saving = ref(false)
const deleting = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const b = props.budget
    Object.assign(form, blank())
    if (b) Object.assign(form, { id: b.id ?? null, name: b.name ?? '', initialCost: b.initialCost ?? 0 })
  },
  { immediate: true },
)

async function save() {
  const ok = formRef.value ? (await formRef.value.validate()).valid : !!form.name
  if (!ok) return
  saving.value = true
  try {
    const vo = budgetPayload(form)
    const url = `${APP_BASE}rest/service/prov/${props.subscriptionId}/budget`
    const created = form.id == null
    const res = created ? await api.post(url, vo) : await api.put(url, vo)
    if (res === null) return
    emit('saved', vo.name)
    emit('changed')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}

async function remove() {
  if (form.id == null) return
  deleting.value = true
  try {
    const res = await api.del(`${APP_BASE}rest/service/prov/${props.subscriptionId}/budget/${form.id}`)
    if (res === null) return
    emit('changed')
    emit('update:modelValue', false)
  } finally {
    deleting.value = false
  }
}

defineExpose({ form, save, remove })
</script>
