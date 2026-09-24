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
    smsHistory.jsp: patient SMS history popup.

    Purpose: lists one patient's text messages, newest first, 25 per page, with direction, type,
    status, the last four digits of the number, the consent reason code and any error code.
    Message text is never on this page. Each row whose text is stored offers "Show message", a
    POST form that asks for a reason and opens smsMessageBody.jsp through the audited read path.

    Parameters: reads only the "smsHistory" request attribute (SmsHistoryViewModel) that
    ViewSmsHistory2Action sets after checking _sms and _demographic read for the patient.

    Security: every value is encoded with the carlos encoder. The show-message forms are real
    POST forms with an action URL, so CSRFGuard injects their token. The button only appears when
    canReadMessageBodies is true; the read service still checks _msgSMS itself.

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
    <title><fmt:message key="sms.history.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
</head>
<body>
<nav class="navbar navbar-dark bg-dark">
    <div class="container-fluid">
        <span class="navbar-brand"><fmt:message key="sms.history.title"/></span>
        <span class="navbar-text text-white-50">
            <em><carlos:encode value="${smsHistory.patientDisplayName}"/></em>
            &nbsp;(<carlos:encode value="${smsHistory.demographicNo}"/>)
        </span>
    </div>
</nav>

<div class="container-fluid mt-3">
    <c:choose>
        <c:when test="${empty smsHistory.rows}">
            <p class="text-muted" id="smsHistoryEmpty"><fmt:message key="sms.history.empty"/></p>
        </c:when>
        <c:otherwise>
            <table id="smsHistoryTable" class="table table-striped table-hover table-bordered table-sm">
                <thead>
                <tr>
                    <th><fmt:message key="sms.history.col.date"/></th>
                    <th><fmt:message key="sms.history.col.direction"/></th>
                    <th><fmt:message key="sms.history.col.type"/></th>
                    <th><fmt:message key="sms.history.col.status"/></th>
                    <th><fmt:message key="sms.history.col.number"/></th>
                    <th><fmt:message key="sms.history.col.consent"/></th>
                    <th><fmt:message key="sms.history.col.error"/></th>
                    <th><fmt:message key="sms.history.col.completed"/></th>
                    <th><fmt:message key="sms.history.col.message"/></th>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${smsHistory.rows}" var="row">
                    <tr>
                        <td><carlos:encode value="${row.createdAt}"/></td>
                        <td><fmt:message key="sms.direction.${row.direction}"/></td>
                        <td><fmt:message key="sms.purpose.${row.purpose}"/></td>
                        <td><fmt:message key="sms.status.${row.status}"/></td>
                        <td><carlos:encode value="${row.phone}"/></td>
                        <td><carlos:encode value="${row.consentReason}"/></td>
                        <td><carlos:encode value="${row.errorCode}"/></td>
                        <td><carlos:encode value="${row.completedAt}"/></td>
                        <td>
                            <c:choose>
                                <c:when test="${not row.bodyStored}">
                                    <span class="text-muted"><fmt:message key="sms.history.notStored"/></span>
                                </c:when>
                                <c:when test="${smsHistory.canReadMessageBodies}">
                                    <form method="post" action="${ctx}/sms/ViewSmsHistory" class="d-flex gap-1">
                                        <input type="hidden" name="method" value="showMessage"/>
                                        <input type="hidden" name="demographic_no"
                                               value="<carlos:encode value='${smsHistory.demographicNo}' context='htmlAttribute'/>"/>
                                        <input type="hidden" name="smsTransactionId"
                                               value="<carlos:encode value='${row.id}' context='htmlAttribute'/>"/>
                                        <select name="reason" class="form-select form-select-sm" required>
                                            <option value="CARE_REVIEW"><fmt:message key="sms.history.reason.CARE_REVIEW"/></option>
                                            <option value="DELIVERY_REVIEW"><fmt:message key="sms.history.reason.DELIVERY_REVIEW"/></option>
                                            <option value="PATIENT_REQUEST"><fmt:message key="sms.history.reason.PATIENT_REQUEST"/></option>
                                        </select>
                                        <button type="submit" class="btn btn-sm btn-outline-primary text-nowrap">
                                            <fmt:message key="sms.history.btnShowMessage"/></button>
                                    </form>
                                </c:when>
                            </c:choose>
                        </td>
                    </tr>
                </c:forEach>
                </tbody>
            </table>

            <nav class="d-flex align-items-center gap-3" id="smsHistoryPaging">
                <c:if test="${smsHistory.hasPreviousPage}">
                    <a href="${ctx}/sms/ViewSmsHistory?demographic_no=${carlos:forUriComponent(smsHistory.demographicNo)}&amp;page=${smsHistory.page - 1}">
                        <fmt:message key="sms.history.previous"/></a>
                </c:if>
                <span>
                    <fmt:message key="sms.history.pageOf">
                        <fmt:param value="${smsHistory.page}"/>
                        <fmt:param value="${smsHistory.pageCount}"/>
                        <fmt:param value="${smsHistory.totalCount}"/>
                    </fmt:message>
                </span>
                <c:if test="${smsHistory.hasNextPage}">
                    <a href="${ctx}/sms/ViewSmsHistory?demographic_no=${carlos:forUriComponent(smsHistory.demographicNo)}&amp;page=${smsHistory.page + 1}">
                        <fmt:message key="sms.history.next"/></a>
                </c:if>
            </nav>
        </c:otherwise>
    </c:choose>
</div>
</body>
</html>
