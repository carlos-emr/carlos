<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%-- The hidden edit iframe returns a refusal message; the parent keeps the entered form. --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="tickler.ticklerEdit.attachments.error" var="attachmentError"/>
<!DOCTYPE html>
<html>
<head><title></title></head>
<body>
<p id="tickler-edit-attachments-refused"><carlos:encode value="${attachmentError}"/></p>
</body>
</html>
