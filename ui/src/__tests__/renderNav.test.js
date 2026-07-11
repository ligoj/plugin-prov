/*
 * Verifies plugin-prov contributes its catalog / currency / terraform
 * screens to the host's shared Administration menu via the declarative
 * `renderNav` feature — a `{ menu, children }` insert consumed by the host's
 * mergeNav engine (replaces the former VNode-based `renderAdmin`).
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useI18nStore } from '@ligoj/host'
import provDef from '../index.js'

beforeEach(() => { setActivePinia(createPinia()) })

describe('plugin-prov renderNav contribution', () => {
  it('inserts three entries into the Administration (nav.system) menu', () => {
    provDef.install({ router: { addRoute() {} } })
    const out = provDef.feature('renderNav')
    expect(out.menu).toBe('nav.system')
    expect(Array.isArray(out.children)).toBe(true)
    expect(out.children).toHaveLength(3)
  })

  it('links to the catalog / currency / terraform routes', () => {
    provDef.install({ router: { addRoute() {} } })
    const routes = provDef.feature('renderNav').children.map((c) => c.route)
    expect(routes).toContain('/prov/catalog')
    expect(routes).toContain('/prov/currency')
    expect(routes).toContain('/prov/terraform')
  })

  it('localizes the entry titles', () => {
    provDef.install({ router: { addRoute() {} } })
    const i18n = useI18nStore()
    const titles = provDef.feature('renderNav').children.map((c) => c.label)
    expect(titles).toContain(i18n.t('catalog.title'))
    expect(titles).toContain(i18n.t('currency.title'))
    expect(titles).toContain(i18n.t('terraform.title'))
  })

  it('labels the block with an ownership divider on the first entry only', () => {
    provDef.install({ router: { addRoute() {} } })
    const [first, ...rest] = provDef.feature('renderNav').children
    expect(first.divider).toBe('Provisioning')
    for (const c of rest) expect(c.divider).toBeUndefined()
  })
})
