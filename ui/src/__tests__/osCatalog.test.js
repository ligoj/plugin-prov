import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import { h } from 'vue'
import OsIcon from '../views/OsIcon.vue'
import { osLabel, osTooltip } from '../osCatalog.js'

const vuetify = createVuetify({ components, directives })

describe('osCatalog', () => {
  it('maps codes to full names', () => {
    expect(osLabel('RHEL')).toBe('Red Hat Enterprise Linux')
    expect(osLabel('rhel')).toBe('Red Hat Enterprise Linux')
  })

  it('tooltip combines name and code', () => {
    expect(osTooltip('RHEL')).toBe('Red Hat Enterprise Linux (RHEL)')
    expect(osTooltip('windows')).toBe('Microsoft Windows (WINDOWS)')
  })

  it('falls back to the raw code for an unknown OS', () => {
    expect(osLabel('PLAN9')).toBe('PLAN9')
    expect(osTooltip('plan9')).toBe('PLAN9')
  })
})

// Regression guard: the v-autocomplete item/selection slot exposes the RAW
// value as `item` (not `item.raw`); the OS field relied on that. Mounting a
// real v-autocomplete proves the icon + code render for the selected value.
describe('OS autocomplete selection slot', () => {
  it('renders the OS icon and code for the selected value', () => {
    const w = mount(components.VAutocomplete, {
      props: { modelValue: 'RHEL', items: ['LINUX', 'RHEL'] },
      global: { plugins: [vuetify] },
      slots: {
        selection: ({ item }) => h('span', { class: 'sel' }, [h(OsIcon, { os: item }), item]),
      },
    })
    const sel = w.find('.sel')
    expect(sel.exists()).toBe(true)
    expect(sel.find('svg').exists()).toBe(true)
    expect(sel.text()).toContain('RHEL')
  })
})
