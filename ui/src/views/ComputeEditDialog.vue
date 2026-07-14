<template>
  <v-dialog :model-value="modelValue" max-width="820" scrollable @update:model-value="(v) => emit('update:modelValue', v)">
    <v-card>
      <v-card-title>
        {{ titleKey }}
      </v-card-title>
      <v-card-text>
        <v-form ref="formRef" @submit.prevent="save">
          <v-row density="comfortable">
            <v-col cols="12" md="6">
              <v-text-field v-model="form.name" :label="t('prov.quote.name')" :rules="REQUIRED_RULES" maxlength="50"
                variant="outlined" density="compact" autofocus />
            </v-col>

            <v-col v-if="hasOs" cols="12" md="6">
              <LigojAutocomplete v-model="form.os" :items="OS_OPTIONS" :label="t('prov.quote.cols.os')" :rules="REQUIRED_RULES"
                variant="outlined" density="compact">
                <template #item="{ props: itemProps, item }">
                  <v-list-item v-bind="itemProps">
                    <template #prepend><OsIcon :os="item" /></template>
                  </v-list-item>
                </template>
                <template #selection="{ item }">
                  <OsIcon :os="item" class="me-2" />{{ item }}
                </template>
              </LigojAutocomplete>
            </v-col>

            <v-col v-if="type === 'database'" cols="12" md="6">
              <LigojAutocomplete v-model="form.engine" :items="ENGINE_OPTIONS" :label="t('prov.quote.cols.engine')"
                :rules="REQUIRED_RULES" variant="outlined" density="compact" />
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
              <CapacityField v-model="form.cpu" :label="t('prov.quote.cols.cpu')" :rules="REQUIRED_POSITIVE_RULES"
                min="0" step="0.25" kind="cpu" :unit="t('prov.quote.cols.cpu')" :provided="suggest?.price?.type?.cpu || 0"
                :explanation="t('prov.quote.microbar.cpu')" />
            </v-col>
            <v-col cols="6" md="3">
              <CapacityField v-model="form.ramGb" :label="ramLabel" :rules="REQUIRED_POSITIVE_RULES"
                min="0" step="0.5" kind="ram" :provided="suggest?.price?.type?.ram || 0"
                :explanation="t('prov.quote.microbar.ram')" />
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
                :rules="REQUIRED_POSITIVE_RULES" type="number" min="1" max="10000" variant="outlined" density="compact" />
            </v-col>
            <v-col v-if="type === 'function'" cols="12" md="4">
              <v-text-field v-model.number="form.duration" :label="t('prov.quote.function.duration')"
                :rules="REQUIRED_POSITIVE_RULES" type="number" min="1" max="7200000" variant="outlined" density="compact" />
            </v-col>
            <v-col v-if="type === 'function'" cols="12" md="4">
              <v-text-field v-model.number="form.concurrency" :label="t('prov.quote.function.concurrency')"
                type="number" min="0" max="10000" variant="outlined" density="compact" />
            </v-col>

            <v-col cols="12" md="6">
              <LigojAutocomplete v-model="form.location" :items="config?.locations || []" item-title="name"
                item-value="name" :label="t('prov.quote.cols.location')" variant="outlined" density="compact" clearable />
            </v-col>
            <v-col cols="12" md="6">
              <LigojAutocomplete v-model="form.usage" :items="config?.usages || []" item-title="name" item-value="name"
                :label="t('prov.quote.fields.usage')" variant="outlined" density="compact" clearable />
            </v-col>
          </v-row>

          <!-- Advanced fields, collapsed by default. They all feed the
               lookup query but stay out of the way for typical
               requirements.
               `eager` pre-renders the body so inputs mount once on
               dialog open, NOT during the expand transition — that
               combination triggered "Maximum recursive updates" in
               Vuetify 4 when v-form revalidated each transition frame. -->
          <v-expansion-panels v-model="advancedOpen" variant="accordion" class="mt-3">
            <v-expansion-panel :title="t('prov.quote.compute.advanced')" eager>
              <template #text>
                <v-row density="comfortable">
                  <v-col cols="12" md="6">
                    <!-- Free-text inputs (was v-combobox). Vuetify 4's
                         v-combobox with a computed `:items` array +
                         clearable inside an expanding panel reliably
                         triggers "Maximum recursive updates" — the
                         catalog suggestions weren't worth the cost. -->
                    <v-text-field v-model="form.processor" :label="t('prov.quote.fields.processor')"
                      variant="outlined" density="compact" clearable :hint="t('prov.quote.compute.processorHint')" persistent-hint />
                  </v-col>
                  <v-col cols="12" md="6">
                    <v-text-field v-model="form.architecture" :label="t('prov.quote.fields.architecture')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="12" md="6">
                    <v-select v-model="form.physical" :items="physicalOptions" :label="t('prov.quote.fields.physical')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="12" md="6">
                    <v-text-field v-model="form.license" :label="t('prov.quote.compute.license')" variant="outlined"
                      density="compact" clearable />
                  </v-col>
                  <v-col v-if="props.type === 'instance'" cols="12" md="6">
                    <v-text-field v-model="form.software" :label="t('prov.quote.compute.software')" variant="outlined"
                      density="compact" clearable :hint="t('prov.quote.compute.softwareHint')" persistent-hint />
                  </v-col>
                  <v-col v-if="hasGpu" cols="12" md="6">
                    <v-text-field v-model.number="form.gpu" :label="t('prov.quote.compute.gpu')" type="number" min="0" max="8"
                      variant="outlined" density="compact" />
                  </v-col>
                  <v-col cols="12" md="4">
                    <v-select v-model="form.cpuRate" :items="RATE_OPTIONS" :label="t('prov.quote.compute.cpuRate')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="12" md="4">
                    <v-select v-model="form.ramRate" :items="RATE_OPTIONS" :label="t('prov.quote.compute.ramRate')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="12" md="4">
                    <v-select v-model="form.networkRate" :items="RATE_OPTIONS" :label="t('prov.quote.compute.networkRate')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="12" md="4">
                    <v-select v-model="form.storageRate" :items="RATE_OPTIONS" :label="t('prov.quote.compute.storageRate')"
                      variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col cols="12">
                    <v-text-field v-model="form.workload" :label="t('prov.quote.compute.workload')" :rules="WORKLOAD_RULES"
                      :hint="t('prov.quote.compute.workloadHint')" persistent-hint variant="outlined" density="compact" clearable />
                  </v-col>
                  <v-col v-if="hasEphemeral" cols="12" md="6">
                    <v-switch v-model="form.ephemeral" :label="t('prov.quote.compute.ephemeral')" color="primary"
                      density="compact" hide-details />
                  </v-col>
                  <v-col v-if="hasEphemeral" cols="12" md="6">
                    <v-text-field v-model.number="form.maxVariableCost" :label="t('prov.quote.compute.maxVariableCost')"
                      type="number" min="0" variant="outlined" density="compact" clearable />
                  </v-col>
                </v-row>
              </template>
            </v-expansion-panel>
          </v-expansion-panels>

          <!-- Tag editor — edit mode only. Tags ride their own REST
               endpoint, so the editor mutates the model immediately
               without waiting for Save. -->
          <QuoteTagsEditor v-if="isEdit && props.resource?.id" :subscription-id="props.subscriptionId" :type="props.type"
            :resource-id="props.resource.id" :model-value="resourceTags" :all-tags-by-type="props.config?.tags || {}"
            @update:model-value="(v) => emit('tags-changed', v)" />

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
        <!-- Create mode only: keep the dialog open after saving so several
             resources can be added in one sitting. -->
        <v-checkbox v-if="!isEdit" v-model="createAnother" :label="t('prov.quote.createAnother')"
          density="compact" hide-details color="primary" class="ml-2 create-another" />
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
import { useApi, useErrorStore, useI18nStore, APP_BASE, LigojAutocomplete } from '@ligoj/host'
import { formatCost } from '../quoteFormatters.js'
import QuoteTagsEditor from './QuoteTagsEditor.vue'
import CapacityField from './CapacityField.vue'
import OsIcon from './OsIcon.vue'

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
const emit = defineEmits(['update:modelValue', 'saved', 'tags-changed'])

const api = useApi()
const errorStore = useErrorStore()
const { t } = useI18nStore()

const isEdit = computed(() => !!props.resource?.id)

const resourceTags = computed(() => {
  const tagsByType = props.config?.tags
  if (!tagsByType || !props.resource?.id) return []
  // The backend keys this map by the ResourceType enum name (UPPERCASE,
  // e.g. "INSTANCE"), so a plain lowercase `props.type` lookup misses and
  // the editor showed no tags. Resolve it case-insensitively.
  const key = String(props.type)
  const byId = tagsByType[key.toUpperCase()] || tagsByType[key.toLowerCase()] || tagsByType[key]
  if (!byId) return []
  const list = byId[props.resource.id]
  return Array.isArray(list) ? list : []
})

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
// Create-mode only: when checked, saving keeps the dialog open and resets
// the form for the next resource instead of closing.
const createAnother = ref(false)

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
  // Advanced ↓
  processor: null,
  architecture: null,
  physical: null,
  license: null,
  software: null,
  gpu: 0,
  ephemeral: false,
  maxVariableCost: null,
  cpuRate: null,
  ramRate: null,
  networkRate: null,
  storageRate: null,
  /** Comma-separated CPU profile string, e.g. `100,40@20,80@30`.
   *  Legacy format: peak%[,duration@cpu]* — kept as raw text. */
  workload: '',
})

/**
 * Loose validator for the workload string. Empty is allowed; otherwise
 * each segment must be either a single percent (0–100) or `dur@cpu`
 * with both fractions in 0..100. Doesn't try to be exhaustive — the
 * backend re-validates and reports per-token errors.
 */
function workloadRule(v) {
  if (!v) return true
  const parts = String(v).split(',').map((s) => s.trim()).filter(Boolean)
  for (const p of parts) {
    if (!/^\d{1,3}(@\d{1,3})?$/.test(p)) return t('prov.quote.compute.workloadHint')
  }
  return true
}

// Weighted-rate selector — same enum used by the legacy
// `.prov-rate` button groups and by storage's latency.
const RATE_OPTIONS = ['BEST', 'GOOD', 'MEDIUM', 'LOW', 'WORST']

const advancedOpen = ref(null)

const hasGpu = computed(() => props.type === 'instance' || props.type === 'container')
const hasEphemeral = computed(() => props.type === 'instance' || props.type === 'container')

const physicalOptions = computed(() => [
  { value: true,  title: t('prov.quote.fields.physical.true') },
  { value: false, title: t('prov.quote.fields.physical.false') },
])

const required = (v) => (v != null && v !== '') || (t('common.required') || 'Required')
const positive = (v) => (typeof v === 'number' && v > 0) || (t('common.positive') || 'Must be positive')

/* Stable rule arrays. Vuetify 4's v-form re-runs validation whenever
 * `:rules` changes by reference — inline `:rules="REQUIRED_RULES"` creates
 * a fresh array each render, which when combined with v-form's
 * mount-time validation re-runs forever inside `v-expansion-panel-text`
 * transitions ("Maximum recursive updates exceeded"). Hoisting these
 * to stable refs sidesteps the cycle. */
const REQUIRED_RULES = [required]
const REQUIRED_POSITIVE_RULES = [required, positive]
const WORKLOAD_RULES = [workloadRule]

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
    form.processor     = it.processor ?? it.price?.type?.processor ?? null
    form.architecture  = it.architecture ?? it.price?.type?.architecture ?? null
    form.physical      = it.physical ?? null
    form.license       = it.license ?? it.price?.license ?? null
    form.software      = it.software ?? it.price?.software ?? null
    form.gpu           = it.gpu ?? 0
    form.ephemeral     = it.ephemeral === true
    form.maxVariableCost = it.maxVariableCost ?? null
    form.cpuRate       = it.cpuRate ?? null
    form.ramRate       = it.ramRate ?? null
    form.networkRate   = it.networkRate ?? null
    form.storageRate   = it.storageRate ?? null
    form.workload      = it.workload ?? ''
  } else {
    blankForm()
  }
  suggest.value = it?.price ? { price: it.price, cost: it.cost } : null
  lookupError.value = null
})

/** Resets the form to its create-mode defaults (shared by open + "create another"). */
function blankForm() {
  Object.assign(form, {
    id: null, name: '', description: '', os: 'LINUX', engine: 'MYSQL', edition: '',
    cpu: 1, ramGb: 1, minQuantity: 1, maxQuantity: null,
    location: null, usage: null,
    nbRequests: 1, duration: 100, concurrency: 0,
    processor: null, architecture: null, physical: null, license: null, software: null,
    gpu: 0, ephemeral: false, maxVariableCost: null,
    cpuRate: null, ramRate: null, networkRate: null, storageRate: null, workload: '',
  })
}

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
    form.processor, form.architecture, form.physical, form.license, form.software, form.gpu,
    form.cpuRate, form.ramRate, form.networkRate, form.storageRate,
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
    if (form.processor) qs.set('processor', String(form.processor).toLowerCase())
    if (form.architecture) qs.set('architecture', String(form.architecture).toLowerCase())
    if (form.physical === true || form.physical === false) qs.set('physical', String(form.physical))
    if (form.license) qs.set('license', String(form.license).toLowerCase())
    if (props.type === 'instance' && form.software) qs.set('software', String(form.software).toLowerCase())
    if (hasGpu.value && form.gpu > 0) qs.set('gpu', String(form.gpu))
    if (form.cpuRate) qs.set('cpuRate', form.cpuRate)
    if (form.ramRate) qs.set('ramRate', form.ramRate)
    if (form.networkRate) qs.set('networkRate', form.networkRate)
    if (form.storageRate) qs.set('storageRate', form.storageRate)
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
    // Advanced fields — normalised the same way the legacy
    // `genericUiToData` did (lowercase strings; tri-state physical).
    if (form.processor) payload.processor = String(form.processor).toLowerCase()
    if (form.architecture) payload.architecture = String(form.architecture).toLowerCase()
    if (form.physical === true || form.physical === false) payload.physical = form.physical
    if (form.license) payload.license = String(form.license).toLowerCase()
    if (props.type === 'instance' && form.software) payload.software = String(form.software).toLowerCase()
    if (hasGpu.value && form.gpu > 0) payload.gpu = form.gpu
    if (form.cpuRate) payload.cpuRate = form.cpuRate
    if (form.ramRate) payload.ramRate = form.ramRate
    if (form.networkRate) payload.networkRate = form.networkRate
    if (form.storageRate) payload.storageRate = form.storageRate
    if (form.workload) payload.workload = form.workload
    if (hasEphemeral.value) {
      payload.ephemeral = !!form.ephemeral
      if (typeof form.maxVariableCost === 'number' && form.maxVariableCost > 0) {
        payload.maxVariableCost = form.maxVariableCost
      }
    }
    const url = `rest/service/prov/${props.type}`
    const created = !form.id
    const result = created ? await api.post(url, payload) : await api.put(url, payload)
    if (result === null) return // useApi surfaced the error
    const i18nKey = created ? `prov.quote.${props.type}.created` : `prov.quote.${props.type}.updated`
    errorStore.success(t(i18nKey, { name: payload.name }))
    emit('saved')
    if (created && createAnother.value) {
      // Keep the dialog open for the next resource; re-lookup the reset form.
      blankForm()
      suggest.value = null
      lookupError.value = null
      scheduleLookup()
    } else {
      emit('update:modelValue', false)
    }
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
