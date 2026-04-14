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
<%@page import="java.net.URLEncoder" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String curProvider_no = (String) session.getAttribute("user");
    boolean isSiteAccessPrivacy = false;
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/error/SecurityError.do?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ page import="org.owasp.encoder.Encode" %>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotaddrecord.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
    </head>

    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th><font face="Helvetica" color="#FFFFFF">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.lotaddrecord.description"/>
                </font></th>
            </tr>
        </table>
        <%
            // All business logic (duplicate check, insert/restore) is handled by LotNrAddRecord2Action.
            // This JSP only displays the outcome and navigation links.
            String resultMsg = (String) request.getAttribute("resultMsg");
            String prevention = (String) request.getAttribute("prevention");
            if (prevention == null) prevention = "";
        %>
        <%= resultMsg != null ? Encode.forHtml(resultMsg) : "" %>
        <br/>
        <a href="${pageContext.request.contextPath}/admin/ViewLotNrAddRecordHtm.do?prevention=<%=URLEncoder.encode(prevention,"UTF-8")%>">Add Another Lot #
            to <%=Encode.forHtml(prevention)%>
        </a> <br/>
        <a href="${pageContext.request.contextPath}/admin/LotNrSearchResults.do?search_mode=search_prev&keyword=<%=URLEncoder.encode(prevention,"UTF-8")%>&orderby=prevention_type&dboperation=lotnr_search_prevention&limit1=0&limit2=10&button=submit">View
            Lots for <%=Encode.forHtml(prevention)%>
        </a>
    </center>
    </body>
</html>
