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
    emailComposeDraftExpired.jsp — the eForm email window whose draft is gone.

    Purpose:
      Shown by EmailCompose2Action.prepareComposeEFormMailer, with HTTP 410, when the window's
      one-time draft key finds no usable draft: it is missing, unknown, already used, or was dropped
      for a newer save (#4101). The eForm error page needs an eForm to show anything, so without one
      this window used to stay blank.

    Behaviour:
      Static text from the bundle and a Close button. It names no patient, eForm or draft key.

    Access control:
      Reached only as an internal forward from EmailCompose2Action, which requires the _email write
      privilege before it looks for a draft.

    @since 2026-10-08
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="email.compose.draftExpired.title"/></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <style>
        body { padding: 2rem; }
        .draft-expired-card { max-width: 640px; margin: 0 auto; }
    </style>
</head>
<body>
<div class="card draft-expired-card">
    <div class="card-header bg-warning-subtle">
        <h5 class="mb-0"><fmt:message key="email.compose.draftExpired.title"/></h5>
    </div>
    <div class="card-body">
        <p id="draftExpiredMessage" role="alert"><fmt:message key="email.compose.draftExpired.message"/></p>
        <button type="button" class="btn btn-secondary" onclick="window.close()">
            <fmt:message key="global.btnClose"/>
        </button>
    </div>
</div>
</body>
</html>
