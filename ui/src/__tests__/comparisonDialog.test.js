import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import ComparisonDialog from '../views/ComparisonDialog.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }
enableAutoUnmount(afterEach)

const jsonResp = (data, status = 200) => Promise.resolve({
  ok: status >= 200 && status < 300,
  status,
  headers: { get: (h) => (h === 'content-type' ? 'application/json' : null) },
  json: () => Promise.resolve(data),
  text: () => Promise.resolve(JSON.stringify(data)),
  clone() { return this },
})

// AWS (sub 10, current) vs Azure (sub 20). Azure is cheaper on the instance,
// dearer on the database — so best-of-breed beats either single provider.
const SUBSCRIPTIONS = {
  subscriptions: [
    { id: 10, node: 'service:prov:aws:acc', project: 1 },
    { id: 20, node: 'service:prov:azure:acc', project: 1 },
    { id: 30, node: 'service:id:ldap:acc', project: 1 }, // non-prov, must be ignored
  ],
  nodes: [
    { id: 'service:prov:aws', name: 'AWS' },
    { id: 'service:prov:aws:acc', name: 'AWS Account', refined: 'service:prov:aws' },
    { id: 'service:prov:azure', name: 'Azure' },
    { id: 'service:prov:azure:acc', name: 'Azure Account', refined: 'service:prov:azure' },
  ],
}

const PRICES = {
  '10:instance': 100, '20:instance': 90,
  '10:database': 50, '20:database': 70,
}

function mockFetch() {
  globalThis.fetch = vi.fn((url) => {
    if (url.endsWith('rest/subscription')) return jsonResp(SUBSCRIPTIONS)
    const m = url.match(/prov\/(\d+)\/(\w+)-lookup/)
    if (m) {
      const cost = PRICES[`${m[1]}:${m[2]}`]
      if (cost == null) return jsonResp(null, 204)
      return jsonResp({ cost, co2: cost / 10, price: { type: { name: `${m[2]}-type` } } })
    }
    return jsonResp(null, 204)
  })
}

const config = {
  currency: { unit: '$', rate: 1 },
  instances: [{ id: 1, name: 'web', cpu: 2, ram: 4096, os: 'LINUX' }],
  databases: [{ id: 2, name: 'db', cpu: 1, ram: 2048, engine: 'MYSQL' }],
}

const AWS = 'service:prov:aws'
const AZURE = 'service:prov:azure'

describe('<ComparisonDialog>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en'); mockFetch() })

  const open = async () => {
    const w = mount(ComparisonDialog, { props: { modelValue: false, config, subscriptionId: '10' }, ...withApp })
    await w.setProps({ modelValue: true }) // opening triggers loadProviders
    await flushPromises()
    return w
  }

  it('loads the visible prov providers, collapsed per tool node, current first', async () => {
    const w = await open()
    const ids = w.vm.providers.map((p) => p.toolId)
    expect(ids).toEqual([AWS, AZURE])              // ldap subscription filtered out
    expect(w.vm.providers[0]).toMatchObject({ current: true, name: 'AWS' })
    expect(w.vm.providers.every((p) => p.selected)).toBe(true)
  })

  it('prices every resource against every provider and builds the matrix', async () => {
    const w = await open()
    await w.vm.run()
    await flushPromises()

    expect(w.vm.rows.map((r) => r.name)).toEqual(['web', 'db'])
    // 2 resources × 2 providers = 4 lookup calls (+1 for rest/subscription).
    const lookupCalls = globalThis.fetch.mock.calls.filter(([u]) => u.includes('-lookup'))
    expect(lookupCalls.length).toBe(4)
    // Neutral params only — no location/usage leaked into the query.
    expect(lookupCalls[0][0]).toMatch(/cpu=2&ram=4096&os=LINUX/)
    expect(lookupCalls.every(([u]) => !/[?&](location|usage|optimizer|budget)=/.test(u))).toBe(true)
  })

  it('summarises totals, per-row winners and best-of-breed', async () => {
    const w = await open()
    await w.vm.run()
    await flushPromises()

    const s = w.vm.summary
    expect(s.totals[AWS]).toEqual({ value: 150, missing: 0 })   // 100 + 50
    expect(s.totals[AZURE]).toEqual({ value: 160, missing: 0 }) // 90 + 70
    expect(s.bestPid).toBe(AWS)                                 // cheapest single provider
    expect(s.bestOfBreed).toEqual({ value: 140, missing: 0 })  // 90 (azure) + 50 (aws)
    const web = s.perRow.find((r) => r.name === 'web')
    const db = s.perRow.find((r) => r.name === 'db')
    expect(web.bestPid).toBe(AZURE)
    expect(db.bestPid).toBe(AWS)
  })
})
