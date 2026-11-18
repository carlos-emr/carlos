# Image replacement write refusal

The atomic image publisher now checks effective write access to an existing
image while holding the shared publication lease. An unwritable destination
returns the established upload error before staging or replacement, preserving
the original bytes and permissions. Missing destinations and writable image
replacement retain their existing success behavior.

On Linux, replacing an existing eForm image also requires effective read
access. The metadata copier opens the destination image for reading to retain
its ACLs and extended attributes. Shared image storage must grant the upload
process both read and write access to images it replaces. A write-only image
returns a specific upload error before staging.

The new deterministic refusal case fails before the fix (`success` instead of
`error`); all 152 related Java controls pass afterward, with no skipped cases.
The changed upload code retains 100% line, instruction and branch coverage.

A separate Ubuntu 26.04 container runs the real filesystem test as UID 1000,
with all capabilities dropped, no network and read-only class/dependency mounts.
Its missing, writable and mode-0444 destination cases reproduce one failure
before the fix and pass all three cases afterward. Original image bytes and
permissions survive the read-only refusal. The unit control also models effective
access denial explicitly, so privileged CI cannot hide this regression.

- Java controls JUnit SHA-256: `08092ee243b867f1f2c2222b250fac1e9901dc95a9f0a7f1a4e5939ce5bf72e5`.
- Physical before JUnit SHA-256: `06e2d4e639798f29896887fdaa4b667b8692306056988486a74815ad8854764f`.
- Physical after JUnit SHA-256: `fbf853c26dcf053f29e7fb2ff084546ce5eb4b20f168d436288c527d5968a627`.
- Source-bound coverage SHA-256: `3f9d117f1f1b344a1e2509aef06860e9f0cf14e6b3c083c927541c2961cde957`.

The incoming ownership/ACL/extended-attribute preservation changes are retained.
Twenty provider/process controls cover portable metadata copying, truncated
attribute transfers, unsupported views, ownership/group/mode mismatches,
non-regular files, unavailable or failed copy tools, timeouts and interruption.
They exercise the platform-specific helper without changing global OS properties.
The combined upload code has fresh 100% line, instruction and branch coverage;
both parents' thresholds and historical evidence are retained.

These are selected-source JVM/filesystem proofs. Rebuilt-DEB verification of
this follow-up belongs to the next combined batch; this document makes no claim
that the currently installed previous image-upload revision contains the fix.
