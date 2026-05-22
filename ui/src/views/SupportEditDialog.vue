<template>
  <v-dialog :model-value="modelValue" max-width="700" scrollable @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title>
        {{ isEdit ? t('prov.quote.support.edit') : t('prov.quote.support.new') }}
      </v-card-title>
      <v-card-text>
        <v-form ref="formRef" @submit.prevent="save">
          <v-row dense>
            <v-col cols="12" md="8">
              <v-text-field v-model="form.name" :label="t('prov.quote.name')" :rules="[required]" maxlength="50"
                variant="outlined" density="compact" autofocus />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field v-model.number="form.seats" :label="t('prov.quote.cols.seats')" type="number" min="0"
                variant="outlined" density="compact" />
            </v-col>
            <v-col cols="12">
              <v-text-field v-model="form.description" :label="t('prov.quote.description')" maxlength="250"
                variant="outlined" density="compact" />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field v-model="form.level" :label="t('prov.quote.cols.level')" variant="outlined" density="compact"
                :hint="t('prov.quote.support.levelHint')" />
            </v-col>
          </v-row>

          <p class="text-caption text-medium-emphasis mt-2 mb-0">
            {{ t('prov.quote.support.accessNote') }}
          </p>

          <div class="mt-4 d-flex align-center ga-3 flex-wrap">
            <div class="lookup-status">
              <v-icon v-if="lookingUp" size="small" color="primary" class="mr-1">mdi-progress-clock</v-icon>
              <v-icon v-else-if="suggest?.price" size="small" color="success" class="mr-1">mdi-check-circle</v-icon>
              <v-icon v-else-if="lookupError" size="small" color="warning" class="mr-1">mdi-alert</v-icon>
              <v-icon v-else size="small" class="mr-1">mdi-magnify</v-icon>
              <span class="text-caption text-medium-emphasis">
                {{
                  lookingUp ? t('prov.quote.instance.lookingUp')
                  : !canLookup ? t('prov.quote.support.lookupNeedsFields')
                  : t('prov.quote.instance.lookupAuto')
                }}
              </span>
            </div>
            <v-alert v-if="lookupError && !lookingUp" type="warning" variant="tonal" density="compact" class="flex-grow-1">
              {{ lookupError }}
            </v-alert>
            <v-card v-else-if="suggest?.price" variant="tonal" class="flex-grow-1 lookup-card">
              <v-card-text class="py-2">
                <div class="d-flex align-center ga-2 flex-wrap">
                  <v-icon size="small">mdi-lifebuoy</v-icon>
                  <strong>{{ suggest.price.type?.name || '?' }}</strong>
                  <v-spacer />
                  <span class="text-h6">{{ formatCost(suggest.cost, config?.currency) }}</span>
                  <span class="text-caption text-medium-emphasis">/mo</span>
                </div>
              </v-card-text>
            </v-card>
          </div>
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">{{ t('common.cancel') }}</v-btn>
        <v-btn color="primary" variant="elevated" :loading="saving" :disabled="!suggest?.price" @click="save">
          <v-icon start>mdi-content-save</v-icon>
          {{ isEdit ? t('common.save') : t('common.create') }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'
import { useApi, useErrorStore, useI18nStore, APP_BASE } from '@ligoj/host'
import { formatCost } from '../quoteFormatters.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  subscriptionId: { type: [Number, String], default: null },
  config: { type: Object, default: null },
  /** Existing support row when editing; `null` switches to create. */
  resource: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const api = useApi()
const errorStore = useErrorStore()
const { t } = useI18nStore()

const isEdit = computed(() => !!props.resource?.id)

const formRef = ref(null)
const saving = ref(false)
const lookingUp = ref(false)
const lookupError = ref(null)
const suggest = ref(null)

const form = reactive({
  id: null,
  name: '',
  description: '',
  level: '',
  seats: null,
})

const required = (v) => (v != null && v !== '') || (t('common.required') || 'Required')

/**
 * Support lookup accepts any combination of level/seats — the cheapest
 * match drives the suggestion. We only require a name to enable Save;
 * the lookup itself can run with empty constraints.
 */
const canLookup = computed(() => true)

/* ---------- Repopulate on open ---------- */

watch(() => props.modelValue, (open) => {
  if (!open) {
    clearLookupTimer()
    return
  }
  const it = props.resource
  if (it) {
    form.id          = it.id
    form.name        = it.name || ''
    form.description = it.description || ''
    form.level       = it.level || it.price?.level || ''
    form.seats       = it.seats ?? null
  } else {
    Object.assign(form, { id: null, name: '', description: '', level: '', seats: null })
  }
  suggest.value = it?.price ? { price: it.price, cost: it.cost } : null
  lookupError.value = null
  scheduleLookup()
})

/* ---------- Auto-lookup ---------- */

let lookupSeq = 0
let lookupTimer = null
const LOOKUP_DEBOUNCE_MS = 400

function clearLookupTimer() {
  if (lookupTimer) {
    clearTimeout(lookupTimer)
    lookupTimer = null
  }
}

function scheduleLookup() {
  if (!props.modelValue) return
  clearLookupTimer()
  lookupTimer = setTimeout(runLookup, LOOKUP_DEBOUNCE_MS)
}

watch(() => [form.level, form.seats], () => scheduleLookup())
onBeforeUnmount(clearLookupTimer)

async function runLookup() {
  const seq = ++lookupSeq
  lookingUp.value = true
  lookupError.value = null
  try {
    const qs = new URLSearchParams()
    if (form.level) qs.set('level', form.level)
    if (typeof form.seats === 'number' && form.seats > 0) qs.set('seats', String(form.seats))
    const url = `${APP_BASE}rest/service/prov/${props.subscriptionId}/support-lookup/?${qs}`
    const resp = await fetch(url, { credentials: 'include' })
    if (seq !== lookupSeq) return
    if (!resp.ok) {
      lookupError.value = t('prov.quote.support.lookupFailed')
      suggest.value = null
      return
    }
    const data = await resp.json()
    if (seq !== lookupSeq) return
    const hit = Array.isArray(data) ? data[0] : data
    if (!hit?.price) {
      lookupError.value = t('prov.quote.support.noMatch')
      suggest.value = null
      return
    }
    suggest.value = hit
  } catch (err) {
    if (seq !== lookupSeq) return
    lookupError.value = err?.message || t('prov.quote.support.lookupFailed')
    suggest.value = null
  } finally {
    if (seq === lookupSeq) lookingUp.value = false
  }
}

/* ---------- Save ---------- */

async function save() {
  const { valid } = await formRef.value.validate()
  if (!valid || !suggest.value?.price) return
  saving.value = true
  try {
    const payload = {
      id: form.id ?? undefined,
      name: form.name,
      description: form.description || null,
      subscription: Number(props.subscriptionId),
      // Support save sends the type CODE — same convention as storage.
      type: suggest.value.price.type?.code || suggest.value.price.type?.name,
      level: form.level || null,
      seats: form.seats ?? null,
    }
    const url = 'rest/service/prov/support'
    const result = form.id ? await api.put(url, payload) : await api.post(url, payload)
    if (result === null) return
    errorStore.success(t(form.id ? 'prov.quote.support.updated' : 'prov.quote.support.created', { name: payload.name }))
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.lookup-card {
  min-width: 0;
}

.lookup-status {
  display: inline-flex;
  align-items: center;
  min-width: 14rem;
  white-space: nowrap;
}
</style>
