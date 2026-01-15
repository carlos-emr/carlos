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
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.form.FrmBCAR2020Record" %>
<%@ page import="ca.openosp.openo.form.FrmRecordFactory" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>



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
        <title>BC Antenatal Record 2020 Part 2 Page 1</title>

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

        <link rel="stylesheet" type="text/css" media="all"
              href="<%=request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
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
                init(2);

                // Set values in drop downs
                $("select[name='s_investigationsABO']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_investigationsABO", "UN")) %>');
                $("select[name='s_investigationsRhFactor']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_investigationsRhFactor", "UN")) %>');

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
                            <b>
                                <a href="javascript:void(0);" onclick="return onPageChange('2');">Part 2 (Page 1)</a>
                            </b>
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
                            <a href="javascript:void(0);" onclick="return onPageChange('5');" class="small10">Reference
                                Page 2</a>
                        </td>
                    </tr>
                </table>

                <!-- Page Heading -->
                <table width="100%">
                    <tr>
                        <th>British Columbia Antenatal Record Part 2 <font size="-2">PSBC 1905 - January 2020</font>
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
                                        <span class="title">12.</span> Planned place of birth @ 20 wks
                                    </td>
                                    <td width="40%" style="border-left: 1px solid black;border-right: 1px solid black;">
                                        Planned place of birth @ 36 wks
                                    </td>
                                    <td width="20%" style="border-left: 1px solid black;border-right: 1px solid black;">
                                        Referral hospital
                                    </td>
                                </tr>
                                <tr>
                                    <td style="border-right: 1px solid black;">
                                        <input type="text" name="t_plannedBirthAt20Wks" size="15" maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_plannedBirthAt20Wks", "")) %>"/>
                                        <input type="checkbox"
                                               name="c_plannedBirthAt20WksCopyHospital" <%=Encode.forHtmlAttribute(props.getProperty("c_plannedBirthAt20WksCopyHospital", "").equals("X") ? "checked" : "") %> />
                                        Copy to hospital
                                    </td>
                                    <td style="border-left: 1px solid black;border-right: 1px solid black;">
                                        <input type="text" name="t_plannedBirthAt36Wks" size="15" maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_plannedBirthAt36Wks", "")) %>"/>
                                        <input type="checkbox"
                                               name="c_plannedBirthAt36WksCopyHospital" <%=Encode.forHtmlAttribute(props.getProperty("c_plannedBirthAt36WksCopyHospital", "").equals("X") ? "checked" : "") %> />
                                        Copy to hospital
                                    </td>
                                    <td style="border-left: 1px solid black;border-right: 1px solid black;">
                                        <input type="text" name="t_plannedBirthReferralHospital" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_plannedBirthReferralHospital", "")) == null || UtilMisc.htmlEscape(props.getProperty("t_plannedBirthReferralHospital", "")) == "" ? OscarProperties.getInstance().getProperty("BCAR_hospital") : ""  %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3"
                                        style="border-top: 1px solid black; border-bottom: 1px solid black;">
                                        <span class="title">Confirmed EDD</span><span
                                            class="sub-text">(dd/mm/yyyy)</span>
                                        <input type="text" id="d_confirmedEDD" name="d_confirmedEDD"
                                               title="Section 12 - Confirmed EDD" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_confirmedEDD", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_confirmedEDD_cal">
                                        by:
                                        <input type="checkbox"
                                               name="c_confirmedEDDUS" <%=Encode.forHtmlAttribute(props.getProperty("c_confirmedEDDUS", "").equals("X") ? "checked" : "") %> />
                                        US
                                        <input type="checkbox"
                                               name="c_confirmedEDDIVF" <%=Encode.forHtmlAttribute(props.getProperty("c_confirmedEDDIVF", "").equals("X") ? "checked" : "") %> />
                                        IVF
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
                                            Date
                                            <span class="sub-text">(dd/mm/yyyy)</span>
                                        </div>
                                        <div class="div-right">
                                            Antibody Titre
                                        </div>
                                    </td>
                                    <td width="28%" class="alignTop">
                                        Date RhIg given
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                    </td>
                                    <td width="18%" class="alignTop">
                                        Hemoglobin
                                        <span class="sub-text">(g/L)</span>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="10%" class="alignTop">
                                        <div class="div-center">
                                            ABO
                                        </div>
                                    </td>
                                    <td width="12%" class="alignTop">
                                        <div class="div-center">
                                            Rh factor
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
                                            <option value="UN">Unknown</option>
                                        </select>
                                    </td>
                                    <td>
                                        <select name="s_investigationsRhFactor" style="width: 100%">
                                            <option value="POS">Pos</option>
                                            <option value="NEG">Neg</option>
                                            <option value="UN">Unknown</option>
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
                                            <span class="title">Test</span>
                                        </div>
                                    </td>
                                    <td colspan="2" width="20%">
                                        <div class="div-center">
                                            <span class="title">Results</span>
                                        </div>
                                    </td>
                                    <td colspan="2" width="63%">
                                        <div class="div-center">
                                            <span class="title">Results/Follow-up/Comments</span>
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Rubella
                                    </td>
                                    <td width="10%">
                                        <input type="checkbox"
                                               name="c_investigationsRubellaImm" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsRubellaImm", "").equals("X") ? "checked" : "") %> />
                                        Imm
                                    </td>
                                    <td width="10%">
                                        <input type="checkbox"
                                               name="c_investigationsRubellaNonImm" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsRubellaNonImm", "").equals("X") ? "checked" : "") %> />
                                        N-Imm
                                    </td>
                                    <td width="32%">
                                        <div class="divFlex">
                                            Value
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
                                               name="c_investigationsRubellaPPVaccine" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsRubellaPPVaccine", "").equals("X") ? "checked" : "") %> />
                                        Postpartum vaccine required
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        HIV
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHIVNeg" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsHIVNeg", "").equals("X") ? "checked" : "") %> />
                                        Neg
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHIVPos" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsHIVPos", "").equals("X") ? "checked" : "") %> />
                                        Pos
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
                                               name="c_investigationsHIVT3Repeat" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsHIVT3Repeat", "").equals("X") ? "checked" : "") %> />
                                        T3 repeat if high-risk
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Syphilis
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsSyphilisNR" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsSyphilisNR", "").equals("X") ? "checked" : "") %> />
                                        N/R
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsSyphilisR" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsSyphilisR", "").equals("X") ? "checked" : "") %> />
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
                                        HBsAg
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgNR" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsHBsAgNR", "").equals("X") ? "checked" : "") %> />
                                        N/R
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgR" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsHBsAgR", "").equals("X") ? "checked" : "") %> />
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
                                               name="c_investigationsHBsAgPartner" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsHBsAgPartner", "").equals("X") ? "checked" : "") %> />
                                        Partner/household contact
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgAntiViral" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsHBsAgAntiViral", "").equals("X") ? "checked" : "") %> />
                                        Anti-viral therapy required
                                        <br/>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgNewbornVaccine" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsHBsAgNewbornVaccine", "").equals("X") ? "checked" : "") %> />
                                        Newborn vaccine required
                                        <br/>
                                        <input type="checkbox"
                                               name="c_investigationsHBsAgNewbornHBIg" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsHBsAgNewbornHBIg", "").equals("X") ? "checked" : "") %> />
                                        Newborn HBIg required
                                        <br/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Gonorrhea
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGonorrheaNeg" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGonorrheaNeg", "").equals("X") ? "checked" : "") %> />
                                        Neg
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGonorrheaPos" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGonorrheaPos", "").equals("X") ? "checked" : "") %> />
                                        Pos
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
                                               name="c_investigationsGonorrheaT3Repeat" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGonorrheaT3Repeat", "").equals("X") ? "checked" : "") %> />
                                        T3 repeat if Pos
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Chlamydia
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsChlamydiaNeg" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsChlamydiaNeg", "").equals("X") ? "checked" : "") %> />
                                        Neg
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsChlamydiaPos" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsChlamydiaPos", "").equals("X") ? "checked" : "") %> />
                                        Pos
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
                                               name="c_investigationsChlamydiaT3Repeat" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsChlamydiaT3Repeat", "").equals("X") ? "checked" : "") %> />
                                        T3 repeat if Pos
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Urine C&S
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsUrineNeg" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsUrineNeg", "").equals("X") ? "checked" : "") %> />
                                        Neg
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsUrinePos" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsUrinePos", "").equals("X") ? "checked" : "") %> />
                                        Pos
                                    </td>
                                    <td>
                                        <div class="divFlex">
                                            Culture
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
                                        GDM
                                        <span class="sub-text">(@24-28 wks)</span>
                                    </td>
                                    <td>
                                    </td>
                                    <td>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMTestDeclined" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGDMTestDeclined", "").equals("X") ? "checked" : "") %> />
                                        GDM test declined
                                    </td>
                                    <td rowspan="2" valign="top">
                                        <input type="checkbox"
                                               name="c_investigationsGDMDietControlled" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGDMDietControlled", "").equals("X") ? "checked" : "") %> />
                                        Diet controlled
                                        <br/>
                                        <input type="checkbox"
                                               name="c_investigationsGDMInsulinReqd" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGDMInsulinReqd", "").equals("X") ? "checked" : "") %> />
                                        Insulin required
                                    </td>
                                </tr>
                                <tr class="noBorderTopBottom">
                                    <td>
                                        <span style="margin-left: 2em;">GCT</span>
                                        <span class="sub-text">(50 g)</span>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMGCTNeg" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGDMGCTNeg", "").equals("X") ? "checked" : "") %> />
                                        Neg
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMGCTPos" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGDMGCTPos", "").equals("X") ? "checked" : "") %> />
                                        Pos
                                    </td>
                                    <td>
                                        Value
                                        <span class="sub-text">(mmol/L)</span>
                                        @1hr
                                        <input type="text" name="t_investigationsGDMGCT1hr" size="6" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_investigationsGDMGCT1hr", "")) %>"/>
                                    </td>
                                </tr>
                                <tr class="noBorderTopBottom">
                                    <td>
                                        <span style="margin-left: 2em;">GTT</span>
                                        <span class="sub-text">(75 g)</span>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMGTTNeg" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGDMGTTNeg", "").equals("X") ? "checked" : "") %> />
                                        Neg
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGDMGTTPos" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGDMGTTPos", "").equals("X") ? "checked" : "") %> />
                                        Pos
                                    </td>
                                    <td>
                                        Value
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
                                               name="c_investigationsGBSNeg" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGBSNeg", "").equals("X") ? "checked" : "") %> />
                                        Neg
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGBSPos" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGBSPos", "").equals("X") ? "checked" : "") %> />
                                        Pos
                                    </td>
                                    <td>
                                        Date
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                        <input type="text" id="d_investigationsGBSDate" name="d_investigationsGBSDate"
                                               size="8" maxlength="20"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_investigationsGBSDate", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_investigationsGBSDate_cal">
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_investigationsGBSCopyHospital" <%=Encode.forHtmlAttribute(props.getProperty("c_investigationsGBSCopyHospital", "").equals("X") ? "checked" : "") %> />
                                        Copy to hospital
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="5">
                                        Other (e.g. Ferritin, TSH, HepC)
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
                                        <div class="title">Prenatal Genetic Investigations</div>
                                    </td>
                                    <td colspan="2">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticDeclined" <%=Encode.forHtmlAttribute(props.getProperty("c_prenatalGeneticDeclined", "").equals("X") ? "checked" : "") %> />
                                        Declined
                                    </td>
                                    <td style="border-left: 1px solid black;">
                                        <div class="title">Results</div>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="18%">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticSIPS" <%=Encode.forHtmlAttribute(props.getProperty("c_prenatalGeneticSIPS", "").equals("X") ? "checked" : "") %> />
                                        SIPS
                                    </td>
                                    <td width="20%">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticIPS" <%=Encode.forHtmlAttribute(props.getProperty("c_prenatalGeneticIPS", "").equals("X") ? "checked" : "") %> />
                                        IPS
                                    </td>
                                    <td width="20">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticQuad" <%=Encode.forHtmlAttribute(props.getProperty("c_prenatalGeneticQuad", "").equals("X") ? "checked" : "") %> />
                                        Quad
                                    </td>
                                    <td width="10%">
                                        <input type="checkbox"
                                               name="c_prenatalGeneticCVS" <%=Encode.forHtmlAttribute(props.getProperty("c_prenatalGeneticCVS", "").equals("X") ? "checked" : "") %> />
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
                                               name="c_prenatalGeneticNIPTMSP" <%=Encode.forHtmlAttribute(props.getProperty("c_prenatalGeneticNIPTMSP", "").equals("X") ? "checked" : "") %> />
                                        NIPT (MSP)
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_prenatalGeneticNIPTSelf" <%=Encode.forHtmlAttribute(props.getProperty("c_prenatalGeneticNIPTSelf", "").equals("X") ? "checked" : "") %> />
                                        NIPT (self-pay)
                                    </td>
                                    <td>
                                        <div class="divFlex">
                                            <input type="checkbox"
                                                   name="c_prenatalGeneticOther" <%=Encode.forHtmlAttribute(props.getProperty("c_prenatalGeneticOther", "").equals("X") ? "checked" : "") %> />
                                            Other
                                            <input type="text" name="t_prenatalGeneticOtherDetails" size="10"
                                                   maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_prenatalGeneticOtherDetails", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_prenatalGeneticAmnio" <%=Encode.forHtmlAttribute(props.getProperty("c_prenatalGeneticAmnio", "").equals("X") ? "checked" : "") %> />
                                        Amnio
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
                                               name="c_edinburgDeclined" <%=Encode.forHtmlAttribute(props.getProperty("c_edinburgDeclined", "").equals("X") ? "checked" : "") %> />
                                        Declined
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="2">
                                        Date
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                        <input type="text" id="d_edinburgDate" name="d_edinburgDate"
                                               title="14. Edinburgh - Date" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_edinburgDate", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_edinburgDate_cal">
                                        GA
                                        <span class="sub-text">(wks/days)</span>
                                        <input type="text" name="t_edinburgGA" title="14. Edinburgh - GA"
                                               class="calcField" size="8" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_edinburgGA", "")) %>"
                                               onDblClick="getGAByFieldDate('t_edinburgGA', 'd_confirmedEDD', 'd_edinburgDate')"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="2">
                                        <div class="flex-row flex-justify">
                                            <div>
                                                Total Score
                                                <input type="text" name="t_edinburgTotalScore" size="6" maxlength="150"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_edinburgTotalScore", "")) %>"/>
                                            </div>
                                            <div>
                                                Anxiety subscore
                                                <span class="sub-text">(questions 3-5)</span>
                                                <input type="text" name="t_edinburgAnxietySubscore" size="6"
                                                       maxlength="150"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_edinburgAnxietySubscore", "")) %>"/>
                                            </div>
                                            <div>
                                                Self-harm subscore
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
                                            Follow-up
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
                                        Surname<br/>
                                        <input type="text" name="t_patientSurname" class="text-style" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientSurname", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td valign="top" width="50%" colspan="2">
                                        Given name<br/>
                                        <input type="text" name="t_patientGivenName" class="text-style" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientGivenName", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3">
                                        Address - Number, Street name<br/>
                                        <input type="text" name="t_patientAddress" class="text-style" size="60"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientAddress", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="50%">
                                        City<br/>
                                        <input type="text" name="t_patientCity" class="text-style" size="60"
                                               maxlength="50"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientCity", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        Province<br/>
                                        <input type="text" name="t_patientProvince" class="text-style" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientProvince", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        Postal Code<br/>
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
                                        <span class="title">15.</span> <span class="title">Ultrasounds & Other Imaging Investigations</span>
                                    </td>
                                </tr>
                                <tr>
                                    <th width="27%" class="div-center">
                                        Date</br>
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                    </th>
                                    <th width="10%" class="div-center">
                                        GA</br>
                                        <span class="sub-text">(wks/days)</span>
                                    </th>
                                    <th width="63%" class="div-center">
                                        Comments
                                    </th>
                                </tr>
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" id="d_imagingDate1" name="d_imagingDate1"
                                                   title="US/Imaging Line 1 - Date" size="9" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_imagingDate1", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_imagingDate1_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingGA1" title="US/Imaging Line 1 - GA"
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
                                                   title="US/Imaging Line 2 - Date" size="9" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_imagingDate2", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_imagingDate2_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingGA2" title="US/Imaging Line 2 - GA"
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
                                                   title="US/Imaging Line 3 - Date" size="9" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_imagingDate3", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_imagingDate3_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingGA3" title="US/Imaging Line 3 - GA"
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
                                                   title="US/Imaging Line 4 - Date" size="9" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_imagingDate4", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_imagingDate4_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_imagingGA4" title="US/Imaging Line 4 - GA"
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
                                        <span class="title">16.</span> <span class="title">Perinatal Considerations & Referrals</span>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="40%">
                                        Pregnancy type:
                                    </td>
                                    <td width="60%">
                                        <input type="checkbox"
                                               name="c_considerationsPregnancySingleton" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsPregnancySingleton", "").equals("X") ? "checked" : "") %> />
                                        Singleton
                                        <input type="checkbox"
                                               name="c_considerationsPregnancyTwin" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsPregnancyTwin", "").equals("X") ? "checked" : "") %> />
                                        Twin
                                        <input type="checkbox"
                                               name="c_considerationsPregnancyMultiple" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsPregnancyMultiple", "").equals("X") ? "checked" : "") %> />
                                        Multiple (3+)
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        VBAC eligible @ 36 wks:
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_considerationsVBACEligNo" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsVBACEligNo", "").equals("X") ? "checked" : "") %> />
                                        No
                                        <input type="checkbox"
                                               name="c_considerationsVBACEligYes" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsVBACEligYes", "").equals("X") ? "checked" : "") %> />
                                        Yes
                                        <input type="checkbox"
                                               name="c_considerationsVBACEligNA" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsVBACEligNA", "").equals("X") ? "checked" : "") %> />
                                        N/A
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        VBAC planned @ 36 wks:
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_considerationsVBACPlanNo" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsVBACPlanNo", "").equals("X") ? "checked" : "") %> />
                                        No
                                        <input type="checkbox"
                                               name="c_considerationsVBACPlanYes" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsVBACPlanYes", "").equals("X") ? "checked" : "") %> />
                                        Yes
                                        <input type="checkbox"
                                               name="c_considerationsVBACPlanNA" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsVBACPlanNA", "").equals("X") ? "checked" : "") %> />
                                        N/A
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Plan to breastfeed:
                                    </td>
                                    <td>
                                        <input type="checkbox"
                                               name="c_considerationsBreastfeedNo" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsBreastfeedNo", "").equals("X") ? "checked" : "") %> />
                                        No
                                        <input type="checkbox"
                                               name="c_considerationsBreastfeedYes" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsBreastfeedYes", "").equals("X") ? "checked" : "") %> />
                                        Yes
                                        <input type="checkbox"
                                               name="c_considerationsBreastfeedUN" <%=Encode.forHtmlAttribute(props.getProperty("c_considerationsBreastfeedUN", "").equals("X") ? "checked" : "") %> />
                                        Undecided
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Lifestyle/substance use
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsLifestyle" class="text-style" size="60"
                                               maxlength="39"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsLifestyle", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Pregnancy
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsPregnancy" class="text-style" size="60"
                                               maxlength="48"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsPregnancy", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Labour & birth
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsLabour" class="text-style" size="60"
                                               maxlength="45"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsLabour", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Breastfeeding
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsBreastfeeding" class="text-style"
                                               size="60" maxlength="48"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsBreastfeeding", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Postpartum
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsPostpartum" class="text-style"
                                               size="60" maxlength="48"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsPostpartum", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Contraception plan
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsContraception" class="text-style"
                                               size="60" maxlength="41"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsContraception", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Newborn
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
                            Date
                            <br/>
                            <span class="sub-text">(dd/mm/yyyy)</span>
                        </th>
                        <th width="4%">
                            GA
                            <br/>
                            <span class="sub-text">(wks/days)</span>
                        </th>
                        <th width="6%">
                            BP
                        </th>
                        <th width="7%">
                            Urine
                            <br/>
                            <span class="sub-text">(if indicated)</span>
                        </th>
                        <th width="4%">
                            WT
                            <br/>
                            <span class="sub-text">(kg)</span>
                        </th>
                        <th width="4%">
                            Fundus
                            <br/>
                            <span class="sub-text">(cm)</span>
                        </th>
                        <th width="5%">
                            FHR
                            <br/>
                            <span class="sub-text">(per min)</span>
                        </th>
                        <th width="4%">
                            FM
                        </th>
                        <th width="7%">
                            Pres. &
                            <br/>
                            position
                        </th>
                        <th width="40%">
                            Comments*
                        </th>
                        <th width="5%">
                            Next
                            <br/>
                            visit
                        </th>
                        <th width="3%">
                            Initials
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
                            <i>Please see the next page, British Columbia Antenatal Record part 2 (cont'd), to record
                                additional visits.</i>
                        </td>
                    </tr>

                </table>
                <table width="100%" class="outside-border">
                    <tr>
                        <td colspan="5">
                            <span class="title">18. Sign-Offs</span>
                        </td>
                    </tr>
                    <tr>
                        <td width="40%">
                            <div class="divFlex">
                                1.
                                <span class="sub-text">(name)</span>
                                <input type="text" name="t_signOffsName1" class="text-style" size="30" maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsName1", "")) %>"/>
                            </div>
                        </td>
                        <td width="40%">
                            <div class="divFlex">
                                <span class="sub-text">(signature)</span>
                                <input type="text" name="t_signOffsSignature1" class="text-style" size="30"
                                       maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsSignature1", "")) %>"/>
                            </div>
                        </td>
                        <td width="6%">
                            <input type="checkbox"
                                   name="c_signOffsMD1" <%=Encode.forHtmlAttribute(props.getProperty("c_signOffsMD1", "").equals("X") ? "checked" : "") %> />
                            MD
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsRM1" <%=Encode.forHtmlAttribute(props.getProperty("c_signOffsRM1", "").equals("X") ? "checked" : "") %> />
                            RM
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsNP1" <%=Encode.forHtmlAttribute(props.getProperty("c_signOffsNP1", "").equals("X") ? "checked" : "") %> />
                            NP
                        </td>
                    </tr>
                    <tr>
                        <td width="40%">
                            <div class="divFlex">
                                2.
                                <span class="sub-text">(name)</span>
                                <input type="text" name="t_signOffsName2" class="text-style" size="30" maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsName2", "")) %>"/>
                            </div>
                        </td>
                        <td width="40%">
                            <div class="divFlex">
                                <span class="sub-text">(signature)</span>
                                <input type="text" name="t_signOffsSignature2" class="text-style" size="30"
                                       maxlength="150"
                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_signOffsSignature2", "")) %>"/>
                            </div>
                        </td>
                        <td width="6%">
                            <input type="checkbox"
                                   name="c_signOffsMD2" <%=Encode.forHtmlAttribute(props.getProperty("c_signOffsMD2", "").equals("X") ? "checked" : "") %> />
                            MD
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsRM2" <%=Encode.forHtmlAttribute(props.getProperty("c_signOffsRM2", "").equals("X") ? "checked" : "") %> />
                            RM
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsNP2" <%=Encode.forHtmlAttribute(props.getProperty("c_signOffsNP2", "").equals("X") ? "checked" : "") %> />
                            NP
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
                                   name="c_signOffsMD3" <%=Encode.forHtmlAttribute(props.getProperty("c_signOffsMD3", "").equals("X") ? "checked" : "") %> />
                            MD
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsRM3" <%=Encode.forHtmlAttribute(props.getProperty("c_signOffsRM3", "").equals("X") ? "checked" : "") %> />
                            RM
                        </td>
                        <td width="7%">
                            <input type="checkbox"
                                   name="c_signOffsNP3" <%=Encode.forHtmlAttribute(props.getProperty("c_signOffsNP3", "").equals("X") ? "checked" : "") %> />
                            NP
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
                <input type="checkbox" name="print_pr1" id="print_pr1" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr1">Part 1</label>
                <br/>
                <input type="checkbox" name="print_pr2" id="print_pr2" checked="checked"
                       class="text ui-widget-content ui-corner-all"/>
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
