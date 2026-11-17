# API Collections Index

This index explains the three artifacts included in this repository for testing and documenting the REST and SOAP APIs.

## Files 

| File | Type | Purpose | How to use |
|---|---|---|---|
| `docs/postman-rest-collection.json` | Postman Collection (REST) | Full set of REST endpoints (GET/POST/PUT/DELETE). Includes example bodies and headers. | Import into Postman (or compatible tools) to run requests against your environments. |
| `docs/postman-soap-collection.json` | Postman Collection (SOAP) | SOAP requests packaged for Postman (HTTP + XML payloads). | Import into Postman; edit SOAP XML bodies and auth as needed. |
| `docs/api-request-response-matrix.xlsx` | Excel Workbook | Canonical table of endpoints with method, path/action, expected request body, response body, and status codes. | Open to review coverage, expected fields (incl. null/empty arrays), and error handling. Useful for QA and third-party integrators. |

## REST appointment edit compatibility and conflict detection

Responses serialized as `AppointmentTo1` include an `editVersion` string. Fetch the
current appointment with `POST /schedule/getAppointment` and body `{"id":42}` before
editing; the day and `fetchDays` summaries do not carry this token. Clients posting
to `/schedule/updateAppointment` should echo the token from the appointment they read,
alongside its `id` and editable fields. Preserve it as an opaque string, including
`"unversioned"` on a legacy row with no update timestamp. Use the new token returned
by a successful edit for the next save.

A missing, null, empty, or whitespace-only token preserves the legacy last-writer-wins
behavior in 2026.08: existing clients can keep sending their current payloads without
changes. These updates still lock the current row, archive its previous state, preserve
creation-audit fields, and return the next `editVersion`. The server logs a deprecation
warning without appointment or patient data; clients should adopt the read/echo flow
to detect conflicting edits.

When a nonblank token is supplied, an appointment changed since the read returns HTTP 409 with code
`APPOINTMENT_STALE_VERSION` and message `Appointment has changed; reload before saving`.
Reload the appointment and reconcile edits before retrying. A removed appointment
returns HTTP 404 with code `APPOINTMENT_NOT_FOUND`. Refused writes change neither
the appointment nor its archive. The specialized
`/schedule/appointment/{id}/updateStatus`, `/updateType`, and `/updateUrgency` routes
support the same optional token and return the same errors; each changes only its named field.
Creator and creation-audit fields remain
server managed, and the updating provider comes from the authenticated session.

## REST list pagination and demographic validation

These contracts apply to both the session routes under `/ws/rs` and the OAuth
routes under `/ws/services`. Paths below are relative to either prefix.

| List | Accepted `offset` | Accepted `limit` | Omitted values |
|---|---|---|---|
| `GET /demographics` | Zero or greater | 0 through 500 | Offset starts at zero; an omitted or zero limit uses the 500-patient default. |
| `GET /pharmacies/` | Zero or greater | 0 through 5000 | Offset starts at zero; an omitted limit uses the 5000-row maximum. A zero limit returns an empty page. |

With the required read privilege, either list returns HTTP 400 for a negative
offset, a negative limit or a limit above its maximum, before reading list data.
A negative limit is not an “all rows” option. For example,
`GET /demographics?offset=0&limit=500` and
`GET /pharmacies/?offset=0&limit=5000` use the largest supported page sizes.
Advance the nonnegative offset to request subsequent pages.

This changes older clients that sent negative offsets or used a negative
demographic limit: those requests previously used the first/default page.
Oversized limits and negative pharmacy limits now receive HTTP 400 instead of a
server error.

For `POST /demographics` and `PUT /demographics`, text fields checked by
`Demographic.validateFieldLengths()` return HTTP 400 before the write when they
exceed their limits. This guarantee applies to the fields checked by that validator;
it does not cover every text property (for example, `title` is not checked on the
release base). The response has `Content-Type: text/plain`
and lists the affected fields and their maximum lengths, separated by semicolons.
It does not echo submitted patient values. This error body is plain text even
when the client requests a JSON or XML response, so inspect the status and content
type before decoding it. Earlier builds could return a database error, or
truncate text when database strict mode was disabled.
