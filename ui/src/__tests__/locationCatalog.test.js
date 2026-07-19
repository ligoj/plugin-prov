import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import i18nPlugin, { mergeMessages } from '@/plugins/i18n.js'
import enMessages from '../i18n/en.js'
import {
  flagEmoji, globeEmoji, geoGlyph, m49Name, geoFromCode, resolveGeo, locationMatches, normA2,
} from '../locationCatalog.js'
import LocationLabel from '../views/LocationLabel.vue'
import LocationField from '../views/LocationField.vue'

const vuetify = createVuetify({ components, directives })
const withApp = { global: { plugins: [vuetify, i18nPlugin] } }

// Explicit-field locations.
const usEast = { name: 'us-east-1', countryA2: 'US', countryM49: 840, continentM49: 19, latitude: 38, longitude: -78 }
const ukLon = { name: 'eu-west-2', countryA2: 'UK', countryM49: 826, continentM49: 150 }
const frParis = {
  name: 'eu-west-3', description: 'EU (Paris)', countryA2: 'FR', countryM49: 250,
  continentM49: 150, regionM49: 155, latitude: 48.7, longitude: 2.2, subRegion: 'Paris',
}

describe('geo helpers', () => {
  it('flagEmoji builds regional-indicator pairs (UK→GB)', () => {
    expect(flagEmoji('US')).toBe('🇺🇸')
    expect(flagEmoji('uk')).toBe(flagEmoji('GB'))
    expect(flagEmoji('X')).toBe('')
    expect(flagEmoji(null)).toBe('')
    expect(normA2('uk')).toBe('GB')
  })

  it('m49Name resolves per-locale with English fallback', () => {
    expect(m49Name(840, 'en')).toBe('USA')
    expect(m49Name(840, 'fr')).toBe('États-Unis')
    expect(m49Name(150, 'en')).toBe('Europe')
    expect(m49Name(999)).toBeNull()
  })

  it('geoFromCode derives country / continent from the code tokens', () => {
    expect(geoFromCode('us-east-1')).toMatchObject({ a2: 'US', countryM49: 840 })
    expect(geoFromCode('eu-west-1')).toEqual({ continentM49: 150 })   // cloud prefix → Europe
    expect(geoFromCode('ap-southeast-2')).toEqual({ continentM49: 142 })
    expect(geoFromCode('af-south-1')).toEqual({ continentM49: 2 })     // Africa, not Afghanistan
    expect(geoFromCode('ca-central-1')).toMatchObject({ a2: 'CA', countryM49: 124 })
    expect(geoFromCode('global')).toBeNull()
  })

  it('resolveGeo prefers explicit fields, else derives from the code', () => {
    expect(resolveGeo(usEast)).toMatchObject({ a2: 'US', countryM49: 840, continentM49: 19, lat: 38, lon: -78 })
    expect(resolveGeo({ name: 'eu-west-1' })).toMatchObject({ a2: null, continentM49: 150 })
    expect(resolveGeo({ name: 'us-east-1' })).toMatchObject({ a2: 'US', countryM49: 840, continentM49: 19 })
  })

  it('globeEmoji maps continents to the region-facing globe', () => {
    expect(globeEmoji(150)).toBe('🌍') // Europe → Europe-Africa
    expect(globeEmoji(2)).toBe('🌍')   // Africa
    expect(globeEmoji(19)).toBe('🌎')  // Americas
    expect(globeEmoji(142)).toBe('🌏') // Asia
    expect(globeEmoji(9)).toBe('🌏')   // Oceania → Asia-Australia
    expect(globeEmoji(null)).toBe('')
  })

  it('geoGlyph is the country flag, else the region globe', () => {
    expect(geoGlyph(resolveGeo(usEast))).toBe('🇺🇸')
    expect(geoGlyph(resolveGeo({ name: 'eu-south-2' }))).toBe('🌍') // continent only → globe
    expect(geoGlyph(resolveGeo({ name: 'ap-southeast-2' }))).toBe('🌏')
  })

  it('locationMatches covers code, country, code-derived and continent', () => {
    expect(locationMatches(usEast, 'us-east')).toBe(true)
    expect(locationMatches(usEast, 'US')).toBe(true)
    expect(locationMatches(usEast, 'usa')).toBe(true)
    expect(locationMatches(usEast, 'États', 'fr')).toBe(true)
    expect(locationMatches(usEast, 'americas')).toBe(true)
    expect(locationMatches(ukLon, 'europe')).toBe(true)
    expect(locationMatches({ name: 'eu-west-1' }, 'europe')).toBe(true) // derived continent
    expect(locationMatches(usEast, 'zzzz')).toBe(false)
  })
})

describe('<LocationLabel>', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mergeMessages(enMessages, 'en')
  })

  it('renders the emoji flag + localized country name', () => {
    const w = mount(LocationLabel, { props: { location: usEast, tooltip: false }, ...withApp })
    expect(w.find('.loc-flag').text()).toBe('🇺🇸')
    expect(w.find('.loc-name').text()).toBe('USA')
  })

  it('derives the country from the code when not provided', () => {
    const w = mount(LocationLabel, { props: { location: { name: 'ca-central-1' }, tooltip: false }, ...withApp })
    expect(w.find('.loc-flag').text()).toBe('🇨🇦')
    expect(w.find('.loc-name').text()).toBe('Canada')
  })

  it('shows the region globe + continent name when no country (eu-south-2)', () => {
    const w = mount(LocationLabel, { props: { location: { name: 'eu-south-2' }, tooltip: false }, ...withApp })
    expect(w.find('.loc-flag').text()).toBe('🌍')
    expect(w.find('.loc-name').text()).toBe('Europe')
  })

  it('shows the code when requested', () => {
    const w = mount(LocationLabel, { props: { location: ukLon, showCode: true, tooltip: false }, ...withApp })
    expect(w.find('.loc-flag').text()).toBe('🇬🇧')
    expect(w.find('.loc-code').text()).toBe('eu-west-2')
  })

  it('renders the tooltip with subRegion + map on hover (t is wired up)', async () => {
    const w = mount(LocationLabel, { props: { location: frParis }, attachTo: document.body, ...withApp })
    await w.find('.loc-label').trigger('mouseenter')
    await new Promise((r) => setTimeout(r, 250))
    const tip = document.querySelector('.loc-tip')
    expect(tip).toBeTruthy()
    expect(tip.textContent).toContain('France')
    expect(tip.textContent).toContain('Code')  // t('prov.quote.location.code')
    expect(tip.textContent).toContain('Paris') // subRegion
    const iframe = tip.querySelector('iframe.loc-tip-map')
    expect(iframe).toBeTruthy()
    expect(iframe.getAttribute('src')).toContain('openstreetmap.org/export/embed')
    expect(iframe.getAttribute('src')).toContain('marker=')
    // Our own attribution replaces the clipped OSM footer.
    expect(tip.querySelector('.loc-tip-map-attr')?.textContent).toContain('OpenStreetMap')
    w.unmount()
  })

  it('shows no map when coordinates are absent', async () => {
    const w = mount(LocationLabel, { props: { location: ukLon }, attachTo: document.body, ...withApp })
    await w.find('.loc-label').trigger('mouseenter')
    await new Promise((r) => setTimeout(r, 250))
    const tip = document.querySelector('.loc-tip')
    expect(tip.querySelector('iframe.loc-tip-map')).toBeNull()
    w.unmount()
  })
})

describe('<LocationField>', () => {
  beforeEach(() => { setActivePinia(createPinia()); mergeMessages(enMessages, 'en') })

  it('renders the selected location as flag + name', () => {
    const w = mount(LocationField, {
      props: { modelValue: 'us-east-1', items: [usEast, ukLon], label: 'Location' },
      ...withApp,
    })
    const label = w.find('.loc-label')
    expect(label.find('.loc-flag').text()).toBe('🇺🇸')
    expect(label.text()).toContain('USA')
  })

  it('filters by name, code, country, M49 and continent', () => {
    const w = mount(LocationField, { props: { items: [usEast, ukLon] }, ...withApp })
    const f = w.vm.filter
    expect(f('', 'usa', { raw: usEast })).toBe(true)
    expect(f('', '840', { raw: usEast })).toBe(true)
    expect(f('', 'americas', { raw: usEast })).toBe(true)
    expect(f('', 'europe', { raw: ukLon })).toBe(true)
    expect(f('', 'zzz', { raw: usEast })).toBe(false)
  })
})
