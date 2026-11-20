<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%--
    Link from a native screen to the patient's preserved CDS source fields for one category.

    Renders nothing unless the patient has imported CDS source data in that category, so a chart no
    import ever touched carries no CDS wording. CdsSourceFieldPresenceResolver decides and remembers
    the answer for the request; the source-fields view still applies its own read policy.

    Attributes: demographicNo (patient), category (a CDS source category such as ClinicalNotes),
    label (optional link text; the generic "CDS source fields" when absent), hint (optional hover
    text explaining what the link holds), wrapperClass (optional;
    when given, the link is wrapped in a span of that class, so a caller that styles or spaces the
    link still emits no markup at all for a patient without source fields).
--%>
<%@ tag pageEncoding="UTF-8" body-content="empty" %>
<%@ tag import="io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSourceFieldPresenceResolver" %>
<%@ tag import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ attribute name="demographicNo" required="true" type="java.lang.String" rtexprvalue="true" %>
<%@ attribute name="category" required="true" type="java.lang.String" rtexprvalue="true" %>
<%@ attribute name="label" required="false" type="java.lang.String" rtexprvalue="true" %>
<%@ attribute name="hint" required="false" type="java.lang.String" rtexprvalue="true" %>
<%@ attribute name="wrapperClass" required="false" type="java.lang.String" rtexprvalue="true" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    jspContext.setAttribute("cdsSourceFieldsPresent", SpringUtils.getBean(CdsSourceFieldPresenceResolver.class)
            .hasSourceFields(request, (String) jspContext.getAttribute("demographicNo"), (String) jspContext.getAttribute("category")));
%>
<c:if test="${cdsSourceFieldsPresent}">
    <fmt:setBundle basename="cdsImportMessages" var="sourceMessages"/>
    <c:url var="sourceFieldsUrl" value="/demographic/cdsSourceFields"><c:param name="demographicNo" value="${demographicNo}"/><c:param name="category" value="${category}"/></c:url>
    <c:if test="${not empty wrapperClass}"><span class="${carlos:forHtmlAttribute(wrapperClass)}"></c:if>
    <a class="cds-source-fields-link" href="${carlos:forHtmlAttribute(sourceFieldsUrl)}" target="_blank" rel="noopener"<c:if test="${not empty hint}"> title="${carlos:forHtmlAttribute(hint)}"</c:if>><c:choose><c:when test="${not empty label}"><carlos:encode value="${label}"/></c:when><c:otherwise><fmt:message key="sourceFieldsLink" bundle="${sourceMessages}"/></c:otherwise></c:choose></a>
    <c:if test="${not empty wrapperClass}"></span></c:if>
</c:if>
