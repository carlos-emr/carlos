<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Check message delivery</title>
    <c:url var="bootstrapUrl" value="/library/bootstrap/5.3.8/css/bootstrap.min.css"/>
    <link rel="stylesheet" href="<c:out value='${bootstrapUrl}'/>">
</head>
<body>
<main class="container py-4">
    <h1>Check message delivery</h1>
    <p class="alert alert-warning" role="alert"><c:out value="${requestScope.messageSubmissionError}"/></p>
    <c:url var="sentMessagesUrl" value="/messenger/DisplayMessages">
        <c:param name="boxType" value="1"/>
    </c:url>
    <a class="btn btn-primary" href="<c:out value='${sentMessagesUrl}'/>">Check Sent Messages</a>
</main>
</body>
</html>
