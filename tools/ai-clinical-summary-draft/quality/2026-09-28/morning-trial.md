# Morning trial: reviewed chart updates

This workspace has a separate CARLOS trial at **http://localhost:8082/carlos/**.
It uses a fresh copy of the development database, named
`carlos_chartupdates_morning_20260929`. The earlier automated test results remain
in `carlos_chartupdates_20260928`; the original development charts are unchanged.
The server binds only to loopback. Forward port 8082 if accessing the devcontainer
from another machine.

## Try it

1. Sign in with the existing development clinician account.
2. Find **NHSSYN001**, **NHSSYN002**, or **NHSSYN003** and open its eChart. This
   establishes the chart context and editing lock needed for signed history.
3. In the eChart header, click the blue **Review chart updates** link. The workflow
   stays in a modal over the eChart. In the
   modal document list that opens, click **Review chart updates** beside the desired
   document. This keeps the chart open while you review the document. If the
   document is unavailable, a modal explains the problem and leaves the document
   list in place. Close it to choose another document. Open original is offered
   when the source is available; a missing file needs an administrator to restore it.

   Direct links are also available in the same browser session:

   | Chart | Review page |
   | --- | --- |
   | NHSSYN001 | http://localhost:8082/carlos/documentManager/AiChartUpdates?documentId=40 |
   | NHSSYN002 | http://localhost:8082/carlos/documentManager/AiChartUpdates?documentId=41 |
   | NHSSYN003 | http://localhost:8082/carlos/documentManager/AiChartUpdates?documentId=42 |

   The same page is available through **Review chart updates** on the document
   summary preview. Generating a document summary first is optional.
4. Generate proposals. The updated form suggests reminder dates from clear source
   timing, assigns reminders to you initially, and suggests a chart section.
   Relative dates use the document date shown beside the field. Past suggested
   dates are flagged; ambiguous timing stays blank. All suggestions are editable.
   Compare each source passage with the chart, edit as
   needed, choose the reminder date/assignee or history destination, and approve
   that item. **Dismiss** makes no chart entry.

Saving one item keeps edits on the other cards when JavaScript is enabled.
Approval checkboxes reset after a response so the remaining items receive a fresh
review. Refreshing the result page does not repeat generation or saves. A completed
review shows a completion message; generating another set is under an expandable
control and replaces unsaved edits.

## What this trial verifies

The proposal source is visibly labeled **Fixed NHS proposals - no model**. The
three documents return fixed, manually selected exact source excerpts. There are
no paid AI requests. This exercises the review and real chart-save workflow;
it does not measure model extraction quality. The trial gateway only supports
chart-update proposals for these three unchanged source documents. Live document
summary generation needs the separate model gateway configuration.

The feature reads the original document and does not depend on the wording or
structure of the developing single-document summary. Original sources, approved
text and native document links are retained in the saved entries.

## Local runtime

- Private trial configuration and source files: `target/nhs-chart-update-morning/`.
- Trial Tomcat: `target/nhs-chart-update-morning/tomcat/`.
- Fixed proposal gateway: loopback port 11438.
- The private runtime configuration is ignored by Git and contains database
  credentials. Do not copy it into an issue, PR or commit.
- This is a temporary development instance. See
  [the repeatable integration check](../../browser/NHS_LIVE_CHECK.md) for setup
  requirements after an environment rebuild.

See [the integration report](chart-update-integration.md) for the test evidence
and its limits. Feature flags remain disabled by default in application code.
