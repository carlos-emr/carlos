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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.userAdmin" rights="r"
                   reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ page import="java.sql.*, java.util.*,java.security.*,ca.openosp.*,ca.openosp.openo.db.*" errorPage="/errorpage.jsp" %>
<%@ page import="ca.openosp.openo.log.LogAction,ca.openosp.openo.log.LogConst" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Security" %>
<%@ page import="ca.openosp.openo.commn.dao.SecurityDao" %>
<%@ page import="ca.openosp.openo.managers.SecurityManager" %>
<%@ page import="ca.openosp.MyDateFormat" %>
<%@ page import="ca.openosp.Misc" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%
    SecurityDao securityDao = SpringUtils.getBean(SecurityDao.class);
%>

<html>
    <script src="${pageContext.request.contextPath}/csrfguard"></script>
    <head>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityupdate.title"/></title>
    </head>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css"/>
    <body topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityupdate.description"/></font></th>
            </tr>
        </table>
        <%
	SecurityManager securityManager = SpringUtils.getBean(SecurityManager.class);

            String sPin = request.getParameter("pin");
            if (OscarProperties.getInstance().isPINEncripted()) sPin = Misc.encryptPIN(request.getParameter("pin"));

            int rowsAffected = 0;

            Security s = securityDao.find(Integer.parseInt(request.getParameter("security_no")));
            if (s != null) {
                s.setUserName(request.getParameter("user_name"));
                s.setProviderNo(request.getParameter("provider_no"));
                s.setBExpireset(request.getParameter("b_ExpireSet") == null ? 0 : Integer.parseInt(request.getParameter("b_ExpireSet")));
                s.setDateExpiredate(MyDateFormat.getSysDate(request.getParameter("date_ExpireDate")));
                s.setBLocallockset(request.getParameter("b_LocalLockSet") == null ? 0 : Integer.parseInt(request.getParameter("b_LocalLockSet")));
                s.setBRemotelockset(request.getParameter("b_RemoteLockSet") == null ? 0 : Integer.parseInt(request.getParameter("b_RemoteLockSet")));

                if (request.getParameter("password") == null || !"*********".equals(request.getParameter("password"))) {
    		s.setPassword(securityManager.encodePassword(request.getParameter("password")));
                    s.setPasswordUpdateDate(new java.util.Date());
                }

                if (request.getParameter("pin") == null || !"****".equals(request.getParameter("pin"))) {
                    s.setPin(sPin);
                    s.setPinUpdateDate(new java.util.Date());
                }

                if (request.getParameter("forcePasswordReset") != null && request.getParameter("forcePasswordReset").equals("1")) {
                    s.setForcePasswordReset(Boolean.TRUE);
                } else {
                    s.setForcePasswordReset(Boolean.FALSE);
                }

		if (request.getParameter("enableMfa") != null && request.getParameter("enableMfa").equals("1")) {
			s.setUsingMfa(Boolean.TRUE);
		} else {
			s.setUsingMfa(Boolean.FALSE);
		}

                s.setLastUpdateDate(new java.util.Date());

                securityDao.saveEntity(s);
                rowsAffected = 1;
            }


            if (rowsAffected == 1) {
                LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.UPDATE, LogConst.CON_SECURITY,
                        request.getParameter("security_no") + "->" + request.getParameter("user_name"), request.getRemoteAddr());
        %>
        <p>
        <h2><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityupdate.msgUpdateSuccess"/> <%=request.getParameter("provider_no")%>
        </h2>
        <%
        } else {
        %>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.securityupdate.msgUpdateFailure"/><%= request.getParameter("provider_no") %>.</h1>
        <%
            }
        %>
        </p>
        <p></p>

    </center>
    </body>
</html>
