import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import { usageSummary, usagePayload, USAGE_FLAGS } from '../usageCatalog.js'
import UsageDialog from '../views/UsageDialog.vue'
import UsageField from '../views/UsageField.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }
enableAutoUnmount(afterEach)

describe('usageCatalog', () => {
  it('usageSummary shows rate + commitment + start', () => {
    expect(usageSummary({ rate: 35, duration: 12 })).toBe('35% · 12mo')
    expect(usageSummary({ rate: 100, duration: 1 })).toBe('100%')
    expect(usageSummary({ rate: 50, duration: 36, start: 3 })).toBe('50% · 36mo · +3mo')
    expect(usageSummary(null)).toBe('')
  })

  it('usagePayload coerces numbers and flags to booleans', () => {
    const p = usagePayload({ id: 4, name: 'biz', rate: '29', duration: '12', start: '', convertibleType: true })
    expect(p).toMatchObject({
      id: 4, name: 'biz', rate: 29, duration: 12, start: 0,
      convertibleType: true, convertibleOs: false, reservation: false,
    })
  })
})

describe('<UsageDialog>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })
  const open = (usage) => mount(UsageDialog, { props: { modelValue: true, usage, subscriptionId: 7 }, ...withApp })

  it('loads the form from the usage on open', () => {
    const w = open({ id: 3, name: 'biz', rate: 35, duration: 12, start: 2, convertibleType: true })
    expect(w.vm.form).toMatchObject({ id: 3, name: 'biz', rate: 35, duration: 12, start: 2, convertibleType: true })
  })

  it('renders the doughnut and every flag', () => {
    open(null) // v-dialog content is teleported to <body>
    expect(document.querySelector('.usage-donut')).toBeTruthy()
    expect(document.querySelectorAll('.usage-flag').length).toBe(USAGE_FLAGS.length)
  })

  it('mirrors the rate as hours/days per month/year and commits back', async () => {
    const w = open({ name: 'x', rate: 50 })
    await w.vm.$nextTick()
    expect(w.vm.hoursMonth).toBe(365) // 50% of 730h
    expect(w.vm.daysYear).toBe(183) // 50% of 365d
    expect(w.vm.daysMonth).toBeCloseTo(15.2, 1)
    // Editing an equivalent sets the rate; the slider (and others) follow.
    w.vm.daysYear = 365
    w.vm.commitDaysYear()
    expect(w.vm.form.rate).toBe(100)
    await w.vm.$nextTick()
    expect(w.vm.hoursMonth).toBe(730)
  })

  it('draws the rate arc (empty at 0, full ring at 100, arc between)', () => {
    expect(open({ name: 'x', rate: 0 }).vm.ratePath).toBe('')
    expect(open({ name: 'x', rate: 100 }).vm.ratePath.length).toBeGreaterThan(0)
    expect(open({ name: 'x', rate: 40 }).vm.ratePath).toMatch(/^M /)
  })

  it('POSTs a new usage and emits saved + changed', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve({
      ok: true, status: 200, headers: { get: () => 'application/json' }, json: () => Promise.resolve(42),
    }))
    const w = open(null)
    w.vm.form.name = 'new'
    await w.vm.$nextTick()
    await w.vm.save()
    const [url, opts] = globalThis.fetch.mock.calls[0]
    expect(url).toContain('rest/service/prov/7/usage')
    expect(opts.method).toBe('POST')
    expect(JSON.parse(opts.body)).toMatchObject({ name: 'new', rate: 100, duration: 1 })
    expect(w.emitted('saved').at(-1)).toEqual(['new'])
    expect(w.emitted('changed')).toBeTruthy()
  })

  it('DELETEs an existing usage', async () => {
    globalThis.fetch = vi.fn(() => Promise.resolve({
      ok: true, status: 200, headers: { get: () => 'application/json' }, json: () => Promise.resolve({}),
    }))
    const w = open({ id: 9, name: 'old', rate: 50 })
    await w.vm.remove()
    const [url, opts] = globalThis.fetch.mock.calls[0]
    expect(url).toContain('/usage/9')
    expect(opts.method).toBe('DELETE')
    expect(w.emitted('changed')).toBeTruthy()
  })
})

describe('<UsageField>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })
  const usages = [{ id: 1, name: 'biz', rate: 35, duration: 12 }]

  it('opens the editor for a new usage', async () => {
    const w = mount(UsageField, { props: { usages, subscriptionId: 7 }, ...withApp })
    await w.find('.mdi-plus').trigger('click')
    const dlg = w.findComponent(UsageDialog)
    expect(dlg.props('modelValue')).toBe(true)
    expect(dlg.props('usage')).toBe(null)
  })

  it('opens the editor for the selected usage', async () => {
    const w = mount(UsageField, { props: { usages, modelValue: 'biz', subscriptionId: 7 }, ...withApp })
    expect(w.find('.mdi-pencil').exists()).toBe(true)
    await w.find('.mdi-pencil').trigger('click')
    const dlg = w.findComponent(UsageDialog)
    expect(dlg.props('modelValue')).toBe(true)
    expect(dlg.props('usage')).toMatchObject({ name: 'biz' })
  })

  it('re-emits changed from the dialog', () => {
    const w = mount(UsageField, { props: { usages, subscriptionId: 7 }, ...withApp })
    w.findComponent(UsageDialog).vm.$emit('changed')
    expect(w.emitted('changed')).toBeTruthy()
  })
})
