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

--%>



<%-- Updated by Eugene Petruhin on 08 jan 2009 while fixing #2482832 & #2494061 --%>

<%@ page import="ca.openosp.openo.casemgmt.web.formbeans.CaseManagementEntryFormBean" %>
<%@ include file="/casemgmt/taglibs.jsp" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_casemgmt.issues" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_casemgmt.issues");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    String demographicNo = request.getParameter("demographicNo");
    String sessionFrmName = "caseManagementEntryForm" + demographicNo;
    CaseManagementEntryFormBean form = (CaseManagementEntryFormBean) session.getAttribute(sessionFrmName);
    pageContext.setAttribute("caseManagementEntryForm", form);
%>

<html>
<head>
    <title>Issue Search</title>
    <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
    <link rel="stylesheet" href="<c:out value="${ctx}"/>/css/casemgmt.css" type="text/css">
</head>

<body>
<form action="<%= request.getContextPath() %>/CaseManagementEntry.do">
    <c:url value="/casemgmt/CaseManagementEntry.jsp?demographicNo=${param.demographicNo}&providerNo=${param.providerNo}&demoName=${requestScope.demoName}&demoAge=${requestScope.demoAge}&demoDOB=${requestScope.demoDOB}"
           var="url"/>
    <script type="text/javascript">
        function backToNote(from) {

            if (from == null) location.href = "<c:out value='${url}' escapeXml='false'/>";
            else location.href = "<c:out value='${url}' escapeXml='false'/>" + "&from=" + from;
            return false;
        }


        function addIssue(form) {
            form.method.value = 'issueAdd';
            var btn = document.getElementById('submit_add_issue');
            btn.disabled = true;
            btn.value = 'processing';
            return true;
        }

    </script>
    <input type="hidden" name="demographicNo" id="demographicNo"/>
    <input type="hidden" name="providerNo" id="providerNo"/>
    <input type="hidden" name="method" value="issueSearch"/>
    <input type="hidden" name="lastPage" value="true"/>
    <input type="hidden" name="change_diagnosis" value="<c:out value="${change_diagnosis}"/>"/>
    <input type="hidden" name="change_diagnosis_id" value="<c:out value="${change_diagnosis_id}"/>"/>

    <c:if test="${param.from=='casemgmt'||requestScope.from=='casemgmt'}">
        <input type="hidden" name="from" value="casemgmt"/>
    </c:if>

    <b>Client name: <I><c:out value="${requestScope.demoName}"/></I></b>
    <br><br>

    <P><b>Search the Issue </b></P>
    <input type="text" name="searString" />
    <input type="submit" value="search" onclick="this.form.method.value='issueSearch';" />

    <c:if test="${showList == 'true'}">
        <P><b>Issue List</b></P>
        <table width="100%" border="0" cellpadding="0" cellspacing="1" bgcolor="#C0C0C0">
            <tr class="title">
                <td></td>
                <td>Issue Location</td>
                <td>Issue Code</td>
                <td>Issue Description</td>
                <td>Issue Role</td>
            </tr>

                <%-- table contents --%>

                <c:forEach var="newIssueCheckList" items="${newIssueCheckList}" varStatus="status">
                    <tr bgcolor="${status.index % 2 == 0 ? '#EEEEFF' : 'white'}" align="center">
                        <td>
                            <input type="checkbox" name="newIssueCheckList[${status.index}].checked" value="true" ${newIssueCheckList.checked ? 'checked' : ''} />
                        </td>
                        <td>
                            <c:if test="${newIssueCheckList.communityString == 'false'}">
                                Local
                            </c:if>
                            <c:if test="${newIssueCheckList.communityString != 'false'}">
                                Community
                            </c:if>
                        </td>
                        <td>${newIssueCheckList.issue.code}</td>
                        <td bgcolor="${newIssueCheckList.issue.priority == 'allergy' ? 'yellow' : ''}">
                            ${newIssueCheckList.issue.description}
                        </td>
                        <td>${newIssueCheckList.issue.role}</td>
                    </tr>
                </c:forEach>
        </table>
        <br>
        <input type="button" value="add checked issue" name="submit_add_issue"
               onclick="this.form.method.value='issueAdd'; this.disabled = true; this.className='processing' ;this.form.submit();"/>
    </c:if>

    <c:if test="${from == 'casemgmt'}">
        <input type="submit" value="back to notes" onclick="this.form.method.value='edit';backToNote('casemgmt');return false;" />
    </c:if>
    <c:choose>
        <c:when test="${from != 'casemgmt'}">
            <input type="submit" value="back to notes" onclick="this.form.method.value='edit';backToNote(); return false;" />
        </c:when>
    </c:choose>

</form>
</body>
</html>
