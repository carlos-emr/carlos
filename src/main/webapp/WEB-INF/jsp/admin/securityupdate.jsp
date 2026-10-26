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
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%-- Writes belong to SecurityUpdate2Action; this view only reports confirmed results. --%>
<html>
<head>
    <title><fmt:message key="admin.securityupdate.title"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/web.css"/>
</head>
<body>
<% if (Boolean.TRUE.equals(request.getAttribute("securityUpdateCommitted"))) { %>
    <h2 id="security-update-ok"><fmt:message key="admin.securityupdate.msgUpdateSuccess"/>
        <carlos:encode value='<%= java.util.Objects.toString(request.getParameter("provider_no"), "") %>' context="html"/>
    </h2>
<% } else { %>
    <h1><fmt:message key="admin.securityupdate.msgUpdateFailure"/></h1>
<% } %>
</body>
</html>
