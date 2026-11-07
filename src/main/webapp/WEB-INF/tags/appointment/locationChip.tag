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
    Appointment location chip: a small marker in the location's colour with its icon, naming the
    location on hover and to screen readers. Renders nothing when item is null.

    The colour and icon come from AppointmentLocationStyle, which only ever returns a #rrggbb colour
    and a whitelisted Font Awesome class (falling back to the defaults for anything else stored), so
    neither can carry markup into the style or class attribute. Needs css/fontawesome-all.min.css,
    which the schedule already loads. Uses inline styles only, so it does not depend on Bootstrap.

    Attributes:
      item       LookupListItem the location, or null
      showLabel  Boolean true to also print the location's name inside the chip
      label      String the name to give it, or blank for the item's current name. The schedule
                 passes the name the appointment was booked with, which a later rename does not change.

    @since 2026-11-04
--%>
<%@ tag body-content="empty" pageEncoding="UTF-8" %>
<%@ attribute name="item" required="false" type="io.github.carlos_emr.carlos.commn.model.LookupListItem" %>
<%@ attribute name="showLabel" required="false" type="java.lang.Boolean" %>
<%@ attribute name="label" required="false" %>
<%@ tag import="io.github.carlos_emr.carlos.appt.location.AppointmentLocationStyle" %>
<%@ tag import="io.github.carlos_emr.carlos.commn.model.LookupListItem" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<c:if test="${not empty item}">
    <c:set var="chipColour" value='<%= AppointmentLocationStyle.chipColour((LookupListItem) jspContext.getAttribute("item")) %>'/>
    <c:set var="chipTextColour" value='<%= AppointmentLocationStyle.chipTextColour((LookupListItem) jspContext.getAttribute("item")) %>'/>
    <c:set var="chipIcon" value='<%= AppointmentLocationStyle.chipIcon((LookupListItem) jspContext.getAttribute("item")) %>'/>
    <c:set var="chipLabel" value="${empty label ? item.label : label}"/>
    <span class="appt-location-chip" role="img" title="${carlos:forHtmlAttribute(chipLabel)}" aria-label="${carlos:forHtmlAttribute(chipLabel)}"
          data-location-code="${carlos:forHtmlAttribute(item.id)}"
          style="display:inline-block;padding:0 3px;border-radius:3px;line-height:1.3;white-space:nowrap;background-color:${carlos:forCssString(chipColour)};color:${carlos:forCssString(chipTextColour)};"><i
            class="fa-solid ${carlos:forHtmlAttribute(chipIcon)}" aria-hidden="true"></i><c:if test="${showLabel}"> <carlos:encode value="${chipLabel}"/></c:if></span>
</c:if>
