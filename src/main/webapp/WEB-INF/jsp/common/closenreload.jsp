<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%@ taglib prefix="e" uri="owasp.encoder.jakarta.advanced" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
</head>
<body>
<c:set var="parentAjaxId" value="${not empty param.parentAjaxId ? param.parentAjaxId : parentAjaxId}" />
<center>Closing Window, Please Wait....</center>
<script type="text/javascript" language="javascript">
    const parentAjaxId = "<carlos:encode value='${parentAjaxId}' context="javaScript"/>";
    if (window.opener && !window.opener.closed) {
        if (parentAjaxId !== "") {
            window.opener.reloadNav(parentAjaxId);
        } else {
            window.opener.location.reload();
        }
        window.opener.focus();
    }
    window.close();
</script>
Click
<a href="javascript:window.close();">here</a>
to close this window.
</body>
</html>