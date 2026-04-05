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

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%--
    dbTicklerAdd.jsp (WEB-INF view)

    Purpose:
    View fragment for DbTicklerAdd2Action. Renders hidden sentinel elements
    read by ticklerAdd.jsp iframe.onload to confirm save status.

    Request Attributes (set by DbTicklerAdd2Action):
    - rowsAffected (Boolean): true if the tickler was saved successfully
    - ticklerLinkFailed (Boolean): true if the document link save failed
    - writeToEncounterFailed (Boolean): true if the encounter note write failed

    @since 2006-01-01 (original OSCAR implementation)
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    Boolean rowsAffected = (Boolean) request.getAttribute("rowsAffected");
    Boolean ticklerLinkFailed = (Boolean) request.getAttribute("ticklerLinkFailed");
    Boolean writeToEncounterFailed = (Boolean) request.getAttribute("writeToEncounterFailed");
%>
<% if (Boolean.TRUE.equals(rowsAffected)) { %>
<%-- ticklerAdd.jsp reads this element to confirm the save succeeded before closing --%>
<span id="tickler-save-ok" style="display:none;"></span>
<% if (Boolean.TRUE.equals(ticklerLinkFailed)) { %>
<span id="tickler-save-ok-link-failed" style="display:none;"></span>
<% } %>
<% if (Boolean.TRUE.equals(writeToEncounterFailed)) { %>
<span id="tickler-write-encounter-failed" style="display:none;"></span>
<% } %>
<% } %>
