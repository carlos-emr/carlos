# Stale tickler selections

`tickler-stale-selection` implements #5810 through Schedule → Tickler and the
native DataTable/search/status/Assigned To/pagination and bulk-action controls.
SQL creates 59 synthetic tasks for one owned patient: 53 active tasks span two
default 50-row pages; separate assignee, Completed, Deleted and hidden controls
make incorrect selection reuse distinguishable.

For search, status, assignee and page changes, a selected old row remains on
screen while the check holds an **actual successful ListTicklers response**.
The server payload is neither edited nor fabricated. Complete/Delete must be
inert; no bulk POST, task-row change or history write is allowed. Releasing the
response must replace the rows with exactly the independently enumerated set,
with no checked old selection. Check All and Clear All then apply to that draw.

A separate real response is delayed until a later search result has drawn.
Delivering the old response must not replace the latest rows/filter, restore
selection or change stored tasks. The check observes that response's native
DataTables xhr event and the next browser task before inspecting the DOM, so
receipt of HTTP headers alone cannot certify a completed stale-response test.
Native Complete and Delete subsequently
submit exactly one currently visible, active, owned task each. An ownership
guard runs before either mutation. The actual POST must carry precisely its
checked ID, chosen action and CSRF protection. SQL verifies every task status,
one status-history record per selected task with the acting provider, no history
on sentinels, complete unchanged unselected rows and no unexpected attachments
or comments. Deleted-view action restrictions and Clear All remain asserted.

Response-barrier teardown releases held requests, disposes their responses and
removes browser routes even after an assertion fails. Independent controls
retain simultaneous primary/delivery/disposal/route-removal errors. Nothing
converts a network error or an unavailable second assignee into successful race
coverage. Exact owned task/history/comment/attachment cleanup is verified;
existing bulk-action, paged-selection and preference checks remain registered.
