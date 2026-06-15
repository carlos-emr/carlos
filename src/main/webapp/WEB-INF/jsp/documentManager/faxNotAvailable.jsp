<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
    faxNotAvailable.jsp
    Shown when a document cannot be faxed (no fax accounts, wrong content type, missing file, etc.).
    The "message" request attribute carries the human-readable reason set by FaxDocument2Action.

    @since 2026-06
--%>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8"/>
    <title>Fax Not Available</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
</head>
<body class="p-4">
<div class="alert alert-warning" role="alert">
    <h5 class="alert-heading">Cannot Fax Document</h5>
    <p class="mb-2"><carlos:encode value="${requestScope.message}" context="html"/></p>
    <hr/>
    <button type="button" class="btn btn-secondary btn-sm" onclick="window.close()">Close</button>
</div>
</body>
</html>
