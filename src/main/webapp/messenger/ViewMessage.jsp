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
  ViewMessage.jsp - Primary message viewing interface

  This comprehensive JSP page displays individual messages with full details,
  attachments, and action options. It supports viewing messages from various
  sources including inbox, sent items, and demographic-specific messages.

  Main features:
  - Full message display with sender, recipients, subject, body
  - Attachment viewing and download capabilities
  - Reply, forward, and delete actions
  - Integration with case management notes
  - Support for resident/supervisor message approval workflow
  - PDF attachment preview
  - Message thread navigation

  Security:
  - Requires "_msg" object with read ("r") permissions
  - Validates user access to specific messages

  Request parameters:
  - messageID: Unique identifier of message to view
  - boxType: Source mailbox (inbox, sent, deleted)
  - demographic_no: Associated patient ID if applicable
  - providerview: Filter for provider-specific views
  - bFirstDisp: First display flag for marking as read

  Integration points:
  - Case management notes for clinical documentation
  - Resident supervision workflow
  - PDF document management
  - Patient encounter system

  @since 2003
--%>

<%@page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote" %>
<%@page import="io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO" %>
<%@page import="java.util.Set" %>
<%@page import="java.util.List" %>
<%@page import="java.util.HashMap" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ResidentOscarMsg" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ResidentOscarMsgDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.OscarMsgType" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    // Retrieve user information from session
    String providerNo = (String) session.getAttribute("providerNo");
    String curUser_no = (String) session.getAttribute("user");
    String roleName$ = (String) session.getAttribute("userrole") + "," + curUser_no;

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
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>


<%
    String providerview = request.getParameter("providerview") == null ? "all" : request.getParameter("providerview");
    boolean bFirstDisp = true;
    if (request.getParameter("bFirstDisp") != null) bFirstDisp = (request.getParameter("bFirstDisp")).equals("true");
%>
<%@ page
        import="io.github.carlos_emr.carlos.demographic.data.*, java.util.Enumeration" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib prefix="Encode" uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" %>

<%
    // Determine box type for conditional nav display
    String boxType = request.getParameter("boxType");
    // Determine if opened from encounter view
    String fromParam = request.getParameter("from");
    boolean fromEncounter = "encounter".equals(fromParam);
%>

<!DOCTYPE html>
<html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script src="<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js"></script>

        <link href="<%=request.getContextPath()%>/css/bootstrap.css" rel="stylesheet">
        <link href="<%=request.getContextPath()%>/css/bootstrap-responsive.css" rel="stylesheet">
        <link href="<%=request.getContextPath()%>/css/fontawesome-all.min.css" rel="stylesheet">
        <link rel="stylesheet" type="text/css" href="printable.css" media="print">

        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.title"/></title>

        <style type="text/css">
            .msg-label {
                font-weight: bold;
                padding: 4px 8px;
                background-color: #f5f5f5;
                border-bottom: 1px solid #e5e5e5;
                width: 100px;
                vertical-align: top;
            }
            .msg-value {
                padding: 4px 8px;
                border-bottom: 1px solid #e5e5e5;
            }
            .msg-detail-table {
                width: 100%;
                border-collapse: collapse;
                margin-bottom: 10px;
            }
            /* Modal styles */
            .modal {
                display: none;
                position: fixed;
                z-index: 1000;
                left: 0;
                top: 0;
                width: 100%;
                height: 100%;
                background-color: rgba(0,0,0,0.4);
            }
            .modal-content {
                background-color: #fff;
                margin: 10% auto;
                padding: 0;
                border: 1px solid #888;
                width: 50%;
                border-radius: 4px;
            }
            .modal-header {
                padding: 10px 15px;
                border-bottom: 1px solid #e5e5e5;
            }
            .modal-header .close {
                float: right;
                font-size: 21px;
                font-weight: bold;
                cursor: pointer;
            }
            .modal-body {
                padding: 15px;
            }
            .modal-footer {
                padding: 10px 15px;
                border-top: 1px solid #e5e5e5;
                text-align: right;
            }
            .demographicOption {
                padding: 8px;
                margin: 5px 0;
                border: 1px solid #ddd;
                border-radius: 3px;
            }
            .modal_button {
                padding: 4px 12px;
                margin-left: 5px;
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

            function popupViewAttach(vheight, vwidth, varpage) {
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var winName;

                if (page.indexOf("IncomingEncounter.do") > -1) {
                    winName = "encounter";
                } else {
                    winName = "oscarMVA";
                }

                var popup = window.open(varpage, winName, windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            function popup(demographicNo, msgId, providerNo, action) {
                var vheight = 700;
                var vwidth = 980;

                if (demographicNo != null && demographicNo != "") {
                    windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                    var page = "";
                    var win;
                    var today = "<%=session.getAttribute("today")%>";
                    var header = "messenger";
                    var encType = "messenger";
                    var txt;

                    var noteEditorId = "noteEditor" + demographicNo;
                    var noteEditor = window.parent.opener.document.getElementById(noteEditorId);
                    var ngApp = window.parent.opener.document.body.parentElement.getAttribute("ng-app");

                    if (action == "writeToEncounter") {
                        win = window.open("", "<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.apptProvider"/>");
                        if (win.pasteToEncounterNote && win.demographicNo == demographicNo) {
                            txt = fmtOscarMsg();
                            win.pasteToEncounterNote(txt);
                        } else if (noteEditor != undefined) {
                            win.close();
                            txt = "\n" + fmtOscarMsg();
                            noteEditor.value = noteEditor.value + txt;
                        } else if (noteEditor == undefined && ngApp != undefined) {
                            win.close();
                            txt = "\n" + fmtOscarMsg();
                            getAngJsPath = window.opener.location.href;
                            newAngJsPath = getAngJsPath.substring(0, getAngJsPath.indexOf('#') + 2) + "record/" + demographicNo + "/summary?noteEditorText=" + encodeURI(txt);
                            window.opener.location.href = newAngJsPath;
                        } else {
                            win.close();
                            page = 'WriteToEncounter.do?demographic_no=' + demographicNo + '&msgId=' + msgId + '&providerNo=' + providerNo + '&encType=messenger';
                            var popUp = window.open(page, "<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.apptProvider"/>", windowprops);
                            if (popUp != null) {
                                if (popUp.opener == null) {
                                    popUp.opener = self;
                                }
                                popUp.focus();
                            }
                        }
                    } else if (action == "linkToDemographic") {
                        page = 'ViewMessage.do?linkMsgDemo=true&demographic_no=' + demographicNo + '&messageID=' + msgId + '&providerNo=' + providerNo;
                        window.location = page;
                    }
                }
            }

            function popupStart(vheight, vwidth, varpage, windowname) {
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
                var popup = window.open(varpage, windowname, windowprops);
            }

            function popupSearchDemo(keyword) {
                var vheight = 700;
                var vwidth = 980;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var page = 'msgSearchDemo.jsp?keyword=' + keyword + '&firstSearch=' + true;
                var popUp = window.open(page, "msgSearchDemo", windowprops);
                if (popUp != null) {
                    if (popUp.opener == null) {
                        popUp.opener = self;
                    }
                    popUp.focus();
                }
            }

            function fmtOscarMsg() {
                txt = "From: ";
                tmp = document.getElementById("sentBy").innerHTML;
                tmp = tmp.replace(/^\s+|\s+$/g, "");
                txt += tmp;
                txt += "\nTo: ";
                tmp = document.getElementById("sentTo").innerHTML;
                tmp = tmp.replace(/^\s+|\s+$/g, "");
                txt += tmp;
                txt += "\nDate: ";
                tmp = document.getElementById("sentDate").innerHTML;
                tmp = tmp.replace(/\s+|\n+/g, "");
                tmp = tmp.replace(/&nbsp;/g, " ");
                txt += tmp;
                txt += "\nSubject: ";
                tmp = document.getElementById("msgSubject").innerHTML;
                tmp = tmp.replace(/^\s+|\s+$/g, "");
                txt += tmp;
                txt += "\n";
                tmp = document.getElementById("msgBody").innerHTML;
                tmp = tmp.replace(/^\s+|\s+$/g, "");
                txt += tmp;

                return txt;
            }

        </script>

    </head>

    <body>
    <form action="<%=request.getContextPath()%>/messenger/HandleMessages.do" method="post">

        <table style="width:100%">
            <tr>
                <td style="width:1%"></td>
                <td style="width:80%; text-align:left;">
                    <h4>
                        <i class="fa-solid fa-envelope-open-text"></i>&nbsp;
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgViewMessage"/>
                    </h4>
                </td>
                <td style="width:1%"></td>
            </tr>
        </table>

        <div class="well">

            <ul class="nav nav-tabs">
                <% if (!fromEncounter) { %>
                    <li>
                        <a href="<%=request.getContextPath()%>/messenger/CreateMessage.jsp">
                            <i class="fa-solid fa-pen-to-square"></i>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnCompose"/>
                        </a>
                    </li>
                <% } %>

                <li>
                    <a href="javascript:window.print()">
                        <i class="fa-solid fa-print"></i>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnPrint"/>
                    </a>
                </li>

                <% if (!fromEncounter) { %>
                    <li>
                        <a href="<%=request.getContextPath()%>/messenger/DisplayMessages.jsp">
                            <i class="fa-solid fa-inbox"></i>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnInbox"/>
                        </a>
                    </li>
                <% } %>

                <% if ("1".equals(boxType)) { %>
                <li>
                    <a href="<%=request.getContextPath()%>/messenger/DisplayMessages.jsp?boxType=1">
                        <i class="fa-solid fa-paper-plane"></i>
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnSent"/>
                    </a>
                </li>
                <%} %>
            </ul>

            <div class="Printable" style="margin-top:10px;">
                <table class="msg-detail-table">
                    <tr>
                        <td class="msg-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgFrom"/>:</td>
                        <td class="msg-value" id="sentBy" colspan="2"><%= session.getAttribute("viewMessageSentby") %></td>
                    </tr>
                    <tr>
                        <td class="msg-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgTo"/>:</td>
                        <td class="msg-value" id="sentTo" colspan="2"><%= session.getAttribute("viewMessageSentto") %></td>
                    </tr>
                    <tr>
                        <td class="msg-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgSubject"/>:</td>
                        <td class="msg-value" id="msgSubject" colspan="2"><%= session.getAttribute("viewMessageSubject") %></td>
                    </tr>
                    <tr>
                        <td class="msg-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgDate"/>:</td>
                        <td class="msg-value" id="sentDate" colspan="2">
                            <c:out value="${ viewMessageDate }"/> <c:out value="${ viewMessageTime }"/>
                        </td>
                    </tr>
                    <% String attach = (String) session.getAttribute("viewMessageAttach");
                        String id = (String) session.getAttribute("viewMessageId");
                        if (attach != null && attach.equals("1")) {
                    %>
                    <tr>
                        <td class="msg-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgAttachments"/>:</td>
                        <td class="msg-value" colspan="2"><a
                                href="javascript:popupViewAttach(700,960,'ViewAttach.do?attachId=<%=id%>')">
                            <i class="fa-solid fa-paperclip"></i>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnAttach"/> </a></td>
                    </tr>
                    <%
                        }
                    %>
                    <%
                        String pdfAttach = (String) session.getAttribute("viewMessagePDFAttach");
                        if (pdfAttach != null && pdfAttach.equals("1")) {
                    %>
                    <tr>
                        <td class="msg-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgAttachments"/>:</td>
                        <td class="msg-value" colspan="2"><a
                                href="javascript:popupViewAttach(700,960,'ViewPDFAttach.do?attachId=<%=id%>')">
                            <i class="fa-solid fa-file-pdf"></i>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnAttach"/> </a></td>
                    </tr>
                    <%
                        }
                    %>

                    <tr>
                        <td class="msg-label"></td>
                        <td class="msg-value" colspan="2">
                            <textarea id="msgBody" name="Message" wrap="hard" readonly="true" rows="18"
                                      cols="60" style="width:100%; border:1px solid #ccc; padding:5px;"><c:out value="${ viewMessageMessage }"/></textarea>
                        </td>
                    </tr>

                    <!-- switch views depending on if the request was made from the patient encounter -->

                    <c:choose>
                        <%-- If view request is from the encounter, display the following: --%>
                        <c:when test="${ from eq 'encounter' }">
                            <tr>
                                <td class="msg-label"></td>
                                <td class="msg-value">
                                    <strong>
                                        Demographic(s) linked to this message
                                    </strong>
                                </td>
                            </tr>

                            <%-- display the list of attached demographics --%>
                            <c:choose>
                                <c:when test="${ not empty attachedDemographics }">
                                    <c:forEach items="${ attachedDemographics }" var="demoattached">
                                        <tr>
                                            <td class="msg-label"></td>
                                            <td class="msg-value" colspan="2">

                                                <c:out value="${ demoattached.value }"/> <br/>

                                                <c:if test="${ demoattached.key eq demographic_no }">
                                                    <input
                                                            onclick="javascript:popup('<c:out value="${ demographic_no }"/>', '<c:out value="${ messageID }"/>', '<c:out value="${ providerNo }"/>');"
                                                            class="btn" type="button"
                                                            name="writeToEncounter"
                                                            value="Write To Encounter"> <input
                                                        onclick="return paste2Encounter('<c:out value="${ demographic_no }"/>');"
                                                        class="btn" type="button"
                                                        name="pasteToEncounter"
                                                        value="Paste To Encounter">
                                                </c:if>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </c:when>

                                <%--  or send a message that no demogrpahic is linked --%>
                                <c:otherwise>
                                    <tr>
                                        <td class="msg-label"></td>
                                        <td class="msg-value">
                                            No demographic is linked to this message
                                        </td>
                                    </tr>
                                </c:otherwise>
                            </c:choose>
                        </c:when>

                        <%-- If view request is from the inbox, display the following --%>
                        <c:otherwise>
                            <tr>
                                <td class="msg-label"></td>
                                <td class="msg-value" colspan="2">
                                    <input type="submit" name="reply" class="btn btn-sm btn-primary"
                                        style="background-image:none; background-color:#428bca; border-color:#357ebd;"
                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnReply"/>"/>
                                    <input type="submit" name="replyAll" class="btn btn-sm btn-primary"
                                        style="background-image:none; background-color:#428bca; border-color:#357ebd;"
                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnReplyAll"/>"/>
                                    <input type="submit" name="forward" class="btn"
                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnForward"/>"/>
                                    <input type="submit" name="delete" class="btn btn-danger"
                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnDelete"/>"/>
                                    <input type="hidden" name="messageNo" id="messageNo" value="<c:out value='${ viewMessageNo }'/>"/>
                                </td>
                            </tr>
                            <tr>
                                <td class="msg-label"></td>
                                <td class="msg-value" colspan="2">
                                    <strong>
                                        Link this message to ...
                                    </strong>
                                </td>
                            </tr>

                            <tr>
                                <td class="msg-label"></td>
                                <td class="msg-value"><input type="text" name="keyword" size="30" style="height:30px;"/>
                                </td>
                                <td class="msg-value">
                                    <input type="hidden"
                                           name="demographic_no"/> <input type="button"
                                                                          class="btn"
                                                                          name="searchDemo"
                                                                          value="Search Demographic"
                                                                          onclick="popupSearchDemo(document.forms[0].keyword.value)"/>
                                </td>

                            </tr>
                            <tr>
                                <td class="msg-label"></td>
                                <td class="msg-value" colspan="2"><strong>Selected Demographic</strong>
                                </td>
                            </tr>

                            <%

                                String demographic_no = request.getParameter("demographic_no");
                                DemographicData demoData = new DemographicData();
                                Demographic demo = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographic_no);
                                String demoName = "";
                                String demoLastName = "";
                                String demoFirstName = "";
                                if (demo != null) {
                                    demoName = demo.getLastName() + ", " + demo.getFirstName();
                                    demoLastName = demo.getLastName();
                                    demoFirstName = demo.getLastName();

                                } %>
                            <tr>
                                <td class="msg-label"></td>
                                <td class="msg-value"><input type="text"
                                                             name="selectedDemo" size="30" readonly
                                                             style="background: #f5f5f5; border: 1px solid #ccc;"
                                                             value="none"/>
                                    <script>
                                        if ("<%=Encode.forJavaScript(demoName)%>" && "<%=Encode.forJavaScript(demoName)%>" !== "null") {
                                            document.forms[0].selectedDemo.value = "<%=Encode.forJavaScript(demoName)%>"
                                            document.forms[0].demographic_no.value = "<%=Encode.forJavaScript(demographic_no)%>"
                                        }
                                    </script>
                                </td>

                                <td class="msg-value">
                                    <input type="button"
                                           class="btn" name="linkDemo"
                                           value="Link to demographic"
                                           onclick="popup(document.forms[0].demographic_no.value,'<%=session.getAttribute("viewMessageId")%>','<%=session.getAttribute("providerNo")%>','linkToDemographic')"/>

                                    <input type="button" class="btn"
                                           name="clearDemographic" value="Clear selected demographic"
                                           onclick='document.forms[0].demographic_no.value = ""; document.forms[0].selectedDemo.value = "none"'/>
                                </td>

                            </tr>


                            <tr>
                                <td class="msg-label"></td>
                                <td class="msg-value" colspan="2">
                                    <strong>
                                        Demographic(s) linked to this message
                                    </strong>
                                </td>
                            </tr>
                            <c:if test="${ not empty unlinkedDemographics }">
                                <c:forEach items="${ unlinkedDemographics }" var="unlinkedDemographic">
                                    <tr id="unlinkedDemographicDetails">
                                        <td class="msg-label"></td>
                                        <td class="msg-value">
                                            <input type="hidden"
                                                   name="unlinkedIntegratorDemographicName"
                                                   value="<Encode:forHtmlAttribute value='${ unlinkedDemographic.lastName }, ${ unlinkedDemographic.firstName }' />"/>
                                            <c:out value="${ unlinkedDemographic.lastName }"/>, <c:out
                                                value="${ unlinkedDemographic.firstName }"/> <br/>
                                            <strong>Gender:</strong> <c:out
                                                value="${ unlinkedDemographic.gender }"/><br/>
                                            <strong>HIN:</strong> <c:out
                                                value="${ unlinkedDemographic.hin }"/><br/>
                                            <strong>File Location:</strong> <c:out
                                                value="${ demographicLocation }"/>
                                        </td>
                                        <td class="msg-value">
                                            <a title="Import"
                                               href="<%= request.getContextPath() %>/messenger/ImportDemographic.do?remoteFacilityId=<c:out value='${ unlinkedDemographic.integratorFacilityId }'/>&remoteDemographicNo=<c:out value='${ unlinkedDemographic.caisiDemographicId }'/>&messageID=<c:out value='${ viewMessageNo }'/>">
                                                Import
                                            </a>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </c:if>
                            <% int demoCount = 0; %>
                            <c:forEach items="${ attachedDemographics }" var="demographic">
                                <c:set var="demographicNumber" value="${ demographic.key }"/>
                                <tr>
                                    <td class="msg-label"></td>
                                    <td class="msg-value">
                                        <input type="text" size="30" readonly
                                               style="background: #f5f5f5; border: 1px solid #ccc;"
                                               value="<Encode:forHtmlAttribute value='${ demographic.value }' />"/>
                                    </td>
                                    <td class="msg-value">
                                        <a class="btn btn-link" href="javascript:popupViewAttach(700,960, '<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<c:out value="${ demographic.key }"/>&displaymode=edit&dboperation=search_detail')">M</a>

                                        <%
                                            boolean showOldEchartLink = true;
                                            UserPropertyDAO propDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
                                            UserProperty oldEchartLink = propDao.getProp(curUser_no, UserProperty.HIDE_OLD_ECHART_LINK_IN_APPT);
                                            if (oldEchartLink != null && "Y".equals(oldEchartLink.getValue()))
                                                showOldEchartLink = false;
                                            CaseManagementNoteDAO caseManagementNoteDAO = SpringUtils.getBean(CaseManagementNoteDAO.class);
                                            if (showOldEchartLink) {
                                                String params = "";
                                                String msgType = (String) session.getAttribute("msgType");

                                                if (msgType != null) {

                                                    if (Integer.valueOf(msgType).equals(OscarMsgType.OSCAR_REVIEW_TYPE)) {
                                                        HashMap<String, List<String>> hashMap = (HashMap<String, List<String>>) session.getAttribute("msgTypeLink");
                                                        if (hashMap != null) {
                                                            List<String> demoList = hashMap.get((String) pageContext.getAttribute("demographicNumber"));

                                                            String[] val = demoList.get(demoCount).split(":");
                                                            if (val.length == 3) {
                                                                String note_id = "";
                                                                CaseManagementNote note = caseManagementNoteDAO.getNote(Long.valueOf(val[2]));
                                                                if (note != null) {
                                                                    String uuid = note.getUuid();
                                                                    List<CaseManagementNote> noteList = caseManagementNoteDAO.getNotesByUUID(uuid);
                                                                    if (noteList.get(noteList.size() - 1).getId().equals(note.getId())) {
                                                                        note_id = String.valueOf(note.getId());
                                                                    } else {
                                                                        note_id = String.valueOf(noteList.get(noteList.size() - 1).getId());
                                                                    }
                                                                }

                                                                params = "&appointmentNo=" + (val[0].equalsIgnoreCase("null") ? "" : val[0]) + "&msgType=" + msgType + "&OscarMsgTypeLink=" + val[1] + "&noteId=" + note_id;
                                                            } else {
                                                                params = "";
                                                            }
                                                        }
                                                    }
                                                }


                                        %>
                                        <a class="btn btn-link" href="javascript:void(0)"
                                           onclick="popupViewAttach(700,960,'<%=request.getContextPath()%>/oscarEncounter/IncomingEncounter.do?demographicNo=<c:out value="${ demographic.key }"/>&curProviderNo=<%=session.getAttribute("providerNo")%>
                                                   <%=params%>');return false;">E</a>
                                        <%} %>

                                        <a class="btn btn-link" href="javascript:popupViewAttach(700,960,'<%=request.getContextPath()%>/oscarRx/choosePatient.do?providerNo=<%=session.getAttribute("providerNo")%>&demographicNo=<c:out value="${ demographic.key }"/>')">Rx</a>



                                        <input type="button" class="btn"
                                               name="writeEncounter" value="Write to encounter"
                                               onclick="popup( '<c:out value="${ demographic.key }"/>','<%=session.getAttribute("viewMessageId")%>','<%=session.getAttribute("providerNo")%>','writeToEncounter')"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td class="msg-label"></td>
                                    <td class="msg-value"><a
                                            href="javascript:popupStart(400,850, '<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=<c:out value="${ demographic.key }"/>&last_name=<%=demoLastName%>&first_name=<%=demoFirstName%>&orderby=appointment_date&displaymode=appt_history&dboperation=appt_history&limit1=0&limit2=25','ApptHist')"
                                            title="Click to see appointment history">Next Appt:
                                        <oscar:nextAppt
                                                demographicNo="${ demographic.key }"/></a></td>
                                    <td class="msg-value"></td>
                                </tr>
                                <% ++demoCount; %>
                            </c:forEach>

                        </c:otherwise>
                    </c:choose>  <!-- end view demographic selection block -->

                </table>
            </div>

        </div>

        <div style="margin-top:5px; margin-left:5px;">
            <a href="javascript:BackToOscar()" class="btn btn-link">
                <i class="fa-solid fa-arrow-right-from-bracket"></i>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnExit"/>
            </a>
        </div>

    </form>
    <% String bodyTextAsHTML = (String) session.getAttribute("viewMessageMessage");
        bodyTextAsHTML = bodyTextAsHTML.replaceAll("\n|\r\n?", "<br/>"); %>
    <p class="NotDisplayable Printable"><%= bodyTextAsHTML %>
    </p>


    <!-- Select demographic modal window for the import demographic process -->
    <div id="selectDemographic" class="modal">
        <div class="modal-content">
            <form id="selectDemographicForm"
                  action="<%= request.getContextPath() %>/messenger/ImportDemographic.do">
                <div class="modal-header">
                    <span id="closeSelectDemographic" class="close">&times;</span>
                    <h2>Local Demographic Matches Found</h2>
                </div>
                <div class="modal-body">
                    <c:if test="${ not empty demographicUserSelect }">
                        <c:forEach items="${ unlinkedDemographics }" var="unlinkedDemographic">
                            <c:if test="${ unlinkedDemographic.caisiDemographicId eq remoteDemographicNo }">
                                <div>
                                    <c:out value="${ unlinkedDemographic.lastName }"/>, <c:out
                                        value="${ unlinkedDemographic.firstName }"/> <br/>
                                    <strong>Gender:</strong> <c:out value="${ unlinkedDemographic.gender }"/><br/>
                                    <strong>HIN:</strong> <c:out value="${ unlinkedDemographic.hin }"/><br/>
                                    <strong>File Location:</strong> <c:out value="${ demographicLocation }"/>
                                    <input type="hidden" id="remoteDemographicNo" name="remoteDemographicNo"
                                           value="<c:out value='${ unlinkedDemographic.caisiDemographicId }'/>"/>
                                    <input type="hidden" id="remoteFacilityId" name="remoteFacilityId"
                                           value="<c:out value='${ unlinkedDemographic.integratorFacilityId }'/>"/>
                                </div>
                            </c:if>
                        </c:forEach>
                        <p>
                            Select a local demographic file to link with this remote demographic. Or select "No Match"
                            to import the remote demographic.
                        </p>
                        <c:forEach items="${ demographicUserSelect }" var="demographicSelect">
                            <div class="demographicOption">
                                <input type="radio" name="selectedDemographicNo"
                                       id="demographic_<c:out value='${ demographicSelect.demographicNo }'/>"
                                       value="<c:out value='${ demographicSelect.demographicNo }'/>"/>
                                <label for="demographic_<c:out value='${ demographicSelect.demographicNo }'/>">
                                    <c:out value="${ demographicSelect.lastName }"/>, <c:out
                                        value="${ demographicSelect.firstName }"/> <br/>
                                    <strong>Gender:</strong> <c:out value="${ demographicSelect.sex }"/><br/>
                                    <strong>HIN:</strong> <c:out value="${ demographicSelect.hin }"/><br/>
                                    <strong>DOB:</strong> <c:out value="${ demographicSelect.birthDayAsString }"/>
                                </label>
                            </div>
                        </c:forEach>
                        <div class="demographicOption">
                            <input type="radio" name="selectedDemographicNo" id="no_selection" value="0"/>
                            <label for="no_selection">
                                No Match
                            </label>
                        </div>

                        <input type="hidden" id="messageID" name="messageID" value="<c:out value='${ viewMessageId }'/>"/>
                    </c:if>
                </div>
            </form>
            <div class="modal-footer">
                <div>
                    <button class="btn" id="cancelbtn" value="cancel">cancel</button>
                    <button class="btn btn-primary" id="linkbtn" value="link">link</button>
                </div>
            </div>
        </div>
    </div>

    <script type="text/javascript">
        /*
         * Modal window scripts for import demographic selector.
         * This is triggered after the user selects to import a remote demographic
         * and several matching demographics are found in the local database.
         */
        var modal = document.getElementById("selectDemographic");
        var span = document.getElementById("closeSelectDemographic");
        var cancel = document.getElementById("cancelbtn");
        var link = document.getElementById("linkbtn");

        //open the modal
        function openSelectDemographicModal() {
            modal.style.display = "block";
        }

        //(x), close the modal
        cancel.onclick = function () {
            modal.style.display = "none";
        }
        span.onclick = function () {
            modal.style.display = "none";
        }
        window.onclick = function (event) {
            if (event.target == modal) {
                modal.style.display = "none";
            }
        }

        // submit actions
        link.onclick = function () {
            var form = document.getElementById("selectDemographicForm");
            var selected = form.elements["selectedDemographicNo"];
            var remoteDemographic = form.elements["remoteDemographicNo"];

            if (!selected.value) {
                alert("Please select a demographic or \"No Match\"");
                return false;
            }

            if (!remoteDemographic) {
                alert("Cannot link this demographic. Contact support.");
                return false;
            }

            form.submit();
            modal.style.display = "none";
        }

        /* the select demographic modal will open if there are
         * a selection of demogrpahic files to select from
         */
        if ("<c:out value='${ demographicUserSelect }'/>") {
            openSelectDemographicModal();
        }
    </script>

    </body>
</html>
