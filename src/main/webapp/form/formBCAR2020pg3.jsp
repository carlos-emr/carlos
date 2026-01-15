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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="ca.openosp.openo.form.FrmBCAR2020Record" %>
<%@ page import="ca.openosp.openo.form.FrmRecordFactory" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    String formClass = "BCAR2020";
    Integer pageNo = 3;
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

        <title>BC Antenatal Record 2020 Part 2 Page 2</title>

        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
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
                init(3);

                // Set values in drop downs
                // $("select[name='s_investigationsABO']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_investigationsABO", "UN")) %>');
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
                            <b>
                                <a href="javascript:void(0);" onclick="return onPageChange('3');">Part 2 (Page 2)</a>
                            </b>
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
                        <th align="left">British Columbia Antenatal Record Part 2 (cont'd) <font size="-2">PSBC 1905 -
                            January 2020</font></th>
                    </tr>
                </table>

                <table width="100%" class="small9">
                    <tr>
                        <td width="60%" valign="top">
                            <!-- Planned place of birth -->
                            <table width="100%" class="outside-border">
                                <tr>
                                    <td colspan="2">
                                        <span class="title">16. Perinatal Considerations & Referrals</span><span
                                            class="sub-text"> (cont'd)</span>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="2">
                                        <div class="div-right">
                                            <span class="title">Confirmed EDD</span>
                                            <span class="sub-text">(dd/mm/yyyy)</span>
                                            <input type="text" id="d_confirmedEDD" name="d_confirmedEDD"
                                                   title="Section 16 - Confirmed EDD" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_confirmedEDD", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_confirmedEDD_cal">
                                        </div>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Lifestyle/substance use
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsContdLifestyle" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsContdLifestyle", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Pregnancy
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsContdPregnancy" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsContdPregnancy", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Labour & birth
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsContdLabour" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsContdLabour", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Breastfeeding
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsContdBreastfeeding" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsContdBreastfeeding", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Postpartum
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsContdPostpartum" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsContdPostpartum", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Contraception plan
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsContdContraception" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsContdContraception", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        Newborn
                                    </td>
                                    <td>
                                        <input type="text" name="t_considerationsContdNewborn" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_considerationsContdNewborn", "")) %>"/>
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
                                        <table width="100%" cellpadding="0">
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
                        </td>
                    </tr>
                </table>

                <table width="100%" class="outside-border">
                    <tr>
                        <td>
                            <div class="div-center"><i>Cont'd from previous page of British Columbia Antenatal Record
                                Part 2.</i></div>
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
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "15")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "16")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "17")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "18")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "19")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "20")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "21")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "22")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "23")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "24")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "25")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "26")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "27")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "28")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "29")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "30")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "31")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "32")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "33")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "34")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "35")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "36")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "37")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "38")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "39")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "40")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "41")%>
                    <%=((FrmBCAR2020Record) rec).createPrenatalVisitRow(props, "42")%>

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
                <input type="checkbox" name="print_pr2" id="print_pr2" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr2">Part 2 (Page 1)</label>
                <br/>
                <input type="checkbox" name="print_pr3" id="print_pr3" checked="checked"
                       class="text ui-widget-content ui-corner-all"/>
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
