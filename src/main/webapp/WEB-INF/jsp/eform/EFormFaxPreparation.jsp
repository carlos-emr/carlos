<%-- Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later.
     Internal result of a CSRF-protected eForm save, never a GET staging endpoint.
     Do not replay the original form's clinical text or signature data to the fax route. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="fax.eformMissingContent.btnPreparingFax"/></title>
</head>
<body>
<c:if test="${not empty requestScope.preparedFaxTarget}">
    <form id="eform-fax-preparation" method="post"
          action="<carlos:encode value="${requestScope.preparedFaxTarget}" context="htmlAttribute"/>">
        <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
        <button type="submit"><fmt:message key="fax.eformMissingContent.btnPreparingFax"/></button>
    </form>
    <script>
        (() => {
            const form = document.getElementById('eform-fax-preparation');
            form.addEventListener('submit', () => {
                form.querySelector('button').disabled = true;
            });
            form.requestSubmit();
        })();
    </script>
</c:if>
</body>
</html>
