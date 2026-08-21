# Fax Provider Configuration & UX Notes

## Purpose
This document captures the operational and developer context for the fax provider abstraction
in CARLOS, with emphasis on SRFax behavior and admin configuration UX.

## Provider Routing Model
- Fax transport is selected per `FaxConfig` via `providerType`.
- Supported values:
  - `SRFAX` (direct SRFax API) — **the supported provider and the default for new configurations**.
  - `MIDDLEWARE` (legacy relay) — transport code is retained, but the admin UI only offers this
    option for a grandfathered row that is already stored with `providerType=MIDDLEWARE`.
- Routing is resolved by `FaxProviderClientFactory`; every core service
  (`FaxImporter`, `FaxSender`, `FaxStatusUpdater`, and the Manage Faxes cancel path) resolves the
  client per config and never branches on provider type itself.

## Admin UX Entry Points
- Navigation: **Administration > Faxes > Configure Fax** and **Administration > Faxes > Manage Faxes**
- Pages (internal, behind gate actions): `src/main/webapp/WEB-INF/jsp/admin/configureFax.jsp`,
  `src/main/webapp/WEB-INF/jsp/admin/manageFaxes.jsp`
- Gate actions (extensionless routes): `/admin/ViewConfigureFax`, `/admin/ViewManageFaxes`
- AJAX endpoints: `/admin/ManageFax?method=configure|getFaxSchedularStatus|restartFaxScheduler|getPendingIncomingFaxes`
  and `/admin/ManageFaxes?method=fetchFaxStatus|viewFax|CancelFax|ResendFax|SetCompleted`
- All mutating methods (`configure`, `restartFaxScheduler`, `CancelFax`, `ResendFax`,
  `SetCompleted`) reject GET/HEAD with 405 and are registered in the mutator GET-rejection
  contract test.

## Required Permissions
- Fax configuration view/edit requires `_admin.fax` with write rights (`w`).
- Manage Faxes queue operations (cancel/resend/resolve) require `_admin.fax` write; the status
  search (`fetchFaxStatus`) requires `_admin.fax` read.
- Scheduler controls (status display + restart) are separately gated by `_admin.fax.restart`.
- Clinician send/receive uses `_fax`.

## SRFax API Mapping
Single endpoint (`POST https://www.srfax.com/SRF_SecWebSvc.php`, overridable via `srfax.api.url`
in carlos.properties — HTTPS on `srfax.com`/`*.srfax.com` only). Credentials on every call:
- `FaxConfig.faxUser` → `access_id` (the numeric SRFax account number)
- `FaxConfig.faxPasswd` → `access_pwd` (AES-encrypted at rest via `EncryptionUtils`)

Operations used:
- `Queue_Fax` — outbound send (`sCallerID` = the configured 10-digit fax number,
  `sToFaxNumber` = 11-digit dialable destination, single PDF as `sFileName_1`/`sFileContent_1`).
  The returned numeric `FaxDetailsID` is stored as `FaxJob.jobId`; a missing or non-numeric id
  fails the send (job goes to ERROR for a deliberate manual resend — never auto-retried, to avoid
  double transmission).
- `Get_Fax_Inbox` with `sViewedStatus=UNREAD` — inbound polling (unread-only pull).
- `Retrieve_Fax` (`sDirection=IN`, `sFaxFormat=PDF`, **no** `sMarkasViewed`) — inbound download.
- `Update_Viewed_Status` (`sMarkasViewed=Y`) — marks the fax read WITHOUT re-downloading it,
  issued only after the document is safely persisted locally.
- `Get_FaxStatus` — outbound delivery status polling by `sFaxDetailsID`.
- `Stop_Fax` — cancel from Manage Faxes. `Status=Success` distinguishes "Fax Cancelled" /
  "Fax cancelled but partially sent" (recorded as CANCELLED) from "Fax transmission completed"
  (already sent — recorded as SENT, surfaced to the admin in the status string).

Responses are parsed fail-closed: only `Status` of `Success`/`1` is accepted; failures and
unrecognized statuses raise `FaxProviderException`.

## Number Normalization Policy
- `fax_config.faxNumber` stores exactly **10 digits** (Configure Fax strips formatting and drops a
  leading `1` from an 11-digit entry; anything else is rejected with a row-level error). The
  10-digit value is also the join key between `FaxConfig.faxNumber` and `FaxJob.fax_line`.
- At send time the SRFax client derives `sCallerID` (10-digit) and `sToFaxNumber` (11-digit,
  `1` prepended to a 10-digit destination).

## SRFax Duplicate Management Policy
Inbound duplicate prevention is read-state based — inbound SRFax files are **never deleted**
server-side:
1. Poll pulls unread-only (`sViewedStatus=UNREAD`).
2. `Retrieve_Fax` downloads WITHOUT marking read; the file is quarantined under
   `FAX_INCOMING_DIR/<configId>/`.
3. Only after the local file is safe is `Update_Viewed_Status` issued; the EMR import
   (EDoc "Received Fax" → configured inbox queue → unclaimed provider routing) then proceeds.
4. If mark-as-read fails, the next poll would see the same fax again — the importer therefore
   dedups by provider job id (`FaxDetailsID`, parsed from the pipe suffix of the inbox
   `FileName`): a fax whose id already maps to an imported row is not downloaded again; the
   importer just retries the mark-as-read.
A failed import (download/PDF validation) leaves the fax unread on SRFax so it is retried —
that is intentional, and pre-import error rows do not suppress the retry.

## Scheduler
- `FaxSchedulerJob` polls at `faxPollInterval` (default 60000 ms), running importer → sender →
  status updater per cycle. On a RuntimeException it stops and auto-restarts at a fixed
  10-minute interval (uncapped by design); OOM/JVM errors require manual restart.
- Admin status distinguishes **Scheduler Running**, **Scheduler Idle (No Active Fax Accounts)**
  (stopped without a recorded error — the benign startup state), and
  **Scheduler Stopped (Fatal Error)** (stopped with a recorded error).
- Saving a configuration with an active account auto-starts the scheduler.

## Configuration Expectations
When provider type is `SRFAX`:
- `faxUrl` is ignored; the fixed API endpoint is used automatically (see `srfax.api.url` above).
- `faxUser`/`faxPassword` are the SRFax `access_id`/`access_pwd`.
- "Poll incoming faxes" maps to `FaxConfig.download`; the Inbox Queue selects the document
  review queue incoming faxes are filed into.

When provider type is `MIDDLEWARE` (grandfathered rows only):
- existing relay behavior and URL conventions remain unchanged, including cancel via HTTP PUT
  through the endpoint allow-list (`carlos.fax.middleware.allowedHosts`).

## Files to Know
- Provider API contract: `src/main/java/io/github/carlos_emr/carlos/fax/provider/FaxProviderClient.java`
- Provider resolver: `src/main/java/io/github/carlos_emr/carlos/fax/provider/FaxProviderClientFactory.java`
- SRFax implementation: `src/main/java/io/github/carlos_emr/carlos/fax/provider/SRFaxProviderClient.java`
- Middleware implementation: `src/main/java/io/github/carlos_emr/carlos/fax/provider/MiddlewareFaxProviderClient.java`
- Core pipeline: `src/main/java/io/github/carlos_emr/carlos/fax/core/{FaxSchedulerJob,FaxImporter,FaxSender,FaxStatusUpdater}.java`
- Admin actions: `src/main/java/io/github/carlos_emr/carlos/fax/admin/{ConfigureFax2Action,ManageFaxes2Action}.java`
- Clinician action: `src/main/java/io/github/carlos_emr/carlos/fax/action/Fax2Action.java`
- Admin UI: `src/main/webapp/WEB-INF/jsp/admin/{configureFax.jsp,manageFaxes.jsp,faxStatusResults.jspf}`
- Send dialog: `src/main/webapp/WEB-INF/jsp/fax/CoverPage.jsp`
