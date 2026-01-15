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

--%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String curProvider_no = (String) session.getAttribute("user");

    boolean isSiteAccessPrivacy = false;
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


<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%isSiteAccessPrivacy = true; %>
</security:oscarSec>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="java.sql.*, java.util.*, ca.openosp.*" errorPage="/errorpage.jsp" %>
<%@ page import="ca.openosp.openo.log.LogAction,ca.openosp.openo.log.LogConst" %>
<%@ page import="ca.openosp.openo.log.*, ca.openosp.openo.db.*" %>

<%@page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>

<jsp:useBean id="apptMainBean" class="ca.openosp.AppointmentMainBean" scope="session"/>

<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.PreventionsLotNrs" %>
<%@ page import="ca.openosp.openo.commn.dao.PreventionsLotNrsDao" %>
<%
    PreventionsLotNrsDao PreventionsLotNrsDao = (PreventionsLotNrsDao) SpringUtils.getBean(PreventionsLotNrsDao.class);
    String prevention = request.getParameter("prevention");
    String lotNr = request.getParameter("lotnr");
%>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotdeleterecord.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
    </head>

    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th><font face="Helvetica" color="#FFFFFF">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotdeleterecord.description"/>
                </font></th>
            </tr>
        </table>
        <%
            String curUser_no = (String) session.getAttribute("user");

            List<String> lotnrs = PreventionsLotNrsDao.findLotNrs(prevention, false);
            if (!lotnrs.contains(lotNr)) {
        %>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotdeleterecord.msgNonExistentLotnr"/>
        <%
        } else {
            PreventionsLotNrs p = PreventionsLotNrsDao.findByName(prevention, lotNr, false);
            if (p != null) {
                p.setDeleted(true);
                PreventionsLotNrsDao.merge(p);
            }

            lotnrs = PreventionsLotNrsDao.findLotNrs(prevention, false);
            if (!lotnrs.contains(lotNr)) {
        %>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotdeleterecord.msgDeletionSuccess"/>
        <%
        } else {
        %>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotdeleterecord.msgDeletionFailure"/>
        <%
                }
            }
        %>

    </center>
    </body>
</html>
