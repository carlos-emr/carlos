<%--

    CARLOS EMR - Template Conversion Review Page

    Purpose:
    Temporary review page providing links to all JSP files that have been converted
    from the legacy OSCAR table-based layout to the CARLOS Bootstrap 5 template.
    This page allows reviewers to quickly access and verify each conversion.

    This page should be removed once the conversion review process is complete.

    @since 2026-04-13
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <meta charset="UTF-8">
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <title>Template Conversion Review</title>
</head>
<body>

<div class="container">

    <div id="jsAlertBanner"
         class="alert alert-danger alert-dismissible"
         style="display:none"
         role="alert">
        <span id="jsAlertText"></span>
        <button type="button"
                class="btn-close"
                onclick="this.closest('.alert').style.display='none'"
                aria-label="Close"></button>
    </div>

    <div class="page-header-bar d-flex align-items-center justify-content-between
                py-2 mb-3 border-bottom" id="header">
        <div class="d-flex align-items-center gap-2">
            <i class="fa-solid fa-list-check" aria-hidden="true"></i>
            <span class="fw-semibold">Template Conversion Review</span>
        </div>
        <div class="text-muted small">CARLOS Bootstrap Migration — Batch 1</div>
    </div>

    <div class="bg-light border rounded p-2">

        <div class="alert alert-info small" role="alert">
            <strong>Reviewer instructions:</strong> Click each link to verify that the converted page
            renders correctly. Check for: correct page header, proper layout, no broken forms,
            no JavaScript console errors. Some pages may require login or specific session context.
        </div>

        <h6 class="fw-semibold mb-3">Batch 1 &mdash; Simple Pages (&le; 95 LOC, 1&ndash;2 tables)</h6>

        <table class="table table-sm table-striped">
            <thead>
                <tr>
                    <th>#</th>
                    <th>File</th>
                    <th>Description</th>
                    <th>Original LOC</th>
                    <th>Link</th>
                </tr>
            </thead>
            <tbody>
                <tr>
                    <td>1</td>
                    <td><code>provider/providerColourErr.jsp</code></td>
                    <td>Provider colour set error page</td>
                    <td>64</td>
                    <td>
                        <a href="${pageContext.request.contextPath}/provider/ViewProviderColourErr"
                           target="_blank" class="btn btn-outline-primary btn-sm">Open</a>
                    </td>
                </tr>
                <tr>
                    <td>2</td>
                    <td><code>provider/providerFaxErr.jsp</code></td>
                    <td>Provider fax number error page</td>
                    <td>64</td>
                    <td>
                        <a href="${pageContext.request.contextPath}/provider/ViewProviderFaxErr"
                           target="_blank" class="btn btn-outline-primary btn-sm">Open</a>
                    </td>
                </tr>
                <tr>
                    <td>3</td>
                    <td><code>encounter/TimeOut.jsp</code></td>
                    <td>Encounter save &amp; exit timeout popup</td>
                    <td>80</td>
                    <td>
                        <a href="${pageContext.request.contextPath}/encounter/ViewTimeOut"
                           target="_blank" class="btn btn-outline-primary btn-sm">Open</a>
                    </td>
                </tr>
                <tr>
                    <td>4</td>
                    <td><code>encounter/oscarConsultationRequest/nothingtoPrint.jsp</code></td>
                    <td>Consultation &mdash; nothing to print message</td>
                    <td>92</td>
                    <td>
                        <a href="${pageContext.request.contextPath}/encounter/oscarConsultationRequest/ViewNothingtoPrint"
                           target="_blank" class="btn btn-outline-primary btn-sm">Open</a>
                    </td>
                </tr>
                <tr>
                    <td>5</td>
                    <td><code>encounter/License.jsp</code></td>
                    <td>License display page</td>
                    <td>95</td>
                    <td>
                        <a href="${pageContext.request.contextPath}/encounter/ViewLicense"
                           target="_blank" class="btn btn-outline-primary btn-sm">Open</a>
                    </td>
                </tr>
            </tbody>
        </table>

        <hr>

        <h6 class="fw-semibold mb-3">Conversion Statistics</h6>
        <ul class="list-unstyled small">
            <li><strong>Total legacy MainTable files identified:</strong> 106</li>
            <li><strong>Converted in this batch:</strong> 5</li>
            <li><strong>Remaining:</strong> 101</li>
            <li><strong>Scan report:</strong> <code>docs/maintable-conversion-scan.md</code></li>
            <li><strong>Conversion guide:</strong> <code>docs/oscar-to-carlos-bootstrap-conversion.md</code></li>
        </ul>

    </div>

</div>
</body>
</html>
