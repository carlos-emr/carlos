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
    EFormMissingContent.jsp — informed approval for an incomplete eForm render.

    Purpose:
      Shown by Fax2Action.prepareFax when required resources, layout, signature content, or timer
      behavior could not be represented. The page shows sanitized issue categories before the
      clinician may approve the exact incomplete render.

    Behaviour:
      The approval action submits a one-time exact-issue capability and re-runs prepareFax. "Cancel"
      returns to the prior page. Security and renderer-integrity failures remain non-overridable.

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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
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
            Faxing an incomplete clinical document is your decision. Consider cancelling and correcting
            the eForm when any required content or behavior is missing.
        </p>
        <ul class="small">
            <li>Failed content resources: <carlos:encode value="${failedContentResources}"/></li>
            <li>Excluded visible elements: <carlos:encode value="${excludedContentElements}"/></li>
            <li>Signature missing: <carlos:encode value="${signatureMissing}"/></li>
            <li>Timer compatibility failed: <carlos:encode value="${timerCompatibilityFailure}"/></li>
        </ul>
        <div class="d-flex gap-2 mt-3">
            <form method="post" action="${pageContext.request.contextPath}/fax/faxAction">
                <input type="hidden" name="method" value="prepareFax">
                <input type="hidden" name="transactionType" value="<carlos:encode value="${transactionType}" context="htmlAttribute"/>">
                <input type="hidden" name="transactionId" value="<carlos:encode value="${transactionId}" context="htmlAttribute"/>">
                <input type="hidden" name="demographicNo" value="<carlos:encode value="${demographicNo}" context="htmlAttribute"/>">
                <input type="hidden" name="recipient" value="<carlos:encode value="${recipient}" context="htmlAttribute"/>">
                <input type="hidden" name="recipientFaxNumber" value="<carlos:encode value="${recipientFaxNumber}" context="htmlAttribute"/>">
                <input type="hidden" name="letterheadFax" value="<carlos:encode value="${letterheadFax}" context="htmlAttribute"/>">
                <input type="hidden" name="renderApproval" value="<carlos:encode value="${renderApproval}" context="htmlAttribute"/>">
                <button type="submit" class="btn btn-warning">Approve listed issues and fax</button>
            </form>
            <button type="button" class="btn btn-secondary" onclick="history.back();">Cancel</button>
        </div>
    </div>
</div>
</body>
</html>
