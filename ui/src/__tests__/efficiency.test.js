import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import { computeEfficiency } from '../quoteFormatters.js'
import EfficiencyBar from '../views/EfficiencyBar.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }

// cpu reserved/available, ram reserved/available, monthly cost.
const inst = (cpu, cpuA, ram, ramA, cost) => ({ cpu, ram, cost, minQuantity: 1, price: { type: { cpu: cpuA, ram: ramA } } })
const store = (size, minimal, cost) => ({ size, cost, price: { type: { minimal } } })

describe('computeEfficiency()', () => {
  it('returns 100% for an empty quote', () => {
    expect(computeEfficiency(null)).toEqual({ overall: 1, byType: [], costNoSupport: 0 })
    expect(computeEfficiency({}).overall).toBe(1)
  })

  it('weights CPU 0.8 and RAM 0.2 per compute type', () => {
    // cpu 3/4 = .75, ram 2048/4096 = .5 → 0.8*.75 + 0.2*.5 = 0.7
    const r = computeEfficiency({ instances: [inst(3, 4, 2048, 4096, 10)] })
    expect(r.overall).toBeCloseTo(0.7, 5)
    expect(r.byType).toEqual([{ key: 'instance', efficiency: expect.closeTo(0.7, 5), cost: 10 }])
  })

  it('is 100% for a perfect fit', () => {
    const r = computeEfficiency({ instances: [inst(4, 4, 4096, 4096, 10)] })
    expect(r.overall).toBe(1)
  })

  it('folds storage utilisation (size vs minimal) into the cost-weighted average', () => {
    // instance contributes 7 (of 10); storage 30/50 = .6 → 3 (of 5). (7+3)/15
    const r = computeEfficiency({
      instances: [inst(3, 4, 2048, 4096, 10)],
      storages: [store(30, 50, 5)],
    })
    expect(r.overall).toBeCloseTo(10 / 15, 5)
    expect(r.byType.find((b) => b.key === 'storage').efficiency).toBeCloseTo(0.6, 5)
  })

  it('weights by emissions instead of cost with { weight: "co2" }', () => {
    // A (instance): eff .25, co2 90.  B (database): eff 1, co2 10.
    // co2-weighted:  (90*.25 + 10*1) / 100 = 0.325
    // cost-weighted: (10*.25 + 10*1) / 20  = 0.625  (same rows, different metric)
    const A = { cpu: 1, ram: 1024, co2: 90, cost: 10, minQuantity: 1, price: { type: { cpu: 4, ram: 4096 } } }
    const B = { cpu: 4, ram: 4096, co2: 10, cost: 10, minQuantity: 1, price: { type: { cpu: 4, ram: 4096 } } }
    const cfg = { instances: [A], databases: [B] }
    expect(computeEfficiency(cfg, { weight: 'co2' }).overall).toBeCloseTo(0.325, 5)
    expect(computeEfficiency(cfg).overall).toBeCloseTo(0.625, 5)
    expect(computeEfficiency(cfg, { weight: 'co2' }).costNoSupport).toBe(100) // total CO₂
  })

  it('is 100% when no emissions are reported (nothing to weight)', () => {
    const r = computeEfficiency({ instances: [inst(1, 4, 1024, 4096, 10)] }, { weight: 'co2' })
    expect(r.overall).toBe(1)
    expect(r.costNoSupport).toBe(0)
  })
})

describe('<EfficiencyBar>', () => {
  enableAutoUnmount(afterEach)
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })

  it('renders the bar + percentage below 100%', () => {
    const w = mount(EfficiencyBar, { props: { config: { instances: [inst(3, 4, 2048, 4096, 10)] } }, ...withApp })
    expect(w.find('.eff-bar').exists()).toBe(true)
    expect(w.find('.eff-bar-pct').text()).toBe('70%')
    expect(w.find('.v-progress-linear').exists()).toBe(true)
  })

  it('renders nothing at a perfect 100% fit', () => {
    const w = mount(EfficiencyBar, { props: { config: { instances: [inst(4, 4, 4096, 4096, 10)] } }, ...withApp })
    expect(w.find('.eff-bar').exists()).toBe(false)
  })

  it('renders nothing for an empty quote', () => {
    const w = mount(EfficiencyBar, { props: { config: {} }, ...withApp })
    expect(w.find('.eff-bar').exists()).toBe(false)
  })

  it('shows explanation + per-type efficiency in the lazy tooltip', async () => {
    const w = mount(EfficiencyBar, {
      props: { config: { instances: [inst(3, 4, 2048, 4096, 10)], storages: [store(30, 50, 5)] } },
      attachTo: document.body,
      ...withApp,
    })
    await w.find('.eff-bar').trigger('mouseenter')
    await new Promise((r) => setTimeout(r, 250))
    const tip = document.querySelector('.eff-tip')
    expect(tip).toBeTruthy()
    expect(tip.textContent).toContain('capacity') // explanation
    expect(tip.textContent).toContain('Instance')
    expect(tip.textContent).toContain('Storage')
    expect(tip.textContent).toContain('60%')      // storage efficiency
  })
})
