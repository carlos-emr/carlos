<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%@ tag pageEncoding="UTF-8" body-content="empty" %>
<%@ attribute name="demographicNo" required="true" type="java.lang.String" rtexprvalue="true" %>
<%@ attribute name="category" required="true" type="java.lang.String" rtexprvalue="true" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<c:if test="${not empty demographicNo}">
    <fmt:setBundle basename="cdsImportMessages" var="sourceMessages"/>
    <c:url var="sourceFieldsUrl" value="/demographic/cdsSourceFields"><c:param name="demographicNo" value="${demographicNo}"/><c:param name="category" value="${category}"/></c:url>
    <a class="cds-source-fields-link" href="${carlos:forHtmlAttribute(sourceFieldsUrl)}" target="_blank" rel="noopener"><fmt:message key="sourceFieldsLink" bundle="${sourceMessages}"/></a>
</c:if>
