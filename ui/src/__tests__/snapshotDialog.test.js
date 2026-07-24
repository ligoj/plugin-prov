import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import SnapshotDialog from '../views/SnapshotDialog.vue'

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

const SNAPSHOTS = [
  { id: 7, name: 'before', createdDate: '2026-07-01T10:00:00Z', createdBy: 'fdaugan', nbResources: 2, cost: 150, co2: 9000 },
]
// Snapshot document: web (100, m1/od) + gone (50). Current: web (80, m2/od) + new (30).
const DOCUMENT = {
  version: 1,
  resources: [
    { resourceType: 'instance', name: 'web', cost: 100, co2: 5, typeName: 'm1', term: 'od' },
    { resourceType: 'instance', name: 'gone', cost: 50, co2: 4, typeName: 'm1', term: 'od' },
  ],
}
const CONFIG = {
  currency: { unit: '$', rate: 1 },
  instances: [
    { name: 'web', cost: 80, co2: 5, price: { type: { name: 'm2' }, term: { name: 'od' } } },
    { name: 'new', cost: 30, co2: 1, price: { type: { name: 'm1' }, term: { name: 'od' } } },
  ],
}

function mockFetch({ restoreResult = [] } = {}) {
  globalThis.fetch = vi.fn((url, opts) => {
    const method = opts?.method || 'GET'
    if (url.endsWith('/snapshot') && method === 'GET') return jsonResp(SNAPSHOTS)
    if (url.endsWith('/snapshot') && method === 'POST') return jsonResp(99)
    if (url.endsWith('/snapshot/7') && method === 'GET') return jsonResp(DOCUMENT)
    if (url.endsWith('/snapshot/7/restore')) return jsonResp(restoreResult)
    if (method === 'DELETE') return jsonResp(null, 204)
    return jsonResp(null, 204)
  })
}

describe('<SnapshotDialog>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en'); mockFetch() })

  const open = async () => {
    const w = mount(SnapshotDialog, {
      props: { modelValue: false, subscriptionId: '1801', config: CONFIG, currency: CONFIG.currency },
      ...withApp,
    })
    await w.setProps({ modelValue: true })
    await flushPromises()
    return w
  }

  it('lists the snapshots on open', async () => {
    const w = await open()
    expect(w.vm.snapshots).toHaveLength(1)
    expect(w.vm.snapshots[0]).toMatchObject({ id: 7, name: 'before', nbResources: 2 })
  })

  it('creates a snapshot with the typed label and reloads', async () => {
    const w = await open()
    w.vm.label = 'v2'
    await w.vm.create()
    await flushPromises()
    const post = globalThis.fetch.mock.calls.find(([u, o]) => o?.method === 'POST' && u.endsWith('/snapshot'))
    expect(post).toBeTruthy()
    expect(JSON.parse(post[1].body)).toMatchObject({ name: 'v2' })
  })

  it('computes the diff snapshot → current (added / removed / changed)', async () => {
    const w = await open()
    await w.vm.toggleDiff(SNAPSHOTS[0])
    await flushPromises()
    const d = w.vm.diff
    expect(d).toBeTruthy()
    expect(d.added.map((x) => x.row.name)).toEqual(['new'])
    expect(d.removed.map((x) => x.row.name)).toEqual(['gone'])
    expect(d.changed.map((x) => x.a.name)).toEqual(['web'])
    expect(d.totals).toMatchObject({ from: 150, to: 110 })
  })

  it('restores after confirmation and emits restored', async () => {
    const w = await open()
    w.vm.askRestore(SNAPSHOTS[0])
    await w.vm.doRestore()
    await flushPromises()
    const post = globalThis.fetch.mock.calls.find(([u]) => u.endsWith('/snapshot/7/restore'))
    expect(post).toBeTruthy()
    expect(w.emitted('restored')).toBeTruthy()
  })
})
