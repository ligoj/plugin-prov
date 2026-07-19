import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import { optimizerSummary, optimizerPayload, optimizerModeIcon, OPTIMIZER_MODES } from '../optimizerCatalog.js'
import OptimizerDialog from '../views/OptimizerDialog.vue'
import OptimizerField from '../views/OptimizerField.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }
enableAutoUnmount(afterEach)

describe('optimizerCatalog', () => {
  it('has the two modes with icons', () => {
    expect(OPTIMIZER_MODES.map((m) => m.value)).toEqual(['COST', 'CO2'])
    expect(optimizerModeIcon('CO2')).toBe('mdi-leaf')
    expect(optimizerModeIcon('COST')).toBe('mdi-currency-usd')
    expect(optimizerModeIcon('???')).toBe('mdi-currency-usd') // fallback
  })

  it('optimizerSummary shows the mode label and the P1 marker', () => {
    const t = (k) => (k === 'prov.quote.optimizer.mode.co2' ? 'Optimize CO₂' : 'Optimize cost')
    expect(optimizerSummary({ mode: 'CO2' }, t)).toBe('Optimize CO₂')
    expect(optimizerSummary({ mode: 'COST', p1TypeOnly: true }, t)).toBe('Optimize cost · P1')
    expect(optimizerSummary(null, t)).toBe('')
  })

  it('optimizerPayload defaults + coerces the flag', () => {
    expect(optimizerPayload({ id: 2, name: 'green', mode: 'CO2', p1TypeOnly: 1 }))
      .toEqual({ id: 2, name: 'green', mode: 'CO2', p1TypeOnly: true })
    expect(optimizerPayload({ name: 'x' })).toEqual({ id: undefined, name: 'x', mode: 'COST', p1TypeOnly: false })
  })
})

describe('<OptimizerDialog>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })
  const open = (optimizer) => mount(OptimizerDialog, { props: { modelValue: true, optimizer, subscriptionId: 7 }, ...withApp })

  it('loads the form from the optimizer on open', () => {
    const w = open({ id: 3, name: 'green', mode: 'CO2', p1TypeOnly: true })
    expect(w.vm.form).toMatchObject({ id: 3, name: 'green', mode: 'CO2', p1TypeOnly: true })
  })

  it('POSTs a new optimizer and emits saved + changed', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve({
      ok: true, status: 200, headers: { get: () => 'application/json' }, json: () => Promise.resolve(42),
    }))
    const w = open(null)
    w.vm.form.name = 'new'
    w.vm.form.mode = 'CO2'
    await w.vm.$nextTick()
    await w.vm.save()
    const [url, opts] = globalThis.fetch.mock.calls[0]
    expect(url).toContain('rest/service/prov/7/optimizer')
    expect(opts.method).toBe('POST')
    expect(JSON.parse(opts.body)).toMatchObject({ name: 'new', mode: 'CO2', p1TypeOnly: false })
    expect(w.emitted('saved').at(-1)).toEqual(['new'])
    expect(w.emitted('changed')).toBeTruthy()
  })

  it('DELETEs an existing optimizer', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve({
      ok: true, status: 200, headers: { get: () => 'application/json' }, json: () => Promise.resolve({}),
    }))
    const w = open({ id: 9, name: 'old', mode: 'COST' })
    await w.vm.remove()
    const [url, opts] = globalThis.fetch.mock.calls[0]
    expect(url).toContain('/optimizer/9')
    expect(opts.method).toBe('DELETE')
    expect(w.emitted('changed')).toBeTruthy()
  })
})

describe('<OptimizerField>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })
  const optimizers = [{ id: 1, name: 'green', mode: 'CO2' }]

  it('opens the editor for a new optimizer', async () => {
    const w = mount(OptimizerField, { props: { optimizers, subscriptionId: 7 }, ...withApp })
    await w.find('.mdi-plus').trigger('click')
    const dlg = w.findComponent(OptimizerDialog)
    expect(dlg.props('modelValue')).toBe(true)
    expect(dlg.props('optimizer')).toBe(null)
  })

  it('opens the editor for the selected optimizer', async () => {
    const w = mount(OptimizerField, { props: { optimizers, modelValue: 'green', subscriptionId: 7 }, ...withApp })
    await w.find('.mdi-pencil').trigger('click')
    expect(w.findComponent(OptimizerDialog).props('optimizer')).toMatchObject({ name: 'green' })
  })

  it('re-emits changed from the dialog', () => {
    const w = mount(OptimizerField, { props: { optimizers, subscriptionId: 7 }, ...withApp })
    w.findComponent(OptimizerDialog).vm.$emit('changed')
    expect(w.emitted('changed')).toBeTruthy()
  })
})
