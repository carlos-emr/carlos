<!DOCTYPE html>
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
<%@page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.*,io.github.carlos_emr.*,java.io.*,java.net.*,io.github.carlos_emr.carlos.util.*,org.apache.commons.io.FileUtils,java.text.SimpleDateFormat,io.github.carlos_emr.carlos.billing.CA.ON.util.EDTFolder,io.github.carlos_emr.carlos.utility.MiscUtils,org.owasp.encoder.Encode"%>
<%@ page import="io.github.carlos_emr.carlos.util.FileSortByDate" %>
<%@ page import="io.github.carlos_emr.carlos.util.zip" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean bodd = false;
    EDTFolder folder = EDTFolder.getFolder(request.getParameter("folder"));
    String folderPath = folder.getPath();
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.backup,_admin.billing" rights="r" reverse="<%=true%>">
    <% response.sendRedirect(request.getContextPath() + "/logout.jsp"); %>
</security:oscarSec>
<jsp:useBean id="oscarVariables" class="java.util.Properties" scope="session"/>
<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.viewMOHFiles"/></title>

    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">

    <script LANGUAGE="JavaScript">
        function viewMOHFile(anchor) {
            var filename = anchor.dataset.filename;
            var form = document.getElementById("form");
            document.getElementById("filename").value = filename;
            var fileType = decodeURIComponent(filename).substring(0, 1).toUpperCase();
            if (decodeURIComponent(filename).substring(decodeURIComponent(filename).length - 4).toLowerCase() == ".zip") {
                alert("Please unzip " + decodeURIComponent(filename) + " before processing.");
                location.href = "<%= request.getContextPath() %>/billing/CA/ON/viewMOHFiles.jsp";
                return;
            } else if (fileType == "P" || fileType == "S") {
                form.action = "<%= request.getContextPath() %>/servlet/io.github.carlos_emr.DocumentUploadServlet";
            } else if (fileType == "L") {
                form.action = "<%= request.getContextPath() %>/billing/CA/ON/billingLreport.jsp";
            } else {
                form.action = "/<%= CarlosProperties.getInstance().getProperty("project_home") %>/oscarBilling/DocumentErrorReportUpload.do";
            }
            form.submit();
        }

        function navigateToFolder(selectEl) {
            var allowed = ["inbox", "outbox", "sent", "archive"];
            var folder = selectEl.options[selectEl.selectedIndex].value;
            if (allowed.indexOf(folder) !== -1) {
                location.href = "<%= request.getContextPath() %>/billing/CA/ON/viewMOHFiles.jsp?folder=" + encodeURIComponent(folder);
            }
        }

        function toggleCheckboxes(el) {
            document.querySelectorAll("input[name='mohFile']").forEach(function (cb) {
                cb.checked = el.checked;
            });
        }

        function checkForm() {
            if (document.querySelectorAll("input[name='mohFile']:checked").length > 0) {
                return true;
            }
            alert("Please select a file first.");
            return false;
        }
    </script>
</head>

<body>
<h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.viewMOHFiles"/></h3>

<div class="container-fluid card card-body bg-body-tertiary">

    <form id="form" method="POST">
        <input type="hidden" id="filename" name="filename" value="">
    </form>

    <% if (folder == EDTFolder.INBOX) { %>
    <form method="POST" action="<%=request.getContextPath()%>/billing/CA/ON/moveMOHFiles.do" onsubmit="return checkForm();" class="d-flex flex-wrap align-items-center gap-2">
    <% } %>

        <% if (folder == EDTFolder.INBOX) {%>
        <input type="submit" value="Archive" class="btn btn-secondary">
        <% } %>

        View:
        <select name="folder" onchange="navigateToFolder(this)">
            <option value="inbox" <% if (folder == EDTFolder.INBOX) {%>selected<%}%>>Inbox</option>
            <option value="outbox" <% if (folder == EDTFolder.OUTBOX) {%>selected<%}%>>Outbox</option>
            <option value="sent" <% if (folder == EDTFolder.SENT) {%>selected<%}%>>Sent</option>
            <option value="archive" <% if (folder == EDTFolder.ARCHIVE) {%>selected<%}%>>Archive</option>
        </select>


        <table class="table table-striped table-hover">
            <thead>
            <tr>
                <% if (folder == EDTFolder.INBOX) {%>
                <th><input type="checkbox" onclick="toggleCheckboxes(this)" title="select all"></th>
                <% } %>
                <th>View File</th>
                <% if (folder.providesAccessToFiles()) {%>
                <th>Download File</th>
                <%}%>
                <th>Date</th>
            </tr>
            </thead>

            <tbody>
            <%
                if (folderPath == null || folderPath.equals("")) {
                    Exception e = new Exception("Unable to find the key ONEDT_" + folder.name() + " in the properties file.  Please check the value of this key or add it if it is missing.");
                    throw e;
                }
                session.setAttribute("backupfilepath", folderPath);

                // unzip any files indicated by <unzipfile>
                String zname = request.getParameter("unzipfile");
                String unzipMSG = "";
                try {
                    if (zname != null && !zname.equals("")) {
                        Boolean unzipDone = zip.unzipXML(folderPath, zname);
                        if (!unzipDone) {
                            unzipMSG = "(Cannot unzip)";
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("viewMOHFiles: unzip file Unhandled exception:", e);
                    unzipMSG = "(Cannot unzip)";
                }

                File f = new File(folderPath);
                File[] contents = null;
                if (f.exists()) {
                    contents = f.listFiles();
                } else {
                    contents = new File[]{};
                }

                Arrays.sort(contents, new FileSortByDate());
                if (contents == null) {
                    Exception e = new Exception("Unable to find any files in the directory " + folderPath + ".  (If this is the incorrect directory, please modify the value of ONEDT_" + folder.name() + " in your properties file to reflect the correct directory).");
                    throw e;
                }
                for (int i = 0; i < contents.length; i++) {
                    bodd = bodd ? false : true;
                    if (contents[i].isDirectory() || contents[i].getName().startsWith(".")) continue;
                    if (contents[i].getName().endsWith(".sh")) continue;
                    String archiveElement = "<td ><input type='checkbox' name='mohFile' value='" + Encode.forHtmlAttribute(URLEncoder.encode(contents[i].getName(), StandardCharsets.UTF_8)) + "' title='select to archive'/></td>";
                    if (folder == EDTFolder.INBOX || folder == EDTFolder.ARCHIVE) {
                        out.println("<tr>" + (folder == EDTFolder.INBOX ? archiveElement : "") + "<td><a href='#' onclick='viewMOHFile(this)' data-filename='" + Encode.forHtmlAttribute(URLEncoder.encode(contents[i].getName(), StandardCharsets.UTF_8)) + "'>" + Encode.forHtml(contents[i].getName()) + unzipMSG + "</a></td>");
                        out.println("<td><a href=\"" + Encode.forHtmlAttribute(request.getContextPath() + "/servlet/BackupDownload?filename=" + URLEncoder.encode(contents[i].getName(), StandardCharsets.UTF_8)) + "\">Download</a></td>");
                    } else {
                        out.println("<tr><td>" + Encode.forHtml(contents[i].getName()) + "</td>");
                    }
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                    Date d = new Date(contents[i].lastModified());
                    out.println("<td align='right'>" + sdf.format(d) + "</td></tr>"); //+System.getProperty("file.separator")
                }
            %>
            </tbody>
        </table>

        <% if (contents.length > 20) { %>

        <% if (folder == EDTFolder.INBOX) {%>
        <input type="submit" value="Archive" class="btn btn-secondary">
        <% } %>

        <select name="folder" onchange="navigateToFolder(this)">
            <option value="inbox" <% if (folder == EDTFolder.INBOX) {%>selected<%}%>>Inbox</option>
            <option value="outbox" <% if (folder == EDTFolder.OUTBOX) {%>selected<%}%>>Outbox</option>
            <option value="sent" <% if (folder == EDTFolder.SENT) {%>selected<%}%>>Sent</option>
            <option value="archive" <% if (folder == EDTFolder.ARCHIVE) {%>selected<%}%>>Archive</option>
        </select>


        <% } %>
        <% if (folder == EDTFolder.INBOX) { %></form> <% } %>
</div><!--container-->
</body>
</html>