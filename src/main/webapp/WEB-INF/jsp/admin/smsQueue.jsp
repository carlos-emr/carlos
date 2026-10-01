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
    smsQueue.jsp: Administration > SMS > SMS queue, a read-only operational view of outbound SMS.

    Purpose: shows, per SMS provider, the outbound counts by status and four problem lists: queued
    messages overdue by more than a few minutes (due work that is not draining), sends whose outcome
    is unknown (still SENDING past the worker's stale threshold), terminal failures by error code, and
    messages blocked by consent by reason code. Also shows the queue scheduler setting and, for this
    server only, whether the scheduler is running and when it last ran.

    Parameters: reads only the "smsQueue" request attribute (SmsQueueViewModel) that SmsQueue2Action
    sets after checking _admin.sms read. The page changes nothing. Its one form is a GET that reloads
    the page with the "window" parameter: the time period for the Failed and Blocked by consent
    sections. The action accepts only the listed values; the page never reads the parameter itself.

    Security: rows are redacted view-model records, never entities. There is no message text, the
    recipient shows only as its last four digits, and the patient only as a plain demographic number
    (no link; drill-down through the patient SMS history view is a follow-up). Messages of patients
    the viewer may not open are not in the model at all; each list only says how many were left out.
    Every dynamic value is encoded with the carlos encoder, and message keys are built only from enum
    names and the time period values the server lists.

    @since 2026-09-28
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<html lang="<carlos:encode value='${pageContext.request.locale.language}' context='htmlAttribute'/>">
<head>
    <link rel="icon" href="${ctx}/images/favicon.ico"/>
    <title><fmt:message key="sms.queue.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
</head>
<body>
<div class="container-fluid mt-3" style="max-width: 1400px;">
    <h4><fmt:message key="sms.queue.title"/></h4>
    <p class="text-muted mb-1"><fmt:message key="sms.queue.intro"/></p>
    <p class="text-muted small" id="smsQueueGeneratedAt">
        <fmt:message key="sms.queue.generatedAt"/> <carlos:encode value="${smsQueue.generatedAt}"/>
    </p>

    <%-- A GET form: it only reloads this read-only page, so it carries no CSRF token. --%>
    <form method="get" action="${ctx}/admin/SmsQueue" id="smsQueueWindowForm" class="row g-2 align-items-center">
        <div class="col-auto">
            <label class="col-form-label" for="smsQueueWindow"><fmt:message key="sms.queue.window.label"/></label>
        </div>
        <div class="col-auto">
            <select class="form-select form-select-sm" id="smsQueueWindow" name="window">
                <c:forEach items="${smsQueue.windowOptions}" var="windowOption">
                    <option value="<carlos:encode value='${windowOption}' context='htmlAttribute'/>"
                            <c:if test="${windowOption eq smsQueue.window}">selected</c:if>>
                        <fmt:message key="sms.queue.window.option.${windowOption}"/>
                    </option>
                </c:forEach>
            </select>
        </div>
        <div class="col-auto">
            <button type="submit" class="btn btn-sm btn-primary"><fmt:message key="sms.queue.window.apply"/></button>
        </div>
    </form>
    <p class="form-text"><fmt:message key="sms.queue.window.help"/></p>

    <h5 class="mt-4"><fmt:message key="sms.queue.scheduler.title"/></h5>
    <div class="alert alert-secondary py-2" id="smsQueueSchedulerPerServer">
        <fmt:message key="sms.queue.scheduler.perServer"/>
    </div>
    <table class="table table-sm w-auto" id="smsQueueScheduler">
        <tbody>
        <tr>
            <th scope="row"><fmt:message key="sms.queue.scheduler.setting"/></th>
            <td>
                <c:choose>
                    <c:when test="${smsQueue.scheduler.settingEnabled}"><fmt:message key="sms.queue.on"/></c:when>
                    <c:otherwise><fmt:message key="sms.queue.off"/></c:otherwise>
                </c:choose>
                <span class="text-muted small">
                    <c:choose>
                        <c:when test="${smsQueue.scheduler.settingStored}"><fmt:message key="sms.queue.scheduler.settingStored"/></c:when>
                        <c:otherwise><fmt:message key="sms.queue.scheduler.settingProperty"/></c:otherwise>
                    </c:choose>
                </span>
            </td>
        </tr>
        <tr>
            <th scope="row"><fmt:message key="sms.queue.scheduler.running"/></th>
            <td id="smsQueueSchedulerRunning">
                <c:choose>
                    <c:when test="${smsQueue.scheduler.running}"><fmt:message key="sms.queue.yes"/></c:when>
                    <c:otherwise><fmt:message key="sms.queue.no"/></c:otherwise>
                </c:choose>
            </td>
        </tr>
        <tr>
            <th scope="row"><fmt:message key="sms.queue.scheduler.lastRunStarted"/></th>
            <td id="smsQueueSchedulerLastRun">
                <c:choose>
                    <c:when test="${empty smsQueue.scheduler.lastRunStartedAt}">
                        <fmt:message key="sms.queue.scheduler.neverRun"/>
                    </c:when>
                    <c:otherwise>
                        <carlos:encode value="${smsQueue.scheduler.lastRunStartedAt}"/>
                        <c:if test="${smsQueue.scheduler.runInProgress}">
                            <span class="badge text-bg-info"><fmt:message key="sms.queue.scheduler.inProgress"/></span>
                        </c:if>
                    </c:otherwise>
                </c:choose>
            </td>
        </tr>
        <c:if test="${not empty smsQueue.scheduler.lastRunOutcome}">
            <tr>
                <th scope="row"><fmt:message key="sms.queue.scheduler.lastRunFinished"/></th>
                <td id="smsQueueSchedulerLastFinished">
                    <carlos:encode value="${smsQueue.scheduler.lastRunFinishedAt}"/>:
                    <fmt:message key="sms.queue.scheduler.outcome.${smsQueue.scheduler.lastRunOutcome}"/>
                    <c:if test="${smsQueue.scheduler.lastRunOutcome eq 'COMPLETED'}">
                        (<fmt:message key="sms.queue.scheduler.processed"/>
                        <carlos:encode value="${smsQueue.scheduler.lastRunProcessed}"/>)
                    </c:if>
                </td>
            </tr>
        </c:if>
        </tbody>
    </table>

    <c:forEach items="${smsQueue.providers}" var="provider">
        <section class="mt-4 border-top pt-3"
                 id="smsQueueProvider-<carlos:encode value='${provider.providerType}' context='htmlAttribute'/>">
            <h5><fmt:message key="sms.queue.provider"/> <carlos:encode value="${provider.providerType}"/></h5>
            <c:choose>
                <c:when test="${provider.outboundTotal == 0}">
                    <p class="text-muted"><fmt:message key="sms.queue.noOutbound"/></p>
                </c:when>
                <c:otherwise>
                    <h6 class="mt-3"><fmt:message key="sms.queue.counts.title"/></h6>
                    <table class="table table-sm w-auto">
                        <thead>
                        <tr>
                            <th scope="col"><fmt:message key="sms.queue.col.status"/></th>
                            <th scope="col" class="text-end"><fmt:message key="sms.queue.col.count"/></th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${provider.statusCounts}" var="statusCount">
                            <tr>
                                <td><fmt:message key="sms.queue.status.${statusCount.status}"/></td>
                                <td class="text-end"><carlos:encode value="${statusCount.count}"/></td>
                            </tr>
                        </c:forEach>
                        <tr class="fw-bold">
                            <td><fmt:message key="sms.queue.total"/></td>
                            <td class="text-end"><carlos:encode value="${provider.outboundTotal}"/></td>
                        </tr>
                        </tbody>
                    </table>

                    <%-- Overdue queued: due work that is not draining. --%>
                    <h6 class="mt-3"><fmt:message key="sms.queue.overdue.title"/>
                        (<carlos:encode value="${provider.overdueCount}"/>)</h6>
                    <p class="form-text">
                        <fmt:message key="sms.queue.overdue.help">
                            <fmt:param value="${smsQueue.overdueMinutes}"/>
                        </fmt:message>
                    </p>
                    <c:choose>
                        <c:when test="${provider.overdueCount == 0}">
                            <p class="text-muted"><fmt:message key="sms.queue.overdue.none"/></p>
                        </c:when>
                        <c:otherwise>
                            <c:set var="queueRows" value="${provider.overdue.rows}"/>
                            <c:set var="queueRowsHidden" value="${provider.overdue.hiddenCount}"/>
                            <c:set var="queueRowsTotal" value="${provider.overdueCount}"/>
                            <%@ include file="/WEB-INF/jsp/admin/smsQueueRows.jspf" %>
                        </c:otherwise>
                    </c:choose>

                    <%-- Stale sends: handed to the SMS provider, no result recorded, outcome unknown. --%>
                    <h6 class="mt-3"><fmt:message key="sms.queue.stale.title"/>
                        (<carlos:encode value="${provider.staleCount}"/>)</h6>
                    <p class="form-text">
                        <fmt:message key="sms.queue.stale.help">
                            <fmt:param value="${smsQueue.staleMinutes}"/>
                        </fmt:message>
                    </p>
                    <c:choose>
                        <c:when test="${provider.staleCount == 0}">
                            <p class="text-muted"><fmt:message key="sms.queue.stale.none"/></p>
                        </c:when>
                        <c:otherwise>
                            <c:set var="queueRows" value="${provider.stale.rows}"/>
                            <c:set var="queueRowsHidden" value="${provider.stale.hiddenCount}"/>
                            <c:set var="queueRowsTotal" value="${provider.staleCount}"/>
                            <%@ include file="/WEB-INF/jsp/admin/smsQueueRows.jspf" %>
                        </c:otherwise>
                    </c:choose>

                    <%-- Terminal failures within the selected time period, by error code. --%>
                    <h6 class="mt-3"><fmt:message key="sms.queue.failed.title"/>
                        (<carlos:encode value="${provider.failedCount}"/>)</h6>
                    <p class="form-text"><fmt:message key="sms.queue.failed.help"/></p>
                    <p class="small mb-1">
                        <fmt:message key="sms.queue.window.totals">
                            <fmt:param value="${provider.failedCount}"/>
                            <fmt:param value="${provider.failedTotal}"/>
                        </fmt:message>
                    </p>
                    <c:choose>
                        <c:when test="${provider.failedCount == 0}">
                            <p class="text-muted"><fmt:message key="sms.queue.failed.none"/></p>
                        </c:when>
                        <c:otherwise>
                            <%-- Empty when every message here belongs to a patient the viewer has no access to. --%>
                            <c:if test="${not empty provider.failedByErrorCode}">
                            <table class="table table-sm w-auto">
                                <thead>
                                <tr>
                                    <th scope="col"><fmt:message key="sms.queue.col.errorCode"/></th>
                                    <th scope="col" class="text-end"><fmt:message key="sms.queue.col.count"/></th>
                                </tr>
                                </thead>
                                <tbody>
                                <c:forEach items="${provider.failedByErrorCode}" var="codeCount">
                                    <tr>
                                        <td>
                                            <c:choose>
                                                <c:when test="${empty codeCount.code}"><fmt:message key="sms.queue.noCode"/></c:when>
                                                <c:otherwise><carlos:encode value="${codeCount.code}"/></c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td class="text-end"><carlos:encode value="${codeCount.count}"/></td>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                            </c:if>
                            <c:set var="queueRows" value="${provider.recentFailed.rows}"/>
                            <c:set var="queueRowsHidden" value="${provider.recentFailed.hiddenCount}"/>
                            <c:set var="queueRowsTotal" value="${provider.failedCount}"/>
                            <%@ include file="/WEB-INF/jsp/admin/smsQueueRows.jspf" %>
                        </c:otherwise>
                    </c:choose>

                    <%-- Blocked by consent (no consent or opted out) within the selected time period, by reason code. --%>
                    <h6 class="mt-3"><fmt:message key="sms.queue.blocked.title"/>
                        (<carlos:encode value="${provider.blockedCount}"/>)</h6>
                    <p class="form-text"><fmt:message key="sms.queue.blocked.help"/></p>
                    <p class="small mb-1">
                        <fmt:message key="sms.queue.window.totals">
                            <fmt:param value="${provider.blockedCount}"/>
                            <fmt:param value="${provider.blockedTotal}"/>
                        </fmt:message>
                    </p>
                    <c:choose>
                        <c:when test="${provider.blockedCount == 0}">
                            <p class="text-muted"><fmt:message key="sms.queue.blocked.none"/></p>
                        </c:when>
                        <c:otherwise>
                            <%-- Empty when every message here belongs to a patient the viewer has no access to. --%>
                            <c:if test="${not empty provider.blockedByReason}">
                            <table class="table table-sm w-auto">
                                <thead>
                                <tr>
                                    <th scope="col"><fmt:message key="sms.queue.col.reasonCode"/></th>
                                    <th scope="col" class="text-end"><fmt:message key="sms.queue.col.count"/></th>
                                </tr>
                                </thead>
                                <tbody>
                                <c:forEach items="${provider.blockedByReason}" var="codeCount">
                                    <tr>
                                        <td>
                                            <c:choose>
                                                <c:when test="${empty codeCount.code}"><fmt:message key="sms.queue.noCode"/></c:when>
                                                <c:otherwise><carlos:encode value="${codeCount.code}"/></c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td class="text-end"><carlos:encode value="${codeCount.count}"/></td>
                                    </tr>
                                </c:forEach>
                                </tbody>
                            </table>
                            </c:if>
                            <c:set var="queueRows" value="${provider.recentBlocked.rows}"/>
                            <c:set var="queueRowsHidden" value="${provider.recentBlocked.hiddenCount}"/>
                            <c:set var="queueRowsTotal" value="${provider.blockedCount}"/>
                            <%@ include file="/WEB-INF/jsp/admin/smsQueueRows.jspf" %>
                        </c:otherwise>
                    </c:choose>
                </c:otherwise>
            </c:choose>
        </section>
    </c:forEach>
</div>
</body>
</html>
