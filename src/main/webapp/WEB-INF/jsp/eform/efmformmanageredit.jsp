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
<%@ page
        import="io.github.carlos_emr.carlos.eform.data.*, io.github.carlos_emr.carlos.eform.*, java.util.*, io.github.carlos_emr.carlos.util.*, org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="io.github.carlos_emr.carlos.eform.EFormUtil" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%
    HashMap<String, Object> curform = new HashMap<String, Object>();
    HashMap<String, String> errors = new HashMap<String, String>();

    if (request.getAttribute("submitted") != null) {
        curform = (HashMap<String, Object>) request.getAttribute("submitted");
        errors = (HashMap<String, String>) request.getAttribute("errors");
    } else if (request.getParameter("fid") != null) {
        String curfid = request.getParameter("fid");
        curform = EFormUtil.loadEForm(curfid);
    }

    //remove "null" values
    if (curform.get("fid") == null) curform.put("fid", "");
    if (curform.get("formName") == null) curform.put("formName", "");
    if (curform.get("formSubject") == null) curform.put("formSubject", "");
    if (curform.get("formFileName") == null) curform.put("formFileName", "");
    if (curform.get("roleType") == null) curform.put("roleType", "");

    if (request.getParameter("formHtmlG") != null) {
        //load html from hidden form from eformGenerator,the html is then injected into edit-eform
        curform.put("formHtml", StringEscapeUtils.unescapeHtml4(request.getParameter("formHtmlG")));
    }
    if (curform.get("formDate") == null) curform.put("formDate", "--");
    if (curform.get("formTime") == null) curform.put("formTime", "--");

    if (curform.get("showLatestFormOnly") == null) curform.put("showLatestFormOnly", false);
    if (curform.get("patientIndependent") == null) curform.put("patientIndependent", false);

    String formHtml = SafeEncode.forHtml((String) curform.get("formHtml"));
    if (formHtml == null) {
        formHtml = "";
    }
%>
<!DOCTYPE html>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="eform.edithtml.msgEditEform"/></title>

        <style>
            .input-error {
                border-color: rgba(229, 103, 23, 0.8) !important;
                box-shadow: 0 1px 1px rgba(229, 103, 23, 0.075) inset, 0 0 8px rgba(229, 103, 23, 0.6) !important;
                outline: 0 none !important;
            }

            #popupDisplay {
                display: inline-block;
            }

            #panelDisplay {
                display: none;
            }
        </style>

        <script type="text/javascript" language="JavaScript">
            function openLastSaved() {
                window.open('<%=request.getContextPath()%>/eform/efmshowform_data?fid=<carlos:encode value='<%= (String) curform.get("fid") %>' context="uriComponent"/>', 'PreviewForm', 'toolbar=no, location=no, status=yes, menubar=no, scrollbars=yes, resizable=yes, width=700, height=600, left=300, top=100');
            }

            //using this to check if page is being viewing in admin panel or in popup
            var elementExists = document.getElementById("dynamic-content");

            if (elementExists) {
                document.getElementById("popupDisplay").style.display = 'none';
                document.getElementById("panelDisplay").style.display = 'inline';
            } else {
                document.write('<link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">');
            }

            <% if ((request.getAttribute("success") != null) && (errors.size() == 0)) { %>
            if (elementExists == null) {
                window.location.href = '<%=request.getContextPath()%>/eform/efmformmanager';
            }
            <%}%>
        </script>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

    </head>

    <body id="eformBody">

    <%@ include file="efmTopNav.jspf" %>

    <%if (request.getParameter("fid") != null) {%>
    <h3><fmt:message key="eform.edithtml.msgEditEform"/></h3>
    <%} else {%>
    <h3>Create New eForm</h3>
    <%}%>

    <form action="<%=request.getContextPath()%>/eform/editForm" method="POST" enctype="multipart/form-data"
          id="editform" name="eFormEdit">

        <div class="card card-body bg-body-tertiary" style="position: relative;">

            <% if ((request.getAttribute("success") != null) && (errors.size() == 0)) { %>
            <div class="alert alert-success">
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                <fmt:message key="eform.edithtml.msgChangesSaved"/>.
            </div>
            <% } %>

            <%
                String formNameMissing = errors.get("formNameMissing");
                if (errors.containsKey("formNameMissing")) {
            %>
            <div class="alert alert-danger">
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                <fmt:message key="<%=formNameMissing%>"/>
            </div>
            <%} else if (errors.containsKey("formNameExists")) {
                String formNameExists = errors.get("formNameExists"); %>
            <div class="alert alert-danger">
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                <fmt:message key="<%=formNameExists%>"/>
            </div>
            <%}%>

            <input type="hidden" name="fid" id="fid" value="<carlos:encode value='<%= (String) curform.get("fid") %>' context="htmlAttribute"/>">

            <% if ((request.getAttribute("success") == null) || (errors.size() != 0)) {%>
            <!--error? -->
            <% } %>

            <!--LAST SAVED-->
            <div style="position:absolute;top:2px;right:4px;">
                <em><fmt:message key="eform.edithtml.msgLastModified"/>:    <carlos:encode value='<%= (String) curform.get("formDate") %>' context="html"/>&nbsp;<carlos:encode value='<%= (String) curform.get("formTime") %>' context="html"/>
                </em>
            </div>

            <!--FORM NAME-->
            <div style="display:inline-block">

                <fmt:message key="eform.uploadhtml.formName"/>:
                <br/>
                <input type="text" name="formName" value="<carlos:encode value='<%= (String) curform.get("formName") %>' context="htmlAttribute"/>"
                       class="<% if (errors.containsKey("formNameMissing") || (errors.containsKey("formNameExists"))) { %> input-error <% } %>"
                       size="30"/>
                <br/>

            </div>

            <!--FORM ADDITIONAL INFO-->
            <div style="display:inline-block">
                <fmt:message key="eform.uploadhtml.formSubject"/>:<br/>
                <input type="text" name="formSubject" value="<carlos:encode value='<%= (String) curform.get("formSubject") %>' context="htmlAttribute"/>" size="30"/><br/>
            </div>

            <!--ROLE TYPE-->
            <div style="display:inline-block">
                <fmt:message key="eform.uploadhtml.btnRoleType"/><br/>
                <select name="roleType">
                    <option value="">- select one -</option>
                    <% ArrayList roleList = EFormUtil.listSecRole();
                        String selected = "";
                        for (int i = 0; i < roleList.size(); i++) {
                            selected = "";
                            if (roleList.get(i).equals(curform.get("roleType"))) {
                                selected = "selected";
                            }
                    %>
                    <option value="<carlos:encode value='<%= (String) roleList.get(i) %>' context="htmlAttribute"/>" <%= selected%>><carlos:encode value='<%= (String) roleList.get(i) %>' context="html"/>
                    </option>

                    <%} %>
                </select><br/>
            </div>

            <!--PATIENT INDEPENDANT-->
            <div style="display:inline-block">
                <fmt:message key="eform.uploadhtml.showLatestFormOnly"/> <input type="checkbox"
                                                                                 name="showLatestFormOnly"
                                                                                 value="true" <%= (Boolean) curform.get("showLatestFormOnly") ? "checked" : "" %> />
                <br/>
                <fmt:message key="eform.uploadhtml.patientIndependent"/> <input type="checkbox"
                                                                                 name="patientIndependent"
                                                                                 value="true" <%= (Boolean) curform.get("patientIndependent") ? "checked" : "" %> /><br/>
            </div>

            <br/>
            <fmt:message key="eform.edithtml.msgEditHtml"/>:<br/>
            <textarea wrap="off" name="formHtml" style="" class="form-control" rows="40"><%= formHtml%></textarea><br/>

            <p>
            <div id="panelDisplay">
                <a href="<%=request.getContextPath()%>/eform/efmformmanager" class="btn contentLink">
                    <i class="fa-solid fa-circle-arrow-left"></i> Back to eForm Library
                    <!--<fmt:message key="eform.edithtml.msgBackToForms"/>-->
                </a>
                <input type="button" class="btn btn-secondary"
                       value="<fmt:message key="eform.edithtml.msgPreviewLast"/>" <% if (curform.get("fid") == null) {%>
                       disabled    <%}%> name="previewlast" onclick="openLastSaved()">
                <a href="<%=request.getContextPath()%>/eform/efmformmanageredit?fid=<carlos:encode value='<%= (String) curform.get("fid") %>' context="uriComponent"/>"
                   class="btn contentLink"> <fmt:message key="eform.edithtml.cancelChanges"/></a>
            </div>

            <a href="#" class="btn btn-secondary" id="popupDisplay" onClick="window.close()">
                <i class="fa-solid fa-circle-arrow-left"></i> Back to eForm Library
                <!--<fmt:message key="eform.edithtml.msgBackToForms"/>-->
            </a>

            <input type="submit" class="btn btn-primary" value="<fmt:message key="eform.edithtml.msgSave"/>"
                   data-bs-loading-text="Saving..." name="savebtn" id="savebtn">

            </p>
        </div>
    </form>


    <%@ include file="efmFooter.jspf" %>

    <script>
        registerFormSubmit('editform', 'dynamic-content');

        $(document).ready(function () {

            $("html, body").animate({scrollTop: 0}, "slow");
            return false;

        });
    </script>


    </body>
</html>
