<%@ page import="ca.openosp.openo.commn.model.enumerator.LabType" %><%--

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

--%>
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<%
    pageContext.setAttribute("labTypes", LabType.values());
    String outcome = (String) request.getAttribute("outcome");
%>

<html>
<head>

    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="lab.ca.all.testUploader.labUploadUtility"/></title>

    <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.structure-1.12.1.min.css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.theme-1.12.1.min.css">

    <style>
        body {
            margin: 30px !important;
        }

        .file-item {
            border: 1px solid green;
            border-radius: 5px;
            padding: 7px;
            margin-bottom: 3px;
            font-size: 14px;
            word-wrap: break-word;
            max-width: 100%;
            position: relative;
        }

        .upload-text {
            position: absolute;
            top: 50%;
            right: 10px;
            transform: translateY(-50%);
            font-weight: bold;
        }

        .file-name {
            max-width: calc(100% - 170px);
            display: inline-block;
            vertical-align: middle;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }

        .loading-screen {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background-color: #f1f1f1;
            display: none;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            z-index: 9999;
        }

        .loading-bar {
            width: 50%;
            margin-bottom: 20px;
        }

        .loading-message {
            font-size: 16px;
            font-weight: bold;
        }

        .flex {
            display: flex;
        }

        .hidden {
            display: none;
        }

        .invalid,
        .failed {
            color: red;
        }

        .success,
        .pending {
            color: green;
        }

        .exists {
            color: #FFD700;
        }

        #file-list {
            margin-top: 20px;
        }
    </style>

    <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="<%=request.getContextPath() %>/js/bootstrap.js"></script>
    <script src="<%=request.getContextPath() %>/js/jquery.validate.js"></script>
    <script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>

    <script>
        $(function () {
            $(document).tooltip();
        });

        function selectOther() {
            document.querySelector('.alert').classList.add('hidden');
            if (document.UPLOAD.type.value == "OTHER") {
                document.getElementById('OTHER').classList.remove('hidden');
                document.getElementById('otherType').required = true;
            } else {
                document.getElementById('OTHER').classList.add('hidden');
                document.getElementById('otherType').required = false;
            }
        }

        function validateForm() {
            let numberOfLabs = document.getElementById("importFiles").files.length;
            let labType = document.getElementById("type").value;

            if (numberOfLabs === 0) {
                showErrorMessage("<strong>Error!</strong> Please select labs for upload.");
                return false;
            } else if (labType === "0") {
                showErrorMessage("<strong>Error!</strong> Please specify a lab type.");
                return false;
            }

            document.querySelector('.loading-screen').classList.toggle('flex');
            return true;
        }

        function showErrorMessage(message) {
            document.querySelector('.alert').classList.add('alert-error');
            document.querySelector('.alert').classList.remove('hidden');
            document.getElementById('errorMsg').innerHTML = message;
        }

        function getFileList(event) {
            document.querySelector('.alert').classList.add('hidden');
            const fileList = document.getElementById('file-list');
            const files = event.target.files;
            fileList.innerHTML = '';

            for (let i = 0; i < files.length; i++) {
                addFileNameWithStatus(files[i].name, "PENDING");
            }
        }

        function addFileNameWithStatus(name, status) {
            const fileList = document.getElementById('file-list');
            const fileItem = document.createElement('div');
            fileItem.className = 'file-item';

            const fileName = document.createElement('span');
            fileName.className = 'file-name';
            fileName.textContent = name;

            const uploadText = document.createElement('span');
            uploadText.className = 'upload-text';
            uploadText.classList.remove('invalid', 'success', 'pending', 'failed', 'exists');
            switch (status.trim()) {
                case "FAILED":
                    uploadText.textContent = 'Failed to upload HL7 lab';
                    uploadText.classList.add('failed');
                    break;
                case "COMPLETED":
                    uploadText.textContent = 'Uploaded successfully';
                    uploadText.classList.add('success');
                    break;
                case "PENDING":
                    uploadText.textContent = 'Pending upload';
                    uploadText.classList.add('pending');
                    break;
                case "EXISTS":
                    uploadText.textContent = 'Already uploaded';
                    uploadText.classList.add('exists');
                    break;
                default:
                    uploadText.textContent = 'Invalid lab';
                    uploadText.classList.add('invalid');
                    break;
            }

            fileItem.appendChild(fileName);
            fileItem.appendChild(uploadText);
            fileList.appendChild(fileItem);
        }
    </script>

</head>

<body>

<h3>HL7 Lab Upload</h3>
<div class="loading-screen">
    <div class="loading-bar progress progress-striped active">
        <div class="bar" style="width: 100%;"></div>
    </div>
    <div class="loading-message">
        Please be patient. Uploading a large number of HL7 labs may take some time. Do not close this window while
        uploading...
    </div>
</div>

<div class="well">

    <div class="alert hidden">
        <button type="button" class="close" data-dismiss="alert">&times;</button>
        <div id="errorMsg">

        </div>
    </div>

    <form method='POST' name="UPLOAD" id="uploadForm" enctype="multipart/form-data" onsubmit="return validateForm()"
          action='${ctx}/lab/CA/ALL/insideLabUpload.do'>

        <fmt:setBundle basename="oscarResources"/><fmt:message key="lab.ca.all.testUploader.pleaseSelectTheLabfile"/>: <i class="icon-question-sign"></i>
        

        <div style="position:relative;">
<span class='btn'>
    Choose File...
    <input type="file" name="importFiles" id="importFiles" accept=".xml,.hl7" multiple onChange="getFileList(event)"
           style='position:absolute;z-index:2;top:0;left:0;filter: alpha(opacity=0);-ms-filter:"progid:DXImageTransform.Microsoft.Alpha(Opacity=0)";opacity:0;background-color:transparent;color:transparent;'
           size="40">
</span>


        </div>
        <span title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.uploadWarningBody"/>"
              style="vertical-align:middle;font-family:arial;font-size:20px;font-weight:bold;color:#ABABAB;cursor:pointer"><img
                alt="alert" src="<%= request.getContextPath() %>/images/icon_alertsml.gif"/></span>

        <br><br>
        <label for="type"><fmt:setBundle basename="oscarResources"/><fmt:message key="lab.ca.all.testUploader.labType"/></label><br>
        <select name="type" id="type" onchange="selectOther()">
            <option value="0">Select Lab Type:</option>
            <c:forEach items="${pageScope.labTypes}" var="type">
                <option value="${type}">${type}</option>
            </c:forEach>
            <option value="OTHER">Other</option>
        </select>
        <br>
        <div id="OTHER" class="hidden">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="lab.ca.all.testUploader.pleaseSpecifyTheOtherLabType"/>:<br>
            <input type="text" id="otherType">
        </div>

        <br>
        <fmt:setBundle basename="oscarResources"/><fmt:message key="lab.ca.all.testUploader.warnings"/>
        <br><br>
        <button type="submit" class="btn btn-primary"><i class="icon-upload"></i> Upload</button>

        <div id="file-list">
        </div>

        <c:forEach var="file" items="${filesStatusMap}">
            <script>
                addFileNameWithStatus("<c:out value="${file.key}" />", "<c:out value="${file.value}" />");
            </script>
        </c:forEach>
    </form>
</div>
</body>

</html>