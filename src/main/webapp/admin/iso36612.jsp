<!DOCTYPE html>
<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

--%>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.ISO36612Dao" %>
<%@ page
        import="java.util.*,ca.openosp.openo.report.data.*, java.util.Properties, ca.openosp.openo.billing.ca.on.administration.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>

    <%
        ISO36612Dao iso = SpringUtils.getBean(ISO36612Dao.class);
        boolean result = iso.reloadTable();

    %>

    <script type="text/javascript">
    </script>

    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageGSTControl"/></title>
        <link href="<%=request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet">
    </head>
    <body onload="loadData()">

    <h3>ISO 3661-2 Reload Manager</h3>

    <%if (result) { %>
    <p>Reloaded your ISO3661-2 (Province/Country codes) into DB</p>

    <% } else { %>
    <p>Error reloading your ISO3661-2 (Province/Country codes) into DB. Please check logs</p>

    <% } %>

    </body>
</html>
