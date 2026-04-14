<%--

    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%--
    Client Image Manager - Upload patient photo.
    Allows uploading GIF/JPG images for patient identification.

    @since 2005 (original), modernized 2026-02-22
--%>

<%@ include file="/casemgmt/taglibs.jsp" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.web.formbeans.*" %>

<%
    if (application.getAttribute("jakarta.servlet.context.tempdir") == null) {
        String tmpDir = System.getProperty("java.io.tmpdir");
        application.setAttribute("jakarta.servlet.context.tempdir", new java.io.File(tmpDir));
    }
%>
<html>
<head>
    <%@ include file="/includes/global-head.jspf" %>
    <title>Client Image Manager</title>
    <script>
        function init_page() {
            <%
                if(request.getAttribute("success") != null)
                {
                    %>
            opener.location.reload();
            self.close();
            <%
                }
            %>
        }

        function onPicUpload() {
            var file = document.getElementById("clientImage").files[0];
            if (!file) {
                alert("Please specify a picture path and name for the upload.");
                return false;
            }
            return true;
        }
    </script>
</head>
<body onload="self.focus();init_page();">

<div class="page-header-bar">
    <h4 class="page-header-title">
        <i class="fas fa-camera page-header-icon"></i> Client Image Manager
    </h4>
</div>

<div class="container-fluid mt-3">
    <form action="${pageContext.request.contextPath}/ClientImage.do" enctype="multipart/form-data"
          method="post" onsubmit="return onPicUpload();">
        <input type="hidden" name="method" value="saveImage"/>
        <%
            String demoNo = request.getParameter("demographicNo");
            if (demoNo != null && demoNo.matches("\\d+")) {
                request.getSession().setAttribute("clientId", demoNo);
            }
        %>
        <div class="row align-items-center mb-3">
            <div class="col-auto">
                <input type="file" name="clientImage" id="clientImage" class="form-control form-control-sm"
                       accept=".gif,.jpg,.jpeg"/>
            </div>
            <div class="col-auto">
                <button type="submit" class="btn btn-primary btn-sm">Upload</button>
            </div>
        </div>
    </form>

    <div class="alert alert-info py-2" role="alert">
        <small>Only GIF and JPG image types are allowed for the client photo.</small>
    </div>

    <div class="d-flex gap-2">
        <form action="${pageContext.request.contextPath}/ClientImage.do" method="post"
              onsubmit="return confirm('Are you sure you want to remove the client photo?');">
            <input type="hidden" name="method" value="deleteImage"/>
            <button type="submit" class="btn btn-danger btn-sm">Clear Photo</button>
        </form>
        <button type="button" class="btn btn-secondary btn-sm" onclick="self.close();">Cancel</button>
    </div>
</div>

</body>
</html>
