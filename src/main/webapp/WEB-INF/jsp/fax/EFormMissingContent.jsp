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
    EFormMissingContent.jsp — "fax anyway?" confirmation for an incomplete eForm render.

    Purpose:
      Shown by Fax2Action.prepareFax when the eForm browser render reported that the form's own
      same-origin content (e.g. a signature or an image served by CARLOS) could not be loaded, so the
      generated PDF would be visually incomplete. Rather than dead-ending, this page informs the
      clinician and lets them choose to fax the incomplete document anyway.

    Behaviour:
      "Fax anyway" re-invokes prepareFax with renderAnyway=true, which tolerates the missing content
      and proceeds to the cover-page preview. "Cancel" returns to the eForm. This override relaxes
      ONLY the missing-content gate; security gates (blocked egress) and a main document that never
      loaded still fail regardless.

    Access control:
      Reached only as an internal forward from Fax2Action.prepareFax, which enforces the _fax READ
      security object before returning this result.

    @since 2026-07-23
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Incomplete eForm — Fax anyway?</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.0/css/bootstrap.min.css">
    <style>
        body { padding: 2rem; }
        .missing-content-card { max-width: 640px; margin: 0 auto; }
    </style>
</head>
<body>
<div class="card missing-content-card">
    <div class="card-header bg-warning-subtle">
        <h5 class="mb-0">Some eForm content could not be loaded</h5>
    </div>
    <div class="card-body">
        <p><carlos:encode value="${missingContentMessage}"/></p>
        <p class="text-muted small">
            Faxing an incomplete clinical document is your decision. If a signature or required image
            is missing, consider cancelling and correcting the eForm first.
        </p>
        <c:set var="faxAnywayUrl">${pageContext.request.contextPath}/fax/faxAction?method=prepareFax&amp;transactionType=${carlos:forUriComponent(transactionType)}&amp;transactionId=${carlos:forUriComponent(transactionId)}&amp;demographicNo=${carlos:forUriComponent(demographicNo)}&amp;recipient=${carlos:forUriComponent(recipient)}&amp;recipientFaxNumber=${carlos:forUriComponent(recipientFaxNumber)}&amp;letterheadFax=${carlos:forUriComponent(letterheadFax)}&amp;renderAnyway=true</c:set>
        <div class="d-flex gap-2 mt-3">
            <a class="btn btn-warning" href="${faxAnywayUrl}">Fax anyway (incomplete)</a>
            <button type="button" class="btn btn-secondary" onclick="history.back();">Cancel</button>
        </div>
    </div>
</div>
</body>
</html>
