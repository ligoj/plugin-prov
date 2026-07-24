import { describe, it, expect } from 'vitest'
import {
  resourceTypeName,
  diffRatio,
  diffMeta,
  formatDiffPct,
  valueIndex,
  indexValue,
  totalValue,
  comparisonSummary,
} from '../compareApi.js'

describe('resourceTypeName', () => {
  it('uppercases a tab key for the backend enum', () => {
    expect(resourceTypeName('instance')).toBe('INSTANCE')
    expect(resourceTypeName('database')).toBe('DATABASE')
  })
})

describe('diffRatio', () => {
  it('is the signed relative delta (cs - ms)/ms', () => {
    expect(diffRatio(100, 90)).toBeCloseTo(-0.1, 6) // CS 10% cheaper
    expect(diffRatio(100, 130)).toBeCloseTo(0.3, 6)  // CS 30% pricier
    expect(diffRatio(100, 100)).toBe(0)
  })
  it('returns null when there is nothing to compare', () => {
    expect(diffRatio(0, 50)).toBeNull()
    expect(diffRatio(null, 50)).toBeNull()
    expect(diffRatio(100, undefined)).toBeNull()
  })
})

describe('diffMeta', () => {
  it('flags CS-cheaper as a green down arrow', () => {
    expect(diffMeta(-0.1)).toMatchObject({ dir: 'down', color: 'success' })
  })
  it('flags CS-pricier as a red up arrow', () => {
    expect(diffMeta(0.2)).toMatchObject({ dir: 'up', color: 'error' })
  })
  it('treats sub-threshold diffs as "same"', () => {
    expect(diffMeta(0.001).dir).toBe('same')
  })
  it('returns na for null', () => {
    expect(diffMeta(null).dir).toBe('na')
  })
})

describe('formatDiffPct', () => {
  it('signs the percentage and trims precision', () => {
    expect(formatDiffPct(-0.1)).toBe('-10%')
    expect(formatDiffPct(0.045)).toBe('+4.5%')
    expect(formatDiffPct(0)).toBe('0%')
    expect(formatDiffPct(null)).toBe('')
  })
})

describe('valueIndex / indexValue / totalValue', () => {
  const cfg = {
    instances: [{ name: 'web', cost: 100, co2: 5 }],
    databases: [{ name: 'db', cost: 50, co2: 3 }],
    storages: [{ name: 'disk', cost: 10, co2: 1 }],
  }
  it('indexes by type:name for the chosen metric', () => {
    const idx = valueIndex(cfg, 'cost')
    expect(idx.get('instance:web')).toBe(100)
    expect(idx.get('database:db')).toBe(50)
    expect(indexValue(idx, 'instance', 'web')).toBe(100)
    expect(indexValue(idx, 'instance', 'missing')).toBeNull()
  })
  it('indexes CO₂ when asked', () => {
    expect(valueIndex(cfg, 'co2').get('instance:web')).toBe(5)
  })
  it('totals the metric across all compared resources', () => {
    expect(totalValue(cfg, 'cost')).toBe(160)
    expect(totalValue(cfg, 'co2')).toBe(9)
  })
})

describe('comparisonSummary', () => {
  const ms = {
    instances: [{ name: 'web', cost: 100 }, { name: 'api', cost: 40 }],
    databases: [{ name: 'db', cost: 50 }], // will be unmatched
  }
  // CS matched: web cheaper (90), api dearer (60). db missing.
  const csIndex = new Map([['instance:web', 90], ['instance:api', 60]])

  it('unmatched resources contribute the MS value (not 0)', () => {
    const s = comparisonSummary(ms, csIndex, 'cost')
    expect(s.byType.instance).toEqual({ count: 2, ms: 140, cs: 150, unmatched: 0 })
    expect(s.byType.database).toEqual({ count: 1, ms: 50, cs: 50, unmatched: 1 }) // db counts as MS cost
    expect(s.msTotal).toBe(190)
    expect(s.csTotal).toBe(200) // 150 (instances) + 50 (db as MS)
    expect(s.unmatched).toBe(1)
    expect(s.unmatchedCost).toBe(50)
    expect(s.pct).toBeCloseTo((200 - 190) / 190, 6)
  })

  it('is a zero diff when nothing is reproduced (everything counts as MS)', () => {
    const s = comparisonSummary(ms, new Map(), 'cost')
    expect(s.unmatched).toBe(3)
    expect(s.csTotal).toBe(s.msTotal)
    expect(s.pct).toBe(0)
  })
})
