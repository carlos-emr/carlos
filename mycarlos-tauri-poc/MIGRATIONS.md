# myCarlos vault migration protocol

- **Status:** protocol defined; no migration is needed while only format v1 exists
- **Data restriction:** synthetic fixtures only until independent review and physical-device testing
- **Applies to:** `header.json`, both encrypted manifest slots, and encrypted object envelopes

This protocol defines how a future format reader/writer must migrate a vault without modifying the
only known-good copy in place. A format implementation must not be merged merely because it follows
this document: it also needs format-specific review, fixtures, and interruption tests.

## Compatibility rules

1. Every reader accepts only explicitly supported versions. Unknown older, newer, missing, or
   malformed versions fail closed without changing storage.
2. The application never silently downgrades a vault. A binary that cannot read the current version
   reports that limitation and leaves the vault untouched.
3. An upgrade keeps its previous complete vault until the replacement has been authenticated and
   durably activated.
4. Backup metadata records its format version and source generation. Restore authenticates the
   complete backup and refuses an incompatible or detectably stale replacement before changing the
   live vault.
5. Version-specific readers are immutable after release except for security fixes that preserve
   their accepted grammar. New output uses a new writer and explicit migration.

## Transaction layout

For a live vault named `mycarlos-vault`, migration uses sibling directories on the same filesystem:

```text
mycarlos-vault/                         current complete vault
.mycarlos-migrate-<operation-id>/       staged replacement
.mycarlos-retired-<operation-id>/       previous complete vault during activation
```

The operation ID is random and is not derived from patient data. Staging on the same filesystem is
required so directory renames use the platform's atomic rename boundary. Migration must refuse a
different-filesystem staging location.

## Migration sequence

1. Lock the vault and take the same exclusive operation mutex used by create, unlock, reset, and
   passphrase rotation. Do not expose a partially migrated state to another command.
2. Read and authenticate the current header, both manifest slots, and every referenced object with
   the old version reader. Resolve manifest redundancy before migration. Do not delete or repair
   unreferenced data after migration has begun.
3. Check target-version support, required free-space reserve, and migration-specific preconditions.
4. Create a private staged sibling directory. Write new header/object/manifest envelopes there
   without plaintext temporary files. Preserve stable record/profile/folder IDs unless the target
   format explicitly requires a reviewed mapping.
5. Re-open the staged vault through the target version's normal reader. Authenticate every object,
   compare plaintext size and a keyed content fingerprint for every record, and compare the complete
   logical snapshot with the source.
6. Sync staged files and directories, then sync the parent directory.
7. Rename the live vault to the retired sibling, sync the parent, rename the staged vault to the live
   name, and sync the parent again. These are the only activation steps.
8. Open the live name through the target reader and repeat header/manifest/object authentication.
9. Only after successful live verification may the retired ciphertext tree be removed. Sync the
   parent after removal. If removal fails, report cleanup as incomplete without using the retired
   vault as an alternative live state.

Passphrases, master keys, derived keys, object keys, and plaintext buffers follow the same
zeroization and renderer-boundary rules as ordinary vault operations.

## Restart recovery

Startup resolves migration artifacts before ordinary unlock and never chooses a vault by timestamp
alone:

- Live valid, retired valid: keep live; remove retired only after confirming the live target version
  and its migration identity.
- Live missing, retired valid, stage valid: activate the stage only if its authenticated migration
  metadata identifies the retired vault and verification is complete; otherwise restore retired.
- Live missing, retired valid, stage invalid/incomplete: restore retired and remove stage.
- Live valid, stage present: keep live and remove the uncommitted stage.
- Live invalid, retired valid: restore retired and report that migration did not complete.
- No single authenticated complete candidate: fail closed and preserve every artifact for recovery.

Authenticated migration metadata in the target manifest must bind the source vault ID, source
format, source generation, target format, and random operation ID. Local metadata cannot by itself
detect restoration of an older formerly valid vault; the future backup/synchronization rollback
trust anchor remains a separate requirement.

## Required test matrix for each migration

- [ ] Golden fixtures for every supported source version and the new target version.
- [ ] Unknown version, downgrade attempt, malformed version, corrupt header, corrupt manifest slot,
      corrupt object, wrong vault ID, and wrong passphrase all fail without mutation.
- [ ] Abrupt termination and deterministic `NoSpace` injection after every file write, sync, rename,
      verification, activation, and cleanup boundary.
- [ ] Restart each interrupted fixture repeatedly and obtain either the complete old logical state or
      the complete new logical state, never a mixture.
- [ ] Compare IDs, profile/folder relationships, names, sizes, keyed fingerprints, and decrypted
      synthetic canaries before and after migration.
- [ ] Restore old and new portable backups with compatible binaries; reject forward-incompatible and
      detectably stale backups without touching the live vault.
- [ ] Run the matrix on NTFS, APFS, Android application storage, and iOS application storage,
      including actual power interruption and full-filesystem conditions.
- [ ] Confirm an older binary refuses the new format and never rewrites it.
- [ ] Independent reviewers approve the new format, migration code, fixtures, and recovery evidence.

The v1 automated suite already confirms that unsupported header and authenticated-manifest versions
fail closed, and samples random wrapping nonces for accidental repetition. The activation and
interruption boxes remain open until a real v2 writer exists.
