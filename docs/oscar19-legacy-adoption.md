# Adopting a legacy OSCAR 19 / OpenO database

How to bring an existing clinic database into a `carlos-ctl`-managed CARLOS
install, in place — the schema is adopted where it stands and brought up to the
current migration level.

> This is **not** the same thing as `carlos-ctl o19-import`. That verb runs a
> full ETL from a separate OSCAR 19 estate into a CARLOS database; this page
> covers taking the clinic's own datadir and making it *be* the CARLOS
> database. Use `o19-import` when you are transforming and merging; use this
> when you are adopting.

---

## The short version

```bash
# 1. Decrypt and load the clinic dump
openssl enc -d -aes-256-cbc -pbkdf2 -in dump.tar.gz.enc -out dump.tar.gz -pass stdin
tar xzf dump.tar.gz
sudo carlos-ctl backup full                 # verified rollback point, before anything
sudo carlos-ctl db < dump.sql
sudo carlos-ctl db < myisambackup.sql       # if the clinic dumped MyISAM separately

# 2. Adopt — see the plan first if you like
sudo carlos-ctl db-baseline --dry-run
sudo carlos-ctl db-baseline

# 3. Apply the forward deltas
sudo carlos-ctl db-migrate

# 4. Verify, then destroy the plaintext dump
sudo carlos-ctl db-validate
sudo carlos-ctl check
shred -u dump.sql dump.tar.gz dump.tar.gz.enc; [ -f myisambackup.sql ] && shred -u myisambackup.sql
```

**Step 4's `shred` is not optional.** The decrypted dump is the clinic's entire
clinical record sitting in plaintext on disk. Remove it the moment the adoption
is verified, and prefer a working directory on encrypted storage until then.

---

## What `db-baseline` actually does, and why it has to

`db-baseline` stamps `flyway_schema_history` at **1.0.2**, not at 1 — stamping
at 1 would let Flyway re-run the province migrations, and `V1.0.1` DROPs and
recreates province tables. That part has always been right.

But a stamp at 1.0.2 is an **assertion**: it tells Flyway that common `V1` and
the province `V1.0.1`/`V1.0.2` are already satisfied and need not run. On a
genuine OSCAR 19 datadir that assertion is false. The datadir forked from this
lineage years before those files existed, so every column that has been part of
the schema *since* `V1` — rather than added by a later forward migration — is
simply absent.

Flyway's `baseline` command only writes the history row. It never executes
`V1__baseline_schema.sql`. So for most of this tool's life, adopting a legacy
database produced an install that looked completely healthy:

| check | result | why it is not enough |
|---|---|---|
| `db-migrate` | `applied 12 migration(s); schema is at 1.0.23` | forward deltas really did apply |
| `db-validate` | passes | compares the **history** against the WAR's migrations — not the live schema against either |
| first login | **fails** | `Unknown column 's1_0.mfaSecret'` |
| first screen after login | **500** | `ProviderPreference.defaultBillingLocation` missing |

`db-baseline` now makes the assertion true before stamping it. It reads the
genesis DDL out of the deployed WAR — the same files Flyway would have run —
and reconciles the live schema up to it:

* `CREATE TABLE IF NOT EXISTS` for a table the datadir never had;
* `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` for every genesis column.

Both are no-ops on anything already present, which is what makes the pass safe
to run unconditionally and safe to repeat after an interruption. The session
pins `sql_mode` to empty first: seven genesis columns default to
`'0000-00-00'`, and under a `sql_mode` carrying `NO_ZERO_DATE` or `TRADITIONAL`
those `ADD COLUMN` statements fail *part way through*. The packaged MariaDB
drop-in already sets `sql_mode = ""`, but it is only read at server start and
`db-baseline` has no ordering against `db-apply-settings`. On a real
clinic import it touched 410 tables and 10,139 columns in one pass, of which
only the genuinely missing handful did any work.

Two caveats worth knowing:

* Added columns land at the **end** of the table rather than in their genesis
  position. Column order is not part of any contract CARLOS relies on —
  Hibernate binds by name — but a diff against a fresh install will show it.
* An **AUTO_INCREMENT** column missing from a table that otherwise exists is
  reported, not added. `ADD COLUMN ... AUTO_INCREMENT` requires the column to
  become a key in the same statement, and a table that has lost its
  auto-increment primary key needs a human, not a generated `ALTER`.

## Preparing the adopted data

Two forward migrations cannot run against a real clinic's data as it stands.
Both are properties of the *data*, not bugs in the schema, so `db-baseline`
clears them while it is adopting.

### `V1.0.5` — the unguarded `icd10` seed

`V1.0.5` restores the legacy common tables and seeds `icd10` with two
statements. The first says `INSERT IGNORE`; the second does not. Against a
datadir that already holds ICD-10 reference rows it dies on
`duplicate entry 14902 for key PRIMARY`.

`V1.0.5` ships unchanged in every published release tag, so it cannot be fixed
in place — editing it would break Flyway's checksum for every existing install.
Instead, `db-baseline` parses the keys that statement is about to insert,
copies any rows already holding those keys into
`carlos_adopt_backup_icd10`, and deletes exactly those keys. The migration then
lays down the canonical seed it always intended to. A legacy row the canonical
seed does not cover is left alone.

Two guards sit around this, because the premise — *the migration will lay down
the same rows* — is an assumption:

* Rows are only cleared when the live table's primary key really is its single
  leading integer column. The key parsed out of a seed tuple is its **first**
  field, so without that check a future seed whose leading field is something
  else (a `demographic_no`, say) would delete live rows that were never backed
  up.
* Before deleting, the codes are compared. `Icd10DaoImpl` looks this table up
  by **code string**, never by id, so if a legacy id carried a code the
  canonical seed does not, that code disappears from the lookup while clinical
  records still reference it. The run warns with an exact count and names the
  backup table rather than letting a clinician discover it.

A regression test (`carlos_ctl/tests/test_dbadopt.py`) fails the build if a
*new* forward migration seeds a non-temporary table without `INSERT IGNORE`.
`V1.0.5` is carried there as a named, documented exception.

### `V1.0.11` / `V1.0.12` — the billing filename UNIQUE indexes

These add `UNIQUE` indexes over `billing_on_diskname.ohipfilename` and
`billing_on_filename.htmlfilename`. Legacy OHIP/MCEDT submission filenames
(`HA036013.001`) do not encode the year, so a clinic that bills every January
for a decade has ten genuinely **distinct, real** billing-submission rows
sharing one filename string.

None of that is duplicate data and none of it is safe to delete. The migrations
are right to refuse to discard it silently. `db-baseline` disambiguates
instead: the earliest submission keeps its filename verbatim, and every later
row is suffixed. **The suffix always ends in the row's primary key**, so the
result is unique by construction — a `-YEAR` suffix alone collides again the
moment a clinic submits the same filename twice in one year, which is exactly
the shape these filenames take.

> **This is the one part of adoption that changes a value a human may later
> have to reconcile.** `ohipfilename` is the clinic's record of the filename
> actually sent to the Ministry, and after this it no longer matches. The
> original string is preserved in
> `carlos_adopt_backup_billing_on_diskname (row_id, column_name, original_value)`
> — keep that table.

The row's `timestamp` column is **pinned** during the rewrite. Both billing
tables declare it `ON UPDATE current_timestamp()`, so an ordinary `UPDATE`
would silently replace the clinic's record of *when* it submitted with the
adoption date — and on `billing_on_filename` that column is the very `ORDER BY`
the ranking depends on, so a second run would rank differently. The generated
statement assigns the column to itself to suppress the auto-update. That
self-assignment is not redundant; do not remove it.

Afterwards the pass re-counts duplicates and refuses to continue if any remain,
rather than letting `db-migrate` discover it later. That re-check is a hard
gate: a query that cannot be answered fails the run rather than reading as
zero.

## Stale migration history

The installer runs `carlos-ctl db-migrate` on the database it provisions, so
`flyway_schema_history` is already populated before an operator ever loads a
legacy dump over it. `mysqldump`'s `DROP TABLE IF EXISTS` then replaces the
data tables but **not** that one — an authentic OSCAR 19 dump never contained
it. The result is bookkeeping describing a schema that is no longer there, and
`db-baseline` used to refuse with *"flyway_schema_history already contains
migrations"*.

It is now detected, and the table is **renamed** to
`flyway_schema_history_preadopt_<timestamp>` — not dropped — before a fresh
stamp is written. Detection takes two signals together, because either alone is
a false positive:

* **No `BASELINE` row.** A history written by `migrate` against an empty
  database records `V1`/`V1.0.1`/`V1.0.2` as ordinary applied migrations; a
  history written by `baseline` carries a `BASELINE` row. That row is exactly
  what makes re-running `baseline` a harmless no-op, so a schema that has one
  is a previously *adopted* datadir whose history is correct — even if it is
  also short of genesis columns because it was adopted before this
  reconciliation existed. Its history is left alone.
* **Genesis columns missing.** Otherwise this is an ordinary healthy install
  and nothing here should touch its history at all.

---

## Tables this leaves behind

| table | holds |
|---|---|
| `carlos_adopt_backup_icd10` | reference rows cleared so `V1.0.5`'s seed could apply |
| `carlos_adopt_backup_billing_on_diskname` | the `ohipfilename` values actually submitted to MOH |
| `carlos_adopt_backup_billing_on_filename` | the original `htmlfilename` values |
| `flyway_schema_history_preadopt_*` | the installer's migration history, parked |

They are small, inert, and deliberately not cleaned up. Drop them only once the
clinic has signed off on the adopted data.

## Troubleshooting

**`db-migrate` fails after a successful `db-baseline`.** Read the error before
anything else. If it is a duplicate key on a table not covered above, that is a
new instance of the `V1.0.5` class of bug — file it rather than hand-editing
the deployed migration, because the next upgrade will overwrite the edit and
the checksum will not match.

**An adoption that was interrupted.** Re-run `carlos-ctl db-baseline`. Every
statement it issues is guarded or idempotent, and the backup tables are written
with `CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`, so the *first* recorded
original is preserved rather than overwritten with an already-rewritten value.

**"`carlos_adopt_backup_x` already exists with a different shape."** A backup
from an earlier adoption is still present, and the table it came from has
changed since — usually because a different legacy dump was loaded over the
database (mysqldump's `DROP TABLE IF EXISTS` does not know about the backup).
Move it aside under a new name; it holds rows an earlier run cleared.

**A schema that was already adopted with the old `--stamp-only` behaviour**
(or by an older package) is repaired by running `carlos-ctl db-baseline` again:
the reconciliation runs, the history is already correct, and the final stamp is
a no-op Flyway reports rather than an error.

## See also

* `docs/carlos-ctl.md` — the full verb reference
* `docs/database-schema-management.md` — how the Flyway migration set is built
* `carlos-ctl(8)` — the man page
