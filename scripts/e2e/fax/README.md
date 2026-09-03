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
    node scripts/e2e/fax/backbone-loopback.js

## Configure Fax page check (no fax pages used)

`scripts/fax-configure-playwright-checks.js` (`npm run test:fax-configure-playwright`)
is the committed browser check for the admin page itself: it walks
Administration > Faxes > Configure Fax, asserts the field guidance (account
number vs. login email, sender/notification email, 10-digit fax number,
password mask), clicks **Test SRFax connection**, saves, and re-reads the row.
It runs with fake defaults anywhere; export the same `SRFAX_*` variables and
`SRFAX_LIVE=true` to assert the live connection test succeeds against a real
development account. Its save step overwrites the single fax account row, so by
default it only saves when no account is configured yet or the stored account is
its own fake test account (otherwise the step is reported as SKIP).
`SRFAX_LIVE=true` saves the real values you supplied; set
`FAX_CONFIG_ALLOW_OVERWRITE=true` to force the save on a shared dev instance.
Screenshots follow the same rule, and additionally require that the values the
run types are the built-in fake ones; they are never captured in live mode or
with real `SRFAX_*` values exported unless `FAX_CONFIG_SCREENSHOTS=always` is
set, so credential-bearing images do not land in shared artifact directories by
accident.

## Loopback

A fax sent to the account's own `SRFAX_FAX_NUMBER` is delivered back to the
same account's inbox, so send and receive can be validated with one account.
The provider `Queue_Fax` returns a job id immediately; the inbound copy arrives
minutes later and the scheduler (60s poll) imports it.

## Database + staging access (for the tests that assert on the DB)

Some tests read and assert against the live database and stage files into the
service-owned document directory. On a hardened deployment those need
privilege; pass a launcher via the environment (nothing is shell-parsed — argv
only):

| Variable             | Meaning                                                           |
|----------------------|------------------------------------------------------------------|
| `MARIADB`            | mariadb launcher, e.g. `sudo mariadb` (default `mariadb`)         |
| `CARLOS_DB_NAME`     | application schema (default `carlos`)                              |
| `CARLOS_DOCUMENT_DIR`| document dir for staged outbound PDFs                             |
| `STAGE_AS`           | launcher to write into a dir the runner does not own, e.g. `sudo -u carlos` |
| `DEDUP_WAIT_MS`      | how long `dedup-no-reimport.js` watches for a re-import (default 150000) |

## The tests

Run them in this order against a freshly provisioned deployment with
`fixtures.sql` loaded:

    sudo mariadb carlos < scripts/e2e/fax/fixtures.sql
    set -a; . /secure/path/srfax.env; set +a
    export MARIADB="sudo mariadb" STAGE_AS="sudo -u carlos"

1. **`backbone-loopback.js`** — the shared send/receive backbone every clinical
   outbound entry point funnels into: injects a WAITING outbound fax to the
   account's own number, confirms the scheduler sends it via the real SRFax
   `Queue_Fax` (WAITING → SENT + provider job id), then confirms the inbound
   copy is downloaded, imported (RECEIVED), and routed to the UNCLAIMED inbox.
   *Needs SRFax credentials.*

2. **`inbox-lifecycle.js`** — picks up an imported inbound fax left UNCLAIMED by
   the backbone test and drives the provider workflow through the real server
   actions, asserting each DB transition: redirected-to-inbox → attached to a
   patient (`documentUpdate`, `demog`) → attached to a provider
   (`documentUpdate`, `flagproviders`) → provider files it (`fileLabAjax`,
   status → `F`). *No SRFax credentials needed.*

3. **`dedup-no-reimport.js`** — the live counterpart to
   `FaxImporterDedupUnitTest`: every inbound fax is stamped with the account's
   fax line (the dedup key), no two inbound faxes share a file name, and the
   inbound count does not grow across more than two scheduler poll cycles (an
   already-held fax is recognised and not re-imported). *No SRFax credentials.*

4. **`prescription-drugref.js`** — proves the DrugRef2 lookup the prescription
   module depends on is live: a common drug returns real reference results, a
   nonsense term returns none, and a second common drug also resolves. The fax
   transmission of the resulting prescription funnels into the same outbound
   backbone proven by `backbone-loopback.js`. *No SRFax credentials.*
