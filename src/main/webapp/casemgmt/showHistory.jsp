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
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ include file="/casemgmt/taglibs.jsp" %>
<%@page import="ca.openosp.openo.casemgmt.model.CaseManagementNote" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.notes" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_casemgmt.notes");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page
        import="org.springframework.web.context.*,org.springframework.web.context.support.*, ca.openosp.openo.PMmodule.service.ProviderManager, ca.openosp.openo.casemgmt.model.CaseManagementNote" %>
<%
    WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(getServletContext());
    ProviderManager pMgr = (ProviderManager) ctx.getBean(ProviderManager.class);
    pageContext.setAttribute("pMgr", pMgr);
%>
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Note History</title>
</head>
<body>
    <h3 style="text-align: center;">${title}</h3>
    <h3 style="text-align: center;">${demoName}</h3>
    <c:forEach var="note" items="${history}" varStatus="idx">
        <div style="width: 99%; background-color: #EFEFEF; font-size: 12px; border-left: thin groove #000000; border-bottom: thin groove #000000; border-right: thin groove #000000;">
            <div>
                <p>
                <c:out value="${note.note}" escapeXml="false" />
                </p>
            </div>
            <div style="color: #0000FF;">
                <c:if test="${not empty current[idx.index] and current[idx.index] == false}">
                    <div style="color: #FF0000;">REMOVED</div>
                </c:if>
                <c:if test="${note.archived == true}">
                    <div style="color: #336633;">ARCHIVED</div>
                </c:if>
    
                Documentation Date: <fmt:formatDate value="${note.observation_date}" pattern="dd-MMM-yyyy H:mm" /><br />
    
                <c:if test="${note.signed == true}">
                    Signed by
                    <c:out value="${pMgr.getProvider(note.signing_provider_no).formattedName}" />
                </c:if>
    
                <c:if test="${note.signed != true}">
                    Saved by
                    <c:out value="${note.provider.formattedName}" />:
                </c:if>
    
                <fmt:formatDate value="${note.update_date}" pattern="dd-MMM-yyyy H:mm" />
            </div>
        </div>
    </c:forEach>    
</body>
</html>
