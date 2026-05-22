<template>
  <div class="quote-stub">
    <v-alert type="warning" variant="tonal">
      <v-alert-title>{{ t('quote.title') }}</v-alert-title>
      <p class="mt-2 mb-0">{{ t('quote.notMigrated') }}</p>
    </v-alert>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useI18nStore, useAppStore } from '@ligoj/host'
const { t } = useI18nStore()
const appStore = useAppStore()
onMounted(() => {
  appStore.setBreadcrumbs([
    { title: t('nav.home'), to: '/' },
    { title: t('prov.title') },
    { title: t('quote.title') },
  ])
})
</script>

<style scoped>
.quote-stub {
  padding: 1rem;
}
</style>
