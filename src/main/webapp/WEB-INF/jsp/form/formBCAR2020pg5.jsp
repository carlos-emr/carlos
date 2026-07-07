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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="http://displaytag.sf.net" prefix="display" %>

<%
    String user = (String) session.getAttribute("user");
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
    String roleName2$ = (String) session.getAttribute("userrole") + "," + user;
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import=" io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*, java.util.Properties" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilMisc" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmBCAR2020Record" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
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
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>

        <title><fmt:message key="form.formBCAR2020pg5.title"/></title>

        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%=request.getContextPath() %>/share/calendar/lang/<fmt:message key='global.javascript.calendar'/>"></script>
        <script type="text/javascript" src="<%=request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/formBCAR2020Record.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
                type="text/javascript"></script>

        <script type="text/javascript" src="<%= request.getContextPath() %>/js/jquery.are-you-sure.js"></script>
        <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

        <script src="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.14.2.min.js"
                type="text/javascript"></script>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/bootstrap/5.3.8/css/bootstrap.min.css">

        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/jquery/jquery-ui.structure-1.14.2.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/jquery/jquery-ui.theme-1.14.2.min.css">
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
                $("form").find('input[value="<fmt:message key="global.save"/>"]').attr('disabled', true);
                $("form").find('input[value="<fmt:message key="global.saveExit"/>"]').attr('disabled', true);
                $("form").find('input[value="<fmt:message key="global.btnExit"/>"]').removeAttr('disabled');

                $('form').on('dirty.areYouSure', function () {

                    $(this).find('input[value="<fmt:message key="global.save"/>"]').removeAttr('disabled');
                    $(this).find('input[value="<fmt:message key="global.saveExit"/>"]').removeAttr('disabled');
                    $(this).find('input[value="<fmt:message key="global.btnExit"/>"]').attr('disabled', true);
                });

                $('form').on('clean.areYouSure', function () {

                    $(this).find('input[value="<fmt:message key="global.save"/>"]').attr('disabled', true);
                    $(this).find('input[value="<fmt:message key="global.saveExit"/>"]').attr('disabled', true);
                    $(this).find('input[value="<fmt:message key="global.btnExit"/>"]').removeAttr('disabled');
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
            <form action="${pageContext.request.contextPath}/form/BCAR2020" method="post">
                <input type="hidden" id="demographicNo" name="demographicNo" value="<%=demoNo%>"/>
                <input type="hidden" id="formId" name="formId" value="<%=formId%>"/>
                <input type="hidden" name="provider_no" value=<carlos:encode value='<%= providerNo %>' context="htmlUnquotedAttribute"/>/>
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
                            <input type="submit" class="btn btn-primary" value="<fmt:message key="global.save"/>" onclick="return onSave();"/>
                            <input type="submit" class="btn btn-secondary" value="<fmt:message key='global.saveExit'/>"
                                   onclick="return onSaveExit();"/>

                            <input type="submit" class="btn btn-danger" value="<fmt:message key="global.btnExit"/>" onclick="window.close();"/>
                            <input type="submit" class="btn btn-secondary" value="<fmt:message key="global.btnPrint"/>" onclick="return onPrint();"/>
                            <span style="display:none"><input id="printBtn" type="submit" value="PrintIt"/></span>

                        </td>
                        <td align="right" rowspan="2" width="5%" valign="top">
                            <b>
                                <fmt:message key="form.formBCAR2020pg3.label.edit"/>
                            </b>
                        </td>
                        <td align="right" width="37%">
                            <a href="javascript:void(0);" onclick="return onPageChange('1');"><fmt:message key="form.formBCAR2020pg3.link.part1"/></a>
                            |
                            <a href="javascript:void(0);" onclick="return onPageChange('2');"><fmt:message key="form.formBCAR2020pg3.link.part2page1"/></a>
                            |
                            <a href="javascript:void(0);" onclick="return onPageChange('3');"><fmt:message key="form.formBCAR2020pg3.link.part2page2"/></a>
                        </td>
                    </tr>
                    <tr>
                        <td align="right">
                            <a href="javascript:void(0);" onclick="return onPageChange('6');" class="small10"><fmt:message key="form.formBCAR2020pg3.link.attachments"/></a>
                            |
                            <a href="javascript:void(0);" onclick="return onPageChange('4');" class="small10"><fmt:message key="form.formBCAR2020pg3.link.referencePage1"/></a>
                            |
                            <b>
                                <a href="javascript:void(0);" onclick="return onPageChange('5');" class="small10"><fmt:message key="form.formBCAR2020pg3.link.referencePage2"/></a>
                            </b>
                        </td>
                    </tr>
                </table>

                <!-- Page Heading -->
                <table width="100%" border="0" cellspacing="0" cellpadding="1">
                    <tr>
                        <th align="left"><fmt:message key="form.formBCAR2020pg5.heading.record"/> <font size="-2"><fmt:message key="form.formBCAR2020pg3.heading.subtitle"/></font></th>
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
                                        <fmt:message key="form.formBCAR2020pg5.heading.discussionTopics"/>
                                    </div>
                                </td>
                            </tr>

                            <!-- 1st-3rd Trimester -->
                            <tr>
                                <td>
                                    <div class="reference-header-2">
                                        <fmt:message key="form.formBCAR2020pg5.heading.trimester"/>
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
                                                           name="c_1st3rdTrimesterNutrition" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterNutrition", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.nutritionFolicAcid"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterWeightGain" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterWeightGain", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.healthyWeightGain"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterPhysicalActivity" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterPhysicalActivity", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.physicalActivity"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterOccupation" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterOccupation", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.occupationalConcerns"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterPersonalSafety" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterPersonalSafety", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.personalSafety"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterSupportSystem" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterSupportSystem", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.supportSystem"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterMentalHealth" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterMentalHealth", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.mentalHealth"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterSubstanceUse" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterSubstanceUse", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.substanceUse"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterSexualActivity" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterSexualActivity", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.sexualActivity"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterImmunization" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterImmunization", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.immunization"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1st3rdTrimesterVBAC" <carlos:encode value='<%= props.getProperty("c_1st3rdTrimesterVBAC", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.vbacCounseling"/>
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
                                        <fmt:message key="form.formBCAR2020pg5.heading.firstTrimester"/>
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
                                                           name="c_1stTrimesterNausea" <carlos:encode value='<%= props.getProperty("c_1stTrimesterNausea", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.nauseaVomiting"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterSafety" <carlos:encode value='<%= props.getProperty("c_1stTrimesterSafety", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.safety"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterOralHealth" <carlos:encode value='<%= props.getProperty("c_1stTrimesterOralHealth", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.oralHealth"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterExposures" <carlos:encode value='<%= props.getProperty("c_1stTrimesterExposures", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.exposures"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterTravel" <carlos:encode value='<%= props.getProperty("c_1stTrimesterTravel", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.travel"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterGeneticScreening" <carlos:encode value='<%= props.getProperty("c_1stTrimesterGeneticScreening", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    Prenatal genetic screening
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterEarlyLoss" <carlos:encode value='<%= props.getProperty("c_1stTrimesterEarlyLoss", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.earlyPregnancyLoss"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterRoutine" <carlos:encode value='<%= props.getProperty("c_1stTrimesterRoutine", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    Routine prenatal care, emergency contact/ on-call providers
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterBreastfeeding" <carlos:encode value='<%= props.getProperty("c_1stTrimesterBreastfeeding", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    Breastfeeding: attitudes/beliefs
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterQualityEducation" <carlos:encode value='<%= props.getProperty("c_1stTrimesterQualityEducation", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    Quality educational resources
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_1stTrimesterPublicServices" <carlos:encode value='<%= props.getProperty("c_1stTrimesterPublicServices", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
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
                                                           name="c_2ndTrimesterBleeding" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterBleeding", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.bleeding"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterPretermLabour" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterPretermLabour", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.pretermLabourSignsSymptoms"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterPROM" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterPROM", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.prom"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterLifestyle" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterLifestyle", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.lifestyleAndSocialRiskAssessment"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterGestationalDiab" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterGestationalDiab", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.gestationalDiabetesScreening"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterPrenatalClasses" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterPrenatalClasses", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.prenatalClasses"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterBirthOptions" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterBirthOptions", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.birthOptionsAndPracticesThatPromoteHealthyBirth"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterBirthPlan" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterBirthPlan", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.birthPlanTravelOtherCommunity"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterBreastfeeding" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterBreastfeeding", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.breastfeedingAndImportanceOfImmediateUninterruptedSkinToSkinCare"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_2ndTrimesterPostpartumContra" <carlos:encode value='<%= props.getProperty("c_2ndTrimesterPostpartumContra", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.postpartumContraception"/>
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
                                                           name="c_3rdTrimesterFetalMovement" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterFetalMovement", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.fetalMovement"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterEmergencyContact" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterEmergencyContact", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.emergencyContactOnCallProviders"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterECV" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterECV", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.ecvBreechDeliveryElectiveCesareanDeliveryIfApplicable"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterIndicationsInduction" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterIndicationsInduction", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.indicationsForInductionOfLabour"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterSignsLabour" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterSignsLabour", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.signsSymptomsOfLabourAndAdmissionTiming"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterBirthPlan" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterBirthPlan", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.birthPlanLabourSupportPainManagement"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterPotentialInterventions" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterPotentialInterventions", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.potentialInterventionsBloodProducts"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterGenitalHerpes" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterGenitalHerpes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.genitalHerpesSuppression"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterGBSScreening" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterGBSScreening", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.gbsScreeningProphylaxis"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterCordBloodBanking" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterCordBloodBanking", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.cordBloodBanking"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterErythromycin" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterErythromycin", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.erythromycinOphthalmiaProphylaxisTreatment"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterVitaminK" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterVitaminK", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.vitaminKProphylaxis"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterNewbornCare" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterNewbornCare", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.newbornCareScreeningCircumcisionFollowup"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterBreastfeeding" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterBreastfeeding", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.breastfeedingAdjustmentSkillsSupport"/>
                                                </div>
                                            </td>
                                            <td width="25%">
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterPostpartumCare" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterPostpartumCare", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.postpartumCare"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterPostpartumContraception" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterPostpartumContraception", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.postpartumContraception"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterDischargePlanning" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterDischargePlanning", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.dischargePlanningCarSeatSafety"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterInfantSleep" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterInfantSleep", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.infantSafeSleep"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterWorkPlan" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterWorkPlan", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg5.label.workPlanMaternityLeave"/>
                                                </div>
                                                <div class="reference-pad">
                                                    <input type="checkbox"
                                                           name="c_3rdTrimesterEPDSScreening" <carlos:encode value='<%= props.getProperty("c_3rdTrimesterEPDSScreening", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
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
    <div id="print-dialog" title="<fmt:message key='form.formBCAR2020pg3.title.print'/>">
        <p class="validateTips"></p>
        <p><fmt:message key="form.formBCAR2020pg3.msg.saveBeforePrint"/></p>
        <div>
            <input type="checkbox" onclick="return printSelectAll();" id="print_all"
                   class="text ui-widget-content ui-corner-all"/>
            <label for="print_all" class="small10"><fmt:message key="form.formBCAR2020pg3.label.selectAll"/></label>
        </div>
        <form>
            <fieldset>
                <input type="checkbox" name="print_pr1" id="print_pr1" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr1"><fmt:message key="form.formBCAR2020pg3.link.part1"/></label>
                <br/>
                <input type="checkbox" name="print_pr2" id="print_pr2" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr2"><fmt:message key="form.formBCAR2020pg3.link.part2page1"/></label>
                <br/>
                <input type="checkbox" name="print_pr3" id="print_pr3" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr3"><fmt:message key="form.formBCAR2020pg3.link.part2page2"/></label>
                <br/>
                <input type="checkbox" name="print_att" id="print_att" class="text ui-widget-content ui-corner-all"/>
                <label for="print_att"><fmt:message key="form.formBCAR2020pg3.label.attachmentsAdditionalInfo"/></label>
                <br/>
                <input type="checkbox" name="print_pr4" id="print_pr4" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr4"><fmt:message key="form.formBCAR2020pg3.link.referencePage1"/></label>
                <br/>
                <input type="checkbox" name="print_pr5" id="print_pr5" checked="checked"
                       class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr5"><fmt:message key="form.formBCAR2020pg3.link.referencePage2"/></label>
                <br/>
            </fieldset>
        </form>
    </div>
    </body>


</html>
