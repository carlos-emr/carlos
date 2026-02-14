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
    CreateMessage.jsp - Message composition interface for the CARLOS EMR messaging system

    Key Features:
    - Message composition with subject and body text
    - Recipient selection from local and remote provider lists
    - Group-based recipient management
    - Patient demographic association for clinical messages
    - Attachment support including PDF documents
    - Delegate/proxy messaging capabilities
    - Reply and forward functionality with quoted text

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
<%@ page import="java.util.Map, java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.pageUtil.MsgSessionBean" %>
<%@ page import="io.github.carlos_emr.carlos.managers.MessengerGroupManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
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
    MessengerGroupManager groupManager = SpringUtils.getBean(MessengerGroupManager.class);
    Map<Groups, List<MsgProviderData>> groups = groupManager.getAllGroupsWithMembers(LoggedInInfo.getLoggedInInfoFromSession(request));
    Map<String, List<MsgProviderData>> remoteMembers = groupManager.getAllRemoteMembers(LoggedInInfo.getLoggedInInfoFromSession(request));
    List<MsgProviderData> localMembers = groupManager.getAllLocalMembers(LoggedInInfo.getLoggedInInfoFromSession(request));
    MessagingManager messagingManager = SpringUtils.getBean(MessagingManager.class);

    request.setAttribute("groupManager", groups);
    request.setAttribute("remoteMembers", remoteMembers);
    request.setAttribute("localMembers", localMembers);

    pageContext.setAttribute("messageSubject", request.getParameter("subject"));
    pageContext.setAttribute("messageSubject", request.getAttribute("ReSubject"));
    pageContext.setAttribute("messageBody", request.getAttribute("ReText"));

    MsgSessionBean bean = (MsgSessionBean) session.getAttribute("msgSessionBean");

    String demographic_no = (String) request.getAttribute("demographic_no");
    DemographicData demoData = new DemographicData();
    Demographic demo = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demographic_no);
    String demoName = "";
    if (demo != null) {
        demoName = demo.getLastName() + ", " + demo.getFirstName();
    }

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

    String createMessageError = (String) request.getAttribute("createMessageError");
    if (createMessageError == null) createMessageError = "";
%>
<!DOCTYPE html>
<html>
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.title"/></title>

    <link href="<%=request.getContextPath()%>/css/bootstrap.css" rel="stylesheet">
    <link href="<%=request.getContextPath()%>/css/bootstrap-responsive.css" rel="stylesheet">
    <link href="<%=request.getContextPath()%>/css/fontawesome-all.min.css" rel="stylesheet">

    <script src="<%=request.getContextPath()%>/js/global.js"></script>
    <script src="<%=request.getContextPath()%>/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="<%=request.getContextPath()%>/js/jquery-ui-1.8.18.custom.min.js"></script>

    <style type="text/css">
        summary { cursor: pointer; }
        .muted { color: silver; }
        .group_member_contact, .remote_member_contact { margin-left: 15px; }
        summary label { font-weight: bold; }
        .member_contact, .group_member_contact { white-space: nowrap; }
        .subheader { background-color: #e8e8e8; }
    </style>

    <script type="text/javascript">

        function disableArchive() {
            var theLink = document.referrer;
            if (theLink.indexOf('messageID') == -1) {
                $('#sendArchive').hide();
            }
        }

        function checkGroup(group) {
            $.each($("input." + group.id), function () {
                $(this).prop("checked", $(group).prop("checked") ? "checked" : false);
            })
        }

        function validatefields() {
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
        }

        function validateCheckBoxes(form) {
            var retval = "0";
            for (var i = 0; i < form.provider.length; i++)
                if (form.provider[i].checked)
                    retval = "1";
            return retval
        }

        function BackToOscar() {
            if (opener && opener.callRefreshTabAlerts) {
                opener.callRefreshTabAlerts("oscar_new_msg");
                setTimeout(function() { window.close(); }, 100);
            } else {
                window.close();
            }
        }

        function XMLHttpRequestSendnArch() {
            var oRequest = new XMLHttpRequest();
            var theLink = document.referrer;
            var theLinkComponents = theLink.split('?');
            var theQueryComponents = theLinkComponents[1].split('&');

            for (var index = 0; index < theQueryComponents.length; ++index) {
                var theKeyValue = theQueryComponents[index].split('=');
                if (theKeyValue[0] == 'messageID') {
                    var theArchiveLink = theLinkComponents[0].substring(0, theLinkComponents[0].lastIndexOf('/')) + '/DisplayMessages.do?btnDelete=archive&messageNo=' + theKeyValue[1];
                }
            }

            oRequest.open('GET', theArchiveLink, false);
            oRequest.send();
            document.forms[0].submit();
        }

        function popupSearchDemo(keyword) {
            var vheight = 700;
            var vwidth = 980;
            var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var page = 'msgSearchDemo.jsp?keyword=' + keyword + '&firstSearch=' + true;
            var popUp = window.open(page, "msgSearchDemo", windowprops);
            if (popUp != null) {
                if (popUp.opener == null) {
                    popUp.opener = self;
                }
                popUp.focus();
            }
        }

        function popupAttachDemo(demographic) {
            var subject = document.forms[0].subject.value;
            var message = document.forms[0].message.value;
            var formData = "subject=" + subject + "&message=" + message;

            $.ajax({
                type: "post",
                data: formData,
                success: function (data) {
                    console.log(data);
                },
                error: function (jqXHR, textStatus, errorThrown) {
                    console.log("Error: " + textStatus);
                }
            });

            var vheight = 700;
            var vwidth = 900;
            var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=0,scrollbars=1,menubar=0,toolbar=1,resizable=1,screenX=0,screenY=0,top=0,left=0";
            var page = 'attachmentFrameset.jsp?demographic_no=' + demographic;

            if (demographic == "" || !demographic || demographic == null || demographic == "null") {
                alert("Please select a demographic.");
            } else {
                var popUp = window.open(page, "msgAttachDemo", windowprops);
                if (popUp != null) {
                    if (popUp.opener == null) {
                        popUp.opener = self;
                    }
                    popUp.focus();
                }
            }
        }

        $(document).ready(function () {
            var submissionerror = '<%=Encode.forJavaScript(createMessageError)%>';
            if (submissionerror) {
                alert(submissionerror);
            }
            disableArchive();
        })
    </script>
</head>
<body>

<table style="width:100%">
    <tr>
        <td style="width:1%"></td>
        <td style="width:80%; text-align:left;">
            <h4>
                <i class="fa-solid fa-envelope"></i>&nbsp;
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgCreate"/>
            </h4>
        </td>
        <td style="width:1%"></td>
    </tr>
</table>

<div class="well">

    <ul class="nav nav-tabs">
        <li class="active">
            <a href="<%=request.getContextPath()%>/messenger/CreateMessage.jsp">
                <i class="fa-solid fa-pen-to-square"></i>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.DisplayMessages.btnCompose"/>
            </a>
        </li>
        <li>
            <a href="<%=request.getContextPath()%>/messenger/DisplayMessages.jsp">
                <i class="fa-solid fa-inbox"></i>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.ViewMessage.btnInbox"/>
            </a>
        </li>
        <li>
            <a href="<%=request.getContextPath()%>/messenger/ClearMessage.do">
                <i class="fa-solid fa-eraser"></i>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.btnClear"/>
            </a>
        </li>
    </ul>

    <form action="<%=request.getContextPath()%>/messenger/CreateMessage.do" method="post" onsubmit="return validatefields()">
        <table style="width:100%;">
            <tr class="subheader">
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgRecipients"/></th>
                <th colspan="2" style="text-align:left;"><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgMessage"/></th>
            </tr>
            <tr>
                <td valign="top">
                    <div class="ChooseRecipientsBox" style="max-height:420px; overflow-y:scroll;">
                        <table>
                            <tr>
                                <td style="padding: 10px 5px; min-width:fit-content;">
                                    <%if (recall) { %>
                                    <div>
                                        <input name="provider" value="<%=Encode.forHtmlAttribute(delegate)%>"
                                               type="checkbox" checked>
                                        <strong><a title="default recall delegate: <%=Encode.forHtmlAttribute(delegateName)%>">default: <%=Encode.forHtml(delegateName)%></a></strong>
                                    </div>
                                    <%} %>

                                    <div id="member-groups">
                                        <strong>Member Groups</strong>

                                        <c:forEach items="${groupManager}" var="group">
                                            <details>
                                                <summary>
                                                    <input type="checkbox" name="tableDFR"
                                                           id="member_group_${group.key.id}"
                                                           value="${group.key.id}"
                                                           onclick="checkGroup(this)"/>
                                                    <label for="member_group_${group.key.id}"><c:out value="${group.key.groupDesc}"/></label>
                                                </summary>

                                                <c:forEach items="${group.value}" var="member">
                                                    <div class="group_member_contact">
                                                        <input type="checkbox" name="provider"
                                                               class="member_group_${group.key.id}"
                                                               id="${group.key.id}-${member.id.compositeId}"
                                                               value="${member.id.compositeId}"/>
                                                        <label for="${group.key.id}-${member.id.compositeId}">
                                                            <c:out value="${member.lastName}"/>,
                                                            <c:out value="${member.firstName}"/>
                                                        </label>
                                                    </div>
                                                </c:forEach>
                                            </details>
                                        </c:forEach>
                                    </div>

                                    <c:if test="${not empty remoteMembers}">
                                        <hr style="border-top:1px solid #dcdcdc; border-bottom:none;"/>
                                        <div id="remote-locations">
                                            <details>
                                                <summary>
                                                    <strong>All Integrated Clinics</strong>
                                                </summary>
                                                <c:forEach items="${remoteMembers}" var="location">
                                                    <details>
                                                        <summary>
                                                            <input type="checkbox" name="tableDFR"
                                                                   id="remote_group_${location.key}"
                                                                   value="${location.key}"
                                                                   onchange="checkGroup(this)"/>
                                                            <label for="remote_group_${location.key}"><c:out value="${location.key}"/></label>
                                                        </summary>
                                                        <c:forEach items="${location.value}" var="member">
                                                            <c:set var="providerChecked" value="false"/>
                                                            <c:forEach var="replyId" items="${replyList}">
                                                                <c:if test="${replyId.compositeId eq member.id.compositeId}">
                                                                    <c:set var="providerChecked" value="true"/>
                                                                </c:if>
                                                            </c:forEach>
                                                            <div class="remote_member_contact">
                                                                <input type="checkbox" name="provider"
                                                                       class="remote_group_${location.key}"
                                                                       id="${member.id.compositeId}"
                                                                       value="${member.id.compositeId}" ${providerChecked ? 'checked' : ''}/>
                                                                <label for="${member.id.compositeId}">
                                                                    <c:out value="${member.lastName}"/>,
                                                                    <c:out value="${member.firstName}"/>
                                                                </label>
                                                            </div>
                                                        </c:forEach>
                                                    </details>
                                                </c:forEach>
                                            </details>
                                        </div>
                                    </c:if>

                                    <hr style="border-top:1px solid #dcdcdc; border-bottom:none;"/>

                                    <details open="true">
                                        <summary>
                                            <strong>All Local Members</strong>
                                        </summary>
                                        <c:forEach items="${localMembers}" var="member">
                                            <c:set var="providerChecked" value="false"/>
                                            <c:forEach var="replyId" items="${replyList}">
                                                <c:if test="${replyId.compositeId eq member.id.compositeId}">
                                                    <c:set var="providerChecked" value="true"/>
                                                </c:if>
                                            </c:forEach>
                                            <div class="member_contact">
                                                <input type="checkbox" name="provider"
                                                       id="0-${member.id.compositeId}"
                                                       value="${member.id.compositeId}" ${providerChecked ? 'checked' : ''}/>
                                                <label for="0-${member.id.compositeId}">
                                                    <c:out value="${member.lastName}"/>,
                                                    <c:out value="${member.firstName}"/>
                                                </label>
                                            </div>
                                        </c:forEach>
                                    </details>
                                </td>
                            </tr>
                        </table>
                    </div>
                </td>
                <td valign="top" colspan="2">
                    <br>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.formSubject"/> :
                    <input type="text" name="subject" id="subject" class="input-xxlarge" value="<c:out value='${messageSubject}'/>"/>
                    <br><br>
                    <textarea name="message" rows="15" style="min-width:100%;"><c:out value="${messageBody}"/></textarea>
                    <div style="margin-top:10px;">
                        <input type="submit" class="btn btn-sm btn-primary"
                               style="background-image:none; background-color:#428bca; border-color:#357ebd;"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.btnSendMessage"/>">
                        <input type="button" class="btn btn-primary" id="sendArchive"
                               onclick="XMLHttpRequestSendnArch();"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.btnSendnArchiveMessage"/>">
                    </div>
                    <%
                        String att = bean.getAttachment();
                        String pdfAtt = bean.getPDFAttachment();
                        if (att != null || pdfAtt != null) {
                    %>
                    <br>
                    <i class="fa-solid fa-paperclip"></i>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgAttachments"/>
                    <input type="hidden" id="attachmentAlert" name="attachmentAlert" value="true"/>
                    <%
                            bean.setSubject(null);
                            bean.setMessage(null);
                        }
                    %>
                </td>
            </tr>

            <tr>
                <td class="subheader"></td>
                <td class="subheader" colspan="2"><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgLinkThisMessage"/></strong></td>
            </tr>

            <tr>
                <td><br><br>&nbsp;</td>
                <td>
                    <input type="text" name="keyword" class="input-medium" style="height:30px;"/>
                    <input type="hidden" name="demographic_no" value="<%=demographic_no%>"/>
                </td>
                <td>
                    <input type="button" class="btn" name="searchDemo"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgSearchDemographic"/>"
                           onclick="popupSearchDemo(document.forms[0].keyword.value)"/>
                </td>
            </tr>
            <tr>
                <td></td>
                <td colspan="2"><strong><fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgSelectedDemographic"/></strong></td>
            </tr>
            <tr>
                <td></td>
                <td>
                    <c:choose>
                        <c:when test="${not empty unlinkedIntegratorDemographicName}">
                            <input type="text" name="selectedDemo"
                                   value="<c:out value='${unlinkedIntegratorDemographicName}'/>"
                                   class="input-medium" style="border: none;" readonly/>
                        </c:when>
                        <c:otherwise>
                            <input type="text" id="selectedDemo" name="selectedDemo"
                                   class="input-medium" readonly style="border: none" value="none"/>
                            <script type="text/javascript">
                                if ('<%=Encode.forHtmlUnquotedAttribute(demoName)%>' && '<%=Encode.forHtmlUnquotedAttribute(demoName)%>' !== 'null') {
                                    document.forms[0].selectedDemo.value = "<%=Encode.forJavaScript(demoName)%>";
                                    document.forms[0].demographic_no.value = "<%=Encode.forJavaScript(demographic_no)%>";
                                }
                            </script>
                        </c:otherwise>
                    </c:choose>
                </td>
                <td>
                    <input type="button" class="btn" name="clearDemographic"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgClearSelectedDemographic"/>"
                           onclick='document.forms[0].demographic_no.value = ""; document.forms[0].selectedDemo.value = "none"'/>
                    <input type="button" class="btn" name="attachDemo"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.msgAttachDemographic"/>"
                           onclick="popupAttachDemo(document.forms[0].demographic_no.value)"/>
                </td>
            </tr>
        </table>
    </form>

</div>

<table style="width:100%">
    <tr>
        <td>
            <a href="javascript:BackToOscar()" class="btn btn-link">
                <i class="fa-solid fa-arrow-right-from-bracket"></i>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="messenger.CreateMessage.btnExit"/>
            </a>
        </td>
    </tr>
</table>

<script>
    document.forms[0].message.focus();
</script>
</body>
</html>
