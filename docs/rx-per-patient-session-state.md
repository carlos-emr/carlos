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
| Render Rx fragments for a named patient outside the Rx window (eChart, messenger PDF preview) | `ensure(request, demographicNo, providerNo)` | Creates the bean if missing; never changes the active patient or prunes drafts. |
| Any Rx read | `resolve(request)` | The bean of the patient named by `demographicNo` / `demographic_no`; if the request names no patient, the active patient's bean. A named patient without a bean resolves to `null`, never to another patient. |
| Patient record for the page header, allergies, pharmacies | `resolvePatient(request)` / `resolvePatient(request, demographicNo)` | Loaded per request for the resolved bean's patient (cached on the request). The `"Patient"` session attribute is gone. |
| Stage, re-prescribe, edit or remove staged items | `resolveForWrite(request)` | Only the explicitly named patient's bean; `null` (no fallback) when the request names no patient. Staging and re-prescribe calls also check that a source drug id belongs to that patient. |
| Persist or archive medications | `isRequestForBeanPatient(request, bean)` | Required before a save. A save that does not name the window's patient is refused with 409 rather than written to the fallback patient; the staging page alerts the prescriber that nothing was saved. |

Beans live in the `RxSessionBeans` session attribute, keyed by demographic number, capped at 25
patients per session. Over the cap the least recently opened patient *with nothing staged* is
dropped; only when every bean holds drafts or a ReRx selection is the least recently opened one
dropped anyway (logged), and the patient being opened is never the one dropped. The active patient
is `RxActiveDemographicNo`.

The no-patient fallback is for read-only compatibility only. Pages that change Rx state for a
patient (`StaticScript2.jsp`, the staging page) are opened with an explicit `demographicNo`, and
every link into them names it.

## Browser side

`share/javascript/rx-patient-context.js`, loaded on the Rx staging page with
`data-demographic-no`, adds `demographicNo` to every `/rx/` request made through `CarlosAjax`,
`jQuery.ajax`, `popupWindow()`, form submission and followed links, so the no-patient fallback is only a
compatibility path. `RxPatientContext.withPatient(url)` tags URLs built by hand (the print-preview
POST). Pages that post with `fetch` (for example `ViewScript2.jsp`) put `demographicNo` in the body.

## Why not the alternatives

* **Swap the shared attribute per request** (the upstream Open-O approach): two tabs' concurrent
  requests race on the one attribute, so a request can still read the other tab's bean.
* **Per-browser-tab workspaces** (CARLOS PR #479): isolates duplicate tabs for the same patient
  too, but needs a request/session wrapper, a context id threaded through every URL, heartbeats
  and a much larger change. It can be layered on later; the per-patient keying here is its
  prerequisite, not a conflict.

## Known limits

* Two windows open for the *same* patient share that patient's stash (by design: it is what lets
  a reopen keep drafts).
* Reprint mode still uses the session-wide `rePrint` / `tmpBeanRX` attributes.
