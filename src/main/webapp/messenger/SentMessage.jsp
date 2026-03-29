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
  SentMessage.jsp - Message sent confirmation page

  This JSP page displays a confirmation message after successfully sending
  a message through the messaging system. It provides feedback to the user
  and offers navigation options to continue working.

  Main features:
  - Displays success confirmation via Bootstrap alert
  - Shows sent confirmation with OWASP-encoded recipient list
  - Provides navigation options (compose new, inbox, exit)
  - Parent window notification on exit (via callRefreshTabAlerts)

  Security:
  - Requires "_msg" object with read ("r") permissions
  - Session validation through msgSessionBean (redirects to index.jsp if invalid)
  - OWASP encoding on recipient list output

  Session dependencies:
  - msgSessionBean: Must be valid for page access
  - SentMessageProvs: Request attribute containing comma-separated recipient names

  Frontend Dependencies:
  - Bootstrap 5.3.3 (alert component, button styling)
  - Font Awesome 3.x (compose, inbox, exit icons)
  - global.js (popupPage utility)

  @since 2002-11-08
--%>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<fmt:setBundle basename="oscarResources"/>
<%
    // Build role string for security validation
    String userrole = (String) session.getAttribute("userrole");
    String user = (String) session.getAttribute("user");
    String roleName$ = (userrole != null ? userrole : "") + "," + (user != null ? user : "");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<!DOCTYPE html>
<html>
    <head>
<script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
<link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet" type="text/css">
<%-- global.css: CARLOS color overrides for Bootstrap (messenger pages don't use global-head.jspf) --%>
<link rel="stylesheet" href="<%=request.getContextPath() %>/share/css/global.css">
<link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">

        <c:if test="${empty msgSessionBean}">
            <c:redirect url="index.jsp"/>
        </c:if>
        <c:if test="${not empty msgSessionBean}">
            <c:if test="${msgSessionBean.valid == false}">
                <c:redirect url="index.jsp"/>
            </c:if>
        </c:if>
        <title><fmt:message key="messenger.SentMessage.title"/></title>
        <script type="text/javascript" src="<%= request.getContextPath() %>/messenger/messenger-common.js"></script>

        <style>
            .TopStatusBar {
                width: 100% !important;
                height: 100% !important;
            }

        </style>

    </head>

 <body class="BodyStyle" >
<table class="MainTable" id="scrollNumber1" style="width:100%; margin-top: 10px;">
	<tr class="MainTableTopRow">
		<td class="MainTableTopRowLeftColumn"><h4>&nbsp;<i class="fa-solid fa-envelope" title='<fmt:message key="messenger.DisplayMessages.msgMessenger"/>'></i>&nbsp;<fmt:message
			key="messenger.SentMessage.msgMessenger" />: <fmt:message
					key="messenger.SentMessage.msgMessageSent" /></h4></td>
		<td class="MainTableTopRowRightColumn" >
		<table class="TopStatusBar" style="width:100%;">
			<tr>
				<td style="text-align: right;">
            <i class="fa-solid fa-circle-question"></i>
            <a href="javascript:void(0)" onClick ="popupPage(700,960,''+'Messenger sent')"><fmt:message key="app.top1"/></a>
            <i class="fa-solid fa-circle-info" style="margin-left:10px;"></i>
            <a href="javascript:void(0)"  onClick="window.open('<%=request.getContextPath()%>/encounter/About.jsp','About CARLOS','scrollbars=1,resizable=1,width=800,height=600,left=0,top=0')" ><fmt:message key="global.about" /></a>
        </td>
			</tr>
		</table>
		</td>
	</tr>
</table>
<div class="alert alert-success" role="alert" style="margin-left:20px; margin-right:20px; margin-top: 20px;">
    <fmt:message
					key="messenger.SentMessage.msgMessageSentTo" /> <%= Encode.forHtml(request.getAttribute("SentMessageProvs") != null ? request.getAttribute("SentMessageProvs").toString() : "") %>
</div>
<div style="width:100%; margin-left:10px; margin-top: 50px;">
<a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/messenger/CreateMessage.jsp">
    <i class="fa-solid fa-pencil"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.btnCompose"/></a>
<a class="btn btn-outline-secondary" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp">
    <i class="fa-solid fa-inbox"></i>&nbsp;<fmt:message key="messenger.SentMessagebtnBack" /></a>
<a class="btn btn-outline-secondary" href="javascript:BackToCarlos()">
    <i class="fa-solid fa-right-from-bracket"></i>&nbsp;<fmt:message key="messenger.SentMessage.btnExit" /></a>
</div>
</body>
</html>
