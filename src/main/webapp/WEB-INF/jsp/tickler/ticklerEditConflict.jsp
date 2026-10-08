<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%--
    Renders the refused tickler-edit response read by the parent editor's iframe callback.
    The parent keeps the entered draft; this view provides the localized refusal and whether
    a separate current-record review is available. No tickler data is written here.
    Request attributes: ticklerEditErrorKey (message key), ticklerReviewAvailable (boolean).
    recoveryLocale is resolved from the browser's supported language preferences.
    @since 2026-10-06
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<% pageContext.setAttribute("recoveryLocale", io.github.carlos_emr.carlos.utility.LocaleUtils.resolveBundleLocale(request)); %>
<fmt:setLocale value="${recoveryLocale}"/>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(recoveryLocale.language)}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="tickler.ticklerEdit.title"/></title>
</head>
<body>
<fmt:message key="${requestScope.ticklerEditErrorKey}" var="editError"/>
<p id="tickler-edit-refused" data-review-available="${carlos:forHtmlAttribute(requestScope.ticklerReviewAvailable)}">${carlos:forHtml(editError)}</p>
</body>
</html>
