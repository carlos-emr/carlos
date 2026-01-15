<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>
<!DOCTYPE html>
<%@ page
        import="java.util.*, ca.openosp.openo.documentManager.EDocUtil" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.document" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect("{ pageContext.request.contextPath }/securityError.jsp?type=_admin,_admin.document");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<%
    ArrayList<String> doctypesD = EDocUtil.getDoctypes("demographic");
    ArrayList<String> doctypesP = EDocUtil.getDoctypes("provider");
%>

<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <link href="${ pageContext.request.contextPath }/css/bootstrap.css" rel="stylesheet" type="text/css">
    <!-- Bootstrap 2.3.1 -->
    <link href="${ pageContext.request.contextPath }/css/DT_bootstrap.css" rel="stylesheet" type="text/css">
    <link href="${ pageContext.request.contextPath }/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"
          rel="stylesheet">
    <script src="${ pageContext.request.contextPath }/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="${ pageContext.request.contextPath }/js/global.js"></script>
    <script src="${ pageContext.request.contextPath }/library/DataTables/datatables.min.js"></script>
    <!-- DataTables 1.13.4 -->

    <title> Document Categories</title>

    <script>


        function popupPage(vheight, vwidth, varpage) { //open a new popup window
            var page = "" + varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";//360,680
            var popup = window.open(page, "groupno", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
                popup.focus();
            }
        }

        function submitUpload(object) {
            object.Submit.disabled = true;

            return true;
        }
    </script>
    <script>
        jQuery(document).ready(function () {
            jQuery('#demographicDocType').DataTable({
                "language": {
                    "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                }
            });
        });
    </script>
    <script>
        jQuery(document).ready(function () {
            jQuery('#demographicDocType2').DataTable({
                "language": {
                    "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                }
            });
        });
    </script>

</head>
<body>
<h4>Document Categories</h4>
<div class="well">
    <table style="width:100%;">
        <tr>
            <td style="width:50%; padding:15px; vertical-align:top;">
                <h5>Demographic Document Categories</h5>
                <table id="demographicDocType" class="table table-striped table-condensed" style="width:80%;">
                    <thead>
                    <tr>
                        <th>Document Type</th>
                        <th>Status</th>
                    </tr>
                    </thead>
                    <tbody>
                    <% for (String doctypeD : doctypesD) { %>
                    <tr>
                        <td><%=doctypeD%>
                        </td>
                        <td><%=EDocUtil.getDocStatus("demographic", doctypeD)%>
                        </td>
                    </tr>
                    <% }%>
                    </tbody>
                </table>
            </td>
            <td style="width:50%; padding:15px; vertical-align:top;">
                <h5>Provider Document Categories</h5>
                <table id="demographicDocType2" class="table table-striped table-condensed" style="width:80%;">
                    <thead>
                    <tr>
                        <th>Document Type</th>
                        <th>Status</th>
                    </tr>
                    </thead>
                    <tbody>
                    <% for (String doctypeP : doctypesP) { %>
                    <tr>
                        <td><%=doctypeP%>
                        </td>
                        <td><%=EDocUtil.getDocStatus("provider", doctypeP)%>
                        </td>
                    </tr>
                    <% }%>
                    </tbody>
                </table>
            </td>
        </tr>
    </table>

    <input type="button" class="btn" value="Add New" onclick='popupPage(550,800,"<%=request.getContextPath() %>/documentManager/addNewDocumentCategories.jsp");return false;'/>
    <input type="button" class="btn" value="Update Status" onclick='popupPage(550,800,"<%=request.getContextPath() %>/documentManager/changeStatus.jsp");return false;'/>



</div> <!-- well -->


</body>
</html>