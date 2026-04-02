<%--

    Copyright (c) 2008-2012 Indivica Inc.
    
    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".
    

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01//EN"
"http://www.w3.org/TR/html4/strict.dtd">
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.AttachDocPopup.title"/></title>

        <% boolean linkIncluded = StringUtils.isNotEmpty(request.getParameter("link")); %>
        <script type="text/javascript">
            function init() {
                document.getElementById("image").src = decodeURIComponent("<%= Encode.forJavaScript(request.getParameter("url")) %>");
                <% if (linkIncluded) { %>
                document.getElementById("link").href = decodeURIComponent("<%= Encode.forJavaScript(request.getParameter("link")) %>");
                <% } %>
            }
        </script>
    </head>
    <body onload="init()">
    <% if (linkIncluded) { %>
    <a id="link" href="#" style="border:0;" target="_blank">
        <% } %>
        <img id="image" src="" style="border:0;"/>
        <% if (linkIncluded) { %>
    </a>
    <% } %>
    </body>
</html>
