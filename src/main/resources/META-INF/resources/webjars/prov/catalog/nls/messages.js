define({
	root: {
		'title': 'Catalog',
		'quotes' : 'Number of quotes using this catalog',
		'lastSuccess' : 'Date',
		'nbLocations' : 'Number of available locations for this provider',
		'nbStorageTypes' : 'Number of available storage types for this provider',
		'nbInstanceTypes' : 'Number of available instance types for this provider',
		'nbInstancePrices' : 'Number of available price combinations for this provider',
		'actions' : 'Enabled features for this provider',
		'status' : 'Catalog status',
		'status-updating' : '{{[0]}}% ({{[1]}}/{{[2]}})<br>Started {{[3]}} by {{[4]}}<br>Current step : {{[5]}}<br>Last success : {{[6]}}',
		'status-initializing' : '{{[0]}}% ({{[1]}}/{{[2]}})<br>Started {{[3]}} by {{[4]}}<br>Current step : {{[5]}}<br>Last success : first import',
		'status-finished-ok' : 'Updated {{[0]}} by {{[1]}} and took {{[2]}}',
		'status-finished-ko' : 'Started {{[0]}} by {{[1]}} and failed {{[2]}}<br>Last step : {{[3]}} {{[4]}}% ({{[5]}}/{{[6]}})<br>Last success : {{[7]}}',
		'status-not-supported' : 'Version {{this}}, does not support remote catalog update, requires a plug-in update',
		'status-no-version' : 'Does not support remote catalog update, requires a plug-in update',
		'status-new' : 'Never updated',
		'status-started' : 'Catalog update request of {{this}} has been sent'
	},
	fr: true
});
