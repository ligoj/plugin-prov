# plugin-prov UI

Vue sources for the Ligoj "prov" (provisioning) plugin. Built with Vite in
library mode; the output bundle is placed under the Java module's webjars
classpath so the Ligoj host serves it at `/webjars/prov/vue/index.js`.

## Layout

```
ui/
├── package.json
├── vite.config.js            # library build → ../src/main/resources/.../webjars/prov/vue/
├── index.html                # standalone dev entry
└── src/
    ├── index.js              # plugin contract entry (default export)
    ├── ProvPlugin.vue        # root component
    ├── service.js            # service / feature implementations
    ├── i18n/
    │   ├── en.js
    │   └── fr.js
    └── views/
        ├── CurrencyListView.vue   # ported happy-path
        ├── CatalogListView.vue    # stub — port follows
        ├── TerraformView.vue      # stub — port follows
        ├── NetworkView.vue        # stub — port follows
        └── QuoteView.vue          # stub — port follows
```

## Commands

```sh
npm install
npm run dev        # standalone dev server on :5175; proxies REST to :8080
npm run build      # writes ../src/main/resources/META-INF/resources/webjars/prov/vue/index.js
npm run lint
```

`npm run dev` gives you the plugin in isolation — useful for UI work without
booting the full host app. The dev server proxies `/rest` and `/webjars` to
a locally running Ligoj backend on `:8080`.

## Shared dependencies

`vue`, `vue-router`, `pinia`, and `vuetify` are kept **external** in the
build output — the host resolves them via an import map so the plugin and
host share the same module instances. Without that, reactivity and
cross-component plugin registries break at SFC boundaries.

## Migration status

| Module | Legacy path | Vue port |
|---|---|---|
| Currency CRUD | `webjars/prov/currency/` | `views/CurrencyListView.vue` ✅ |
| Catalog list | `webjars/prov/catalog/` | `views/CatalogListView.vue` ⚠ stub |
| Terraform tab | `webjars/prov/terraform/` | `views/TerraformView.vue` ⚠ stub |
| Network editor | `webjars/home/project/network/` | `views/NetworkView.vue` ⚠ stub |
| Quote editor | `webjars/service/prov/` | `views/QuoteView.vue` ⚠ stub |

Legacy AMD bundles remain in place — the host's plugin loader prefers
`vue/index.js` and ignores the older assets at runtime.
