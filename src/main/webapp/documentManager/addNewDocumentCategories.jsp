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
<%@ page
        import="java.util.*" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin.document,_admin" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin.document,_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%
    HashMap<String, String> doctypeerrors = new HashMap<String, String>();
    if (request.getAttribute("doctypeerrors") != null) {
        doctypeerrors = (HashMap<String, String>) request.getAttribute("doctypeerrors");
    }
%>

<html>
<head>
    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css"/>
    <script>

        function submitUpload(object) {
            object.Submit.disabled = true;

            return true;
        }
    </script>
    <!-- <title>Add New Document Type</title> -->
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgAddNewDocumentType"/></title>
</head>
<body>
<div>
    <% Iterator iter = doctypeerrors.keySet().iterator();
        while (iter.hasNext()) {%>
    <font class="warning">Error: <fmt:setBundle basename="oscarResources"/><fmt:message key="<%= doctypeerrors.get(iter.next())%>"/></font><br/>
    <% } %>
</div>

<table class="MainTable" id="scrollNumber1" name="documentCategoryTable" style="margin: 0px;">
    <tr class="topbar">
        <td class="MainTableTopRowLeftColumn" width="60px">Document Types</td>
        <td class="MainTableTopRowRightColumn">
            <table class="TopStatusBar">
                <tr>
                    <!--                             <td>Add New Document Type</td> -->
                    <td><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentEdit.msgAddDocument"/></td>

                </tr>
            </table>
        </td>
    </tr>
    <form action="${pageContext.request.contextPath}/documentManager/addDocumentType.do" method="POST"
               enctype="multipart/form-data" class="forms"
               onsubmit="return submitUpload(this)">
        <table>
            <tr>
                <!-- 		<td><b>Select module name: </b></td> -->
                <td><b><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentEdit.msgSelectModuleName"/></b></td>

                <td>
                    <input <% if (doctypeerrors.containsKey("modulemissing")) {%>
                        <%-- 				class="warning" <%}%> id="function" type="radio" name="function" value="Demographic"> Demographic</td> --%>
                            class="warning" <%}%> id="function" type="radio" name="function" value="Demographic">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportCatchment.msgDemographic"/></td>

                <td>
                    <input <% if (doctypeerrors.containsKey("modulemissing")) {%>
                        <%-- 				class="warning" <%}%> id="function" type="radio" name="function" value="Provider"> Provider</td> --%>
                            class="warning" <%}%> id="function" type="radio" name="function" value="Provider">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgProvider"/></td>

            </tr>

            <tr>
                <td><b><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentReport.msgEnterDocumentType"/> </b></td>
                <td>
                    <input <% if (doctypeerrors.containsKey("doctypemissing")) {%>
                            class="warning" <%}%> id="docType" type="text" name="docType" value=""> <br></td>
            </tr>


            <tr>
                <td><input type="submit" name="submit" value="Submit"/></td>
                <td><input type="button" name="button" value="Cancel" onclick=self.close()></td>
            </tr>
        </table>
    </form>
</table>
</body>
</html>
