# Fax Provider Configuration & UX Notes

## Purpose
This document captures the operational and developer context for the fax provider abstraction
introduced in CARLOS, with emphasis on SRFax behavior and admin configuration UX.

## Provider Routing Model
- Fax transport is selected per `FaxConfig` via `providerType`.
- Supported values:
  - `MIDDLEWARE` (legacy relay behavior)
  - `SRFAX` (direct SRFax API behavior)
  - `RINGCENTRAL` (direct RingCentral Fax API behavior)
- Routing is resolved by `FaxProviderClientFactory`.

## Admin UX Entry Points
- Navigation: **Administration > Faxes > Configure Fax**
- Page: `src/main/webapp/WEB-INF/jsp/admin/configureFax.jsp`
- View action (gate): `admin/ViewConfigureFax` (extensionless Struts 7 route in `struts-admin.xml`)
- Save / scheduler action: `admin/ManageFax?method=configure|getFaxSchedularStatus|restartFaxScheduler|getPendingIncomingFaxes`
  (extensionless route in `struts-provider.xml`, backed by `ConfigureFax2Action`)

## Required Permissions
- Fax configuration view/edit requires `_admin.fax` with write rights (`w`).
- Scheduler controls are separately gated by `_admin.fax.restart`.

## SRFax Duplicate Management Policy
- SRFax inbound duplicates are controlled via provider read-state semantics:
  - pull unread-only inbox items (`sUnreadOnly=true` / `sIncludeRead=false`)
  - mark as viewed on successful retrieve via SRFax API (`sMarkasViewed=Y`, treated as "read" in CARLOS EMR)
- Do **not** delete inbound SRFax files server-side as part of duplicate management.

## Configuration Expectations
When provider type is `SRFAX`:
- `faxUrl` is ignored for SRFax; the fixed API endpoint (`https://www.srfax.com/SRF_SecWebSvc.php`)
  is used automatically. Override via `srfax.api.url` in carlos.properties if needed.
- `faxUser` maps to `sFaxUserName`.
- `faxPassword` maps to `sFaxPassword`.

When provider type is `MIDDLEWARE`:
- existing relay behavior and URL conventions remain unchanged.

When provider type is `RINGCENTRAL`:
- `faxUrl` is ignored; the fixed production endpoint (`https://platform.ringcentral.com`) is used
  unless `ringcentral.use.sandbox=true` selects the sandbox endpoint.
- The endpoint can be overridden via `ringcentral.api.url` (production) or
  `ringcentral.api.sandbox.url` (sandbox) in `carlos.properties`. Override values are validated
  against an explicit host whitelist (`platform.ringcentral.com`, `platform.devtest.ringcentral.com`)
  and must be plain `https://<host>` with no path/port/query/userinfo; values that don't match
  fall back to the documented default and a warning is logged.
- Required fields are RingCentral client ID, client secret, and JWT token. The client secret and
  JWT token are encrypted at rest with the same CARLOS credential encryption used for fax passwords.
  A `FaxConfig` `@PrePersist`/`@PreUpdate` invariant rejects rows that select `RINGCENTRAL` without
  the three OAuth fields, so admin-save fails before the row hits the database.
- Account ID and extension ID are optional; leave blank to use RingCentral's `~` default for the
  authenticated account and extension.
- Inbound duplicate prevention uses unread-only polling plus mark-as-read after successful local
  persistence. CARLOS does not delete RingCentral messages server-side; the RingCentral
  `deleteFax` implementation is a contractual no-op (matching SRFax) so the shared `FaxImporter`
  cleanup loop calls it uniformly.
- The inbox poll walks RingCentral's `navigation.nextPage` cursor up to a 50-page safety cap, so
  inboxes larger than the per-page limit are not silently truncated. The 50-page cap protects
  against pathological self-loop cursors; remaining unread faxes are picked up on the next poll.
- A 401 from any RingCentral call invalidates the cached OAuth token via
  `RingCentralAuthService.invalidateToken(...)` so the next call re-authenticates rather than
  reusing a token the provider has rejected.

## Files to Know
- Provider API contract: `src/main/java/io/github/carlos_emr/carlos/fax/provider/FaxProviderClient.java`
- Provider resolver: `src/main/java/io/github/carlos_emr/carlos/fax/provider/FaxProviderClientFactory.java`
- SRFax implementation: `src/main/java/io/github/carlos_emr/carlos/fax/provider/SRFaxProviderClient.java`
- Middleware implementation: `src/main/java/io/github/carlos_emr/carlos/fax/provider/MiddlewareFaxProviderClient.java`
- RingCentral implementation: `src/main/java/io/github/carlos_emr/carlos/fax/ringcentral/RingCentralFaxService.java`
- RingCentral API connector: `src/main/java/io/github/carlos_emr/carlos/fax/ringcentral/RingCentralApiConnector.java`
- Admin action: `src/main/java/io/github/carlos_emr/carlos/fax/admin/ConfigureFax2Action.java`
- Admin UI: `src/main/webapp/WEB-INF/jsp/admin/configureFax.jsp`
