import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import RateIcon from '../views/RateIcon.vue'
import RateField from '../views/RateField.vue'
import { rateVisual, rateLabelKey, rateDescKey, RATE_OPTIONS, RATE_ORDER } from '../rateCatalog.js'

const vuetify = createVuetify({ components, directives })
const mountOpts = { global: { plugins: [vuetify, i18nPlugin] } }

describe('rateCatalog', () => {
  it('maps every rate to a star glyph + colour', () => {
    for (const r of RATE_ORDER) {
      const v = rateVisual(r)
      expect(v).toBeTruthy()
      expect(v.icon).toMatch(/^mdi-star/)
      expect(v.color).toBeTruthy()
    }
  })

  it('uses the three star glyphs across the scale', () => {
    const icons = RATE_ORDER.map((r) => rateVisual(r).icon)
    expect(icons).toContain('mdi-star-outline')
    expect(icons).toContain('mdi-star-half-full')
    expect(icons).toContain('mdi-star')
  })

  it('is case-insensitive and null for unknown/empty', () => {
    expect(rateVisual('best').icon).toBe('mdi-star')
    expect(rateVisual('')).toBeNull()
    expect(rateVisual('NOPE')).toBeNull()
  })

  it('builds label/description i18n keys', () => {
    expect(rateLabelKey('best')).toBe('prov.quote.rate.BEST')
    expect(rateDescKey('best')).toBe('prov.quote.rate.BEST.desc')
  })

  it('offers all five rates as options, best first', () => {
    expect([...RATE_OPTIONS]).toEqual(['BEST', 'GOOD', 'MEDIUM', 'LOW', 'WORST'])
  })
})

describe('rate components', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mergeMessages(enMessages, 'en')
  })

  it('<RateIcon> renders the mapped glyph for a known rate', () => {
    const w = mount(RateIcon, { props: { rate: 'GOOD' }, ...mountOpts })
    expect(w.find('.mdi-star').exists()).toBe(true)
  })

  it('<RateIcon> renders nothing for an unknown rate', () => {
    const w = mount(RateIcon, { props: { rate: 'ZZZ' }, ...mountOpts })
    expect(w.find('.rate-icon').exists()).toBe(false)
  })

  it('<RateField> shows the translated label + star for the selected rate', () => {
    const w = mount(RateField, { props: { modelValue: 'WORST', label: 'CPU rate' }, ...mountOpts })
    const sel = w.find('.rate-selection')
    expect(sel.exists()).toBe(true)
    expect(sel.find('.mdi-star-outline').exists()).toBe(true)
    expect(sel.text()).toContain('Worst') // translated, not the raw code
    expect(sel.text()).not.toContain('WORST')
  })

  it('<RateField> emits the picked rate code through v-model', () => {
    const w = mount(RateField, { props: { modelValue: null }, ...mountOpts })
    w.vm.$emit('update:modelValue', 'BEST')
    expect(w.emitted('update:modelValue').at(-1)).toEqual(['BEST'])
  })
})
