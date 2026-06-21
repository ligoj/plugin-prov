/*
 * Verifies plugin-prov contributes its catalog / currency / terraform
 * screens to the host's shared Administration menu via the new
 * `renderAdmin` render-delegation feature.
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useI18nStore } from '@ligoj/host'
import provDef from '../index.js'

beforeEach(() => { setActivePinia(createPinia()) })

describe('plugin-prov renderAdmin contribution', () => {
  it('exposes renderAdmin as a feature', () => {
    provDef.install({ router: { addRoute() {} } })
    const out = provDef.feature('renderAdmin')
    expect(Array.isArray(out)).toBe(true)
    expect(out).toHaveLength(3)
    for (const node of out) expect(node.__v_isVNode).toBe(true)
  })

  it('links to the catalog / currency / terraform routes', () => {
    provDef.install({ router: { addRoute() {} } })
    const routes = provDef.feature('renderAdmin').map((n) => n.props?.to)
    expect(routes).toContain('/prov/catalog')
    expect(routes).toContain('/prov/currency')
    expect(routes).toContain('/prov/terraform')
  })

  it('localizes the entry titles', () => {
    provDef.install({ router: { addRoute() {} } })
    const i18n = useI18nStore()
    const titles = provDef.feature('renderAdmin').map((n) => n.props?.title)
    expect(titles).toContain(i18n.t('catalog.title'))
    expect(titles).toContain(i18n.t('currency.title'))
    expect(titles).toContain(i18n.t('terraform.title'))
  })
})
