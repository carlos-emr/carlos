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

`FileUploadCheck.addFile` returns `UNSUCCESSFUL_SAVE` both for a duplicate checksum and for any failure it swallows. ON CML `LabUpload2Action` used to answer every such case with an empty `<outcome/>`. It now asks `FileUploadCheck.isFileRecorded`, which does not swallow failures. The action answers `uploadedPreviously` only when a checksum row exists; otherwise it answers `databaseNotStarted`, the existing retryable outcome, so a transient failure is never acknowledged as a duplicate. If the confirming lookup itself fails, the action also answers `databaseNotStarted`. The parser's `BufferedReader` is now closed.

`addFile` recorded the checksum before the lab was parsed and saved, and `ABCDParser.save` writes the report, patient, routing and result rows through separate DAOs. A failure part-way used to leave partial rows and the checksum committed, so a retry was answered `uploadedPreviously` although the lab was never fully stored. The CML action no longer claims through `addFile`: it calls the new `FileUploadCheck.storeIfNew`, which checks `isFileRecorded`, then records the checksum with the new, non-swallowing `FileUploadCheck.recordFile` and runs the parse and save in one READ_COMMITTED `TransactionTemplate`, which the `REQUIRED` DAO writes join. The checksum therefore exists exactly when the lab does: a failure rolls both back, so a retry stores the lab; a commit whose outcome is unknown left both or neither; and there is no separate cleanup step that could itself fail.

`storeIfNew` holds a per-content lock (one of a fixed set of stripes chosen by the checksum, shared with `addFile`) from the duplicate check until the upload and checksum have committed or rolled back. Every lab uploader claims through `addFile` or `storeIfNew`, so no other upload of the same bytes in the same application instance can see or claim the content in between, while uploads of different content proceed in parallel. The lock covers one application instance, not several servers.

## Inside-lab and PathNet uploads (PR #3915)

The inside-lab batch uploader (`InsideLabUpload2Action`) and the BC PathNet uploader (`bc.PathNet.pageUtil.LabUpload2Action`) claimed through `addFile` too, then parsed and stored outside any transaction:

- Inside-lab released the checksum lock before the handler ran, so a concurrent upload of the same file was told "Already uploaded" while the first could still fail. The HL7 handlers compensate on failure with `MessageUploader.clean`, which moves the file's rows and checksum to the recycle bin; the PDF and FHIR handlers do not, so a failed PDF or FHIR upload kept its checksum and was refused forever. A failing `clean` left the same stale checksum.
- PathNet answered `uploadedPreviously` for any `addFile` failure, wrote each message with no transaction (the code carried a "for future when transactional" note), kept the checksum after a failed message, and left the outcome empty for an unreadable batch.

Both now use `storeIfNew`. The inside-lab handler runs inside the transaction and links its rows to the uncommitted checksum id; a handler that returns null rejects the file and rolls everything back (`Invalid lab`), and one that throws does too (`Failed to upload HL7 lab`), so the file can be sent again. The HL7 handlers' own `clean` calls now run inside the same rolled-back transaction, so they leave no recycle-bin entries either. `ProviderLabRouting.routeMagic` joins the transaction, which reads at READ_COMMITTED as that method requires under MariaDB's snapshot isolation. PathNet stores every message of a batch in the transaction: a failing message rolls back the ones before it and the checksum, and a batch with no messages is rejected; both answer `exception`, and the archive copy is still written as before. `LogAction` audit entries are written asynchronously outside the transaction, as before.

Unit tests cover each branch: `FileUploadCheckUnitTest` (11 tests, including `storeIfNew` stored, duplicate, rejected, thrown, lookup-failure and cannot-start cases), `LabUpload2ActionUnitTest` for CML (9), `InsideLabUpload2ActionUnitTest` (6) and PathNet `LabUpload2ActionUnitTest` (6), all run under Spring's real commit and rollback lifecycle through the shared `RecordingTransactionManager` test helper. All 815 unit tests in the `lab` packages pass.

The rollback was also checked on a real database, with the installed package from the section below. A `BEFORE INSERT` trigger on `labTestResults` raised an error for one marker test name in a synthetic ABCD file. The file was posted to `lab/CMLlabUpload`, then posted again after the trigger was dropped.

| Package | Failed save leaves | Retry answers |
|---|---|---|
| before (`4a5578e3`) | report, patient and both routing rows, plus the checksum; no results | `uploadedPreviously`, and the lab stays partial |
| after (`46b355cd`, and again with the checksum recorded inside the lab transaction) | nothing, including the checksum | `uploaded`, with every row stored once |

The probe was a one-off and is not committed; it removed its rows, archived files and trigger.

The inside-lab path was checked the same way, with the `storeIfNew` classes deployed onto the installed package. A synthetic CML HL7 lab was uploaded through the Inbox hub popup while a trigger failed its `patientLabRouting` insert, after its message, info and provider routing rows had been written in the transaction. The upload reported "Failed to upload HL7 lab" and left no message, info or routing rows, no checksum and no recycle-bin entries. With the trigger dropped, the same file reported "Uploaded successfully" and every row was stored. PathNet is a BC uploader and was not exercised on this Ontario install; its unit tests cover it.

## Installed-DEB browser validation

`scripts/lab-upload-playwright-checks.js` (`npm run test:lab-upload-playwright`, manifest entry `lab-upload`, tier `core`) drives the packaged front door. It uploads a synthetic CML HL7 message through the Inbox hub's inside-lab uploader popup and checks that the message is filed once and routed to the run's FAKE- patient. It then uploads the same file again and checks that the page reports "Already uploaded" without adding a second row. Finally it posts to `lab/CMLlabUpload`: a wrong key must answer `accessDenied`, and, when `CML_UPLOAD_KEY` is set to the server's value, a recorded file must answer `uploadedPreviously`. Every row it creates is removed, found by accession number and by the run-stamped saved file name. With `LAB_UPLOAD_DOCUMENT_STORE` set to the server's `DOCUMENT_DIR`, it also deletes its archived `LabUpload.lab-upload-probe-<stamp>.hl7.*` files, and it fails if it finds none. The manifest scopes it to Ontario (`provinces: ["ON"]`), because `lab/CMLlabUpload` is an Ontario uploader.

Validated on 2026-09-24 against a fresh `2026.09.0~snapshot23` install. The three packages were first built from this branch at `4a5578e3`. After the atomic-save change, `carlos-emr` was rebuilt at `46b355cd` and reinstalled over the same system. `lab-upload` and the rollback probe were repeated on that rebuild; the adjacent checks named below ran on the first build. They were installed per [deb-install-validation.md](deb-install-validation.md) sections 3 to 6: Ontario, self-signed TLS, demo data, forced first-login reset, `carlos-ctl check` all OK. `CML_UPLOAD_KEY` was set in `/etc/carlos-emr/carlos.properties` before the run, and `LAB_UPLOAD_DOCUMENT_STORE` pointed at `DOCUMENT_DIR`. Results: `lab-upload` passed all three steps, including the keyed `uploadedPreviously` half, and left no rows or files behind. Pointing `LAB_UPLOAD_DOCUMENT_STORE` at the wrong directory makes cleanup fail, as intended, while the rows are still removed. The adjacent checks `login`, `inboxhub-filters`, `inbox-preview-acknowledge`, `lab-acknowledge` and `lab-pdf-footer` passed on the same install.

`document-upload` first failed on its chart-navigation step, for a reason unrelated to lab upload. In the encounter left nav (`LeftNavBarDisplay.jsp`), each item's date column is a `z-index:100` span floated over the truncated title, and it holds a "..." link with the same `onclick`. With a long document title, the date column covers the centre of the title link, so Playwright's centre-point click was refused as intercepted; a user clicking the title still opens the document. `clickOpensPopup` now accepts a click `position`, and the check clicks near the start of the title (`{ x: 4, y: 8 }`); it passes on the installed package.

`inboxhub-filters` passed on the first run against the fresh demo data. Re-run later on the same install, it failed ("review status: New returned row HL7:44, which the unfiltered list does not contain"), and it fails identically with the package's own classes reinstalled, so it is not this change. Earlier runs of the acknowledgement checks had filed demo lab 44 and updated other routing rows on this install; a fresh install is the reliable baseline for that check.
