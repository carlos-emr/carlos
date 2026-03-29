<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%--
    dxResearchCustomization.jsp - Disease registry quick list administration hub

    Purpose:
    Provides navigation to the three quick list management functions:
    - Add New Quick List (dxResearchNewQuickList.jsp)
    - Edit Quick List (dxResearchLoadQuickList.do → dxResearchSelectQuickList.jsp)
    - Edit Associations (dxResearchSelectAssociations.jsp)

    Opened as a popup from the "add/edit" link in dxQuickList.jsp.

    @since 2006-01-01 (original OSCAR implementation)
--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <title><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.title"/></title>
        <%@ include file="/includes/global-head.jspf" %>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" href="${pageContext.servletContext.contextPath}/oscarResearch/oscarDxResearch/dxResearch.css"/>
    </head>

    <body>
    <div class="container">
<%
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>

        <%-- Page header matching search.jsp / report.jsp / tickler pattern --%>
        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" class="page-header-icon" viewBox="0 0 16 16">
                    <path d="M8 4.754a3.246 3.246 0 1 0 0 6.492 3.246 3.246 0 0 0 0-6.492M5.754 8a2.246 2.246 0 1 1 4.492 0 2.246 2.246 0 0 1-4.492 0"/>
                    <path d="M9.796 1.343c-.527-1.79-3.065-1.79-3.592 0l-.094.319a.873.873 0 0 1-1.255.52l-.292-.16c-1.64-.892-3.433.902-2.54 2.541l.159.292a.873.873 0 0 1-.52 1.255l-.319.094c-1.79.527-1.79 3.065 0 3.592l.319.094a.873.873 0 0 1 .52 1.255l-.16.292c-.892 1.64.901 3.434 2.541 2.54l.292-.159a.873.873 0 0 1 1.255.52l.094.319c.527 1.79 3.065 1.79 3.592 0l.094-.319a.873.873 0 0 1 1.255-.52l.292.16c1.64.893 3.434-.902 2.54-2.541l-.159-.292a.873.873 0 0 1 .52-1.255l.319-.094c1.79-.527 1.79-3.065 0-3.592l-.319-.094a.873.873 0 0 1-.52-1.255l.16-.292c.893-1.64-.902-3.433-2.541-2.54l-.292.159a.873.873 0 0 1-1.255-.52zM8 1a7 7 0 1 1 0 14A7 7 0 0 1 8 1"/>
                </svg>
                &nbsp;<fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.title"/>
            </h4>
        </div>

        <div style="display:flex; gap:10px; margin-top:15px; flex-wrap:wrap;">
            <input type="button" class="btn btn-primary"
                   onclick="popupPage(230,600,'oscarResearch/oscarDxResearch/dxResearchNewQuickList.jsp')"
                   value="<fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.addNewQuickList"/>"/>
            <input type="button" class="btn btn-primary"
                   onclick="popupPage(230,600,'oscarResearch/oscarDxResearch/dxResearchLoadQuickList.do')"
                   value="<fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.editQuickList"/>"/>
            <input type="button" class="btn btn-primary"
                   onclick="popupPage(230,600,'oscarResearch/oscarDxResearch/dxResearchSelectAssociations.jsp')"
                   value="<fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.editAssociations"/>"/>
        </div>

    </div>
    </body>
</html>
