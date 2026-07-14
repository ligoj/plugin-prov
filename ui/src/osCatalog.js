// Human-readable names for the VmOs enum codes (proper nouns, locale-agnostic).
// Kept beside OsIcon.vue so OS presentation lives in one place.
export const OS_LABELS = {
  LINUX: 'Linux',
  WINDOWS: 'Microsoft Windows',
  SUSE: 'SUSE',
  RHEL: 'Red Hat Enterprise Linux',
  CENTOS: 'CentOS',
  DEBIAN: 'Debian',
  FEDORA: 'Fedora',
  UBUNTU: 'Ubuntu',
  FREEBSD: 'FreeBSD',
  PANOS: 'PAN-OS',
  ORACLE: 'Oracle Linux',
}

/** Full display name for an OS code, falling back to the code itself. */
export function osLabel(code) {
  const key = String(code || '').toUpperCase()
  return OS_LABELS[key] || String(code || '')
}

/** Tooltip text combining full name and code, e.g. "Red Hat Enterprise Linux (RHEL)". */
export function osTooltip(code) {
  const key = String(code || '').toUpperCase()
  const name = OS_LABELS[key]
  return name ? `${name} (${key})` : key
}
