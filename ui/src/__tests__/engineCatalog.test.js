import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EngineIcon from '../views/EngineIcon.vue'
import { engineKey, engineLabel, engineTooltip } from '../engineCatalog.js'

const mountIcon = (engine) => mount(EngineIcon, { props: { engine } })

describe('engineCatalog', () => {
  it('resolves the icon key, incl. compound Aurora variants', () => {
    expect(engineKey('MYSQL')).toBe('mysql')
    expect(engineKey('POSTGRESQL')).toBe('postgresql')
    expect(engineKey('MARIADB')).toBe('mariadb')
    expect(engineKey('ORACLE')).toBe('oracle')
    expect(engineKey('SQL_SERVER')).toBe('sqlserver')
    expect(engineKey('AURORA MYSQL')).toBe('mysql')
    expect(engineKey('AURORA POSTGRESQL')).toBe('postgresql') // postgres wins over the sql fallback
    expect(engineKey('AURORA')).toBe('mysql')
    expect(engineKey('DB2')).toBe('db2')
    expect(engineKey('')).toBe('')
  })

  it('labels and tooltips combine name + code', () => {
    expect(engineLabel('MYSQL')).toBe('MySQL')
    expect(engineLabel('sql_server')).toBe('Microsoft SQL Server')
    expect(engineLabel('DB2')).toBe('IBM Db2')
    expect(engineLabel('UNKNOWN_DB')).toBe('UNKNOWN_DB')
    expect(engineTooltip('POSTGRESQL')).toBe('PostgreSQL (POSTGRESQL)')
  })
})

describe('<EngineIcon>', () => {
  it('renders an inline SVG for known engines', () => {
    expect(mountIcon('MYSQL').find('svg').exists()).toBe(true)
    expect(mountIcon('postgresql').find('svg').exists()).toBe(true) // case-insensitive
    expect(mountIcon('SQL_SERVER').find('svg').exists()).toBe(true)
    expect(mountIcon('MYSQL').attributes('aria-label')).toBe('MYSQL')
  })

  it('resolves an Aurora variant to its compatible glyph', () => {
    expect(mountIcon('AURORA POSTGRESQL').find('svg').exists()).toBe(true)
  })

  it('renders the local DB2 glyph', () => {
    expect(mountIcon('DB2').find('svg').exists()).toBe(true)
    expect(mountIcon('db2').find('svg').exists()).toBe(true) // case-insensitive
  })

  it('renders nothing for an unknown engine', () => {
    expect(mountIcon('COCKROACHDB').find('.engine-icon').exists()).toBe(false)
    expect(mountIcon('').find('.engine-icon').exists()).toBe(false)
  })
})
