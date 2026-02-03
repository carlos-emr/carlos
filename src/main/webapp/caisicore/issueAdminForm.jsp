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


<%@ include file="/taglibs.jsp" %>
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

<title>MyIssues ~ Issue Details</title>
<p>Please fill in issue's information below:</p>
<!-- form action="/issueAdmin" focus="issueAdmin.code" onsubmit="return validateIssueAdminForm(this)" -->
<form action="${pageContext.request.contextPath}/issueAdmin.do" method="post" focus="issueAdmin.code">
    <input type="hidden" name="method" value="save"/>
    <input type="hidden" name="id" id="id"/>
    <input type="hidden" name="update_date_web" id="update_date_web"/>

    <div style="color: red">
    <%@ include file="messages.jsp" %>

    <table>
        <tr>
            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="issueAdmin.code"/>:</th>
            <td><input type="text" name="issueAdmin.code" id="issueAdmin.code" /></td>
        </tr>
        <tr>
            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="issueAdmin.description"/>:</th>
            <td><input type="text" name="issueAdmin.description" id="issueAdmin.description" /></td>
        </tr>
        <tr>
            <th><fmt:setBundle basename="oscarResources"/><fmt:message key="issueAdmin.role"/>:</th>
            <td>
                <%
                    String role = (String) request.getAttribute("issueRole");
                    pageContext.setAttribute("issue_role", role);
                %> <select name="issueAdmin.role">
                <option value="">&nbsp;</option>
                <c:forEach var="caisiRole" items="${caisiRoles}" varStatus="status">
                    <c:choose>
                        <c:when
                                test="${caisiRole.name == issueAdminForm.map.issueAdmin.role}">
                            <option value="<c:out value="${caisiRole.name}"/>" selected><c:out
                                    value="${caisiRole.name}"/></option>
                        </c:when>
                        <c:otherwise>
                            <option value="<c:out value="${caisiRole.name}"/>"><c:out
                                    value="${caisiRole.name}"/></option>
                        </c:otherwise>
                    </c:choose>
                </c:forEach>
            </select></td>
        </tr>
        <!--
<tr>
     <th><fmt:setBundle basename="oscarResources"/><fmt:message key="issueAdmin.update_date"/>: </th>
     <td><input type="text" name="issueAdmin.update_date" id="issueAdmin.update_date" /></td>
</tr>
-->
        <tr>
            <td></td>
            <td><input type="submit" name="submit" value="Save" class="button" />
                <!--
       <c:if test="${not empty param.id}">
          <input type="submit" name="submit" value="Delete“ class="button"          onclick="this.form.method.value='delete'"/>
       </c:if>
       	--> <input type="submit" name="submit" value="Cancel" class="button" onclick="this.form.method.value='cancel'"/>
            </td>
    </table>
</form>
<!-- html:javascript formName="issueAdminForm"/ -->
