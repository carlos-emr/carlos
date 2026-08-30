<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    Shared "input" result for file-upload actions that have no page of their own
    able to report a rejection.

    Struts' multipart layer rejects a request -- an empty part,
    struts.multipart.maxStringLength (default 4096), maxFiles, the 50MB maxSize,
    or a parse failure -- BEFORE the action runs, and the workflow interceptor
    answers with the "input" result. An action with no "input" mapping falls
    through to errorpage.jsp as a raw HTTP 500.

    The obvious fix, pointing "input" at the action's own success page, is worse
    than the 500: those pages are written for a request the action completed, so
    they either say nothing (their error blocks key off attributes only the
    action sets) or, for pages like uploadComplete.jsp, actively report a
    rejected upload as finished. Rendering the multipart layer's own messages
    here is unconditional and cannot be mistaken for success.

    Errors can embed the submitted filename, so they are encoded, and the
    response carries 400 rather than 200 so scripted clients see a failure.
--%><%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<% response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST); %>
<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <title><fmt:message key="global.error"/></title>
</head>
<body class="p-4">
<div class="alert alert-danger" role="alert">
    <h5 class="alert-heading"><fmt:message key="dms.error.uploadError"/></h5>
    <%
        java.util.Collection<String> uploadErrors =
                (java.util.Collection<String>) request.getAttribute("actionErrors");
        if (uploadErrors != null && !uploadErrors.isEmpty()) {
    %>
    <ul class="mb-0">
        <% for (String uploadError : uploadErrors) { %>
        <li><%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(uploadError) %></li>
        <% } %>
    </ul>
    <% } else { %>
    <p class="mb-0"><fmt:message key="dms.addDocument.errorZeroSize"/></p>
    <% } %>
</div>
<button type="button" class="btn btn-secondary" onclick="history.back();">
    <fmt:message key="global.btnBack"/>
</button>
</body>
</html>
