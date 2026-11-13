<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    "unavailable" result of oscarReport/reportByTemplate/generateOutFilesAction
    (GenerateOutFiles2Action). Report by Template keeps a result's CSV on the server
    (ReportCsvDownloads) and its result page posts only a token. When this session no
    longer holds that result (it expired, a newer report replaced it, the token is from
    another session, or a result page from before the change posted its CSV), the export
    answers with this page instead of a download.

    The action has already set HTTP 410 and Cache-Control: no-store; nothing was written.
    Request attribute: templateid (a positive Integer, or absent): the "Run the report
    again" link opens that template's configuration, otherwise the Template Library.
    The page echoes no other request data.

    @since 2026-11-13
--%><%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <title><fmt:message key="report.byTemplate.resultUnavailableTitle"/></title>
</head>
<body class="p-4">
<div class="alert alert-warning" role="alert" id="reportResultUnavailable">
    <h5 class="alert-heading"><fmt:message key="report.byTemplate.resultUnavailableTitle"/></h5>
    <p class="mb-0"><fmt:message key="report.byTemplate.resultUnavailable"/></p>
</div>
<c:choose>
    <c:when test="${not empty templateid}">
        <c:set var="runAgainUrl" value="${pageContext.request.contextPath}/oscarReport/reportByTemplate/ViewReportConfiguration?templateid=${carlos:forUriComponent(templateid)}"/>
    </c:when>
    <c:otherwise>
        <c:set var="runAgainUrl" value="${pageContext.request.contextPath}/oscarReport/reportByTemplate/ViewHomePage"/>
    </c:otherwise>
</c:choose>
<a class="btn btn-primary" href="${carlos:forHtmlAttribute(runAgainUrl)}"><fmt:message key="report.byTemplate.runAgain"/></a>
</body>
</html>
