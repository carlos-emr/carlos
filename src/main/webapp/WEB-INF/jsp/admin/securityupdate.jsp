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
<%--
    Result page of the Security Records edit form (admin/SecurityUpdate, SecurityUpdate2Action).

    SecurityUpdate2Action validates the form and saves it through SecurityRecordUpdateService; this
    page only reports what happened. It writes nothing.

    Request attributes (set by SecurityUpdate2Action):
        securityUpdateOutcome       saved | userNameInvalid | passwordInvalid | failed | editConflict
        securityUpdatePasswordError message key of a refused password (passwordInvalid only)
        securityUpdateConflictNo    security_no to reopen (editConflict only)

    editConflict: the form was rendered before another save changed the login
    (SecurityEditVersion). The action has already set HTTP 409 and Cache-Control: no-store; the
    page says that nothing was saved and offers to reopen the record as it is now.

    @since 2026-04-05
--%>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.userAdmin" rights="r"
                   reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.admin.web.SecurityUpdate2Action" %>
<%@ page import="io.github.carlos_emr.carlos.www.admin.SecurityUpdatePasswordValidator" %>
<%
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        return;
    }
    String outcome = (String) request.getAttribute(SecurityUpdate2Action.OUTCOME_ATTRIBUTE);
    String passwordError = (String) request.getAttribute(SecurityUpdate2Action.PASSWORD_ERROR_ATTRIBUTE);
%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <title><fmt:message key="admin.securityupdate.title"/></title>
    </head>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css"/>
    <body style="margin-top: 0; margin-left: 0; margin-right: 0;">
    <div style="text-align: center;">
        <table style="border: 0; border-spacing: 0; width: 100%;">
            <tr style="background-color: #486ebd;">
                <th style="padding: 0; text-align: center;"><span style="font-family: Helvetica; color: #FFFFFF;"><fmt:message key="admin.securityupdate.description"/></span></th>
            </tr>
        </table>
        <%
            if (SecurityUpdate2Action.OUTCOME_SAVED.equals(outcome)) {
        %>
        <p>
        <h2><fmt:message key="admin.securityupdate.msgUpdateSuccess"/> <carlos:encode value='<%= request.getParameter("provider_no") != null ? request.getParameter("provider_no") : "" %>' context="html"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
        </h2>
        <%
        } else if (SecurityUpdate2Action.OUTCOME_EDIT_CONFLICT.equals(outcome)) {
        %>
        <div id="securityEditConflict" role="alert">
            <h1><fmt:message key="admin.securityupdate.editConflict.title"/></h1>
            <p><fmt:message key="admin.securityupdate.editConflict.message"/></p>
            <p><fmt:message key="admin.securityupdate.editConflict.instructions"/></p>
            <c:if test="${not empty securityUpdateConflictNo}">
                <%-- The edit page opens a login by its security_no (keyword), as the search results link to it. --%>
                <c:set var="reopenUrl" value="${pageContext.request.contextPath}/admin/ViewSecurityUpdateSecurity?keyword=${carlos:forUriComponent(securityUpdateConflictNo)}"/>
                <p><a id="securityEditConflictReopen" href="${carlos:forHtmlAttribute(reopenUrl)}"><fmt:message key="admin.securityupdate.editConflict.btnReopen"/></a></p>
            </c:if>
        </div>
        <%
        } else if (SecurityUpdate2Action.OUTCOME_USER_NAME_INVALID.equals(outcome)) {
        %>
        <h1><fmt:message key="admin.securityupdate.msgUserNameInvalid"/></h1>
        <%
        } else if (SecurityUpdate2Action.OUTCOME_PASSWORD_INVALID.equals(outcome) && passwordError != null) {
        %>
        <h1><fmt:message key="<%=passwordError%>"><fmt:param value="<%=SecurityUpdatePasswordValidator.MAX_PASSWORD_LENGTH%>"/></fmt:message></h1>
        <%
        } else {
        %>
        <h1><fmt:message key="admin.securityupdate.msgUpdateFailure"/><carlos:encode value='<%= StringUtils.noNull(request.getParameter("provider_no")) %>' context="html"/>.</h1>
        <%
            }
        %>
        </p>
        <p></p>

    </div>
    </body>
</html>
