<%--
Purpose: Preview and apply a reviewed CML measurement projection repair.
Features: Patient authorization, read-only preview, exact stale-plan rejection and transactional audit.
Parameters: reportNo (positive CML report ID), fingerprint (preview token on POST only).
@since 2026-11-09
--%>
<%-- Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later --%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<!doctype html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}"><head><title><fmt:message key="clinical.cml.repairTitle"/></title></head>
<body>
<h1><fmt:message key="clinical.cml.repairTitle"/></h1>
<p><fmt:message key="clinical.cml.repairIntro"/></p>
<c:if test="${not empty repairError}"><p role="alert"><fmt:message key="${repairError}"/></p></c:if>
<c:if test="${repairApplied}"><p role="status"><fmt:message key="clinical.cml.applied"/></p></c:if>
<form method="get" action="${pageContext.request.contextPath}/admin/CmlProjectionRepair">
<input type="hidden" name="operation" value="preview">
<label><fmt:message key="clinical.cml.report"/>
<input name="reportNo" type="number" min="1" max="2147483647" required value="${carlos:forHtmlAttribute(reportNo)}"></label>
<button type="submit"><fmt:message key="clinical.cml.preview"/></button>
</form>
<c:if test="${not empty projectionPlan}">
<fmt:message key="clinical.cml.preview" var="previewLabel"/>
<section aria-label="${carlos:forHtmlAttribute(previewLabel)}">
<c:set var="reasonKey" value="clinical.cml.reason.${projectionPlan.reason}"/>
<p id="repairReason"><fmt:message key="${reasonKey}"/></p>
<dl>
<dt><fmt:message key="clinical.cml.archiveCount"/></dt><dd id="archiveCount">${carlos:forHtml(projectionPlan.archiveCount)}</dd>
<dt><fmt:message key="clinical.cml.createCount"/></dt><dd id="createCount">${carlos:forHtml(projectionPlan.createCount)}</dd>
</dl>
<c:if test="${projectionPlan.ready and projectionPlan.changeNeeded}">
<form method="post" action="${pageContext.request.contextPath}/admin/CmlProjectionRepair">
<input type="hidden" name="operation" value="apply">
<input type="hidden" name="reportNo" value="${carlos:forHtmlAttribute(reportNo)}">
<input type="hidden" name="fingerprint" value="${carlos:forHtmlAttribute(projectionPlan.fingerprint)}">
<button type="submit"><fmt:message key="clinical.cml.apply"/></button>
</form>
</c:if>
</section>
</c:if>
</body></html>
