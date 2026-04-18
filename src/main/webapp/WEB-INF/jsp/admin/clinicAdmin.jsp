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
<!DOCTYPE HTML>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Clinic" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<fmt:setBundle basename="oscarResources"/>
<%
    Clinic clinic = (Clinic) request.getAttribute("clinicForm");
    if (clinic == null) {
        clinic = new Clinic();
    }
%>


<html>
    <head>
        <title><fmt:message key="admin.admin.clinicAdmin"/></title>

        <script src="${pageContext.request.contextPath}/js/global.js"></script>
        <script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap -->

    </head>
    <body class="BodyStyle">
    <h4><fmt:message key="admin.admin.clinicAdmin"/></h4>
    <div class="card card-body bg-body-tertiary">

        <form action="<%=request.getContextPath() %>/admin/ManageClinic" method="post" class="">
            <input type="hidden" name="clinic.id" id="clinic.id" value="<%=clinic.getId() != null ? clinic.getId() : ""%>"/>
            <input type="hidden" name="clinic.status" id="clinic.status" value="A"/>
            <input type="hidden" name="method" id="method" value="update"/>

            <div class="mb-3">
                <label class="form-label" for="clinic.clinicName"><fmt:message key="admin.provider.clinicName"/></label>
                <div>
                    <input type="text" name="clinic.clinicName" id="clinic.clinicName" value="<e:forHtmlAttribute value='<%= clinic.getClinicName() != null ? clinic.getClinicName() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="clinic.clinicAddress"><fmt:message key="admin.provider.formAddress"/></label>
                <div>
                    <input type="text" name="clinic.clinicAddress" id="clinic.clinicAddress" value="<e:forHtmlAttribute value='<%= clinic.getClinicAddress() != null ? clinic.getClinicAddress() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="clinic.clinicCity"><fmt:message key="oscarReport.oscarReportCatchment.msgCity"/></label>
                <div>
                    <input type="text" name="clinic.clinicCity" id="clinic.clinicCity" value="<e:forHtmlAttribute value='<%= clinic.getClinicCity() != null ? clinic.getClinicCity() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="clinic.clinicPostal"><fmt:message key="oscarReport.oscarReportCatchment.msgPostal"/></label>
                <div>
                    <input type="text" name="clinic.clinicPostal" id="clinic.clinicPostal" value="<e:forHtmlAttribute value='<%= clinic.getClinicPostal() != null ? clinic.getClinicPostal() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="clinic.clinicPhone"><fmt:message key="appointment.addappointment.msgPhone"/></label>
                <div>
                    <input type="text" name="clinic.clinicPhone" id="clinic.clinicPhone" value="<e:forHtmlAttribute value='<%= clinic.getClinicPhone() != null ? clinic.getClinicPhone() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="clinic.clinicFax"><fmt:message key="admin.provider.formFax"/></label>
                <div>
                    <input type="text" name="clinic.clinicFax" id="clinic.clinicFax" value="<e:forHtmlAttribute value='<%= clinic.getClinicFax() != null ? clinic.getClinicFax() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="clinic.clinicLocationCode"><fmt:message key="location"/>&nbsp;
                    <fmt:message key="billing.billingDigSearch.formCode"/></label>
                <div>
                    <input type="text" name="clinic.clinicLocationCode" id="clinic.clinicLocationCode" value="<e:forHtmlAttribute value='<%= clinic.getClinicLocationCode() != null ? clinic.getClinicLocationCode() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="clinic.clinicProvince"><fmt:message key="demographic.demographicaddrecordhtm.formprovince"/></label>
                <div>
                    <input type="text" name="clinic.clinicProvince" id="clinic.clinicProvince" value="<e:forHtmlAttribute value='<%= clinic.getClinicProvince() != null ? clinic.getClinicProvince() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3" title="<fmt:message key="admin.clinicAdmin.multiPhoneTitle"/>">
                <label class="form-label" for="clinic.clinicDelimPhone"><fmt:message key="appointment.addappointment.msgPhone"/>|<fmt:message key="appointment.addappointment.msgPhone"/></label>
                <div>
                    <input type="text" name="clinic.clinicDelimPhone" id="clinic.clinicDelimPhone" value="<e:forHtmlAttribute value='<%= clinic.getClinicDelimPhone() != null ? clinic.getClinicDelimPhone() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3" title="<fmt:message key="admin.clinicAdmin.multiFaxTitle"/>">
                <label class="form-label" for="clinic.clinicDelimFax"><fmt:message key="admin.provider.formFax"/>|<fmt:message key="admin.provider.formFax"/></label>
                <div>
                    <input type="text" name="clinic.clinicDelimFax" id="clinic.clinicDelimFax" value="<e:forHtmlAttribute value='<%= clinic.getClinicDelimFax() != null ? clinic.getClinicDelimFax() : "" %>' />" />
                </div>
            </div>
            <div class="mb-3">
                <div>
                    <input type="submit" value="<fmt:message key="global.btnSubmit"/>" class="btn btn-primary">
                </div>
            </div>

        </form>

    </div>
</html>
