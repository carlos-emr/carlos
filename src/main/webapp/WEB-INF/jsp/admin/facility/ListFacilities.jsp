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
        <link rel="stylesheet" type="text/css" href='${request.contextPath}/css/tigris.css'/>
        <link rel="stylesheet" type="text/css" href='${request.contextPath}/css/displaytag.css'/>

        <script>
            function ConfirmDelete(name) {
                if (confirm("<fmt:message key='admin.facility.list.confirmDeletePrefix'/> " + name + "<fmt:message key='admin.facility.list.confirmDeleteSuffix'/>")) {
                    return true;
                }
                return false;
            }
        </script>
    </head>
    <body>
    <h1><fmt:message key="admin.facility.list.heading"/></h1>
    <form action="${pageContext.request.contextPath}/FacilityManager" method="post">
        <display:table class="simple" cellspacing="2" cellpadding="3"
                       id="facility" name="facilities" export="false" pagesize="0"
                       requestURI="/FacilityManager">
            <display:setProperty name="paging.banner.placement" value="bottom"/>
            <display:setProperty name="paging.banner.item_name" value="agency"/>
            <display:setProperty name="paging.banner.items_name" value="facilities"/>
            <fmt:message key="admin.facility.list.msgNoFacilities" var="noFacilitiesMessage"/>
            <display:setProperty name="basic.msg.empty_list" value="${noFacilitiesMessage}"/>

            <display:column property="name" sortable="true" titleKey="admin.facility.list.header.name"/>
            <display:column property="description" sortable="true" titleKey="admin.facility.list.header.description"/>

            <display:column sortable="false" title="">
                <a href="<%=request.getContextPath() %>/FacilityManager?method=edit&id=${carlos:forHtmlAttribute(facility.id)}">
                    <fmt:message key="admin.facility.list.linkEdit"/> </a>
            </display:column>
            <!--
            < isplay:column sortable="false" title="">
            <a href="< tml:rewrite action="/FacilityManager"/>?method=delete&id=< :out value="${facility.id}"/>&name=< :out value="${facility.name}"/>"
            onclick="return ConfirmDelete('< :out value="${facility.name}"/>')">
            Delete </a>
            </ isplay:column>
            -->
        </display:table>
    </form>
    <!--
            <p><a href="< tml:rewrite action="/FacilityManager"/>?method=add">
            Add new facility </a></p>
    -->
    </body>
</html>
