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

<%@ include file="/taglibs.jsp" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>

<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>

<%
    if (!authed) {
        return;
    }
%>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title><fmt:message key="admin.facility.list.title"/></title>
        <link rel="stylesheet" type="text/css" href='${pageContext.request.contextPath}/css/tigris.css'/>
        <link rel="stylesheet" type="text/css" href='${pageContext.request.contextPath}/css/displaytag.css'/>

        <script>
            function ConfirmDelete(name) {
                if (confirm("<fmt:message key='admin.facility.list.confirmDeletePrefix'/> " + name + "<fmt:message key='admin.facility.list.confirmDeleteSuffix'/>")) {
                    return true;
                }
                return false;
            }
        </script>
    <%@ include file="/WEB-INF/jsp/common/carlosTableAssets.jspf" %>
</head>
    <body>
    <h1><fmt:message key="admin.facility.list.heading"/></h1>
    <form action="${pageContext.request.contextPath}/FacilityManager" method="post">
        <c:if test="${not empty facilities}">
        <table class="carlos-table-spaced simple" id="facility" data-carlos-table data-page-size="0" data-default-sort="0" data-default-order="ascending">
<thead><tr>
            <th scope="col"><span data-table-sort><fmt:message key="admin.facility.list.header.name"/></span></th>
            <th scope="col"><span data-table-sort><fmt:message key="admin.facility.list.header.description"/></span></th>

            <th scope="col"></th>
        </tr></thead>
<tbody><c:forEach var="facility" items="${facilities}" varStatus="tableRowStatus"><tr class="${tableRowStatus.count % 2 == 0 ? 'even' : 'odd'}">
            <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(facility.name))}" data-sort-type="${carlos:tableSortType(facility.name)}">${carlos:forHtml(facility.name)}</td>
            <td data-sort-value="${carlos:forHtmlAttribute(carlos:tableSortValue(facility.description))}" data-sort-type="${carlos:tableSortType(facility.description)}">${carlos:forHtml(facility.description)}</td>

            <td>
                <a href="<%=request.getContextPath() %>/FacilityManager?method=edit&id=${carlos:forHtmlAttribute(facility.id)}">
                    <fmt:message key="admin.facility.list.linkEdit"/> </a>
            </td>
        </tr></c:forEach></tbody>
</table>
        </c:if>
<c:if test="${empty facilities}"><p class="table-empty"><fmt:message key="admin.facility.list.msgNoFacilities"/></p></c:if>
    </form>
    <!--
            <p><a href="< tml:rewrite action="/FacilityManager"/>?method=add">
            Add new facility </a></p>
    -->
    </body>
</html>
