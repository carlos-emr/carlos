# Tickler attachments (shared attachment picker)

## Purpose
A tickler can carry any number of the patient's documents, labs, eForms, encounter forms and
HRM reports, chosen in the same attachment picker consultation requests and eForms use
(`WEB-INF/jsp/documentManager/attachDocument.jsp`). Attachments are listed by name in the Add and
Edit Tickler windows and rendered as links in the tickler list and the patient tickler view.
Ported from openo-beta/Open-O PR #2491 (Sebastian Ibanez) for #3984, with the security and
data-migration gaps in that PR closed rather than copied.

## Storage
- Table `ticklerdocs` (`database/mysql/migration/common/V1.0.35__tickler_docs.sql`), mirroring
  `consultdocs`/`EFormDocs`: `tickler_id`, `document_no`, `doctype` (`D` document, `L` lab,
  `E` eForm, `F` encounter form, `H` HRM), `lab_type` (lab source: HL7/MDS/CML/BCP, labs only),
  `deleted` (soft delete, `Y`), `attach_date`, `provider_no`.
- Entity `commn.model.TicklerDocs`; DAO `commn.dao.TicklerDocsDao` with per-tickler finders, the
  batched `findByTicklerIds` for the list, and the reverse finders `findByDocument` /
  `findByLab` for "ticklers for this document / lab".
- The migration backfills every `tickler_link` row whose `table_name` is `DOC`, `HRM`, `HL7`,
  `MDS`, `CML` or `BCP`, keeping the tickler's creator as `provider_no`, its creation date as
  `attach_date`, and the lab source in `lab_type`. It is re-runnable and never resurrects a row
  that was detached after backfill. `tickler_link` is kept read-only for one release; nothing
  reads it any more (`TicklerLink*` classes are removal candidates for the next train).

## Layers
- `documentManager.TicklerAttachmentService` (`@Service`, constructor-injected):
  - `syncAttachments(loggedInInfo, tickler, Map<DocumentType, ids>)` requires `_tickler` write on
    the patient, then, per submitted type, the caller's read right on that type and proof that
    every id belongs to the tickler's patient (`ctl_document`, `patientLabRouting`, `eform_data`,
    `HRMDocumentToDemographic`, the patient's encounter forms). Only submitted types are
    synchronised; a type the caller cannot read is never touched. The attaching provider is
    always the session provider. Every attach and detach is audited through `LogAction`.
  - `listAttachments` requires `_tickler` read; items of a type the caller may not open are
    returned unnamed so the view renders a generic "restricted" label.
  - `formNamesByFormId` resolves encounter form names per patient, dropping ids claimed by more
    than one form type (form ids are only unique per form table).
- Request contract `documentManager.data.TicklerAttachmentParameters`: the picker parameters
  (`docNo`, `labNo`, `eFormNo`, `hrmNo`, `formNo`) plus the `attachmentsSubmitted=1` marker the
  tickler forms set when the picker's selection is authoritative. Without the marker an edit
  leaves the stored set untouched.
- Picker endpoint: `previewDocs?method=fetchTicklerDocuments&demographicNo=N`
  (`DocumentPreview2Action`), gated by `_tickler` read on the patient, per-type read gates for
  each section, selection enabled by `_tickler` write; a non-positive or non-numeric
  `demographicNo` is a 400.
- Actions: `DbTicklerAdd2Action` folds the legacy forward-from-document `docType`/`docId` pair
  into the picker selection and records the session provider as creator; `EditTickler2Action`
  is POST-only (405 otherwise, registered in `MutatorActionGetRejectionContractUnitTest`) and
  syncs attachments only with the marker; `ReportMacro2Action` attaches the lab through
  `ticklerdocs` with the session provider.
- Readers: `TicklerDaoImpl.loadLinksForTicklerDTOs` (tickler list JSON), `TicklerManagerImpl`
  (ticklers for an HL7 lab), `EDocUtil.getHtmlTicklers` (ticklers for a document), the REST
  `TicklerConverter` (`ticklerLinks` keeps its shape: `tableName` carries `DOC`, `HRM`, the lab
  source, or `EFORM`/`FORM`), `ticklerMain.jsp` and `ticklerDemoMain.jsp`.
- Views: `WEB-INF/jsp/tickler/ticklerAttachmentsPanel.jspf` (inside the form) and
  `ticklerAttachmentsDialog.jspf` (after the form) are included by `ticklerAdd.jsp` and
  `ticklerEdit.jsp`.

## Verification
- Unit: `TicklerAttachmentServiceUnitTest`, `TicklerAttachmentParametersUnitTest`,
  `EditTickler2ActionUnitTest`, `DbTicklerAdd2ActionUnitTest`, `DocumentPreview2ActionUnitTest`.
- Integration: `TicklerDocsDaoIntegrationTest`.
- Browser: `scripts/tickler-attachments-playwright-checks.js`
  (`npm run test:tickler-attachments-playwright`, suite tier `core`, needs the demo dataset).
