# Fax outbound end-to-end tests (SRFax)

Real end-to-end validation of the SRFax outbound faxing path through a
running CARLOS deployment: it configures the SRFax provider, exercises each
clinical outbound entry point (eForm, consultation, prescription, rich-text
letter, and eDoc/document), sends each fax **to the account's own number as a
loopback**, then waits for the fax to arrive back inbound, be imported by the
scheduler, appear in the fax inbox, and be signed to a provider.

These tests talk to the **live SRFax API** and cost real fax pages, so they are
NOT part of the unit suite and are never run in CI. They exist to validate a
deployment against a real (non-PHI, development) SRFax account.

## Configuration — ALL via environment, nothing is hardcoded

| Variable            | Meaning                                                            |
|---------------------|-------------------------------------------------------------------|
| `BASE_URL`          | e.g. `https://host/carlos`                                         |
| `TEST_USER`         | an admin/provider login (needs `_admin.fax`), e.g. `carlosdoc`    |
| `TEST_PASSWORD`     | that user's password                                              |
| `TEST_PIN`          | that user's 4-digit PIN                                           |
| `CHROME_PATH`       | Playwright Chromium executable path                              |
| `SRFAX_ACCESS_ID`   | the SRFax **account number** (NOT the email — email 403s)         |
| `SRFAX_PASS`        | the SRFax account password                                       |
| `SRFAX_USER`        | the SRFax account email (stored as sender/contact only)          |
| `SRFAX_FAX_NUMBER`  | the account's own fax number (10 digits), used as loopback dest   |
| `ALLOW_NON_LOCAL_BASE_URL` | `true` to permit a non-loopback BASE_URL host             |

Never commit real values. Source them from a file outside the repo:

    set -a; . /secure/path/srfax.env; set +a
    node scripts/e2e/fax/run-all.js

## Loopback

A fax sent to the account's own `SRFAX_FAX_NUMBER` is delivered back to the
same account's inbox, so send and receive can be validated with one account.
The provider `Queue_Fax` returns a job id immediately; the inbound copy arrives
minutes later and the scheduler (60s poll) imports it.
