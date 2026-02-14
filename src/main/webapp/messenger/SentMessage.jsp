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
  - Displays success confirmation
  - Shows message details (recipients, subject)
  - Provides navigation options (close window, new message, inbox)
  - Auto-refresh of parent window if in popup mode

  Security:
  - Requires "_msg" object with read ("r") permissions
  - Session validation through msgSessionBean

  Session dependencies:
  - msgSessionBean: Must be valid for page access
  - Contains sent message details for display

  UI elements:
  - Success message with sent details
  - Action buttons for next steps
  - Auto-close timer option (if configured)

  @since 2003
--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%
    // Build role string for security validation
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
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
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script src="<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js"></script>

        <c:if test="${empty msgSessionBean}">
            <c:redirect url="index.jsp"/>
        </c:if>
        <c:if test="${not empty msgSessionBean}">
            <c:if test="${msgSessionBean.valid == false}">
                <c:redirect url="index.jsp"/>
            </c:if>
        </c:if>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.title"/></title>
        <link href="<%=request.getContextPath()%>/css/bootstrap.css" rel="stylesheet">
        <link href="<%=request.getContextPath()%>/css/bootstrap-responsive.css" rel="stylesheet">
        <link href="<%=request.getContextPath()%>/css/fontawesome-all.min.css" rel="stylesheet">

        <script type="text/javascript">
            function BackToOscar() {
                if (opener && opener.callRefreshTabAlerts) {
                    opener.callRefreshTabAlerts("oscar_new_msg");
                    setTimeout(function() { window.close(); }, 100);
                } else {
                    window.close();
                }
            }
        </script>

    </head>

    <body>

    <table style="width:100%">
        <tr>
            <td style="width:1%"></td>
            <td style="width:80%; text-align:left;">
                <h4>
                    <i class="fa-solid fa-envelope"></i>&nbsp;
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.msgMessageSent"/>
                </h4>
            </td>
            <td style="width:1%"></td>
        </tr>
    </table>

    <div class="well">
        <ul class="nav nav-tabs">
            <li>
                <a href="${pageContext.request.contextPath}/messenger/CreateMessage.jsp">
                    <i class="fa-solid fa-pen-to-square"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.btnCompose"/>
                </a>
            </li>
            <li>
                <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp">
                    <i class="fa-solid fa-inbox"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessagebtnBack"/>
                </a>
            </li>
        </ul>

        <div style="margin-top:15px;">
            <p>
                <i class="fa-solid fa-check" style="color:green;"></i>&nbsp;
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.msgMessageSentTo"/> <%= request.getAttribute("SentMessageProvs") %>
            </p>
        </div>
    </div>

    <table style="width:100%">
        <tr>
            <td>
                <a href="<%=request.getContextPath()%>/messenger/DisplayMessages.jsp" class="btn btn-link">
                    <i class="fa-solid fa-inbox"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessagebtnBack"/>
                </a>
            </td>
            <td style="text-align:right;">
                <a href="javascript:BackToOscar()" class="btn btn-link">
                    <i class="fa-solid fa-arrow-right-from-bracket"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.btnExit"/>
                </a>
            </td>
        </tr>
    </table>

    </body>
</html>
