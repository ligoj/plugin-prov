import { describe, it, expect } from 'vitest'
import { textMatcher, quickMatch, matchesFilter, rowPasses, FILTER_FIELDS, NUMBER_OPS } from '../searchFilters.js'

describe('textMatcher', () => {
  it('does case-insensitive substring by default', () => {
    expect(textMatcher('web')('my-WEB-server')).toBe(true)
    expect(textMatcher('web')('database')).toBe(false)
    expect(textMatcher('')('anything')).toBe(true)
  })
  it('detects and applies a regex with metacharacters', () => {
    expect(textMatcher('^web-\\d+$')('web-12')).toBe(true)
    expect(textMatcher('^web-\\d+$')('my-web-12')).toBe(false)
    expect(textMatcher('web|db')('db-1')).toBe(true)
  })
  it('supports the explicit /pattern/ form', () => {
    expect(textMatcher('/^srv/')('srv-1')).toBe(true)
    expect(textMatcher('/^srv/')('my-srv')).toBe(false)
  })
  it('falls back to substring on an invalid regex', () => {
    expect(textMatcher('a(b')('xa(bx')).toBe(true)
  })
})

const row = {
  id: 5, name: 'web-1', cost: 100, co2: 12, os: 'LINUX',
  location: { name: 'region-1' },
  price: { type: { name: 'm1.large', code: 'm1' }, term: { name: 'reserved-3y' } },
}
const ctx = {
  profileName: (field, r) => (field === 'usage' && r.id === 5 ? 'Dev' : null),
  locationName: (r) => r.location?.name,
  tagsFor: (tab, id) => (id === 5 ? [{ name: 'team', value: 'alpha' }] : []),
}

describe('matchesFilter', () => {
  it('numeric compare on cost / co2 (> < =)', () => {
    expect(matchesFilter(row, 'instance', { field: 'cost', op: '>', value: '50' }, ctx)).toBe(true)
    expect(matchesFilter(row, 'instance', { field: 'cost', op: '<', value: '50' }, ctx)).toBe(false)
    expect(matchesFilter(row, 'instance', { field: 'cost', op: '=', value: '100' }, ctx)).toBe(true)
    expect(matchesFilter(row, 'instance', { field: 'co2', op: '>', value: '10' }, ctx)).toBe(true)
  })
  it('matches type / term with regex-aware text', () => {
    expect(matchesFilter(row, 'instance', { field: 'type', value: 'm1' }, ctx)).toBe(true)
    expect(matchesFilter(row, 'instance', { field: 'term', value: '/^reserved/' }, ctx)).toBe(true)
    expect(matchesFilter(row, 'instance', { field: 'term', value: 'on-demand' }, ctx)).toBe(false)
  })
  it('matches entity fields exactly (os, location, usage) and tags with key[:value]', () => {
    expect(matchesFilter(row, 'instance', { field: 'os', value: 'linux' }, ctx)).toBe(true)
    expect(matchesFilter(row, 'instance', { field: 'location', value: 'region-1' }, ctx)).toBe(true)
    expect(matchesFilter(row, 'instance', { field: 'location', value: 'region-2' }, ctx)).toBe(false)
    expect(matchesFilter(row, 'instance', { field: 'usage', value: 'Dev' }, ctx)).toBe(true)
    expect(matchesFilter(row, 'instance', { field: 'tag', value: 'team' }, ctx)).toBe(true)
    expect(matchesFilter(row, 'instance', { field: 'tag', value: 'team', value2: 'alpha' }, ctx)).toBe(true)
    expect(matchesFilter(row, 'instance', { field: 'tag', value: 'team', value2: 'beta' }, ctx)).toBe(false)
  })
  it('empty value is a no-op filter', () => {
    expect(matchesFilter(row, 'instance', { field: 'cost', op: '>', value: '' }, ctx)).toBe(true)
  })
})

describe('rowPasses (AND / OR)', () => {
  const cheap = { field: 'cost', op: '<', value: '50' }   // false for row
  const linux = { field: 'os', value: 'LINUX' }           // true
  it('AND requires every filter', () => {
    expect(rowPasses(row, 'instance', { mode: 'AND', filters: [linux, cheap] }, ctx)).toBe(false)
    expect(rowPasses(row, 'instance', { mode: 'AND', filters: [linux] }, ctx)).toBe(true)
  })
  it('OR requires at least one', () => {
    expect(rowPasses(row, 'instance', { mode: 'OR', filters: [linux, cheap] }, ctx)).toBe(true)
    expect(rowPasses(row, 'instance', { mode: 'OR', filters: [cheap] }, ctx)).toBe(false)
  })
  it('an empty set matches everything', () => {
    expect(rowPasses(row, 'instance', { mode: 'AND', filters: [] }, ctx)).toBe(true)
    expect(rowPasses(row, 'instance', null, ctx)).toBe(true)
  })
})

describe('quickMatch + catalog sanity', () => {
  it('quickMatch spans the searched values with regex support', () => {
    expect(quickMatch(row, textMatcher('m1\\.'))).toBe(true)
    expect(quickMatch(row, textMatcher('region'))).toBe(true)
    expect(quickMatch(row, textMatcher('zzz'))).toBe(false)
  })
  it('exposes the field catalog and numeric ops', () => {
    expect(FILTER_FIELDS.some((f) => f.key === 'tag')).toBe(true)
    expect(NUMBER_OPS).toEqual(['>', '<', '='])
  })
})
