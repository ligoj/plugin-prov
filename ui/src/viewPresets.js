/**
 * Saved views (feature 13 phase 3) — pure storage helpers.
 *
 * A view is a named capture of the quote screen state:
 * `{ name, search, filters, filterMode, tab, viewMode, groupBy, tagKey,
 *    columns: { <tabKey>: <raw column-storage payload> } }`.
 * Views are persisted per subscription in localStorage (v1 of the spec);
 * these helpers only (de)serialize and edit the list — applying a view is the
 * view's job.
 */

/** localStorage key holding the views of one subscription. */
export function viewsStorageKey(subscriptionId) {
  return `ligoj-prov-quote-views-${subscriptionId}`
}

/** Parse the stored list defensively — a broken payload yields []. */
export function readViews(storage, key) {
  try {
    const raw = storage?.getItem?.(key)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((v) => v && typeof v.name === 'string' && v.name) : []
  } catch {
    return []
  }
}

/** Persist the list (silently no-op without storage). */
export function writeViews(storage, key, views) {
  try {
    storage?.setItem?.(key, JSON.stringify(views || []))
  } catch {
    // Quota / privacy mode: the views just don't persist.
  }
}

/** Insert or replace a view by name (case-sensitive), returning a new list. */
export function upsertView(views, view) {
  const out = (views || []).filter((v) => v.name !== view.name)
  out.push(view)
  return out.sort((a, b) => a.name.localeCompare(b.name))
}

/** Remove a view by name, returning a new list. */
export function removeView(views, name) {
  return (views || []).filter((v) => v.name !== name)
}
