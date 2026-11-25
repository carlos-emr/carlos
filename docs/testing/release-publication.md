# Release publication and frozen candidates

A tag push builds the WAR and SBOM into a draft release and dispatches
`deb-packages.yml`. The Debian workflow attaches the application, DrugRef and
pinned CLI packages and their checksums, with provenance attestations recorded
by GitHub. It verifies the complete remote asset set and publishes the same
release automatically as its final step.

The final check uses a clean checkout of the tag because packaging updates its
changelog and creates staging files. It validates canonical asset names, versions,
checksums, provenance and the application package's contents against the release
WAR. A final server digest check runs immediately before publication. Failed
builds or checks leave the draft available for retry. Successful published
releases retain the existing immutable-release and prerelease/latest behavior.

Rerun failed Debian jobs, or dispatch the build on the existing tag's own ref.

```bash
gh workflow run deb-packages.yml --ref "refs/tags/$RELEASE_TAG" -f "tag=$RELEASE_TAG"
```

## Optional qualification of existing frozen drafts

The `qualification_run_id` input remains available for an existing frozen draft
whose source-bound qualification has completed. This route verifies the recorded
assets and their evidence before publication.

1. Start with an existing complete unpublished draft whose annotated tag and
   Maven/SCM version match. Its WAR, SBOM, packages and checksums must already be
   attached, and their provenance attestations must be available. Debian
   packaging compares the final WAR against its input, and the package's two
   explicit resource transforms are verified
   against the exploded application in the `.deb`.
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
assets, a newer failed CI run, or any `releaseReady=false` result block this
optional promotion. A rebuilt candidate needs fresh qualification. A prior snapshot
receipt cannot qualify a later tag, even when the source changes appear small.
Both release workflows share a publication concurrency group so an automated
candidate rebuild cannot race final promotion.

Missing path-to-test mappings are currently advisory under CARLOS #6336. The scope command
runs with `--migration-integrity-only` during qualification and retains the full
inventory with `ownershipRequired: false`. Main-only migration dispositions and
published migration byte integrity remain required. Restoring the ownership
gate requires a reviewed change to the qualification command and its assertions.
[Ownership follow-up](https://github.com/carlos-emr/carlos/issues/6336)

The optional qualification plan still contains external or incomplete acceptance
work. Its readiness requires the reviewed executable adapters and their actual
evidence. Ordinary `evidence-contracts` tooling success cannot authorize optional
qualified promotion. Published immutable releases are handled as completed runs.
