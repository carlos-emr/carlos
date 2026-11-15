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
    ticklerEditStaleRefused.jsp - Refused stale Edit Tickler response.

    Purpose:
    Render the "staleRefused" result inside ticklerEdit.jsp's hidden iframe. The
    edit form carries the version of the tickler it was rendered from
    (TicklerEditVersion). When another user completed, deleted, reassigned or
    commented on the task after the popup was opened, EditTickler2Action writes
    nothing and sets HTTP 409 before dispatching this result.

    Features:
    Provides an encoded, localized message and the refusal sentinel
    (#tickler-edit-stale) recognized by the parent editor, which shows the
    message and keeps what was typed on screen so it can be copied.

    Parameters:
    None. The message comes from the resource bundle; submitted values stay in
    the parent form and are not rendered in this response.

    @since 2026-11-12
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LocaleUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<% pageContext.setAttribute("refusalLocale", LocaleUtils.resolveBundleLocale(request)); %>
<fmt:setLocale value="${refusalLocale}"/>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="tickler.ticklerEdit.stale.error" var="staleError"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(refusalLocale.toLanguageTag())}">
<head><title></title></head>
<body>
<p id="tickler-edit-stale"><carlos:encode value="${staleError}"/></p>
</body>
</html>
