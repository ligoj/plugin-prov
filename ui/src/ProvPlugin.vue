<template>
  <div class="plugin-prov">
    <h2>{{ t('prov.title') }}</h2>
    <p>{{ t('prov.shellHint') }}</p>
  </div>
</template>

<script setup>
import { useI18nStore } from '@ligoj/host'
const { t } = useI18nStore()
</script>

<style scoped>
.plugin-prov {
  padding: 1rem;
}
</style>
