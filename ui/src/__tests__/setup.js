// Vitest global setup for the plugin-id-ldap UI test suite.
//
// The plugin's tests don't mount Vuetify components today — they just
// exercise the plugin contract (manifest, feature dispatcher, VNode
// shape). The stubs below match the host's setup.js so any future
// component-mount test (LdapXyzView.vue, …) works without extra wiring.
import { vi, beforeEach } from 'vitest'

globalThis.fetch = vi.fn()

Object.defineProperty(document, 'title', {
  writable: true,
  value: '',
})

const storage = new Map()
Object.defineProperty(globalThis, 'localStorage', {
  value: {
    getItem: (k) => (storage.has(k) ? storage.get(k) : null),
    setItem: (k, v) => { storage.set(k, String(v)) },
    removeItem: (k) => { storage.delete(k) },
    clear: () => { storage.clear() },
    key: (i) => Array.from(storage.keys())[i] ?? null,
    get length() { return storage.size },
  },
  writable: true,
})
beforeEach(() => { storage.clear() })

if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}
if (typeof globalThis.IntersectionObserver === 'undefined') {
  globalThis.IntersectionObserver = class {
    constructor() {}
    observe() {}
    unobserve() {}
    disconnect() {}
    takeRecords() { return [] }
  }
}
if (typeof window !== 'undefined' && !window.visualViewport) {
  Object.defineProperty(window, 'visualViewport', {
    writable: true,
    value: {
      width: 1024,
      height: 768,
      offsetLeft: 0,
      offsetTop: 0,
      pageLeft: 0,
      pageTop: 0,
      scale: 1,
      addEventListener() {},
      removeEventListener() {},
      dispatchEvent() { return true },
    },
  })
}
