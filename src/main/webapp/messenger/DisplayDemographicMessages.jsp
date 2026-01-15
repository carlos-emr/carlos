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
  DisplayDemographicMessages.jsp - Displays messages associated with a specific patient
  
  This JSP page shows all messages linked to a particular patient demographic record.
  It provides a filtered view of the messaging system focused on patient-related
  communications, useful for reviewing clinical correspondence and care coordination.
  
  Main features:
  - Lists all messages associated with a demographic ID
  - Sortable columns (date, subject, sender, status)
  - Pagination support for large message lists
  - Integration with patient encounter workflow
  - Quick access to message details and actions
  
  Security:
  - Requires "_msg" object with read ("r") permissions
  - Session validation through msgSessionBean
  
  Request parameters:
  - demographic_no: Patient ID to filter messages
  - orderby: Sort column and direction
  - moreMessages: Pagination flag
  
  Session attributes:
  - msgSessionBean: Message display session state
  - orderby: Current sort preference
  
  Display settings:
  - Initial display: 20 messages
  - Expandable to show all messages
  
  @since 2003
--%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicData" %>
<%@ page import="ca.openosp.openo.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="ca.openosp.openo.messenger.data.MsgDisplayMessage" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    // Build role string for security check
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    // Exit if user is not authorized
    if (!authed) {
        return;
    }
%>

<%
    // Handle sorting logic - toggle between ascending and descending
    // Prefix with "!" indicates descending order
    if (request.getParameter("orderby") != null) {
        String orderby = request.getParameter("orderby");
        String sessionOrderby = (String) session.getAttribute("orderby");
        if (sessionOrderby != null && sessionOrderby.equals(orderby)) {
            // Toggle sort direction if clicking same column
            orderby = "!" + orderby;
        }
        session.setAttribute("orderby", orderby);
    }
    String orderby = (String) session.getAttribute("orderby");

    // Handle pagination - show more messages beyond initial display
    String moreMessages = "false";
    if (request.getParameter("moreMessages") != null) {
        moreMessages = request.getParameter("moreMessages");
    }
    // Initial number of messages to display
    final int INITIAL_DISPLAY = 20;
%>

<c:if test="${empty sessionScope.msgSessionBean}">
    <c:redirect url="index.jsp"/>
</c:if>
<c:if test="${not empty sessionScope.msgSessionBean}">
    <c:set var="bean" value="${sessionScope.msgSessionBean}" scope="page"/>
    <c:if test="${bean.valid == false}">
        <c:redirect url="index.jsp"/>
    </c:if>
</c:if>
<%
    MsgSessionBean bean = (MsgSessionBean) pageContext.findAttribute("bean");
    String demographic_no = "";
    if (request.getParameter("demographic_no") != null) {
        demographic_no = request.getParameter("demographic_no");
    } else {
        demographic_no = bean.getDemographic_no();
    }

    String demographic_name = "";
    if (demographic_no != null) {
        DemographicData demographic_data = new DemographicData();
        Demographic demographic = demographic_data.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographic_no);
        demographic_name = demographic.getLastName() + ", " + demographic.getFirstName();
    }

%>
<jsp:useBean id="DisplayMessagesBeanId" scope="session"
             class="ca.openosp.openo.messenger.pageUtil.MsgDisplayMessagesBean"/>
<% 
    if (bean == null) {
        bean = (MsgSessionBean) request.getSession().getAttribute("msgSessionBean");
        if (bean == null) {
            response.sendRedirect(request.getContextPath() + "/errorpage.jsp?message=Session expired");
            return;
        }
    }

    DisplayMessagesBeanId.setProviderNo(bean.getProviderNo());
    bean.nullAttachment();
%>
<jsp:setProperty name="DisplayMessagesBeanId" property="*"/>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" href="css/encounterStyles.css">
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.title"/>
        </title>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

        <style>
            .TopStatusBar {
                width: 100% !important;
                height: 100% !important;
            }
        </style>

        <script type="text/javascript">
            function BackToOscar() {
                if (opener.callRefreshTabAlerts) {
                    opener.callRefreshTabAlerts("oscar_new_msg");
                    setTimeout("window.close()", 100);
                } else {
                    window.close();
                }
            }

            function unlink() {
                document.forms[0].submit();
            }
        </script>
    </head>

    <body class="BodyStyle" vlink="#0000FF" onload="window.focus()">
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgMessenger"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td>
                            <div class="DivContentTitle"><h2>Messages related to <%=demographic_name%>
                            </h2></div>
                        </td>
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
                <table width="100%">
                    <tr>
                        <td>
                            <table cellspacing=3>
                                <tr>
                                    <td>
                                        <table class=messButtonsA cellspacing=0 cellpadding=3>
                                            <tr>
                                                <td class="messengerButtonsA"><a
                                                        href="javascript:BackToOscar()"
                                                        class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnExit"/></a></td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>


                    <tr>
                        <td>
                            <%
                                String contextPath = request.getContextPath();
                                String strutsAction = contextPath + "/messenger/DisplayDemographicMessages.do?demographic_no=" + demographic_no; 
                            %>

                            <form action="<%=strutsAction%>" method="post">

                                <table border="0" width="80%" cellspacing="1">
                                    <tr>
                                        <th bgcolor="#DDDDFF" width="75">&nbsp;</th>
                                        <th align="left" bgcolor="#DDDDFF">
                                            <% if (moreMessages.equals("true")) {%> <a
                                                href="${pageContext.request.contextPath}/messenger/DisplayDemographicMessages.jsp?orderby=from&moreMessages=true">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgFrom"/>
                                        </a> <%} else {%> <a
                                                href="${pageContext.request.contextPath}/messenger/DisplayDemographicMessages.jsp?orderby=from&moreMessages=false">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgFrom"/>
                                        </a> <%}%>
                                        </th>
                                        <th align="left" bgcolor="#DDDDFF">
                                            <% if (moreMessages.equals("true")) {%> <a
                                                href="${pageContext.request.contextPath}/messenger/DisplayDemographicMessages.jsp?orderby=subject&moreMessages=true">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgSubject"/>
                                        </a> <%} else {%> <a
                                                href="${pageContext.request.contextPath}/messenger/DisplayDemographicMessages.jsp?orderby=subject&moreMessages=false">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgSubject"/>
                                        </a> <%}%>
                                        </th>
                                        <th align="left" bgcolor="#DDDDFF">
                                            <% if (moreMessages.equals("true")) {%> <a
                                                href="${pageContext.request.contextPath}/messenger/DisplayDemographicMessages.jsp?orderby=date&moreMessages=true">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgDate"/>
                                        </a> <%} else {%> <a
                                                href="${pageContext.request.contextPath}/messenger/DisplayDemographicMessages.jsp?orderby=date&moreMessages=false">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgDate"/>
                                        </a> <%}%>
                                        </th>
                                        <th align="left" bgcolor="#DDDDFF">
                                            <% if (moreMessages.equals("true")) {%>
                                            <a
                                                    href="${pageContext.request.contextPath}/messenger/DisplayDemographicMessages.jsp?orderby=linked&moreMessages=true">
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgLinked"/>
                                            </a>
                                            <%} else {%>
                                            <a
                                                    href="${pageContext.request.contextPath}/messenger/DisplayDemographicMessages.jsp?orderby=linked&moreMessages=false">
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgLinked"/>
                                            </a>
                                            <%}%>
                                        </th>
                                    </tr>
                                    <% //java.util.Vector theMessages = new java.util.Vector() ;
                                        java.util.Vector theMessages2 = new java.util.Vector();
                                        theMessages2 = DisplayMessagesBeanId.estDemographicInbox(orderby, demographic_no);
                                        String msgCount = Integer.toString(theMessages2.size());
                                    %>
                                    <!--   for loop Control Initiliation variabe changed to nextMessage   -->
                                    <%
                                        for (int i = 0; i < theMessages2.size(); i++) {
                                            MsgDisplayMessage dm;
                                            dm = (MsgDisplayMessage) theMessages2.get(i);
                                            String isLastMsg = "false";
                                    %>
                                    <tr>
                                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "normalMessage" %>'
                                            width="75"><input type="checkbox" name="messageNo" value="<%=dm.getMessageId() %>"/> <%
                                            String atta = dm.getAttach();
                                            if (atta.equals("1")) {
                                        %><img src="img/clip4.jpg">
                                            <%
                                                }
                                            %> &nbsp;
                                        </td>

                                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "normalMessage" %>'><%= dm.getSentby()  %>
                                        </td>
                                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "normalMessage" %>'><a
                                                href="<%=request.getContextPath()%>/messenger/ViewMessage.do?from=encounter&demographic_no=<%=demographic_no%>&msgCount=<%=msgCount%>&orderBy=<%=orderby%>&messageID=<%=dm.getMessageId()%>&messagePosition=<%=dm.getMessagePosition()%>">
                                            <%=dm.getThesubject()%>
                                        </a></td>
                                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "normalMessage" %>'><%= dm.getThedate()  %>
                                        </td>
                                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "normalMessage" %>'>
                                            <oscar:nameage demographicNo="<%=dm.getDemographic_no()%>"></oscar:nameage>
                                        </td>
                                    </tr>
                                    <%}%>
                                </table>
                                <table width="80%">
                                    <tr>
                                        <td><input type="button" value="Unlink Messages"
                                                   onclick="javascript:unlink();"></td>
                                        <%
                                            if (moreMessages.equals("false") && theMessages2.size() >= INITIAL_DISPLAY) {
                                        %>
                                        <td width="60%"></td>
                                        <td align="left"><a
                                                href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?moreMessages=true">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgAllMessage"/>
                                        </a></td>
                                        <%}%>
                                    </tr>
                                </table>

                            </form></td>
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
