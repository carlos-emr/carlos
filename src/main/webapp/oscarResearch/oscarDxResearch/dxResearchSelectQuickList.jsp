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
    dxResearchSelectQuickList.jsp - Select an existing quick list to edit

    Purpose:
    Popup that displays a dropdown of all available quick lists. On submit,
    posts to dxResearchLoadQuickListItems.do which loads the selected list's
    items and opens dxResearchEditQuickList.jsp.

    Opened from dxResearchCustomization.jsp "Edit Quick List" button.

    Request Attributes:
    - allQuickLists: dxQuickListBeanHandler containing available quick lists

    @since 2006-01-01 (original OSCAR implementation)
--%>

<%@ page import="java.util.*,io.github.carlos_emr.carlos.report.pageUtil.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>

<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.selectQuickList"/></title>
        <script type="text/javascript">
            function setfocus() {
                window.focus();
                window.resizeTo(450, 300);
            }
        </script>
    </head>

    <body onload="setfocus()">
    <div class="container" style="padding-top:10px;">

        <%-- Page header matching search.jsp / report.jsp pattern --%>
        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" class="page-header-icon" viewBox="0 0 16 16">
                    <path d="M12.146.146a.5.5 0 0 1 .708 0l3 3a.5.5 0 0 1 0 .708l-10 10a.5.5 0 0 1-.168.11l-5 2a.5.5 0 0 1-.65-.65l2-5a.5.5 0 0 1 .11-.168zM11.207 2.5 13.5 4.793 14.793 3.5 12.5 1.207zm1.586 3L10.5 3.207 4 9.707V10h.5a.5.5 0 0 1 .5.5v.5h.5a.5.5 0 0 1 .5.5v.5h.293zm-9.761 5.175-.106.106-1.528 3.821 3.821-1.528.106-.106A.5.5 0 0 1 5 12.5V12h-.5a.5.5 0 0 1-.5-.5V11h-.5a.5.5 0 0 1-.468-.325"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgDxResearch"/>
            </h4>
        </div>

        <form action="${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/dxResearchLoadQuickListItems.do" method="post">
            <input type="hidden" name="forward" value="error"/>

            <div style="margin-top:15px;">
                <label class="form-label">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.pleaseSelectAQuickList"/>
                </label>
                <select class="form-select" name="quickListName">
                    <c:forEach var="quickLists" items="${allQuickLists.dxQuickListBeanVector}">
                        <option value="${quickLists.quickListName}" ${quickLists.lastUsed == 'true' ? 'selected' : ''}>
                            <c:out value="${quickLists.quickListName}"/>
                        </option>
                    </c:forEach>
                </select>
            </div>

            <div style="margin-top:15px; display:flex; gap:8px;">
                <input type="submit" class="btn btn-primary" name="Button" value="Continue"/>
                <input type="button" class="btn btn-secondary" name="Button"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>"
                       onclick="window.close()">
            </div>
        </form>

    </div>
    </body>
</html>
