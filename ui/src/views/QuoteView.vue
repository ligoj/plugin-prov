<template>
  <div class="quote-view lj-surface">
    <v-skeleton-loader v-if="loading && !config" type="article, table" />

    <v-alert v-if="error" type="warning" variant="tonal" class="mb-4">{{ error }}</v-alert>

    <template v-if="config">
      <!-- Header — provider tile + quote identity on the left, the
           total-cost key figure + grouped tools on the right. Same
           actions as before (edit / period / refresh / refresh-prices /
           exports), only regrouped. -->
      <header class="q-head mb-4">
        <QuoteIdentity :config="config" :provider-node="providerNode" :fallback-name="subscriptionId" @edit="openEdit" />
        <v-spacer />
        <div class="q-cost" :class="{ 'q-cost--filtered': anyFilterActive }">
          <!-- No "monthly" caption: the active period is shown in the period
               selector, and its suffix would just duplicate it here. The value,
               the compare delta and the "unmatched" flag share one line so the
               summary stays compact. -->
          <div class="q-cost-line">
          <span v-if="anyFilterActive" class="q-cost-label">
            <v-icon size="12" class="q-cost-filter-ic">mdi-filter-variant</v-icon>
            <v-tooltip activator="parent" location="bottom" max-width="280">{{ t('prov.quote.totalFiltered') }}</v-tooltip>
          </span>
          <span class="q-cost-value">
            {{ viewMode === 'co2'
              ? formatCo2Range(scaleCost(displayedQuoteCo2, costPeriod))
              : formatCostRange(scaledCost(displayedQuoteCost), config.currency) }}
          </span>
          <span v-if="csTotalDiff != null" class="q-cost-diff" :class="`text-${diffMeta(csTotalDiff).color}`">
            <v-icon size="14">{{ diffMeta(csTotalDiff).icon }}</v-icon>{{ formatDiffPct(csTotalDiff) }}
            <!-- Rich summary: per-type resource count + cost for MS and CS. -->
            <v-tooltip activator="parent" location="bottom" open-delay="120" content-class="cmp-tip">
              <div class="cmp-sum">
                <div class="cmp-tip-head">
                  <strong>{{ t('prov.quote.compare.summaryTitle') }}</strong>
                  <span class="cmp-tip-vs">{{ t('prov.quote.compare.vs', { name: activeCsName }) }}</span>
                </div>
                <table class="cmp-sum-tbl">
                  <thead>
                    <tr><th></th><th>#</th><th>{{ t('prov.quote.compare.msCol') }}</th><th class="cmp-cs">{{ activeCsName }}</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in summaryRows" :key="row.type">
                      <td><v-icon size="12" class="me-1">{{ typeIcon(row.type) }}</v-icon>{{ tabLabel(row.type) }}</td>
                      <td>{{ row.count }}<span v-if="row.unmatched" class="cmp-sum-unm"> (−{{ row.unmatched }})</span></td>
                      <td>{{ fmtMetric(row.ms) }}</td>
                      <td>{{ fmtMetric(row.cs) }}</td>
                    </tr>
                    <tr class="cmp-sum-total">
                      <td>{{ t('prov.quote.compare.total') }}</td>
                      <td></td>
                      <td>{{ fmtMetric(compareSummary.msTotal) }}</td>
                      <td>{{ fmtMetric(compareSummary.csTotal) }} <span :class="`text-${diffMeta(csTotalDiff).color}`">{{ formatDiffPct(csTotalDiff) }}</span></td>
                    </tr>
                  </tbody>
                </table>
                <div v-if="compareSummary.unmatched" class="cmp-sum-note">
                  <v-icon size="13" color="warning" class="me-1">mdi-close-octagon-outline</v-icon>
                  {{ t('prov.quote.compare.unmatchedNote', { n: compareSummary.unmatched, cost: fmtMetric(compareSummary.unmatchedCost) }) }}
                </div>
              </div>
            </v-tooltip>
          </span>
          <v-chip v-if="compareSummary?.unmatched" size="x-small" color="warning" variant="tonal" class="q-cost-unmatched">
            <v-icon start size="12">mdi-close-octagon-outline</v-icon>{{ t('prov.quote.compare.unmatchedN', { n: compareSummary.unmatched }) }}
            <v-tooltip activator="parent" location="bottom">{{ t('prov.quote.compare.unmatchedNote', { n: compareSummary.unmatched, cost: fmtMetric(compareSummary.unmatchedCost) }) }}</v-tooltip>
          </v-chip>
          </div>
          <CarbonBar v-if="viewMode === 'co2'" :config="filteredConfig" :compare="csConfig" :cs-name="activeCsName" class="q-cost-eff" />
          <EfficiencyBar v-else :config="filteredConfig" :compare="csConfig" :cs-name="activeCsName" class="q-cost-eff" />
        </div>
        <div class="q-tools">
          <!-- Cost-period selector. Pure display — the backend stores
               monthly numbers; we just scale at render time. -->
          <v-menu>
            <template #activator="{ props: actProps }">
              <v-btn v-bind="actProps" size="small" variant="text">
                <v-icon size="small">mdi-clock-outline</v-icon>
                <span class="text-caption ml-1">/{{ t(`prov.quote.period.${costPeriod}Suffix`) }}</span>
              <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.period.title') }}</div>{{ t('prov.quote.period.tip') }}</v-tooltip></v-btn>
            </template>
            <v-list density="compact" min-width="160">
              <v-list-item v-for="p in COST_PERIODS" :key="p" :title="t(`prov.quote.period.${p}`)" @click="costPeriod = p">
                <template v-if="costPeriod === p" #append>
                  <v-icon size="x-small">mdi-check</v-icon>
                </template>
              </v-list-item>
            </v-list>
          </v-menu>
          <v-btn icon size="small" variant="text" :loading="refreshing" @click="reload">
            <v-icon>mdi-refresh</v-icon>
          <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.refresh') }}</div>{{ t('prov.quote.refresh.tip') }}</v-tooltip></v-btn>
          <!-- Recomputes prices against the latest provider catalog —
               the legacy `refreshCost` action. Distinct from "reload",
               which only re-fetches the configuration as-is. -->
          <v-btn icon size="small" variant="text" :loading="refreshingPrices" @click="refreshPrices">
            <v-icon>mdi-cash-sync</v-icon>
          <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.refreshPrices') }}</div>{{ t('prov.quote.refreshPrices.tip') }}</v-tooltip></v-btn>
          <!-- Tag-based cost allocation (client-side report in a dialog). -->
          <v-btn icon size="small" variant="text" @click="tagAllocDialog = true">
            <v-icon>mdi-tag-multiple-outline</v-icon>
          <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.tagAlloc.action') }}</div>{{ t('prov.quote.tagAlloc.tip') }}</v-tooltip></v-btn>
          <!-- Quote snapshots: named versions, diff and restore. -->
          <v-btn icon size="small" variant="text" :disabled="!subscriptionId" @click="snapshotDialog = true">
            <v-icon>mdi-history</v-icon>
          <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.snap.action') }}</div>{{ t('prov.quote.snap.tip') }}</v-tooltip></v-btn>
          <!-- Cross-provider comparison: pick a compared subscription (CS) to
               diff against, or manage the synchronized CS set. -->
          <v-menu>
            <template #activator="{ props: cmpProps }">
              <v-btn v-bind="cmpProps" size="small" variant="text" :disabled="!subscriptionId"
                :color="activeCs != null ? 'primary' : undefined">
                <v-icon size="small">mdi-scale-balance</v-icon>
                <span v-if="activeCsName" class="text-caption ml-1">{{ activeCsName }}</span>
              <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.compare.action') }}</div>{{ t('prov.quote.compare.tip') }}</v-tooltip></v-btn>
            </template>
            <v-list density="compact" min-width="220">
              <v-list-subheader>{{ t('prov.quote.compare.against') }}</v-list-subheader>
              <v-list-item :active="activeCs == null" @click="activeCs = null" prepend-icon="mdi-close-circle-outline">
                <v-list-item-title>{{ t('prov.quote.compare.off') }}</v-list-item-title>
              </v-list-item>
              <v-list-item v-for="cs in comparedList" :key="cs.subscription"
                :active="String(activeCs) === String(cs.subscription)" @click="activeCs = cs.subscription">
                <v-list-item-title>{{ cs.name || cs.subscription }}</v-list-item-title>
              </v-list-item>
              <v-divider />
              <v-list-item prepend-icon="mdi-cog-outline" @click="compareSetup = true">
                <v-list-item-title>{{ t('prov.quote.compare.manage') }}</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
          <!-- Exports — three pre-built backend endpoints. The path
               segment itself is the suggested filename so the backend
               can mirror it as Content-Disposition. -->
          <v-menu>
            <template #activator="{ props: actProps }">
              <v-btn v-bind="actProps" icon size="small" variant="text" :disabled="!subscriptionId">
                <v-icon>mdi-download</v-icon>
              <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.exports') }}</div>{{ t('prov.quote.exports.tip') }}</v-tooltip></v-btn>
            </template>
            <v-list density="compact" min-width="260">
              <v-list-item :href="exportUrl.inline" prepend-icon="mdi-file-table" :title="t('prov.quote.exports.inline')" />
              <v-list-item :href="exportUrl.split" prepend-icon="mdi-file-table-outline" :title="t('prov.quote.exports.split')" />
              <v-list-item :href="exportUrl.json" :download="jsonDownloadName" prepend-icon="mdi-code-json" :title="t('prov.quote.exports.json')" />
            </v-list>
          </v-menu>
          <!-- CSV bulk import (instances) — kept beside its export sibling. -->
          <v-btn icon size="small" variant="text" :disabled="!subscriptionId" @click="importDialog = true">
            <v-icon>mdi-file-upload</v-icon>
            <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.import.title') }}</div>{{ t('prov.quote.import.tip') }}</v-tooltip>
          </v-btn>
          <!-- Saved views: personal (browser) + shared (server, all users). A
               view restores the exact screen: search, filters, columns, sort,
               active tab, cost/CO₂ mode, grouping and cost period. -->
          <v-menu>
            <template #activator="{ props: viewProps }">
              <v-btn v-bind="viewProps" icon size="small" variant="text" :color="hasViews ? 'primary' : undefined">
                <v-icon>mdi-bookmark-multiple-outline</v-icon>
                <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.views.action') }}</div>{{ t('prov.quote.views.tip') }}</v-tooltip>
              </v-btn>
            </template>
            <v-list density="compact" min-width="280">
              <v-list-subheader>{{ t('prov.quote.views.personal') }}</v-list-subheader>
              <v-list-item v-if="!savedViews.length" disabled>
                <v-list-item-title class="text-medium-emphasis">{{ t('prov.quote.views.empty') }}</v-list-item-title>
              </v-list-item>
              <v-list-item v-for="v in savedViews" :key="`l-${v.name}`" prepend-icon="mdi-bookmark-outline"
                :subtitle="v.description || undefined" @click="applyLocalView(v)">
                <v-list-item-title>{{ v.name }}</v-list-item-title>
                <template #append>
                  <!-- Only the last-loaded view offers the one-click "update with current state". -->
                  <v-btn v-if="isLastApplied('local', v)" icon size="x-small" variant="text" color="primary" @click.stop="updateViewWithCurrent('local', v)">
                    <v-icon size="14">mdi-content-save-outline</v-icon>
                  <v-tooltip activator="parent" location="bottom">{{ t('prov.quote.views.update') }}</v-tooltip></v-btn>
                  <v-btn icon size="x-small" variant="text" @click.stop="openEditView('local', v)">
                    <v-icon size="14">mdi-pencil</v-icon>
                  <v-tooltip activator="parent" location="bottom">{{ t('prov.quote.views.edit') }}</v-tooltip></v-btn>
                  <v-btn icon size="x-small" variant="text" @click.stop="deleteView(v.name)">
                    <v-icon size="14">mdi-delete-outline</v-icon>
                  <v-tooltip activator="parent" location="bottom">{{ t('common.delete') }}</v-tooltip></v-btn>
                </template>
              </v-list-item>
              <v-list-subheader>{{ t('prov.quote.views.shared') }}</v-list-subheader>
              <v-list-item v-if="!sharedViews.length" disabled>
                <v-list-item-title class="text-medium-emphasis">{{ t('prov.quote.views.empty') }}</v-list-item-title>
              </v-list-item>
              <v-list-item v-for="v in sharedViews" :key="`s-${v.id}`" prepend-icon="mdi-account-group-outline"
                :subtitle="v.description || undefined" @click="applySharedView(v)">
                <v-list-item-title>{{ v.name }}</v-list-item-title>
                <template #append>
                  <v-btn v-if="isLastApplied('shared', v)" icon size="x-small" variant="text" color="primary" @click.stop="updateViewWithCurrent('shared', v)">
                    <v-icon size="14">mdi-content-save-outline</v-icon>
                  <v-tooltip activator="parent" location="bottom">{{ t('prov.quote.views.update') }}</v-tooltip></v-btn>
                  <v-btn icon size="x-small" variant="text" @click.stop="openEditView('shared', v)">
                    <v-icon size="14">mdi-pencil</v-icon>
                  <v-tooltip activator="parent" location="bottom">{{ t('prov.quote.views.edit') }}</v-tooltip></v-btn>
                  <v-btn icon size="x-small" variant="text" @click.stop="deleteSharedView(v)">
                    <v-icon size="14">mdi-delete-outline</v-icon>
                  <v-tooltip activator="parent" location="bottom">{{ t('common.delete') }}</v-tooltip></v-btn>
                </template>
              </v-list-item>
              <v-divider />
              <v-list-item prepend-icon="mdi-content-save-outline" @click="openSaveView">
                <v-list-item-title>{{ t('prov.quote.views.save') }}</v-list-item-title>
              </v-list-item>
            </v-list>
          </v-menu>
        </div>
      </header>

      <!-- Cost-breakdown card — modern ring + a row of stat tiles
           (already-computed aggregates). The donut renders only when
           something is in the quote (the component itself early-returns
           on zero total). The metric toggle (cost ↔ CO₂) mirrors the
           legacy `optimizer-view-mode` switch — see `viewMode` below. -->
      <v-card variant="flat" class="q-costcard mb-4">
        <v-card-text class="py-3">
          <div class="d-flex align-center justify-space-between flex-wrap ga-2 mb-2">
            <div class="d-flex align-center ga-2 flex-wrap">
              <span class="q-card-title">
                {{ viewMode === 'co2' ? t('prov.quote.breakdown.titleCo2') : t('prov.quote.breakdown.title') }}
              </span>
              <!-- Group the donut + timeline by resource type or by a tag key
                   (hidden when the quote carries no tag to group by). -->
              <LjSegmented v-if="breakdownTagKeys.length" v-model="groupBy" class="q-groupby" :options="[
                { value: 'type', icon: 'mdi-shape-outline', label: t('prov.quote.cols.type') },
                { value: 'tag', icon: 'mdi-tag-outline', label: t('prov.quote.filter.tag') },
              ]" />
              <LigojAutocomplete v-if="groupBy === 'tag' && breakdownTagKeys.length" v-model="breakdownTagKey"
                :items="breakdownTagKeys" :label="t('prov.quote.tagAlloc.key')" variant="outlined" density="compact"
                hide-details class="q-groupby-key" />
              <v-chip v-if="selectedMonth != null" size="small" color="primary" variant="tonal" closable
                @click:close="selectedMonth = null">
                <v-icon start size="small">mdi-calendar-filter</v-icon>
                {{ t('prov.quote.timeline.month', { n: selectedMonth + 1 }) }}
              </v-chip>
            </div>
            <LjSegmented v-model="viewMode" class="q-mode" :options="[
              { value: 'cost', icon: 'mdi-currency-usd', label: t('prov.quote.viewMode.cost') },
              { value: 'co2', icon: 'mdi-leaf', label: t('prov.quote.viewMode.co2') },
            ]" />
          </div>
          <div class="q-costcard-body">
            <QuoteBreakdown :config="filteredConfig" :mode="viewMode" :groups="chartGroups.groups"
              :group-of="chartGroups.groupOf" :drillable="chartGroups.drillable" />
            <div class="q-stats">
              <div v-for="s in statTiles" :key="s.key" class="q-stat">
                <span class="q-stat-ic"><v-icon size="18">{{ s.icon }}</v-icon></span>
                <div class="q-stat-txt">
                  <div class="q-stat-num">{{ s.value }}</div>
                  <div class="q-stat-label">{{ s.label }}</div>
                </div>
              </div>
            </div>
            <!-- Monthly cost projection — resources ramp in at their usage
                 start month and drop off at the end of their usage duration.
                 Clicking a month filters every table + the donut + the totals
                 to the resources billed that month. -->
            <CostTimeline :config="timelineConfig" :mode="viewMode" :selected-month="selectedMonth"
              :groups="chartGroups.groups" :group-of="chartGroups.groupOf" @month-click="onMonthClick" />
          </div>
        </v-card-text>
      </v-card>

      <!-- Tabs — one per resource type. The chip shows the (filtered)
           resource count; the total is only appended when a search is
           actively hiding rows (e.g. "3/12"), never as a redundant "12/12". -->
      <div class="q-tabs-row d-flex align-center ga-2 mb-3">
        <!-- Icon-only tabs: the label moved into a rich tooltip (type, count,
             cost, and the filtered subset when a search narrows the tab). -->
        <v-tabs v-model="activeTab" density="compact" show-arrows class="q-tabs" color="primary">
          <v-tab v-for="tb in TAB_TYPES" :key="tb.key" :value="tb.key" :aria-label="tabLabel(tb.key)">
            <v-icon :icon="tb.icon" size="small" />
            <v-chip v-if="counts[tb.key]" size="x-small" variant="tonal" :color="isTabFiltered(tb.key) ? 'primary' : undefined"
              class="ml-1 q-count" :class="{ 'q-count-filtered': isTabFiltered(tb.key) }">{{ tabCountLabel(tb.key) }}</v-chip>
            <v-tooltip activator="parent" location="bottom" content-class="q-tab-tip">
              <div class="q-tab-tip-head">
                <strong>{{ tabLabel(tb.key) }}</strong>
                <template v-if="counts[tb.key]"> — {{ t('prov.quote.tagAlloc.nRes', { n: counts[tb.key] }) }} · {{ fmtMetric(costByType[tb.key]) }}</template>
                <template v-else> — {{ t('prov.quote.empty') }}</template>
              </div>
              <div v-if="isTabFiltered(tb.key)" class="q-tab-tip-filtered">
                {{ t('prov.quote.tabs.filteredTip', { n: filteredRowsByType[tb.key].length, cost: fmtMetric(filteredCostByType[tb.key]) }) }}
              </div>
            </v-tooltip>
          </v-tab>
        </v-tabs>
        <v-spacer />
        <!-- Global search (feature 13 phase 1): ONE debounced query across every
             resource type; each tab's chip turns into its per-type match count. -->
        <v-text-field :model-value="searchInput" :label="t('common.search')" prepend-inner-icon="mdi-magnify"
          density="compact" hide-details variant="outlined" clearable class="quote-search"
          :placeholder="t('prov.quote.filter.textHint')" @update:model-value="onSearch" />
        <!-- Advanced filters (dimension / numeric / tag / regex, AND-OR) —
             managed in their own dialog; the badge counts the active ones. -->
        <v-btn icon size="small" :variant="advFilters.length ? 'elevated' : 'outlined'"
          :color="advFilters.length ? 'primary' : undefined" @click="filterDialog = true">
          <v-badge v-if="advFilters.length" :content="advFilters.length" color="warning" offset-x="-2" offset-y="-2">
            <v-icon>mdi-filter-variant</v-icon>
          </v-badge>
          <v-icon v-else>mdi-filter-variant</v-icon>
          <v-tooltip activator="parent" location="bottom" max-width="300"><div class="font-weight-bold">{{ t('prov.quote.filter.action') }}</div>{{ t('prov.quote.filter.tip') }}</v-tooltip>
        </v-btn>
        <!-- Compact per-type create (targets the active tab). -->
        <v-btn icon size="small" color="primary" variant="elevated" @click="openResourceCreate(activeTab)">
          <v-icon>mdi-plus</v-icon>
          <v-tooltip activator="parent" location="bottom">{{ t(`prov.quote.${activeTab}.new`) }}</v-tooltip>
        </v-btn>
      </div>

      <v-window v-model="activeTab">
        <v-window-item v-for="tab in TAB_TYPES" :key="tab.key" :value="tab.key">
          <v-alert v-if="!rowsByType[tab.key].length" type="info" variant="tonal" density="compact">
            {{ t('prov.quote.empty') }}
          </v-alert>
          <!-- Bulk-action bar. Appears only when something is selected
               on the current tab — keeps the toolbar quiet otherwise. -->
          <v-slide-y-transition>
            <v-toolbar v-if="selectedByType[tab.key]?.length" density="compact" color="primary" rounded class="mb-2">
              <v-toolbar-title>
                {{ selectedByType[tab.key].length }} {{ t('common.selected') }}
              </v-toolbar-title>
              <v-spacer />
              <!-- Server-side bulk edit — compute types only (profiles + location).
                   Explicit surface color: inside the primary toolbar an elevated
                   button would otherwise inherit the on-primary (white) text and
                   vanish on its own white background in light mode. -->
              <v-btn v-if="COMPUTE_TYPES.has(tab.key)" variant="elevated" color="surface"
                prepend-icon="mdi-pencil-box-multiple-outline" class="me-2" @click="openBulkEdit(tab.key)">
                {{ t('prov.quote.bulk.action') }}
              </v-btn>
              <v-btn variant="elevated" color="error" prepend-icon="mdi-delete" @click="askDeleteBulk(tab.key)">
                {{ t('common.delete') }}
              </v-btn>
            </v-toolbar>
          </v-slide-y-transition>

          <!-- `tablesEpoch` remounts the tables after a saved view rewrites the
               persisted column sets, so they re-read their visibility state. -->
          <LigojDataTable v-if="rowsByType[tab.key].length" :key="`${tab.key}-v${tablesEpoch}`" v-model="selectedByType[tab.key]" v-model:sort-by="sortByType[tab.key]" show-select hover :filename="`prov-${tab.key}.csv`" :headers="headersByType[tab.key]"
            :pinned-columns="PINNED_COLUMNS" :columns-storage-key="`ligoj-prov-quote-cols-${tab.key}`" :columns-label="t('prov.quote.columns')"
            :tool-actions="tableToolActions" :items="filteredRowsByType[tab.key]" v-model:items-per-page="itemsPerPage" :items-per-page-options="ITEMS_PER_PAGE_OPTIONS"
            density="comfortable" item-value="id" class="q-table"
            @click:row="(e, { item }) => onRowClick(tab.key, e, item)" @tool-action="(key) => onToolAction(tab.key, key)">
            <template #item.name="{ item }">
              <span class="q-cell-name">{{ item.name }}</span>
              <!-- Tags inherited from the legacy `conf.tags` map. Each
                   tag carries an optional value, rendered as
                   `name:value` when present. The lookup map is
                   case-folded once in `tagsByTypeAndId` so we never
                   re-create the lookup key per cell. -->
              <span v-if="tagsFor(tab.key, item.id).length" class="d-inline-flex flex-wrap ga-1 ml-1">
                <!-- `env:TST` renders as a de-emphasised key ("env") next to
                     the value ("TST"); the ':' separator is dropped. -->
                <v-chip v-for="tag in tagsFor(tab.key, item.id)" :key="`${tag.name}:${tag.value ?? ''}`" size="x-small" variant="tonal">
                  <span v-if="tag.value" class="q-tag-key">{{ tag.name }}</span>{{ tag.value || tag.name }}
                </v-chip>
              </span>
            </template>
            <!-- Bar fill = the resource's reserved vCPU / RAM against the
                 capacity the chosen instance type provides (per-row
                 utilisation), not the column-wide maximum. -->
            <template #item.cpu="{ item }">
              <ResourceMicroBar v-if="item.price?.type?.cpu" :value="item.cpu ?? item.price.type.cpu" :max="item.price.type.cpu"
                :label="formatCpu(item.cpu ?? item.price.type.cpu)" :tooltip="t('prov.quote.microbar.cpu')" :format="cpuTip" />
              <template v-else>{{ formatCpu(item.cpu ?? item.price?.type?.cpu) }}</template>
            </template>
            <template #item.ram="{ item }">
              <ResourceMicroBar v-if="item.price?.type?.ram" :value="item.ram ?? item.price.type.ram" :max="item.price.type.ram"
                :label="formatRam(item.ram ?? item.price.type.ram)" :tooltip="t('prov.quote.microbar.ram')" :format="formatRam" />
              <template v-else>{{ formatRam(item.ram ?? item.price?.type?.ram) }}</template>
            </template>
            <template #item.size="{ item }">{{ formatStorage(item.size) }}</template>
            <template #item.cost="{ item }">
              <span class="q-cell-cost-wrap">
                <span v-if="viewMode === 'co2'" class="q-cell-cost">{{ formatCo2(item.co2 ?? item.maxCo2) }}</span>
                <span v-else class="q-cell-cost">{{ formatCost(item.cost, config.currency) }}</span>
                <CompareDiff v-if="activeCs != null" :row="item" :cs="csResourceOf(tab.key, item.name)"
                  :errored="rowErrored(tab.key, item)" :metric="viewMode" :currency="config.currency" :cs-name="activeCsName" />
              </span>
            </template>
            <template #item.os="{ item }">
              <span v-if="item.os || item.price?.os" class="q-os">
                <OsIcon :os="item.os || item.price?.os" />
                <v-tooltip activator="parent" location="top">
                  {{ osTooltip(item.os || item.price?.os) }}
                </v-tooltip>
              </span>
            </template>
            <template #item.engine="{ item }">
              <span v-if="item.engine || item.price?.engine" class="q-engine">
                <EngineIcon :engine="item.engine || item.price?.engine" />
                <v-tooltip activator="parent" location="top">
                  {{ engineTooltip(item.engine || item.price?.engine) }}
                </v-tooltip>
              </span>
            </template>
            <template #item.type="{ item }">
              <span v-if="item.price?.type?.name" class="q-type">{{ item.price.type.name }}</span>
            </template>
            <template #header.location>
              <span class="q-loc-header">
                <v-icon size="16">mdi-map-marker-outline</v-icon>
                <v-tooltip activator="parent" location="top">{{ t('prov.quote.cols.location') }}</v-tooltip>
              </span>
            </template>
            <template #item.location="{ item }">
              <LocationLabel v-if="locationOf(item)" :location="locationOf(item)" flag-only class="q-loc-cell" />
            </template>
            <template #item.level="{ item }">{{ item.level || item.price?.level || '' }}</template>
            <template #item.seats="{ item }">{{ item.seats ?? item.price?.seats ?? '' }}</template>
            <template #item.attachedTo="{ item }">
              <span v-if="attachedLabel(item)" class="text-caption text-medium-emphasis">
                {{ attachedLabel(item) }}
              </span>
            </template>
            <!-- All row actions grouped behind a single cog, mirroring the
                 header tools menu (standard RowActionsMenu). Edit is also
                 reachable by clicking anywhere on the row. -->
            <template #item.actions="{ item }">
              <RowActionsMenu :actions="rowActions" :label="t('common.actions')" @select="(key) => onRowAction(tab.key, item, key)" />
            </template>
          </LigojDataTable>
        </v-window-item>
      </v-window>
    </template>

    <!-- Edit quote. Sources for location / usage / budget / optimizer
         come from the loaded config (no extra REST call needed); the
         backend stores them by NAME so item-value is 'name'. -->
    <v-dialog v-model="editDialog" max-width="780" scrollable>
      <v-card>
        <v-card-title>{{ t('prov.quote.edit') }}</v-card-title>
        <v-card-text>
          <v-form ref="formRef" @submit.prevent="saveEdit">
            <v-row density="comfortable">
              <v-col cols="12" md="6">
                <v-text-field v-model="editForm.name" :label="t('prov.quote.name')" :rules="REQUIRED_RULES" maxlength="50" variant="outlined" density="compact" autofocus />
              </v-col>
              <v-col cols="12" md="6">
                <LocationField v-model="editForm.location" :items="config?.locations || []" :label="t('prov.quote.cols.location')" />
              </v-col>
              <v-col cols="12">
                <v-text-field v-model="editForm.description" :label="t('prov.quote.description')" maxlength="250" variant="outlined" density="compact" />
              </v-col>
              <v-col cols="12" md="6">
                <UsageField v-model="editForm.usage" :usages="config?.usages || []" :subscription-id="subscriptionId" scope="config"
                  :label="t('prov.quote.fields.usage')" @changed="reload" />
              </v-col>
              <v-col cols="12" md="6">
                <BudgetField v-model="editForm.budget" :budgets="config?.budgets || []" :subscription-id="subscriptionId" scope="config"
                  :currency="config?.currency" :label="t('prov.quote.fields.budget')" @changed="reload" />
              </v-col>
              <v-col cols="12" md="6">
                <OptimizerField v-model="editForm.optimizer" :optimizers="config?.optimizers || []" :subscription-id="subscriptionId" scope="config"
                  :label="t('prov.quote.fields.optimizer')" @changed="reload" />
              </v-col>
              <v-col cols="12" md="6">
                <LigojSelect v-model="editForm.reservationMode" :items="reservationOptions" :label="t('prov.quote.fields.reservationMode')" variant="outlined" density="compact" />
              </v-col>
              <v-col cols="12" md="6">
                <LigojSelect v-model="editForm.physical" :items="physicalOptions" :label="t('prov.quote.fields.physical')" variant="outlined" density="compact" clearable />
              </v-col>
              <v-col cols="12" md="6">
                <div class="text-caption text-medium-emphasis mb-1">
                  {{ t('prov.quote.fields.ramAdjustedRate') }} ({{ editForm.ramAdjustedRate }}%)
                </div>
                <v-slider v-model="editForm.ramAdjustedRate" :min="50" :max="200" :step="5" thumb-label hide-details />
              </v-col>
            </v-row>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="editDialog = false">{{ t('common.cancel') }}</v-btn>
          <v-btn color="primary" variant="elevated" :loading="saving" @click="saveEdit">
            <v-icon start>mdi-content-save</v-icon>
            {{ t('common.save') }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <LigojConfirmDialog v-model="deleteRowDialog" :title="t('prov.quote.delete.row.title')" :confirm-label="t('common.delete')" confirm-color="error" :loading="deleting" @confirm="confirmDeleteRow">
      {{ t('prov.quote.delete.row.body', { name: deleteRowTarget?.row?.name || `#${deleteRowTarget?.row?.id}` }) }}
    </LigojConfirmDialog>

    <!-- Compute dialog handles instance / container / function / database;
         storage and support have their own modals because their lookup
         and save shapes are too different to share a form. -->
    <ComputeEditDialog v-model="computeDialog" :type="editType && COMPUTE_TYPES.has(editType) ? editType : 'instance'" :subscription-id="subscriptionId" :config="config" :resource="editTarget"
      @saved="onResourceSaved" @tags-changed="onResourceSaved" @usage-changed="reload" @budget-changed="reload" @optimizer-changed="reload" />
    <StorageEditDialog v-model="storageDialog" :subscription-id="subscriptionId" :config="config" :resource="editTarget" @saved="onResourceSaved" @tags-changed="onResourceSaved" />
    <SupportEditDialog v-model="supportDialog" :subscription-id="subscriptionId" :config="config" :resource="editTarget" @saved="onResourceSaved" @tags-changed="onResourceSaved" />
    <InstanceImportDialog v-model="importDialog" :subscription-id="subscriptionId" @saved="onResourceSaved" />
    <CompareSetupDialog v-model="compareSetup" :subscription-id="subscriptionId" :currency="config?.currency" @changed="loadCompared" />
    <TagAllocationDialog v-model="tagAllocDialog" :config="config" :currency="config?.currency" :view-mode="viewMode" />
    <SnapshotDialog v-model="snapshotDialog" :subscription-id="subscriptionId" :config="config" :currency="config?.currency"
      :view-mode="viewMode" @restored="onSnapshotRestored" />
    <BulkEditDialog v-model="bulkEditDialog" :type="bulkEditType" :ids="bulkEditIds" :config="config"
      :subscription-id="subscriptionId" @saved="onBulkEdited" />
    <FilterDialog v-model="filterDialog" v-model:filters="advFilters" v-model:mode="advMode" :config="config" />
    <!-- Save-current / edit-view dialog. The name is a combobox over the
         existing views so an existing one can be overridden on purpose — with
         an explicit warning. Editing changes name / description / sharing
         without recapturing the state (unsharing deletes the server copy). -->
    <v-dialog v-model="saveViewDialog" max-width="440">
      <v-card>
        <v-card-title>
          {{ viewDialogMode === 'edit' ? t('prov.quote.views.editTitle') : t('prov.quote.views.saveTitle') }}
        </v-card-title>
        <v-card-text>
          <v-combobox v-model="viewName" :items="viewNameItems" item-title="title" item-value="title"
            :return-object="false" :label="t('prov.quote.views.name')"
            variant="outlined" density="compact" hide-details autofocus autocomplete="off"
            data-1p-ignore data-lpignore="true" @keyup.enter="submitViewDialog">
            <template #item="{ props: itemProps, item }">
              <v-list-item v-bind="itemProps">
                <template #prepend>
                  <v-icon size="16">{{ (item.raw || item).shared ? 'mdi-account-group-outline' : 'mdi-bookmark-outline' }}</v-icon>
                </template>
              </v-list-item>
            </template>
          </v-combobox>
          <v-textarea v-model="viewDescription" :label="t('prov.quote.views.description')" variant="outlined"
            density="compact" rows="2" auto-grow hide-details class="mt-3" />
          <v-checkbox v-model="shareView" :label="t('prov.quote.views.share')" density="compact" hide-details
            color="primary" class="mt-2" />
          <p v-if="shareView" class="text-caption text-medium-emphasis mb-0">{{ t('prov.quote.views.shareHelp') }}</p>
          <v-alert v-if="unshareWarning" type="warning" variant="tonal" density="compact" class="mt-2">
            {{ t('prov.quote.views.unshareWarn') }}
          </v-alert>
          <v-alert v-if="overrideWarning" type="warning" variant="tonal" density="compact" class="mt-2">
            {{ t('prov.quote.views.overrideWarn', { name: viewName?.trim(), scope: shareView ? t('prov.quote.views.shared') : t('prov.quote.views.personal') }) }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="saveViewDialog = false">{{ t('common.cancel') }}</v-btn>
          <v-btn color="primary" variant="elevated" :disabled="!viewName?.trim() || savingView" :loading="savingView"
            @click="submitViewDialog">
            {{ t('common.save') }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <LigojConfirmDialog v-model="deleteAllDialog" :title="t('prov.quote.delete.all.title', { type: deleteAllType ? tabLabel(deleteAllType) : '' })" :confirm-label="t('prov.quote.delete.all.label')"
      confirm-color="error" :loading="deleting" @confirm="confirmDeleteAll">
      {{ t('prov.quote.delete.all.body', { type: deleteAllType ? tabLabel(deleteAllType) : '', count: deleteAllType ? rowsByType[deleteAllType].length : 0 }) }}
    </LigojConfirmDialog>

    <LigojConfirmDialog v-model="deleteBulkDialog" :title="t('prov.quote.delete.bulk.title')" :confirm-label="t('common.delete')" confirm-color="error" :loading="deleting"
      @confirm="confirmDeleteBulk">
      {{ t('prov.quote.delete.bulk.body', { type: deleteBulkType ? tabLabel(deleteBulkType) : '', count: deleteBulkType ? selectedByType[deleteBulkType].length : 0 }) }}
    </LigojConfirmDialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  useApi,
  useAppStore,
  useErrorStore,
  useI18nStore,
  LigojConfirmDialog,
  LigojDataTable,
  LigojAutocomplete,
  LigojSelect,
  LjSegmented,
  RowActionsMenu,
  APP_BASE,
} from '@ligoj/host'
import {
  formatCo2,
  formatCo2Range,
  formatCost,
  formatCostRange,
  formatCpu,
  formatRam,
  formatStorage,
  scaleCost,
  COST_PERIODS,
  rowInMonth,
  sumCostRange,
  TAB_TYPES,
} from '../quoteFormatters.js'
import QuoteBreakdown from './QuoteBreakdown.vue'
import CostTimeline from './CostTimeline.vue'
import ComputeEditDialog from './ComputeEditDialog.vue'
import StorageEditDialog from './StorageEditDialog.vue'
import SupportEditDialog from './SupportEditDialog.vue'
import InstanceImportDialog from './InstanceImportDialog.vue'
import CompareSetupDialog from './CompareSetupDialog.vue'
import TagAllocationDialog from './TagAllocationDialog.vue'
import SnapshotDialog from './SnapshotDialog.vue'
import BulkEditDialog from './BulkEditDialog.vue'
import FilterDialog from './FilterDialog.vue'
import { textMatcher, quickMatch, rowPasses } from '../searchFilters.js'
import { viewsStorageKey, readViews, writeViews, upsertView, removeView } from '../viewPresets.js'
import CompareDiff from './CompareDiff.vue'
import QuoteIdentity from './QuoteIdentity.vue'
import { valueIndex, diffMeta, formatDiffPct, comparisonSummary } from '../compareApi.js'
import { tagKeys, tagGrouping, TAG_OTHER_KEY, TAG_UNTAGGED_KEY } from '../tagAllocation.js'
import ResourceMicroBar from './ResourceMicroBar.vue'
import EfficiencyBar from './EfficiencyBar.vue'
import CarbonBar from './CarbonBar.vue'
import OsIcon from './OsIcon.vue'
import EngineIcon from './EngineIcon.vue'
import LocationField from './LocationField.vue'
import LocationLabel from './LocationLabel.vue'
import UsageField from './UsageField.vue'
import BudgetField from './BudgetField.vue'
import OptimizerField from './OptimizerField.vue'
import { osTooltip } from '../osCatalog.js'
import { engineTooltip } from '../engineCatalog.js'

const route = useRoute()
const api = useApi()
const app = useAppStore()
const errorStore = useErrorStore()
const i18n = useI18nStore()
const t = i18n.t

/* vCPU tooltip formatter — formatCpu has no unit, so append the column
 * label ("vCPU") for the micro-bar's requested/provided line. RAM passes
 * `formatRam` directly (it already carries its unit). */
const cpuTip = (n) => `${formatCpu(n)} ${t('prov.quote.cols.cpu')}`

const loading = ref(false)
const refreshing = ref(false)
const refreshingPrices = ref(false)
const error = ref(null)

/* ---------- Items-per-page (client-side pagination) ----------
 * Each tab's data table now paginates in the browser to keep render
 * cost bounded even for quotes with hundreds of resources. The
 * default of 15 mirrors Ligoj's other paginated views; `-1` shows
 * everything for users who want the legacy "all on one page" view.
 * Persisted globally — switching tabs keeps the same page size. */
const ITEMS_PER_PAGE_KEY = 'ligoj-prov-quote-items-per-page'
const ITEMS_PER_PAGE_OPTIONS = [
  { value: 15, title: '15' },
  { value: 30, title: '30' },
  { value: 50, title: '50' },
  { value: 100, title: '100' },
  { value: -1, title: '∞' },
]
const VALID_PAGE_SIZES = new Set(ITEMS_PER_PAGE_OPTIONS.map((o) => o.value))
function readPersistedItemsPerPage() {
  if (typeof localStorage === 'undefined') return 15
  const stored = Number(localStorage.getItem(ITEMS_PER_PAGE_KEY))
  return VALID_PAGE_SIZES.has(stored) ? stored : 15
}
const itemsPerPage = ref(readPersistedItemsPerPage())
watch(itemsPerPage, (v) => {
  if (typeof localStorage !== 'undefined' && VALID_PAGE_SIZES.has(v)) {
    localStorage.setItem(ITEMS_PER_PAGE_KEY, String(v))
  }
})

/* Cost period selector — persisted so the user's preference survives
 * a reload. The scaling math lives in `scaleCost` (quoteFormatters)
 * so it's covered by unit tests. */
const COST_PERIOD_KEY = 'ligoj-prov-quote-cost-period'
function readPersistedCostPeriod() {
  if (typeof localStorage === 'undefined') return 'month'
  const stored = localStorage.getItem(COST_PERIOD_KEY)
  return COST_PERIODS.includes(stored) ? stored : 'month'
}
const costPeriod = ref(readPersistedCostPeriod())
watch(costPeriod, (v) => {
  if (typeof localStorage !== 'undefined' && COST_PERIODS.includes(v)) {
    localStorage.setItem(COST_PERIOD_KEY, v)
  }
})

function scaledCost(cost) {
  return scaleCost(cost, costPeriod.value)
}
// `config` is the inner quote (the `configuration` block of the API
// response). Top-level fields from the response (subscription id,
// project, node) live in `meta` so the header can render the provider
// icon without a second round-trip.
const config = ref(null)
const meta = ref(null)
/* Active tab persisted in localStorage so reloading or following an
 * external link to this view keeps the user where they were. */
const ACTIVE_TAB_STORAGE_KEY = 'ligoj-prov-quote-active-tab'
const VALID_TAB_KEYS = new Set(TAB_TYPES.map((t) => t.key))
function readPersistedTab() {
  if (typeof localStorage === 'undefined') return 'instance'
  const stored = localStorage.getItem(ACTIVE_TAB_STORAGE_KEY)
  return VALID_TAB_KEYS.has(stored) ? stored : 'instance'
}
const activeTab = ref(readPersistedTab())
watch(activeTab, (v) => {
  if (typeof localStorage !== 'undefined' && VALID_TAB_KEYS.has(v)) {
    localStorage.setItem(ACTIVE_TAB_STORAGE_KEY, v)
  }
})

/* ---------- View mode (cost ↔ CO₂) ----------
 * Persisted in localStorage so the choice survives reloads — matches
 * the legacy `SETTINGS_OPTIMIZER_VIEW` key. */
const VIEW_MODE_STORAGE_KEY = 'ligoj-prov-quote-view-mode'
const viewMode = ref(
  (typeof localStorage !== 'undefined' && localStorage.getItem(VIEW_MODE_STORAGE_KEY)) || 'cost',
)
watch(viewMode, (v) => {
  if (typeof localStorage !== 'undefined') localStorage.setItem(VIEW_MODE_STORAGE_KEY, v)
})

/* ---------- Breakdown grouping (resource type ↔ tag) ----------
 * The donut + timeline group by resource type by default, or by the values of a
 * chosen tag key. Both the mode and the key are persisted. */
const GROUP_BY_KEY = 'ligoj-prov-quote-breakdown-groupby'
const GROUP_TAG_KEY = 'ligoj-prov-quote-breakdown-tagkey'
const groupBy = ref((typeof localStorage !== 'undefined' && localStorage.getItem(GROUP_BY_KEY)) || 'type')
const breakdownTagKey = ref((typeof localStorage !== 'undefined' && localStorage.getItem(GROUP_TAG_KEY)) || null)
watch(groupBy, (v) => {
  if (typeof localStorage !== 'undefined') localStorage.setItem(GROUP_BY_KEY, v)
})
watch(breakdownTagKey, (v) => {
  if (typeof localStorage === 'undefined') return
  if (v == null) localStorage.removeItem(GROUP_TAG_KEY)
  else localStorage.setItem(GROUP_TAG_KEY, v)
})

/** Tag keys available in the quote (for the breakdown grouping selector). */
const breakdownTagKeys = computed(() => tagKeys(config.value))
/* Default / repair the selected tag key when the quote's tags change. */
watch([breakdownTagKeys, groupBy], () => {
  if (groupBy.value !== 'tag') return
  if (breakdownTagKey.value == null || !breakdownTagKeys.value.includes(breakdownTagKey.value)) {
    breakdownTagKey.value = breakdownTagKeys.value[0] ?? null
  }
})

/**
 * Grouping descriptor passed to the donut + timeline. Per resource type by
 * default; per tag value (top-N + "Other" + "Untagged") when tag mode is on and
 * a key with values exists — otherwise it falls back to per-type.
 */
const chartGroups = computed(() => {
  const field = viewMode.value === 'co2' ? 'co2' : 'cost'
  if (groupBy.value === 'tag' && breakdownTagKey.value) {
    const { groups, groupOf } = tagGrouping(filteredConfig.value, breakdownTagKey.value, field)
    if (groups.length) {
      return {
        groupOf,
        drillable: false,
        groups: groups.map((g) => ({ ...g, label: chartGroupLabel(g.key) })),
      }
    }
  }
  return {
    groupOf: (type) => type,
    drillable: true,
    groups: TAB_TYPES.map((tt) => ({ key: tt.key, color: tt.color, label: tabLabel(tt.key) })),
  }
})
function chartGroupLabel(key) {
  if (key === TAG_OTHER_KEY) return t('prov.quote.tagAlloc.other')
  if (key === TAG_UNTAGGED_KEY) return t('prov.quote.tagAlloc.untagged')
  return key || t('prov.quote.tagAlloc.noValue')
}

/* ---------- Global search (feature 13 phase 1) ----------
 * ONE debounced query applied to every resource type at once. The tabs' count
 * chips become per-type match counts ("3/12"), and the tab tooltips surface the
 * filtered subset's count + cost. */
const searchInput = ref('')
const searchQuery = ref('')
const SEARCH_DEBOUNCE_MS = 200
let searchTimer = null
function onSearch(value) {
  searchInput.value = value || ''
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    searchQuery.value = searchInput.value
  }, SEARCH_DEBOUNCE_MS)
}

/* Advanced filters (feature 13 phase 2): dimension / numeric / tag / regex
 * criteria combined with AND-OR, edited in FilterDialog and evaluated by the
 * pure searchFilters engine on top of the quick query. */
const filterDialog = ref(false)
const advFilters = ref([])
const advMode = ref('AND')

/* ---------- Saved views (feature 13 phase 3) ----------
 * Named captures of the screen state — search, filters, active tab, cost/CO₂
 * mode, breakdown grouping and each table's column set — persisted per
 * subscription in localStorage. */
const savedViews = ref([])
const sharedViews = ref([]) // server-side, visible to every user of the subscription
const saveViewDialog = ref(false)
const viewDialogMode = ref('save') // 'save' (capture current) | 'edit' (metadata only)
const editingView = ref(null) // { kind: 'local'|'shared', id?, name, description, body }
const viewName = ref('')
const viewDescription = ref('')
const shareView = ref(false)
const savingView = ref(false)
/* The view loaded last — `{ kind, name, id? }`. Persisted per subscription so
 * the one-click "update" shortcut survives a browser refresh. */
const LAST_VIEW_KEY = () => `ligoj-prov-quote-lastview-${subscriptionId.value}`
const lastApplied = ref(null)
watch(lastApplied, (v) => {
  const store = localViewStore()
  if (!store || !subscriptionId.value) return
  if (v == null) store.removeItem(LAST_VIEW_KEY())
  else store.setItem(LAST_VIEW_KEY(), JSON.stringify(v))
})
function restoreLastApplied() {
  try {
    const raw = localViewStore()?.getItem(LAST_VIEW_KEY())
    if (raw) lastApplied.value = JSON.parse(raw)
  } catch {
    lastApplied.value = null
  }
}
const tablesEpoch = ref(0)
const COLUMNS_KEY = (tabKey) => `ligoj-prov-quote-cols-${tabKey}`
const localViewStore = () => (typeof localStorage !== 'undefined' ? localStorage : null)

/* Sort state lifted out of the tables so views capture it AND so it survives a
 * reload on its own (persisted per tab, like the column sets). */
const SORT_KEY = (tabKey) => `ligoj-prov-quote-sort-${tabKey}`
function readSort(tabKey) {
  try {
    const raw = localViewStore()?.getItem(SORT_KEY(tabKey))
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}
const sortByType = reactive(Object.fromEntries(TAB_TYPES.map((tt) => [tt.key, readSort(tt.key)])))
watch(sortByType, () => {
  const store = localViewStore()
  if (!store) return
  for (const tab of TAB_TYPES) {
    if (sortByType[tab.key]?.length) store.setItem(SORT_KEY(tab.key), JSON.stringify(sortByType[tab.key]))
    else store.removeItem(SORT_KEY(tab.key))
  }
}, { deep: true })

/* The whole search state (quick query + advanced filters + mode) also persists
 * per subscription, so a refreshed browser lands on the exact same rows. */
const SEARCH_STATE_KEY = () => `ligoj-prov-quote-search-${subscriptionId.value}`
function restoreSearchState() {
  try {
    const raw = localViewStore()?.getItem(SEARCH_STATE_KEY())
    const s = raw ? JSON.parse(raw) : null
    if (!s) return
    advFilters.value = Array.isArray(s.filters) ? s.filters : []
    advMode.value = s.mode === 'OR' ? 'OR' : 'AND'
    if (s.search) onSearch(s.search)
  } catch {
    // Broken payload: start clean.
  }
}
watch([advFilters, advMode, searchQuery], () => {
  const store = localViewStore()
  if (!store || !subscriptionId.value) return
  if (!advFilters.value.length && advMode.value === 'AND' && !searchInput.value) {
    store.removeItem(SEARCH_STATE_KEY())
  } else {
    store.setItem(SEARCH_STATE_KEY(), JSON.stringify({
      search: searchInput.value || '',
      mode: advMode.value,
      filters: advFilters.value,
    }))
  }
}, { deep: true })

const hasViews = computed(() => savedViews.value.length > 0 || sharedViews.value.length > 0)

/* Existing views offered in the save dialog (override on purpose, warned),
 * each flagged so shared and personal entries are distinguishable. */
const viewNameItems = computed(() => [
  ...savedViews.value.map((v) => ({ title: v.name, shared: false })),
  ...sharedViews.value.map((v) => ({ title: v.name, shared: true })),
].sort((a, b) => a.title.localeCompare(b.title) || (a.shared === b.shared ? 0 : a.shared ? 1 : -1)))

/** Overriding an existing view in the TARGET scope (other than the one being edited). */
const overrideWarning = computed(() => {
  const name = viewName.value?.trim()
  if (!name) return false
  const target = shareView.value ? sharedViews.value : savedViews.value
  const hit = target.find((v) => v.name === name)
  if (!hit) return false
  const src = editingView.value
  return !(viewDialogMode.value === 'edit' && src
    && src.kind === (shareView.value ? 'shared' : 'local') && src.name === name)
})

/** Unsharing an edited shared view deletes its server copy. */
const unshareWarning = computed(() =>
  viewDialogMode.value === 'edit' && editingView.value?.kind === 'shared' && !shareView.value)

function loadSavedViews() {
  savedViews.value = readViews(localViewStore(), viewsStorageKey(subscriptionId.value))
}

async function loadSharedViews() {
  if (!subscriptionId.value) return
  try {
    sharedViews.value = (await api.get(`rest/service/prov/${subscriptionId.value}/view`, { silent: true })) || []
  } catch {
    sharedViews.value = []
  }
}

function persistViews() {
  writeViews(localViewStore(), viewsStorageKey(subscriptionId.value), savedViews.value)
}

/**
 * Snapshot the current screen state under a name: search, filters, active tab,
 * cost/CO₂ mode, breakdown grouping, cost period, per-table sort orders and
 * column-visibility sets — everything needed to restore the exact view after a
 * browser restart, or for another user when shared.
 */
function captureView(name) {
  const columns = {}
  const store = localViewStore()
  if (store) {
    for (const tab of TAB_TYPES) {
      const payload = store.getItem(COLUMNS_KEY(tab.key))
      if (payload != null) columns[tab.key] = payload
    }
  }
  const sort = {}
  for (const tab of TAB_TYPES) {
    if (sortByType[tab.key]?.length) sort[tab.key] = sortByType[tab.key].map((s) => ({ ...s }))
  }
  return {
    name,
    search: searchInput.value || '',
    filters: advFilters.value.map((f) => ({ ...f })),
    filterMode: advMode.value,
    tab: activeTab.value,
    viewMode: viewMode.value,
    groupBy: groupBy.value,
    tagKey: breakdownTagKey.value,
    costPeriod: costPeriod.value,
    sort,
    columns,
  }
}

function openSaveView() {
  viewDialogMode.value = 'save'
  editingView.value = null
  viewName.value = ''
  viewDescription.value = ''
  shareView.value = false
  saveViewDialog.value = true
}

/** Edit a view's metadata (name / description / sharing) without recapturing its state. */
function openEditView(kind, v) {
  viewDialogMode.value = 'edit'
  editingView.value = {
    kind,
    id: v.id,
    name: v.name,
    description: v.description || '',
    body: kind === 'shared' ? safeParse(v.data) : { ...v },
  }
  viewName.value = v.name
  viewDescription.value = v.description || ''
  shareView.value = kind === 'shared'
  saveViewDialog.value = true
}

function safeParse(json) {
  try {
    return JSON.parse(json || '{}')
  } catch {
    return {}
  }
}

/** Persist a view body under a scope, replacing by name (server upsert / local upsert). */
async function storeViewAs(body, name, description, shared) {
  const view = { ...body, name, description: description || '' }
  if (shared) {
    await api.post(`rest/service/prov/${subscriptionId.value}/view`,
      { name, description: description || '', data: JSON.stringify(view) })
    await loadSharedViews()
  } else {
    savedViews.value = upsertView(savedViews.value, view)
    persistViews()
  }
  return view
}

async function submitViewDialog() {
  const name = viewName.value?.trim()
  if (!name) return
  savingView.value = true
  try {
    if (viewDialogMode.value === 'save') {
      await storeViewAs(captureView(name), name, viewDescription.value, shareView.value)
    } else {
      const src = editingView.value
      await storeViewAs(src.body, name, viewDescription.value, shareView.value)
      // Drop the previous copy when the name or the scope changed —
      // unsharing (shared → personal) deletes the server copy.
      if (src.kind === 'shared' && (!shareView.value || src.name !== name)) {
        await api.del(`rest/service/prov/${subscriptionId.value}/view/${src.id}`, { silent: true })
        await loadSharedViews()
      } else if (src.kind === 'local' && (shareView.value || src.name !== name)) {
        savedViews.value = removeView(savedViews.value, src.name)
        persistViews()
      }
    }
    saveViewDialog.value = false
    errorStore.success(t(viewDialogMode.value === 'edit' ? 'prov.quote.views.updated' : 'prov.quote.views.saved',
      { name }))
    viewName.value = ''
    viewDescription.value = ''
  } finally {
    savingView.value = false
  }
}

/** One-click "update the last-loaded view with the current screen state". */
async function updateViewWithCurrent(kind, v) {
  await storeViewAs(captureView(v.name), v.name, v.description, kind === 'shared')
  errorStore.success(t('prov.quote.views.updated', { name: v.name }))
}

function isLastApplied(kind, v) {
  const last = lastApplied.value
  return !!last && last.kind === kind && (kind === 'shared' ? last.id === v.id : last.name === v.name)
}

function deleteView(name) {
  savedViews.value = removeView(savedViews.value, name)
  persistViews()
  if (isLastApplied('local', { name })) lastApplied.value = null
  errorStore.success(t('prov.quote.views.deleted', { name }))
}

async function deleteSharedView(v) {
  await api.del(`rest/service/prov/${subscriptionId.value}/view/${v.id}`)
  await loadSharedViews()
  if (isLastApplied('shared', v)) lastApplied.value = null
  errorStore.success(t('prov.quote.views.deleted', { name: v.name }))
}

/** Restore a saved view onto the screen (sort + columns included). */
function applyView(v) {
  onSearch(v.search || '')
  advFilters.value = (v.filters || []).map((f) => ({ ...f }))
  advMode.value = v.filterMode === 'OR' ? 'OR' : 'AND'
  if (v.viewMode === 'cost' || v.viewMode === 'co2') viewMode.value = v.viewMode
  if (v.groupBy === 'type' || v.groupBy === 'tag') groupBy.value = v.groupBy
  if (v.tagKey !== undefined) breakdownTagKey.value = v.tagKey
  if (v.tab && VALID_TAB_KEYS.has(v.tab)) activeTab.value = v.tab
  if (COST_PERIODS.includes(v.costPeriod)) costPeriod.value = v.costPeriod
  for (const tab of TAB_TYPES) {
    sortByType[tab.key] = (v.sort?.[tab.key] || []).map((s) => ({ ...s }))
  }
  const store = localViewStore()
  if (store && v.columns) {
    for (const tab of TAB_TYPES) {
      const payload = v.columns[tab.key]
      if (payload == null) store.removeItem(COLUMNS_KEY(tab.key))
      else store.setItem(COLUMNS_KEY(tab.key), payload)
    }
    tablesEpoch.value++
  }
}

/** Apply a personal view, remembering it as the last-loaded one. */
function applyLocalView(v) {
  applyView(v)
  lastApplied.value = { kind: 'local', name: v.name }
}

/** Apply a server-side shared view (its state is an opaque JSON document). */
function applySharedView(v) {
  applyView(safeParse(v.data))
  lastApplied.value = { kind: 'shared', id: v.id, name: v.name }
}

// --- Edit-quote dialog state ---
const editDialog = ref(false)
const formRef = ref(null)
const saving = ref(false)
const editForm = reactive({
  name: '',
  description: '',
  // Backend stores these by NAME (location/usage/budget/optimizer) so
  // the autocomplete's item-value is 'name' and the model is a string.
  location: null,
  usage: null,
  budget: null,
  optimizer: null,
  reservationMode: 'reserved',
  physical: null,
  ramAdjustedRate: 100,
})
const rules = {
  required: (v) => (v != null && v !== '') || (t('common.required') || 'Required'),
}
// Stable rule array for the quote-edit modal — Vuetify 4 re-validates
// whenever `:rules` changes by reference, and inline arrays in templates
// cause a recursive-update loop inside transitioned panels.
const REQUIRED_RULES = [rules.required]

const reservationOptions = computed(() => [
  { value: 'reserved', title: t('prov.quote.fields.reservation.reserved') },
  { value: 'max', title: t('prov.quote.fields.reservation.max') },
])
const physicalOptions = computed(() => [
  { value: true, title: t('prov.quote.fields.physical.true') },
  { value: false, title: t('prov.quote.fields.physical.false') },
])

// --- Delete dialog state ---
const deleteRowDialog = ref(false)
const deleteRowTarget = ref(null)         // { type, row }
const deleteAllDialog = ref(false)
const deleteAllType = ref(null)            // tab key
const deleteBulkDialog = ref(false)
const deleteBulkType = ref(null)
const deleting = ref(false)

// --- Per-tab selection (drives the bulk-delete toolbar) ---
const selectedByType = reactive(Object.fromEntries(TAB_TYPES.map((t) => [t.key, []])))

// --- Instance CSV import dialog state ---
const importDialog = ref(false)

/* ----- Column visibility -----
 * Column show/hide is now a standard LigojDataTable feature: the table
 * owns the selector (in its header tools cog) and persists the hidden set
 * per tab under `ligoj-prov-quote-cols-<tab>`. `name` and `actions` stay
 * pinned so the user can always identify a row and act on it. */
const PINNED_COLUMNS = ['name', 'actions']

// --- Per-type create/edit dialog state ---
// Compute types (instance/container/function/database) share
// ComputeEditDialog; storage and support each have their own. A single
// `editType` ref tracks which dialog is currently open so the buttons
// can route the click to the right modal.
const computeDialog = ref(false)
const storageDialog = ref(false)
const supportDialog = ref(false)
/* ---------- Cross-provider comparison ----------
 * `comparedList` is the MS's set of compared subscriptions (CS) from the
 * backend. `activeCs` is the one the table/summary currently diff against;
 * its full config is fetched into `csConfig` and indexed by resource name so
 * each MS row can show its price/CO₂ delta vs the CS. */
const compareSetup = ref(false)
const tagAllocDialog = ref(false)
const snapshotDialog = ref(false)

/* ---------- Server-side bulk edit ---------- */
const bulkEditDialog = ref(false)
const bulkEditType = ref('instance')
const bulkEditIds = ref([])
function openBulkEdit(type) {
  bulkEditType.value = type
  bulkEditIds.value = [...(selectedByType[type] || [])]
  bulkEditDialog.value = true
}
async function onBulkEdited() {
  errorStore.success(t('prov.quote.bulk.done', { count: bulkEditIds.value.length, type: tabLabel(bulkEditType.value) }))
  selectedByType[bulkEditType.value] = []
  await reload()
  await syncCompared()
}

/** After a snapshot restore the whole quote changed: reload + re-sync compared. */
async function onSnapshotRestored() {
  await reload()
  await syncCompared()
}
const comparedList = ref([])
const activeCs = ref(null)
const csConfig = ref(null)

const activeCsName = computed(() => {
  const cs = comparedList.value.find((c) => String(c.subscription) === String(activeCs.value))
  return cs ? (cs.name || `#${cs.subscription}`) : ''
})

/* The active compared subscription is persisted per MS so the chosen comparison
 * survives reloads. */
const activeCsStorageKey = () => `ligoj-prov-quote-active-cs-${subscriptionId.value}`
function inCompared(id) {
  return comparedList.value.some((c) => String(c.subscription) === String(id))
}

async function loadCompared() {
  if (!subscriptionId.value) return
  try {
    comparedList.value = (await api.get(`rest/service/prov/${subscriptionId.value}/compare`)) || []
  } catch {
    comparedList.value = []
  }
  // Restore the persisted selection, or drop it if that CS is gone.
  if (activeCs.value == null && typeof localStorage !== 'undefined') {
    const saved = localStorage.getItem(activeCsStorageKey())
    if (saved != null && inCompared(saved)) activeCs.value = saved
  } else if (activeCs.value != null && !inCompared(activeCs.value)) {
    activeCs.value = null
  }
}

/**
 * Keep the compared subscriptions in step with an MS change. Re-clones every CS
 * from the current MS state (reusing the backend's tested clone path), then
 * refreshes the CS list and the active diff config. No-op when no comparison is
 * set up, so ordinary edits are unaffected.
 */
async function syncCompared() {
  if (!comparedList.value.length || !subscriptionId.value) return
  try {
    await api.post(`rest/service/prov/${subscriptionId.value}/compare/resync`)
  } finally {
    await loadCompared()
    if (activeCs.value != null) {
      const data = await api.get(`rest/subscription/${activeCs.value}/configuration`)
      csConfig.value = data?.configuration || data || null
    }
  }
}

// Fetch the active CS configuration (for the per-resource diff) when it changes,
// and persist the choice.
watch(activeCs, async (id) => {
  if (typeof localStorage !== 'undefined') {
    if (id == null) localStorage.removeItem(activeCsStorageKey())
    else localStorage.setItem(activeCsStorageKey(), String(id))
  }
  if (id == null) { csConfig.value = null; return }
  const data = await api.get(`rest/subscription/${id}/configuration`, { silent: true }).catch(() => null)
  csConfig.value = data?.configuration || data || null
})

// CS resources indexed by "type:name" for the active metric (cost or CO₂).
const csIndex = computed(() =>
  activeCs.value == null ? null : valueIndex(csConfig.value, viewMode.value === 'co2' ? 'co2' : 'cost'),
)

/* MS resources the active CS could NOT reproduce (recorded as ProvLookupError),
 * keyed "type:name" — so those rows are flagged "not available on the CS" rather
 * than silently showing no delta (which would read as "same cost"). */
const csErrorKeys = computed(() => {
  const set = new Set()
  const cs = comparedList.value.find((c) => String(c.subscription) === String(activeCs.value))
  for (const e of cs?.errors || []) {
    if (e?.name != null && e?.resourceType != null) set.add(`${String(e.resourceType).toLowerCase()}:${e.name}`)
  }
  return set
})

/** True when a row could not be reproduced on the active CS (errored / no match). */
function rowErrored(tabKey, item) {
  return activeCs.value != null && csErrorKeys.value.has(`${tabKey}:${item.name}`)
}

/** The matching CS resource for an MS row (by name within its type), or null. */
function csResourceOf(tabKey, name) {
  const cfg = csConfig.value
  if (activeCs.value == null || !cfg || name == null) return null
  const listField = TAB_TYPES.find((t) => t.key === tabKey)?.listField
  const rows = listField ? cfg[listField] : null
  return Array.isArray(rows) ? rows.find((r) => r?.name === name) || null : null
}

/* Whole-quote MS→CS summary over the compared compute types. Unmatched
 * resources count as their MS value (see comparisonSummary), so an unavailable
 * price reads as "no change", and the unmatched count is surfaced separately. */
const compareSummary = computed(() =>
  activeCs.value == null || !config.value
    ? null
    : comparisonSummary(config.value, csIndex.value, viewMode.value === 'co2' ? 'co2' : 'cost'),
)
const csTotalDiff = computed(() => compareSummary.value?.pct ?? null)

/** Per-type rows for the summary tooltip (only types that have resources). */
const summaryRows = computed(() =>
  Object.entries(compareSummary.value?.byType || {}).map(([type, v]) => ({ type, ...v })),
)
const COMPARE_TYPE_ICON = Object.fromEntries(TAB_TYPES.map((t) => [t.key, t.icon]))
const typeIcon = (type) => COMPARE_TYPE_ICON[type] || 'mdi-cube-outline'
/** Format a raw value in the active metric (cost or CO₂). */
function fmtMetric(v) {
  return viewMode.value === 'co2' ? formatCo2(v) : formatCost(v, config.value?.currency)
}
const editType = ref(null)
const editTarget = ref(null)

const subscriptionId = computed(() => route.params.subscription)

/**
 * URLs for the three exports the legacy view exposed. The backend uses
 * the trailing path segment as the suggested filename for the CSVs,
 * which is why it's baked into the URL — preserve that contract.
 *
 * For the JSON the URL is the plain configuration endpoint, so the
 * download filename is set with the `download` attribute instead.
 */
const today = computed(() => new Date().toISOString().slice(0, 10))
const exportUrl = computed(() => {
  const id = subscriptionId.value
  const d = today.value
  if (!id) return { inline: '#', split: '#', json: '#' }
  return {
    inline: `${APP_BASE}rest/service/prov/${id}/ligoj-prov-instances-inline-storage-${id}-${d}.csv`,
    split: `${APP_BASE}rest/service/prov/${id}/ligoj-prov-split-${id}-${d}.csv`,
    json: `${APP_BASE}rest/subscription/${id}/configuration`,
  }
})
const jsonDownloadName = computed(() =>
  subscriptionId.value ? `ligoj-full-${subscriptionId.value}-${today.value}.json` : 'ligoj-full.json',
)

/**
 * Provider node displayed in the header (e.g. `service:prov:aws`).
 * The configuration endpoint already nests the full node chain, so we
 * walk `node.refined` to get to the tool node (`service:prov:<tool>`).
 */
const providerNode = computed(() => meta.value?.node?.refined || null)

/**
 * Rows per type, keyed by tab key. The config payload nests them under
 * plural keys (instances, databases, …) — TAB_TYPES.listField maps the
 * key to the actual array name. Returns `[]` when the field is absent
 * so v-data-table never sees `undefined`.
 */
const rowsByType = computed(() => {
  const out = {}
  for (const tab of TAB_TYPES) {
    out[tab.key] = Array.isArray(config.value?.[tab.listField])
      ? config.value[tab.listField]
      : []
  }
  return out
})

const counts = computed(() => {
  const out = {}
  for (const tab of TAB_TYPES) out[tab.key] = rowsByType.value[tab.key].length
  return out
})

/* ---------- Stat tiles (cost card) ----------
 * Presentation aggregates over the currently VISIBLE rows (so they track
 * the active search alongside the total cost and the donut): instance
 * count plus total vCPU / RAM across the compute types. Same fallback
 * chain as the table cells (own value, else the price type's). */
const statTiles = computed(() => {
  let cpu = 0
  let ram = 0
  for (const key of COMPUTE_KEYS) {
    for (const row of filteredRowsByType.value[key]) {
      cpu += Number(row.cpu ?? row.price?.type?.cpu) || 0
      ram += Number(row.ram ?? row.price?.type?.ram) || 0
    }
  }
  return [
    { key: 'instances', icon: 'mdi-server', label: t('prov.quote.tabs.instance'), value: filteredRowsByType.value.instance.length },
    { key: 'cpu', icon: 'mdi-chip', label: t('prov.quote.cols.cpu'), value: formatCpu(cpu) || '0' },
    { key: 'ram', icon: 'mdi-memory', label: t('prov.quote.cols.ram'), value: formatRam(ram) || '0' },
  ]
})

/* `rowMatches` lives in quoteFormatters.js so the predicate is covered
 * by unit tests (and reusable if another view needs a similar filter). */

/* Search-only view of the rows. Feeds the cost timeline, which must keep
 * showing the full month projection (the month selection only highlights a
 * column there — it doesn't collapse the chart onto itself). */
const searchRowsByType = computed(() => {
  const out = {}
  const q = searchQuery.value || ''
  const matcher = q ? textMatcher(q) : null
  const state = { mode: advMode.value, filters: advFilters.value }
  const hasAdv = state.filters.some((f) => f?.field && f.value != null && f.value !== '')
  for (const tab of TAB_TYPES) {
    const rows = rowsByType.value[tab.key]
    out[tab.key] = (matcher || hasAdv)
      ? rows.filter((r) => (!matcher || quickMatch(r, matcher)) && (!hasAdv || rowPasses(r, tab.key, state, filterCtx)))
      : rows
  }
  return out
})

/* Resolution context for the filter engine: how a row links to its profile
 * names (ids → names via the quote lists), its location and its tags. */
const filterCtx = {
  profileName: (field, row) => scopedName(row?.[field],
    field === 'usage' ? usagesById : field === 'budget' ? budgetsById : optimizersById),
  locationName: (row) => locationOf(row)?.name ?? null,
  tagsFor: (tabKey, id) => tagsFor(tabKey, id),
}

/* Selected timeline month (0-based) or null. Clicking a bar filters every
 * table, the donut and the totals down to the resources billed that month —
 * on top of the per-tab search. */
const selectedMonth = ref(null)

/* The effective per-tab filter: search first, then the month selection. */
const filteredRowsByType = computed(() => {
  const month = selectedMonth.value
  if (month == null) return searchRowsByType.value
  const out = {}
  for (const tab of TAB_TYPES) {
    out[tab.key] = searchRowsByType.value[tab.key].filter((r) => rowInMonth(r, config.value, month))
  }
  return out
})

/* Per-type totals in the active metric (cost or CO₂) for the tab tooltips —
 * the full tab, and the filtered subset when a search / month narrows it. */
function sumMetric(rows) {
  const field = viewMode.value === 'co2' ? 'co2' : 'cost'
  return (rows || []).reduce((s, r) => s + (Number(r?.[field]) || 0), 0)
}
const costByType = computed(() => {
  const out = {}
  for (const tab of TAB_TYPES) out[tab.key] = sumMetric(rowsByType.value[tab.key])
  return out
})
const filteredCostByType = computed(() => {
  const out = {}
  for (const tab of TAB_TYPES) out[tab.key] = sumMetric(filteredRowsByType.value[tab.key])
  return out
})

/* Config for the timeline: search-filtered only, so a month click highlights
 * rather than shrinks the chart. */
const timelineConfig = computed(() => {
  if (!config.value) return config.value
  const clone = { ...config.value }
  for (const tab of TAB_TYPES) clone[tab.listField] = searchRowsByType.value[tab.key]
  return clone
})

function onMonthClick(month) {
  selectedMonth.value = selectedMonth.value === month ? null : month
}

/* Resolve a row's location to the full ProvLocation object (with flag/country
 * data) — the row may carry it inline or only reference it by code name via
 * its price, so we look it up in the quote's `locations` list. */
const locationsByName = computed(() => {
  const out = {}
  for (const l of config.value?.locations || []) if (l?.name) out[l.name] = l
  return out
})
function locationOf(item) {
  const ref = item?.location || item?.price?.location
  if (!ref) return null
  const name = typeof ref === 'string' ? ref : ref.name
  return locationsByName.value[name] || (typeof ref === 'object' ? ref : null)
}

/* Resource-level usage/optimizer serialize to their id (ToIdSerializer), so a
 * row carries a number, not an object. Resolve it to the profile name via the
 * quote's `usages` / `optimizers` lists (tolerating an inline object too). */
const usagesById = computed(() => {
  const out = {}
  for (const u of config.value?.usages || []) if (u?.id != null) out[u.id] = u
  return out
})
const budgetsById = computed(() => {
  const out = {}
  for (const b of config.value?.budgets || []) if (b?.id != null) out[b.id] = b
  return out
})
const optimizersById = computed(() => {
  const out = {}
  for (const o of config.value?.optimizers || []) if (o?.id != null) out[o.id] = o
  return out
})
function scopedName(ref, byId) {
  if (ref == null) return ''
  if (typeof ref === 'object') return ref.name || ''
  return byId.value[ref]?.name || ''
}

/* ---------- Filtered totals ----------
 * A search hides rows on one or more tabs. So the header total and the
 * breakdown reflect what the user is actually looking at, we apply the
 * DELTA of the hidden resources' costs to the authoritative aggregate
 * (`config.cost`) rather than recomputing from scratch — support/initial
 * coupling in the backend total is preserved, only the removed rows'
 * direct cost/maxCost is subtracted. */
const anyFilterActive = computed(() => TAB_TYPES.some((tab) => isTabFiltered(tab.key)))

/** Sum of the hidden rows' costs across every tab: `{ min, max }`. */
const filteredDeltaCost = computed(() => {
  let min = 0
  let max = 0
  if (!anyFilterActive.value) return { min, max }
  for (const tab of TAB_TYPES) {
    if (!isTabFiltered(tab.key)) continue
    const visible = new Set(filteredRowsByType.value[tab.key])
    const hidden = rowsByType.value[tab.key].filter((r) => !visible.has(r))
    const d = sumCostRange(hidden)
    min += d.min
    max += d.max
  }
  return { min, max }
})

/**
 * Header total, adjusted for the active filter. Passes `config.cost`
 * through untouched when nothing is filtered; otherwise subtracts the
 * hidden-rows delta from `min`/`max` (leaving a null bound null).
 */
const displayedQuoteCost = computed(() => {
  const base = config.value?.cost
  if (!base || !anyFilterActive.value) return base
  const { min: dMin, max: dMax } = filteredDeltaCost.value
  return {
    ...base,
    // clamp: float drift / support coupling must never yield a negative total.
    min: base.min != null ? Math.max(0, base.min - dMin) : base.min,
    max: base.max != null ? Math.max(0, base.max - dMax) : base.max,
  }
})

/**
 * Carbon counterpart of `displayedQuoteCost`. Unlike cost, the quote's
 * `co2`/`maxCo2` aggregates aren't populated in the configuration payload,
 * so we sum the per-resource emissions directly over `filteredConfig` — the
 * exact same rows the breakdown donut sums, so the headline total always
 * matches the pie (and follows the search + month filters for free).
 */
const displayedQuoteCo2 = computed(() => {
  const cfg = filteredConfig.value
  let min = 0
  let max = 0
  if (cfg) {
    for (const tab of TAB_TYPES) {
      const rows = Array.isArray(cfg[tab.listField]) ? cfg[tab.listField] : []
      for (const r of rows) {
        if (!r) continue
        min += Number(r.co2) || 0
        max += Number(r.maxCo2 ?? r.co2) || 0
      }
    }
  }
  return { min, max }
})

/**
 * Config clone whose resource lists hold only the filtered rows, so the
 * cost-breakdown donut/legend recompute over the visible set. Returns the
 * original config (same identity) when no filter is active.
 */
const filteredConfig = computed(() => {
  if (!config.value || !anyFilterActive.value) return config.value
  const clone = { ...config.value }
  for (const tab of TAB_TYPES) {
    clone[tab.listField] = filteredRowsByType.value[tab.key]
  }
  return clone
})

/* ---------- Tags ---------- *
 * The configuration's `tags` map is keyed by resource type (the legacy
 * lower-cases it once on load — same trick here) and then by resource
 * id. Each entry is an array of `{ name, value }`. */
const tagsByTypeAndId = computed(() => {
  const src = config.value?.tags
  if (!src || typeof src !== 'object') return {}
  const out = {}
  for (const [type, byId] of Object.entries(src)) {
    out[type.toLowerCase()] = byId || {}
  }
  return out
})

function tagsFor(type, id) {
  const byId = tagsByTypeAndId.value[type]
  if (!byId) return []
  const list = byId[id]
  return Array.isArray(list) ? list : []
}

/* Compute resource types (carry CPU / RAM); used by the stat tiles. */
const COMPUTE_KEYS = new Set(['instance', 'container', 'function', 'database'])

/**
 * Headers per type. Kept small and read-only for iteration 1; CRUD
 * affordances land in iteration 2 once the modal flow is migrated.
 */
const headersByType = computed(() => {
  const name = { title: t('prov.quote.cols.name'), key: 'name', sortable: true }
  const cpu = { title: t('prov.quote.cols.cpu'), key: 'cpu', sortable: true, width: '90px', align: 'end' }
  const ram = { title: t('prov.quote.cols.ram'), key: 'ram', sortable: true, width: '110px', align: 'end' }
  const type = { title: t('prov.quote.cols.type'), key: 'type', sortable: true }
  const term = { title: t('prov.quote.cols.term'), key: 'term', value: (item) => item.price?.term?.name || '', sortable: true }
  const usage = { title: t('prov.quote.cols.usage'), key: 'usage', value: (item) => scopedName(item.usage, usagesById), sortable: true }
  const optimizer = { title: t('prov.quote.cols.optimizer'), key: 'optimizer', value: (item) => scopedName(item.optimizer, optimizersById), sortable: true }
  // Flag-only cell + pin-icon header (see the #item.location / #header.location slots).
  const loc = { title: t('prov.quote.cols.location'), key: 'location', sortable: true, width: '64px', align: 'center' }
  const cost = { title: t('prov.quote.cols.cost'), key: 'cost', sortable: true, width: '140px', align: 'end' }
  // Single per-row cog (RowActionsMenu) + the header tools cog live in
  // this column, so it only needs room for one icon button. `minWidth`
  // keeps Vuetify from collapsing it when other columns claim the space.
  const actions = {
    title: '', key: 'actions', sortable: false, align: 'end',
    width: '72px', minWidth: '72px',
    cellProps: { class: 'actions-cell' },
  }
  const compute = [
    name,
    { title: t('prov.quote.cols.quantity'), key: 'minQuantity', sortable: true, width: '70px', align: 'end' },
    cpu, ram, type, loc, term, usage, optimizer, cost,
  ]

  return {
    instance: [...compute.slice(0, 4), { title: t('prov.quote.cols.os'), key: 'os', sortable: true }, ...compute.slice(4), actions],
    container: [...compute, actions],
    function: [...compute, actions],
    database: [name, cpu, ram,
      { title: t('prov.quote.cols.engine'), key: 'engine', sortable: true },
      type, loc, term, usage, optimizer, cost, actions],
    storage: [name,
      { title: t('prov.quote.cols.size'), key: 'size', sortable: true, width: '110px', align: 'end' },
      type, loc,
      { title: t('prov.quote.cols.attachedTo'), key: 'attachedTo', sortable: false },
      term, cost, actions],
    support: [name,
      { title: t('prov.quote.cols.level'), key: 'level', sortable: true },
      { title: t('prov.quote.cols.seats'), key: 'seats', sortable: true, width: '90px', align: 'end' },
      type, term, cost, actions],
  }
})

function tabLabel(key) {
  return t(`prov.quote.tabs.${key}`)
}

/** True when the search is actively hiding rows in this tab. */
function isTabFiltered(key) {
  return filteredRowsByType.value[key].length !== rowsByType.value[key].length
}

/**
 * Count shown in a tab's chip. Renders the filtered count alone when no
 * search is hiding rows (`12`), and only appends the total when a filter
 * is active (`3/12`) — so an unfiltered tab never shows a redundant
 * `12/12`.
 */
function tabCountLabel(key) {
  const total = rowsByType.value[key].length
  const filtered = filteredRowsByType.value[key].length
  return isTabFiltered(key) ? `${filtered}/${total}` : String(total)
}

/**
 * Storage rows reference their host resource via `quoteInstance`,
 * `quoteDatabase`, etc. Display the attachment name when present.
 */
function attachedLabel(storage) {
  return (
    storage?.quoteInstance?.name
    || storage?.quoteDatabase?.name
    || storage?.quoteContainer?.name
    || storage?.quoteFunction?.name
    || ''
  )
}

async function loadConfig() {
  if (!subscriptionId.value) return
  loading.value = true
  error.value = null
  try {
    const data = await api.get(`rest/subscription/${subscriptionId.value}/configuration`)
    if (!data) {
      error.value = t('common.loadFailed') || 'Failed to load quote configuration.'
      return
    }
    // The endpoint returns a wrapper: { subscription, project, node,
    // parameters, configuration: { … the quote … } }. Older shape
    // (legacy plain quote at the root) is handled defensively.
    if (data.configuration) {
      config.value = data.configuration
      meta.value = { subscription: data.subscription, project: data.project, node: data.node, parameters: data.parameters }
    } else {
      config.value = data
      meta.value = null
    }
  } finally {
    loading.value = false
  }
}

async function reload() {
  refreshing.value = true
  try {
    await loadConfig()
  } finally {
    refreshing.value = false
  }
}

/**
 * Calls the legacy `PUT service/prov/<sub>/refresh` which re-runs the
 * price discovery against the current provider catalog. The response
 * is the new aggregate cost. If anything moved we reload the whole
 * configuration to pick up the new per-resource costs; otherwise we
 * just inform the user nothing changed (matches legacy
 * `reloadAsNeed`). Reloading is the simplest path — the response
 * doesn't carry per-resource deltas. */
async function refreshPrices() {
  if (!subscriptionId.value) return
  refreshingPrices.value = true
  try {
    const newCost = await api.put(`rest/service/prov/${subscriptionId.value}/refresh`, null)
    if (newCost == null) return
    const conf = config.value
    const changed =
      !conf?.cost
      || newCost.min !== conf.cost.min
      || newCost.max !== conf.cost.max
      || newCost.unbound !== conf.cost.unbound
    if (changed) {
      errorStore.success(t('prov.quote.refreshPrices.changed'))
      await loadConfig()
    } else {
      // Not an error: informative notice only
      errorStore.info(t('prov.quote.refreshPrices.noChange'))
    }
  } finally {
    refreshingPrices.value = false
  }
}

/* ----------------- Edit-quote ---------------- */

function openEdit() {
  const conf = config.value || {}
  editForm.name = conf.name || ''
  editForm.description = conf.description || ''
  editForm.location = conf.location?.name ?? null
  editForm.usage = conf.usage?.name ?? null
  editForm.budget = conf.budget?.name ?? null
  editForm.optimizer = conf.optimizer?.name ?? null
  editForm.reservationMode = conf.reservationMode || 'reserved'
  editForm.physical = conf.physical ?? null
  editForm.ramAdjustedRate = conf.ramAdjustedRate || 100
  editDialog.value = true
}

/**
 * Saves the quote. The backend PUT expects the FULL quote shape: any
 * field we omit is treated as a reset. So we send the form values plus
 * the current values of fields not exposed in the modal (license /
 * processor / architecture — those need per-tool catalog lookups that
 * aren't migrated yet and stay on iteration 4b+).
 *
 * The dropdown sources in the form bind to a string (the resource's
 * `name`); for `processor` / `architecture` the backend accepts a
 * string id directly. `physical` is a tri-state: `true` / `false` /
 * `null` (no constraint).
 */
async function saveEdit() {
  const { valid } = await formRef.value.validate()
  if (!valid) return
  saving.value = true
  try {
    const conf = config.value || {}
    const payload = {
      name: editForm.name,
      description: editForm.description,
      location: editForm.location,
      usage: editForm.usage,
      budget: editForm.budget,
      optimizer: editForm.optimizer,
      reservationMode: editForm.reservationMode,
      physical: editForm.physical,
      ramAdjustedRate: editForm.ramAdjustedRate,
      // Not in the form yet — keep current values to avoid wiping them.
      license: conf.license ?? null,
      processor: conf.processor?.id ?? conf.processor ?? null,
      architecture: conf.architecture?.id ?? conf.architecture ?? null,
    }
    const result = await api.put(`rest/service/prov/${subscriptionId.value}`, payload)
    if (result === null) return // useApi already surfaced the error
    errorStore.success(t('prov.quote.saved', { name: editForm.name }))
    editDialog.value = false
    await reload()
  } finally {
    saving.value = false
  }
}

/* ----------------- Delete row & delete-all ---------------- */

function askDeleteRow(type, row) {
  deleteRowTarget.value = { type, row }
  deleteRowDialog.value = true
}

async function confirmDeleteRow() {
  const { type, row } = deleteRowTarget.value || {}
  if (!type || !row) return
  deleting.value = true
  try {
    const result = await api.del(`rest/service/prov/${type}/${row.id}`)
    if (result === null) return
    errorStore.success(t('prov.quote.delete.row.done', { name: row.name || `#${row.id}` }))
    deleteRowDialog.value = false
    deleteRowTarget.value = null
    await reload()
    await syncCompared()
  } finally {
    deleting.value = false
  }
}

function askDeleteAll(type) {
  deleteAllType.value = type
  deleteAllDialog.value = true
}

function askDeleteBulk(type) {
  if (!selectedByType[type]?.length) return
  deleteBulkType.value = type
  deleteBulkDialog.value = true
}

/**
 * Deletes the selection. Compute types go through the server-side bulk
 * endpoint (one transaction, one recompute); storage/support keep the
 * per-id fan-out (no bulk endpoint for them yet).
 */
async function confirmDeleteBulk() {
  const type = deleteBulkType.value
  if (!type) return
  const ids = [...(selectedByType[type] || [])]
  if (ids.length === 0) {
    deleteBulkDialog.value = false
    deleteBulkType.value = null
    return
  }
  deleting.value = true
  try {
    if (COMPUTE_TYPES.has(type)) {
      await api.post(`rest/service/prov/${subscriptionId.value}/bulk/${type}/delete`, ids)
    } else {
      for (const id of ids) {
        await api.del(`rest/service/prov/${type}/${id}`)
      }
    }
    errorStore.success(t('prov.quote.delete.bulk.done', { type: tabLabel(type), count: ids.length }))
    deleteBulkDialog.value = false
    deleteBulkType.value = null
    selectedByType[type] = []
    await reload()
    await syncCompared()
  } finally {
    deleting.value = false
  }
}

/* ----------------- Per-type create / edit ---------------- */

const COMPUTE_TYPES = new Set(['instance', 'container', 'function', 'database'])

function openResourceCreate(type) {
  editType.value = type
  editTarget.value = null
  if (type === 'storage') storageDialog.value = true
  else if (type === 'support') supportDialog.value = true
  else if (COMPUTE_TYPES.has(type)) computeDialog.value = true
}

function openResourceEdit(type, row) {
  editType.value = type
  editTarget.value = row
  if (type === 'storage') storageDialog.value = true
  else if (type === 'support') supportDialog.value = true
  else if (COMPUTE_TYPES.has(type)) computeDialog.value = true
}

/**
 * Opens the create dialog pre-populated from `row`. The dialog
 * detects `id == null` as "create mode" and skips the PUT path, so
 * stripping `id` is enough to turn an edit into a duplicate. Name
 * suffixed with " (copy)" to avoid a duplicate-name validation
 * collision; user can rename freely before saving.
 */
function openResourceDuplicate(type, row) {
  if (!row) return
  editType.value = type
  editTarget.value = { ...row, id: null, name: `${row.name || ''} (copy)`.trim() }
  if (type === 'storage') storageDialog.value = true
  else if (type === 'support') supportDialog.value = true
  else if (COMPUTE_TYPES.has(type)) computeDialog.value = true
}

/* ----- Row actions (grouped in the per-row cog) ----- *
 * Same three actions on every row; the labels are reactive to the locale
 * so this is a computed rather than a module constant. */
const rowActions = computed(() => [
  { key: 'edit',      title: t('common.edit'),          icon: 'mdi-pencil' },
  { key: 'duplicate', title: t('prov.quote.duplicate'), icon: 'mdi-content-duplicate' },
  { key: 'delete',    title: t('common.delete'),        icon: 'mdi-delete', color: 'error' },
])

function onRowAction(type, row, key) {
  if (key === 'edit') openResourceEdit(type, row)
  else if (key === 'duplicate') openResourceDuplicate(type, row)
  else if (key === 'delete') askDeleteRow(type, row)
}

/* ----- Table-level actions (header tools cog) ----- *
 * "Delete all" now lives in the table header cog instead of the toolbar. */
const tableToolActions = computed(() => [
  { key: 'delete-all', title: t('prov.quote.delete.all.label'), icon: 'mdi-delete-sweep', color: 'error' },
])

function onToolAction(type, key) {
  if (key === 'delete-all') askDeleteAll(type)
}

/**
 * Row-click opens the editor (replacing the old pencil icon). Clicks that
 * land on an interactive control — the selection checkbox, the row-action
 * cog, links, inputs — are ignored so those keep their own behaviour.
 * `item` is Vuetify 4's raw row (guard the `.raw` shape just in case).
 */
function onRowClick(type, event, item) {
  if (event?.target?.closest?.('button, a, input, .v-selection-control, .no-row-edit')) return
  const row = item?.raw ?? item
  if (row) openResourceEdit(type, row)
}

async function onResourceSaved() {
  await reload()
  await syncCompared()
}

async function confirmDeleteAll() {
  const type = deleteAllType.value
  if (!type) return
  const count = rowsByType.value[type]?.length || 0
  deleting.value = true
  try {
    const result = await api.del(`rest/service/prov/${subscriptionId.value}/${type}`)
    if (result === null) return
    errorStore.success(t('prov.quote.delete.all.done', { type: tabLabel(type), count }))
    deleteAllDialog.value = false
    deleteAllType.value = null
    await reload()
    await syncCompared()
  } finally {
    deleting.value = false
  }
}

function setBreadcrumbs() {
  /* Passed as a FACTORY so the host can re-run it on a locale change (the crumb
   * titles use `t()`); it also re-reads the latest reactive data each call.
   * Full path: Home → Projects → <project name> → Provisioning → <quote>.
   * The project segment is only emitted once the configuration has landed
   * (read from `meta.value.project`, populated by loadConfig). */
  app.setBreadcrumbs(() => {
    const id = subscriptionId.value
    const project = meta.value?.project
    const crumbs = [
      { title: t('nav.home'), to: '/' },
      { title: t('nav.projects'), to: '/home/project' },
    ]
    if (project?.id) {
      crumbs.push({
        title: project.name || `#${project.id}`,
        to: `/home/project/${project.id}`,
      })
    }
    crumbs.push({ title: t('prov.title') })
    crumbs.push({ title: config.value?.name || `#${id}` })
    return crumbs
  }, { refresh: reload })
}

// Re-run when the quote name, the subscription id, or the parent
// project (id + name) change. The watch fires once for the empty
// initial state and once more after `loadConfig` lands.
watch(
  [() => config.value?.name, subscriptionId, () => meta.value?.project?.id, () => meta.value?.project?.name],
  setBreadcrumbs,
)

onMounted(async () => {
  setBreadcrumbs()
  loadSavedViews()
  loadSharedViews()
  restoreLastApplied()
  restoreSearchState()
  await loadConfig()
  setBreadcrumbs()
  loadCompared()
})
</script>

<style scoped>
/* All colours below come from theme tokens only: Vuetify semantic
 * variables (rgb/rgba(var(--v-theme-…))) and the shared `.lj-surface`
 * design variables (--ink / --card / --border / --pill / --accent /
 * --radius / --mono …) set by the host for the 2026 views. */
.quote-view {
  padding: 0.5rem;
}

/* ---------- Header ---------- */
.q-head {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.q-cost {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

/* Total value + compare delta + "unmatched" flag on a single row (the delta and
 * the flag sit to the right of the amount), keeping the header compact. */
.q-cost-line {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
}

.q-cost-eff {
  margin-top: 8px;
}

.q-cost-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--ink-3);
}

/* A search narrows the total: tint the label + value with the accent
 * colour and surface a small filter glyph so the figure reads as a
 * filtered subtotal, not the whole quote. */
.q-cost--filtered .q-cost-label,
.q-cost-filter-ic {
  color: rgb(var(--v-theme-primary));
}

.q-cost-filter-ic {
  margin-left: 3px;
  vertical-align: text-top;
}

.q-cost--filtered .q-cost-value {
  color: rgb(var(--v-theme-primary));
}

.q-cost-value {
  font-size: 25px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  font-family: var(--mono);
  color: var(--ink);
  line-height: 1.25;
  white-space: nowrap;
}

.q-cost-suffix {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-3);
  font-family: var(--font);
}

.q-tools {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 3px 6px;
  border-radius: 999px;
  border: var(--border-w) var(--lj-border-style, solid) var(--border-c);
  background: var(--card);
}

.q-tools .v-btn {
  color: var(--ink-2);
}

/* ---------- Cost card ---------- */
.q-costcard {
  border-radius: var(--radius);
  border: var(--border-w) var(--lj-border-style, solid) var(--border-c);
  background: var(--card);
  box-shadow: var(--shadow);
}

.q-card-title {
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--ink-3);
}

.q-costcard-body {
  display: flex;
  align-items: center;
  gap: 28px;
  flex-wrap: wrap;
}

/* Breakdown grouping controls, next to the title. */
.q-groupby-key {
  min-width: 130px;
  max-width: 190px;
}

.q-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
  flex: 1;
  min-width: 260px;
}

.q-stat {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border-radius: var(--radius-sm);
  border: var(--border-w) var(--lj-border-style, solid) var(--border-c);
  background: var(--surface);
}

.q-stat-ic {
  width: 36px;
  height: 36px;
  flex: none;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--pill);
  color: var(--accent);
}

.q-stat-num {
  font-family: var(--mono);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  font-size: 19px;
  line-height: 1.1;
  color: var(--ink);
}

.q-stat-label {
  font-size: 11.5px;
  font-weight: 600;
  color: var(--ink-3);
  margin-top: 2px;
}

/* ---------- Tabs ---------- */
/* Tabs + global search + compact actions on one row. The tabs shrink first
 * (icon-only, so they compress well); the search keeps a usable width. */
.q-tabs-row {
  flex-wrap: wrap;
}

.q-tabs {
  border-bottom: var(--border-w) var(--lj-border-style, solid) var(--border-c);
  flex: 0 1 auto;
  min-width: 0;
}

.q-tabs :deep(.v-tab) {
  text-transform: none;
  letter-spacing: 0;
  font-weight: 600;
  min-width: 56px;
}

.q-tabs :deep(.v-tab__slider) {
  height: 3px;
  border-radius: 3px 3px 0 0;
}

/* Tab tooltip: type recall + count/cost, and the filtered subset. */
:global(.q-tab-tip .q-tab-tip-head) {
  font-size: 0.85em;
}
:global(.q-tab-tip .q-tab-tip-filtered) {
  font-size: 0.8em;
  color: rgb(var(--v-theme-primary));
  font-weight: 600;
}

.q-count {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

/* When a search narrows the tab, the chip switches to the accent colour
 * (via the `primary` chip color) and gains a subtle ring so the filtered
 * "3/12" reads as an active-filter state rather than a plain total. */
.q-count-filtered {
  font-weight: 800;
  box-shadow: 0 0 0 1px rgba(var(--v-theme-primary), 0.45);
}

/* ---------- Global search (tabs row) ---------- */
.quote-search {
  max-width: 280px;
  min-width: 170px;
  flex: 1 1 auto;
}

.quote-search :deep(.v-field) {
  border-radius: 999px;
}

/* ---------- Table ---------- */
.q-table :deep(.v-table) {
  background: transparent;
}

.q-table :deep(thead th) {
  font-size: 11.5px !important;
  font-weight: 700 !important;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ink-3) !important;
  white-space: nowrap;
}

.q-table :deep(tbody td) {
  font-size: 13.5px;
  color: var(--ink-2);
}

.q-table :deep(tbody tr:hover td) {
  background: var(--hover);
}

/* Numeric columns line up on tabular figures. */
.q-table :deep(td.v-data-table-column--align-end) {
  font-variant-numeric: tabular-nums;
}

.q-cell-name {
  font-weight: 600;
  color: var(--ink);
}

.q-cell-cost {
  font-family: var(--mono);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--ink);
  font-size: 12.5px;
}

/* MS↔CS comparison delta shown beside a cell cost and the header total. */
.q-cell-cost-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
}
.q-cost-diff {
  display: inline-flex;
  align-items: center;
  gap: 1px;
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  cursor: help;
}
.q-cost-unmatched {
  cursor: help;
}

/* Tag key ("env" in "env:TST") — de-emphasised prefix before the value.
 * Thinner + smaller than the value, with a small gap replacing the ':'. */
.q-tag-key {
  font-size: 0.82em;
  font-weight: 400;
  opacity: 0.65;
  margin-right: 3px;
}

.q-type {
  font-family: var(--mono);
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-2);
  background: var(--pill);
  border-radius: var(--radius-sm);
  padding: 2px 8px;
  white-space: nowrap;
}

.q-loc {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11.5px;
  font-weight: 700;
  color: var(--ink-2);
  background: var(--pill);
  border-radius: 999px;
  padding: 3px 10px;
  white-space: nowrap;
}

.q-loc :deep(.v-icon) {
  color: var(--ink-3);
}

.q-os,
.q-engine {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

/* Location column: only a flag in the body and a pin in the header, so
 * both stay centred in the narrow column. */
.q-loc-header {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: rgba(var(--v-theme-on-surface), 0.7);
}
.q-loc-cell {
  justify-content: center;
}

/* Row-click opens the editor, so make the whole row read as clickable. */
.q-table :deep(tbody tr) {
  cursor: pointer;
}

/* The per-row actions cog stays quiet until the row is hovered or
 * focused. Pointer devices only — touch users keep it always visible. */
@media (hover: hover) {
  .q-table :deep(.actions-cell .v-btn) {
    opacity: 0.35;
    transition: opacity 120ms ease;
  }

  .q-table :deep(tbody tr:hover .actions-cell .v-btn),
  .q-table :deep(tbody tr:focus-within .actions-cell .v-btn) {
    opacity: 1;
  }
}
</style>

<!--
  Unscoped: the v-data-table renders cells via a render function, so
  `<td>` elements live outside this component's scoped class. The
  selector is unique enough not to bleed into other tables.
-->
<style>
.v-data-table td.actions-cell {
  white-space: nowrap;
}
</style>
