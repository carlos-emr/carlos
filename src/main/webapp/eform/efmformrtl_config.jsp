<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>
<%@ page
        import="java.util.*,ca.openosp.openo.demographic.data.*,ca.openosp.openo.prevention.*,ca.openosp.openo.providers.data.*,ca.openosp.openo.util.*,ca.openosp.openo.report.data.*,ca.openosp.openo.prevention.pageUtil.*,ca.openosp.openo.demographic.pageUtil.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils,ca.openosp.openo.commn.dao.EFormDao" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String userRole = (String) session.getAttribute("userrole");
    String status = (String) request.getAttribute("status");
    EFormDao efd = (EFormDao) SpringUtils.getBean(EFormDao.class);
%>

<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>

        <title>Rich Text Letter Settings</title>
        <script src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>


    </head>

    <body>

    <% if (!userRole.toLowerCase().contains("admin")) { %>
    <p>
    <h2>Sorry! Only administrators can change these settings..</h2>
    </p>
    <% } else { %>

    <div class="well">

        <h3>Rich Text Letter</h3>

        <% if (status != null) { %>
        Rich Text Letter is <%= status %>.
        <% } else { %>
        <form action="<%=request.getContextPath()%>/eform/IndivicaRichTextLetterSettings.do" method="post"
              class="form-inline" id="rtlConfigForm">

            <label for="indivica_rtl_enabled"><input type="checkbox" name="indivica_rtl_enabled"
                                                     id="indivica_rtl_enabled" <%= efd.isIndivicaRTLEnabled() ? "checked" : "" %>/>
                Check to use Rich Text Letter</label>
            <br/><br/>

            <input type="submit" class="btn btn-primary" value="Save"/>
        </form>

    </div>


    <%}%>

    <%}%>

    <script>
        registerFormSubmit('rtlConfigForm', 'dynamic-content');

    </script>
    </body>
</html>
<%!

%>
