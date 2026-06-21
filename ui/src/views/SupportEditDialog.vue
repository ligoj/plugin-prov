<template>
  <v-dialog :model-value="modelValue" max-width="700" scrollable @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title>
        {{ isEdit ? t('prov.quote.support.edit') : t('prov.quote.support.new') }}
      </v-card-title>
      <v-card-text>
        <v-form ref="formRef" @submit.prevent="save">
          <v-row density="comfortable">
            <v-col cols="12" md="8">
              <v-text-field v-model="form.name" :label="t('prov.quote.name')" :rules="REQUIRED_RULES" maxlength="50"
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

          <!-- Access channels: per-channel enum (NONE/CHAT/TECHNICAL/
               BILLING/ALL). Drives the support-lookup query so the
               cheapest plan that supports the desired channels comes
               back. -->
          <v-expansion-panels v-model="advancedOpen" variant="accordion" class="mt-3">
            <v-expansion-panel :title="t('prov.quote.support.access')" eager>
              <template #text>
                <v-row density="comfortable">
                  <v-col cols="6" md="3">
                    <v-select v-model="form.accessApi" :items="ACCESS_OPTIONS" :label="t('prov.quote.support.accessApi')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="6" md="3">
                    <v-select v-model="form.accessEmail" :items="ACCESS_OPTIONS" :label="t('prov.quote.support.accessEmail')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="6" md="3">
                    <v-select v-model="form.accessPhone" :items="ACCESS_OPTIONS" :label="t('prov.quote.support.accessPhone')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="6" md="3">
                    <v-select v-model="form.accessChat" :items="ACCESS_OPTIONS" :label="t('prov.quote.support.accessChat')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                </v-row>
              </template>
            </v-expansion-panel>
          </v-expansion-panels>

          <QuoteTagsEditor v-if="isEdit && props.resource?.id" :subscription-id="props.subscriptionId" type="support"
            :resource-id="props.resource.id" :model-value="resourceTags" :all-tags-by-type="props.config?.tags || {}"
            @update:model-value="(v) => emit('tags-changed', v)" />

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
import QuoteTagsEditor from './QuoteTagsEditor.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  subscriptionId: { type: [Number, String], default: null },
  config: { type: Object, default: null },
  /** Existing support row when editing; `null` switches to create. */
  resource: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved', 'tags-changed'])

const api = useApi()
const errorStore = useErrorStore()
const { t } = useI18nStore()

const isEdit = computed(() => !!props.resource?.id)

const resourceTags = computed(() => {
  const tagsByType = props.config?.tags
  if (!tagsByType || !props.resource?.id) return []
  const byId = tagsByType.support || tagsByType.SUPPORT
  if (!byId) return []
  const list = byId[props.resource.id]
  return Array.isArray(list) ? list : []
})

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
  // Access channels — each is one of the ACCESS_OPTIONS enum or null.
  accessApi: null,
  accessEmail: null,
  accessPhone: null,
  accessChat: null,
})

const advancedOpen = ref(null)

// Common enum used by every provider catalog — the legacy view shows
// each value with an i18n'd label, but the enum itself is stable.
const ACCESS_OPTIONS = ['NONE', 'CHAT', 'TECHNICAL', 'BILLING', 'ALL']

const required = (v) => (v != null && v !== '') || (t('common.required') || 'Required')
// Stable rule array — see ComputeEditDialog for the rationale.
const REQUIRED_RULES = [required]

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
    form.accessApi   = it.accessApi || null
    form.accessEmail = it.accessEmail || null
    form.accessPhone = it.accessPhone || null
    form.accessChat  = it.accessChat || null
  } else {
    Object.assign(form, {
      id: null, name: '', description: '', level: '', seats: null,
      accessApi: null, accessEmail: null, accessPhone: null, accessChat: null,
    })
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

watch(
  () => [form.level, form.seats, form.accessApi, form.accessEmail, form.accessPhone, form.accessChat],
  () => scheduleLookup(),
)
onBeforeUnmount(clearLookupTimer)

async function runLookup() {
  const seq = ++lookupSeq
  lookingUp.value = true
  lookupError.value = null
  try {
    const qs = new URLSearchParams()
    if (form.level) qs.set('level', form.level)
    if (typeof form.seats === 'number' && form.seats > 0) qs.set('seats', String(form.seats))
    if (form.accessApi) qs.set('accessApi', form.accessApi)
    if (form.accessEmail) qs.set('accessEmail', form.accessEmail)
    if (form.accessPhone) qs.set('accessPhone', form.accessPhone)
    if (form.accessChat) qs.set('accessChat', form.accessChat)
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
      accessApi: form.accessApi || null,
      accessEmail: form.accessEmail || null,
      accessPhone: form.accessPhone || null,
      accessChat: form.accessChat || null,
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
