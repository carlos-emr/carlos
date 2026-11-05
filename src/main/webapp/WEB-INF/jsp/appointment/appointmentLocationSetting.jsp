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
    Appointment Settings, Locations tab

    Purpose:
      Manages the appointment Location List (issue #3982): the places an appointment can be booked,
      such as rooms, "Virtual" or a satellite office. Once a location is active and no site setup is
      configured, the booking screens offer the list as a dropdown and the day schedule marks each
      booking with the location's chip.

    Features:
      - Add one location, or paste a list (one per line); duplicates of active names are skipped.
      - Rename, set the chip colour and icon, move up/down the booking order, disable and enable.
        Each is its own POST form to appointment/apptLocationSetting, which answers 405 to GET/HEAD
        for a change; CSRFGuard adds the token to these same-origin POST forms.
      - Disabling asks for confirmation first; cancelling sends no request.
      - Read-only rendering for a user with read but not write.

    Request attributes (AppointmentLocationSettings2Action):
      locations             List<LookupListItem> active locations in booking order, then inactive by name
      activeLocationCount   Integer how many locations are active (they come first)
      siteSetup             Boolean whether a site setup is configured, which the booking screens use instead
      canChange             Boolean whether the user may change locations
      canDeactivate         Boolean whether the user may disable a location
      locationIcons         List<String> the whitelisted icon classes
      defaultLocationColour String the chip colour of a location with none
      nameMaxLength         Integer the longest name allowed
      pasteMaxLines         Integer the most names one paste may add

    @since 2026-11-04
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%-- One locale for the bundle and for <html lang>, as the booking screens resolve it. --%>
<% pageContext.setAttribute("pageLocale", io.github.carlos_emr.carlos.utility.LocaleUtils.resolveBundleLocale(request)); %>
<fmt:setLocale value="${pageLocale}"/>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/struts-tags" prefix="s" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib tagdir="/WEB-INF/tags/appointment" prefix="appt" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.schedule" rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logoutPage");%>
</security:oscarSec>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="locationAction" value="${ctx}/appointment/apptLocationSetting"/>
<c:set var="activeTab" value="location"/>
<fmt:message key="admin.appt.location.msg.confirmDisable" var="confirmDisable"/>
<fmt:message key="admin.appt.location.msg.confirmLastDisable" var="confirmLastDisable"/>
<fmt:message key="admin.appt.location.label.defaultStyle" var="defaultStyleLabel"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageLocale.language)}">
<head>
    <meta charset="UTF-8">
    <link rel="icon" href="${carlos:forHtmlAttribute(ctx)}/images/favicon.ico"/>
    <title><fmt:message key="admin.appt.location.title"/></title>
    <%@ include file="/WEB-INF/jspf/bootstrap-css.jspf" %>
    <link rel="stylesheet" href="${carlos:forHtmlAttribute(ctx)}/css/fontawesome-all.min.css">
    <style>
        .location-name { min-width: 12rem; }
        .location-colour { width: 3rem; padding: .1rem; }
        .location-icon { min-width: 10rem; }
    </style>
</head>
<body class="p-3" data-site-setup="${siteSetup}" data-location-mode="${not siteSetup and activeLocationCount > 0}">
<%@ include file="/WEB-INF/jsp/appointment/appointmentSettingsNav.jspf" %>

<h1 class="h5 mb-3"><fmt:message key="admin.appt.location.title"/></h1>

<s:if test="hasActionErrors()">
    <div class="alert alert-danger" role="alert"><s:actionerror/></div>
</s:if>
<s:if test="hasActionMessages()">
    <output class="alert alert-success d-block"><s:iterator value="actionMessages"><span class="d-block"><s:property/></span></s:iterator></output>
</s:if>

<p class="text-body-secondary small"><fmt:message key="admin.appt.location.msg.intro"/></p>
<c:if test="${siteSetup}">
    <div class="alert alert-info" role="note"><fmt:message key="admin.appt.location.msg.siteSetup"/></div>
</c:if>
<c:if test="${not siteSetup and activeLocationCount == 0}">
    <div class="alert alert-info" role="note"><fmt:message key="admin.appt.location.msg.noneActive"/></div>
</c:if>

<c:if test="${canChange}">
    <div class="row g-3 mb-3">
        <div class="col-md-6">
            <form action="${carlos:forHtmlAttribute(locationAction)}" method="post" class="d-flex gap-2" id="addLocationForm">
                <input type="hidden" name="dispatch" value="add"/>
                <input type="hidden" name="carlosFormSubmission" value="${carlos:forHtmlAttribute(locationAddToken)}"/>
                <label class="visually-hidden" for="newLocationName"><fmt:message key="admin.appt.location.label.newName"/></label>
                <fmt:message key="admin.appt.location.label.newName" var="newNamePlaceholder"/>
                <input class="form-control form-control-sm" type="text" id="newLocationName" name="name" maxlength="${nameMaxLength}"
                       value="${refusedDispatch eq 'add' ? carlos:forHtmlAttribute(param.name) : ''}"
                       placeholder="${carlos:forHtmlAttribute(newNamePlaceholder)}" required>
                <button class="btn btn-sm btn-primary text-nowrap" type="submit"><fmt:message key="admin.appt.location.btn.add"/></button>
            </form>
        </div>
        <div class="col-md-6">
            <details${refusedDispatch eq 'addMany' ? ' open' : ''}>
                <summary class="small"><fmt:message key="admin.appt.location.label.paste"/></summary>
                <form action="${carlos:forHtmlAttribute(locationAction)}" method="post" class="mt-2" id="pasteLocationsForm">
                    <input type="hidden" name="dispatch" value="addMany"/>
                    <input type="hidden" name="carlosFormSubmission" value="${carlos:forHtmlAttribute(locationPasteToken)}"/>
                    <label class="form-label small" for="pastedLocationNames">
                        <fmt:message key="admin.appt.location.msg.pasteHelp"><fmt:param value="${pasteMaxLines}"/></fmt:message>
                    </label>
                    <textarea class="form-control form-control-sm mb-2" id="pastedLocationNames" name="names" rows="4"
                              maxlength="${pasteMaxLength}" required><c:if test="${refusedDispatch eq 'addMany'}"><carlos:encode value="${param.names}"/></c:if></textarea>
                    <button class="btn btn-sm btn-outline-primary" type="submit"><fmt:message key="admin.appt.location.btn.addMany"/></button>
                </form>
            </details>
        </div>
    </div>
</c:if>

<c:choose>
    <c:when test="${empty locations}">
        <p class="text-body-secondary"><fmt:message key="admin.appt.location.msg.empty"/></p>
    </c:when>
    <c:otherwise>
        <div class="table-responsive">
            <table id="locationTable" class="table table-sm table-striped align-middle">
                <thead>
                <tr>
                    <th scope="col"><fmt:message key="admin.appt.location.label.name"/></th>
                    <th scope="col"><fmt:message key="admin.appt.location.label.colour"/></th>
                    <th scope="col"><fmt:message key="admin.appt.location.label.icon"/></th>
                    <th scope="col"><fmt:message key="admin.appt.location.label.chip"/></th>
                    <th scope="col"><fmt:message key="admin.appt.location.label.active"/></th>
                    <c:if test="${canChange}">
                        <th scope="col"><fmt:message key="admin.appt.location.label.actions"/></th>
                    </c:if>
                </tr>
                </thead>
                <tbody>
                <c:forEach items="${locations}" var="item" varStatus="loop">
                    <tr id="location-${item.id}" data-location-id="${item.id}" class="${item.active ? '' : 'text-body-secondary'}">
                        <td>
                            <span class="visually-hidden" id="row-${item.id}"><carlos:encode value="${item.label}"/></span>
                            <c:choose>
                                <c:when test="${canChange}">
                                    <form class="d-flex gap-1" action="${carlos:forHtmlAttribute(locationAction)}" method="post">
                                        <input type="hidden" name="dispatch" value="rename"/>
                                        <input type="hidden" name="id" value="${item.id}"/>
                                        <label class="visually-hidden" for="name-${item.id}"><fmt:message key="admin.appt.location.label.name"/></label>
                                        <c:set var="shownName" value="${refusedDispatch eq 'rename' and refusedId eq item.id ? param.name : item.label}"/>
                                        <input class="form-control form-control-sm location-name" type="text" id="name-${item.id}" name="name"
                                               aria-describedby="row-${item.id}"
                                               value="<carlos:encode value='${shownName}' context='htmlAttribute'/>" maxlength="${nameMaxLength}" required>
                                        <button class="btn btn-sm btn-outline-primary" type="submit" aria-describedby="row-${item.id}"><fmt:message key="global.btnSave"/></button>
                                    </form>
                                </c:when>
                                <c:otherwise><carlos:encode value="${item.label}"/></c:otherwise>
                            </c:choose>
                        </td>
                        <td class="text-nowrap">
                            <c:set var="hasColour" value="${not empty item.colour and item.colour.matches('#[0-9a-fA-F]{6}')}"/>
                            <c:choose>
                                <c:when test="${canChange}">
                                    <form class="d-inline-flex align-items-center gap-1" action="${carlos:forHtmlAttribute(locationAction)}" method="post">
                                        <input type="hidden" name="dispatch" value="updateColour"/>
                                        <input type="hidden" name="id" value="${item.id}"/>
                                        <label class="visually-hidden" for="colour-${item.id}"><fmt:message key="admin.appt.location.label.colour"/></label>
                                        <input class="form-control form-control-sm form-control-color location-colour" type="color" id="colour-${item.id}" name="colour"
                                               aria-describedby="row-${item.id}"
                                               value="${carlos:forHtmlAttribute(hasColour ? item.colour : defaultLocationColour)}">
                                        <button class="btn btn-sm btn-outline-primary" type="submit" aria-describedby="row-${item.id}"><fmt:message key="global.btnSave"/></button>
                                    </form>
                                    <c:if test="${not empty item.colour}">
                                        <form class="d-inline" action="${carlos:forHtmlAttribute(locationAction)}" method="post">
                                            <input type="hidden" name="dispatch" value="updateColour"/>
                                            <input type="hidden" name="id" value="${item.id}"/>
                                            <input type="hidden" name="colour" value=""/>
                                            <button class="btn btn-sm btn-link" type="submit" aria-describedby="row-${item.id}"><fmt:message key="admin.appt.location.btn.useDefault"/></button>
                                        </form>
                                    </c:if>
                                </c:when>
                                <c:when test="${hasColour}"><carlos:encode value="${item.colour}"/></c:when>
                                <c:otherwise><span title="${carlos:forHtmlAttribute(defaultStyleLabel)}">&mdash;</span></c:otherwise>
                            </c:choose>
                        </td>
                        <td class="text-nowrap">
                            <c:choose>
                                <c:when test="${canChange}">
                                    <form class="d-flex align-items-center gap-1" action="${carlos:forHtmlAttribute(locationAction)}" method="post">
                                        <input type="hidden" name="dispatch" value="updateIcon"/>
                                        <input type="hidden" name="id" value="${item.id}"/>
                                        <label class="visually-hidden" for="icon-${item.id}"><fmt:message key="admin.appt.location.label.icon"/></label>
                                        <%-- Only whitelisted icons reach the class attribute, as in the chip. The
                                             emptiness test comes first: List.of(...).contains(null) throws. --%>
                                        <c:set var="shownIcon" value="${empty item.icon or not locationIcons.contains(item.icon) ? defaultLocationIcon : item.icon}"/>
                                        <i class="fa-solid ${carlos:forHtmlAttribute(shownIcon)} location-icon-preview" aria-hidden="true"
                                           data-current="${carlos:forHtmlAttribute(shownIcon)}" data-default-icon="${carlos:forHtmlAttribute(defaultLocationIcon)}"></i>
                                        <select class="form-select form-select-sm location-icon" id="icon-${item.id}" name="icon" aria-describedby="row-${item.id}">
                                            <option value=""${empty item.icon ? ' selected' : ''}><carlos:encode value="${defaultStyleLabel}"/></option>
                                            <c:forEach items="${locationIcons}" var="iconClass">
                                                <option value="${carlos:forHtmlAttribute(iconClass)}"${iconClass == item.icon ? ' selected' : ''}><carlos:encode value="${fn:replace(fn:substringAfter(iconClass, 'fa-'), '-', ' ')}"/></option>
                                            </c:forEach>
                                        </select>
                                        <button class="btn btn-sm btn-outline-primary" type="submit" aria-describedby="row-${item.id}"><fmt:message key="global.btnSave"/></button>
                                    </form>
                                </c:when>
                                <c:when test="${not empty item.icon}"><carlos:encode value="${item.icon}"/></c:when>
                                <c:otherwise><span title="${carlos:forHtmlAttribute(defaultStyleLabel)}">&mdash;</span></c:otherwise>
                            </c:choose>
                        </td>
                        <%-- Inactive locations still mark the appointments booked with them. --%>
                        <td><appt:locationChip item="${item}" showLabel="${true}"/></td>
                        <td class="text-nowrap"><fmt:message key="${item.active ? 'global.yes' : 'global.no'}"/></td>
                        <c:if test="${canChange}">
                            <td class="text-nowrap">
                                <c:choose>
                                    <c:when test="${item.active}">
                                        <c:if test="${activeLocationCount > 1}">
                                            <form class="d-inline" action="${carlos:forHtmlAttribute(locationAction)}" method="post">
                                                <input type="hidden" name="dispatch" value="moveUp"/>
                                                <input type="hidden" name="id" value="${item.id}"/>
                                                <input type="hidden" name="position" value="${loop.count}"/>
                                                <fmt:message key="admin.appt.location.btn.moveUp" var="moveUpLabel"/>
                                                <button class="btn btn-sm btn-outline-secondary" type="submit" title="${carlos:forHtmlAttribute(moveUpLabel)}"
                                                        aria-label="${carlos:forHtmlAttribute(moveUpLabel)}" aria-describedby="row-${item.id}"${loop.first ? ' disabled' : ''}><i class="fa-solid fa-chevron-up" aria-hidden="true"></i></button>
                                            </form>
                                            <form class="d-inline" action="${carlos:forHtmlAttribute(locationAction)}" method="post">
                                                <input type="hidden" name="dispatch" value="moveDown"/>
                                                <input type="hidden" name="id" value="${item.id}"/>
                                                <input type="hidden" name="position" value="${loop.count}"/>
                                                <fmt:message key="admin.appt.location.btn.moveDown" var="moveDownLabel"/>
                                                <button class="btn btn-sm btn-outline-secondary" type="submit" title="${carlos:forHtmlAttribute(moveDownLabel)}"
                                                        aria-label="${carlos:forHtmlAttribute(moveDownLabel)}" aria-describedby="row-${item.id}"${loop.index == activeLocationCount - 1 ? ' disabled' : ''}><i class="fa-solid fa-chevron-down" aria-hidden="true"></i></button>
                                            </form>
                                        </c:if>
                                        <c:if test="${canDeactivate}">
                                            <form class="d-inline" action="${carlos:forHtmlAttribute(locationAction)}" method="post"
                                                  data-confirm="${carlos:forHtmlAttribute(activeLocationCount == 1 ? confirmLastDisable : confirmDisable)}">
                                                <input type="hidden" name="dispatch" value="deactivate"/>
                                                <input type="hidden" name="id" value="${item.id}"/>
                                                <button class="btn btn-sm btn-outline-danger" type="submit" aria-describedby="row-${item.id}"><fmt:message key="admin.appt.location.btn.disable"/></button>
                                            </form>
                                        </c:if>
                                    </c:when>
                                    <c:otherwise>
                                        <form class="d-inline" action="${carlos:forHtmlAttribute(locationAction)}" method="post">
                                            <input type="hidden" name="dispatch" value="restore"/>
                                            <input type="hidden" name="id" value="${item.id}"/>
                                            <button class="btn btn-sm btn-outline-success" type="submit" aria-describedby="row-${item.id}"><fmt:message key="admin.appt.location.btn.enable"/></button>
                                        </form>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </c:if>
                    </tr>
                </c:forEach>
                </tbody>
            </table>
        </div>
        <p class="text-body-secondary small"><fmt:message key="admin.appt.location.msg.renameNote"/></p>
    </c:otherwise>
</c:choose>

<%-- Disabling takes the location out of every booking screen, so it asks first (form[data-confirm]). --%>
<script src="${carlos:forHtmlAttribute(ctx)}/js/appointmentSettingsConfirm.js"></script>
<script>
    (function () {
        // The icon picker shows the chosen icon before it is saved.
        document.querySelectorAll('select.location-icon').forEach(function (select) {
            var preview = select.parentElement.querySelector('.location-icon-preview');
            if (!preview) {
                return;
            }
            select.addEventListener('change', function () {
                var current = preview.dataset.current || preview.dataset.defaultIcon;
                preview.classList.remove(current);
                var chosen = select.value || preview.dataset.defaultIcon;
                preview.classList.add(chosen);
                preview.dataset.current = chosen;
            });
        });
    })();
</script>
</body>
</html>
