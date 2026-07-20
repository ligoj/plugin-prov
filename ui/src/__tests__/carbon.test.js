import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, enableAutoUnmount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import CarbonBar from '../views/CarbonBar.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }
enableAutoUnmount(afterEach)

// cpu reserved/available, ram reserved/available, monthly CO₂ grams.
const inst = (cpu, cpuA, ram, ramA, co2) => ({ cpu, ram, co2, minQuantity: 1, price: { type: { cpu: cpuA, ram: ramA } } })

describe('<CarbonBar>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })

  it('renders the carbon gauge + percentage below 100%', () => {
    const w = mount(CarbonBar, { props: { config: { instances: [inst(3, 4, 2048, 4096, 500)] } }, ...withApp })
    expect(w.find('.carb-bar').exists()).toBe(true)
    expect(w.find('.carb-bar-pct').text()).toBe('70%') // .8*.75 + .2*.5
    expect(w.find('.v-progress-linear').exists()).toBe(true)
  })

  it('renders nothing at a perfect 100% fit', () => {
    const w = mount(CarbonBar, { props: { config: { instances: [inst(4, 4, 4096, 4096, 500)] } }, ...withApp })
    expect(w.find('.carb-bar').exists()).toBe(false)
  })

  it('renders nothing when no emissions are reported', () => {
    // Below-100% fit but zero CO₂ → nothing to weight, gauge stays hidden.
    const w = mount(CarbonBar, { props: { config: { instances: [inst(3, 4, 2048, 4096, 0)] } }, ...withApp })
    expect(w.find('.carb-bar').exists()).toBe(false)
  })

  it('renders nothing for an empty quote', () => {
    const w = mount(CarbonBar, { props: { config: {} }, ...withApp })
    expect(w.find('.carb-bar').exists()).toBe(false)
  })

  it('shows explanation + per-type efficiency in the lazy tooltip', async () => {
    const w = mount(CarbonBar, {
      props: { config: { instances: [inst(3, 4, 2048, 4096, 500)] } },
      attachTo: document.body,
      ...withApp,
    })
    await w.find('.carb-bar').trigger('mouseenter')
    await new Promise((r) => setTimeout(r, 250))
    const tip = document.querySelector('.carb-tip')
    expect(tip).toBeTruthy()
    expect(tip.textContent).toContain('emissions') // explanation
    expect(tip.textContent).toContain('Instance')
  })
})
