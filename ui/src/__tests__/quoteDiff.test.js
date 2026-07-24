import { describe, it, expect } from 'vitest'
import { normalizeConfig, snapshotRows, quoteDiff } from '../quoteDiff.js'

describe('normalizeConfig', () => {
  it('flattens the live config into canonical rows', () => {
    const rows = normalizeConfig({
      instances: [{ name: 'web', cost: 100, maxCost: 120, co2: 5,
        price: { type: { name: 'm1' }, term: { name: 'on-demand' } }, location: { name: 'r1' } }],
      storages: [{ name: 'disk', cost: 10, size: 50, price: { type: { name: 'ssd' } } }],
    })
    expect(rows).toHaveLength(2)
    expect(rows[0]).toMatchObject({ resourceType: 'instance', name: 'web', cost: 100, maxCost: 120, typeName: 'm1', term: 'on-demand', location: 'r1' })
    expect(rows[1]).toMatchObject({ resourceType: 'storage', name: 'disk', size: 50, typeName: 'ssd', term: null })
  })
})

describe('snapshotRows', () => {
  it('passes through the normalized document rows', () => {
    const rows = snapshotRows({ resources: [
      { resourceType: 'instance', name: 'web', cost: 90, typeName: 'm1', term: 'reserved' },
    ] })
    expect(rows[0]).toMatchObject({ resourceType: 'instance', name: 'web', cost: 90, maxCost: 90, term: 'reserved' })
    expect(snapshotRows(null)).toEqual([])
  })
})

describe('quoteDiff', () => {
  const A = [
    { resourceType: 'instance', name: 'web', cost: 100, co2: 5, typeName: 'm1', term: 'od' },
    { resourceType: 'instance', name: 'gone', cost: 40, co2: 2, typeName: 'm1', term: 'od' },
    { resourceType: 'database', name: 'db', cost: 50, co2: 3, typeName: 'db1', term: 'od' },
  ]
  const B = [
    { resourceType: 'instance', name: 'web', cost: 80, co2: 5, typeName: 'm2', term: 'od' }, // cheaper + type moved
    { resourceType: 'database', name: 'db', cost: 50, co2: 3, typeName: 'db1', term: 'od' }, // identical
    { resourceType: 'instance', name: 'new', cost: 30, co2: 1, typeName: 'm1', term: 'od' }, // added
  ]

  it('classifies added / removed / changed / unchanged', () => {
    const d = quoteDiff(A, B, 'cost')
    expect(d.added.map((x) => x.row.name)).toEqual(['new'])
    expect(d.removed.map((x) => x.row.name)).toEqual(['gone'])
    expect(d.changed.map((x) => x.a.name)).toEqual(['web'])
    expect(d.changed[0]).toMatchObject({ from: 100, to: 80, delta: -20, priceChanged: true })
    expect(d.changed[0].pct).toBeCloseTo(-0.2, 6)
    expect(d.unchanged).toBe(1)
  })

  it('totals both sides and the signed delta', () => {
    const d = quoteDiff(A, B, 'cost')
    expect(d.totals).toMatchObject({ from: 190, to: 160, delta: -30 })
    expect(d.totals.pct).toBeCloseTo(-30 / 190, 6)
  })

  it('flags a type/term move even at identical cost', () => {
    const d = quoteDiff(
      [{ resourceType: 'instance', name: 'web', cost: 100, typeName: 'm1', term: 'od' }],
      [{ resourceType: 'instance', name: 'web', cost: 100, typeName: 'm1', term: 'reserved' }],
    )
    expect(d.changed).toHaveLength(1)
    expect(d.changed[0]).toMatchObject({ delta: 0, priceChanged: true })
  })

  it('diffs on CO₂ when asked and sorts movers first', () => {
    const d = quoteDiff(A, B, 'co2')
    expect(d.totals.from).toBe(10)
    expect(d.totals.to).toBe(9)
    // web co2 unchanged but type moved → still "changed", with delta 0.
    expect(d.changed[0].delta).toBe(0)
  })

  it('handles empty sides', () => {
    const d = quoteDiff([], B, 'cost')
    expect(d.added).toHaveLength(3)
    expect(d.removed).toHaveLength(0)
    expect(d.totals.pct).toBeNull() // no reference total → no ratio
  })
})
