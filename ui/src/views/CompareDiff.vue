<template>
  <span v-if="show" class="cmp-diff" :class="chipClass">
    <template v-if="errored || !cs">
      <v-icon size="13">mdi-close-octagon-outline</v-icon>
    </template>
    <template v-else>
      <v-icon size="11">{{ meta.icon }}</v-icon>{{ formatDiffPct(pct) }}
    </template>

    <!-- Rich comparison detail. -->
    <v-tooltip activator="parent" location="top" open-delay="120" content-class="cmp-tip">
      <div class="cmp-tip-body">
        <div class="cmp-tip-head">
          <strong>{{ row.name }}</strong>
          <span class="cmp-tip-vs">{{ t('prov.quote.compare.vs', { name: csName }) }}</span>
        </div>

        <template v-if="errored || !cs">
          <div class="cmp-tip-na">
            <v-icon size="13" class="me-1">mdi-close-octagon-outline</v-icon>{{ t('prov.quote.compare.notAvailable', { name: csName }) }}
          </div>
          <div class="cmp-tip-na-cost">
            <span class="cmp-tip-key">{{ t('prov.quote.compare.msCol') }}</span>{{ fmt(msVal) }}
            <span class="cmp-tip-unchanged">· {{ t('prov.quote.compare.countedUnchanged') }}</span>
          </div>
        </template>

        <table v-else class="cmp-tip-tbl">
          <thead>
            <tr><th></th><th>{{ t('prov.quote.compare.msCol') }}</th><th class="cmp-cs">{{ csName }}</th></tr>
          </thead>
          <tbody>
            <tr>
              <td>{{ t('prov.quote.cols.term') }}</td><td>{{ term(row) }}</td><td>{{ term(cs) }}</td>
            </tr>
            <tr>
              <td>{{ t('prov.quote.cols.type') }}</td><td>{{ typeName(row) }}</td><td>{{ typeName(cs) }}</td>
            </tr>
            <tr>
              <td>{{ t('prov.quote.cols.cpu') }}</td>
              <td><ResourceMicroBar v-if="typeCap(row, 'cpu')" :value="cap(row, 'cpu')" :max="typeCap(row, 'cpu')" :label="formatCpu(cap(row, 'cpu'))" :format="formatCpu" /><template v-else>{{ formatCpu(cap(row, 'cpu')) }}</template></td>
              <td><ResourceMicroBar v-if="typeCap(cs, 'cpu')" :value="cap(cs, 'cpu')" :max="typeCap(cs, 'cpu')" :label="formatCpu(cap(cs, 'cpu'))" :format="formatCpu" /><template v-else>{{ formatCpu(cap(cs, 'cpu')) }}</template></td>
            </tr>
            <tr>
              <td>{{ t('prov.quote.cols.ram') }}</td>
              <td><ResourceMicroBar v-if="typeCap(row, 'ram')" :value="cap(row, 'ram')" :max="typeCap(row, 'ram')" :label="formatRam(cap(row, 'ram'))" :format="formatRam" /><template v-else>{{ formatRam(cap(row, 'ram')) }}</template></td>
              <td><ResourceMicroBar v-if="typeCap(cs, 'ram')" :value="cap(cs, 'ram')" :max="typeCap(cs, 'ram')" :label="formatRam(cap(cs, 'ram'))" :format="formatRam" /><template v-else>{{ formatRam(cap(cs, 'ram')) }}</template></td>
            </tr>
            <tr>
              <td>{{ t('prov.quote.cols.location') }}</td><td>{{ loc(row) }}</td><td>{{ loc(cs) }}</td>
            </tr>
            <tr class="cmp-tip-cost">
              <td>{{ t('prov.quote.cols.cost') }}</td>
              <td>{{ fmt(msVal) }}</td>
              <td>{{ fmt(csVal) }} <span class="cmp-tip-pct" :class="`text-${meta.color}`">{{ formatDiffPct(pct) }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </v-tooltip>
  </span>
</template>

<script setup>
// Per-row MS↔CS comparison marker: a signed % delta (or a "not reproduced"
// octagon) with a rich tooltip contrasting the two subscriptions' resolved
// term / type / vCPU / RAM (utilisation bars) / location / cost.
import { computed } from 'vue'
import { useI18nStore } from '@ligoj/host'
import { formatCost, formatCo2, formatCpu, formatRam } from '../quoteFormatters.js'
import { diffRatio, diffMeta, formatDiffPct } from '../compareApi.js'
import ResourceMicroBar from './ResourceMicroBar.vue'

const props = defineProps({
  /** The MS resource row. */
  row: { type: Object, required: true },
  /** The matching CS resource, or null when it could not be reproduced. */
  cs: { type: Object, default: null },
  /** True when the CS could not reproduce this resource (recorded error). */
  errored: { type: Boolean, default: false },
  /** 'cost' or 'co2'. */
  metric: { type: String, default: 'cost' },
  currency: { type: Object, default: null },
  /** Display name of the compared subscription. */
  csName: { type: String, default: '' },
})

const { t } = useI18nStore()

const isCo2 = computed(() => props.metric === 'co2')
const valueOf = (r) => (r == null ? null : (isCo2.value ? (r.co2 ?? r.maxCo2) : r.cost))
const msVal = computed(() => valueOf(props.row))
const csVal = computed(() => valueOf(props.cs))
const pct = computed(() => diffRatio(msVal.value, csVal.value))
const meta = computed(() => diffMeta(pct.value))
const show = computed(() => props.errored || (props.cs != null && pct.value != null))
const chipClass = computed(() => (props.errored || !props.cs ? 'cmp-diff-na' : `text-${meta.value.color}`))

const fmt = (v) => (v == null ? '—' : isCo2.value ? formatCo2(v) : formatCost(v, props.currency || { unit: '$', rate: 1 }))
const term = (r) => r?.price?.term?.name || '—'
const typeName = (r) => r?.price?.type?.name || '—'
const cap = (r, k) => r?.[k] ?? r?.price?.type?.[k]
const typeCap = (r, k) => r?.price?.type?.[k]
const loc = (r) => r?.location?.name || r?.price?.location?.name || '—'
</script>

<style scoped>
.cmp-diff {
  display: inline-flex;
  align-items: center;
  font-size: 10.5px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  cursor: help;
}
.cmp-diff-na {
  color: rgb(var(--v-theme-warning));
}
</style>

<style>
/* Tooltip content — unscoped (teleported outside the component). */
.cmp-tip .cmp-tip-body {
  min-width: 240px;
  max-width: 380px;
}
.cmp-tip .cmp-tip-head {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  margin-bottom: 4px;
}
.cmp-tip .cmp-tip-vs {
  font-size: 0.8em;
  opacity: 0.7;
}
.cmp-tip .cmp-tip-na {
  color: rgb(var(--v-theme-warning));
  font-weight: 600;
  display: flex;
  align-items: center;
}
.cmp-tip .cmp-tip-na-cost {
  font-size: 0.85em;
  margin-top: 3px;
}
.cmp-tip .cmp-tip-unchanged {
  opacity: 0.7;
}
.cmp-tip .cmp-tip-key {
  opacity: 0.6;
  margin-right: 0.35rem;
}
.cmp-tip .cmp-tip-tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.82em;
}
.cmp-tip .cmp-tip-tbl th {
  text-align: right;
  font-weight: 600;
  padding: 1px 0.4rem;
  opacity: 0.75;
}
.cmp-tip .cmp-tip-tbl th.cmp-cs {
  color: rgb(var(--v-theme-primary));
}
.cmp-tip .cmp-tip-tbl td {
  padding: 2px 0.4rem;
  text-align: right;
  white-space: nowrap;
}
.cmp-tip .cmp-tip-tbl td:first-child {
  text-align: left;
  opacity: 0.6;
}
.cmp-tip .cmp-tip-cost td {
  border-top: 1px solid rgba(var(--v-theme-on-surface), 0.15);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.cmp-tip .cmp-tip-pct {
  font-weight: 800;
  margin-left: 0.3rem;
}

/* Summary tooltip (header total) — shares the .cmp-tip content-class. */
.cmp-tip .cmp-sum {
  min-width: 260px;
}
.cmp-tip .cmp-cs {
  color: rgb(var(--v-theme-primary));
}
.cmp-tip .cmp-sum-tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.82em;
}
.cmp-tip .cmp-sum-tbl th {
  text-align: right;
  font-weight: 600;
  padding: 1px 0.5rem;
  opacity: 0.75;
}
.cmp-tip .cmp-sum-tbl th:first-child {
  text-align: left;
}
.cmp-tip .cmp-sum-tbl td {
  padding: 2px 0.5rem;
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.cmp-tip .cmp-sum-tbl td:first-child {
  text-align: left;
  display: flex;
  align-items: center;
}
.cmp-tip .cmp-sum-unm {
  color: rgb(var(--v-theme-warning));
  font-weight: 700;
}
.cmp-tip .cmp-sum-total td {
  border-top: 1px solid rgba(var(--v-theme-on-surface), 0.15);
  font-weight: 700;
}
.cmp-tip .cmp-sum-note {
  display: flex;
  align-items: center;
  margin-top: 6px;
  font-size: 0.8em;
  color: rgb(var(--v-theme-warning));
}
</style>
