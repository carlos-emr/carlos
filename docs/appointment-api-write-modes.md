# Appointment API write modes

Appointment updates support an explicit per-request header,
`X-Carlos-Appointment-Write-Mode`, on the full update and status/type/urgency
update routes. This applies to both application REST surfaces.

| Header | Behavior |
| --- | --- |
| Absent | Retains existing behavior: nonblank `editVersion` enables conflict detection; omitted/null/blank versions use last-writer-wins. |
| `guarded` | Requires a nonblank `editVersion`. Missing versions return 400, stale versions return 409, and a missing appointment returns 404. |
| `legacy-echo` | Ignores an echoed version explicitly and uses the established last-writer-wins path. The response still returns the new `editVersion`. |

Unknown, blank, duplicate or combined mode selections return 400 before calling
the update manager. The server cannot infer whether an identical echoed token
came from a client deliberately protecting edits or a client resubmitting an
old response. It therefore keeps existing token protection unless the caller
explicitly selects `legacy-echo`. First-party guarded callers require no change.

Choose `guarded` for clients that can refresh/reconcile conflicts. Use
`legacy-echo` only for a client whose intended contract is unconditional edits;
concurrent edits can overwrite one another in that mode. Keep the header on
every applicable write. Removing the header from such a client restores the
existing token-aware behavior and may produce stale-write conflicts again.

All modes retain authorization, the locked transactional read/archive/write,
editable-field allowlist, actor metadata, audit behavior and response token.
The compatibility mode does not permit arbitrary property writes. Legacy writes
emit their fixed diagnostic at DEBUG, rather than a WARN on every request.
