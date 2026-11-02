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
    ticklerEditAttachmentsRefused.jsp - Refused Edit Tickler attachment response.

    Purpose:
    Render the dedicated refusal result inside ticklerEdit.jsp's hidden iframe.
    EditTickler2Action sets HTTP 400 before dispatching this result.

    Features:
    Provides an encoded, localized message and the refusal sentinel recognized
    by the parent editor, which keeps the entered fields and attachment selection
    available for correction and retry.

    Parameters:
    None. The message comes from the resource bundle; submitted values stay in
    the parent form and are not rendered in this response.

    @since 2026-11-02
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="tickler.ticklerEdit.attachments.error" var="attachmentError"/>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.toLanguageTag())}">
<head><title></title></head>
<body>
<p id="tickler-edit-attachments-refused"><carlos:encode value="${attachmentError}"/></p>
</body>
</html>
