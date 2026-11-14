# CDS export/import bounds audit (worktree `integ-next` @ f3f0538654)

I read the code as it stands. Nothing was edited. I read CLAUDE.md first. Two other agents are changing the demographic and clinical mappers, so the mapper rows (E50, E51) need a second look once their work lands.

## Summary

- **C: arbitrary bounds that can refuse, hold or fail something.** There are 21 rows. The ones real data can reach:
  - **E1** `CdsExportWebService.parameters`: each request parameter is capped at 120,000 characters. That refuses a web export of about 15,000 or more explicitly listed patients. The docs say the form has no limit of CARLOS's own.
  - **E40–E43**: four counts left behind after the docs say they were removed. Each one fails the whole job; none of them holds a patient.
  - **I2** `cds.import.upload.maxFiles` (20,000): the import page accepts many files at once, and each upload stays counted until its run takes its snapshot.
  - **I29** `cds.import.native.maxNoteBytes` (256 KiB): a longer ClinicalNote, which the schema allows, becomes a placeholder note in the chart.
  - **I53**: at most 128 corrections per source file, so a file with more than 128 invalid values cannot be imported.
  - **I8**: an 8,192-character limit on import delegation lists, about 480 providers.
  - **I35**: the receipt counts `partial_date` and other dependency rows by key, so it can pick up rows the import never wrote.
  - **E54**: a clinic logo over 4 MiB is left out of the consultation letter.
  - **W5**: the export page gives up on every request after 15 seconds, including the submit.
  - **S1/S2, E21**: size and count caps on hashing CARLOS's own classes and the JDK. Clinic data cannot reach them; growth of CARLOS or a different JDK would fail every export and import.
- **D: needs your decision.** 14 rows: the receipt/witness format (512 columns, 192 rows, 8 MiB), the 3-second lock probes, the 4,096 cap on held patients in the diagnostic report, package retention (60 s to 30 days), the 24-hour idle upload cleanup, namespace-prefix variety in the import parser, the CLI heap ceiling, upper limits on settings, the nginx read timeout during downloads, the retention-sweep livelock, the CLI 30-second shutdown wait, and the import page's 30-second wait.
- **All the already-classified items still hold** (Part 1), with three caveats: the retention sweep can livelock on a huge directory, the export XML check has two more integrity bounds of the same kind, and `RESOURCE_LIMIT` is thrown only in `DrugDaoImpl`, which is outside the named scope.

**Path aliases (each one stands for an absolute path):**
- J = `/home/user/build/wt-integ/src/main/java/io/github/carlos_emr/carlos/`
- EXS = J`demographic/cdsexport/service/`, EXD = J`demographic/cdsexport/dto/`, EXV = J`demographic/cdsexport/validator/`, EXW = J`demographic/cdsexport/web/`, EXC = J`demographic/cdsexport/cli/`, EXP = J`demographic/cdsexport/support/`
- IMS = J`demographic/cdsimport/service/`, IMD = J`demographic/cdsimport/dto/`, IMP = J`demographic/cdsimport/support/`, IMW = J`demographic/cdsimport/web/`, IMA = J`demographic/cdsimport/assembler/`, IMC = J`demographic/cdsimport/cli/`
- CDS = J`demographic/cds/`, DAO = J`commn/dao/`, UTL = J`utility/`
- JS = `/home/user/build/wt-integ/src/main/webapp/js/demographic/`
- DEB = `/home/user/build/wt-integ/debian/assets/`

---

## Part 1: Already-classified items, re-checked

| Item | Where | Still accurate? |
|---|---|---|
| `RESOURCE_LIMIT` (callers pass `Integer.MAX_VALUE`) | J`commn/dao/DrugDaoImpl.java`:96-98 is the only place it is thrown. Callers: EXS`CdsExportDrugReferenceProjectionService.java`:51 and EXS`CdsExportDrugReferenceSourceReader.java`:154. EXS`CdsExportClinicalPackageService.java`:1544 maps it to a job failure. | Yes, it cannot be reached. The `maximum` parameter and the throw are dead code; delete them. |
| XML parser integrity bounds | EXV`CdsExportXmlValidator.java`: MAX_MARKUP :78, MAX_RESTRICTED_VALUE :84, MAX_NAMES :87, MAX_NAME :90, MAX_URI :96, MAX_ATTRIBUTES :99, MAX_DEPTH :102, MAX_CAPTURE :109. Checked at :295-299, :319-320, :418, :512-514. | Yes, they only check what CARLOS itself wrote. Two bounds of the same kind are missing from your list: MAX_NAMESPACES 64 (:93, :299) and the 8,192-character reference path (:542). The identity-value guard is gone (:393). |
| `CdsExportDemographicCaptureReader.MAX_ROW_BYTES` | EXS:83, :109 (1 MiB per demographic row). Also DAO`DemographicDaoImpl.java`:174 (65,536 bytes per column). | Yes, column capacity: `demographic` has no TEXT or BLOB columns. |
| `CdsExportRunDao.now` | DAO`CdsExportRunDao.java`:158-169 | Yes. It reads the database clock and has no statement or transaction timeout of its own; the ledger no longer sets the 15-second timeout. |
| Retention sweep, 15 minutes per cleanup or trim | EXS`CdsExportRetentionService.java`:264-274 (trim) and :297-303 (purge), ending with `CDS_EXPORT_CLEANUP_STOPPED`; the next sweep retries. | Yes, with one caveat (D-row R11): the purge validates the whole directory again on every sweep, so a directory too big to validate in 15 minutes is never erased. It holds disk indefinitely, which can later cause `CDS_EXPORT_DISK_RESERVE`. |
| eForm renderer refusals | J`eform/util/EFormBrowserPdfService.java`: 200 pages :258, 150 MiB :262, page dimension 20,000 :263, 90 s render :116, 30 s page and script :119-120, 30 s driver start :187, 2 slots :190. EXS`CdsExportEformRenderService.java`:55-71 and :314-336 turn each failure into a data summary; a timeout or browser failure is tried twice. | Yes, nothing is held. |
| Lease 120 s, heartbeat 20 s | EXS`CdsExportLedgerService.java`:81. EXS`CdsExportExecutionService.java`:305-306 (100 s renewal window), :486-517 (20 s beat), ~:670 (stall detector). | Yes. |
| `cds.import.drainTimeoutSeconds` | IMS`CdsImportMaintenanceService.java`:132-134 (query timeout on the admission-row `FOR UPDATE`, then `MAINTENANCE_ACQUISITION_FAILED`; the run resumes). | Yes, kept as you decided. |

**`CdsTargetReceiptService`, now classified** (rows I35–I38):
- The 64-row limit (:302-307, :326-331, :351-356, :375-380) counts every row returned by a dependency query: `partial_date` by `(table_name, table_id)`, issue links by `note_id`, `preventionsExt` by `prevention_id`, and file references by `(run_id, unit_key)`. The import itself writes at most 2, 1, 14 or 34 such rows (IMS`CdsMedicationPersister.java`:95-96, IMS`CdsAllergyPersister.java`:68-69, IMS`CdsImmunizationPersister.java`:102).
- So the limit is reached only through rows the import did not write: orphaned or duplicate `partial_date` rows. That table has no unique key and no foreign key (V1 baseline :10017-10024; only an index in V1.0.59.8.1:604). Such rows should not be in a receipt in the first place.
- Classification: **C**. Capture the receipt from the rows the unit actually wrote and delete the count.
- The 512-column limit (:467) is part of a format shared with the export reader (I37/S8/S9): **D**.

---

## Part 2: The table

Classes: **A** threshold that only changes how work is done · **B** external or integrity limit (reason given) · **C** arbitrary; delete · **D** your decision.

### Export: request, history, report

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| E1 | EXW`CdsExportWebService.java`:101-108 (applied by EXW`SubmitCdsExport2Action.java`:76) | 120,000 characters per parameter (`patientNos`, `physicianNos`) | `CDS_EXPORT_PARAMETERS_INVALID`; the submission is refused | Yes. At 7–8 characters per patient, about 15,000 explicit patients. The packaged Tomcat allows 50 MiB (DEB`tomcat/server.xml`:67), so this CARLOS limit is the one that bites. | **C** |
| E2 | EXW`CdsExportStatus2Action.java`:85; EXS`CdsExportLedgerService.java`:2110 | History page of 1–100 jobs | Request refused | Only from a hand-built request; the page asks for 25 | A |
| E3 | DAO`DemographicDaoImpl.java`:109-111, :129; EXS`CdsExportSelectionService.java`:56, :58 | 100 physicians and 500 rows per roster statement; 500 ids per selection statement (the page is 256, physicians go in groups of 100) | IllegalArgumentException | No; the callers split their work | A |
| E4 | EXS`CdsExportSelectionService.java`:267; EXS`CdsExportCohortService.java`:611; EXS`CdsExportConfigurationService.java`:147-153 | Physician or provider number up to 16 characters; operator name up to 64 characters | Refusal | No: `provider_no` is VARCHAR(6) | B |
| E5 | EXS`CdsExportCohortService.java`:76, :249-271, :285, :297, :318 | 512 KiB single-line manifest; 8 KiB write batch | Changes how the manifest is stored (one line or two); the read-back checks integrity | n/a | A |
| E6 | EXS`CdsExportLedgerService.java`:79, :374, :935, :945, :1914, :1965, :1993 | 262,144-character job-document line | A longer document is stored as two lines | n/a | A |
| E7 | EXS`CdsExportLedgerService.java`:2086, :2097-2099 | Operator and submission tokens up to 128 characters | Refusal | Column `VARCHAR(128)` (V1.0.59.41:7) | B |
| E8 | EXS`CdsExportLedgerService.java`:93, :266-275, :329 | Diagnostic report (browser and `--report`) lists at most 4,096 held charts | The list is cut and marked truncated; only the ReadMe, which is encrypted for the recipient, names them all | Yes, for example a 50,000-patient job with 8% held. The guide promises the report lists each held chart. | **D** |
| E9 | EXS`CdsExportLedgerService.java`:248-251, :327 | 128 recent events in the report | Truncated with a flag | Yes, on long jobs | **D** (with E8) |
| E10 | EXS`CdsExportLedgerService.java`:304 | Up to 64 unlinked-source codes | The report fails (`CDS_EXPORT_DIAGNOSTIC_INVALID`) | No: there are 7 fixed tables (J`commn/dto/CdsExportUnlinkedSourceDto.java` TOKENS) | B |
| E11 | EXD`CdsExportCoverageDto.java`:33, :49 | 16 issue previews | Previews only; the counts are exact | n/a | A |
| E12 | EXV`CdsExportCoverageValidator.java`:57, :117 | `maximumOccurrences` | `CDS_EXPORT_COVERAGE_LIMIT_EXCEEDED` | No: the only caller passes `Long.MAX_VALUE` (EXS`CdsExportPatientDocumentService.java`:333) | A (dead parameter) |

### Export: settings and application identity

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| E13 | EXS`CdsExportConfigurationService.java`:106-116 | `cds.export.maxDurationMillis`: a stall window, default 24 h, accepted range 1 ms–31 days | `CDS_EXPORT_WORKER_STALLED` only after no checkpoint for the whole window. A value over 31 days refuses every export with `CONFIGURATION_INVALID`. | Only a hung worker | A for the window; **D** for the 31-day maximum |
| E14 | EXS`CdsExportConfigurationService.java`:92-97 | `cliRendererHandoffSeconds`, 120 (0–86,400) | Changes how a CLI job is handed to the web renderer | n/a | A; the maximum is **D** (R8) |
| E15 | EXS`CdsExportArtifactService.java`:81-83, :300-307, :345-366 | `downloadTimeoutSeconds`, 900 (1–86,400): a no-progress window. Every verification step is progress (probe :353-366). | `CDS_EXPORT_DOWNLOAD_STOPPED`; download again | Only silence | A |
| E16 | EXS`CdsExportActivePlanService.java`:106; EXS`CdsExportPublicationService.java`:59 | `retentionSeconds`, 7 days (60 s–30 days) | After expiry the download is refused (`CDS_EXPORT_RETENTION_EXPIRED`); a value outside the range refuses every export | Yes, a package collected after 7 days | **D** |
| E17 | EXS`CdsExportStagingFactory.java`:111, :137, :318-324 | `minimumFreeBytes`, 512 MiB (0–1 TiB) | `CDS_EXPORT_DISK_RESERVE` (your rule 1) | Real disk only | A for the reserve; the 1 TiB maximum is **D** |
| E18 | EXS`CdsExportEformRenderService.java`:285-289 | `eformRenderIntervalMillis`, 250 (0–60,000) | Pacing between renders | n/a | A (maximum: R8) |
| E19 | EXS`CdsExportActivePlanService.java`:120-162 | Retired settings | Ignored, with one WARN | n/a | A |
| E20 | EXS`CdsExportActivePlanService.java`:86-92; EXS`CdsExportImplementationIdentityService.java`:163 | Build version up to 128 characters | `..._BUILD_VERSION_UNAVAILABLE` | CARLOS's own build string | B |
| E21 | EXS`CdsExportImplementationIdentityService.java`:39, :92, :133, :175, :181 | At most 32,768 resources per pattern, 128 MiB per BouncyCastle jar, 65,536 entries, 4,096-character names, 512 MiB hashed in total | `CDS_EXPORT_IMPLEMENTATION_IDENTITY_UNAVAILABLE`: **every export fails** | Not by clinic data. CARLOS's own growth reaches it (about 7.5k classes plus 14 MB of migrations today, roughly 4x headroom). | **C** (latent) |

### Export: capture and database reads

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| E22 | DAO`CdsExportStatementWaitReadSupport.java`:80, :83, :86 | 30 s first wait, doubling; 30 s grace; MariaDB's maximum of 31,536,000 s | The statement is sent again; silence past the grace counts as a lost connection and the chart is read again | n/a | A (the MariaDB maximum is B) |
| E23 | EXS`CdsExportNativeCaptureService.java`:315 | 3 further readings after a lost connection | `CDS_EXPORT_DATABASE_UNAVAILABLE` (rule 1, resumable) | Only if the database is really gone | A |
| E24 | EXP`CdsExportSourceReadLock.java`:155 | 5 s `isValid` check on the idle lock connection | The connection is replaced; nothing is lost | n/a | A |
| E25 | DAO`CdsExportPatientTableReadSupport.java`:1414, :1508, :2157, :1450, :1991, :2191, :2561; J`casemgmt/dao/CdsExportNoteAccessDao.java`:40, :50, :130; EXS`CdsExportPatientAuthorization.java`:157; EXS`CdsExportClinicalPackageService.java`:1647 | Batches of 8,192 ids, restrictions and notes; 65,536-key windows; 4,096 grants; 32-row pages; 64 KiB chunks | Changes only how much one statement reads | n/a | A |
| E26 | Readers that page by 32: EXS`CdsExportPublicProviderSourceReader.java`:139, `CdsExportHospitalReportFileSourceReader.java`:140, `CdsExportDocumentFileSourceReader.java`:110, `CdsExportImportOptionsSourceReader.java`:140, `CdsExportNoteIssueLinkSourceReader.java`:86, `CdsExportRetainedTableSourceReader.java`:119, `CdsExportLaboratorySourceReader.java`:133, `CdsExportPatientTableSourceReader.java`:181, `CdsExportPreventionSnapshotService.java`:425, :493; DAO`CdsExportExactNumericReadSupport.java`:42, :107; DAO`CdsExportProviderReadSupport.java`:69; DAO`CdsExportDataSummaryReadDao.java`:303, :495 | Page size | Only a broken DAO contract fails | n/a | A |
| E27 | EXS`CdsExportClinicalOccurrenceService.java`:58, :60, :86; EXS`CdsExportSectionWindowPlanner.java`:86-111; EXS`CdsExportCapturedCareRowsReader.java`:191-219 | Windows of 8,192 rows and 64 MiB; fields and bytes `UNLIMITED` | A new window starts | n/a | A |
| E28 | EXS`CdsExportVerifiedCaptures.java`:89-93, :130, :220-229, :257, :279, :300, :320, :325 | Cache sized at heap/16 (32–256 MiB), up to 64 entries | The capture is not cached and is verified again | n/a | A |
| E29 | DAO`DemographicDaoImpl.java`:213-214, :225; DAO`CdsExportPatientTableReadSupport.java`:1877-1878, :1910-1922; DAO`CdsExportDataSummaryReadDao.java`:222-223, :244-256; EXD`CdsExportCapturedTableDto.java`:82; EXD`CdsExportCapturedTextRowDto.java`:37 | Up to 4,096 columns and 16 key columns | Review or invalid | MariaDB's own limits | B |
| E30 | DAO`CdsExportDataSummaryReadDao.java`:100, :179, :458; DAO`CdsExportProviderReadSupport.java`:111 | Keyless `mds*` tables: up to 64 columns and 1,020 bytes a value; provider cells up to 1,024 bytes | Invalid | Fixed tables of VARCHAR(255) | B |
| E31 | EXS`CdsExportStagingService.java`:92, :100-124, :220, :668, :794 | `maxBytes` per staged member | `CDS_EXPORT_STAGED_BYTE_LIMIT` | No: production uses the 6-argument constructor, which passes `Long.MAX_VALUE` | A (dead constructor) |
| E32 | EXD`CdsExportNativeCaptureDto.java`:39; EXS`CdsExportNativeCaptureService.java`:70, :280, :740, :1062; EXS`CdsExportNativeCaptureReader.java`:70, :131, :193; EXS`CdsExportNativeCaptureComposer.java`:126, :199, :248; EXD`CdsExportCapturedFieldDto.java`:37; EXD`CdsExportNativeFieldKeyDto.java`:28 | Up to 256 captured tables, 2,048-character record keys, 4 MiB per manifest string | Invalid | The capture plan is fixed; keys are short | B |

### Export: mapping and holds (code being changed by the mapper agents)

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| E40 | EXD`CdsExportDemographicEnrolmentProjectionDto.java`:41 | Up to 65,536 enrolment source rows (1 plus the `demographicArchive` rows); built at EXS`CdsExportNativeDemographicAssembler.java`:205 | IllegalArgumentException: **the whole job fails** (it is not a hold) | Yes in principle: every demographic save adds an archive row, and integrations save often. The docs (cds-export.md:1937) say this limit was removed. | **C** |
| E41 | EXD`CdsExportPublicProviderLookupsDto.java`:30 (from EXS`CdsExportDemographicOccurrenceService.java`:362) | Up to 65,536 provider rows per chart | The job fails | Not in practice; the docs say it was removed | **C** |
| E42 | EXV`CdsExportDemographicClosureValidator.java`:56 | Up to 65,536 source fields of an imported Demographics record | `..._CLOSURE_SCOPE_INVALID`: the job fails | A schema-valid file can reach it (Contact and EnrolmentHistory repeat without limit). The docs (cds-export.md:1962) say it was removed. | **C** |
| E43 | CDS`contract/CdsSchemaContentOrder.java`:51 | Up to 131,072 children of one retained element (EXS`CdsExportRetainedSectionWriter.java` rebuilds imported records with it) | `CDS_SCHEMA_CONTENT_ORDER_INVALID`: the job fails | A schema-valid file can reach it (ResultReviewer, NoteReviewer and others are unbounded) | **C** |
| E44 | CDS`contract/CdsSchemaContentOrder.java`:85 | Repeating group | `..._REPEATING_GROUP_REQUIRES_REVIEW` | No: the pinned XSD has no sequence or choice with maxOccurs over 1 | B |
| E45 | EXS`CdsExportDemographicRetainedCoreReconciler.java`:81 | Up to 16 uses of one provider field | `..._PROVIDER_USES_INVALID`, job | No: there is one use per role and uses are deduplicated (:640-643) | B |
| E46 | EXS`CdsExportDataSummaryRowsReader.java`:84, :387-388 | One value up to 2 GiB − 8 (Java array) | **Holds** the patient with `CDS_EXPORT_DATA_SUMMARY_SIZE_LIMIT` | No: no screen can store more than 1 GiB (`max_allowed_packet`) | B (it holds if reached; R-note) |
| E47 | EXS`CdsExportHistoryNativeReconciler.java`:555-556 | 131,072 UTF-16 units of one imported Notes | Hold | No: the import validated the text32K/text64K facet | B |
| E48 | EXS`CdsExportNativeHistoryXmlWriter.java`:139-145; EXS`CdsExportNativeHistoryMapper.java`:116-122, :548 | CPP Notes limits of 32,000, 64,000 and 1,000 | Hold if the archived statement does not fit | No: the fit reserves room for the statement (:548) | B |
| E50 | Dictionary limits that write an element plus a carrier: EXS`CdsExportDictionaryLength.java`:51-66, `CdsExportPatientAlertMapper.java`:63/:82, `CdsExportScreeningMapper.java`:60/:255-276, `CdsExportProblemRegistryMapper.java`:78, `CdsExportAppointmentMapper.java`:66/:121/:130/:554, `CdsExportImmunizationMapper.java`:242/:417-444/:619/:647, `CdsExportAllergyMapper.java`:254/:539, `CdsExportPreparedAllergyMapper.java`:64, `CdsExportNativeLaboratoryMapper.java`:174/:796, `CdsExportNativeCareMapper.java`:110, `CdsExportReportContext.java`:39/:69, `CdsExportDemographicMapper.java`:350-365/:536-550, `CdsExportPersonalContactMapper.java`:375, `CdsExportHistoryFieldReconciler.java`:952-953, `CdsExportNativeHistoryMapper.java`:860-866, `CdsExportImportedDocumentEditReconciler.java`:207, `CdsExportRetainedPharmacyBinding.java`:140, `CdsExportReportFileFormats.java`:140 | text1K/32K/64K and facets of 250, 120, 60, 50 and 20 | The element follows a documented rule and the exact value travels in a carrier | — | B (re-check after the mapper agents finish) |
| E51 | XSD and column checks in DTOs: EXD`CdsExportPublicProviderDto.java`:28-35 (50/6), `CdsExportPersonalContactDto.java`:61-72 (50/200, email facet 50), `CdsExportAppointmentDto.java`:34-36 (250/250/32,768), `CdsExportAppointmentTimingDto.java`:36 (1,440 minutes), `CdsExportDrugReferenceDto.java`:51-60 (1,024/128), `CdsExportNoteRevisionDto.java`:30-31 (256 bytes), plus every year 1–9999 check | Facet or column width | Refusal | No: provider names are VARCHAR(30), imported names were validated at 50, appointment times are TIME columns, the note metadata columns are short. I could not see the drugref column widths from this repository. | B |
| E52 | EXS`CdsExportPathComposer.java`:56, :99, :120; EXS`CdsExportPackagePaths.java`:55, :70, :83; EXS`CdsExportDocumentInputStream.java`:140 | 65,535-byte ZIP name; name parts of 1,024 and 4,096; 8,192-character path; 128 path parts; `docfilename` up to 1,024 | Review or invalid | No: the ZIP length field is 2 bytes; names are VARCHAR(50)/(30); `docfilename` is VARCHAR(255) | B |
| E53 | EXS`CdsExportHospitalReportMessage.java`:55; EXS`CdsExportHospitalReportOccurrenceService.java`:283-284 | HRM messages up to 64 MiB are parsed in memory | A larger message is attached exactly as stored instead of parsed | Rarely | A (changes the form, never whether it is sent) |
| E54 | EXS`CdsExportConsultationLetterComposer.java`:93, :470 | Clinic logo up to 4 MiB | The logo is left out, with a `LOGO_UNAVAILABLE` note | Yes, a high-resolution logo | **C** |
| E55 | EXS`CdsExportDataSummaryPdfService.java`:123; EXS`CdsExportDataSummaryPlanner.java`:368 | 6 attempts to get a render slot; parts of 400,000 / 1,000,000 / 64 MiB / 32 MiB | Images are attached instead of drawn; the summary is written in parts | n/a | A |
| E56 | EXS`CdsExportEformRenderService.java`:164-176 | Breaker after 3 failures for 5 minutes; queue pauses of 1–30 s | The export waits | n/a | A |
| E57 | Projection services nothing in production calls (only the CLI `@Import` and tests): EXS`CdsExportAppointmentProjectionService.java`:96-98, :133-146; `CdsExportMedicationProjectionService.java`:76, :95, :112, :124; `CdsExportAllergyProjectionService.java`:76, :93; `CdsExportImmunizationProjectionService.java`:108-110, :168-175 | 131,072 rows / 64 MiB | `..._RESOURCE_LIMIT` | Not reachable (dead code) | **C** (dead code) |
| E58 | EXS`CdsExportNativeFieldAssessmentValidator.java`:77 | `maximumFields` 1–1,000,000 (every caller passes 1,000,000; nothing counts against it any more) | IllegalArgumentException | No | A (dead parameter) |

### Export: imported-chart readers (the importer's stored format), packaging, XML, time

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| E60 | EXS`CdsExportRetainedIndexService.java`:433, :454, :462; EXS`CdsExportRetainedReceiptVerificationService.java`:107; EXD`CdsExportRetainedReceiptVerificationDto.java`:131; EXS`CdsExportImmunizationImportReceipts.java`:67, :70, :155; EXS`CdsExportRetainedPreparationReader.java`:54, :382-383; EXS`CdsExportRetainedPayloadService.java`:61, :253, :303; EXS`CdsExportRetainedImportOptionsReader.java`:102, :147; EXS`CdsExportImportOptionsSourceReader.java`:181; EXS`CdsExportRetainedSectionWriter.java`:138, :190, :225; plus the facet readers (`CdsExportRetainedPharmacyBinding.java`:153, `CdsExportRetainedContactBinding.java`:181, `CdsExportPatientIdentityReconciler.java`:95, `CdsExportAppointmentOriginalValues.java`:124, `CdsExportDemographicRetainedPhonesReconciler.java`:637-643, `CdsExportContactFieldReconciler.java`:960-967, `CdsExportDemographicNamesReconciler.java`:365-372, `CdsExportAllergyRetainedAssembler.java`:54, :372) | 192 references, 65,536-character receipts, 1 MiB preparations, 65,536-byte job document, XSD facets | Refusal | No: these mirror the importer's own writers. The 192/512 coupling is row S8/S9 (D). | B |
| E61 | EXS`CdsExportJsonLines.java`:56; EXS`CdsExportClinicalPackageService.java`:984, :1131, :1683; EXS`CdsExportDisclosureScopeService.java`:118; EXS`CdsExportClinicalPackageService.java`:1514; EXV`CdsExportSupportingFilesValidator.java`:50, :440 | 1 MiB per line CARLOS wrote; up to 1,024 listed tables; 65,536 characters per ReadMe line | Invalid | No: notes are split at 8,192; the listed tables are fixed catalogue entries | B |
| E62 | EXS`CdsExportSpoolService.java`:55, :58; EXS`CdsExportPackageInventoryService.java`:46-47; EXS`CdsExportDeliveryComposer.java`:81, :84; EXS`CdsExportPackageComposer.java`:203; EXV`CdsExportPackageValidator.java`:187 | 4 MiB batches, fan-in of 32, 4,096 / 8 MiB sort runs, 32 GiB per message then 16 MiB parts, ZIP64 above 65,535 entries or 4 GiB | Changes how the package is written | n/a | A |
| E63 | EXS`CdsExportDeliveryComposer.java`:305 | Recipient RSA key of at least 2,048 bits | A weak key is refused | n/a | B (security) |
| E64 | EXS`CdsExportXmlComposer.java`:348, :617 | Depth 252; a byte limit (callers pass `Long.MAX_VALUE`, EXS`CdsExportPatientDocumentService.java`~:297) | — | No | B / A |
| E65 | EXS`CdsExportLedgerService.java`:87; EXS`CdsExportArtifactService.java`:393 | Kept charts for 7 days; re-verification every 30 s | After 7 days a resume rebuilds every chart | n/a | A |
| E66 | EXS`CdsExportDisclosurePolicyService.java`:62, :103-131, :158-165, :171-172 | Each policy file up to 32 KiB; up to 64 recipients and certificates and 100 audit plans; an audit plan names up to 64 recipients and 100 providers | `AUDIT_ACCESS_UNAVAILABLE` or `RECIPIENT_UNAVAILABLE`: the job is refused | Yes, more than 100 exporting providers under one plan. These are operator files, but the numbers are arbitrary. | **C** |
| E67 | EXC`CdsExportCli.java`:301, :312 | 30 s for the progress thread to shut down | `CDS_EXPORT_PROGRESS_CLEANUP_INCOMPLETE` after the job's own outcome | Yes, a slow database when the CLI exits | **D** |
| E68 | EXC`CdsExportCli.java`:289; EXC`CdsExportSpringRuntime.java`:67 | Cause depth 16 in logs; 64 KiB bootstrap | Logging only / integrity | n/a | A / B |

### Shared (`demographic/cds`): affects both import and export

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| S1 | IMS`CdsRulesetFingerprint.java`:257, :278, :286, :327 | Up to 4,096 resources per pattern, 256 nested classes, 8,192 classes, 16 MiB per resource | `IMPORT_RULESET_IMPLEMENTATION_UNAVAILABLE` stops every import, and every export too, because the export identity hashes this result (EXS`CdsExportImplementationIdentityService.java`:47-49) | Not by data; only by CARLOS's growth (about 520 matching classes today) | **C** (latent) |
| S2 | IMS`CdsRuntimeIdentity.java`:47, :48, :122, :231, :237, :239, :241, :285 | 128 MiB jar; 512 MiB JDK `lib/modules` (140 MB today); 1,024-character JVM properties; 4,096 time zones; 16 versions; 64 MiB / 1 MiB of time-zone rules | Same as S1 | A different JDK distribution | **C** (latent) |
| S3 | CDS`cli/CdsProcessConfiguration.java`:39, :69, :77 | Configuration file up to 1 MiB; heap 128–16,384 MiB | The CLI refuses to start | The heap ceiling, yes: the guide says a long imported chart needs heap and otherwise "does not finish" | **D** |
| S4 | CDS`cli/CdsProcessDelegationVerifier.java`:119, :143, :157, :160 | Delegation token up to 64 KiB, at most 300 s old | Denied | CLI handshake | B (security) |
| S5 | CDS`cli/CdsProcessPersistence.java`:74 | 10 s validation query | The connection is replaced | n/a | A |
| S6 | CDS`jdbc/CdsBinaryReads.java`:48; CDS`jdbc/CdsMariaDbBinaryRead.java`:42 | SQL text up to 65,536 characters; up to 66,560 parameters | `CDS_BINARY_SELECT_REQUIRED` | The SQL length is reached by wide tables (see I38). The parameter count sits at MariaDB's protocol limit of 65,535. | **C** (SQL length) / B (parameters) |
| S7 | CDS`jdbc/CdsMariaDbBinaryRead.java`:175-235 | Client-side deadline | `SQLTimeoutException`, then the read is sent again with twice the wait | n/a | A |
| S8 | CDS`contract/CdsNativeRowState.java`:72; CDS`contract/CdsNativeRowDigest.java`:41, :44, :86; DAO`CdsExportNativeMetadataReadSupport.java`:113; EXD`CdsExportCapturedTableDto.java`:73 | Up to 512 receipt columns; names up to 128 characters | Every export of an imported chart is refused | Only if a CARLOS target table is extended locally; InnoDB allows 1,017 columns | **D** |
| S9 | CDS`contract/CdsNativeBaselineCodec.java`:67, :70, :86, :92, :148, :165, :233, :364, :376 | 8 MiB witness, 12 MiB sealed, up to 192 rows, 65,536-character receipt | `NATIVE_BASELINE_REQUIRES_REVIEW` | Sized for 192 rows of the 65-column `drugs` table; a widened table reaches it. The columns themselves are LONGTEXT (V1.0.59.40:4-5; V1.0.59.8.1:85, :113). | **D** |
| S10 | CDS`contract/CdsXmlReaders.java`:66-80, :153; CDS`contract/CdsScalarParser.java`:165-166 | JDK backstops set to the CDS bound + 1; markup and constrained values up to 65,536 | `CDS_XML_*` | No: the longest facet in the schema is 65,536 | B |

### Import: admission, upload, settings

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| I2 | IMS`CdsUploadService.java`:152-155 | `cds.import.upload.maxFiles` 20,000 uploads holding a reservation | `UPLOAD_CAPACITY_BUSY` (HTTP 429) | Yes. The page takes `multiple` files (J`../webapp/WEB-INF/jsp/demographic/demographicImport.jsp`:79; JS`cds-import.js`:719-727), and each reservation is released only when its run's snapshot is published (:652-664). A clinic sending one XML file per patient for 20,000+ patients hits it. | **C** |
| I3 | IMS`CdsUploadService.java`:722-741 | `ttlHours` 24 of idleness for an unsubmitted upload | The upload is abandoned and deleted | Yes, a resumable upload left over a weekend | **D** |
| I4 | IMS`CdsUploadService.java`:90; JS`cds-import.js`:301-315, :685-693 | 120 s with no bytes on a chunk; 3 attempts | HTTP 408, then resume from the upload list | Only a stalled link | A |
| I5 | IMS`CdsUploadService.java`:745-748, :846, :900 | Chunk 64 KiB–16 MiB; 512 MiB disk reserve; file name up to 1,000 characters | 503 for a bad setting / 507 for the reserve / refusal | The 16 MiB chunk matches ModSecurity's limit (DEB`modsecurity/main.conf`:48); file systems allow 255-byte names | A / A / B |
| I8 | IMS`CdsImportConfigurationService.java`:273-278 | Any list setting up to 8,192 characters | `IMPORT_REGISTRY_CONFIGURATION_INVALID`: every source and delegation check fails | Yes, delegating about 480+ providers to one operator. Export has no such cap. | **C** |
| I9 | IMA`CdsImportViewModelAssembler.java`:49-55; IMS`CdsCourseService.java`:181 (and :99, a page of 200) | Namespaces up to 8,192 characters and 200 entries; educators up to 8,192 characters | The import page fails, or course access is denied | Configuration only | **C** (low) / A |
| I10 | IMS`CdsImportConfigurationService.java`:142 | Namespace up to 64 characters (the column is VARCHAR(128) and the token rule allows 128) | `IMPORT_SOURCE_NAMESPACE_TOO_LONG` | Configuration only | **C** (low) |
| I11 | IMS`CdsImportConfigurationService.java`:49-54, :281 | Digest cache of 64; tokens up to 128 characters | Cache / refusal | VARCHAR(128) | A / B |
| I12 | IMS`CdsPreacceptService.java`:97, :146, :204, :268, :383, :492-493, :573, :863, :876 | Lock wait, 64 roots, pre-accept staging kept 24 h, CARLOS's own 64 KiB index rows | Busy / cleanup | n/a | A / B |

### Import: XML input guards (IMS`CdsXmlParser.java`, IMS`CdsSnapshotService.java`, IMD`CdsInputLimitsDto.java`)

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| I15 | Parser :499; snapshot :877; DTO :96 | Depth 256 | `CDS_XML_DEPTH_LIMIT` | No: the schema is 8 levels deep | B |
| I16 | Parser :92, :496; snapshot :873 | 128 attributes | `..._ATTRIBUTE_RESOURCE_LIMIT` | No: at most 2 plus the xsi attributes | B |
| I17 | Parser :515 | 128 different child names under one element | `CDS_XML_CHILD_NAME_LIMIT` | No: the XSD's largest is 36 and it has no `xs:any` | B |
| I18 | Parser :533; snapshot :643, :889 | Paths up to 8,192 characters | `CDS_XML_PATH_LIMIT` / `CDS_SOURCE_MEMBER_PATH_INVALID` | No | B |
| I19 | IMP`CdsXmlText.java`:146 via CDS`contract/CdsXmlReaders.java`:153 | One tag, comment or processing instruction up to 65,536 characters | `CDS_XML_MARKUP_LIMIT` | Tags, no. A comment over 64 KiB is still schema-valid (the XSD does not govern comments), but no exporter writes one. | B (note) |
| I20 | Parser :613-618; CDS`contract/CdsScalarParser.java`:165 | A constrained value up to 65,536 characters | `CDS_XML_SCALAR_RESOURCE_LIMIT` | No: the largest maxLength is 65,536, and there is no minLength-only or unbounded-pattern type | B |
| I21 | Parser :469-476; snapshot :852-858 | 128 different prefixes and URIs | `CDS_XML_NAMESPACE_RESOURCE_LIMIT` | URIs, no (there are 3). Prefixes, yes: the XSD does not constrain prefixes, so a writer that makes up a new prefix for each declaration (`ns1`, `ns2`, …) produces a schema-valid file past this. The docs say a genuine file cannot reach it. | **D** |
| I22 | Parser :857-862; snapshot :863-865 | 4,096 different qualified names; names up to 256 characters | `CDS_XML_NAME_RESOURCE_LIMIT` | Same exposure as I21 (prefix × name: about 15 prefixes are enough) | **D** (with I21) |
| I23 | Parser :816-827; DTO :100, :103 | 16,384 identity values / 1 MiB of identity characters | `CDS_XML_IDENTITY_RESOURCE_LIMIT` | No: the schema has no ID, IDREF or IDREFS types; only a contrived `xsi:type="xs:ID"` would. The export already deleted this guard as dead code (cds-export.md:1761-1763). | B (dead) |
| I24 | Parser :411-413; IMD`CdsFieldDto.java`:35 | Schema default up to 64 characters | `..._SCHEMA_DEFAULT_RESOURCE_LIMIT` | No: the defaults are 'GIV', 'FAMC' and 'L' | B |
| I25 | IMP`CdsXmlText.java`:39, :98 | XML declaration within the first 64 KiB | `CDS_XML_DECLARATION_LIMIT` | No real writer | B |
| I26 | Parser :169, :792, :871; snapshot :162 | Diagnostic detail cut at 4,096 characters | Diagnostics only | n/a | A |
| I27 | Parser :835, :839, :846-849; snapshot :95 | Cancellation checked every 250 ms; stall window; free disk checked every 16 MiB | Rule 4 | n/a | A |

### Import: persisters, receipts, ledger, review, clocks

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| I29 | IMS`CdsHistoryPersister.java`:322-323, :372-385; IMS`CdsImportConfigurationService.java`:250 | `cds.import.native.maxNoteBytes` 262,144 (up to 393,216) | The native note is replaced by a placeholder; the full text exists only in the CDS source fields | Yes: `MyClinicalNotesContent` is `cdsd:text`, which has no length limit, and `casemgmt_note.note` is MEDIUMTEXT (16 MiB) | **C** |
| I30 | IMS`CdsDemographicDetailsService.java`:227-235; IMD`CdsDemographicDetailsDto.java`:41 | Patient note up to 65,518 bytes | A placeholder plus the source field | `demographiccust.content` is TEXT (65,535 bytes) less the frame | B (column) |
| I31 | IMS`CdsAppointmentPersister.java`:251-256, :296; IMS`CdsDemographicPersister.java`:644, :651; IMS`CdsContactNormalizationService.java`:109, :150-151; IMS`CdsClinicalNormalizationService.java`:56, :130; IMS`CdsReportImportService.java`:287; IMS`CdsDemographicRelationsService.java`:203; IMS`CdsImportFieldPersister.java`:111 | Native column widths (80, 32,768, 50, 25, 30, 101, 60, 20, 250) | The value is kept in the source fields; the native field is left blank or derived | Column widths (`appointment.reason` is VARCHAR(80), and so on) | B |
| I32 | IMD`CdsContactDto.java`:41 | Up to 3 phones, 1 role, 16 diagnostics | IllegalArgumentException | No: phones are keyed by type R/C/W and diagnostics are deduplicated (IMS`CdsContactNormalizationService.java`:79-82) | B |
| I33 | IMS`CdsNativePreparationService.java`:45, :89-97, :178 | Prepared native record up to 1 MiB | `NATIVE_PREPARATION_CAPACITY_EXCEEDED` | No: every medication, allergy and immunization field has an XSD maxLength | B |
| I35 | IMS`CdsTargetReceiptService.java`:299-310, :321-341, :347-365, :371-384 | Up to 64 rows per dependency query | `TARGET_DEPENDENCY_COUNT_REQUIRES_REVIEW`: the unit fails | Only through rows the importer did not write (`partial_date` has no unique key and no foreign key, so orphan or duplicate rows count) | **C** |
| I36 | IMS`CdsTargetReceiptService.java`:73, :143, :248, :394-395, :198, :223 | Up to 192 references; receipt up to 65,536 characters | `TARGET_REFERENCE_COUNT_INVALID` / `TARGET_RECEIPT_TOO_LARGE` | The importer's own format once I35 is exact (the largest unit has 100 references; the column is LONGTEXT) | **D** now (B after the I35 fix) |
| I37 | IMS`CdsTargetReceiptService.java`:467-468 | Up to 512 columns in a target table | `TARGET_COLUMN_COUNT_REQUIRES_REVIEW` | Only a local schema extension | **D** (with S8/S9) |
| I38 | IMS`CdsTargetReceiptService.java`:532 | Digest SQL up to 65,536 characters | `TARGET_DIGEST_QUERY_CAPACITY` | At about 430 columns, before I37; `max_allowed_packet` is the real limit | **C** |
| I39 | IMS`CdsImportLedgerService.java`:445, :3618-3626; DAO`CdsImportDependencyDao.java`:142 | Up to 8 dependencies per unit, 1,600 per page | `UNIT_DEPENDENCY_LIMIT` | No: the planner adds at most 2 (IMS`CdsImportPlanningService.java`:567, :610, :649-654) | B |
| I40 | IMS`CdsImportLedgerService.java`:1701, :1709, :3350, :3472 | 16 outcomes, 128 categories, 1,024-character audit context | `UNEXPECTED_*` / `CONTROL_AUDIT_CAPACITY_EXCEEDED` | No: fixed lists | B |
| I41 | IMS`CdsImportLedgerService.java`:537, :2199, :2546, :3132, :3661, :3682; IMS`CdsImportExecutionService.java`:354-360, :460; IMS`CdsImportResultService.java`:271, :294; IMS`CdsImportAbandonService.java`:223; DAO`CdsImportPatientDao.java`:212; IMS`CdsProgramContextService.java`:54; IMS`CdsSourceFieldService.java`:37, :83; IMA`CdsSourceFieldsViewModelAssembler.java`:32 | Pages of 32, 50, 100 and 200; inline values up to 32,768 | Changes how much is read at once | n/a | A |
| I43 | IMS`CdsImmunizationPersister.java`:166-167 | Prevention configuration file up to 1 MiB | IOException: every immunization unit fails | A large custom file (the default is 30 KB). Export reads the same file whole (EXS`CdsExportPreventionConfigurationLoader.java`:59, :73). | **C** |
| I44 | IMS`CdsImportJobService.java`:203; IMS`CdsImportResultService.java`:579; IMS`CdsImportPlanningService.java`:736, :1024, :1215, :1639; IMS`CdsFieldParser.java`:98, :342, :566, :619, :957, :1021, :1032, :1078; IMS`CdsImportFailureEvidenceService.java`:156, :180-182, :216, :246; IMS`CdsImportCleanupService.java`:93, :366, :372, :491, :496, :577; IMD`CdsImportCleanupDto.java`:41; IMP`CdsCalendarZone.java`:40, :52 | CARLOS's own formats: 64 KiB job document, 1 MiB report row, proofs, manifests, evidence | Refusal | No: CARLOS writes these within the same bounds | B |
| I53 | IMS`CdsImportReviewService.java`:853, :903; IMS`CdsFieldParser.java`:1043; IMP`CdsCorrectionFiles.java`:63; DAO`CdsImportReviewDao.java`:166 | Up to 128 corrections per source XML | `REVIEW_CORRECTION_CAPACITY_EXCEEDED` | Yes: a file from another EMR with more than 128 schema-invalid values cannot be corrected, so it cannot be imported | **C** |
| I54 | IMS`CdsImportReviewService.java`:845, :888, :1161 | Correction manifest up to 1 MiB (about 3,500 entries) | Invalid | Tied to I53 | **C** (with I53) |
| I55 | IMS`CdsImportReviewService.java`:150, :221, :863, :1101; IMP`CdsCorrectionFiles.java`:79; J`demographic/cdsimport/command/CdsImportReviewCommand.java`:31-35; IMS`CdsCorrectionLookupService.java`:164-166; IMS`CdsClinicalSourceService.java`:109-118; `demographicImport.jsp`:218-223 | Request up to 1 MiB; replacement up to 262,144 bytes; reason up to 4,000; XPath up to 2,048; value up to 65,536; pages of 50 | Refusal | No: corrections target constrained values, which are at most 65,536 characters | B / A |
| I57 | IMS`CdsImportMaintenanceService.java`:199, :215, :298, :416; IMP`CdsJdbcAdmissionGuard.java`:227, :530, :558, :678; DAO`CdsImportCoordinatorDao.java`:54, :79, :122, :140; UTL`CdsJdbcConnectionTimeouts.java`:58, :63 | 3-second statement deadlines on lock probes | An import attempt fails (resumable); the clinical status read and admission gate fail closed | A server too busy to answer a lock-table probe within 3 s. These are documented as deliberate (docs/cds-import-jdbc-timeouts.md:91-103) but go against the letter of rule 4. | **D** |
| I58 | IMP`CdsImportStatementWaits.java`:71, :74, :77; IMS`CdsControlPollBackoff.java`:43, :45; IMC`CdsImportCli.java`:99, :102; IMS`CdsImportTargetSafetyService.java`:55; IMP`CdsJdbcAdmissionGuard.java`:792-794 | Doubling 30 s waits; MariaDB's 1-year maximum; 0.25–2 s polling; 5–60 s progress; 16 tables per pin; SQL cache | Changes how work is paced | n/a | A (the 1-year maximum is B) |
| I61 | IMS`CdsImportTargetSafetyService.java`:54, :201 | Up to 256 target tables | `IMPORT_TARGET_CATALOGUE_REQUIRES_REVIEW` | Not in practice | **C** (low) |
| I62 | IMS`CdsImportTargetSafetyService.java`:56, :230, :328, :373, :386-387 | Up to 1,024 triggers; 1 MiB per trigger value; 1 MiB approval manifest; 256-character key | Every import is refused | Not in practice | **C** (low) |
| I65 | IMC`CdsCliArguments.java`:37, :44, :52; IMS`CdsImportAbandonService.java`:66; IMC`CdsImportCli.java`:201, :430, :483, :501; IMW`CdsWebService.java`:247-264; IMW`CdsImportStatus2Action.java`:101 | Reason up to 1,000; up to 32 arguments of 4,096 characters; 64 KiB console JSON; summary strings and maps of 2,048 / 200; pages of 100 | Refusal | Operator input and fixed shapes | B / A |

### Web pages and web server limits

| # | file:line | Number and what it bounds | What happens when reached | Can real data reach it? | Class |
|---|---|---|---|---|---|
| W1 | JS`cds-export.js`:253 | 15 s total for every request, including submit, control and status | The page reports a failure; the server may still have accepted the job | Yes: a busy server or a long list | **C** |
| W2 | JS`cds-export.js`:162, :306-310, :357 | Poll backoff of 5–60 s; 10 s download marker; history page of 25 | Changes pacing | n/a | A |
| W3 | JS`cds-import.js`:580 | 30 s of silence on a metadata request (the clock runs before the response headers too, so it includes a slow submit) | The page abandons the request | Yes, a busy server | **D** |
| W4 | JS`cds-import.js`:233-241, :522, :162 | Reply up to 2 MiB; 20 unconfirmed controls; 20 recent runs | Refusal or the list is cut | The replies are paged | B / A |
| W5 | DEB`tomcat/server.xml`:66-69; DEB`modsecurity/main.conf`:40, :48, :51; J`../webapp/WEB-INF/classes/struts.xml`:67 | 50 MiB `maxPostSize`, 10,000 parameters, ModSecurity 50 MB / 16 MiB without files / 2,000 arguments, Struts multipart 50 MiB | 413 or dropped parameters | E1 is reached first | B (external) |
| W6 | DEB`nginx/proxy-params.conf`:28-29; DEB`nginx/carlos-emr.conf`:81, :88 | `proxy_read_timeout` 300 s; body 50 MB; `client_body_timeout` 15 s | 504 or a cut connection | Yes: during a download, a cohort re-verification pass writes nothing (EXS`CdsExportArtifactService.java`:300-307 says a large roster's pass can take minutes) | **D** |

---

## Part 3: What to change, for each C and D row

**C1 (E1)**
- Change: in EXW`CdsExportWebService.java`:107, delete `|| values[0].length() > 120_000`. Keep the known-key and single-value checks.
- Docs: correct the operator guide :577-579 and cds-export.md:1695, which give Tomcat's limit as 2 MiB; the packaged value is 50 MiB.
- Tests: a new `CdsExportWebServiceUnitTest`, `shouldAcceptPatientList_whenParameterExceeds120000Characters` (20,000 patients reach the parser unchanged); a case in `scripts/cds-export-page.test.js` that posts 20,000 patients.

**C2 (E40), C3 (E41), C4 (E42)**
- Change: delete EXD`CdsExportDemographicEnrolmentProjectionDto.java`:41 `sourceRows > 65_536` (keep `>= 1` and the `nativeRows == sourceRows` check), EXD`CdsExportPublicProviderLookupsDto.java`:30 `providers.size()>65536`, and EXV`CdsExportDemographicClosureValidator.java`:56 `size() > 65536`.
- Tests: in `CdsExportEnrolmentHistoryUnitTest` / `CdsExportDemographicAssemblerUnitTest`, a chart with 65,537 archive rows projects; in `CdsExportDemographicClosureValidatorUnitTest`, 65,537 source fields are accepted; a DTO unit test builds 65,537 providers.

**C5 (E43)**
- Change: delete `supplied.size() > 131_072` at CDS`contract/CdsSchemaContentOrder.java`:51.
- Test: in `CdsSchemaContentOrderUnitTest`, 131,073 `ResultReviewer` occurrences come back in ordinal order.

**C6 (E54)**
- Change: delete `MAXIMUM_LOGO_BYTES` and the size check (EXS`CdsExportConsultationLetterComposer.java`:93, :470); a read failure still gives `LOGO_UNAVAILABLE`.
- Test: `CdsExportConsultationLetterComposerUnitTest`, `shouldIncludeLogo_whenLogoExceeds4MiB`.

**C7 (E21, S1, S2)**
- Change: delete the count and size caps in EXS`CdsExportImplementationIdentityService.java`:39, :92, :133, :175, :181, IMS`CdsRulesetFingerprint.java`:257, :278, :286, :327, and IMS`CdsRuntimeIdentity.java`:47-48, :231, :237, :239, :241, :285. Hashing already streams. Keep the origin, anchor and duplicate-digest checks and the version format check.
- Tests: in `CdsExportImplementationIdentityServiceUnitTest`, a synthetic class loader exposing 32,769 resources and a resource stream over 512 MiB (generated, not stored); in `CdsRuntimeIdentityUnitTest`, a `lib/modules` stand-in over 512 MiB (a sparse file).

**C8 (I29)**
- Change: in IMS`CdsHistoryPersister.java`:322-323, use the `casemgmt_note.note` MEDIUMTEXT capacity (16,777,215 bytes) instead of the setting. Only a note above that becomes the documented placeholder plus the source fields.
- Settings: add `cds.import.native.maxNoteBytes` to `RETIRED_SETTINGS` (IMS`CdsImportConfigurationService.java`:186-194) and drop it from the policy digest at :250. That changes the digest for new runs only.
- Check: whether editing a very large note in the CPP and note editor (the `history` column) is safe.
- Tests: `CdsClinicalPersisterUnitTest`, `shouldStoreWholeNote_whenNoteExceeds256KiB`; `CdsClinicalStorageMariaDbIntegrationTest` with a 1 MiB note.

**C9 (I2)**
- Change: delete the `uploads.reservedCount() >= maxFiles` refusal at IMS`CdsUploadService.java`:152-155 and retire `cds.import.upload.maxFiles`. Running out of disk or inodes already fails as `UPLOAD_DISK_RESERVE_REFUSED` or an I/O failure. If pacing is wanted, let the page wait for its earlier runs' snapshots instead of the server answering 429.
- Docs: remove the line saying "one import needs one upload" from docs/cds-import-configuration.md.
- Tests: `CdsUploadServiceUnitTest`, `shouldBeginUpload_whenTwentyThousandReservationsAwaitSnapshot`; `scripts/cds-import-upload.test.js` with 25 files and a stub that counts reservations.

**C10 (I53, I54)**
- Change: delete the 128-entry checks (IMS`CdsImportReviewService.java`:853, :903; IMS`CdsFieldParser.java`:1043; IMP`CdsCorrectionFiles.java`:63; DAO`CdsImportReviewDao.java`:166) and the 1 MiB manifest reads (:845, :888, :1161). Read the manifest as a stream (`CdsXmlParser` already holds one entry per correction).
- Tests: `CdsImportReviewServiceUnitTest`, `shouldAcceptCorrection_whenSourceHas128Corrections`; `CdsXmlParserUnitTest` applying 300 corrections.

**C11 (I8, I9, I10)**
- Change: delete the 8,192-character and 200-entry caps (IMS`CdsImportConfigurationService.java`:275, :142; IMA`CdsImportViewModelAssembler.java`:49-55; IMS`CdsCourseService.java`:181). A namespace may be up to 128 characters (the token rule and the VARCHAR(128) column).
- Test: a new `CdsImportConfigurationServiceUnitTest`, `shouldAuthorizeDelegation_whenProviderListExceeds8192Characters` (600 providers).

**C12 (I35)**
- Change: make the receipt exact. Have the persisters return the ids they inserted (IMS`CdsMedicationPersister.java`:95-96/:211, IMS`CdsAllergyPersister.java`:68-69, IMS`CdsImmunizationPersister.java`:102, the history issue links, and the file references) and receipt those rows. Remove `setMaxRows(65)` and the 64 count at IMS`CdsTargetReceiptService.java`:302-380. After that I36 is the importer's format (B).
- Test: a MariaDB integration test that seeds 65 orphaned `partial_date` rows for the next `drugs` id. The unit must commit, and its receipt must name only its own rows.

**C13 (I43)**
- Change: at IMS`CdsImmunizationPersister.java`:166-167, read the configuration whole through the secure SAX builder, as the export does.
- Test: `shouldLoadPreventionTypes_whenConfigurationExceeds1MiB`.

**C14 (W1)**
- Change: at JS`cds-export.js`:253, replace the 15-second total abort with a silence window that restarts on every progress event, as `cds-import.js` does. A POST that is abandoned is reported as "unconfirmed — check the saved submission", which the page already supports.
- Test: `scripts/cds-export-page.test.js`, where the stub server waits 20 s before its headers and the submit still resolves.

**C15 (E66)**
- Change: delete the 64 and 100 count caps and the 32 KiB file cap (EXS`CdsExportDisclosurePolicyService.java`:62, :105-106, :158-165). `policy.json` already pins every file by SHA-256.
- Test: `CdsExportDisclosurePolicyServiceUnitTest` with an audit plan of 150 providers, 70 recipients and a 40 KiB recipient file.

**C16 (I38, S6 SQL length)**
- Change: delete the 65,536-character SQL caps (IMS`CdsTargetReceiptService.java`:532; CDS`jdbc/CdsBinaryReads.java`:48). Keep the SELECT-only check.
- Test: `CdsBinaryReadsUnitTest`, `shouldPrepare_whenSelectExceeds65536Characters`.

**C17 (I61, I62)**
- Change: delete the counts and byte caps at IMS`CdsImportTargetSafetyService.java`:54, :56, :201, :230, :328, :373, :386-387. Read the approval manifest as a stream.
- Test: `CdsImportTargetSafetyServiceUnitTest` with 1,025 approved triggers.

**C18 (E57)**
- Change: delete the four unused projection services (or at least their bounds), and remove them from EXC`CdsExportCliSpringConfiguration.java`.
- Tests: delete their unit tests along with them.

**D1 (S8, S9, I36, I37): the receipt and witness format.** Your call: should CARLOS support locally widened target tables?
- If yes: set the column limit to MariaDB's 4,096 (or remove it) in all five places (IMS`CdsTargetReceiptService.java`:467, CDS`contract/CdsNativeRowState.java`:72, CDS`contract/CdsNativeRowDigest.java`:86, DAO`CdsExportNativeMetadataReadSupport.java`:113, EXD`CdsExportCapturedTableDto.java`:73). Derive the 8 MiB witness and 192-row limits from what the importer wrote, or drop them (the columns are LONGTEXT).
- Test: a MariaDB integration test that imports into `drugs` widened to 600 columns and exports it again.

**D2 (E16): package retention.** Keeping retention as a privacy policy fits your rules, because the export itself has completed.
- Recommendation: keep the 7-day default but delete the 30-day maximum (or set it by your policy).
- Test: a retention of 45 days is accepted.

**D3 (I3): idle upload cleanup.** It works as the upload's stall window and has a privacy purpose.
- Recommendation: keep it, but show "removed at …" in the upload list and never remove an upload that has received at least one chunk without warning the user first. Your decision.

**D4 (I57): 3-second probes.**
- Recommendation: move the probes inside an import run (coordinator, maintenance owner checks and readbacks, GET_LOCK, IS_USED_LOCK, RELEASE_LOCK, CONNECTION_ID, `wait_timeout`) to `CdsImportStatementWaits.awaitOwned`. A healthy server answers at once, so nothing changes there.
- Keep the short deadline on the clinical status read (IMS`CdsImportMaintenanceService.java`:199) and the per-connection gate (IMP`CdsJdbcAdmissionGuard.java`:530, :558), which fail closed on every clinical request.
- Test: `CdsImportMaintenanceServiceUnitTest` where IS_USED_LOCK answers after 5 s and the owner still verifies.

**D5 (E8, E9): diagnostic report truncation.**
- Recommendation: page through `CdsExportHeldPatientDao` while the report download streams, with no cap.
- Test: `CdsExportLedgerIntegrationTest` with 5,000 held patients, all listed.

**D6 (I21, I22): namespace prefixes.**
- Recommendation: count distinct namespace URIs (at most 3 in a schema-valid file) rather than prefixes, and bound only the prefixes in scope at one time.
- Or, if the parser's memory is the concern, keep the bound and document precisely that a file whose writer invents a new prefix per declaration is refused.
- Test: `CdsXmlParserUnitTest` with a file that redeclares the same prefix on every element, which must pass.

**D7 (S3): CLI heap.**
- Recommendation: delete the 16,384 MiB maximum (keep the 128 MiB minimum) and keep 1 MiB as an integrity check on the configuration file.
- Test: `CdsProcessConfiguration.parse` accepts 32,768.

**D8 (E13, E14, E17, E18): upper limits on how-only settings.**
- Recommendation: remove the 31-day, 86,400 s, 1 TiB and 60,000 ms maxima, or replace them with the real external ceiling (MariaDB's 1-year `wait_timeout` for windows). Keep the parse checks.
- Tests: the settings tests accept values above the old maxima.

**D9 (W6): nginx read timeout during downloads.**
- Recommendation: measure the longest re-verification pass in the scaling lane. If it approaches 300 s, interleave the verification with the output, or raise `proxy_read_timeout` for `/form/cdsExportDownload` only.
- Test: `CdsExportArtifactServiceUnitTest` asserting that the longest gap with no bytes written stays well under the proxy timeout.

**D10 (E67): CLI shutdown wait.**
- Recommendation: once the job's durable outcome is known, log a progress thread that does not stop and exit with the job's own code.
- Test: `CdsExportCliUnitTest` with a progress task that never ends.

**D11 (retention sweep caveat).**
- Recommendation: make the purge resumable (keep a persistent cursor and delete progressively) so the 15-minute bound changes how the cleanup runs, not whether a large directory is ever erased.
- Test: `CdsExportRetentionServiceUnitTest`, a directory erased over two sweeps.

**D12 (W3): import page's 30-second wait.**
- Recommendation: start the silence clock only after the request body has been sent, and treat an abandoned submit as unconfirmed, as the export page does.
- Test: `scripts/cds-import-contract.test.js` with a submit whose headers arrive after 40 s.

## Part 4: Docs that no longer match the code

- cds-export.md:1746-1748 says the prevention configuration is capped at 1 MiB. The export reads it whole, and line 1961 of the same file says the cap was removed. The cap now exists only in the import (I43).
- cds-export.md:1937 and :1962 say the 65,536 limits on archive rows, providers and retained fields are gone. The checks are still in E40, E41 and E42.
- cds-export-operator-guide.md:578 and cds-export.md:1695 say Tomcat's `maxPostSize` is 2 MiB. The packaged value is 50 MiB, and CARLOS's own 120,000-character check (E1) is reached first.
- cds-import-configuration.md says the XML guards cannot be reached by a genuine file and that "one import needs one upload". Rows I21, I22 and I2 show otherwise.

### Critical Files for Implementation
- /home/user/build/wt-integ/src/main/java/io/github/carlos_emr/carlos/demographic/cdsexport/web/CdsExportWebService.java
- /home/user/build/wt-integ/src/main/java/io/github/carlos_emr/carlos/demographic/cdsimport/service/CdsTargetReceiptService.java
- /home/user/build/wt-integ/src/main/java/io/github/carlos_emr/carlos/demographic/cdsimport/service/CdsUploadService.java
- /home/user/build/wt-integ/src/main/java/io/github/carlos_emr/carlos/demographic/cdsimport/service/CdsHistoryPersister.java
- /home/user/build/wt-integ/src/main/java/io/github/carlos_emr/carlos/demographic/cdsimport/service/CdsImportReviewService.java