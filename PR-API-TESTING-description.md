# Pull Request: CARLOS API Testing Documentation

**Title:** Docs: Add CARLOS API testing checklist

**Base Branch:** develop

**Compare Branch:** docs/carlos-api-testing

## Summary

Adds a CARLOS API testing worksheet so SOAP and REST testing can be documented
consistently. This is documentation-only and does not change runtime code,
configuration, API behavior, or automated tests.

## Changes Made

### API Testing Checklist

- Added `API-TESTING-CARLOS.md`.
- Documented discovered REST surfaces:
  - `/ws/rs` session-authenticated REST resources from `spring_ws.xml`.
  - `/ws/oauth` OAuth 1.0a token endpoints from `applicationContextREST.xml`.
  - `/ws/services` OAuth-protected REST services from `applicationContextREST.xml`.
- Documented discovered SOAP surfaces:
  - CXF SOAP services mounted under `/ws`.
  - OLIS `OLISRequest` WSDL contract in `src/main/axis2/service.wsdl`.
- Added an appointment integration mapping that connects external method names
  such as `post_appointment_data`, `update_appointment`,
  `get_day_work_schedule`, and `get_providers_appointments` to the actual
  CARLOS `ScheduleService` SOAP operations.
- Added dedicated Schedule SOAP checklist rows for required appointment reads,
  writes, status updates, date-only derived behavior, and unresolved support
  methods.
- Added manual testing checklists for authentication, successful calls, bad
  input, authorization failures, response formats, and PHI-safe error handling.
- Added a results table for recording what was tested, expected result, actual
  result, status, evidence, and notes.

## Testing and Verification

- Verified the documented API surfaces against:
  - `src/main/webapp/WEB-INF/web.xml`
  - `src/main/resources/spring_ws.xml`
  - `src/main/resources/applicationContextREST.xml`
  - `src/main/axis2/service.wsdl`
- Ran `git diff --check`.

## Manual Test Plan

Use `API-TESTING-CARLOS.md` during CARLOS API testing and record sanitized
results only. A minimum smoke pass should include:

- REST unauthenticated rejection.
- REST authenticated read through `/ws/rs`.
- OAuth 1.0a flow or a documented environment blocker.
- OAuth-protected `/ws/services` rejection without credentials.
- OAuth-protected `/ws/services` success when credentials are available.
- SOAP WSDL load.
- SOAP authentication rejection.
- SOAP success against a safe read-only operation when test credentials are
  available.
- Schedule SOAP appointment operation inventory.
- Required appointment integration reads and writes against disposable test
  data.
- Malformed REST and SOAP requests returning controlled errors.
- Confirmation that no PHI, secrets, OAuth tokens, passwords, or production
  patient identifiers are included in testing evidence.

## Notes

OSCAR API testing is intentionally deferred because that environment is
currently blocked. This PR records the CARLOS testing scope and gives testers a
place to document CARLOS results first.
