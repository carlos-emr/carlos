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
    dxResearchNewQuickList.jsp - Create a new diagnosis quick list

    Purpose:
    Popup form that accepts a name for a new quick list. On submit, posts to
    dxResearchLoadQuickListItems.do which creates the list and opens the
    edit view (dxResearchEditQuickList.jsp).

    Opened from dxResearchCustomization.jsp "Add New Quick List" button.

    @since 2006-01-01 (original OSCAR implementation)
--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.selectQuickList"/></title>
        <script type="text/javascript">
            function setfocus() {
                window.focus();
                window.resizeTo(450, 280);
            }
        </script>
    </head>

    <body onload="setfocus()">
    <div class="container pt-2">

        <%-- Page header matching search.jsp / report.jsp pattern --%>
        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" class="page-header-icon" viewBox="0 0 16 16">
                    <path d="M2 0a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V2a2 2 0 0 0-2-2zm6.5 4.5v3h3a.5.5 0 0 1 0 1h-3v3a.5.5 0 0 1-1 0v-3h-3a.5.5 0 0 1 0-1h3v-3a.5.5 0 0 1 1 0"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxResearch.msgDxResearch"/>
            </h4>
        </div>

        <form action="${pageContext.request.contextPath}/oscarResearch/oscarDxResearch/dxResearchLoadQuickListItems.do" method="post">
            <input type="hidden" name="forward" value="error"/>

            <div class="mt-3">
                <label class="form-label" for="quickListName">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarResearch.oscarDxResearch.dxCustomization.pleaseEnterTheNewQuickListName"/>:
                </label>
                <input type="text" class="form-control" name="quickListName" id="quickListName"/>
            </div>

            <div class="mt-3 d-flex gap-2">
                <input type="submit" class="btn btn-primary" name="Button"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnContinue"/>"/>
                <input type="button" class="btn btn-secondary" name="Button"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnClose"/>"
                       onclick="window.close()">
            </div>
        </form>

    </div>
    </body>
</html>
