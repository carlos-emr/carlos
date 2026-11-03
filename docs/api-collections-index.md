# API Collections Index

This index explains the three artifacts included in this repository for testing and documenting the REST and SOAP APIs.

## Files 

| File | Type | Purpose | How to use |
|---|---|---|---|
| `docs/postman-rest-collection.json` | Postman Collection (REST) | Full set of REST endpoints (GET/POST/PUT/DELETE). Includes example bodies and headers. | Import into Postman (or compatible tools) to run requests against your environments. |
| `docs/postman-soap-collection.json` | Postman Collection (SOAP) | SOAP requests packaged for Postman (HTTP + XML payloads). | Import into Postman; edit SOAP XML bodies and auth as needed. |
| `docs/api-request-response-matrix.xlsx` | Excel Workbook | Canonical table of endpoints with method, path/action, expected request body, response body, and status codes. | Open to review coverage, expected fields (incl. null/empty arrays), and error handling. Useful for QA and third-party integrators. |

## REST appointment edit precondition

Appointment responses include an `editVersion` string. Clients posting to
`/schedule/updateAppointment` must echo the token from the appointment they read,
alongside its `id` and editable fields. Preserve it as an opaque string, including
`"unversioned"` on a legacy row with no update timestamp. Use the new token returned
by a successful edit for the next save.

A missing or blank token returns HTTP 400 with code `APPOINTMENT_MISSING_VERSION`.
An appointment changed since the read returns HTTP 409 with code
`APPOINTMENT_STALE_VERSION` and message `Appointment has changed; reload before saving`.
Reload the appointment and reconcile edits before retrying. A removed appointment
returns HTTP 404 with code `APPOINTMENT_NOT_FOUND`. Refused writes change neither
the appointment nor its archive. Existing clients must adopt this read/echo flow;
there is no unconditional-write fallback. Creator and creation-audit fields remain
server managed, and the updating provider comes from the authenticated session.
