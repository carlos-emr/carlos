<!DOCTYPE html>
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

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.demographic.data.*,java.util.*,ca.openosp.openo.demographic.pageUtil.Util" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProgramDao, ca.openosp.openo.utility.SpringUtils,ca.openosp.openo.PMmodule.model.Program" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%
    ProgramDao programDao = (ProgramDao) SpringUtils.getBean(ProgramDao.class);
    List<Program> programs = programDao.getAllPrograms();
    List<Program> courses = new ArrayList<Program>();
    for (Program p : programs) {
        if (p.getSiteSpecificField() != null && p.getSiteSpecificField().equals("course")) {
            courses.add(p);
        }
    }

%>
<html>
    <script src="${pageContext.request.contextPath}/csrfguard"></script>
    <head>
        <!--I18n-->
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DemoImport"/></title>
        <link href="<%=request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet">

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

        <link rel="stylesheet" type="text/css"
              href="<%=request.getContextPath() %>/js/jquery_css/smoothness/jquery-ui-1.10.2.custom.min.css"/>
        <script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery-1.9.1.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/jquery-ui-1.10.2.custom.min.js"></script>


        <script>
            $(document).ready(function () {
                $("form[name='ImportDemographicDataForm']").submit(function (event) {
                    event.preventDefault();

                    const files = $("#importFile")[0].files;

                    if (files.length === 0) {
                        alert("Please select at least one file.");
                        return;
                    }

                    $('#result').empty();
                    uploadFiles(files, this).then(() => {
                        $('#importFile').val('');
                    }).catch(error => {
                        console.error("Error in file upload:", error);
                    });

                });
            });

            async function uploadFiles(files, form) {
                // Loop through each selected file
                for (let i = 0; i < files.length; i++) {
                    ShowSpin(true);
                    const cloneForm = $(form).clone();
                    const file = files[i];
                    let newImportFile = $("<input type='file' name='importFile'>");
                    let newFileList = new DataTransfer();
                    newFileList.items.add(file);
                    newImportFile[0].files = newFileList.files;
                    cloneForm.find("#importFile").replaceWith(newImportFile);

                    try {
                        await uploadFile(file, cloneForm);
                    } catch (error) {
                        //console.error("Error in AJAX call:", error);
                    }
                }
            }

            function uploadFile(file, formData) {
                return new Promise((resolve, reject) => {
                    $.ajax({
                        url: formData.attr('action'),
                        type: "POST",
                        data: new FormData(formData[0]),
                        processData: false,
                        contentType: false,
                        success: function (response) {
                            HideSpin();
                            const jsondata = JSON.parse(response.substring(response.indexOf('{'), response.indexOf('}') + 1));
                            showResponse(file.name, jsondata.warnings, jsondata.importLog);
                            resolve(jsondata);
                        },
                        error: function (error) {
                            HideSpin();
                            showError(file.name, error.responseText);
                            reject(error);
                        }
                    });
                });
            }

            function showResponse(fileName, warnings, importLog) {
                const resultDiv = $('<div>');

                resultDiv.append($('<h4>').text('File Name: ' + fileName));

                resultDiv.append($('<h5>').text('Imported Successfully').css('color', 'green'));

                if (warnings && warnings.length > 0) {
                    resultDiv.append($('<h5>').text('Warnings:'));
                    const warningsList = $('<ul>');
                    warnings.forEach(warning => {
                        warningsList.append($('<li>').text(warning));
                    });
                    resultDiv.append(warningsList);
                }

                resultDiv.append($('<a>').attr('href', '<%=request.getContextPath() %>/form/importLogDownload.do?importlog=' + encodeURIComponent(importLog)).attr('target', '_blank').text('Download Import Event Log'));
                resultDiv.append($('<hr>'));

                $('#result').append(resultDiv);
            }

            function showError(fileName, responseText) {
                const errorDiv = $('<div>');

                errorDiv.append($('<h4>').text('File Name: ' + fileName));

                errorDiv.append($('<h5>').text('500 Server Error: Invalid file').css('color', 'red'));
                errorDiv.append($('<hr>'));

                $('#result').append(errorDiv);
            }

            $(document).ready(function () {
                $("#uploadWarn").tooltip();
            });
        </script>

    </head>
    <jsp:include page="/images/spinner.jsp" flush="true"/>
    <body vlink="#0000FF">

    <%
        OscarProperties op = OscarProperties.getInstance();
        String learningEnabled = op.getProperty("OSCAR_LEARNING");
        if (!Util.checkDir(op.getProperty("TMP_DIR"))) { %>
    <p>
    <h2>Error! Cannot perform demographic import. Please contact support.</h2>

    <%
    } else {
    %>

    <div class="container-fluid well">
        <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DemoImport"/></h3>

        <form name="ImportDemographicDataForm" action="${pageContext.request.contextPath}/form/importUpload.do" method="POST"
                   enctype="multipart/form-data">
        <p><input type="file" name="importFile" id="importFile" multiple="multiple"/>
            <span id="uploadWarn" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.uploadWarningBody"/>"
                  style="vertical-align:middle;font-family:arial;font-size:20px;font-weight:bold;color:#ABABAB;cursor:pointer"><img
                    border="0" src="<%= request.getContextPath() %>/images/icon_alertsml.gif"/></span></span>

        </p>
                <%if(learningEnabled != null && learningEnabled.equalsIgnoreCase("yes")) { %>
        <!-- Drop Down box of courses -->
        Course:&nbsp;<select name="courseId" id="courseId">
        <option value="0">Choose One</option>
                <%for(Program course:courses) { %>
        <option value="<%=course.getId().intValue()%>"><%=course.getName()%>
        </option>
                <% } %>
        </select><br/>
        Timeshift (in days +/-):&nbsp;<input type="text" name="timeshiftInDays" value="0" size="5"/></br/>
                <%} %>
        If patient's providers do not have OHIP numbers:<br>
        <input type="radio" name="matchProviderNames" value="true" checked/>
        Match providers in database by first and last names (Recommended)
        <br>
        <input type="radio" name="matchProviderNames" value="false"/>
        Import as new - same provider may have multiple entries
        <br><br>
        <p><input class="btn btn-primary" type="submit" name="Submit" value="Import (EMR DM 5.0)"></p>
        </form>

        <div id="result"></div>

                <% } %>
        <script src="<%=request.getContextPath() %>/js/bootstrap.min.js"></script>

    </body>
</html>
