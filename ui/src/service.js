import { h } from 'vue'
import { VBtn, VChip, VIcon, useI18nStore } from '@ligoj/host'

const REST = '/rest/'

/* Small format helpers — kept inside the plugin so its details column
 * has no dependency on private host modules. Mirrors legacy
 * `formatRam`/`formatStorage` from service/prov/prov.js. */
function formatRam(mb) {
  if (mb == null) return ''
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${Math.round(mb)} MB`
}
function formatStorage(gb) {
  if (gb == null) return ''
  return gb >= 1024 ? `${(gb / 1024).toFixed(1)} TB` : `${Math.round(gb)} GB`
}

/**
 * Service surface for the "prov" plugin. Exposes the small set of
 * cross-plugin actions other plugins / the host may call via
 * `callFeature('prov', action, …)`, plus their raw functions for
 * direct ES imports.
 *
 * Most of the legacy `service/prov/prov.js` is UI orchestration that
 * belongs in the views — only the operations another plugin would
 * reasonably reach for live here.
 */
const service = {
  /**
   * Plugin-contributed buttons rendered next to the host's unsubscribe
   * icon on the ProjectDetailView subscription rows. Returns VNodes
   * directly — the host mounts them as-is without HTML interpretation,
   * mirroring the legacy `renderFeatures` convention.
   *
   * For a provisioning subscription the action is "Open quote" — links
   * to the per-subscription quote editor.
   */
  renderFeatures(subscription) {
    const { t } = useI18nStore()
    return [
      h(
        VBtn,
        {
          icon: true,
          size: 'small',
          variant: 'text',
          title: t('prov.renderFeatures.quote'),
          to: `/prov/quote/${subscription?.id ?? ''}`,
        },
        () => h(VIcon, { size: 'small' }, () => 'mdi-calculator'),
      ),
    ]
  },

  /**
   * Plugin-rendered subscription details for the "Details" column. For
   * a provisioning subscription we surface a compact resource summary
   * (instances/databases/containers/functions, total CPU/RAM, public
   * access, total storage, location) — matches the legacy
   * `renderDetailsKey` carousel from service/prov/prov.js, just flatter.
   *
   * Returns null when no quote data is available so the cell stays
   * empty rather than showing a noisy "—".
   */
  renderDetailsKey(subscription) {
    const { t } = useI18nStore()
    const quote = subscription?.data?.quote
    if (!quote) return null

    const chips = []
    const chip = (icon, value, titleKey) =>
      h(
        VChip,
        { size: 'x-small', variant: 'tonal', class: 'mr-1', title: t(titleKey) },
        () => [h(VIcon, { start: true, size: 'x-small' }, () => icon), ' ', String(value)],
      )

    if (quote.nbInstances) chips.push(chip('mdi-server', quote.nbInstances, 'prov.renderDetailsKey.instances'))
    if (quote.nbDatabases) chips.push(chip('mdi-database', quote.nbDatabases, 'prov.renderDetailsKey.databases'))
    if (quote.nbFunctions) chips.push(chip('mdi-lambda', quote.nbFunctions, 'prov.renderDetailsKey.functions'))
    if (quote.nbContainers) chips.push(chip('mdi-docker', quote.nbContainers, 'prov.renderDetailsKey.containers'))

    // Aggregated CPU/RAM are only meaningful when at least one
    // compute resource is in the quote — matches the legacy guard.
    if (quote.nbInstances || quote.nbDatabases || quote.nbContainers || quote.nbFunctions) {
      if (quote.totalCpu) chips.push(chip('mdi-flash', `${quote.totalCpu} ${t('prov.renderDetailsKey.cpuUnit')}`, 'prov.renderDetailsKey.cpu'))
      if (quote.totalRam) chips.push(chip('mdi-memory', formatRam(quote.totalRam), 'prov.renderDetailsKey.ram'))
    }
    if (quote.nbPublicAccess) chips.push(chip('mdi-earth', quote.nbPublicAccess, 'prov.renderDetailsKey.publicAccess'))
    if (quote.totalStorage) chips.push(chip('mdi-harddisk', formatStorage(quote.totalStorage), 'prov.renderDetailsKey.storage'))

    if (quote.location?.name) {
      chips.push(chip('mdi-map-marker', quote.location.name, 'prov.renderDetailsKey.location'))
    }

    if (chips.length === 0) return null
    return h('div', { class: 'd-inline-flex flex-wrap align-center' }, chips)
  },

  /**
   * Administration-menu contribution. The host's `mergeNav` engine inserts
   * these entries into the shared Administration ("System") menu via the
   * `renderNav` feature — a declarative `{ menu, children }` insert (no VNodes).
   * They append after the built-in system screens; the `divider` on the first
   * entry labels the block with the plugin name (its ownership notice).
   *
   * Provisioning contributes its three admin screens — catalog, currency and
   * terraform — whose routes are already registered in `install()`. These were
   * the legacy `service/prov/*` administration pages; they have no
   * per-subscription context, so the Administration menu (not a subscription
   * row) is their natural home.
   */
  renderNav() {
    const { t } = useI18nStore()
    return {
      menu: 'nav.system',
      children: [
        { id: 'prov-catalog', label: t('catalog.title'), icon: 'mdi-database-search', route: '/prov/catalog', divider: 'Provisioning' },
        { id: 'prov-currency', label: t('currency.title'), icon: 'mdi-cash-multiple', route: '/prov/currency' },
        { id: 'prov-terraform', label: t('terraform.title'), icon: 'mdi-terraform', route: '/prov/terraform' },
      ],
    }
  },

  /**
   * Triggers a catalog update for a given provider node.
   * `force` switches to the "full" update (prices + types) instead of
   * the standard prices-only refresh.
   */
  async requestCatalogUpdate(nodeId, { force = false } = {}) {
    const resp = await fetch(
      `${REST}service/prov/catalog/${encodeURIComponent(nodeId)}${force ? '?force=true' : ''}`,
      { method: 'POST', credentials: 'include' },
    )
    if (!resp.ok) throw new Error(`Catalog update failed: ${resp.status}`)
    return true
  },

  /**
   * Polls a long-running provisioning task (catalog update, terraform
   * apply, …) until completion. Returns the cleanup handle so callers
   * can cancel.
   */
  scheduleTaskPoll(url, onPartial, onDone, intervalMs = 1500) {
    const handle = setInterval(
      () => service._poll(url, onPartial, onDone, handle),
      intervalMs,
    )
    return handle
  },

  async _poll(url, onPartial, onDone, handle) {
    try {
      const resp = await fetch(REST + url, { credentials: 'include' })
      if (!resp.ok) return
      const data = await resp.json()
      onPartial?.(data)
      if (data.end || data.finished) {
        clearInterval(handle)
        onDone?.(data)
      }
    } catch (err) {
      console.error('[plugin:prov] task poll error', err)
    }
  },
}

export default service
