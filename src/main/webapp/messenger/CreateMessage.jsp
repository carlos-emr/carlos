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
    CreateMessage.jsp - Main message composition interface for the CARLOS EMR messaging system

    Purpose:
    This JSP page provides the primary interface for healthcare providers to compose and send
    internal messages within the EMR system. It supports creating new messages, replying to
    existing messages, and forwarding messages with attachments.

    Key Features:
    - Message composition with subject and body text
    - Rich text editing via Toast UI Editor with WYSIWYG and Markdown modes
    - XSS-safe HTML sanitization for editor content
    - Recipient selection from provider lists
    - Group-based recipient management
    - Patient demographic association for clinical messages
    - Attachment support including PDF documents
    - Delegate/proxy messaging capabilities
    - Reply and forward functionality with quoted text

    Security:
    - Requires write permissions on "_msg" object
    - Session validation through msgSessionBean
    - OWASP encoding for XSS prevention
    - DOMPurify sanitizes editor content to prevent XSS

    Dependencies:
    - MsgSessionBean: Maintains message session state
    - MessengerGroupManager: Handles provider groups and membership
    - MessagingManager: Core messaging operations
    - DemographicData: Patient information retrieval

    Frontend Dependencies:
    - Bootstrap 5.0.2 (responsive layout)
    - Font Awesome 6.x (icons)
    - Toast UI Editor 3.x (WYSIWYG/Markdown rich text editor with i18n)
    - No jQuery dependency (uses native DOMContentLoaded)

    Request Parameters:
    - subject: Initial message subject
    - demographic_no: Associated patient ID (request attribute, not parameter)
    - ReSubject: Reply subject (from reply action)
    - ReText: Quoted text for replies

    Session Attributes:
    - msgSessionBean: Message composition session data
    - userrole: Current user role
    - user: Current username

    @since 2002-11-08
--%>

<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<%@ page import="org.w3c.dom.*" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.util.Msgxml" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.managers.MessagingManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Groups" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.data.MsgProviderData" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>

<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.managers.MessengerGroupManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%
    // Security check: Build role string from session attributes for authorization
    String userrole = (String) session.getAttribute("userrole");
    String user = (String) session.getAttribute("user");
    String roleName$ = (userrole != null ? userrole : "") + "," + (user != null ? user : "");
    boolean authed = true;
%>
<%-- Security tag: Verify user has write permissions for messaging module --%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    // Exit page execution if user is not authorized
    if (!authed) {
        return;
    }
%>


<%-- Session validation: Ensure message session bean exists and is valid --%>
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
    // Initialize messaging managers and retrieve provider/group data
    MessengerGroupManager groupManager = SpringUtils.getBean(MessengerGroupManager.class);
    Map<Groups, List<MsgProviderData>> groups = groupManager.getAllGroupsWithMembers(LoggedInInfo.getLoggedInInfoFromSession(request));
    List<MsgProviderData> localMembers = groupManager.getAllLocalMembers(LoggedInInfo.getLoggedInInfoFromSession(request));
    MessagingManager messagingManager = SpringUtils.getBean(MessagingManager.class);

    // Store provider and group data in request scope for JSP access
    request.setAttribute("groupManager", groups);
    request.setAttribute("localMembers", localMembers);

    // Set up message subject and body (from new message, reply, or forward).
    // Note: This always overwrites the subject parameter above. When ReSubject is
    // null (new message), messageSubject becomes null.
    pageContext.setAttribute("messageSubject", request.getParameter("subject"));
    pageContext.setAttribute("messageSubject", request.getAttribute("ReSubject"));
    pageContext.setAttribute("messageBody", request.getAttribute("ReText"));

    // Retrieve the message session bean for maintaining state
    MsgSessionBean bean = (MsgSessionBean) pageContext.findAttribute("bean");

    // Handle patient demographic association if message is patient-related
    String demographic_no = (String) request.getAttribute("demographic_no");
    DemographicData demoData = new DemographicData();
    Demographic demo = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographic_no);
    String demoName = "";
    if (demo != null) {
        demoName = demo.getLastName() + ", " + demo.getFirstName();
    }

    // Initialize delegate/proxy provider variables
    String delegate = "";
    String delegateName = "";
    boolean recall = (request.getParameter("recall") != null);

    if (recall) {
        String subjectText = messagingManager.getLabRecallMsgSubjectPref(LoggedInInfo.getLoggedInInfoFromSession(request));
        delegate = messagingManager.getLabRecallDelegatePref(LoggedInInfo.getLoggedInInfoFromSession(request));
        if (delegate != null && !delegate.isEmpty()) {
            delegateName = messagingManager.getDelegateName(delegate);
        }
        pageContext.setAttribute("delegateName", delegateName);
        pageContext.setAttribute("messageSubject", subjectText);
    }

%>
<!DOCTYPE html>
<html>
    <head>
    <title><fmt:message key="messenger.CreateMessage.title"/></title>
    <style>
        summary {
                cursor: pointer;
        }

        .muted {
                color: silver;
        }

        .group_member_contact {
                margin-left: 15px;
        }

        summary label {
                font-weight: bold;
        }
    </style>

    <!-- js -->
    <script src="<%=request.getContextPath() %>/library/dompurify/purify.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/toastui/toastui-editor-all.min.js"></script>
    <script src="<%= request.getContextPath() %>/messenger/messenger-common.js"></script>
    <c:set var="langCode"><fmt:message key="global.i18nLanguagecode"/></c:set>
    <c:if test="${langCode != 'en-GB'}">
    <script src="<%=request.getContextPath() %>/library/toastui/i18n/${fn:escapeXml(langCode)}.js"></script>
    </c:if>

    <!-- css -->
    <link href="<%=request.getContextPath() %>/library/toastui/toastui-editor.min.css" rel="stylesheet">
    <link href="<%=request.getContextPath() %>/library/bootstrap/5.0.2/css/bootstrap.css" rel="stylesheet">
    <link href="<%=request.getContextPath() %>/css/fontawesome-all.min.css" rel="stylesheet"><!-- fontawesome 6.x -->

    <style>
        .toastui-editor-contents{
            font-size: 17px;
        }
    </style>

<script>


// Toggles all provider checkboxes within a group when the group header checkbox is clicked
function checkGroup(group) {
    // 1. Find all input elements that have the same class as the group's ID
    const inputs = document.querySelectorAll("input." + group.id);

    // 2. Determine if the main group checkbox is checked
    const isChecked = group.checked;

    // 3. Loop through them and set their checked status
    inputs.forEach(function (input) {
        input.checked = isChecked;
    });
}

function validateFields() {
    // Sync editor content to hidden textarea before validation
    if (typeof editor !== 'undefined') {
        document.getElementsByName("message")[0].value = editor.getMarkdown();
    }
    // Check if message body is empty
    if (document.forms[0].message.value.length === 0) {
        alert('<fmt:message key="messenger.CreateMessage.msgEmptyMessage"/>');
        return false;
    }
    // Validate check boxes
    var val = validateCheckBoxes(document.forms[0]);
    if (val === "0") {
        alert('<fmt:message key="messenger.CreateMessage.msgNoProvider"/>');
        return false;
    }
    return true;
}
	// Checks if at least one provider recipient is selected.
	// Handles both single-element and NodeList cases for the provider checkbox collection.
	function validateCheckBoxes(form)
	{
	  var boxes = form.provider;
	  if (!boxes) return "0";
	  if (typeof boxes.length === 'undefined') {
	    return boxes.checked ? "1" : "0";
	  }
	  for (var i = 0; i < boxes.length; i++)
	    if (boxes[i].checked) return "1";
	  return "0";
	}

	var msgArchiveFailed = '<fmt:message key="messenger.CreateMessage.archiveFailed"/>';
	var msgArchiveError = '<fmt:message key="messenger.CreateMessage.archiveError"/>';
	var msgArchiveTimeout = '<fmt:message key="messenger.CreateMessage.archiveTimeout"/>';

	// Archives the current message via XHR before submitting the compose form.
	// Includes CSRF token and 10-second timeout. On failure or timeout, still submits
	// the message without archiving.
	function XMLHttpRequestSendnArch() {
		if (!validateFields()) {
			return;
		}
		var theLink = document.referrer;
		if (!theLink || theLink.indexOf('?') === -1) {
			document.forms[0].submit();
			return;
		}

		var oRequest = new XMLHttpRequest();
		var theLinkComponents = theLink.split('?');
		var theQueryComponents = theLinkComponents[1].split('&');

		var messageNo = '';
		for (var index = 0; index < theQueryComponents.length; ++index) {
			var theKeyValue = theQueryComponents[index].split('=');
			if (theKeyValue[0] == 'messageID') {
				messageNo = theKeyValue[1];
			}
		}

		if (!messageNo) {
			document.forms[0].submit();
			return;
		}

		var theArchiveLink = theLinkComponents[0].substring(0, theLinkComponents[0].lastIndexOf('/')) + '/DisplayMessages.do';

		oRequest.open('POST', theArchiveLink, true);
		oRequest.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
		oRequest.timeout = 10000;
		oRequest.onload = function() {
			if (oRequest.status >= 200 && oRequest.status < 300) {
				document.forms[0].submit();
			} else {
				alert(msgArchiveFailed);
				document.forms[0].submit();
			}
		};
		oRequest.onerror = function() {
			alert(msgArchiveError);
			document.forms[0].submit();
		};
		oRequest.ontimeout = function() {
			alert(msgArchiveTimeout);
			document.forms[0].submit();
		};
		oRequest.send('btnDelete=archive&messageNo=' + encodeURIComponent(messageNo));
	}

	// Opens demographic attachment popup for attaching documents from a patient record
	function popupAttachDemo(demographic){
	    var vheight = 700;
	    var vwidth = 900;
	    var windowprops = "height="+vheight+",width="+vwidth+",location=0,scrollbars=1,menubar=0,toolbar=1,resizable=1,screenX=0,screenY=0,top=0,left=0";
	    var page = 'attachmentFrameset.jsp?demographic_no=' +demographic;

	    if ( demographic == "" || !demographic || demographic == "null") {
	        alert("Please select a demographic.");
	    }
	    else {
	        var popUp=window.open(page, "msgAttachDemo", windowprops);
	        if (popUp != null) {
	            if (popUp.opener == null) {
	              popUp.opener = self;
	            }
	            popUp.focus();
	        }
	    }

	}

	// On page load: displays any server-side error, hides plain textarea,
	// syncs editor with initial message body content, and pre-populates
	// the selected demographic field if a patient is linked to this message.
	document.addEventListener('DOMContentLoaded', function(){
		<%
			String createMsgError = (String) request.getAttribute("createMessageError");
			if (createMsgError == null) { createMsgError = ""; }
		%>
		var submissionerror = '<%= Encode.forJavaScript(createMsgError) %>';
		if(submissionerror)
		{
			alert(submissionerror);
		}

        document.getElementsByName("message")[0].setAttribute("style", "display:none;");
        if (typeof editor !== 'undefined') {
            editor.setMarkdown("<br>" + document.getElementsByName("message")[0].value);
            editor.moveCursorToStart();
        }

        // Pre-populate the selected demographic display field if a patient is linked.
        // Done here (after DOM is ready) so the selectedDemo input exists before access.
        if ('<%=Encode.forJavaScript(demoName)%>' && '<%=Encode.forJavaScript(demoName)%>' !== 'null') {
            document.forms[0].selectedDemo.value = "<%=Encode.forJavaScript(demoName)%>";
            document.forms[0].demographic_no.value = "<%=Encode.forJavaScript(demographic_no)%>";
        }

	});
</script>
</head>
<body>
<table style="width:100%;">
    <tr>
        <td style="vertical-align:top">
            <h4>&nbsp;<i class="fa-regular fa-envelope" title="<fmt:message key="messenger.ViewMessage.msgMessenger"/>"></i>&nbsp;
                    <fmt:message key="messenger.CreateMessage.msgCreate"/>
            </h4>
        </td>
        <td>
        </td>
        <td style="text-align:right" >
            <!-- About and Help links removed to elsewhere in the EMR to ease maintenance -->
        </td>
    </tr>
</table>

<table class="MainTable" id="scrollNumber1" style="width:100%">

	<tr>
		<td class="MainTableRightColumn">
		<table style="width:95%">

			<tr>

						<td><div style="display:flex; padding-left:10px;">
						    <a class="btn btn-outline-primary" href="<%=request.getContextPath()%>/messenger/DisplayMessages.jsp">
								<fmt:message key="messenger.ViewMessage.btnInbox" />
							</a>
                            <a class="btn btn-outline-secondary" href="<%=request.getContextPath()%>/messenger/ClearMessage.do">
								<fmt:message key="messenger.CreateMessage.btnClear" />
							</a>
                            <button type="button" class="btn btn-outline-secondary" onclick="BackToCarlos()">
                                <fmt:message key="messenger.CreateMessage.btnExit" />
                            </button>
                            </div>
						</td>


			</tr>

			<tr>
				<td><!-- colspan -->
				<form action="${pageContext.request.contextPath}/messenger/CreateMessage.do" method="post" onsubmit="return validateFields()">
				<table class="well" style="width:100%">
						<tr class="subheader">
							<th><fmt:message key="messenger.CreateMessage.msgRecipients" /></th>
							<th colspan="2" style="text-align:left"><fmt:message key="messenger.CreateMessage.msgMessage" /></th>
						</tr>
						<tr>

						<td style="vertical-align:top"><br>

							<%-- Recipient selection panel: organized by member groups (collapsible)
							     and local members (expanded by default) --%>
							<div class="ChooseRecipientsBox" style="max-height: 576px; overflow-y: scroll;">
							<table>
                                <tr>
								<td style="padding: 10px 5px; min-width:fit-content;"  class="form-inline"><!--list of the providers cell Start-->
									<%if(recall){ %>
										<div>
											<input name="provider" value="<%=Encode.forHtmlAttribute(delegate)%>" type="checkbox" checked>
											<strong><a title="default recall delegate: <%=Encode.forHtmlAttribute(delegateName)%>">default: <%=Encode.forHtml(delegateName)%></a></strong>
										</div>
									<%} %>

										<!-- Display Member Groups -->
										<div id="member-groups">

											<strong><fmt:message key="messenger.CreateMessage.memberGroups" /></strong>

											<c:forEach items="${ groupManager }" var="group">
											<details>
												<summary>
													<input type="checkbox" name="tableDFR" id="member_group_${ fn:replace(fn:escapeXml(group.key.id), ' ', '_') }"
															value="${ fn:escapeXml(group.key.id) }" onclick="checkGroup(this)" >
													<label for="member_group_${ fn:replace(fn:escapeXml(group.key.id), ' ', '_') }" ><c:out value="${ group.key.groupDesc }" /></label>
												</summary>

												<c:forEach items="${ group.value }" var="member">
													<div class="group_member_contact" style="white-space: nowrap;">
														<input type="checkbox" name="provider" class="member_group_${ fn:replace(fn:escapeXml(group.key.id), ' ', '_') }"
															id="${ fn:replace(fn:escapeXml(group.key.id), ' ', '_') }-${ fn:replace(fn:escapeXml(member.id.compositeId), ' ', '_') }" value="${ fn:escapeXml(member.id.compositeId) }" >

														<label for="${ fn:replace(fn:escapeXml(group.key.id), ' ', '_') }-${ fn:replace(fn:escapeXml(member.id.compositeId), ' ', '_') }" >
															<c:out value="${ member.lastName }" />, <c:out value="${ member.firstName }" />
														</label>
													</div>
												</c:forEach>

											</details>
											</c:forEach>

										</div>

										<hr style="border-top:1px solid #dcdcdc; border-bottom:none;">

										<details open="true">
											<summary>
												<strong><fmt:message key="messenger.CreateMessage.localMembers" /></strong>
											</summary>

											<!-- Display all local members -->
											<c:forEach items="${ localMembers }" var="member">

												<%-- Nested forEach checks replyList to pre-select recipients for replies --%>
												<c:set var="providerChecked" value="false" />
												<c:forEach var="replyId" items="${ replyList }">
													<c:if test="${ replyId.compositeId eq member.id.compositeId }">
														<c:set var="providerChecked" value="true" />
													</c:if>
												</c:forEach>

												<div class="member_contact" style="white-space: nowrap;">
													<input type="checkbox" name="provider" id="0-${ fn:replace(fn:escapeXml(member.id.compositeId), ' ', '_') }"
														value="${ fn:escapeXml(member.id.compositeId) }"  ${ providerChecked ? 'checked' : '' }/>
													<label for="0-${ fn:replace(fn:escapeXml(member.id.compositeId), ' ', '_') }" >
														<c:out value="${ member.lastName }" />, <c:out value="${ member.firstName }" />
													</label>
												</div>

											</c:forEach>
										</details>
									</td><!--list of the providers cell end-->
								</tr>
							</table>
						</div> <!-- end ChooseRecipientsBox -->
					</td>
					<%-- Message composition area with subject field, Toast UI WYSIWYG editor,
					     send buttons, and attachment display --%>
					<td style="vertical-align:top;" colspan="2">
                    <div class="row"><div class="col-auto">
					<label for="subject" class="form-label"><fmt:message key="messenger.CreateMessage.formSubject" /> :</label>
                    </div><div class="col">
					<input type="text" name="subject" id="subject" class="form-control w-75" value="<c:out value="${messageSubject}"/>"> </div>
                    <div id="messagediv"></div></div>
					<textarea name="message" rows="15" style="min-width: 100%"><c:out value="${messageBody}"/></textarea>
							<table>
								<tr>
									<td><button type="submit" class="btn btn-primary" onclick="writeToMessage();"
										title="<fmt:message key="messenger.CreateMessage.btnSendMessage"/>"><i class="fa-solid fa-share-from-square"></i>&nbsp;<fmt:message key="messenger.CreateMessage.btnSendMessage"/></button>
									</td>
									<td><button type="button" class="btn btn-secondary" id="sendArchive" onclick="writeToMessage(); XMLHttpRequestSendnArch();"
										title="<fmt:message key="messenger.CreateMessage.btnSendnArchiveMessage"/>"><i class="fa-solid fa-envelope-circle-check"></i>&nbsp;<fmt:message key="messenger.CreateMessage.btnSendnArchiveMessage"/></button>
									</td>
								</tr>
							</table>
					<%
                       String att = bean.getAttachment();
                       String pdfAtt = bean.getPDFAttachment();
                       if (att != null || pdfAtt != null){
                    %>
							<br>
							<fmt:message key="messenger.CreateMessage.msgAttachments" />
							<%
							bean.setSubject(null);
							bean.setMessage(null);
						}%>
					</td>
				</tr>

				<%-- Patient demographic linking section: search, select, and attach
			     a patient record to this message --%>
			<tr>
					<td class="subheader"></td>
					<td class="subheader" colspan="2" style="font-weight: bold"><fmt:message key="messenger.CreateMessage.msgLinkThisMessage" /></td>
				</tr>

				<tr>
					<td><br><br>&nbsp;</td>
					<td style="width: 40%;">
                      <input type="text" name="keyword" class="form-control"> <input type="hidden" name="demographic_no" value="<%=Encode.forHtmlAttribute(demographic_no)%>" >
                    </td>
	                <td>
                      <input type="button" class="btn btn-outline-secondary" name="searchDemo" value="<fmt:message key="messenger.CreateMessage.msgSearchDemographic" />" onclick="popupSearchDemo(document.forms[0].keyword.value)" >
                  	</td>
				</tr>
				<tr>
					<td></td>
					<td colspan="2" style="font-weight: bold"><fmt:message key="messenger.CreateMessage.msgSelectedDemographic" /></td>
				</tr>
				<tr>
					<td>
                    </td>
					<td>

								<input type="text" name="selectedDemo" readonly
								style="border: none" value="none">


	                </td>
	                <td>
					<input type="button" class="btn btn-outline-secondary" name="attachDemo"
						value="<fmt:message key="messenger.CreateMessage.msgAttachDemographic" />"
						onclick="popupAttachDemo(document.forms[0].demographic_no.value)"
						>
                    <input type="button"
						class="btn btn-outline-secondary" name="clearDemographic"
						value="<fmt:message key="messenger.CreateMessage.msgClearSelectedDemographic" />"
						onclick='document.forms[0].demographic_no.value = ""; document.forms[0].selectedDemo.value = "none"'>

					</td>

				</tr>

		</table>
		</form>
			</td>
		</tr>
	</table>
	</td>
	</tr>

</table>
<script>
                 document.forms[0].message.focus();
</script>
<script>

    // Initialize Toast UI Editor in WYSIWYG mode with custom HTML sanitizer for XSS prevention.
    // Falls back to plain textarea if the editor library fails to load.
    // Note: global.language.code != global.i18nLanguagecode
    var editor;
    try {
        var Editor = toastui.Editor;
        editor = new Editor({
            el: document.getElementById('messagediv'),
            initialEditType: 'wysiwyg',
            usageStatistics: false,
            height: '500px',
            language: '<fmt:message key="global.language.code" />',
            customHTMLSanitizer: function(html) {
                return DOMPurify.sanitize(html);
            }
        });
    } catch (e) {
        console.error('Toast UI Editor failed to initialize:', e);
        document.getElementsByName("message")[0].style.display = '';
        var warning = document.createElement('div');
        warning.className = 'alert alert-warning mt-2';
        warning.textContent = 'Rich text editor could not load. Using plain text mode.';
        document.getElementById('messagediv').parentNode.insertBefore(warning, document.getElementById('messagediv'));
    }

    function writeToMessage() {
        if (typeof editor !== 'undefined') {
            document.getElementsByName("message")[0].value = editor.getMarkdown();
        }
    }

</script>
</body>
</html>
