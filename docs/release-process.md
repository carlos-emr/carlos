# CARLOS Release Process

CARLOS uses Calendar Versioning (CalVer), protected maintenance branches, and
immutable GitHub releases. This document is the source of truth for selecting a
target branch, preparing a release, creating tags, and forwarding fixes.

## Version format

Release versions and tags use the same value:

```text
YYYY.MM.PATCH[-alphaN|-betaN|-rcN]
```

Examples are `2026.08.0-alpha2`, `2026.08.0-rc1`, `2026.08.0`, and
`2026.08.1`. This is CalVer rather than strict Semantic Versioning: the year
and zero-padded month identify the release train, `PATCH` identifies a stable
maintenance release, and the optional qualifier identifies a prerelease.

Ongoing work always uses the next intended version with `-SNAPSHOT` and an SCM
tag of `HEAD`. Published commits use an exact version and an SCM tag identical
to the release tag. Snapshots are never tagged or published.

## Branch roles

| Branch | Purpose | Version state |
| --- | --- | --- |
| `develop` | Default integration branch for the next release train | Next `YYYY.MM.0-SNAPSHOT` |
| `release/YYYY.MM` | Stabilization and supported fixes for one release train | Next prerelease or patch `-SNAPSHOT` |
| `main` | Published releases for the current release train | Exact, non-SNAPSHOT release |

Normal features and fixes target `develop`. A high-priority bug or security fix
for a supported release starts from and targets its `release/YYYY.MM` branch.
Release-preparation PRs are the only application changes that target `main`.
All protected branches require a reviewed pull request.

While a release train is current, each alpha, beta, release candidate, and
stable release is promoted from its maintenance branch to `main` and tagged on
the resulting main commit. After `main` advances to a newer train, later patches
for an older supported train are tagged at the exact head of that train's
`release/YYYY.MM` branch.

## Forward and back merges

Fixes move forward by merge so Git retains their ancestry:

1. Merge the fix into the oldest affected supported `release/YYYY.MM` branch.
2. Create an integration branch from each newer target branch.
3. Merge the older line into the integration branch with `--no-ff --no-commit`.
4. Resolve version metadata in favor of the target branch, sign off the merge
   commit, and open a PR to the target branch.

Do not routinely cherry-pick fixes or merge a newer release line backward into
an older one. After publishing a release from `main`, back-merge the tagged main
commit into its maintenance branch before advancing that branch to its next
snapshot. Keep `develop` on its newer snapshot during every forward merge.

## Preparing and publishing a release

1. Freeze merges into the release source branch.
2. Open a preparation PR that changes the Maven project version from the
   planned snapshot to the exact release and changes `project.scm.tag` from
   `HEAD` to the same exact value.
3. Run and pass all required checks. Merge the PR with a merge commit.
4. Confirm the source branch head, `pom.xml` version, and SCM tag all match.
5. Create and push an annotated tag on that exact commit:

   ```bash
   git tag -a 2026.08.0-alpha2 -m "Release 2026.08.0-alpha2"
   git push origin 2026.08.0-alpha2
   ```

6. The tag workflow re-runs the full build and tests, creates a clean WAR and
   CycloneDX SBOM, generates signed provenance attestations, attaches and
   verifies checksums, then publishes the verified draft as an immutable GitHub
   release.
7. Confirm prerelease/latest classification and asset attestations, then unfreeze
   the source branch.
8. Back-merge the tagged commit and advance the maintenance branch to the next
   snapshot. Forward-merge the maintenance branch into newer lines.

Release tags are permanent declarations. They are annotated, protected, never
moved, deleted, or reused, and never serve as mutable aliases such as `latest`.
If publishing fails because of infrastructure, rerun the workflow for the same
tag. If the tagged code is defective, leave the tag unpublished and prepare the
next version identifier.

## Release assets and verification

Each GitHub release contains:

- `carlos-VERSION.war`
- `carlos-VERSION.war.sha256`
- `carlos-VERSION-cyclonedx.json`
- `carlos-VERSION-cyclonedx.json.sha256`

GitHub source archives supplement but do not replace the compiled WAR. Verify a
download before deployment:

```bash
sha256sum --check carlos-VERSION.war.sha256
sha256sum --check carlos-VERSION-cyclonedx.json.sha256
gh attestation verify carlos-VERSION.war --repo carlos-emr/carlos
gh attestation verify carlos-VERSION-cyclonedx.json --repo carlos-emr/carlos
```

Alpha, beta, and release-candidate versions are GitHub prereleases. Only stable
versions may be marked as the latest release. Published releases and their tags
and assets are immutable.

## Support and retirement

The active release line and any explicitly supported maintenance lines receive
high-priority security and correctness fixes. Document support changes in
`SECURITY.md`. Retire a maintenance branch only after its support window ends;
historical tags and releases remain available permanently.
