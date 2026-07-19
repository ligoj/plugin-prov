import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import { budgetSummary, budgetPayload } from '../budgetCatalog.js'
import BudgetDialog from '../views/BudgetDialog.vue'
import BudgetField from '../views/BudgetField.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }
const USD = { unit: '$', rate: 1 }
enableAutoUnmount(afterEach)

describe('budgetCatalog', () => {
  it('budgetSummary shows the available cash, empty when none', () => {
    expect(budgetSummary({ initialCost: 500 }, USD)).toContain('500')
    expect(budgetSummary({ initialCost: 0 }, USD)).toBe('')
    expect(budgetSummary(null, USD)).toBe('')
  })

  it('budgetPayload coerces initialCost to a number', () => {
    expect(budgetPayload({ id: 2, name: 'b', initialCost: '500' })).toEqual({ id: 2, name: 'b', initialCost: 500 })
    expect(budgetPayload({ name: 'x' })).toEqual({ id: undefined, name: 'x', initialCost: 0 })
  })
})

describe('<BudgetDialog>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })
  const open = (budget) => mount(BudgetDialog, { props: { modelValue: true, budget, subscriptionId: 7, currency: USD }, ...withApp })

  it('loads the form from the budget on open', () => {
    const w = open({ id: 3, name: 'b', initialCost: 500 })
    expect(w.vm.form).toMatchObject({ id: 3, name: 'b', initialCost: 500 })
  })

  it('POSTs a new budget and emits saved + changed', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve({
      ok: true, status: 200, headers: { get: () => 'application/json' }, json: () => Promise.resolve(42),
    }))
    const w = open(null)
    w.vm.form.name = 'new'
    w.vm.form.initialCost = 1000
    await w.vm.$nextTick()
    await w.vm.save()
    const [url, opts] = globalThis.fetch.mock.calls[0]
    expect(url).toContain('rest/service/prov/7/budget')
    expect(opts.method).toBe('POST')
    expect(JSON.parse(opts.body)).toMatchObject({ name: 'new', initialCost: 1000 })
    expect(w.emitted('saved').at(-1)).toEqual(['new'])
    expect(w.emitted('changed')).toBeTruthy()
  })

  it('DELETEs an existing budget', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve({
      ok: true, status: 200, headers: { get: () => 'application/json' }, json: () => Promise.resolve({}),
    }))
    const w = open({ id: 9, name: 'old', initialCost: 100 })
    await w.vm.remove()
    const [url, opts] = globalThis.fetch.mock.calls[0]
    expect(url).toContain('/budget/9')
    expect(opts.method).toBe('DELETE')
    expect(w.emitted('changed')).toBeTruthy()
  })
})

describe('<BudgetField>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })
  const budgets = [{ id: 1, name: 'ceo', initialCost: 5000 }]

  it('opens the editor for a new budget', async () => {
    const w = mount(BudgetField, { props: { budgets, subscriptionId: 7, currency: USD }, ...withApp })
    await w.find('.mdi-plus').trigger('click')
    const dlg = w.findComponent(BudgetDialog)
    expect(dlg.props('modelValue')).toBe(true)
    expect(dlg.props('budget')).toBe(null)
  })

  it('opens the editor for the selected budget', async () => {
    const w = mount(BudgetField, { props: { budgets, modelValue: 'ceo', subscriptionId: 7, currency: USD }, ...withApp })
    await w.find('.mdi-pencil').trigger('click')
    expect(w.findComponent(BudgetDialog).props('budget')).toMatchObject({ name: 'ceo' })
  })

  it('re-emits changed from the dialog', () => {
    const w = mount(BudgetField, { props: { budgets, subscriptionId: 7, currency: USD }, ...withApp })
    w.findComponent(BudgetDialog).vm.$emit('changed')
    expect(w.emitted('changed')).toBeTruthy()
  })
})
