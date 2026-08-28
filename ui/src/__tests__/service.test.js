import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import service from '../service.js'
import enMessages from '../i18n/en.js'

const vuetify = createVuetify({ components, directives })

// A provisioning subscription as returned by `rest/subscription/status/refresh`
const SUBSCRIPTION = {
  id: 7,
  data: {
    quote: {
      nbInstances: 3,
      totalCpu: 21037.489999999998,
      totalRam: 148310 * 1024, // MB
      totalStorage: 163573, // GB
      location: { name: 'eu-west-1', countryA2: 'IE', countryM49: 372, continentM49: 150 },
    },
  },
}

function renderDetails(subscription) {
  return mount({ render: () => service.renderDetailsKey(subscription) }, {
    global: { plugins: [vuetify, i18nPlugin] },
  })
}

describe('service.renderDetailsKey (subscription row details)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mergeMessages(enMessages, 'en')
  })

  it('formats the CPU / RAM / storage totals with the quote formatters', () => {
    const text = renderDetails(SUBSCRIPTION).text()
    expect(text).toContain('21K vCPU')
    expect(text).toContain('144.8 TB') // memory is base-1024 everywhere in the plugin
    expect(text).toContain('163.6 TB')
    expect(text).not.toContain('148310')
    expect(text).not.toContain('21037')
  })

  it('renders the preferred location with the location component (flag + country)', () => {
    const w = renderDetails(SUBSCRIPTION)
    const label = w.find('.loc-label')
    expect(label.exists()).toBe(true)
    expect(label.find('.loc-flag').text()).toBe('🇮🇪')
    expect(label.text()).toContain('Ireland')
  })

  it('renders nothing without quote data', () => {
    expect(service.renderDetailsKey({ id: 1, data: {} })).toBeNull()
  })
})
