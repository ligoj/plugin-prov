// Traductions locales du plugin, fusionnées dans le store i18n du host à
// l'installation. Les clefs restent à plat (notation pointée) pour suivre
// la convention du host (vue-i18n configuré sans traversal de chemin).
export default {
  // ---- Coquille du plugin & navigation ----
  'prov.title': 'Provisioning',
  'prov.shellHint': 'Utilisez la barre latérale pour gérer les devises, catalogues, Terraform et le réseau.',
  'prov.catalog': 'Catalogue',
  'prov.currency': 'Devise',
  'prov.terraform': 'Terraform',
  'prov.network': 'Réseau',
  'prov.quote': 'Devis',

  // ---- CRUD Devise (chemin heureux) ----
  'currency.title': 'Devise',
  'currency.new': 'Nouvelle devise',
  'currency.edit': 'Modifier la devise',
  'currency.name': 'Nom',
  'currency.description': 'Description',
  'currency.unit': 'Unité',
  'currency.rate': 'Ratio',
  'currency.quotes': 'Nombre de devis utilisant cette devise',
  'currency.editingUsed': 'Vous modifiez une devise actuellement utilisée par au moins un devis. Mettre à jour les valeurs implique un coût visible changé pour les utilisateurs au niveau graphique',
  'currency.deleteTitle': 'Supprimer la devise',
  'currency.deleteConfirmBefore': 'Êtes-vous certain de supprimer ',
  'currency.deleteConfirmAfter': ' ?',
  'currency.created': 'Devise « {name} » créée',
  'currency.updated': 'Devise « {name} » mise à jour',
  'currency.deleted': 'Devise « {name} » supprimée',

  // ---- Catalogue (étiquettes pour le stub) ----
  'catalog.title': 'Catalogue',
  'catalog.intro': 'Gérez les catalogues, consultez les prix configurés, et demandez une mise à jour lorsque le fournisseur le supporte.',
  'catalog.lastSuccess': 'Date',
  'catalog.quotes': 'Nombre de devis utilisant ce catalogue',
  'catalog.nbLocations': 'Nombre de localisations disponibles pour ce fournisseur',
  'catalog.locationPreferred': 'Localisation préférée',
  'catalog.nbTypes': 'Nombre de types disponibles pour ce fournisseur',
  'catalog.nbPrices': 'Nombre de combinaisons de prix disponibles pour ce fournisseur',
  'catalog.percentCo2Prices': 'Couverture des prix ayant une donnée d\'équivalent CO2',
  'catalog.actions': 'Fonctionnalités disponibles pour ce fournisseur',
  'catalog.status': 'État du catalogue',
  'catalog.updateStandard': 'Mise à jour standard',
  'catalog.updateStandardHelp': 'Mise à jour des prix depuis le catalogue fournisseur',
  'catalog.updateForce': 'Mise à jour complète',
  'catalog.updateForceHelp': 'Mise à jour des prix et des types depuis le catalogue fournisseur. Plus lent que le mode standard.',

  // ---- Terraform / Réseau / Devis (vues stub) ----
  'terraform.title': 'Terraform',
  'terraform.notMigrated': 'La vue Terraform n\'est pas encore portée vers Vue. Utilisez l\'interface héritée pour le moment.',
  'network.title': 'Réseau',
  'network.notMigrated': 'L\'éditeur Réseau n\'est pas encore porté vers Vue. Utilisez l\'interface héritée pour le moment.',
  'quote.title': 'Devis',
  'quote.notMigrated': 'L\'éditeur de devis n\'est pas encore porté vers Vue. Utilisez l\'interface héritée pour le moment.',
}
