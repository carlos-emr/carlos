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
