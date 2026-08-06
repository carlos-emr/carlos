<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

    This software is published under the GPL GNU General Public License.
    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<!doctype html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="refresh" content="3;url=${pageContext.request.contextPath}/administration">
    <title>Import complete</title>
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-body-tertiary">
    <main class="container py-5">
        <div class="alert alert-success" role="status">
            <h1 class="h3">Import complete</h1>
            <p>Your form data import has completed. You will return to Administration in 3 seconds.</p>
            <a class="btn btn-primary" href="${pageContext.request.contextPath}/administration">Return to Administration now</a>
        </div>
    </main>
</body>
</html>
