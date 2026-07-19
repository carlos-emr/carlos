<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%@ taglib prefix="e" uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
<head>
</head>
<body>
<c:set var="parentAjaxId" value="${not empty param.parentAjaxId ? param.parentAjaxId : parentAjaxId}" />
<center>Closing Window, Please Wait....</center>
<script type="text/javascript" language="javascript">
    const parentAjaxId = "<e:forHtml value="${parentAjaxId}" />";
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