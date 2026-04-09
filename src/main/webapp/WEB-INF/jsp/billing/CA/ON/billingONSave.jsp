<%--
    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
    billingONSave.jsp (view) - Ontario billing save success page.
    Closes the billing window and triggers a schedule refresh via BroadcastChannel.
    Rendered by BillingONSave2Action on successful save.
    @since 2026
--%>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
</head>
<body>
<%
    String safeUrlBack = (String) request.getAttribute("safeUrlBack");
    if (safeUrlBack == null) safeUrlBack = "";
    String submit = request.getParameter("submit");
    Boolean failure = Boolean.TRUE.equals(request.getAttribute("billingFailed"));
%>
<% if (Boolean.TRUE.equals(failure)) { %>
    <h1>Sorry, billing has failed. Please do it again!</h1>
<% } else if ("Save & Add Another Bill".equals(submit)) { %>
<script type="text/javascript">
    try { if (self.opener && self.opener.refresh) { self.opener.refresh(); } else { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); } } catch(e) { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); }
    <% if (!safeUrlBack.isEmpty()) { %>
    self.location.href = "<%= Encode.forJavaScript(safeUrlBack) %>";
    <% } else { %>
    self.close();
    <% } %>
</script>
<% } else { %>
<script type="text/javascript">
    self.close();
    try { if (self.opener && self.opener.refresh) { self.opener.refresh(); } else { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); } } catch(e) { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); }
</script>
<% } %>
</body>
</html>

