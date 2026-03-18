<%--

    Copyright (c) 2007 Peter Hutten-Czapski based on OSCAR general requirements
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
<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<%@page import="io.github.carlos_emr.carlos.commn.dao.QueueDao"%>
<%@page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.UserProperty"%>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao"%>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils"%>

<%@page import="io.github.carlos_emr.carlos.mds.data.*"%>
<%@page import="io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData"%>
<%@page import="java.util.*"%>
<%@page import="org.owasp.encoder.Encode"%>



<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String)session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed=true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_edoc" rights="w" reverse="<%=true%>">
	<%authed=false; %>
	<%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_edoc");%>
</security:oscarSec>
<%
	if(!authed) {
		return;
	}
%>

<%

    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    ArrayList<Provider> providers = new ArrayList<Provider>(providerDao.getActiveProviders());
    String provider = CommonLabResultData.NOT_ASSIGNED_PROVIDER_NO;

    QueueDao queueDao = (QueueDao) SpringUtils.getBean(QueueDao.class);
    HashMap<Integer, String> queues = queueDao.getHashMapOfQueues();
    String queueIdStr = (String) request.getSession().getAttribute("preferredQueue");
    int queueId = 1;
    if (queueIdStr != null) {
        queueIdStr = queueIdStr.trim();
        try {
            queueId = Integer.parseInt(queueIdStr);
        } catch (NumberFormatException e) {
            // fall back to default queueId = 1
        }
    }

    String user_no = (String) session.getAttribute("user");
    UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);

    UserProperty uProp = userPropertyDAO.getProp(user_no, UserProperty.UPLOAD_DOCUMENT_DESTINATION);
    String destination = UserProperty.PENDINGDOCS;
    if (uProp != null && uProp.getValue().equals(UserProperty.INCOMINGDOCS)) {
        destination = UserProperty.INCOMINGDOCS;
    }

    uProp = userPropertyDAO.getProp(user_no, UserProperty.UPLOAD_INCOMING_DOCUMENT_FOLDER);
    String destFolder = "Mail";
    if (uProp != null) {
        destFolder = uProp.getValue();
    }
    String context = request.getContextPath();

%>
<!DOCTYPE HTML>
<html lang="en" class="no-js">
<head>
	<meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
	<title><fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.title" /></title>
	<link rel="stylesheet" href="${pageContext.request.contextPath}/share/css/OscarStandardLayout.css" />

     <!-- Bootstrap -->
    <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet" type="text/css">

    <!-- jQuery for preference AJAX calls (CSRFGuard auto-patches jQuery XHR) -->
    <script src="<%= Encode.forHtmlAttribute(context) %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%= Encode.forHtmlAttribute(context) %>/library/jquery/jquery-compat.js"></script>

	<script>
	function setProvider(select){
		document.getElementById("provider").value = select.options[select.selectedIndex].value;
	}

	function setQueue(select){
		document.getElementById("queue").value = select.options[select.selectedIndex].value;
	}

    function setDestination(select){
        var destination=select.options[select.selectedIndex].value;
		document.getElementById("destination").value = destination;
        setDropList();
        jQuery.ajax({type:'POST', url:'${pageContext.request.contextPath}/documentManager/documentUpload.do', data:{method:'setUploadDestination', destination:destination}, success:function(data) {}});
	}

    function setDestFolder(select){
        var destFolder=select.options[select.selectedIndex].value;
		document.getElementById("destFolder").value = destFolder;
        jQuery.ajax({type:'POST', url:'${pageContext.request.contextPath}/documentManager/documentUpload.do', data:{method:'setUploadIncomingDocumentFolder', destFolder:destFolder}, success:function(data) {}});
	}

    function setDropList(){
        if(document.getElementById('destinationDrop').options[document.getElementById('destinationDrop').selectedIndex].value=="incomingDocs"){
            document.getElementById('providerDropDiv').style.display = 'none';
            document.getElementById('destFolderDiv').style.display = 'block';
        } else {
            document.getElementById('providerDropDiv').style.display = 'block';
            document.getElementById('destFolderDiv').style.display = 'none';
        }
     }
	</script>

    <style>
      #navigation { margin: 10px 0; }
      @media (max-width: 767px) {
        #title, #description { display: none; }
      }
      .fileinput-button {
        position: relative;
        overflow: hidden;
        display: inline-block;
      }
      .fileinput-button input {
        position: absolute;
        top: 0;
        right: 0;
        margin: 0;
        height: 100%;
        opacity: 0;
        font-size: 200px !important;
        direction: ltr;
        cursor: pointer;
      }
      #drop-area {
        border: 2px dashed #ccc;
        border-radius: 20px;
        width: 100%;
        font-family: sans-serif;
        margin: 20px auto;
        padding: 10px;
        transition: border-color 0.2s, background-color 0.2s;
      }
      #drop-area.drag-over {
        border-color: #0d6efd;
        background-color: #f0f7ff;
      }
      .progress { margin-bottom: 10px; }
    </style>

</head>
<body onload="setDropList();">
    <div class="container">
      <div class="card">
        <div class="card-header">
          <h3 class="card-title"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.addDocument.msgManageUploadDocument" /></h3>
        </div>
        <div class="card-body">
            <ul>
                <li><fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.title" /></li>
                <li><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentUpload.onlyPdf" /></li>
            </ul>
        </div>
       </div>
      <form
        id="fileupload"
        action="<%= Encode.forHtmlAttribute(context) %>/documentManager/documentUpload.do?method=executeUpload"
        method="POST"
        enctype="multipart/form-data"
      >
            <input type="hidden" id="destination" name="destination" value="<%= Encode.forHtmlAttribute(destination) %>"/>
            <input type="hidden" id="destFolder" name="destFolder" value="<%= Encode.forHtmlAttribute(destFolder) %>"/>
			<input type="hidden" id="provider" name="provider" value="<%= Encode.forHtmlAttribute(provider) %>" />
		    <input type="hidden" id="queue" name="queue" value="<%=queueId%>"/>

             <div class="mb-3">
                <label for="destinationDrop"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentUploader.destination" />:</label>
                    <select onchange="javascript:setDestination(this);"  id="destinationDrop"  name="destinationDrop" class="form-select">
                        <option value="pendingDocs" <%=( destination.equals("pendingDocs") ? " selected" : "")%> ><fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.pendingDocs" /></option>
                        <option value="incomingDocs" <%=( destination.equals("incomingDocs") ? " selected" : "")%> ><fmt:setBundle basename="oscarResources"/><fmt:message key="inboxmanager.document.incomingDocs" /></option>
                    </select>
             </div>
             <div class="mb-3" id="providerDropDiv">
                <label for="providerDrop" class="fields"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentUploader.sendToProvider" />:</label>
				<select onchange="javascript:setProvider(this);" id="providerDrop" name="providerDrop" class="form-select">
					<option value="0" <%=("0".equals(provider) ? " selected" : "")%>><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentUploader.none" /></option>
					<%
					for (int i = 0; i < providers.size(); i++) {
	                	Provider h = providers.get(i);
	                %>
					<option value="<%= Encode.forHtmlAttribute(h.getProviderNo())%>" <%= (h.getProviderNo().equals(provider) ? " selected" : "")%>><%= Encode.forHtml(h.getLastName())%> <%= Encode.forHtml(h.getFirstName())%></option>
					<%
					}
					%>
				</select>
             </div>
             <div class="mb-3">
				<label for="queueDrop" class="fields"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.incomingDocs.queue" />:</label>
				<select onchange="javascript:setQueue(this);" id="queueDrop" name="queueDrop" class="form-select">
					<%
					for (Map.Entry<Integer,String> entry : queues.entrySet()) {
					    int key = entry.getKey();
					    String value = entry.getValue();

	                %>
					<option value="<%=key%>" <%=( (key == queueId) ? " selected" : "")%>><%= Encode.forHtml(value)%></option>
					<%
					}
					%>
				</select>
             </div>
             <div class="mb-3" id="destFolderDiv">
                <label for="destFolderDrop" class="fields"><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentUploader.folder" />:</label>
                    <select onchange="javascript:setDestFolder(this);"  id="destFolderDrop"  name="destFolderDrop" class="form-select">
                        <option value="Fax" <%=( destFolder.equals("Fax") ? " selected" : "")%> ><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.incomingDocs.fax" /></option>
                        <option value="Mail" <%=( destFolder.equals("Mail") ? " selected" : "")%> ><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.incomingDocs.mail" /></option>
                        <option value="File" <%=( destFolder.equals("File") ? " selected" : "")%> ><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.incomingDocs.file" /></option>
                        <option value="Refile" <%=( destFolder.equals("Refile") ? " selected" : "")%> ><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.incomingDocs.refile" /></option>
                    </select>
              </div>

        <div class="row fileupload-buttonbar">
          <div class="col-lg-7">
            <span class="btn fileinput-button btn-secondary">
              <i class="fa-solid fa-plus"></i>
              <span><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnAdd" />...</span>
              <input type="file" id="fileInput" name="filedata" multiple accept=".pdf,application/pdf" />
            </span>
            <button type="button" class="btn btn-primary" id="btnUpload">
              <i class="fa-solid fa-upload"></i>
              <span><fmt:setBundle basename="oscarResources"/><fmt:message key="dms.zadddocument.btnUpload" /></span>
            </button>
            <button type="button" class="btn btn-secondary" id="btnReset">
              <i class="fa-solid fa-ban"></i>
              <span><fmt:setBundle basename="oscarResources"/><fmt:message key="global.reset" /></span>
            </button>
          </div>
          <div class="col-lg-5">
            <div class="progress" id="globalProgress" style="display:none"
                 role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="0">
              <div class="progress-bar bg-success" style="width:0%"></div>
            </div>
          </div>
        </div>
        <div id="drop-area">
            <span style="font-size:40px; color:lightblue;"><i class="fa-solid fa-cloud-arrow-up"></i></span>
            <table role="presentation" class="table table-striped table-sm">
              <tbody id="fileList"></tbody>
            </table>
        </div>
        <ul id="msg" class="alert alert-danger" style="display:none;"></ul>
        <ul id="msgU" class="alert alert-success" style="display:none;"></ul>
      </form>
</div>

<script>
(function () {
    'use strict';

    var uploadUrl = '<%= Encode.forJavaScript(context) %>/documentManager/documentUpload.do?method=executeUpload';
    var pendingFiles = [];
    var uploading = false;

    var fileInput = document.getElementById('fileInput');
    var fileList = document.getElementById('fileList');
    var btnUpload = document.getElementById('btnUpload');
    var btnReset = document.getElementById('btnReset');
    var dropArea = document.getElementById('drop-area');
    var msgError = document.getElementById('msg');
    var msgSuccess = document.getElementById('msgU');
    var globalProgress = document.getElementById('globalProgress');

    function formatSize(bytes) {
        if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB';
        if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return bytes + ' bytes';
    }

    function clearMessages() {
        msgError.style.display = 'none';
        while (msgError.firstChild) msgError.removeChild(msgError.firstChild);
        msgSuccess.style.display = 'none';
        while (msgSuccess.firstChild) msgSuccess.removeChild(msgSuccess.firstChild);
    }

    function showError(text) {
        var li = document.createElement('li');
        li.textContent = text;
        msgError.appendChild(li);
        msgError.style.display = 'block';
    }

    function showSuccess(text) {
        var li = document.createElement('li');
        li.textContent = text;
        msgSuccess.appendChild(li);
        msgSuccess.style.display = 'block';
    }

    function addFiles(files) {
        clearMessages();
        for (var i = 0; i < files.length; i++) {
            var file = files[i];
            if (!/\.pdf$/i.test(file.name)) {
                showError(file.name + ' \u2014 <fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentUpload.onlyPdf" />');
                continue;
            }
            var entry = { file: file, row: null };
            pendingFiles.push(entry);
            entry.row = createFileRow(entry);
            fileList.appendChild(entry.row);
        }
    }

    // Build a file row using safe DOM methods (no innerHTML)
    function createFileRow(entry) {
        var tr = document.createElement('tr');

        var tdName = document.createElement('td');
        tdName.className = 'name';
        tdName.textContent = entry.file.name;

        var tdSize = document.createElement('td');
        tdSize.className = 'size';
        tdSize.textContent = formatSize(entry.file.size);

        var tdProgress = document.createElement('td');
        tdProgress.className = 'file-progress';
        tdProgress.style.minWidth = '150px';
        var progressDiv = document.createElement('div');
        progressDiv.className = 'progress';
        progressDiv.setAttribute('role', 'progressbar');
        progressDiv.setAttribute('aria-valuemin', '0');
        progressDiv.setAttribute('aria-valuemax', '100');
        progressDiv.setAttribute('aria-valuenow', '0');
        var progressBar = document.createElement('div');
        progressBar.className = 'progress-bar bg-success';
        progressBar.style.width = '0%';
        progressDiv.appendChild(progressBar);
        tdProgress.appendChild(progressDiv);

        var tdAction = document.createElement('td');
        var removeBtn = document.createElement('button');
        removeBtn.type = 'button';
        removeBtn.className = 'btn btn-sm btn-outline-danger btn-remove';
        var removeIcon = document.createElement('i');
        removeIcon.className = 'fa-solid fa-xmark';
        removeBtn.appendChild(removeIcon);
        removeBtn.addEventListener('click', function () {
            var idx = pendingFiles.indexOf(entry);
            if (idx !== -1) pendingFiles.splice(idx, 1);
            tr.remove();
        });
        tdAction.appendChild(removeBtn);

        tr.appendChild(tdName);
        tr.appendChild(tdSize);
        tr.appendChild(tdProgress);
        tr.appendChild(tdAction);
        return tr;
    }

    function updateRowProgress(row, pct) {
        var bar = row.querySelector('.progress-bar');
        bar.style.width = pct + '%';
        row.querySelector('.progress').setAttribute('aria-valuenow', pct);
    }

    // Upload files sequentially (matching original blueimp sequentialUploads:true)
    function startUpload() {
        if (uploading || pendingFiles.length === 0) return;
        uploading = true;
        clearMessages();
        btnUpload.disabled = true;
        globalProgress.style.display = 'block';

        var totalFiles = pendingFiles.length;
        var completedFiles = 0;

        function uploadNext() {
            if (pendingFiles.length === 0) {
                uploading = false;
                btnUpload.disabled = false;
                updateGlobalProgress(100);
                return;
            }
            var entry = pendingFiles.shift();
            uploadSingleFile(entry, function () {
                completedFiles++;
                updateGlobalProgress(Math.round((completedFiles / totalFiles) * 100));
                uploadNext();
            });
        }

        uploadNext();
    }

    function updateGlobalProgress(pct) {
        var bar = globalProgress.querySelector('.progress-bar');
        bar.style.width = pct + '%';
        globalProgress.setAttribute('aria-valuenow', pct);
    }

    // Uses XMLHttpRequest so CSRFGuard auto-injects CSRF token headers
    function uploadSingleFile(entry, onComplete) {
        var formData = new FormData();
        formData.append('filedata', entry.file);
        formData.append('destination', document.getElementById('destination').value);
        formData.append('destFolder', document.getElementById('destFolder').value);
        formData.append('provider', document.getElementById('provider').value);
        formData.append('queue', document.getElementById('queue').value);

        var xhr = new XMLHttpRequest();

        xhr.upload.addEventListener('progress', function (e) {
            if (e.lengthComputable && entry.row) {
                updateRowProgress(entry.row, Math.round((e.loaded / e.total) * 100));
            }
        });

        xhr.addEventListener('load', function () {
            if (entry.row) updateRowProgress(entry.row, 100);

            if (xhr.status >= 200 && xhr.status < 300) {
                try {
                    var result = JSON.parse(xhr.responseText);
                    // Server returns JSON array: [{name, size, error}]
                    var item = Array.isArray(result) ? result[0] : result;
                    if (item && item.error) {
                        showError(entry.file.name + ': ' + item.error);
                        markRowError(entry.row);
                    } else if (item && item.name) {
                        showSuccess(item.name);
                        markRowDone(entry.row);
                    }
                } catch (e) {
                    showError(entry.file.name + ': <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.errors.upload.failed" />');
                    markRowError(entry.row);
                }
            } else {
                showError(entry.file.name + ': <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.errors.upload.failed" /> (HTTP ' + xhr.status + ')');
                markRowError(entry.row);
            }
            onComplete();
        });

        xhr.addEventListener('error', function () {
            showError(entry.file.name + ': <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.errors.upload.failed" />');
            if (entry.row) markRowError(entry.row);
            onComplete();
        });

        xhr.open('POST', uploadUrl, true);
        xhr.send(formData);
    }

    function markRowDone(row) {
        if (!row) return;
        var btn = row.querySelector('.btn-remove');
        if (btn) btn.remove();
        var bar = row.querySelector('.progress-bar');
        if (bar) bar.textContent = '\u2713';
    }

    function markRowError(row) {
        if (!row) return;
        var bar = row.querySelector('.progress-bar');
        if (bar) {
            bar.classList.remove('bg-success');
            bar.classList.add('bg-danger');
            bar.style.width = '100%';
            bar.textContent = '\u2717';
        }
    }

    // File input change handler
    fileInput.addEventListener('change', function () {
        addFiles(this.files);
        this.value = '';
    });

    // Upload button
    btnUpload.addEventListener('click', startUpload);

    // Reset button
    btnReset.addEventListener('click', function () {
        pendingFiles = [];
        while (fileList.firstChild) fileList.removeChild(fileList.firstChild);
        clearMessages();
        globalProgress.style.display = 'none';
        updateGlobalProgress(0);
        fileInput.value = '';
    });

    // Drag and drop
    ['dragenter', 'dragover'].forEach(function (evt) {
        dropArea.addEventListener(evt, function (e) {
            e.preventDefault();
            e.stopPropagation();
            dropArea.classList.add('drag-over');
        });
    });
    ['dragleave', 'drop'].forEach(function (evt) {
        dropArea.addEventListener(evt, function (e) {
            e.preventDefault();
            e.stopPropagation();
            dropArea.classList.remove('drag-over');
        });
    });
    dropArea.addEventListener('drop', function (e) {
        if (e.dataTransfer && e.dataTransfer.files.length > 0) {
            addFiles(e.dataTransfer.files);
        }
    });

    // Click error/success messages to dismiss
    msgError.addEventListener('click', function () { this.style.display = 'none'; });
    msgSuccess.addEventListener('click', function () { this.style.display = 'none'; });
})();
</script>
</body>
</html>
