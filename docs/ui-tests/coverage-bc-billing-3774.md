# BC supplementary billing associations (#3774 / #3784)

Coverage found an inverted validation branch: valid edits and every delete redirected before persistence, while invalid edits reached the DAO. Six focused assertions fail on the original release. The fix writes only validated POST edits/deletes, rejects unknown modes and malformed delete identifiers, redirects after successful persistence, and renders action errors without writing on validation failure. The form now has the name its fee-code picker expects.

The focused Java suite includes invalid primary/secondary codes, successful save/delete, GET rejection, unknown mode, invalid delete identifier and read-only view, plus the mutation-action discovery contract. The browser workflow opens Administration through the schedule, creates three owned fee codes, rejects an invalid association, creates/reopens/updates/deletes an owned association, verifies database values and cleans up its rows. It requires a BC schema and billing region; an Ontario deployment reports SKIP, not PASS.

Local Java tests pass with the VM stopped. Installed-DEB BC browser validation is pending and will be reported in this PR before readiness. No migration or schema is added by the fix.
