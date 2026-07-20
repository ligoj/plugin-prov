import { describe, it, expect } from 'vitest'
import {
  buildLookupParams,
  comparableResources,
  resourceKey,
  summarizeComparison,
  mapLimit,
} from '../comparisonHelpers.js'

describe('buildLookupParams', () => {
  it('sends only provider-neutral requirements for an instance', () => {
    const row = {
      cpu: 2, ram: 4096, os: 'linux', software: 'SQL',
      // all of these must be omitted (target-catalog-resolved / over-constraining):
      location: { name: 'eu-west-3' }, usage: 5, optimizer: 3, budget: 1,
      processor: 'Intel Xeon', architecture: 'x86_64', license: 'BYOL',
    }
    const p = buildLookupParams(row, 'instance')
    expect(p).toEqual({ cpu: '2', ram: '4096', os: 'LINUX', software: 'sql' })
    for (const k of ['location', 'usage', 'optimizer', 'budget', 'processor', 'architecture', 'license', 'type']) {
      expect(p[k]).toBeUndefined()
    }
  })

  it('falls back to the price type capacity when the row omits cpu/ram', () => {
    const p = buildLookupParams({ os: 'WINDOWS', price: { type: { cpu: 8, ram: 16384 } } }, 'instance')
    expect(p).toMatchObject({ cpu: '8', ram: '16384', os: 'WINDOWS' })
  })

  it('adds engine + edition for a database', () => {
    const p = buildLookupParams({ cpu: 1, ram: 2048, engine: 'mysql', edition: 'standard' }, 'database')
    expect(p).toMatchObject({ engine: 'MYSQL', edition: 'standard' })
    expect(p.os).toBeUndefined()
  })

  it('adds the workload params for a function', () => {
    const p = buildLookupParams({ cpu: 1, ram: 1024, nbRequests: 5, duration: 200, concurrency: 2 }, 'function')
    expect(p).toMatchObject({ nbRequests: '5', duration: '200', concurrency: '2' })
  })

  it('forwards gpu and the rate requirements', () => {
    const p = buildLookupParams({ cpu: 1, ram: 1024, gpu: 2, cpuRate: 'BEST', ramRate: 'GOOD' }, 'instance')
    expect(p).toMatchObject({ gpu: '2', cpuRate: 'BEST', ramRate: 'GOOD' })
  })
})

describe('comparableResources', () => {
  it('flattens compute + database rows in tab order, skipping storage/support', () => {
    const cfg = {
      instances: [{ id: 1, name: 'web' }],
      databases: [{ id: 2, name: 'db' }],
      functions: [{ id: 3 }],
      storages: [{ id: 9, name: 'disk' }],
      supports: [{ id: 8 }],
    }
    const list = comparableResources(cfg)
    expect(list.map((r) => r.type)).toEqual(['instance', 'database', 'function'])
    expect(list[0]).toMatchObject({ key: 'instance:1', type: 'instance', name: 'web' })
    expect(list[2].name).toBe('function #1') // unnamed fallback
    expect(list.some((r) => r.type === 'storage' || r.type === 'support')).toBe(false)
  })

  it('resourceKey uses the id, falling back to the index', () => {
    expect(resourceKey('instance', { id: 7 }, 0)).toBe('instance:7')
    expect(resourceKey('instance', {}, 3)).toBe('instance:#3')
  })
})

describe('summarizeComparison', () => {
  const rows = [
    { key: 'i1', name: 'web', type: 'instance', byProvider: { aws: { cost: 100, co2: 5 }, azure: { cost: 90, co2: 8 } } },
    { key: 'd1', name: 'db', type: 'database', byProvider: { aws: { cost: 50, co2: 3 }, azure: { cost: 70, co2: 2 } } },
  ]

  it('marks the cheapest provider per row', () => {
    const s = summarizeComparison(rows, ['aws', 'azure'])
    expect(s.perRow[0].bestPid).toBe('azure') // 90 < 100
    expect(s.perRow[1].bestPid).toBe('aws')   // 50 < 70
  })

  it('totals each provider and picks the cheapest single provider', () => {
    const s = summarizeComparison(rows, ['aws', 'azure'])
    expect(s.totals.aws).toEqual({ value: 150, missing: 0 })
    expect(s.totals.azure).toEqual({ value: 160, missing: 0 })
    expect(s.bestPid).toBe('aws') // 150 < 160
  })

  it('computes the best-of-breed total from per-row minima', () => {
    const s = summarizeComparison(rows, ['aws', 'azure'])
    expect(s.bestOfBreed).toEqual({ value: 140, missing: 0 }) // 90 (azure) + 50 (aws)
  })

  it('counts unmatched cells as missing and excludes incomplete providers from bestPid', () => {
    const partial = [
      { key: 'i1', byProvider: { aws: { cost: 100 }, gcp: null } },
      { key: 'd1', byProvider: { aws: { cost: 50 }, gcp: { cost: 40 } } },
    ]
    const s = summarizeComparison(partial, ['aws', 'gcp'])
    expect(s.totals.gcp.missing).toBe(1)
    expect(s.bestPid).toBe('aws') // gcp is incomplete → can't win
    expect(s.bestOfBreed).toEqual({ value: 140, missing: 0 }) // 100 (aws) + 40 (gcp)
  })

  it('summarizes on CO₂ when asked', () => {
    const s = summarizeComparison(rows, ['aws', 'azure'], 'co2')
    expect(s.perRow[0].bestPid).toBe('aws')   // 5 < 8
    expect(s.totals.aws).toEqual({ value: 8, missing: 0 })
    expect(s.totals.azure).toEqual({ value: 10, missing: 0 })
  })
})

describe('mapLimit', () => {
  it('preserves order and honours the concurrency cap', async () => {
    let inFlight = 0
    let peak = 0
    const fn = async (x) => {
      inFlight++; peak = Math.max(peak, inFlight)
      await new Promise((r) => setTimeout(r, 5))
      inFlight--
      return x * 2
    }
    const out = await mapLimit([1, 2, 3, 4, 5], 2, fn)
    expect(out).toEqual([2, 4, 6, 8, 10])
    expect(peak).toBeLessThanOrEqual(2)
  })
})
