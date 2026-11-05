# Incoming-document mutation authorization

Issue #3776: ViewIncomingDocs was mapped to the shared read-only gate, but its JSP dispatches rotation, deletion, and extraction. Authenticated GET requests could mutate queue files outside CSRFGuard's protected methods; read-only document access also lacked a separate write check.

The dedicated gate preserves the existing authenticated `_edoc` read requirement. Navigation still works through GET or POST. Every nonempty PDF mutation requires POST, `_edoc` write access, and one of the nine supported action names. Wrong methods return 405 with `Allow: POST`; insufficient write privilege returns 403; unsupported actions return 400. The existing POST CSRFGuard configuration is unchanged.

`incomingDocs.jsp` is also rendered as `ManageDocument`'s `nextIncomingDoc` result, which does not pass through the gate. The JSP therefore repeats the same POST, `_edoc` write, and supported-action checks immediately before `IncomingDocUtil.doPagesAction`. The supported names live in one place, `IncomingDocUtil.isSupportedPageAction`, which both boundaries use.

Installed validation12 Playwright confirmed GET Rotate90 returned 200 and changed an owned synthetic PDF. The proposed fixed-DEB workflow in coverage PR #3773 verifies all nine GET mutations are rejected without changing the document, tokenless POST is rejected, and normal UI extraction still works.

All 42 focused Java tests pass. The next installed-DEB positive run is pending. Parameterized tests cover navigation, all supported verbs, wrong methods, read-only callers, write-authorized callers, unknown operations, the shared read gate, the actual Struts mapping, and the JSP's repeated checks for the ungated `nextIncomingDoc` result. No database or Flyway changes.
