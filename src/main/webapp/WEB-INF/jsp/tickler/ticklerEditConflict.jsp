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
    <title><fmt:message key="tickler.ticklerEdit.title"/></title>
</head>
<body>
<fmt:message key="${requestScope.ticklerEditErrorKey}" var="editError"/>
<p id="tickler-edit-refused" data-review-available="<c:out value='${requestScope.ticklerReviewAvailable}'/>"><c:out value="${editError}"/></p>
</body>
</html>
