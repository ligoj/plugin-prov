import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount, flushPromises } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import FilterDialog from '../views/FilterDialog.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }
enableAutoUnmount(afterEach)

const CONFIG = {
  currency: { unit: '$', rate: 1 },
  usages: [{ id: 1, name: 'Dev', rate: 35 }],
  budgets: [{ id: 2, name: 'Dept1', initialCost: 1000 }],
  optimizers: [{ id: 3, name: 'CO2', mode: 'CO2' }],
  locations: [{ name: 'region-1', countryA2: 'US' }],
  instances: [{ id: 9, os: 'LINUX' }, { id: 10, price: { os: 'WINDOWS' } }],
  databases: [{ id: 11, engine: 'MYSQL' }],
  tags: { INSTANCE: { 9: [{ name: 'team', value: 'alpha' }, { name: 'env', value: 'prod' }] } },
}

describe('<FilterDialog>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })

  const open = async (filters = [], mode = 'AND') => {
    const w = mount(FilterDialog, { props: { modelValue: false, filters, mode, config: CONFIG }, ...withApp })
    await w.setProps({ modelValue: true })
    await flushPromises()
    return w
  }

  it('starts with one empty row and derives enum/tag items from the quote', async () => {
    const w = await open()
    expect(w.vm.localFilters).toHaveLength(1)
    expect(w.vm.localFilters[0].field).toBe('text')
    expect(w.vm.osItems).toEqual(['LINUX', 'WINDOWS'])
    expect(w.vm.engineItems).toEqual(['MYSQL'])
    expect(w.vm.tagKeyItems).toEqual(['env', 'team'])
    expect(w.vm.tagValueItems('team')).toEqual(['alpha'])
  })

  it('resets op/value when the field changes and applies the edited set', async () => {
    const w = await open()
    const f = w.vm.localFilters[0]
    f.value = 'web'
    w.vm.onFieldChange(f, 'cost')
    expect(f.value).toBe('')
    f.op = '<'
    f.value = '50'
    w.vm.localMode = 'OR'
    w.vm.apply()
    const emitted = w.emitted('update:filters').at(-1)[0]
    expect(emitted).toHaveLength(1)
    expect(emitted[0]).toMatchObject({ field: 'cost', op: '<', value: '50' })
    expect(w.emitted('update:mode').at(-1)).toEqual(['OR'])
    expect(w.emitted('update:modelValue').at(-1)).toEqual([false])
  })

  it('clear-all empties the applied set', async () => {
    const w = await open([{ id: 1, field: 'os', value: 'LINUX' }])
    expect(w.vm.localFilters).toHaveLength(1)
    w.vm.clearAll()
    expect(w.emitted('update:filters').at(-1)[0]).toEqual([])
  })
})
