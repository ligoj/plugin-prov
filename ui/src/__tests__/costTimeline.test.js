import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import { costTimeline, resourceMonthRange, rowInMonth, MONTH_HORIZON } from '../quoteFormatters.js'
import CostTimeline from '../views/CostTimeline.vue'

const vuetify = createVuetify({ components, directives })

// Minimal resource builders.
const res = (cost, maxCost, usage) => ({ cost, maxCost, usage })

describe('resourceMonthRange() / rowInMonth()', () => {
  it('spans the whole horizon without a usage', () => {
    expect(resourceMonthRange({}, {})).toEqual({ start: 0, end: MONTH_HORIZON })
  })

  it('treats a duration of 0/1 as unbounded (fills the horizon)', () => {
    expect(resourceMonthRange({ usage: { start: 0, duration: 1 } }, {})).toEqual({ start: 0, end: 36 })
  })

  it('windows an explicit start + duration', () => {
    expect(resourceMonthRange({ usage: { start: 3, duration: 6 } }, {})).toEqual({ start: 3, end: 9 })
  })

  it('clamps a past (negative) start to now', () => {
    expect(resourceMonthRange({ usage: { start: -4, duration: 6 } }, {}).start).toBe(0)
  })

  it('falls back to the quote default usage', () => {
    expect(resourceMonthRange({}, { usage: { start: 2 } })).toEqual({ start: 2, end: 36 })
  })

  it('rowInMonth respects the [start, end) window', () => {
    const r = { usage: { start: 3, duration: 6 } }
    expect(rowInMonth(r, {}, 2)).toBe(false)
    expect(rowInMonth(r, {}, 3)).toBe(true)
    expect(rowInMonth(r, {}, 8)).toBe(true)
    expect(rowInMonth(r, {}, 9)).toBe(false)
  })
})

describe('costTimeline()', () => {
  it('returns empty for null / cost-free config', () => {
    expect(costTimeline(null)).toEqual({ horizon: 0, series: [], totals: [], max: 0 })
    expect(costTimeline({ instances: [{ cost: 0 }] }).horizon).toBe(0)
  })

  it('projects an on-demand resource flat across the full 36-month horizon', () => {
    const r = costTimeline({ instances: [res(10, 10)] })
    expect(r.horizon).toBe(36)
    expect(r.series).toHaveLength(1)
    expect(r.series[0].values).toHaveLength(36)
    expect(r.series[0].values.every((v) => v.min === 10 && v.max === 10)).toBe(true)
    expect(r.max).toBe(10)
  })

  it('tracks a min→max range per month', () => {
    const r = costTimeline({ instances: [res(10, 15, { start: 0, duration: 6 })] })
    expect(r.series[0].values[0]).toEqual({ min: 10, max: 15 })
    expect(r.series[0].values[5]).toEqual({ min: 10, max: 15 })
    expect(r.series[0].values[6]).toEqual({ min: 0, max: 0 })
    expect(r.max).toBe(15)
  })

  it('ramps in at usage start and drops off at the end of the window', () => {
    const r = costTimeline({
      instances: [res(10, 10, { start: 0, duration: 6 })],
      databases: [res(20, 20, { start: 3, duration: 6 })],
    })
    const totalMax = r.totals.map((t) => t.max)
    expect(totalMax.slice(0, 3)).toEqual([10, 10, 10])
    expect(totalMax.slice(3, 6)).toEqual([30, 30, 30])
    expect(totalMax.slice(6, 9)).toEqual([20, 20, 20])
    expect(totalMax.slice(9)).toEqual(new Array(27).fill(0))
    expect(r.max).toBe(30)
    expect(r.series.map((s) => s.key)).toEqual(['instance', 'database'])
  })

  it('projects CO₂ min/max when asked', () => {
    const r = costTimeline({ instances: [{ co2: 7, maxCo2: 9, usage: { start: 0, duration: 2 } }] }, { field: 'co2' })
    expect(r.series[0].values[0]).toEqual({ min: 7, max: 9 })
    expect(r.series[0].values[2]).toEqual({ min: 0, max: 0 })
  })
})

describe('<CostTimeline>', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mergeMessages(enMessages, 'en')
  })

  const mountChart = (config, props = {}) =>
    mount(CostTimeline, {
      props: { config, mode: 'cost', ...props },
      global: { plugins: [vuetify, i18nPlugin] },
    })

  it('renders nothing when there is no cost', () => {
    expect(mountChart({ instances: [{ cost: 0 }] }).find('.cost-timeline').exists()).toBe(false)
  })

  it('renders a solid min segment plus a faded gap segment per active month', () => {
    const w = mountChart({ instances: [res(10, 20, { start: 0, duration: 2 })] })
    expect(w.find('.cost-timeline').exists()).toBe(true)
    expect(w.findAll('rect.ct-min')).toHaveLength(2) // 2 active months
    expect(w.findAll('rect.ct-gap')).toHaveLength(2) // gap because max > min
  })

  it('omits the gap segment when min equals max', () => {
    const w = mountChart({ instances: [res(10, 10, { start: 0, duration: 2 })] })
    expect(w.findAll('rect.ct-min')).toHaveLength(2)
    expect(w.findAll('rect.ct-gap')).toHaveLength(0)
  })

  it('emits month-click with the clicked month index', async () => {
    const w = mountChart({ instances: [res(10, 10, { start: 0, duration: 3 })] })
    await w.findAll('.ct-col')[2].trigger('click')
    expect(w.emitted('month-click').at(-1)).toEqual([2])
  })

  it('highlights the selected month', () => {
    const w = mountChart({ instances: [res(10, 10, { start: 0, duration: 3 })] }, { selectedMonth: 1 })
    expect(w.find('rect.ct-selected').exists()).toBe(true)
  })

  it('forces the selected month number as a badge even off a regular tick', () => {
    // On-demand over the full 36-month horizon → ticks every ~5 months, so
    // month index 2 (label "3") would not normally show.
    const w = mountChart({ instances: [res(10, 10)] }, { selectedMonth: 2 })
    expect(w.find('rect.ct-badge').exists()).toBe(true)
    const forced = w.find('text.ct-badge-label')
    expect(forced.exists()).toBe(true)
    expect(forced.text()).toBe('3')
  })
})
