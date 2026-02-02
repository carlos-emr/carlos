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
<%@page import="ca.openosp.openo.prescript.data.RxPatientData" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscarProperties-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ page import="ca.openosp.openo.providers.data.ProSignatureData, ca.openosp.openo.providers.data.ProviderData" %>
<%@ page import="ca.openosp.openo.rx.data.*" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>

<%@ page import="ca.openosp.*,
                 java.lang.*,
                 java.util.Date,
                 java.text.SimpleDateFormat,
                 ca.openosp.openo.prescript.util.RxUtil,
                 org.springframework.web.context.WebApplicationContext,
                 org.springframework.web.context.support.WebApplicationContextUtils,
                 ca.openosp.openo.commn.dao.UserPropertyDAO,
                 ca.openosp.openo.commn.model.UserProperty" %>

<!-- Classes needed for signature injection -->
<%@page import="ca.openosp.openo.commn.model.*" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.openo.utility.DigitalSignatureUtils" %>
<%@page import="ca.openosp.openo.ui.servlet.ImageRenderingServlet" %>
<!-- end -->
<%@ page import="org.owasp.encoder.Encode" %>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String providerNo = loggedInInfo.getLoggedInProviderNo();
    String scriptid = request.getParameter("scriptId");
    String rx_enhance = OscarProperties.getInstance().getProperty("rx_enhance");
    RxSessionBean bean = null;
%>

<%@page import="ca.openosp.openo.web.PrescriptionQrCodeUIBean" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="java.util.Objects" %>
<%@ page import="ca.openosp.openo.prescript.pageUtil.RxSessionBean" %>
<%@ page import="ca.openosp.openo.prescript.data.RxProviderData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPrescriptionData" %>
<%@ page import="ca.openosp.openo.prescript.data.RxPharmacyData" %>
<%@ page import="ca.openosp.openo.commn.model.DemographicExt" %>
<%@ page import="ca.openosp.openo.commn.model.PharmacyInfo" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_rx" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_rx");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<!DOCTYPE html>
<html>
    <head>
            <%--<script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>--%>
            <%--<script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>--%>
            <%--<script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>--%>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.title"/></title>
        <style media="print">
            .noprint {
                display: none;
            }
        </style>
        <style media="all">
            * {
		font-family: Arial, "Helvetica Neue", Helvetica, sans-serif !important;
		font-size: 12px;
		overscroll-behavior: none;
		-webkit-font-smoothing: antialiased;
		-moz-osx-font-smoothing: grayscale;
            }

            #fax-success {
                color: green;
            }

            th {
                border-bottom: 2px solid;
                text-align: left;
            }
        </style>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

        <c:if test="${empty RxSessionBean}">
            <% response.sendRedirect("error.html"); %>
        </c:if>
        <c:if test="${not empty sessionScope.RxSessionBean}">
            <%
                // Directly access the RxSessionBean from the session
                bean = (RxSessionBean) session.getAttribute("RxSessionBean");
                if (bean != null && !bean.isValid()) {
                    response.sendRedirect("error.html");
                    return; // Ensure no further JSP processing
                }
            %>
        </c:if>

            <%--<link rel="stylesheet" type="text/css" href="styles.css">--%>
            <%--<script type="text/javascript" language="Javascript">--%>
            <%--	--%>

            <%--    function onPrint2(method) {--%>

            <%--            document.getElementById("preview2Form").action = "<%= request.getContextPath() %>/form/createcustomedpdf?__title=Rx&__method=" + method;--%>
            <%--            document.getElementById("preview2Form").target="_blank";--%>
            <%--            document.getElementById("preview2Form").submit();--%>
            <%--       return true;--%>
            <%--    }--%>
            <%--</script>--%>

    </head>
    <body topmargin="0" leftmargin="0" vlink="#0000FF">

    <%
        Date rxDate = RxUtil.Today();
//String rePrint = request.getParameter("rePrint");
        String rePrint = (String) request.getSession().getAttribute("rePrint");
//String rePrint = (String)request.getSession().getAttribute("rePrint");
        RxProviderData.Provider provider;
        String signingProvider;
        if (rePrint != null && rePrint.equalsIgnoreCase("true")) {
            bean = (RxSessionBean) session.getAttribute("tmpBeanRX");
            signingProvider = bean.getStashItem(0).getProviderNo();
            rxDate = bean.getStashItem(0).getRxDate();
            provider = new RxProviderData().getProvider(signingProvider);
//    session.setAttribute("tmpBeanRX", null);
            String ip = request.getRemoteAddr();
            //LogAction.addLog((String) session.getAttribute("user"), LogConst.UPDATE, LogConst.CON_PRESCRIPTION, String.valueOf(bean.getDemographicNo()), ip);
        } else {
            bean = (RxSessionBean) pageContext.findAttribute("RxSessionBean");

            //set Date to latest in stash
            Date tmp;

            for (int idx = 0; idx < bean.getStashSize(); ++idx) {
                tmp = bean.getStashItem(idx).getRxDate();
                if (tmp.after(rxDate)) {
                    rxDate = tmp;
                }
            }
            rePrint = "";
            signingProvider = bean.getProviderNo();
            provider = new RxProviderData().getProvider(bean.getProviderNo());
        }

        DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);

        RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, bean.getDemographicNo());
        String patientAddress = patient.getAddress() == null ? "" : patient.getAddress();
        String patientCity = patient.getCity() == null ? "" : patient.getCity();
        String patientProvince = patient.getProvince() == null ? "" : patient.getProvince();
        String patientPostal = patient.getPostal() == null ? "" : patient.getPostal();
        String patientPhone = patient.getPhone() == null ? "" : patient.getPhone();
        String patientHin = patient.getHin() == null ? "" : patient.getHin();


        RxPrescriptionData.Prescription rx = null;
        int i;
        ProSignatureData sig = new ProSignatureData();
        boolean hasSig = sig.hasSignature(signingProvider);
        String doctorName = "";
        if (hasSig) {
            doctorName = sig.getSignature(signingProvider);
        } else {
            doctorName = (provider.getFirstName() + ' ' + provider.getSurname());
        }

//doctorName = doctorName.replaceAll("\\d{6}","");
//doctorName = doctorName.replaceAll("\\-","");

        if ("true".equalsIgnoreCase(OscarProperties.getInstance().getProperty("FIRST_NATIONS_MODULE"))) {
            // Addition of First Nations Band Number to prescriptions
            DemographicExt demographicExtStatusNum = demographicManager.getDemographicExt(loggedInInfo, bean.getDemographicNo(), "statusNum");
            DemographicExt demographicExtBandName = null;
            DemographicExt demographicExtBandFamily = null;
            DemographicExt demographicExtBandFamilyPosition = null;
            String bandNumber = "";
            String bandName = "";
            String bandFamily = "";
            String bandFamilyPosition = "";

            if (demographicExtStatusNum != null) {
                bandNumber = demographicExtStatusNum.getValue();
            }

            if (bandNumber == null) {
                bandNumber = "";
            }

            // if band number is empty try the alternate composite.
            if (bandNumber.isEmpty()) {

                demographicExtBandName = demographicManager.getDemographicExt(loggedInInfo, bean.getDemographicNo(), "fNationCom");
                demographicExtBandFamily = demographicManager.getDemographicExt(loggedInInfo, bean.getDemographicNo(), "fNationFamilyNumber");
                demographicExtBandFamilyPosition = demographicManager.getDemographicExt(loggedInInfo, bean.getDemographicNo(), "fNationFamilyPosition");

                if (demographicExtBandName != null) {
                    bandName = demographicExtBandName.getValue();
                }

                if (demographicExtBandFamily != null) {
                    bandFamily = demographicExtBandFamily.getValue();
                }

                if (demographicExtBandFamilyPosition != null) {
                    bandFamilyPosition = demographicExtBandFamilyPosition.getValue();
                }

                if (bandName == null) {
                    bandName = "";
                }

                if (bandFamily == null) {
                    bandFamily = "";
                }

                if (bandFamilyPosition == null) {
                    bandFamilyPosition = "";
                }

                StringBuilder bandNumberString = new StringBuilder();

                if (!bandName.isEmpty()) {
                    bandNumberString.append(bandName);
                }

                if (!bandFamily.isEmpty()) {
                    bandNumberString.append("-" + bandFamily);
                }

                if (!bandFamilyPosition.isEmpty()) {
                    bandNumberString.append("-" + bandFamilyPosition);
                }

                bandNumber = bandNumberString.toString();
            }

            pageContext.setAttribute("bandNumber", bandNumber);
        }

        OscarProperties props = OscarProperties.getInstance();

        String pracNo = provider.getPractitionerNo();
        String strUser = (String) session.getAttribute("user");
        ProviderData user = new ProviderData(strUser);
        String pharmaFax = "";
        String pharmaFax2 = "";
        String pharmaName = "";
        RxPharmacyData pharmacyData = new RxPharmacyData();
        PharmacyInfo pharmacy;
        String pharmacyId = request.getParameter("pharmacyId");

        if (pharmacyId != null && !"null".equalsIgnoreCase(pharmacyId)) {
            pharmacy = pharmacyData.getPharmacy(pharmacyId);
            if (pharmacy != null) {
                pharmaFax = pharmacy.getFax();
                pharmaFax2 = "<fmt:setBundle basename='oscarResources'/><fmt:message key='RxPreview.msgFax'/>" + ": " + pharmacy.getFax();
                pharmaName = pharmacy.getName();
            }
        }

        String patientDOBStr = RxUtil.DateToString(patient.getDOB(), "MMM d, yyyy");
        boolean showPatientDOB = false;

//check if user prefer to show dob in print
        WebApplicationContext ctx = WebApplicationContextUtils.getRequiredWebApplicationContext(request.getSession().getServletContext());
        UserPropertyDAO userPropertyDAO = (UserPropertyDAO) ctx.getBean(UserPropertyDAO.class);
        UserProperty prop = userPropertyDAO.getProp(signingProvider, UserProperty.RX_SHOW_PATIENT_DOB);
        if (prop != null && prop.getValue().equalsIgnoreCase("yes")) {
            showPatientDOB = true;
        }
    %>
    <form action="${pageContext.request.contextPath}/form/formname.do" method="post" id="preview2Form">
        <input type="hidden" name="demographic_no" value="<%=bean.getDemographicNo()%>"/>
        <table>
            <tr>
                <td>
                    <table id="pwTable" width="400px" height="500px" cellspacing=0 cellpadding=10 border=2 rules="none">
                        <thead>
                        <tr>
                            <th valign=top width="100px">
                                <input type="image"
                                       src="oscarRx/img/rx.gif" border="0" alt="[Submit]"
                                       name="submit" title="Print in a half letter size paper"
                                       onclick="<%=rePrint.equalsIgnoreCase("true") ? "javascript:return onPrint2('rePrint');" : "javascript:return onPrint2('print');"  %>"/>
                                <%
                                    String clinicTitle = provider.getClinicName().replaceAll("\\(\\d{6}\\)", "") + "<br>";
                                    clinicTitle += provider.getClinicAddress() + "<br>";
                                    clinicTitle += provider.getClinicCity() + "   " + provider.getClinicPostal();

                                    if (rx_enhance != null && rx_enhance.equals("true")) {
                                        SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd");
                                        String patientDOB = patient.getDOB() == null ? "" : formatter.format(patient.getDOB());

                                        String docInfo = doctorName + "\n" + provider.getClinicName().replaceAll("\\(\\d{6}\\)", "")
                                                + "<fmt:setBundle basename='oscarResources'/><fmt:message key='RxPreview.PractNo'/>" + pracNo
                                                + "\n" + provider.getClinicAddress() + "\n"
                                                + provider.getClinicCity() + "   "
                                                + provider.getClinicPostal() + "\n"
                                                + "<fmt:setBundle basename='oscarResources'/><fmt:message key='RxPreview.msgTel'/>" + ": "
                                                + provider.getClinicPhone() + "\n"
                                                + "<fmt:setBundle basename='oscarResources'/><fmt:message key='RxPreview.msgFax'/>" + ": "
                                                + provider.getClinicFax();

                                        String patientInfo = patient.getFirstName() + " "
                                                + patient.getSurname() + "\n"
                                                + patientAddress + "\n"
                                                + patientCity + "   "
                                                + patientPostal + "\n"
                                                + "<fmt:setBundle basename='oscarResources'/><fmt:message key='RxPreview.msgTel'/>" + ": " + patientPhone
                                                + (patientDOB != null && !patientDOB.trim().equals("") ? "\n"
                                                + "<fmt:setBundle basename='oscarResources'/><fmt:message key='RxPreview.msgDOB'/>" + ": " + patientDOB : "")
                                                + (!patientHin.trim().equals("") ? "\n" + "<fmt:setBundle basename='oscarResources'/><fmt:message key='oscar.rx.hin'/>" + ": " + patientHin : "");
                                    }
                                %>
                                <input type="hidden" name="doctorName"
                                       value="<%= StringEscapeUtils.escapeHtml4(doctorName) %>"/>
                                <c:choose>
                                    <c:when test="${empty infirmaryView_programAddress}">
                                        <%
                                            UserProperty phoneProp = userPropertyDAO.getProp(provider.getProviderNo(), "rxPhone");
                                            String finalPhone = provider.getClinicPhone();

                                            if (phoneProp != null && phoneProp.getValue().length() > 0) {
                                                finalPhone = phoneProp.getValue();
                                            }

                                            request.setAttribute("phone", finalPhone);
                                        %>
                                        <input type="hidden" name="clinicName"
                                               value="<%= StringEscapeUtils.escapeHtml4(clinicTitle.replaceAll("(<br>)","\\\n")) %>"/>
                                        <input type="hidden" name="clinicPhone"
                                               value="<%= StringEscapeUtils.escapeHtml4(finalPhone) %>"/>
                                        <input type="hidden" id="finalFax" name="clinicFax" value=""/>
                                    </c:when>
                                    <c:otherwise>
                                        <%
                                            UserProperty phoneProp = userPropertyDAO.getProp(provider.getProviderNo(), "rxPhone");
                                            UserProperty faxProp = userPropertyDAO.getProp(provider.getProviderNo(), "faxnumber");

                                            String finalPhone = (String) session.getAttribute("infirmaryView_programTel");
                                            String finalFax = (String) session.getAttribute("infirmaryView_programFax");

                                            if (phoneProp != null && phoneProp.getValue().length() > 0) {
                                                finalPhone = phoneProp.getValue();
                                            }
                                            if (faxProp != null && faxProp.getValue().length() > 0) {
                                                finalFax = faxProp.getValue();
                                            }

                                            request.setAttribute("phone", finalPhone);
                                        %>
                                        <input type="hidden" name="clinicName"
                                               value="<c:out value="${infirmaryView_programAddress}"/>"/>
                                        <input type="hidden" name="clinicPhone" value="<%=finalPhone%>"/>
                                        <input type="hidden" id="finalFax" name="clinicFax" value=""/>
                                    </c:otherwise>
                                </c:choose>
                                <input type="hidden" name="patientName"
                                       value="<%= StringEscapeUtils.escapeHtml4(patient.getFirstName())+ " " +StringEscapeUtils.escapeHtml4(patient.getSurname()) %>"/>
                                <input type="hidden" name="patientDOB"
                                       value="<%= StringEscapeUtils.escapeHtml4(patientDOBStr) %>"/>
                                <input type="hidden" name="pharmaFax" value="<%=pharmaFax%>"/>
                                <input type="hidden" name="pharmaName" value="<%=pharmaName%>"/>
                                <input type="hidden" name="pracNo" value="<%= StringEscapeUtils.escapeHtml4(pracNo) %>"/>
                                <input type="hidden" name="showPatientDOB" value="<%=showPatientDOB%>"/>
                                <input type="hidden" name="pdfId" id="pdfId" value=""/>
                                <input type="hidden" name="patientAddress"
                                       value="<%= StringEscapeUtils.escapeHtml4(patientAddress) %>"/>
                                <%
                                    int check = (patientCity.trim().length() > 0 ? 1 : 0) | (patientProvince.trim().length() > 0 ? 2 : 0);
                                    String patientCityPostal = String.format("%s%s%s %s",
                                            patientCity,
                                            check == 3 ? ", " : check == 2 ? "" : " ",
                                            patientProvince,
                                            patientPostal);

                                    String ptChartNo = "";
                                    if (props.getProperty("showRxChartNo", "").equalsIgnoreCase("true")) {
                                        ptChartNo = patient.getChartNo() == null ? "" : patient.getChartNo();
                                    }
                                %>
                                <input type="hidden" name="patientCityPostal"
                                       value="<%= StringEscapeUtils.escapeHtml4(patientCityPostal)%>"/>
                                <input type="hidden" name="patientHIN"
                                       value="<%= StringEscapeUtils.escapeHtml4(patientHin) %>"/>
                                <input type="hidden" name="patientChartNo"
                                       value="<%=StringEscapeUtils.escapeHtml4(ptChartNo)%>"/>
                                <input type="hidden" name="bandNumber" value="${ bandNumber }"/>
                                <input type="hidden" name="patientPhone"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgTel"/>: <%=StringEscapeUtils.escapeHtml4(patientPhone) %>"/>
                                <input type="hidden" name="rxDate"
                                       value="<%= StringEscapeUtils.escapeHtml4(RxUtil.DateToString(rxDate, "MMMM d, yyyy")) %>"/>
                                <input type="hidden" name="sigDoctorName"
                                       value="<%= StringEscapeUtils.escapeHtml4(doctorName) %>"/>
                                <!--img src="img/prescript.gif" border="0"-->
                            </th>
                            <th valign=top height="100px" id="clinicAddress">
                                <b><%=doctorName%>
                                </b><br>
                                <c:choose>
                                    <c:when test="${empty infirmaryView_programAddress}">
                                        <%= provider.getClinicName().replaceAll("\\(\\d{6}\\)", "") %><br>
                                        <%= provider.getClinicAddress() %><br>
                                        <%= provider.getClinicCity() %>&nbsp;&nbsp;<%=provider.getClinicProvince()%>&nbsp;&nbsp;
                                        <%= provider.getClinicPostal() %>
                                        <% if (provider.getPractitionerNo() != null && !provider.getPractitionerNo().equals("")) { %>
                                        <br><fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.PractNo"/>:<%= provider.getPractitionerNo() %>
                                        <% } %>
                                        <br>
                                        <%
                                            UserProperty phoneProp = userPropertyDAO.getProp(provider.getProviderNo(), "rxPhone");
                                            UserProperty faxProp = userPropertyDAO.getProp(provider.getProviderNo(), "faxnumber");

                                            String finalPhone = provider.getClinicPhone();
                                            String finalFax = provider.getClinicFax();
                                            //if(providerPhone != null) {
                                            //	finalPhone = providerPhone;
                                            //}
                                            if (phoneProp != null && phoneProp.getValue().length() > 0) {
                                                finalPhone = phoneProp.getValue();
                                            }

                                            if (faxProp != null && faxProp.getValue().length() > 0) {
                                                finalFax = faxProp.getValue();
                                            }

                                            request.setAttribute("phone", finalPhone);

                                        %>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgTel"/>: <%= finalPhone %><br>
                                        <oscar:oscarPropertiesCheck property="RXFAX" value="yes">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgFax"/>: <%= finalFax %><br>
                                        </oscar:oscarPropertiesCheck>
                                    </c:when>
                                    <c:otherwise>
                                        <%
                                            UserProperty phoneProp = userPropertyDAO.getProp(provider.getProviderNo(), "rxPhone");
                                            UserProperty faxProp = userPropertyDAO.getProp(provider.getProviderNo(), "faxnumber");

                                            String finalPhone = (String) session.getAttribute("infirmaryView_programTel");
                                            String finalFax = (String) session.getAttribute("infirmaryView_programFax");

                                            //if(providerPhone != null) {
                                            //	finalPhone = providerPhone;
                                            //}
                                            if (phoneProp != null && phoneProp.getValue().length() > 0) {
                                                finalPhone = phoneProp.getValue();
                                            }

                                            if (faxProp != null && faxProp.getValue().length() > 0) {
                                                finalFax = faxProp.getValue();
                                            }

                                            request.setAttribute("phone", finalPhone);

                                        %>
                                        <c:out value="${infirmaryView_programAddress}" escapeXml="false"/><br>
                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgTel"/>: <%=finalPhone %><br>
                                        <oscar:oscarPropertiesCheck property="RXFAX" value="yes">
                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgFax"/>: <%=finalFax %>
                                        </oscar:oscarPropertiesCheck>
                                    </c:otherwise>
                                </c:choose>
                            </th>
                        </tr>
                        <tr>
                            <th colspan=2 valign=top height="75px">
								<span style="float: left">
									<%= Encode.forHtmlContent(patient.getFirstName()) %> <%= Encode.forHtmlContent(patient.getSurname()) %> <%if (showPatientDOB) {%><br>DOB:<%= Encode.forHtmlContent(StringEscapeUtils.escapeHtml4(patientDOBStr)) %> <%}%><br>
										<%= Encode.forHtmlContent(patientAddress) %><br>
										<%= Encode.forHtmlContent(patientCityPostal) %><br>
										<%= Encode.forHtmlContent(patientPhone) %><br>
										<oscar:oscarPropertiesCheck value="true" property="showRxBandNumber">
                                            <c:if test="${ not empty bandNumber }">
                                                <br/>
                                                <b><fmt:setBundle basename="oscarResources"/><fmt:message key="ca.openosp.openo.rx.bandNumber"/></b>
                                                <c:out value="${ bandNumber }"/>
                                            </c:if>
                                        </oscar:oscarPropertiesCheck>
										<b>
											<% if (!props.getProperty("showRxHin", "").equals("false")) { %>
												<fmt:setBundle basename="oscarResources"/><fmt:message key="ca.openosp.openo.rx.hin"/><%= Encode.forHtmlContent(patientHin) %>
											<% } %>
										</b><br>
										<% if (props.getProperty("showRxChartNo", "").equalsIgnoreCase("true")) { %>
											<fmt:setBundle basename="oscarResources"/><fmt:message key="ca.openosp.openo.rx.chartNo"/><%=ptChartNo%>
										<% } %>
								</span>
                                <span style="float:right">
									<%= RxUtil.DateToString(rxDate, "MMMM d, yyyy", request.getLocale()) %>
								</span>
                            </th>
                        </tr>
                        </thead>
                        <tfoot>
                        <% if (ca.openosp.OscarProperties.getInstance().getProperty("RX_FOOTER") != null) {
                            out.write(ca.openosp.OscarProperties.getInstance().getProperty("RX_FOOTER"));
                        } %>

                        <tr valign=bottom>
                            <td height=25px width=25%><fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgSignature"/>:</td>
                            <td height=25px width=75% style="border-bottom: 2px solid;">
                                <%
                                    String signatureRequestId = null;
                                    String imageUrl = null;
                                    String startimageUrl = null;
                                    String statusUrl = null;

                                    signatureRequestId = loggedInInfo.getLoggedInProviderNo();
                                    imageUrl = request.getContextPath() + "/imageRenderingServlet?source=" + ImageRenderingServlet.Source.signature_preview.name() + "&" + DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY + "=" + signatureRequestId;
                                    startimageUrl = request.getContextPath() + "/images/1x1.gif";
                                    statusUrl = request.getContextPath() + "/PMmodule/ClientManager/check_signature_status.jsp?" + DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY + "=" + signatureRequestId;

								if (bean.getStashSize() > 0 && Objects.nonNull(bean.getStashItem(0).getDigitalSignatureId())) {
									startimageUrl=request.getContextPath()+"/imageRenderingServlet?source="+ImageRenderingServlet.Source.signature_stored.name()+"&digitalSignatureId="+bean.getStashItem(0).getDigitalSignatureId();
								}


                                %>

                                <input type="hidden" name="<%= DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY %>"
                                       value="<%=signatureRequestId%>"/>
                                <img id="signature" style="width:300px; height:60px" src="<%=startimageUrl%>"
                                     alt="digital_signature"/>
                                <input type="hidden" name="imgFile" id="imgFile" value=""/>

                                <script type="text/javascript">
                                    var POLL_TIME = 2500;
                                    var counter = 0;

                                    function refreshImage() {
                                        counter++;
                                        var img = document.getElementById("signature");
                                        img.src = '<%=imageUrl%>&rand=' + counter;

                                        // Modern fetch API
                                        fetch('<%=statusUrl%>', {
                                            method: 'POST',
                                            headers: {
                                                'Accept': 'text/html'
                                            }
                                        })
                                        .then(function(response) {
                                            return response.text();
                                        })
                                        .then(function(data) {
                                            var x = data.trim();
                                        })
                                        .catch(function(error) {
                                            console.error('Error checking signature status:', error);
                                        });
                                    }
                                </script>
                                &nbsp;
                            </td>
                        </tr>

                        <tr valign=bottom>
                            <td height=25px>
                                <%-- Topaz signature pad support removed - HTML5 signature capture is now standard --%>
                            </td>
                            <td height=25px>
                                &nbsp; <%= Encode.forHtmlContent(doctorName) %>
                                <% if (pracNo != null && !pracNo.equals("") && !pracNo.equalsIgnoreCase("null")) { %>
                                <br>
                                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.PractNo"/> <%= pracNo%>
                                <% } %>
                            </td>
                        </tr>

                        <%
                            if (bean.getStashSize() > 0) {
                                rx = bean.getStashItem(0);
                                if (rePrint.equalsIgnoreCase("true") && rx != null) {
                        %>
                        <tr valign=bottom>
                            <td height=55px colspan="2">
										<span style="float:right; font-size:10px;">
											<fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgReprintBy"/> <%=Encode.forHtmlContent(ProviderData.getProviderName(strUser))%> <br>
											<fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgOrigPrinted"/>:&nbsp;<%=rx.getPrintDate()%> <br>
											<fmt:setBundle basename="oscarResources"/><fmt:message key="RxPreview.msgTimesPrinted"/>:&nbsp;<%=String.valueOf(rx.getNumPrints())%>
										</span>
                                <input type="hidden" name="origPrintDate" value="<%=rx.getPrintDate()%>"/>
                                <input type="hidden" name="numPrints" value="<%=String.valueOf(rx.getNumPrints())%>"/>
                                <input type="hidden" name="rxReprint" value="true"/>
                            </td>
                        </tr>
                        <%
                                }
                            }
                        %>

                        <% if (PrescriptionQrCodeUIBean.isPrescriptionQrCodeEnabledForProvider(providerNo)) { %>
                        <tr>
                            <td colspan="2">
                                <img src="<%=request.getContextPath()%>/contentRenderingServlet/prescription_qr_code_<%=rx.getScript_no()%>.png?source=prescriptionQrCode&prescriptionId=<%=rx.getScript_no()%>"
                                     alt="qr_code"/>
                            </td>
                        </tr>
                        <% } %>

                        <% if (ca.openosp.OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT") != null && ca.openosp.OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT").length() > 0) { %>
                        <tr valign=bottom align="center">
                            <td height=25px colspan="2" style="font-size: 9px"></br>
                                <%= ca.openosp.OscarProperties.getInstance().getProperty("FORMS_PROMOTEXT") %>
                            </td>
                        </tr>
                        <% } %>
                        </tfoot>
                        <tbody>
                        <%
                            String strRx = "";
                            StringBuffer strRxNoNewLines = new StringBuffer();

                            for (i = 0; i < bean.getStashSize(); i++) {
                                rx = bean.getStashItem(i);
                                String fullOutLine = rx.getFullOutLine().replaceAll(";", "<br />");

                                if (fullOutLine == null || fullOutLine.length() <= 6) {
                                    ca.openosp.openo.utility.MiscUtils.getLogger();
                                    fullOutLine = "<span style=\"color:red;font-size:16;font-weight:bold\">An error occurred, please write a new prescription.</span><br />" + fullOutLine;
                                }
                        %>
                        <tr style="page-break-inside: avoid;">
                            <td colspan=2 style><%=fullOutLine%>
                            </td>
                        </tr>

                        <%
                                strRx += rx.getFullOutLine() + ";;";
                                strRxNoNewLines.append(rx.getFullOutLine().replaceAll(";", " ") + "\n");
                            }
                        %>
                        <tr valign="bottom">
                            <td colspan="2" id="additNotes"></td>
                        </tr>

                        <input type="hidden" name="rx"
                               value="<%= StringEscapeUtils.escapeHtml4(strRx.replaceAll(";","\\\n")) %>"/>
                        <input type="hidden" name="rx_no_newlines" value="<%= strRxNoNewLines.toString() %>"/>
                        <input type="hidden" name="additNotes" value=""/>
                        </tbody>
                    </table>
                </td>
                <td style="vertical-align: top;padding: 5px;">
                    <div id="pharmInfo">
                    </div>
                </td>
            </tr>
        </table>
    </form>
    </body>
</html>
