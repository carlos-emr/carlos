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
    Appointment Location List dropdown for the booking screens.

    Purpose:
      Shown in place of the free-text location when AppointmentLocationList.isLocationMode() is true
      (the list has an active item and no site setup is configured). It posts locationCode, plus a
      hidden location field holding the legacy free-text location, if any;
      AppointmentLocationResolver.applyPostedLocation saves them as the item's id and label.

    Options:
      - "Not specified" (value ""), which saves no location.
      - A legacy option (value "legacy", data-legacy) when the appointment has location text that
        names no item, so saving it unchanged keeps that text.
      - The items, in booking order. A location deactivated since this booking is still offered,
        marked data-inactive, so saving unchanged keeps it. js/appointmentLocationSelect.js matches
        options by their text, so labels are written without surrounding whitespace.

    Attributes:
      choices     List<LookupListItem> the items to offer, in display order
      selected    String the value to select (see AppointmentLocationList.Choice), or blank
      legacy      String the legacy location text to offer, or blank
      selectId    String the select's id
      cssClass    String the select's classes
      labelledBy  String the id of the visible label, or blank

    @since 2026-11-04
--%>
<%@ tag body-content="empty" pageEncoding="UTF-8" %>
<%@ attribute name="choices" required="true" type="java.util.List" %>
<%@ attribute name="selected" required="false" %>
<%@ attribute name="legacy" required="false" %>
<%@ attribute name="selectId" required="false" %>
<%@ attribute name="cssClass" required="false" %>
<%@ attribute name="labelledBy" required="false" %>
<%@ tag import="io.github.carlos_emr.carlos.appt.location.AppointmentLocationList" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%-- A tag file cannot see the page's fmt:setLocale, so it picks the bundle the same way the page does. --%>
<fmt:setLocale value="<%= io.github.carlos_emr.carlos.utility.LocaleUtils.resolveBundleLocale(request) %>"/>
<fmt:setBundle basename="oscarResources"/>
<c:set var="legacyValue" value="<%= AppointmentLocationList.LEGACY_VALUE %>"/>
<select name="locationCode" id="${carlos:forHtmlAttribute(empty selectId ? 'locationCode' : selectId)}"
        class="${carlos:forHtmlAttribute(cssClass)}"<c:if test="${not empty labelledBy}"> aria-labelledby="${carlos:forHtmlAttribute(labelledBy)}"</c:if>
        data-appointment-location-list><option value=""><fmt:message key="appointment.location.notSpecified"/></option><c:if test="${not empty legacy}"><option value="${carlos:forHtmlAttribute(legacyValue)}" data-legacy${legacyValue eq selected ? ' selected' : ''}><fmt:message key="appointment.location.legacyOption"><fmt:param value="${carlos:forHtml(legacy)}"/></fmt:message></option></c:if><c:forEach items="${choices}" var="item"><%-- A body-set var is a String, so the comparison is text, like the posted value. --%><c:set var="optionValue">${item.id}</c:set><option value="${carlos:forHtmlAttribute(optionValue)}"${optionValue eq selected ? ' selected' : ''}${item.active ? '' : ' data-inactive'}><c:choose><c:when test="${item.active}"><carlos:encode value="${item.label}"/></c:when><c:otherwise><fmt:message key="appointment.location.inactiveOption"><fmt:param value="${carlos:forHtml(item.label)}"/></fmt:message></c:otherwise></c:choose></option></c:forEach></select>
<input type="hidden" name="location" value="${carlos:forHtmlAttribute(legacy)}">
