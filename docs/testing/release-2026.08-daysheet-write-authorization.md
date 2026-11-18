# Day-sheet acknowledgment authorization (#5963)

The release day-sheet JSP changes appointment status from `t` to `T` on its
CSRF-protected acknowledgment POST. Its mapped action previously checked only
`_report` read access. This repair concerns that release authorization bug.

The real Struts mapping now uses a dedicated action which retains the shared
report-read and HTTP-method gate, then requires `_appointment` write permission
before an acknowledgment in new-appointments mode can reach the JSP. Read-only
GET/HEAD previews, filter POSTs and all-appointments mode remain readable.
The JSP offers the acknowledgment button only to appointment writers. Existing
Struts security-exception handling returns HTTP 403 for a refused write.

The source regression loads the actual Struts mapping and executes its mapped
Java action. Four of fourteen cases fail on the release gate: three new-mode
acknowledgments are admitted without appointment-write permission, and the
permitted-write control confirms the missing privilege check. Before report
SHA-256: `2c46b594ca0515f41257a2622ae1968d90708a30dc42a0422265ae4d687a28ca`.
This is an executed action-boundary reproduction, not a live pre-fix database
mutation. All fourteen cases pass after repair, including read-only controls,
explicit write access, absent report access, anonymous requests and HTTP-method
refusals. Source-bound coverage and rebuilt-package/browser qualification follow
in this issue's validation record; no installed execution is claimed here yet.
