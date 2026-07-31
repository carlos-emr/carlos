# PR 3288 Pending Docs Missing-Refile Playwright Test Design

## Goal

Prove in a real browser that viewing a pending document does not render an error page when the selected queue's `Refile` directory has not been created yet.

## Scope

Add one standalone local Playwright check and one npm entry point. The check covers the behavior changed by PR 3288; it does not alter Java production code, database schema, or unrelated document-manager workflows.

## Test flow

1. Validate that `BASE_URL` targets a local or private CARLOS instance and log in with the configured local test user.
2. Resolve a real pending-document identifier from the disposable development database, failing with a precise prerequisite message when no suitable fixture exists.
3. Determine the queue-1 `Refile` directory from the application configuration, then rename that exact directory to a unique sibling backup only when it already exists. The rename is reversible and preserves all contents.
4. Navigate through the Pending Docs document-view route for the selected document.
5. Require a successful HTTP response and recognizable document-view content. Record and fail on an HTTP 500/error page, uncaught page error, or unexpected severe console error.
6. In `finally`, restore the original `Refile` directory exactly. If the directory did not exist initially, leave it absent. Close browser/database resources regardless of outcome.

## Boundary and data handling

The check mutates only the queue-1 `Refile` directory in a local/dev filesystem and restores it before exit. It does not delete data. Database access is read-only: the existing local seed data supplies the pending document. The test must reject non-local base URLs by default so it cannot be directed at a production environment accidentally.

## Assertions

The test passes only if the document-view response succeeds while `Refile` is absent and the browser reaches the expected Pending Docs document UI. It fails on the pre-PR behavior, where the missing directory is interpreted as invalid configuration and causes the JSP request to fail.

## Verification

Use the same script against the PR's parent commit to demonstrate the expected failure, then restore PR 3288 and require it to pass. Run the script through its npm entry point against the local Tomcat instance. The existing focused Java tests remain the unit-level coverage; this check supplies end-to-end evidence of the UI contract.
