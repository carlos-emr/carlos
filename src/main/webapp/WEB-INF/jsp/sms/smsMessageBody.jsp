<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
    smsMessageBody.jsp: full text of one SMS, shown after the audited read.

    Purpose: result of ViewSmsHistory2Action's POST-only showMessage. Shows the message text and
    the reason recorded in the audit log; or that the text was not kept (consent blocked the
    send, so nothing was read or audited); or that the user may not read SMS text (_msgSMS).

    Parameters: request attributes smsMessageBody, smsMessageNotStored, smsMessageDenied,
    smsMessageReason and smsHistoryDemographicNo, all set by ViewSmsHistory2Action.

    Security: the message text is PHI and patient-supplied for inbound messages, so it is
    HTML-encoded and never placed in an attribute or script. The page is only reached by POST.

    @since 2026-09-24
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<html>
<head>
    <link rel="icon" href="${ctx}/images/favicon.ico"/>
    <title><fmt:message key="sms.message.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
</head>
<body>
<nav class="navbar navbar-dark bg-dark">
    <div class="container-fluid">
        <span class="navbar-brand"><fmt:message key="sms.message.title"/></span>
    </div>
</nav>

<div class="container-fluid mt-3">
    <c:choose>
        <c:when test="${smsMessageDenied}">
            <div class="alert alert-danger" id="smsMessageDenied"><fmt:message key="sms.message.denied"/></div>
        </c:when>
        <c:when test="${smsMessageNotStored}">
            <div class="alert alert-secondary" id="smsMessageNotStored"><fmt:message key="sms.message.notStored"/></div>
        </c:when>
        <c:otherwise>
            <pre class="border rounded p-3 bg-light" id="smsMessageBody" style="white-space: pre-wrap;"><carlos:encode value="${smsMessageBody}"/></pre>
            <p class="text-muted small">
                <fmt:message key="sms.message.reason"/>:
                <fmt:message key="sms.history.reason.${smsMessageReason}"/>.
                <fmt:message key="sms.message.audited"/>
            </p>
        </c:otherwise>
    </c:choose>
    <a href="${ctx}/sms/ViewSmsHistory?demographic_no=${carlos:forUriComponent(smsHistoryDemographicNo)}">
        <fmt:message key="sms.message.back"/></a>
</div>
</body>
</html>
