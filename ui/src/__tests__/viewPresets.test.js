import { describe, it, expect } from 'vitest'
import { viewsStorageKey, readViews, writeViews, upsertView, removeView } from '../viewPresets.js'

const memoryStorage = () => {
  const m = new Map()
  return { getItem: (k) => m.get(k) ?? null, setItem: (k, v) => m.set(k, v) }
}

describe('viewPresets', () => {
  it('keys the storage per subscription', () => {
    expect(viewsStorageKey(1801)).toBe('ligoj-prov-quote-views-1801')
  })

  it('round-trips views through storage', () => {
    const s = memoryStorage()
    const key = viewsStorageKey(1)
    writeViews(s, key, [{ name: 'prod', search: 'web' }])
    expect(readViews(s, key)).toEqual([{ name: 'prod', search: 'web' }])
  })

  it('reads defensively: broken JSON, wrong shapes, nameless entries', () => {
    const s = memoryStorage()
    s.setItem('k', '{not json')
    expect(readViews(s, 'k')).toEqual([])
    s.setItem('k', JSON.stringify({ nope: 1 }))
    expect(readViews(s, 'k')).toEqual([])
    s.setItem('k', JSON.stringify([{ name: 'ok' }, { search: 'x' }, null]))
    expect(readViews(s, 'k')).toEqual([{ name: 'ok' }])
    expect(readViews(null, 'k')).toEqual([])
  })

  it('upserts by name and keeps the list sorted', () => {
    let views = upsertView([], { name: 'b', search: '1' })
    views = upsertView(views, { name: 'a', search: '2' })
    views = upsertView(views, { name: 'b', search: '3' }) // replace
    expect(views.map((v) => [v.name, v.search])).toEqual([['a', '2'], ['b', '3']])
  })

  it('removes by name', () => {
    expect(removeView([{ name: 'a' }, { name: 'b' }], 'a')).toEqual([{ name: 'b' }])
  })
})
