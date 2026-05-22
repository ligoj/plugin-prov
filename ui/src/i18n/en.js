// Plugin-local translations merged into the host i18n store at install
// time. Keep keys flat (dot-separated) to match the host's existing
// convention (the host's vue-i18n is configured with
// messageResolver: (obj, path) => obj?.[path] — no dot traversal).
export default {
  // ---- Plugin shell & navigation ----
  'prov.title': 'Provisioning',
  'prov.shellHint': 'Use the sidebar to manage currencies, catalogs, Terraform and network resources.',
  'prov.catalog': 'Catalog',
  'prov.currency': 'Currency',
  'prov.terraform': 'Terraform',
  'prov.network': 'Network',
  'prov.quote': 'Quote',

  // ---- Currency CRUD (happy path) ----
  'currency.title': 'Currency',
  'currency.new': 'New currency',
  'currency.edit': 'Edit currency',
  'currency.name': 'Name',
  'currency.description': 'Description',
  'currency.unit': 'Unit',
  'currency.rate': 'Rate',
  'currency.quotes': 'Number of quotes using this currency',
  'currency.editingUsed': 'You are editing a currency that is being used by at least one quote. Updating values implies an updated cost at UI level for the users',
  'currency.deleteTitle': 'Delete currency',
  'currency.deleteConfirmBefore': 'Are you sure you want to delete ',
  'currency.deleteConfirmAfter': '?',
  'currency.created': 'Currency "{name}" created',
  'currency.updated': 'Currency "{name}" updated',
  'currency.deleted': 'Currency "{name}" deleted',

  // ---- Catalog (placeholder for stub) ----
  'catalog.title': 'Catalog',
  'catalog.intro': 'Manage catalogs, see configured prices, and request an update when supported by the provider.',
  'catalog.lastSuccess': 'Date',
  'catalog.quotes': 'Number of quotes using this catalog',
  'catalog.nbLocations': 'Number of available locations for this provider',
  'catalog.locationPreferred': 'Preferred location',
  'catalog.nbTypes': 'Number of available types for this provider',
  'catalog.nbPrices': 'Number of available price combinations for this provider',
  'catalog.percentCo2Prices': 'CO2 equivalent data coverage of prices',
  'catalog.actions': 'Enabled features for this provider',
  'catalog.status': 'Catalog status',
  'catalog.updateStandard': 'Standard update',
  'catalog.updateStandardHelp': 'Update the prices from the provider pricing list',
  'catalog.updateForce': 'Full update',
  'catalog.updateForceHelp': 'Update the prices and the type configurations from the provider pricing list. Slower than standard mode.',

  // ---- Terraform / Network / Quote (stub views) ----
  'terraform.title': 'Terraform',
  'terraform.notMigrated': 'The Terraform view is not yet ported to Vue. Use the legacy interface in the meantime.',
  'network.title': 'Network',
  'network.notMigrated': 'The Network editor is not yet ported to Vue. Use the legacy interface in the meantime.',
  'quote.title': 'Quote',
  'quote.notMigrated': 'The quote editor is not yet ported to Vue. Use the legacy interface in the meantime.',
}
