<!--
  Thank you for contributing to CARLOS EMR! We appreciate your time and effort.
  Please fill out the sections below to help reviewers understand your changes.
  Normal work targets develop. Supported-release fixes target release/YYYY.MM,
  while current-train release preparation and narrowly scoped release-infrastructure
  corrections may target main. See docs/release-process.md.
-->

## Description

<!-- What does this PR do and why? Link the motivation, not just the mechanics. -->

## Related Issues

<!-- Link related issues. Use "Fixes #123" to auto-close, or "Related to #123" to link without closing. -->

## Target Branch

<!--
Explain why this PR targets develop, release/YYYY.MM, or main.
- Normal work: topic branch from develop -> develop.
- Supported fix: topic branch from the oldest affected release/YYYY.MM -> that same branch.
- Current-train release preparation: maintenance-line preparation branch -> main.
- Older supported release preparation after main advances: preparation branch -> that release/YYYY.MM.
- Necessary release-infrastructure correction: topic branch from main -> main, with no application change or tag.
Maintainers forward-merge supported fixes; do not create duplicate cherry-pick PRs.
-->

## How Was This Tested?

<!-- How did you verify this works? Commands run, manual steps, or other evidence. -->

## Screenshots

<!-- If this PR includes visual changes, please add before/after screenshots. Delete this section if not applicable. -->

## Checklist

- [ ] My commits are signed off for the [DCO](https://developercertificate.org/) (`git commit -s`)
- [ ] My commits follow [Conventional Commits](https://www.conventionalcommits.org/) format, or I've written clear commit messages and will use the format next time
- [ ] I have not included any patient data (PHI) in this PR
- [ ] I have added tests for new functionality, or this change doesn't need new tests
- [ ] I have read the [contributing guide](https://github.com/carlos-emr/carlos/blob/develop/CONTRIBUTING.md)
- [ ] I selected the target branch according to the [release process](https://github.com/carlos-emr/carlos/blob/develop/docs/release-process.md)
- [ ] I preserved the target branch version/SCM metadata, or this is an explicit release-preparation PR
- [ ] I did not modify a Flyway migration that exists in a published release tag
