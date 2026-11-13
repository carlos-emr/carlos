<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%--
    CDS source links for the encounter chart: one short link to the imported clinical-note source
    fields and one to the additional clinical categories.

    The chart is where an imported note or an unmapped category is read, so it keeps a route to the
    values the import preserved. It renders nothing at all for a patient without imported CDS data
    in those categories, and each link only for its own category. It prints no explanatory text:
    the note about source creation times is the hover text of the clinical-notes link.

    The modern chart places this in the notes toolbar, beside the View Full Chart and Filter
    buttons, where it adds no height; the legacy layout places it at the top of the page.

    Attributes: demographicNo (patient).
--%>
<%@ tag pageEncoding="UTF-8" body-content="empty" %>
<%@ attribute name="demographicNo" required="true" type="java.lang.String" rtexprvalue="true" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib prefix="source" tagdir="/WEB-INF/tags" %>
<fmt:setBundle basename="cdsImportMessages" var="cdsSourceLabels"/>
<fmt:message key="sourceChartClinicalNotesLink" bundle="${cdsSourceLabels}" var="cdsNotesLabel"/>
<fmt:message key="sourceChartAdditionalCategoriesLink" bundle="${cdsSourceLabels}" var="cdsAdditionalLabel"/>
<fmt:message key="sourceClinicalNotesTimingHelp" bundle="${cdsSourceLabels}" var="cdsNotesTimingHelp"/>
<source:cdsSourceFields demographicNo="${demographicNo}" category="ClinicalNotes" label="${cdsNotesLabel}" hint="${cdsNotesTimingHelp}" wrapperClass="cds-chart-source-links"/>
<source:cdsSourceFields demographicNo="${demographicNo}" category="NewCategory" label="${cdsAdditionalLabel}" wrapperClass="cds-chart-source-links"/>
