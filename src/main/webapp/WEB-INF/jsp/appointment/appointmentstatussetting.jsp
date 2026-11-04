<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%--
    Appointment Settings, Statuses tab

    Purpose:
      Lists the appointment statuses and edits each one inline: description, background colour and
      icon each save from their own small form, so saving one field never rewrites another (a
      legacy non-hexadecimal colour stays until a colour is saved on purpose, #3381).

    Features:
      - Style edits for every status, including the core statuses seeded with editable=0: in
        CARLOS that flag only gates enabling and disabling (#3888).
      - Enable/Disable for editable statuses; disabling asks for confirmation first, and
        cancelling sends no request. Reset asks too, because it cannot be undone.
      - Every form posts to appointment/apptStatusSetting, which answers 405 to GET/HEAD for a
        change. CSRFGuard adds the token to these same-origin POST forms.

    Request attributes (AppointmentStatus2Action):
      allStatus            - List<AppointmentStatus> every status
      iconSet              - List<String> the icon images a status may use, under /images
      descriptionMaxLength - Integer the description column width
      useStatus            - String code of a disabled status still used by appointments, if any

    @since 2026-08-07
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%-- One locale for the bundle and for <html lang>, as the booking screens resolve it. --%>
<% pageContext.setAttribute("pageLocale", io.github.carlos_emr.carlos.utility.LocaleUtils.resolveBundleLocale(request)); %>
<fmt:setLocale value="${pageLocale}"/>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/struts-tags" prefix="s" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.schedule" rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logoutPage");%>
</security:oscarSec>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="statusAction" value="${ctx}/appointment/apptStatusSetting"/>
<c:set var="activeTab" value="status"/>
<fmt:message key="admin.appt.status.mgr.msg.confirmDisable" var="confirmDisable"/>
<fmt:message key="admin.appt.status.mgr.msg.confirmReset" var="confirmReset"/>
<fmt:message key="admin.appt.status.mgr.label.lockedTitle" var="lockedTitle"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageLocale.language)}">
<head>
    <meta charset="UTF-8">
    <link rel="icon" href="${carlos:forHtmlAttribute(ctx)}/images/favicon.ico"/>
    <title><fmt:message key="admin.appt.status.mgr.title"/></title>
    <%@ include file="/WEB-INF/jspf/bootstrap-css.jspf" %>
    <style>
        .status-swatch { display: inline-block; width: 1.25rem; height: 1.25rem; vertical-align: middle;
            border: 1px solid #adb5bd; border-radius: .2rem; }
        .status-desc { min-width: 10rem; }
        .status-colour { width: 3rem; padding: .1rem; }
        .status-icon-preview { width: 16px; height: 16px; object-fit: contain; }
    </style>
</head>
<body class="p-3">
<%@ include file="/WEB-INF/jsp/appointment/appointmentSettingsNav.jspf" %>

<div class="d-flex align-items-center mb-3">
    <h1 class="h5 mb-0 me-auto"><fmt:message key="admin.appt.status.mgr.title"/></h1>
    <c:if test="${canChange}">
    <form action="${carlos:forHtmlAttribute(statusAction)}" method="post" data-confirm="${carlos:forHtmlAttribute(confirmReset)}">
        <input type="hidden" name="dispatch" value="reset"/>
        <button class="btn btn-sm btn-outline-secondary" type="submit"><fmt:message key="admin.appt.status.mgr.label.reset"/></button>
    </form>
    </c:if>
</div>

<s:if test="hasActionErrors()">
    <div class="alert alert-danger" role="alert"><s:actionerror/></div>
</s:if>
<s:if test="hasActionMessages()">
    <div class="alert alert-success" role="status"><s:actionmessage/></div>
</s:if>
<c:if test="${not empty useStatus}">
    <div class="alert alert-warning" role="alert">
        <fmt:message key="admin.appt.status.mgr.msg.usedBefore">
            <fmt:param value="${carlos:forHtml(useStatus)}"/>
        </fmt:message>
    </div>
</c:if>

<%-- A disabled fieldset disables every control inside it, whichever form each belongs to. --%>
<fieldset class="table-responsive"${canChange ? '' : ' disabled'}>
    <table id="statusTable" class="table table-sm table-striped align-middle"
           data-icon-base="${carlos:forHtmlAttribute(ctx)}/images/">
        <thead>
        <tr>
            <th scope="col"><fmt:message key="admin.appt.status.mgr.label.status"/></th>
            <th scope="col"><fmt:message key="admin.appt.status.mgr.label.desc"/></th>
            <th scope="col"><fmt:message key="admin.appt.status.mgr.label.color"/></th>
            <th scope="col"><fmt:message key="admin.appt.status.mgr.label.icon"/></th>
            <th scope="col"><fmt:message key="admin.appt.status.mgr.label.enable"/></th>
            <th scope="col"><fmt:message key="admin.appt.status.mgr.label.active"/></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${allStatus}" var="apptStatus">
            <c:set var="statusColour" value="${apptStatus.color}"/>
            <%-- A legacy colour (a name saved by an older release) cannot be shown by the picker. --%>
            <c:set var="legacyColour" value="${empty statusColour or not statusColour.matches('#[0-9A-Fa-f]{6}')}"/>
            <tr id="status-${apptStatus.id}" data-status-code="${carlos:forHtmlAttribute(apptStatus.status)}">
                <%-- The row header (code and description) gives each row's repeated controls their context. --%>
                <th scope="row" class="text-nowrap" id="status-row-${apptStatus.id}"><code><carlos:encode value="${apptStatus.status}"/></code><span
                        class="visually-hidden"> <carlos:encode value="${apptStatus.description}"/></span></th>
                <td>
                    <form class="d-flex gap-1" action="${carlos:forHtmlAttribute(statusAction)}" method="post">
                        <input type="hidden" name="dispatch" value="updateDescription"/>
                        <input type="hidden" name="id" value="${apptStatus.id}"/>
                        <label class="visually-hidden" for="desc-${apptStatus.id}"><fmt:message key="admin.appt.status.mgr.label.desc"/></label>
                        <input class="form-control form-control-sm status-desc" type="text" id="desc-${apptStatus.id}" name="apptDesc"
                               value="<carlos:encode value='${apptStatus.description}' context='htmlAttribute'/>"
                               maxlength="${descriptionMaxLength}" aria-describedby="status-row-${apptStatus.id}" required>
                        <button class="btn btn-sm btn-outline-primary" type="submit" aria-describedby="status-row-${apptStatus.id}"><fmt:message key="global.btnSave"/></button>
                    </form>
                </td>
                <td>
                    <form class="d-flex align-items-center gap-1" action="${carlos:forHtmlAttribute(statusAction)}" method="post">
                        <input type="hidden" name="dispatch" value="updateColour"/>
                        <input type="hidden" name="id" value="${apptStatus.id}"/>
                        <%-- Only a stored #rrggbb value is ever written into a style attribute. --%>
                        <span class="status-swatch"<c:if test="${not legacyColour}"> style="background-color: ${carlos:forCssString(statusColour)}"</c:if>></span>
                        <label class="visually-hidden" for="colour-${apptStatus.id}"><fmt:message key="admin.appt.status.mgr.label.color"/></label>
                        <input class="form-control form-control-sm form-control-color status-colour" type="color" id="colour-${apptStatus.id}"
                               name="apptColor" value="${legacyColour ? '#ffffff' : carlos:forHtmlAttribute(statusColour)}"
                               aria-describedby="status-row-${apptStatus.id}">
                        <button class="btn btn-sm btn-outline-primary" type="submit" aria-describedby="status-row-${apptStatus.id}"><fmt:message key="global.btnSave"/></button>
                    </form>
                    <c:if test="${legacyColour}">
                        <div class="form-text text-warning-emphasis">
                            <fmt:message key="admin.appt.status.mgr.msg.legacyColour">
                                <fmt:param value="${carlos:forHtml(statusColour)}"/>
                            </fmt:message>
                        </div>
                    </c:if>
                </td>
                <td>
                    <form class="d-flex align-items-center gap-1" action="${carlos:forHtmlAttribute(statusAction)}" method="post">
                        <input type="hidden" name="dispatch" value="updateIcon"/>
                        <input type="hidden" name="id" value="${apptStatus.id}"/>
                        <%-- No icon saved: no image request until one is chosen. --%>
                        <img class="status-icon-preview" alt=""<c:choose><c:when test="${empty apptStatus.icon}"> hidden</c:when><c:otherwise> src="${carlos:forHtmlAttribute(ctx)}/images/${carlos:forUriComponent(apptStatus.icon)}"</c:otherwise></c:choose>>
                        <label class="visually-hidden" for="icon-${apptStatus.id}"><fmt:message key="admin.appt.status.mgr.label.icon"/></label>
                        <select class="form-select form-select-sm status-icon" id="icon-${apptStatus.id}" name="apptIcon"
                                aria-describedby="status-row-${apptStatus.id}">
                            <c:set var="iconListed" value="false"/>
                            <c:forEach items="${iconSet}" var="icon">
                                <c:if test="${icon == apptStatus.icon}"><c:set var="iconListed" value="true"/></c:if>
                                <option value="${carlos:forHtmlAttribute(icon)}"${icon == apptStatus.icon ? ' selected' : ''}><carlos:encode value="${icon}"/></option>
                            </c:forEach>
                            <%-- A stored icon outside the set stays shown; it cannot be chosen again once changed. --%>
                            <c:if test="${not iconListed}">
                                <option value="" selected disabled>
                                    <fmt:message key="admin.appt.status.mgr.label.iconNotListed">
                                        <fmt:param value="${carlos:forHtml(apptStatus.icon)}"/>
                                    </fmt:message>
                                </option>
                            </c:if>
                        </select>
                        <%-- An unlisted stored icon is a disabled option, which a form does not post: Save waits for a listed icon. --%>
                        <button class="btn btn-sm btn-outline-primary" type="submit" aria-describedby="status-row-${apptStatus.id}"${iconListed ? '' : ' disabled'}><fmt:message key="global.btnSave"/></button>
                    </form>
                </td>
                <td class="text-nowrap"><fmt:message key="${apptStatus.active > 0 ? 'global.yes' : 'global.no'}"/></td>
                <td class="text-nowrap">
                    <c:choose>
                        <c:when test="${apptStatus.editable == 1}">
                            <form action="${carlos:forHtmlAttribute(statusAction)}" method="post"<c:if test="${apptStatus.active > 0}"> data-confirm="${carlos:forHtmlAttribute(confirmDisable)}"</c:if>>
                                <input type="hidden" name="dispatch" value="changestatus"/>
                                <input type="hidden" name="id" value="${apptStatus.id}"/>
                                <input type="hidden" name="active" value="${apptStatus.active > 0 ? 0 : 1}"/>
                                <button class="btn btn-sm ${apptStatus.active > 0 ? 'btn-outline-danger' : 'btn-outline-success'}" type="submit"
                                        aria-describedby="status-row-${apptStatus.id}">
                                    <fmt:message key="${apptStatus.active > 0 ? 'admin.appt.status.mgr.label.disable' : 'admin.appt.status.mgr.label.enableAction'}"/>
                                </button>
                            </form>
                        </c:when>
                        <c:otherwise>
                            <span class="badge text-bg-secondary" title="${carlos:forHtmlAttribute(lockedTitle)}"><fmt:message key="admin.appt.status.mgr.label.locked"/></span>
                        </c:otherwise>
                    </c:choose>
                </td>
            </tr>
        </c:forEach>
        </tbody>
    </table>
</fieldset>

<script>
    (function () {
        // Reset and Disable cannot be undone from the page, so each asks first; cancelling sends nothing.
        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (event) {
                if (!window.confirm(form.dataset.confirm)) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                }
            });
        });

        // Preview the chosen icon; the select only offers the server's whitelist of file names.
        const base = document.getElementById('statusTable').dataset.iconBase;
        document.querySelectorAll('select.status-icon').forEach(function (select) {
            select.addEventListener('change', function () {
                const preview = select.form.querySelector('.status-icon-preview');
                if (preview && select.value) {
                    preview.src = base + encodeURIComponent(select.value);
                    preview.hidden = false;
                }
                const save = select.form.querySelector('button[type="submit"]');
                if (save && select.value) {
                    save.disabled = false;
                }
            });
        });
    })();
</script>
</body>
</html>
