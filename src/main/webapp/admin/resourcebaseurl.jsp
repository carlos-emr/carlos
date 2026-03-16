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
<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.text.*, java.lang.*,java.net.*"
         errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%
    UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
    UserProperty upUrl = propertyDao.getProp("resource_baseurl");
    UserProperty upHtml = propertyDao.getProp("resource_helphtml");

    Properties oscarVariables = OscarProperties.getInstance();

    String checkedWebsite = "";
    String checkedDetails = "";

    //get value from property
    String resource_baseurl_value = oscarVariables.getProperty("resource_base_url");
    String resource_helpHtml_value = "";

    if (upUrl != null) {
        //update with value from property table
        resource_baseurl_value = upUrl.getValue();
        checkedWebsite = "checked";
    } else if (upHtml != null) {
        //update with value from property table
        resource_helpHtml_value = upHtml.getValue();
        checkedDetails = "checked";
    } else {
        checkedWebsite = "checked";
        checkedDetails = "";
    }

    //delete if exists before saving
    if (request.getParameter("websiteSave") != null || request.getParameter("detailsSave") != null) {
        if (upUrl != null) {
            propertyDao.delete(upUrl);
        }

        if (upHtml != null) {
            propertyDao.delete(upHtml);
        }
    }

    //save
    if (request.getParameter("websiteSave") != null) {
        propertyDao.saveProp("resource_baseurl", request.getParameter("resource_baseurl"));
        checkedWebsite = "checked";
        checkedDetails = "";
        resource_helpHtml_value = "";
        resource_baseurl_value = request.getParameter("resource_baseurl");
    } else if (request.getParameter("detailsSave") != null) {
        propertyDao.saveProp("resource_helpHtml", request.getParameter("resource_helpHtml"));
        checkedWebsite = "";
        checkedDetails = "checked";

        resource_helpHtml_value = request.getParameter("resource_helpHtml");
    }

%>
<!DOCTYPE html>
<html lang="en">
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.resourcebaseurl.title"/></title>


    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" type="text/css" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <link href="<%=request.getContextPath() %>/library/toastui/toastui-editor.min.css" rel="stylesheet">

    <style>
        .info {
            background-color: #d9edf7;
        }

        .alert-plain {
            color: #333;
            background-color: #f2f2f2;
            border: 0;
        }
    </style>
</head>
<body>

<div class="container-fluid">

    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnBaseURLSetting"/></h3>

    <%if (request.getParameter("websiteSave") != null) {%>
    <div class="alert alert-success">
        <strong>Success!</strong> Help link has been saved.
    </div>
    <%}%>

    <%if (request.getParameter("detailsSave") != null) {%>
    <div class="alert alert-success">
        <strong>Success!</strong> Your new help details has been saved.
    </div>
    <%}%>

    <!-- Help Link - Website -->
    <div class="row card card-body bg-body-tertiary" id="websiteDiv" style="background-color:">
        <div class="col-md-1" style="background-color:">
            <input type="radio" name="helpOption" class="helpOption" value="website" <%=checkedWebsite%>>
        </div><!-- col-md-2 -->

        <div class="col-md-8" style="background-color:">
            <form method="post" name="baseurl" id="websiteForm" action="resourcebaseurl.jsp" class="d-flex flex-wrap align-items-center gap-2">

                <h4>Website</h4>
                <!--<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.resourcebaseurl.formBaseUrl"/><br>-->
                <input type="text" name="resource_baseurl" style="width:100%;margin-bottom:10px"
                       placeholder="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.resourcebaseurl.formBaseUrlExample"/>"
                       value="<%if(resource_baseurl_value!=null){ out.print(resource_baseurl_value);}%>">
                <div class="col-md-8">
                    <input type="submit" class="btn float-end" name="websiteSave" id="websiteSave"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.resourcebaseurl.btnSave"/>">
                </div>

            </form>
        </div><!-- col-md-8 -->
    </div>

    <h4 class="text-muted text-center"><em>~ or ~</em></h4>

    <!-- Help Link - Details -->
    <div class="row card card-body bg-body-tertiary" id="detailsDiv">
        <div class="col-md-1" style="background-color:">
            <input type="radio" name="helpOption" class="helpOption" value="details" <%=checkedDetails%>>
        </div><!-- col-md-2 -->

        <div class="col-md-8" style="background-color:">
            <form method="post" name="baseurl" id="detailsForm" action="resourcebaseurl.jsp">
                <h4>Details</h4>
                <input type="hidden" name="resource_helpHtml" id="resource_helpHtml_hidden"/>
                <div id="resource_helpHtml_editor" style="width:100%;min-height:160px"></div>
                <div class="col-md-8" style="padding-left:0px;padding-right:0px;">
                    <div class="col-md-6" id="chars">
                        <div class='alert alert-plain'>Character Limit = 2000</div>
                    </div>
                    <input type="submit" class="btn float-end" name="detailsSave" id="detailsSave"
                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.resourcebaseurl.btnSave"/>">
                </div>
            </form>
        </div><!-- col-md-8 -->
    </div>

</div><!-- container fluid -->

<script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
<script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
<script src="<%=request.getContextPath() %>/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
<script src="<%=request.getContextPath() %>/library/dompurify/purify.min.js"></script>
<script src="<%=request.getContextPath() %>/library/toastui/toastui-editor-all.min.js"></script>

<script>
    var Editor = toastui.Editor;
    var editor = new Editor({
        el: document.querySelector('#resource_helpHtml_editor'),
        height: '200px',
        initialEditType: 'wysiwyg',
        initialValue: '',
        hideModeSwitch: true,
        toolbarItems: [['bold', 'italic', 'strike'], ['ul', 'ol'], ['link']],
        customHTMLSanitizer: function(html) {
            return DOMPurify.sanitize(html);
        }
    });

    <%-- Load existing HTML content into the editor (OWASP-encoded for JS context, DOMPurify-sanitized by editor) --%>
    <% if (resource_helpHtml_value != null && !resource_helpHtml_value.isEmpty()) { %>
    editor.setHTML('<%= Encode.forJavaScript(resource_helpHtml_value) %>');
    <% } %>

    <%-- Sync editor content to hidden input before form submit --%>
    document.getElementById('detailsForm').addEventListener('submit', function() {
        document.getElementById('resource_helpHtml_hidden').value = editor.getHTML();
    });

    <%-- Character count on editor change --%>
    editor.on('change', function() {
        var html = editor.getHTML();
        var c = html.length;
        if (c > 2000) {
            $("#chars").html("<div class='alert'><strong>Warning!</strong> Character Limit = 2000. Character Count = " + c + "</div>");
            $("#detailsSave").prop("disabled", true);
        } else {
            $("#chars").html("<div class='alert alert-success'><strong>Good!</strong> Character Limit = 2000. Character Count = " + c + "</div>");
            $("#detailsSave").prop("disabled", false);
        }
    });

    helpOptionCheck();

    $(".helpOption").click(function () {
        helpOptionCheck();
    });

    function helpOptionCheck() {
        if ($("input[type='radio'].helpOption").is(':checked')) {

            option = $("input[type='radio'].helpOption:checked").val();

            if (option == "website") {
                $("#websiteForm :input").prop("disabled", false);
                $("#detailsSave").prop("disabled", true);

                $("#websiteDiv").addClass('info');
                $("#detailsDiv").removeClass('info');

                $("#websiteSave").addClass('btn-primary');
                $("#detailsSave").removeClass('btn-primary');

            } else {
                $("#websiteForm :input").prop("disabled", true);
                $("#detailsSave").prop("disabled", false);

                $("#websiteDiv").removeClass('info');
                $("#detailsDiv").addClass('info');

                $("#websiteSave").removeClass('btn-primary');
                $("#detailsSave").addClass('btn-primary');
            }
        }
    }


    $(document).ready(function () {
        parent.parent.resizeIframe($('html').height());
    });
</script>
</body>
</html>
