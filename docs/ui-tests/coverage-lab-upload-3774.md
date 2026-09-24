# Lab upload coverage for issue #3774

This release-targeted slice executes four changed production paths that had zero covered changed lines in the release audit: the shared upload writer, inside-lab batch action, BC PathNet uploader, and ON CML uploader.

The focused Java run now passes 17 tests with no failures or skips. The line counts below were measured on the original 16-test run: after deleting the previous JaCoCo execution file, it covered 36/54 changed executable lines in `Utilities`, 6/13 in `InsideLabUpload2Action`, 13/27 in BC `LabUpload2Action`, and 10/30 in ON CML `LabUpload2Action`. The comparison is against alpha12 `main` (`d182f254cd`) and measures focused execution, not whole-suite coverage.

The tests use real temporary document directories. They verify upload content, generated names, stream closure, partial-file cleanup, HL7 message splitting, access and key gates, duplicate handling, and archive contents. PathNet and CML parsing/storage collaborators are mocked at their external boundaries, while the action's stream and file-writing paths execute.

Code review found that BC PathNet called `reset()` on a `Files.newInputStream` after `FileUploadCheck.addFile` consumed it. That stream does not support reset, so a successful duplicate check failed before parsing or archiving. The action now reads the validated upload once into a snapshot and gives the duplicate check, parser, and archive writer their own stream over it, so all three see the same bytes even if the temporary file changes. A regression consumes the complete first stream and rewrites the temporary file, then verifies the parser and archive still receive the original, hashed content and that the file stream is closed. The `reset()` also ran before the duplicate branch, so a rejected duplicate reported `exception` instead of `uploadedPreviously`; a second PathNet test covers that path and checks nothing is parsed or archived. Both tests fail against the pre-fix action.

Reproduce with:

```bash
rm -f target/jacoco.exec
mvn -B -Dtest=UtilitiesUploadUnitTest,InsideLabUpload2ActionUnitTest,io.github.carlos_emr.carlos.lab.ca.bc.PathNet.pageUtil.LabUpload2ActionUnitTest,io.github.carlos_emr.carlos.lab.ca.on.CML.Upload.LabUpload2ActionUnitTest \
  test org.jacoco:jacoco-maven-plugin:0.8.14:report
```

The release audit script in PR #3781 can intersect `target/site/jacoco/jacoco.xml` with `d182f254cd` through this branch's head.

## CML duplicate outcome (PR #3915)

`FileUploadCheck.addFile` returns `UNSUCCESSFUL_SAVE` both for a duplicate checksum and for any failure it swallows. ON CML `LabUpload2Action` used to answer every such case with an empty `<outcome/>`. It now asks `FileUploadCheck.isFileRecorded`, which does not swallow failures. The action answers `uploadedPreviously` only when a checksum row exists; otherwise it answers `databaseNotStarted`, the existing retryable outcome, so a transient failure is never acknowledged as a duplicate. The parser's `BufferedReader` is now closed. Unit tests cover each branch and fail against the previous action: `LabUpload2ActionUnitTest` (5 tests) and `FileUploadCheckUnitTest` (3 tests).

## Installed-DEB browser validation

`scripts/lab-upload-playwright-checks.js` (`npm run test:lab-upload-playwright`, manifest entry `lab-upload`, tier `core`) drives the packaged front door. It uploads a synthetic CML HL7 message through the Inbox hub's inside-lab uploader popup and checks that the message is filed once and routed to the run's FAKE- patient. It then uploads the same file again and checks that the page reports "Already uploaded" without adding a second row. Finally it posts to `lab/CMLlabUpload`: a wrong key must answer `accessDenied`, and, when `CML_UPLOAD_KEY` is set to the server's value, a recorded file must answer `uploadedPreviously`. Every row it creates is removed, found by accession number and by the run-stamped saved file name.

Validated on 2026-09-24 against a fresh `2026.09.0~snapshot23` install. The three packages were built from this branch at `4a5578e3`, whose duplicate check is behaviourally identical to the later `isFileRecorded` refactor, and the probe script from the branch head was run. They were installed per [deb-install-validation.md](deb-install-validation.md) sections 3 to 6: Ontario, self-signed TLS, demo data, forced first-login reset, `carlos-ctl check` all OK. `CML_UPLOAD_KEY` was set in `/etc/carlos-emr/carlos.properties` before the run. Results: `lab-upload` passed all three steps, including the keyed `uploadedPreviously` half, and left no residue. The adjacent checks `login`, `inboxhub-filters`, `inbox-preview-acknowledge`, `lab-acknowledge` and `lab-pdf-footer` passed on the same install.

`document-upload` failed on its chart-navigation step, for a reason unrelated to lab upload. In the encounter left nav (`LeftNavBarDisplay.jsp`), each item's date column is a `z-index:100` span floated over the truncated title, and it holds a "..." link with the same `onclick`. With a long document title, the date column covers the centre of the title link, so Playwright's centre-point click is refused as intercepted. A user clicking the title still opens the document. Clicking near the start of the title (`position: { x: 4, y: 8 }`) passes the whole check. That harness change is left out of this PR.
