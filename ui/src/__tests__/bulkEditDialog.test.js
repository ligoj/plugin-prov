import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import BulkEditDialog from '../views/BulkEditDialog.vue'

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

const CONFIG = {
  usages: [{ id: 1, name: 'Dev', rate: 35, duration: 12 }, { id: 2, name: 'Full Time', rate: 100 }],
  budgets: [{ id: 3, name: 'Dept1', initialCost: 1000 }],
  optimizers: [{ id: 4, name: 'Cost', mode: 'COST' }, { id: 5, name: 'CO2', mode: 'CO2' }],
  locations: [{ name: 'region-1', countryA2: 'US' }, { name: 'region-2', countryA2: 'FR' }],
}

describe('<BulkEditDialog>', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mergeMessages(enMessages, 'en')
    globalThis.fetch = vi.fn(() => jsonResp({ min: 100, max: 120 }))
  })

  const open = async () => {
    const w = mount(BulkEditDialog, {
      props: { modelValue: false, type: 'instance', ids: [11, 12], config: CONFIG, subscriptionId: '1801' },
      ...withApp,
    })
    await w.setProps({ modelValue: true })
    await flushPromises()
    return w
  }

  it('is not applicable until a field moves off "keep"', async () => {
    const w = await open()
    expect(w.vm.dirty).toBe(false)
    w.vm.usage = 'Dev'
    expect(w.vm.dirty).toBe(true)
  })

  it('PUTs only the touched fields with the tri-state convention', async () => {
    const w = await open()
    w.vm.usage = 'Dev'          // set
    w.vm.budget = '__clear__'   // clear
    // optimizer / location stay on keep → absent from the payload.
    await w.vm.apply()
    await flushPromises()

    const put = globalThis.fetch.mock.calls.find(([u, o]) => o?.method === 'PUT' && u.includes('/1801/bulk/instance'))
    expect(put).toBeTruthy()
    const body = JSON.parse(put[1].body)
    expect(body.ids).toEqual([11, 12])
    expect(body.usage).toBe('Dev')
    expect(body.budget).toBe('')
    expect('optimizer' in body).toBe(false)
    expect('location' in body).toBe(false)
    expect(w.emitted('saved')).toBeTruthy()
  })

  it('renders the shared item presentations (usage rate, location flag)', async () => {
    const w = await open()
    // Select a usage + location and check the selection slots reuse the shared
    // renderings (v-dialog teleports to <body>, so assert there).
    w.vm.usage = 'Dev'
    w.vm.location = 'region-1'
    await flushPromises()
    expect(document.body.textContent).toContain('35%')     // usageSummary: "35% · 12mo"
    expect(document.body.innerHTML).toContain('loc-flag')  // LocationLabel flag rendering
  })

  it('resets the tri-states each time it opens', async () => {
    const w = await open()
    w.vm.usage = 'Dev'
    await w.setProps({ modelValue: false })
    await w.setProps({ modelValue: true })
    expect(w.vm.usage).toBe('__keep__')
    expect(w.vm.dirty).toBe(false)
  })
})
