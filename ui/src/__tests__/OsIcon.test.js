import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import OsIcon from '../views/OsIcon.vue'

const mountIcon = (os) => mount(OsIcon, { props: { os } })

describe('<OsIcon>', () => {
  it('renders an inline SVG for a known OS', () => {
    const w = mountIcon('RHEL')
    expect(w.find('svg').exists()).toBe(true)
    expect(w.attributes('title')).toBe('RHEL')
  })

  it('matches the OS case-insensitively', () => {
    expect(mountIcon('windows').find('svg').exists()).toBe(true)
    expect(mountIcon('Ubuntu').find('svg').exists()).toBe(true)
  })

  it('falls back to the Linux glyph for FreeBSD (no dedicated icon)', () => {
    expect(mountIcon('FREEBSD').find('svg').exists()).toBe(true)
  })

  it('renders nothing for an unknown OS', () => {
    const w = mountIcon('PLAN9')
    expect(w.find('svg').exists()).toBe(false)
    expect(w.find('.os-icon').exists()).toBe(false)
  })

  it('renders nothing when no OS is given', () => {
    expect(mountIcon('').find('.os-icon').exists()).toBe(false)
  })
})
