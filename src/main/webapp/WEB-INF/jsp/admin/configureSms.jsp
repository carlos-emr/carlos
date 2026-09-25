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
    configureSms.jsp: Administration > SMS settings.

    Purpose: lets an administrator choose the SMS provider, turn sending and the queue scheduler
    on or off, set the sender number, the webhook secret and the provider's credentials, and send
    the fixed system-test text to a number they type (always through the STUB provider).

    Parameters: reads only the "smsConfig" request attribute (SmsConfigViewModel) that
    ConfigureSms2Action sets after checking _admin.sms read.

    Security: secrets are write-only. Their inputs are always empty; the page only says whether a
    value is stored, and leaving an input blank keeps the stored value. Both forms are real POST
    forms with an action URL, so CSRFGuard injects their token; the action checks _admin.sms write.
    Every value is encoded with the carlos encoder.

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
    <title><fmt:message key="sms.config.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
</head>
<body>
<div class="container-fluid mt-3" style="max-width: 900px;">
    <h4><fmt:message key="sms.config.title"/></h4>

    <c:if test="${not empty smsConfig.resultKey}">
        <div class="alert alert-info" id="smsConfigResult"><fmt:message key="${smsConfig.resultKey}"/></div>
    </c:if>
    <c:if test="${not empty smsConfig.errorKeys}">
        <div class="alert alert-danger" id="smsConfigErrors">
            <c:forEach items="${smsConfig.errorKeys}" var="errorKey">
                <div><fmt:message key="${errorKey}"/></div>
            </c:forEach>
        </div>
    </c:if>
    <c:if test="${not smsConfig.stored}">
        <div class="alert alert-secondary" id="smsConfigNotSaved"><fmt:message key="sms.config.notSaved"/></div>
    </c:if>

    <form method="post" action="${ctx}/admin/ConfigureSms" id="smsConfigForm" autocomplete="off">
        <input type="hidden" name="method" value="configure"/>

        <div class="mb-3">
            <label class="form-label" for="providerType"><fmt:message key="sms.config.provider"/></label>
            <select class="form-select" id="providerType" name="providerType">
                <c:forEach items="${smsConfig.providerOptions}" var="option">
                    <option value="<carlos:encode value='${option}' context='htmlAttribute'/>"
                            <c:if test="${option eq smsConfig.providerType}">selected</c:if>>
                        <carlos:encode value="${option}"/></option>
                </c:forEach>
            </select>
            <div class="form-text"><fmt:message key="sms.config.providerHelp"/></div>
        </div>

        <div class="form-check mb-2">
            <input class="form-check-input" type="checkbox" id="enabled" name="enabled" value="true"
                   <c:if test="${smsConfig.enabled}">checked</c:if>/>
            <label class="form-check-label" for="enabled"><fmt:message key="sms.config.enabled"/></label>
        </div>
        <div class="form-check mb-3">
            <input class="form-check-input" type="checkbox" id="schedulerEnabled" name="schedulerEnabled" value="true"
                   <c:if test="${smsConfig.schedulerEnabled}">checked</c:if>/>
            <label class="form-check-label" for="schedulerEnabled"><fmt:message key="sms.config.schedulerEnabled"/></label>
            <div class="form-text" id="schedulerState">
                <c:choose>
                    <c:when test="${smsConfig.schedulerRunning}"><fmt:message key="sms.config.schedulerRunning"/></c:when>
                    <c:otherwise><fmt:message key="sms.config.schedulerStopped"/></c:otherwise>
                </c:choose>
            </div>
        </div>

        <div class="mb-3">
            <label class="form-label" for="senderNumber"><fmt:message key="sms.config.senderNumber"/></label>
            <input class="form-control" type="text" id="senderNumber" name="senderNumber"
                   value="<carlos:encode value='${smsConfig.senderNumber}' context='htmlAttribute'/>"/>
        </div>

        <div class="mb-3">
            <label class="form-label" for="webhookSecret"><fmt:message key="sms.config.webhookSecret"/></label>
            <input class="form-control" type="password" id="webhookSecret" name="webhookSecret" value=""
                   autocomplete="new-password"/>
            <div class="form-text" id="webhookSecretState">
                <c:choose>
                    <c:when test="${smsConfig.webhookSecretSet}"><fmt:message key="sms.config.secretStored"/></c:when>
                    <c:otherwise><fmt:message key="sms.config.secretNotStored"/></c:otherwise>
                </c:choose>
            </div>
            <c:if test="${smsConfig.webhookSecretSet}">
                <div class="form-check">
                    <input class="form-check-input" type="checkbox" id="clearWebhookSecret" name="clearWebhookSecret"
                           value="true"/>
                    <label class="form-check-label" for="clearWebhookSecret"><fmt:message key="sms.config.clearSecret"/></label>
                </div>
            </c:if>
        </div>

        <fieldset class="mb-3" id="credentialFields">
            <legend class="fs-6"><fmt:message key="sms.config.credentials"/></legend>
            <c:choose>
                <c:when test="${empty smsConfig.credentialFields}">
                    <p class="text-muted"><fmt:message key="sms.config.noCredentials"/></p>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${smsConfig.credentialFields}" var="field">
                        <div class="mb-2">
                            <label class="form-label"><carlos:encode value="${field.name}"/></label>
                            <input class="form-control" type="password" value="" autocomplete="new-password"
                                   name="credential.<carlos:encode value='${field.name}' context='htmlAttribute'/>"/>
                            <div class="form-text">
                                <c:choose>
                                    <c:when test="${field.set}"><fmt:message key="sms.config.secretStored"/></c:when>
                                    <c:otherwise><fmt:message key="sms.config.secretNotStored"/></c:otherwise>
                                </c:choose>
                            </div>
                        </div>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
        </fieldset>

        <button type="submit" class="btn btn-primary" id="smsConfigSave"><fmt:message key="sms.config.save"/></button>
    </form>

    <hr class="my-4"/>

    <h5><fmt:message key="sms.config.systemTestTitle"/></h5>
    <p class="text-muted"><fmt:message key="sms.config.systemTestHelp"/></p>
    <c:if test="${not smsConfig.systemTestEnabled}">
        <div class="alert alert-warning" id="systemTestOff"><fmt:message key="sms.config.systemTestOff"/></div>
    </c:if>
    <form method="post" action="${ctx}/admin/ConfigureSms" id="smsSystemTestForm" class="d-flex gap-2" autocomplete="off">
        <input type="hidden" name="method" value="sendSystemTest"/>
        <fmt:message key="sms.config.testNumber" var="testNumberPlaceholder"/>
        <input class="form-control" type="text" id="testNumber" name="testNumber" style="max-width: 16rem;"
               placeholder="<carlos:encode value='${testNumberPlaceholder}' context='htmlAttribute'/>"/>
        <button type="submit" class="btn btn-outline-primary" id="smsSystemTestSend"><fmt:message key="sms.config.sendSystemTest"/></button>
    </form>
</div>
</body>
</html>
