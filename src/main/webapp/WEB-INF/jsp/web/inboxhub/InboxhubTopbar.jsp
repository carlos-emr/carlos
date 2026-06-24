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
<%--
    InboxhubTopbar — secondary navigation strip rendered above the inbox hub
    list view. Each link opens a feature popup via reportWindow(...) defined
    in /share/javascript/oscarMDSIndex.js.

    Inputs:
      providerNo — provider number of the logged-in user; set below from
        sessionScope.user and passed to the Forwarding Rules popup so the
        rules editor scopes to that provider.

    Conditional rendering:
      The "Doc Upload" link toggles between two endpoints based on the
      Carlos property `legacy_document_upload_enabled` (default true). When
      true, the legacy `ViewHtml5AddDocuments` popup is rendered; when
      false, the modern `ViewDocumentUploader` popup is rendered. Both
      branches render the same i18n label.

    Popup sizing:
      reportWindow's signature is (page, height, width). The popup args
      are intentionally height=800, width=1000 for the sub-pages that
      share a Bootstrap `.container` wrapper (Pending Docs, HL7 Lab
      Upload, Create Lab, Forwarding Rules, modern Doc Upload). Legacy
      Doc Upload keeps its compact 600x500 popup because it uses a fixed
      legacy layout rather than Bootstrap's container gutter. Incoming
      Docs keeps width=1200 because it uses a full-width `container-fluid`
      layout for queue triage. At
      width=1000 the container hits Bootstrap's lg breakpoint (>=992px)
      and resolves to a 960px max-width — producing the same ~20px page
      gutter on every popup. Diverging widths land on different
      breakpoints and visually drift apart.

    @since 2025-02-12
--%>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>


<c:set var="providerNo" value="${sessionScope.user}" />
<c:set var="contextPath" value="${pageContext.request.contextPath}" />

<%-- Ordered by workflow: incoming → pending → uploads → create → config --%>
<%-- Incoming Docs uses container-fluid, so it intentionally keeps a wider popup. --%>

<!-- Preview button -->
<input type="checkbox" class="btn-check btn-sm" name="viewMode2" id="btnViewMode2" autocomplete="off" onchange="fetchInboxhubDataByMode(this)" ${query.viewMode ? 'checked' : ''}>
<label class="btn btn-secondary btn-sm" id="btnViewModeLabel" for="btnViewMode2"><c:choose><c:when test="${query.viewMode}"><fmt:message key="inboxhub.form.listMode"/></c:when><c:otherwise><fmt:message key="inboxhub.form.previewMode"/></c:otherwise></c:choose></label>

<a href="javascript:reportWindow('${carlos:forJavaScript(contextPath)}/documentManager/ViewIncomingDocs',800,1200)" class="nav-link"><fmt:message key="inboxmanager.document.incomingDocs"/></a>
<a href="javascript:reportWindow('${carlos:forJavaScript(contextPath)}/documentManager/inboxManage?method=getDocumentsInQueues',800,1000)" class="nav-link"><fmt:message key="inboxmanager.document.pendingDocs"/></a>
<c:choose>
    <c:when test="${CarlosProperties.getInstance().getBooleanProperty('legacy_document_upload_enabled', 'true')}">
        <a href="javascript:reportWindow('${carlos:forJavaScript(contextPath)}/documentManager/ViewHtml5AddDocuments',600,500)" class="nav-link"><fmt:message key="inboxmanager.document.uploadDoc"/></a>
    </c:when>
    <c:otherwise>
        <a href="javascript:reportWindow('${carlos:forJavaScript(contextPath)}/documentManager/ViewDocumentUploader',800,1000)" class="nav-link"><fmt:message key="inboxmanager.document.uploadDoc"/></a>
    </c:otherwise>
</c:choose>
<a href="javascript:reportWindow('${carlos:forJavaScript(contextPath)}/lab/CA/ALL/insideLabUpload',800,1000)" class="nav-link"><fmt:message key="admin.admin.hl7LabUpload"/></a>
<a href="javascript:reportWindow('${carlos:forJavaScript(contextPath)}/oscarMDS/ViewCreateLab',800,1000)" class="nav-link"><fmt:message key="global.createLab" /></a>
<a href="javascript:reportWindow('${carlos:forJavaScript(contextPath)}/oscarMDS/ForwardingRules?providerNo=${carlos:forJavaScript(carlos:forUriComponent(providerNo))}',800,1000);" class="nav-link"><fmt:message key="inboxhub.topbar.forwardingRules"/></a>
