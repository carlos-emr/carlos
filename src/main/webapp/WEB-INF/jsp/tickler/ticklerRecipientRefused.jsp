<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%--
Purpose: Machine-readable refusal of a Tickler recipient that is no longer active.
Features: HTTP 400 and the tickler-recipient-refused marker let ticklerAdd.jsp
retain its draft and tickler-refusal-diagnostics.js verify the owned refusal.
Parameters: None; the action validates the recipient before rendering this view.
@since 2026-11-09
--%>
<% response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST); %>
<span id="tickler-recipient-refused"></span>
