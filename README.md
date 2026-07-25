# :link: Ligoj Provisioning plugin ![Maven Central](https://img.shields.io/maven-central/v/org.ligoj.plugin/plugin-prov)

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

| Name         | Note                                                                                                                              |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Term         | A contract, defining the constraints: reservation, conversion options and sometimes a location                                    |
| Location     | A geographical place (variable GPS coordinates precision). May be a country, or sometimes a city                                  |
| Processor    | The underlying physical processor. The vendor can be used instead of the full product code.                                       |
| Architecture | The underlying physical processor architecture, such as x86 or ARM.                                                               |
| Physical     | A boolean constraint to expect a physical (bar metal) instance instead of a virtual one                                           |
| Tenancy      | Shared or dedicated, to handle the noisy neighborhood issue                                                                       |
| OS           | Operating system. Currently, only the type name is supported: Windows, Linux, RHEL,...                                            |
| Software     | The pre-installed software                                                                                                        |
| Engine       | Database engine name. Can be MySQL, Oracle, .. or even custom one like Aurora. Depends on the availability in the catalog.        |
| Edition      | Database edition valid for a specific engine. For sample: Oracle Standard Edition 1                                               |
| License      | BYOL or included mode. Depends on the availability in the catalog.                                                                |
| Optimized    | Storage expected optimization: durability, IOPS, throughput                                                                       |
| CPU/RAM      | Expected vCPU/RAM(MiB) to match. Note that some providers support custom (elastic) settings for these values. Ligoj handles this. |
| Ephemeral    | A boolean to accept a shutdown of a instance. Would be plugged to Spot/Batch like services                                        |
| Usage        | Utilization profile including up-time, commitment, conversion, reservation and forecasted start.                                  |
| Size         | Storage size in GiB. The required value is checked against provider limits.                                                       |
| Latency      | IO latency access rating from WORST to BEST                                                                                       |
| Optimized    | Optimization profile: durability, throughput and IOPS                                                                             |
| Generation   | (p1TypeOnly) Instead of searching a cheapest instance, find the cheapest instances among the last generations SKUs                |

## Covered requirements per resource

| Resource | Criteria                                                                                                                     |
|----------|------------------------------------------------------------------------------------------------------------------------------|
| Instance | Tenancy, processor, physical (metal), architecture, ephemeral, %usage, location, term, cpu, ram, OS, software, p1TypeOnly    |
| Database | Tenancy, processor, physical (metal), architecture, ephemeral, %usage, location, term, cpu, ram, engine, edition, p1TypeOnly |
| Storage  | Location, size, latency, location, optimization                                                                              |
| Support  | Phone, mail, chat, API, seats                                                                                                |

## Covered service per provider

| Provider      | Plugin                                                                        | Covered services                                                            |
|---------------|-------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| AWS           | [plugin-prov-aws](https://github.com/ligoj/plugin-prov-aws)                   | EC2 (Savings Plan, RI, Spot), RDS, S3 (Glacier, IA,...), EFS, Support, EBS, |
| Azure         | [plugin-prov-azure](https://github.com/ligoj/plugin-prov-azure)               | VM, Database, Disk (object, file, block),                                   |
| Digital Ocean | [plugin-prov-digitalocean](https://github.com/ligoj/plugin-prov-digitalocean) | VM, Database, Disk                                                          |
| OVH           | [plugin-prov-ovh](https://github.com/ligoj/plugin-prov-ovh)                   | VM, Database, Disk                                                          |
| OutScale      | [plugin-prov-outscale](https://github.com/ligoj/plugin-prov-outscale)         | VM, Database, Disk                                                          |

Related plugins:

- [plugin-prov-aws](https://github.com/ligoj/plugin-prov-aws)
- [plugin-prov-azure](https://github.com/ligoj/plugin-prov-azure)
- [plugin-prov-digitalocean](https://github.com/ligoj/plugin-prov-digitalocean)
- [plugin-prov-outscale](https://github.com/ligoj/plugin-prov-outscale)
- [plugin-prov-ovh](https://github.com/ligoj/plugin-prov-ovh)

Custom configuration:
service:prov:use-parallel = 0/1

## Catalog import and the database

Provider catalog imports that opt in (currently `plugin-prov-azure`) run inside a single database
transaction: prices are accumulated in the JPA persistence context, flushed and cleared by chunks of
`1000` prices, and written with a session-scoped JDBC batch size of `100` (`AbstractImportCatalogResource`:
`initJdbcBatch()`, `flushChunk()`, `flushAndClear()`). No global Hibernate configuration is required. On
failure, the whole import rolls back and the previous catalog is preserved.

Database recommendations for large imports (millions of prices):

- `idle_in_transaction_session_timeout` (PostgreSQL) must be `0` (no limit, the default) or
  comfortably above a few minutes: the import transaction sits idle while the provider catalog files
  (tens of MB) are downloaded and parsed. The `ligoj-cli` managed `ligoj-db` pod pins
  `-c idle_in_transaction_session_timeout=0` explicitly since 2026-07, so a base-image or
  `ALTER SYSTEM` change cannot silently kill a running import.
- Add `reWriteBatchedInserts=true` to the PostgreSQL JDBC URL (`jdbc.url`) so the JDBC batches are
  rewritten as multi-row `INSERT` statements.