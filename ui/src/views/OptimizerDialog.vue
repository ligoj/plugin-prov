<template>
  <v-dialog :model-value="modelValue" max-width="480" @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon>mdi-tune-variant</v-icon>
        {{ form.id ? t('prov.quote.optimizer.edit') : t('prov.quote.optimizer.new') }}
      </v-card-title>

      <v-card-text>
        <v-form ref="formRef" @submit.prevent="save">
          <v-text-field v-model="form.name" :label="t('prov.quote.optimizer.name')" :rules="REQUIRED_RULES"
            maxlength="50" variant="outlined" density="compact" autofocus />

          <div class="d-flex align-center ga-1 mb-1 mt-1">
            <span class="text-caption text-medium-emphasis">{{ t('prov.quote.optimizer.mode') }}</span>
            <HelpTip :text="t('prov.quote.optimizer.modeHelp')" />
          </div>
          <v-btn-toggle v-model="form.mode" mandatory density="comfortable" variant="outlined" divided class="mb-2">
            <v-btn v-for="m in OPTIMIZER_MODES" :key="m.value" :value="m.value" size="small">
              <v-icon start>{{ m.icon }}</v-icon>{{ t(m.labelKey) }}
            </v-btn>
          </v-btn-toggle>

          <div class="d-flex align-center">
            <v-checkbox v-model="form.p1TypeOnly" :label="t('prov.quote.optimizer.p1TypeOnly')"
              density="compact" hide-details color="primary" />
            <HelpTip :text="t('prov.quote.optimizer.p1TypeOnlyHelp')" />
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
// Create / edit / delete an optimizer profile (ProvOptimizer) via ProvOptimizerResource.
import { ref, reactive, watch } from 'vue'
import { useApi, useI18nStore, APP_BASE } from '@ligoj/host'
import { OPTIMIZER_MODES, optimizerPayload } from '../optimizerCatalog.js'
import HelpTip from './HelpTip.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** Optimizer object to edit, or null to create a new one. */
  optimizer: { type: Object, default: null },
  subscriptionId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'saved', 'changed'])

const api = useApi()
const { t } = useI18nStore()

const REQUIRED_RULES = [(v) => (v != null && v !== '') || (t('common.required') || 'Required')]

const blank = () => ({ id: null, name: '', mode: 'COST', p1TypeOnly: false })
const form = reactive(blank())
const formRef = ref(null)
const saving = ref(false)
const deleting = ref(false)

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    const o = props.optimizer
    Object.assign(form, blank())
    if (o) Object.assign(form, { id: o.id ?? null, name: o.name ?? '', mode: o.mode || 'COST', p1TypeOnly: !!o.p1TypeOnly })
  },
  { immediate: true },
)

async function save() {
  const ok = formRef.value ? (await formRef.value.validate()).valid : !!form.name
  if (!ok) return
  saving.value = true
  try {
    const vo = optimizerPayload(form)
    const url = `${APP_BASE}rest/service/prov/${props.subscriptionId}/optimizer`
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
    const res = await api.del(`${APP_BASE}rest/service/prov/${props.subscriptionId}/optimizer/${form.id}`)
    if (res === null) return
    emit('changed')
    emit('update:modelValue', false)
  } finally {
    deleting.value = false
  }
}

defineExpose({ form, save, remove })
</script>
