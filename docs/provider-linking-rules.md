# Provider Linking Rules

**Administration → Labs/Inbox → Provider Linking Rules** is one clinic-wide switch. When it is
on, an HL7 lab or HRM report that is matched to a patient also goes to that patient's **Most
Responsible Provider** (MRP, `demographic.provider_no`). Without it, the report goes only to the
ordering or delivered-to provider. The legacy upload fallback still sends a result with no
matched ordering provider to an active MRP; otherwise it stays in the unassigned inbox.

It is for clinics where specialists, locums or residents order tests but the family physician
must see the result. Without the switch, someone has to use **Send to MRP** in the inbox for each
item. That manual action is unchanged and still works with the switch on or off.

Issue: [#3971](https://github.com/carlos-emr/carlos/issues/3971). The behaviour is adapted from
open-osp/Open-O pull request #196 by Deval Italiya.

## What the switch changes

| Where a report meets a patient | Switch off (default) | Switch on |
|---|---|---|
| **HL7 upload** (`MessageUploader`): the message names providers that match | Routed to those providers | Also routed to the matched patient's MRP |
| **HL7 upload**: no provider in the message matches | Routed to the MRP, or to the unassigned inbox (`0`) when no patient matched | Same as off |
| **Lab Patient Match** (`oscarMDS/PatientMatch`): a lab is matched or corrected from the Patient Search popup | Patient link and removal of obsolete automatic access | Every version of the lab is also routed to the MRP, and the unassigned (`0`) rows are dropped |
| **HRM assign patient** (`hospitalReportManager/Modify`, `method=assignDemographic`) | Patient link and removal of obsolete automatic access | Also routed to the MRP, the MRP's HRM forwarding rules are applied, and the unclaimed (`-1`) rows are removed. The viewer updates its provider list in place |
| **eDocuments** (scanned or uploaded documents) | Not affected | Not affected |

Rules shared by every path:

- **The MRP is skipped when it cannot receive results.** That covers a blank MRP, `0` (none),
  `-1` (the HRM "unclaimed" marker) and an inactive or unknown provider. Routing a result to a
  departed provider's inbox hides it from everyone who is still working.
- **Routing is idempotent.** An MRP who already has the report, including one who ordered the
  test, gets no second row. A result the MRP has already acknowledged or filed is not reopened.
- **The MRP's forwarding rules apply** as they do for any other delivery. For labs these are
  `IncomingLabRules` through `ProviderLabRouting`. For HRM, forwarding is one hop only, as the
  manual assign-provider action does.
- **Correcting or unlinking a patient revokes obsolete automatic access.** New automatic MRP and
  forwarded rows record their source patient in `mrpDemographicNo`. A correction removes those
  rows when they no longer belong to the current match, even if the switch has since been turned
  off. Existing delivered/manual assignments are retained; a later independent assignment promotes
  an automatic row so it also survives correction. With no remaining recipient, the report returns
  to the unassigned inbox. Existing rows without provenance are retained because their origin cannot
  safely be inferred.
- **Patient corrections are atomic.** Patient links, provider access and existing source-linked lab
  measurements move in one transaction. Existing measurement values and annotations are preserved;
  repeated matching does not import duplicate measurements. A failed lookup or write leaves the
  previous state intact and reports failure to the popup. The selected source/report and target
  patient must exist.
- **Only new events are affected.** Turning the switch on does not re-route results already in
  the system.
- **The decision does not depend on who triggers it.** An automatic import, a user with `_lab`
  write, and a user with `_hrm` write all get the same routing. The switch is read without a
  privilege check; changing it needs `_admin` write.

## Configuration

- **Where it is stored:** one global `property` row named `provider_linking_rules`, with the value
  `true` or `false`. A missing row, a provider-scoped row, or any value other than `true` means
  off. If duplicate global rows disagree, routing is off regardless of row order. No setting seed
  is needed. Migration `V1.0.39__track_automatic_mrp_routing.sql` adds the nullable provenance
  columns in both routing tables and is required before this application version starts. In the
  PR #3985–4000 rollout, deploy #3986's migration 36 and #3996's migrations 37/38 before 39;
  applying 39 first would put those lower versions out of order for Flyway.
  A row inserted by SQL with the column's default
  `provider_no = ''` counts as global, the same as one saved from the page (`NULL`).
- **Page:** `admin/providerLinkingRules` (`ProviderLinkingRules2Action`) accepts GET and HEAD
  only and needs `_admin` read. A user without `_admin` write sees the switch disabled.
- **Save:** `admin/saveProviderLinkingRules` (`SaveProviderLinkingRules2Action`) is POST only. GET
  and HEAD get 405 before anything else runs. It needs `_admin` write and is protected by
  CSRFGuard through the page's real `<form method="post">`. It redirects back to the page, so a
  reload cannot resubmit. It is registered in `MutatorActionGetRejectionContractUnitTest`.
- **Why `_admin` write:** the Open-O fork allowed `_admin` **or** `_lab` write, so any clinician
  with lab rights could change clinic-wide routing. CARLOS has no `_admin.lab` object, and
  `_admin.hrm` covers HRM only.

## Privacy notes for privacy officers

- Turning the switch on **widens who sees results**. Each patient's MRP receives labs and HRM
  reports they did not order. The MRP is the provider the chart names as responsible for the
  patient, and so is inside the circle of care. Clinics should still record the decision in
  their privacy documentation.
- **Every change of the switch is audited** in the `log` table:
  `action = 'update'`, `content = 'providerLinkingRules'`, `data = 'enabled=true|false'`, with the
  administrator's provider number and IP address.
- **Audit entries are written only after the change commits.** A match that rolls back leaves
  no entry, so the log never records a routing that did not happen.
- **Each newly created direct MRP routing is audited** in the `log` table (an existing routing
  does not generate a duplicate “route to MRP” event):
  `action = 'route to MRP'`, `content = 'providerLinkingRules'`,
  `contentId = '<HL7|MDS|CML|…|HRM>:<report id>'`, `demographic_no`, `data = 'mrp=<provider no>'`. The provider number is the user who triggered the match,
  or empty for an automatic import.
- The no-orderer upload fallback uses audit content `labPatientFallback`; it is independent of
  the switch. A committed lab patient match also records `match patient` / `labPatientRouting`,
  including when the MRP already had independent access.
- **The application log gets only sanitised identifiers** (`LogSafe`): the report type and id.
  No names, health numbers or report content are logged.

## Code map

| Piece | Class |
|---|---|
| Read and change the switch, and audit changes | `lab.service.ProviderLinkingRulesService` |
| Defer an audit entry until its transaction commits | `lab.service.CommittedAudit` |
| Decide on and perform MRP routing, and audit it | `lab.service.MrpRoutingService` |
| Route an HRM report to a provider (row, forwarding rules, unclaimed cleanup); shared with the manual assign-provider action | `hospitalReportManager.service.HrmProviderRoutingService` |
| HL7 upload hook | `MessageUploader.routeToProviders` |
| Lab Patient Match hook (now POST only) | `mds.pageUtil.PatientMatch2Action` |
| HRM assign-patient hook and provider-list update | `HRMModifyDocument2Action.assignDemographic`, `hospitalReportManager/hrmActions.js` |
| Admin page | `admin.web.ProviderLinkingRules2Action`, `admin.web.SaveProviderLinkingRules2Action`, `WEB-INF/jsp/admin/providerLinkingRules.jsp` |

**Related HRM fix.** HRM changes run under a report lock (`HRMDocumentDao.findForUpdate`). That
lock loads the eager `matchedProviders` and `matchedDemographics` collections. Removing one of
those rows with `EntityManager.remove()` made the next flush throw
`TransientPropertyValueException`. As a result, assigning a provider to an unclaimed report
failed on `release/2026.08`, and so did unlinking or re-linking a patient. The unclaimed and
patient rows are now deleted in bulk
(`HRMDocumentToProviderDao.deleteByHrmDocumentIdAndProviderNo`,
`HRMDocumentToDemographicDao.deleteByHrmDocumentId`). `HRMModifyTransactionIntegrationTest`
pins all four cases.

The legacy `auto_link_to_mrp` enum key and `ProviderManager2` methods remain available and are
marked deprecated for compatibility. Their old permission and storage behavior is preserved.
They do not enable the new clinic-wide rule: migrating an unrelated old preference automatically
would widen access without an administrator choosing it.

## Tests

- Unit: `ProviderLinkingRulesServiceUnitTest`, `MrpRoutingServiceUnitTest`,
  `HrmProviderRoutingServiceUnitTest`, `MessageUploaderProviderRoutingUnitTest`,
  `ProviderLinkingRules2ActionUnitTest`, `PatientMatch2ActionUnitTest`, the MRP cases in
  `HRMModifyDocument2ActionUnitTest`, and the two registrations in
  `MutatorActionGetRejectionContractUnitTest`.
- Browser: `scripts/provider-linking-rules-playwright-checks.js` (`npm run
  test:provider-linking-rules-playwright`, manifest entry `provider-linking-rules`, Ontario, core
  tier, database-asserting). It drives the switch from the Administration panel and then covers:
  - a synthetic CML lab uploaded through **HL7 Lab Upload**, with the switch off and then on;
  - Patient Match and correction from the lab's Patient Search popup, preserving independent
    ordering access and moving measurements without duplication;
  - HRM assign-patient and unlink in the viewer, verifying provenance, revoked access and the
    updated provider list;
  - the save route's refusal of GET and of a POST without a CSRF token.

  It restores everything it changed. Its pure helpers are pinned by
  `scripts/provider-linking-rules-playwright-checks.test.js`.

Related pre-existing defects found during review are tracked in [#4036](https://github.com/carlos-emr/carlos/issues/4036)
(atomic patient matching and visible failure), [#4037](https://github.com/carlos-emr/carlos/issues/4037)
(measurement ownership and duplication), and [#4038](https://github.com/carlos-emr/carlos/issues/4038)
(patient/status inbox query syntax).
