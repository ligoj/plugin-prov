/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define({
	'global-configuration': 'Configuration globale',
	'instance-import-message': 'Importer des instances depuis un fichier CSV, <code> ;</code> en séparateur',
	'instance-import-sample': 'Exemple',
	'quote-assumptions': 'Hypothèses',
	'quote-ram-adjust': 'Ratio de mémoire appliqué qur la valeur demandée. En deça de 100%,  la valeur de la recherche sera inférieure à la valeur actuelle. Au delà de 100%, la valeur recherchée sera supérieur à la valeur réelle.',
	'service:prov:reservation-mode-reserved': 'Réservé',
	'service:prov:reservation-mode-max': 'Maximum utilisé',
	'service:prov:reservation-mode-help': 'Mode de recherche basé sur les valeurs maximales ou réservées de CPU/RAM',
	'service:prov': 'Provisionnement',
	'service:prov:nb': 'Nb',
	'service:prov:date': 'Date',
	'service:prov:manage': 'Gérer',
	'service:prov:currency': 'Devise',
	'service:prov:default': 'Défaut',
	'service:prov:no-requirement': 'Pas d\'exigence',
	'service:prov:network': 'Réseau',
	'service:prov:instances-block': 'Instances',
	'service:prov:storages-block': 'Stockages',
	'service:prov:support-block': 'Supports',
	'service:prov:support-access-all': 'Tous',
	'service:prov:support-access-technical': 'Technique',
	'service:prov:support-access-billing': 'Facturation',
	'service:prov:support-api': 'API',
	'service:prov:support-api-help': 'Qui peut accéder au support avec des API',
	'service:prov:support-phone': 'Téléphone',
	'service:prov:support-phone-help': 'Qui peut accéder au support par téléphone',
	'service:prov:support-email': 'Mail',
	'service:prov:support-email-help': 'Qui peut accéder au support par e-mail',
	'service:prov:support-chat': 'Chat',
	'service:prov:support-chat-help': 'Qui peut accéder au support par chat',
	'service:prov:support-level': 'Niveau',
	'service:prov:support-level-help': 'Niveau de support',
	'service:prov:support-level-low': 'Conseil générique',
	'service:prov:support-level-medium': 'Conseil contextuel',
	'service:prov:support-level-good': 'Revue contextuelle',
	'service:prov:support-seats': 'Sièges',
	'service:prov:support-seats-help': 'Sièges requis. Si indéfini, serr illimité',
	'service:prov:support-type': 'Type',
	'service:prov:support-type-help': 'Type de support',
	'service:prov:support-commitment': 'Engagement',
	'service:prov:os': 'OS',
	'service:prov:os-help': 'Système d\'exploitation préinstallé pour cette instance. Le prix de l\'instance inclue la licence correspondante, et est souvent en relation avec la quantité de CPU',
	'service:prov:cpu': 'CPU',
	'service:prov:cpu-help': 'Le CPU demandé. La meilleure instance correspondante à cette exigence peut inclure plus que cette quantité. Il est alors important de bien équilibrer la ressource (CPU/RAM) pour limiter cette perte.<div class=\'text-left\'><i class=\'fas fa-bolt fa-fw\'></i> CPU variable, dispose de crédit turbo.<br><i class=\'fas fa-minus fa-fw\'></i> CPU constant délivre une puissance continue.</div>',
	'service:prov:constant-null': 'Importe',
	'service:prov:constant-false': 'Variable',
	'service:prov:constant-true': 'Constant',
	'service:prov:physical-null': 'Importe',
	'service:prov:physical-false': 'Virtuel',
	'service:prov:physical-true': 'Physique',
	'service:prov:physical-help': 'Contrainte physique (métal) or virtuelle',
	'service:prov:ram': 'RAM',
	'service:prov:ram-mega': 'Mo',
	'service:prov:ram-giga': 'Go',
	'service:prov:ram-tera': 'To',
	'service:prov:ram-help': 'La mémoire demandée. La meilleure instance correspondante à cette exigence peut inclure plus que cette quantité. Il est alors important de bien équilibrer la ressource (CPU/RAM) pour limiter cette perte',
	'service:prov:instance-quantity': 'Quantité',
	'service:prov:instance-quantity-to': 'à',
	'service:prov:instance-quantity-help': 'Quantité variable pour cette instance. Lorsque la quantité maximale n\'est pas renseignée, les coûts ne sont plus bornés. Lorsque les quantités maximale et minimale sont différentes, auto-scale est activée automatiquement',
	'service:prov:instance-auto-scale-help': 'Le mode auto-scale est activé dès lors que les quantités maximale et minimale sont différentes',
	'service:prov:instance': 'Instance',
	'service:prov:instance-help': 'La meilleur instance répondant aux ressources demandées',
	'service:prov:instance-custom': 'Instance personnalisée',
	'service:prov:instance-custom-help': 'Type de VM avec des ressources personnalisées',
	'service:prov:instance-cleared': 'Toutes les instances et leurs stockages attachés ont été supprimées',
	'service:prov:instance-choice': 'La meilleure instance du fournisseur sera choisie en fonction des exigences exprimées',
	'service:prov:instance-type': 'Type',
	'service:prov:instance-type-help': 'Type d\'instance du fournisseur',
	'service:prov:instance-ephemeral': 'Ephémère',
	'service:prov:instance-ephemeral-help': 'Un comportement éphémère induit une durée de vie incertaine des instance, préemptible suivant des condition de disponibilité ou de coût variable.',
	'service:prov:instance-max-variable-cost': 'Coût max',
	'service:prov:instance-max-variable-cost-help': 'Coût maximum optionnel où cette instance sera valide. Lorsque non définie, il n\'y a pas de limite. Lorsque ce seuil est atteint, l\'instance serait supprimée.',
	'service:prov:internet': 'Accès Internet',
	'service:prov:internet-help': 'Option d\'accès Internet. Un accès public implique une instance frontale Internet.',
	'service:prov:license': 'Licence',
	'service:prov:license-included': 'Incluse',
	'service:prov:license-byol': 'BYOL - License Mobility',
	'service:prov:license-help': 'L\'OS et les licences logicielles sont incluses dans la prix pour l\'option "Incluse".<br>Pour le moment WINDOWS uniquement.<br>Si indéfinie, le mode de licence par défaut est utilisé.',
	'service:prov:term': 'Utilisation',
	'service:prov:term-help': 'Condition de prix, période et contrat. En général, plus le contrat est court et plus il est cher',
	'service:prov:merge-upload': 'Mode de fusion',
	'service:prov:merge-upload-help': 'Le mode de fusion détermine la façon dont les entrées sont insérées',
	'service:prov:merge-upload-update': 'Mettre à jour',
	'service:prov:merge-upload-update-help': 'Les entrées existantes seront écrasées par mise à jour pour les valeurs non "null" du fichier. Aucun suppressions.',
	'service:prov:merge-upload-keep': 'Garger',
	'service:prov:merge-upload-keep-help': 'Les entrées existantes ne sont pas modifiées, et les entrées similaires sont ajoutées avec un suffixe pour gérer les conflits',
	'service:prov:merge-upload-insert': 'Insérer',
	'service:prov:merge-upload-insert-help': 'Les entrées dupliquées sont rejetées en erreur',
	'service:prov:memory-unit-upload': 'Unité mémoire',
	'service:prov:memory-unit-upload-help': 'Unité mémoire pour la RAM dans le fichier importé',
	'service:prov:container': 'Conteneur',
	'service:prov:container-type': 'Type',
	'service:prov:container-type-help': 'Type d\'instance de conteneur du fournisseur',
	'service:prov:container-quantity': 'Quantité',
	'service:prov:container-quantity-help': 'Quantité d\'instances de conteneur',
	'service:prov:container-size': 'Taille',
	'service:prov:containers-block': 'Conteneurs',
	'service:prov:function': 'Fonction',
	'service:prov:function-requests-help': 'Nombre d\'invocations (en millions)de cette fonctin durant un mois',
	'service:prov:function-duration-help': 'Durée maximale (en milli secondes) de cette fonction',
	'service:prov:function-concurrency-help': 'Concurence moyenne de cette fonction. Difficile à renseigner, et devrait correspondre au percentile p99 et non pas à une réelle moyenne. Peut être inférieure à 1',
	'service:prov:function-requests': 'Requêtes',
	'service:prov:function-millions': 'Millions',
	'service:prov:function-milliseconds': 'Millisecondes',
	'service:prov:function-duration': 'Durée',
	'service:prov:function-concurrency': 'Concurence',
	'service:prov:functions-block': 'Fonctions',
	'service:prov:database': 'Bases de données',
	'service:prov:database-type': 'Type',
	'service:prov:database-type-help': 'Type d\'instance de base de données du fournisseur',
	'service:prov:database-quantity': 'Quantité',
	'service:prov:database-quantity-help': 'Quantité d\'instances de base de données',
	'service:prov:database-engine': 'Moteur',
	'service:prov:database-engine-help': 'Moteur de base de données: MySQL,...',
	'service:prov:database-edition': 'Édition',
	'service:prov:database-edition-help': 'Edition du moteur de cette base de données',
	'service:prov:database-size': 'Taille',
	'service:prov:databases-block': 'Bases de données',
	'service:prov:ramAdjustedRate-failed': 'Le ratio de RAM {{this}}% rend certaines instances incompatibles',
	'service:prov:reservedMode-failed': 'Le mode de réservation {{this}} rend certaines instances incompatibles',
	'service:prov:processor-failed': 'Le processeur de type {{this}} rend certaines instances incompatibles',
	'service:prov:processor-default': 'Tous',
	'service:prov:processor': 'Processeur',
	'service:prov:processor-help': 'Type de processeur requis. Seuls les types d\'instance correspondanr seront utilisés: "Intel", "AMD",...<br>Non sensible à la casse et un "contient" est utiilisé pour la correspondance.',
	'service:prov:default-failed': 'La propriété "{{name}}" pour valeur "{{value}}" rend certaines instances incompatibles',
	'service:prov:storage': 'Stockage',
	'service:prov:storage-giga': 'Go',
	'service:prov:storage-help': 'Taille du stockage, en Go',
	'service:prov:storage-quantity': 'Quantité',
	'service:prov:storage-quantity-help': 'Nombre de volumes. Lorsque liés à des instances, correspond au nombre d\'instances associées.',
	'service:prov:storage-type': 'Type',
	'service:prov:storage-type-help': 'Type de stockage du fournisseur',
	'service:prov:storage-latency': 'Latence',
	'service:prov:storage-latency-help': 'Latence d\'accès au stockage. Plus elle est faible, meilleure elle est.',
	'service:prov:storage-latency-invalid-help': 'Stockage non lisible ou écrivable directement',
	'service:prov:storage-select': 'Taille du stockage en Go',
	'service:prov:storage-optimized': 'Optimisé',
	'service:prov:storage-optimized-help': 'Ce qui est le plus important for ce stockage',
	'service:prov:storage-optimized-throughput': 'Débit',
	'service:prov:storage-optimized-throughput-help': 'Volume des échanges de données, généralement basé sur du stockage de type HDD',
	'service:prov:storage-optimized-iops': 'IOPS',
	'service:prov:storage-optimized-iops-help': 'I/O par second, généralement basé sur du stockage de type SSD',
	'service:prov:storage-optimized-durability': 'Durabilité',
	'service:prov:storage-optimized-durability-help': 'Durabilité des données plus que la performance',
	'service:prov:storage-instance': 'Attachement',
	'service:prov:storage-instance-help': 'Instance ou base de donnée ce stockage est attaché. Sera supprimée lorsque cette instance le sera, même si leur cycle de vie sont indépendants à l\'exécution',
	'service:prov:storage-size': 'Taille',
	'service:prov:storage-size-help': 'Taille requise. Suivant cette valeur, les types disponibles varient',
	'service:prov:storage-cleared': 'Tous les stockages ont été supprimés',
	'service:prov:no-attached-instance': 'Aucune ressource attachée',
	'service:prov:cannot-attach-instance': 'Non disponible',
	'service:prov:cost': 'Coût',
	'service:prov:cost-help': 'Facturés par mois',
	'service:prov:storage-rate-worst': 'Moindre',
	'service:prov:storage-rate-low': 'Faible',
	'service:prov:storage-rate-medium': 'Moyen',
	'service:prov:storage-rate-good': 'Bon',
	'service:prov:storage-rate-best': 'Meilleur',
	'service:prov:resources': 'Ressources',
	'service:prov:tag-help': 'Tags des ressources',
	'service:prov:total': 'Total',
	'service:prov:total-ram': 'Mémoire totale',
	'service:prov:total-cpu': 'CPU total',
	'service:prov:total-storage': 'Stockage total',
	'service:prov:total-requests': 'Requêtes totales',
	'service:prov:nb-public-access': 'Nombre d\'instances exposées sur Internet',
	'service:prov:nb-instances': 'Nombre d\'instances',
	'service:prov:nb-databases': 'Nombre de bases de données total',
	'service:prov:nb-container': 'Nombre de bases de conteneurs total',
	'service:prov:nb-functions': 'Nombre de fonctions total',
	'service:prov:cost-month': 'Mois',
	'service:prov:efficiency-help': 'Efficacité globale de cette demande : CPU, RAM et stockage',
	'service:prov:support': 'Support',
	'service:prov:support-help': 'Supports activés',
	'service:prov:terraform:download': 'Télécharger le workspace',
	'service:prov:terraform:target': 'Paramètre du fournisseur',
	'service:prov:terraform:execute': 'Exécuter',
	'service:prov:terraform:destroy': 'Détruire',
	'service:prov:terraform:destroy-alert': 'Vous aller détruire toutes les ressource du projet <strong>{{this}}</strong>. Ce n\'est pas une opération réversible.',
	'service:prov:terraform:destroy-confirm': 'Saisissez le nom du projet pour confirmer',
	'service:prov:terraform:started': 'Terraform démarré',
	'service:prov:terraform:cidr': 'CIDR',
	'service:prov:terraform:private-subnets': 'Subnets privés',
	'service:prov:terraform:public-subnets': 'Ssubnets publics',
	'service:prov:terraform:public-key': 'Clé publique',
	'service:prov:terraform:key-name': 'Nom de la clé',
	'service:prov:terraform:status': 'Démarré {{startDate}} par {{author}}{{#if toAdd}}, {{toAdd}} à ajouter{{/if}}{{#if toUpdate}}, {{toUpdate}} à mettre à jour{{/if}}{{#if toReplace}}, {{toReplace}} à remplacer (x2){{/if}}{{#if toDestroy}}, {{toDestroy}} à supprimer{{/if}} {{#if failed}}<i class="text-danger fas fa-exclamation-circle" data-toggle="tooltip" title="Échoué {{endDate}} lors de la commande <strong>{{command}}</strong><br>Voir les logs pour les détails"></i>{{else}}{{#if end}}<i class="text-success fas fa-check-circle" data-toggle="tooltip" title="Réussi {{endDate}}"></i>{{else}}<i class="text-primary fas fa-sync-alt fa-spin" data-toggle="tooltip" title="En cours, commande <strong>{{command}}</strong> ..."></i>{{/if}}{{/if}}',
	'service:prov:terraform:status-generate': 'Génération',
	'service:prov:terraform:status-clean': 'Nettoyage',
	'service:prov:terraform:status-secrets': 'Génération des secrets',
	'service:prov:terraform:status-command': 'Terraform commande <strong>{{this}}</strong>',
	'service:prov:terraform:status-completed': 'Terraform <strong>{{[0]}}</strong>: {{[1]}}/{{[2]}} changements faits',
	'service:prov:terraform:status-completing': 'Terraform <strong>{{[0]}}</strong>: {{[1]}} changements en cours',
	'service:prov:terraform-dashboard': 'Tableau de bord du provider',
	'service:prov:cost-refresh-help': 'Raffraichir (calcul complet) le coût global',
	'service:prov:refresh-needed': 'Le coût global a changé, rechargement des détails ...',
	'service:prov:refresh-no-change': 'Pas de changement de coût',
	'service:prov:location-failed': 'L\'emplacement sélectionné {{this}} ne supporte pas toutes vos exigences',
	'service:prov:location': 'Emplacement',
	'service:prov:location-help': 'Emplacement géographique de cette resource. Les prix dépendent de l\'emplacement sélectionné. Lorsque l\'emplacement n\'est pas défini, celui du devis est utilisé.',
	'service:prov:software-none': 'Aucun',
	'service:prov:software': 'Logiciel',
	'service:prov:software-help': 'Logiciel pré-installé pour cette instance. Le prix horaire inclus les coûts et le contrat est établi entre le provider et l\'éditeur.',
	'service:prov:tags': 'Tags',
	'service:prov:usage-failed': 'L\'usage sélectionné {{this}} ne supporte pas toutes vos exigences',
	'service:prov:usage-null': 'Toujours utilisé',
	'service:prov:usage-help': 'L\'utilisation choisie influencera le terme et le meilleur coût. Les profils disponibles sont au niveau de la souscription. Lorsqu\'il est non défini, le profil d\'utilisation par défaut au niveau de la souscription est utilisé. Lorsqu\'il n\'y a pas de profile d\'utilisation par défaut, le niveau d\'utilisation est à 100% sans engagement (1 mois).',
	'service:prov:usage': 'Profil d\'utilisation',
	'service:prov:usage-upload-help': 'Profil d\'utilisation à associer à chaque instance importée',
	'service:prov:usage-default': 'Profil d\'utilisation par défaut : {{this}}%',
	'service:prov:usage-actual-cost': 'Profil d\'utilisation actuel : {{this}}%',
	'service:prov:usage-partial': 'Utilisation seulement de {{[0]}} sur {{[1]}} disponibles ({{[2]}}%)',
	'service:prov:usage-rate': 'Niveau',
	'service:prov:usage-rate-help': 'Taux dutilisation correspondant à la durée de disponibilité de cette resource. 100% implique toujours disponible.',
	'service:prov:usage-duration': 'Durée',
	'service:prov:usage-duration-help': 'Durée estimée d\'utilisation en mois. Suivant cette valeur, le meilleur terme est déterminé.',
	'service:prov:usage-start': 'Début',
	'service:prov:usage-start-help': 'Délais positif ou négatif en mois de démarrage d\'utilisation.',
	'service:prov:usage-template-full': 'Toujours utilisé',
	'service:prov:budget-failed': 'Le budget sélectionné {{this}} ne supporte pas toutes vos exigences',
	'service:prov:budget-help': 'Le profil de budget choisi influencera le terme et le meilleur coût. Les profils disponibles sont au niveau de la souscription. Lorsqu\'il est non définie, le profil de budget par défaut au niveau de la souscription est utilisée. Lorsqu\'il n\'y a pas de profil de budget par défaut, le cash disponible est considéré comme nul.',
	'service:prov:budget': 'Profil de budget',
	'service:prov:budget-null': 'Pas de cash',
	'service:prov:budget-upload-help': 'Profil de budget à associer à chaque instance importée',
	'service:prov:budget-default': 'Budget par défaut : {{this}}%',
	'service:prov:budget-initialCost': 'Cash disponible',
	'service:prov:budget-initialCost-help': 'Cash disponible pour ce budget',
	'service:prov:export:instances': 'Exporter',
	'service:prov:export:instances:inline': 'Allignées, compatible import',
	'service:prov:export:instances:split': 'Une ligne par resource',
	'service:prov:export:full:json': 'Complet',
	'service:prov:created': '{{#if more}}{{count}} créés: {{/if}}{{sample}}{{#if more}}, ... (+{{more}}){{else}} créé{{/if}}',
	'service:prov:deleted': '{{#if more}}{{count}} supprimés: {{/if}}{{sample}}{{#if more}}, ... (+{{more}}){{else}} supprimé{{/if}}',
	'service:prov:updated': '{{#if more}}{{count}} mis à jours: {{/if}}{{sample}}{{#if more}}, ... (+{{more}}){{else}} mis à jours{{/if}}',
	'csv-headers-included': 'CSV contient les entêtes',
	'csv-headers': 'Entêtes',
	'csv-headers-included-help': 'Lorsque les entêtes sont en première ligne du fichier',
	'csv-create-missing-usage': 'Créer profil d\'utilisation',
	'csv-create-missing-usage-help': 'Créer un profil d\'utilisation non existant au lieu de provoquer une erreur',
	'csv-separator': 'Séparateur',
	'csv-separator-help': 'Caractère de séparation des champs du CSV',
	'error': {
		'service:prov-no-catalog': 'Il n\'y a pas encore de catalogue pour le provider "{{[0]}}" ({{[1]}}). il peut être importé. <a class="btn btn-success btn-raised" href="#/prov/catalog">Importer ...</button>',
		'no-match-instance': 'Mise à jour échouées, au moins la resource ({{resource}}) ne supporte pas toutes les exigences',
		'not-compatible-storage-instance': 'Le stockage {{[0]}} ne peut être attaché à la ressource {{[1]}}'
	},
	'm49': {
		'2': 'Afrique',
		'5': 'Amérique du Sud',
		'9': 'Océanie',
		'18': 'Afrique australe',
		'19': 'Amériques',
		'21': 'Amérique septentrionale',
		'30': 'Asie orientale',
		'34': 'Asie du Sud',
		'35': 'Asie du Sud-Est',
		'36': 'Australie',
		'39': 'Europe méridionale',
		'40': 'Autriche',
		'53': 'Australie et Nouvelle-Zélande',
		'56': 'Belgique',
		'76': 'Brésil',
		'100': 'Bulgarie',
		'124': 'Canada',
		'142': 'Asie',
		'143': 'Asie centrale',
		'145': 'Asie occidentale',
		'150': 'Europe',
		'151': 'Europe orientale',
		'154': 'Europe septentrionale',
		'155': 'Europe occidentale',
		'156': 'Chine',
		'208': 'Danemark',
		'246': 'Finlande',
		'250': 'France',
		'276': 'Allemagne',
		'300': 'Grèce',
		'344': 'Hong Kong',
		'356': 'Inde',
		'372': 'Irlande',
		'376': 'Israël',
		'380': 'Italie',
		'392': 'Japon',
		'410': 'Corée du sud',
		'442': 'Luxembourg',
		'528': 'Pays-Bas',
		'554': 'Nouvelle-Zélande',
		'578': 'Norvège',
		'616': 'Pologne',
		'620': 'Portugal',
		'634': 'Qatar',
		'643': 'Russie',
		'702': 'Singapour',
		'710': 'Afrique du sud',
		'724': 'Espagne',
		'752': 'Suède',
		'764': 'Thaïlande',
		'792': 'Turquie',
		'804': 'Ukraine',
		'826': 'Royaume-Uni',
		'840': 'États-Unis',
	}
});
