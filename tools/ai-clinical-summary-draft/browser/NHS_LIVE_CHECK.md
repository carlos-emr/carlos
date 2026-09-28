# Three-patient CARLOS integration walkthrough

`nhs_live_check.cjs` drives a real CARLOS login, patient search, eChart, document
summary preview and chart-update review. It verifies persisted reminders and
signed history entries in MariaDB. Run it only against a disposable database copy
whose name starts with `carlos_chartupdates_`. It intentionally leaves its approved
entries in that copy for inspection; it does not remove clinical records afterward.

## Model dependency

For application integration without model credentials, start:

```sh
python3 tools/ai-clinical-summary-draft/browser/nhs_fixture_gateway.py
```

This loopback server on port 11438 returns three **fixed, manually selected source
excerpts** for each of NHSSYN001–003. It does not run AI and cannot measure extraction
quality. It accepts only the complete committed corpus note selected for each case
and validates the same request contract as the model gateway. Changed documents
and other notes are rejected. Configure the isolated application's HTTP agent port
as 11438 and label it `Fixed NHS proposals - no model`.

For model testing, use the configured model gateway instead and give
`CHART_TEST_AGENT` its actual model/provider. The walkthrough currently expects at
least three proposals including one reminder and two history candidates; a model
returning fewer may require a different test document or a tailored quality review.

## Preparation

1. Build this branch, copy the existing development database into a new isolated
   schema, and apply the reviewed-chart-update receipt migration to that copy.
2. Run a separate Tomcat against the copied schema, with all three feature flags
   from `../CHART_UPDATES.md` enabled. Give it a separate document directory. Clear
   inherited `CATALINA_OPTS`, `JAVA_TOOL_OPTIONS` and `JDK_JAVA_OPTIONS` when starting
   it so a global `carlos_override_properties` cannot override its private config.
   Confirm the database name in the startup log before testing.
3. Verify the existing `NHSSYN001`, `NHSSYN002`, and `NHSSYN003` charts. Obtain the
   complete source notes from `openrouter_agent.committed_notes()`: zero-based
   indices 16, 16, and 14 respectively within each patient's notes. Verify that
   each source is already present in the corresponding `casemgmt_note` record.
4. Place those unchanged UTF-8 texts in the isolated document directory and link
   them as active `text/plain` documents to their respective charts. These fixture
   documents exercise the summarizer/add-on; document upload is outside this check.
5. Write a local JSON array with exactly three objects, in fixture order:

```json
[
  {
    "fixture": "NHSSYN001",
    "demographicId": 3001,
    "documentId": 40,
    "sourceFile": "/absolute/path/to/unchanged-note.txt",
    "sourceSha256": "sha256-of-the-file"
  }
]
```

The numbers above are examples. Use actual IDs from the isolated database. Add
NHSSYN002 and NHSSYN003 objects. No credentials belong in this file.

## Run

Set the existing browser harness variables `BASE_URL`, `TEST_USER`,
`TEST_PASSWORD`, `TEST_PIN`, `MYSQL_HOST`, `MYSQL_USER`, `MYSQL_PASSWORD`, and
`MYSQL_DATABASE` privately. For a deliberate devcontainer database host such as
`db`, also set `ALLOW_NON_LOCAL_MYSQL_HOST=true`. Supply:

- `CHART_TEST_FIXTURES`: absolute path to the local fixture JSON.
- `CHART_TEST_OUTPUT`: local directory for screenshots and `result.json`.
- `CHART_TEST_AGENT`: truthful description of the model or fixed fixture gateway.

Then run:

```sh
node tools/ai-clinical-summary-draft/browser/nhs_live_check.cjs
```

The approved reminders use October 5, 2026 and development provider `999998` as
explicit test inputs. They are not inferred clinical recommendations. Use a fresh database copy for an initial save test. Repeating the check on the
same fixture verifies that the existing approvals replay without duplicate rows;
the result records how many approval receipts existed before each patient run. Screenshots and the result identify only these
verified synthetic fixtures. Keep login storage and database credentials private.

The September 28 follow-up also checks the real 403/405 security responses,
forged review tokens, retention of edits on other cards, fresh approval after a
response, and refresh-safe redirects. Each of the three patients passed 21 checks.
See [the prepared morning trial](../quality/2026-09-28/morning-trial.md) for the
separate fresh workspace instance; the fixture gateway still makes no model calls.
