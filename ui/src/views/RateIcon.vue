<template>
  <span v-if="visual" class="rate-icon">
    <v-icon :icon="visual.icon" :color="visual.color" :size="size" />
    <v-tooltip activator="parent" location="top">
      <div class="rate-tip">
        <strong>{{ code }}</strong>
        <span>{{ t(descKey) }}</span>
      </div>
    </v-tooltip>
  </span>
</template>

<script setup>
// A single star glyph tinted to represent a Rate.java level, with a tooltip
// carrying the raw code and its explanation. Reusable across the rate/latency
// inputs and (later) table cells. Renders nothing for an empty/unknown rate.
import { computed } from 'vue'
import { useI18nStore } from '@ligoj/host'
import { rateVisual, rateDescKey } from '../rateCatalog.js'

const props = defineProps({
  /** Rate code, any case (e.g. "BEST", "worst"). */
  rate: { type: String, default: '' },
  size: { type: [String, Number], default: 'small' },
})

const { t } = useI18nStore()
const visual = computed(() => rateVisual(props.rate))
const code = computed(() => String(props.rate || '').toUpperCase())
const descKey = computed(() => rateDescKey(props.rate))
</script>

<style scoped>
.rate-icon {
  display: inline-flex;
  align-items: center;
}
.rate-tip {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.rate-tip strong {
  font-weight: 700;
}
</style>
