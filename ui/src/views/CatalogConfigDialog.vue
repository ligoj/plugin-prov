<template>
  <LjDialog v-model="open" :title="t('catalog.config.title', { name: catalog?.node?.name || catalog?.node?.id })" max-width="720">
    <v-form ref="formRef" @submit.prevent="save">
      <v-row density="comfortable" class="mt-1">
        <v-col cols="12">
          <!-- Default location: the location name, picked among the provider catalog locations (flag + country) -->
          <LocationField v-model="form.defaultLocation" :items="locations" :label="t('catalog.config.defaultLocation')">
            <template #append-inner>
              <v-icon size="small">mdi-help-circle-outline</v-icon>
              <v-tooltip activator="parent" location="top" max-width="360" :text="t('catalog.config.defaultLocationHelp')" />
            </template>
          </LocationField>
        </v-col>

        <v-col v-for="property in properties" :key="property.name" cols="12" md="6">
          <LigojTextField v-model="form.properties[property.name]" :label="propertyLabel(property)"
            :placeholder="property.default || ''" :rules="property.type === 'regExp' ? REGEXP_RULES : []"
            variant="outlined" density="compact" clearable persistent-placeholder>
            <template #append-inner>
              <v-icon size="small">mdi-help-circle-outline</v-icon>
              <v-tooltip activator="parent" location="top" max-width="360" :text="propertyTooltip(property)" />
            </template>
          </LigojTextField>
        </v-col>
      </v-row>
    </v-form>

    <template #footer>
      <LjButton variant="ghost" @click="open = false">{{ t('common.cancel') }}</LjButton>
      <LjButton icon="mdi-content-save" :loading="saving" @click="save">{{ t('common.save') }}</LjButton>
    </template>
  </LjDialog>
</template>

<script setup>
/*
 * Provider configuration dialog: default location (plain name, no foreign key, picked among the provider
 * `ProvLocation` objects returned by the configuration endpoint) plus the provider scoped
 * SystemConfiguration properties. The five common filter patterns (regions, instance type, OS, database
 * type/engine — case-insensitive regular expressions, empty means "all") are declared here; the provider's Vue
 * plugin contributes its own extra properties through its `catalogConfiguration` feature:
 * `[{ name, key, type: 'regExp'|'string', default }]`.
 */
import { ref, reactive, computed, watch } from 'vue'
import { LigojTextField, useApi, useI18nStore, APP_BASE, LjDialog, LjButton, pluginRegistry } from '@ligoj/host'
import LocationField from './LocationField.vue'

const api = useApi()
const i18n = useI18nStore()
const t = i18n.t

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** The catalog row: `{ node: { id, name } }`. */
  catalog: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const formRef = ref(null)
const saving = ref(false)
const locations = ref([])
const form = reactive({ defaultLocation: null, properties: {} })

/** Common provider filter patterns, keyed under the provider node's namespace. */
function commonProperties(tool) {
  return [
    { name: `${tool}:regions`, key: 'regions', type: 'regExp', default: '.*' },
    { name: `${tool}:instance-type`, key: 'instanceType', type: 'regExp', default: '.*' },
    { name: `${tool}:os`, key: 'os', type: 'regExp', default: '.*' },
    { name: `${tool}:database-type`, key: 'databaseType', type: 'regExp', default: '.*' },
    { name: `${tool}:database-engine`, key: 'databaseEngine', type: 'regExp', default: '.*' },
  ]
}

/** Provider specific properties contributed by the provider's Vue plugin. */
function pluginProperties(nodeId) {
  const parts = String(nodeId || '').split(':').filter(Boolean)
  if (parts.length < 3) return []
  const plugin = pluginRegistry.get(`${parts[1]}-${parts[2]}`)
  if (typeof plugin?.feature !== 'function') return []
  try {
    const list = plugin.feature('catalogConfiguration')
    return Array.isArray(list) ? list : []
  } catch {
    // The provider does not declare extra configuration
    return []
  }
}

const properties = computed(() => {
  const nodeId = props.catalog?.node?.id
  if (!nodeId) return []
  return [...commonProperties(nodeId), ...pluginProperties(nodeId)]
})

function propertyLabel(property) {
  const key = `catalog.config.${property.key || property.name}`
  const label = t(key)
  return label === key ? property.name : label
}

function propertyTooltip(property) {
  const key = `catalog.config.${property.key || property.name}Help`
  const help = t(key)
  const base = help === key ? property.name : help
  return property.type === 'regExp' ? `${base} — ${t('catalog.config.regExpHelp')}` : base
}

const REGEXP_RULES = [
  (v) => {
    if (!v) return true
    try {
      new RegExp(v, 'i')
      return true
    } catch {
      return t('catalog.config.invalidRegExp')
    }
  },
]

async function load() {
  const nodeId = props.catalog?.node?.id
  if (!nodeId) return
  const names = properties.value.map((p) => `names=${encodeURIComponent(p.name)}`).join('&')
  const url = `${APP_BASE}rest/service/prov/catalog/${encodeURIComponent(nodeId)}/configuration${names ? '?' + names : ''}`
  const data = await api.get(url, { silent: true })
  locations.value = Array.isArray(data?.locations) ? data.locations : []
  form.defaultLocation = data?.defaultLocation ?? null
  form.properties = { ...(data?.properties || {}) }
}

watch(
  () => [props.modelValue, props.catalog?.node?.id],
  () => { if (props.modelValue) load() },
  { immediate: true },
)

async function save() {
  const valid = await formRef.value?.validate()
  if (valid && valid.valid === false) return
  saving.value = true
  try {
    // Send every rendered property: blank values delete the configuration entry
    const sent = {}
    for (const p of properties.value) sent[p.name] = form.properties[p.name] || ''
    const payload = {
      node: props.catalog.node.id,
      defaultLocation: form.defaultLocation || null,
      properties: sent,
    }
    const res = await api.put(`${APP_BASE}rest/service/prov/catalog`, payload, { raw: true })
    if (res?.ok !== false) {
      emit('saved')
      open.value = false
    }
  } finally {
    saving.value = false
  }
}
</script>
