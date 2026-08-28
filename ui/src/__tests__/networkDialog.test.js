import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import NetworkDialog from '../views/NetworkDialog.vue'
import enMessages from '../i18n/en.js'

const vuetify = createVuetify({ components, directives })

const CONFIG = {
  instances: [{ id: 1, name: 'web' }, { id: 2, name: 'app' }],
  databases: [{ id: 3, name: 'db' }],
  containers: [],
  functions: [],
  storages: [
    { id: 4, name: 'nfs', price: { type: { network: true } } },
    { id: 5, name: 'disk', price: { type: { network: false } } },
  ],
  networks: [
    // Inbound to web (from app)
    { name: 'http', source: 2, sourceType: 'INSTANCE', target: 1, targetType: 'INSTANCE', port: 80 },
    // Outbound from web (to db)
    { name: 'sql', source: 1, sourceType: 'INSTANCE', target: 3, targetType: 'DATABASE', port: 3306, rate: 10, throughput: 5 },
    // Unrelated to web
    { source: 2, sourceType: 'INSTANCE', target: 3, targetType: 'DATABASE', port: 3306 },
  ],
}

let wrapper = null
function mountDialog() {
  wrapper = mount(NetworkDialog, {
    props: { modelValue: true, config: CONFIG, subscriptionId: '12', resourceType: 'instance', resource: CONFIG.instances[0] },
    global: { plugins: [vuetify, i18nPlugin] },
    attachTo: document.body,
  })
  return wrapper
}

describe('<NetworkDialog>', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mergeMessages(enMessages, 'en')
    globalThis.fetch = vi.fn(() => Promise.resolve({ ok: true, status: 204, headers: { get: () => null }, text: () => Promise.resolve('') }))
  })
  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.restoreAllMocks()
  })

  it('splits the resource links into inbound and outbound rows and offers the network-capable peers', async () => {
    mountDialog()
    await flushPromises()
    expect(wrapper.vm.inbound).toEqual([{ name: 'http', peer: 'INSTANCE#2', port: 80, rate: null, throughput: null }])
    expect(wrapper.vm.outbound).toEqual([{ name: 'sql', peer: 'DATABASE#3', port: 3306, rate: 10, throughput: 5 }])
    // Peers: every network-capable resource but the edited one (no 'web', no non-network 'disk')
    expect(wrapper.vm.peers.map((p) => p.name)).toEqual(['app', 'db', 'nfs'])
    const text = document.body.textContent
    expect(text).toContain('Network: web')
    expect(text).toContain('Inbound')
    expect(text).toContain('Outbound')
  })

  it('saves every row as a NetworkVo on the per-resource endpoint and emits saved', async () => {
    mountDialog()
    await flushPromises()
    wrapper.vm.addRow('outbound')
    Object.assign(wrapper.vm.outbound[1], { peer: 'STORAGE#4', port: 2049 })
    await wrapper.vm.save()
    await flushPromises()

    const put = globalThis.fetch.mock.calls.find(([, o]) => o?.method === 'PUT')
    expect(put).toBeTruthy()
    expect(put[0]).toContain('rest/service/prov/12/network/INSTANCE/1')
    expect(JSON.parse(put[1].body)).toEqual([
      { inbound: true, name: 'http', port: 80, rate: null, throughput: null, peer: 2, peerType: 'INSTANCE' },
      { inbound: false, name: 'sql', port: 3306, rate: 10, throughput: 5, peer: 3, peerType: 'DATABASE' },
      { inbound: false, name: null, port: 2049, rate: null, throughput: null, peer: 4, peerType: 'STORAGE' },
    ])
    expect(wrapper.emitted('saved')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('refuses to save a row without peer or with an invalid port', async () => {
    mountDialog()
    await flushPromises()
    wrapper.vm.addRow('inbound') // empty row: no peer, no port
    await wrapper.vm.save()
    await flushPromises()
    expect(globalThis.fetch.mock.calls.some(([, o]) => o?.method === 'PUT')).toBe(false)
    expect(wrapper.emitted('saved')).toBeUndefined()
  })

  it('removes a row', async () => {
    mountDialog()
    await flushPromises()
    wrapper.vm.removeRow('inbound', 0)
    expect(wrapper.vm.inbound).toEqual([])
  })
})
