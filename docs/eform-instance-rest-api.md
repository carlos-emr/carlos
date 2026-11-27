# eForm Instance REST API

Saved eForm instances (`eform_data` rows, identified by `fdid`) can be read, created, revised and
deleted through the OAuth REST surface at `/ws/services/eform/instance`, which needs a signed
OAuth access token. The same endpoints answer a signed-in browser session at
`/ws/rs/eform/instance`. Templates (`eform` rows, `fid`) keep their existing endpoints under
`/eform` on both routes. Paths below are relative to either route.

Code: `EFormService` (REST) → `EFormInstanceService` (transactional rules).

## Endpoints

| Method | Path | Body | Returns |
|--------|------|------|---------|
| `GET` | `/eform/instance/{fdid}` | none | the instance, its `formHtml` and stored `values` |
| `POST` | `/eform/instance` | `fid`, `demographicNo`, optional `subject`, `values` | the new instance (no HTML) |
| `PUT` | `/eform/instance/{fdid}` | `values` (**required**), optional `subject` | the **new** revision (no HTML) |
| `DELETE` | `/eform/instance/{fdid}` | none | the retired `fdid` |

```json
POST /ws/services/eform/instance
{ "fid": 3, "demographicNo": 42, "subject": "Follow-up",
  "values": { "bp_systolic": "120", "notes": "", "smoker": null } }
```

In `values`, a string sets the field, `""` clears the template default (a checkbox included), and
`null` (or leaving the field out) keeps the default. Field names are at most 30 characters (the
width of `eform_values.var_name`) and the subject at most 255; longer ones are refused rather than
cut, since a cut name could not be read back or sent back.

Each name must be a text, hidden or date input, a checkbox, a radio group, a textarea or a select
on the template, and a radio or select value must be one of its choices. A checkbox takes the
value a browser submits for it when checked: its `value` attribute, or `"on"` when it has none.
`""` unchecks it. A name shared by several checkboxes is refused, because the form can only check
the first of them. Any other value (a name not on the template, `type="number"`, a control the form
fills by inserting text into its markup, a checkbox value other than the box's own) is refused with
`"EForm field is not on the template or cannot be set through the API"`, rather than stored as a
value the form never shows.

`values` holds one value per field. A `<select multiple>` is stored, as in the browser save, with
its first choice as its value; a PUT that sends that value back unchanged keeps every choice the
revised instance had, and a PUT that changes it selects that one choice.

A GET returns each stored value, and `""` for each field the instance cleared from a nonblank
template default (a radio group whose checked choice has a blank value included), so sending the
`values` back keeps those fields as they were. A GET response can always be sent back as a PUT: a
stored value the current template cannot show (a browser save also stores values such as a
`type="number"` field) is carried forward to the new revision unchanged, whether it is sent back or
left out. It cannot be changed through the API; a different value for such a field is refused. When
the field's control is still on the template, the revision shows the carried value the way a browser
re-save of the same instance shows it: a radio value that is no longer one of the choices (the
template was edited) leaves every choice unchecked, and a checkbox group's value checks the box.

## What a save does

A save through the API does what the browser save does, apart from the handoffs listed at the end:

- **Prefill (POST).** Fields the template fills from the database (`oscarDB=`, such as the
  patient's name or the provider) get those values for every field the caller leaves out, as the
  new-form page shows them before the clinician submits, and they get value rows like any submitted
  value. A value the caller sends replaces the prefill. Appointment-based prefills are blank: the
  API has no appointment. A PUT does not refresh them, as a browser re-save does not; GET returns
  them as stored values to send back.
- **Chart writes.** Every POST and PUT runs the template's chart writes, exactly once: encounter and
  CPP notes, documents, preventions, messages, ticklers and consultation requests declared in the
  template's `<template>` section, with the same nonblank values the browser save passes. They run
  in the save's transaction, so a failure stores neither the form nor part of its chart entries.
  Notes are filed under the provider's default program, and their link to the form is relative to
  the application's context path.
- **Attachments (PUT).** The revision takes the old row's place in the chart: the old row's
  attachments (documents, labs, forms, eForms, HRM reports) are copied to the revision, and the
  consultation packets and other eForms that attached the old row attach the revision instead. The
  old row keeps its own attachments as history. Without this, a consultation packet would list the
  retired eForm as unavailable and leave it out.
- Not part of this API: new attachments, fax, e-mail, PDF and eDoc handoffs.

## A saved instance is never rewritten

The web UI never updates a saved eForm. Re-saving an opened form stores a new row. The API keeps
that rule:

- **PUT stores a new revision and retires the old row** (`current=false`), in one transaction. The
  response's `fdid` is the new one; keep it. The old `fdid` stays readable as history; a second
  PUT or a DELETE on it answers **409**.
- **`values` is required on PUT.** A body without it is refused, so a client cannot reset the form by
  sending only a subject. Send `{}` to save the template defaults on purpose.
- **A 409 does not say what replaced the row.** A GET on a retired `fdid` returns that row itself
  (`current=false`), never its replacement, and this API has no lookup from an `fdid` to its
  successor or listing of a patient's instances. A row retired by a PUT has a successor, the `fdid`
  that PUT returned; a row retired by a DELETE has none. A client that no longer has the newer
  `fdid` cannot find it through this API: the current revision has to be found in the patient's
  eForm list in the chart.
- **A browser re-save is not visible as a retirement.** The browser stores a new row and leaves the
  opened one current, so a PUT built from an older read can still succeed and replace a clinician's
  later edit. For a template that shows only its latest form (`showLatestFormOnly`), the API refuses
  that case with **409** ("A newer form of this template exists for the patient"), because the
  chart would otherwise show the revision and hide the clinician's form. Other templates list every
  form, so both stay visible; re-read the patient's chart before revising a form a clinician may
  also be editing.
- The revision is rebuilt from the template **as it is now** plus the submitted `values`, the same
  way a browser re-save works. PUT replaces all values: a field left out gets the template default,
  not its earlier value. To change one field, GET the instance and send back all of its `values`
  with the change. `fid` and `demographicNo` cannot be changed; server-set fields in the body are
  ignored, so a GET response can be sent back as is.
- **DELETE only retires the row**, the same as the eForm list's delete button. It can be restored
  from the deleted-eForms list. A row retired by a PUT also appears in that list (see
  `docs/ui-tests/oauth-clinical-api.md`); restoring it there makes it current next to its newer
  revision, because nothing stored marks it as replaced, and a PUT on the restored row is then
  accepted and starts a second line of revisions. This is a known limit: refusing it needs a schema
  change that records which row replaced which. Until then, do not restore a row a PUT replaced.

## Content and safety

Callers send values, never HTML. The server fills the stored template with the same `EForm.setValues`
path the browser save uses. Values are escaped, so they cannot add markup to the chart. Only
nonblank values get `eform_values` rows, the contract that measurement and chart readers rely on.

**Signed forms and Rich Text Letters** are a known limit. GET returns a signature pad's image as the
data URI the browser save stored, and a letter's body as HTML. The packaged ModSecurity rules exempt
those values only on the browser save (`carlos-data-uri` and `Letter` on `/eform/addEForm`), so on a
packaged install a PUT that sends them back is likely refused with **403** by the WAF before it
reaches CARLOS (from the shipped rules; not checked against a live WAF). A PUT that leaves such a
field out gives it the template default, like any other field, so the revision is unsigned and the
signed row is retired. Revise signed forms and letters in the browser.

## Status codes

Only these are HTTP errors:

| Status | When |
|--------|------|
| **404** | no instance has the `fdid`, or the caller may not see it (its patient, patient record or role) |
| **409** | the instance was already deleted or revised, or (PUT, latest-only template) a newer form replaced it |

Every other refusal is HTTP **200** with `"status": "ERROR"` and a message, this service's usual
convention: `"Access Denied"`, a missing `fid` or `demographicNo` on POST, and the reasons
`TEMPLATE_UNAVAILABLE`, `PATIENT_NOT_FOUND`, `INVALID_FIELD_NAME`, `INVALID_SUBJECT`,
`UNSUPPORTED_FIELD` and `VALUES_REQUIRED` (their messages are in
`EFormInstanceService.EFormInstanceException.Reason`). Check `status`, not only the HTTP status.

## Authorization

| Operation | OAuth scope | `_eform` right | Also requires |
|-----------|-------------|----------------|---------------|
| GET | `eform.read` | `r` | patient-scoped `_eform r`, patient-record access |
| POST | `eform.write` | `w` | template active, patient-scoped `_eform w`, patient-record access, patient exists |
| PUT | `eform.write` | `w` (as the browser re-save) | patient-scoped `_eform w`, patient-record access |
| DELETE | `eform.write` | `w` (as the browser delete button) | patient-scoped `_eform w`, patient-record access |

In the default legacy-restricted OAuth mode, every instance call is refused with
`restricted_endpoint`; the endpoints need scoped mode. `eform.write` also covers template
authoring (`/eform/json`), so a client that should only file forms still holds the right to change
templates; a separate instance scope is a possible follow-up.

Patient-record access is `SecurityInfoManager.isAllowedAccessToPatientRecord` for the instance's
patient (or, on POST, the requested one). A template or instance with a `roleType` also needs
`_eform.<roleType>`. A caller without the `_eform` right itself gets the service's usual `200` with
`"Access Denied"`. On GET, PUT and DELETE, an instance the caller may not see (its patient, patient
record or role) gets **404**, exactly as a missing instance does, so a caller cannot learn which
`fdid`s exist. The one exception is a PUT whose template has since been given a `roleType` the caller
lacks: the instance itself is visible to that caller, so the PUT gets `"Access Denied"` instead. An instance already retired gets **409**, but only after those checks pass. On
POST, a patient the caller may not write to is answered exactly as a patient that does not exist,
and those checks run before the patient is looked up. Every operation writes an audit row (`eform`
content, with the patient).

## Tests

`EFormInstanceServiceUnitTest`, `EFormServiceEndpointTest`, `OAuthScopesUnitTest`,
`RestPrivilegeGuardContractUnitTest`, `RestTransferObjectDateShapeContractUnitTest`,
`JaxbImplementationCompatibilityUnitTest`. Against a deployed, scoped-OAuth server,
`OAUTH_CLINICAL_API_ONLY=eforms.instances node scripts/oauth-clinical-api-playwright-checks.js` (manifest entry
`oauth-clinical-api`) checks the endpoints end to end and how their results look to a provider in
the browser (see `docs/ui-tests/oauth-clinical-api.md`).
