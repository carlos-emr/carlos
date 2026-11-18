# eForm Instance REST API

Saved eForm instances (`eform_data` rows, identified by `fdid`) can be read, created, revised and
deleted through the OAuth REST surface at `/ws/rs/eform/instance`. Templates (`eform` rows, `fid`)
keep their existing endpoints under `/ws/rs/eform`.

Code: `EFormService` (REST) → `EFormInstanceService` (transactional rules).

## Endpoints

| Method | Path | Body | Returns |
|--------|------|------|---------|
| `GET` | `/eform/instance/{fdid}` | none | the instance, its `formHtml` and stored `values` |
| `POST` | `/eform/instance` | `fid`, `demographicNo`, optional `subject`, `values` | the new instance (no HTML) |
| `PUT` | `/eform/instance/{fdid}` | optional `subject`, `values` | the **new** revision (no HTML) |
| `DELETE` | `/eform/instance/{fdid}` | none | the retired `fdid` |

```json
POST /ws/rs/eform/instance
{ "fid": 3, "demographicNo": 42, "subject": "Follow-up",
  "values": { "bp_systolic": "120", "notes": "", "smoker": null } }
```

In `values`, a string sets the field, `""` clears the template default, and `null` (or leaving the
field out) keeps the default. `values` holds one value per field; a `<select multiple>` gets its
first option only.

## A saved instance is never rewritten

The web UI never updates a saved eForm. Re-saving an opened form stores a new row. The API keeps
that rule:

- **PUT stores a new revision and retires the old row** (`current=false`), in one transaction. The
  response's `fdid` is the new one. The old `fdid` stays readable as history; a second PUT or a
  DELETE on it answers **409**. A client that gets a 409 should re-read the patient's current
  instance and apply its change there.
- The revision is rebuilt from the template **as it is now** plus the submitted `values`, the same
  way a browser re-save works. PUT replaces all values: a field left out gets the template default,
  not its earlier value. To change one field, GET the instance and send back all of its `values`
  with the change. `fid` and `demographicNo` cannot be changed; server-set fields in the body are
  ignored, so a GET response can be sent back as is.
- **DELETE only retires the row**, the same as the eForm list's delete button. It can be restored
  from the deleted-eForms list.

## Content and safety

Callers send values, never HTML. The server fills the stored template with the same `EForm.setValues`
path the browser save uses. Values are escaped, so they cannot add markup to the chart. Only
nonblank values get `eform_values` rows, the contract that measurement and chart readers rely on.
Attachments, fax, e-mail, PDF and eDoc handoffs from the browser save are not part of this API.

## Authorization

| Operation | `_eform` right | Also requires |
|-----------|----------------|---------------|
| GET | `r` | patient-scoped `_eform r` |
| POST | `w` | patient exists, template active, patient-scoped `_eform w` |
| PUT | `u` (implied by `w`) | patient-scoped `_eform u` |
| DELETE | `w` (as the browser delete button) | patient-scoped `_eform w` |

A template or instance with a `roleType` also needs `_eform.<roleType>`. A denied caller gets the
service's usual `200` with `"Access Denied"`. A missing instance gets **404**, and an instance
already retired gets **409**. Every operation writes an audit row (`eform` content, with the
patient).

## Tests

`EFormInstanceServiceUnitTest`, `EFormServiceEndpointTest`, `RestPrivilegeGuardContractUnitTest`,
`RestTransferObjectDateShapeContractUnitTest`. Against a deployed, scoped-OAuth server,
`AI_SCRIBE_API_ONLY=eforms.instances node scripts/ai-scribe-oauth-api-live-check.js` checks the
endpoints end to end and how their results look to a provider in the browser (see
`docs/ui-tests/ai-scribe-oauth-api.md`).
