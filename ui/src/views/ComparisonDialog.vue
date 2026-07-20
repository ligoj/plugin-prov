<template>
  <v-dialog :model-value="modelValue" fullscreen transition="dialog-bottom-transition" @update:model-value="close">
    <v-card class="cmp-card">
      <v-toolbar density="comfortable" color="surface">
        <v-icon class="ms-3 me-2" color="primary">mdi-scale-balance</v-icon>
        <v-toolbar-title class="text-subtitle-1 font-weight-medium">{{ t('prov.quote.compare.title') }}</v-toolbar-title>
        <v-spacer />
        <v-btn-toggle v-model="metric" mandatory density="compact" variant="outlined" divided class="me-2">
          <v-btn size="small" value="cost"><v-icon size="small" start>mdi-currency-usd</v-icon>{{ t('prov.quote.viewMode.cost') }}</v-btn>
          <v-btn size="small" value="co2"><v-icon size="small" start>mdi-leaf</v-icon>{{ t('prov.quote.viewMode.co2') }}</v-btn>
        </v-btn-toggle>
        <v-btn icon variant="text" :title="t('common.close')" @click="close"><v-icon>mdi-close</v-icon></v-btn>
      </v-toolbar>

      <v-card-text class="cmp-body">
        <!-- Basis note: this is an apples-to-apples on-demand comparison. -->
        <v-alert type="info" variant="tonal" density="compact" class="mb-4 cmp-note">
          {{ t('prov.quote.compare.note') }}
        </v-alert>

        <div v-if="loadingProviders" class="d-flex align-center ga-2 text-medium-emphasis">
          <v-progress-circular indeterminate size="18" width="2" /> {{ t('common.loading') }}
        </div>

        <v-alert v-else-if="!resources.length" type="warning" variant="tonal" density="compact">
          {{ t('prov.quote.compare.empty') }}
        </v-alert>

        <v-alert v-else-if="providers.length < 2" type="warning" variant="tonal" density="compact">
          {{ t('prov.quote.compare.noProviders') }}
        </v-alert>

        <template v-else>
          <!-- Provider picker + run. -->
          <div class="cmp-pick mb-4">
            <span class="cmp-pick-label">{{ t('prov.quote.compare.selectNodes') }}</span>
            <v-chip
              v-for="p in providers"
              :key="p.toolId"
              :color="p.selected ? 'primary' : undefined"
              :variant="p.selected ? 'flat' : 'outlined'"
              size="small"
              class="cmp-pick-chip"
              @click="p.selected = !p.selected"
            >
              <NodeIcon :node="p.toolId" class="me-1" />
              {{ p.name }}
              <v-chip v-if="p.current" size="x-small" variant="tonal" class="ms-1">{{ t('prov.quote.compare.baseline') }}</v-chip>
            </v-chip>
            <v-spacer />
            <v-btn color="primary" variant="elevated" size="small" :loading="running" :disabled="selectedProviders.length < 2"
              prepend-icon="mdi-play" @click="run">
              {{ t('prov.quote.compare.run') }}
            </v-btn>
          </div>

          <v-progress-linear v-if="running" :model-value="progressPct" color="primary" height="6" rounded class="mb-2" />
          <div v-if="running" class="text-caption text-medium-emphasis mb-3">
            {{ t('prov.quote.compare.progress', { done: progress.done, total: progress.total }) }}
          </div>

          <!-- Matrix. -->
          <div v-if="matrixReady" class="cmp-matrix-wrap">
            <table class="cmp-matrix">
              <thead>
                <tr>
                  <th class="cmp-sticky cmp-th-res">{{ t('prov.quote.compare.resource') }}</th>
                  <th v-for="pid in matrixProviders" :key="pid" class="cmp-th-prov" :class="{ 'cmp-winner-col': summary.bestPid === pid }">
                    <div class="cmp-prov-head">
                      <NodeIcon :node="pid" />
                      <span class="cmp-prov-name">{{ providerName(pid) }}</span>
                      <v-icon v-if="summary.bestPid === pid" size="14" color="success" :title="t('prov.quote.compare.cheapest')">mdi-trophy</v-icon>
                    </div>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.key">
                  <td class="cmp-sticky cmp-td-res">
                    <v-icon size="14" class="me-1 text-medium-emphasis">{{ typeIcon(row.type) }}</v-icon>{{ row.name }}
                  </td>
                  <td v-for="pid in matrixProviders" :key="pid" class="cmp-td-val"
                    :class="{ 'cmp-best': rowBest(row.key) === pid, 'cmp-none': !cell(row, pid) }">
                    <template v-if="cell(row, pid)">{{ fmt(cell(row, pid)[metric]) }}</template>
                    <template v-else>{{ t('prov.quote.compare.noMatch') }}</template>
                  </td>
                </tr>
              </tbody>
              <tfoot>
                <tr class="cmp-total">
                  <td class="cmp-sticky cmp-td-res">{{ t('prov.quote.compare.total') }}</td>
                  <td v-for="pid in matrixProviders" :key="pid" class="cmp-td-val"
                    :class="{ 'cmp-best': summary.bestPid === pid }">
                    <div>{{ fmt(summary.totals[pid]?.value) }}</div>
                    <div v-if="summary.totals[pid]?.missing" class="cmp-missing" :title="t('prov.quote.compare.incomplete')">
                      +{{ summary.totals[pid].missing }} {{ t('prov.quote.compare.unmatched') }}
                    </div>
                    <div v-else-if="pid !== baselinePid && baselineTotal != null" class="cmp-delta" :class="deltaClass(pid)">
                      {{ deltaLabel(pid) }}
                    </div>
                  </td>
                </tr>
                <tr class="cmp-bob">
                  <td class="cmp-sticky cmp-td-res">
                    {{ t('prov.quote.compare.bestOfBreed') }}
                    <HelpTip :text="t('prov.quote.compare.bestOfBreedHelp')" />
                  </td>
                  <td class="cmp-td-val cmp-bob-val" :colspan="matrixProviders.length">
                    {{ fmt(summary.bestOfBreed.value) }}
                    <span v-if="summary.bestOfBreed.missing" class="cmp-missing">
                      (+{{ summary.bestOfBreed.missing }} {{ t('prov.quote.compare.unmatched') }})
                    </span>
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </template>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<script setup>
// Cross-provider comparison (feature 02). Re-prices the quote's compute +
// database resources against every selected provider's catalog by reusing the
// existing per-type lookup endpoints — one call per (resource × provider),
// concurrency-limited. Comparison is on-demand and provider-neutral (no region
// / usage / commitment), so it is a fair apples-to-apples baseline.
import { ref, computed, watch } from 'vue'
import { useApi, useI18nStore, NodeIcon } from '@ligoj/host'
import HelpTip from './HelpTip.vue'
import { formatCost, formatCo2, TAB_TYPES } from '../quoteFormatters.js'
import {
  comparableResources,
  buildLookupParams,
  summarizeComparison,
  mapLimit,
} from '../comparisonHelpers.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** Quote configuration block (already loaded by QuoteView). */
  config: { type: Object, default: null },
  /** The current subscription id (route param). */
  subscriptionId: { type: [Number, String], default: null },
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18nStore()
const api = useApi()

const metric = ref('cost')
const currency = computed(() => props.config?.currency || { unit: '$', rate: 1 })
const fmt = (v) => (v == null ? '—' : metric.value === 'co2' ? formatCo2(v) : formatCost(v, currency.value))

const TYPE_ICON = Object.fromEntries(TAB_TYPES.map((x) => [x.key, x.icon]))
const typeIcon = (key) => TYPE_ICON[key] || 'mdi-cube-outline'

/* ---- Resources to compare (compute + database) ---- */
const resources = computed(() => comparableResources(props.config))

/* ---- Provider (tool-node) picker ---- */
const loadingProviders = ref(false)
const providers = ref([]) // [{ toolId, name, provider, subId, current, selected }]

const toolOf = (nodeId) => String(nodeId).split(':').slice(0, 3).join(':')

async function loadProviders() {
  loadingProviders.value = true
  try {
    const data = await api.get('rest/subscription')
    const subs = Array.isArray(data?.subscriptions) ? data.subscriptions : []
    const nodes = Array.isArray(data?.nodes) ? data.nodes : []
    const nameById = Object.fromEntries(nodes.map((n) => [n.id, n.name]))
    const curId = String(props.subscriptionId)
    // The catalog is keyed on the tool node, so collapse subscriptions to one
    // representative per provider; prefer the current subscription's own.
    const byTool = new Map()
    for (const s of subs) {
      const nodeId = typeof s.node === 'string' ? s.node : s.node?.id
      if (!nodeId || !nodeId.startsWith('service:prov:')) continue
      const toolId = toolOf(nodeId)
      const isCurrent = String(s.id) === curId
      const existing = byTool.get(toolId)
      if (!existing || isCurrent) {
        byTool.set(toolId, {
          toolId,
          name: nameById[toolId] || toolId.split(':')[2] || toolId,
          provider: toolId.split(':')[2],
          subId: s.id,
          current: existing?.current || isCurrent,
          selected: true,
        })
      }
    }
    providers.value = [...byTool.values()].sort((a, b) => (b.current - a.current) || a.name.localeCompare(b.name))
  } finally {
    loadingProviders.value = false
  }
}

const selectedProviders = computed(() => providers.value.filter((p) => p.selected))
const providerName = (toolId) => providers.value.find((p) => p.toolId === toolId)?.name || toolId
const baselinePid = computed(() => providers.value.find((p) => p.current)?.toolId || null)

/* ---- Run the N×M lookups ---- */
const running = ref(false)
const progress = ref({ done: 0, total: 0 })
const progressPct = computed(() => (progress.value.total ? (progress.value.done / progress.value.total) * 100 : 0))
const rows = ref([])                 // [{ key, name, type, byProvider }]
const matrixProviders = ref([])      // provider ids (columns), frozen at run time

async function run() {
  const cols = selectedProviders.value
  if (cols.length < 2 || !resources.value.length) return
  running.value = true
  rows.value = []
  const res = resources.value
  const tasks = []
  for (const r of res) for (const p of cols) tasks.push({ r, p })
  progress.value = { done: 0, total: tasks.length }

  // Seed the row scaffolding so cells fill in place.
  const byKey = new Map(res.map((r) => [r.key, { key: r.key, name: r.name, type: r.type, byProvider: {} }]))

  await mapLimit(tasks, 6, async ({ r, p }) => {
    const cellVal = await lookup(p.subId, r)
    byKey.get(r.key).byProvider[p.toolId] = cellVal
    progress.value = { ...progress.value, done: progress.value.done + 1 }
  })

  rows.value = res.map((r) => byKey.get(r.key))
  matrixProviders.value = cols.map((p) => p.toolId)
  running.value = false
}

/** One lookup against a target subscription; returns `{ cost, co2 }` or null. */
async function lookup(subId, resource) {
  try {
    const qs = new URLSearchParams(buildLookupParams(resource.row, resource.type))
    const data = await api.get(`rest/service/prov/${subId}/${resource.type}-lookup/?${qs}`, { silent: true })
    const hit = Array.isArray(data) ? data[0] : data
    if (!hit?.price) return null
    return { cost: Number(hit.cost) || 0, co2: Number(hit.co2) || 0, type: hit.price?.type?.name }
  } catch {
    return null
  }
}

const matrixReady = computed(() => rows.value.length > 0 && matrixProviders.value.length > 0)
const cell = (row, pid) => row.byProvider?.[pid] || null
const rowBest = (key) => summary.value.perRow.find((r) => r.key === key)?.bestPid || null

const summary = computed(() => summarizeComparison(rows.value, matrixProviders.value, metric.value))

/* ---- Savings vs the current provider ---- */
const baselineTotal = computed(() => {
  const pid = baselinePid.value
  const t0 = pid && summary.value.totals[pid]
  return t0 && t0.missing === 0 ? t0.value : null
})
function deltaLabel(pid) {
  const total = summary.value.totals[pid]
  if (!total || total.missing || baselineTotal.value == null) return ''
  const d = total.value - baselineTotal.value
  if (Math.abs(d) < 1e-9) return t('prov.quote.compare.same')
  const abs = fmt(Math.abs(d))
  return d < 0 ? t('prov.quote.compare.save', { amount: abs }) : `+${abs}`
}
function deltaClass(pid) {
  const total = summary.value.totals[pid]
  if (!total || total.missing || baselineTotal.value == null) return ''
  const d = total.value - baselineTotal.value
  return d < -1e-9 ? 'cmp-cheaper' : d > 1e-9 ? 'cmp-pricier' : ''
}

function close() {
  emit('update:modelValue', false)
}

// Load providers each time the dialog opens; reset any stale matrix.
watch(() => props.modelValue, (open) => {
  if (open) {
    rows.value = []
    matrixProviders.value = []
    loadProviders()
  }
})

defineExpose({ providers, rows, summary, run })
</script>

<style scoped>
.cmp-body {
  max-width: 1400px;
  margin: 0 auto;
  padding-top: 1rem;
}
.cmp-note {
  font-size: 0.85rem;
}
.cmp-pick {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.cmp-pick-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(var(--v-theme-on-surface), 0.55);
  margin-right: 0.25rem;
}
.cmp-pick-chip {
  cursor: pointer;
}

.cmp-matrix-wrap {
  overflow-x: auto;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.12);
  border-radius: 8px;
}
.cmp-matrix {
  border-collapse: separate;
  border-spacing: 0;
  width: 100%;
  font-size: 0.9rem;
}
.cmp-matrix th,
.cmp-matrix td {
  padding: 0.5rem 0.85rem;
  white-space: nowrap;
  border-bottom: 1px solid rgba(var(--v-theme-on-surface), 0.08);
}
.cmp-th-prov {
  text-align: right;
  font-weight: 600;
}
.cmp-prov-head {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}
.cmp-td-val {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.cmp-td-res {
  font-weight: 500;
}
/* Sticky first column so the resource name stays visible while scrolling. */
.cmp-sticky {
  position: sticky;
  left: 0;
  z-index: 1;
  background: rgb(var(--v-theme-surface));
}
thead .cmp-sticky,
.cmp-winner-col {
  background: rgba(var(--v-theme-primary), 0.06);
}
.cmp-best {
  background: rgba(var(--v-theme-success), 0.14);
  font-weight: 700;
}
.cmp-none {
  color: rgba(var(--v-theme-on-surface), 0.4);
}
.cmp-total td,
.cmp-bob td {
  border-top: 2px solid rgba(var(--v-theme-on-surface), 0.15);
  font-weight: 700;
  border-bottom: none;
}
.cmp-bob-val {
  text-align: right;
  color: rgb(var(--v-theme-primary));
}
.cmp-missing {
  font-size: 0.72rem;
  font-weight: 500;
  color: rgb(var(--v-theme-warning));
}
.cmp-delta {
  font-size: 0.72rem;
  font-weight: 600;
}
.cmp-cheaper { color: rgb(var(--v-theme-success)); }
.cmp-pricier { color: rgb(var(--v-theme-error)); }
</style>
