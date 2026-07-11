// Load the sibling index.css at runtime. Vite's library build emits it as
// a separate file but does NOT add `import './index.css'` to the JS entry
// — so when the host dynamic-imports this bundle the stylesheet never
// loads. Injecting a <link rel="stylesheet"> resolved against
// import.meta.url keeps the approach path-agnostic.
if (typeof document !== 'undefined') {
  const id = 'ligoj-plugin-prov-css'
  if (!document.getElementById(id)) {
    const link = document.createElement('link')
    link.id = id
    link.rel = 'stylesheet'
    const cssUrl = new URL(/* @vite-ignore */ './index.css', import.meta.url)
    // Carry over the loader's `?v=<digest>` token (URL resolution drops the
    // query) so the stylesheet is long-cached and busted with the bundle.
    cssUrl.search = new URL(import.meta.url).search
    link.href = cssUrl.href
    document.head.appendChild(link)
  }
}

/*
 * Plugin "prov" — Provisioning (currencies, catalogs, terraform, network,
 * subscription-level quote editor).
 *
 * Contract consumed by the Ligoj Vue host:
 *   - id         : stable plugin identifier
 *   - label      : display name
 *   - component  : root Vue component (plugin shell)
 *   - install    : called once at registration; receives ctx.router so the
 *                  plugin can register its own routes dynamically
 *   - feature    : single entry point callable from the app and other plugins
 *                  (action dispatcher over the plugin's service functions)
 *   - service    : raw service functions (direct ES access)
 *   - meta       : presentation hints (icon, color)
 *
 * Authored as source — compiled to `/webjars/prov/vue/index.js` by Vite.
 * Shared host surface (stores, composables) is imported from `@ligoj/host`,
 * kept external at build so plugin and host share the same instances.
 *
 * Only `CurrencyListView` is a real Vue port at this slice. The other
 * routes resolve to "not migrated" stubs so navigation does not 404 while
 * the rest of the legacy `service/prov/prov.js` is being ported.
 */
import { useI18nStore } from '@ligoj/host'
import ProvPlugin from './ProvPlugin.vue'
import CurrencyListView from './views/CurrencyListView.vue'
import CatalogListView from './views/CatalogListView.vue'
import TerraformView from './views/TerraformView.vue'
import NetworkView from './views/NetworkView.vue'
import QuoteView from './views/QuoteView.vue'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {
  requestCatalogUpdate: service.requestCatalogUpdate,
  scheduleTaskPoll: service.scheduleTaskPoll,
  // Host's PluginFeatures slot calls this for each subscription row.
  renderFeatures: service.renderFeatures,
  // Plugin-rendered details column on subscription rows.
  renderDetailsKey: service.renderDetailsKey,
  // Host's mergeNav engine reads this to insert the catalog / currency /
  // terraform screens into the shared Administration menu (declarative
  // `{ menu, children }` contribution; see service.renderNav).
  renderNav: service.renderNav,
}

const routes = [
  { path: '/prov/currency', name: 'prov-currency', component: CurrencyListView },
  { path: '/prov/catalog', name: 'prov-catalog', component: CatalogListView },
  { path: '/prov/terraform', name: 'prov-terraform', component: TerraformView },
  { path: '/prov/network', name: 'prov-network', component: NetworkView },
  // Subscription-scoped quote editor. `:subscription` matches the legacy
  // `service:prov:<provider>:<instance>` route shape used by the host's
  // PluginView fallback. Kept as a stub for now.
  { path: '/prov/quote/:subscription', name: 'prov-quote', component: QuoteView },
]

export default {
  id: 'prov',
  label: 'Provisioning',
  component: ProvPlugin,
  routes,
  install({ router }) {
    for (const route of routes) {
      router.addRoute(route)
    }
    // Register plugin-local translations into the host i18n store.
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "prov" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-server-network', color: 'teal-darken-2' },
}

export { service }
