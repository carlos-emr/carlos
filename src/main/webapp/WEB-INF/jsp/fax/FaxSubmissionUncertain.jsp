<%-- Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="<carlos:encode value='${pageContext.request.locale.toLanguageTag()}' context="htmlAttribute"/>">
<head>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <title><fmt:message key="consultation.fax.uncertain.title"/></title>
</head>
<body>
<main class="container py-4">
    <div id="fax-queue-uncertain" class="alert alert-warning" role="alert">
        <h1 class="h4"><fmt:message key="consultation.fax.uncertain.title"/></h1>
        <p><fmt:message key="fax.queue.uncertain.message"/></p>
    </div>
    <%-- Deliberately no fax form, retry control or automatic navigation. --%>
</main>
</body>
</html>
