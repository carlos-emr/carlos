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
  - PDF attachment preview
  - Markdown rendering via Toast UI Editor viewer mode
  - XSS-safe HTML sanitization for rendered markdown content

  Security:
  - Requires "_msg" object with read ("r") permissions
  - Validates user access to specific messages
  - XSS-safe custom HTML sanitizer strips dangerous elements and event
    handlers from markdown output

  Request parameters:
  - messageID: Unique identifier of message to view
  - boxType: Source mailbox (inbox, sent, deleted)
  - demographic_no: Associated patient ID if applicable

  Frontend Dependencies:
  - Bootstrap 5.0.2 (responsive layout and button styles)
  - Font Awesome 3.x (icons for reply, forward, delete, etc.)
  - Toast UI Editor 3.x (viewer mode for markdown-formatted message bodies)

  Integration points:
  - Case management notes for clinical documentation
  - PDF document management
  - Patient encounter system

  @since 2002
--%>

<%@page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.OscarMsgType" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<fmt:setBundle basename="oscarResources"/>
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

<%
    // bodyTextAsHTML: the message body content retrieved from session for display.
    String bodyTextAsHTML = (String) session.getAttribute("viewMessageMessage");
    if (bodyTextAsHTML == null) {
        bodyTextAsHTML = "";
    }
%>
<!DOCTYPE html>

<html>
<head>
<script src="<%= request.getContextPath() %>/js/global.js"></script>
<link href="<%=request.getContextPath() %>/library/bootstrap/5.0.2/css/bootstrap.css" rel="stylesheet" type="text/css">
<link rel="stylesheet" href="<%=request.getContextPath() %>/css/font-awesome.min.css">
<script src="<%=request.getContextPath() %>/library/dompurify/purify.min.js"></script>
<script src="<%=request.getContextPath() %>/library/toastui/toastui-editor-all.min.js"></script>

<%
String boxType = request.getParameter("boxType");
%>

<title><fmt:message key="messenger.ViewMessage.title" /></title>


<script type="text/javascript" src="<%= request.getContextPath() %>/messenger/messenger-common.js"></script>
<script>
// Opens attachment in a popup window; routes to the encounter window if the URL contains IncomingEncounter
function popupViewAttach(vheight,vwidth,varpage) {
  var page = varpage;
  windowprops = "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
  var winName;

  if( page.indexOf("IncomingEncounter.do") > -1 ) {
    winName = "encounter";
  }
  else {
    winName = "oscarMVA";
  }

  var popup=window.open(varpage, winName, windowprops);
  if (popup != null) {
    if (popup.opener == null) {
      popup.opener = self;
    }
  }


}

// Handles demographic-related actions: writing message content to encounter notes
// or linking this message to a patient demographic record.
// When action is "writeToEncounter", attempts multiple strategies to paste the
// formatted message into the encounter note editor (direct, Angular, or form POST).
// When action is "linkToDemographic", submits a form to associate the demographic.
function popup(demographicNo, msgId, providerNo, action) {
  var vheight = 700;
  var vwidth = 980;

  if (demographicNo!=null &&  demographicNo!="" ){
      windowprops = "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
      var page = "";
      var win;
      var txt;

      //note editor in new ui
      var noteEditorId = "noteEditor"+demographicNo;
      var noteEditor;
      var ngApp;
      if (window.parent.opener){
        noteEditor = window.parent.opener.document.getElementById(noteEditorId);
        var ngApp = window.parent.opener.document.body.parentElement.getAttribute("ng-app");
        }

      if ( action == "writeToEncounter") {
          win = window.open("","<fmt:message key="provider.appointmentProviderAdminDay.apptProvider"/>");
          if ( win.pasteToEncounterNote && win.demographicNo == demographicNo ) {
            txt = fmtOscarMsg();
            win.pasteToEncounterNote(txt);
          } else if ( noteEditor != undefined ){
        	win.close();
        	txt = "\n" + fmtOscarMsg();
        	noteEditor.value = noteEditor.value + txt;
          } else if ( noteEditor == undefined && ngApp != undefined ){
        	  win.close();
        	  txt = "\n" + fmtOscarMsg();
        	  getAngJsPath = window.opener.location.href;
        	  newAngJsPath = getAngJsPath.substring(0, getAngJsPath.indexOf('#')+2) + "record/" + demographicNo + "/summary?noteEditorText=" + encodeURI(txt);
        	  window.opener.location.href = newAngJsPath;
          } else {
              win.close();
              var writeForm = document.createElement('form');
              writeForm.method = 'post';
              writeForm.action = 'WriteToEncounter.do';
              writeForm.target = "<fmt:message key="provider.appointmentProviderAdminDay.apptProvider"/>";
              var writeFields = {'demographic_no': demographicNo, 'msgId': msgId, 'providerNo': providerNo, 'encType': 'messenger'};
              for (var k in writeFields) {
                  var inp = document.createElement('input');
                  inp.type = 'hidden'; inp.name = k; inp.value = writeFields[k];
                  writeForm.appendChild(inp);
              }
              document.body.appendChild(writeForm);
              var popUp = window.open('', writeForm.target, windowprops);
              writeForm.submit();
              if (popUp != null) {
                  if (popUp.opener == null) {
                      popUp.opener = self;
                  }
                  popUp.focus();
              }
          }
      }
      else if ( action == "linkToDemographic"){
          var linkForm = document.createElement('form');
          linkForm.method = 'post';
          linkForm.action = 'ViewMessage.do';
          var linkFields = {'linkMsgDemo': 'true', 'demographic_no': demographicNo, 'messageID': msgId, 'providerNo': providerNo};
          for (var lk in linkFields) {
              var li = document.createElement('input');
              li.type = 'hidden'; li.name = lk; li.value = linkFields[lk];
              linkForm.appendChild(li);
          }
          document.body.appendChild(linkForm);
          linkForm.submit();
      }
  }

}

function popupStart(vheight,vwidth,varpage,windowname) {
    var page = varpage;
    windowprops = "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
    var popup=window.open(varpage, windowname, windowprops);
}

// Formats the current message fields (from, to, date, subject, body) into a
// plain text string suitable for pasting into encounter notes
function fmtOscarMsg() {
    txt = "From: ";
    tmp = document.getElementById("sentBy").innerHTML;
    tmp = tmp.replace(/^\s+|\s+$/g,"");
    txt += tmp;
    txt += "\nTo: ";
    tmp = document.getElementById("sentTo").innerHTML;
    tmp = tmp.replace(/^\s+|\s+$/g,"");
    txt += tmp;
    txt += "\nDate: ";
    tmp = document.getElementById("sentDate").innerHTML;
    tmp = tmp.replace(/\s+|\n+/g,"");
    tmp = tmp.replace(/&nbsp;/g," ");
    txt += tmp;
    txt += "\nSubject: ";
    tmp = document.getElementById("msgSubject").innerHTML;
    tmp = tmp.replace(/^\s+|\s+$/g,"");
    txt += tmp;
    txt += "\n\n";
    tmp = document.getElementById("msgBody").innerHTML;
    tmp = tmp.replace(/^\s+|\s+$/g,"");
    txt += tmp;

    return txt;

}

</script>
<style type="text/css">
    .emphasis {
	    font-weight:bold;
	}
    .subheader {
	    background-color:silver;
	}
blockquote p {
font-size:14px;
}
p.toastui-editor-contents {
font-size:17px;
}
    .modal {
      font-size: 11px;
      display: none; /* Hidden by default */
      position: fixed; /* Stay in place */
      z-index: 1; /* Sit on top */
      left: 0;
      top: 0;
      width: 100%; /* Full width */
      height: 100%; /* Full height */
      overflow: auto; /* Enable scroll if needed */
      background-color: rgb(0,0,0); /* Fallback color */
      background-color: rgba(0,0,0,0.4); /* Black w/ opacity */
    }
#print_helper {
  display: none;
}
</style>
<style type="text/css" media="print">

 .DoNotPrint {
	display: none;
}
#print_helper {
    display: block;
    overflow: visible;
    font-family: Menlo, "Deja Vu Sans Mono", "Bitstream Vera Sans Mono", Monaco, monospace;
    white-space: pre;
    white-space: pre-wrap;
}
</style>

</head>

<body class="BodyStyle">
<form action="<%=request.getContextPath()%>/messenger/HandleMessages.do" method="post">
  <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>

	<table class="MainTable" id="scrollNumber1" style="width:95%">
		<tr class="MainTableTopRow">
			<td class="MainTableTopRowLeftColumn"><h4>&nbsp;<i class="icon-envelope" title='<fmt:message key="messenger.ViewMessage.msgMessenger" />'></i>&nbsp;</h4></td>
			<td class="MainTableTopRowRightColumn">
			<table class="TopStatusBar" style="width:100%">
				<tr>
					<td><h4><fmt:message key="messenger.ViewMessage.msgViewMessage" /></h4></td>
            <td style="text-align: right;" class="DoNotPrint" >
            <i class=" icon-question-sign"></i>
            <a href="javascript:void(0)" onClick ="popupPage(700,960,''+'Messenger view')"><fmt:message key="app.top1"/></a>
            <i class=" icon-info-sign" style="margin-left:10px;"></i>
            <a href="javascript:void(0)"  onClick="window.open('<%=request.getContextPath()%>/oscarEncounter/About.jsp','About CARLOS','scrollbars=1,resizable=1,width=800,height=600,left=0,top=0')" ><fmt:message key="global.about" /></a>
        </td>
		</tr>
			</table>
			</td>
		<tr style="width:100%;">
			<td class="MainTableLeftColumn">&nbsp;</td>
			<td class="MainTableRightColumn Printable" colspan="2">
			<table style="width:100%">
				<tr class="DoNotPrint">
					<td>
					<table>
						<tr>
							<!-- dont need this button from the encounter view -->
							<c:if test="${ empty param.from or not param.from eq 'encounter' }">
								<td>
								<table class=messButtonsA>
									<tr>
										<td class="messengerButtonsA">
                                            <a href="${pageContext.request.contextPath}/messenger/CreateMessage.jsp"
                                                class="btn btn-outline-secondary">
                                                <fmt:message key="messenger.ViewMessage.btnCompose"/>
                                            </a>
                                        </td>
									</tr>
								</table>
								</td>
							</c:if>

							<td>
							<table class=messButtonsA >
								<tr>
									<td class="messengerButtonsA">
									    <a href="javascript:window.print()" class="btn btn-outline-secondary"><fmt:message key="messenger.ViewMessage.btnPrint" />
									    </a>
									</td>
								</tr>
							</table>
							</td>

							<!-- dont need this button from the encounter view -->
							<c:if test="${ empty param.from or not param.from eq 'encounter' }">
								<td>
								<table class=messButtonsA >
									<tr>
										<td class="messengerButtonsA">
									        <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp"
									            class="btn btn-outline-secondary">
									            <fmt:message key="messenger.ViewMessage.btnInbox"/>
									        </a>
									    </td>
									</tr>
								</table>
								</td>
							</c:if>

							<% if( "1".equals(boxType) ) { %>
							<td>
							<table class=messButtonsA >
								<tr>
									<td class="messengerButtonsA">
									    <a href="${pageContext.request.contextPath}/messenger/DisplayMessages.jsp?boxType=1"
									        class="btn btn-outline-secondary">
									        <fmt:message key="messenger.ViewMessage.btnSent"/>
									    </a>
									</td>
								</tr>
							</table>
							</td>
							<%} %>

							<td>
							<table class=messButtonsA >
								<tr>
									<td class="messengerButtonsA">
									    <a href="javascript:BackToCarlos()" class="nav-link">
                                        <fmt:message key="messenger.ViewMessage.btnExit" />
									    </a>
									</td>
								</tr>
							</table>
							</td>
						</tr>
					</table>
					</td>
				</tr>
				<tr>
					<td class="Printable">

					<table valign="top" class="well"  style="width:100%"><!-- the messageblock -->
						<tr>
							<td class="Printable emphasis" ><fmt:message key="messenger.ViewMessage.msgFrom" />:</td>
							<td colspan="2" id="sentBy" class="Printable" ><c:out value="${ viewMessageSentby }" />
							</td>
						</tr>
						<tr>
							<td class="Printable emphasis" ><fmt:message key="messenger.ViewMessage.msgTo" />:</td>
							<td colspan="2" id="sentTo" class="Printable" ><c:out value="${ viewMessageSentto }" />
							</td>
						</tr>
						<tr>
							<td class="Printable emphasis" ><fmt:message key="messenger.ViewMessage.msgSubject" />:</td>
							<td colspan="2" id="msgSubject" class="Printable" ><c:out value="${ viewMessageSubject }" />
							</td>
						</tr>

						<tr>
							<td class="Printable emphasis" ><fmt:message key="messenger.ViewMessage.msgDate" />:</td>
							<td colspan="2" id="sentDate" class="Printable" >
								<c:out value="${ viewMessageDate }" /> <c:out value="${ viewMessageTime }" />
							</td>
						</tr>
						<%-- Display file and PDF attachments if present in session --%>
						<%  String attach = (String) session.getAttribute("viewMessageAttach");
                                    String id = (String) session.getAttribute("viewMessageId");
                                    if ( attach != null && attach.equals("1") ){
                                    %>
						<tr>
							<td><fmt:message key="messenger.ViewMessage.msgAttachments" />:</td>
							<td colspan="2"><a
								href="javascript:popupViewAttach(700,960,'ViewAttach.do?attachId=<%=Encode.forJavaScript(id)%>')">
							<fmt:message key="messenger.ViewMessage.btnAttach" /> </a></td>
						</tr>
						<%
                                    }
                                %>
						<%
                                    String pdfAttach = (String) session.getAttribute("viewMessagePDFAttach");
                                    if ( pdfAttach != null && pdfAttach.equals("1") ){
                                    %>
						<tr>
							<td><fmt:message key="messenger.ViewMessage.msgAttachments" />:</td>
							<td colspan="2"><a
								href="javascript:popupViewAttach(700,960,'ViewPDFAttach.do?attachId=<%=Encode.forJavaScript(id)%>')">
							<fmt:message key="messenger.ViewMessage.btnAttach" /> </a></td>
						</tr>
						<%
                                    }
                                %>

						<%--
						  Message body display: the hidden textarea holds HTML-encoded content
						  that JavaScript reads and passes to the Toast UI viewer for markdown
						  rendering. The print_helper div provides a plain-text fallback for
						  print media.
						--%>
						<tr>
							<td></td>
							<td colspan="2" class="Printable"><p>&nbsp;</p>

                            <div id="viewer" class="DoNotPrint"></div>
								<textarea id="msgBody" name="Message" wrap="hard" readonly rows="18" cols="80" class="DoNotPrint" style="display:none; min-width: 100%"><%=Encode.forHtml(bodyTextAsHTML)%></textarea>

                            <div id="print_helper"><%=Encode.forHtml(bodyTextAsHTML)%></div>
							</td>
						</tr>

						<!-- switch views depending on if the request was made from the patient encounter -->

						<c:choose>
						<%-- If view request is from the encounter, display the following: --%>
						<c:when test="${ from eq 'encounter' }">
							<tr>
								<td></td>
								<td>
								<strong>
									<fmt:message key="messenger.ViewMessage.demoLinked" />
								</strong>
								</td>
							</tr>

							<%-- display the list of attached demographics --%>
							<c:choose>
								<c:when test="${ not empty attachedDemographics }">
									<c:forEach items="${ attachedDemographics }" var="demoattached">
										<tr>
										<td></td>
										<td  colspan="2">

											<c:out value="${ demoattached.value }" /> <br />

											<c:if test="${ demoattached.key eq demographic_no }">
												<input
													onclick="javascript:popup('${ fn:escapeXml(demographic_no) }', '${ fn:escapeXml(messageID) }', '${ fn:escapeXml(providerNo) }');"
													class="btn DoNotPrint" type="button"  name="writeToEncounter"
													value="<fmt:message key="messenger.ViewMessage.writeToE" />">
											 </c:if>
										</td>
										</tr>
									</c:forEach>
								</c:when>

								<%--  or send a message that no demographic is linked --%>
								<c:otherwise>
									<tr>
									<td ></td>
									<td>
										<fmt:message key="messenger.ViewMessage.demoNotLinked" />
									</td>
								</tr>
								</c:otherwise>
							</c:choose>
						</c:when>

						<%-- If view request is from the inbox, display the following --%>
						<c:otherwise>
						<tr class="DoNotPrint">
							<td ></td>
							<td  colspan="2">
								<button type="submit" class="btn" name="reply"
                                    title="<fmt:message key="messenger.ViewMessage.btnReply"/>"><i class="icon-reply"></i>&nbsp;<fmt:message key="messenger.ViewMessage.btnReply"/></button>
                                <button type="submit" class="btn" name="replyAll"
                                    title="<fmt:message key="messenger.ViewMessage.btnReplyAll"/>"><i class="icon-reply-all"></i>&nbsp;<fmt:message key="messenger.ViewMessage.btnReplyAll"/></button>
                                <button type="submit" class="btn" name="forward"
                                    title="<fmt:message key="messenger.ViewMessage.btnForward"/>"><i class="icon-share-alt"></i>&nbsp;<fmt:message key="messenger.ViewMessage.btnForward"/></button>
                                <button type="submit" class="btn" name="delete"
                                    title="<fmt:message key="messenger.ViewMessage.btnDelete"/>"><i class="icon-trash"></i>&nbsp;<fmt:message key="messenger.ViewMessage.btnDelete"/></button>
                                <input type="hidden" name="messageNo" id="messageNo" value="${ fn:escapeXml(viewMessageNo) }"/>
							</td>
						</tr>
						<tr class="subheader DoNotPrint">
							<td></td>
							<td colspan="2">
							<strong>
								<fmt:message key="messenger.ViewMessage.linkTo" />
							</strong>
							</td>
						</tr>

						<tr class="DoNotPrint">
							<td></td>
							<td><input type="text" name="keyword"
								size="30" />
							</td>
							<td>
							<input type="hidden" class="btn"
								name="demographic_no" /> <input type="button"
								class="btn" name="searchDemo"
								value="<fmt:message key="messenger.ViewMessage.searchDemo" />"
								onclick="popupSearchDemo(document.forms[0].keyword.value)" />
							</td>

						</tr>
						<tr class="DoNotPrint">
							<td></td>
							<td colspan="2"><strong><fmt:message key="messenger.ViewMessage.selectedDemo" /></strong></td>
						</tr>

                                            <%
                                                // Null-safe session attribute retrieval for use in the link button onclick
                                                String viewMsgId = session.getAttribute("viewMessageId") != null ? (String) session.getAttribute("viewMessageId") : "";
                                                String viewProvNo = session.getAttribute("providerNo") != null ? (String) session.getAttribute("providerNo") : "";

                                                // Demographic search and link section: looks up a patient by
                                                // demographic_no from the request and allows associating (linking)
                                                // that patient with this message.
                                                String demographic_no = request.getParameter("demographic_no");
                                                DemographicData demoData = new DemographicData();
                                                Demographic demo = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographic_no);
                                                String demoName = "";
                                                String demoLastName = "";
                                                String demoFirstName = "";
                                                if (demo != null) {
                                                    demoName = demo.getLastName() + ", " + demo.getFirstName();
                                                    demoLastName = demo.getLastName();
                                                    demoFirstName = demo.getFirstName();

                                                } %>
						<tr class="DoNotPrint">
							<td></td>
							<td><input type="text"
								name="selectedDemo" size="30" readonly
								style="border: none" value="none" /> <script>
                                            if ( "<%=Encode.forJavaScript(demoName)%>" != "null" && "<%=Encode.forJavaScript(demoName)%>" != "") {
                                                document.forms[0].selectedDemo.value = "<%=Encode.forJavaScript(demoName)%>"
                                                document.forms[0].demographic_no.value = "<%=Encode.forJavaScript(demographic_no)%>"
                                            }
                                        </script>
                                </td>

                                <td>
                                        <input type="button"
								class="btn" name="linkDemo"
								value="<fmt:message key="messenger.ViewMessage.linkToDemo" />"
								onclick="popup(document.forms[0].demographic_no.value,'<%=Encode.forJavaScript(viewMsgId)%>','<%=Encode.forJavaScript(viewProvNo)%>','linkToDemographic')" />

							<input type="button" class="btn"
								name="clearDemographic" value="<fmt:message key="messenger.ViewMessage.clearDemo" />"
								onclick='document.forms[0].demographic_no.value = ""; document.forms[0].selectedDemo.value = "none"' />
							</td>

						</tr>


						<tr>
							<td></td>
							<td colspan="2">
								<strong>
									<fmt:message key="messenger.ViewMessage.demoLinked" />
								</strong>
							</td>
						</tr>
                        <%-- Display demographics already linked to this message with
                             encounter (E), prescription (Rx), and appointment shortcuts --%>
                        <% int demoCount = 0; %>
                        <c:forEach items="${ attachedDemographics }" var="demographic">
             			<c:set var="demographicNumber" value="${ demographic.key }" />
						<% String demoKeyJs = Encode.forJavaScript(String.valueOf(pageContext.getAttribute("demographicNumber"))); %>
							<tr>
								<td></td>
								<td>
								<input type="text" size="30" readonly
									style=" border: none"
									value="${ fn:escapeXml(demographic.value) }" />
								</td>
								<td class="DoNotPrint">
								<a href="javascript:popupViewAttach(700,960,'../demographic/demographiccontrol.jsp?demographic_no=<%=demoKeyJs%>&displaymode=edit&dboperation=search_detail')"><fmt:message key="global.M" /></a>

								<%
									CaseManagementNoteDAO caseManagementNoteDAO = SpringUtils.getBean(CaseManagementNoteDAO.class);
	                                                            String params = "";
	                                                            String msgType = (String)session.getAttribute("msgType");

	                                                            if( msgType != null ) {

	                                                                    if( Integer.valueOf(msgType).equals(OscarMsgType.OSCAR_REVIEW_TYPE) ) {
	                                                                        HashMap<String,List<String>> hashMap =  (HashMap<String,List<String>>)session.getAttribute("msgTypeLink");
	                                                                        if( hashMap != null) {
	                                                                            List<String> demoList = hashMap.get((String) pageContext.getAttribute("demographicNumber"));

	                                                                            if( demoList != null && demoCount < demoList.size() ) {
	                                                                             String[] val = demoList.get(demoCount).split(":");
	                                                                             if( val.length == 3 ) {
	                                                                                 String note_id = "";
	                                                                                 CaseManagementNote note = caseManagementNoteDAO.getNote(Long.valueOf(val[2]));
	                                                                                 if( note != null ) {
	                                                                                     String uuid = note.getUuid();
	                                                                                     List<CaseManagementNote> noteList = caseManagementNoteDAO.getNotesByUUID(uuid);
	                                                                                     if( noteList != null && !noteList.isEmpty() ) {
	                                                                                         if( noteList.get(noteList.size()-1).getId().equals(note.getId()) ) {
	                                                                                             note_id = String.valueOf(note.getId());
	                                                                                         }
	                                                                                         else {
	                                                                                             note_id = String.valueOf(noteList.get(noteList.size()-1).getId());
	                                                                                         }
	                                                                                     }
	                                                                                 }

	                                                                                params = "&appointmentNo=" + (val[0].equalsIgnoreCase("null") ? "" :  val[0]) +"&msgType=" + msgType + "&OscarMsgTypeLink="+val[1]+"&noteId="+note_id;
	                                                                             }
	                                                                             else {
	                                                                                 params = "";
	                                                                             }
	                                                                            }
	                                                                         }
	                                                                    }
	                                                                }



	                                                        %>
	                                                         <a href="javascript:void(0)" onclick="popupViewAttach(700,960,'../oscarEncounter/IncomingEncounter.do?demographicNo=<%=demoKeyJs%>&curProviderNo=<%=Encode.forJavaScript((String)session.getAttribute("providerNo"))%><%=Encode.forJavaScript(params)%>');return false;"><fmt:message key="global.E" /></a>

								<a href="javascript:popupViewAttach(700,960,'../oscarRx/choosePatient.do?providerNo=<%=Encode.forJavaScript((String)session.getAttribute("providerNo"))%>&demographicNo=<%=demoKeyJs%>')">Rx</a>





								<input type="button" class="btn DoNotPrint"
									name="writeEncounter" value="<fmt:message key="messenger.ViewMessage.writeToE" />"
									onclick="popup( '<%=demoKeyJs%>','<%=Encode.forJavaScript((String)session.getAttribute("viewMessageId"))%>','<%=Encode.forJavaScript((String)session.getAttribute("providerNo"))%>','writeToEncounter')" />
								</td>
							</tr>
							<tr>
								<td></td>
								<td><a
									href="javascript:popupStart(400,850,'../demographic/demographiccontrol.jsp?demographic_no=<%=demoKeyJs%>&last_name=<%=Encode.forUriComponent(demoLastName)%>&first_name=<%=Encode.forUriComponent(demoFirstName)%>&orderby=appointment_date&displaymode=appt_history&dboperation=appt_history&limit1=0&limit2=25','ApptHist')"
									title="<fmt:message key="messenger.ViewMessage.clickApptHx" />"><fmt:message key="caseload.msgNextAppt" />:    <oscar:nextAppt demographicNo="${ demographic.key }" /></a></td>
								<td></td>
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

<script>
    // Initialize Toast UI Editor in read-only viewer mode to render the message
    // body as markdown. A custom HTML sanitizer removes dangerous elements
    // (script, iframe, object, embed, form) and strips event-handler attributes
    // and javascript: hrefs to prevent XSS. Falls back to showing the plain
    // textarea if the editor library fails to load.
    var content=document.getElementById("msgBody").value;
    content = content.replace(/\r\n/g, "\n");

    var viewer;
    try {
        viewer = new toastui.Editor.factory({
            el: document.getElementById('viewer'),
            usageStatistics: false,
            viewer: true,
            initialEditType: 'wysiwyg',
            initialValue: content,
            height: '500px',
            customHTMLSanitizer: function(html) {
                return DOMPurify.sanitize(html);
            }
        });
    } catch (e) {
        console.error('Toast UI viewer failed to initialize:', e);
        document.getElementById('msgBody').style.display = '';
        var warning = document.createElement('div');
        warning.className = 'alert alert-warning mt-2';
        warning.textContent = 'Rich text viewer could not load. Using plain text mode.';
        document.getElementById('viewer').parentNode.insertBefore(warning, document.getElementById('viewer'));
    }

</script>
</body>
</html>
