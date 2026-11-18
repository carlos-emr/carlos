# Image replacement write refusal

The atomic image publisher now checks effective write access to an existing
image while holding the shared publication lease. An unwritable destination
returns the established upload error before staging or replacement, preserving
the original bytes and permissions. Missing destinations and writable image
replacement retain their existing success behavior.

The new deterministic refusal case fails before the fix (`success` instead of
`error`); all 130 related Java controls pass afterward, with no skipped cases.
The changed upload code retains 100% line, instruction and branch coverage.

A separate Ubuntu 26.04 container runs the real filesystem test as UID 1000,
with all capabilities dropped, no network and read-only class/dependency mounts.
Its missing, writable and mode-0444 destination cases reproduce one failure
before the fix and pass all three cases afterward. Original image bytes and
permissions survive the read-only refusal. The unit control also models effective
access denial explicitly, so privileged CI cannot hide this regression.

- Java controls JUnit SHA-256: `cef925cbe9d4245b2b8a629c4ad11e0537983076b0d4857a0d96fccd714d3cb5`.
- Physical before JUnit SHA-256: `a45631b88eb7c3c3f948801e3844aa102aa6ad29464fe0b095a2a3d1e40c8714`.
- Physical after JUnit SHA-256: `8e25f9e02c184a30fc70ef4d52e10e380eb83adb0ec1c762affcc21c91dcf55c`.
- Source-bound coverage SHA-256: `368f35b183f875af279a6931e46f5795964201504cd676dde212c3a81be6b5fc`.

These are selected-source JVM/filesystem proofs. Rebuilt-DEB verification of
this follow-up belongs to the next combined batch; this document makes no claim
that the currently installed previous image-upload revision contains the fix.
