<%--

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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<!DOCTYPE html>
<%@ page import="io.github.carlos_emr.carlos.eform.data.*, io.github.carlos_emr.carlos.eform.*, java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.eform.EFormUtil" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    String orderByRequest = request.getParameter("orderby");
    String orderBy = "";
    if (orderByRequest == null) orderBy = EFormUtil.DATE;
    else if (orderByRequest.equals("form_subject")) orderBy = EFormUtil.SUBJECT;
    else if (orderByRequest.equals("form_name")) orderBy = EFormUtil.NAME;
    else if (orderByRequest.equals("file_name")) orderBy = EFormUtil.FILE_NAME;
%>
<html>
    <head>
    <title>E-Form Manager</title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css">
        <link rel="stylesheet" href="<%= request.getContextPath() %>/css/fontawesome-all.min.css">
        <script type="text/javascript" src="<%= request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css">
        <script type="text/javascript" src="<%= request.getContextPath() %>/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>


    <script language="javascript">
        function checkFormAndDisable() {
            if (document.forms[0].formHtml.value == "") {
                alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.msgFileMissing"/>");
            } else {
                document.forms[0].subm.value = "<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadimages.processing"/>";
                document.forms[0].subm.disabled = true;
                document.forms[0].submit();
            }
        }

        function newWindow(url, id) {
            Popup = window.open(url, id, 'toolbar=no,location=no,status=yes,menubar=no, scrollbars=yes,resizable=yes,width=900,height=600,left=200,top=0');
        }

        function confirmNDelete(fid) {
            if (confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.confirmDelete"/>")) {
                var form = document.createElement('form');
                form.method = 'post';
                form.action = '<%= request.getContextPath() %>/eform/delEForm.do';
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = 'fid';
                input.value = fid;
                form.appendChild(input);
                document.body.appendChild(form);
                form.submit();
            }
        }

        var normalStyle = "eformInputHeading"
        var activeStyle = "eformInputHeading eformInputHeadingActive"

        function closeInputs() {
            document.getElementById("uploadDiv").style.display = 'none';
            document.getElementById("importDiv").style.display = 'none';
            document.getElementById("uploadHeading").className = normalStyle;
            document.getElementById("importHeading").className = normalStyle;
        }

        function openUpload() {
            closeInputs();
            document.getElementById("uploadHeading").className = activeStyle;
            document.getElementById("uploadDiv").style.display = 'block';
        }

        function openImport() {
            closeInputs();
            document.getElementById("importHeading").className = activeStyle;
            document.getElementById("importDiv").style.display = 'block';
        }

        function openDownload() {
            closeInputs();
            document.getElementById("downloadHeading").className = activeStyle;
            document.getElementById("downloadDiv").style.display = 'block';
        }

        function doOnLoad() {
            <%String input = request.getParameter("input");
            if (input == null) input = (String) request.getAttribute("input");
            if (input != null && input.equals("import")) {%>
            openImport();
            <%}%>
        }

        $(function () {
            document.querySelectorAll('[data-bs-toggle="popover"]').forEach(function(el) { new bootstrap.Popover(el); });
        });

    </script>

    <style>
        div#eformTbl_wrapper table tr td a,
        div#eformTbl_wrapper table tr td:nth-child(3){
            text-wrap: auto;
            word-wrap: anywhere;
            word-break: break-word;
        }
    </style>
</head>
    <body>


    <%@ include file="efmTopNav.jspf" %>

    <h3 style='display:inline;padding-right:10px'><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.msgLibrary"/></h3> <a
            href="<%= request.getContextPath() %>/eform/efmformmanagerdeleted.jsp" class="contentLink">View Deleted
        <!--<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnDeleted"/>--> </a>


    <ul class="nav nav-pills" id="eformOptions">
        <li class="nav-item"><a class="nav-link active" data-bs-toggle="pill" href="#upload">Upload</a></li>
        <li class="nav-item"><a class="nav-link" data-bs-toggle="pill" href="#import">Import</a></li>
    </ul>

    <div class="tab-content">
        <div class="tab-pane show active" id="upload">
            <div class="row">
                <div class="card card-body bg-body-tertiary">

                    <iframe id="uploadFrame" name="uploadFrame" frameborder="0" width="100%" height="auto"
                            scrolling="no" src="<%=request.getContextPath()%>/eform/partials/upload.jsp"></iframe>

                </div>
            </div>
        </div>

        <div class="tab-pane" id="import">
            <div class="row">
                <div class="card card-body bg-body-tertiary">

                    <iframe id="importFrame" name="importFrame" frameborder="0" width="100%" height="auto"
                            src="<%=request.getContextPath()%>/eform/partials/import.jsp"></iframe>

                </div>
            </div>
        </div>
    </div><!-- tab content eformOptions -->

    <div class="row" style="overflow-x:scroll;">
        <table class="table table-sm table-striped" id="eformTbl">
            <thead>
            <tr>
                <th></th>

                <th><a href="<%= request.getContextPath() %>/eform/efmformmanager.jsp?orderby=form_name"
                       class="contentLink"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnFormName"/></a></th>
                <th><a href="<%= request.getContextPath() %>/eform/efmformmanager.jsp?orderby=form_subject"
                       class="contentLink"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnSubject"/></a></th>

                <th><a href="<%= request.getContextPath() %>/eform/efmformmanager.jsp?"
                       class="contentLink"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnDate"/></a></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnTime"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnRoleType"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.msgAction"/></th>
            </tr>
            </thead>

            <tbody>
            <%
                ArrayList<HashMap<String, ? extends Object>> eForms = EFormUtil.listEForms(orderBy, EFormUtil.CURRENT);
                for (int i = 0; i < eForms.size(); i++) {
                    HashMap<String, ? extends Object> curForm = eForms.get(i);
            %>
            <tr>
                <td><%if (curForm.get("formFileName") != null && curForm.get("formFileName").toString().length() != 0) {%><i
                        class="fa-solid fa-file" title="<%=curForm.get("formFileName").toString()%>"></i><%}%></td>
                <td title="<%=curForm.get("formName")%>">
                    <a href="#"
                       onclick="newWindow('<%= request.getContextPath() %>/eform/efmshowform_data.jsp?fid=<%=curForm.get("fid")%>', '<%="Form"+i%>'); return false;"><%=curForm.get("formName")%>
                    </a>
                </td>
                <td><%=curForm.get("formSubject")%>
                </td>
                <td><%=curForm.get("formDate")%>
                </td>
                <td><%=curForm.get("formTime")%>
                </td>
                <td><%=curForm.get("roleType")%>
                </td>
                <td>

                    <div class="btn-group">
                        <a class="btn btn-link contentLink"
                           href="<%= request.getContextPath() %>/eform/efmformmanageredit.jsp?fid=<%= curForm.get("fid")%>"
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.editform"/><%=curForm.get("formName")%>'><i
                                class="fa-solid fa-pencil" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.editform"/>"></i></a>


                        <a class="btn btn-link"
                           href='<%= request.getContextPath() %>/eform/manageEForm.do?method=exportEForm&fid=<%=curForm.get("fid")%>'
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnExport"/> <%=curForm.get("formName")%>'><i
                                class="fa-solid fa-download" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnExport"/>"></i></a>


                        <a class="btn btn-link contentLink"
                           href='javascript:void(0);' onclick='confirmNDelete("<%=curForm.get("fid")%>")'
                           title='<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnDelete"/> <%=curForm.get("formName")%>'><i
                                class="fa-solid fa-trash" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadhtml.btnDelete"/>"></i></a>
                    </div>
                </td>

            </tr>
            <% } %>
            </tbody>
        </table>
    </div>
    <%@ include file="efmFooter.jspf" %>

    <script>
        if (typeof registerFormSubmit === 'function') {
            registerFormSubmit('eformImportForm', 'dynamic-content');
        }

        $('#eformTbl').DataTable({
            "paging": false,
            "columnDefs": [{"orderable": false, "targets": [0]}]
        });
    </script>
    </body>
</html>
