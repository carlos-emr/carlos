# Frozen candidates and release publication

Tag pushes build a **draft candidate**. Publication requires a separate completed
qualification run, including the current non-Maven checks for the exact commit.
There is no bypass input. Direct dispatch of `deb-packages.yml` and dispatch
retries enforce the same gate as `publish-release.yml`.

1. Push an annotated release tag with the matching Maven/SCM version. The normal
   release workflow verifies it, builds the WAR/SBOM, and builds the Debian
   packages into a draft. The final Debian identity is stamped into the WAR
   before it is frozen. An already matching stamp is a byte-preserving no-op;
   Debian packaging compares its final WAR against the input. The standalone
   archive carries that same candidate build identity. The package's two
   explicit resource transforms remain separately verified against the final
   exploded application in the `.deb`.
2. Wait for the tagged commit's required workflows: Maven, Script Regressions,
   WAF Policy, Debian Python, CDS native, SpotBugs, PMD, Semgrep and CI Validation.
   These now also run on release tag pushes. Only the latest direct execution
   for the exact commit is accepted; failed, cancelled, skipped, fork and PR
   executions cannot supply success. Required jobs must actually complete.
3. Dispatch `release-verification.yml` **on the tag's own ref**, supplying `tag`.
   Its `Release candidate qualification` job uses a dedicated disposable runner
   labeled `self-hosted, linux, x64, carlos-release-qualification`. Provision the
   native runtimes, loopback installations and named environment prerequisites
   described by the reviewed `release-2026.08-verification.json` plan. Each
   check/profile/repetition runs through the existing evidence adapters; a build
   receipt copies the frozen candidate rather than rebuilding it. The bundle
   binds the exact tag, tree, WAR, SBOM, all package bytes and checksums. Only
   receipt-declared evidence is uploaded; private execution logs stay on the
   disposable runner. Retain artifacts according to their sensitivity.
4. Once that workflow succeeds, dispatch `publish-release.yml` or
   `deb-packages.yml` on the tag with `tag` and `qualification_run_id` set to its
   run ID. Promotion downloads the current draft assets and trusted workflow
   artifact, re-runs verification against raw evidence, compares the complete
   candidate hashes, and checks current required CI results. It performs no
   Maven rebuild, package rebuild or stamping. Publication then freezes those
   assets using the repository's existing immutable-release/latest policy.

Missing runners, prerequisites, evidence, expired artifacts, changed draft
assets, a newer failed CI run, or any `releaseReady=false` result block
publication. A rebuilt candidate needs fresh qualification. A prior snapshot
receipt cannot qualify a later tag, even when the source changes appear small.
Both release workflows share a publication concurrency group so an automated
candidate rebuild cannot race final promotion.

The wider release plan still contains explicitly external or incomplete
acceptance work. This change does **not** turn that work green: those rows
continue to block readiness until their reviewed executable adapters and actual
evidence exist. Ordinary `evidence-contracts` tooling success cannot publish a
release. Existing published immutable releases remain read-only no-ops.
