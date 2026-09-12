<%-- Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:url var="consultationListUrl" value="/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests">
    <c:param name="de" value="${demographicId}"/>
</c:url>
<!DOCTYPE html>
<html>
<head>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <title><fmt:message key="consultation.fax.uncertain.title"/></title>
</head>
<body>
<main class="container py-4">
    <div id="consult-fax-uncertain" class="alert alert-warning" role="alert">
        <h1 class="h4"><fmt:message key="consultation.fax.uncertain.title"/></h1>
        <p><fmt:message key="consultation.fax.uncertain.message"/></p>
    </div>
    <%-- No automatic close, history-back navigation, fax form or retry control. --%>
    <a class="btn btn-outline-secondary" href="<carlos:encode value='${consultationListUrl}' context="htmlAttribute"/>"><fmt:message key="consultation.fax.uncertain.return"/></a>
</main>
</body>
</html>
