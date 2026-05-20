<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>


<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.notes" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_casemgmt.notes");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%
    String caseNote_history = (String) request.getAttribute("caseNote_history");
    if (caseNote_history == null) {
        caseNote_history = "";
    }
%>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Note History</title>
    <c:set var="ctx" value="${pageContext.request.contextPath}"
           scope="request"/>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(ctx)}/css/casemgmt.css"
          type="text/css">

</head>
<body bgcolor="#eeeeff">
<form action="<%= request.getContextPath() %>/CaseManagementEntry">
<br>
<b>Archived Note Update History</b>
<br>
<br>
Client name:
<I> <c:if test="${not empty requestScope.demoName}">
    ${carlos:forHtml(requestScope.demoName)}
</c:if>
<c:if test="${empty requestScope.demoName}">
    ${carlos:forHtml(param.demoName)}
</c:if> </I>
<br>
&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; Age:
<I> <c:if test="${not empty requestScope.demoName}">
    ${carlos:forHtml(requestScope.demoAge)}
</c:if>
<c:if test="${empty requestScope.demoName}">
    ${carlos:forHtml(param.demoAge)}
</c:if> </I>
<br>
&nbsp; &nbsp; &nbsp; &nbsp; &nbsp; &nbsp; DOB:
<I> <c:if test="${not empty requestScope.demoName}">
    ${carlos:forHtml(requestScope.demoDOB)}
</c:if>
<c:if test="${empty requestScope.demoName}">
    ${carlos:forHtml(param.demoDOB)}
</c:if> </I>
<br>
<br>


<input type="button" value=" Close This Page " onclick="self.close()">
<br>
<table width="400" border="0">
    <tr>
        <td class="fieldValue">
            <textarea name="caseNote_history" cols="107" rows="29" wrap="soft">
                <carlos:encode value='<%= caseNote_history %>' context="html"/>                       
            </textarea>
        </td>
    </tr>
    <br>

    </form>
</body>
</html>
