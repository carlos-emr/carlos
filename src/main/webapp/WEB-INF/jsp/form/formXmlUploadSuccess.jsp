<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

    This software is published under the GPL GNU General Public License.
    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>

<%--
/**
 * Form XML Import Confirmation
 *
 * Confirmation page shown after a successful form/eForm XML archive import
 * (see FrmXmlUpload2Action). Informs the admin that the import completed and
 * automatically returns them to Administration after a short delay.
 *
 * Main Features:
 * - Success message with a manual "Return to Administration now" link
 * - Client-side redirect to /administration after 3 seconds (setTimeout, not
 *   a meta refresh, so the delay is not silently forced on assistive tech)
 *
 * Request Attributes: none; this page renders unconditionally on the
 * "success" result of the /form/xmlUpload action.
 *
 * @since 2026-08-05
 */
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<!doctype html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <title><fmt:message key="form.xmlUploadSuccess.title"/></title>
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-body-tertiary">
    <main class="container py-5">
        <div class="alert alert-success">
            <h1 class="h3"><fmt:message key="form.xmlUploadSuccess.heading"/></h1>
            <output class="d-block"><fmt:message key="form.xmlUploadSuccess.message"/></output>
            <a class="btn btn-primary" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/administration"><fmt:message key="form.xmlUploadSuccess.returnLink"/></a>
        </div>
    </main>
    <script>
        setTimeout(function () {
            window.location.href = "${carlos:forJavaScript(pageContext.request.contextPath)}/administration";
        }, 3000);
    </script>
</body>
</html>
