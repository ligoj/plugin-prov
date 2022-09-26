/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define({
	root: {
		'title': 'Catalog',
		'stop': 'Stop',
		'quotes': 'Number of quotes using this catalog',
		'lastSuccess': 'Date',
		'nbLocations': 'Number of available locations for this provider',
		'locationPreferred': 'Preferred location',
		'nbTypes': 'Number of available types for this provider',
		'nbPrices': 'Number of available price combinations for this provider',
        'percentCo2Prices': 'Percent of prices having equivalent CO2 data',
		'actions': 'Enabled features for this provider',
		'status': 'Catalog status',
		'status-updating': '{{[0]}}% ({{[1]}}/{{[2]}})<br>Started {{[3]}} by {{[4]}}<br>Current step : {{[5]}}<br>Last success : {{[6]}}',
		'status-initializing': '{{[0]}}% ({{[1]}}/{{[2]}})<br>Started {{[3]}} by {{[4]}}<br>Current step : {{[5]}}<br>Last success : first import',
		'status-finished-ok': 'Updated {{[0]}} by {{[1]}} and took {{[2]}}',
		'status-canceled': 'Cancel requested by {{this}}',
		'status-finished-ko': 'Started {{[0]}} by {{[1]}} and failed {{[2]}}<br>Last step : {{[3]}} {{[4]}}% ({{[5]}}/{{[6]}})<br>Last success : {{[7]}}',
		'status-not-supported': 'Version {{this}}, does not support remote catalog update, requires a plug-in update',
		'status-no-version': 'Does not support remote catalog update, requires a plug-in update',
		'status-new': 'Never updated',
		'status-started': 'Catalog update request of {{this}} has been sent',
		'update-standard-help': 'Update the prices from the provider pricing list',
		'update-standard': 'Standard update',
		'update-force': 'Full update',
        'update-force-help': 'Update the prices and the type configurations from the provider pricing list. Slower than standard mode.',
        'service:prov:default': 'Default',
		'm49': {
            '2': 'Africa',
            '5': 'South America',
            '9': 'Oceania',
            '18': 'Southern Africa',
            '19': 'Americas',
            '21': 'Northern America',
            '30': 'Eastern Asia',
            '34': 'Southern Asia',
            '35': 'South-eastern Asia',
            '36': 'Australia',
            '39': 'Southern Europe',
            '40': 'Austria',
            '53': 'Australia and New Zealand',
            '56': 'Belgium',
            '76': 'Brazil',
            '100': 'Bulgaria',
            '124': 'Canada',
            '142': 'Asia',
            '143': 'Central Asia',
            '145': 'Western Asia',
            '150': 'Europe',
            '151': 'Eastern Europe',
            '154': 'Northern Europe',
            '155': 'Western Europe',
            '156': 'China',
            '208': 'Denmark',
            '246': 'Finland',
            '250': 'France',
            '276': 'Germany',
            '300': 'Greece',
            '344': 'Hong Kong',
            '356': 'India',
            '372': 'Ireland',
            '376': 'Israel',
            '380': 'Italy',
            '392': 'Japan',
            '410': 'South Korea',
            '442': 'Luxembourg',
            '528': 'Netherlands',
            '554': 'New Zealand',
            '578': 'Norway',
            '616': 'Poland',
            '620': 'Portugal',
            '634': 'Qatar',
            '643': 'Russia',
            '702': 'Singapore',
            '710': 'South Africa',
            '724': 'Spain',
            '752': 'Sweden',
            '764': 'Thailand',
            '792': 'Turkey',
            '804': 'Ukraine',
            '826': 'United Kingdom',
            '840': 'USA'
        }
	},
	fr: true
});
