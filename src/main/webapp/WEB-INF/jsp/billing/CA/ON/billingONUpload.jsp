<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.

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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `billingONUpload.jsp` for the Ontario billing workflow.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%--
    Authentication / authorization is enforced by BillingOnUpload2Action
    (struts mapping billing/CA/ON/billingONUpload), which gates _admin.billing
    w privilege before forwarding to this page. The form's onSubmit handler
    reroutes the multipart POST to the appropriate upload endpoint
    (DocumentUploadServlet for MOH diskette files, DocumentErrorReportUpload
    for error reports), so this page does not POST back to the gate action.
    The DocumentErrorReportUpload form target uses an extensionless Struts
    route, so no JSP-side project_home lookup is needed.
--%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>

<html>
<head>
    <title><fmt:message key="admin.admin.uploadMOHFile"/></title>
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

    <script type="text/javascript">
        function onSubmit() {
            var val1 = document.form1.file1.value;
            var n = val1.lastIndexOf('\\');
            val1 = val1.substring((n * 1 + 1));
            if (val1.length > 30) {
                alert("File name: " + val1 + " is too long. Please rename file and upload again!");
                return false;
            }
            if (val1.substring(0, 1) == "P" || val1.substring(0, 1) == "S") {
                if (document.all) {
                    document.all.form1.action = "${pageContext.request.contextPath}/servlet/io.github.carlos_emr.DocumentUploadServlet";
                    document.all.form1.submit();
                } else {
                    document.getElementById('form1').action = "${pageContext.request.contextPath}/servlet/io.github.carlos_emr.DocumentUploadServlet";
                    document.getElementById('form1').submit();
                }
            } else {
                if (document.all) {
                    document.all.form1.action = "${pageContext.request.contextPath}/oscarBilling/DocumentErrorReportUpload";
                    document.all.form1.submit();
                } else {
                    document.getElementById('form1').action = "${pageContext.request.contextPath}/oscarBilling/DocumentErrorReportUpload";
                    document.getElementById('form1').submit();
                }
            }
            return false;
        }
    </SCRIPT>
</head>

<body>
<h3><fmt:message key="admin.admin.uploadMOHFile"/></h3>
<div class="container-fluid card card-body bg-body-tertiary">
    <form id="form1" name="form1" method="post" action="" ENCTYPE="multipart/form-data" onsubmit="return onSubmit();">
        Select diskette<input style="margin-left:40px;" type="file" name="file1" value="" required>
        <input class="btn btn-primary" type="submit" name="Submit" value="Create Report">
    </form>
    *Select a file downloaded from EDT.
</div>
</body>
</html>
