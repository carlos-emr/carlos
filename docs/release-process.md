# CARLOS Release Process

CARLOS uses Calendar Versioning (CalVer), protected maintenance branches, and
immutable GitHub releases. This document is the source of truth for selecting a
target branch, preparing a release, creating tags, and forwarding fixes.

## Version format

Exact release versions and tags use the same value:

```text
YYYY.MM.PATCH[-alphaN|-betaN|-rcN]
```

Working snapshot versions append `-SNAPSHOT` to the intended exact version:

```text
YYYY.MM.PATCH[-alphaN|-betaN|-rcN]-SNAPSHOT
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

`main` is not an integration branch and never moves to a `-SNAPSHOT` version.
Release-infrastructure or documentation corrections may land there when they
are required to operate the release process, but those commits are not releases
and must not be tagged with an existing version. Application development stays
on `develop` or the applicable maintenance branch.

For example, while 2026.08 is being stabilized and 2026.09 is under active
development, the intended states are:

| Branch | Example state |
| --- | --- |
| `develop` | `2026.09.0-SNAPSHOT`, SCM tag `HEAD` |
| `release/2026.08` | `2026.08.0-alpha3-SNAPSHOT`, SCM tag `HEAD` |
| `main` | Exact most recently promoted 2026.08 release metadata |

Advancing a maintenance branch to its next snapshot is bookkeeping for future
work; it does not by itself justify publishing that prerelease. Before the
stable release, the planned snapshot may advance through alpha, beta, or release
candidate identifiers. After `2026.08.0` is stable, the next maintenance version
is `2026.08.1-SNAPSHOT`, published later as `2026.08.1`.

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

For a forward merge into `develop`, retain the newer project version on
`develop` and `project.scm.tag=HEAD`. For a merge into a newer maintenance line,
retain the planned snapshot on that line. Never allow version metadata from an
older line to move a newer line backward.

Versioned Flyway migrations require the same care. Files present in a published
tag are checksum-frozen. If two lines independently used the same migration
number, keep every published file byte-for-byte unchanged and renumber only the
unreleased migration on the newer target line. Update the migration inventories
and validate the combined common/province sequences before merging.

Do not routinely cherry-pick fixes or merge a newer release line backward into
an older one. After publishing a release from `main`, back-merge the tagged main
commit into its maintenance branch before advancing that branch to its next
snapshot. Keep `develop` on its newer snapshot during every forward merge.

Merge commits must carry a DCO sign-off. If a true forward or back merge connects
older commits that the automated DCO job cannot verify individually, an
authorized maintainer may post the exact repository fallback phrase only after
reviewing the connected history:

```text
Confirming DCO sign off for all commits
```

## Supported-release fix cycle

Use this cycle for a high-priority correctness or security fix found in an alpha,
beta, release candidate, or stable supported release. Keep embargoed security
work in the private security-advisory workflow until coordinated disclosure; the
branch-target rules below apply when the fix is landed.

1. Create a topic branch from the oldest affected supported
   `release/YYYY.MM` branch, not from `main` or `develop`.
2. Implement and test the fix without removing the maintenance branch
   `-SNAPSHOT` version or changing its SCM tag from `HEAD`.
3. Open a reviewed PR back to that same maintenance branch.
4. After it merges, forward-merge the maintenance branch into each newer
   supported line and finally `develop`, preserving each target version and SCM
   metadata. Do not ask contributors to duplicate or cherry-pick the fix.
5. Continue accumulating approved fixes until the release owner chooses the
   next prerelease or patch milestone. A snapshot name is an intention, not an
   obligation to publish immediately.
6. Publish the milestone using the release preparation and tagging process
   below. Then back-merge and advance the maintenance branch to its next
   planned snapshot.

## Preparing and publishing a release

First choose the publication target:

- While the train is current on `main`, create a preparation branch from its
  `release/YYYY.MM` branch and open the preparation PR to `main`. Tag the
  resulting `main` merge commit.
- After `main` has advanced to a newer train, prepare an older supported patch
  through a PR to that older `release/YYYY.MM` branch and tag the resulting
  maintenance-branch head. Never merge an older train backward into `main`.

Then:

1. Freeze merges into the release source branch.
2. On the preparation branch, change the Maven project version from the planned
   snapshot to the exact release and change `project.scm.tag` from `HEAD` to the
   same exact value.
3. Run and pass all required checks. Merge the preparation PR with a merge
   commit into the publication target selected above.
4. Fetch the merged target and confirm that its head commit, `pom.xml` version,
   and SCM tag are the exact values intended for publication.
5. Create and push an annotated tag on that exact commit. For example:

   ```bash
   RELEASE_TAG=2026.08.0-alpha3
   RELEASE_COMMIT="<verified-publication-target-sha>"
   git tag -a "$RELEASE_TAG" "$RELEASE_COMMIT" -m "Release $RELEASE_TAG"
   test "$(git rev-parse "${RELEASE_TAG}^{commit}")" = "$RELEASE_COMMIT"
   git push origin "$RELEASE_TAG"
   ```

6. The tag workflow re-runs the full build and tests, creates a clean WAR and
   CycloneDX SBOM, generates signed provenance attestations, attaches and
   verifies checksums, then publishes the verified draft as an immutable GitHub
   release.
7. Confirm prerelease/latest classification and asset attestations, then unfreeze
   the source branch.
8. If publication occurred from `main`, back-merge the tagged commit into its
   maintenance branch. Advance that branch to the next planned snapshot through
   a reviewed PR, then forward-merge the maintenance branch into newer lines.
   If publication occurred directly from an older maintenance branch, advance
   that branch to its next snapshot and forward-merge it in the same way.

Release tags are permanent declarations. They are annotated, protected, never
moved, deleted, or reused, and never serve as mutable aliases such as `latest`.
If publishing fails because of infrastructure, rerun the workflow for the same
tag. If the tagged code is defective, leave the tag unpublished and prepare the
next version identifier.

For a retry, dispatch the release workflow with the existing tag. Do not create
a replacement tag, move the original tag, or tag a newer branch head with the
same version. The workflow accepts an ancestor only when a prior unsuccessful
tag-push run provides evidence that this is a retry of the immutable tag.

## Pull-request and CI coverage

Pull requests to `develop`, `main`, and `release/*` run the repository build,
test, JSP compilation, DCO, commit-format, dependency review, database, and
security checks as applicable. Trusted same-repository PRs also run SonarCloud.
The publication workflow is separate: it runs only for a protected CalVer tag or
an explicit retry of an existing tag, rebuilds from the tagged commit, and never
publishes a snapshot.

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
