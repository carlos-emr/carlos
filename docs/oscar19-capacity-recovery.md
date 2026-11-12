# OSCAR 19 capacity and pre-copy package recovery (#5503)

The fixed application and carlos-ctl 1.3.1 must be installed together. The
application ships o19map-6 and requires CLI >= 1.3.1; the release pin must
publish that CLI before the application release is assembled.

The source linked by [OSCAR Galaxy](https://oscargalaxy.org/links-for-oscar-developers/)
is `https://bitbucket.org/oscaremr/oscar.git`. The capacity audit uses stable
commit `b425be066fa17f7dff0a4e0ee58e41bf89b83d81`, including historical init
and update declarations. Keeping the largest declaration matters: a later
patch can narrow a field even though an installed clinic still has its older,
wider form. Manifest generation retains the separately pinned OSCAR 19 fixture
commit `a7900d569d3faf741993e5e1da8c14021bbefede`.

## Preserved values

| Column | New minimum character capacity |
|---|---:|
| demographic.sourceOfIncome | 255 |
| favorites.BN | 255 |
| property.value | 4000 |
| demographic.previousAddress / children | 255 |
| demographic.provider_no | 250 |
| demographic.newsletter | 32 |
| billing.total | 7 |
| measurementTypeDeleted.typeDisplayName | 255 |
| pharmacyInfo.city | 255 |
| formMentalHealthForm1.datetimeOfDetention | 50 |
| formBCAR2007.ar2_urineCStxt (BC, restored native field) | 255 |

Common, Ontario and BC forward migrations also enlarge mapped text fields.
TEXT sizes count bytes, so equal TEXT types can still be too small after
converting latin1 to utf8mb4. TEXT becomes MEDIUMTEXT and MEDIUMTEXT becomes
LONGTEXT where necessary; small text fields receive a sufficient text tier.
LONGTEXT is the engine maximum: values that cannot fit that limit must still
fail explicitly. No values are truncated, replaced with NULL, stripped of
markers, or moved out of their original field. Source schema differences
alone do not constitute data loss.

The inventory is `scripts/migration/o19/fixtures/schema-capacity.json`. To
reproduce the source audit, export the pre-change CARLOS migration tree from
commit `905c455840adf9ec327f1cef13b6e327b69e2e41`, then run:

```sh
python3 scripts/migration/o19/audit_text_capacity.py \
  --oscar-src /path/to/oscar-stable \
  --carlos-baseline /path/to/pre-change-carlos \
  --out /tmp/o19-capacities.json
```

The migrations explicitly retain the packaged collations, including clinical
note overrides, defaults and nullability. Strict SQL mode rejects incompatible
local data rather than allowing DDL truncation. Existing data-dependent ETL
capacity and exact-content verification remain in place. No additional
preflight source-value measurements are introduced.

Common V1.0.59.32 also widens the seven matching consultation archive fields
and consultationRequestExtArchive.value. Consultation edits copy these values
verbatim, so their history must accommodate the enlarged live columns. A
physical-contract regression checks named archive/deleted/history counterparts
for all shared text/binary fields, not only the widened fields. The same migration
preserves time-of-day in demographicArchive.lastUpdateDate and
providerArchive.lastUpdateDate by upgrading DATE to DATETIME. Existing dates
retain their midnight value; previously discarded times cannot be recovered.
The demographic archive also uses VARCHAR(2) for day/month components, matching
the live fields and retaining their exact text rather than CHAR padding rules.

Legacy sourceOfIncome text is visible, HTML encoded, in demographic details.
This includes guardian contact details and template-only lines. Its existing
255-character model field remains the destination. Brand and property model
annotations match the enlarged columns.

## Consent

The generated mapping reads deleted from the archived Consent__live helper.
Deleted and undecided records remain retired; one deciding live record per
patient/type is selected conservatively, with opt-out taking precedence.
Prior flags and retirement reasons are retained. The application migration
records pre-existing normalization/duplicate retirement in
Consent_migration_audit (including standard system attribution and update dates),
retains every row, and enforces one live record with
a generated-column unique key. Both application lookups exclude retired rows.
In the published `V1.0.59.30` migration, `null_flag` audit rows record flags
before normalization. `duplicate_retired` rows record flags after null
normalization but before retirement. For a duplicate whose flags included
NULL, its `null_flag` row retains the original values; its
`duplicate_retired` row explains the later retirement. The published migration
is unchanged so existing Flyway checksums remain valid.
An older unmodified manifest is diagnosed as an incompatible package pair;
file tampering is not assumed. Host preflight checks this mapping structurally.

## Retained run after a package upgrade

The CLI installer permits this retained pre-copy state only while holding the
workspace and database locks until dpkg exits. It refuses an active importer or
a ledger that records any copy. Application provisioning and startup stay guarded.

Use the same original bundle and command with `--resume` after installing both
fixed packages. Do not modify staging or delete its workspace. The original
pre-import backup remains the rollback point.

Recovery is permitted only before the ETL ledger records a seed/admin or any
table copy. It requires an exact packaged pre-fix physical contract (or an
otherwise completely explained capacity-only transition), a pristine target,
the seeded login alone, and an additional successful backup. The importer
then runs the packaged Flyway migrations, verifies the new physical schema,
repeats the pristine checks, and records the package transition before copying.
It retains staging and source identities. An already upgraded pristine target
can be rebound after the same schema/pristine checks without repeating DDL.

A partial copy cannot change its schema, manifest or ETL contract mid-import.
Unexpected drift, a failed backup or a failed migration stops recovery with an
explicit error. Resolve an interrupted DDL operation or restore the original
snapshot; MariaDB DDL is not transactional. Widening can rebuild large tables,
so package rehearsal must include disk space and maintenance-window timing for
the clinic's actual database size.
