<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
     Successful form Save redirect: refresh the owning patient's panels without
     closing this form or disturbing the encounter draft, then redisplay the saved
     record. The authenticated forwardname action validates the form and supplies
     savedFormUrl; it deliberately omits saved=true to prevent a redirect loop.
--%>
<%@ taglib uri="carlos" prefix="carlos" %>
<!doctype html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}">
<head>
    <meta charset="UTF-8"/>
    <title>CARLOS - Saved form</title>
    <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/form/form-completion.js"></script>
</head>
<body>
<script>
    refreshEncounterForms(window, "${carlos:forJavaScript(param.demographic_no)}");
    window.location.replace("${carlos:forJavaScript(requestScope.savedFormUrl)}");
</script>
<noscript><a href="${carlos:forHtmlAttribute(requestScope.savedFormUrl)}">Return to the saved form</a></noscript>
</body>
</html>
