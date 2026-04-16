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
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>


<c:set var="providerNo" value="${sessionScope.user}" />
<c:set var="contextPath" value="${pageContext.request.contextPath}" />

<%-- Ordered by workflow: incoming → pending → uploads → create → config --%>
<a href="javascript:reportWindow('${e:forJavaScript(contextPath)}/documentManager/ViewIncomingDocs',800,1200)" class="nav-link"><fmt:message key="inboxmanager.document.incomingDocs"/></a>
<a href="javascript:reportWindow('${e:forJavaScript(contextPath)}/documentManager/inboxManage?method=getDocumentsInQueues',700,1100)" class="nav-link"><fmt:message key="inboxmanager.document.pendingDocs"/></a>
<c:if test="${CarlosProperties.getInstance().getBooleanProperty('legacy_document_upload_enabled', 'true')}">
    <a href="javascript:reportWindow('${e:forJavaScript(contextPath)}/documentManager/ViewHtml5AddDocuments',600,500)" class="nav-link"><fmt:message key="inboxmanager.document.uploadDoc"/></a>
</c:if>
<c:if test="${!CarlosProperties.getInstance().getBooleanProperty('legacy_document_upload_enabled', 'true')}">
    <a href="javascript:reportWindow('${e:forJavaScript(contextPath)}/documentManager/ViewDocumentUploader',800,1000)" class="nav-link"><fmt:message key="inboxmanager.document.uploadDoc"/></a>
</c:if>
<a href="javascript:reportWindow('${e:forJavaScript(contextPath)}/lab/CA/ALL/insideLabUpload',800,1000)" class="nav-link"><fmt:message key="admin.admin.hl7LabUpload"/></a>
<a href="javascript:reportWindow('${e:forJavaScript(contextPath)}/oscarMDS/SubmitLab',800,1000)" class="nav-link"><fmt:message key="global.createLab" /></a>
<a href="javascript:reportWindow('${e:forJavaScript(contextPath)}/oscarMDS/ForwardingRules?providerNo=${e:forJavaScript(providerNo)}');" class="nav-link"><fmt:message key="inboxhub.topbar.forwardingRules"/></a>
