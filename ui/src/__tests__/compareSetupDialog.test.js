import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import CompareSetupDialog from '../views/CompareSetupDialog.vue'

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

// MS = sub 10 (aws). Already compared: sub 20 (azure). Addable: sub 30 (gcp).
const COMPARED = [
  { subscription: 20, name: 'Azure clone', cost: { min: 90, co2: 8000 }, errors: [{ id: 1, name: 'gpu-box', resourceType: 'INSTANCE' }] },
]
const SUBSCRIPTIONS = {
  subscriptions: [
    { id: 10, node: 'service:prov:aws:acc', project: 1 },   // current (self)
    { id: 20, node: 'service:prov:azure:acc', project: 1 }, // already compared
    { id: 30, node: 'service:prov:gcp:acc', project: 1 },   // addable
    { id: 40, node: 'service:id:ldap:acc', project: 1 },    // non-prov → excluded
    { id: 50, node: 'service:prov:gcp:acc2', project: 2 },  // other project → excluded
  ],
  nodes: [
    { id: 'service:prov:aws', name: 'AWS' }, { id: 'service:prov:aws:acc', name: 'AWS Acc' },
    { id: 'service:prov:azure', name: 'Azure' }, { id: 'service:prov:azure:acc', name: 'Azure Acc' },
    { id: 'service:prov:gcp', name: 'GCP' }, { id: 'service:prov:gcp:acc', name: 'GCP Acc' },
    { id: 'service:prov:gcp:acc2', name: 'GCP Acc 2' },
  ],
}

function mockFetch() {
  globalThis.fetch = vi.fn((url, opts) => {
    if (url.endsWith('/compare') && (!opts || !opts.method || opts.method === 'GET')) return jsonResp(COMPARED)
    if (url.endsWith('rest/subscription')) return jsonResp(SUBSCRIPTIONS)
    const cfg = url.match(/rest\/subscription\/(\d+)\/configuration/)
    if (cfg) return jsonResp({ configuration: { name: `quote-${cfg[1]}` } })
    if (opts?.method === 'POST' || opts?.method === 'DELETE') return jsonResp(null, 204)
    return jsonResp(null, 204)
  })
}

describe('<CompareSetupDialog>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en'); mockFetch() })

  const open = async () => {
    const w = mount(CompareSetupDialog, { props: { modelValue: false, subscriptionId: '10' }, ...withApp })
    await w.setProps({ modelValue: true })
    await flushPromises()
    return w
  }

  it('lists the compared subscriptions with their errors', async () => {
    const w = await open()
    expect(w.vm.compared).toHaveLength(1)
    expect(w.vm.compared[0]).toMatchObject({ subscription: 20, name: 'Azure clone' })
    expect(w.vm.compared[0].errors).toHaveLength(1)
  })

  it('offers only same-project prov subscriptions, excluding self/compared, with the config name', async () => {
    const w = await open()
    const ids = w.vm.addable.map((s) => s.id)
    // 10 = self, 20 = already compared, 40 = non-prov, 50 = other project → only 30 remains.
    expect(ids).toEqual([30])
    expect(w.vm.addable[0]).toMatchObject({ id: 30, configName: 'quote-30', provider: 'GCP' })
    expect(w.vm.addable[0].label).toBe('quote-30') // selection chip shows the quote name
  })

  it('POSTs to add the selected subscription (and guards when none is picked)', async () => {
    const w = await open()
    await w.vm.add() // nothing selected → no POST
    expect(globalThis.fetch.mock.calls.some(([, o]) => o?.method === 'POST')).toBe(false)

    w.vm.toAdd = 30
    await w.vm.add()
    await flushPromises()
    const post = globalThis.fetch.mock.calls.find(([u, o]) => o?.method === 'POST' && u.includes('/compare/30'))
    expect(post).toBeTruthy()
    expect(w.emitted('changed')).toBeTruthy()
  })

  it('removes a compared subscription via DELETE', async () => {
    const w = await open()
    await w.vm.remove(COMPARED[0])
    await flushPromises()
    const del = globalThis.fetch.mock.calls.find(([u, o]) => o?.method === 'DELETE' && u.includes('/compare/20'))
    expect(del).toBeTruthy()
    expect(w.emitted('changed')).toBeTruthy()
  })
})
