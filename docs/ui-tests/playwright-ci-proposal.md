# Playwright suite in CI: proposal for a maintainer to commit

Status: **proposal**. `.claude/settings.json` denies Claude write access to `.github/**`, so none of the
YAML below is committed. A maintainer reviews it, writes the three files (two new, one edited) and
decides whether to run the migration leg in section 5. Written on the branch
`claude/playwright-coverage-gaps-2026-08` (from `release/2026.08`) as Task 6 of the
[coverage-gap plan](../superpowers/plans/2026-10-08-playwright-coverage-gaps.md) (which implements section 2.1,
"A CI smoke tier", of the [coverage plan](playwright-coverage-plan-2026.08.md)). Read section 7 before
committing anything: it separates what was run from what was only read.

## Summary

| # | Change | File | Trigger | What it runs | Time | Needs | Blockers |
|---|---|---|---|---|---|---|---|
| 1 | Add the standalone tier | `.github/workflows/script-regressions.yml` (edit: one step and a comment) | every PR and push, as today | `run-playwright-suite.js --tier standalone` | +10 s measured; the job's 10 min limit stays | The Chromium the job already installs. No secrets, images or services | None known |
| 2 | Smoke tier on pull requests | `.github/workflows/playwright-smoke.yml` (new) | `pull_request` on app, script and database paths; also `workflow_call` | `--tier smoke --province ON` against the devcontainer stack | About 25 min estimated (45 min limit); 12 min of it is the planned suite time | `ghcr.io` read for two images, Docker on the runner. No secrets | `appointment-lifecycle` fails today (smoke is 11 of 12); never run on GitHub |
| 3 | Nightly core tier | `.github/workflows/playwright-nightly.yml` (new) | cron `17 6 * * *` and `workflow_dispatch` | `--tier core --province ON`, 8 shards per branch, `develop` and every `release/*`, JUnit per shard and a roll-up | Not measured: the tier's timeouts sum to 45.65 h. 350 min limit per shard | As change 2, and change 2 committed first | The core tier has never run end to end; expect environment noise on the first runs |
| - | Populated-database upgrade leg | `.github/workflows/db-schema-verify.yml` (edit) | the PRs the job already runs on | Previous release's database plus demo data, migrate to HEAD, compare row counts | Not measured | MariaDB container and Flyway CLI the job already has | Sketch only (section 5) |

The `front-door` tier is in none of these. It needs the `.deb`, nginx and ModSecurity, and stays in the deb
runbook ([deb-install-validation.md section 6](deb-install-validation.md#6-run-the-suite)).
Nothing here touches a live external system: the four SRFax scripts under `scripts/e2e/fax/` are registered
as manual entries (section 6) and are in no workflow.

## 1. What exists today

- No Playwright check runs in CI. `script-regressions.yml` runs `npm run test:scripts` (the `scripts/*.test.js`
  meta-tests, about 1.5 min on a developer host) and three isolated Chromium fixtures
  (`rtl-measurement-browser-check.js`, `eform-toolbar-browser-check.js`, `calendar-eform-browser-check.js`).
- The manifest (`scripts/playwright-suite.json`) has 500 entries: `core` 462, `extended` 18, `smoke` 12,
  `front-door` 9, `standalone` 4, `live-external` 4 (a check can sit in two tiers). Six are `manual: true`.
  The smoke tier's worst-case timeouts sum to 3,600 s, a ceiling `playwright-suite-manifest.test.js` enforces.
- `maven-project.yml` already pulls `carlos-tomcat-dev`, mounts the checkout and runs `make install`.
  `container-images.yml` builds and publishes `carlos-tomcat-dev`, `carlos-mariadb-dev` and `carlos-drugref-dev`
  (`:latest` from `develop`, a branch-scoped `<branch>-latest` tag from every push).
- Conventions copied below, not invented: `actions/*` and `docker/*` pinned to the SHAs already used in
  `.github/workflows/`, `permissions: contents: read` plus `packages: read` for image pulls, `ubuntu-latest`
  for container jobs and `ubuntu-24.04` for `script-regressions.yml`, the `workflow_call` shape of
  `maven-project.yml`, the image pull and JDK check of `maven-project.yml`, and the database wait of
  `container-images.yml`.

Measured facts used in the budgets (the run is `maven-project.yml` run 37786996166, a pull request on
2026-10-08; its job and step timestamps are on GitHub):

| Step | Measured |
|---|---|
| Checkout | about 16 s |
| Pull `carlos-tomcat-dev:latest` | about 78 s |
| `make install` with a warm Maven cache (the "Build project (no tests)" step) | 3 min 20 s |
| The `tests` job ("Run tests") | 9 min 12 s |
| The `jspc` job ("Compile JSPs") | 2 min 56 s |

## 2. Change 1: the standalone tier in `script-regressions.yml`

```diff
--- a/.github/workflows/script-regressions.yml
+++ b/.github/workflows/script-regressions.yml
@@ -1,5 +1,5 @@
-# Browser-harness/source-contract tests and an isolated Chromium editor fixture.
-# No VM, running application, database or secrets required.
+# Browser-harness/source-contract tests, isolated Chromium fixtures and the standalone
+# Playwright tier. No VM, running application, database or secrets required.
 # Licensed under the repository's LICENSE.
 name: Script Regressions

@@ -84,3 +84,8 @@
         run: node scripts/eform-toolbar-browser-check.js
       - name: Check calendar initialization in eForms
         run: node scripts/calendar-eform-browser-check.js
+      # The manifest's `standalone` tier: four checks that serve their own pages and need no
+      # Tomcat, database or login (scripts/playwright-suite.json). The Chromium installed above
+      # is the one they use. Measured at about 10 s.
+      - name: Run the standalone Playwright tier
+        run: node scripts/run-playwright-suite.js --tier standalone
```

What it runs: `eform-admin`, `eform-page-exclusion`, `eform-runtime-compat` and `hrm-window`. The manifest
test already requires every `standalone` check to have `assertsDatabase: false`; they serve their own
pages, so they need no Tomcat, database or login.

Measured (inside the `carlos-deb-validation` container, node 22, Playwright from the install's own
Chromium; the runs below are after the Docker restart at about 14:10 UTC):

| Run | Result | Wall time |
|---|---|---|
| `source /root/suite-env.sh`, then `--tier standalone` (twice, 14:18 UTC) | 4 passed, 0 failed, 0 skipped | 10.9 s, 10.9 s |
| Only `CHROME_PATH` exported: no `BASE_URL`, credentials or `MYSQL_*`, the shape of the CI job (14:20 UTC) | 4 passed, 0 failed, 0 skipped | 10.8 s |

Per check: `eform-admin` 1.4 to 1.6 s, `eform-page-exclusion` 1.1 to 1.4 s, `eform-runtime-compat` 1.4 to
1.6 s, `hrm-window` 5.5 to 6.6 s. The worst-case timeouts sum to 1,080 s. The runner's default `BASE_URL` is
the devcontainer's, its build-identity probe fails quickly against nothing and disables itself, and the
target gate accepts the loopback default, so no environment is needed. A run that is not told a browser
fails all four, exit 1 (seen once in the container, which has no Playwright browser of its own;
`hrm-window` reports "Executable doesn't exist"). CI has one because an earlier step installs it.

- Needs: nothing new. Secrets none, images none, services none.
- Time budget: about +10 s on a job whose limit is 10 min. No change to `timeout-minutes`.
- Blockers: none known. This is the lowest-risk change and can be committed first and alone.
- If a standalone check fails in CI, the table the runner prints names it; reproduce with
  `node scripts/run-playwright-suite.js --only <name>`.

## 3. Change 2: `playwright-smoke.yml`

A single file that is both the pull-request gate and the reusable engine of the nightly (section 4).
On a pull request it runs the `smoke` tier; called with inputs it runs any tier, optionally one shard.

```yaml
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
#
# GitHub Workflow: Playwright Suite
#
# Description:
# Boots CARLOS EMR the way the devcontainer does and runs the browser suite against it:
#   - carlos-mariadb-dev  built from THIS tree (it bakes in database/ and the demo SQL),
#                         initialised by populate_db.sh with the Ontario demo dataset
#   - carlos-drugref-dev  published image (the smoke tier's drug-search check needs DrugRef)
#   - carlos-tomcat-dev   published image, the checkout mounted at /workspace, `make install`
#                         builds the WAR and starts Tomcat (exactly what maven-project.yml runs)
# then runs scripts/run-playwright-suite.js inside the Tomcat container, which already carries
# Node.js, the mariadb client and the Playwright-managed Chromium.
#
# Triggers:
# - pull_request: the `smoke` tier (12 checks, 3,600 s worst-case ceiling, ~12 min planned).
#   Start it as a NON-REQUIRED check; make it required after two weeks of green runs.
# - workflow_call: any tier, optionally one shard of it. playwright-nightly.yml calls this
#   with `--tier core` split across shards.
#
# NOT covered: the `front-door` tier. It needs the .deb, nginx and ModSecurity and stays in
# the deb runbook (docs/ui-tests/deb-install-validation.md section 6).
#
# This file was written without being able to run it on GitHub. See
# docs/ui-tests/playwright-ci-proposal.md for what was verified and what was not.

name: Playwright Suite

on:
  pull_request:
    branches:
      - main
      - develop
      - 'release/*'
    paths:
      - 'src/main/webapp/**'
      - 'src/main/resources/**'
      - 'src/main/java/**/*2Action.java'
      - 'src/main/java/**/*Filter*.java'
      - 'src/main/java/**/web/**'
      - 'database/mysql/migration/**'
      - '.devcontainer/**'
      - 'scripts/**'
      - 'package.json'
      - 'package-lock.json'
      - '.github/workflows/playwright-smoke.yml'
  workflow_call:
    inputs:
      ref:
        description: Git ref to test; defaults to the event commit
        required: false
        type: string
        default: ''
      tier:
        description: Suite tier to run (smoke, core, extended)
        required: false
        type: string
        default: smoke
      shard:
        description: This job's shard, 1..shards
        required: false
        type: number
        default: 1
      shards:
        description: Number of shards the tier is split into (1 = run it whole)
        required: false
        type: number
        default: 1
      residue_audit:
        description: Pass --residue-audit to the runner
        required: false
        type: boolean
        default: false
      timeout_minutes:
        description: Job timeout
        required: false
        type: number
        default: 45

permissions:
  contents: read
  packages: read

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ${{ github.repository_owner }}

jobs:
  suite:
    name: Playwright ${{ inputs.tier || 'smoke' }}${{ inputs.shards > 1 && format(' shard {0}/{1}', inputs.shard, inputs.shards) || '' }}
    runs-on: ubuntu-latest
    timeout-minutes: ${{ inputs.timeout_minutes || 45 }}
    # One run per PR/ref/shard. The group names the shard, so the legs of the nightly matrix
    # do not cancel one another. Declared on the job, and the caller (playwright-nightly.yml)
    # declares none: a group shared between a caller and its callee deadlocks.
    concurrency:
      group: playwright-suite-${{ inputs.ref || github.ref }}-${{ inputs.tier || 'smoke' }}-${{ inputs.shard || 1 }}
      cancel-in-progress: ${{ github.event_name == 'pull_request' }}
    steps:
      - name: Checkout code
        uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4.3.1
        with:
          ref: ${{ inputs.ref != '' && inputs.ref || github.sha }}
          persist-credentials: false

      - name: Name this run's artifacts
        id: names
        env:
          # Through env, never interpolated into the script: github.head_ref is branch-name text.
          REF_NAME: ${{ inputs.ref || github.head_ref || github.ref_name }}
          TIER: ${{ inputs.tier || 'smoke' }}
          SHARD: ${{ inputs.shard || 1 }}
          SHARDS: ${{ inputs.shards || 1 }}
        run: |
          slug=$(printf '%s' "$REF_NAME" \
            | tr '[:upper:]/' '[:lower:]-' \
            | sed -E 's/[^a-z0-9_.-]+/-/g; s/^-+//; s/-+$//; s/^$/branch/' \
            | cut -c1-60)
          echo "artifact=playwright-${TIER}-${slug}-${SHARD}of${SHARDS}" >> "$GITHUB_OUTPUT"

      # Read-only restore: maven-project.yml's `build` job owns saving this cache.
      - name: Restore Maven cache
        uses: actions/cache/restore@0057852bfaa89a56745cba8c7296529d2fc39830 # v4.3.0
        id: cache-restore
        continue-on-error: true
        with:
          path: .m2-cache
          key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml', '**/dependencies-lock*.json', 'local_repo/**') }}
          restore-keys: |
            ${{ runner.os }}-maven-

      - name: Prepare Maven cache directory
        if: steps.cache-restore.outputs.cache-hit != 'true'
        run: |
          echo "Cache miss or restore failed, Maven will download dependencies"
          mkdir -p .m2-cache

      - name: Log in to Container Registry
        uses: docker/login-action@c94ce9fb468520275223c153574b00df6fe4bcc9 # v3.7.0
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      # Built from this tree on purpose. Unlike tomcat-dev (a toolchain; the source is
      # mounted), this image COPYs database/ and the demo SQL in, so a PR that adds a
      # migration is only exercised by an image built from that PR. The published :latest
      # would load the base branch's schema under the PR's code.
      - name: Build the mariadb-dev image from this tree
        run: docker build -t carlos-mariadb-dev -f .devcontainer/db/Dockerfile .

      - name: Create the container network
        run: docker network create carlos-network

      # `--network-alias db` is the hostname carlos.properties and drugref2.properties use.
      # populate_db.sh runs on the first start (several minutes); the wait is placed after the
      # image pulls and the container start so the two overlap.
      - name: Start MariaDB
        run: |
          docker run -d --name carlos-mariadb-dev --network carlos-network --network-alias db \
            --env-file .devcontainer/development/config/shared/local.env \
            -e TZ=America/Toronto \
            carlos-mariadb-dev

      # The same pull / JDK check / fallback as maven-project.yml. The published :latest is not
      # keyed to the Dockerfile, so the JDK it carries is compared with pom.xml <release>.
      - name: Try to pull pre-built image from ghcr.io
        id: pull-image
        continue-on-error: true
        env:
          IMAGE: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/carlos-tomcat-dev:latest
        run: |
          echo "Attempting to pull pre-built image from $IMAGE..."
          # Capture stderr so we can distinguish "not found" from other errors
          if docker pull "$IMAGE" 2>pull-image-error.log; then
            docker tag "$IMAGE" carlos-tomcat-dev
            want="$(sed -n 's:.*<release>\([0-9][0-9]*\)</release>.*:\1:p' pom.xml | head -1)"
            got="$(docker run --rm --entrypoint java carlos-tomcat-dev -version 2>&1 \
                   | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
            if [ -n "$want" ] && [ "$got" != "$want" ]; then
              echo "pulled=false" >> "$GITHUB_OUTPUT"
              echo "reason=not-found" >> "$GITHUB_OUTPUT"
              echo "Pre-built image has JDK ${got:-unknown}, this tree needs JDK $want - building locally."
            else
              echo "pulled=true" >> "$GITHUB_OUTPUT"
              echo "reason=success" >> "$GITHUB_OUTPUT"
              echo "Successfully pulled pre-built image (JDK ${got:-unknown})!"
            fi
          else
            echo "pulled=false" >> "$GITHUB_OUTPUT"
            if grep -qiE 'manifest unknown|not found' pull-image-error.log; then
              echo "reason=not-found" >> "$GITHUB_OUTPUT"
              echo "Pre-built image not available (not found in registry), will build locally"
            else
              echo "reason=error" >> "$GITHUB_OUTPUT"
              echo "Image pull failed due to an unexpected error:"
              cat pull-image-error.log
            fi
          fi
          rm -f pull-image-error.log

      - name: Fail on unexpected image pull error
        if: steps.pull-image.outputs.reason == 'error'
        run: |
          echo "::error::Failed to pull pre-built image from ${{ env.REGISTRY }} for a reason other than 'image not found'. Check network connectivity, registry permissions, or image name."
          exit 1

      - name: Set up Docker Buildx
        if: steps.pull-image.outputs.pulled != 'true' && steps.pull-image.outputs.reason == 'not-found'
        uses: docker/setup-buildx-action@8d2750c68a42422c14e847fe6c8ac0403b4cbd6f # v3.12.0

      - name: Cache Docker layers
        if: steps.pull-image.outputs.pulled != 'true' && steps.pull-image.outputs.reason == 'not-found'
        uses: actions/cache@0057852bfaa89a56745cba8c7296529d2fc39830 # v4.3.0
        with:
          path: /tmp/.buildx-cache
          key: ${{ runner.os }}-buildx-${{ hashFiles('.devcontainer/development/**') }}
          restore-keys: |
            ${{ runner.os }}-buildx-

      - name: Build dev container (fallback)
        if: steps.pull-image.outputs.pulled != 'true'
        run: |
          echo "Building container locally..."
          docker buildx build \
            --cache-from=type=local,src=/tmp/.buildx-cache \
            --cache-to=type=local,dest=/tmp/.buildx-cache-new,mode=max \
            --load \
            -t carlos-tomcat-dev \
            .devcontainer/development
          rm -rf /tmp/.buildx-cache
          mv /tmp/.buildx-cache-new /tmp/.buildx-cache

      - name: Pull or build the drugref-dev image
        env:
          IMAGE: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/carlos-drugref-dev:latest
        run: |
          if docker pull "$IMAGE"; then
            docker tag "$IMAGE" carlos-drugref-dev
          else
            echo "::warning::$IMAGE is not published; building drugref-dev locally"
            docker build -t carlos-drugref-dev .devcontainer/drugref
          fi

      # Same flags as the compose service, minus the ports and the named volumes.
      - name: Start the application container
        run: |
          docker run -d --name carlos-tomcat-dev --init --network carlos-network \
            --env-file .devcontainer/development/config/shared/local.env \
            -e TZ=America/Toronto -e LANG=en_US.UTF-8 -e LANGUAGE=en_US:en \
            -v "$GITHUB_WORKSPACE:/workspace" \
            -v "$GITHUB_WORKSPACE/.m2-cache:/root/.m2" \
            -v "$GITHUB_WORKSPACE/.devcontainer/db/db_data:/db-data" \
            -w /workspace \
            carlos-tomcat-dev tail -f /dev/null

      - name: Configure git safe directory
        run: |
          docker exec carlos-tomcat-dev bash -c "
            git config --global --add safe.directory /workspace
          "

      - name: Purge stale local_repo artifacts from Maven cache
        # Same reason as maven-project.yml: a restored cache copy shadows local_repo.
        run: |
          rm -rf .m2-cache/repository/com/github/openosp \
                 .m2-cache/repository/com/github/librepdf

      # populate_db.sh prints this line last (container-images.yml waits for the same one).
      # The provider count guards against a log line left by an earlier, failed init.
      - name: Wait for the demo database
        run: |
          initialized=false
          for i in $(seq 1 150); do
            if docker logs carlos-mariadb-dev 2>&1 | grep -q "Database initialization complete!"; then
              provider_count=$(docker exec -e MYSQL_PWD=password carlos-mariadb-dev \
                mariadb -N -h 127.0.0.1 -u root -e "SELECT COUNT(*) FROM carlos.provider;" 2>/dev/null || echo 0)
              if [ "$provider_count" -gt 0 ]; then
                echo "Demo database ready (providers=$provider_count) after ~$((i * 2))s"
                initialized=true
                break
              fi
            fi
            sleep 2
          done
          if [ "$initialized" != "true" ]; then
            echo "::error::the demo database did not finish initialising within 300s"
            docker logs --tail 100 carlos-mariadb-dev
            exit 1
          fi

      # Started only once the database is up: drugref2's Hibernate context dies at boot
      # against a database that is still initialising (the compose file's depends_on).
      - name: Start DrugRef
        run: |
          docker run -d --name carlos-drugref-dev --network carlos-network --network-alias drugref \
            -e TZ=America/Toronto \
            -e CATALINA_OPTS=-Dhibernate.dialect=org.hibernate.dialect.MariaDBDialect \
            -v "$GITHUB_WORKSPACE/.devcontainer/development/config/shared/volumes/drugref2.properties:/root/drugref2.properties:ro" \
            -v "$GITHUB_WORKSPACE/.devcontainer/drugref/drugref2.xml:/usr/local/tomcat/conf/Catalina/localhost/drugref2.xml:ro" \
            carlos-drugref-dev

      - name: Seed the demo documents
        run: |
          docker exec carlos-tomcat-dev bash -c "
            cd /workspace && sh .devcontainer/development/setup/seed_data.sh
          "

      # Builds the WAR, deploys it to Tomcat and starts Tomcat (server start is its last step).
      - name: Build and start the application
        run: |
          docker exec carlos-tomcat-dev bash -c "
            echo '========================================='
            echo 'Building and starting CARLOS'
            echo '========================================='
            make install
          "

      - name: Wait for the application
        run: |
          for i in $(seq 1 90); do
            code=$(docker exec carlos-tomcat-dev curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
              http://127.0.0.1:8080/carlos/ || true)
            case "$code" in
              200|302) echo "Application answered HTTP $code after ~$((i * 5))s"; exit 0 ;;
            esac
            sleep 5
          done
          echo "::error::the application did not answer at /carlos/ within 450s"
          docker exec carlos-tomcat-dev tail -n 200 /usr/local/tomcat/logs/catalina.out || true
          exit 1

      - name: Wait for DrugRef
        run: |
          for i in $(seq 1 60); do
            if docker exec carlos-drugref-dev curl -fs -o /dev/null http://localhost:8080/drugref2/; then
              echo "DrugRef is up"
              exit 0
            fi
            sleep 5
          done
          echo "::error::DrugRef did not answer within 300s"
          docker logs --tail 100 carlos-drugref-dev
          exit 1

      # The checks `require('playwright')` from the repository, so install it where they run.
      - name: Install script dependencies
        run: |
          docker exec carlos-tomcat-dev bash -c "
            cd /workspace && npm ci --ignore-scripts --no-audit --no-fund
          "

      # Every smoke check's defaults are already the devcontainer's (carlosdoc / carlos2026 / 2026,
      # patient 1, provider 999998, BASE_URL http://127.0.0.1:8080/carlos). What the stack needs
      # said explicitly: the DB host is `db` (non-loopback, so the disposable-database opt-in),
      # the packaged Chromium, the login check's password hash, and artifact paths under the
      # mounted workspace so upload-artifact can reach them. The dataset-fact and store-layout
      # lines are the packaged install's /root/suite-env.sh with the dev paths; they matter to
      # the core tier, not to smoke.
      - name: Write the suite environment
        run: |
          mkdir -p "$GITHUB_WORKSPACE/playwright-artifacts/screenshots" "$GITHUB_WORKSPACE/playwright-artifacts/files"
          cat > "$RUNNER_TEMP/suite-env.sh" <<'EOF'
          export BASE_URL=http://127.0.0.1:8080/carlos
          export CHROME_PATH=/root/.cache/ms-playwright/chromium-1223/chrome-linux64/chrome
          export TEST_USER=carlosdoc TEST_PASSWORD=carlos2026 TEST_PIN=2026
          export MYSQL_HOST=db MYSQL_USER=root MYSQL_PASSWORD=password MYSQL_DATABASE=carlos
          export ALLOW_NON_LOCAL_MYSQL_HOST=true
          export TEST_PASSWORD_HASH="$(MYSQL_PWD="$MARIADB_ROOT_PASSWORD" mariadb -h db -u root carlos -Nse "SELECT password FROM security WHERE user_name='carlosdoc' LIMIT 1")"
          export SCREENSHOT_DIR=/workspace/playwright-artifacts/screenshots ARTIFACT_DIR=/workspace/playwright-artifacts/files
          # The stack is private, disposable and runs one check at a time.
          export CARLOS_DISPOSABLE_VM=true EXCLUSIVE=1
          # Dataset facts: the same demo data as the packaged install.
          export PRESCRIPTION_SCRIPT_ID=45 PRESCRIPTION_DEMOGRAPHIC_NO=1
          export CONSULT_DEMO_NO=1 CONSULT_SERVICE_ID=1 CONSULT_REQUEST_ID=1 CONSULT_STAMP_PROVIDER_NO=999998
          export PATIENT_LIST_FIXTURE_PROFILE=local-seed-obec-report-v1
          export BILLING_APPOINTMENT_NO=11 BILLING_DEMOGRAPHIC_NO=1 BILLING_PROVIDER_NO=999998
          export BILLING_APPOINTMENT_DATE=2024-04-16 BILLING_START_TIME=12:00:00
          export RTL_TEMPLATE_NAME=MissedAppointment.rtl
          export RX_FAX_PROVIDER_NO=999998 RX_FAX_DEMOGRAPHIC_NO=1
          export APPOINTMENT_PROVIDER_NO=999998 APPOINTMENT_DEMOGRAPHIC_NO=1 APPOINTMENT_DAYS_AHEAD=400
          export MESSENGER_PROVIDER_NO=999998 LAB_PROVIDER_NO=999998
          export MEASUREMENT_DEMOGRAPHIC_NO=1 MEASUREMENT_GROUP=Anthropometrics MEASUREMENT_TYPE=WT
          export NEXT_APPT_DEMOGRAPHIC_NO=1 NEXT_APPT_PROVIDER_NO=999998
          export FIRST_NATIONS_DEMOGRAPHIC_NO=1 NOTE_DEMOGRAPHIC_NO=2 PREVENTION_BRAND_QUERY=Tdap
          export BILLING_SUBMIT_DATE=2024-05-06 BILLING_OHIP_CODE=A007A BILLING_BONUS_CODE=Q040A
          export GROUP_DISK_SERVICE_DATE=2003-02-03 GROUP_DISK_PAID_CODE=A007A
          export BILLING_CODE_EXISTING=A007A BILLING_CODE_NEW=X987Z
          # The devcontainer's document store (the packaged install uses /var/lib/carlos-emr/...).
          export DOCUMENT_DIR=/var/lib/CarlosDocument/carlos/document
          export EDOC_NAV_DOCUMENT_STORE=$DOCUMENT_DIR LAB_UPLOAD_DOCUMENT_STORE=$DOCUMENT_DIR
          export LETTER_DOCUMENT_DIR=$DOCUMENT_DIR BOUNDARY_DOCUMENT_DIR=$DOCUMENT_DIR
          export FORWARDING_SCOPE_DOCUMENT_STORE=$DOCUMENT_DIR RA_DOCUMENT_DIR=$DOCUMENT_DIR
          export RX_FAX_DOCUMENT_DIR=$DOCUMENT_DIR
          export INCOMINGDOCUMENT_DIR=/var/lib/CarlosDocument/carlos/incomingdocs
          export OHIP_DISK_DIR=/var/lib/CarlosDocument/carlos/billing/download
          export INVOICE_DIR=/var/lib/CarlosDocument/carlos/billing/invoices
          export ONEDT_INBOX=/var/lib/CarlosDocument/carlos/onEDTDocs/inbox RA_EDT_INBOX=/var/lib/CarlosDocument/carlos/onEDTDocs/inbox
          export EFORM_IMAGE_DIR=/var/lib/CarlosDocument/carlos/eform/images
          EOF
          docker cp "$RUNNER_TEMP/suite-env.sh" carlos-tomcat-dev:/root/suite-env.sh

      - name: Run the suite
        env:
          TIER: ${{ inputs.tier || 'smoke' }}
          SHARD: ${{ inputs.shard || 1 }}
          SHARDS: ${{ inputs.shards || 1 }}
          RESIDUE_AUDIT: ${{ inputs.residue_audit && 'true' || 'false' }}
        run: |
          docker exec -w /workspace -e TIER -e SHARD -e SHARDS -e RESIDUE_AUDIT carlos-tomcat-dev bash -c '
            set -euo pipefail
            source /root/suite-env.sh
            test -x "$CHROME_PATH" || { echo "::error::no Chromium at $CHROME_PATH"; exit 1; }
            # The harness execs `mysql`; MariaDB 11 clients no longer ship that name.
            command -v mysql >/dev/null || ln -s "$(command -v mariadb)" /usr/local/bin/mysql
            junit=/workspace/playwright-artifacts/junit.xml
            # The dev stack is the Ontario schema, so BC-only checks are out of scope.
            select=(--tier "$TIER" --province ON)
            if [ "$SHARDS" -gt 1 ]; then
              # Round-robin the tier by name. --list also prints manual checks (they run only by
              # name), so drop them: a shard must never start one.
              mapfile -t names < <(node scripts/run-playwright-suite.js "${select[@]}" --list \
                | grep -v "(manual:" | awk "{print \$1}" | LC_ALL=C sort \
                | awk -v n="$SHARDS" -v i="$SHARD" "NR % n == i % n")
              [ "${#names[@]}" -gt 0 ] || { echo "::error::shard $SHARD/$SHARDS selected no checks"; exit 1; }
              select=(--province ON)
              for name in "${names[@]}"; do select+=(--only "$name"); done
              echo "shard $SHARD/$SHARDS: ${#names[@]} checks"
            fi
            args=("${select[@]}" --junit "$junit")
            if [ "$RESIDUE_AUDIT" = "true" ]; then
              # Older branches have a runner without the audit; run them unaudited, loudly.
              if grep -q -- "--residue-audit" scripts/run-playwright-suite.js; then
                args+=(--residue-audit)
              else
                echo "::warning::the runner on this ref has no --residue-audit; running without it"
              fi
            fi
            node scripts/run-playwright-suite.js "${args[@]}"
          '

      - name: Collect screenshots and the Tomcat log
        if: always()
        run: |
          mkdir -p playwright-artifacts/screenshots playwright-artifacts/logs
          docker exec carlos-tomcat-dev sh -c 'cp /tmp/*.png /workspace/playwright-artifacts/screenshots/ 2>/dev/null || true'
          docker exec carlos-tomcat-dev tail -n 5000 /usr/local/tomcat/logs/catalina.out \
            > playwright-artifacts/logs/catalina.out.tail 2>/dev/null || true
          docker logs --tail 500 carlos-mariadb-dev > playwright-artifacts/logs/mariadb.log 2>&1 || true

      - name: Upload JUnit, screenshots and logs
        if: always()
        uses: actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4.6.2
        with:
          name: ${{ steps.names.outputs.artifact }}
          path: playwright-artifacts/
          if-no-files-found: warn
          retention-days: 14
```

### How it boots the application

| Order | Step | Why |
|---|---|---|
| 1 | Checkout, restore the Maven cache read-only | `maven-project.yml`'s `build` job owns saving it |
| 2 | Build `carlos-mariadb-dev` from the PR's tree | The image bakes in `database/` and the demo SQL, so the published `:latest` would load the base branch's schema under the PR's code. `tomcat-dev` is only a toolchain (the source is mounted), so that one is pulled |
| 3 | Start MariaDB on a `carlos-network` with alias `db` | `carlos.properties` and `drugref2.properties` connect to `db:3306`. `populate_db.sh` runs on first start |
| 4 | Pull `tomcat-dev` (JDK-checked against `pom.xml`, fallback build) and `drugref-dev` | Same logic as `maven-project.yml`; this overlaps the database initialising |
| 5 | Start the Tomcat container idle, wait for the demo database | `Database initialization complete!` plus a non-empty `carlos.provider`, as `container-images.yml` does |
| 6 | Start DrugRef | After the database: drugref2's Hibernate context does not survive a database that is still starting (the compose file's `depends_on`) |
| 7 | `seed_data.sh`, `make install`, wait for `/carlos/` | The devcontainer's post-create step, then the same build `maven-project.yml` runs, which ends by starting Tomcat |
| 8 | `npm ci`, write `/root/suite-env.sh`, run the suite in the container | Node, the mariadb client and the Playwright Chromium are already in the image |
| 9 | Upload `playwright-artifacts/` (JUnit, screenshots, Tomcat and MariaDB log tails) | `if: always()`, 14 days |

The suite runs inside the Tomcat container because the harness needs `mysql` and `db` resolves only on the
Docker network. Every smoke check's defaults are already the devcontainer's (`carlosdoc`, `carlos2026`,
PIN `2026`, patient 1, provider 999998, `http://127.0.0.1:8080/carlos`); the environment step states only
what differs: `MYSQL_HOST=db` with the disposable-database opt-in, the packaged Chromium path, the `login`
check's `TEST_PASSWORD_HASH` (read back from the seeded row), and artifact paths under the mounted
workspace. The dataset-fact and document-store lines are `suite-env.sh` from the packaged install with the
devcontainer's paths; they matter to the core tier and are inert for smoke.

### Time budget

| Phase | Time | Basis |
|---|---|---|
| Checkout and Maven cache | about 20 s | Measured (run above) |
| Pull `tomcat-dev` | about 78 s | Measured |
| Build `mariadb-dev`, pull `drugref-dev` | not measured | Two `COPY` layers on `mariadb:11.8.8`; the build context is the repository |
| Database initialisation | not measured | Overlaps the pulls. `container-images.yml` waits up to 180 s for it; this file allows 300 s |
| `make install` | about 3 min 20 s | Measured, warm Maven cache |
| Application start | not measured | The wait allows 450 s |
| `npm ci` | not measured | |
| The smoke tier | 12 min planned | Coverage plan 2.1; 60 min is the ceiling the manifest test enforces |
| **Total** | **about 25 min, an estimate** | `timeout-minutes: 45` |

### Needs

- Secrets: none. The job uses `GITHUB_TOKEN` with `contents: read` and `packages: read` only.
- Images: `ghcr.io/carlos-emr/carlos-tomcat-dev:latest` and `carlos-drugref-dev:latest`, both published by
  `container-images.yml`. A missing image falls back to a local build, which is slow for `tomcat-dev`
  (the buildx layer cache of `maven-project.yml` is reused) and, for DrugRef, builds from a git repository.
- Services: none beyond the three containers the job starts. A fork pull request runs with a read-only
  token and no secrets, which is all the job asks for.
- Branch protection: add the check to the required list only after two weeks of green runs
  (coverage plan 2.1). Its name is `Playwright smoke` in the workflow `Playwright Suite`.

### Known blockers and unknowns

1. **Smoke is 11 of 12 today.** The baseline on a fresh Ontario install (2026-10-08 about 12:40 UTC) had
   `appointment-lifecycle` failing at the add-appointment `#searchBtn` click, with navigation never
   finishing. It is not triaged. This is the figure recorded when the plan started; it was not re-measured for
   this document. [deb-install-validation.md](deb-install-validation.md) records smoke 12 of 12 earlier on the same
   kind of install, so the failure is new or specific to the current build. A gate that is red on every run
   is not a gate. Ship it non-required, and until the defect has a row in
   [app-findings-log.md](app-findings-log.md) either fix it or give the manifest entry an `expectedFailure`
   (the runner then reports `known-fail` and exits 0). Do not skip the check silently.
2. **The dev stack is not the packaged install.** Document paths, the log location and several fixtures
   differ (`/var/lib/CarlosDocument/...` against `/var/lib/carlos-emr/CarlosDocument/...`). Smoke needs
   almost none of that; the core tier does (section 4).
3. **`drug-search` and `eform-render` need DrugRef and the eForm renderer's Chromium.** A build that leaves
   them out fails exactly those two ([coverage-bc-billing-simulation-3950.md](coverage-bc-billing-simulation-3950.md)).
   The workflow therefore starts DrugRef, and the application's `eform_pdf_browser_chromium_path` and the
   suite's `CHROME_PATH` both point at the image's `chromium-1223` (Playwright 1.60.0). Raising the Playwright
   version in `.devcontainer/development/Dockerfile` means changing both, as
   `.devcontainer/PLAYWRIGHT_SETUP.md` says.
4. **Not run on GitHub.** See section 7 for the list of things only a first run can settle.
5. **Front door.** `EXPECT_FRONT_DOOR` is unset, so the WAF-sensitive checks prove nothing here; that is the
   front-door tier's job.

## 4. Change 3: `playwright-nightly.yml`

```yaml
# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
#
# GitHub Workflow: Playwright Nightly
#
# Description:
# Runs the `core` tier of the browser suite every night against develop and every
# release/* branch, each branch split into shards that run in parallel. The boot-and-run
# sequence is playwright-smoke.yml's (called as a reusable workflow), so the two cannot
# drift. Each shard uploads its JUnit file; the last job rolls them up.
#
# Why shards: the core tier is 462 checks whose timeouts sum to 165,180 s (45.9 h) and
# whose real wall time has never been measured end to end, so it cannot be assumed to fit
# one 6 h job. Round-robin by name; raise `shards` if a shard runs long.
#
# A scheduled workflow runs from the DEFAULT branch's copy of this file, so the matrix
# below checks out each branch itself. `uses: ./...` resolves playwright-smoke.yml from
# that same copy; each branch's own scripts/ are what run.
#
# Requires playwright-smoke.yml to be committed first.

name: Playwright Nightly

on:
  schedule:
    - cron: '17 6 * * *'  # 06:17 UTC, 02:17 in Toronto
  workflow_dispatch:
    inputs:
      tier:
        description: 'Suite tier'
        required: false
        default: core
        type: string
      shards:
        description: 'Shards per branch'
        required: false
        default: '8'
        type: string
      branches:
        description: 'Space-separated branches (empty = develop and every release/*)'
        required: false
        default: ''
        type: string

permissions:
  contents: read
  packages: read

jobs:
  plan:
    name: Plan the matrix
    runs-on: ubuntu-latest
    permissions:
      contents: read
    outputs:
      matrix: ${{ steps.plan.outputs.matrix }}
    steps:
      - name: Build the branch x shard matrix
        id: plan
        env:
          GH_TOKEN: ${{ github.token }}
          # Through env, never interpolated into the script.
          INPUT_BRANCHES: ${{ inputs.branches }}
          INPUT_SHARDS: ${{ inputs.shards }}
        run: |
          set -euo pipefail
          shards="${INPUT_SHARDS:-8}"
          case "$shards" in
            ''|*[!0-9]*|0) echo "::error::shards must be a positive integer"; exit 1 ;;
          esac
          if [ -n "${INPUT_BRANCHES:-}" ]; then
            branches="$INPUT_BRANCHES"
          else
            releases=$(gh api --paginate "repos/${GITHUB_REPOSITORY}/git/matching-refs/heads/release/" \
              --jq '.[].ref | sub("^refs/heads/"; "")' | sort -V)
            branches="develop $(echo $releases)"
          fi
          for branch in $branches; do
            case "$branch" in
              *[!A-Za-z0-9._/-]*|'') echo "::error::unsafe branch name: $branch"; exit 1 ;;
            esac
          done
          matrix=$(jq -cn --arg branches "$branches" --argjson shards "$shards" '
            { include: [ ($branches | split(" ") | map(select(length > 0)))[] as $ref
                         | range(1; $shards + 1) as $shard
                         | { ref: $ref, shard: $shard, shards: $shards } ] }')
          echo "matrix=$matrix" >> "$GITHUB_OUTPUT"
          echo "Branches: $branches ($shards shards each)"

  suite:
    name: ${{ matrix.ref }} shard ${{ matrix.shard }}/${{ matrix.shards }}
    needs: plan
    strategy:
      fail-fast: false
      matrix: ${{ fromJSON(needs.plan.outputs.matrix) }}
    permissions:
      contents: read
      packages: read
    uses: ./.github/workflows/playwright-smoke.yml
    with:
      ref: ${{ matrix.ref }}
      tier: ${{ inputs.tier || 'core' }}
      shard: ${{ matrix.shard }}
      shards: ${{ matrix.shards }}
      residue_audit: true
      # 6 h is the hosted-runner ceiling; leave room for the boot.
      timeout_minutes: 350

  report:
    name: Roll up the JUnit files
    needs: suite
    if: always()
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - name: Download every shard's artifacts
        uses: actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093 # v4.3.0
        with:
          pattern: playwright-*
          path: shards

      - name: Summarise
        run: |
          python3 - <<'PY' >> "$GITHUB_STEP_SUMMARY"
          import glob, xml.etree.ElementTree as ET
          rows, tests, failed, skipped = [], 0, 0, 0
          for path in sorted(glob.glob("shards/*/junit.xml")):
              suite = ET.parse(path).getroot().find("testsuite")
              t, f, s = (int(suite.get(k, 0)) for k in ("tests", "failures", "skipped"))
              tests, failed, skipped = tests + t, failed + f, skipped + s
              rows.append((path.split("/")[1], t, f, s))
              for case in suite.findall("testcase"):
                  failure = case.find("failure")
                  if failure is not None:
                      rows.append(("  FAIL " + case.get("name"), "", "", failure.get("message", "")[:120]))
          print("## Playwright nightly")
          print(f"{tests} checks, {failed} failed, {skipped} skipped, {len(glob.glob('shards/*/junit.xml'))} shards reported")
          print("")
          print("| shard / check | tests | failed | skipped / message |")
          print("|---|---|---|---|")
          for row in rows:
              print("| " + " | ".join(str(c).replace("|", "\\|") for c in row) + " |")
          PY
```

### Why it is built this way

- **Branches.** A scheduled workflow runs the default branch's copy of the file, so the matrix checks out
  each branch itself. `plan` lists `develop` plus every `release/*` head through the matching-refs API and
  rejects names outside `[A-Za-z0-9._/-]`. Today that is `develop` and `release/2026.08`
  ([release-process.md](../release-process.md) names no other maintenance line). `workflow_dispatch` takes an
  explicit branch list.
- **Shards.** The core tier is 462 checks (459 for Ontario) whose per-check timeouts sum to 164,340 s
  (45.65 h), median 300 s. It cannot be assumed to fit one 6 h job, and its real wall time has never been
  measured end to end. The runner has no shard flag, so each job lists the tier with `--list`, drops manual
  entries (`--list` prints them, and they must run only by name), sorts the names and keeps every n-th one.
  Run against the real manifest, 8 shards hold 58, 58, 58, 57, 57, 57, 57, 57 checks, with no overlap and a
  union equal to the full list. The worst-case timeout sum per shard is 19,560 to 23,640 s (5.4 to 6.6 h), so
  the 350 min job limit, not the per-check timeouts, is what bounds a shard. Typical wall time is a guess:
  the smoke budget is about a minute a check, which puts a 58-check shard near an hour plus about 12 min of
  boot. Replace the guess with the first `workflow_dispatch` run's numbers and move `shards` accordingly.
- **Residue audit.** Each shard passes `--residue-audit`, so a check that leaves the shared install changed
  fails its shard (Task 3). `origin/release/2026.08` does not have the option until the coverage-gap pull
  request merges, so the workflow checks the checked-out runner for it and runs unaudited with a warning
  when it is missing.
- **Known failures stay green.** The runner exits 1 only for `FAIL` and `failed-elsewhere`.
  `known-fail` and `unexpected-pass` are reported and do not fail the job.
- **JUnit.** Every shard uploads `junit.xml` inside its artifact `playwright-core-<branch>-<n>of8`; the
  `report` job downloads them all and writes totals and each failure to the run summary. The upload does not
  depend on the runner's JUnit properties, so the `browserVersion` property Task 4 adds changes nothing.
- **Dependency.** `uses: ./.github/workflows/playwright-smoke.yml` needs change 2 committed first. The
  caller declares no `concurrency` group; the callee's job-level group includes the ref and shard, and a
  group shared by caller and callee deadlocks.

### Time budget

Per shard: about 12 min of boot (the section 3 table) plus its checks. Jobs per night: (1 + number of
`release/*` branches) times 8, so 16 shard jobs today, plus `plan` and `report`. The job limit is 350 min.
No total can be quoted honestly before a measured run.

### Needs

Secrets none; images and services as change 2. Hosted-runner time: 16 jobs a night; at one to two hours each (a guess until a run is
measured) that is 16 to 32 runner-hours. If that is too much, lower the branch list with the `branches` input or raise `shards` and run
fewer branches.

### Known blockers and unknowns

1. **The core tier has never run end to end** on any stack. The first runs are calibration: expect
   failures that come from the environment, not the application.
2. **Environment coverage.** The core tier names 258 environment variables. The workflow exports the
   dataset facts and document-store paths of the packaged install's `suite-env.sh`, translated to the
   devcontainer's layout. Checks whose variables are missing either `SKIP` (exit 2, not a failure) or
   fail on a precondition; the journal-reading checks (`CARLOS_LOG_JOURNAL_UNIT`) have no journal in a
   container. Extend the "Write the suite environment" step from what the first runs report; do not edit
   the checks.
3. **Exclusive checks.** 15 manifest entries say in `fixtures` that they must not run concurrently with other
   checks (live wrapper: `EXCLUSIVE=1`), and `admin-role-management` and `session-heartbeat-timeout` refuse to
   run without `EXCLUSIVE=1`. Each job owns a private stack and runs one check at a time, so the workflow sets
   `EXCLUSIVE=1` and `CARLOS_DISPOSABLE_VM=true`.
4. **Older release branches.** The reusable workflow comes from `develop`'s copy; the scripts that run are
   each branch's own. `origin/release/2026.08` already has the `standalone` tier, `--province` and `--junit`
   (checked), but not `--residue-audit` or `expectedFailure`, which arrive with the coverage-gap pull request.
   A branch whose runner predates `--province` would fail with "Unknown argument".
5. **Same as change 2:** `appointment-lifecycle`, and nothing has run on GitHub.

## 5. Populated-database migration leg for `db-schema-verify.yml`

Task 17 of the plan delegates this to the proposal. Status: **a sketch for the maintainer**, not run
anywhere. `db-schema-verify.yml` today proves two things: a fresh `flyway migrate` produces a populated schema,
and a genesis-loaded database (the adoption path) migrates forward. Neither starts from a database that
already holds a clinic's rows, which is what an upgrade is.

The leg, per province, on the pull requests the job already runs on (its `paths` filter covers
`database/mysql/**`, `.devcontainer/**` and this workflow):

1. Take the newest tag the commit descends from (`git describe --tags --abbrev=0 --match '[0-9]*' HEAD`;
   `2026.08.0-alpha18` in this checkout) and check out its tree beside the PR's.
2. Build that release's database with that release's migrations, then load that release's demo dataset
   through that release's `scripts/build-demo-additive.sh`, the transform `carlos-ctl demo-data` uses.
   Checked here: the script exists from tag `2026.08.0-alpha9` onward, and run from the `2026.08.0-alpha18`
   tree it writes a 32.7 MB Ontario artifact in 0.3 s. That tree has 28 common migrations against 35 at
   this commit, so the leg applies at least seven forward migrations.
3. Insert one `FAKE-UPGRADE-SENTINEL` patient, so survival is checked on a row the job wrote.
4. Count every base table, `flyway migrate` and `flyway validate` with this commit's files, count again.
5. Fail if a table disappeared or shrank unless `scripts/migration/upgrade-row-count-exceptions.txt` names it
   (the maintainers create that file; one table per line, with the migration that justifies it), if
   `demographic`, `provider`, `security` or `appointment` changed at all, if the sentinel is gone, or if no
   migration was applied on top of the tag.

```yaml
# Hunk 1 of 2: the job needs the tags and the history behind them.
      - uses: actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2
        with:
          persist-credentials: false
          fetch-depth: 0   # git describe needs the tags, and the previous tag's tree

# Hunk 2 of 2: a new step in `flyway-baseline`, after "Migrate the V1 baseline and smoke-check
# the result" (it reuses that step's Flyway CLI and MariaDB container).
      # SKETCH. Not run anywhere. The fresh-install and adoption legs above prove an EMPTY (or
      # genesis-loaded) database migrates; this one proves a database that already holds a
      # clinic's rows survives the migrations added since the last published release.
      - name: Upgrade a populated database from the previous published release
        # Catches the class behind #3856: a migration numbered at or below the high-water mark
        # of a published release is never applied to a database already past it, and fails
        # `flyway validate` there (database/mysql/migration/README.md, "never number a new
        # migration at or below the global high-water mark").
        env:
          MYSQL_PWD: password
          FLYWAY_PASSWORD: password
          PROV: ${{ matrix.province }}
        run: |
          set -euo pipefail
          MYSQL="mariadb -h 127.0.0.1 -uroot"
          DB="carlos_${PROV}_upgrade"
          JDBC="jdbc:mariadb://127.0.0.1:3306/${DB}?allowPublicKeyRetrieval=true&useSSL=false"
          NEW=database/mysql/migration

          # 1. The newest published release this commit descends from, checked out beside it.
          prev=$(git describe --tags --abbrev=0 --match '[0-9]*' HEAD)
          echo "Upgrading ${PROV} from ${prev}"
          git worktree add --detach "$RUNNER_TEMP/prev" "refs/tags/${prev}"
          OLD="$RUNNER_TEMP/prev/database/mysql/migration"

          # 2. Build that release's database with ITS migrations, then fill it with ITS demo
          #    dataset through the same additive transform `carlos-ctl demo-data` ships.
          #    scripts/build-demo-additive.sh exists from tag 2026.08.0-alpha9 onward.
          $MYSQL -e "CREATE DATABASE ${DB};"
          timeout 30m flyway -url="$JDBC" -user=root \
            -locations="filesystem:${OLD}/common,filesystem:${OLD}/${PROV}" \
            -baselineOnMigrate=true migrate
          bash "$RUNNER_TEMP/prev/scripts/build-demo-additive.sh" \
            "$RUNNER_TEMP/prev/.devcontainer/db/scripts/development.sql" "$PROV" "$RUNNER_TEMP/demo-additive.sql"
          $MYSQL "${DB}" < "$RUNNER_TEMP/demo-additive.sql"

          # 3. A sentinel patient, so "the rows survived" is checked on a row this job wrote.
          $MYSQL "${DB}" -e "INSERT INTO demographic (last_name, first_name, sex, provider_no, roster_status, patient_status, hin, year_of_birth, month_of_birth, date_of_birth, date_joined, lastUpdateDate) VALUES ('FAKE-UPGRADE-SENTINEL', 'FAKE-Ada', 'F', '999998', 'RO', 'AC', '', '1980', '01', '01', CURDATE(), NOW())"

          # 4. Exact row count of every base table, before and after.
          snapshot() {
            $MYSQL -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='${DB}' AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history' ORDER BY table_name" \
              | while read -r t; do printf '%s\t%s\n' "$t" "$($MYSQL -N "${DB}" -e "SELECT COUNT(*) FROM \`$t\`")"; done > "$1"
          }
          snapshot "$RUNNER_TEMP/rows-before.tsv"
          history_before=$($MYSQL -N "${DB}" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1")

          # 5. Migrate to this commit exactly as production does, then validate.
          timeout 30m flyway -url="$JDBC" -user=root \
            -locations="filesystem:${NEW}/common,filesystem:${NEW}/${PROV}" migrate
          timeout 10m flyway -url="$JDBC" -user=root \
            -locations="filesystem:${NEW}/common,filesystem:${NEW}/${PROV}" validate
          snapshot "$RUNNER_TEMP/rows-after.tsv"

          # 6. Assert. A migration may add rows and tables. It may remove or change a table's
          #    rows only if the table is named, with a reason, in the exceptions file.
          python3 - "$RUNNER_TEMP/rows-before.tsv" "$RUNNER_TEMP/rows-after.tsv" \
              scripts/migration/upgrade-row-count-exceptions.txt <<'PY'
          import sys
          def read(path):
              return {l.split("\t")[0]: int(l.split("\t")[1]) for l in open(path) if l.strip()}
          before, after = read(sys.argv[1]), read(sys.argv[2])
          allowed = {l.split("#")[0].strip() for l in open(sys.argv[3]) if l.split("#")[0].strip()}
          problems = []
          for table, count in sorted(before.items()):
              if table in allowed:
                  continue
              if table not in after:
                  problems.append(f"{table}: table dropped (was {count} rows)")
              elif after[table] < count:
                  problems.append(f"{table}: {count} -> {after[table]} rows")
          # A migration never deletes patients, providers or logins from a clinic.
          for table in ("demographic", "provider", "security", "appointment"):
              if before.get(table) != after.get(table):
                  problems.append(f"{table}: {before.get(table)} -> {after.get(table)}, expected unchanged")
          if before.get("demographic", 0) < 2:
              problems.append("the demo dataset did not load (demographic has fewer than 2 rows)")
          print(f"{len(before)} tables compared, {len(after) - len(before)} added by the migrations")
          if problems:
              print("::error::upgrade lost data:\n  " + "\n  ".join(problems))
              sys.exit(1)
          PY
          sentinel=$($MYSQL -N "${DB}" -e "SELECT COUNT(*) FROM demographic WHERE last_name='FAKE-UPGRADE-SENTINEL' AND first_name='FAKE-Ada'")
          [ "${sentinel}" = "1" ] || { echo "::error::the sentinel patient did not survive the upgrade"; exit 1; }
          history_after=$($MYSQL -N "${DB}" -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1")
          [ "${history_after}" -gt "${history_before}" ] || { echo "::error::no migration was applied on top of ${prev}"; exit 1; }
          echo "Upgrade verified for ${PROV}: ${prev} -> HEAD, $((history_after - history_before)) migration(s) applied"
```

Open points for the maintainer:

- **What it catches.** A migration numbered at or below the highest version a published release has already
  applied. Flyway runs without `outOfOrder`, so on an upgraded database it is never applied and `validate`
  fails; this is the incident class of #3856 (a `develop` migration left at V1.0.43 against the release
  line's own V1.0.43), and `database/mysql/migration/README.md` forbids it. The existing fresh-install and
  adoption legs cannot see it, because neither has a database that is past the number.
- **Which tag.** `git describe` takes the nearest tag reachable from the checked-out commit, so on a pull
  request to `develop` it is the newest release line that has been forward-merged into `develop`. A release
  that has not been forward-merged yet is not reachable and is not tested; the forward merge is where
  [release-process.md](../release-process.md) renumbers the unreleased side, never a published file.
- **Cost.** One more Flyway run on a database with demo data, in each province. The job limit is 60 min;
  the leg has not been timed.
- **Tags.** The tag's migration set must never change; the release policy already forbids editing a Flyway
  migration present in a published tag, which is what makes the previous tag a stable starting point.
- **Packaged route.** `deb-docker-validation.sh upgrade <from>` (Task 17) covers the `.deb` and `carlos-ctl`
  path on a real install. This leg is the cheap database-only half and does not replace it.
- **BC.** The matrix runs both provinces, as the existing job does; the BC leg has not been tried (BC work is
  out of scope for this plan, issue #4439).
- The comparison script was run against synthetic before/after tables here and flags a dropped table, a
  shrunk table and a changed `demographic` count, and accepts an excepted table and added tables.

## 6. Manual `live-external` entries

Four scripts under `scripts/e2e/fax/` were not manifest entries. They are now, as `manual: true` entries in
the `live-external` tier (an existing tier in `playwright-suite-manifest.test.js`; no new tier). `manual`
means the runner selects them only when named with `--only`: never by `--tier`, never in a default run.
`--tier live-external` matches nothing (the runner reports "No checks matched the selection"), and no workflow
in this proposal names them. `annotate-document-playwright-checks.js`, the fifth script in that directory,
was already a `core` entry and is unchanged.

| Entry | Script | Talks to SRFax | `assertsDatabase` | Timeout | npm alias |
|---|---|---|---|---|---|
| `fax-backbone-loopback` | `backbone-loopback.js` | Yes: sends a real fax to the account's own number | true | 900 s | `test:fax-backbone-loopback-playwright` |
| `fax-inbox-lifecycle` | `inbox-lifecycle.js` | No, but files the inbound fax a live account delivered | true | 300 s | `test:fax-inbox-lifecycle-playwright` |
| `fax-dedup-no-reimport` | `dedup-no-reimport.js` | No, but watches the real scheduler for 150 s or more | true | 420 s | `test:fax-dedup-no-reimport-playwright` |
| `fax-prescription-drugref` | `prescription-drugref.js` | No: only DrugRef | true | 180 s | `test:fax-prescription-drugref-playwright` |

All four read MariaDB through the `MARIADB` launcher (default `mariadb` as root over the unix socket), not
`MYSQL_*`, so `assertsDatabase` is true and the entries say so. The aliases are required by the manifest
test (every entry's script must appear in a `package.json` script); they follow the existing
`test:<name>-playwright` pattern. `lib.js` and `fixtures.sql` are helpers, not checks.

**Not enforced: `SRFAX_LIVE`.** Each note points at `SRFAX_LIVE`, but none of the four scripts reads it; only
`fax-configure-playwright-checks.js` does. The notes tell the operator to export
`SRFAX_LIVE=true` as the project's marker for a live-SRFax run and say plainly that the script does not
check it. The only guard on `fax-backbone-loopback`, which spends real fax pages, is that it runs only when
named. A follow-up could make it refuse to start without `SRFAX_LIVE=true`; that edits the script and was
not part of registering them.

None of the four cleans up (README: run them in order against a freshly provisioned deployment). They are not
declared in `mutates`, because the residue audit diffs `fax_config`, `encounterForm` and property rows, and
these write `faxes`, `document`, routing and chart-note rows instead. Each entry's `fixtures` field says what
it leaves behind.

## 7. Verified and not verified

Run for this document:

- `node scripts/run-playwright-suite.js --tier standalone` in `carlos-deb-validation`: 4 passed in seven runs
  between 14:03 and 14:20 UTC, 9.7 to 10.9 s wall time (one more run, given no browser, failed all four on
  purpose). The Docker daemon restarted around 14:10; the three runs after it (14:18 twice, 14:20) are the
  ones quoted in section 2.
- The three new or edited workflow files and the sketch parse as YAML; all 24 `run:` blocks pass `bash -n`,
  including the inner script of "Run the suite" and the suite-environment file; the `db-schema-verify.yml`
  comparison script compiles and behaves as described.
- Every `uses:` is pinned to a SHA already present in `.github/workflows/`.
- The `script-regressions.yml` diff applies to the current file with `patch` and gives the edited file shown.
- Shard selection was run against the real manifest (section 4); the `jq` matrix expression and the
  `sub("^refs/heads/"; "")` filter were run; the roll-up script was run against output of the runner's own
  `toJUnit`.
- Manifest behaviour of the manual entries: `--list` shows them marked manual, a default `--dry-run` omits
  them, `--tier live-external --dry-run` matches nothing, `--only fax-backbone-loopback --only
  fax-prescription-drugref --dry-run` selects exactly those two. `node --test scripts/*.test.js` ends `fail 0`.
- The previous-tag worktree step (section 5): the tag's `build-demo-additive.sh` ran.

Not run, and only a first run on GitHub (or a maintainer's machine) can settle:

- Anything on GitHub Actions: expression evaluation, the reusable workflow called from a matrix, job-level
  `concurrency` with `inputs`, `gh api .../matching-refs`, artifact names across shards.
- The container boot. The `tomcat-dev`, `mariadb-dev` and `drugref-dev` images are not on this host, so the
  sequence in section 3 was written from `docker-compose.yml`, the Dockerfiles, `populate_db.sh`,
  `seed_data.sh`, `devcontainer.json` and the two workflows it copies, not executed. Specifically unconfirmed:
  that `docker run --env-file` accepts `local.env` (its values are quoted, and `docker run` keeps the quotes
  literally; only cosmetic text is quoted), that the image's `mariadb-client` provides `mysql` (the
  workflow links `mariadb` to that name when it is missing), that `chromium-1223` is at the path used, that
  `/carlos/` answers 200 or 302 when the application is up, and the timings marked "not measured".
- Whether the smoke tier is 12 of 12 on this stack. The 11 of 12 above is on the packaged install.
- The `db-schema-verify.yml` leg end to end: no MariaDB or Flyway was run for it.

## Rollout order

1. Merge the coverage-gap pull request, then commit change 1 alone. (Change 1 already works on
   `release/2026.08`, which has the `standalone` tier; the nightly's residue audit and known-failure
   reporting come from this branch.)
2. Commit `playwright-smoke.yml` in a pull request whose diff touches the file (its `paths` list includes it,
   so it triggers itself). Read the first run's log. Fix the sequence, not the checks.
3. Keep it non-required. After two weeks of green runs, require `Playwright smoke`.
4. Commit `playwright-nightly.yml`. First run: `workflow_dispatch` with `branches=develop` and `shards=8`.
   Record the per-shard wall time and the skip and failure counts here, and tune `shards` and the
   environment step.
5. Decide on the migration leg separately, as its own pull request.
