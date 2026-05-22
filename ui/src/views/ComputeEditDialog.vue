<template>
  <v-dialog :model-value="modelValue" max-width="820" scrollable @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title>
        {{ titleKey }}
      </v-card-title>
      <v-card-text>
        <v-form ref="formRef" @submit.prevent="save">
          <v-row dense>
            <v-col cols="12" md="6">
              <v-text-field v-model="form.name" :label="t('prov.quote.name')" :rules="[required]" maxlength="50"
                variant="outlined" density="compact" autofocus />
            </v-col>

            <v-col v-if="hasOs" cols="12" md="6">
              <v-autocomplete v-model="form.os" :items="OS_OPTIONS" :label="t('prov.quote.cols.os')" :rules="[required]"
                variant="outlined" density="compact" />
            </v-col>

            <v-col v-if="type === 'database'" cols="12" md="6">
              <v-autocomplete v-model="form.engine" :items="ENGINE_OPTIONS" :label="t('prov.quote.cols.engine')"
                :rules="[required]" variant="outlined" density="compact" />
            </v-col>
            <v-col v-if="type === 'database'" cols="12" md="6">
              <v-text-field v-model="form.edition" :label="t('prov.quote.compute.edition')" variant="outlined"
                density="compact" :hint="t('prov.quote.compute.editionHint')" />
            </v-col>

            <v-col cols="12">
              <v-text-field v-model="form.description" :label="t('prov.quote.description')" maxlength="250"
                variant="outlined" density="compact" />
            </v-col>

            <v-col cols="6" md="3">
              <v-text-field v-model.number="form.cpu" :label="t('prov.quote.cols.cpu')" :rules="[required, positive]"
                type="number" min="0" step="0.25" variant="outlined" density="compact" />
            </v-col>
            <v-col cols="6" md="3">
              <v-text-field v-model.number="form.ramGb" :label="ramLabel" :rules="[required, positive]" type="number"
                min="0" step="0.5" variant="outlined" density="compact" />
            </v-col>

            <v-col v-if="hasQuantity" cols="6" md="3">
              <v-text-field v-model.number="form.minQuantity" :label="t('prov.quote.compute.minQty')" type="number"
                min="0" variant="outlined" density="compact" />
            </v-col>
            <v-col v-if="hasQuantity" cols="6" md="3">
              <v-text-field v-model.number="form.maxQuantity" :label="t('prov.quote.compute.maxQty')" type="number"
                min="0" variant="outlined" density="compact" />
            </v-col>

            <v-col v-if="type === 'function'" cols="12" md="4">
              <v-text-field v-model.number="form.nbRequests" :label="t('prov.quote.function.nbRequests')"
                :rules="[required, positive]" type="number" min="1" max="10000" variant="outlined" density="compact" />
            </v-col>
            <v-col v-if="type === 'function'" cols="12" md="4">
              <v-text-field v-model.number="form.duration" :label="t('prov.quote.function.duration')"
                :rules="[required, positive]" type="number" min="1" max="7200000" variant="outlined" density="compact" />
            </v-col>
            <v-col v-if="type === 'function'" cols="12" md="4">
              <v-text-field v-model.number="form.concurrency" :label="t('prov.quote.function.concurrency')"
                type="number" min="0" max="10000" variant="outlined" density="compact" />
            </v-col>

            <v-col cols="12" md="6">
              <v-autocomplete v-model="form.location" :items="config?.locations || []" item-title="name"
                item-value="name" :label="t('prov.quote.cols.location')" variant="outlined" density="compact" clearable />
            </v-col>
            <v-col cols="12" md="6">
              <v-autocomplete v-model="form.usage" :items="config?.usages || []" item-title="name" item-value="name"
                :label="t('prov.quote.fields.usage')" variant="outlined" density="compact" clearable />
            </v-col>
          </v-row>

          <!-- Lookup result — auto-debounced. Save commits only on the explicit button. -->
          <div class="mt-4 d-flex align-center ga-3 flex-wrap">
            <div class="lookup-status">
              <v-icon v-if="lookingUp" size="small" color="primary" class="mr-1">mdi-progress-clock</v-icon>
              <v-icon v-else-if="suggest?.price" size="small" color="success" class="mr-1">mdi-check-circle</v-icon>
              <v-icon v-else-if="lookupError" size="small" color="warning" class="mr-1">mdi-alert</v-icon>
              <v-icon v-else size="small" class="mr-1">mdi-magnify</v-icon>
              <span class="text-caption text-medium-emphasis">
                {{
                  lookingUp ? t('prov.quote.instance.lookingUp')
                  : !canLookup ? lookupRequirementsHint
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
                  <v-icon size="small">{{ icon }}</v-icon>
                  <strong>{{ suggest.price.type?.name || suggest.price.term?.name || '?' }}</strong>
                  <v-chip v-if="suggest.price.term?.name" size="x-small" variant="tonal">{{ suggest.price.term.name }}</v-chip>
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

/**
 * Generic create/edit dialog for the four compute-style resources
 * (instance, container, function, database). The legacy view used the
 * same `popup-prov-generic` for all of them, with `data-exclusive`
 * attributes flipping field visibility per type — same idea here, via
 * `v-if` and a couple of computed predicates.
 *
 * The lookup endpoint is `<type>-lookup` and the save endpoint is
 * `service/prov/<type>`. RAM travels in MB on the wire (the form
 * works in GB).
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** One of: 'instance' | 'container' | 'function' | 'database'. */
  type: { type: String, default: 'instance' },
  subscriptionId: { type: [Number, String], default: null },
  config: { type: Object, default: null },
  /** Existing resource row when editing; `null` switches to create. */
  resource: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const api = useApi()
const errorStore = useErrorStore()
const { t } = useI18nStore()

const isEdit = computed(() => !!props.resource?.id)

// Per-type field visibility predicates.
const hasOs = computed(() => props.type === 'instance' || props.type === 'container')
const hasQuantity = computed(() => props.type !== 'function')

const OS_OPTIONS = ['LINUX', 'WINDOWS', 'RHEL', 'SUSE', 'CENTOS', 'DEBIAN', 'FEDORA', 'UBUNTU', 'ORACLE']
const ENGINE_OPTIONS = ['MYSQL', 'POSTGRESQL', 'ORACLE', 'MARIADB', 'SQL_SERVER', 'AURORA']

const ICONS = {
  instance:  'mdi-server',
  container: 'mdi-docker',
  function:  'mdi-lambda',
  database:  'mdi-database',
}
const icon = computed(() => ICONS[props.type] || 'mdi-server')

const titleKey = computed(() => {
  const k = isEdit.value ? 'edit' : 'new'
  return t(`prov.quote.${props.type}.${k}`)
})

const ramLabel = computed(() => `${t('prov.quote.cols.ram')} (GB)`)

const lookupRequirementsHint = computed(() => {
  if (props.type === 'database') return t('prov.quote.database.lookupNeedsFields')
  if (props.type === 'function') return t('prov.quote.function.lookupNeedsFields')
  return t('prov.quote.instance.lookupNeedsFields')
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
  os: 'LINUX',
  engine: 'MYSQL',
  edition: '',
  cpu: 1,
  ramGb: 1,
  minQuantity: 1,
  maxQuantity: null,
  location: null,
  usage: null,
  nbRequests: 1,
  duration: 100,
  concurrency: 0,
})

const required = (v) => (v != null && v !== '') || (t('common.required') || 'Required')
const positive = (v) => (typeof v === 'number' && v > 0) || (t('common.positive') || 'Must be positive')

const canLookup = computed(() => {
  if (typeof form.cpu !== 'number' || form.cpu <= 0) return false
  if (typeof form.ramGb !== 'number' || form.ramGb <= 0) return false
  if (hasOs.value && !form.os) return false
  if (props.type === 'database' && !form.engine) return false
  if (props.type === 'function') {
    if (!(form.nbRequests > 0) || !(form.duration > 0)) return false
  }
  return true
})

/* ---------- Repopulate on open ---------- */

watch(() => props.modelValue, (open) => {
  if (!open) {
    clearLookupTimer()
    return
  }
  const it = props.resource
  if (it) {
    form.id            = it.id
    form.name          = it.name || ''
    form.description   = it.description || ''
    form.os            = (it.os || it.price?.os || 'LINUX').toUpperCase()
    form.engine        = (it.engine || it.price?.engine || 'MYSQL').toUpperCase()
    form.edition       = it.edition || it.price?.edition || ''
    form.cpu           = it.cpu ?? it.price?.type?.cpu ?? 1
    form.ramGb         = ((it.ram ?? it.price?.type?.ram ?? 1024) / 1024)
    form.minQuantity   = it.minQuantity ?? 1
    form.maxQuantity   = it.maxQuantity ?? null
    form.location      = it.location?.name ?? null
    form.usage         = it.usage?.name ?? null
    form.nbRequests    = it.nbRequests ?? 1
    form.duration      = it.duration ?? 100
    form.concurrency   = it.concurrency ?? 0
  } else {
    Object.assign(form, {
      id: null, name: '', description: '', os: 'LINUX', engine: 'MYSQL', edition: '',
      cpu: 1, ramGb: 1, minQuantity: 1, maxQuantity: null,
      location: null, usage: null,
      nbRequests: 1, duration: 100, concurrency: 0,
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

// Reactive deps for lookup; name/description/quantity excluded.
watch(
  () => [
    props.type,
    form.os, form.engine, form.edition,
    form.cpu, form.ramGb,
    form.nbRequests, form.duration, form.concurrency,
    form.location, form.usage,
  ],
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
    qs.set('cpu', String(form.cpu))
    qs.set('ram', String(Math.round(form.ramGb * 1024)))
    if (hasOs.value) qs.set('os', form.os)
    if (props.type === 'database') {
      qs.set('engine', form.engine)
      if (form.edition) qs.set('edition', form.edition)
    }
    if (props.type === 'function') {
      qs.set('nbRequests', String(form.nbRequests))
      qs.set('duration', String(form.duration))
      qs.set('concurrency', String(form.concurrency || 0))
    }
    if (form.location) qs.set('location', form.location)
    if (form.usage) qs.set('usage', form.usage)
    const url = `${APP_BASE}rest/service/prov/${props.subscriptionId}/${props.type}-lookup/?${qs}`
    const resp = await fetch(url, { credentials: 'include' })
    if (seq !== lookupSeq) return
    if (!resp.ok) {
      lookupError.value = t('prov.quote.instance.lookupFailed')
      suggest.value = null
      return
    }
    const data = await resp.json()
    if (seq !== lookupSeq) return
    const hit = Array.isArray(data) ? data[0] : data
    if (!hit?.price) {
      lookupError.value = t('prov.quote.instance.noMatch')
      suggest.value = null
      return
    }
    suggest.value = hit
  } catch (err) {
    if (seq !== lookupSeq) return
    lookupError.value = err?.message || t('prov.quote.instance.lookupFailed')
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
      price: suggest.value.price.id,
      cpu: form.cpu,
      ram: Math.round(form.ramGb * 1024),
      location: form.location,
      usage: form.usage,
    }
    if (hasOs.value) payload.os = form.os
    if (props.type === 'database') {
      payload.engine = form.engine
      payload.edition = form.edition || null
    }
    if (props.type === 'function') {
      payload.nbRequests = form.nbRequests
      payload.duration = form.duration
      payload.concurrency = form.concurrency || 0
    }
    if (hasQuantity.value) {
      payload.minQuantity = form.minQuantity ?? 0
      payload.maxQuantity = form.maxQuantity ?? null
    }
    const url = `rest/service/prov/${props.type}`
    const result = form.id ? await api.put(url, payload) : await api.post(url, payload)
    if (result === null) return // useApi surfaced the error
    const i18nKey = form.id ? `prov.quote.${props.type}.updated` : `prov.quote.${props.type}.created`
    errorStore.success(t(i18nKey, { name: payload.name }))
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
