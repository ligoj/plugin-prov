# plugin-prov — Vue migration notes

Companion to the host `app-ui/REWRITE_VUEJS.md`. This file records what's
ported, what's not, and how to keep going. Start here when you pick the
work back up.

The legacy AMD assets stay in
`src/main/resources/META-INF/resources/webjars/` and are still picked up
by the host plugin loader as a fallback. The new Vue bundle is the
preferred path (the host's plugin loader checks `vue/index.js` first).

---

## Status snapshot

| Area | Status | Notes |
|---|---|---|
| Quote editor (`/prov/quote/:subscription`) | **Done** | All 6 resource types, CRUD + duplicate + bulk delete + tag editing + advanced fields + import + exports + refresh-prices + cost ↔ CO₂ toggle + cost-period selector + per-tab search + column visibility + persisted preferences + breakdown donut with drill-down. |
| Catalog list (`/prov/catalog`) | **Done** | List, import (standard / full), cancel, 5-second polling for running updates. |
| Currency list (`/prov/currency`) | **Done** | List, create, edit, delete. |
| Subscription row contributions | **Done** | `renderFeatures` (quote shortcut button) + `renderDetailsKey` (resource chips on subscription rows). |
| Network editor | **Stub only** | Legacy has no standalone `/prov/network` page — it's a per-resource popup. Route exists, view is a "not migrated" placeholder. |
| Terraform | **Excluded** | Explicitly out of scope. Legacy popups stay. |
| Catalog-fed enum dropdowns | **Not started** | OS / engine / processor / architecture / support access channels are free-text or hardcoded enums in the Vue port; the legacy fetched them from the catalog endpoint per provider. |
| Workload SVG editor | **Not ported** | Legacy has a click-to-add `duration@cpu` visual editor; Vue has a free-text field with a regex validator. |

Bundle: ~170 KB / ~33 KB gzipped. Lint clean. Host-side tests covering
plugin-prov: ~50 (formatters, dialogs, view components, contract).

---

## Directory layout

```
plugin-prov/
├── pom.xml
├── src/                           # Maven module — unchanged
│   └── main/resources/META-INF/resources/webjars/
│       ├── prov/vue/              # Vite build target (gitignored)
│       ├── prov/                  # Legacy AMD (kept as fallback)
│       └── service/prov/          # Legacy quote view assets
├── ui/                            # Vue source
│   ├── package.json
│   ├── vite.config.js
│   ├── eslint.config.js
│   ├── index.html                 # Standalone dev entry
│   └── src/
│       ├── index.js               # Plugin contract — see §contract
│       ├── ProvPlugin.vue         # Root shell component
│       ├── service.js             # `feature()` action implementations
│       ├── quoteFormatters.js     # Pure helpers (cost / co2 / cpu / ram / storage, donut paths, scaleCost, rowMatches, maxOfField, TAB_TYPES)
│       ├── uploadFormData.js      # CSV multipart payload builder
│       ├── i18n/
│       │   ├── en.js
│       │   └── fr.js
│       └── views/
│           ├── CatalogListView.vue    # /prov/catalog
│           ├── CurrencyListView.vue   # /prov/currency
│           ├── NetworkView.vue        # stub
│           ├── TerraformView.vue      # stub
│           ├── QuoteView.vue          # /prov/quote/:subscription — the big one
│           ├── QuoteBreakdown.vue     # Header donut + drill-down
│           ├── ResourceMicroBar.vue   # CPU/RAM efficiency bar
│           ├── ComputeEditDialog.vue  # Instance / container / function / database
│           ├── StorageEditDialog.vue
│           ├── SupportEditDialog.vue
│           ├── InstanceImportDialog.vue  # CSV bulk import
│           └── QuoteTagsEditor.vue       # Inline tag editor with suggestions
└── REWRITE_VUEJS.md              # This file
```

---

## Plugin contract

`ui/src/index.js` follows the same shape as plugin-id / plugin-ui:
`{ id, label, component, routes, install, feature, service, meta }`. The
two plugin-prov specifics:

### Routes registered

```js
{ path: '/prov/currency',           component: CurrencyListView }
{ path: '/prov/catalog',            component: CatalogListView }
{ path: '/prov/terraform',          component: TerraformView }      // stub
{ path: '/prov/network',            component: NetworkView }        // stub
{ path: '/prov/quote/:subscription', component: QuoteView }
```

### `feature()` actions

| Action | Args | Returns | Use |
|---|---|---|---|
| `renderFeatures` | `subscription` | array of VNodes | Subscription row's actions cell — quote shortcut button |
| `renderDetailsKey` | `subscription` | VNode \| `null` | Subscription row's details cell — resource chip row from `subscription.data.quote` |
| `requestCatalogUpdate` | `nodeId, { force }` | `true` on success | Triggers `POST rest/service/prov/catalog/<id>?force=<bool>` |
| `scheduleTaskPoll` | `url, onPartial, onDone, intervalMs?` | interval handle | Polls a long-running task; clears itself on `{ end: true }` |

The same set is mirrored on `pluginProvDef.service` for direct ES
import (`import { service } from '@ligoj-plugin/prov/...'` once the
plugin is registered) — both surfaces back the same functions.

---

## Backend endpoints touched

Every URL the Vue code uses, with the call site for cross-reference:

| Endpoint | Method | Caller |
|---|---|---|
| `rest/subscription/:id/configuration` | GET | `QuoteView#loadConfig` — primary load. Response shape: `{ subscription, project, parameters, node, configuration: { name, cost, currency, instances, databases, …, locations, usages, budgets, optimizers, processors, architectures, tags } }` |
| `rest/service/prov/:sub` | PUT | `QuoteView#saveEdit` — quote-level update |
| `rest/service/prov/:sub/refresh` | PUT | `QuoteView#refreshPrices` — re-runs catalog price discovery |
| `rest/service/prov/:type` | POST / PUT | `ComputeEditDialog`, `StorageEditDialog`, `SupportEditDialog` — resource create / edit |
| `rest/service/prov/:type/:id` | DELETE | Per-row delete |
| `rest/service/prov/:sub/:type` | DELETE | "Delete all" per tab |
| `rest/service/prov/:sub/:type-lookup` | GET | Price lookup driving the auto-debounced suggestion in every dialog |
| `rest/service/prov/:sub/upload` | POST (multipart) | `InstanceImportDialog` |
| `rest/service/prov/:sub/tag` | POST | `QuoteTagsEditor#add` |
| `rest/service/prov/:sub/tag/:id` | DELETE | `QuoteTagsEditor#remove` |
| `rest/service/prov/catalog` | GET | `CatalogListView#reload` |
| `rest/service/prov/catalog/:id?force=:bool` | POST | `runImport` |
| `rest/service/prov/catalog/:id` | DELETE | `cancelImport` |
| `rest/service/prov/currency` | GET / POST / PUT / DELETE | `CurrencyListView` |
| `rest/service/prov/:sub/ligoj-prov-instances-inline-storage-:sub-:date.csv` | GET | Export menu (URL embeds the filename — backend uses the trailing segment as Content-Disposition) |
| `rest/service/prov/:sub/ligoj-prov-split-:sub-:date.csv` | GET | Export menu |
| `rest/subscription/:sub/configuration` | GET (re-used) | Export menu (JSON download via `download` attr) |

---

## Pure helpers (and their tests)

All pure logic lives in two files so a future maintainer can reason
about it independently of the views.

### `quoteFormatters.js`

- `formatCost(value, currency)` — locale-aware monthly cost.
- `formatCostRange({ min, max, unbound })` — `min – max` or single value, `+` marker on unbound.
- `formatCo2(grams)` — g below 1 kg, kg above.
- `formatCpu`, `formatRam`, `formatStorage`.
- `scaleCost(cost, period)` — multiplies by `COST_PERIOD_FACTORS[period]`. Ratios match the legacy: 730 h/mo, 30 d/mo, 12 mo/y.
- `donutPath(cx, cy, r, ri, start, end)` — SVG annulus slice.
- `donutFullPath(cx, cy, r, ri)` — full annulus (used when one type carries 100%).
- `rowMatches(row, query)` — search predicate for the quote tables.
- `maxOfField(rows, accessor)` — per-column max for the efficiency micro-bars.
- `TAB_TYPES` — the 6 resource types with their icon / list field / color.

Test file: `app-ui/.../__tests__/plugins/quoteFormatters.test.js` — ~50
specs covering every branch.

### `uploadFormData.js`

- `buildInstanceUploadFormData(form)` — multipart body for the CSV import. Extracted so the dialog has a unit-testable payload builder.

Test file: `app-ui/.../__tests__/components/InstanceImportDialog.test.js`.

---

## Persisted preferences

Keys stored in `localStorage` by the quote view. All whitelist-guarded
so stale or tampered values fall back to defaults.

| Key | Default | Drives |
|---|---|---|
| `ligoj-prov-quote-active-tab` | `'instance'` | Active resource tab |
| `ligoj-prov-quote-view-mode` | `'cost'` | Cost ↔ CO₂ toggle |
| `ligoj-prov-quote-cost-period` | `'month'` | Header cost period (hour / day / month / year) |
| `ligoj-prov-quote-items-per-page` | `15` | Page size (15 / 30 / 50 / 100 / -1) |
| `ligoj-prov-quote-hidden-cols` | `{}` | Per-tab map of hidden columns (`{ instance: ['cpu','ram'], … }`) |

---

## Open work

Items left intentionally on the table, with enough context to attack them.

### 1. Catalog-fed enum dropdowns

The legacy fetches OS / engine / processor / architecture / support
access channels per provider catalog. Vue uses hardcoded enums in
`ComputeEditDialog.vue` (`OS_OPTIONS`, `ENGINE_OPTIONS`,
`RATE_OPTIONS`) and `SupportEditDialog.vue` (`ACCESS_OPTIONS`).

To plug catalog-fed lists in:
- The configuration response already includes `processors[type]` and `architectures[type]` (used to live in `ComputeEditDialog` before iteration where v-combobox was swapped out for free-text due to a Vuetify-4 recursion bug — see §gotchas).
- For OS / engine: there's no dedicated endpoint; the legacy reads them from `<type>-lookup` response metadata. A pragmatic move is to call `<type>-lookup` with empty constraints once on dialog open and harvest the unique OS/engine values from the returned price list.
- For support access channels: the legacy reads enum labels from `current.$messages['service:prov:support-access-' + id]` — the values are a fixed enum (`NONE / CHAT / TECHNICAL / BILLING / ALL`), already hardcoded in the Vue port.

### 2. Network detail editor

The legacy `webjars/home/project/network/` is opened from a row in the
subscription detail view; it edits the inbound/outbound links of one
resource. The Vue route exists but renders a "not migrated" stub.

To port: add an action per compute row that opens an inline modal
listing the rows from `config.networks.filter(l => l.source === id || l.target === id)`. PUT to
`rest/service/prov/<sub>/network/<type>/<id>` with the io array. The legacy
form takes name / peer (combo of compute resources) / port / rate /
throughput per link.

### 3. Workload visual editor

`ComputeEditDialog` has a free-text `workload` field with a regex
validator. The legacy has an SVG click-to-add `duration@cpu` segment
editor. Port path: replace the v-text-field with a small SFC that
parses the comma-separated string into `{ duration, cpu }[]`, renders
a sparkline-like SVG, and lets the user drag points. Out of scope for
day-to-day usage — most users type the format directly.

### 4. Per-tool processor / architecture / engine completions

The Vue dialogs use static enums and free text. If the user types a
processor name that doesn't exist in the catalog, the lookup will
return "no match" and the user can't recover. Two paths:

- Switch back to `v-combobox` with items from `config.processors[type]`. **Carefully** — Vuetify 4's v-combobox with a computed items array + `clearable` triggers `Maximum recursive updates` inside an expansion panel (see §gotchas). Either keep the panel `eager` (already done) or pre-validate the items array via `shallowRef`.
- Or render a small "suggestions" panel below the field — clicking a chip fills the field.

### 5. Storage attached-resource live filter

`StorageEditDialog`'s attached-resource dropdown flattens all compute
resources from `config.{instances,databases,containers,functions}`.
When a resource is selected, the storage's location is forced
(disabled) to match the parent's location. The legacy also restricts
the storage type to those that support the parent's resource type
(`s.price.type.network` etc.). Not ported — could be useful for
catalogs with strict storage compatibility.

---

## Dev workflow

```bash
cd plugin-prov/ui
npm install                # Once
npm run dev                # Standalone preview on :5175 — rarely useful
npm run build              # Emits to ../src/main/resources/.../webjars/prov/vue/
npm run lint
```

Real integration loop:

1. `npm run dev` in `app-ui/src/main/webapp` (the host) — on `:5173`.
2. Edit a plugin-prov view.
3. `npm run build --prefix ~/git/ligoj-plugins/plugin-prov/ui`.
4. Browser auto-reloads (Vite watches the proxied URL).

Run host tests against the plugin source:

```bash
cd app-ui/src/main/webapp
npx vitest run
```

The tests import plugin-prov source files via relative paths; no plugin
build is required.

---

## Gotchas (plugin-prov specific)

Everything in the host's `REWRITE_VUEJS.md` "Decisions and gotchas"
applies. The plugin-prov-specific ones:

### Configuration response is wrapped

`rest/subscription/:id/configuration` returns
`{ subscription, project, parameters, node, configuration: { … } }`.
The **quote** lives under `data.configuration`, not at the root. The
view's loader unwraps it; the per-resource arrays
(`instances`, `databases`, `containers`, `functions`, `storages`,
`supports`) all live on `configuration`.

### Subscription rows don't carry `data`

`rest/project/:id` returns subscriptions WITHOUT `data` / `status` /
fresh `parameters`. The legacy fetches them via
`rest/subscription/status/refresh?id=…&id=…` (see `webjars/home/home.js`).
`ProjectDetailView` mirrors that in `refreshSubscriptions()`. Without
this step `renderDetailsKey` returns null because `data.quote` is
undefined.

### Tag endpoint mutates per-change

`QuoteTagsEditor` doesn't bundle tags into the resource save payload.
Each chip add/remove fires its own `POST` / `DELETE` against
`rest/service/prov/<sub>/tag` / `…/tag/<id>`. The parent dialog
listens for `@tags-changed` and reloads the configuration so the
table row chips re-render.

### v-combobox + computed items + clearable + expansion panel = infinite loop

Vuetify 4 reliably triggers `Maximum recursive updates` when a
`v-combobox` with a computed `:items` array and `clearable` is mounted
inside an animating `v-expansion-panel-text`. We swapped to
`v-text-field` for processor / architecture (free-text, no
suggestions). Workarounds if you need the dropdown back:

- Add `eager` to the expansion panel so content mounts on open, not during the expand transition (already done for every advanced panel in plugin-prov).
- Move items to a `shallowRef` updated by `watchEffect` instead of a `computed`.
- Initialise the panel model with `null` (not `undefined`).

### `:rules="[required]"` array literals trigger validation loops

Vuetify 4's `v-form` watches `rules` by reference; an inline array is
a fresh reference per render. Mounting an input inside an expansion
panel transition then re-validates every frame ("Maximum recursive
updates exceeded in `<VForm>`"). Always hoist:

```js
const REQUIRED_RULES = [required]
const REQUIRED_POSITIVE_RULES = [required, positive]
```

Done across every dialog in plugin-prov.

### vue-i18n's `@` escape

Any literal `@` in a translation string must be wrapped in `{'@'}` —
vue-i18n parses `@` as the start of a linked-message reference and
throws `Invalid linked format` at message-compilation time. Caught
us once on `prov.quote.compute.workloadHint` (the `duration@cpu`
hint). When adding any string containing `@`, use the JS string
literal form `"... {'@'} ..."` and a comment explaining why.

### Storage / support save sends `type.code`, not `price.id`

Compute resources (`POST rest/service/prov/instance`, etc.) carry
`price` as the matched id. Storage and support carry **type** as the
catalog code:

```js
// storage / support payload
type: suggest.value.price.type?.code || suggest.value.price.type?.name
```

That's intentional — the legacy `storageUiToData` and
`supportUiToData` do the same. Don't "fix" it by sending `price.id`.

### RAM travels in MB

The compute dialog shows RAM in **GB** for readability; the wire
expects **MB**. Conversion happens in the dialog (`form.ramGb * 1024`).
Don't forget to convert when reading back from the API response
(`it.ram / 1024`).

### Cost period scaling is purely client-side

The backend stores monthly numbers. The header period selector
(`hour / day / month / year`) only re-scales the header total via
`scaleCost(cost, period)`. Per-row table costs stay monthly — a
follow-up could plumb the period through if asked, but it's a UX
choice the user has to opt into per cell.

---

## Migration checklist (per remaining item)

When you tackle one of the open items above, follow the same shape as
the existing iterations:

- [ ] Read the legacy implementation in `src/main/resources/META-INF/resources/webjars/...` — find the endpoints, the data shape, and the user flow.
- [ ] Mirror the field names (the backend's CSV parsers and JSON shapes are case-sensitive).
- [ ] Add the view / dialog under `ui/src/views/`.
- [ ] Pull cross-view logic into a pure helper under `ui/src/` for testability.
- [ ] Add i18n keys in **both** `en.js` and `fr.js`. Comment any non-obvious string.
- [ ] Add `REQUIRED_RULES`-style stable refs for any `:rules` you pass to Vuetify inputs.
- [ ] Add at least one unit test (pure helper) and one component test (render or behaviour).
- [ ] `npm run lint` and `npm run build` in `plugin-prov/ui`.
- [ ] `npx vitest run` in `app-ui/src/main/webapp` — keep the host suite green.
- [ ] Smoke-test in the host dev server: navigate, change locale, refresh the page.
