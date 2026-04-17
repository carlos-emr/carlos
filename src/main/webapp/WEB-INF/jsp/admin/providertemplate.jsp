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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_newCasemgmt.templates" rights="w" reverse="true">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_newCasemgmt.templates");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    String curUser_no = (String) request.getAttribute("curUser_no");
%>
<%@ page import="java.util.*, io.github.carlos_emr.carlos.commn.model.EncounterTemplate" errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <title><fmt:message key="admin.providertemplate.title"/></title>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

        <script>
            function setfocus() {
                this.focus();
                document.template.name.focus();
            }

            function idExists(id) {
                var element = document.getElementById(id);
                if (typeof (element) != 'undefined' && element != null) {
                    return true;
                }
                return false;
            }

            function hideExit() {
                var isInIFrame = (window.location != window.parent.location);
                if (isInIFrame == true && idExists('exit-btn')) {
                    document.getElementById('exit-btn').style.display = "none";
                }
            }
        </script>

    </head>
    <body onLoad="setfocus(),hideExit();">

    <div class="container-fluid">
        <div class="row">
            <div class="col-md-12">
                <!--Body content-->

                <h3><fmt:message key="admin.providertemplate.msgTitle"/></h3>

                <div class="card card-body bg-body-tertiary">
                    <form name="edittemplate" method="post" action="${pageContext.request.contextPath}/admin/ProviderTemplate" class="d-flex flex-wrap align-items-center gap-2">
                        <!--<fmt:message key="admin.providertemplate.formEdit"/>:-->
                        Select Template<br>
                        <select name="name">
                            <%
                                @SuppressWarnings("unchecked")
                                List<EncounterTemplate> allTemplates = (List<EncounterTemplate>) request.getAttribute("allTemplates");

                                if (allTemplates != null) {
                                    for (EncounterTemplate encounterTemplate : allTemplates) {
                                        String templateName = Encode.forHtmlAttribute(encounterTemplate.getEncounterTemplateName());
                            %>
                            <option value="<%=templateName%>"><%=templateName%>
                            </option>
                            <%
                                    }
                                }
                            %>
                        </select>
                        <input type="hidden" value="Edit" name="dboperation">
                        <input type="button" value="<fmt:message key="admin.providertemplate.btnEdit"/>"
                               name="dboperation" class="btn btn-secondary"
                               onclick="document.forms['edittemplate'].dboperation.value='Edit'; document.forms['edittemplate'].submit();">
                    </form>

                </div>

                <%
                    EncounterTemplate editTemplate = (EncounterTemplate) request.getAttribute("editTemplate");
                    boolean bEdit = editTemplate != null;
                    String tName = bEdit ? editTemplate.getEncounterTemplateName() : null;
                    String tValue = bEdit ? editTemplate.getEncounterTemplateValue() : null;
                %>

                <div class="card card-body bg-body-tertiary">
                    <form name="template" method="post" action="${pageContext.request.contextPath}/admin/ProviderTemplate">
                        <input type="hidden" name="dboperation" value="">

                        <fmt:message key="admin.providertemplate.formTemplateName"/>:<br>
                        <input type="text" name="name" pattern="^[a-zA-Z0-9\s]+$" value="<%=bEdit && tName != null ? Encode.forHtmlAttribute(tName) : ""%>"
                               class="form-control" maxlength="50"> <!-- match the definition in the schema -->

                        <br><br>

                        <fmt:message key="admin.providertemplate.formTemplateText"/>:<br>
                        <textarea name="value" rows="20" class="form-control"><%=bEdit && tValue != null ? Encode.forHtml(tValue) : ""%></textarea>

                        <br>
                        <input type="button" value="<fmt:message key="admin.providertemplate.btnDelete"/>"
                               class="btn btn-danger"
                               onClick="document.forms['template'].dboperation.value='Delete'; document.forms['template'].submit();">

                        <INPUT TYPE="hidden" NAME="creator" VALUE="<%=curUser_no%>">
                        <input type="button" value="<fmt:message key="admin.providertemplate.btnSave"/>"
                               class="btn btn-primary"
                               onClick="document.forms['template'].dboperation.value=' Save '; document.forms['template'].submit();">


                        <input type="button" name="Button" id="exit-btn"
                               value="<fmt:message key="admin.providertemplate.btnExit"/>" class="btn btn-secondary"
                               onClick="window.close();">

                    </form>
                </div>

            </div><!-- col-md-12 -->
        </div><!-- row fluid -->
    </div><!-- container -->


    </body>
</html>