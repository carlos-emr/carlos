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

<%@page import="io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.SecRole" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.SecRoleDao" %>
<%@page import="io.github.carlos_emr.carlos.db.*" %>
<%@page import="java.sql.*" %>
<%@page import="java.util.*" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.DbConnectionFilter" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.log.LogAction" %>
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
<head>
    <title>Fix Roles in Notes</title>
</head>
<body>

<h2>Fix Roles in Notes</h2>


<%

    String action = request.getParameter("action");

    SecRoleDao roleDao = SpringUtils.getBean(SecRoleDao.class);
    List<SecRole> roles = roleDao.findAll();
%>


<ul>
    <li>If you have some notes which have a reporter_caisi_role of 0
    <li>This utility will fix them in a bulk way.</li>
    <li>Make sure you know what you are doing!</li>
</ul>
</h4>

<%if (action == null || !"run".equals(action)) { %>
<form action="<%=request.getContextPath()%>/admin/FixRolesOnNotes.do" method="post">
    Choose role to change to: <select name="role_to">
    <%
        for (SecRole role : roles) {
    %>
    <option value="<%=role.getId() %>"><%=role.getName() %>
    </option>
    <%} %>
</select>
    <input type="hidden" name="action" value="run"/>
    <input type="submit" value="Run Report"/>
</form>
<%
} else {
    String roleTo = request.getParameter("role_to");
    String error = "";
    if (roleTo == null || "0".equals(roleTo)) {
        error = "Must set valid destination role";
    }

    CaseManagementNoteDAO noteDao = SpringUtils.getBean(CaseManagementNoteDAO.class);
    PreparedStatement pstmt = null;
    try {
        pstmt = DbConnectionFilter.getThreadLocalDbConnection().prepareStatement("update casemgmt_note set reporter_caisi_role = ? where reporter_caisi_role  = 0");
        pstmt.setInt(1, Integer.parseInt(roleTo));
        pstmt.executeUpdate();
    } finally {
        if (pstmt != null)
            pstmt.close();
    }


%>
<!-- fix here -->
<H2>FIXED</H2>
<%
    }
%>
</body>
</html>
