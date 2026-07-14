<template>
  <div class="quote-tags-editor">
    <div class="text-caption text-medium-emphasis mb-1">{{ t('prov.quote.tags.title') }}</div>
    <div class="d-flex flex-wrap align-center ga-1">
      <!-- `env:TST` → de-emphasised key ("env") + value ("TST"), no ':'. -->
      <v-chip v-for="tag in localTags" :key="tag.id" size="small" variant="tonal" closable
        :disabled="busy" @click:close="remove(tag)">
        <span v-if="tag.value" class="q-tag-key">{{ tag.name }}</span>{{ tag.value || tag.name }}
      </v-chip>
      <!-- Autocomplete pre-populated with every known tag in the quote
           (legacy `prov-tag.js#suggest`). `v-combobox` keeps the field
           free-text so the user can add brand-new keys / values that
           don't exist anywhere yet. -->
      <v-combobox :model-value="draft" :items="suggestions" :label="t('prov.quote.tags.addHint')"
        density="compact" variant="outlined" hide-details class="tag-add" :disabled="busy"
        no-filter clearable
        @update:model-value="(v) => (draft = v == null ? '' : String(v))"
        @keyup.enter="add" />
      <v-btn icon size="x-small" variant="text" :disabled="!draft || busy" :title="t('common.add')"
        @click="add">
        <v-icon size="small">mdi-plus</v-icon>
      </v-btn>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { useApi, useErrorStore, useI18nStore } from '@ligoj/host'

/**
 * Inline tag editor used by the resource dialogs in edit mode. Tags
 * have their own REST endpoints
 *   POST   service/prov/<sub>/tag         { name, value, type, resource }
 *   DELETE service/prov/<sub>/tag/<id>
 * — i.e. they're committed independently of the parent resource save,
 * so we apply changes optimistically and mirror them in the parent
 * model via `@change`.
 *
 * Only renders meaningfully when a resource already exists (the
 * backend rejects tags without a `resource` id). The parent should
 * hide this component in create mode.
 */
const props = defineProps({
  subscriptionId: { type: [Number, String], required: true },
  /** Resource type: 'instance' | 'database' | 'container' | 'function' | 'storage' | 'support'. */
  type: { type: String, required: true },
  resourceId: { type: [Number, String, null], default: null },
  /** Array of existing tags ({ id, name, value }). Read once on open. */
  modelValue: { type: Array, default: () => [] },
  /**
   * The entire `config.tags` map ({ [type]: { [resourceId]: Tag[] } }).
   * Powers the suggestion list — every distinct key and `key:value`
   * across the quote becomes a pick option. Falls back to an empty
   * suggestion list if absent.
   */
  allTagsByType: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue'])

const api = useApi()
const errorStore = useErrorStore()
const { t } = useI18nStore()

const localTags = ref([...(props.modelValue || [])])
const draft = ref('')
const busy = ref(false)

watch(() => props.modelValue, (v) => { localTags.value = [...(v || [])] })

/**
 * Flatten every tag in the quote into a unique sorted list of strings:
 *   - `<key>` for keys
 *   - `<key>:<value>` for full pairs
 * Mirrors the legacy `prov-tag.js#suggest` keyspace. Re-orders so
 * matches starting with the user's draft float to the top, then
 * substring matches, then everything else.
 */
const knownTags = computed(() => {
  const out = new Set()
  const src = props.allTagsByType || {}
  for (const byId of Object.values(src)) {
    if (!byId || typeof byId !== 'object') continue
    for (const list of Object.values(byId)) {
      if (!Array.isArray(list)) continue
      for (const tag of list) {
        if (tag?.name) {
          out.add(tag.name)
          if (tag.value) out.add(`${tag.name}:${tag.value}`)
        }
      }
    }
  }
  return [...out].sort()
})

/** Tags already on this resource — excluded from suggestions. */
const localStrings = computed(() => {
  const out = new Set()
  for (const t of localTags.value) {
    out.add(t.value ? `${t.name}:${t.value}` : t.name)
  }
  return out
})

const suggestions = computed(() => {
  const q = (draft.value || '').toLowerCase()
  const all = knownTags.value.filter((s) => !localStrings.value.has(s))
  if (!q) return all.slice(0, 30)
  const starts = []
  const contains = []
  for (const s of all) {
    const lower = s.toLowerCase()
    if (lower.startsWith(q)) starts.push(s)
    else if (lower.includes(q)) contains.push(s)
  }
  // Append `<key>:` shortcuts when the user has typed a key prefix —
  // matches the legacy "Then add keys starting with the key" branch.
  const keyShortcuts = []
  if (!q.includes(':')) {
    for (const k of knownTags.value) {
      if (!k.includes(':') && k.toLowerCase().startsWith(q) && !starts.includes(`${k}:`)) {
        keyShortcuts.push(`${k}:`)
      }
    }
  }
  return [...starts, ...keyShortcuts, ...contains].slice(0, 30)
})

function parseDraft(raw) {
  const trimmed = (raw || '').trim()
  if (!trimmed) return null
  const idx = trimmed.indexOf(':')
  if (idx === -1) return { name: trimmed, value: null }
  return { name: trimmed.slice(0, idx).trim(), value: trimmed.slice(idx + 1).trim() || null }
}

async function add() {
  if (!props.resourceId || busy.value) return
  const parsed = parseDraft(draft.value)
  if (!parsed?.name) return
  // Duplicate guard — exact name+value match.
  const dupe = localTags.value.find((t) => t.name === parsed.name && (t.value || null) === (parsed.value || null))
  if (dupe) {
    draft.value = ''
    return
  }
  busy.value = true
  try {
    const payload = { name: parsed.name, value: parsed.value, type: props.type, resource: Number(props.resourceId) }
    const id = await api.post(`rest/service/prov/${props.subscriptionId}/tag`, payload)
    if (id == null) return
    const next = [...localTags.value, { id, ...parsed }]
    localTags.value = next
    emit('update:modelValue', next)
    draft.value = ''
  } finally {
    busy.value = false
  }
}

async function remove(tag) {
  if (busy.value) return
  busy.value = true
  try {
    const result = await api.del(`rest/service/prov/${props.subscriptionId}/tag/${tag.id}`)
    if (result === null) return
    const next = localTags.value.filter((t) => t.id !== tag.id)
    localTags.value = next
    emit('update:modelValue', next)
    errorStore.success(t('prov.quote.tags.removed', { name: tag.name }))
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.quote-tags-editor {
  margin-top: 0.5rem;
}
.tag-add {
  flex: 1 1 200px;
  max-width: 260px;
}
/* Tag key ("env" in "env:TST") — thinner + smaller than the value, with a
 * small gap replacing the dropped ':' separator. */
.q-tag-key {
  font-size: 0.82em;
  font-weight: 400;
  opacity: 0.65;
  margin-right: 3px;
}
</style>
