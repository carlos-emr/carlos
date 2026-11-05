<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License.

    The "conflict" result of DemographicUpdate2Action (issue #4225).

    A Master Record edit form carries the version of the record it was rendered
    from (DemographicEditVersion). When another user saved the patient after it
    was opened, Update Record is refused with HTTP 409 instead of putting back
    every field that user changed, and nothing is written. This page keeps the
    refused entries:

      - a table of every field whose refused value differs from the record as it
        is now (the user's entry beside the current value), so nothing typed is
        lost and the newer values can be reviewed;
      - a link that opens the current record in a new window, to re-apply the
        intended change to it;
      - a form that posts the same entries again, deliberately, bound to the
        current version, after a confirmation that the "Your entry" values will
        replace the current ones.

    Request attributes: demographicNo, conflictDifferences
    (List<DemographicEditVersion.Difference>), conflictVersion (the record's
    current version) and conflictDraft (List<String[]> name/value pairs of the
    refused form, without its CSRF token and version). Every value is encoded
    for its context. The response is no-store.

    @since 2026-10-15
--%><%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="https://owasp.org/www-project-csrfguard/Owasp.CsrfGuard.tld" prefix="csrf" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="demographic.editConflict.confirmSaveMine" var="confirmSaveMine"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css">
    <title><fmt:message key="demographic.editConflict.title"/></title>
    <style>
        .conflict-value { white-space: pre-wrap; overflow-wrap: anywhere; }
    </style>
</head>
<body class="p-4">
<div class="alert alert-warning" role="alert" id="demographicUpdateConflict">
    <h5 class="alert-heading"><fmt:message key="demographic.editConflict.title"/></h5>
    <p class="mb-0"><fmt:message key="demographic.editConflict.message"/></p>
</div>

<c:choose>
    <c:when test="${not empty conflictDifferences}">
        <p><fmt:message key="demographic.editConflict.differences"/></p>
        <table class="table table-sm table-bordered" id="demographicConflictDifferences">
            <thead>
            <tr>
                <th scope="col"><fmt:message key="demographic.editConflict.colField"/></th>
                <th scope="col"><fmt:message key="demographic.editConflict.colYours"/></th>
                <th scope="col"><fmt:message key="demographic.editConflict.colCurrent"/></th>
            </tr>
            </thead>
            <tbody>
            <c:forEach var="difference" items="${conflictDifferences}">
                <tr>
                    <th scope="row">
                        <c:choose>
                            <c:when test="${not empty difference.labelKey}"><fmt:message key="${difference.labelKey}"/></c:when>
                            <c:otherwise><carlos:encode value="${difference.name}"/></c:otherwise>
                        </c:choose>
                    </th>
                    <td class="conflict-value yours"><carlos:encode value="${difference.yours}"/></td>
                    <td class="conflict-value current"><carlos:encode value="${difference.current}"/></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </c:when>
    <c:otherwise>
        <p><fmt:message key="demographic.editConflict.noDifferences"/></p>
    </c:otherwise>
</c:choose>

<div class="d-flex flex-wrap gap-2">
    <a class="btn btn-primary" id="openCurrentRecord" target="_blank" rel="noopener"
       href="${pageContext.request.contextPath}/demographic/DemographicEdit?demographic_no=${carlos:forUriComponent(demographicNo)}&amp;displaymode=edit&amp;dboperation=search_detail">
        <fmt:message key="demographic.editConflict.openCurrent"/>
    </a>
    <form method="post" id="saveMyEntries" class="m-0"
          action="${pageContext.request.contextPath}/demographic/DemographicUpdate"
          onsubmit="return confirm('${carlos:forJavaScriptAttribute(confirmSaveMine)}');">
        <input type="hidden" name="<csrf:tokenname/>" value="<csrf:tokenvalue/>"/>
        <c:forEach var="entry" items="${conflictDraft}">
            <input type="hidden" name="<carlos:encode value='${entry[0]}' context='htmlAttribute'/>"
                   value="<carlos:encode value='${entry[1]}' context='htmlAttribute'/>"/>
        </c:forEach>
        <input type="hidden" name="demographicVersion" value="<carlos:encode value='${conflictVersion}' context='htmlAttribute'/>"/>
        <button type="submit" class="btn btn-outline-danger"><fmt:message key="demographic.editConflict.saveMine"/></button>
    </form>
</div>
</body>
</html>
