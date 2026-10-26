# Prescription (Rx) session state is per patient

CARLOS EMR keeps the prescription module's working state (the staged "stash" of prescriptions,
the ReRx selection, allergy warnings) in an `RxSessionBean`. Until issue #3875 there was one
`"RxSessionBean"` and one `"Patient"` attribute per HTTP session. Opening Rx for a second patient
replaced both, so with two charts open a prescriber could stage, save or archive medications
against the wrong patient, and reopening Rx for the same patient wiped the staged drafts.

## Model

All lookups go through `io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBeanResolver`.

| Operation | Method | Behaviour |
|-----------|--------|-----------|
| Open Rx for a patient (`rx/choosePatient`, `rx/showAllergy`, a static-script link) | `activate(request, demographicNo, providerNo)` | Reuses that patient's bean (drafts survive, already-saved items are dropped) or creates one; makes the patient the session's active Rx patient. |
| Render Rx fragments for a named patient outside the Rx window (eChart, messenger PDF preview) | `ensure(request, demographicNo, providerNo)` | Creates the bean if missing; never prunes drafts. Sets the active patient only when the session has none yet; an existing active patient is left alone. |
| Any Rx read | `resolve(request)` | The bean of the patient named by `demographicNo` / `demographic_no`; if the request names no patient, the active patient's bean captured once for that request. A later active-patient change cannot switch the request's fragments to another patient. A named patient without a bean resolves to `null`, never to another patient. |
| Patient record for the page header, allergies, pharmacies | `resolvePatient(request)` / `resolvePatient(request, demographicNo)` | Loaded per request for the resolved bean's patient (cached on the request). The `"Patient"` session attribute is gone. |
| Stage, re-prescribe, edit, favourite, remove or clear staged items; delete, discontinue or long-term-toggle a saved drug; delete or re-activate an allergy | `resolveForWrite(request)` | Only the explicitly named patient's bean; `null` (no fallback) when the request names no patient. Staging and re-prescribe calls also check that a source drug id belongs to that patient. Actions with a read-only branch (the `rx/stash` `action=edit` cursor move, `rx/writeScript` without an `update*` action, `rx/viewScript` GET preview) keep `resolve` for that branch only. |
| Persist or archive medications | `isRequestForBeanPatient(request, bean)` | Required before a save. A save that does not name the window's patient is refused with 409 rather than written to the fallback patient; the staging page alerts the prescriber that nothing was saved. |

Beans live in the `RxSessionBeans` session attribute, keyed by demographic number, with a target of
25 patients per session. Above that target the least recently opened patients *with nothing staged*
are dropped. Drafts and pending ReRx selections are always preserved, so the map temporarily grows
beyond 25 when every older bean holds work. Later additions remove empty beans back toward the target
as work is saved or discarded. The patient being opened is never dropped. Eviction also clears that
patient's saved reprint workspace so reopening cannot revive an old reprint. The active patient
is `RxActiveDemographicNo`.

Every bean returned through a request is leased until the servlet container finishes that request
(including error and asynchronous completion); eviction skips leased beans. Resolving a bean and
then waiting to stage a draft therefore cannot leave the request holding an evicted workspace.
The registered `RxSessionBeanRequestListener` releases each request's leases without creating or
accessing a session that logout may already have invalidated.

### Patient-level authorisation of writes

A write's global privilege check (`_rx` or `_allergy` with no patient) only admits the caller to
the Rx module. Every Rx write also authorises the specific patient it changes through
`io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess`:

- `resolveForWrite(securityInfoManager, request, object, privilege)` resolves the named patient's
  bean exactly like `RxSessionBeanResolver.resolveForWrite` and then calls `requirePatient`;
- `requirePatient(...)` throws `SecurityException("missing required sec object (<object>)")`
  unless the caller holds the privilege for that patient (`hasPrivilege(..., demographicNo)`)
  **and** may open the patient's record (`isAllowedAccessToPatientRecord`);
- `mayAccessPatient(...)` is the same test as a boolean, for branches that skip rather than
  refuse (the `rx/viewScript` signature stamp).

It runs before any side effect on: stash staging, editing, removal and clearing; saves
(`updateSaveAllDrugs`, `updateAndPrint`, the `rx/viewScript` save/stamp POST); re-prescribing;
delete, discontinue and long-term toggles; favourites from a staged card; the signature link
(`saveDigitalSignature`, which also requires the script to belong to the window's patient);
allergy add, delete, re-activate and reorder; drug reasons; and the encounter append. The Rx view
gates use `require(...)` for the patient a request names. Stash staging (`rx/chooseDrug`, `rx/useFavorite`) and drug
reasons (`rx/RxReason` add/archive, POST-only; the popup view stays a GET at `_rx` read) need
`_rx` **write**, globally and for the patient: a staged card can only be saved by a writer and a
reason is persisted chart data. `RxPatientWriteAuthorizationUnitTest` drives every one of these
paths with the patient-level privilege denied, with only its write level denied (read still
held), and with record access denied.

The no-patient fallback is for read-only compatibility only. Pages that change Rx state for a
patient (`StaticScript2.jsp`, the staging page) are opened with an explicit `demographicNo`, and
every link into them names it.

## Browser side

`share/javascript/rx-patient-context.js`, loaded on the Rx staging page with
`data-demographic-no`, adds `demographicNo` to every `/rx/` request made through `CarlosAjax`,
`jQuery.ajax`, `popupWindow()`, form submission and followed links, so the no-patient fallback is only a
compatibility path. It tags only relative URLs and absolute http(s) URLs on the page's own origin;
protocol-relative (`//host/...`), cross-origin and non-http (`javascript:`, `data:`) URLs are never
tagged, so the patient id cannot leak to another host. The wrappers are installed once per window
but read the patient at call time from a per-window holder that each page's `install()` updates, so
wrapped globals that outlive a page never keep tagging with the previous page's patient.
A CarlosAjax or jQuery call whose body already names the patient is not tagged again on the URL.
The resolver accepts a repeated, equal `demographicNo`, but Struts binds a repeated parameter into a
typed action property as an array: that is a conversion error, and the action answers with its
unmapped `input` result (HTTP 404). So Rx actions resolve the patient through the resolver and must
not declare `demographicNo` as a typed `@StrutsParameter` property
(`RxWriteScript2ActionStrutsBindingIntegrationTest` drives the real interceptor stack with the
repeated shape). A form submitted programmatically
(`form.submit()`) fires no submit event, so such forms carry their own hidden `demographicNo`. `RxPatientContext.withPatient(url)` tags URLs built by hand (the print-preview
POST). Pages that post with `fetch` (for example `ViewScript2.jsp`) put `demographicNo` in the body.

## Why not the alternatives

* **Swap the shared attribute per request** (the upstream Open-O approach): two tabs' concurrent
  requests race on the one attribute, so a request can still read the other tab's bean.
* **Per-browser-tab workspaces** (CARLOS PR #479): isolates duplicate tabs for the same patient
  too, but needs a request/session wrapper, a context id threaded through every URL, heartbeats
  and a much larger change. It can be layered on later; the per-patient keying here is its
  prerequisite, not a conflict.

## Concurrent saves

Each rendered card carries an opaque draft revision independent of its numeric UI key. A save
validates the full card set and every revision under the patient workspace lock before changing
any fields. Persistence consumes the revisions before writing, so a second submission of the
same form cannot create another prescription. A newly staged card also has a new revision,
even if it reuses a removed card's numeric key. Stale forms receive HTTP 409 (`STALE_RX_STASH`).
The successful Save and Print response returns the new revisions to that window, so its Edit Rx
flow can continue while other windows retain stale revisions. The legacy editor submits the
selected card identity and the complete rendered revision set too. Save Only clears the saved
stash inside the same lock as persistence; it does not send a later clear request that could
discard another window's newly staged draft.

Malformed numeric fields and unknown diagnosis codes are refused before persistence. Database
or ancillary-write failures return an explicit error and do not open a successful preview or
clear the drafts. The legacy DAO writes do not share one transaction: an incomplete save may
already have written some rows. Its error tells the clinician to inspect the chart before
retrying, and the consumed revisions prevent resubmitting the old form. Reloading alone does
not establish that nothing was saved.

ReRx batch lookups finish before staging starts, so a database failure does not stage a partial
selection. The browser retains selections on a failed staging request. Pharmacy updates and
discontinuation note failures likewise report incomplete changes instead of appearing successful.

## Prescription and pharmacy ownership coverage

The ownership cases in [issue #2463](https://github.com/carlos-emr/carlos/issues/2463)
and Simran Panda's (@simpanda01)
[PR #3304](https://github.com/carlos-emr/carlos/pull/3304) are incorporated into this work's
regression coverage. Bulk deletion validates every requested drug before changing any row;
single deletion and discontinuation reject missing or foreign drugs; discontinuation notes use
the verified patient. Pharmacy unlink and preference changes authorize the explicitly named
patient and use that patient's valid workspace.

The original contribution's session-wide patient lookup is superseded by the per-patient
resolver here, so an action in one chart cannot be retargeted by opening a different chart.
The checks also cover malformed IDs, null record ownership and rejected mixed batches without
partial mutation. This preserves the contribution's security intent without restoring the old
single-active-patient assumption.

Copilot's [PR #2478](https://github.com/carlos-emr/carlos/pull/2478) also identified the
favorite-copy dispatches as mutations. Copying favorites and changing sharing preferences now
require POST, while refreshing the provider list remains a read-only GET. The method-rejection
contract covers these dispatches alongside the other Rx mutations.
Sharing preferences and copy destinations always belong to the authenticated provider. A source
must be public or owned by that provider, and every selected favorite must belong to that source
before any copy is written. If copying succeeds but the page cannot reload, the error explicitly
states that the favorites were saved so a retry cannot look like the only way to finish.

## Known limits

* Two windows open for the *same* patient share that patient's stash (by design: it is what lets
  a reopen keep drafts).
* Reprint mode is per patient too: `RxReprintWorkspace` keeps one entry per demographic number
  (the reprinted script, its comment and the "reprinting" flag), replacing the session-wide
  `rePrint` / `tmpBeanRX` attributes.
