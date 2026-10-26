# Database comparisons and imported schemas

Flyway owns column definitions. Hibernate schema generation remains disabled;
Spring/JPA uses the database column's comparison rules, including columns with
different collations in the same entity. The reviewed choices are recorded in
`database/mysql/collation-policy.json` and enforced by the physical schema contracts.

All application text uses `utf8mb4` storage. The connection, database and legacy
table default remains `utf8mb4_general_ci` for established identifier/lookup-key
equivalence and compatibility with the checksum-frozen migration chain. It is not
a general recommendation for human text: its comparisons conflate some distinct
supplementary Unicode characters.

V1.0.59.5 changes these reviewed human-text columns to `utf8mb4_uca1400_ai_ci`:

- `demographic.first_name`, `demographic.last_name`
- `provider.first_name`, `provider.last_name`
- `casemgmt_note.note`, `document.docdesc`, `drugs.special`, `tickler.message`

These comparisons remain case/accent insensitive while providing Unicode-aware
comparison and ordering. Stored text is not normalized or rewritten. Other columns
retain their established comparison rules; further changes need a per-column
review of queries, uniqueness and references, followed by another forward migration.

`appointment.status` and `appointment_status.status` use `utf8mb4_bin`. Appointment
codes distinguish `C` (Cancelled) from `c` (Customized 3), and `H` (Here) from `h`
(Confirmed). The lookup column previously inherited a case-insensitive default;
the forward migration aligns it with the appointment column and Java's case-sensitive
lookup. A binary collation still ignores padding spaces: import value verification
compares binary strings after character-set conversion instead.

V1.0.59.5 checks the affected shapes and complete composite/prefix unique keys
before changing a persistent column. A collision under the proposed collation
stops the migration with source rows untouched; it never chooses a row to delete.
Existing widths, defaults, nullability and comments are preserved. A failed
multi-statement migration can still need the documented Flyway recovery procedure;
DDL is not one transaction. Stop all writers and take a verified backup first.

The JDBC connection remains explicitly `utf8mb4_general_ci`. With the tested
Connector/J 9.6.0, requesting UCA14 directly in `connectionCollation` selected a
different collation; enabling custom-collation detection failed. Column-level UCA14
does not require that connection change. CLI/Flyway sessions and MariaDB's
`init-connect` also explicitly select the compatible connection collation, rather
than inheriting a distribution's `character_set_collations` mapping.

OSCAR imports restore source encodings/collations in isolated staging, then insert
values into the existing Flyway-created application tables. Source indexes do not
replace application indexes. Key comparisons, merge partitions and ID maps use the
target column's type and collation. Source archive objects retain original
definitions and capacity; their differences are preservation metadata.

V1.0.59.6 reconciles the reviewed performance indexes by complete definition,
including column order, prefixes, uniqueness, index type and ignored status. A
same-name unexpected unique index is reported before any repair, never downgraded.
The old `providerExt` provider index is redundant with its full unique replacement.
The plain `demographicExt.demographic_no` index is removed only where the canonical
conditional unique key supplies that full leading column. Primary/unique constraints
and the audit log's hinted index names are preserved. These decisions follow the
existing DAO index review; workload-specific improvements still require query plans
and representative data.

`database/mysql/schema-contract/{on,bc}.json` records columns, defaults, generated
expressions, indexes, constraints and database defaults from a fresh Flyway build.
Regenerate with `scripts/schema-contract.py` after a schema change. CI compares a
fresh database with the committed contract; the package build also verifies that
the WAR contains the identical migration bytes. Import checks the physical contract
before copying and after verification, checks every declared foreign key, and
refreshes optimizer statistics. Flyway history validation alone does not inspect
physical definitions. Documented `import_archived_` preservation objects are outside
the managed application contract.

The current OSCAR manifest also follows forward reference-data changes:
the retired worksheet is subtracted from both provinces' pristine menu floors,
and the BC WorkSafeBC body-part, nature-of-injury and side catalogs merge on
codes. CARLOS reference rows win; clinic-only codes append and original clinic
rows remain archived. The schema verification job checks actual fresh row counts
against every copy/merge table's pristine expectation as well as its physical
schema. This catches release changes that a static INSERT tuple counter misses.

Legacy notes without a revision UUID keep that source value. The application
uses their recorded provider for the editor display and does not group unrelated
notes on a blank UUID. This compatibility fallback requires no clinical-row rewrite.
