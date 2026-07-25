<template>
  <div class="qi">
    <span v-if="providerNode" class="qi-provider">
      <NodeIcon :node="providerNode" />
      <v-tooltip activator="parent" location="bottom">{{ providerNode.name || providerNode.id }}</v-tooltip>
    </span>
    <div class="qi-txt">
      <div class="qi-row">
        <h1 class="qi-name">{{ config?.name || fallbackName }}</h1>

        <!-- Configured default location — flag + localized name, with the
             LocationLabel rich tooltip (country, continent, map). -->
        <LocationLabel v-if="config?.location" :location="config.location" class="qi-chip" />

        <!-- Configured default usage: name + rate/commitment summary, or the
             "always up" default when none is set. -->
        <span v-if="config" class="qi-chip qi-usage">
          <v-icon size="13">mdi-clock-outline</v-icon>
          {{ usageLabel }}
          <v-tooltip activator="parent" location="bottom" max-width="340">
            <div class="font-weight-bold">{{ t('prov.quote.fields.usage') }} — {{ usageLabel }}</div>
            <div>{{ config.usage ? t('prov.quote.usage.about') : t('prov.quote.usage.defaultNote') }}</div>
          </v-tooltip>
        </span>

        <!-- Edit sits at the end of the identity group. -->
        <v-btn icon size="small" variant="text" class="qi-edit" @click="emit('edit')">
          <v-icon size="small">mdi-pencil</v-icon>
          <v-tooltip activator="parent" location="bottom" max-width="300">
            <div class="font-weight-bold">{{ t('prov.quote.edit') }}</div>
            <div>{{ t('prov.quote.edit.tip') }}</div>
          </v-tooltip>
        </v-btn>
      </div>
      <span v-if="config?.description" class="qi-desc">
        {{ config.description }}
        <v-tooltip activator="parent" location="bottom" max-width="420">{{ config.description }}</v-tooltip>
      </span>
    </div>
  </div>
</template>

<script setup>
// Quote identity block: provider icon, name + description, and the quote-level
// defaults every resource inherits (location with its flag, usage with its
// rate) — closed by the edit pencil.
import { computed } from 'vue'
import { useI18nStore, NodeIcon } from '@ligoj/host'
import LocationLabel from './LocationLabel.vue'
import { usageSummary } from '../usageCatalog.js'

const props = defineProps({
  /** Quote configuration block. */
  config: { type: Object, default: null },
  /** Provider (tool) node for the icon. */
  providerNode: { type: Object, default: null },
  /** Shown when the quote has no name yet (e.g. the subscription id). */
  fallbackName: { type: [String, Number], default: '' },
})
const emit = defineEmits(['edit'])

const { t } = useI18nStore()

const usageLabel = computed(() => {
  const usage = props.config?.usage
  if (!usage) return '100%'
  const summary = usageSummary(usage)
  return summary ? `${usage.name} · ${summary}` : usage.name
})
</script>

<style scoped>
.qi {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.qi-provider {
  width: 46px;
  height: 46px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--pill);
  border: var(--border-w) var(--lj-border-style, solid) var(--border-c);
}

.qi-provider :deep(img),
.qi-provider :deep(.v-icon) {
  max-width: 26px;
  max-height: 26px;
}

.qi-txt {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.qi-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.qi-name {
  font-size: 22px;
  font-weight: var(--bold);
  color: var(--ink);
  line-height: 1.2;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Quote-level defaults as quiet pills beside the name. */
.qi-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-3);
  background: var(--pill);
  border-radius: 999px;
  padding: 3px 10px;
  cursor: help;
}

.qi-edit {
  color: var(--ink-3);
}

.qi-desc {
  display: inline-block;
  align-self: flex-start;
  margin-top: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-3);
  background: var(--pill);
  border-radius: 999px;
  padding: 2px 10px;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
