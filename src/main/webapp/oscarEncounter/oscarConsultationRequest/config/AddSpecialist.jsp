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

<%@page import="io.github.carlos_emr.carlos.commn.dao.EFormDao" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.consult" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.consult");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.util.ResourceBundle" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConAddSpecialistForm" %>
<%@page import="java.util.List" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.InstitutionDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Institution" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.InstitutitionDepartmentDao, io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.InstitutionDepartment, io.github.carlos_emr.carlos.commn.model.ConsultationServices" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DepartmentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Department" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.EForm" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>

<%
    InstitutionDao institutionDao = SpringUtils.getBean(InstitutionDao.class);
    InstitutitionDepartmentDao idDao = SpringUtils.getBean(InstitutitionDepartmentDao.class);
    DepartmentDao departmentDao = SpringUtils.getBean(DepartmentDao.class);
    EFormDao eformDao = SpringUtils.getBean(EFormDao.class);

    List<EForm> eforms = eformDao.findAll(true);
    pageContext.setAttribute("eforms", eforms);

    String referralNoMsg = OscarProperties.getInstance().getProperty("referral_no.msg", "Must be an integer");
    pageContext.setAttribute("referralNoMsg", referralNoMsg);

    ConsultationServiceDao specialtyDao = SpringUtils.getBean(ConsultationServiceDao.class);
    List<ConsultationServices> specialties = specialtyDao.findActive();
    pageContext.setAttribute("specialties", specialties);

    java.util.Properties oscarVariables = OscarProperties.getInstance();
%>
<fmt:setBundle basename="oscarResources"/>

<%
    ResourceBundle oscarR = ResourceBundle.getBundle("oscarResources", request.getLocale());

    String transactionType = new String(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.AddSpecialist.addOperation"));
    String specId = null;
    int whichType = 1;
    if (request.getAttribute("upd") != null) {
        transactionType = new String(oscarR.getString("oscarEncounter.oscarConsultationRequest.config.AddSpecialist.updateOperation"));
        whichType = 2;
        specId = (String) request.getAttribute("specId");
    }
%>
<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><%=transactionType%></title>

        <script>
            function updateDepartments(i) {
                <%
                for(Institution i: institutionDao.findAll()) {
                    %>
                if (i == '<%=i.getId()%>') {
                    $('#department').empty();
                    $('#department').append($("<option></option>").attr("value", '0').text('Select Below'));
                    <%
                    for(InstitutionDepartment id : idDao.findByInstitutionId(i.getId())) {
                        int deptId = id.getId().getDepartmentId();
                        Department d = departmentDao.find(deptId);
                        if(d != null) {
                        %>
                    $('#department').append($("<option></option>").attr("value", '<%=deptId%>').text('<%= Encode.forJavaScript(d.getName()) %>'));
                    <%
                } }
                %>
                }
                <%
                }
                %>
            }
        </script>

        <script>
            $(document).ready(function () {
                $('#institution').change(function () {
                    changeInstitution();
                });
            });

            function changeInstitution() {
                var id = $('#institution').val();
                if (id == '0') {
                    $('#department').empty();
                    $('#department').append($("<option></option>").attr("value", '0').text('Select Below'));
                } else {
                    updateDepartments(id);
                }
            }
        </script>
        <%-- Capture CPSO i18n messages for safe JS injection via OWASP forJavaScript encoding --%>
        <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.unavailable"   var="cpsoMsgUnavailable"/>
        <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.noResults"     var="cpsoMsgNoResults"/>
        <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.tooManyResults" var="cpsoMsgTooMany"/>
        <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.phoneLabel"    var="cpsoMsgPhone"/>
        <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.faxLabel"      var="cpsoMsgFax"/>
        <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.cpsoLabel"     var="cpsoMsgCpso"/>
        <script>
            /* i18n strings for CPSO search – rendered server-side so JS stays locale-aware */
            var cpsoI18n = {
                unavailable:   '${e:forJavaScript(cpsoMsgUnavailable)}',
                noResults:     '${e:forJavaScript(cpsoMsgNoResults)}',
                tooMany:       '${e:forJavaScript(cpsoMsgTooMany)}',
                phoneLabel:    '${e:forJavaScript(cpsoMsgPhone)}',
                faxLabel:      '${e:forJavaScript(cpsoMsgFax)}',
                cpsoLabel:     '${e:forJavaScript(cpsoMsgCpso)}'
            };
        </script>
        <script>
            $(document).ready(function () {
                var cpsoTimer = null;
                var cpsoDelay = 1000;
                var currentCpsoRequest = null;

                function doCpsoSearch() {
                    var lastName = $('#cpsoLastName').val().trim();
                    var firstName = $('#cpsoFirstName').val().trim();

                    // Abort any in-flight request before the early return so stale results
                    // cannot overwrite the cleared UI when input drops below the minimum length
                    if (currentCpsoRequest) {
                        currentCpsoRequest.abort();
                        currentCpsoRequest = null;
                    }

                    if (lastName.length < 2 && firstName.length < 2) {
                        $('#cpsoSpinner').hide();
                        $('#cpsoResults').hide().empty();
                        return;
                    }

                    $('#cpsoSpinner').show();

                    currentCpsoRequest = $.ajax({
                        url: '${pageContext.request.contextPath}/oscarEncounter/CpsoSearch.do',
                        method: 'GET',
                        data: { lastName: lastName, firstName: firstName },
                        dataType: 'json',
                        success: function (data) {
                            $('#cpsoSpinner').hide();
                            currentCpsoRequest = null;
                            renderCpsoResults(data);
                        },
                        error: function (jqXHR) {
                            if (jqXHR.statusText === 'abort') return;
                            $('#cpsoSpinner').hide();
                            currentCpsoRequest = null;
                            $('#cpsoResults').html('<div class="p-2 text-danger">' + $('<span>').text(cpsoI18n.unavailable).html() + '</div>').show();
                        }
                    });
                }

                function renderCpsoResults(data) {
                    var container = $('#cpsoResults');
                    container.empty();

                    if (!data) {
                        container.html('<div class="p-2 text-danger">' + $('<span>').text(cpsoI18n.unavailable).html() + '</div>').show();
                        return;
                    }

                    // Check for server-reported errors before checking result count
                    if (data.error) {
                        container.html('<div class="p-2 text-danger">' + $('<span>').text(data.error).html() + '</div>').show();
                        return;
                    }

                    if (data.totalcount === -1) {
                        container.html('<div class="p-2 text-warning">' + $('<span>').text(cpsoI18n.tooMany).html() + '</div>').show();
                        return;
                    }

                    if (!Array.isArray(data.results) || data.results.length === 0) {
                        container.html('<div class="p-2 text-muted">' + $('<span>').text(cpsoI18n.noResults).html() + '</div>').show();
                        return;
                    }

                    $.each(data.results, function (i, doc) {
                        var nameParts = doc.name.split(',');
                        var docLastName = nameParts[0] ? nameParts[0].trim() : '';
                        var docFirstName = nameParts[1] ? nameParts[1].trim() : '';

                        var addressParts = [];
                        if (doc.street1) addressParts.push(doc.street1);
                        if (doc.street2) addressParts.push(doc.street2);
                        if (doc.street3) addressParts.push(doc.street3);
                        if (doc.city) addressParts.push(doc.city);
                        if (doc.province) addressParts.push(doc.province);
                        if (doc.postalcode) addressParts.push(doc.postalcode.trim());
                        var fullAddress = addressParts.join(', ');

                        var specialty = doc.specialties ? doc.specialties.trim() : '';
                        var phone = doc.phonenumber ? doc.phonenumber.trim() : '';
                        var fax = doc.fax ? doc.fax.trim() : '';
                        var cpsoNum = doc.cpsonumber || '';
                        var status = doc.registrationstatus || '';

                        var statusBadge = '';
                        if (status === 'Active') {
                            statusBadge = '<span class="badge bg-success ms-2">' + $('<span>').text(status).html() + '</span>';
                        } else {
                            statusBadge = '<span class="badge bg-secondary ms-2">' + $('<span>').text(status).html() + '</span>';
                        }

                        var row = $('<div>')
                            .addClass('cpso-result-item p-2')
                            .css({ cursor: 'pointer', borderBottom: '1px solid #eee' })
                            .attr('tabindex', '0')
                            .attr('role', 'option')
                            .data('physician', {
                                firstName: docFirstName,
                                lastName: docLastName,
                                address: fullAddress,
                                phone: phone,
                                fax: fax,
                                specialty: specialty,
                                cpsoNum: cpsoNum
                            });

                        row.html(
                            '<div><strong>' + $('<span>').text(docLastName + ', ' + docFirstName).html() + '</strong>'
                            + statusBadge
                            + ' <small class="text-muted">' + $('<span>').text(cpsoI18n.cpsoLabel).html() + $('<span>').text(cpsoNum).html() + '</small></div>'
                            + (specialty ? '<div><small class="text-primary">' + $('<span>').text(specialty).html() + '</small></div>' : '')
                            + (fullAddress ? '<div><small class="text-muted">' + $('<span>').text(fullAddress).html() + '</small></div>' : '')
                            + ((phone || fax) ? '<div>'
                                + (phone ? '<small class="text-muted">' + $('<span>').text(cpsoI18n.phoneLabel).html() + $('<span>').text(phone).html() + '</small>' : '')
                                + (fax ? ' <small class="text-muted">' + $('<span>').text(cpsoI18n.faxLabel).html() + $('<span>').text(fax).html() + '</small>' : '')
                                + '</div>' : '')
                        );

                        row.on('mouseenter', function () { $(this).css('background-color', '#e8f0fe'); });
                        row.on('mouseleave', function () { $(this).css('background-color', ''); });

                        row.on('click keypress', function (e) {
                            if (e.type === 'keypress' && e.which !== 13) return;
                            var physician = $(this).data('physician');
                            $('#firstName').val(physician.firstName);
                            $('#lastName').val(physician.lastName);
                            $('#address').val(physician.address);
                            $('#phone').val(physician.phone);
                            $('#fax').val(physician.fax);
                            container.hide();
                        });

                        container.append(row);
                    });

                    container.show();
                }

                $('#cpsoLastName, #cpsoFirstName').on('input', function () {
                    clearTimeout(cpsoTimer);
                    cpsoTimer = setTimeout(doCpsoSearch, cpsoDelay);
                });
            });
        </script>
    </head>

    <body>
    <div class="container-fluid">
        <div class="page-header-bar">
            <h5 class="page-header-title"><%=transactionType%></h5>
        </div>

<%
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
        <div class="action-errors">
            <ul>
                <% for (String error : actionErrors) { %>
                    <li><%= Encode.forHtml(error) %></li>
                <% } %>
            </ul>
        </div>
<% } %>

        <div class="row">
            <div class="col-md-3 consult-sidebar">
                <%
                    EctConTitlebar titlebar = new EctConTitlebar(request);
                    out.print(titlebar.estBar(request));
                %>
            </div>

            <div class="col-md-9">
                <%
                    String added = (String) request.getAttribute("Added");
                    if (added != null) {
                %>
                <div class="alert alert-success">
                    <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgSpecialistAdded">
                        <fmt:param value="<%=added%>" />
                    </fmt:message>
                </div>
                <% } %>

                <form action="${pageContext.request.contextPath}/oscarEncounter/AddSpecialist.do" method="post">
                    <%
                        EctConAddSpecialistForm thisForm;
                        thisForm = (EctConAddSpecialistForm) request.getAttribute("EctConAddSpecialistForm");
                        if (thisForm == null) {
                            thisForm = new EctConAddSpecialistForm();
                            request.setAttribute("EctConAddSpecialistForm", thisForm);
                        }

                        if (request.getAttribute("specId") != null) {
                            thisForm.setFirstName((String) request.getAttribute("fName"));
                            thisForm.setLastName((String) request.getAttribute("lName"));
                            thisForm.setProLetters((String) request.getAttribute("proLetters"));
                            thisForm.setAddress((String) request.getAttribute("address"));
                            thisForm.setPhone((String) request.getAttribute("phone"));
                            thisForm.setFax((String) request.getAttribute("fax"));
                            thisForm.setWebsite((String) request.getAttribute("website"));
                            thisForm.setEmail((String) request.getAttribute("email"));
                            thisForm.setSpecType((String) request.getAttribute("specType"));
                            thisForm.setSpecId((String) request.getAttribute("specId"));
                            thisForm.seteDataUrl((String) request.getAttribute("eDataUrl"));
                            thisForm.seteDataOscarKey((String) request.getAttribute("eDataOscarKey"));
                            thisForm.seteDataServiceKey((String) request.getAttribute("eDataServiceKey"));
                            thisForm.seteDataServiceName((String) request.getAttribute("eDataServiceName"));
                            thisForm.setAnnotation((String) request.getAttribute("annotation"));
                            thisForm.setReferralNo((String) request.getAttribute("referralNo"));
                            thisForm.setInstitution((String) request.getAttribute("institution"));
                            thisForm.setDepartment((String) request.getAttribute("department"));
                            thisForm.setPrivatePhoneNumber((String) request.getAttribute("privatePhoneNumber"));
                            thisForm.setCellPhoneNumber((String) request.getAttribute("cellPhoneNumber"));
                            thisForm.setPagerNumber((String) request.getAttribute("pagerNumber"));
                            thisForm.setSalutation((String) request.getAttribute("salutation"));
                            thisForm.setHideFromView((Boolean) request.getAttribute("hideFromView"));
                            thisForm.setEformId((Integer) request.getAttribute("eformId"));
                    %>
                    <script>
                        $(document).ready(function () {
                            $('#institution').val('<%= Encode.forJavaScript(String.valueOf(request.getAttribute("institution"))) %>');
                            changeInstitution();
                            $('#department').val('<%= Encode.forJavaScript(String.valueOf(request.getAttribute("department"))) %>');
                        });
                    </script>
                    <% } %>

                    <input type="hidden" name="specId" id="specId" value="<%= specId != null ? specId : "" %>"/>

                    <div class="row mb-2">
                        <div class="col-md-4">
                            <label for="firstName" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.firstName"/></label>
                            <input type="text" name="firstName" id="firstName" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.firstName}'/>"/>
                        </div>
                        <div class="col-md-4">
                            <label for="lastName" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.lastName"/></label>
                            <input type="text" name="lastName" id="lastName" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.lastName}'/>"/>
                        </div>
                        <div class="col-md-4">
                            <label for="proLetters" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.professionalLetters"/></label>
                            <input type="text" name="proLetters" id="proLetters" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.proLetters}'/>"/>
                        </div>
                    </div>

                    <div class="row mb-2">
                        <div class="col-md-6">
                            <label for="address" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.address"/></label>
                            <textarea name="address" id="address" class="form-control" cols="30" rows="3"><e:forHtmlContent value='${EctConAddSpecialistForm.address}'/></textarea>
                            <small class="form-text text-muted"><%= oscarVariables.getProperty("consultation_comments", "") %></small>
                        </div>
                        <div class="col-md-6">
                            <label for="annotation" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.Annotation"/></label>
                            <textarea name="annotation" id="annotation" class="form-control" cols="30" rows="3"><e:forHtmlContent value='${EctConAddSpecialistForm.annotation}'/></textarea>
                        </div>
                    </div>

                    <div class="row mb-2">
                        <div class="col-md-6">
                            <label for="phone" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.phone"/></label>
                            <input type="text" name="phone" id="phone" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.phone}'/>"/>
                        </div>
                        <div class="col-md-6">
                            <label for="fax" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.fax"/></label>
                            <input type="text" name="fax" id="fax" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.fax}'/>"/>
                        </div>
                    </div>

                    <div class="row mb-2">
                        <div class="col-md-6">
                            <label for="privatePhoneNumber" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.privatePhoneNumber"/></label>
                            <input type="text" name="privatePhoneNumber" id="privatePhoneNumber" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.privatePhoneNumber}'/>"/>
                        </div>
                        <div class="col-md-6">
                            <label for="cellPhoneNumber" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cellPhoneNumber"/></label>
                            <input type="text" name="cellPhoneNumber" id="cellPhoneNumber" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.cellPhoneNumber}'/>"/>
                        </div>
                    </div>

                    <div class="row mb-2">
                        <div class="col-md-6">
                            <label for="pagerNumber" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.pagerNumber"/></label>
                            <input type="text" name="pagerNumber" id="pagerNumber" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.pagerNumber}'/>"/>
                        </div>
                        <div class="col-md-6">
                            <label for="salutation" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.salutation"/></label>
                            <select name="salutation" id="salutation" class="form-select">
                                <option value="" ${EctConAddSpecialistForm.salutation == '' ? 'selected' : ''}><fmt:message key="demographic.demographiceditdemographic.msgNotSet"/></option>
                                <option value="Dr." ${EctConAddSpecialistForm.salutation == 'Dr.' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgDr"/></option>
                                <option value="Mr." ${EctConAddSpecialistForm.salutation == 'Mr.' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgMr"/></option>
                                <option value="Mrs." ${EctConAddSpecialistForm.salutation == 'Mrs.' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgMrs"/></option>
                                <option value="Miss" ${EctConAddSpecialistForm.salutation == 'Miss' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgMiss"/></option>
                                <option value="Ms." ${EctConAddSpecialistForm.salutation == 'Ms.' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgMs"/></option>
                            </select>
                        </div>
                    </div>

                    <div class="row mb-2">
                        <div class="col-md-6">
                            <label for="website" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.website"/></label>
                            <input type="text" name="website" id="website" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.website}'/>"/>
                        </div>
                        <div class="col-md-6">
                            <label for="email" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.email"/></label>
                            <input type="text" name="email" id="email" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.email}'/>"/>
                        </div>
                    </div>

                    <div class="row mb-2">
                        <div class="col-md-6">
                            <label for="specType" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.specialistType"/></label>
                            <select id="specType" name="specType" class="form-select">
                                <option value="0" selected>&nbsp;</option>
                                <c:forEach items="${ specialties }" var="specialtyType">
                                    <option value="${ specialtyType.serviceId }" ${ specialtyType.serviceId eq specType ? 'selected' : '' }>
                                        <c:out value="${ specialtyType.serviceDesc }"/>
                                    </option>
                                </c:forEach>
                            </select>
                        </div>
                        <div class="col-md-6">
                            <label for="referralNo" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.referralNo"/></label>
                            <% if (request.getAttribute("refnoinuse") != null) { %>
                            <div class="text-danger mb-1"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.referralNoInUse"/></div>
                            <% } else if (request.getAttribute("refnoinvalid") != null) { %>
                            <div class="text-danger mb-1">
                                <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.referralNoInvalid">
                                    <fmt:param value="${referralNoMsg}" />
                                </fmt:message>
                            </div>
                            <% } %>
                            <input type="text" name="referralNo" id="referralNo" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.referralNo}'/>"/>
                        </div>
                    </div>

                    <div class="row mb-2">
                        <div class="col-md-6">
                            <label for="institution" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.institution"/></label>
                            <select name="institution" id="institution" class="form-select">
                                <option value="0">Select Below</option>
                                <%
                                    String selectedInst = request.getAttribute("institution") != null ? (String) request.getAttribute("institution") : "0";
                                    for (Institution institution : institutionDao.findAll()) {
                                        String instSelected = String.valueOf(institution.getId()).equals(selectedInst) ? " selected" : "";
                                %>
                                <option value="<%=institution.getId()%>"<%=instSelected%>><%= Encode.forHtml(institution.getName()) %></option>
                                <% } %>
                            </select>
                        </div>
                        <div class="col-md-6">
                            <label for="department" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.department"/></label>
                            <select name="department" id="department" class="form-select">
                                <option value="0">Select Below</option>
                            </select>
                        </div>
                    </div>

                    <hr/>

                    <div class="mb-2">
                        <label for="eDataUrl" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eDataUrl"/></label>
                        <input type="text" name="eDataUrl" id="eDataUrl" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.eDataUrl}'/>"/>
                    </div>
                    <div class="mb-2">
                        <label for="eDataOscarKey" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eDataOscarKey"/></label>
                        <input type="text" name="eDataOscarKey" id="eDataOscarKey" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.eDataOscarKey}'/>"/>
                    </div>
                    <div class="mb-2">
                        <label for="eDataServiceKey" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eDataServiceKey"/></label>
                        <input type="text" name="eDataServiceKey" id="eDataServiceKey" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.eDataServiceKey}'/>"/>
                    </div>
                    <div class="mb-2">
                        <label for="eDataServiceName" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eDataServiceName"/></label>
                        <input type="text" name="eDataServiceName" id="eDataServiceName" class="form-control" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.eDataServiceName}'/>"/>
                    </div>
                    <div class="row mb-2">
                        <div class="col-md-3">
                            <label for="hideFromView" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.hideFromView"/></label>
                            <select name="hideFromView" id="hideFromView" class="form-select">
                                <option value="false" ${!EctConAddSpecialistForm.hideFromView ? 'selected' : ''}>false</option>
                                <option value="true" ${EctConAddSpecialistForm.hideFromView ? 'selected' : ''}>true</option>
                            </select>
                        </div>
                    </div>
                    <div class="row mb-3">
                        <div class="col-md-6">
                            <label for="eformId" class="form-label"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eform"/></label>
                            <select name="eformId" id="eformId" class="form-select">
                            <option value="0" ${EctConAddSpecialistForm.eformId == 0 ? 'selected' : ''}>--None--</option>
                            <c:forEach var="eform" items="${eforms}">
                                <option value="${eform.id}" ${EctConAddSpecialistForm.eformId == eform.id ? 'selected' : ''}>
                                    <c:out value="${eform.formName}"/>
                                </option>
                            </c:forEach>
                        </select>
                        </div>
                    </div>

                    <%-- CPSO Physician Search --%>
                    <%-- Capture attribute strings for OWASP HTML-attribute encoding --%>
                    <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.lastNamePlaceholder"  var="cpsoAttrLNPlaceholder"/>
                    <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.firstNamePlaceholder" var="cpsoAttrFNPlaceholder"/>
                    <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.ariaSearching"        var="cpsoAttrAriaSearching"/>
                    <fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.ariaResults"          var="cpsoAttrAriaResults"/>
                    <div class="card mb-3" style="border: 1px solid var(--carlos-primary, #337ab7);">
                        <div class="card-header text-white py-2" style="background-color: var(--carlos-primary, #337ab7);">
                            <strong><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.title"/></strong>
                            <small class="ms-2"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.subtitle"/></small>
                        </div>
                        <div class="card-body py-2">
                            <div class="row mb-2">
                                <div class="col-md-5">
                                    <label for="cpsoLastName" class="form-label form-label-sm mb-1"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.lastNameLabel"/></label>
                                    <input type="text" id="cpsoLastName" class="form-control form-control-sm" placeholder="${e:forHtmlAttribute(cpsoAttrLNPlaceholder)}" autocomplete="off"/>
                                </div>
                                <div class="col-md-5">
                                    <label for="cpsoFirstName" class="form-label form-label-sm mb-1"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.firstNameLabel"/></label>
                                    <input type="text" id="cpsoFirstName" class="form-control form-control-sm" placeholder="${e:forHtmlAttribute(cpsoAttrFNPlaceholder)}" autocomplete="off"/>
                                </div>
                                <div class="col-md-2 d-flex align-items-end">
                                    <span id="cpsoSpinner" class="spinner-border spinner-border-sm text-primary" style="display:none;" role="status" aria-label="${e:forHtmlAttribute(cpsoAttrAriaSearching)}"></span>
                                </div>
                            </div>
                            <div id="cpsoResults" role="listbox" aria-live="polite" aria-label="${e:forHtmlAttribute(cpsoAttrAriaResults)}" style="display:none; max-height:200px; overflow-y:auto; border:1px solid #dee2e6; border-radius:4px;">
                            </div>
                            <small class="form-text text-muted"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cpsoSearch.hint"/></small>
                        </div>
                    </div>

                    <input type="hidden" name="whichType" value="<%=whichType%>"/>
                    <input type="submit" class="btn btn-primary" name="transType" value="<%=transactionType%>"/>
                </form>
            </div>
        </div>
    </div>
    </body>
</html>
