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
  tags: { INSTANCE: { 2: [{ name: 'app', value: 'shop' }] }, database: { 3: [{ name: 'env', value: 'prod' }] } },
  networks: [
    // Inbound to web (from app), continuous (rate 0)
    { name: 'http', source: 2, sourceType: 'INSTANCE', target: 1, targetType: 'INSTANCE', port: 80, rate: 0 },
    // Outbound from web (to db), hourly
    { name: 'sql', source: 1, sourceType: 'INSTANCE', target: 3, targetType: 'DATABASE', port: 3306, rate: 3600, throughput: 5 },
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
    expect(wrapper.vm.inbound).toEqual([{ name: 'http', peer: 'INSTANCE#2', port: 80, rate: 0, throughput: null }]) // rate 0 = continuous
    expect(wrapper.vm.outbound).toEqual([{ name: 'sql', peer: 'DATABASE#3', port: 3306, rate: 3600, throughput: 5 }])
    // Peers: every network-capable resource (no non-network 'disk'), the edited one marked, with their tags
    expect(wrapper.vm.peers.map((p) => p.name)).toEqual(['web', 'app', 'db', 'nfs'])
    expect(wrapper.vm.peers.map((p) => !!p.current)).toEqual([true, false, false, false])
    expect(wrapper.vm.peers.map((p) => p.tags)).toEqual([[], ['app:shop'], ['env:prod'], []])
    const text = document.body.textContent
    expect(text).toContain('Network: web')
    expect(text).toContain('Inbound')
    expect(text).toContain('Outbound')
  })

  it('renders the links of the active tab as rows with a peer autocomplete, and switches tabs', async () => {
    mountDialog()
    await flushPromises()
    // Outbound tab first (default): one row, its peer picker showing the selected resource
    const rowsOf = () => [...document.querySelectorAll(`.net-tab[data-kind="${wrapper.vm.tab}"] .net-row`)]
    expect(wrapper.vm.tab).toBe('outbound')
    const tabs = [...document.querySelectorAll('.v-tab')]
    expect(tabs).toHaveLength(2)
    expect(tabs[0].textContent).toContain('Outbound')
    expect(rowsOf()).toHaveLength(1)
    expect(rowsOf()[0].querySelector('.v-autocomplete')).not.toBeNull()
    expect(rowsOf()[0].textContent).toContain('db')
    // Frequency is a select (continuous / hourly / ...) and throughput carries its unit
    expect(rowsOf()[0].querySelector('.v-select.net-rate')).not.toBeNull()
    // Native browser autofill is disabled on EVERY input of the dialog:
    // unique unmatchable autocomplete token + name (LigojAutocomplete style)
    const inputs = [...document.querySelectorAll('.net-tab input')]
    expect(inputs.length).toBeGreaterThan(3)
    for (const input of inputs) {
      expect(input.getAttribute('autocomplete') || '(missing)').not.toMatch(/^(on|)$|\(missing\)/)
      expect(input.getAttribute('name')).toBeTruthy()
    }
    expect(rowsOf()[0].querySelector('.net-rate').textContent).toContain('Hourly')
    expect(rowsOf()[0].querySelector('.net-throughput').textContent).toContain('KB/s')

    // Inbound tab: the other row, peer 'app', continuous
    wrapper.vm.tab = 'inbound'
    await flushPromises()
    expect(rowsOf()).toHaveLength(1)
    expect(rowsOf()[0].textContent).toContain('app')
    expect(rowsOf()[0].querySelector('.net-rate').textContent).toContain('Continuous')
  })

  it('adds and removes rows from the tab buttons', async () => {
    mountDialog()
    await flushPromises()
    const rowsOf = () => [...document.querySelectorAll(`.net-tab[data-kind="${wrapper.vm.tab}"] .net-row`)]
    const addButton = [...document.querySelectorAll('button')].find((b) => b.textContent.includes('Add a link'))
    expect(addButton).toBeTruthy()
    addButton.click()
    await flushPromises()
    expect(rowsOf()).toHaveLength(2)
    expect(rowsOf()[1].querySelector('.v-autocomplete')).not.toBeNull()

    rowsOf()[0].querySelector('.net-remove').click()
    await flushPromises()
    expect(rowsOf()).toHaveLength(1)
    expect(wrapper.vm.outbound).toEqual([{ name: null, peer: null, port: null, rate: 0, throughput: null }])
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
    // Outbound rows first (tab order); the backend replaces every link of the resource anyway
    expect(JSON.parse(put[1].body)).toEqual([
      { inbound: false, name: 'sql', port: 3306, rate: 3600, throughput: 5, peer: 3, peerType: 'DATABASE' },
      { inbound: false, name: null, port: 2049, rate: null, throughput: null, peer: 4, peerType: 'STORAGE' },
      { inbound: true, name: 'http', port: 80, rate: null, throughput: null, peer: 2, peerType: 'INSTANCE' },
    ])
    expect(wrapper.emitted('saved')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
  })

  it('saves from the footer Save button click (real wiring, no vm call)', async () => {
    mountDialog()
    await flushPromises()
    const saveButton = [...document.querySelectorAll('.vmodal-foot button')].find((b) => b.textContent.includes('Save'))
    expect(saveButton).toBeTruthy()
    saveButton.click()
    await flushPromises()
    const put = globalThis.fetch.mock.calls.find(([, o]) => o?.method === 'PUT')
    expect(put).toBeTruthy()
    expect(put[0]).toContain('rest/service/prov/12/network/INSTANCE/1')
    expect(JSON.parse(put[1].body)).toHaveLength(2)
  })

  it('matches the resource links whatever the enum casing of the REST JSON (lowercase sourceType/targetType)', async () => {
    const config = {
      ...CONFIG,
      networks: [
        { name: 'http', source: 2, sourceType: 'instance', target: 1, targetType: 'instance', port: 80 },
        { name: 'sql', source: 1, sourceType: 'instance', target: 3, targetType: 'database', port: 3306 },
      ],
    }
    wrapper = mount(NetworkDialog, {
      props: { modelValue: true, config, subscriptionId: '12', resourceType: 'instance', resource: CONFIG.instances[0] },
      global: { plugins: [vuetify, i18nPlugin] },
      attachTo: document.body,
    })
    await flushPromises()
    expect(wrapper.vm.inbound.map((r) => r.peer)).toEqual(['INSTANCE#2'])
    expect(wrapper.vm.outbound.map((r) => r.peer)).toEqual(['DATABASE#3'])
  })

  it('keeps links without port savable (imports by name have none) — port is optional like in the backend', async () => {
    const config = { ...CONFIG, networks: [{ name: 'imported', source: 2, sourceType: 'INSTANCE', target: 1, targetType: 'INSTANCE', port: null }] }
    wrapper = mount(NetworkDialog, {
      props: { modelValue: true, config, subscriptionId: '12', resourceType: 'instance', resource: CONFIG.instances[0] },
      global: { plugins: [vuetify, i18nPlugin] },
      attachTo: document.body,
    })
    await flushPromises()
    await wrapper.vm.save()
    await flushPromises()
    const put = globalThis.fetch.mock.calls.find(([, o]) => o?.method === 'PUT')
    expect(put).toBeTruthy()
    expect(JSON.parse(put[1].body)).toEqual([
      { inbound: true, name: 'imported', port: null, rate: null, throughput: null, peer: 2, peerType: 'INSTANCE' },
    ])
  })

  it('refuses to save a row without peer or with an out-of-range port, pointing at the offending tab', async () => {
    mountDialog()
    await flushPromises()
    wrapper.vm.addRow('outbound') // no peer
    await wrapper.vm.save()
    await flushPromises()
    expect(globalThis.fetch.mock.calls.some(([, o]) => o?.method === 'PUT')).toBe(false)
    expect(wrapper.emitted('saved')).toBeUndefined()
    // The dialog switches to the tab holding the invalid row and flags it
    expect(wrapper.vm.tab).toBe('outbound')
    expect(wrapper.vm.invalid).toBe(true)
    expect(document.querySelector('.net-tab[data-kind="outbound"] .net-row.net-row-invalid')).not.toBeNull()

    // Out-of-range port on an otherwise valid row
    wrapper.vm.removeRow('outbound', 1)
    wrapper.vm.outbound[0].port = 70000
    await wrapper.vm.save()
    await flushPromises()
    expect(globalThis.fetch.mock.calls.some(([, o]) => o?.method === 'PUT')).toBe(false)
  })

  it('removes a row', async () => {
    mountDialog()
    await flushPromises()
    wrapper.vm.removeRow('inbound', 0)
    expect(wrapper.vm.inbound).toEqual([])
  })
})
