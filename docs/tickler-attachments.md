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
  leaves the stored set untouched. Lab ids are only unique within their source (HL7, MDS, CML
  and BCP each number their own tables), so a tickler `labNo` value is source-qualified:
  `HL7:123`. The picker's lab checkboxes carry `data-lab-type` and source-qualified DOM ids
  (`labNoHL7123`, so two sources sharing a segment id never collide; the consultation page's
  stored lab delegates use the same key), the dialog builds the value from the source, and the
  service checks ownership against that source's `patientLabRouting` row; a bare id is read as
  HL7, which keeps the legacy `docType=HL7&docId=` forward links working. The dialog only
  replaces the form's selection when the picker actually rendered: closing a dialog whose load
  failed leaves the delegates and the marker untouched. A stored attachment the picker does not
  offer (an older encounter form, a superseded eForm) is marked unlisted on load and carried
  through Save and Close unchanged, since the reader had no way to un-check it.
- `requireAttachable(loggedInInfo, demographicNo, ids)` runs the same rights and ownership
  checks without writing, for flows that create the tickler and attach in one step: the lab
  macro (`ReportMacro2Action`) checks first and creates no tickler when the lab may not be
  attached to that patient, then attaches through `syncAttachments`.
- Restricted types on edit: a reader who lacks read on an attachment's type still sees that
  something is attached. The Edit form carries those rows through as `data-restricted` hidden
  delegates, so a save after opening the picker resubmits them unchanged; `syncAttachments`
  leaves a type the caller cannot read alone when the submitted set equals the stored set and
  refuses any difference. The tickler list JSON (`ListTicklers`) applies the same per-type gate:
  such links are returned as `{tableName, restricted: true}` with no identifier at all (neither
  the item id nor the `ticklerdocs` row id), and `ticklerMain.jsp` renders an unlinked, titled
  paperclip. The REST `TicklerConverter` applies the gate too and leaves denied rows out, since
  the `ticklerLinks` shape has no restricted flag.
- Picker endpoint: `previewDocs?method=fetchTicklerDocuments&demographicNo=N`
  (`DocumentPreview2Action`), gated by `_tickler` read on the patient, per-type read gates for
  each section, selection enabled by `_tickler` write; a non-positive or non-numeric
  `demographicNo` is a 400.
- Actions: `DbTicklerAdd2Action` folds the legacy forward-from-document `docType`/`docId` pair
  into the picker selection and records the session provider as creator; `EditTickler2Action`
  is POST-only (405 otherwise, registered in `MutatorActionGetRejectionContractUnitTest`) and
  syncs attachments only with the marker; `ReportMacro2Action` attaches the lab through the
  service (`requireAttachable` before the tickler is created, then `syncAttachments`), so the
  request's `segmentID`/`labType`/`demographicNo` are checked against the patient's routing
  and the caller's `_tickler` write right before anything is written.
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
