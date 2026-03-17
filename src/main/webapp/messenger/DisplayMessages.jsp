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
    - Pagination support for large message lists (25 messages per page)
    - Patient demographic filtering
    - Security validation for read permissions
    - Message status indicators (new, read, unread) with bold styling for unread
    - Quick actions (archive, unarchive, mark read, mark unread)
    - Select-all checkbox with single-element handling for bulk operations

    Request Parameters:
    - boxType: Type of message box to display (0=inbox, 1=sent, 2=deleted, 3=demographic)
    - demographic_no: Filter messages for specific patient
    - orderby: Column to sort by (toggles ascending/descending on repeat click)
    - page: Current page number for pagination

    Session Requirements:
    - msgSessionBean: Must be valid for page access (redirects to index.jsp if null)
    - userrole: User's role for security validation
    - orderby: Stored sort preference (persisted across requests)

    Frontend Dependencies:
    - Bootstrap 5.3.3 (responsive layout, navigation tabs, table styling)
    - Font Awesome 6 (action icons: trash, folder, undo, search, caret)

    Form Routing:
    - Inbox/Sent/Demographic views POST to DisplayMessages.do (MsgDisplayMessages2Action)
    - Deleted/Archived view POSTs to ReDisplayMessages.do (MsgReDisplayMessages2Action)

    @since 2002-11-08
--%>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.data.MsgDisplayMessage" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<fmt:setBundle basename="oscarResources"/>
<%
    // Build security role string from session attributes
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
    if (bean == null) {
        response.sendRedirect(request.getContextPath() + "/messenger/index.jsp");
        return;
    }
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
            <fmt:message key="messenger.DisplayMessages.title"/>
        </title>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">

        <style>
        tr.newMessage td {
             font-weight: bold;
        }

        .TopStatusBar{
        width:100% !important;
        }

        tr.integratedMessage td {
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

        <script type="text/javascript" src="<%= request.getContextPath() %>/messenger/messenger-common.js"></script>
        <script type="text/javascript">
            function uload() {
                if (opener && opener.callRefreshTabAlerts) {
                    opener.callRefreshTabAlerts("oscar_new_msg");
                    setTimeout(function() { window.close(); }, 100);
                    return false;
                }
                return true;
            }

            // Toggles all message checkboxes in the form; handles both single-element and NodeList cases
            function checkAll(formId) {
                var f = document.getElementById(formId);
                var val = f.checkA.checked;
                var boxes = f.messageNo;
                if (!boxes) return;
                if (typeof boxes.length === 'undefined') {
                    // Single checkbox (single message in list)
                    boxes.checked = val;
                } else {
                    for (var i = 0; i < boxes.length; i++) {
                        boxes[i].checked = val;
                    }
                }
            }

            // Truncate long recipient lists to 30 chars with ellipsis; full text shown on hover via title attribute
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
        <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
    </head>

    <body class="BodyStyle" onload="window.focus()" onunload="return uload()">
<table style="width: 100%;">
  <tr>
    <td style="vertical-align:top;">
			<h4>&nbsp;<i class="fa-regular fa-envelope" title="<fmt:message key="messenger.DisplayMessages.msgMessenger"/>"></i>&nbsp;
                        <% switch(pageType){
                            case 0: %>
     		                    <fmt:message key="messenger.DisplayMessages.msgInbox"/>
                        <%      break;
                            case 1: %>
                                <fmt:message key="messenger.DisplayMessages.msgSentTitle"/>
                        <%      break;
                            case 2: %>
                                <fmt:message key="messenger.DisplayMessages.msgArchived"/>
                        <%      break;
                            case 3: %>
                                Messages related to <%=Encode.forHtml(demographic_name)%>
                        <%      break;
                        }%>
        </h4>
    </td>
<td style="width:60%; display:flex; align-items:center;">

                            <form action="${pageContext.request.contextPath}/messenger/DisplayMessages.do" method="post">
<div class="input-group mb-3">
                            <input name="boxType" type="hidden" value="<%=pageType%>">
                            <button name="btnSearch" type="submit" class="btn btn-outline-secondary"  title="<fmt:message key="messenger.DisplayMessages.btnSearch"/>"><i class="fa-solid fa-magnifying-glass fa-lg"></i></button>
                            <input name="searchString" type="text" class="form-control h-50"  value="<%=Encode.forHtmlAttribute(DisplayMessagesBeanId.getFilter())%>">

                            <button name="btnClearSearch" type="submit" class="btn btn-secondary" title="<fmt:message key="messenger.DisplayMessages.btnClearSearch"/>"><i class="fa-solid fa-xmark fa-lg"></i></button>
</div>
                            </form>

</td>
    <td style="text-align: right;" >

    </td>
</tr>
</table>

                    <%
                        // Route form to different Struts actions based on view type:
                        // - Inbox/Sent/Demographic → DisplayMessages.do (handles delete, read, unread)
                        // - Deleted/Archived → ReDisplayMessages.do (handles unarchive, mark as read)
                        String contextPath = request.getContextPath();
                        String strutsAction = contextPath + "/messenger/DisplayMessages.do";
                        if (pageType == 2) {
                            strutsAction = contextPath + "/messenger/ReDisplayMessages.do";
                        }
                    %>

                    <form action="<%=strutsAction%>" method="post" id="msgList">

                    <c:if test="${not empty updateFailureCount}">
                        <div class="alert alert-warning alert-dismissible fade show" role="alert">
                            <fmt:message key="messenger.DisplayMessages.updatePartialFailure"/>
                            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                        </div>
                    </c:if>

       <table  class="MainTable" id="scrollNumber1" style="width: 100%;">
        <tr>
            <td class="MainTableRightColumn" >
                <table style="width: 100%;">

                    <tr>
                        <td>
                            <ul class="nav nav-tabs">
                                <li class="nav-item">
                                         <a class="nav-link" href="${pageContext.request.contextPath}/messenger/CreateMessage.jsp">
                                         <fmt:message key="messenger.DisplayMessages.btnCompose"/></a>
                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link <% if (pageType == 0) { %>active<% } %>"
                                          href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp">
                                         <fmt:message key="messenger.DisplayMessages.btnRefresh"/></a>
                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link <% if (pageType == 1) { %>active<% } %>"
                                          href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?boxType=1">
                                         <fmt:message key="messenger.DisplayMessages.btnSent"/></a><!-- sentMessage link-->

                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link <% if (pageType == 2) { %>active<% } %>"
                                          href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?boxType=2">
                                         <fmt:message key="messenger.DisplayMessages.btnDeletedMessage"/></a><!--deletedMessage link-->

                                    </li>
                                    <li class="nav-item">
                                        <a class="nav-link" href="javascript:BackToCarlos()"><fmt:message key="messenger.DisplayMessages.btnExit"/></a>
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
                                    <button name="btnDelete" type="submit" class="btn btn-secondary" title="<fmt:message key="messenger.DisplayMessages.formArchive"/>"><i class="fa-solid fa-box-archive"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.formArchive"/></button>
                                    <button name="btnRead" type="submit" class="btn btn-secondary" title="<fmt:message key="messenger.DisplayMessages.markRead"/>"><i class="fa-solid fa-envelope-open-text"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.markRead"/></button>
                                    <button name="btnUnread" type="submit" class="btn btn-secondary" title="<fmt:message key="messenger.DisplayMessages.markUnRead"/>"><i class="fa-solid fa-envelope"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.markUnRead"/></button>
                            <%}else if (pageType == 2){%>
                                    <button name="btnUnarchive" type="submit" class="btn btn-secondary" title="<fmt:message key="messenger.DisplayMessages.formUnarchive"/>"><i class="fa-solid fa-box-open"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.formUnarchive"/></button>
                            <%}%>
                            &nbsp;</span>
                        <span class="float-end">
		                    <%
		                    int recordsToDisplay = 25;
		                    ResourceBundle msgBundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

		                    String previous = "";
		                    String next = "";
		                    String path = request.getContextPath()+"/messenger/DisplayMessages.jsp?boxType=" + pageType + "&page=";
		                    if (pageType != 3){

		                    int totalMsgs = DisplayMessagesBeanId.getTotalMessages(pageType);

		                    int totalPages = totalMsgs / recordsToDisplay + (totalMsgs % recordsToDisplay == 0 ? 0 : 1);

		                    String prevLabel;
		                    String nextLabel;
		                    try {
		                        prevLabel = Encode.forHtml(msgBundle.getString("messenger.DisplayMessages.btnPrevious"));
		                    } catch (java.util.MissingResourceException e) {
		                        MiscUtils.getLogger().debug("Missing resource key: messenger.DisplayMessages.btnPrevious");
		                        prevLabel = "&laquo; Previous";
		                    }
		                    try {
		                        nextLabel = Encode.forHtml(msgBundle.getString("messenger.DisplayMessages.btnNext"));
		                    } catch (java.util.MissingResourceException e) {
		                        MiscUtils.getLogger().debug("Missing resource key: messenger.DisplayMessages.btnNext");
		                        nextLabel = "Next &raquo;";
		                    }

		                    if(pageNum>1){
		                    	previous = "<a href='" + path + (pageNum-1) + "' title='previous page'>" + prevLabel + "</a> ";
		                    	out.print(previous);
							}

		                    if(pageNum<totalPages){
		                    	next = "<a href='" + path + (pageNum+1) + "' title='next page'>" + nextLabel + "</a>";
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
                                       <input type="checkbox" name="checkA" onclick="checkAll('msgList')" id="checkA" style="margin-bottom: 10px;" title="<fmt:message key="messenger.DisplayMessages.msgAllMessage"/>">
                                    <%} %>
                                    </th>
                                    <th style="text-align: left; width:120px;">
                                        <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=status"
                                                   >
                                            <fmt:message key="messenger.DisplayMessages.msgStatus"/>
                                            <i class="fa-solid fa-caret-down"></i>
                                        </a>
                                    </th>
                                    <th style="text-align: left;">
                                      <%if( pageType == 1 ) {%>
                                                 <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=sentto"
                                                    >
                                                    <fmt:message key="messenger.DisplayMessages.msgTo"/>
                                                    <i class="fa-solid fa-caret-down"></i>
                                                </a>
                                       <%} else {%>
                                                <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=from"
                                                   >
                                                    <fmt:message key="messenger.DisplayMessages.msgFrom"/>
                                                    <i class="fa-solid fa-caret-down"></i>
                                                </a>
                                       <% } %>
                                    </th>
                                    <th style="text-align: left;">
                                            <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=subject"
                                                   >
                                                <fmt:message key="messenger.DisplayMessages.msgSubject"/>
                                                <i class="fa-solid fa-caret-down"></i>
                                            </a>
                                    </th>
                                    <th style="text-align: left;">
                                            <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=date"
                                                   >
                                                <fmt:message key="messenger.DisplayMessages.msgDate"/>
                                                <i class="fa-solid fa-caret-down"></i>
                                            </a>
                                    </th>
                                    <th style="text-align: left;" >
                                            <a class="nav-link" href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?orderby=linked"
                                                   >
                                                <fmt:message key="messenger.DisplayMessages.msgLinked"/>
                                                <i class="fa-solid fa-caret-down"></i>
                                            </a>
                                    </th>
                                </tr>
                        </thead>
                        <tbody>
                                <%
                                    // Render each message row; build i18n key from status (e.g., "new" → "msgStatusNew")
                                    for (int i = 0; i < theMessages2.size(); i++) {
                                        MsgDisplayMessage dm;
                                        dm = (MsgDisplayMessage) theMessages2.get(i);
                                        // Guard against null/empty status from data corruption or unarchive edge cases.
                                        // Default to "new" so messages with missing status appear as unread (bold),
                                        // prompting the user to review them rather than silently appearing as read.
                                        String statusStr = dm.getStatus();
                                        if (statusStr == null || statusStr.trim().isEmpty()) {
                                            MiscUtils.getLogger().debug("Message " + dm.getMessageId() + " has null/empty status; defaulting to 'new'");
                                            statusStr = "new";
                                        }
                                        // Build resource bundle key: e.g., "messenger.DisplayMessages.msgStatusNew"
                                        String key = "messenger.DisplayMessages.msgStatus" + statusStr.substring(0, 1).toUpperCase() + statusStr.substring(1);
                                %>

                                <%-- Apply bold styling (newMessage class) for unread messages, and integratedMessage for type 3 --%>
                                <%
                                String rowClass = "";
                                if ("messenger.DisplayMessages.msgStatusNew".equals(key) || "messenger.DisplayMessages.msgStatusUnread".equals(key)) {
                                    rowClass = "newMessage";
                                }
                                if (dm.getType() == 3) {
                                    rowClass += (rowClass.isEmpty() ? "" : " ") + "integratedMessage";
                                }
                                %>
                                <tr class="<%=rowClass%>">
                                    <td style="width:25px;">
                                    <%if (pageType != 1){%>
                                       <input type="checkbox" name="messageNo" value="<%=Encode.forHtmlAttribute(dm.getMessageId()) %>">
                                     <% } %>

                                    </td>
                                    <td>
                                     <fmt:message key="<%= key %>"/>
                                    </td>
                                    <td>

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
                                    <td>
                                    <a href="<%=request.getContextPath()%>/messenger/ViewMessage.do?messageID=<%=Encode.forUriComponent(dm.getMessageId())%>&boxType=<%=pageType%>">
                                        <%=Encode.forHtml(dm.getThesubject())%>
                                    </a>
                                    <%
                                       String atta = dm.getAttach();
                                       String pdfAtta = dm.getPdfAttach();
                                       if ("1".equals(atta) || "1".equals(pdfAtta) ){ %>
                                            &nbsp;<i class="fa-solid fa-paperclip" title="attachment"></i>
                                    <% } %>
                                    </td>
                                    <td title="<%= Encode.forHtmlAttribute(dm.getThedate()) %>&nbsp;&nbsp;<%= Encode.forHtmlAttribute(dm.getThetime()) %>">
                                    	<%=Encode.forHtml(dm.getThedate())%>

                                    </td>
                                    <td>

                                    <%if(dm.getDemographic_no() != null  && !dm.getDemographic_no().equalsIgnoreCase("null")) {%>
                                        <oscar:nameage demographicNo="<%=Encode.forHtmlAttribute(dm.getDemographic_no())%>"></oscar:nameage>
                                    <%} %>

                                    </td>
                                </tr>
                            <%}%>

                            <tr><td colspan="6">
                        <span>
                            <%if (pageType == 0){%>
                                    <button name="btnDelete" type="submit" class="btn btn-secondary" title="<fmt:message key="messenger.DisplayMessages.formArchive"/>"><i class="fa-solid fa-box-archive"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.formArchive"/></button>
                                    <button name="btnRead" type="submit" class="btn btn-secondary" title="<fmt:message key="messenger.DisplayMessages.markRead"/>"><i class="fa-solid fa-envelope-open-text"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.markRead"/></button>
                                    <button name="btnUnread" type="submit" class="btn btn-secondary" title="<fmt:message key="messenger.DisplayMessages.markUnRead"/>"><i class="fa-solid fa-envelope"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.markUnRead"/></button>
                            <%}else if (pageType == 2){%>
                                    <button name="btnUnarchive" type="submit" class="btn btn-secondary" title="<fmt:message key="messenger.DisplayMessages.formUnarchive"/>"><i class="fa-solid fa-box-open"></i>&nbsp;<fmt:message key="messenger.DisplayMessages.formUnarchive"/></button>
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
