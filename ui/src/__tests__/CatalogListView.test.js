import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import { createRouter, createMemoryHistory } from 'vue-router'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import CatalogListView from '../views/CatalogListView.vue'
import enMessages from '../i18n/en.js'

const vuetify = createVuetify({ components, directives })
const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/', component: { template: '<div/>' } }] })

function mountView() {
  return mount(CatalogListView, {
    global: { plugins: [vuetify, i18nPlugin, router] },
    attachTo: document.body,
  })
}

function jsonResponse(body) {
  return Promise.resolve({
    ok: true,
    status: 200,
    headers: { get: (k) => (k === 'content-type' ? 'application/json' : null) },
    json: () => Promise.resolve(body),
  })
}

describe('<CatalogListView>', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // Plugin-local translations are merged into the host i18n at the
    // plugin's `install()` time. Tests bypass that path, so seed the
    // bundle directly.
    mergeMessages(enMessages, 'en')
  })
  afterEach(() => vi.restoreAllMocks())

  it('loads catalogs on mount and renders one row per provider', async () => {
    globalThis.fetch = vi.fn(() => jsonResponse([
      {
        node: { id: 'service:prov:aws', name: 'AWS' },
        nbQuotes: 4,
        status: { lastSuccess: 1700000000000, nbLocations: 24, nbTypes: 200, nbPrices: 10000, end: 1700000000000 },
      },
      {
        node: { id: 'service:prov:azure', name: 'Azure' },
        nbQuotes: 0,
        status: null,
      },
    ]))
    const wrapper = mountView()
    await flushPromises()
    expect(globalThis.fetch).toHaveBeenCalled()
    const [url] = globalThis.fetch.mock.calls[0]
    expect(url).toContain('rest/service/prov/catalog')
    expect(wrapper.text()).toContain('AWS')
    expect(wrapper.text()).toContain('Azure')
  })

  it('renders the empty-state alert when the API returns []', async () => {
    globalThis.fetch = vi.fn(() => jsonResponse([]))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toMatch(/No catalogs registered\.|Aucun catalogue/)
  })
})

describe('status tooltip lines (legacy toStatusText parity)', () => {
  const mountBare = () => {
    globalThis.fetch = vi.fn(() => jsonResponse([]))
    return mountView()
  }

  it('failure: when + started/by + last step with progress + last success + stats', async () => {
    const w = mountBare()
    await flushPromises()
    // The AWS sample from the API: failed run with a scoring phase.
    const aws = {
      canImport: true,
      status: {
        start: 1780769918498, failed: true, end: 1780771800621, author: 'ligoj-admin',
        lastSuccess: 1780765260276, nbLocations: 55, nbTypes: 1507, nbPrices: 1905486,
        nbCo2Prices: 1283742, location: 'eu-central-1', phase: 'ec2 (scoring 2/2)', done: 63, workload: 559,
      },
    }
    const lines = w.vm.statusLines(aws)
    expect(lines[0]).toContain('Failed')
    expect(lines[1]).toContain('ligoj-admin')
    expect(lines[2]).toContain('ec2 (scoring 2/2)@eu-central-1')
    expect(lines[2]).toContain('11% (63/559)') // round(63/559*100)
    expect(lines[3]).toContain('Last success')
    expect(w.vm.compact(1905486)).toBe('1.9M')
    expect(w.vm.compact(null)).toBe('—')
  })

  it('success: updated/by + duration; running: progress + step + first import', async () => {
    const w = mountBare()
    await flushPromises()
    const ok = { canImport: true, status: { start: 1784572674613, end: 1784584704275, failed: false, author: 'ligoj-admin' } }
    const okLines = w.vm.statusLines(ok)
    expect(okLines[0]).toContain('ligoj-admin')
    expect(okLines[1]).toContain('3h 20m') // 12029662 ms ≈ 200 min

    const running = { canImport: true, status: { start: 1, end: 0, author: 'me', phase: 'support', done: 25, workload: 44 } }
    const runLines = w.vm.statusLines(running)
    expect(runLines[0]).toBe('57% (25/44)')
    expect(runLines[1]).toContain('support')
    expect(runLines.at(-1)).toContain('First import')
  })
})
