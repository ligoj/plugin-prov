<template>
  <div class="quote-tags-editor">
    <div class="text-caption text-medium-emphasis mb-1">{{ t('prov.quote.tags.title') }}</div>
    <!-- Real multi-tag editor: chips with individual clear, free-text
         entry, and suggestions drawn from every tag already used in the
         quote. A combobox (autocomplete + free input) backs it so brand
         new keys/values can be typed, not only picked from the list. -->
    <v-combobox
      :model-value="selected"
      :items="suggestions"
      :label="t('prov.quote.tags.addHint')"
      :loading="busy"
      multiple
      chips
      closable-chips
      no-filter
      hide-no-data
      density="compact"
      variant="outlined"
      hide-details
      @update:model-value="onModelUpdate"
      @update:search="(v) => (search = v || '')"
    >
      <!-- Vuetify passes the raw value as the slot's `item` (not `item.raw`).
           `env:TST` → de-emphasised key ("env") + value ("TST"), no ':'. -->
      <template #chip="{ props: chipProps, item }">
        <v-chip v-bind="chipProps" size="small" variant="tonal">
          <span v-if="splitTag(item).value" class="q-tag-key">{{ splitTag(item).name }}</span>{{ splitTag(item).value || splitTag(item).name }}
        </v-chip>
      </template>
    </v-combobox>
  </div>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { useApi, useErrorStore, useI18nStore } from '@ligoj/host'

/**
 * Inline tag editor used by the resource dialogs in edit mode. Tags have
 * their own REST endpoints
 *   POST   service/prov/<sub>/tag         { name, value, type, resource }
 *   DELETE service/prov/<sub>/tag/<id>
 * — committed independently of the parent resource save. The combobox
 * model is diffed against the current tags on every change; the resulting
 * add/remove delta is persisted, then the UI reconciles to the
 * authoritative list (reverting anything the backend rejected).
 *
 * Only meaningful once a resource exists (the backend rejects tags without
 * a `resource` id) — the parent hides this component in create mode.
 */
const props = defineProps({
  subscriptionId: { type: [Number, String], required: true },
  /** Resource type: 'instance' | 'database' | 'container' | 'function' | 'storage' | 'support'. */
  type: { type: String, required: true },
  resourceId: { type: [Number, String, null], default: null },
  /** Array of existing tags ({ id, name, value }). */
  modelValue: { type: Array, default: () => [] },
  /**
   * The entire `config.tags` map ({ [type]: { [resourceId]: Tag[] } }).
   * Powers the suggestion list — every distinct key and `key:value`
   * across the quote becomes a pick option.
   */
  allTagsByType: { type: Object, default: () => ({}) },
})
const emit = defineEmits(['update:modelValue'])

const api = useApi()
const errorStore = useErrorStore()
const { t } = useI18nStore()

/** Authoritative tag objects ({ id, name, value }) mirroring the backend. */
const localTags = ref([...(props.modelValue || [])])
/** Combobox model — the tag display strings ("env:TST" / "owner"). */
const selected = ref(localTags.value.map(tagString))
const search = ref('')
const busy = ref(false)

watch(() => props.modelValue, (v) => {
  localTags.value = [...(v || [])]
  selected.value = localTags.value.map(tagString)
})

/** `{ name, value }` → its display/wire string. */
function tagString(tag) {
  return tag.value ? `${tag.name}:${tag.value}` : tag.name
}

/** Display string → `{ name, value }`, split on the first ':'. */
function splitTag(str) {
  const s = String(str ?? '')
  const idx = s.indexOf(':')
  return idx === -1 ? { name: s, value: null } : { name: s.slice(0, idx), value: s.slice(idx + 1) }
}

/** Parse user input into a persisted tag ({ name, value }); empty value → null. */
function parseInput(raw) {
  const trimmed = (raw || '').trim()
  if (!trimmed) return null
  const idx = trimmed.indexOf(':')
  if (idx === -1) return { name: trimmed, value: null }
  return { name: trimmed.slice(0, idx).trim(), value: trimmed.slice(idx + 1).trim() || null }
}

/* Suggestions: every distinct key and key:value across the quote, minus
 * the tags already on this resource. Ranked so prefix matches float up,
 * then substring matches; `<key>:` shortcuts are offered while typing a
 * bare key. Mirrors the legacy prov-tag.js#suggest keyspace. */
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

const selectedSet = computed(() => new Set(selected.value))

const suggestions = computed(() => {
  const q = (search.value || '').toLowerCase()
  const all = knownTags.value.filter((s) => !selectedSet.value.has(s))
  if (!q) return all.slice(0, 30)
  const starts = []
  const contains = []
  for (const s of all) {
    const lower = s.toLowerCase()
    if (lower.startsWith(q)) starts.push(s)
    else if (lower.includes(q)) contains.push(s)
  }
  // `<key>:` shortcuts when the user has typed a bare key prefix.
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

/**
 * Diff the combobox model against the current tags and persist the
 * add/remove delta. UI updates optimistically, then reconciles to the
 * authoritative `localTags` (reverting anything the backend rejected).
 */
async function onModelUpdate(next) {
  const nextStrings = (next || []).map((v) => String(v).trim()).filter(Boolean)
  const prevSet = new Set(selected.value)
  const nextSet = new Set(nextStrings)
  const added = nextStrings.filter((s) => !prevSet.has(s))
  const removed = selected.value.filter((s) => !nextSet.has(s))
  if (!added.length && !removed.length) return
  selected.value = nextStrings
  search.value = ''
  busy.value = true
  // `localTags` is reassigned only on a successful add/remove, so a change
  // of reference tells us whether anything actually persisted.
  const before = localTags.value
  try {
    for (const s of removed) {
      const tag = localTags.value.find((tg) => tagString(tg) === s)
      if (tag) await removeTag(tag)
    }
    for (const s of added) {
      await addTag(s)
    }
  } finally {
    busy.value = false
    // Reconcile to the backend truth: drops rejected adds, restores
    // rejected removes, and collapses any duplicate the user typed.
    selected.value = localTags.value.map(tagString)
    if (localTags.value !== before) emit('update:modelValue', localTags.value)
  }
}

async function addTag(str) {
  if (!props.resourceId) return
  const parsed = parseInput(str)
  if (!parsed?.name) return
  // Duplicate guard — exact name+value already present.
  if (localTags.value.some((t) => t.name === parsed.name && (t.value || null) === (parsed.value || null))) return
  const payload = { name: parsed.name, value: parsed.value, type: props.type, resource: Number(props.resourceId) }
  const id = await api.post(`rest/service/prov/${props.subscriptionId}/tag`, payload)
  if (id == null) return
  localTags.value = [...localTags.value, { id, ...parsed }]
}

async function removeTag(tag) {
  const result = await api.del(`rest/service/prov/${props.subscriptionId}/tag/${tag.id}`)
  if (result === null) return
  localTags.value = localTags.value.filter((t) => t.id !== tag.id)
  errorStore.success(t('prov.quote.tags.removed', { name: tag.name }))
}
</script>

<style scoped>
.quote-tags-editor {
  margin-top: 0.5rem;
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
