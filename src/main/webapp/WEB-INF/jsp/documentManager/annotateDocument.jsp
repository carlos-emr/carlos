<%--

    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos

--%>
<%--
    Document annotation viewer.

    Purpose : Lets a provider mark up a stored PDF and save the result as a NEW document
              in the chart, optionally continuing straight to the fax flow. The received
              document is never modified.

    Renders : Server-produced page images from ManageDocument?method=showPage, with an SVG
              overlay per page. No PDF is parsed in the browser and no PDF library is
              loaded, so a crafted inbound fax cannot reach a client-side parser.

    Reached : documentManager/AnnotateDocument (AnnotateDocument2Action), which enforces
              _edoc write and circle-of-care before this page renders.

    Posts   : documentManager/SaveAnnotatedDocument as JSON with the CSRF-TOKEN header.
              The csrf-token.jspf include below is required: this page has no non-GET
              <form>, so CSRFGuard's client script would otherwise never populate a token
              and every save would be rejected with an HTML error the user never sees.

    Params  : docId, pageCount, documentTitle, demographicNo (set by the action)

    @since 2026-09
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%
    // Locks this page to its own origin. There is no third-party script here, so a strict
    // policy costs nothing and blocks injected script outright. The eForm render servlets
    // set a per-page policy the same way.
    response.setHeader("Content-Security-Policy",
            "default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'self'");
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="${pageContext.response.locale.language}">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><fmt:message key="faxAnnotateViewer.title"/></title>
    <link rel="stylesheet" href="<%=ctx%>/css/documentAnnotate.css"/>
</head>
<body>
<%-- Required: this page's only writes are fetch() POSTs, so the token needs bootstrapping. --%>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>

<header class="bar">
    <div class="bar-title" title="<carlos:encode value='${documentTitle}' context='htmlAttribute'/>">
        <carlos:encode value="${documentTitle}"/>
    </div>

    <div class="group" role="group" aria-label="<fmt:message key='faxAnnotateViewer.btn.select'/>">
        <button type="button" class="tool" data-tool="select" aria-pressed="true"
                title="<fmt:message key='faxAnnotateViewer.btn.select'/>">
            <fmt:message key="faxAnnotateViewer.btn.select"/>
        </button>
        <button type="button" class="tool" data-tool="highlight" aria-pressed="false"
                title="<fmt:message key='faxAnnotateViewer.btn.highlight'/>">
            <fmt:message key="faxAnnotateViewer.btn.highlight"/>
        </button>
        <button type="button" class="tool" data-tool="draw" aria-pressed="false"
                title="<fmt:message key='faxAnnotateViewer.btn.draw'/>">
            <fmt:message key="faxAnnotateViewer.btn.draw"/>
        </button>
        <button type="button" class="tool" data-tool="text" aria-pressed="false"
                title="<fmt:message key='faxAnnotateViewer.btn.addText'/>">
            <fmt:message key="faxAnnotateViewer.btn.addText"/>
        </button>
        <button type="button" class="tool" data-tool="date" aria-pressed="false"
                title="<fmt:message key='faxAnnotateViewer.btn.addDate'/>">
            <fmt:message key="faxAnnotateViewer.btn.addDate"/>
        </button>
        <button type="button" class="tool" data-tool="signature" aria-pressed="false"
                title="<fmt:message key='faxAnnotateViewer.btn.signature'/>">
            <fmt:message key="faxAnnotateViewer.btn.signature"/>
        </button>
    </div>

    <div class="group" role="group" aria-label="<fmt:message key='faxAnnotateViewer.label.colour'/>">
        <button type="button" class="swatch s-yellow" data-color="yellow" aria-pressed="true" aria-label="yellow"></button>
        <button type="button" class="swatch s-green" data-color="green" aria-pressed="false" aria-label="green"></button>
        <button type="button" class="swatch s-blue" data-color="blue" aria-pressed="false" aria-label="blue"></button>
        <button type="button" class="swatch s-pink" data-color="pink" aria-pressed="false" aria-label="pink"></button>
        <button type="button" class="swatch s-red" data-color="red" aria-pressed="false" aria-label="red"></button>
        <button type="button" class="swatch s-black" data-color="black" aria-pressed="false" aria-label="black"></button>
    </div>

    <div class="group">
        <button type="button" id="btnZoomOut" title="<fmt:message key='faxAnnotateViewer.btn.zoomOut'/>">&minus;</button>
        <button type="button" id="btnZoomIn" title="<fmt:message key='faxAnnotateViewer.btn.zoomIn'/>">+</button>
    </div>

    <div class="spacer"></div>

    <div class="group">
        <span class="count"><fmt:message key="faxAnnotateViewer.label.marks"/>: <span id="markCount">0</span></span>
        <button type="button" id="btnSave" class="primary" disabled>
            <fmt:message key="faxAnnotateViewer.btn.saveCopy"/>
        </button>
        <button type="button" id="btnSaveFax" class="primary" disabled>
            <fmt:message key="faxAnnotateViewer.btn.saveAndFax"/>
        </button>
    </div>
</header>

<div class="notice">
    <fmt:message key="faxAnnotateViewer.notice.newDocument"/>
    <span id="status" class="status" role="status" aria-live="polite"></span>
    <span id="savedLink"></span>
</div>

<main id="pages" class="pages" data-tool="select"></main>

<script>
    // Server-resolved configuration for documentAnnotate.js. Every value here is either a
    // number the action parsed or a localized string; nothing is interpolated into markup.
    window.CARLOS_ANNOTATE = {
        contextPath: '<carlos:encode value="<%=ctx%>" context="javaScript"/>',
        docId: ${docId},
        pageCount: ${pageCount},
        demographicNo: ${demographicNo},
        i18n: {
            pageLabel: '<fmt:message key="faxAnnotateViewer.label.page"/>',
            saving: '<fmt:message key="faxAnnotateViewer.status.saving"/>',
            saved: '<fmt:message key="faxAnnotateViewer.status.savedCopy"/>',
            saveFailed: '<fmt:message key="faxAnnotateViewer.alert.saveFailed"/>',
            promptText: '<fmt:message key="faxAnnotateViewer.prompt.text"/>',
            signatureHere: '<fmt:message key="faxAnnotateViewer.label.signatureHere"/>',
            openSaved: '<fmt:message key="faxAnnotateViewer.link.openSaved"/>'
        }
    };
</script>
<script src="<%=ctx%>/js/documentAnnotate.js"></script>
</body>
</html>
