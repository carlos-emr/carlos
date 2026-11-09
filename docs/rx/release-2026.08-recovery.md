# RX recovery and compatibility in release/2026.08

The integrated change targets `release/2026.08` from `9004536dc8314d5f50542b48905ca3ca4099a916`. It preserves the published migration files, prescription/reprint authority rules, patient-scoped workspaces and public route compatibility. The three new forward migrations are `1.0.59.33` (provider-owned favourite groups), `.34` (durable submission evidence) and `.35` (original versions).

## Ticket dispositions

| Ticket | Resulting behaviour |
| --- | --- |
| #5751 | Exact rendered-revision replays consult durable evidence before empty/stale workspace validation. A committed submission returns `RX_ALREADY_SAVED`; it never claims the prescription was not saved. |
| #5865 | Flush and callback completion distinguish a callback rollback from a possibly durable COMMIT. A JDBC COMMIT that delegates and then throws retains consumed revisions. |
| #5753 | A durable `SAVE_ATTEMPT` precedes clinical work. ADD, drug-reason and source-archival audits join the clinical transaction. SAVED receipt, clinical rows and success audits commit or roll back together. Pending evidence remains visible after a restart. |
| #5986 | Authoritative RX audits use the strict synchronous path inside the business transaction, independently of executor capacity. Audit failure aborts the clinical transaction. |
| #5989 | Failed composed drafts regain their original editable instructions along with their identities and revisions. |
| #5867 | REST prescribing has one transaction; any failed medication write aborts the header and earlier medications. An existing-ID update cannot fall through to an insert. |
| #139 | Drug and prescription entities have versions; current drug-form editors carry the original version, staged replacements retain their source version, and notes compare the original displayed text. REST exposes optional `rowVersion` with `includeRowVersion=true`. See the legacy-client limitation below. |
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

`rx_submission_receipt` stores an opaque SHA-256 digest of the complete, sorted set of rendered card keys and UUID revisions, scoped to the authenticated provider and authorized patient. It stores no medication text. Its states have deliberately different meanings:

* `PENDING`: a durable intent exists, but success has not been confirmed. A retry reports uncertainty and refreshes the profile; it cannot submit another prescription automatically.
* `SAVED`: the exact clinical transaction, its strict success audits and its script ID became durable together. A replay reports already saved without clearing a newer draft in another window.
* `FAILED`: the callback failed, rollback was confirmed, and the intent was released in a fresh transaction. Only this state permits the exact same draft to retry.

Uncertain cards retain their original pending submission identity across reopening; their advanced revisions cannot start a new save. A later login, CSRF or stale refusal does not erase an earlier uncertain outcome. A missing receipt is not proof that an in-flight operation failed. The client never automatically retries an uncertain save. If the database is unavailable during recovery, preserve the draft/evidence and review the medication profile after service is restored. Pending submissions are intentionally not expired or automatically declared rolled back. A clinician must reconcile the chart before entering a new prescription after an unresolved attempt. Do not manually change pending evidence while an application process may still be writing.

Clinical success audits are authoritative transaction writes. Best-effort asynchronous audit helpers are not used to establish prescription-save success. Technical failures are logged without prescription text or patient identifiers.

## Original-version compatibility

Current web editors detect an intervening edit from the original display, including an in-place drug-form edit that does not change the drug ID. Staged source checks take physical row locks in sorted ID order until the clinical transaction finishes. A conflict rolls back the complete clinical batch, preserves the editable draft and asks the prescriber to reload/review.

Notes requests carry their original displayed text; a stored NULL and the editor’s empty textarea represent the same initial blank. Queued edits bind that original when the preceding request is acknowledged, so two local edits do not use one stale common version. Exact retries of the already stored note are idempotent. A response without the explicit write acknowledgement remains unconfirmed.

**Legacy REST limitation:** clients that omit `rowVersion` retain their existing request contract and response shape. Current clients request `includeRowVersion=true` to receive the token, then echo it on updates; absent tokens are omitted from both XML and JSON. JPA detects overlapping transaction writes, but the server cannot infer what version an old client originally read before sending its update. Complete stale-read detection for those clients requires them to echo `rowVersion` or a deliberately breaking requirement to supply it. Do not describe this compatibility mode as complete protection for versionless legacy clients, or close #139 on that basis alone. Reopen older prescribing windows after upgrading so they obtain source versions.

## Renal guidance policy

The existing dosing table is expressed in creatinine-clearance categories. This change retains its Cockcroft–Gault calculation; it does not substitute indexed CKD-EPI eGFR into those thresholds. The displayed factor is 1.25, matching the retained division by 0.8. Switching the equation/table requires a separate clinical validation decision.

Automatic highlighting is qualified only for recorded male/female adults aged 18–120, weight 20–500 kg, and creatinine 10–3000 micromol/L with explicit supported units and dates. These are conservative tool qualification limits, not normal laboratory reference ranges. Outside them, the table remains available for manual clinical assessment and the UI explains why no automatic recommendation is highlighted. Known mg/dL and pound observations are converted; unknown units and Unicode look-alikes are refused. CML results retain missing units and undated observations rather than fabricating dates or silently replacing them with older normal results.

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
