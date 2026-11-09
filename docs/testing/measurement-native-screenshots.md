# Installed measurement and chart screenshots

**126 before/after Playwright screenshot pairs** across installed Ontario and BC packages. The [renderer gallery](measurement-chart-screenshots.md) adds 45 pairs. Supplemental repair/routing/eForm screenshots and five-profile server-locale PNG/PDF controls are linked below. All clinical fixtures are synthetic and owned by the tests.

Installed candidate: `989f0eb94a529c2cdb30f3c6b93e8b0ca2062389`; target `release/2026.08`, release base `74f240a4b9f`. The original 98 native pairs use baseline `9004536dc8314d5f50542b48905ca3ca4099a916`. The 24 CML pairs and supplemental locale controls use `a7b22e9b63f9481dcb34ea776a31dfba254cb9cb` as their explicitly identified before revision. Four partial-review pairs use intermediate revision `a3934f88a44e` before the final acknowledgment coverage fix.

The final Ubuntu 26.04 packages pass all 18 checks in each province. Runtime verification checks 6,216 deployed classes, 253 dependency jars and their resources before and after qualification. The two Debian build/JNDI property rewrites are verified against the exact `.deb` payload. [Artifact/package/helper hashes](../images/pr6102/native/manifest.json) · [Acceptance register](measurement-integrity-acceptance.md)

## What the comparisons establish

- CML before runs fail four clinical assertions: both values remain active after correction and an older delivery demotes the current selection/projection. After runs retain only the correction, preserve every raw source and the original acknowledgment, reject exact replay without side effects, and keep the correction current after older delivery.
- Qualified, qualitative and filter-excluded results retain their source literals in histories, cumulative views and actual PDFs; only exact numeric observations become graph points. Independent geometry, numeric controls, cross-patient isolation and unchanged-read checks pass.
- All three CDM reports retain each originating tab’s definitions and counts.
- Eight native geometry cases pass per province, including visible single points and constants through 45 million. Baseline renderer errors remain failures in their retained logs.
- Unlink passes real MariaDB late-audit rollback, normal retry, stale 409, independent/automatic provider controls and the later-delivery unmatch fence. Active projections are archived with exact untruncated provenance; existing superseded archives remain retained.
- Repair passes read-only preview, physical late-audit rollback, retry, fresh no-op preview, stale-plan 409 and exact physical provenance. Real repair/import and unlink/import transactions are proven waiting on the same MariaDB coordination row, then complete without losing the correction or restoring an explicit unmatch.
- Missing clinical revision time keeps raw HTML/PDF and complete confirmed-chain unlink available, with a visible review warning. Incomplete patient identity never establishes peer membership or automatic projection.
- Controlled CLS contention waits on the source-wide coordination row while an unrelated CML upload completes; measured latencies are in each workflow log.
- Real renderer outage/retry, mixed native Chromium work, bounded queues, PDF fairness, queued interruption, cleanup and five server-locale PNG/PDF profiles pass. Actual eForm PDF, JSTL/EL, XLSX/CSV, selected deletion/archive, plot semantics and range-print controls also pass.

No server response is fabricated. Capture helpers forward actual response bytes; screenshots are not edited, resized or composited. The earlier renderer gallery’s model, action, exact-value parser, browser renderer and drawing-script bytes match the final WAR; its component comparison is recorded in the manifest.

## ON Playwright comparisons

### CML correction, older delivery and replay

| Case | Before | After |
| --- | --- | --- |
| Original before review | ![Before Original before review](../images/pr6102/native/on/before/cml-version/001-Original-before-review.png) | ![After Original before review](../images/pr6102/native/on/after/cml-version/001-Original-before-review.png) |
| CORRECTION ingestion Inbox selected correction | ![Before CORRECTION ingestion Inbox selected correction](../images/pr6102/native/on/before/cml-version/002-CORRECTION-ingestion-Inbox-selected-correction.png) | ![After CORRECTION ingestion Inbox selected correction](../images/pr6102/native/on/after/cml-version/002-CORRECTION-ingestion-Inbox-selected-correction.png) |
| CORRECTION ingestion historical ORIGINAL | ![Before CORRECTION ingestion historical ORIGINAL](../images/pr6102/native/on/before/cml-version/003-CORRECTION-ingestion-historical-ORIGINAL.png) | ![After CORRECTION ingestion historical ORIGINAL](../images/pr6102/native/on/after/cml-version/003-CORRECTION-ingestion-historical-ORIGINAL.png) |
| CORRECTION ingestion historical CORRECTION | ![Before CORRECTION ingestion historical CORRECTION](../images/pr6102/native/on/before/cml-version/004-CORRECTION-ingestion-historical-CORRECTION.png) | ![After CORRECTION ingestion historical CORRECTION](../images/pr6102/native/on/after/cml-version/004-CORRECTION-ingestion-historical-CORRECTION.png) |
| CORRECTION ingestion chart current accession | ![Before CORRECTION ingestion chart current accession](../images/pr6102/native/on/before/cml-version/005-CORRECTION-ingestion-chart-current-accession.png) | ![After CORRECTION ingestion chart current accession](../images/pr6102/native/on/after/cml-version/005-CORRECTION-ingestion-chart-current-accession.png) |
| CORRECTION ingestion chart current | ![Before CORRECTION ingestion chart current](../images/pr6102/native/on/before/cml-version/006-CORRECTION-ingestion-chart-current.png) | ![After CORRECTION ingestion chart current](../images/pr6102/native/on/after/cml-version/006-CORRECTION-ingestion-chart-current.png) |
| OLDER ingestion Inbox selected correction | ![Before OLDER ingestion Inbox selected correction](../images/pr6102/native/on/before/cml-version/007-OLDER-ingestion-Inbox-selected-correction.png) | ![After OLDER ingestion Inbox selected correction](../images/pr6102/native/on/after/cml-version/007-OLDER-ingestion-Inbox-selected-correction.png) |
| OLDER ingestion historical ORIGINAL | ![Before OLDER ingestion historical ORIGINAL](../images/pr6102/native/on/before/cml-version/008-OLDER-ingestion-historical-ORIGINAL.png) | ![After OLDER ingestion historical ORIGINAL](../images/pr6102/native/on/after/cml-version/008-OLDER-ingestion-historical-ORIGINAL.png) |
| OLDER ingestion historical CORRECTION | ![Before OLDER ingestion historical CORRECTION](../images/pr6102/native/on/before/cml-version/009-OLDER-ingestion-historical-CORRECTION.png) | ![After OLDER ingestion historical CORRECTION](../images/pr6102/native/on/after/cml-version/009-OLDER-ingestion-historical-CORRECTION.png) |
| OLDER ingestion historical OLDER | ![Before OLDER ingestion historical OLDER](../images/pr6102/native/on/before/cml-version/010-OLDER-ingestion-historical-OLDER.png) | ![After OLDER ingestion historical OLDER](../images/pr6102/native/on/after/cml-version/010-OLDER-ingestion-historical-OLDER.png) |
| OLDER ingestion chart current accession | ![Before OLDER ingestion chart current accession](../images/pr6102/native/on/before/cml-version/011-OLDER-ingestion-chart-current-accession.png) | ![After OLDER ingestion chart current accession](../images/pr6102/native/on/after/cml-version/011-OLDER-ingestion-chart-current-accession.png) |
| OLDER ingestion chart current | ![Before OLDER ingestion chart current](../images/pr6102/native/on/before/cml-version/012-OLDER-ingestion-chart-current.png) | ![After OLDER ingestion chart current](../images/pr6102/native/on/after/cml-version/012-OLDER-ingestion-chart-current.png) |

[Before receipts](../images/pr6102/native/on/before/cml-version/captures.json) · [After receipts](../images/pr6102/native/on/after/cml-version/captures.json) · [After workflow log](../images/pr6102/native/on/after/cml-version/workflow.log)

### Laboratory literals, reports and charts

| Case | Before | After |
| --- | --- | --- |
| Unrelated-patient Create Lab control | ![Before Unrelated-patient Create Lab control](../images/pr6102/native/on/before/qualified/001-ViewCreateLab.png) | ![After Unrelated-patient Create Lab control](../images/pr6102/native/on/after/qualified/001-ViewCreateLab.png) |
| Create Lab: 2021 qualified/qualitative panel | ![Before Create Lab: 2021 qualified/qualitative panel](../images/pr6102/native/on/before/qualified/002-ViewCreateLab.png) | ![After Create Lab: 2021 qualified/qualitative panel](../images/pr6102/native/on/after/qualified/002-ViewCreateLab.png) |
| Create Lab: 2022 Negative observation | ![Before Create Lab: 2022 Negative observation](../images/pr6102/native/on/before/qualified/003-ViewCreateLab.png) | ![After Create Lab: 2022 Negative observation](../images/pr6102/native/on/after/qualified/003-ViewCreateLab.png) |
| Create Lab: 2023 exact numeric observation | ![Before Create Lab: 2023 exact numeric observation](../images/pr6102/native/on/before/qualified/004-ViewCreateLab.png) | ![After Create Lab: 2023 exact numeric observation](../images/pr6102/native/on/after/qualified/004-ViewCreateLab.png) |
| Create Lab: 2025 qualified observation | ![Before Create Lab: 2025 qualified observation](../images/pr6102/native/on/before/qualified/005-ViewCreateLab.png) | ![After Create Lab: 2025 qualified observation](../images/pr6102/native/on/after/qualified/005-ViewCreateLab.png) |
| Create Lab: 2026 exact numeric observation | ![Before Create Lab: 2026 exact numeric observation](../images/pr6102/native/on/before/qualified/006-ViewCreateLab.png) | ![After Create Lab: 2026 exact numeric observation](../images/pr6102/native/on/after/qualified/006-ViewCreateLab.png) |
| Hemoglobin: exact points and excluded observations | ![Before Hemoglobin: exact points and excluded observations](../images/pr6102/native/on/before/qualified/007-ViewLabValuesGraph.png) | ![After Hemoglobin: exact points and excluded observations](../images/pr6102/native/on/after/qualified/007-ViewLabValuesGraph.png) |
| Hematocrit: wholly qualified result | ![Before Hematocrit: wholly qualified result](../images/pr6102/native/on/before/qualified/008-ViewLabValuesGraph.png) | ![After Hematocrit: wholly qualified result](../images/pr6102/native/on/after/qualified/008-ViewLabValuesGraph.png) |
| Sodium: qualitative result history/plot | ![Before Sodium: qualitative result history/plot](../images/pr6102/native/on/before/qualified/009-ViewLabValuesGraph.png) | ![After Sodium: qualitative result history/plot](../images/pr6102/native/on/after/qualified/009-ViewLabValuesGraph.png) |
| Glucose: filter-excluded Pending result | ![Before Glucose: filter-excluded Pending result](../images/pr6102/native/on/before/qualified/010-ViewLabValuesGraph.png) | ![After Glucose: filter-excluded Pending result](../images/pr6102/native/on/after/qualified/010-ViewLabValuesGraph.png) |
| Cholesterol: numeric compatibility control | ![Before Cholesterol: numeric compatibility control](../images/pr6102/native/on/before/qualified/011-ViewLabValuesGraph.png) | ![After Cholesterol: numeric compatibility control](../images/pr6102/native/on/after/qualified/011-ViewLabValuesGraph.png) |
| Raw report: 2021-01-01 | ![Before Raw report: 2021-01-01](../images/pr6102/native/on/before/qualified/012-ViewLabDisplay.png) | ![After Raw report: 2021-01-01](../images/pr6102/native/on/after/qualified/012-ViewLabDisplay.png) |
| Raw report: 2022-05-07 | ![Before Raw report: 2022-05-07](../images/pr6102/native/on/before/qualified/013-ViewLabDisplay.png) | ![After Raw report: 2022-05-07](../images/pr6102/native/on/after/qualified/013-ViewLabDisplay.png) |
| Raw report: 2023-12-29 | ![Before Raw report: 2023-12-29](../images/pr6102/native/on/before/qualified/014-ViewLabDisplay.png) | ![After Raw report: 2023-12-29](../images/pr6102/native/on/after/qualified/014-ViewLabDisplay.png) |
| Raw report: 2025-10-02 | ![Before Raw report: 2025-10-02](../images/pr6102/native/on/before/qualified/015-ViewLabDisplay.png) | ![After Raw report: 2025-10-02](../images/pr6102/native/on/after/qualified/015-ViewLabDisplay.png) |
| Raw report: 2026-11-01 | ![Before Raw report: 2026-11-01](../images/pr6102/native/on/before/qualified/016-ViewLabDisplay.png) | ![After Raw report: 2026-11-01](../images/pr6102/native/on/after/qualified/016-ViewLabDisplay.png) |
| Cumulative grid: source case and Pending row | ![Before Cumulative grid: source case and Pending row](../images/pr6102/native/on/before/qualified/017-ViewCumulativeLabValues3.png) | ![After Cumulative grid: source case and Pending row](../images/pr6102/native/on/after/qualified/017-ViewCumulativeLabValues3.png) |
| Cumulative row history: source literals | ![Before Cumulative row history: source literals](../images/pr6102/native/on/before/qualified/018-ViewCumulativeLabValues.png) | ![After Cumulative row history: source literals](../images/pr6102/native/on/after/qualified/018-ViewCumulativeLabValues.png) |

[Before receipts](../images/pr6102/native/on/before/qualified/captures.json) · [After receipts](../images/pr6102/native/on/after/qualified/captures.json) · [After workflow log](../images/pr6102/native/on/after/qualified/workflow.log)

### CDM two-tab definitions

| Case | Before | After |
| --- | --- | --- |
| Guideline: original tab A | ![Before Guideline: original tab A](../images/pr6102/native/on/before/cdm/001-InitializePatientsMetGuidelineCDMReport.png) | ![After Guideline: original tab A](../images/pr6102/native/on/after/cdm/001-InitializePatientsMetGuidelineCDMReport.png) |
| Guideline: second tab B control | ![Before Guideline: second tab B control](../images/pr6102/native/on/before/cdm/002-InitializePatientsMetGuidelineCDMReport.png) | ![After Guideline: second tab B control](../images/pr6102/native/on/after/cdm/002-InitializePatientsMetGuidelineCDMReport.png) |
| Range: original tab A | ![Before Range: original tab A](../images/pr6102/native/on/before/cdm/003-InitializePatientsInAbnormalRangeCDMReport.png) | ![After Range: original tab A](../images/pr6102/native/on/after/cdm/003-InitializePatientsInAbnormalRangeCDMReport.png) |
| Range: second tab B control | ![Before Range: second tab B control](../images/pr6102/native/on/before/cdm/004-InitializePatientsInAbnormalRangeCDMReport.png) | ![After Range: second tab B control](../images/pr6102/native/on/after/cdm/004-InitializePatientsInAbnormalRangeCDMReport.png) |
| Frequency: original tab A | ![Before Frequency: original tab A](../images/pr6102/native/on/before/cdm/005-InitializeFrequencyOfRelevantTestsCDMReport.png) | ![After Frequency: original tab A](../images/pr6102/native/on/after/cdm/005-InitializeFrequencyOfRelevantTestsCDMReport.png) |
| Frequency: second tab B control | ![Before Frequency: second tab B control](../images/pr6102/native/on/before/cdm/006-InitializeFrequencyOfRelevantTestsCDMReport.png) | ![After Frequency: second tab B control](../images/pr6102/native/on/after/cdm/006-InitializeFrequencyOfRelevantTestsCDMReport.png) |

[Before receipts](../images/pr6102/native/on/before/cdm/captures.json) · [After receipts](../images/pr6102/native/on/after/cdm/captures.json) · [After workflow log](../images/pr6102/native/on/after/cdm/workflow.log)

### Unlink and later delivery

| Case | Before | After |
| --- | --- | --- |
| Invalid upload: first rejection control | ![Before Invalid upload: first rejection control](../images/pr6102/native/on/before/unlink/001-insideLabUpload.png) | ![After Invalid upload: first rejection control](../images/pr6102/native/on/after/unlink/001-insideLabUpload.png) |
| Invalid upload: retry rejection control | ![Before Invalid upload: retry rejection control](../images/pr6102/native/on/before/unlink/002-insideLabUpload.png) | ![After Invalid upload: retry rejection control](../images/pr6102/native/on/after/unlink/002-insideLabUpload.png) |
| Original source upload | ![Before Original source upload](../images/pr6102/native/on/before/unlink/003-insideLabUpload.png) | ![After Original source upload](../images/pr6102/native/on/after/unlink/003-insideLabUpload.png) |
| Exact duplicate rejection control | ![Before Exact duplicate rejection control](../images/pr6102/native/on/before/unlink/004-insideLabUpload.png) | ![After Exact duplicate rejection control](../images/pr6102/native/on/after/unlink/004-insideLabUpload.png) |
| Correction source upload | ![Before Correction source upload](../images/pr6102/native/on/before/unlink/005-insideLabUpload.png) | ![After Correction source upload](../images/pr6102/native/on/after/unlink/005-insideLabUpload.png) |
| Actual report before unlink | ![Before Actual report before unlink](../images/pr6102/native/on/before/unlink/006-ViewLabDisplay.png) | ![After Actual report before unlink](../images/pr6102/native/on/after/unlink/006-ViewLabDisplay.png) |
| Later delivery after unlink | ![Before Later delivery after unlink](../images/pr6102/native/on/before/unlink/007-insideLabUpload.png) | ![After Later delivery after unlink](../images/pr6102/native/on/after/unlink/007-insideLabUpload.png) |
| Actual report after unlink | ![Before Actual report after unlink](../images/pr6102/native/on/before/unlink/008-ViewLabDisplay.png) | ![After Actual report after unlink](../images/pr6102/native/on/after/unlink/008-ViewLabDisplay.png) |
| Obsolete second-tab report | ![Before Obsolete second-tab report](../images/pr6102/native/on/before/unlink/009-ViewLabDisplay.png) | ![After Obsolete second-tab report](../images/pr6102/native/on/after/unlink/009-ViewLabDisplay.png) |

[Before receipts](../images/pr6102/native/on/before/unlink/captures.json) · [After receipts](../images/pr6102/native/on/after/unlink/captures.json) · [After workflow log](../images/pr6102/native/on/after/unlink/workflow.log)

### Single-point and constant-value charts

| Case | Before | After |
| --- | --- | --- |
| Single 99 | ![Before Single 99](../images/pr6102/native/on/before/boundaries/001-GraphMeasurements.png) | ![After Single 99](../images/pr6102/native/on/after/boundaries/001-GraphMeasurements.png) |
| Single zero | ![Before Single zero](../images/pr6102/native/on/before/boundaries/002-GraphMeasurements.png) | ![After Single zero](../images/pr6102/native/on/after/boundaries/002-GraphMeasurements.png) |
| Single 0.05 | ![Before Single 0.05](../images/pr6102/native/on/before/boundaries/003-GraphMeasurements.png) | ![After Single 0.05](../images/pr6102/native/on/after/boundaries/003-GraphMeasurements.png) |
| Single -0.05 | ![Before Single -0.05](../images/pr6102/native/on/before/boundaries/004-GraphMeasurements.png) | ![After Single -0.05](../images/pr6102/native/on/after/boundaries/004-GraphMeasurements.png) |
| Constant 5,000,000 | ![Before Constant 5,000,000](../images/pr6102/native/on/before/boundaries/005-GraphMeasurements.png) | ![After Constant 5,000,000](../images/pr6102/native/on/after/boundaries/005-GraphMeasurements.png) |
| Constant 12,500,000 | ![Before Constant 12,500,000](../images/pr6102/native/on/before/boundaries/006-GraphMeasurements.png) | ![After Constant 12,500,000](../images/pr6102/native/on/after/boundaries/006-GraphMeasurements.png) |
| Constant 45,000,000 | ![Before Constant 45,000,000](../images/pr6102/native/on/before/boundaries/007-GraphMeasurements.png) | ![After Constant 45,000,000](../images/pr6102/native/on/after/boundaries/007-GraphMeasurements.png) |
| Constant -6,000,000 | ![Before Constant -6,000,000](../images/pr6102/native/on/before/boundaries/008-GraphMeasurements.png) | ![After Constant -6,000,000](../images/pr6102/native/on/after/boundaries/008-GraphMeasurements.png) |

[Before receipts](../images/pr6102/native/on/before/boundaries/captures.json) · [After receipts](../images/pr6102/native/on/after/boundaries/captures.json) · [After workflow log](../images/pr6102/native/on/after/boundaries/workflow.log)

### Renderer outage and recovery

| Case | Before | After |
| --- | --- | --- |
| Invalid upload: first rejection control | ![Before Invalid upload: first rejection control](../images/pr6102/native/on/before/outage/001-insideLabUpload.png) | ![After Invalid upload: first rejection control](../images/pr6102/native/on/after/outage/001-insideLabUpload.png) |
| Invalid upload: retry rejection control | ![Before Invalid upload: retry rejection control](../images/pr6102/native/on/before/outage/002-insideLabUpload.png) | ![After Invalid upload: retry rejection control](../images/pr6102/native/on/after/outage/002-insideLabUpload.png) |
| Original source upload | ![Before Original source upload](../images/pr6102/native/on/before/outage/003-insideLabUpload.png) | ![After Original source upload](../images/pr6102/native/on/after/outage/003-insideLabUpload.png) |
| Exact duplicate rejection control | ![Before Exact duplicate rejection control](../images/pr6102/native/on/before/outage/004-insideLabUpload.png) | ![After Exact duplicate rejection control](../images/pr6102/native/on/after/outage/004-insideLabUpload.png) |
| Healthy embedded graph control | ![Before Healthy embedded graph control](../images/pr6102/native/on/before/outage/005-ViewLabValuesGraph.png) | ![After Healthy embedded graph control](../images/pr6102/native/on/after/outage/005-ViewLabValuesGraph.png) |
| Renderer stopped: visible failure and retry | ![Before Renderer stopped: visible failure and retry](../images/pr6102/native/on/before/outage/006-ViewLabValuesGraph.png) | ![After Renderer stopped: visible failure and retry](../images/pr6102/native/on/after/outage/006-ViewLabValuesGraph.png) |
| Graph recovered after restart/retry | ![Before Graph recovered after restart/retry](../images/pr6102/native/on/before/outage/007-ViewLabValuesGraph.png) | ![After Graph recovered after restart/retry](../images/pr6102/native/on/after/outage/007-ViewLabValuesGraph.png) |
| Raw source report control | ![Before Raw source report control](../images/pr6102/native/on/before/outage/008-ViewLabDisplay.png) | ![After Raw source report control](../images/pr6102/native/on/after/outage/008-ViewLabDisplay.png) |

[Before receipts](../images/pr6102/native/on/before/outage/captures.json) · [After receipts](../images/pr6102/native/on/after/outage/captures.json) · [After workflow log](../images/pr6102/native/on/after/outage/workflow.log)

### Partial correction review and conflict recovery

Before revision: `a3934f88a44e1a9e06d9890926cb758086076471`. Its partial acknowledgment silently files an older report containing an omitted observation. The after run keeps that source NEW, preserves prior acknowledgments, blocks a conflicting revision with visible recovery, and resolves the conflict when a newer explicit correction arrives.

| Case | Before | After |
| --- | --- | --- |
| partial-correction-review | ![Before partial-correction-review](../images/pr6102/native/on/before/cml-review/001-partial-correction-review.png) | ![After partial-correction-review](../images/pr6102/native/on/after/cml-review/001-partial-correction-review.png) |
| original-after-partial-review | ![Before original-after-partial-review](../images/pr6102/native/on/before/cml-review/002-original-after-partial-review.png) | ![After original-after-partial-review](../images/pr6102/native/on/after/cml-review/002-original-after-partial-review.png) |

[Before failure receipt](../images/pr6102/native/on/before/cml-review/workflow.log) · [After complete workflow](../images/pr6102/native/on/after/cml-review/workflow.log)

**conflicting-revision-ack-blocked**

![conflicting-revision-ack-blocked](../images/pr6102/native/on/after/cml-review/003-conflicting-revision-ack-blocked.png)

**newer-unambiguous-resolution**

![newer-unambiguous-resolution](../images/pr6102/native/on/after/cml-review/004-newer-unambiguous-resolution.png)

**fully-covered-history-reviewed**

![fully-covered-history-reviewed](../images/pr6102/native/on/after/cml-review/005-fully-covered-history-reviewed.png)

### Actual native PNG/PDF response bytes

These are the unmodified endpoint responses, separate from the browser screenshots.

| Response | Before | After |
| --- | --- | --- |
| Constant 99 | [PNG](../images/pr6102/native/on/before/boundary-artifacts/constant-99.png) | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-99.png) |
| Constant 0 | [PNG](../images/pr6102/native/on/before/boundary-artifacts/constant-0.png) | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-0.png) |
| Constant 0.05 | [PNG](../images/pr6102/native/on/before/boundary-artifacts/constant-0_05.png) | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-0_05.png) |
| Constant -0.05 | [PNG](../images/pr6102/native/on/before/boundary-artifacts/constant-n0_05.png) | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-n0_05.png) |
| Constant 5000000 | HTTP 503; retained in the browser comparison | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-5000000.png) |
| Constant 12500000 | HTTP 503; retained in the browser comparison | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-12500000.png) |
| Constant 45000000 | HTTP 503; retained in the browser comparison | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-45000000.png) |
| Constant -6000000 | HTTP 503; retained in the browser comparison | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-n6000000.png) |
| Hemoglobin plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-2010.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-2010.png) |
| Hematocrit plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-2013.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-2013.png) |
| Sodium plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-3210.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-3210.png) |
| Glucose plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-3007.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-3007.png) |
| Cholesterol plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-3120.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-3120.png) |
| Raw report 2021-01-01 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-173.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-314.pdf) |
| Raw report 2022-05-07 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-174.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-315.pdf) |
| Raw report 2023-12-29 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-175.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-316.pdf) |
| Raw report 2025-10-02 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-176.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-317.pdf) |
| Raw report 2026-11-01 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-177.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PWeed63daf2bd5e570-318.pdf) |

### Reviewed repair and recovery

These are new-feature checks with no original-release counterpart. The duplicate fixture restores only an owned historical projection; raw source reports are always uploaded through the native UI.

**duplicate-preview**

![duplicate-preview](../images/pr6102/native/on/after/cml-repair/001-duplicate-preview.png)

**audit-failure-rollback**

![audit-failure-rollback](../images/pr6102/native/on/after/cml-repair/002-audit-failure-rollback.png)

**repair-committed**

![repair-committed](../images/pr6102/native/on/after/cml-repair/003-repair-committed.png)

**idempotent-preview**

![idempotent-preview](../images/pr6102/native/on/after/cml-repair/004-idempotent-preview.png)

**concurrent-repair-import**

![concurrent-repair-import](../images/pr6102/native/on/after/cml-repair/005-concurrent-repair-import.png)

**missing-revision-source-review**

![missing-revision-source-review](../images/pr6102/native/on/after/cml-repair/006-missing-revision-source-review.png)

[Repair/concurrency workflow log](../images/pr6102/native/on/after/cml-repair/workflow.log) · [Missing-time raw PDF with review warning](../images/pr6102/native/on/after/cml-repair/missing-revision-source.pdf)

### Server-locale compatibility controls

Before revision: `a7b22e9b63f9481dcb34ea776a31dfba254cb9cb`. These use the installed rendering classes and real native Chromium PNG/PDF output. They are raw rendering controls, not additional Playwright screenshots.

| Server locale | Before PNG / PDF | After PNG / PDF |
| --- | --- | --- |
| en-CA | [PNG](../images/pr6102/native/on/before/renderer-locales-before/en-CA.png) / [PDF](../images/pr6102/native/on/before/renderer-locales-before/en-CA.pdf) | [PNG](../images/pr6102/native/on/after/renderer-locales/en-CA.png) / [PDF](../images/pr6102/native/on/after/renderer-locales/en-CA.pdf) |
| fr-CA | [PNG](../images/pr6102/native/on/before/renderer-locales-before/fr-CA.png) / [PDF](../images/pr6102/native/on/before/renderer-locales-before/fr-CA.pdf) | [PNG](../images/pr6102/native/on/after/renderer-locales/fr-CA.png) / [PDF](../images/pr6102/native/on/after/renderer-locales/fr-CA.pdf) |
| es-ES | [PNG](../images/pr6102/native/on/before/renderer-locales-before/es-ES.png) / [PDF](../images/pr6102/native/on/before/renderer-locales-before/es-ES.pdf) | [PNG](../images/pr6102/native/on/after/renderer-locales/es-ES.png) / [PDF](../images/pr6102/native/on/after/renderer-locales/es-ES.pdf) |
| pl-PL | [PNG](../images/pr6102/native/on/before/renderer-locales-before/pl-PL.png) / [PDF](../images/pr6102/native/on/before/renderer-locales-before/pl-PL.pdf) | [PNG](../images/pr6102/native/on/after/renderer-locales/pl-PL.png) / [PDF](../images/pr6102/native/on/after/renderer-locales/pl-PL.pdf) |
| pt-BR | [PNG](../images/pr6102/native/on/before/renderer-locales-before/pt-BR.png) / [PDF](../images/pr6102/native/on/before/renderer-locales-before/pt-BR.pdf) | [PNG](../images/pr6102/native/on/after/renderer-locales/pt-BR.png) / [PDF](../images/pr6102/native/on/after/renderer-locales/pt-BR.pdf) |

[Native admission/locale/interruption/cleanup results](../images/pr6102/native/on/after/renderer-locales/qualification.json) · [CLS contention log](../images/pr6102/native/on/after/cls/workflow.log)

### Additional installed controls

[Independent/automatic provider routing screenshots](../images/pr6102/native/on/after/unlink-controls/captures.json) · [eForm screenshot](../images/pr6102/native/on/after/eform/eform-bootstrap-print-fidelity.png) · [Actual eForm PDF](../images/pr6102/native/on/after/eform/eform-bootstrap-print-fidelity.pdf)

Compatibility logs: [el-jstl](../images/pr6102/native/on/after/el-jstl/workflow.log) · [rbt-export](../images/pr6102/native/on/after/rbt-export/workflow.log) · [measurement-history](../images/pr6102/native/on/after/measurement-history/workflow.log) · [measurement-plot-semantics](../images/pr6102/native/on/after/measurement-plot-semantics/workflow.log) · [chart-print-lab-range](../images/pr6102/native/on/after/chart-print-lab-range/workflow.log).

[Deployed artifact verification before tests](../images/pr6102/native/on/after/artifact-integrity-before.json) · [After tests](../images/pr6102/native/on/after/artifact-integrity-after.json)

## BC Playwright comparisons

### CML correction, older delivery and replay

| Case | Before | After |
| --- | --- | --- |
| Original before review | ![Before Original before review](../images/pr6102/native/bc/before/cml-version/001-Original-before-review.png) | ![After Original before review](../images/pr6102/native/bc/after/cml-version/001-Original-before-review.png) |
| CORRECTION ingestion Inbox selected correction | ![Before CORRECTION ingestion Inbox selected correction](../images/pr6102/native/bc/before/cml-version/002-CORRECTION-ingestion-Inbox-selected-correction.png) | ![After CORRECTION ingestion Inbox selected correction](../images/pr6102/native/bc/after/cml-version/002-CORRECTION-ingestion-Inbox-selected-correction.png) |
| CORRECTION ingestion historical ORIGINAL | ![Before CORRECTION ingestion historical ORIGINAL](../images/pr6102/native/bc/before/cml-version/003-CORRECTION-ingestion-historical-ORIGINAL.png) | ![After CORRECTION ingestion historical ORIGINAL](../images/pr6102/native/bc/after/cml-version/003-CORRECTION-ingestion-historical-ORIGINAL.png) |
| CORRECTION ingestion historical CORRECTION | ![Before CORRECTION ingestion historical CORRECTION](../images/pr6102/native/bc/before/cml-version/004-CORRECTION-ingestion-historical-CORRECTION.png) | ![After CORRECTION ingestion historical CORRECTION](../images/pr6102/native/bc/after/cml-version/004-CORRECTION-ingestion-historical-CORRECTION.png) |
| CORRECTION ingestion chart current accession | ![Before CORRECTION ingestion chart current accession](../images/pr6102/native/bc/before/cml-version/005-CORRECTION-ingestion-chart-current-accession.png) | ![After CORRECTION ingestion chart current accession](../images/pr6102/native/bc/after/cml-version/005-CORRECTION-ingestion-chart-current-accession.png) |
| CORRECTION ingestion chart current | ![Before CORRECTION ingestion chart current](../images/pr6102/native/bc/before/cml-version/006-CORRECTION-ingestion-chart-current.png) | ![After CORRECTION ingestion chart current](../images/pr6102/native/bc/after/cml-version/006-CORRECTION-ingestion-chart-current.png) |
| OLDER ingestion Inbox selected correction | ![Before OLDER ingestion Inbox selected correction](../images/pr6102/native/bc/before/cml-version/007-OLDER-ingestion-Inbox-selected-correction.png) | ![After OLDER ingestion Inbox selected correction](../images/pr6102/native/bc/after/cml-version/007-OLDER-ingestion-Inbox-selected-correction.png) |
| OLDER ingestion historical ORIGINAL | ![Before OLDER ingestion historical ORIGINAL](../images/pr6102/native/bc/before/cml-version/008-OLDER-ingestion-historical-ORIGINAL.png) | ![After OLDER ingestion historical ORIGINAL](../images/pr6102/native/bc/after/cml-version/008-OLDER-ingestion-historical-ORIGINAL.png) |
| OLDER ingestion historical CORRECTION | ![Before OLDER ingestion historical CORRECTION](../images/pr6102/native/bc/before/cml-version/009-OLDER-ingestion-historical-CORRECTION.png) | ![After OLDER ingestion historical CORRECTION](../images/pr6102/native/bc/after/cml-version/009-OLDER-ingestion-historical-CORRECTION.png) |
| OLDER ingestion historical OLDER | ![Before OLDER ingestion historical OLDER](../images/pr6102/native/bc/before/cml-version/010-OLDER-ingestion-historical-OLDER.png) | ![After OLDER ingestion historical OLDER](../images/pr6102/native/bc/after/cml-version/010-OLDER-ingestion-historical-OLDER.png) |
| OLDER ingestion chart current accession | ![Before OLDER ingestion chart current accession](../images/pr6102/native/bc/before/cml-version/011-OLDER-ingestion-chart-current-accession.png) | ![After OLDER ingestion chart current accession](../images/pr6102/native/bc/after/cml-version/011-OLDER-ingestion-chart-current-accession.png) |
| OLDER ingestion chart current | ![Before OLDER ingestion chart current](../images/pr6102/native/bc/before/cml-version/012-OLDER-ingestion-chart-current.png) | ![After OLDER ingestion chart current](../images/pr6102/native/bc/after/cml-version/012-OLDER-ingestion-chart-current.png) |

[Before receipts](../images/pr6102/native/bc/before/cml-version/captures.json) · [After receipts](../images/pr6102/native/bc/after/cml-version/captures.json) · [After workflow log](../images/pr6102/native/bc/after/cml-version/workflow.log)

### Laboratory literals, reports and charts

| Case | Before | After |
| --- | --- | --- |
| Unrelated-patient Create Lab control | ![Before Unrelated-patient Create Lab control](../images/pr6102/native/bc/before/qualified/001-ViewCreateLab.png) | ![After Unrelated-patient Create Lab control](../images/pr6102/native/bc/after/qualified/001-ViewCreateLab.png) |
| Create Lab: 2021 qualified/qualitative panel | ![Before Create Lab: 2021 qualified/qualitative panel](../images/pr6102/native/bc/before/qualified/002-ViewCreateLab.png) | ![After Create Lab: 2021 qualified/qualitative panel](../images/pr6102/native/bc/after/qualified/002-ViewCreateLab.png) |
| Create Lab: 2022 Negative observation | ![Before Create Lab: 2022 Negative observation](../images/pr6102/native/bc/before/qualified/003-ViewCreateLab.png) | ![After Create Lab: 2022 Negative observation](../images/pr6102/native/bc/after/qualified/003-ViewCreateLab.png) |
| Create Lab: 2023 exact numeric observation | ![Before Create Lab: 2023 exact numeric observation](../images/pr6102/native/bc/before/qualified/004-ViewCreateLab.png) | ![After Create Lab: 2023 exact numeric observation](../images/pr6102/native/bc/after/qualified/004-ViewCreateLab.png) |
| Create Lab: 2025 qualified observation | ![Before Create Lab: 2025 qualified observation](../images/pr6102/native/bc/before/qualified/005-ViewCreateLab.png) | ![After Create Lab: 2025 qualified observation](../images/pr6102/native/bc/after/qualified/005-ViewCreateLab.png) |
| Create Lab: 2026 exact numeric observation | ![Before Create Lab: 2026 exact numeric observation](../images/pr6102/native/bc/before/qualified/006-ViewCreateLab.png) | ![After Create Lab: 2026 exact numeric observation](../images/pr6102/native/bc/after/qualified/006-ViewCreateLab.png) |
| Hemoglobin: exact points and excluded observations | ![Before Hemoglobin: exact points and excluded observations](../images/pr6102/native/bc/before/qualified/007-ViewLabValuesGraph.png) | ![After Hemoglobin: exact points and excluded observations](../images/pr6102/native/bc/after/qualified/007-ViewLabValuesGraph.png) |
| Hematocrit: wholly qualified result | ![Before Hematocrit: wholly qualified result](../images/pr6102/native/bc/before/qualified/008-ViewLabValuesGraph.png) | ![After Hematocrit: wholly qualified result](../images/pr6102/native/bc/after/qualified/008-ViewLabValuesGraph.png) |
| Sodium: qualitative result history/plot | ![Before Sodium: qualitative result history/plot](../images/pr6102/native/bc/before/qualified/009-ViewLabValuesGraph.png) | ![After Sodium: qualitative result history/plot](../images/pr6102/native/bc/after/qualified/009-ViewLabValuesGraph.png) |
| Glucose: filter-excluded Pending result | ![Before Glucose: filter-excluded Pending result](../images/pr6102/native/bc/before/qualified/010-ViewLabValuesGraph.png) | ![After Glucose: filter-excluded Pending result](../images/pr6102/native/bc/after/qualified/010-ViewLabValuesGraph.png) |
| Cholesterol: numeric compatibility control | ![Before Cholesterol: numeric compatibility control](../images/pr6102/native/bc/before/qualified/011-ViewLabValuesGraph.png) | ![After Cholesterol: numeric compatibility control](../images/pr6102/native/bc/after/qualified/011-ViewLabValuesGraph.png) |
| Raw report: 2021-01-01 | ![Before Raw report: 2021-01-01](../images/pr6102/native/bc/before/qualified/012-ViewLabDisplay.png) | ![After Raw report: 2021-01-01](../images/pr6102/native/bc/after/qualified/012-ViewLabDisplay.png) |
| Raw report: 2022-05-07 | ![Before Raw report: 2022-05-07](../images/pr6102/native/bc/before/qualified/013-ViewLabDisplay.png) | ![After Raw report: 2022-05-07](../images/pr6102/native/bc/after/qualified/013-ViewLabDisplay.png) |
| Raw report: 2023-12-29 | ![Before Raw report: 2023-12-29](../images/pr6102/native/bc/before/qualified/014-ViewLabDisplay.png) | ![After Raw report: 2023-12-29](../images/pr6102/native/bc/after/qualified/014-ViewLabDisplay.png) |
| Raw report: 2025-10-02 | ![Before Raw report: 2025-10-02](../images/pr6102/native/bc/before/qualified/015-ViewLabDisplay.png) | ![After Raw report: 2025-10-02](../images/pr6102/native/bc/after/qualified/015-ViewLabDisplay.png) |
| Raw report: 2026-11-01 | ![Before Raw report: 2026-11-01](../images/pr6102/native/bc/before/qualified/016-ViewLabDisplay.png) | ![After Raw report: 2026-11-01](../images/pr6102/native/bc/after/qualified/016-ViewLabDisplay.png) |
| Cumulative grid: source case and Pending row | ![Before Cumulative grid: source case and Pending row](../images/pr6102/native/bc/before/qualified/017-ViewCumulativeLabValues3.png) | ![After Cumulative grid: source case and Pending row](../images/pr6102/native/bc/after/qualified/017-ViewCumulativeLabValues3.png) |
| Cumulative row history: source literals | ![Before Cumulative row history: source literals](../images/pr6102/native/bc/before/qualified/018-ViewCumulativeLabValues.png) | ![After Cumulative row history: source literals](../images/pr6102/native/bc/after/qualified/018-ViewCumulativeLabValues.png) |

[Before receipts](../images/pr6102/native/bc/before/qualified/captures.json) · [After receipts](../images/pr6102/native/bc/after/qualified/captures.json) · [After workflow log](../images/pr6102/native/bc/after/qualified/workflow.log)

### CDM two-tab definitions

| Case | Before | After |
| --- | --- | --- |
| Guideline: original tab A | ![Before Guideline: original tab A](../images/pr6102/native/bc/before/cdm/001-InitializePatientsMetGuidelineCDMReport.png) | ![After Guideline: original tab A](../images/pr6102/native/bc/after/cdm/001-InitializePatientsMetGuidelineCDMReport.png) |
| Guideline: second tab B control | ![Before Guideline: second tab B control](../images/pr6102/native/bc/before/cdm/002-InitializePatientsMetGuidelineCDMReport.png) | ![After Guideline: second tab B control](../images/pr6102/native/bc/after/cdm/002-InitializePatientsMetGuidelineCDMReport.png) |
| Range: original tab A | ![Before Range: original tab A](../images/pr6102/native/bc/before/cdm/003-InitializePatientsInAbnormalRangeCDMReport.png) | ![After Range: original tab A](../images/pr6102/native/bc/after/cdm/003-InitializePatientsInAbnormalRangeCDMReport.png) |
| Range: second tab B control | ![Before Range: second tab B control](../images/pr6102/native/bc/before/cdm/004-InitializePatientsInAbnormalRangeCDMReport.png) | ![After Range: second tab B control](../images/pr6102/native/bc/after/cdm/004-InitializePatientsInAbnormalRangeCDMReport.png) |
| Frequency: original tab A | ![Before Frequency: original tab A](../images/pr6102/native/bc/before/cdm/005-InitializeFrequencyOfRelevantTestsCDMReport.png) | ![After Frequency: original tab A](../images/pr6102/native/bc/after/cdm/005-InitializeFrequencyOfRelevantTestsCDMReport.png) |
| Frequency: second tab B control | ![Before Frequency: second tab B control](../images/pr6102/native/bc/before/cdm/006-InitializeFrequencyOfRelevantTestsCDMReport.png) | ![After Frequency: second tab B control](../images/pr6102/native/bc/after/cdm/006-InitializeFrequencyOfRelevantTestsCDMReport.png) |

[Before receipts](../images/pr6102/native/bc/before/cdm/captures.json) · [After receipts](../images/pr6102/native/bc/after/cdm/captures.json) · [After workflow log](../images/pr6102/native/bc/after/cdm/workflow.log)

### Unlink and later delivery

| Case | Before | After |
| --- | --- | --- |
| Invalid upload: first rejection control | ![Before Invalid upload: first rejection control](../images/pr6102/native/bc/before/unlink/001-insideLabUpload.png) | ![After Invalid upload: first rejection control](../images/pr6102/native/bc/after/unlink/001-insideLabUpload.png) |
| Invalid upload: retry rejection control | ![Before Invalid upload: retry rejection control](../images/pr6102/native/bc/before/unlink/002-insideLabUpload.png) | ![After Invalid upload: retry rejection control](../images/pr6102/native/bc/after/unlink/002-insideLabUpload.png) |
| Original source upload | ![Before Original source upload](../images/pr6102/native/bc/before/unlink/003-insideLabUpload.png) | ![After Original source upload](../images/pr6102/native/bc/after/unlink/003-insideLabUpload.png) |
| Exact duplicate rejection control | ![Before Exact duplicate rejection control](../images/pr6102/native/bc/before/unlink/004-insideLabUpload.png) | ![After Exact duplicate rejection control](../images/pr6102/native/bc/after/unlink/004-insideLabUpload.png) |
| Correction source upload | ![Before Correction source upload](../images/pr6102/native/bc/before/unlink/005-insideLabUpload.png) | ![After Correction source upload](../images/pr6102/native/bc/after/unlink/005-insideLabUpload.png) |
| Actual report before unlink | ![Before Actual report before unlink](../images/pr6102/native/bc/before/unlink/006-ViewLabDisplay.png) | ![After Actual report before unlink](../images/pr6102/native/bc/after/unlink/006-ViewLabDisplay.png) |
| Later delivery after unlink | ![Before Later delivery after unlink](../images/pr6102/native/bc/before/unlink/007-insideLabUpload.png) | ![After Later delivery after unlink](../images/pr6102/native/bc/after/unlink/007-insideLabUpload.png) |
| Actual report after unlink | ![Before Actual report after unlink](../images/pr6102/native/bc/before/unlink/008-ViewLabDisplay.png) | ![After Actual report after unlink](../images/pr6102/native/bc/after/unlink/008-ViewLabDisplay.png) |
| Obsolete second-tab report | ![Before Obsolete second-tab report](../images/pr6102/native/bc/before/unlink/009-ViewLabDisplay.png) | ![After Obsolete second-tab report](../images/pr6102/native/bc/after/unlink/009-ViewLabDisplay.png) |

[Before receipts](../images/pr6102/native/bc/before/unlink/captures.json) · [After receipts](../images/pr6102/native/bc/after/unlink/captures.json) · [After workflow log](../images/pr6102/native/bc/after/unlink/workflow.log)

### Single-point and constant-value charts

| Case | Before | After |
| --- | --- | --- |
| Single 99 | ![Before Single 99](../images/pr6102/native/bc/before/boundaries/001-GraphMeasurements.png) | ![After Single 99](../images/pr6102/native/bc/after/boundaries/001-GraphMeasurements.png) |
| Single zero | ![Before Single zero](../images/pr6102/native/bc/before/boundaries/002-GraphMeasurements.png) | ![After Single zero](../images/pr6102/native/bc/after/boundaries/002-GraphMeasurements.png) |
| Single 0.05 | ![Before Single 0.05](../images/pr6102/native/bc/before/boundaries/003-GraphMeasurements.png) | ![After Single 0.05](../images/pr6102/native/bc/after/boundaries/003-GraphMeasurements.png) |
| Single -0.05 | ![Before Single -0.05](../images/pr6102/native/bc/before/boundaries/004-GraphMeasurements.png) | ![After Single -0.05](../images/pr6102/native/bc/after/boundaries/004-GraphMeasurements.png) |
| Constant 5,000,000 | ![Before Constant 5,000,000](../images/pr6102/native/bc/before/boundaries/005-GraphMeasurements.png) | ![After Constant 5,000,000](../images/pr6102/native/bc/after/boundaries/005-GraphMeasurements.png) |
| Constant 12,500,000 | ![Before Constant 12,500,000](../images/pr6102/native/bc/before/boundaries/006-GraphMeasurements.png) | ![After Constant 12,500,000](../images/pr6102/native/bc/after/boundaries/006-GraphMeasurements.png) |
| Constant 45,000,000 | ![Before Constant 45,000,000](../images/pr6102/native/bc/before/boundaries/007-GraphMeasurements.png) | ![After Constant 45,000,000](../images/pr6102/native/bc/after/boundaries/007-GraphMeasurements.png) |
| Constant -6,000,000 | ![Before Constant -6,000,000](../images/pr6102/native/bc/before/boundaries/008-GraphMeasurements.png) | ![After Constant -6,000,000](../images/pr6102/native/bc/after/boundaries/008-GraphMeasurements.png) |

[Before receipts](../images/pr6102/native/bc/before/boundaries/captures.json) · [After receipts](../images/pr6102/native/bc/after/boundaries/captures.json) · [After workflow log](../images/pr6102/native/bc/after/boundaries/workflow.log)

### Renderer outage and recovery

| Case | Before | After |
| --- | --- | --- |
| Invalid upload: first rejection control | ![Before Invalid upload: first rejection control](../images/pr6102/native/bc/before/outage/001-insideLabUpload.png) | ![After Invalid upload: first rejection control](../images/pr6102/native/bc/after/outage/001-insideLabUpload.png) |
| Invalid upload: retry rejection control | ![Before Invalid upload: retry rejection control](../images/pr6102/native/bc/before/outage/002-insideLabUpload.png) | ![After Invalid upload: retry rejection control](../images/pr6102/native/bc/after/outage/002-insideLabUpload.png) |
| Original source upload | ![Before Original source upload](../images/pr6102/native/bc/before/outage/003-insideLabUpload.png) | ![After Original source upload](../images/pr6102/native/bc/after/outage/003-insideLabUpload.png) |
| Exact duplicate rejection control | ![Before Exact duplicate rejection control](../images/pr6102/native/bc/before/outage/004-insideLabUpload.png) | ![After Exact duplicate rejection control](../images/pr6102/native/bc/after/outage/004-insideLabUpload.png) |
| Healthy embedded graph control | ![Before Healthy embedded graph control](../images/pr6102/native/bc/before/outage/005-ViewLabValuesGraph.png) | ![After Healthy embedded graph control](../images/pr6102/native/bc/after/outage/005-ViewLabValuesGraph.png) |
| Renderer stopped: visible failure and retry | ![Before Renderer stopped: visible failure and retry](../images/pr6102/native/bc/before/outage/006-ViewLabValuesGraph.png) | ![After Renderer stopped: visible failure and retry](../images/pr6102/native/bc/after/outage/006-ViewLabValuesGraph.png) |
| Graph recovered after restart/retry | ![Before Graph recovered after restart/retry](../images/pr6102/native/bc/before/outage/007-ViewLabValuesGraph.png) | ![After Graph recovered after restart/retry](../images/pr6102/native/bc/after/outage/007-ViewLabValuesGraph.png) |
| Raw source report control | ![Before Raw source report control](../images/pr6102/native/bc/before/outage/008-ViewLabDisplay.png) | ![After Raw source report control](../images/pr6102/native/bc/after/outage/008-ViewLabDisplay.png) |

[Before receipts](../images/pr6102/native/bc/before/outage/captures.json) · [After receipts](../images/pr6102/native/bc/after/outage/captures.json) · [After workflow log](../images/pr6102/native/bc/after/outage/workflow.log)

### Partial correction review and conflict recovery

Before revision: `a3934f88a44e1a9e06d9890926cb758086076471`. Its partial acknowledgment silently files an older report containing an omitted observation. The after run keeps that source NEW, preserves prior acknowledgments, blocks a conflicting revision with visible recovery, and resolves the conflict when a newer explicit correction arrives.

| Case | Before | After |
| --- | --- | --- |
| partial-correction-review | ![Before partial-correction-review](../images/pr6102/native/bc/before/cml-review/001-partial-correction-review.png) | ![After partial-correction-review](../images/pr6102/native/bc/after/cml-review/001-partial-correction-review.png) |
| original-after-partial-review | ![Before original-after-partial-review](../images/pr6102/native/bc/before/cml-review/002-original-after-partial-review.png) | ![After original-after-partial-review](../images/pr6102/native/bc/after/cml-review/002-original-after-partial-review.png) |

[Before failure receipt](../images/pr6102/native/bc/before/cml-review/workflow.log) · [After complete workflow](../images/pr6102/native/bc/after/cml-review/workflow.log)

**conflicting-revision-ack-blocked**

![conflicting-revision-ack-blocked](../images/pr6102/native/bc/after/cml-review/003-conflicting-revision-ack-blocked.png)

**newer-unambiguous-resolution**

![newer-unambiguous-resolution](../images/pr6102/native/bc/after/cml-review/004-newer-unambiguous-resolution.png)

**fully-covered-history-reviewed**

![fully-covered-history-reviewed](../images/pr6102/native/bc/after/cml-review/005-fully-covered-history-reviewed.png)

### Actual native PNG/PDF response bytes

These are the unmodified endpoint responses, separate from the browser screenshots.

| Response | Before | After |
| --- | --- | --- |
| Constant 99 | [PNG](../images/pr6102/native/bc/before/boundary-artifacts/constant-99.png) | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-99.png) |
| Constant 0 | [PNG](../images/pr6102/native/bc/before/boundary-artifacts/constant-0.png) | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-0.png) |
| Constant 0.05 | [PNG](../images/pr6102/native/bc/before/boundary-artifacts/constant-0_05.png) | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-0_05.png) |
| Constant -0.05 | [PNG](../images/pr6102/native/bc/before/boundary-artifacts/constant-n0_05.png) | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-n0_05.png) |
| Constant 5000000 | HTTP 503; retained in the browser comparison | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-5000000.png) |
| Constant 12500000 | HTTP 503; retained in the browser comparison | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-12500000.png) |
| Constant 45000000 | HTTP 503; retained in the browser comparison | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-45000000.png) |
| Constant -6000000 | HTTP 503; retained in the browser comparison | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-n6000000.png) |
| Hemoglobin plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-2010.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-2010.png) |
| Hematocrit plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-2013.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-2013.png) |
| Sodium plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-3210.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-3210.png) |
| Glucose plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-3007.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-3007.png) |
| Cholesterol plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-3120.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-3120.png) |
| Raw report 2021-01-01 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-173.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-306.pdf) |
| Raw report 2022-05-07 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-174.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-307.pdf) |
| Raw report 2023-12-29 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-175.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-308.pdf) |
| Raw report 2025-10-02 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-176.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-309.pdf) |
| Raw report 2026-11-01 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-177.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW49eeee01d507eef7-310.pdf) |

### Reviewed repair and recovery

These are new-feature checks with no original-release counterpart. The duplicate fixture restores only an owned historical projection; raw source reports are always uploaded through the native UI.

**duplicate-preview**

![duplicate-preview](../images/pr6102/native/bc/after/cml-repair/001-duplicate-preview.png)

**audit-failure-rollback**

![audit-failure-rollback](../images/pr6102/native/bc/after/cml-repair/002-audit-failure-rollback.png)

**repair-committed**

![repair-committed](../images/pr6102/native/bc/after/cml-repair/003-repair-committed.png)

**idempotent-preview**

![idempotent-preview](../images/pr6102/native/bc/after/cml-repair/004-idempotent-preview.png)

**concurrent-repair-import**

![concurrent-repair-import](../images/pr6102/native/bc/after/cml-repair/005-concurrent-repair-import.png)

**missing-revision-source-review**

![missing-revision-source-review](../images/pr6102/native/bc/after/cml-repair/006-missing-revision-source-review.png)

[Repair/concurrency workflow log](../images/pr6102/native/bc/after/cml-repair/workflow.log) · [Missing-time raw PDF with review warning](../images/pr6102/native/bc/after/cml-repair/missing-revision-source.pdf)

### Server-locale compatibility controls

Before revision: `a7b22e9b63f9481dcb34ea776a31dfba254cb9cb`. These use the installed rendering classes and real native Chromium PNG/PDF output. They are raw rendering controls, not additional Playwright screenshots.

| Server locale | Before PNG / PDF | After PNG / PDF |
| --- | --- | --- |
| en-CA | [PNG](../images/pr6102/native/bc/before/renderer-locales-before/en-CA.png) / [PDF](../images/pr6102/native/bc/before/renderer-locales-before/en-CA.pdf) | [PNG](../images/pr6102/native/bc/after/renderer-locales/en-CA.png) / [PDF](../images/pr6102/native/bc/after/renderer-locales/en-CA.pdf) |
| fr-CA | [PNG](../images/pr6102/native/bc/before/renderer-locales-before/fr-CA.png) / [PDF](../images/pr6102/native/bc/before/renderer-locales-before/fr-CA.pdf) | [PNG](../images/pr6102/native/bc/after/renderer-locales/fr-CA.png) / [PDF](../images/pr6102/native/bc/after/renderer-locales/fr-CA.pdf) |
| es-ES | [PNG](../images/pr6102/native/bc/before/renderer-locales-before/es-ES.png) / [PDF](../images/pr6102/native/bc/before/renderer-locales-before/es-ES.pdf) | [PNG](../images/pr6102/native/bc/after/renderer-locales/es-ES.png) / [PDF](../images/pr6102/native/bc/after/renderer-locales/es-ES.pdf) |
| pl-PL | [PNG](../images/pr6102/native/bc/before/renderer-locales-before/pl-PL.png) / [PDF](../images/pr6102/native/bc/before/renderer-locales-before/pl-PL.pdf) | [PNG](../images/pr6102/native/bc/after/renderer-locales/pl-PL.png) / [PDF](../images/pr6102/native/bc/after/renderer-locales/pl-PL.pdf) |
| pt-BR | [PNG](../images/pr6102/native/bc/before/renderer-locales-before/pt-BR.png) / [PDF](../images/pr6102/native/bc/before/renderer-locales-before/pt-BR.pdf) | [PNG](../images/pr6102/native/bc/after/renderer-locales/pt-BR.png) / [PDF](../images/pr6102/native/bc/after/renderer-locales/pt-BR.pdf) |

[Native admission/locale/interruption/cleanup results](../images/pr6102/native/bc/after/renderer-locales/qualification.json) · [CLS contention log](../images/pr6102/native/bc/after/cls/workflow.log)

### Additional installed controls

[Independent/automatic provider routing screenshots](../images/pr6102/native/bc/after/unlink-controls/captures.json) · [eForm screenshot](../images/pr6102/native/bc/after/eform/eform-bootstrap-print-fidelity.png) · [Actual eForm PDF](../images/pr6102/native/bc/after/eform/eform-bootstrap-print-fidelity.pdf)

Compatibility logs: [el-jstl](../images/pr6102/native/bc/after/el-jstl/workflow.log) · [rbt-export](../images/pr6102/native/bc/after/rbt-export/workflow.log) · [measurement-history](../images/pr6102/native/bc/after/measurement-history/workflow.log) · [measurement-plot-semantics](../images/pr6102/native/bc/after/measurement-plot-semantics/workflow.log) · [chart-print-lab-range](../images/pr6102/native/bc/after/chart-print-lab-range/workflow.log).

[Deployed artifact verification before tests](../images/pr6102/native/bc/after/artifact-integrity-before.json) · [After tests](../images/pr6102/native/bc/after/artifact-integrity-after.json)

## Reproduction

Use exclusive disposable installed ON/BC containers with the harness environment loaded. Run one workflow at a time per database; fixtures validate ownership and restore coordination rows. Keep credentials and the renderer capability URL private.

```bash
. /root/suite-env.sh
export CARLOS_TEST_ROOT=/root/carlos CARLOS_DISPOSABLE_HOST=true EXCLUSIVE=1
export LAB_VERSION_SCREENSHOT_DIR=/root/native-proof/cml-version
node scripts/lab-version-ingestion-playwright-checks.js
export CML_REPAIR_SCREENSHOT_DIR=/root/native-proof/cml-repair
node docs/images/pr6102/capture/cml-repair-native.cjs
export CML_REVIEW_SCREENSHOT_DIR=/root/native-proof/cml-review
node docs/images/pr6102/capture/cml-review-native.cjs
node docs/images/pr6102/capture/cls-coordination-native.cjs
```

The passive observer `capture-native-pages.cjs` uses `NATIVE_SCREENSHOT_DIR`; load it with `node --require` for CDM, geometry, unlink, qualified-results and outage runs. Set `LAB_QUALIFIED_ARTIFACT_DIR`, `MEASUREMENT_PLOT_ARTIFACT_DIR` or `EFORM_SCREENSHOT_DIR` for the corresponding actual response files. `UNLINK_AUDIT_FAULT=1` injects the physical audit fault; `UNLINK_ROUTE_CONTROL=1` adds independent/automatic provider controls.

Compile `RendererQualification.java` and `QualificationChart.java` against the installed WAR classes/jars and Tomcat servlet API, then run the former with the deployed WAR directory, private application properties path and output directory. It records five locale profiles, ten real mixed renders, queue bounds of 16 charts/four PDFs, PDF fairness, queued interruption and zero remaining sessions with both permits restored.

Generate the deployment reference with `python3 verify-installed-runtime.py --prepare QUALIFIED.war APPLICATION.deb expected.json`; verify it with `python3 verify-installed-runtime.py expected.json DEPLOYED_APP receipt.json`. The manifest is reproducible from the exact WAR/package bytes and includes both intentional Debian configuration rewrites.

The helper hashes identify the retained reproduction versions. Screenshot/response hashes identify retained bytes; they do not claim a signed execution attestation for every earlier fixture iteration. Baseline failures and intermediate development failures were not counted as passing qualification.
