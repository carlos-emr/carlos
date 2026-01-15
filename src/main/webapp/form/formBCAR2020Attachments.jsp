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
    String user = (String) session.getAttribute("user");
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName2$ = (String) session.getAttribute("userrole") + "," + user;
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import=" ca.openosp.openo.form.*, java.util.Properties" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.util.UtilMisc" %>
<%@ page import="ca.openosp.openo.casemgmt.service.CaseManagementManager" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.util.StringUtils" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.form.FrmBCAR2020Record" %>
<%@ page import="ca.openosp.openo.form.FrmRecordFactory" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    String formClass = "BCAR2020";
    Integer pageNo = 6;
    String formLink = "formBCAR2020Attachments.jsp";

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));
    String provNo = (String) session.getAttribute("user");
    String providerNo = request.getParameter("provider_no") != null ? request.getParameter("provider_no") : loggedInInfo.getLoggedInProviderNo();
    String appointment = request.getParameter("appointmentNo") != null ? request.getParameter("appointmentNo") : "";

    FrmBCAR2020Record rec = (FrmBCAR2020Record) (new FrmRecordFactory()).factory(formClass);
    Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId, pageNo);

%>
<!DOCTYPE HTML>
<html>
    <head>

        <title>BC Antenatal Record 2020 Attachments/Additional Info</title>

        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%=request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/formBCAR2020Record.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-1.12.0.min.js"
                type="text/javascript"></script>

        <script src="<%=request.getContextPath()%>/js/fg.menu.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/jquery.are-you-sure.js"></script>
        <!-- Checkbox multi-select -->
        <script src="<%=request.getContextPath() %>/js/bootstrap.bundle.min.js"></script>
        <script src="<%=request.getContextPath() %>/js/bootstrap-select.min.js"></script>

        <script src="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.12.1.min.js"
                type="text/javascript"></script>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/bootstrap4.1.1.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/bootstrap-select.css"/>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/jquery/jquery-ui.structure-1.12.1.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/jquery/jquery-ui.theme-1.12.1.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/formBCAR2020.css">

        <!-- Field Naming Scheme throughout BCAR2020
        c_XXXX Is a checkbox field
        d_XXXX Is a textbox field containing a date
        t_XXXX Is a textbox field containing text
        s_XXXX Is a drop down field (select field)
        mt_XXXX Is a text blob (multiline text field that can store lots of text/data)
        -->

        <script type="text/javascript">
            $(document).ready(function () {
                init(6);

                // Buttons that get the Clinical Data
                $(".clinicalData").click(function () {
                    var data = new Object();
                    var target = "#" + this.id.split("|")[1];
                    data.method = this.id.split("|")[0];
                    data.demographicNo = <%= demoNo %>;
                    getClinicalData(data, target)
                });

                $('form').areYouSure({'addRemoveFieldsMarksDirty': true});
            });

            /*
            * JQuery dirty form check
            */
            $(function () {

                //dirty form enable/disable save button.
                $("form").find('input[value="Save"]').attr('disabled', true);
                $("form").find('input[value="Save and Exit"]').attr('disabled', true);
                $("form").find('input[value="Exit"]').removeAttr('disabled');

                $('form').on('dirty.areYouSure', function () {

                    $(this).find('input[value="Save"]').removeAttr('disabled');
                    $(this).find('input[value="Save and Exit"]').removeAttr('disabled');
                    $(this).find('input[value="Exit"]').attr('disabled', true);
                });

                $('form').on('clean.areYouSure', function () {

                    $(this).find('input[value="Save"]').attr('disabled', true);
                    $(this).find('input[value="Save and Exit"]').attr('disabled', true);
                    $(this).find('input[value="Exit"]').removeAttr('disabled');
                });

            });

            /*
             * reload the are you sure form check. Usually after a
             * javascript is run.
             */
            const recheckForm = function () {
                $('form').trigger('checkform.areYouSure');
            }

            function getClinicalData(data, target) {
                $.ajax({
                    method: "POST",
                    url: "${ pageContext.request.contextPath }/oscarConsultationRequest/consultationClinicalData.do",
                    data: data,
                    dataType: 'JSON',
                    success: function (data) {
                        var currentVal = $(target).val();
                        $(target).val(currentVal + (currentVal === "" || currentVal.charAt(currentVal.length - 1) === "\n" ? "" : "\n") + data.note);
                    }
                });
            }

            function importFromEnct(reqInfo, txtArea) {
                var info = "";
                <%
                    String value = ""; 
                    String demoNoStr = String.valueOf(demoNo);
                    CaseManagementManager cmgmtMgr = (CaseManagementManager)SpringUtils.getBean(CaseManagementManager.class);
                    
                    UserPropertyDAO userPropertyDAO = (UserPropertyDAO)SpringUtils.getBean(UserPropertyDAO.class);
                    UserProperty fmtProperty = userPropertyDAO.getProp(providerNo, UserProperty.CONSULTATION_REQ_PASTE_FMT);
                    String pasteFmt = fmtProperty != null?fmtProperty.getValue():null;
                %>
                switch (reqInfo) {
                    case "MedicalHistory":
                    <%
                        value = cmgmtMgr.listNotes("MedHistory", providerNo, demoNoStr);

                        if (pasteFmt == null || pasteFmt.equalsIgnoreCase("single"))
                        {
                            value = StringUtils.lineBreaks(value);
                        }

                        out.println("info = '" + Encode.forJavaScript(value) + "'");
                    %>
                        break;
                    case "ongoingConcerns":
                    <%
                        value = cmgmtMgr.listNotes("Concerns", providerNo, demoNoStr);

                        if (pasteFmt == null || pasteFmt.equalsIgnoreCase("single"))
                        {
                            value = StringUtils.lineBreaks(value);
                        }

                        out.println("info = '" + Encode.forJavaScript(value) + "'");
                    %>
                        break;
                    case "FamilyHistory":
                    <%
                        value = cmgmtMgr.listNotes("FamHistory", providerNo, demoNoStr);

                        if (pasteFmt == null || pasteFmt.equalsIgnoreCase("single"))
                        {
                            value = StringUtils.lineBreaks(value);
                        }

                        out.println("info = '" + Encode.forJavaScript(value) + "'");
                    %>
                        break;
                    case "SocialHistory":
                    <%
                        value = cmgmtMgr.listNotes("SocHistory", providerNo, demoNoStr);

                        if (pasteFmt == null || pasteFmt.equalsIgnoreCase("single"))
                        {
                            value = StringUtils.lineBreaks(value);
                        }

                        out.println("info = '" + Encode.forJavaScript(value) + "'");
                    %>
                        break;
                    case "OtherMeds":
                    <%
                        value = cmgmtMgr.listNotes("OMeds", providerNo, demoNoStr);

                        if (pasteFmt == null || pasteFmt.equalsIgnoreCase("single"))
                        {
                            value = StringUtils.lineBreaks(value);
                        }

                        out.println("info = '" + Encode.forJavaScript(value) + "'");
                    %>
                        break;
                    case "Reminders":
                    <%
                        value = cmgmtMgr.listNotes("Reminders", providerNo, demoNoStr);

                        if (pasteFmt == null || pasteFmt.equalsIgnoreCase("single"))
                        {
                            value = StringUtils.lineBreaks(value);
                        }

                        out.println("info = '" + Encode.forJavaScript(value) + "'");
                    %>
                        break;
                }

                if (txtArea.value.length > 0 && txtArea.value.charAt(txtArea.value.length - 1) !== "\n" && info.length > 0)
                    txtArea.value += '\n';

                txtArea.value += info;
                txtArea.scrollTop = txtArea.scrollHeight;
                txtArea.focus();
            }
        </script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    </head>

    <body bgproperties="fixed" topmargin="0" leftmargin="1" rightmargin="1">
    <div id="maincontent">
        <div id="content_bar" class="innertube">
            <form action="${pageContext.request.contextPath}/form/BCAR2020.do" method="post">
                <input type="hidden" id="demographicNo" name="demographicNo" value="<%=demoNo%>"/>
                <input type="hidden" id="formId" name="formId" value="<%=formId%>"/>
                <input type="hidden" name="provider_no" value=<%=Encode.forHtmlAttribute(providerNo)%>/>
                <input type="hidden" id="user" name="provNo" value=<%=provNo%>/>
                <input type="hidden" name="method" value="exit"/>

                <input type="hidden" name="forwardTo" value="<%=pageNo%>"/>
                <input type="hidden" name="pageNo" value="<%=pageNo%>"/>
                <input type="hidden" name="formCreated" value="<%= props.getProperty("formCreated", "") %>"/>

                <input type="hidden" id="printPg1" name="printPg1" value=""/>
                <input type="hidden" id="printPg2" name="printPg2" value=""/>
                <input type="hidden" id="printPg3" name="printPg3" value=""/>
                <input type="hidden" id="printPg4" name="printPg4" value=""/>
                <input type="hidden" id="printPg5" name="printPg5" value=""/>
                <input type="hidden" id="printPg6" name="printPg6" value=""/>

                <!-- Option Header -->
                <table class="sectionHeader hidePrint">
                    <tr>
                        <td align="left" rowspan="2" width="58%" style="padding:10px !important;">
                            <input type="submit" class="btn btn-primary" value="Save" onclick="return onSave();"/>
                            <input type="submit" class="btn btn-secondary" value="Save and Exit"
                                   onclick="return onSaveExit();"/>

                            <input type="submit" class="btn btn-danger" value="Exit" onclick="window.close();"/>
                            <input type="submit" class="btn btn-secondary" value="Print" onclick="return onPrint();"/>
                            <span style="display:none"><input id="printBtn" type="submit" value="PrintIt"/></span>

                        </td>
                        <td align="right" rowspan="2" width="5%" valign="top">
                            <b>
                                Edit:
                            </b>
                        </td>
                        <td align="right" width="37%">
                            <a href="javascript:void(0);" onclick="return onPageChange('1');">Part 1</a>
                            |
                            <a href="javascript:void(0);" onclick="return onPageChange('2');">Part 2 (Page 1)</a>
                            |
                            <a href="javascript:void(0);" onclick="return onPageChange('3');">Part 2 (Page 2)</a>
                        </td>
                    </tr>
                    <tr>
                        <td align="right">
                            <b>
                                <a href="javascript:void(0);" onclick="return onPageChange('6');" class="small10">Attachments</a>
                            </b>
                            |
                            <a href="javascript:void(0);" onclick="return onPageChange('4');" class="small10">Reference
                                Page 1</a>
                            |
                            <a href="javascript:void(0);" onclick="return onPageChange('5');" class="small10">Reference
                                Page 2</a>
                        </td>
                    </tr>
                </table>

                <!-- Page Heading -->
                <table width="100%" border="0" cellspacing="0" cellpadding="1">
                    <tr>
                        <th align="left">British Columbia Antenatal Record Attachments <font size="-2">PSBC 1905 -
                            January 2020</font></th>
                    </tr>
                </table>

                <table width="100%" border="0" cellspacing="1" cellpadding="1" class="small9">
                    <tr>
                        <td width="60%" valign="top">
                            <p>
                                This page is not part of the standard BC Antenatal Record 2020 form. Any additional
                                information that does not fit onto the standard form can be added to the sections below.
                            </p>
                            <p>
                                The buttons above each section will pull information from the patients E-Chart into the
                                section below.

                                If a section is checked (enabled), it will print as part of the attachments. If the
                                section is unchecked (disabled), it will not print.
                            </p>
                            <p>
                                The Privacy Statement is a requirement by PHSA, it cannot be modified or removed, and
                                must be printed as part of the BCAR2020.
                            </p>
                        </td>

                        <td width="40%" valign="top">
                            <!-- Addressograph/Label -->
                            <table width="100%" border="0" cellspacing="0" cellpadding="1">
                                <tr>
                                    <td valign="top" width="50%">
                                        Surname<br/>
                                        <input type="text" name="t_patientSurname" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientSurname", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td valign="top" width="50%" colspan="2">
                                        Given name<br/>
                                        <input type="text" name="t_patientGivenName" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientGivenName", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3">
                                        Address - Number, Street name<br/>
                                        <input type="text" name="t_patientAddress" style="width: 100%" size="60"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientAddress", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="50%">
                                        City<br/>
                                        <input type="text" name="t_patientCity" style="width: 100%" size="60"
                                               maxlength="50"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientCity", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        Province<br/>
                                        <input type="text" name="t_patientProvince" style="width: 100%" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientProvince", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        Postal Code<br/>
                                        <input type="text" name="t_patientPostal" style="width: 100%" size="60"
                                               maxlength="10"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientPostal", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3">
                                        <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                            <tr>
                                                <td width="33%">
                                                    Home Phone Number
                                                </td>
                                                <td width="33%">
                                                    Work Phone Number
                                                </td>
                                                <td width="34%">
                                                    Cell Phone Number
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <input type="text" name="t_patientPhone" style="width: 100%"
                                                           size="60" maxlength="15"
                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientPhone", "")) %>"/>
                                                </td>
                                                <td>
                                                    <input type="text" name="t_patientPhoneWork" style="width: 100%"
                                                           size="60" maxlength="15"
                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientPhoneWork", "")) %>"/>
                                                </td>
                                                <td>
                                                    <input type="text" name="t_patientPhoneCell" style="width: 100%"
                                                           size="60" maxlength="15"
                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientPhoneCell", "")) %>"/>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3">
                                        Personal Health Number<br/>
                                        <input type="text" name="t_patientHIN" style="width: 100%" size="60"
                                               maxlength="10"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientHIN", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2">
                            <table width="100%" border="1" cellspacing="0" cellpadding="4">
                                <tr>
                                    <td valign="top">
                                        <!-- Medications -->
                                        <input type="checkbox"
                                               name="c_attMedications" <%=Encode.forHtmlAttribute(props.getProperty("c_attMedications", "").equals("X") ? "checked" : "") %> />

                                        <span class="title">Medications</span>
                                        <p>You can either click any of the buttons below to pull in any medications from
                                            the E-Chart, or enter your own in the box below.</p>
                                        <input id="fetchMedications|mt_attMedications" type="button"
                                               class="btn-small clinicalData" value="Medications"/>&nbsp;
                                        <input id="fetchLongTermMedications|mt_attMedications" type="button"
                                               class="btn-small clinicalData" value="Long Term Medications"/>&nbsp;
                                        <input id="fetchOtherMeds" type="button" class="btn-small"
                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportOtherMeds"/>"
                                               onclick="importFromEnct('OtherMeds',document.forms[0].mt_attMedications);"/>
                                        <br/>
                                        <textarea name="mt_attMedications" id="mt_attMedications"
                                                  style="width: 100%; height:180px;" size="30"
                                                  title="Medications"><%= UtilMisc.htmlEscape(props.getProperty("mt_attMedications", "")) %></textarea>
                                    </td>
                                </tr>
                            </table>
                            <table width="100%" border="1" cellspacing="0" cellpadding="4">
                                <tr>
                                    <td valign="top">
                                        <!-- Allergies -->
                                        <input type="checkbox"
                                               name="c_attAllergies" <%=Encode.forHtmlAttribute(props.getProperty("c_attAllergies", "").equals("X") ? "checked" : "") %> />

                                        <span class="title">Allergies</span>
                                        <p>You can either click the button below to pull in any allergies from the
                                            E-Chart, or enter your own in the box below.</p>
                                        <input id="fetchAllergies|mt_attAllergies" type="button"
                                               class="btn-small clinicalData" value="Allergies"/>
                                        <br/>
                                        <textarea name="mt_attAllergies" id="mt_attAllergies"
                                                  style="width: 100%; height:180px;" size="30"
                                                  title="Allergies"><%= UtilMisc.htmlEscape(props.getProperty("mt_attAllergies", "")) %></textarea>
                                    </td>
                                </tr>
                            </table>
                            <table width="100%" border="1" cellspacing="0" cellpadding="4">
                                <tr>
                                    <td valign="top">
                                        <!-- Additional Information -->
                                        <input type="checkbox"
                                               name="c_attAdditionalInfo" <%=Encode.forHtmlAttribute(props.getProperty("c_attAdditionalInfo", "").equals("X") ? "checked" : "") %> />

                                        <span class="title">Additional Information</span>
                                        <p>The buttons below will pull information in from the E-Chart.</p>
                                        <input id="fetchAddInfoSocHistory" type="button" class="btn-small"
                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportSocHistory"/>"
                                               onclick="importFromEnct('SocialHistory',document.forms[0].mt_attAdditionalInfo);"/>&nbsp;
                                        <input id="fetchAddInfoFamHistory" type="button" class="btn-small"
                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportFamHistory"/>"
                                               onclick="importFromEnct('FamilyHistory',document.forms[0].mt_attAdditionalInfo);"/>&nbsp;
                                        <input id="fetchAddInfoMedHistory" type="button" class="btn-small"
                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportMedHistory"/>"
                                               onclick="importFromEnct('MedicalHistory',document.forms[0].mt_attAdditionalInfo);"/>&nbsp;
                                        <input id="fetchAddInfoConcerns" type="button" class="btn-small"
                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportConcerns"/>"
                                               onclick="importFromEnct('ongoingConcerns',document.forms[0].mt_attAdditionalInfo);"/>&nbsp;
                                        <input id="fetchAddInfoReminders" type="button" class="btn-small"
                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.btnImportReminders"/>"
                                               onclick="importFromEnct('Reminders',document.forms[0].mt_attAdditionalInfo);"/>&nbsp;
                                        <input id="fetchRiskFactors|mt_attAdditionalInfo" type="button"
                                               class="btn-small clinicalData" value="Risk Factors"/>&nbsp;
                                        <br/>
                                        <textarea name="mt_attAdditionalInfo" id="mt_attAdditionalInfo"
                                                  style="width: 100%; height:180px;" size="30"
                                                  title="Additional Information"><%= UtilMisc.htmlEscape(props.getProperty("mt_attAdditionalInfo", "")) %></textarea>
                                    </td>
                                </tr>
                            </table>
                            <!-- Documents -->
                            <!-- TODO - This is where document attachments could be done in the future -->
                            <!-- EForms -->
                            <!-- TODO - This is where eForm attachments could be done in the future -->
                            <table width="100%" border="1" cellspacing="0" cellpadding="4">
                                <tr>
                                    <td valign="top">
                                        <!-- Privacy Statement -->
                                        <span class="title">Privacy Statement </span><span
                                            class="sub-text">Mandatory</span>
                                        <p>Printing the privacy statement as part of the BCAR2020 is a requirement by
                                            PHSA. It cannot be modified or removed.</p>
                                        <textarea style="width: 100%; height:110px;" size="30" readonly>Perinatal Services BC collects, uses and discloses personal information only as authorized under section 26(c), 33, and 35 of the BC Freedom of Information and Protection of Privacy Act, other legislation and PHSA's Privacy and Confidentiality Policy.  Information is collected for the purposes of supporting health care providers, leaders, researchers, and policymakers in their work to improve maternal, fetal, and neonatal health.  This includes work carried out for purposes of research, surveillance, program delivery, and evaluation.  We take all reasonable steps to make sure personal information is treated confidentially, is used only for the intended purpose and securely stored.  For questions regarding collection, use or disclosure of personal information, please contact the Provincial Director, Informatics and Privacy at (604) 877-2121 extension 223761 or business address:  Perinatal Services BC, Suite 350 - West Tower, 555 West 12th Avenue, Vancouver, BC   V5Z 3X7.</textarea>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
    </div>
    <div id="print-dialog" title="Print BCAR2020 Record">
        <p class="validateTips"></p>
        <p>Note: Remember to Save any changes before printing.</p>

        <div>
            <input type="checkbox" onclick="return printSelectAll();" id="print_all"
                   class="text ui-widget-content ui-corner-all"/>
            <label for="print_all" class="small10">Select All</label>
        </div>
        <form>
            <fieldset>
                <input type="checkbox" name="print_pr1" id="print_pr1" checked="checked"
                       class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr1">Part 1</label>
                <br/>
                <input type="checkbox" name="print_pr2" id="print_pr2" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr2">Part 2 (Page 1)</label>
                <br/>
                <input type="checkbox" name="print_pr3" id="print_pr3" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr3">Part 2 (Page 2)</label>
                <br/>
                <input type="checkbox" name="print_att" id="print_att" class="text ui-widget-content ui-corner-all"/>
                <label for="print_att">Attachments/Additional Info</label>
                <br/>
                <input type="checkbox" name="print_pr4" id="print_pr4" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr4">Reference Page 1</label>
                <br/>
                <input type="checkbox" name="print_pr5" id="print_pr5" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr5">Reference Page 2</label>
                <br/>
            </fieldset>
        </form>
    </div>
    </body>


</html>