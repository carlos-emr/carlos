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

<%@ page import=" ca.openosp.openo.form.*, ca.openosp.openo.form.data.*, java.util.Properties" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.util.UtilMisc" %>
<%@ page import="ca.openosp.openo.form.FrmBCAR2020Record" %>
<%@ page import="ca.openosp.openo.form.FrmRecordFactory" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    String formClass = "BCAR2020";
    Integer pageNo = 5;
    String formLink = "formBCAR2020pg5.jsp";

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

        <title>BC Antenatal Record 2020 Reference Page 2</title>

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
        -->

        <script type="text/javascript">
            $(document).ready(function () {
                init(5);
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
        </script>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    </head>

    <body bgproperties="fixed">
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
                            <a href="javascript:void(0);" onclick="return onPageChange('6');" class="small10">Attachments</a>
                            |
                            <a href="javascript:void(0);" onclick="return onPageChange('4');" class="small10">Reference
                                Page 1</a>
                            |
                            <b>
                                <a href="javascript:void(0);" onclick="return onPageChange('5');" class="small10">Reference
                                    Page 2</a>
                            </b>
                        </td>
                    </tr>
                </table>

                <!-- Page Heading -->
                <table width="100%" border="0" cellspacing="0" cellpadding="1">
                    <tr>
                        <th align="left">British Columbia Antenatal Record Reference Page 2 <font size="-2">PSBC 1905 -
                            January 2020</font></th>
                    </tr>
                </table>

                <table width="100%" border="0" cellspacing="1" cellpadding="1" class="small9">
                    <tr>
                        <td>
                            <img src="graphics/BCAR2020_ref_pg2_top.png" width="100%"/>
                        </td>
                    </tr>
                    <tr>
                        <table width="100%" border="1" cellspacing="0" cellpadding="0" class="reference-table">
                            <tr>
                                <td>
                                    <div class="reference-header-1">
                                        Discussion Topics
                                    </div>
                                </td>
                            </tr>

                            <!-- 1st-3rd Trimester -->
                            <tr>
                                <td>
                                    <div class="reference-header-2">
                                        1st-3rd Trimester (as indicated)
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <table width="100%" border="0" cellspacing="0" cellpadding="3"
                                           class="reference-table">
                                        <tr>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterNutrition" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterNutrition", "").equals("X") ? "checked" : "") %> />
                                                    Nutrition/folic acid
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterWeightGain" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterWeightGain", "").equals("X") ? "checked" : "") %> />
                                                    Healthy weight gain
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterPhysicalActivity" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterPhysicalActivity", "").equals("X") ? "checked" : "") %> />
                                                    Physical activity
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterOccupation" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterOccupation", "").equals("X") ? "checked" : "") %> />
                                                    Occupational concerns
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterPersonalSafety" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterPersonalSafety", "").equals("X") ? "checked" : "") %> />
                                                    Personal Safety
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterSupportSystem" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterSupportSystem", "").equals("X") ? "checked" : "") %> />
                                                    Support system
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterMentalHealth" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterMentalHealth", "").equals("X") ? "checked" : "") %> />
                                                    Mental health
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterSubstanceUse" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterSubstanceUse", "").equals("X") ? "checked" : "") %> />
                                                    Substance Use (i.e. alchohol, drugs)
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterSexualActivity" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterSexualActivity", "").equals("X") ? "checked" : "") %> />
                                                    Sexual activity, STI risk factors, screening
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterImmunization" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterImmunization", "").equals("X") ? "checked" : "") %> />
                                                    Immunization
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterVBAC" <%=Encode.forHtmlAttribute(props.getProperty("c_1st3rdTrimesterVBAC", "").equals("X") ? "checked" : "") %> />
                                                    VBAC counseling (if applicable)
                                                </div>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- 1st Trimester -->
                            <tr>
                                <td>
                                    <div class="reference-header-2">
                                        1st Trimester
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <table width="100%" border="0" cellspacing="0" cellpadding="3"
                                           class="reference-table">
                                        <tr>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterNausea" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterNausea", "").equals("X") ? "checked" : "") %> />
                                                    Nausea/vomiting
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterSafety" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterSafety", "").equals("X") ? "checked" : "") %> />
                                                    Safety: food, medications/ vitamins/ supplements, seatbelts
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterOralHealth" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterOralHealth", "").equals("X") ? "checked" : "") %> />
                                                    Oral health
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterExposures" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterExposures", "").equals("X") ? "checked" : "") %> />
                                                    Exposures: infections, pets, environment, occupation
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterTravel" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterTravel", "").equals("X") ? "checked" : "") %> />
                                                    Travel
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterGeneticScreening" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterGeneticScreening", "").equals("X") ? "checked" : "") %> />
                                                    Prenatal genetic screening
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterEarlyLoss" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterEarlyLoss", "").equals("X") ? "checked" : "") %> />
                                                    Early pregnancy loss: signs / symptoms, what to do
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterRoutine" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterRoutine", "").equals("X") ? "checked" : "") %> />
                                                    Routine prenatal care, emergency contact/ on-call providers
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterBreastfeeding" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterBreastfeeding", "").equals("X") ? "checked" : "") %> />
                                                    Breastfeeding: attitudes/beliefs
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterQualityEducation" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterQualityEducation", "").equals("X") ? "checked" : "") %> />
                                                    Quality educational resources
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterPublicServices" <%=Encode.forHtmlAttribute(props.getProperty("c_1stTrimesterPublicServices", "").equals("X") ? "checked" : "") %> />
                                                    Public health services / programs
                                                </div>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- 2nd Trimester -->
                            <tr>
                                <td>
                                    <div class="reference-header-2">
                                        2nd Trimester
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <table width="100%" border="0" cellspacing="0" cellpadding="3"
                                           class="reference-table">
                                        <tr>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterBleeding" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterBleeding", "").equals("X") ? "checked" : "") %> />
                                                    Bleeding
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterPretermLabour" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterPretermLabour", "").equals("X") ? "checked" : "") %> />
                                                    Preterm labour: signs/symptoms
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterPROM" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterPROM", "").equals("X") ? "checked" : "") %> />
                                                    PROM
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterLifestyle" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterLifestyle", "").equals("X") ? "checked" : "") %> />
                                                    Lifestyle and social risk assessment
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterGestationalDiab" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterGestationalDiab", "").equals("X") ? "checked" : "") %> />
                                                    Gestational diabetes screening
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterPrenatalClasses" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterPrenatalClasses", "").equals("X") ? "checked" : "") %> />
                                                    Prenatal classes
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterBirthOptions" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterBirthOptions", "").equals("X") ? "checked" : "") %> />
                                                    Birth options and practices that promote healthy birth
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterBirthPlan" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterBirthPlan", "").equals("X") ? "checked" : "") %> />
                                                    Birth plan: travel to other community for delivery (if applicable)
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterBreastfeeding" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterBreastfeeding", "").equals("X") ? "checked" : "") %> />
                                                    Breastfeeding and importance of immediate, uninterrupted
                                                    skin-to-skin care
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterPostpartumContra" <%=Encode.forHtmlAttribute(props.getProperty("c_2ndTrimesterPostpartumContra", "").equals("X") ? "checked" : "") %> />
                                                    Postpartum contraception
                                                </div>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>

                            <!-- 3rd Trimester -->
                            <tr>
                                <td>
                                    <div class="reference-header-2">
                                        3rd Trimester
                                    </div>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    <table width="100%" border="0" cellspacing="0" cellpadding="3"
                                           class="reference-table">
                                        <tr>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterFetalMovement" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterFetalMovement", "").equals("X") ? "checked" : "") %> />
                                                    Fetal movement
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterEmergencyContact" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterEmergencyContact", "").equals("X") ? "checked" : "") %> />
                                                    Emergency contact/on-call providers
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterECV" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterECV", "").equals("X") ? "checked" : "") %> />
                                                    ECV, breech delivery, elective Cesarean delivery (if applicable)
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterIndicationsInduction" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterIndicationsInduction", "").equals("X") ? "checked" : "") %> />
                                                    Indications for induction of labour
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterSignsLabour" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterSignsLabour", "").equals("X") ? "checked" : "") %> />
                                                    Signs/symptoms of labour and admission timing
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterBirthPlan" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterBirthPlan", "").equals("X") ? "checked" : "") %> />
                                                    Birth plan: labour support, pain management
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterPotentialInterventions" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterPotentialInterventions", "").equals("X") ? "checked" : "") %> />
                                                    Potential interventions, use of blood products
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterGenitalHerpes" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterGenitalHerpes", "").equals("X") ? "checked" : "") %> />
                                                    Genital herpes suppression
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterGBSScreening" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterGBSScreening", "").equals("X") ? "checked" : "") %> />
                                                    GBS screening/prophylaxis
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterCordBloodBanking" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterCordBloodBanking", "").equals("X") ? "checked" : "") %> />
                                                    Cord blood banking
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterErythromycin" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterErythromycin", "").equals("X") ? "checked" : "") %> />
                                                    Erythromycin/ophthalmia neonatorum prophylaxis/ treatment
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterVitaminK" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterVitaminK", "").equals("X") ? "checked" : "") %> />
                                                    Vitamin K prophylaxis
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterNewbornCare" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterNewbornCare", "").equals("X") ? "checked" : "") %> />
                                                    Newborn care, screening, circumcision, follow-up
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterBreastfeeding" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterBreastfeeding", "").equals("X") ? "checked" : "") %> />
                                                    Breastfeeding adjustment, skills, support
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterPostpartumCare" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterPostpartumCare", "").equals("X") ? "checked" : "") %> />
                                                    Postpartum care
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterPostpartumContraception" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterPostpartumContraception", "").equals("X") ? "checked" : "") %> />
                                                    Postpartum contraception
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterDischargePlanning" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterDischargePlanning", "").equals("X") ? "checked" : "") %> />
                                                    Discharge planning, car seat safety
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterInfantSleep" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterInfantSleep", "").equals("X") ? "checked" : "") %> />
                                                    Infant safe sleep
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterWorkPlan" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterWorkPlan", "").equals("X") ? "checked" : "") %> />
                                                    Work plan, maternity leave
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterEPDSScreening" <%=Encode.forHtmlAttribute(props.getProperty("c_3rdTrimesterEPDSScreening", "").equals("X") ? "checked" : "") %> />
                                                    EPDS screening
                                                </div>
                                            </td>
                                        </tr>
                                    </table>
                                </td>
                            </tr>
                        </table>
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
                <input type="checkbox" name="print_pr1" id="print_pr1" class="text ui-widget-content ui-corner-all"/>
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
                <input type="checkbox" name="print_pr5" id="print_pr5" checked="checked"
                       class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr5">Reference Page 2</label>
                <br/>
            </fieldset>
        </form>
    </div>
    </body>


</html>
