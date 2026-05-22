<template>
  <v-dialog :model-value="modelValue" max-width="780" scrollable @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title>
        {{ isEdit ? t('prov.quote.storage.edit') : t('prov.quote.storage.new') }}
      </v-card-title>
      <v-card-text>
        <v-form ref="formRef" @submit.prevent="save">
          <v-row density="comfortable">
            <v-col cols="12" md="8">
              <v-text-field v-model="form.name" :label="t('prov.quote.name')" :rules="REQUIRED_RULES" maxlength="50"
                variant="outlined" density="compact" autofocus />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field v-model.number="form.sizeGb" :label="t('prov.quote.cols.size') + ' (GB)'"
                :rules="REQUIRED_POSITIVE_RULES" type="number" min="1" variant="outlined" density="compact" />
            </v-col>
            <v-col cols="12">
              <v-text-field v-model="form.description" :label="t('prov.quote.description')" maxlength="250"
                variant="outlined" density="compact" />
            </v-col>
            <!-- Attached resource — single dropdown combining every compute
                 resource in the quote. Selecting an attached resource
                 implicitly forces the storage location to match it. -->
            <v-col cols="12" md="8">
              <v-autocomplete v-model="form.attached" :items="attachedOptions" item-title="label" return-object
                :label="t('prov.quote.cols.attachedTo')" variant="outlined" density="compact" clearable />
            </v-col>
            <v-col cols="12" md="4">
              <v-autocomplete v-model="form.location" :items="config?.locations || []" item-title="name" item-value="name"
                :label="t('prov.quote.cols.location')" variant="outlined" density="compact" clearable
                :disabled="!!form.attached" />
            </v-col>
          </v-row>

          <!-- Advanced storage requirements. The legacy view shows
               these inline; in the Vue port they live behind an
               expansion so the simple case stays compact. -->
          <v-expansion-panels v-model="advancedOpen" variant="accordion" class="mt-3">
            <v-expansion-panel :title="t('prov.quote.storage.advanced')" eager>
              <template #text>
                <v-row density="comfortable">
                  <v-col cols="12" md="6">
                    <v-select v-model="form.latency" :items="RATE_OPTIONS" :label="t('prov.quote.storage.latency')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="12" md="6">
                    <v-select v-model="form.optimized" :items="OPTIMIZED_OPTIONS" :label="t('prov.quote.storage.optimized')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                </v-row>
              </template>
            </v-expansion-panel>
          </v-expansion-panels>

          <QuoteTagsEditor v-if="isEdit && props.resource?.id" :subscription-id="props.subscriptionId" type="storage"
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
                  : !canLookup ? t('prov.quote.storage.lookupNeedsFields')
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
                  <v-icon size="small">mdi-harddisk</v-icon>
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
import { formatCost, TAB_TYPES } from '../quoteFormatters.js'
import QuoteTagsEditor from './QuoteTagsEditor.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  subscriptionId: { type: [Number, String], default: null },
  config: { type: Object, default: null },
  /** Existing storage row when editing; `null` switches to create. */
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
  const byId = tagsByType.storage || tagsByType.STORAGE
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
  sizeGb: 10,
  location: null,
  /** `{ id, name, resourceType }` or `null`. */
  attached: null,
  // Advanced ↓
  latency: null,
  optimized: null,
})

const advancedOpen = ref(null)

// Same enums the legacy `prov-rate-full` / `prov-rate` slider buttons
// surfaced — kept here so the dialog has no dependency on the catalog
// endpoint.
const RATE_OPTIONS = ['BEST', 'GOOD', 'MEDIUM', 'LOW', 'WORST']
const OPTIMIZED_OPTIONS = ['IOPS', 'THROUGHPUT', 'DURABILITY']

const required = (v) => (v != null && v !== '') || (t('common.required') || 'Required')
const positive = (v) => (typeof v === 'number' && v > 0) || (t('common.positive') || 'Must be positive')
// Stable rule arrays — see ComputeEditDialog for the rationale.
const REQUIRED_RULES = [required]
const REQUIRED_POSITIVE_RULES = [required, positive]

const canLookup = computed(() => typeof form.sizeGb === 'number' && form.sizeGb > 0)

/**
 * Builds the attached-resource dropdown by flattening every compute
 * type's list with a synthetic `resourceType` field — the legacy
 * `storage-instance` select2 used the same approach.
 */
const attachedOptions = computed(() => {
  const conf = props.config
  if (!conf) return []
  const out = []
  for (const tab of TAB_TYPES) {
    if (tab.key === 'storage' || tab.key === 'support') continue
    const rows = Array.isArray(conf[tab.listField]) ? conf[tab.listField] : []
    for (const r of rows) {
      out.push({
        id: r.id,
        name: r.name,
        resourceType: tab.key,
        label: `${r.name} (${t(`prov.quote.tabs.${tab.key}`)})`,
      })
    }
  }
  return out
})

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
    form.sizeGb      = it.size ?? 10
    form.location    = it.location?.name ?? null
    // Reconstruct the attached descriptor from the row's quote* fields.
    const fromQuote = ['quoteInstance', 'quoteDatabase', 'quoteContainer', 'quoteFunction']
      .map((k) => {
        const v = it[k]
        if (!v) return null
        return { id: v.id, name: v.name, resourceType: k.replace('quote', '').toLowerCase(), label: `${v.name}` }
      })
      .find(Boolean)
    form.attached = fromQuote || null
    form.latency = it.latency || null
    form.optimized = it.optimized || null
  } else {
    Object.assign(form, {
      id: null, name: '', description: '', sizeGb: 10, location: null, attached: null,
      latency: null, optimized: null,
    })
  }
  suggest.value = it?.price ? { price: it.price, cost: it.cost } : null
  lookupError.value = null
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
  if (!canLookup.value) {
    suggest.value = null
    lookupError.value = null
    return
  }
  lookupTimer = setTimeout(runLookup, LOOKUP_DEBOUNCE_MS)
}

watch(
  () => [form.sizeGb, form.location, form.attached?.id, form.attached?.resourceType, form.latency, form.optimized],
  () => scheduleLookup(),
)
onBeforeUnmount(clearLookupTimer)

async function runLookup() {
  if (!canLookup.value) return
  const seq = ++lookupSeq
  lookingUp.value = true
  lookupError.value = null
  try {
    const qs = new URLSearchParams()
    qs.set('size', String(form.sizeGb))
    if (form.attached?.id && form.attached?.resourceType) {
      qs.set(form.attached.resourceType, String(form.attached.id))
    } else if (form.location) {
      qs.set('location', form.location)
    }
    if (form.latency) qs.set('latency', form.latency)
    if (form.optimized) qs.set('optimized', form.optimized)
    const url = `${APP_BASE}rest/service/prov/${props.subscriptionId}/storage-lookup/?${qs}`
    const resp = await fetch(url, { credentials: 'include' })
    if (seq !== lookupSeq) return
    if (!resp.ok) {
      lookupError.value = t('prov.quote.storage.lookupFailed')
      suggest.value = null
      return
    }
    const data = await resp.json()
    if (seq !== lookupSeq) return
    const hit = Array.isArray(data) ? data[0] : data
    if (!hit?.price) {
      lookupError.value = t('prov.quote.storage.noMatch')
      suggest.value = null
      return
    }
    suggest.value = hit
  } catch (err) {
    if (seq !== lookupSeq) return
    lookupError.value = err?.message || t('prov.quote.storage.lookupFailed')
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
      // Legacy storage save sends the type CODE, not the price id —
      // the price uniquely maps from (type.code, location, size).
      type: suggest.value.price.type?.code || suggest.value.price.type?.name,
      size: form.sizeGb,
      location: form.location,
    }
    if (form.attached?.resourceType && form.attached?.id) {
      payload[form.attached.resourceType] = form.attached.id
    }
    if (form.latency) payload.latency = form.latency
    if (form.optimized) payload.optimized = form.optimized
    const url = 'rest/service/prov/storage'
    const result = form.id ? await api.put(url, payload) : await api.post(url, payload)
    if (result === null) return
    errorStore.success(t(form.id ? 'prov.quote.storage.updated' : 'prov.quote.storage.created', { name: payload.name }))
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
