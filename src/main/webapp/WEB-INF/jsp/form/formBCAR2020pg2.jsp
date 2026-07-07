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
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmBCAR2020Record" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>



<%
    String formClass = "BCAR2020";
    Integer pageNo = 2;
    String formLink = "formBCAR2020pg2.jsp";

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
        <title><fmt:message key="form.formBCAR2020pg2.title"/></title>

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

        <link rel="stylesheet" type="text/css" media="all"
              href="<%=request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
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
                init(2);

                // Set values in drop downs
                $("select[name='s_investigationsABO']").val('<carlos:encode value='<%= props.getProperty("s_investigationsABO", "UN") %>' context="javaScriptBlock"/>');
                $("select[name='s_investigationsRhFactor']").val('<carlos:encode value='<%= props.getProperty("s_investigationsRhFactor", "UN") %>' context="javaScriptBlock"/>');

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
                            <b>
                                <a href="javascript:void(0);" onclick="return onPageChange('2');"><fmt:message key="form.formBCAR2020pg3.link.part2page1"/></a>
                            </b>
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
                            <a href="javascript:void(0);" onclick="return onPageChange('5');" class="small10"><fmt:message key="form.formBCAR2020pg3.link.referencePage2"/></a>
                        </td>
                    </tr>
                </table>

                <!-- Page Heading -->
                <table width="100%">
                    <tr>
                        <th><fmt:message key="form.formBCAR2020pg2.heading.record"/> <font size="-2"><fmt:message key="form.formBCAR2020pg3.heading.subtitle"/></font>
                        </th>
                    </tr>
                </table>

                <table width="100%" class="small9">
                    <tr>
                        <td width="60%" valign="top">
                            <!-- Planned place of birth -->
                            <table width="100%" class="regular-border">
                                <tr>
                                    <td width="40%" style="border-right: 1px solid black;">
                                        <span class="title">12.</span> <fmt:message key="form.formBCAR2020pg2.label.plannedBirth20"/>
                                    </td>
                                    <td width="40%" style="border-left: 1px solid black;border-right: 1px solid black;">
                                        <fmt:message key="form.formBCAR2020pg2.label.plannedBirth36"/>
                                    </td>
                                    <td width="20%" style="border-left: 1px solid black;border-right: 1px solid black;">
                                        <fmt:message key="form.formBCAR2020pg2.label.referralHospital"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="border-right: 1px solid black;">
                                        <input type="text" name="t_plannedBirthAt20Wks" size="15" maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_plannedBirthAt20Wks", "")) %>"/>
                                        <input type="checkbox"
                                               name="c_plannedBirthAt20WksCopyHospital" <carlos:encode value='<%= props.getProperty("c_plannedBirthAt20WksCopyHospital", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.copyToHospital"/>
                                    </td>
                                    <td style="border-left: 1px solid black;border-right: 1px solid black;">
                                        <input type="text" name="t_plannedBirthAt36Wks" size="15" maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_plannedBirthAt36Wks", "")) %>"/>
                                        <input type="checkbox"
                                               name="c_plannedBirthAt36WksCopyHospital" <carlos:encode value='<%= props.getProperty("c_plannedBirthAt36WksCopyHospital", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.copyToHospital"/>
                                    </td>
                                    <td style="border-left: 1px solid black;border-right: 1px solid black;">
                                        <input type="text" name="t_plannedBirthReferralHospital" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_plannedBirthReferralHospital", "")) == null || UtilMisc.htmlEscape(props.getProperty("t_plannedBirthReferralHospital", "")) == "" ? CarlosProperties.getInstance().getProperty("BCAR_hospital") : ""  %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3"
                                        style="border-top: 1px solid black; border-bottom: 1px solid black;">
                                        <span class="title"><fmt:message key="form.formBCAR2020pg2.label.confirmedEDD"/></span><span
                                            class="sub-text">(dd/mm/yyyy)</span>
                                        <input type="text" id="d_confirmedEDD" name="d_confirmedEDD"
                                               title="Section 12 - Confirmed EDD" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_confirmedEDD", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_confirmedEDD_cal">
                                        <fmt:message key="form.formBCAR2020pg2.label.by"/>
                                        <input type="checkbox"
                                               name="c_confirmedEDDUS" <carlos:encode value='<%= props.getProperty("c_confirmedEDDUS", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.us"/>
                                        <input type="checkbox"
                                               name="c_confirmedEDDIVF" <carlos:encode value='<%= props.getProperty("c_confirmedEDDIVF", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.ivf"/>
                                    </td>
                                </tr>
                            </table>

                            <!-- Investigations -->
                            <table width="100%" class="ar-table-border">
                                <tr>
                                    <td colspan="2" width="22%" class="alignTop">
                                        <span class="title">13. Investigations</span>
                                    </td>
                                    <td width="32%">
                                        <div class="div-left">
                                            <fmt:message key="form.formBCAR2020pg2.label.date"/>
                                            <span class="sub-text">(dd/mm/yyyy)</span>
                                        </div>
                                        <div class="div-right">
                                            <fmt:message key="form.formBCAR2020pg2.label.antibodyTitre"/>
                                        </div>
                                    </td>
                                    <td width="28%" class="alignTop">
                                        <fmt:message key="form.formBCAR2020pg2.label.rhigDate"/>
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                    </td>
                                    <td width="18%" class="alignTop">
                                        <fmt:message key="form.formBCAR2020pg2.label.hemoglobin"/>
                                        <span class="sub-text">(g/L)</span>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="10%" class="alignTop">
                                        <div class="div-center">
                                            <fmt:message key="form.formBCAR2020pg2.label.abo"/>
                                        </div>
                                    </td>
                                    <td width="12%" class="alignTop">
                                        <div class="div-center">
                                            <fmt:message key="form.formBCAR2020pg2.label.rhFactor"/>
                                        </div>
                                    </td>
                                    <td>
                                        1.
                                        <input type="text" id="d_investigationsAntibody1"
                                               name="d_investigationsAntibody1" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_investigationsAntibody1", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_investigationsAntibody1_cal">
                                        <input type="text" name="t_investigationsAntibody1" size="8" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsAntibody1", "")) %>"/>
                                    </td>
                                    <td>
                                        1.
                                        <input type="text" id="d_investigationsRhIgDate1"
                                               name="d_investigationsRhIgDate1" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_investigationsRhIgDate1", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_investigationsRhIgDate1_cal">
                                    </td>
                                    <td>
                                        T1
                                        <input type="text" name="t_investigationsHemoglobinT1" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsHemoglobinT1", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <select name="s_investigationsABO" style="width: 100%">
                                            <option value="A">A</option>
                                            <option value="B">B</option>
                                            <option value="AB">AB</option>
                                            <option value="O">O</option>
                                            <option value="UN"><fmt:message key="global.unknown"/></option>
                                        </select>
                                    </td>
                                    <td>
                                        <select name="s_investigationsRhFactor" style="width: 100%">
                                            <option value="POS"><fmt:message key="form.formBCAR2020pg2.label.pos"/></option>
                                            <option value="NEG"><fmt:message key="form.formBCAR2020pg2.label.neg"/></option>
                                            <option value="UN"><fmt:message key="global.unknown"/></option>
                                        </select>
                                    </td>
                                    <td>
                                        2.
                                        <input type="text" id="d_investigationsAntibody2"
                                               name="d_investigationsAntibody2" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_investigationsAntibody2", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_investigationsAntibody2_cal">
                                        <input type="text" name="t_investigationsAntibody2" size="8" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsAntibody2", "")) %>"/>
                                    </td>
                                    <td>
                                        2.
                                        <input type="text" id="d_investigationsRhIgDate2"
                                               name="d_investigationsRhIgDate2" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_investigationsRhIgDate2", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_investigationsRhIgDate2_cal">
                                    </td>
                                    <td>
                                        T3
                                        <input type="text" name="t_investigationsHemoglobinT3" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsHemoglobinT3", "")) %>"/>
                                    </td>
                                </tr>
                            </table>

                            <table width="100%" class="outside-border-alt">
                                <tr>
                                    <td width="17%">
                                        <div class="div-center">
                                            <span class="title"><fmt:message key="form.formBCAR2020pg2.label.test"/></span>
                                        </div>
                                    </td>
                                    <td colspan="2" width="20%">
                                        <div class="div-center">
                                            <span class="title"><fmt:message key="form.formBCAR2020pg2.label.results"/></span>
                                        </div>
                                    </td>
                                    <td colspan="2" width="63%">
                                        <div class="div-center">
                                            <span class="title"><fmt:message key="form.formBCAR2020pg2.label.resultsFollowupComments"/></span>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.rubella"/>
                                    </td>
                                    <td width="10%">
                                        <input type="checkbox"
                                               name="c_investigationsRubellaImm" <carlos:encode value='<%= props.getProperty("c_investigationsRubellaImm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.yes"/>
                                    </td>
                                    <td width="10%">
                                        <input type="checkbox"
                                               name="c_investigationsRubellaNonImm" <carlos:encode value='<%= props.getProperty("c_investigationsRubellaNonImm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.no"/>
                                    </td>
                                    <td width="32%">
                                        <div class="divFlex">
                                            <fmt:message key="form.formBCAR2020pg2.label.value"/>
                                            <span class="sub-text">
												(IU/mL)
											</span>
                                            <input type="text" name="t_investigationsRubellaValue" size="10"
                                                   maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsRubellaValue", "")) %>"/>
                                        </div>
                                    </td>
                                    <td width="31%">
                                        <input type="checkbox"
                                               name="c_investigationsRubellaPPVaccine" <carlos:encode value='<%= props.getProperty("c_investigationsRubellaPPVaccine", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.postpartumVaccineRequired"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.hiv"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHIVNeg" <carlos:encode value='<%= props.getProperty("c_investigationsHIVNeg", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.no"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHIVPos" <carlos:encode value='<%= props.getProperty("c_investigationsHIVPos", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.yes"/>
                                    </td>
                                    <td>
                                        <div class="divFlex">
                                            <input type="text" name="t_investigationsHIVComment" class="text-style"
                                                   size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsHIVComment", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHIVT3Repeat" <carlos:encode value='<%= props.getProperty("c_investigationsHIVT3Repeat", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.t3RepeatHighRisk"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.syphilis"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsSyphilisNR" <carlos:encode value='<%= props.getProperty("c_investigationsSyphilisNR", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        N/R
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsSyphilisR" <carlos:encode value='<%= props.getProperty("c_investigationsSyphilisR", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        R
                                    </td>
                                    <td colspan="2">
                                        <div class="divFlex">
                                            <input type="text" name="t_investigationsSyphilisComment" class="text-style"
                                                   size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsSyphilisComment", "")) %>"/>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.hbsag"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgNR" <carlos:encode value='<%= props.getProperty("c_investigationsHBsAgNR", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        N/R
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgR" <carlos:encode value='<%= props.getProperty("c_investigationsHBsAgR", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        R
                                    </td>
                                    <td>
                                        <div class="divFlex">
                                            HBV DNA
                                            <span class="sub-text">(IU/mL)</span>
                                            <input type="text" name="t_investigationsHBsAgHBV" class="text-style"
                                                   size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsHBsAgHBV", "")) %>"/>
                                        </div>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgPartner" <carlos:encode value='<%= props.getProperty("c_investigationsHBsAgPartner", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.partnerHouseholdContact"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgAntiViral" <carlos:encode value='<%= props.getProperty("c_investigationsHBsAgAntiViral", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.antiviralTherapyRequired"/>
                                        <br/>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgNewbornVaccine" <carlos:encode value='<%= props.getProperty("c_investigationsHBsAgNewbornVaccine", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.newbornVaccineRequired"/>
                                        <br/>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgNewbornHBIg" <carlos:encode value='<%= props.getProperty("c_investigationsHBsAgNewbornHBIg", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.newbornHbIgRequired"/>
                                        <br/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.gonorrhea"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGonorrheaNeg" <carlos:encode value='<%= props.getProperty("c_investigationsGonorrheaNeg", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.neg"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGonorrheaPos" <carlos:encode value='<%= props.getProperty("c_investigationsGonorrheaPos", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.pos"/>
                                    </td>
                                    <td>
                                        <div class="divFlex">
                                            <input type="text" name="t_investigationsGonorrheaComment"
                                                   class="text-style" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsGonorrheaComment", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGonorrheaT3Repeat" <carlos:encode value='<%= props.getProperty("c_investigationsGonorrheaT3Repeat", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.t3RepeatPos"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.chlamydia"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsChlamydiaNeg" <carlos:encode value='<%= props.getProperty("c_investigationsChlamydiaNeg", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.neg"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsChlamydiaPos" <carlos:encode value='<%= props.getProperty("c_investigationsChlamydiaPos", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.pos"/>
                                    </td>
                                    <td>
                                        <div class="divFlex">
                                            <input type="text" name="t_investigationsChlamydiaComment"
                                                   class="text-style" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsChlamydiaComment", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsChlamydiaT3Repeat" <carlos:encode value='<%= props.getProperty("c_investigationsChlamydiaT3Repeat", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.t3RepeatPos"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.urineCS"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsUrineNeg" <carlos:encode value='<%= props.getProperty("c_investigationsUrineNeg", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.neg"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsUrinePos" <carlos:encode value='<%= props.getProperty("c_investigationsUrinePos", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.pos"/>
                                    </td>
                                    <td>
                                        <div class="divFlex">
                                            <fmt:message key="form.formBCAR2020pg2.label.culture"/>
                                            <input type="text" name="t_investigationsUrineCulture" class="text-style"
                                                   size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsUrineCulture", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <div class="divFlex">
                                            <input type="text" name="t_investigationsUrineComment" class="text-style"
                                                   size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsUrineComment", "")) %>"/>
                                        </div>
                                    </td>
                                </tr>
                                <tr class="noBorderTopBottom">
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.gdm"/>
                                        <span class="sub-text">(@24-28 wks)</span>
                                    </td>
                                    <td>
                                    </td>
                                    <td>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMTestDeclined" <carlos:encode value='<%= props.getProperty("c_investigationsGDMTestDeclined", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.gdmTestDeclined"/>
                                    </td>
                                    <td rowspan="2" valign="top">
                                        <input type="checkbox"
                                               name="c_investigationsGDMDietControlled" <carlos:encode value='<%= props.getProperty("c_investigationsGDMDietControlled", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.dietControlled"/>
                                        <br/>
                                        <input type="checkbox"
                                               name="c_investigationsGDMInsulinReqd" <carlos:encode value='<%= props.getProperty("c_investigationsGDMInsulinReqd", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.insulinRequired"/>
                                    </td>
                                </tr>
                                <tr class="noBorderTopBottom">
                                    <td>
                                        <span style="margin-left: 2em;"><fmt:message key="form.formBCAR2020pg2.label.gct"/></span>
                                        <span class="sub-text">(50 g)</span>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMGCTNeg" <carlos:encode value='<%= props.getProperty("c_investigationsGDMGCTNeg", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.neg"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMGCTPos" <carlos:encode value='<%= props.getProperty("c_investigationsGDMGCTPos", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.pos"/>
                                    </td>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.value"/>
                                        <span class="sub-text">(mmol/L)</span>
                                        @1hr
                                        <input type="text" name="t_investigationsGDMGCT1hr" size="6" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsGDMGCT1hr", "")) %>"/>
                                    </td>
                                </tr>
                                <tr class="noBorderTopBottom">
                                    <td>
                                        <span style="margin-left: 2em;"><fmt:message key="form.formBCAR2020pg2.label.gtt"/></span>
                                        <span class="sub-text">(75 g)</span>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMGTTNeg" <carlos:encode value='<%= props.getProperty("c_investigationsGDMGTTNeg", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.neg"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMGTTPos" <carlos:encode value='<%= props.getProperty("c_investigationsGDMGTTPos", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.pos"/>
                                    </td>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.value"/>
                                        <span class="sub-text">(mmol/L)</span>
                                        @1hr
                                        <input type="text" name="t_investigationsGDMGTT1hr" size="6" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsGDMGTT1hr", "")) %>"/>
                                    </td>
                                    <td>
                                        @2hr
                                        <input type="text" name="t_investigationsGDMGTT2hr" size="6" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsGDMGTT2hr", "")) %>"/>
                                        @3hr
                                        <input type="text" name="t_investigationsGDMGTT3hr" size="6" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsGDMGTT3hr", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        GBS
                                        <span class="sub-text">(@35-37 wks)</span>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGBSNeg" <carlos:encode value='<%= props.getProperty("c_investigationsGBSNeg", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.neg"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGBSPos" <carlos:encode value='<%= props.getProperty("c_investigationsGBSPos", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.pos"/>
                                    </td>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.date"/>
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                        <input type="text" id="d_investigationsGBSDate" name="d_investigationsGBSDate"
                                               size="8" maxlength="20"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_investigationsGBSDate", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_investigationsGBSDate_cal">
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGBSCopyHospital" <carlos:encode value='<%= props.getProperty("c_investigationsGBSCopyHospital", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.copyToHospital"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="5">
                                        <fmt:message key="form.formBCAR2020pg2.label.otherExample"/>
                                        <div class="divFlex">
                                            <input type="text" name="t_investigationsOther" size="60" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsOther", "")) %>"/>
                                        </div>
                                    </td>
                                </tr>
                            </table>

                            <!-- Prenatal Genetic Investigations -->
                            <table width="100%" class="outside-border">
                                <tr>
                                    <td colspan="2">
                                        <div class="title"><fmt:message key="form.formBCAR2020pg2.label.prenatalGeneticInvestigations"/></div>
                                    </td>
                                    <td colspan="2">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticDeclined" <carlos:encode value='<%= props.getProperty("c_prenatalGeneticDeclined", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.declined"/>
                                    </td>
                                    <td style="border-left: 1px solid black;">
                                        <div class="title"><fmt:message key="form.formBCAR2020pg2.label.results"/></div>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="18%">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticSIPS" <carlos:encode value='<%= props.getProperty("c_prenatalGeneticSIPS", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        SIPS
                                    </td>
                                    <td width="20%">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticIPS" <carlos:encode value='<%= props.getProperty("c_prenatalGeneticIPS", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        IPS
                                    </td>
                                    <td width="20">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticQuad" <carlos:encode value='<%= props.getProperty("c_prenatalGeneticQuad", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        Quad
                                    </td>
                                    <td width="10%">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticCVS" <carlos:encode value='<%= props.getProperty("c_prenatalGeneticCVS", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        CVS
                                    </td>
                                    <td width="32%" style="border-left: 1px solid black;" rowspan="2">
                                        <div class="divFlex">
                                            <textarea id="t_prenatalGeneticResults" name="t_prenatalGeneticResults"
                                                      style="width: 100%; height:40px;" size="30"
                                                      maxlength="200"><%= UtilMisc.htmlEscape(props.getProperty("t_prenatalGeneticResults", "")) %></textarea>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <input type="checkbox"
                                               name="c_prenatalGeneticNIPTMSP" <carlos:encode value='<%= props.getProperty("c_prenatalGeneticNIPTMSP", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.niptMsp"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_prenatalGeneticNIPTSelf" <carlos:encode value='<%= props.getProperty("c_prenatalGeneticNIPTSelf", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.niptSelfPay"/>
                                    </td>
                                    <td>
                                        <div class="divFlex">
                                            <input type="checkbox"
                                                   name="c_prenatalGeneticOther" <carlos:encode value='<%= props.getProperty("c_prenatalGeneticOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                            <fmt:message key="form.formBCAR2020pg2.label.other"/>
                                            <input type="text" name="t_prenatalGeneticOtherDetails" size="10"
                                                   maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_prenatalGeneticOtherDetails", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_prenatalGeneticAmnio" <carlos:encode value='<%= props.getProperty("c_prenatalGeneticAmnio", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.amnio"/>
                                    </td>
                                </tr>
                            </table>

                            <!-- Edinburgh Perinatal/Postnatal Depression Scale* -->
                            <table width="100%" class="outside-border">
                                <tr>
                                    <td width="61%">
                                        <div class="title">14. Edinburgh Perinatal/Postnatal Depression Scale *</div>
                                    </td>
                                    <td width="39%">
                                        <input type="checkbox"
                                               name="c_edinburgDeclined" <carlos:encode value='<%= props.getProperty("c_edinburgDeclined", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.label.declined"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="2">
                                        <fmt:message key="form.formBCAR2020pg2.label.date"/>
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                        <input type="text" id="d_edinburgDate" name="d_edinburgDate"
                                               title="<fmt:message key='form.formBCAR2020pg2.title.edinburghDate'/>" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_edinburgDate", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_edinburgDate_cal">
                                        <fmt:message key="form.formBCAR2020pg2.label.ga"/>
                                        <span class="sub-text">(wks/days)</span>
                                        <input type="text" name="t_edinburgGA" title="<fmt:message key='form.formBCAR2020pg2.title.edinburghGA'/>"
                                               class="calcField" size="8" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_edinburgGA", "")) %>"
                                               onDblClick="getGAByFieldDate('t_edinburgGA', 'd_confirmedEDD', 'd_edinburgDate')"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="2">
                                        <div class="flex-row flex-justify">
                                            <div>
                                                <fmt:message key="form.formBCAR2020pg2.label.totalScore"/>
                                                <input type="text" name="t_edinburgTotalScore" size="6" maxlength="150"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_edinburgTotalScore", "")) %>"/>
                                            </div>
                                            <div>
                                                <fmt:message key="form.formBCAR2020pg2.label.anxietySubscore"/>
                                                <span class="sub-text">(questions 3-5)</span>
                                                <input type="text" name="t_edinburgAnxietySubscore" size="6"
                                                       maxlength="150"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_edinburgAnxietySubscore", "")) %>"/>
                                            </div>
                                            <div>
                                                <fmt:message key="form.formBCAR2020pg2.label.selfHarmSubscore"/>
                                                <span class="sub-text">(question 10)</span>
                                                <input type="text" name="t_edinburgSelfharmSubscore" size="6"
                                                       maxlength="150"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_edinburgSelfharmSubscore", "")) %>"/>
                                            </div>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="2">
                                        <div class="divFlex">
                                                <fmt:message key="form.formBCAR2020pg2.label.followUp"/>
                                            <input type="text" name="t_edinburgFollowup" size="20" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_edinburgFollowup", "")) %>"/>
                                        </div>
                                    </td>
                                </tr>
                            </table>

                        </td>
                        <td width="40%" valign="top">
                            <!-- Addressograph/Label -->
                            <table width="100%" class="no-border">
                                <tr>
                                    <td valign="top" width="50%">
                                        <fmt:message key="form.formBCAR2020pg2.label.surname"/><br/>
                                        <input type="text" name="t_patientSurname" class="text-style" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientSurname", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td valign="top" width="50%" colspan="2">
                                        <fmt:message key="form.formBCAR2020pg2.label.givenName"/><br/>
                                        <input type="text" name="t_patientGivenName" class="text-style" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientGivenName", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3">
                                        <fmt:message key="form.formBCAR2020pg2.label.addressNumberStreetName"/><br/>
                                        <input type="text" name="t_patientAddress" class="text-style" size="60"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientAddress", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="50%">
                                        <fmt:message key="form.formBCAR2020pg2.label.city"/><br/>
                                        <input type="text" name="t_patientCity" class="text-style" size="60"
                                               maxlength="50"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientCity", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg2.label.province"/><br/>
                                        <input type="text" name="t_patientProvince" class="text-style" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientProvince", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg2.label.postalCode"/><br/>
                                        <input type="text" name="t_patientPostal" class="text-style" size="60"
                                               maxlength="10"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientPostal", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3">
                                        <table width="100%">
                                            <tr>
                                                <td width="33%">
                                                    <fmt:message key="form.formBCAR2020pg2.label.homePhoneNumber"/>
                                                </td>
                                                <td width="33%">
                                                    <fmt:message key="form.formBCAR2020pg2.label.workPhoneNumber"/>
                                                </td>
                                                <td width="34%">
                                                    <fmt:message key="form.formBCAR2020pg2.label.cellPhoneNumber"/>
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
                                        <fmt:message key="form.formBCAR2020pg1.label.personalHealthNumber"/><br/>
                                        <input type="text" name="t_patientHIN" class="text-style" size="60"
                                               maxlength="10"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientHIN", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                </tr>
                            </table>

                            <br/>
                            <br/>

                            <!-- Ultrasounds & Other Imaging Investigations -->
                            <table width="100%" class="regular-border">
                                <tr>
                                    <td colspan="3">
                                        <span class="title">15.</span> <span class="title"><fmt:message key="form.formBCAR2020pg2.heading.ultrasoundsOtherImagingInvestigations"/></span>
                                    </td>
                                </tr>
                                <tr>
                                    <th width="27%" class="div-center">
                                        <fmt:message key="form.formBCAR2020pg2.label.date"/></br>
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                    </th>
                                    <th width="10%" class="div-center">
                                        <fmt:message key="form.formBCAR2020pg2.label.ga"/></br>
                                        <span class="sub-text">(wks/days)</span>
                                    </th>
                                    <th width="63%" class="div-center">
                                        <fmt:message key="form.formBCAR2020pg2.label.comments"/>
                                    </th>
                                </tr>
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" id="d_imagingDate1" name="d_imagingDate1"
                                                   title="<fmt:message key='form.formBCAR2020pg2.title.usImagingDate'/>" size="9" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_imagingDate1", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_imagingDate1_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingGA1" title="<fmt:message key='form.formBCAR2020pg2.title.usImagingGA'/>"
                                               class="calcField" size="8" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_imagingGA1", "")) %>"
                                               onDblClick="getGAByFieldDate('t_imagingGA1', 'd_confirmedEDD', 'd_imagingDate1')"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingComments1" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_imagingComments1", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" id="d_imagingDate2" name="d_imagingDate2"
                                                   title="<fmt:message key='form.formBCAR2020pg2.title.usImagingDate'/>" size="9" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_imagingDate2", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_imagingDate2_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingGA2" title="<fmt:message key='form.formBCAR2020pg2.title.usImagingGA'/>"
                                               class="calcField" size="8" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_imagingGA2", "")) %>"
                                               onDblClick="getGAByFieldDate('t_imagingGA2', 'd_confirmedEDD', 'd_imagingDate2')"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingComments2" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_imagingComments2", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" id="d_imagingDate3" name="d_imagingDate3"
                                                   title="<fmt:message key='form.formBCAR2020pg2.title.usImagingDate'/>" size="9" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_imagingDate3", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_imagingDate3_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingGA3" title="<fmt:message key='form.formBCAR2020pg2.title.usImagingGA'/>"
                                               class="calcField" size="8" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_imagingGA3", "")) %>"
                                               onDblClick="getGAByFieldDate('t_imagingGA3', 'd_confirmedEDD', 'd_imagingDate3')"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingComments3" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_imagingComments3", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" id="d_imagingDate4" name="d_imagingDate4"
                                                   title="<fmt:message key='form.formBCAR2020pg2.title.usImagingDate'/>" size="9" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_imagingDate4", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_imagingDate4_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingGA4" title="<fmt:message key='form.formBCAR2020pg2.title.usImagingGA'/>"
                                               class="calcField" size="8" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_imagingGA4", "")) %>"
                                               onDblClick="getGAByFieldDate('t_imagingGA4', 'd_confirmedEDD', 'd_imagingDate4')"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingComments4" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_imagingComments4", "")) %>"/>
                                    </td>
                                </tr>
                            </table>

                            <!-- Perinatal Considerations & Referrals -->
                            <table width="100%" class="outside-border">
                                <tr>
                                    <td colspan="2">
                                        <span class="title">16.</span> <span class="title"><fmt:message key="form.formBCAR2020pg3.section.perinatalConsiderationsAndReferrals"/></span>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="40%">
                                        <fmt:message key="form.formBCAR2020pg2.label.pregnancyType"/>
                                    </td>
                                    <td width="60%">
                                        <input type="checkbox"
                                               name="c_considerationsPregnancySingleton" <carlos:encode value='<%= props.getProperty("c_considerationsPregnancySingleton", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.option.singleton"/>
                                        <input type="checkbox"
                                               name="c_considerationsPregnancyTwin" <carlos:encode value='<%= props.getProperty("c_considerationsPregnancyTwin", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.option.twin"/>
                                        <input type="checkbox"
                                               name="c_considerationsPregnancyMultiple" <carlos:encode value='<%= props.getProperty("c_considerationsPregnancyMultiple", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.option.multiple"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.vbacEligibleAt36Weeks"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_considerationsVBACEligNo" <carlos:encode value='<%= props.getProperty("c_considerationsVBACEligNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="global.no"/>
                                        <input type="checkbox"
                                               name="c_considerationsVBACEligYes" <carlos:encode value='<%= props.getProperty("c_considerationsVBACEligYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="global.yes"/>
                                        <input type="checkbox"
                                               name="c_considerationsVBACEligNA" <carlos:encode value='<%= props.getProperty("c_considerationsVBACEligNA", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.option.na"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.vbacPlannedAt36Weeks"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_considerationsVBACPlanNo" <carlos:encode value='<%= props.getProperty("c_considerationsVBACPlanNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="global.no"/>
                                        <input type="checkbox"
                                               name="c_considerationsVBACPlanYes" <carlos:encode value='<%= props.getProperty("c_considerationsVBACPlanYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="global.yes"/>
                                        <input type="checkbox"
                                               name="c_considerationsVBACPlanNA" <carlos:encode value='<%= props.getProperty("c_considerationsVBACPlanNA", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.option.na"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.planToBreastfeed"/>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_considerationsBreastfeedNo" <carlos:encode value='<%= props.getProperty("c_considerationsBreastfeedNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="global.no"/>
                                        <input type="checkbox"
                                               name="c_considerationsBreastfeedYes" <carlos:encode value='<%= props.getProperty("c_considerationsBreastfeedYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="global.yes"/>
                                        <input type="checkbox"
                                               name="c_considerationsBreastfeedUN" <carlos:encode value='<%= props.getProperty("c_considerationsBreastfeedUN", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                        <fmt:message key="form.formBCAR2020pg2.option.undecided"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.lifestyleSubstanceUse"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsLifestyle" class="text-style" size="60"
                                               maxlength="39"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsLifestyle", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.pregnancy"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsPregnancy" class="text-style" size="60"
                                               maxlength="48"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsPregnancy", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.labourBirth"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsLabour" class="text-style" size="60"
                                               maxlength="45"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsLabour", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.breastfeeding"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsBreastfeeding" class="text-style"
                                               size="60" maxlength="48"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsBreastfeeding", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.postpartum"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsPostpartum" class="text-style"
                                               size="60" maxlength="48"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsPostpartum", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.contraceptionPlan"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsContraception" class="text-style"
                                               size="60" maxlength="41"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsContraception", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <fmt:message key="form.formBCAR2020pg2.label.newborn"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsNewborn" class="text-style" size="60"
                                               maxlength="48"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsNewborn", "")) %>"/>
                                    </td>
                                </tr>
                            </table>

                        </td>
                    </tr>
                </table>

                <!-- 17 Prenatal Visits Summary -->
                <table width="100%" border="1" class="prenatalVisits">
                    <tr>
                        <th width="11%">
                            <span class="title">17.</span>
                            <fmt:message key="form.formBCAR2020pg2.label.visitDate"/>
                            <br/>
                            <span class="sub-text">(dd/mm/yyyy)</span>
                        </th>
                        <th width="4%">
                            <fmt:message key="form.formBCAR2020pg2.label.visitGA"/>
                            <br/>
                            <span class="sub-text">(wks/days)</span>
                        </th>
                        <th width="6%">
                            <fmt:message key="form.formBCAR2020pg2.label.bp"/>
                        </th>
                        <th width="7%">
                            <fmt:message key="form.formBCAR2020pg2.label.urine"/>
                            <br/>
                            <span class="sub-text">(if indicated)</span>
                        </th>
                        <th width="4%">
                            <fmt:message key="form.formBCAR2020pg2.label.wt"/>
                            <br/>
                            <span class="sub-text">(kg)</span>
                        </th>
                        <th width="4%">
                            <fmt:message key="form.formBCAR2020pg2.label.fundus"/>
                            <br/>
                            <span class="sub-text">(cm)</span>
                        </th>
                        <th width="5%">
                            <fmt:message key="form.formBCAR2020pg2.label.fhr"/>
                            <br/>
                            <span class="sub-text">(per min)</span>
                        </th>
                        <th width="4%">
                            <fmt:message key="form.formBCAR2020pg2.label.fm"/>
                        </th>
                        <th width="7%">
                            <fmt:message key="form.formBCAR2020pg2.label.presentationPosition"/>
                            <br/>
                            position
                        </th>
                        <th width="40%">
                            <fmt:message key="form.formBCAR2020pg2.label.visitComments"/>
                        </th>
                        <th width="5%">
                            Next
                            <br/>
                            visit
                        </th>
                        <th width="3%">
                            <fmt:message key="form.formBCAR2020pg2.label.initials"/>
                        </th>
                    </tr>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "1")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "2")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "3")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "4")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "5")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "6")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "7")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "8")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "9")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "10")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "11")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "12")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "13")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "14")%>
                    <tr>
                        <td colspan="12" class="div-center">
                            <i><fmt:message key="form.formBCAR2020pg2.msg.additionalVisitsOnNextPage"/></i>
                        </td>
                    </tr>

                </table>
                <table width="100%" class="outside-border">
                    <tr>
                        <td colspan="5">
                            <span class="title"><fmt:message key="form.formBCAR2020pg2.section.signOffs"/></span>
                        </td>
                    </tr>
                    <tr>
                        <td width="40%">
                            <div class="divFlex">
                                1.
                                <span class="sub-text"><fmt:message key="form.formBCAR2020pg2.label.name"/></span>
                                <input type="text" name="t_signOffsName1" class="text-style" size="30" maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsName1", "")) %>"/>
                            </div>
                        </td>
                        <td width="40%">
                            <div class="divFlex">
                                <span class="sub-text"><fmt:message key="form.formBCAR2020pg2.label.signature"/></span>
                                <input type="text" name="t_signOffsSignature1" class="text-style" size="30"
                                       maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsSignature1", "")) %>"/>
                            </div>
                        </td>
                        <td width="6%">
                            <input type="checkbox"
                                   name="c_signOffsMD1" <carlos:encode value='<%= props.getProperty("c_signOffsMD1", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                            <fmt:message key="form.formBCAR2020pg2.label.md"/>
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsRM1" <carlos:encode value='<%= props.getProperty("c_signOffsRM1", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                            <fmt:message key="form.formBCAR2020pg2.label.rm"/>
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsNP1" <carlos:encode value='<%= props.getProperty("c_signOffsNP1", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                            <fmt:message key="form.formBCAR2020pg2.label.np"/>
                        </td>
                    </tr>
                    <tr>
                        <td width="40%">
                            <div class="divFlex">
                                2.
                                <span class="sub-text"><fmt:message key="form.formBCAR2020pg2.label.name"/></span>
                                <input type="text" name="t_signOffsName2" class="text-style" size="30" maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsName2", "")) %>"/>
                            </div>
                        </td>
                        <td width="40%">
                            <div class="divFlex">
                                <span class="sub-text"><fmt:message key="form.formBCAR2020pg2.label.signature"/></span>
                                <input type="text" name="t_signOffsSignature2" class="text-style" size="30"
                                       maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsSignature2", "")) %>"/>
                            </div>
                        </td>
                        <td width="6%">
                            <input type="checkbox"
                                   name="c_signOffsMD2" <carlos:encode value='<%= props.getProperty("c_signOffsMD2", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                            <fmt:message key="form.formBCAR2020pg2.label.md"/>
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsRM2" <carlos:encode value='<%= props.getProperty("c_signOffsRM2", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                            <fmt:message key="form.formBCAR2020pg2.label.rm"/>
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsNP2" <carlos:encode value='<%= props.getProperty("c_signOffsNP2", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                            <fmt:message key="form.formBCAR2020pg2.label.np"/>
                        </td>
                    </tr>
                    <tr>
                        <td width="40%">
                            <div class="divFlex">
                                3.
                                <span class="sub-text">(name)</span>
                                <input type="text" name="t_signOffsName3" class="text-style" size="30" maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsName3", "")) %>"/>
                            </div>
                        </td>
                        <td width="40%">
                            <div class="divFlex">
                                <span class="sub-text">(signature)</span>
                                <input type="text" name="t_signOffsSignature3" class="text-style" size="30"
                                       maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsSignature3", "")) %>"/>
                            </div>
                        </td>
                        <td width="6%">
                            <input type="checkbox"
                                   name="c_signOffsMD3" <carlos:encode value='<%= props.getProperty("c_signOffsMD3", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                            MD
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsRM3" <carlos:encode value='<%= props.getProperty("c_signOffsRM3", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                            RM
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsNP3" <carlos:encode value='<%= props.getProperty("c_signOffsNP3", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                            NP
                        </td>
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
                <input type="checkbox" name="print_pr2" id="print_pr2" checked="checked"
                       class="text ui-widget-content ui-corner-all"/>
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
                <input type="checkbox" name="print_pr5" id="print_pr5" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr5"><fmt:message key="form.formBCAR2020pg3.link.referencePage2"/></label>
                <br/>
            </fieldset>
        </form>
    </div>
    </body>


</html>
