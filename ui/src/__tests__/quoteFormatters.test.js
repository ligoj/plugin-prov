import { describe, it, expect } from 'vitest'
import {
  formatCost,
  formatCostRange,
  formatCo2,
  formatCo2Range,
  formatCpu,
  formatRam,
  formatStorage,
  formatReduced,
  scaleUnit,
  donutPath,
  donutFullPath,
  scaleCost,
  COST_PERIODS,
  COST_PERIOD_FACTORS,
  rowMatches,
  maxOfField,
  sumCostRange,
  nextName,
  TAB_TYPES,
} from '../quoteFormatters.js'

// Exact decimal rendering is locale-sensitive (comma vs dot); pin the
// locale in assertions that check specific separators so the suite is
// deterministic regardless of the runner's default locale.
const EN = 'en-US'

describe('quoteFormatters', () => {
  describe('formatCost', () => {
    it('returns dash for null/undefined', () => {
      expect(formatCost(null)).toBe('-')
      expect(formatCost(undefined)).toBe('-')
    })

    it('formats sub-1 values with 3 decimals', () => {
      expect(formatCost(0.5)).toBe('0.500 $')
    })

    it('formats sub-100 values with 2 decimals', () => {
      expect(formatCost(12.345)).toBe('12.35 $') // toFixed rounds
    })

    it('renders whole units without scaling between 100 and 1000', () => {
      expect(formatCost(150, undefined, EN)).toBe('150 $')
      expect(formatCost(999, undefined, EN)).toBe('999 $')
    })

    it('SI-scales values ≥ 1000 to reduce useless precision', () => {
      expect(formatCost(1234.7, undefined, EN)).toBe('1.23 k$')
      // 1,500,622 $ → 1.5 M$ (the redesign brief's amount example).
      expect(formatCost(1500622, undefined, EN)).toBe('1.5 M$')
      expect(formatCost(2_500_000_000, undefined, EN)).toBe('2.5 G$')
    })

    it('honours the active locale for the separator (fr → comma)', () => {
      expect(formatCost(1500622, undefined, 'fr-FR')).toBe('1,5 M$')
    })

    it('applies currency rate and unit', () => {
      expect(formatCost(10, { unit: '€', rate: 0.9 })).toBe('9.00 €')
    })

    it('formats zero as 0.000', () => {
      // Zero falls in the < 1 branch — three decimals.
      expect(formatCost(0)).toBe('0.000 $')
    })
  })

  describe('formatCostRange', () => {
    it('returns dash for null/empty', () => {
      expect(formatCostRange(null)).toBe('-')
      expect(formatCostRange({})).toBe('-')
    })

    it('handles a plain number as a fixed point', () => {
      expect(formatCostRange(10)).toBe('10.00 $')
    })

    it('shows a single value when min === max', () => {
      expect(formatCostRange({ min: 12, max: 12 })).toBe('12.00 $')
    })

    it('shows min – max when they differ', () => {
      expect(formatCostRange({ min: 10, max: 20 })).toBe('10.00 $ – 20.00 $')
    })

    it('appends the unbound + marker', () => {
      expect(formatCostRange({ min: 10, max: 20, unbound: true })).toBe('10.00 $ – 20.00 $+')
    })
  })

  describe('formatReduced', () => {
    it('keeps up to 2 decimals for 1-digit integers', () => {
      expect(formatReduced(8.2486, EN)).toBe('8.25')
    })
    it('trims trailing zeros', () => {
      expect(formatReduced(1.5, EN)).toBe('1.5')
      expect(formatReduced(2, EN)).toBe('2')
    })
    it('drops to 1 decimal for 3-digit integers', () => {
      expect(formatReduced(163.573, EN)).toBe('163.6')
    })
    it('renders comma separators under a comma locale', () => {
      expect(formatReduced(8.25, 'fr-FR')).toBe('8,25')
    })
  })

  describe('scaleUnit', () => {
    it('scales SI units by 1000', () => {
      expect(scaleUnit(8248600, ['g', 'kg', 't'], { locale: EN })).toBe('8.25 t')
    })
    it('scales memory by 1024', () => {
      expect(scaleUnit(2048, ['MB', 'GB', 'TB'], { radix: 1024, locale: EN })).toBe('2 GB')
    })
    it('stops at the top of the ladder', () => {
      expect(scaleUnit(5_000_000, ['g', 'kg', 't'], { locale: EN })).toBe('5 t')
    })
  })

  describe('formatCo2', () => {
    it('returns dash for null', () => {
      expect(formatCo2(null)).toBe('-')
    })
    it('keeps grams below 1 kg', () => {
      expect(formatCo2(750, EN)).toBe('750 g')
    })
    it('switches to kg at 1 kg+', () => {
      expect(formatCo2(1500, EN)).toBe('1.5 kg')
    })
    it('switches to tonnes and reduces precision (8248.6 kg → 8.25 t)', () => {
      expect(formatCo2(8248600, EN)).toBe('8.25 t')
    })
  })

  describe('formatCo2Range', () => {
    it('returns dash for null / empty', () => {
      expect(formatCo2Range(null)).toBe('-')
      expect(formatCo2Range({})).toBe('-')
    })
    it('renders a plain number', () => {
      expect(formatCo2Range(1500, EN)).toBe('1.5 kg')
    })
    it('collapses equal / single bounds to one value', () => {
      expect(formatCo2Range({ min: 1500, max: 1500 }, EN)).toBe('1.5 kg')
      expect(formatCo2Range({ min: null, max: 1500 }, EN)).toBe('1.5 kg')
      expect(formatCo2Range({ min: 1500, max: null }, EN)).toBe('1.5 kg')
    })
    it('renders a min – max span', () => {
      expect(formatCo2Range({ min: 1500, max: 3000 }, EN)).toBe('1.5 kg – 3 kg')
    })
  })

  describe('formatRam', () => {
    it('returns "" for null', () => {
      expect(formatRam(null)).toBe('')
    })
    it('shows MB below 1 GB', () => {
      expect(formatRam(512, EN)).toBe('512 MB')
    })
    it('shows binary GB at 1 GB and above (memory is base-1024)', () => {
      expect(formatRam(2048, EN)).toBe('2 GB')
      expect(formatRam(8192, EN)).toBe('8 GB')
      expect(formatRam(1536, EN)).toBe('1.5 GB')
    })
  })

  describe('formatStorage', () => {
    it('returns "" for null', () => {
      expect(formatStorage(null)).toBe('')
    })
    it('shows GB below 1 TB', () => {
      expect(formatStorage(500, EN)).toBe('500 GB')
    })
    it('shows SI TB above 1000 GB (163573 GB → 163.6 TB)', () => {
      expect(formatStorage(2048, EN)).toBe('2.05 TB')
      expect(formatStorage(163573, EN)).toBe('163.6 TB')
    })
  })

  describe('formatCpu', () => {
    it('returns "" for null', () => {
      expect(formatCpu(null)).toBe('')
    })
    it('drops trailing zeros for integers', () => {
      expect(formatCpu(4)).toBe('4')
    })
    it('keeps one decimal for fractional values', () => {
      expect(formatCpu(2.5)).toBe('2.5')
    })
  })

  describe('donutPath', () => {
    it('returns an SVG arc path string', () => {
      const d = donutPath(100, 100, 80, 50, 0, Math.PI / 2)
      expect(d).toMatch(/^M /)
      expect(d).toContain('A 80 80 0')
      expect(d).toContain('A 50 50 0')
      expect(d.endsWith(' Z')).toBe(true)
    })
    it('flags large-arc when slice > π', () => {
      const d = donutPath(0, 0, 10, 5, 0, Math.PI * 1.5)
      // The large-arc flag for the outer arc is the 4th token after the radii.
      expect(d).toMatch(/A 10 10 0 1 1/)
    })
    it('does not flag large-arc for a quarter slice', () => {
      const d = donutPath(0, 0, 10, 5, 0, Math.PI / 2)
      // The outer-arc large-arc flag must be 0 here.
      expect(d).toMatch(/A 10 10 0 0 1/)
      expect(d).toMatch(/A 5 5 0 0 0/)
    })
    it('places the endpoints at the correct trig positions', () => {
      // Quarter slice starting from the +x axis: end = (0, 10) at 90°.
      const d = donutPath(0, 0, 10, 5, 0, Math.PI / 2)
      expect(d).toMatch(/^M 10 0 /)               // start outer at (r, 0)
      expect(d).toMatch(/A 10 10 0 0 1 [\d.eE-]+ 10 /) // end outer near y=10
    })
  })

  describe('donutFullPath', () => {
    it('yields an even-odd annulus', () => {
      const d = donutFullPath(50, 50, 40, 20)
      expect(d).toContain('A 40 40')
      expect(d).toContain('A 20 20')
      // Two M segments — outer and inner.
      expect((d.match(/M /g) || []).length).toBe(2)
    })
  })

  describe('scaleCost / COST_PERIODS / COST_PERIOD_FACTORS', () => {
    it('exposes the four periods in display order', () => {
      expect([...COST_PERIODS]).toEqual(['hour', 'day', 'month', 'year'])
    })

    it('uses the legacy ratios (730 h/mo, 30 d/mo, 12 mo/y)', () => {
      expect(COST_PERIOD_FACTORS.hour).toBeCloseTo(1 / 730)
      expect(COST_PERIOD_FACTORS.day).toBeCloseTo(1 / 30)
      expect(COST_PERIOD_FACTORS.month).toBe(1)
      expect(COST_PERIOD_FACTORS.year).toBe(12)
    })

    it('returns null/undefined cost unchanged', () => {
      expect(scaleCost(null, 'hour')).toBeNull()
      expect(scaleCost(undefined, 'year')).toBeUndefined()
    })

    it('scales a plain number cost', () => {
      expect(scaleCost(100, 'month')).toBe(100)
      expect(scaleCost(120, 'year')).toBe(1440)
      expect(scaleCost(30, 'day')).toBe(1)
      expect(scaleCost(730, 'hour')).toBeCloseTo(1)
    })

    it('scales min/max of a range and preserves other fields', () => {
      const scaled = scaleCost({ min: 100, max: 200, unbound: true }, 'year')
      expect(scaled.min).toBe(1200)
      expect(scaled.max).toBe(2400)
      expect(scaled.unbound).toBe(true)
    })

    it('passes through null min/max in a range', () => {
      const scaled = scaleCost({ min: null, max: 100, unbound: false }, 'year')
      expect(scaled.min).toBeNull()
      expect(scaled.max).toBe(1200)
      expect(scaled.unbound).toBe(false)
    })

    it('falls back to monthly factor for unknown periods', () => {
      expect(scaleCost(42, 'decade')).toBe(42)
    })

    it('does not mutate the input', () => {
      const input = { min: 10, max: 20 }
      const before = { ...input }
      scaleCost(input, 'year')
      expect(input).toEqual(before)
    })
  })

  describe('rowMatches', () => {
    const sample = {
      id: 42,
      name: 'powerbi gateway',
      description: 'Reporting bridge',
      os: 'LINUX',
      price: {
        type: { name: 't3a.2xlarge', code: 't3a.2xlarge' },
        location: { name: 'eu-west-3' },
      },
    }

    it('returns true for an empty/null/undefined query', () => {
      expect(rowMatches(sample, '')).toBe(true)
      expect(rowMatches(sample, null)).toBe(true)
      expect(rowMatches(sample, undefined)).toBe(true)
    })

    it('returns false when the row itself is missing', () => {
      expect(rowMatches(null, 'foo')).toBe(false)
      expect(rowMatches(undefined, 'foo')).toBe(false)
    })

    it('matches the name field, case-insensitive', () => {
      expect(rowMatches(sample, 'GATEWAY')).toBe(true)
      expect(rowMatches(sample, 'power')).toBe(true)
    })

    it('matches the description', () => {
      expect(rowMatches(sample, 'reporting')).toBe(true)
    })

    it('matches OS / engine / level (top-level or via price)', () => {
      expect(rowMatches(sample, 'linux')).toBe(true)
      expect(rowMatches({ price: { engine: 'POSTGRESQL' } }, 'postgres')).toBe(true)
      expect(rowMatches({ level: 'Enterprise' }, 'ENTER')).toBe(true)
    })

    it('matches the price type name and code', () => {
      expect(rowMatches(sample, 't3a')).toBe(true)
    })

    it('matches the location name (top-level or via price)', () => {
      expect(rowMatches(sample, 'eu-west')).toBe(true)
      expect(rowMatches({ location: { name: 'us-east-1' } }, 'us-east')).toBe(true)
    })

    it('matches the row id as a string', () => {
      expect(rowMatches(sample, '42')).toBe(true)
    })

    it('returns false when nothing matches', () => {
      expect(rowMatches(sample, 'mysql')).toBe(false)
    })

    it('tolerates rows missing many fields', () => {
      const sparse = { name: 'minimal' }
      expect(rowMatches(sparse, 'min')).toBe(true)
      expect(rowMatches(sparse, 'something')).toBe(false)
    })

    it('treats a numeric query as a string', () => {
      expect(rowMatches({ id: 99, name: 'x' }, 99)).toBe(true)
    })
  })

  describe('maxOfField', () => {
    it('returns 0 for an empty / null / undefined list', () => {
      expect(maxOfField([], (r) => r.cpu)).toBe(0)
      expect(maxOfField(null, (r) => r.cpu)).toBe(0)
      expect(maxOfField(undefined, (r) => r.cpu)).toBe(0)
    })

    it('returns the largest accessor result', () => {
      const rows = [{ cpu: 2 }, { cpu: 8 }, { cpu: 4 }]
      expect(maxOfField(rows, (r) => r.cpu)).toBe(8)
    })

    it('skips null/undefined rows', () => {
      const rows = [{ cpu: 1 }, null, { cpu: 5 }, undefined]
      expect(maxOfField(rows, (r) => r.cpu)).toBe(5)
    })

    it('coerces non-numeric accessor returns to 0', () => {
      const rows = [{ cpu: '4' }, { cpu: 'abc' }, { cpu: 6 }]
      expect(maxOfField(rows, (r) => r.cpu)).toBe(6)
    })

    it('respects the accessor function (drill through nested shapes)', () => {
      const rows = [
        { price: { type: { ram: 1024 } } },
        { price: { type: { ram: 8192 } } },
        { ram: 4096 },
      ]
      expect(maxOfField(rows, (r) => r.ram ?? r.price?.type?.ram)).toBe(8192)
    })

    it('returns 0 when every row contributes 0', () => {
      expect(maxOfField([{ cpu: 0 }, {}, { cpu: null }], (r) => r.cpu)).toBe(0)
    })
  })

  describe('sumCostRange', () => {
    it('returns a zero range for an empty / non-array input', () => {
      expect(sumCostRange([])).toEqual({ min: 0, max: 0 })
      expect(sumCostRange(null)).toEqual({ min: 0, max: 0 })
      expect(sumCostRange(undefined)).toEqual({ min: 0, max: 0 })
    })

    it('sums cost into min and maxCost into max', () => {
      const rows = [{ cost: 10, maxCost: 15 }, { cost: 5, maxCost: 8 }]
      expect(sumCostRange(rows)).toEqual({ min: 15, max: 23 })
    })

    it('falls back to cost when maxCost is missing', () => {
      const rows = [{ cost: 10 }, { cost: 5, maxCost: 20 }]
      expect(sumCostRange(rows)).toEqual({ min: 15, max: 30 })
    })

    it('skips null rows and coerces non-numeric costs to 0', () => {
      const rows = [{ cost: 10, maxCost: 12 }, null, { cost: 'abc' }, { cost: 4 }]
      expect(sumCostRange(rows)).toEqual({ min: 14, max: 16 })
    })
  })

  describe('TAB_TYPES', () => {
    it('exposes the 6 quote resource types in legacy order', () => {
      expect(TAB_TYPES.map((t) => t.key)).toEqual([
        'instance', 'database', 'container', 'function', 'storage', 'support',
      ])
    })
    it('every entry carries icon/listField/color', () => {
      for (const t of TAB_TYPES) {
        expect(t.icon).toMatch(/^mdi-/)
        expect(t.listField).toMatch(/s$/)
        // Theme-token expression (no hard-coded hex) so the donut and
        // legend follow the active Vuetify theme.
        expect(t.color).toMatch(/^rgb\(var\(--v-theme-[a-z-]+\)\)$/)
      }
    })
  })
})

describe('nextName', () => {
  it('appends -1 when there is no trailing number', () => {
    expect(nextName('web')).toBe('web-1')
    expect(nextName('db-node')).toBe('db-node-1')
    expect(nextName('')).toBe('-1')
  })

  it('increments an existing trailing -<number>', () => {
    expect(nextName('web-1')).toBe('web-2')
    expect(nextName('db-9')).toBe('db-10')
    expect(nextName('a-b-3')).toBe('a-b-4')
    expect(nextName('web-007')).toBe('web-8')
  })
})
