import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import { pluginRegistry } from '@ligoj/host'
import CatalogConfigDialog from '../views/CatalogConfigDialog.vue'
import enMessages from '../i18n/en.js'

const vuetify = createVuetify({ components, directives })

function jsonResponse(body) {
  return Promise.resolve({
    ok: true,
    status: 200,
    headers: { get: (k) => (k === 'content-type' ? 'application/json' : null) },
    json: () => Promise.resolve(body),
  })
}

const CATALOG = { node: { id: 'service:prov:aws', name: 'AWS' } }

function mountDialog() {
  return mount(CatalogConfigDialog, {
    props: { modelValue: true, catalog: CATALOG },
    global: { plugins: [vuetify, i18nPlugin] },
    attachTo: document.body,
  })
}

describe('<CatalogConfigDialog>', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mergeMessages(enMessages, 'en')
  })
  afterEach(() => vi.restoreAllMocks())

  it('loads the configuration and renders the common patterns and provider properties', async () => {
    globalThis.fetch = vi.fn(() => jsonResponse({
      defaultLocation: 'eu-west-1',
      locations: ['eu-west-1', 'eu-west-3'],
      properties: { 'service:prov:aws:regions': 'eu-.*' },
    }))
    // Provider plugin contribution
    vi.spyOn(pluginRegistry, 'get').mockReturnValue({
      feature: (action) => {
        if (action === 'catalogConfiguration') {
          return [{ name: 'service:prov:aws:aws-prices-url', key: 'awsPricesUrl', type: 'string', default: 'https://x' }]
        }
        throw new Error('no feature "' + action + '"')
      },
    })

    mountDialog()
    await flushPromises()

    const [url] = globalThis.fetch.mock.calls[0]
    expect(url).toContain('rest/service/prov/catalog/service%3Aprov%3Aaws/configuration')
    expect(url).toContain('names=service%3Aprov%3Aaws%3Aregions')
    expect(url).toContain('names=service%3Aprov%3Aaws%3Aaws-prices-url')

    const overlay = document.body.textContent
    expect(overlay).toContain('Default location')
    expect(overlay).toContain('Enabled regions')
    expect(overlay).toContain('Enabled database engines')
    // Provider-specific label resolved from the NLS convention key, falling back to the name
    expect(overlay).toContain('service:prov:aws:aws-prices-url')
  })

  it('saves the default location and all rendered properties', async () => {
    const calls = []
    globalThis.fetch = vi.fn((url, options = {}) => {
      calls.push({ url, options })
      if ((options.method || 'GET') === 'PUT') {
        return Promise.resolve({ ok: true, status: 204, headers: { get: () => null }, text: () => Promise.resolve('') })
      }
      return jsonResponse({ defaultLocation: null, locations: ['eu-west-1'], properties: {} })
    })
    vi.spyOn(pluginRegistry, 'get').mockReturnValue(null)

    const wrapper = mountDialog()
    await flushPromises()
    wrapper.vm.form.defaultLocation = 'eu-west-1'
    wrapper.vm.form.properties['service:prov:aws:regions'] = 'eu-.*'
    await wrapper.vm.save()

    const put = calls.find((c) => (c.options.method || 'GET') === 'PUT')
    expect(put).toBeTruthy()
    const body = JSON.parse(put.options.body)
    expect(body.node).toBe('service:prov:aws')
    expect(body.defaultLocation).toBe('eu-west-1')
    expect(body.properties['service:prov:aws:regions']).toBe('eu-.*')
    // Untouched properties are sent blank so the backend deletes them
    expect(body.properties['service:prov:aws:os']).toBe('')
  })
})
