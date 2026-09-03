# Fax Provider Configuration & UX Notes

## Purpose
This document captures the operational and developer context for the fax provider abstraction
in CARLOS, with emphasis on SRFax behavior and admin configuration UX.

## Provider Routing Model
- Fax transport is selected per `FaxConfig` via `providerType`.
- Supported values:
  - `SRFAX` (direct SRFax API) — **the supported provider, and the only one shown/used in the admin UI**.
  - `MIDDLEWARE` (legacy relay) — hidden from the admin UI. Its transport code and enum are
    retained and remain selectable only via direct configuration/DB for legacy relay deployments.
    A stored MIDDLEWARE row shows SRFAX in the UI and migrates to SRFAX on the next save.
- Routing is resolved by `FaxProviderClientFactory`; every core service
  (`FaxImporter`, `FaxSender`, `FaxStatusUpdater`, and the Manage Faxes cancel path) resolves the
  client per config and never branches on provider type itself.

## Admin UX Entry Points
- Navigation: **Administration > Faxes > Configure Fax** and **Administration > Faxes > Manage Faxes**
- Pages (internal, behind gate actions): `src/main/webapp/WEB-INF/jsp/admin/configureFax.jsp`,
  `src/main/webapp/WEB-INF/jsp/admin/manageFaxes.jsp`
- Gate actions (extensionless routes): `/admin/ViewConfigureFax`, `/admin/ViewManageFaxes`
- AJAX endpoints: `/admin/ManageFax?method=configure|testConnection|getFaxSchedularStatus|restartFaxScheduler|getPendingIncomingFaxes`
  and `/admin/ManageFaxes?method=fetchFaxStatus|viewFax|CancelFax|ResendFax|SetCompleted`
- All mutating methods (`configure`, `restartFaxScheduler`, `CancelFax`, `ResendFax`,
  `SetCompleted`) reject GET/HEAD with 405 and are registered in the mutator GET-rejection
  contract test. `testConnection` persists nothing but forwards submitted credentials to the
  provider, so it is held to the same POST-only rule.

## Admin Setup Walkthrough (SRFax)
An SRFax account gives you three things: a **login email**, a **password**, and a numeric
**account number**. Only two of them authenticate CARLOS to the SRFax API. On
**Administration > Faxes > Configure Fax** (Fax Gateway Configuration card):

| SRFax value | Configure Fax field | Stored as | Used for |
|---|---|---|---|
| Account number (SRFax portal: Account > Account Details) | **SRFax Account Number** (`faxUser`) | `fax_config.faxUser` | `access_id` on every API call |
| Account password | **SRFax Password** (`faxPassword`) | `fax_config.faxPasswd` (AES) | `access_pwd` on every API call |
| Login email | **Sender / Notification Email** (`senderEmail`) | `fax_config.senderEmail` | `sSenderEmail` on `Queue_Fax` (delivery notifications); never used to authenticate |
| Fax number assigned to the account | **Your SRFax Fax Number** (`faxNumber`) | `fax_config.faxNumber` (10 digits) | `sCallerID` on outbound sends; join key to `faxes.faxline` |
| (your choice) | **Account Name** | `fax_config.accountName` | display label inside CARLOS |

Steps:
1. Enter the account number, password, fax number and sender email. Entering the login email
   in the account-number field is the classic mistake: SRFax rejects it and, before the
   connection test existed, the failure only surfaced minutes later as a scheduler error.
2. Click **Test SRFax connection**. It POSTs the form values as entered (nothing is saved) to
   `/admin/ManageFax?method=testConnection`, which runs a read-only `Get_Fax_Inbox` probe
   through `FaxProviderClient.verifyConnection`. A wrong account number/password comes back
   as `Connection failed: SRFax rejected the account number or password (SRFax API returned
   HTTP 403: Forbidden). Check that the account number is the numeric SRFax account number, not
   your login email.` when SRFax answers with a bare HTTP 401/403 (its usual response to a bad
   `access_id`/`access_pwd` pair); when SRFax instead returns a JSON `Failed` body, the message
   is `Connection failed: SRFax connection test failed: ` followed by SRFax's own reason text
   (for example `Invalid Access Code / Password`). A non-numeric account number (for example the login email) is refused
   before anything is sent to SRFax, on both the test and the save. When the
   password field still shows the mask (`**********`), the stored password for that config is
   tested instead; with no stored config the page asks you to enter the password.
3. Set **Enable Fax Gateway** to Enabled and tick **Poll for incoming faxes**, then click
   **Save Configuration**. Saving an active account auto-starts the scheduler; the Scheduler
   Health block should read **Scheduler Running** with no last error after the next poll.
4. Password field semantics: the page never shows the stored password. Leaving the stars
   unchanged keeps the saved password; typing a new value replaces it on save.

Inbound faxes land in the configured Inbox Queue (document review); outbound sends use the
clinician `_fax` entry points (eForm, consultation, prescription, document). The committed
browser check for this page is `scripts/fax-configure-playwright-checks.js`
(`npm run test:fax-configure-playwright`); the live loopback send/receive harness is
`scripts/e2e/fax/` (see its README).

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
- `faxUser`/`faxPassword` are the SRFax `access_id`/`access_pwd` — the UI labels them
  "SRFax Account Number" / "SRFax Password"; `senderEmail` is the notification address only.
- "Poll incoming faxes" maps to `FaxConfig.download`; the Inbox Queue selects the document
  review queue incoming faxes are filed into.

When provider type is `MIDDLEWARE` (configured only outside the admin UI):
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
