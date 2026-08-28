import { describe, it, expect } from 'vitest'
import { emptyRow, peerKey, parsePeerKey, FREQUENCIES, frequencyItems, normalizeRate, resourceTags, linkCounts, networkChip } from '../networkLinks.js'

const t = (k, p) => (p ? `${k}:${p.seconds}` : k)

describe('networkLinks helpers', () => {
  it('peer keys round-trip', () => {
    expect(peerKey('INSTANCE', 12)).toBe('INSTANCE#12')
    expect(parsePeerKey('DATABASE#3')).toEqual({ type: 'DATABASE', id: 3 })
    expect(parsePeerKey(null)).toBeNull()
    expect(parsePeerKey('INSTANCE#')).toBeNull()
    expect(emptyRow()).toEqual({ name: null, peer: null, port: null, rate: 0, throughput: null }) // 0 = continuous
  })

  it('frequency presets are expressed in seconds, continuous first', () => {
    expect(FREQUENCIES.map((f) => f.seconds)).toEqual([0, 60, 3600, 86400, 604800, 2592000, 31536000])
    expect(FREQUENCIES.find((f) => f.seconds === 3600).key).toBe('hourly')
  })

  it('frequencyItems lists the presets, plus a custom entry for a non-preset value', () => {
    const items = frequencyItems(0, t)
    expect(items.map((i) => i.value)).toEqual([0, 60, 3600, 86400, 604800, 2592000, 31536000])
    expect(items[0].title).toBe('prov.quote.network.frequency.continuous')
    expect(items[2].title).toBe('prov.quote.network.frequency.hourly')
    const custom = frequencyItems(900, t)
    expect(custom.at(-1)).toEqual({ value: 900, title: 'prov.quote.network.frequency.custom:900' })
    // A preset value adds nothing
    expect(frequencyItems(3600, t)).toHaveLength(FREQUENCIES.length)
  })

  it('normalizeRate maps continuous (null / 0 / blank) to null and keeps positive seconds', () => {
    expect(normalizeRate(null)).toBeNull()
    expect(normalizeRate(0)).toBeNull()
    expect(normalizeRate('')).toBeNull()
    expect(normalizeRate(3600)).toBe(3600)
    expect(normalizeRate('86400')).toBe(86400)
  })

  it('linkCounts counts the inbound / outbound links of a resource, whatever the enum casing', () => {
    const config = {
      networks: [
        { source: 2, sourceType: 'instance', target: 1, targetType: 'INSTANCE' },
        { source: 1, sourceType: 'INSTANCE', target: 3, targetType: 'database' },
        { source: 1, sourceType: 'instance', target: 4, targetType: 'STORAGE' },
        { source: 9, sourceType: 'DATABASE', target: 8, targetType: 'INSTANCE' },
      ],
    }
    expect(linkCounts(config, 'instance', 1)).toEqual({ inbound: 1, outbound: 2 })
    expect(linkCounts(config, 'database', 3)).toEqual({ inbound: 1, outbound: 0 })
    expect(linkCounts(config, 'instance', 77)).toEqual({ inbound: 0, outbound: 0 })
    expect(linkCounts({}, 'instance', 1)).toEqual({ inbound: 0, outbound: 0 })
  })

  it('networkChip builds the in/out chip parts, or null when the resource has no link', () => {
    const config = { networks: [{ source: 1, sourceType: 'INSTANCE', target: 3, targetType: 'DATABASE' }] }
    expect(networkChip(config, 'instance', 1)).toEqual([
      { icon: 'mdi-arrow-down', text: 0 },
      { icon: 'mdi-arrow-up', text: 1 },
    ])
    expect(networkChip(config, 'instance', 99)).toBeNull()
    expect(networkChip({}, 'instance', 1)).toBeNull()
  })

  it('resourceTags reads the quote tags map case-insensitively as name:value strings', () => {
    const tags = { INSTANCE: { 1: [{ name: 'app', value: 'shop' }, { name: 'env', value: 'dev' }] }, database: { 3: [{ name: 'env', value: 'prod' }] } }
    expect(resourceTags({ tags }, 'INSTANCE', 1)).toEqual(['app:shop', 'env:dev'])
    expect(resourceTags({ tags }, 'DATABASE', 3)).toEqual(['env:prod'])
    expect(resourceTags({ tags }, 'INSTANCE', 99)).toEqual([])
    expect(resourceTags({}, 'INSTANCE', 1)).toEqual([])
  })
})
