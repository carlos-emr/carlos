<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>
<!DOCTYPE html>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
%>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="java.util.*" %>
<%@ page
        import="ca.openosp.openo.hospitalReportManager.SFTPConnector, ca.openosp.openo.hospitalReportManager.dao.HRMProviderConfidentialityStatementDao" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Hospital Report Manager</title>
        <style>
            body {
                margin-left: 30px !important;
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

            .invalid {
                color: red;
            }

            .success,
            .pending {
                color: green;
            }

            .failed {
                color: #FFD700;
            }
        </style>

        <script src="<%= request.getContextPath() %>/js/global.js"></script>

        <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" type="text/css"
              href="${ pageContext.request.contextPath }/hospitalReportManager/inbox.css">


        <script>
            function runFetch() {
                window.location = "<%=request.getContextPath() %>/hospitalReportManager/hospitalReportManager.jsp?fetch=true";
            }

            function validateForm() {
                let fileInput = document.getElementById("fileInput");
                if (fileInput.files.length === 0) {
                    alert("Please select a file to upload.");
                    return false;
                }

                document.getElementById("file-upload-btn").disabled = true;
                document.querySelector('.loading-screen').classList.toggle('flex');
                return true;
            }

            function getFileList(event) {
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
                uploadText.classList.remove('invalid', 'success', 'pending', 'failed');
                switch (status.trim()) {
                    case "FAILED":
                        uploadText.textContent = 'Failed to handle HRM report';
                        uploadText.classList.add('failed');
                        break;
                    case "COMPLETED":
                        uploadText.textContent = 'Uploaded Successfully';
                        uploadText.classList.add('success');
                        break;
                    case "PENDING":
                        uploadText.textContent = 'Pending Upload';
                        uploadText.classList.add('pending');
                        break;
                    default:
                        uploadText.textContent = 'Invalid File';
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
    <div class="container">
        <h4>Hospital Report Manager</h4>
        <div class="loading-screen">
            <div class="loading-bar progress progress-striped active">
                <div class="bar" style="width: 100%;"></div>
            </div>
            <div class="loading-message">
                Please be patient. Uploading a large number of HRM documents may take some time. Do not close this
                window while uploading...
            </div>
        </div>

        <% if (request.getParameter("fetch") != null && request.getParameter("fetch").equalsIgnoreCase("true"))
            if (loggedInInfo == null) {
                System.err.println("loggedInInfo is null"); 
            } else {
                try {
                    new SFTPConnector(loggedInInfo).startAutoFetch(loggedInInfo);
                } catch (Exception e) {
                    System.err.println("Error in startAutoFetch: " + e.getMessage()); 
                }
            }
        %>
        <p>
            HRM Status: <%=SFTPConnector.isFetchRunning() ? "Fetching data from HRM" : "Idle" %><br>
            <% if (!SFTPConnector.isFetchRunning()) { %>
            <input type="button" class="btn" onClick="runFetch()" value="Fetch New Data from HRM">
            <% } else { %>
            Please wait until the current fetch task completes before requesting another data fetch.
            <% } %>
        </p>
        <form enctype="multipart/form-data" action="<%=request.getContextPath() %>/hospitalReportManager/UploadLab.do"
              method="post" onsubmit="return validateForm()">
            Upload HRM reports from your computer: <input type="file" id="fileInput" name="uploads" multiple
                                                          onChange="getFileList(event)"/>
            <span title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.uploadWarningBody"/>"
                  style="vertical-align:middle;font-family:arial;font-size:20px;font-weight:bold;color:#ABABAB;cursor:pointer"><img
                    alt="alert" src="<%= request.getContextPath() %>/images/icon_alertsml.gif"></span>

            <input type="submit" id="file-upload-btn" class="btn" name="submit" value="Upload">
            <div id="file-list">
            </div>

            <c:forEach var="file" items="${filesStatusMap}">
                <script>
                    addFileNameWithStatus("<c:out value="${file.key}" />", "<c:out value="${file.value}" />");
                </script>
            </c:forEach>
        </form>
        <%
            HRMProviderConfidentialityStatementDao hrmProviderConfidentialityStatementDao = (HRMProviderConfidentialityStatementDao) SpringUtils.getBean(HRMProviderConfidentialityStatementDao.class);
            String statement = hrmProviderConfidentialityStatementDao.getConfidentialityStatementForProvider(loggedInInfo.getLoggedInProviderNo());
        %>
        <form action="<%=request.getContextPath() %>/hospitalReportManager/Statement.do" method="post">
            <div class="control-group">
                <label class="control-label">Provider Confidentiality Statement</label>
                <div class="controls">
                    <textarea name="statement"><%=statement %></textarea>
                </div>
            </div>
            <div>
                <input type="submit" class="btn btn-primary" name="submit" value="Save Statement">
                <% if (request.getAttribute("statementSuccess") != null && (Boolean) request.getAttribute("statementSuccess")) { %>
                Success
                <% } else if (request.getAttribute("statementSuccess") != null && !((Boolean) request.getAttribute("statementSuccess"))) { %>
                Error
                <% } %>
            </div>
        </form>
        <input type="button" class="btn"
               value="I don't want to receive any more HRM outage messages for this outage instance"
               onclick="window.location='hospitalReportManager/disable_msg_action.jsp'">
    </div>
    </body>
</html>