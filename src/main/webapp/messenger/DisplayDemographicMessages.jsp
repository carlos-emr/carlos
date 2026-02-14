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

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="org.owasp.encoder.Encode" %>
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

    // Session validation - redirect if no valid session bean
    MsgSessionBean bean = (MsgSessionBean) session.getAttribute("msgSessionBean");
    if (bean == null || !bean.getValid()) {
        response.sendRedirect(request.getContextPath() + "/messenger/index.jsp");
        return;
    }

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
             class="io.github.carlos_emr.carlos.messenger.pageUtil.MsgDisplayMessagesBean"/>
<%
    DisplayMessagesBeanId.setProviderNo(bean.getProviderNo());
    bean.nullAttachment();
%>
<jsp:setProperty name="DisplayMessagesBeanId" property="*"/>

<!DOCTYPE html>
<html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script src="<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js"></script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.title"/></title>

        <link href="<%=request.getContextPath()%>/css/bootstrap.css" rel="stylesheet">
        <link href="<%=request.getContextPath()%>/css/bootstrap-responsive.css" rel="stylesheet">
        <link href="<%=request.getContextPath()%>/css/fontawesome-all.min.css" rel="stylesheet">

        <style type="text/css">
            .integratedMessage {
                background-color: #FFCCCC;
                color: black;
            }
        </style>

        <script type="text/javascript">
            function BackToOscar() {
                if (opener && opener.callRefreshTabAlerts) {
                    opener.callRefreshTabAlerts("oscar_new_msg");
                    setTimeout(function() { window.close(); }, 100);
                } else {
                    window.close();
                }
            }

            function unlink() {
                document.forms[0].submit();
            }
        </script>
    </head>

    <body onload="window.focus()">

    <table style="width:100%">
        <tr>
            <td style="width:1%"></td>
            <td style="width:80%; text-align:left;">
                <h4>
                    <i class="fa-solid fa-envelope"></i>&nbsp;
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgMessenger"/> &mdash;
                    <%=Encode.forHtml(demographic_name)%>
                </h4>
            </td>
            <td style="width:1%"></td>
        </tr>
    </table>

    <div class="well">

        <%
            String contextPath = request.getContextPath();
            String strutsAction = contextPath + "/messenger/DisplayDemographicMessages.do?demographic_no=" + Encode.forUriComponent(demographic_no);
        %>

        <form action="<%=strutsAction%>" method="post">

            <table class="table table-condensed table-striped table-hover">
                <thead>
                    <tr>
                        <th style="width:75px;">&nbsp;</th>
                        <th>
                            <% if (moreMessages.equals("true")) {%>
                            <a href="<%=request.getContextPath()%>/messenger/DisplayDemographicMessages.jsp?orderby=from&moreMessages=true">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgFrom"/>
                            </a>
                            <%} else {%>
                            <a href="<%=request.getContextPath()%>/messenger/DisplayDemographicMessages.jsp?orderby=from&moreMessages=false">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgFrom"/>
                            </a>
                            <%}%>
                        </th>
                        <th>
                            <% if (moreMessages.equals("true")) {%>
                            <a href="<%=request.getContextPath()%>/messenger/DisplayDemographicMessages.jsp?orderby=subject&moreMessages=true">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgSubject"/>
                            </a>
                            <%} else {%>
                            <a href="<%=request.getContextPath()%>/messenger/DisplayDemographicMessages.jsp?orderby=subject&moreMessages=false">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgSubject"/>
                            </a>
                            <%}%>
                        </th>
                        <th>
                            <% if (moreMessages.equals("true")) {%>
                            <a href="<%=request.getContextPath()%>/messenger/DisplayDemographicMessages.jsp?orderby=date&moreMessages=true">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgDate"/>
                            </a>
                            <%} else {%>
                            <a href="<%=request.getContextPath()%>/messenger/DisplayDemographicMessages.jsp?orderby=date&moreMessages=false">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgDate"/>
                            </a>
                            <%}%>
                        </th>
                        <th>
                            <% if (moreMessages.equals("true")) {%>
                            <a href="<%=request.getContextPath()%>/messenger/DisplayDemographicMessages.jsp?orderby=linked&moreMessages=true">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgLinked"/>
                            </a>
                            <%} else {%>
                            <a href="<%=request.getContextPath()%>/messenger/DisplayDemographicMessages.jsp?orderby=linked&moreMessages=false">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgLinked"/>
                            </a>
                            <%}%>
                        </th>
                    </tr>
                </thead>
                <tbody>
                    <%
                        java.util.Vector theMessages2 = new java.util.Vector();
                        theMessages2 = DisplayMessagesBeanId.estDemographicInbox(orderby, demographic_no);
                        String msgCount = Integer.toString(theMessages2.size());
                    %>
                    <%
                        for (int i = 0; i < theMessages2.size(); i++) {
                            MsgDisplayMessage dm;
                            dm = (MsgDisplayMessage) theMessages2.get(i);
                            String isLastMsg = "false";
                    %>
                    <tr>
                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'
                            style="width:75px;"><input type="checkbox" name="messageNo" value="<%=Encode.forHtmlAttribute(dm.getMessageId())%>"/>
                            <%
                                String atta = dm.getAttach();
                                if (atta.equals("1")) {
                            %><i class="fa-solid fa-paperclip"></i>
                            <%
                                }
                            %> &nbsp;
                        </td>

                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'><%= Encode.forHtml(dm.getSentby()) %>
                        </td>
                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'><a
                                href="<%=request.getContextPath()%>/messenger/ViewMessage.do?from=encounter&demographic_no=<%=Encode.forUriComponent(demographic_no)%>&msgCount=<%=Encode.forUriComponent(msgCount)%>&orderBy=<%=Encode.forUriComponent(orderby)%>&messageID=<%=Encode.forUriComponent(dm.getMessageId())%>&messagePosition=<%=Encode.forUriComponent(dm.getMessagePosition())%>">
                            <%=Encode.forHtml(dm.getThesubject())%>
                        </a></td>
                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'><%= Encode.forHtml(dm.getThedate()) %>
                        </td>
                        <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'>
                            <oscar:nameage demographicNo="<%=dm.getDemographic_no()%>"></oscar:nameage>
                        </td>
                    </tr>
                    <%}%>
                </tbody>
            </table>

            <div style="margin-top:10px;">
                <input type="button" class="btn" value="Unlink Messages" onclick="javascript:unlink();">
                <%
                    if (moreMessages.equals("false") && theMessages2.size() >= INITIAL_DISPLAY) {
                %>
                <a href="<%=request.getContextPath()%>/messenger/DisplayMessages.jsp?moreMessages=true" class="btn btn-link" style="margin-left:15px;">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgAllMessage"/>
                </a>
                <%}%>
            </div>

        </form>
    </div>

    <div style="margin-top:5px; margin-left:5px;">
        <a href="javascript:BackToOscar()" class="btn btn-link">
            <i class="fa-solid fa-arrow-right-from-bracket"></i>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnExit"/>
        </a>
    </div>

    </body>
</html>
