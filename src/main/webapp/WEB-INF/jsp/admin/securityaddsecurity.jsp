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

<%@page import="io.github.carlos_emr.carlos.www.admin.SecurityAddSecurityHelper" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ page import="java.sql.*, java.util.*,java.security.*,io.github.carlos_emr.*,io.github.carlos_emr.carlos.db.*" errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogAction,io.github.carlos_emr.carlos.log.LogConst" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Security" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SecurityDao" %>
<%
    SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);
%>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityaddsecurity.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
    </head>
    <body topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityaddsecurity.description"/></font></th>
            </tr>
        </table>
        <%
            SecurityAddSecurityHelper helper = new SecurityAddSecurityHelper();
            helper.addProvider(pageContext);
        %>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="${message}"/></h1>

    </center>
    </body>
</html>
