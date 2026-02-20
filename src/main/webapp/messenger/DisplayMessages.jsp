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
    - Message status indicators (new, read, unread)
    - Quick actions (view, archive, mark read, mark unread)

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
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    // Build security role string from session attributes
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
    // Determine which message box to display based on boxType parameter
    // 0 = Inbox (default), 1 = Sent, 2 = Deleted, 3 = Demographic-specific
    int pageType = 0;
    String boxType = request.getParameter("boxType");
    if (boxType == null || boxType.equals("")) {
        pageType = 0;  // Default to inbox
    } else if (boxType.equals("1")) {
        pageType = 1;  // Sent messages
    } else if (boxType.equals("2")) {
        pageType = 2;  // Deleted messages
    } else if (boxType.equals("3")) {
        pageType = 3;  // Demographic-specific messages
    } else {
        pageType = 0;  // Default to inbox for invalid values
    }

    // Handle demographic filtering if specified
    String demographic_no = request.getParameter("demographic_no");
    String demographic_name = "";
    if (demographic_no != null) {
        // Retrieve patient name for display
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

    int pageNum = 1;
    String pageParam = request.getParameter("page");
    if (pageParam != null) {
        try { pageNum = Integer.parseInt(pageParam); } catch (NumberFormatException e) { pageNum = 1; }
    }
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
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <title>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.title"/>
        </title>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.0.2/css/bootstrap.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/css/font-awesome.min.css">

        <style>
        tr.newMessage td {
             font-weight: bold;
        }

        .TopStatusBar{
        width:100% !important;
        }

        .integratedMessage {
	        background-color: #FFCCCC;
	        color: black;
        }

        span.recipientList:hover{
	        position: relative;
            text-overflow:clip;
            width:auto;
            white-space: normal;
        }
        </style>

        <script type="text/javascript">
            function BackToOscar() {
                if (opener && opener.callRefreshTabAlerts) {
                    opener.callRefreshTabAlerts("oscar_new_msg");
                    setTimeout("window.close()", 100);
                } else {
                    window.close();
                }
            }

            function uload() {
                if (opener && opener.callRefreshTabAlerts) {
                    opener.callRefreshTabAlerts("oscar_new_msg");
                    setTimeout("window.close()", 100);
                    return false;
                }
                return true;
            }

            function checkAll(formId) {
                var f = document.getElementById(formId);
                var val = f.checkA.checked;
                var boxes = f.messageNo;
                if (!boxes) return;
                if (typeof boxes.length === 'undefined') {
                    boxes.checked = val;
                } else {
                    for (var i = 0; i < boxes.length; i++) {
                        boxes[i].checked = val;
                    }
                }
            }

            document.addEventListener("DOMContentLoaded", function () {
                const lengthText = 30;
                const recipientLists = document.querySelectorAll('.recipientList');

                recipientLists.forEach(function (element) {
                    // Use textContent instead of text()
                    let text = element.textContent.trim();

                    if (text.length > lengthText) {
                        // Trim to length, remove partial words
                        let shortText = text.substring(0, lengthText).split(" ").slice(0, -1).join(" ") + "...";
                        element.textContent = shortText;
                    }

                    // Set title attribute
                    element.setAttribute("title", text);
                });
            });

        </script>
    </head>

    <body class="BodyStyle" onload="window.focus()" onunload="return uload()">
<table style="width: 100%;">
  <tr>
    <td style="vertical-align:top;">
			<h4>&nbsp;<i class="icon-envelope" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgMessenger"/>"></i>&nbsp;
                        <% switch(pageType){
                            case 0: %>
     		                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgInbox"/>
                        <%      break;
                            case 1: %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgSentTitle"/>
                        <%      break;
                            case 2: %>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgArchived"/>
                        <%      break;
                            case 3: %>
                                Messages related to <%=Encode.forHtml(demographic_name)%>
                        <%      break;
                        }%>
        </h4>
    </td>
<td style="width:60%; display:flex; align-items:center;">

                            <form action="${pageContext.request.contextPath}/messenger/DisplayMessages.do" method="post">
<div class="input-group">
                            <input name="boxType" type="hidden" value="<%=pageType%>">
                            <input name="searchString" type="text" class="form-control h-50"  value="<%=Encode.forHtmlAttribute(DisplayMessagesBeanId.getFilter())%>">
                            <button name="btnSearch" type="submit" class="btn"  title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnSearch"/>"><i class="icon-search icon-large"></i></button>
                            <button name="btnClearSearch" type="submit" class="btn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnClearSearch"/>"><i class="icon-remove icon-large"></i></button>
</div>
                            </form>

</td>
    <td style="text-align: right;" >
		<i class=" icon-question-sign"></i>
	                        <a href="javascript:void(0)" onClick ="popupPage(700,960,''+'Messenger create')"><fmt:setBundle basename="oscarResources"/><fmt:message key="app.top1"/></a>
	                        <i class=" icon-info-sign" style="margin-left:10px;"></i>
                            <a href="javascript:void(0)" onclick="javascript:popupPage(600,700,'<%= request.getContextPath() %>/oscarEncounter/About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a>
    </td>
</tr>
</table>

                    <%
                        String contextPath = request.getContextPath();
                        String strutsAction = contextPath + "/messenger/DisplayMessages.do";
                        if (pageType == 2) {
                            strutsAction = contextPath + "/messenger/ReDisplayMessages.do";
                        }
                    %>

                    <form action="<%=strutsAction%>" method="post" id="msgList">

       <table  class="MainTable" id="scrollNumber1" style="width: 100%;">
        <tr>
            <td class="MainTableRightColumn" >
                <table style="width: 100%;">

                    <tr>
                        <td>
                            <ul class="nav nav-tabs">
                                <li class="nav-item">
                                         <a class="nav-link" href="${pageContext.request.contextPath}/messenger/CreateMessage.jsp">
                                         <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnCompose"/></a>
                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link <% if (pageType == 0) { %>active"<% } %>"
                                          href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp">
                                         <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnRefresh"/></a>
                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link <% if (pageType == 1) { %>active<% } %>"
                                          href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?boxType=1">
                                         <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnSent"/></a><!-- sentMessage link-->

                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link <% if (pageType == 2) { %>active<% } %>"
                                          href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?boxType=2">
                                         <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnDeletedMessage"/></a><!--deletedMessage link-->

                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link" href="javascript:BackToOscar()"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnExit"/></a>
                                    </li>
                                    </ul>
                        </td>
                    </tr>



                    <%
                           java.util.Vector theMessages2 = new java.util.Vector() ;
                        switch(pageType){
                            case 0:
                                theMessages2 = DisplayMessagesBeanId.estInbox(orderby,pageNum);
                            break;
                            case 1:
                                theMessages2 = DisplayMessagesBeanId.estSentItemsInbox(orderby,pageNum);
                            break;
                            case 2:
                                theMessages2 = DisplayMessagesBeanId.estDeletedInbox(orderby,pageNum);
                            break;
                            case 3:
                                theMessages2 = DisplayMessagesBeanId.estDemographicInbox(orderby,demographic_no);
                            break;
                        }   //messageid
%>
                    <tr>
                        <td style="padding: 10px;" ><span>
                            <%if (pageType == 0){%>
                                    <button name="btnDelete" type="submit" class="btn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formArchive"/>"><i class="icon-trash"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formArchive"/></button>
                                    <button name="btnRead" type="submit" class="btn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markRead"/>"><i class="icon-folder-open"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markRead"/></button>
                                    <button name="btnUnread" type="submit" class="btn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markUnRead"/>"><i class="icon-folder-close"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markUnRead"/></button>
                            <%}else if (pageType == 2){%>
                                    <button name="btnUnarchive" type="submit" class="btn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formUnarchive"/>"><i class="icon-undo"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.formUnarchive"/></button>
                            <%}%>
                            &nbsp;</span>
                        <span class="float-end">
		                    <%
		                    int recordsToDisplay = 25;

		                    String previous = "";
		                    String next = "";
		                    String path = request.getContextPath()+"/messenger/DisplayMessages.jsp?boxType=" + pageType + "&page=";
		                    if (pageType != 3){

		                    int totalMsgs = DisplayMessagesBeanId.getTotalMessages(pageType);

		                    int totalPages = totalMsgs / recordsToDisplay + (totalMsgs % recordsToDisplay == 0 ? 0 : 1);

		                    if(pageNum>1){
		                    	previous = "<a href='" + path + (pageNum-1) + "' title='previous page'><< Previous</a> ";
		                    	out.print(previous);
							}

		                    if(pageNum<totalPages){
		                    	next = "<a href='" + path + (pageNum+1) + "' title='next page'>Next >></a>";
		                    	out.print(next);
		                    }
		                    }%></span>
                        </td>
                   </tr>
                    <tr>
                        <td>
                            <table class="table table-sm table-striped table-hover">
                                <thead><tr>
                                    <th style="text-align: left;">
                                    <%if( pageType!=1 ) {%>
                                       <input type="checkbox" name="checkAll2" onclick="checkAll('msgList')" id="checkA" style="margin-bottom: 10px;" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgAllMessage"/>">
                                    <%} %>
                                    </th>
                                    <th style="text-align: left; width:120px;">
                                        <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=status"
                                                   >
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgStatus"/>
                                            <i class=" icon-caret-down" ></i>
                                        </a>
                                    </th>
                                    <th style="text-align: left;">
                                      <%if( pageType == 1 ) {%>
                                                 <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=sentto"
                                                    >
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgTo"/>
                                                    <i class=" icon-caret-down" ></i>
                                                </a>
                                       <%} else {%>
                                                <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=from"
                                                   >
                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgFrom"/>
                                                    <i class=" icon-caret-down" ></i>
                                                </a>
                                       <% } %>
                                    </th>
                                    <th style="text-align: left;">
                                            <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=subject"
                                                   >
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgSubject"/>
                                                <i class=" icon-caret-down" ></i>
                                            </a>
                                    </th>
                                    <th style="text-align: left;">
                                            <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=date"
                                                   >
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgDate"/>
                                                <i class=" icon-caret-down" ></i>
                                            </a>
                                    </th>
                                    <th style="text-align: left;" >
                                            <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=linked"
                                                   >
                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgLinked"/>
                                                <i class=" icon-caret-down" ></i>
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

                                <% if ("messenger.DisplayMessages.msgStatusNew".equals(key) || "messenger.DisplayMessages.msgStatusUnread".equals(key)){%>
                                <tr class="newMessage">
                                <%}else{%>
                                <tr>
                                <%}%>
                                    <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>' style="width:25px;">
                                    <%if (pageType != 1){%>
                                       <input type="checkbox" name="messageNo" value="<%=Encode.forHtmlAttribute(dm.getMessageId()) %>">
                                     <% } %>

                                    </td>
                                    <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'>
                                     <fmt:setBundle basename="oscarResources"/><fmt:message key="<%= key %>"/>
                                    </td>
                                    <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'>

                                        <%
                                            if( pageType == 1 ) {
%>
<span class="recipientList">
<%
                                                out.print(Encode.forHtml(dm.getSentto()));
%>
</span>
<%
                                            }
                                            else
                                            {
                                                out.print(Encode.forHtml(dm.getSentby()));
                                            }
                                        %>

                                    </td>
                                    <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'>
                                    <a href="<%=request.getContextPath()%>/messenger/ViewMessage.do?messageID=<%=Encode.forUriComponent(dm.getMessageId())%>&boxType=<%=pageType%>">
                                        <%=Encode.forHtml(dm.getThesubject())%>
                                    </a>
                                    <%
                                       String atta = dm.getAttach();
                                       String pdfAtta = dm.getPdfAttach();
                                       if (atta.equals("1") || pdfAtta.equals("1") ){ %>
                                            &nbsp;<i class="icon-paper-clip" title="attachment"></i>
                                    <% } %>
                                    </td>
                                    <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>' title="<%= Encode.forHtmlAttribute(dm.getThedate()) %>&nbsp;&nbsp;<%= Encode.forHtmlAttribute(dm.getThetime()) %>">
                                    	<%=Encode.forHtml(dm.getThedate())%>

                                    </td>
                                    <td class='<%= dm.getType() == 3 ? "integratedMessage" : "" %>'>

                                    <%if(dm.getDemographic_no() != null  && !dm.getDemographic_no().equalsIgnoreCase("null")) {%>
                                        <oscar:nameage demographicNo="<%=dm.getDemographic_no()%>"></oscar:nameage>
                                    <%} %>

                                    </td>
                                </tr>
                            <%}%>

                            <tr><td colspan="6">
                        <span>
                            <%if (pageType == 0){%>
                                    <button name="btnDelete" type="submit" class="btn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formArchive"/>"><i class="icon-trash"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formArchive"/></button>
                                    <button name="btnRead" type="submit" class="btn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markRead"/>"><i class="icon-folder-open"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markRead"/></button>
                                    <button name="btnUnread" type="submit" class="btn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markUnRead"/>"><i class="icon-folder-close"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.markUnRead"/></button>
                            <%}else if (pageType == 2){%>
                                    <button name="btnUnarchive" type="submit" class="btn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.formUnarchive"/>"><i class="icon-undo"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.formUnarchive"/></button>
                            <%}%>
                            &nbsp;</span>
                        <span class="float-end">
                                    <%
                                    if(pageType!=3){
                                    	out.print(previous + next);
                                    }
                                    %>
</span>
                            </td></tr></tbody>
                            </table>
                        </td>
                    </tr></tbody>
                </table>

            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">
            </td>
        </tr>
    </table>
</form>
</body>
</html>
