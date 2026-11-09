# Installed measurement and lab comparisons

**98 before/after screenshot pairs**, across installed Ontario and BC configurations. The [renderer gallery](measurement-chart-screenshots.md) adds 45 pairs covering English/French formatting and compatibility fixtures. All retained clinical data is synthetic and owned by the tests.

Baseline: `9004536dc8314d5f50542b48905ca3ca4099a916`. Installed candidate: `a7b22e9b63f9481dcb34ea776a31dfba254cb9cb`, including release updates through `74f240a4b9f`. All candidate workflows below were rerun on this installed revision, including the serializable CDM context and cached-image recovery changes. Later runtime changes require separate qualification.

Both configurations use Ubuntu 26.04.1, OpenJDK 25.0.5, Tomcat 11.0.22, MariaDB 11.8.6, nginx/ModSecurity and the package-pinned Chromium/ChromeDriver 154.0.8025.0. Debian package version `2026.09.0~snapshot28` contains Maven application version `2026.08.0-beta7-SNAPSHOT`; the PR targets `release/2026.08`. The baseline WAR was compiled on Ubuntu 26.04; the candidate WAR was compiled with Temurin 25.0.3 and packaged on Ubuntu 26.04.

[Capture/package hashes and environment](../images/pr6102/native/manifest.json) · [Acceptance register](measurement-integrity-acceptance.md)

## What these runs establish

- The qualified-results checker reproduces seven baseline findings and passes on the candidate in both provinces. It independently checks raw report/PDF associations, literal histories, exact numeric geometry, numeric controls, other-patient isolation and unchanged clinical state through reads.
- Installed Report by Template exports preserve SQL headings, duplicate column aliases, literal multiline text, NULL/date/decimal values, and XLSX cell types in both provinces. The deployed JSTL/EL page controls also pass; their logs are retained.
- Each of the three CDM reports loses the original tab’s definition on the baseline. Both tabs retain their own type and correct counts on the candidate.
- The baseline single-point charts have no marker; all four large constant charts return HTTP 503. All eight candidate graphs return PNG and contain a visible marker. Raw 1000 × 600 chart images are linked below; the browser may scale a direct image view to its popup viewport.
- A real renderer-service shutdown leaves the baseline embedded image without a visible recovery warning or Retry-After. The candidate returns 503 with Retry-After, displays a warning/Retry button, and renders successfully after restart and Retry.
- Native unlink tests verify complete-chain unmatch, archival of both active projections, source-extension retention, obsolete automatic-MRP access removal, stale-tab 409 and no rematch/projection resurrection after a later delivery. A real MariaDB audit INSERT trigger forces a late failure: HTTP 500, visible recovery, and complete clinical/source rollback; retry then succeeds.

The unlink provider oracle was strengthened after its first candidate run: automatic MRP access must be removed; independent access must remain and reopen. The paired candidate uses the original upload-derived automatic routes. Supplemental runs explicitly seed independent and obsolete automatic route provenance on owned sources and check both classes. This fixture correction is documented; the earlier failing run is not counted as a pass.

Baseline failures remain failures. No expected-failure pin or production response stub waives them. Image/PDF capture helpers forward the actual server response bytes unchanged. The passive screenshot observer records actual pages before closing them. Native acknowledgement self-closes a window; the custom unlink checker waits for that native close.

**Not merge acceptance:** canonical CML current/projection ordering (#5819/#5820), source-specific full-report/partial-correction contracts, historical repair, and remaining concurrency/load/locale gates are still incomplete. These screenshots do not claim those tickets are resolved.

## ON native screenshots

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

### CDM two-tab definitions

| Case | Before | After |
| --- | --- | --- |
| Guideline: original tab A | ![Before Guideline: original tab A](../images/pr6102/native/on/before/cdm/001-InitializePatientsMetGuidelineCDMReport.png) | ![After Guideline: original tab A](../images/pr6102/native/on/after/cdm/001-InitializePatientsMetGuidelineCDMReport.png) |
| Guideline: second tab B control | ![Before Guideline: second tab B control](../images/pr6102/native/on/before/cdm/002-InitializePatientsMetGuidelineCDMReport.png) | ![After Guideline: second tab B control](../images/pr6102/native/on/after/cdm/002-InitializePatientsMetGuidelineCDMReport.png) |
| Range: original tab A | ![Before Range: original tab A](../images/pr6102/native/on/before/cdm/003-InitializePatientsInAbnormalRangeCDMReport.png) | ![After Range: original tab A](../images/pr6102/native/on/after/cdm/003-InitializePatientsInAbnormalRangeCDMReport.png) |
| Range: second tab B control | ![Before Range: second tab B control](../images/pr6102/native/on/before/cdm/004-InitializePatientsInAbnormalRangeCDMReport.png) | ![After Range: second tab B control](../images/pr6102/native/on/after/cdm/004-InitializePatientsInAbnormalRangeCDMReport.png) |
| Frequency: original tab A | ![Before Frequency: original tab A](../images/pr6102/native/on/before/cdm/005-InitializeFrequencyOfRelevantTestsCDMReport.png) | ![After Frequency: original tab A](../images/pr6102/native/on/after/cdm/005-InitializeFrequencyOfRelevantTestsCDMReport.png) |
| Frequency: second tab B control | ![Before Frequency: second tab B control](../images/pr6102/native/on/before/cdm/006-InitializeFrequencyOfRelevantTestsCDMReport.png) | ![After Frequency: second tab B control](../images/pr6102/native/on/after/cdm/006-InitializeFrequencyOfRelevantTestsCDMReport.png) |

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

### Actual downloaded chart images and report PDFs

These files are native endpoint responses, in addition to the browser screenshots above.

| Response | Before | After |
| --- | --- | --- |
| Constant 99 | [PNG](../images/pr6102/native/on/before/boundary-artifacts/constant-99.png) | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-99.png) |
| Constant 0 | [PNG](../images/pr6102/native/on/before/boundary-artifacts/constant-0.png) | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-0.png) |
| Constant 0.05 | [PNG](../images/pr6102/native/on/before/boundary-artifacts/constant-0_05.png) | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-0_05.png) |
| Constant -0.05 | [PNG](../images/pr6102/native/on/before/boundary-artifacts/constant-n0_05.png) | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-n0_05.png) |
| Constant 5000000 | HTTP 503; see browser screenshot above | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-5000000.png) |
| Constant 12500000 | HTTP 503; see browser screenshot above | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-12500000.png) |
| Constant 45000000 | HTTP 503; see browser screenshot above | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-45000000.png) |
| Constant -6000000 | HTTP 503; see browser screenshot above | [PNG](../images/pr6102/native/on/after/boundary-artifacts/constant-n6000000.png) |
| Hemoglobin plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-2010.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-2010.png) |
| Hematocrit plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-2013.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-2013.png) |
| Sodium plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-3210.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-3210.png) |
| Glucose plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-3007.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-3007.png) |
| Cholesterol plot | [PNG](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-3120.png) | [PNG](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-3120.png) |
| Raw report 2021-01-01 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-173.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-214.pdf) |
| Raw report 2022-05-07 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-174.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-215.pdf) |
| Raw report 2023-12-29 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-175.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-216.pdf) |
| Raw report 2025-10-02 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-176.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-217.pdf) |
| Raw report 2026-11-01 | [PDF](../images/pr6102/native/on/before/qualified-artifacts/FAKE-PWb75ce7d6459c83cd-177.pdf) | [PDF](../images/pr6102/native/on/after/qualified-artifacts/FAKE-PW77c0d7771e3b2633-218.pdf) |

## BC native screenshots

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

### CDM two-tab definitions

| Case | Before | After |
| --- | --- | --- |
| Guideline: original tab A | ![Before Guideline: original tab A](../images/pr6102/native/bc/before/cdm/001-InitializePatientsMetGuidelineCDMReport.png) | ![After Guideline: original tab A](../images/pr6102/native/bc/after/cdm/001-InitializePatientsMetGuidelineCDMReport.png) |
| Guideline: second tab B control | ![Before Guideline: second tab B control](../images/pr6102/native/bc/before/cdm/002-InitializePatientsMetGuidelineCDMReport.png) | ![After Guideline: second tab B control](../images/pr6102/native/bc/after/cdm/002-InitializePatientsMetGuidelineCDMReport.png) |
| Range: original tab A | ![Before Range: original tab A](../images/pr6102/native/bc/before/cdm/003-InitializePatientsInAbnormalRangeCDMReport.png) | ![After Range: original tab A](../images/pr6102/native/bc/after/cdm/003-InitializePatientsInAbnormalRangeCDMReport.png) |
| Range: second tab B control | ![Before Range: second tab B control](../images/pr6102/native/bc/before/cdm/004-InitializePatientsInAbnormalRangeCDMReport.png) | ![After Range: second tab B control](../images/pr6102/native/bc/after/cdm/004-InitializePatientsInAbnormalRangeCDMReport.png) |
| Frequency: original tab A | ![Before Frequency: original tab A](../images/pr6102/native/bc/before/cdm/005-InitializeFrequencyOfRelevantTestsCDMReport.png) | ![After Frequency: original tab A](../images/pr6102/native/bc/after/cdm/005-InitializeFrequencyOfRelevantTestsCDMReport.png) |
| Frequency: second tab B control | ![Before Frequency: second tab B control](../images/pr6102/native/bc/before/cdm/006-InitializeFrequencyOfRelevantTestsCDMReport.png) | ![After Frequency: second tab B control](../images/pr6102/native/bc/after/cdm/006-InitializeFrequencyOfRelevantTestsCDMReport.png) |

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

### Actual downloaded chart images and report PDFs

These files are native endpoint responses, in addition to the browser screenshots above.

| Response | Before | After |
| --- | --- | --- |
| Constant 99 | [PNG](../images/pr6102/native/bc/before/boundary-artifacts/constant-99.png) | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-99.png) |
| Constant 0 | [PNG](../images/pr6102/native/bc/before/boundary-artifacts/constant-0.png) | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-0.png) |
| Constant 0.05 | [PNG](../images/pr6102/native/bc/before/boundary-artifacts/constant-0_05.png) | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-0_05.png) |
| Constant -0.05 | [PNG](../images/pr6102/native/bc/before/boundary-artifacts/constant-n0_05.png) | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-n0_05.png) |
| Constant 5000000 | HTTP 503; see browser screenshot above | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-5000000.png) |
| Constant 12500000 | HTTP 503; see browser screenshot above | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-12500000.png) |
| Constant 45000000 | HTTP 503; see browser screenshot above | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-45000000.png) |
| Constant -6000000 | HTTP 503; see browser screenshot above | [PNG](../images/pr6102/native/bc/after/boundary-artifacts/constant-n6000000.png) |
| Hemoglobin plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-2010.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-2010.png) |
| Hematocrit plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-2013.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-2013.png) |
| Sodium plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-3210.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-3210.png) |
| Glucose plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-3007.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-3007.png) |
| Cholesterol plot | [PNG](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-3120.png) | [PNG](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-3120.png) |
| Raw report 2021-01-01 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-173.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-206.pdf) |
| Raw report 2022-05-07 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-174.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-207.pdf) |
| Raw report 2023-12-29 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-175.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-208.pdf) |
| Raw report 2025-10-02 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-176.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-209.pdf) |
| Raw report 2026-11-01 | [PDF](../images/pr6102/native/bc/before/qualified-artifacts/FAKE-PWb8c7a5463e8bc71b-177.pdf) | [PDF](../images/pr6102/native/bc/after/qualified-artifacts/FAKE-PW6bfce92b81844504-210.pdf) |

## Additional candidate controls

These are supplemental candidate checks, with no baseline pair. The independent-provider fixture is explicit setup; its test still uses the native unlink UI. The eForm check runs after renderer outage/recovery and compares actual downloaded PDF marker coordinates with an independently laid-out bare template. Its screenshot deliberately shows the fixture-triggered save-alert styling; it is not evidence of a server save failure.

### ON additional controls

[Independent/automatic routing screenshots](../images/pr6102/native/on/after/unlink-controls/captures.json) · [eForm screenshot](../images/pr6102/native/on/after/eform/eform-bootstrap-print-fidelity.png) · [eForm PDF](../images/pr6102/native/on/after/eform/eform-bootstrap-print-fidelity.pdf)

### BC additional controls

[Independent/automatic routing screenshots](../images/pr6102/native/bc/after/unlink-controls/captures.json) · [eForm screenshot](../images/pr6102/native/bc/after/eform/eform-bootstrap-print-fidelity.png) · [eForm PDF](../images/pr6102/native/bc/after/eform/eform-bootstrap-print-fidelity.pdf)

## Reproduction

Use disposable installed containers, with the validation helper’s `/root/suite-env.sh` and `CARLOS_TEST_ROOT` pointing at the checkout. Keep one workflow at a time per database. Each checker registers owned fixture cleanup and refuses namespace collisions. Do not publish the environment file or renderer capability URL.

```bash
. /root/suite-env.sh
export CARLOS_DISPOSABLE_HOST=true EXCLUSIVE=1
export CARLOS_TEST_ROOT=/root/carlos
export NATIVE_SCREENSHOT_DIR=/root/native-proof/run
node --require docs/images/pr6102/capture/capture-native-pages.cjs \
  docs/images/pr6102/capture/cdm-tabs-native.cjs
```

- `scripts/lab-qualified-results-playwright-checks.js`: set `LAB_QUALIFIED_ARTIFACT_DIR` to a private `/tmp/` directory for actual PNG/PDF bytes.
- `docs/images/pr6102/capture/boundary-native.cjs`: set `MEASUREMENT_PLOT_ARTIFACT_DIR` for native chart PNGs and per-case verdicts.
- `docs/images/pr6102/capture/unlink-native.cjs`: `UNLINK_AUDIT_FAULT=1` enables the physical late audit fault; `UNLINK_ROUTE_CONTROL=1` additionally sets up independent and obsolete automatic provider-route controls.
- `docs/images/pr6102/capture/outage-native.cjs`: stops/restarts only the disposable container’s renderer service and verifies visible retry and clinical-state preservation.
- `scripts/eform-bootstrap-print-fidelity-playwright-checks.js`: set `EFORM_SCREENSHOT_DIR` to retain the actual PDF and layout screenshot.

The capture utilities are reproduction aids; their hashes identify the retained versions. Screenshot/response hashes identify the retained bytes. They do not claim a signed execution attestation for every earlier fixture iteration.
