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

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.lab.ca.on.*" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils,org.apache.commons.text.StringEscapeUtils" %>
<%@page import="org.apache.logging.log4j.Logger,io.github.carlos_emr.carlos.commn.dao.OscarLogDao,io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.inboxhub.query.InboxhubQuery" %>
<%@page import="io.github.carlos_emr.carlos.mds.data.CategoryData" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css">
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.css" /> 
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" media="screen">
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/jquery/jquery-ui.theme-1.14.2.min.css" />
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.14.2.min.css" />
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/jquery/jquery-ui.structure-1.14.2.min.css" />
    <link href="${pageContext.request.contextPath}/css/fontawesome-all.min.css" rel="stylesheet">
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/share/css/global.css"/>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/web/css/Inboxhub.css?v=<%= System.currentTimeMillis() %>"/>

    <script type="text/javascript" src="${pageContext.request.contextPath}/library/flatpickr/flatpickr.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-compat.js"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <script type="text/javascript" charset="utf8" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/dompurify/purify.min.js"></script>
    <script src="${pageContext.request.contextPath}/share/javascript/oscarMDSIndex.js"></script>
    <title>Inboxhub</title>
</head>
<body>
<jsp:include page="/images/spinner.jsp" flush="true"/>
<script>
    const contextPath = "<e:forJavaScript value='${pageContext.request.contextPath}' />";

    /**
     * Toggles the inbox search sidebar between collapsed (hidden) and expanded.
     * The narrow toggle strip remains visible and shows an arrow indicator.
     * Collapsed by default to maximize the results table area.
     */
    function toggleInboxSidebar() {
        var sidebar = document.getElementById('inbox-sidebar');
        var toggle = document.getElementById('inbox-sidebar-toggle');
        if (sidebar.style.display === 'none') {
            sidebar.style.display = '';
            toggle.textContent = '\u25C0 Search';
        } else {
            sidebar.style.display = 'none';
            toggle.textContent = '\u25B6 Search';
        }
    }
</script>
<input type="hidden" id="ctx" value="<e:forHtmlAttribute value='${pageContext.request.contextPath}' />";/>
<div class="container-fluid overflow-hidden">
    <div class="row">
        <nav class="inbox-topbar">
            <jsp:include page="InboxhubTopbar.jsp"/>
            <button type="button" class="btn btn-secondary btn-sm" onclick="window.close();" style="margin-left:auto;">Back</button>
        </nav>
    </div>
    <div class="row flex-nowrap">
        <%-- Collapsible search sidebar: collapsed by default, toggles via the narrow strip --%>
        <div id="inbox-sidebar-toggle" class="px-0" onclick="toggleInboxSidebar()"
             title="Toggle search panel"
             style="width:20px;cursor:pointer;background:#e9ecef;display:flex;align-items:center;justify-content:center;border-right:1px solid #ccc;flex-shrink:0;writing-mode:vertical-rl;font-size:11px;color:#666;user-select:none;">
            &#9654; Search
        </div>
        <div id="inbox-sidebar" class="col-auto px-0 m-1" style="display:none;">
            <div class="bg-light text-dark inbox-form" style="display: inline-block;">
                <jsp:include page="InboxhubForm.jsp"/>
            </div>
        </div>
        <div class="col px-0 m-1" style="overflow-y: auto;">
            <div class="bg-light text-dark">
                <div id="inboxhubMode"> 
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>