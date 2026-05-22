<template>
  <v-dialog :model-value="modelValue" max-width="640" scrollable @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title>{{ t('prov.quote.import.title') }}</v-card-title>
      <v-card-text>
        <p class="text-body-2 mb-2">{{ t('prov.quote.import.intro') }}</p>

        <v-expansion-panels variant="accordion" class="mb-3">
          <v-expansion-panel :title="t('prov.quote.import.headers.title')" eager>
            <template #text>
              <p class="text-caption mb-1">{{ t('prov.quote.import.headers.implicit') }}</p>
              <pre class="csv-hint mb-2">name;cpu;ram;os;disk</pre>
              <p class="text-caption mb-1">{{ t('prov.quote.import.headers.valid') }}</p>
              <pre class="csv-hint">name;cpu;ram;constant;os;disk;latency;optimized;type;internet;maxVariableCost;ephemeral;maxQuantity;minQuantity;location;usage;budget;optimizer;tags;processor;physical</pre>
            </template>
          </v-expansion-panel>
        </v-expansion-panels>

        <v-form ref="formRef" @submit.prevent="upload">
          <v-row density="comfortable">
            <v-col cols="12">
              <v-file-input v-model="form.file" :label="t('prov.quote.import.file')" :rules="FILE_RULES" accept=".csv,text/csv"
                variant="outlined" density="compact" prepend-icon="" prepend-inner-icon="mdi-file-delimited" show-size />
            </v-col>
            <v-col cols="6" md="3">
              <v-text-field v-model="form.separator" :label="t('prov.quote.import.separator')" :rules="REQUIRED_RULES"
                variant="outlined" density="compact" maxlength="1" />
            </v-col>
            <v-col cols="6" md="3">
              <v-text-field v-model="form.encoding" :label="t('prov.quote.import.encoding')" :placeholder="t('prov.quote.import.encodingHint')"
                variant="outlined" density="compact" clearable />
            </v-col>
            <v-col cols="12" md="6">
              <v-select v-model="form.mergeUpload" :items="MERGE_OPTIONS" :label="t('prov.quote.import.merge')"
                variant="outlined" density="compact" />
            </v-col>
            <v-col cols="12" md="6">
              <v-select v-model="form.memoryUnit" :items="MEMORY_UNIT_OPTIONS" :label="t('prov.quote.import.memoryUnit')"
                variant="outlined" density="compact" />
            </v-col>
            <v-col cols="12" md="6">
              <v-switch v-model="form.headersIncluded" :label="t('prov.quote.import.headersIncluded')" color="primary"
                density="compact" hide-details />
            </v-col>
            <v-col cols="12" md="6">
              <v-switch v-model="form.errorContinue" :label="t('prov.quote.import.errorContinue')" color="primary"
                density="compact" hide-details />
            </v-col>
          </v-row>

          <v-alert v-if="result" type="success" variant="tonal" density="compact" class="mt-3">
            {{ t('prov.quote.import.done') }}
          </v-alert>
          <v-alert v-if="errorMsg" type="warning" variant="tonal" density="compact" class="mt-3">
            {{ errorMsg }}
          </v-alert>
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</v-btn>
        <v-btn color="primary" variant="elevated" :loading="uploading" :disabled="!form.file" @click="upload">
          <v-icon start>mdi-upload</v-icon>
          {{ t('prov.quote.import.upload') }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { useApi, useI18nStore } from '@ligoj/host'

/**
 * Ports the legacy `popup-prov-instance-import` (`webjars/service/prov/prov.html`):
 * a CSV instance bulk-import dialog. Only the essentials are wired up —
 * usage/optimizer/budget defaults can still be applied per row via the
 * CSV itself (the backend respects per-row overrides).
 *
 * Endpoint: POST rest/service/prov/<sub>/upload (multipart).
 *
 * @emits saved On a successful upload — parent should reload the
 *              quote configuration to pick up the new resources.
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  subscriptionId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const api = useApi()
const { t } = useI18nStore()

const formRef = ref(null)
const uploading = ref(false)
const errorMsg = ref(null)
const result = ref(false)

const form = reactive({
  file: null,
  separator: ';',
  encoding: '',
  mergeUpload: 'UPDATE',
  memoryUnit: 1024,
  headersIncluded: true,
  errorContinue: true,
})

const required = (v) => (v != null && v !== '') || (t('common.required') || 'Required')
const fileRule = (v) =>
  (v && (Array.isArray(v) ? v.length > 0 : !!v))
  || (t('common.required') || 'Required')
const REQUIRED_RULES = [required]
const FILE_RULES = [fileRule]

const MERGE_OPTIONS = computed(() => [
  { value: 'UPDATE', title: t('prov.quote.import.merge.update') },
  { value: 'KEEP',   title: t('prov.quote.import.merge.keep') },
  { value: 'INSERT', title: t('prov.quote.import.merge.insert') },
])
const MEMORY_UNIT_OPTIONS = computed(() => [
  { value: 1,    title: t('prov.quote.import.memoryUnit.mb') },
  { value: 1024, title: t('prov.quote.import.memoryUnit.gb') },
])

/** Reset state when the dialog opens. */
watch(() => props.modelValue, (open) => {
  if (!open) return
  Object.assign(form, {
    file: null, separator: ';', encoding: '',
    mergeUpload: 'UPDATE', memoryUnit: 1024,
    headersIncluded: true, errorContinue: true,
  })
  errorMsg.value = null
  result.value = false
})

async function upload() {
  const { valid } = await formRef.value.validate()
  if (!valid || !props.subscriptionId) return
  const file = Array.isArray(form.file) ? form.file[0] : form.file
  if (!file) return
  uploading.value = true
  errorMsg.value = null
  result.value = false
  try {
    const fd = new FormData()
    fd.append('csv-file', file)
    fd.append('separator', form.separator)
    if (form.encoding) fd.append('encoding', form.encoding)
    fd.append('mergeUpload', form.mergeUpload)
    fd.append('memoryUnit', String(form.memoryUnit))
    fd.append('headers-included', String(form.headersIncluded))
    fd.append('errorContinue', String(form.errorContinue))
    const resp = await api.upload(`rest/service/prov/${props.subscriptionId}/upload`, fd)
    if (resp == null) {
      errorMsg.value = t('prov.quote.import.failed')
      return
    }
    result.value = true
    emit('saved')
    // Close after a short pause so the success alert is visible.
    setTimeout(() => emit('update:modelValue', false), 800)
  } catch (err) {
    errorMsg.value = err?.message || t('prov.quote.import.failed')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.csv-hint {
  background: rgba(var(--v-theme-on-surface), 0.04);
  padding: 0.3rem 0.5rem;
  border-radius: 4px;
  font-size: 0.75rem;
  white-space: pre-wrap;
  overflow-x: auto;
}
</style>
