<%-- Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="<carlos:encode value="${pageContext.request.locale.language}" context="htmlAttribute"/>">
<head><meta charset="UTF-8"><title><fmt:message key="eform.capacity.waiting"/></title></head>
<body>
<fmt:message key="eform.capacity.cancelled" var="cancelledMessage"/>
<fmt:message key="eform.capacity.continuing" var="continuingMessage"/>
<fmt:message key="eform.capacity.uncertain" var="uncertainMessage"/>
<form id="eform-render-capacity" method="post"
      action="<carlos:encode value="${requestScope.renderCapacityAction}" context="htmlAttribute"/>"
      data-cancelled-message="<carlos:encode value="${cancelledMessage}" context="htmlAttribute"/>"
      data-continuing-message="<carlos:encode value="${continuingMessage}" context="htmlAttribute"/>"
      data-uncertain-message="<carlos:encode value="${uncertainMessage}" context="htmlAttribute"/>">
    <p id="eform-render-capacity-status" role="status"><fmt:message key="eform.capacity.waiting"/></p>
    <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
    <c:forEach var="field" items="${requestScope.renderCapacityFields}">
        <input type="hidden" name="<carlos:encode value="${field.key}" context="htmlAttribute"/>"
               value="<carlos:encode value="${field.value}" context="htmlAttribute"/>"/>
    </c:forEach>
    <button id="eform-render-capacity-continue" type="submit"><fmt:message key="global.btnContinue"/></button>
    <button id="eform-render-capacity-cancel" type="button"><fmt:message key="global.btnCancel"/></button>
</form>
<script src="<c:url value='/js/eform-render-capacity.js'/>"></script>
</body>
</html>
