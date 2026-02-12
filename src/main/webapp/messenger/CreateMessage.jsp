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
    CreateMessage.jsp - Main message composition interface for the OpenO EMR messaging system

    Purpose:
    This JSP page provides the primary interface for healthcare providers to compose and send
    internal messages within the EMR system. It supports creating new messages, replying to
    existing messages, and forwarding messages with attachments.

    Key Features:
    - Message composition with subject and body text
    - Recipient selection from local and remote provider lists
    - Group-based recipient management
    - Patient demographic association for clinical messages
    - Attachment support including PDF documents
    - Delegate/proxy messaging capabilities
    - Reply and forward functionality with quoted text

    Security:
    - Requires write permissions on "_msg" object
    - Session validation through msgSessionBean
    - OWASP encoding for XSS prevention

    Dependencies:
    - MsgSessionBean: Maintains message session state
    - MessengerGroupManager: Handles provider groups and membership
    - MessagingManager: Core messaging operations
    - DemographicData: Patient information retrieval

    Request Parameters:
    - subject: Initial message subject
    - demographic_no: Associated patient ID
    - delegate: Delegate/proxy provider ID
    - ReSubject: Reply subject (from reply action)
    - ReText: Quoted text for replies

    Session Attributes:
    - msgSessionBean: Message composition session data
    - userrole: Current user role
    - user: Current username

    @since 2002
--%>

<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


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
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
    // Security check: Build role string from session attributes for authorization
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
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
    Map<String, List<MsgProviderData>> remoteMembers = groupManager.getAllRemoteMembers(LoggedInInfo.getLoggedInInfoFromSession(request));
    List<MsgProviderData> localMembers = groupManager.getAllLocalMembers(LoggedInInfo.getLoggedInInfoFromSession(request));
    MessagingManager messagingManager = SpringUtils.getBean(MessagingManager.class);

    // Store provider and group data in request scope for JSP access
    request.setAttribute("groupManager", groups);
    request.setAttribute("remoteMembers", remoteMembers);
    request.setAttribute("localMembers", localMembers);

    // Set up message subject and body (from new message, reply, or forward)
    pageContext.setAttribute("messageSubject", request.getParameter("subject"));
    // Note: Second setAttribute overwrites the first if ReSubject exists
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
        if (delegate != null || delegate != "") {
            delegateName = messagingManager.getDelegateName(delegate);
        }
        pageContext.setAttribute("delegateName", delegateName);
        pageContext.setAttribute("messageSubject", subjectText);
    }

%>
<!DOCTYPE html>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.title"/></title>
        <style>

            summary {
                cursor: pointer;
            }

            .muted {
                color: silver;
            }

            .group_member_contact, .remote_member_contact {
                margin-left: 15px;
            }

            summary label {
                font-weight: bold;
            }
        </style>

        <script src="<%=request.getContextPath()%>/js/jquery-1.12.3.js"></script>
        <!--<script src="<%=request.getContextPath()%>/js/jquery-ui-1.8.18.custom.min.js"></script>-->


<link href="<%=request.getContextPath() %>/library/bootstrap/5.0.2/css/bootstrap.css" rel="stylesheet" type="text/css">


<link rel="stylesheet" href="<%=request.getContextPath() %>/library/toastui/toastui-editor.min.css">
<script src="<%=request.getContextPath() %>/library/toastui/toastui-editor-all.min.js"></script>
<script src="<%=request.getContextPath() %>/library/toastui/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.js"></script>

<link rel="stylesheet" href="<%=request.getContextPath() %>/css/font-awesome.min.css">

<style>
    .toastui-editor-contents{
        font-size: 17px;
    }
 </style>

<script>

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


/*            function validatefields() {

                // cannot send attachments to remote facilities
                $("input:checked").each(function () {
                    if (this.id.split("-")[2] > 0 && $("#attachmentAlert").val()) {
                        alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.attachmentsNotPermitted"/>");
                        return false;
                    }
                })

                if (document.forms[0].message.value.length == 0) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgEmptyMessage"/>");
                    return false;
                }
                val = validateCheckBoxes(document.forms[0]);
                if (val == 0) {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgNoProvider"/>");
                    return false;
                }
                return true
            }*/

function validateFields() {
    // Cannot send attachments to remote facilities
    var checkedInputs = document.querySelectorAll('input:checked');
    var attachmentAlert = document.getElementById('attachmentAlert');
    for (var i = 0; i < checkedInputs.length; i++) {
        var idParts = checkedInputs[i].id.split('-');
        // Check if id structure exists and index 2 > 0
        if (idParts.length > 2 && parseInt(idParts) > 0 && attachmentAlert && attachmentAlert.value) {
            alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.attachmentsNotPermitted"/>');
            return false;
        }
    }
    // Check if message body is empty
    if (document.forms[0].message.value.length === 0) {
        alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgEmptyMessage"/>');
        return false;
    }
    // Validate check boxes
    var val = validateCheckBoxes(document.forms[0]);
    if (val === 0) {
        alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgNoProvider"/>');
        return false;
    }
    return true;
}
	function validateCheckBoxes(form)
	{
	  var retval = "0";
	  for (var i =0; i < form.provider.length;i++)
	    if  (form.provider[i].checked)
	      retval = "1";
	  return retval
	}

	function BackToOscar()
	{
	    if (opener.callRefreshTabAlerts) {
		opener.callRefreshTabAlerts("oscar_new_msg");
	        setTimeout("window.close()", 100);
	    } else {
	        window.close();
	    }
	}

	function XMLHttpRequestSendnArch() {
		var oRequest = new XMLHttpRequest();
		var theLink=document.referrer;
		var theLinkComponents=theLink.split('?');
		var theQueryComponents=theLinkComponents[1].split('&');

		for (index = 0; index < theQueryComponents.length; ++index) {
	    		var theKeyValue=theQueryComponents[index].split('=');
			if(theKeyValue[0]=='messageID'){
				var theArchiveLink=theLinkComponents[0].substring(0,theLinkComponents[0].lastIndexOf('/'))+'/DisplayMessages.do?btnDelete=archive&messageNo='+theKeyValue[1];
			}
		}
		oRequest.open('GET', theArchiveLink, false);
		oRequest.send();
		document.forms[0].submit();
	}

	function popupSearchDemo(keyword){ // open a new popup window
	    var vheight = 700;
	    var vwidth = 980;
	    windowprops = "height="+vheight+",width="+vwidth+",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
	    var page = 'msgSearchDemo.jsp?keyword=' +keyword +'&firstSearch='+true;
	    var popUp=window.open(page, "msgSearchDemo", windowprops);
	    if (popUp != null) {
	        if (popUp.opener == null) {
	          popUp.opener = self;
	        }
	        popUp.focus();
	    }
	}

	function popupAttachDemo(demographic){ // open a new popup window
	    var subject = document.forms[0].subject.value;
	    var message = document.forms[0].message.value;
	    var formData = "subject=" + subject + "&message=" + message;

	    $.ajax({
	    	type: "post",
	    	data : formData,
	    	success: function(data){
	    		console.log(data);
	    	},
	    	error: function (jqXHR, textStatus, errorThrown){
	 			console.log("Error: " + textStatus);
	    	}
		});

	    var vheight = 700;
	    var vwidth = 900;
	    windowprops = "height="+vheight+",width="+vwidth+",location=0,scrollbars=1,menubar=0,toolbar=1,resizable=1,screenX=0,screenY=0,top=0,left=0";
	    var page = 'attachmentFrameset.jsp?demographic_no=' +demographic;
	    var demo_no  = demographic;


	    if ( demographic == "" || !demographic || demographic == null || demographic == "null") {
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

	/*
	 * Throw an error returned from the action
	 */
	$(document).ready(function(){
		var submissionerror = '${createMessageError}';
		if(submissionerror)
		{
			alert(submissionerror);
		}

        document.getElementsByName("message")[0].setAttribute("style", "display:none;");
        editor.setMarkdown("<br>" + document.getElementsByName("message")[0].value);
        editor.moveCursorToStart();

        //disableArchive();
	})
</script>
</head>
<body class="BodyStyle" >
<table style="width:100%;">
    <tr>
        <td style="vertical-align:top">
            <h4>&nbsp;<i class="icon-envelope" title='<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.msgMessenger"/>'></i>&nbsp;
<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgCreate"/>
            </h4>
        </td>
        <td>
        </td>
        <td style="text-align:right" >
            <i class=" icon-question-sign"></i>
            <a href="javascript:void(0)" onClick ="popupPage(700,960,''+'Messenger create')"><fmt:setBundle basename="oscarResources"/><fmt:message key="app.top1"/></a>
            <i class=" icon-info-sign" style="margin-left:10px;"></i>
            <a href="javascript:void(0)" onclick="javascript:popupPage(600,700,'<%= request.getContextPath() %>/oscarEncounter/About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a>
        </td>
    </tr>
</table>

<table class="MainTable" id="scrollNumber1" style="width:100%">

	<tr>
		<td class="MainTableRightColumn">
		<table style="width:100%">

			<tr>

						<td>
						    <a class="btn btn-outline-secondary" href="<%=request.getContextPath()%>/messenger/DisplayMessages.jsp">
								<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnInbox" />
							</a>
                            <a class="btn btn-outline-secondary" href="<%=request.getContextPath()%>/messenger/ClearMessage.do">
								<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.btnClear" />
							</a>
                            <a href="javascript:BackToOscar()">
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.btnExit" />
                            </a>
                            <br>&nbsp;
						</td>


			</tr>

			<tr>
				<td><!-- colspan -->
				<form action="${pageContext.request.contextPath}/messenger/CreateMessage.do" method="post" onsubmit="return validatefields()">
				<table class="well" style="width:100%">
						<tr class="subheader">
							<th><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgRecipients" /></th>
							<th colspan="2" style="text-align:left"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgMessage" /></th>
						</tr>
						<tr>

						<td style="vertical-align:top"><br>

							<div class="ChooseRecipientsBox" style="max-height: 576px; overflow-y: scroll;">
							<table>
                                <tr>
								<td style="padding: 10px 5px; min-width:fit-content;"  class="form-inline"><!--list of the providers cell Start-->
									<%if(recall){ %>
										<div>
											<input name="provider" value="<%=delegate%>" type="checkbox" checked>
											<strong><a title="default recall delegate: <%=delegateName%>">default: <%=delegateName%></a></strong>
										</div>
									<%} %>

										<!-- Display Member Groups -->
										<div id="member-groups">

											<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.memberGroups" /></strong>

											<c:forEach items="${ groupManager }" var="group">
											<details>
												<summary>
													<input type="checkbox" name="tableDFR" id="member_group_${ group.key.id }"
															value="${ group.key.id }" onclick="checkGroup(this)" >
													<label for="member_group_${ group.key.id }" >${ group.key.groupDesc }</label>
												</summary>

												<c:forEach items="${ group.value }" var="member">
													<div class="group_member_contact" style="white-space: nowrap;">
														<input type="checkbox" name="provider" class="member_group_${ group.key.id }"
															id="${ group.key.id }-${ member.id.compositeId }" value="${ member.id.compositeId }" >

														<label for="${ group.key.id }-${ member.id.compositeId }" >
															<c:out value="${ member.lastName }" />, <c:out value="${ member.firstName }" />
														</label>
													</div>
												</c:forEach>

											</details>
											</c:forEach>

										</div>

										<!-- Display Members by remote locations -->
										<c:if test="${ not empty remoteMembers }" >

										<hr style="border-top:1px solid #dcdcdc; border-bottom:none;">

										<div id="remote-locations">
										<details>
											<summary>
												<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.remoteMembers" /></strong>
											</summary>
											<c:forEach items="${ remoteMembers }" var="location" >
												<details>
													<summary>
														<input type="checkbox" name="tableDFR" id="remote_group_${ location.key }"
																value="${ location.key }" onchange="checkGroup(this)" />
														<label for="remote_group_${ location.key }" >${ location.key }</label>
													</summary>

													<c:forEach items="${ location.value }" var="member">

														<%-- this is horrible. try not to repeat it --%>
														<c:set var="providerChecked" value="false" />
														<c:forEach var="replyId" items="${ replyList }">
															<c:if test="${ replyId.compositeId eq member.id.compositeId }">
																<c:set var="providerChecked" value="true" />
															</c:if>
														</c:forEach>

														<div class="remote_member_contact">
															<input type="checkbox" name="provider" class="remote_group_${ location.key }"
																id="${ member.id.compositeId }" value="${ member.id.compositeId }"  ${ providerChecked ? 'checked' : '' }/>
															<label for="${ member.id.compositeId }" >
																<c:out value="${ member.lastName }" />, <c:out value="${ member.firstName }" />
															</label>
														</div>
													</c:forEach>

												</details>
											</c:forEach>
										</details>
										</div>
										</c:if>

										<hr style="border-top:1px solid #dcdcdc; border-bottom:none;">

										<details open="true">
											<summary>
												<strong><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.localMembers" /></strong>
											</summary>

											<!-- Display all local members -->
											<c:forEach items="${ localMembers }" var="member">

												<%-- this is horrible. try not to repeat it --%>
												<c:set var="providerChecked" value="false" />
												<c:forEach var="replyId" items="${ replyList }">
													<c:if test="${ replyId.compositeId eq member.id.compositeId }">
														<c:set var="providerChecked" value="true" />
													</c:if>
												</c:forEach>

												<div class="member_contact" style="white-space: nowrap;">
													<input type="checkbox" name="provider" id="0-${ member.id.compositeId }"
														value="${ member.id.compositeId }"  ${ providerChecked ? 'checked' : '' }/>
													<label for="0-${ member.id.compositeId }" >
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
					<td style="vertical-align:top;" colspan="2"><!--Message and Subject Cell-->
                    <div class="row"><div class="col-auto">
					<label for="subject" class="form-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.formSubject" /> :</label>
                    </div><div class="col">
					<input type="text" name="subject" id="subject" class="form-control w-75" value="${messageSubject}"> </div>
                    <div id="messagediv"></div></div>
					<textarea name="message" rows="15" style="min-width: 100%">${messageBody}</textarea>
							<table>
								<tr>
									<td><input type="submit" class="btn btn-primary" onclick="writeToMessage();"
										value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.btnSendMessage"/>">
									</td>
									<td><input type="button" class="btn btn-secondary" id="sendArchive" onclick="writeToMessage(); XMLHttpRequestSendnArch();"
										value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.btnSendnArchiveMessage"/>" >
									</td>
								</tr>
							</table>
					<%
                       String att = bean.getAttachment();
                       String pdfAtt = bean.getPDFAttachment();
                       if (att != null || pdfAtt != null){
                    %>
							<br>
							<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgAttachments" />
							<input type="hidden" id="attachmentAlert" name="attachmentAlert" value="true" />
							<%
							bean.setSubject(null);
							bean.setMessage(null);
						}%>
					</td>
				</tr>

				<tr>
					<td class="subheader"></td>
					<td class="subheader" colspan="2" style="font-weight: bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgLinkThisMessage" /></td>
				</tr>

				<tr>
					<td><br><br>&nbsp;</td>
					<td>
                      <input type="text" name="keyword" class="form-control"> <input type="hidden" name="demographic_no" value="<%=demographic_no%>" >
                    </td>
	                <td>
                      <input type="button" class="btn btn-light" name="searchDemo" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgSearchDemographic" />" onclick="popupSearchDemo(document.forms[0].keyword.value)" >
                  	</td>
				</tr>
				<tr>
					<td></td>
					<td colspan="2" style="font-weight: bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgSelectedDemographic" /></td>
				</tr>
				<tr>
					<td></td>

					<td>

						<c:choose>
							<c:when test="${ not empty unlinkedIntegratorDemographicName }">
								<input type="text" name="selectedDemo" value="${ unlinkedIntegratorDemographicName }"
									class="form-control" style="border: none;" readonly />
							</c:when>
							<c:otherwise>
								<input type="text" name="selectedDemo" class="form-control" readonly style="border: none" value="none" />
								<script>
			                          if ('<%=Encode.forHtmlUnquotedAttribute(demoName)%>' && '<%=Encode.forHtmlUnquotedAttribute(demoName)%>' !== 'null') {
                                        document.forms[0].selectedDemo.value = "<%=Encode.forJavaScript(demoName)%>";
                                        document.forms[0].demographic_no.value = "<%=Encode.forJavaScript(demographic_no)%>";
                                       }
			                     </script>
							</c:otherwise>
						</c:choose>

	                </td>
	                <td>
                    <input type="button"
						class="btn btn-light" name="clearDemographic"
						value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgClearSelectedDemographic" />"
						onclick='document.forms[0].demographic_no.value = ""; document.forms[0].selectedDemo.value = "none"' />
					<input type="button" class="btn btn-light" name="attachDemo"
						value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgAttachDemographic" />"
						onclick="popupAttachDemo(document.forms[0].demographic_no.value)"
						>
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

    // note that global.language.code != global.i18nLanguagecode
    const Editor = toastui.Editor;
    const editor = new Editor({
      el: document.getElementById('messagediv'),
        initialEditType:'wysiwyg',
        usageStatistics: false,
        height: '500px',
        language:'<fmt:setBundle basename="oscarResources"/><fmt:message key="global.language.code" />'
	    })

    function writeToMessage() {
        document.getElementsByName("message")[0].value = editor.getMarkdown();
    }

</script>
</body>
</html>
