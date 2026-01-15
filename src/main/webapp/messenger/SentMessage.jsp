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
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>

        <c:if test="${empty msgSessionBean}">
            <c:redirect url="index.jsp"/>
        </c:if>
        <c:if test="${not empty msgSessionBean}">
            <c:if test="${msgSessionBean.valid == false}">
                <c:redirect url="index.jsp"/>
            </c:if>
        </c:if>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.title"/></title>
        <link rel="stylesheet" type="text/css" href="encounterStyles.css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

        <script type="text/javascript">
            function BackToOscar() {
                if (opener.callRefreshTabAlerts) {
                    opener.callRefreshTabAlerts("oscar_new_msg");
                    setTimeout("window.close()", 100);
                } else {
                    window.close();
                }
            }
        </script>

        <style>
            .TopStatusBar {
                width: 100% !important;
                height: 100% !important;
            }
        </style>

    </head>

    <body class="BodyStyle" vlink="#0000FF">
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.msgMessenger"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td><h2><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.msgMessageSent"/></h2></td>
                        <td></td>
                        <td style="text-align: right">
                            <a href="javascript:void(0)"
                               onclick="javascript:popupPage(600,700,'<%= request.getContextPath() %>/oscarEncounter/About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">&nbsp;</td>
            <td class="MainTableRightColumn">
                <table>
                    <tr>
                        <td>&nbsp;</td>
                    </tr>
                    <tr>
                        <td>
                            <table cellspacing=3>
                                <tr>
                                    <td>
                                        <table class=messButtonsA cellspacing=0 cellpadding=3>
                                            <tr>
                                                <td class="messengerButtonsA"><a
                                                        href="${pageContext.request.contextPath}/messenger/CreateMessage.jsp"
                                                        class="messengerButtons">
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.btnCompose"/>
                                                </a></td>
                                            </tr>
                                        </table>
                                    </td>
                                    <td>
                                        <table class=messButtonsA cellspacing=0 cellpadding=3>
                                            <tr>
                                                <td class="messengerButtonsA"><a
                                                        href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp"
                                                        class="messengerButtons">
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessagebtnBack"/>
                                                </a></td>
                                            </tr>
                                        </table>
                                    </td>
                                    <td>
                                        <table class=messButtonsA cellspacing=0 cellpadding=3>
                                            <tr>
                                                <td class="messengerButtonsA"><a
                                                        href="javascript:BackToOscar()"
                                                        class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.btnExit"/></a></td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.SentMessage.msgMessageSentTo"/> <%= request.getAttribute("SentMessageProvs") %>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
