# :link: Ligoj Provisioning plugin [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.ligoj.plugin/plugin-prov/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.ligoj.plugin/plugin-prov)

[![Build Status](https://app.travis-ci.com/github/ligoj/plugin-prov.svg?branch=master)](https://app.travis-ci.com/github/ligoj/plugin-prov)
[![Build Status](https://circleci.com/gh/ligoj/plugin-prov.svg?style=svg)](https://circleci.com/gh/ligoj/plugin-prov)
[![Build Status](https://ci.appveyor.com/api/projects/status/u6i3563iv6f0omm7/branch/master?svg=true)](https://ci.appveyor.com/project/ligoj/plugin-prov/branch/master)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=org.ligoj.plugin%3Aplugin-prov&metric=coverage)](https://sonarcloud.io/dashboard?id=org.ligoj.plugin%3Aplugin-prov)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?metric=alert_status&project=org.ligoj.plugin:plugin-prov)](https://sonarcloud.io/dashboard/index/org.ligoj.plugin:plugin-prov)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/1c815531da2f40dea89a57999ad7e5ca)](https://www.codacy.com/gh/ligoj/plugin-prov?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ligoj/plugin-prov&amp;utm_campaign=Badge_Grade)
[![CodeFactor](https://www.codefactor.io/repository/github/ligoj/plugin-prov/badge)](https://www.codefactor.io/repository/github/ligoj/plugin-prov)
[![Maintainability](https://api.codeclimate.com/v1/badges/e92fa81768de52d514b7/maintainability)](https://codeclimate.com/github/ligoj/plugin-prov/maintainability)[![License](http://img.shields.io/:license-mit-blue.svg)](http://fabdouglas.mit-license.org/)

[Ligoj](https://github.com/ligoj/ligoj) Provisioning plugin
Provides the following features :
- Find the best instance from the given requirement : CPU, RAM, OS and price type
- Compute the total cost of storage and compute for a set of VM

## Supported requirements
|Name     | Note|
|---------|--------------------------------------------------------------------------------|
|Term     | A contract, defining the constraints: reservation, conversion options and sometimes a location
|Location | A geographical place (variable GPS coordinates precision). May be a coutry, or sometimes a city
|Processor| The underlying physical processor. The vendor can be used instead of the full product code.
|Physical | A boolean constraint to expet a physical (bar metal) instance instead of a virtual one
|Tenancy  | Shared or dedicated, to handle the noisy neighborhood issue
|OS       | Operating system. Currently, only the type name is suppoted: Windows, Linux, RHEL,...
|Software | The pre-installed sofware
|Engine   | Database engine name. Can be MySQL, Oracle, .. or even cutom one like Aurora. Depens on the availability in the catalog.
|Edition  | Database edition valid for a specifi engine. For sample: Oracle Standard Edition 1
|License  | BYOL or included mode. Depens on the availability in the catalog.
|Optimized| Storage expected optimization: durability, IOPS, throughput
|CPU/RAM  | Expected vCPU/RAM(MiB) to match. Note that some providers support custom (eleastic) settings for these valeus. Ligoj handles this.
|Ephemeral| A boolean to accept a shutdown of a instance. Would be plugged to Spot/Batch like services
|Usage    | Utilization profile including up-time, commitment, conversion, reservation and forecasted start.
|Size     | Storage size in GiB. The required value is checked against provider limits.
|Latency  | IO latency access rating from WORST to BEST
|Optimized| Optimization profile: durability, throughput and IOPS


## Covered requirements per resource:
|Resource | Criteria|
|---------|--------------------------------------------------------------------------------|
|Instance | Tenancy, processor, physical (metal), ephemeral, %usage, location, term, cpu, ram, OS, software
|Database | Tenancy, processor, physical (metal), ephemeral, %usage, location, term, cpu, ram, engine, edition
|Storage  | Location, size, latency, location, optimization
|Support  | Phone, mail, chat, API, seats


## Covered service per provider:
|Provider|Plugin                                                         |Covered services|
|--------|---------------------------------------------------------------|----------------|
|AWS     |[plugin-prov-aws](https://github.com/ligoj/plugin-prov-aws)    |EC2 (Savings Plan, RI, Spot), RDS, S3 (Glacier, IA,...), EFS, Support, EBS, |

Related plugins:
- [plugin-prov-aws](https://github.com/ligoj/plugin-prov-aws)  
- [plugin-prov-azure](https://github.com/ligoj/plugin-prov-azure)
- [plugin-prov-digitalocean](https://github.com/ligoj/plugin-prov-digitalocean)
- [plugin-prov-outscale](https://github.com/ligoj/plugin-prov-outscale)

Custom configuration:
service:prov:use-parallel = 0/1