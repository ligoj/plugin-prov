// Database engine presentation — labels, tooltip and icon-key resolution.
// Kept beside EngineIcon.vue so engine presentation lives in one place.
export const ENGINE_LABELS = {
  MYSQL: 'MySQL',
  POSTGRESQL: 'PostgreSQL',
  ORACLE: 'Oracle Database',
  MARIADB: 'MariaDB',
  SQL_SERVER: 'Microsoft SQL Server',
  AURORA: 'Amazon Aurora',
}

/**
 * Icon-file key for an engine, resolving compound names too — the backend
 * emits variants like "AURORA MYSQL" / "AURORA POSTGRESQL". Aurora has no
 * dedicated glyph, so it borrows the compatible engine's (MySQL by default).
 */
export function engineKey(engine) {
  const s = String(engine || '').toLowerCase()
  if (!s) return ''
  if (s.includes('mariadb')) return 'mariadb'
  if (s.includes('postgres')) return 'postgresql' // before the generic "sql" test
  if (s.includes('mysql')) return 'mysql'
  if (s.includes('aurora')) return 'mysql'
  if (s.includes('oracle')) return 'oracle'
  if (s.includes('sql')) return 'sqlserver' // SQL_SERVER, "SQL Server", MSSQL
  return s.replace(/[\s_-]+/g, '')
}

/** Full display name for an engine code, falling back to the code itself. */
export function engineLabel(code) {
  const key = String(code || '').toUpperCase()
  return ENGINE_LABELS[key] || String(code || '')
}

/** Tooltip text combining full name and code, e.g. "MySQL (MYSQL)". */
export function engineTooltip(code) {
  const key = String(code || '').toUpperCase()
  const name = ENGINE_LABELS[key]
  return name ? `${name} (${key})` : key
}
