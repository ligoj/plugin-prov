import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import { parseWorkload, serializeWorkload, hasWorkload, workloadBars } from '../quoteFormatters.js'
import WorkloadDialog from '../views/WorkloadDialog.vue'
import WorkloadIcon from '../views/WorkloadIcon.vue'
import CpuField from '../views/CpuField.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }

enableAutoUnmount(afterEach)

describe('workload parse / serialize', () => {
  it('parses baseline + duration@cpu periods', () => {
    expect(parseWorkload('80,20@55,10@23')).toEqual({
      baseline: 80,
      periods: [{ duration: 20, cpu: 55 }, { duration: 10, cpu: 23 }],
    })
    expect(parseWorkload('100')).toEqual({ baseline: 100, periods: [] })
    expect(parseWorkload('')).toEqual({ baseline: null, periods: [] })
    expect(parseWorkload(null)).toEqual({ baseline: null, periods: [] })
  })

  it('serializes back to the string form', () => {
    expect(serializeWorkload({ baseline: 80, periods: [{ duration: 20, cpu: 55 }] })).toBe('80,20@55')
    expect(serializeWorkload({ baseline: 5, periods: [] })).toBe('5')
    expect(serializeWorkload({ baseline: null })).toBe('')
    expect(serializeWorkload({})).toBe('')
    // Incomplete periods are dropped.
    expect(serializeWorkload({ baseline: 50, periods: [{ duration: 10 }] })).toBe('50')
  })

  it('round-trips', () => {
    for (const s of ['80,20@55,10@23', '100', '5']) {
      expect(serializeWorkload(parseWorkload(s))).toBe(s)
    }
  })

  it('hasWorkload reflects a configured baseline', () => {
    expect(hasWorkload('80,20@55')).toBe(true)
    expect(hasWorkload('100')).toBe(true)
    expect(hasWorkload('')).toBe(false)
    expect(hasWorkload('abc')).toBe(false)
  })
})

describe('<WorkloadDialog>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })

  const open = (workload) => mount(WorkloadDialog, { props: { modelValue: true, workload }, ...withApp })

  it('loads the baseline + periods from the string on open', () => {
    const w = open('80,20@55,10@23')
    expect(w.vm.baseline).toBe(80)
    expect(w.vm.periods).toEqual([{ duration: 20, cpu: 55 }, { duration: 10, cpu: 23 }])
  })

  it('adds and removes periods', () => {
    const w = open('80,20@55')
    expect(w.vm.periods).toHaveLength(1)
    w.vm.addPeriod()
    expect(w.vm.periods).toHaveLength(2)
    w.vm.removePeriod(0)
    expect(w.vm.periods).toHaveLength(1)
  })

  it('emits the serialized workload on save', () => {
    const w = open('80,20@55')
    w.vm.save()
    expect(w.emitted('save').at(-1)).toEqual(['80,20@55'])
    expect(w.emitted('update:modelValue').at(-1)).toEqual([false])
  })

  it('clears the workload', () => {
    const w = open('80,20@55')
    w.vm.clearWorkload()
    expect(w.emitted('save').at(-1)).toEqual([''])
  })

  it('tracks the duration total', () => {
    const w = open('80,60@50,55@40')
    expect(w.vm.durationTotal).toBe(115) // > 100 → warned in the UI
  })
})

describe('workloadBars', () => {
  it('fills every bar with the baseline when there are no periods', () => {
    expect(workloadBars('80', 5)).toEqual({ baseline: 80, bars: [80, 80, 80, 80, 80] })
  })

  it('samples the periods across the bars', () => {
    // two equal-duration periods (100@ then 0@) → first half high, second low
    expect(workloadBars('50,50@100,50@0', 4)).toEqual({ baseline: 50, bars: [100, 100, 0, 0] })
  })

  it('is empty when unconfigured', () => {
    expect(workloadBars('', 5)).toEqual({ baseline: null, bars: [] })
  })
})

describe('<WorkloadIcon>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })
  const withApp = { global: { plugins: [createVuetify({ components, directives }), i18nPlugin] } }

  it('shows the dot only when a workload is configured', () => {
    expect(mount(WorkloadIcon, { props: { workload: '80,20@55' }, ...withApp }).find('.wl-dot').exists()).toBe(true)
    expect(mount(WorkloadIcon, { props: { workload: '' }, ...withApp }).find('.wl-dot').exists()).toBe(false)
  })

  it('emits click to open the editor', async () => {
    const w = mount(WorkloadIcon, { props: { workload: '' }, ...withApp })
    await w.find('.v-icon').trigger('click')
    expect(w.emitted('click')).toBeTruthy()
  })

  it('tooltip shows the parts + a sparkline (baseline line + bars)', async () => {
    const w = mount(WorkloadIcon, {
      props: { workload: '80,20@55,10@23', explanation: 'the meaning' },
      attachTo: document.body, ...withApp,
    })
    await w.find('.wl-icon').trigger('mouseenter')
    await new Promise((r) => setTimeout(r, 250))
    const tip = document.querySelector('.wl-tip')
    expect(tip).toBeTruthy()
    expect(tip.textContent).toContain('the meaning')
    expect(tip.textContent).toContain('20%@55%')          // configured part
    expect(tip.querySelector('.wl-baseline')).toBeTruthy() // dashed baseline
    expect(tip.querySelectorAll('.wl-bar').length).toBe(24) // N bars
  })
})

describe('<CpuField>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })
  const withApp = { global: { plugins: [createVuetify({ components, directives }), i18nPlugin] } }

  it('renders the CPU input and the workload icon; wires their events', async () => {
    const w = mount(CpuField, { props: { modelValue: 2, workload: '80,20@55' }, ...withApp })
    expect(w.find('input').exists()).toBe(true)
    expect(w.find('.wl-icon').exists()).toBe(true)
    expect(w.find('.wl-dot').exists()).toBe(true) // workload configured
    await w.find('.wl-icon .v-icon').trigger('click')
    expect(w.emitted('edit-workload')).toBeTruthy()
    await w.find('input').setValue('3')
    expect(w.emitted('update:modelValue').at(-1)).toEqual([3])
  })
})
