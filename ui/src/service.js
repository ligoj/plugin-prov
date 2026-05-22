const REST = '/rest/'

/**
 * Service surface for the "prov" plugin. Exposes the small set of
 * cross-plugin actions other plugins / the host may call via
 * `callFeature('prov', action, …)`, plus their raw functions for
 * direct ES imports.
 *
 * Most of the legacy `service/prov/prov.js` is UI orchestration that
 * belongs in the views — only the operations another plugin would
 * reasonably reach for live here.
 */
const service = {
  /**
   * Triggers a catalog update for a given provider node.
   * `force` switches to the "full" update (prices + types) instead of
   * the standard prices-only refresh.
   */
  async requestCatalogUpdate(nodeId, { force = false } = {}) {
    const resp = await fetch(
      `${REST}service/prov/catalog/${encodeURIComponent(nodeId)}${force ? '?force=true' : ''}`,
      { method: 'POST', credentials: 'include' },
    )
    if (!resp.ok) throw new Error(`Catalog update failed: ${resp.status}`)
    return true
  },

  /**
   * Polls a long-running provisioning task (catalog update, terraform
   * apply, …) until completion. Returns the cleanup handle so callers
   * can cancel.
   */
  scheduleTaskPoll(url, onPartial, onDone, intervalMs = 1500) {
    const handle = setInterval(
      () => service._poll(url, onPartial, onDone, handle),
      intervalMs,
    )
    return handle
  },

  async _poll(url, onPartial, onDone, handle) {
    try {
      const resp = await fetch(REST + url, { credentials: 'include' })
      if (!resp.ok) return
      const data = await resp.json()
      onPartial?.(data)
      if (data.end || data.finished) {
        clearInterval(handle)
        onDone?.(data)
      }
    } catch (err) {
      console.error('[plugin:prov] task poll error', err)
    }
  },
}

export default service
