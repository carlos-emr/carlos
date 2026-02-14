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
    DisplayMessages.jsp - Main message inbox/outbox display interface for the CARLOS EMR messaging system

    Purpose:
    This JSP page displays the list of messages for healthcare providers, supporting
    different views including inbox, sent messages, deleted messages, and demographic-specific
    messages. It provides sorting, pagination, and message management capabilities.

    Key Features:
    - Multiple message box types (inbox, sent, deleted, demographic)
    - Column sorting with ascending/descending toggle
    - Pagination support for large message lists
    - Patient demographic filtering
    - Security validation for read permissions
    - Message status indicators (new, unread, read, deleted)
    - Bulk mark as read / mark as unread
    - Quick actions (view, reply, forward, delete)

    Request Parameters:
    - boxType: Type of message box to display (0=inbox, 1=sent, 2=deleted, 3=demographic)
    - demographic_no: Filter messages for specific patient
    - orderby: Column to sort by
    - page: Current page number for pagination

    Session Requirements:
    - msgSessionBean: Must be valid for page access
    - userrole: User's role for security validation
    - orderby: Stored sort preference

    @since 2002
--%>

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
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

<%
    int pageType = 0;
    String boxType = request.getParameter("boxType");
    if (boxType == null || boxType.equals("")) {
        pageType = 0;
    } else if (boxType.equals("1")) {
        pageType = 1;
    } else if (boxType.equals("2")) {
        pageType = 2;
    } else if (boxType.equals("3")) {
        pageType = 3;
    } else {
        pageType = 0;
    }

    String demographic_no = request.getParameter("demographic_no");
    String demographic_name = "";
    if (demographic_no != null) {
        DemographicData demographic_data = new DemographicData();
        Demographic demographic = demographic_data.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographic_no);
        if (demographic != null) {
            demographic_name = demographic.getLastName() + ", " + demographic.getFirstName();
        }
    }


    pageContext.setAttribute("pageType", "" + pageType);

    if (request.getParameter("orderby") != null) {
        String orderby = request.getParameter("orderby");
        String sessionOrderby = (String) session.getAttribute("orderby");
        if (sessionOrderby != null && sessionOrderby.equals(orderby)) {
            orderby = "!" + orderby;
        }
        session.setAttribute("orderby", orderby);
    }
    String orderby = (String) session.getAttribute("orderby");

    int pageNum = request.getParameter("page") == null ? 1 : Integer.parseInt(request.getParameter("page"));
%>

<c:if test="${empty msgSessionBean}">
    <c:redirect url="index.jsp"/>
</c:if>
<c:if test="${not empty msgSessionBean}">
    <c:if test="${msgSessionBean.valid == 'false'}">
        <c:redirect url="index.jsp"/>
    </c:if>
</c:if>
<%
    MsgSessionBean bean = (MsgSessionBean) session.getAttribute("msgSessionBean");
%>
<jsp:useBean id="DisplayMessagesBeanId" scope="session" class="io.github.carlos_emr.carlos.messenger.pageUtil.MsgDisplayMessagesBean"/>
<% DisplayMessagesBeanId.setProviderNo(bean.getProviderNo());
    bean.nullAttachment();
%>
<jsp:setProperty name="DisplayMessagesBeanId" property="*"/>

<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
    <title>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.title"/>
    </title>

    <link href="<%=request.getContextPath()%>/css/bootstrap.css" rel="stylesheet">
    <link href="<%=request.getContextPath()%>/css/bootstrap-responsive.css" rel="stylesheet">
    <link href="<%=request.getContextPath()%>/css/fontawesome-all.min.css" rel="stylesheet">

    <script src="<%=request.getContextPath()%>/js/global.js"></script>
    <script src="<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js"></script>

    <style type="text/css">
        tr.newMessage td {
            font-weight: bold;
        }

        .integratedMessage {
            background-color: #FFCCCC;
            color: black;
        }

        span.recipientList:hover {
            position: relative;
            text-overflow: clip;
            width: auto;
            white-space: normal;
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

        function uload() {
            if (opener && opener.callRefreshTabAlerts) {
                opener.callRefreshTabAlerts("oscar_new_msg");
                setTimeout(function() { window.close(); }, 100);
                return false;
            }
            return true;
        }

        function checkAll(formId) {
            var f = document.getElementById(formId);
            var val = f.checkA.checked;
            for (var i = 0; i < f.messageNo.length; i++) {
                f.messageNo[i].checked = val;
            }
        }

        $(document).ready(function () {
            var lengthText = 30;
            var recipientLists = $('.recipientList');

            $.each(recipientLists, function (key, value) {
                var text = $(value).text();
                var shortText = $.trim(text).substring(0, lengthText);
                var names = shortText.split(",");
                if (names.length > 1) {
                    shortText = names[0] + ", " + names[1].substring(0, 2) + "...";
                }
                $(value).text(shortText);
                $(value).attr("title", $.trim(text));
            })
        })
    </script>
</head>

<body onload="window.focus()" onunload="return uload()">

<table style="width:100%">
    <tr>
        <td style="width:1%"></td>
        <td style="width:80%; text-align:left;">
            <h4>
                <i class="fa-solid fa-envelope"></i>&nbsp;
                <% switch (pageType) {
                    case 0: %>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgInbox"/>
                <% break;
                    case 1: %>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgSentTitle"/>
                <% break;
                    case 2: %>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgArchived"/>
                <% break;
                    case 3: %>
                Messages related to <%=Encode.forHtml(demographic_name)%>
                <% break;
                } %>
            </h4>
        </td>
        <td style="width:1%"></td>
    </tr>
</table>

<%
    String contextPath = request.getContextPath();
    String strutsAction = contextPath + "/messenger/DisplayMessages.do";
    if (pageType == 2) {
        strutsAction = contextPath + "/messenger/ReDisplayMessages.do";
    }
%>

<div class="well">
    <form action="<%=strutsAction%>" method="post" id="msgList">

        <ul class="nav nav-tabs">
            <li>
                <a href="${pageContext.request.contextPath}/messenger/CreateMessage.jsp">
                    <i class="fa-solid fa-pen-to-square"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnCompose"/>
                </a>
            </li>
            <li <% if (pageType == 0) { %>class="active"<% } %>>
                <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp">
                    <i class="fa-solid fa-inbox"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnRefresh"/>
                </a>
            </li>
            <li <% if (pageType == 1) { %>class="active"<% } %>>
                <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?boxType=1">
                    <i class="fa-solid fa-paper-plane"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnSent"/>
                </a>
            </li>
            <li <% if (pageType == 2) { %>class="active"<% } %>>
                <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?boxType=2">
                    <i class="fa-solid fa-box-archive"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnDeletedMessage"/>
                </a>
            </li>
        </ul>

        <%
            java.util.Vector theMessages2 = new java.util.Vector();
            switch (pageType) {
                case 0:
                    theMessages2 = DisplayMessagesBeanId.estInbox(orderby, pageNum);
                    break;
                case 1:
                    theMessages2 = DisplayMessagesBeanId.estSentItemsInbox(orderby, pageNum);
                    break;
                case 2:
                    theMessages2 = DisplayMessagesBeanId.estDeletedInbox(orderby, pageNum);
                    break;
                case 3:
                    theMessages2 = DisplayMessagesBeanId.estDemographicInbox(orderby, demographic_no);
                    break;
            }
        %>

        <div style="margin-top:10px;">
            <span>
                <%if (pageType == 0) {%>
                <input name="btnDelete" type="submit" class="btn"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formArchive"/>">
                <input name="btnRead" type="submit" class="btn"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markRead"/>">
                <input name="btnUnread" type="submit" class="btn"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markUnRead"/>">
                <%} else if (pageType == 2) {%>
                <input type="submit" class="btn"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formUnarchive"/>">
                <%}%>
            </span>
            <span class="pull-right">
                <%
                    int recordsToDisplay = 25;

                    String previous = "";
                    String next = "";
                    String path = request.getContextPath() + "/messenger/DisplayMessages.jsp?boxType=" + pageType + "&page=";
                    if (pageType != 3) {

                        int totalMsgs = DisplayMessagesBeanId.getTotalMessages(pageType);

                        int totalPages = totalMsgs / recordsToDisplay + (totalMsgs % recordsToDisplay == 0 ? 0 : 1);

                        if (pageNum > 1) {
                            previous = "<a href='" + path + (pageNum - 1) + "' class='btn btn-link' title='previous page'>&lt;&lt; Previous</a> ";
                            out.print(previous);
                        }

                        if (pageNum < totalPages) {
                            next = "<a href='" + path + (pageNum + 1) + "' class='btn btn-link' title='next page'>Next &gt;&gt;</a>";
                            out.print(next);
                        }
                    }
                %>
            </span>
        </div>

        <table class="table table-condensed table-striped table-hover">
            <thead>
            <tr>
                <th style="text-align: left;">
                    <%if (pageType != 1) {%>
                    <input type="checkbox" name="checkAll2" onclick="checkAll('msgList')" id="checkA"/>
                    <%} %>
                </th>
                <th style="text-align: left;">
                    <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=status&boxType=<%=pageType%>">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgStatus"/>
                    </a>
                </th>
                <th style="text-align: left;">
                    <%if (pageType == 1) {%>
                    <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=sentto&boxType=<%=pageType%>">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgTo"/>
                    </a>
                    <%} else {%>
                    <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=from&boxType=<%=pageType%>">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgFrom"/>
                    </a>
                    <% } %>
                </th>
                <th style="text-align: left;">
                    <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=subject&boxType=<%=pageType%>">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgSubject"/>
                    </a>
                </th>
                <th style="text-align: left;">
                    <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=date&boxType=<%=pageType%>">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgDate"/>
                    </a>
                </th>
                <th style="text-align: left;">
                    <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=linked&boxType=<%=pageType%>">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgLinked"/>
                    </a>
                </th>
            </tr>
            </thead>
            <tbody>
            <%
                for (int i = 0; i < theMessages2.size(); i++) {
                    MsgDisplayMessage dm;
                    dm = (MsgDisplayMessage) theMessages2.get(i);
                    String key = "messenger.DisplayMessages.msgStatus" + dm.getStatus().substring(0, 1).toUpperCase() + dm.getStatus().substring(1);
            %>

            <% if ("messenger.DisplayMessages.msgStatusNew".equals(key) || "messenger.DisplayMessages.msgStatusUnread".equals(key)) {%>
            <tr class="newMessage">
            <%} else {%>
            <tr>
            <%}%>
                <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>' style="width:25px;">
                    <%if (pageType != 1) {%>
                    <input type="checkbox" name="messageNo" value="<%=dm.getMessageId() %>"/>
                    <% } %>
                    &nbsp;
                    <%
                        String atta = dm.getAttach();
                        String pdfAtta = dm.getPdfAttach();
                        if (atta.equals("1") || pdfAtta.equals("1")) { %>
                    <i class="fa-solid fa-paperclip"></i>
                    <% } %>
                </td>
                <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="<%= key %>"/>
                </td>
                <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'>
                    <%
                        if (pageType == 1) {
                    %>
                    <span class="recipientList"><%=Encode.forHtml(dm.getSentto())%></span>
                    <%
                        } else {
                            out.print(Encode.forHtml(dm.getSentby()));
                        }
                    %>
                </td>
                <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'>
                    <a href="<%=request.getContextPath()%>/messenger/ViewMessage.do?messageID=<%=dm.getMessageId()%>&boxType=<%=pageType%>">
                        <%=Encode.forHtml(dm.getThesubject())%>
                    </a>
                </td>
                <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>' title="<%= Encode.forHtmlAttribute(dm.getThedate()) %>&nbsp;&nbsp;<%= Encode.forHtmlAttribute(dm.getThetime()) %>">
                    <%=Encode.forHtml(dm.getThedate())%>
                </td>
                <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'>
                    <%if (dm.getDemographic_no() != null && !dm.getDemographic_no().equalsIgnoreCase("null")) {%>
                    <oscar:nameage demographicNo="<%=dm.getDemographic_no()%>"></oscar:nameage>
                    <%} %>
                </td>
            </tr>
            <%}%>
            </tbody>
        </table>

        <div>
            <span>
                <%if (pageType == 0) {%>
                <input name="btnDelete" type="submit" class="btn"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formArchive"/>">
                <input name="btnRead" type="submit" class="btn"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markRead"/>">
                <input name="btnUnread" type="submit" class="btn"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markUnRead"/>">
                <%} else if (pageType == 2) {%>
                <input type="submit" class="btn"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formUnarchive"/>">
                <%}%>
            </span>
            <span class="pull-right">
                <%
                    if (pageType != 3) {
                        out.print(previous + next);
                    }
                %>
            </span>
        </div>

    </form>
</div>

<table style="width:100%">
    <tr>
        <td>
            <a href="javascript:BackToOscar()" class="btn btn-link">
                <i class="fa-solid fa-arrow-right-from-bracket"></i>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnExit"/>
            </a>
        </td>
    </tr>
</table>

</body>
</html>
