<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%--
    CDS source links for the encounter chart: clinical notes and additional clinical categories,
    with the note about source creation times.

    Both chart layouts place this above the notes. It renders nothing at all for a patient without
    imported CDS data in those categories, so an ordinary chart keeps its layout; each part appears
    only when its category has source fields. The timing note concerns clinical notes only.

    Attributes: demographicNo (patient).
--%>
<%@ tag pageEncoding="UTF-8" body-content="empty" %>
<%@ tag import="io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSourceFieldPresenceResolver" %>
<%@ tag import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ attribute name="demographicNo" required="true" type="java.lang.String" rtexprvalue="true" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib prefix="source" tagdir="/WEB-INF/tags" %>
<%
    CdsSourceFieldPresenceResolver cdsPresence = SpringUtils.getBean(CdsSourceFieldPresenceResolver.class);
    String cdsPatient = (String) jspContext.getAttribute("demographicNo");
    jspContext.setAttribute("cdsNotesPresent", cdsPresence.hasSourceFields(request, cdsPatient, "ClinicalNotes"));
    jspContext.setAttribute("cdsAdditionalPresent", cdsPresence.hasSourceFields(request, cdsPatient, "NewCategory"));
%>
<c:if test="${cdsNotesPresent or cdsAdditionalPresent}">
    <fmt:setBundle basename="cdsImportMessages" var="cdsSourceLabels"/>
    <div class="cds-chart-source-links">
        <c:if test="${cdsNotesPresent}"><span class="cds-chart-source-link"><fmt:message key="sourceClinicalNotes" bundle="${cdsSourceLabels}"/>: <source:cdsSourceFields demographicNo="${demographicNo}" category="ClinicalNotes"/></span></c:if>
        <c:if test="${cdsNotesPresent and cdsAdditionalPresent}">&mdash;</c:if>
        <c:if test="${cdsAdditionalPresent}"><span class="cds-chart-source-link"><fmt:message key="sourceAdditionalCategories" bundle="${cdsSourceLabels}"/>: <source:cdsSourceFields demographicNo="${demographicNo}" category="NewCategory"/></span></c:if>
    </div>
    <c:if test="${cdsNotesPresent}"><p class="cds-note-source-timing"><fmt:message key="sourceClinicalNotesTimingHelp" bundle="${cdsSourceLabels}"/></p></c:if>
</c:if>
