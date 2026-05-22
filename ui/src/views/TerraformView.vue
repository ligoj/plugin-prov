<template>
  <div class="terraform-stub">
    <v-alert type="warning" variant="tonal">
      <v-alert-title>{{ t('terraform.title') }}</v-alert-title>
      <p class="mt-2 mb-0">{{ t('terraform.notMigrated') }}</p>
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
    { title: t('terraform.title') },
  ])
})
</script>

<style scoped>
.terraform-stub {
  padding: 1rem;
}
</style>
