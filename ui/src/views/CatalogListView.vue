<template>
  <div class="catalog-stub">
    <v-alert type="info" variant="tonal" class="mb-4">
      {{ t('catalog.intro') }}
    </v-alert>
    <v-alert type="warning" variant="tonal">
      <v-alert-title>{{ t('catalog.title') }}</v-alert-title>
      <p class="mt-2 mb-0">
        Vue port pending — see <code>webjars/prov/catalog/</code> in the
        plugin JAR for the legacy AMD implementation. The plugin's
        <code>service.requestCatalogUpdate(nodeId)</code> is already
        callable for programmatic catalog refreshes.
      </p>
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
    { title: t('catalog.title') },
  ])
})
</script>

<style scoped>
.catalog-stub {
  padding: 1rem;
}
</style>
