# Incoming-document mutation authorization

Issue #3776: ViewIncomingDocs was mapped to the shared read-only gate, but its JSP dispatches rotation, deletion, and extraction. Authenticated GET requests could mutate queue files outside CSRFGuard's protected methods; read-only document access also lacked a separate write check.

The dedicated gate preserves the existing authenticated `_edoc` read requirement. Navigation still works through GET or POST. Every nonempty PDF mutation requires POST, `_edoc` write access, and one of the nine supported action names. Wrong methods return 405 with `Allow: POST`; insufficient write privilege returns 403; unsupported actions return 400. The existing POST CSRFGuard configuration is unchanged.

Installed validation12 Playwright confirmed GET Rotate90 returned 200 and changed an owned synthetic PDF. The proposed fixed-DEB workflow in coverage PR #3773 verifies all nine GET mutations are rejected without changing the document, tokenless POST is rejected, and normal UI extraction still works.

All 28 focused Java tests pass. The next installed-DEB positive run is pending. Parameterized tests cover navigation, all supported verbs, wrong methods, read-only callers, write-authorized callers, unknown operations, the shared read gate, and the actual Struts mapping. No database or Flyway changes.
