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
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_newCasemgmt.templates");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    String curUser_no = (String) session.getAttribute("user");
%>
<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*,io.github.carlos_emr.carlos.util.*" errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.EncounterTemplate" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    EncounterTemplateDao encounterTemplateDao = SpringUtils.getBean(EncounterTemplateDao.class);
%>
<%
    //save or delete the settings
    int rowsAffected = 0;
    if (request.getParameter("dboperation") != null && (request.getParameter("dboperation").compareTo(" Save ") == 0 ||
            request.getParameter("dboperation").equals("Delete"))) {

        EncounterTemplate et = encounterTemplateDao.find(request.getParameter("name"));
        if (et != null) {
            encounterTemplateDao.remove(et.getId());
        }

        if (request.getParameter("dboperation") != null && request.getParameter("dboperation").equals(" Save ")) {
            et = new EncounterTemplate();
            et.setEncounterTemplateName(request.getParameter("name"));
            et.setEncounterTemplateValue(request.getParameter("value"));
            et.setCreatorProviderNo(request.getParameter("creator"));
            et.setCreatedDate(new java.util.Date());
            encounterTemplateDao.persist(et);
        }
    }
%>

<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providertemplate.title"/></title>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">

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

                <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providertemplate.msgTitle"/></h3>

                <div class="card card-body bg-body-tertiary">
                    <form name="edittemplate" method="post" action="providertemplate.jsp" class="d-flex flex-wrap align-items-center gap-2">
                        <!--<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providertemplate.formEdit"/>:-->
                        Select Template<br>
                        <select name="name">
                            <%
                                List<EncounterTemplate> allTemplates = encounterTemplateDao.findAll();

                                for (EncounterTemplate encounterTemplate : allTemplates) {
                                    String templateName = Encode.forHtmlAttribute(encounterTemplate.getEncounterTemplateName());
                            %>
                            <option value="<%=templateName%>"><%=templateName%>
                            </option>
                            <%
                                }
                            %>
                        </select>
                        <input type="hidden" value="Edit" name="dboperation">
                        <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providertemplate.btnEdit"/>"
                               name="dboperation" class="btn btn-secondary"
                               onclick="document.forms['edittemplate'].dboperation.value='Edit'; document.forms['edittemplate'].submit();">
                    </form>

                </div>

                <%
                    boolean bEdit = request.getParameter("dboperation") != null && request.getParameter("dboperation").equals("Edit") ? true : false;
                    String tName = null;
                    String tValue = null;
                    if (bEdit) {
                        List<EncounterTemplate> templates = encounterTemplateDao.findByName(request.getParameter("name"));
                        for (EncounterTemplate template : templates) {
                            tName = template.getEncounterTemplateName();
                            tValue = template.getEncounterTemplateValue();
                        }
                    }
                %>

                <div class="card card-body bg-body-tertiary">
                    <form name="template" method="post" action="providertemplate.jsp">
                        <input type="hidden" name="dboperation" value="">

                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providertemplate.formTemplateName"/>:<br>
                        <input type="text" name="name" pattern="^[a-zA-Z0-9\s]+$" value="<%=bEdit?tName:""%>"
                               class="form-control" maxlength="50"> <!-- match the definition in the schema -->

                        <br><br>

                        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providertemplate.formTemplateText"/>:<br>
                        <textarea name="value" rows="20" class="form-control"><%=bEdit ? tValue : ""%></textarea>

                        <br>
                        <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providertemplate.btnDelete"/>"
                               class="btn btn-danger"
                               onClick="document.forms['template'].dboperation.value='Delete'; document.forms['template'].submit();">

                        <INPUT TYPE="hidden" NAME="creator" VALUE="<%=curUser_no%>">
                        <input type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providertemplate.btnSave"/>"
                               class="btn btn-primary"
                               onClick="document.forms['template'].dboperation.value=' Save '; document.forms['template'].submit();">


                        <input type="button" name="Button" id="exit-btn"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providertemplate.btnExit"/>" class="btn btn-secondary"
                               onClick="window.close();">

                    </form>
                </div>

            </div><!-- col-md-12 -->
        </div><!-- row fluid -->
    </div><!-- container -->


    </body>
</html>