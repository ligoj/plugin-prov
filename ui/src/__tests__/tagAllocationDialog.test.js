import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import TagAllocationDialog from '../views/TagAllocationDialog.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }
enableAutoUnmount(afterEach)

const config = {
  currency: { unit: '$', rate: 1 },
  instances: [{ id: 1, name: 'web', cost: 100, co2: 10 }, { id: 2, name: 'api', cost: 60, co2: 6 }],
  databases: [{ id: 3, name: 'db', cost: 40, co2: 4 }],
  tags: {
    INSTANCE: { 1: [{ name: 'team', value: 'alpha' }], 2: [{ name: 'team', value: 'beta' }] },
    // db (id 3) is untagged.
  },
}

describe('<TagAllocationDialog>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })

  const open = async (cfg = config, viewMode = 'cost') => {
    const w = mount(TagAllocationDialog, { props: { modelValue: false, config: cfg, currency: cfg.currency, viewMode }, ...withApp })
    await w.setProps({ modelValue: true })
    await flushPromises()
    return w
  }

  it('defaults to the first tag key and buckets cost by value + untagged', async () => {
    const w = await open()
    expect(w.vm.keys).toEqual(['team'])
    expect(w.vm.selectedKey).toBe('team')
    expect(w.vm.allocation.buckets.map((b) => [b.value, b.amount])).toEqual([['alpha', 100], ['beta', 60]])
    expect(w.vm.allocation.untagged.amount).toBe(40)      // the untagged db
    expect(w.vm.allocation.coverage).toBeCloseTo(160 / 200, 6)
    expect(w.vm.rows).toHaveLength(3)                      // alpha + beta + untagged
    expect(w.vm.rows.at(-1).untagged).toBe(true)
  })

  it('honours the initial view mode and can switch to CO₂', async () => {
    const w = await open(config, 'co2')
    expect(w.vm.metric).toBe('co2')
    expect(w.vm.allocation.total).toBe(20)
    w.vm.metric = 'cost'
    await flushPromises()
    expect(w.vm.allocation.total).toBe(200)
  })

  it('shows an empty state when the quote has no tags', async () => {
    const w = await open({ instances: [{ id: 1, name: 'web', cost: 100 }], tags: {} })
    expect(w.vm.keys).toEqual([])
    expect(document.body.textContent).toContain('No tags on this quote yet')
  })
})
