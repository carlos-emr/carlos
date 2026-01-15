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

<%@page import="ca.openosp.openo.commn.dao.EFormDao" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="https://www.owasp.org/index.php/OWASP_Java_Encoder_Project" prefix="e" %>
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
<% java.util.Properties oscarVariables = OscarProperties.getInstance(); %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@page import="ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConAddSpecialistForm" %>
<%@page import="java.util.List" %>
<%@page import="java.util.Map" %>
<%@page import="java.util.HashMap" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.dao.InstitutionDao" %>
<%@page import="ca.openosp.openo.commn.model.Institution" %>
<%@page import="ca.openosp.openo.commn.dao.InstitutitionDepartmentDao, ca.openosp.openo.commn.dao.ConsultationServiceDao" %>
<%@page import="ca.openosp.openo.commn.model.InstitutionDepartment, ca.openosp.openo.commn.model.ConsultationServices" %>
<%@page import="ca.openosp.openo.commn.dao.DepartmentDao" %>
<%@page import="ca.openosp.openo.commn.model.Department" %>
<%@page import="ca.openosp.openo.commn.model.EForm" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>
<%@ page import="ca.openosp.OscarProperties" %>

<%
    InstitutionDao institutionDao = SpringUtils.getBean(InstitutionDao.class);
    InstitutitionDepartmentDao idDao = SpringUtils.getBean(InstitutitionDepartmentDao.class);
    DepartmentDao departmentDao = SpringUtils.getBean(DepartmentDao.class);
    EFormDao eformDao = SpringUtils.getBean(EFormDao.class);

    List<EForm> eforms = eformDao.findAll(true);
    pageContext.setAttribute("eforms", eforms);

    String referralNoMsg = OscarProperties.getInstance().getProperty("referral_no.msg", "Must be an integer");

    ConsultationServiceDao specialtyDao = SpringUtils.getBean(ConsultationServiceDao.class);
    List<ConsultationServices> specialties = specialtyDao.findActive();
    pageContext.setAttribute("specialties", specialties);

%>
<fmt:setBundle basename="oscarResources"/>
<html>

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

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script src="<%= request.getContextPath() %>/js/jquery-1.7.1.min.js"></script>
        <title><%=transactionType%>
        </title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

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
                    $('#department').append($("<option></option>").attr("value", '<%=deptId%>').text('<%=d.getName()%>'));
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
    </head>
    <script language="javascript">
        function BackToOscar() {
            window.close();
        }
    </script>

    <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/css/encounterStyles.css">
    <body class="BodyStyle" vlink="#0000FF">

    <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><%= error %></li>
            <% } %>
        </ul>
    </div>
<% } %>
    <!--  -->
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn">Consultation</td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td class="Header"><%=transactionType%>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr style="vertical-align: top">
            <td class="MainTableLeftColumn">
                <%
                    EctConTitlebar titlebar = new EctConTitlebar(request);
                    out.print(titlebar.estBar(request));
                %>
            </td>
            <td class="MainTableRightColumn">
                <table cellpadding="0" cellspacing="2"
                       style="border-collapse: collapse" bordercolor="#111111" width="100%"
                       height="100%">

                    <!----Start new rows here-->
                    <%
                        String added = (String) request.getAttribute("Added");
                        if (added != null) { %>
                    <tr>
                        <td style="color: red;">
                            <fmt:message  key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgSpecialistAdded">
                                <fmt:param value="<%=added%>" />
                            </fmt:message>
                        </td>
                    </tr>
                    <%}%>
                    <tr>
                        <td>

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
                                        $('#institution').val('<%=request.getAttribute("institution")%>');
                                        changeInstitution();
                                        $('#department').val('<%=request.getAttribute("department")%>');
                                    });
                                </script>
                                <%
                                    }
                                %>
                                <table>

                                    <input type="hidden" name="specId" id="specId" value="<%=specId%>"/>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.firstName"/></td>
                                        <td><input type="text" name="firstName" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.firstName}'/>"/></td>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.lastName"/></td>
                                        <td><input type="text" name="lastName" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.lastName}'/>"/></td>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.professionalLetters"/>
                                        </td>
                                        <td><input type="text" name="proLetters" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.proLetters}'/>"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.address"/>
                                        </td>
                                        <td><textarea name="address" cols="30"
                                                           rows="3"><e:forHtmlContent value='${EctConAddSpecialistForm.address}'/></textarea> <%=oscarVariables.getProperty("consultation_comments", "") %>
                                        </td>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.Annotation"/>
                                        </td>
                                        <td colspan="4"><textarea name="annotation" cols="30" rows="3"><e:forHtmlContent value='${EctConAddSpecialistForm.annotation}'/></textarea>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.phone"/>
                                        </td>
                                        <td><input type="text" name="phone" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.phone}'/>"/></td>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.fax"/>
                                        </td>
                                        <td colspan="4"><input type="text" name="fax" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.fax}'/>"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.privatePhoneNumber"/></td>
                                        <td><input type="text" name="privatePhoneNumber" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.privatePhoneNumber}'/>"/></td>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.cellPhoneNumber"/></td>
                                        <td colspan="4"><input type="text" name="cellPhoneNumber" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.cellPhoneNumber}'/>"/></td>
                                    </tr>

                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.pagerNumber"/></td>
                                        <td><input type="text" name="pagerNumber" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.pagerNumber}'/>"/></td>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.salutation"/></td>
                                        <td colspan="4">
                                            <select name="salutation">
                                                <option value="" ${EctConAddSpecialistForm.salutation == '' ? 'selected' : ''}><fmt:message key="demographic.demographiceditdemographic.msgNotSet"/></option>
                                                <option value="Dr." ${EctConAddSpecialistForm.salutation == 'Dr.' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgDr"/></option>
                                                <option value="Mr." ${EctConAddSpecialistForm.salutation == 'Mr.' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgMr"/></option>
                                                <option value="Mrs." ${EctConAddSpecialistForm.salutation == 'Mrs.' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgMrs"/></option>
                                                <option value="Miss" ${EctConAddSpecialistForm.salutation == 'Miss' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgMiss"/></option>
                                                <option value="Ms." ${EctConAddSpecialistForm.salutation == 'Ms.' ? 'selected' : ''}><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.msgMs"/></option>
                                            </select>
                                        </td>
                                    </tr>

                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.website"/></td>
                                        <td><input type="text" name="website" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.website}'/>"/></td>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.email"/></td>
                                        <td colspan="4"><input type="text" name="email" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.email}'/>"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.specialistType"/></td>
                                        <td>

                                            <select id="specType" name="specType">
                                                <option value="0" selected>&nbsp;</option>
                                                <c:forEach items="${ specialties }" var="specialtyType">

                                                    <option value="${ specialtyType.serviceId }" ${ specialtyType.serviceId eq specType ? 'selected' : '' } >
                                                        <c:out value="${ specialtyType.serviceDesc }"/>
                                                    </option>

                                                </c:forEach>
                                            </select>

                                        </td>


                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.referralNo"/></td>
                                        <td colspan="4">
                                            <% if (request.getAttribute("refnoinuse") != null) { %>
                                            <span style="color: red;"><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.referralNoInUse"/></span><br/>
                                            <% } else if (request.getAttribute("refnoinvalid") != null) { %>
                                            <span style="color: red;">
                                                <fmt:message  key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.referralNoInvalid">
                                                    <fmt:param value="${referralNoMsg}" />
                                                </fmt:message>
                                            </span><br/>
                                            <% } %>
                                            <input type="text" name="referralNo" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.referralNo}'/>"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.institution"/></td>
                                        <td>
                                            <select name="institution" id="institution">
                                                <option value="0">Select Below</option>
                                                <%for (Institution institution : institutionDao.findAll()) { %>
                                                <option value="<%=institution.getId()%>"><%=institution.getName() %>
                                                </option>
                                                <%} %>
                                            </select>

                                        </td>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.department"/></td>
                                        <td>

                                            <select name="department" id="department">
                                                <option value="0">Select Below</option>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="7">
                                            <hr/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eDataUrl"/></td>
                                        <td colspan="5"><input type="text" style="width:100%" name="eDataUrl" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.eDataUrl}'/>"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eDataOscarKey"/></td>
                                        <td colspan="5"><input type="text" style="width:100%" name="eDataOscarKey" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.eDataOscarKey}'/>"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eDataServiceKey"/></td>
                                        <td colspan="5"><input type="text" style="width:100%" name="eDataServiceKey" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.eDataServiceKey}'/>"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eDataServiceName"/></td>
                                        <td colspan="5"><input type="text" style="width:100%" name="eDataServiceName" value="<e:forHtmlAttribute value='${EctConAddSpecialistForm.eDataServiceName}'/>"/></td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.hideFromView"/></td>
                                        <td colspan="5">
                                            <select name="hideFromView">
                                                <option value="false" ${!EctConAddSpecialistForm.hideFromView ? 'selected' : ''}>false</option>
                                                <option value="true" ${EctConAddSpecialistForm.hideFromView ? 'selected' : ''}>true</option>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.eform"/></td>
                                        <td colspan="5">
                                            <select name="eformId">
                                                <option value="0" ${EctConAddSpecialistForm.eformId == 0 ? 'selected' : ''}>--None--</option>
                                                <c:forEach var="eform" items="${eforms}">
                                                    <option value="${eform.id}" ${EctConAddSpecialistForm.eformId == eform.id ? 'selected' : ''}>
                                                            ${eform.formName}
                                                    </option>
                                                </c:forEach>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="6">
                                            <input type="hidden" name="whichType" value="<%=whichType%>"/>
                                            <input type="submit" name="transType" value="<%=transactionType%>"/>
                                        </td>
                                    </tr>
                                </table>
                            </form>
                        </td>
                    </tr>
                    <!----End new rows here-->

                    <tr height="100%">
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
