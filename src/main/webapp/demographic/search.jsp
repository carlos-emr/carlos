<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_search" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_search");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE HTML>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<% Boolean isMobileOptimized = session.getAttribute("mobileOptimized") != null; %>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.title"/></title>

    <script type="text/javascript">
        // Enable global barcode scanner listener for this page
        // This flag prevents the listener from firing on patient edit/add pages
        window.enableGlobalBarcodeSearch = true;

        function setfocus() {
            document.titlesearch.keyword.focus();
            document.titlesearch.keyword.select();
        }
    </script>

    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js" type="text/javascript"></script>
    <script src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js" type="text/javascript"></script>
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.css"/>
    <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet" type="text/css"/>
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/share/css/searchBox.css"/>
</head>
    <body onload="setfocus()">
    <div class="container">
        <%@ include file="zdemographicfulltitlesearch.jsp" %>

        <!-- <security:oscarSec roleName="<%=roleName$%>" objectName="_demographic.addnew" rights="r">  -->
        <div class="createNew">
            <a href="demographicaddarecordhtm.jsp"><b><font size="+1"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.btnCreateNew"/></font></b></a>
        </div>
        <!-- </security:oscarSec> -->

        <oscar:oscarPropertiesCheck
                property="SHOW_FILE_IMPORT_SEARCH" value="yes">
            &nbsp;&nbsp;&nbsp;<a href="demographicImport.jsp"><b><font
            size="+1"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.search.importNewDemographic"/></font></b></a>
        </oscar:oscarPropertiesCheck>
    </div>
    </body>
</html>
