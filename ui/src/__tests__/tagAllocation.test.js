import { describe, it, expect } from 'vitest'
import { tagKeys, tagAllocation, tagGrouping, TAG_OTHER_KEY, TAG_UNTAGGED_KEY } from '../tagAllocation.js'

// Tags: instance 1 → team=alpha, instance 2 → team=beta+env=prod, database 3 → env=prod (no team).
const config = {
  instances: [{ id: 1, name: 'web', cost: 100, co2: 10 }, { id: 2, name: 'api', cost: 60, co2: 6 }],
  databases: [{ id: 3, name: 'db', cost: 40, co2: 4 }],
  tags: {
    INSTANCE: {
      1: [{ name: 'team', value: 'alpha' }],
      2: [{ name: 'team', value: 'beta' }, { name: 'env', value: 'prod' }],
    },
    DATABASE: { 3: [{ name: 'env', value: 'prod' }] },
  },
}

describe('tagKeys', () => {
  it('returns the distinct tag names, sorted', () => {
    expect(tagKeys(config)).toEqual(['env', 'team'])
  })
  it('is empty when there are no tags', () => {
    expect(tagKeys({ tags: {} })).toEqual([])
    expect(tagKeys({})).toEqual([])
  })
})

describe('tagAllocation', () => {
  it('buckets cost by tag value, with an untagged bucket', () => {
    const a = tagAllocation(config, 'team', 'cost')
    expect(a.total).toBe(200)
    expect(a.buckets.map((b) => [b.value, b.amount, b.count])).toEqual([['alpha', 100, 1], ['beta', 60, 1]])
    expect(a.untagged).toMatchObject({ amount: 40, count: 1 }) // the db has no team
    expect(a.tagged).toBe(160)
    expect(a.coverage).toBeCloseTo(0.8, 6) // 160 / 200
    expect(a.buckets[0].share).toBeCloseTo(0.5, 6)
  })

  it('sorts buckets by amount descending', () => {
    const a = tagAllocation({ ...config }, 'env', 'cost')
    // env=prod on api(60) + db(40) = 100; web has no env → untagged 100.
    expect(a.buckets).toEqual([{ value: 'prod', amount: 100, count: 2, share: 0.5 }])
    expect(a.untagged.amount).toBe(100)
  })

  it('allocates CO₂ when asked', () => {
    const a = tagAllocation(config, 'team', 'co2')
    expect(a.total).toBe(20)
    expect(a.buckets[0]).toMatchObject({ value: 'alpha', amount: 10 })
  })

  it('everything is untagged for an unknown key (0% coverage)', () => {
    const a = tagAllocation(config, 'missing', 'cost')
    expect(a.buckets).toEqual([])
    expect(a.untagged.amount).toBe(200)
    expect(a.coverage).toBe(0)
  })
})

describe('tagGrouping', () => {
  it('groups resources by tag value, with an untagged group', () => {
    const g = tagGrouping(config, 'team', 'cost', 8)
    expect(g.groups.map((x) => x.key)).toEqual(['alpha', 'beta', TAG_UNTAGGED_KEY])
    expect(g.groups[0].color).toMatch(/theme-primary/)
    expect(g.groupOf('instance', { id: 1 })).toBe('alpha')
    expect(g.groupOf('instance', { id: 2 })).toBe('beta')
    expect(g.groupOf('database', { id: 3 })).toBe(TAG_UNTAGGED_KEY) // db has no team
  })

  it('folds low buckets into "Other" beyond maxGroups', () => {
    const g = tagGrouping(config, 'team', 'cost', 1) // keep only the top value (alpha)
    expect(g.groups.map((x) => x.key)).toEqual(['alpha', TAG_OTHER_KEY, TAG_UNTAGGED_KEY])
    expect(g.groupOf('instance', { id: 2 })).toBe(TAG_OTHER_KEY) // beta folded into Other
  })
})
