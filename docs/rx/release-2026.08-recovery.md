# RX recovery and compatibility in release/2026.08

The integrated change targets `release/2026.08`, including release head `1335e1b28ab` and published beta7 (`d40d2939afd`). Published migrations remain byte-for-byte unchanged: beta7 owns common `1.0.59.33`; the unpublished BC history migration is `.34`. The RX forward migrations are `.35` (provider-owned favourite groups and transactional engine qualification), `.36` (durable submission evidence) and `.37` (original versions).

Before upgrading, finish and finalize any checkpointed CDS import. Adding `drugs.row_version` changes the physical target schema fingerprint and older import receipts require reconciliation if resumed across this upgrade. Do not discard the import ledger or reinterpret a changed fingerprint as proof of a failed import.

Migration `.35` converts adopted non-InnoDB `drugs`, `prescription`, `log` and `favorites` tables to InnoDB before creating the favourite membership foreign keys. Existing InnoDB tables are left alone. Schedule maintenance time and disk capacity for these table rebuilds on adopted databases; rollback/retry guarantees require these transactional engines. The adoption regression starts with MyISAM tables, verifies retained rows and the foreign keys, and proves that a later clinical rollback leaves the original rows intact.

The requested scope of this integrated maintenance PR retains #850 alongside the RX recovery work. Groups are optional presentation metadata, retain the existing shortened sidebar labels and ungrouped templates, and do not change a favourite's medication fields or prescribe a group as a set.

## Ticket dispositions

| Ticket | Resulting behaviour |
| --- | --- |
| #5751 | Exact rendered-revision replays consult durable evidence before empty/stale workspace validation. A committed submission returns `RX_ALREADY_SAVED` only when the request also carries the fields that submission stored; the same draft re-posted with edited fields returns `RX_SAVED_EARLIER_VERSION` (see below). Neither claims the prescription was not saved. |
| #5865 | Flush and callback completion distinguish a callback rollback from a possibly durable COMMIT. A JDBC COMMIT that delegates and then throws retains consumed revisions. |
| #5753 | A durable `SAVE_ATTEMPT` precedes clinical work. ADD, drug-reason and source-archival audits join the clinical transaction. SAVED receipt, clinical rows and success audits commit or roll back together. Pending evidence remains visible after a restart. Each attempt has its own nonce so failure before clinical work can release only its own durable intent. |
| #5986 | Authoritative RX audits use the strict synchronous path inside the business transaction, independently of executor capacity. Audit failure aborts the clinical transaction. |
| #5989 | Failed composed drafts regain their original editable instructions along with their identities and revisions. |
| #5867 | REST prescribing has one transaction; any failed medication write aborts the header and earlier medications. An existing-ID update cannot fall through to an insert. |
| #139 | Drug and prescription entities have versions; current drug-form editors carry the original version, staged replacements retain their source version, and notes compare a digest of the original displayed text. REST exposes optional `rowVersion` with `includeRowVersion=true`. See the legacy-client limitation below. |
| #5752 | Only the explicit JSON `no-guidance` result clears a renal panel. Empty, malformed and failed responses retain an N/A panel with Refresh. |
| #5984 | Both guidance and failure panels have native collapsible details. Refresh remains available. |
| #5781 | Routine unusable stored observations render a 200 N/A panel. Security refusals keep their failure statuses and WAF audit policy. No route-wide WAF or audit exemption was introduced. |
| #5990 | Known creatinine and weight units are converted explicitly. Missing/unknown units, unknown sex, undated inputs and inputs outside the highlighting qualification range cannot produce automatic highlights. The retained Cockcroft–Gault calculation and displayed factor agree. |
| #3610 | Early/missing/inaccessible preview documents are guarded before fax or print submission and unload-state changes. Notes saving remains independent of preview availability. |
| #5866 | Satellite-clinic printing is requested only when a real offered address matches the selection. A provider with no active site prints the primary clinic header. Nonempty malformed satellite payloads remain refused. |
| #6053 | Legacy WriteScript source is decoded as UTF-8, including the microgram option and instruction parsing. |
| #5723 | The native per-drug label is again `Comment:`. Contextual encoding and whitespace preservation remain in place; no unverified visibility claim is made. |
| #5182 | The pinned installed dataset contains two index aliases (705 and 16175) of DIN 00887056. DrugRef de-duplicates identical names; the old fixture incorrectly demanded one index ID. The check accepts an alias of that exact DIN and still verifies the selected product's DIN and displayed inactive date. Production inactive-product filtering was not changed. |
| #850 | Provider-owned groups support creation, naming, reordering, deletion, checkbox assignment, collapsible sidebar navigation and Ungrouped. Deleting a group removes memberships, never templates. Copying a selection is atomic; destination templates begin Ungrouped, preserving legacy copy behaviour. Group headers never prescribe a medication set. |
| #5340 | Retain the five existing public gates for release compatibility; the sixth is already absent. Per-route dependencies are listed below. |

## Submission evidence and recovery

`rx_submission_receipt` stores an opaque SHA-256 digest of the complete, sorted set of rendered card keys and UUID revisions, scoped to the authenticated provider and authorized patient, and for each saved draft a second digest of the fields that save stored (see "A saved draft posted again" below). It stores no medication text. Its states have deliberately different meanings:

* `PENDING`: a durable intent exists, but success has not been confirmed. A retry reports uncertainty and refreshes the profile; it cannot submit another prescription automatically.
* `SAVED`: the exact clinical transaction, its strict success audits and its script ID became durable together. A replay reports already saved without clearing a newer draft in another window.
* `FAILED`: the callback failed, rollback was confirmed, and the intent was released in a fresh transaction. Only this state permits the exact same draft to retry.

### A saved draft posted again: exact replay or edit

The draft receipt identifies which rendered cards a save consumed, not what was typed into them. A card edited after a lost reply is posted with the same revisions as the save that already consumed them, so the receipt alone cannot tell "the same save again" from "a correction the earlier save never saw". Answering the second with "already saved; do not enter it again" silently dropped the correction.

Each save therefore records a second row in `rx_submission_receipt`, in the same clinical transaction and naming the same script: a SHA-256 digest of every submitted field, each name and value length-framed, bound to the draft token. Left out are only the fields that may differ between two posts of an unchanged form (`rand`, the CSRF token, `clearSaved`, `searchString`, and on the SearchDrug3 save its search-filter radios `method` and `wildcard`, which that save never reads). No schema change was needed and no medication text is stored; because the draft token is derived from random revisions, the digest cannot be matched against guessed prescriptions. The row commits or rolls back with the prescription, like the draft receipt.

When a request names a draft whose receipt is `SAVED`:

* **Its content row matches** (an exact replay): `409 RX_ALREADY_SAVED` with the script ID, as before. When the workspace still stages exactly the cards that script saved, the answer also brings the page back in step, as the lost reply would have. After Save And Print it carries their acknowledged `draftRevisions`, so the cards can be saved or removed again. After Save the staged copies are cleared, unless the workspace also holds another window's draft. In every other case the page drops the spent cards. The page offers, and never opens unasked, the saved prescription for printing.
* **Its content row does not match**: `409 RX_SAVED_EARLIER_VERSION` with the script ID. Nothing is written and the workspace is left as it is. The prescriber is told that an earlier version was saved, that the changes entered afterwards were NOT saved, and to review the medication profile and correct or re-prescribe from there. The edited cards stay on screen to read from, with a standing notice and a button that reopens the prescription page. The legacy Save & Print form renders the same message on its recovery page.

A receipt written before this evidence existed has no content row, so its replay cannot be shown to be exact and gets the cautious `RX_SAVED_EARLIER_VERSION` answer. A prescribing page loaded before the upgrade does not know that code: it shows its uncertain-outcome message after a lost reply, and its generic refusal otherwise.

The standing notice also appears when a save cannot be settled from the open page (`RX_SAVE_OUTCOME_UNKNOWN`, or a conflict after an unconfirmed save), and a card removal refused in that state says that the page must be reopened rather than "please try again": the server holds those cards under revisions the page was never given.

Acknowledging a SAVED pending identity permanently retires its consumed cards, preserving any newer unsaved cards. A request containing newer drafts receives `RX_DRAFT_REVIEW_REQUIRED`, never an acknowledgement that those drafts were saved. Exact receipt replays are checked before stale-card validation on both AJAX and legacy Save & Print; the legacy form renders a localized recovery page.

Uncertain cards retain their original pending submission identity across reopening; their advanced revisions cannot start a new save. A later login, CSRF or stale refusal does not erase an earlier uncertain outcome. A missing receipt is not proof that an in-flight operation failed. The client never automatically retries an uncertain save. If the database is unavailable during recovery, preserve the draft/evidence and review the medication profile after service is restored. Pending submissions are intentionally not expired or automatically declared rolled back. A clinician must reconcile the chart before entering a new prescription after an unresolved attempt. If the prescription is present, do not enter it again. If it is absent after reconciliation, remove the locked card and enter it again; repeatedly submitting the pending card cannot resolve an unknown commit. Do not manually change pending evidence while an application process may still be writing.

Clinical success audits are authoritative transaction writes. Best-effort asynchronous audit helpers are not used to establish prescription-save success. Technical failures are logged without prescription text or patient identifiers.

The REST/SOAP hardening back-merge retains the common `PrescriptionWriteException` rollback boundary and existing prescription failure response. RX retains explicit rejection of unknown positive drug IDs, nonpositive new-drug placeholders, scoped original-version conflicts and strict atomic audits. Both sets of physical MariaDB acceptance families remain required.

## Original-version compatibility

Current web editors detect an intervening edit from the original display, including an in-place drug-form edit that does not change the drug ID. Staged source checks take physical row locks in sorted ID order until the clinical transaction finishes. A conflict rolls back the complete clinical batch, preserves the editable draft and asks the prescriber to remove the affected staged card, review the current medication profile and stage it again. Display-only ordering/visibility changes and their update timestamp do not advance the clinical version. A drug-form conflict renders the fresh stored form/version and shows the unsaved selection separately.

Notes requests carry a SHA-256 digest of the UTF-8 original display, avoiding a second raw clinical-text argument through the WAF. A stored NULL and the editor’s empty textarea share the same digest. Queued edits use the preceding acknowledged digest; exact retries of the stored note are idempotent. Both the write acknowledgement and replacement digest are required. An unconfirmed save or conflict shows a reload/review action and blocks Print; Fax already waits for the acknowledged notes chain.

**Legacy REST limitation:** clients that omit `rowVersion` retain their existing request contract and response shape. Current clients request the declared `includeRowVersion=true` query parameter (case-insensitive true/false; other values receive 400 before writes) to receive the token, then echo it on updates; absent tokens are omitted from both XML and JSON. JPA detects overlapping transaction writes, but the server cannot infer what version an old client originally read before sending its update. REST `/rx/update` retains its replacement behaviour: create a new drug and archive the source. Clients use the returned replacement ID and its version on subsequent edits. Complete stale-read detection for versionless clients requires them to echo `rowVersion` or a deliberately breaking requirement to supply it. Do not describe this compatibility mode as complete protection for versionless legacy clients, or close #139 on that basis alone. Reopen older prescribing windows after upgrading so they obtain source versions. Unknown positive medication IDs are refused; zero/nonpositive placeholders retain new-drug semantics. Batch write/audit rollback returns the existing JSON `success:false` response. Stale source and optimistic-lock failures return 409 `RX_SOURCE_CHANGED` at RX mutation boundaries only; unrelated REST services retain their prior exception mapping.

## Renal guidance policy

The existing dosing table is expressed in creatinine-clearance categories. This change retains its Cockcroft–Gault calculation; it does not substitute indexed CKD-EPI eGFR into those thresholds. The displayed factor is 1.25, matching the retained division by 0.8. Switching the equation/table requires a separate clinical validation decision. The integer clearance used by the existing table is floored rather than rounded upward, keeping fractional values below restrictive thresholds and avoiding gaps between adjoining integer bands.

Automatic highlighting is qualified only for recorded male/female adults aged 18–120, weight 20–500 kg, and creatinine 10–3000 micromol/L with explicit supported units and dates. These are conservative tool qualification limits, not normal laboratory reference ranges. Outside them, the table remains available for manual clinical assessment and the UI explains why no automatic recommendation is highlighted. Known mg/dL and pound observations are converted; unknown units and Unicode look-alikes are refused. CML results retain missing units and undated observations rather than fabricating dates or silently replacing them with older normal results. An undated result has an explicit explanatory notice even when a dated measurement is displayed. Guidance-table load failures are surfaced and retried, never latched as authoritative no-guidance. Both SearchDrug3 and the legacy WriteScript editor use the explicit fragment/no-guidance contract.

## Public `/rx/` gate decisions (#5340)

Static searches of the release and inspected develop snapshots found no active application caller of these exact public gate URLs. This does not establish that installed custom forms, external tools or bookmarks do not use them. The release requirement to avoid breaking public interfaces therefore takes precedence over speculative removal. No shared fragment or interaction service is deleted.

| Exact route | Release decision and independently live dependencies |
| --- | --- |
| `/rx/ViewInteractionDisplay` | Keep the authorized read gate and InteractionDisplay.jsp; preserve current interaction calculations, warnings and unavailable-service display. |
| `/rx/ViewSideLinksEditFavorites2` | Keep the authorized gate. Keep SideLinksEditFavorites2.jsp, included by SearchDrug3 and ShowAllergies2, including each section's own privileges. |
| `/rx/ViewSideLinksNoEditFavorites` | Keep the authorized gate and SideLinksNoEditFavorites.jsp, included by WriteScript and ChooseDrug. |
| `/rx/ViewSideLinksNoEditFavorites2` | Keep the authorized gate and SideLinksNoEditFavorites2.jsp, included by StaticScript2, ChooseAllergy2 and AddReaction2. |
| `/rx/ViewTopLinks` | Keep the banner gate and TopLinks.jsp, included throughout prescribing/allergy/favourite workflows. Preserve its field-level guards and encoding. |
| `/rx/ViewUpdateInteractingDrugs` | Already absent from mapping, implementation and result assets on the pinned release and inspected develop heads. Do not recreate it or broaden aliases; preserve the active interaction UI. |

The existing requested-patient gate tests cover the retained patient-scoped wrappers. Qualification must additionally exercise their effective Struts entry points and normal prescribing/allergy/favourite navigation, with two patient tabs and denied access.

## Validation surfaces

Meaningful regressions cover physical MariaDB COMMIT-then-throw recovery; strict audit rollback/retry; REST late-write rollback; concurrent detached entity edits; original form/note conflicts; provider isolation and atomic favourite copying. Both Ontario and BC physical schema contracts are generated from fresh complete Flyway installations. OSCAR19 target metadata is regenerated from pinned source `a7900d569d3faf741993e5e1da8c14021bbefede`.

Browser checks include `rx-save-failure-retry-only-lost`, `rx-save-failure-retry-print-lost`, `rx-favorite-groups`, `rx-original-version`, `rx-preview-loading-notes`, `rx-renal-waf-privacy`, the renal input matrix, inactive-drug status and existing print/fax/renewal workflows. New browser checks run through the actual installed front door; WAF evidence reports only Boolean results, never audit payloads. Installed qualification must use the actual packaged artifact and retain failure/residue checks. Passing source/unit checks alone does not establish installed acceptance.
