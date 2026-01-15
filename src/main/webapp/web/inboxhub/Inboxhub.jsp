<!--
Copyright (c) 2023. Magenta Health Inc. All Rights Reserved.

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
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA
-->

<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.lab.ca.on.*" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
<%@page import="ca.openosp.openo.utility.MiscUtils,org.apache.commons.text.StringEscapeUtils" %>
<%@page import="org.apache.logging.log4j.Logger,ca.openosp.openo.commn.dao.OscarLogDao,ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.inboxhub.query.InboxhubQuery" %>
<%@page import="ca.openosp.openo.mds.data.CategoryData" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css">
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/css/bootstrap-datetimepicker-standalone.css" />
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/css/bootstrap-datetimepicker.min.css" /> 
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.0.2/css/bootstrap.min.css" rel="stylesheet" media="screen">
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css" />
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.css" />
    <link rel="stylesheet" type="text/css" media="all" href="${pageContext.request.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css" />
    <link href="${pageContext.request.contextPath}/css/font-awesome.min.css" rel="stylesheet">
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/web/css/Inboxhub.css"/>

    <script type="text/javascript" src="${pageContext.request.contextPath}/library/moment.js"></script>
    <script src="${pageContext.request.contextPath}/library/bootstrap/5.0.2/js/bootstrap.bundle.js"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
    <script type="text/javascript" charset="utf8" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/bootstrap-datetimepicker.min.js" ></script>
    <script src="${pageContext.request.contextPath}/share/javascript/oscarMDSIndex.js"></script>
    <title>Inboxhub</title>
</head>
<body>
<jsp:include page="/images/spinner.jsp" flush="true"/>
<script>
    const contextPath = "<e:forJavaScript value='${pageContext.request.contextPath}' />";
</script>
<input type="hidden" id="ctx" value="<e:forHtmlAttribute value='${pageContext.request.contextPath}' />";/>
<div class="container-fluid overflow-hidden">
    <div class="row">
        <nav class="navbar navbar-light d-flex justify-content-center" style="background-color: #e3f2fd;">
            <jsp:include page="InboxhubTopbar.jsp"/>
        </nav>
    </div>
    <div class="row flex-nowrap">
        <div class="col-auto px-0 m-1">
            <div class="bg-light text-dark inbox-form" style="display: inline-block;">
                <jsp:include page="InboxhubForm.jsp"/>
            </div>
        </div>
        <div class="col px-0 m-1" style="overflow-y: auto;">
            <div class="bg-light text-dark">
                <div id="inboxhubMode"> 
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>