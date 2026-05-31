<template>
  <div class="terraform-view">
    <v-alert type="info" variant="tonal" density="compact" class="mb-4">
      {{ t('terraform.intro') }}
    </v-alert>

    <div class="d-flex align-center mb-4 ga-2">
      <h1 class="text-h5 mb-0">{{ t('terraform.title') }}</h1>
      <v-spacer />
      <v-btn icon size="small" variant="text" :loading="loading" :title="t('nav.refresh')" @click="reload">
        <v-icon>mdi-refresh</v-icon>
      </v-btn>
    </div>

    <v-skeleton-loader v-if="loading && !info" type="article" />

    <v-card v-else max-width="640" variant="outlined">
      <v-card-text>
        <!-- Installed version -->
        <div class="d-flex align-center ga-3 mb-4">
          <v-icon :color="installed && version ? 'success' : 'error'" size="28">
            {{ installed && version ? 'mdi-check-circle' : 'mdi-alert-circle' }}
          </v-icon>
          <div>
            <div class="text-caption text-medium-emphasis">{{ t('terraform.installedVersion') }}</div>
            <div v-if="installed && version" class="text-h6">{{ version }}</div>
            <div v-else-if="installed" class="text-body-2 text-error">{{ t('terraform.cmdError') }}</div>
            <div v-else class="text-body-2 text-error">{{ t('terraform.notInstalled') }}</div>
          </div>
        </div>

        <v-divider class="mb-4" />

        <!-- Latest available version -->
        <div class="d-flex align-center ga-3">
          <v-icon color="primary" size="28">mdi-cloud-download-outline</v-icon>
          <div class="flex-grow-1">
            <div class="text-caption text-medium-emphasis">{{ t('terraform.latestVersion') }}</div>
            <div v-if="lastVersion" class="text-h6 d-flex align-center ga-2">
              {{ lastVersion }}
              <a :href="changelogUrl" target="_blank" rel="noopener noreferrer" class="text-caption">
                {{ t('terraform.changelog') }}
              </a>
            </div>
            <div v-else class="text-body-2 text-error">{{ t('terraform.latestUnavailable') }}</div>
          </div>
          <!-- Install / update button: shown only when a latest version is
               known and it differs from the installed one (matches the
               legacy `terraform-install` visibility logic). -->
          <v-btn
            v-if="updateAvailable"
            color="primary"
            :loading="installing"
            prepend-icon="mdi-arrow-down-bold-circle"
            @click="install"
          >
            {{ t('terraform.install') }}
          </v-btn>
          <v-chip v-else-if="upToDate" color="success" size="small" variant="tonal">
            <v-icon start size="small">mdi-check</v-icon>
            {{ t('terraform.upToDate') }}
          </v-chip>
        </div>
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup>
/*
 * Terraform administration view — the Vue port of the legacy
 * `webjars/prov/terraform/terraform.js` admin screen. It manages the
 * server-side Terraform *binary* version (a global, node-level concern,
 * which is why it lives in the Administration menu, not a subscription
 * row):
 *
 *   GET  service/prov/terraform/install            → { version, installed, lastVersion }
 *   POST service/prov/terraform/version/<version>  → installs/updates, returns the same shape
 *
 * The per-subscription terraform *execute / destroy / network* flow is a
 * different legacy screen embedded in the quote editor — not this route.
 */
import { ref, computed, onMounted } from 'vue'
import { useApi, useAppStore, useErrorStore, useI18nStore } from '@ligoj/host'

const api = useApi()
const app = useAppStore()
const errorStore = useErrorStore()
const i18n = useI18nStore()
const t = i18n.t

const loading = ref(false)
const installing = ref(false)
const info = ref(null)

const installed = computed(() => !!info.value?.installed)
const version = computed(() => info.value?.version || null)
const lastVersion = computed(() => info.value?.lastVersion || null)

/** Latest changelog on GitHub for the available version (legacy link). */
const changelogUrl = computed(() =>
  lastVersion.value
    ? `https://github.com/hashicorp/terraform/blob/v${lastVersion.value}/CHANGELOG.md`
    : '#',
)

/** Installed version already matches the latest available one. */
const upToDate = computed(() => !!version.value && version.value === lastVersion.value)

/** A latest version is known and differs from what's installed (or
 *  nothing is installed yet) — an install/update is actionable. */
const updateAvailable = computed(() => !!lastVersion.value && !upToDate.value)

async function reload() {
  loading.value = true
  try {
    const data = await api.get('rest/service/prov/terraform/install')
    info.value = data || null
  } finally {
    loading.value = false
  }
}

async function install() {
  if (!lastVersion.value) return
  installing.value = true
  try {
    const data = await api.post(`rest/service/prov/terraform/version/${encodeURIComponent(lastVersion.value)}`)
    if (data == null) return
    info.value = data
    errorStore.success(t('terraform.installed', { version: data.version || lastVersion.value }))
  } finally {
    installing.value = false
  }
}

onMounted(async () => {
  app.setBreadcrumbs(
    [
      { title: t('nav.home'), to: '/' },
      { title: t('prov.title') },
      { title: t('terraform.title') },
    ],
    { refresh: reload },
  )
  await reload()
})
</script>

<style scoped>
.terraform-view {
  padding: 0.5rem;
}
</style>
