<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<% pageContext.setAttribute("recoveryLocale", io.github.carlos_emr.carlos.utility.LocaleUtils.resolveBundleLocale(request)); %>
<fmt:setLocale value="${recoveryLocale}"/>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="<c:out value='${recoveryLocale.language}'/>">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="messenger.SubmissionConflict.title"/></title>
    <c:url var="bootstrapUrl" value="/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
    <link rel="stylesheet" href="<c:out value='${bootstrapUrl}'/>">
</head>
<body>
<main class="container py-4">
    <h1><fmt:message key="messenger.SubmissionConflict.title"/></h1>
    <fmt:message key="${requestScope.messageSubmissionErrorKey}" var="submissionError"/>
    <p class="alert alert-warning" role="alert"><c:out value="${submissionError}"/></p>
    <c:url var="sentMessagesUrl" value="/messenger/DisplayMessages">
        <c:param name="boxType" value="1"/>
    </c:url>
    <a class="btn btn-primary" href="<c:out value='${sentMessagesUrl}'/>"><fmt:message key="messenger.SubmissionConflict.linkSentMessages"/></a>
</main>
</body>
</html>
