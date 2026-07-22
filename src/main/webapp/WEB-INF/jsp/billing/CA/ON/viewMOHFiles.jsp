<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    Copyright (c) 2008-2012 Indivica Inc.

    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; version 2
    of the License.

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
  Purpose: Supports viewMOHFiles in the Ontario billing workflow.
  Expected request model data includes: mohModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.WebUtils" %>
<fmt:setBundle basename="oscarResources"/>
<%
        if (request.getAttribute("__roleName") == null) {
        Object userRole = session.getAttribute("userrole");
        Object userId = session.getAttribute("user");
        request.setAttribute("__roleName", String.valueOf(userRole) + "," + String.valueOf(userId));
    }
%>
<security:oscarSec roleName="${__roleName}" objectName="_admin,_admin.backup,_admin.billing" rights="r" reverse="true">
    <c:redirect url="${pageContext.request.contextPath}/logoutPage"/>
</security:oscarSec>
<jsp:useBean id="oscarVariables" class="java.util.Properties" scope="session"/>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.admin.viewMOHFiles"/></title>

    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

    <script LANGUAGE="JavaScript">
        function csrfTokenValue() {
            var tokenInput = document.querySelector("input[name='CSRF-TOKEN']");
            return tokenInput ? tokenInput.value : "";
        }

        function attachCsrfToken(form) {
            var token = csrfTokenValue();
            if (!form || token.length === 0) {
                return;
            }
            var existing = form.querySelector("input[name='CSRF-TOKEN']");
            if (!existing) {
                existing = document.createElement("input");
                existing.type = "hidden";
                existing.name = "CSRF-TOKEN";
                form.appendChild(existing);
            }
            existing.value = token;
        }

        function viewMOHFile(anchor) {
            var filename = anchor.dataset.filename;
            var decodedFilename = decodeURIComponent(filename.replace(/\+/g, "%20"));
            var form = document.getElementById("form");
            document.getElementById("filename").value = filename;
            var folderSelect = document.querySelector("select[name='folder']");
            if (folderSelect) {
                document.getElementById("reportFolder").value = folderSelect.value;
            }
            var fileType = decodedFilename.substring(0, 1).toUpperCase();
            if (decodedFilename.substring(decodedFilename.length - 4).toLowerCase() == ".zip") {
                alert("Please unzip " + decodedFilename + " before processing.");
                location.href = "${pageContext.request.contextPath}/billing/CA/ON/moveMOHFiles";
                return;
            } else if (fileType == "P" || fileType == "S") {
                form.action = "${pageContext.request.contextPath}/servlet/io.github.carlos_emr.DocumentUploadServlet";
            } else if (fileType == "L") {
                form.action = "${pageContext.request.contextPath}/billing/CA/ON/billingLreport";
            } else {
                form.action = "/<carlos:encode value='${mohModel.projectHome}' context='javaScriptAttribute'/>/oscarBilling/DocumentErrorReportUpload";
            }
            attachCsrfToken(form);
            form.submit();
        }

        function navigateToFolder(selectEl) {
            var allowed = ["inbox", "outbox", "sent", "archive"];
            var folder = selectEl.options[selectEl.selectedIndex].value;
            if (allowed.indexOf(folder) !== -1) {
                location.href = "${pageContext.request.contextPath}/billing/CA/ON/moveMOHFiles?folder=" + encodeURIComponent(folder);
            }
        }

        function toggleCheckboxes(el) {
            document.querySelectorAll("input[name='mohFile']").forEach(function (cb) {
                cb.checked = el.checked;
            });
        }

        function checkForm() {
            if (document.querySelectorAll("input[name='mohFile']:checked").length > 0) {
                attachCsrfToken(document.querySelector("form[action$='/billing/CA/ON/moveMOHFiles']"));
                return true;
            }
            alert("Please select a file first.");
            return false;
        }
    </script>
</head>

<body>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<h3><fmt:message key="admin.admin.viewMOHFiles"/></h3>
<%= WebUtils.popErrorAndInfoMessagesAsHtml(session) %>

<div class="container-fluid card card-body bg-body-tertiary">

    <form id="form" method="POST" action="${pageContext.request.contextPath}/billing/CA/ON/moveMOHFiles">
        <input type="hidden" id="filename" name="filename" value="">
        <input type="hidden" id="reportFolder" name="folder" value="">
    </form>

    <c:if test="${mohModel.inbox}">
    <form method="POST" action="${pageContext.request.contextPath}/billing/CA/ON/moveMOHFiles" onsubmit="return checkForm();" class="d-flex flex-wrap align-items-center gap-2">
    </c:if>

        <c:if test="${mohModel.inbox}">
        <input type="submit" value="Archive" class="btn btn-secondary">
        </c:if>

        View:
        <select name="folder" onchange="navigateToFolder(this)">
            <option value="inbox" <c:if test="${mohModel.inbox}">selected</c:if>>Inbox</option>
            <option value="outbox" <c:if test="${mohModel.outbox}">selected</c:if>>Outbox</option>
            <option value="sent" <c:if test="${mohModel.sent}">selected</c:if>>Sent</option>
            <option value="archive" <c:if test="${mohModel.archive}">selected</c:if>>Archive</option>
        </select>

        <table class="table table-striped table-hover">
            <thead>
            <tr>
                <c:if test="${mohModel.inbox}">
                <th><input type="checkbox" onclick="toggleCheckboxes(this)" title="select all"></th>
                </c:if>
                <th>View File</th>
                <c:if test="${mohModel.providesAccessToFiles}">
                <th>Download File</th>
                </c:if>
                <th>Date</th>
            </tr>
            </thead>

            <tbody>
            <c:forEach var="file" items="${mohModel.files}">
                <tr>
                    <c:if test="${mohModel.inbox}">
                        <td><input type='checkbox' name='mohFile'
                                   value="<carlos:encode value='${file.urlEncodedName}' context='htmlAttribute'/>"
                                   title='select to archive'/></td>
                    </c:if>
                    <c:choose>
                        <c:when test="${mohModel.inbox or mohModel.archive}">
                            <td>
                                <a href='#' onclick='viewMOHFile(this)'
                                   data-filename="<carlos:encode value='${file.urlEncodedName}' context='htmlAttribute'/>"><carlos:encode
                                        value="${file.displayName}" context="html"/><carlos:encode
                                        value="${file.unzipMessage}" context="html"/></a>
                            </td>
                            <td>
                                <a href="${pageContext.request.contextPath}/servlet/BackupDownload?filename=<carlos:encode value='${file.urlEncodedName}' context='uriComponent'/>">Download</a>
                            </td>
                        </c:when>
                        <c:otherwise>
                            <td><carlos:encode value="${file.displayName}" context="html"/></td>
                        </c:otherwise>
                    </c:choose>
                    <td align='right'><carlos:encode value="${file.date}" context="html"/></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>

        <c:if test="${fn:length(mohModel.files) > 20}">
            <c:if test="${mohModel.inbox}">
                <input type="submit" value="Archive" class="btn btn-secondary">
            </c:if>

            <select name="folder" onchange="navigateToFolder(this)">
                <option value="inbox" <c:if test="${mohModel.inbox}">selected</c:if>>Inbox</option>
                <option value="outbox" <c:if test="${mohModel.outbox}">selected</c:if>>Outbox</option>
                <option value="sent" <c:if test="${mohModel.sent}">selected</c:if>>Sent</option>
                <option value="archive" <c:if test="${mohModel.archive}">selected</c:if>>Archive</option>
            </select>
        </c:if>
        <c:if test="${mohModel.inbox}"></form></c:if>
</div><!--container-->
</body>
</html>
