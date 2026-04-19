<%--
    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
    billingONSaveWorkloadRedirect.jsp (view) - Ontario billing save workload management redirect.
    Redirects to the workload management screen after billing save.
    Rendered by BillingONSave2Action when workload management routing is active.
    @since 2026
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String workloadUrlBack = (String) request.getAttribute("workloadUrlBack");
%>
<!DOCTYPE html>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
</head>
<body>
<script type="text/javascript">
    try { if (self.opener && self.opener.refresh) { self.opener.refresh(); } else { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); } } catch(e) { new BroadcastChannel('carlos_schedule_refresh').postMessage('refresh'); }
    <% if (workloadUrlBack != null && !workloadUrlBack.isEmpty()) { %>
    self.location.href = "<carlos:encode value='<%= workloadUrlBack %>' context="javaScriptBlock"/>";
    <% } else { %>
    self.close();
    <% } %>
</script>
</body>
</html>
