<!DOCTYPE html>
<%-- SPDX-License-Identifier: GPL-2.0-or-later
     Copyright (c) 2026 CARLOS Contributors. All Rights Reserved. --%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="mycarlos.delivery.title"/></title>
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(ctx)}/share/css/mycarlos-send.css">
</head>
<body>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<main class="mycarlos-send" id="mycarlos-send" data-context="${carlos:forHtmlAttribute(ctx)}"
      data-patient="${carlos:forHtmlAttribute(demographicNo)}" data-document="${carlos:forHtmlAttribute(documentId)}"
      data-delivery="${carlos:forHtmlAttribute(deliveryId)}" data-next="${carlos:forHtmlAttribute(nextCursor)}">
    <header><h1><fmt:message key="mycarlos.delivery.title"/></h1>
        <p class="document-title"><carlos:encode value="${documentTitle}"/></p></header>
    <p><fmt:message key="mycarlos.delivery.description"/></p>
    <div id="delivery-message" role="status" aria-live="polite"></div>
    <c:choose>
        <c:when test="${not available}"><p class="delivery-warning"><fmt:message key="mycarlos.delivery.unavailable"/></p></c:when>
        <c:otherwise>
            <form id="delivery-form" method="post" action="${carlos:forHtmlAttribute(ctx)}/demographic/PortalDelivery">
                <label for="delivery-device"><fmt:message key="mycarlos.delivery.device"/></label>
                <select id="delivery-device" name="deviceKeyId" required ${empty devices ? 'disabled' : ''}>
                    <c:forEach items="${devices}" var="device">
                        <option value="${carlos:forHtmlAttribute(device.deviceKeyId)}"><carlos:encode value="${device.acceptedAt}"/> · <carlos:encode value="${device.fingerprint}"/></option>
                    </c:forEach>
                </select>
                <p id="delivery-no-device" ${empty devices ? '' : 'hidden'}><fmt:message key="mycarlos.delivery.noDevice"/></p>
                <div class="delivery-actions">
                    <button type="submit" id="delivery-send" ${empty devices ? 'disabled' : ''}><fmt:message key="mycarlos.delivery.send"/></button>
                    <button type="button" id="delivery-check"><fmt:message key="mycarlos.delivery.check"/></button>
                    <a id="delivery-new" href="${carlos:forHtmlAttribute(ctx)}/demographic/PortalDelivery?demographicNo=${carlos:forUriComponent(demographicNo)}&amp;documentId=${carlos:forUriComponent(documentId)}"><fmt:message key="mycarlos.delivery.new"/></a>
                </div>
            </form>
            <section aria-labelledby="delivery-history-title">
                <h2 id="delivery-history-title"><fmt:message key="mycarlos.delivery.recent"/></h2>
                <table><thead><tr><th scope="col"><fmt:message key="mycarlos.delivery.queued"/></th>
                    <th scope="col"><fmt:message key="mycarlos.delivery.status"/></th></tr></thead>
                    <tbody id="delivery-history">
                    <c:forEach items="${deliveries}" var="row">
                        <tr data-delivery="${carlos:forHtmlAttribute(row.deliveryId)}" data-device="${carlos:forHtmlAttribute(row.deviceKeyId)}"><td><carlos:encode value="${row.queuedAt}"/></td>
                            <td><fmt:message key="mycarlos.delivery.status.${row.status}"/></td></tr>
                    </c:forEach>
                    </tbody>
                </table>
                <p id="delivery-empty" ${empty deliveries ? '' : 'hidden'}><fmt:message key="mycarlos.delivery.empty"/></p>
                <button type="button" id="delivery-more" ${empty nextCursor ? 'hidden' : ''}><fmt:message key="mycarlos.delivery.more"/></button>
            </section>
        </c:otherwise>
    </c:choose>
    <div id="delivery-copy" hidden>
        <c:forEach items="${['staged','waiting','picked_up','expired','removed','fenced','error']}" var="status">
            <span data-status="${carlos:forHtmlAttribute(status)}"><fmt:message key="mycarlos.delivery.status.${status}"/></span>
        </c:forEach>
        <span data-copy="error"><fmt:message key="mycarlos.delivery.error"/></span>
        <span data-copy="sync"><fmt:message key="mycarlos.delivery.syncWarning"/></span>
        <span data-copy="busy"><fmt:message key="mycarlos.delivery.busy"/></span>
    </div>
</main>
<script src="${carlos:forHtmlAttribute(ctx)}/share/javascript/demographic/mycarlos-send.js"></script>
</body>
</html>
