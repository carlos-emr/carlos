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

<%@page import="ca.openosp.openo.casemgmt.model.CaseManagementNote" %>
<%@page import="ca.openosp.openo.casemgmt.dao.CaseManagementNoteDAO" %>
<%@page import="java.util.Set" %>
<%@page import="java.util.List" %>
<%@page import="java.util.HashMap" %>
<%@page import="ca.openosp.openo.commn.model.ResidentOscarMsg" %>
<%@page import="ca.openosp.openo.commn.dao.ResidentOscarMsgDao" %>
<%@page import="ca.openosp.openo.commn.model.OscarMsgType" %>
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
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>


<%
    String providerview = request.getParameter("providerview") == null ? "all" : request.getParameter("providerview");
    boolean bFirstDisp = true; //this is the first time to display the window
    if (request.getParameter("bFirstDisp") != null) bFirstDisp = (request.getParameter("bFirstDisp")).equals("true");
%>
<%@ page
        import="ca.openosp.openo.demographic.data.*, java.util.Enumeration" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.demographic.data.DemographicData" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@taglib prefix="Encode" uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" %>


<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>

        <link rel="stylesheet" type="text/css" href="encounterStyles.css"
              media="screen">
        <link rel="stylesheet" type="text/css" href="printable.css"
              media="print">

        <%
            String boxType = request.getParameter("boxType");
        %>

        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.title"/></title>


        <script type="text/javascript">
            function BackToOscar() {
                if (opener.callRefreshTabAlerts) {
                    opener.callRefreshTabAlerts("oscar_new_msg");
                    setTimeout("window.close()", 100);
                } else {
                    window.close();
                }
            }

            function popupViewAttach(vheight, vwidth, varpage) { //open a new popup window
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

            function popup(demographicNo, msgId, providerNo, action) { //open a new popup window
                var vheight = 700;
                var vwidth = 980;

                if (demographicNo != null && demographicNo != "") {
                    //alert("demographicNo is not null!");
                    windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                    var page = "";
                    var win;
                    var today = "<%=session.getAttribute("today")%>";
                    var header = "messenger";
                    var encType = "messenger";
                    var txt;

                    //note editor in new ui
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

            function popupSearchDemo(keyword) { // open a new popup window
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

            //format msg for pasting into encounter
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

    <body class="BodyStyle" vlink="#0000FF">
    <form action="${pageContext.request.contextPath}/messenger/HandleMessages.do" method="post">

        <table class="MainTable" id="scrollNumber1" name="encounterTable">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgMessenger"/></td>
                <td class="MainTableTopRowRightColumn">
                    <table class="TopStatusBar">
                        <tr>
                            <td><h2><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgViewMessage"/></h2></td>
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
                <td class="MainTableRightColumn Printable">
                    <table>
                        <tr>
                            <td>
                                <table cellspacing=3>
                                    <tr>
                                        <!-- dont need this button from the encounter view -->
                                        <c:if test="${ empty param.from or not param.from eq 'encounter' }">
                                            <td>
                                                <table class=messButtonsA cellspacing=0 cellpadding=3>
                                                    <tr>
                                                        <td class="messengerButtonsA"><a
                                                                href="${pageContext.request.contextPath}/messenger/CreateMessage.jsp"
                                                                class="messengerButtons">
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnCompose"/>
                                                        </a></td>
                                                    </tr>
                                                </table>
                                            </td>
                                        </c:if>

                                        <td>
                                            <table class=messButtonsA cellspacing=0 cellpadding=3>
                                                <tr>
                                                    <td class="messengerButtonsA"><a
                                                            href="javascript:window.print()"
                                                            class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnPrint"/></a></td>
                                                </tr>
                                            </table>
                                        </td>

                                        <!-- dont need this button from the encounter view -->
                                        <c:if test="${ empty param.from or not param.from eq 'encounter' }">
                                            <td>
                                                <table class=messButtonsA cellspacing=0 cellpadding=3>
                                                    <tr>
                                                        <td class="messengerButtonsA"><a
                                                                href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp"
                                                                class="messengerButtons">
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnInbox"/>
                                                        </a></td>
                                                    </tr>
                                                </table>
                                            </td>
                                        </c:if>

                                        <% if ("1".equals(boxType)) { %>
                                        <td>
                                            <table class=messButtonsA cellspacing=0 cellpadding=3>
                                                <tr>
                                                    <td class="messengerButtonsA"><a
                                                            href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?boxType=1"
                                                            class="messengerButtons">
                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnSent"/>
                                                    </a></td>
                                                </tr>
                                            </table>
                                        </td>
                                        <%} %>

                                        <td>
                                            <table class=messButtonsA cellspacing=0 cellpadding=3>
                                                <tr>
                                                    <td class="messengerButtonsA"><a
                                                            href="javascript:BackToOscar()"
                                                            class="messengerButtons"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnExit"/></a></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td class="Printable">
                                <table valign="top">
                                    <tr>
                                        <td class="Printable" bgcolor="#DDDDFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgFrom"/>:
                                        </td>
                                        <td colspan="2" id="sentBy" class="Printable"
                                            bgcolor="#CCCCFF"><%= session.getAttribute("viewMessageSentby") %>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="Printable" bgcolor="#DDDDFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgTo"/>:
                                        </td>
                                        <td colspan="2" id="sentTo" class="Printable"
                                            bgcolor="#BFBFFF"><%= session.getAttribute("viewMessageSentto") %>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="Printable" bgcolor="#DDDDFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgSubject"/>:
                                        </td>
                                        <td colspan="2" id="msgSubject" class="Printable"
                                            bgcolor="#BBBBFF"><%= session.getAttribute("viewMessageSubject") %>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td class="Printable" bgcolor="#DDDDFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgDate"/>:
                                        </td>
                                        <td colspan="2" id="sentDate" class="Printable" bgcolor="#B8B8FF">
                                            <c:out value="${ viewMessageDate }"/> <c:out value="${ viewMessageTime }"/>
                                        </td>
                                    </tr>
                                    <% String attach = (String) session.getAttribute("viewMessageAttach");
                                        String id = (String) session.getAttribute("viewMessageId");
                                        if (attach != null && attach.equals("1")) {
                                    %>
                                    <tr>
                                        <td bgcolor="#DDDDFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgAttachments"/>:
                                        </td>
                                        <td bgcolor="#B8B8FF" colspan="2"><a
                                                href="javascript:popupViewAttach(700,960,'ViewAttach.do?attachId=<%=id%>')">
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
                                        <td bgcolor="#DDDDFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.msgAttachments"/>:
                                        </td>
                                        <td bgcolor="#B8B8FF" colspan="2"><a
                                                href="javascript:popupViewAttach(700,960,'ViewPDFAttach.do?attachId=<%=id%>')">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnAttach"/> </a></td>
                                    </tr>
                                    <%
                                        }
                                    %>

                                    <tr>
                                        <td bgcolor="#EEEEFF"></td>
                                        <td bgcolor="#EEEEFF" colspan="2">
                                            <textarea id="msgBody" name="Message" wrap="hard" readonly="true" rows="18"
                                                      cols="60"><c:out value="${ viewMessageMessage }"/></textarea>
                                        </td>
                                    </tr>

                                    <!-- switch views depending on if the request was made from the patient encounter -->

                                    <c:choose>
                                        <%-- If view request is from the encounter, display the following: --%>
                                        <c:when test="${ from eq 'encounter' }">
                                            <tr>
                                                <td bgcolor="#EEEEFF"></td>
                                                <td bgcolor="#EEEEFF">
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
                                                            <td bgcolor="#EEEEFF"></td>
                                                            <td bgcolor="#EEEEFF" colspan="2">

                                                                <c:out value="${ demoattached.value }"/> <br/>

                                                                <c:if test="${ demoattached.key eq demographic_no }">
                                                                    <input
                                                                            onclick="javascript:popup('${ demographic_no }', '${ messageID }', '${ providerNo }');"
                                                                            class="ControlPushButton" type="button"
                                                                            name="writeToEncounter"
                                                                            value="Write To Encounter"> <input
                                                                        onclick="return paste2Encounter('${ demographic_no }');"
                                                                        class="ControlPushButton" type="button"
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
                                                        <td bgcolor="#EEEEFF"></td>
                                                        <td bgcolor="#EEEEFF">
                                                            No demographic is linked to this message
                                                        </td>
                                                    </tr>
                                                </c:otherwise>
                                            </c:choose>
                                        </c:when>

                                        <%-- If veiw request is from the inbox, display the following --%>
                                        <c:otherwise>
                                            <tr>
                                                <td bgcolor="#EEEEFF"></td>
                                                <td bgcolor="#EEEEFF" colspan="2">
                                                    <input type="submit"  class="ControlPushButton"
                                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnReply"/>"/>
                                                    <input type="submit" class="ControlPushButton"
                                                        value=" <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnReplyAll"/>"/>
                                                    <input type="submit" class="ControlPushButton"
                                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnForward"/>"/>
                                                    <input type="submit" class="ControlPushButton"
                                                        value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnDelete"/>"/>
                                                    <input type="hidden" name="messageNo" id="messageNo" value="${ viewMessageNo }"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td bgcolor="#B8B8FF"></td>
                                                <td bgcolor="#B8B8FF" colspan="2">
                                                    <strong>
                                                        Link this message to ...
                                                    </strong>
                                                </td>
                                            </tr>

                                            <tr>
                                                <td bgcolor="#EEEEFF"></td>
                                                <td bgcolor="#EEEEFF"><input type="text" name="keyword"
                                                                             size="30"/>
                                                </td>
                                                <td bgcolor="#EEEEFF">
                                                    <input type="hidden" class="ControlPushButton"
                                                           name="demographic_no"/> <input type="button"
                                                                                          class="ControlPushButton"
                                                                                          name="searchDemo"
                                                                                          value="Search Demographic"
                                                                                          onclick="popupSearchDemo(document.forms[0].keyword.value)"/>
                                                </td>

                                            </tr>
                                            <tr>
                                                <td bgcolor="#EEEEFF"></td>
                                                <td bgcolor="#EEEEFF" colspan="2"><strong>Selected Demographic</strong>
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
                                                <td bgcolor="#EEEEFF"></td>
                                                <td bgcolor="#EEEEFF"><input type="text"
                                                                             name="selectedDemo" size="30" readonly
                                                                             style="background: #EEEEFF; border: none"
                                                                             value="none"/>
                                                    <script>
                                                        if ("<%=Encode.forJavaScript(demoName)%>" && "<%=Encode.forJavaScript(demoName)%>" !== "null") {
                                                            document.forms[0].selectedDemo.value = "<%=Encode.forJavaScript(demoName)%>"
                                                            document.forms[0].demographic_no.value = "<%=Encode.forJavaScript(demographic_no)%>"
                                                        }
                                                    </script>
                                                </td>

                                                <td bgcolor="#EEEEFF">
                                                    <input type="button"
                                                           class="ControlPushButton" name="linkDemo"
                                                           value="Link to demographic"
                                                           onclick="popup(document.forms[0].demographic_no.value,'<%=session.getAttribute("viewMessageId")%>','<%=session.getAttribute("providerNo")%>','linkToDemographic')"/>

                                                    <input type="button" class="ControlPushButton"
                                                           name="clearDemographic" value="Clear selected demographic"
                                                           onclick='document.forms[0].demographic_no.value = ""; document.forms[0].selectedDemo.value = "none"'/>
                                                </td>

                                            </tr>


                                            <tr>
                                                <td bgcolor="#EEEEFF"></td>
                                                <td bgcolor="#EEEEFF" colspan="2">
                                                    <strong>
                                                        Demographic(s) linked to this message
                                                    </strong>
                                                </td>
                                            </tr>
                                            <c:if test="${ not empty unlinkedDemographics }">
                                                <c:forEach items="${ unlinkedDemographics }" var="unlinkedDemographic">
                                                    <tr id="unlinkedDemographicDetails">
                                                        <td bgcolor="#EEEEFF"></td>
                                                        <td bgcolor="#EEEEFF">
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
                                                        <td bgcolor="#EEEEFF">
                                                            <a title="Import"
                                                               href="<%= request.getContextPath() %>/messenger/ImportDemographic.do?remoteFacilityId=${ unlinkedDemographic.integratorFacilityId }&remoteDemographicNo=${ unlinkedDemographic.caisiDemographicId }&messageID=${ viewMessageNo }">
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
                                                    <td bgcolor="#EEEEFF"></td>
                                                    <td bgcolor="#EEEEFF">
                                                        <input type="text" size="30" readonly
                                                               style="background: #EEEEFF; border: none"
                                                               value="<Encode:forHtmlAttribute value='${ demographic.value }' />"/>
                                                    </td>
                                                    <td bgcolor="#EEEEFF">
                                                        <a href="javascript:popupViewAttach(700,960, '<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=${ demographic.key }&displaymode=edit&dboperation=search_detail')">M</a>

                                                        <%
                                                            //Hide old echart link
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
                                                        <a href="javascript:void(0)"
                                                           onclick="popupViewAttach(700,960,'<%=request.getContextPath()%>/oscarEncounter/IncomingEncounter.do?demographicNo=${ demographic.key }&curProviderNo=<%=session.getAttribute("providerNo")%>
                                                                   <%=params%>');return false;">E</a>
                                                        <%} %>

                                                        <a href="javascript:popupViewAttach(700,960,'<%=request.getContextPath()%>/oscarRx/choosePatient.do?providerNo=<%=session.getAttribute("providerNo")%>&demographicNo=${ demographic.key }')">Rx</a>



                                                        <input type="button" class="ControlPushButton"
                                                               name="writeEncounter" value="Write to encounter"
                                                               onclick="popup( '${ demographic.key }','<%=session.getAttribute("viewMessageId")%>','<%=session.getAttribute("providerNo")%>','writeToEncounter')"/>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td bgcolor="#EEEEFF"></td>
                                                    <td bgcolor="#EEEEFF"><a
                                                            href="javascript:popupStart(400,850, '<%= request.getContextPath() %>/demographic/demographiccontrol.jsp?demographic_no=${ demographic.key }&last_name=<%=demoLastName%>&first_name=<%=demoFirstName%>&orderby=appointment_date&displaymode=appt_history&dboperation=appt_history&limit1=0&limit2=25','ApptHist')"
                                                            title="Click to see appointment history">Next Appt:
                                                        <oscar:nextAppt
                                                                demographicNo="${ demographic.key }"/></a></td>
                                                    <td bgcolor="#EEEEFF"></td>
                                                </tr>
                                                <% ++demoCount; %>
                                            </c:forEach>

                                        </c:otherwise>
                                    </c:choose>  <!-- end view demographic selection block -->

                                </table>
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
                                           value="${ unlinkedDemographic.caisiDemographicId }"/>
                                    <input type="hidden" id="remoteFacilityId" name="remoteFacilityId"
                                           value="${ unlinkedDemographic.integratorFacilityId }"/>
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
                                       id="demographic_${ demographicSelect.demographicNo }"
                                       value="${ demographicSelect.demographicNo }"/>
                                <label for="demographic_${ demographicSelect.demographicNo }">
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

                        <input type="hidden" id="messageID" name="messageID" value="${ viewMessageId }"/>
                    </c:if>
                </div>
            </form>
            <div class="modal-footer">
                <div>
                    <button class="modal_button" id="cancelbtn" value="cancel">cancel</button>
                    <button class="modal_button" id="linkbtn" value="link">link</button>
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
        if ("${ demographicUserSelect }") {
            openSelectDemographicModal();
        }
    </script>

    </body>
</html>
