<!--
Copyright (c) 2023. Magenta Health Inc. All Rights Reserved.

This software is published under the GPL GNU General Public License.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
-->
<%@ page import="ca.openosp.OscarProperties" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>

<fmt:setBundle basename="oscarResources"/>

<c:url var="hospitalReportUrl" value="/hospitalReportManager/hospitalReportManager.jsp"/>

<c:set var="providerNo" value="${sessionScope.user}" />
<c:set var="contextPath" value="${pageContext.request.contextPath}" />

<a href="javascript:reportWindow('${e:forHtml(contextPath)}/oscarMDS/ForwardingRules.jsp?providerNo=${e:forHtml(providerNo)}');" class="nav-link">Forwarding Rules</a>
<a href="javascript:reportWindow('${e:forHtml(contextPath)}/lab/CA/ALL/testUploader.jsp',800,1000)" class="nav-link"><fmt:message key="admin.admin.hl7LabUpload"/></a>

<c:if test="${OscarProperties.getInstance().getBooleanProperty('legacy_document_upload_enabled', 'true')}">
    <a href="javascript:reportWindow('${e:forHtml(contextPath)}/documentManager/html5AddDocuments.jsp',600,500)" class="nav-link"><fmt:message key="inboxmanager.document.uploadDoc"/></a>
</c:if>

<c:if test="${!OscarProperties.getInstance().getBooleanProperty('legacy_document_upload_enabled', 'true')}">
    <a href="javascript:reportWindow('${e:forHtml(contextPath)}/documentManager/documentUploader.jsp',800,1000)" class="nav-link"><fmt:message key="inboxmanager.document.uploadDoc"/></a>
</c:if>

<a href="javascript:reportWindow('${e:forHtml(contextPath)}/documentManager/inboxManage.do?method=getDocumentsInQueues',700,1100)" class="nav-link"><fmt:message key="inboxmanager.document.pendingDocs"/></a>
<a href="javascript:reportWindow('${e:forHtml(contextPath)}/documentManager/incomingDocs.jsp',800,1200)" class="nav-link"><fmt:message key="inboxmanager.document.incomingDocs"/></a>

<c:if test="${!OscarProperties.getInstance().isBritishColumbiaBillingRegion()}">
    <a href="javascript:reportWindow('${e:forHtml(contextPath)}/oscarMDS/CreateLab.jsp',800,1000)" class="nav-link"><fmt:message key="global.createLab" /></a>
    <a href="javascript:reportWindow('${e:forHtml(contextPath)}/olis/Search.jsp',800,1000)" class="nav-link"><fmt:message key="olis.olisSearch" /></a>
    <a href="javascript:reportWindow('${hospitalReportUrl}',400, 400)" class="nav-link">HRM Status/Upload</a>
</c:if>