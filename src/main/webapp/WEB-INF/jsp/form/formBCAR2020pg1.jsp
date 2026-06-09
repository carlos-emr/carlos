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

<%@ page import=" io.github.carlos_emr.carlos.form.*, java.util.Properties" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilMisc" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmBCAR2020Record" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>




<%
    String formClass = "BCAR2020";
    String pageNo = "1";
    String formLink = "formBCAR2020pg1.jsp";

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));
    String provNo = (String) session.getAttribute("user");
    String providerNo = request.getParameter("provider_no") != null ? request.getParameter("provider_no") : loggedInInfo.getLoggedInProviderNo();
    String appointment = request.getParameter("appointmentNo") != null ? request.getParameter("appointmentNo") : "";

    FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
    Properties props = null;
    try {
        props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);
    } catch (SQLException e) {
        // um do nothing I guess. Is the database set up correctly?
    }

%>
<!DOCTYPE html>
<html>

    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title><fmt:message key="form.formBCAR2020pg1.title"/></title>

        <link rel="stylesheet" type="text/css" media="all"
              href="<%=request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/bootstrap/5.3.8/css/bootstrap.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/multiselect-dropdown.css"/>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/jquery/jquery-ui.structure-1.14.2.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/jquery/jquery-ui.theme-1.14.2.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/formBCAR2020.css">

        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%=request.getContextPath()%>/share/calendar/lang/<fmt:message key='global.javascript.calendar'/>"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar-setup.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/formBCAR2020Record.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js" type="text/javascript"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js" type="text/javascript"></script>

        <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js" type="text/javascript"></script>
        <script src="<%=request.getContextPath() %>/js/multiselect-dropdown.js" type="text/javascript"></script>

        <script src="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.14.2.min.js"
                type="text/javascript"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/jquery.are-you-sure.js"></script>

        <!-- Field Naming Scheme throughout BCAR2020
                c_XXXX Is a checkbox field
                d_XXXX Is a textbox field containing a date
                t_XXXX Is a textbox field containing text
                s_XXXX Is a drop down field (select field)
        -->

        <script type="text/javascript">
            $(document).ready(function () {

                init(1);

                // Set values in drop downs
                $("select[name='s_relationshipStatus']").val('<carlos:encode value='<%= props.getProperty("s_relationshipStatus", "UN") %>' context="javaScriptBlock"/>');
                $("select[name='s_highestEducation']").val('<carlos:encode value='<%= props.getProperty("s_highestEducation", "UN") %>' context="javaScriptBlock"/>');
                $("select[name='s_languagePreferred']").val('<carlos:encode value='<%= props.getProperty("s_languagePreferred", "") %>' context="javaScriptBlock"/>');
                $("select[name='s_obHistorySex1']").val('<carlos:encode value='<%= props.getProperty("s_obHistorySex1", "") %>' context="javaScriptBlock"/>');
                $("select[name='s_obHistorySex2']").val('<carlos:encode value='<%= props.getProperty("s_obHistorySex2", "") %>' context="javaScriptBlock"/>');
                $("select[name='s_obHistorySex3']").val('<carlos:encode value='<%= props.getProperty("s_obHistorySex3", "") %>' context="javaScriptBlock"/>');
                $("select[name='s_obHistorySex4']").val('<carlos:encode value='<%= props.getProperty("s_obHistorySex4", "") %>' context="javaScriptBlock"/>');
                $("select[name='s_obHistorySex5']").val('<carlos:encode value='<%= props.getProperty("s_obHistorySex5", "") %>' context="javaScriptBlock"/>');

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

        <%@ include file="demographicMeasurementModal.jsp" %>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    </head>

    <body bgproperties="fixed">
    <div id="maincontent" class="flex-container">

        <%@ taglib uri="jakarta.tags.core" prefix="c" %>
        <c:if test="${param.warning eq 'history'}">
            <script type="text/javascript">
                if (!confirm("Warning: older version.\n\nContents of this form will overwrite newer versions if saved.\n\nSelect 'OK' to continue.")) {
                    window.close();
                }
            </script>
        </c:if>

        <div id="content_bar" class="innertube">
            <form action="${pageContext.request.contextPath}/form/BCAR2020" method="post">
                <input type="hidden" id="demographicNo" name="demographicNo" value="<%=demoNo%>"/>
                <input type="hidden" id="formId" name="formId" value="<%=formId%>"/>
                <input type="hidden" name="provider_no" value=<carlos:encode value='<%= providerNo %>' context="htmlUnquotedAttribute"/>/>
                <input type="hidden" id="user" name="provNo" value=<%=provNo%>/>
                <input type="hidden" name="method" value="exit"/>

                <input type="hidden" name="forwardTo" value="<carlos:encode value='<%= pageNo %>' context="htmlAttribute"/>"/>
                <input type="hidden" name="pageNo" value="<carlos:encode value='<%= pageNo %>' context="htmlAttribute"/>"/>
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
                            <b>
                                <a href="javascript:void(0);" onclick="return onPageChange('1');"><fmt:message key="form.formBCAR2020pg3.link.part1"/></a>
                            </b>
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
                            <a href="javascript:void(0);" onclick="return onPageChange('5');" class="small10"><fmt:message key="form.formBCAR2020pg3.link.referencePage2"/></a>
                        </td>
                    </tr>
                </table>

                <!-- Page Heading -->
                <table border="0">
                    <tr>
                        <th align="left"><fmt:message key="form.formBCAR2020pg1.heading.record"/> <font size="-2"><fmt:message key="form.formBCAR2020pg3.heading.subtitle"/></font></th>
                    </tr>
                </table>

                <table border="0" class="small9">
                    <tr>
                        <td width="60%">
                            <!-- Demographics and Background-->
                            <table border="1">
                                <tr>
                                    <td width="50%" colspan="2">
                                        <span class="title">1.</span> <fmt:message key="form.formBCAR2020pg1.label.primaryCareProvider"/><br/>
                                        <input type="text" name="t_primaryCareProvider" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_primaryCareProvider", "")) %>"/>
                                    </td>
                                    <td width="50%" colspan="2">
                                        <fmt:message key="form.formBCAR2020pg1.label.familyPhysician"/><br/>
                                        <input type="text" name="t_familyPhysician" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_familyPhysician", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.patientSurname"/><br/>
                                        <input type="text" name="t_patientSurname" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientSurname", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.patientGivenName"/><br/>
                                        <input type="text" name="t_patientGivenName" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientGivenName", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.dob"/> <span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <input type="text" name="t_patientDOB" style="width: 100%" size="30"
                                               maxlength="12"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientDOB", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.ageAtEDD"/><br/>
                                        <input type="text" name="t_ageAtEDD" class="calcField"
                                               ondblclick="calcAgeAtEDD('<%= props.getProperty("d_confirmedEDD", "") %>', document.forms[0].t_patientDOB.value, this);"
                                               style="width: 100%" size="30" maxlength="10"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_ageAtEDD", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.surnameAtBirth"/><br/>
                                        <input type="text" name="t_patientSurnameAtBirth" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientSurnameAtBirth", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.preferredNamePronoun"/><br/>
                                        <input type="text" name="t_patientPreferredName" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientPreferredName", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.languagePreferred"/><br/>
                                        <select name="s_languagePreferred" style="width: 100%">
                                            <option value="ENG">English</option>
                                            <option value="FRA">French</option>
                                            <option value="AAR">Afar</option>
                                            <option value="AFR">Afrikaans</option>
                                            <option value="AKA">Akan</option>
                                            <option value="SQI">Albanian</option>
                                            <option value="ASE">American Sign Language (ASL)</option>
                                            <option value="AMH">Amharic</option>
                                            <option value="ARA">Arabic</option>
                                            <option value="ARG">Aragonese</option>
                                            <option value="HYE">Armenian</option>
                                            <option value="ASM">Assamese</option>
                                            <option value="AVA">Avaric</option>
                                            <option value="AYM">Aymara</option>
                                            <option value="AZE">Azerbaijani</option>
                                            <option value="BAM">Bambara</option>
                                            <option value="BAK">Bashkir</option>
                                            <option value="EUS">Basque</option>
                                            <option value="BEL">Belarusian</option>
                                            <option value="BEN">Bengali</option>
                                            <option value="BIS">Bislama</option>
                                            <option value="BOS">Bosnian</option>
                                            <option value="BRE">Breton</option>
                                            <option value="BUL">Bulgarian</option>
                                            <option value="MYA">Burmese</option>
                                            <option value="CAT">Catalan</option>
                                            <option value="KHM">Central Khmer</option>
                                            <option value="CHA">Chamorro</option>
                                            <option value="CHE">Chechen</option>
                                            <option value="YUE">Chinese Cantonese</option>
                                            <option value="CMN">Chinese Mandarin</option>
                                            <option value="CHV">Chuvash</option>
                                            <option value="COR">Cornish</option>
                                            <option value="COS">Corsican</option>
                                            <option value="CRE">Cree</option>
                                            <option value="HRV">Croatian</option>
                                            <option value="CES">Czech</option>
                                            <option value="DAN">Danish</option>
                                            <option value="DIV">Dhivehi</option>
                                            <option value="NLD">Dutch</option>
                                            <option value="DZO">Dzongkha</option>
                                            <option value="EST">Estonian</option>
                                            <option value="EWE">Ewe</option>
                                            <option value="FAO">Faroese</option>
                                            <option value="FIJ">Fijian</option>
                                            <option value="FIL">Filipino</option>
                                            <option value="FIN">Finnish</option>
                                            <option value="FUL">Fulah</option>
                                            <option value="GLG">Galician</option>
                                            <option value="LUG">Ganda</option>
                                            <option value="KAT">Georgian</option>
                                            <option value="DEU">German</option>
                                            <option value="GRN">Guarani</option>
                                            <option value="GUJ">Gujarati</option>
                                            <option value="HAT">Haitian</option>
                                            <option value="HAU">Hausa</option>
                                            <option value="HEB">Hebrew</option>
                                            <option value="HER">Herero</option>
                                            <option value="HIN">Hindi</option>
                                            <option value="HMO">Hiri Motu</option>
                                            <option value="HUN">Hungarian</option>
                                            <option value="ISL">Icelandic</option>
                                            <option value="IBO">Igbo</option>
                                            <option value="IND">Indonesian</option>
                                            <option value="IKU">Inuktitut</option>
                                            <option value="IPK">Inupiaq</option>
                                            <option value="GLE">Irish</option>
                                            <option value="ITA">Italian</option>
                                            <option value="JPN">Japanese</option>
                                            <option value="JAV">Javanese</option>
                                            <option value="KAL">Kalaallisut</option>
                                            <option value="KAN">Kannada</option>
                                            <option value="KAU">Kanuri</option>
                                            <option value="KAS">Kashmiri</option>
                                            <option value="KAZ">Kazakh</option>
                                            <option value="KIK">Kikuyu</option>
                                            <option value="KIN">Kinyarwanda</option>
                                            <option value="KIR">Kirghiz</option>
                                            <option value="KOM">Komi</option>
                                            <option value="KON">Kongo</option>
                                            <option value="KOR">Korean</option>
                                            <option value="KUA">Kuanyama</option>
                                            <option value="KUR">Kurdish</option>
                                            <option value="LAO">Lao</option>
                                            <option value="LAV">Latvian</option>
                                            <option value="LIM">Limburgan</option>
                                            <option value="LIN">Lingala</option>
                                            <option value="LIT">Lithuanian</option>
                                            <option value="LUB">Luba-Katanga</option>
                                            <option value="LTZ">Luxembourgish</option>
                                            <option value="MKD">Macedonian</option>
                                            <option value="MLG">Malagasy</option>
                                            <option value="MSA">Malay</option>
                                            <option value="MAL">Malayalam</option>
                                            <option value="MLT">Maltese</option>
                                            <option value="GLV">Manx</option>
                                            <option value="MRI">Maori</option>
                                            <option value="MAR">Marathi</option>
                                            <option value="MAH">Marshallese</option>
                                            <option value="ELL">Greek</option>
                                            <option value="MON">Mongolian</option>
                                            <option value="NAU">Nauru</option>
                                            <option value="NAV">Navajo</option>
                                            <option value="NDO">Ndonga</option>
                                            <option value="NEP">Nepali</option>
                                            <option value="NDE">North Ndebele</option>
                                            <option value="SME">Northern Sami</option>
                                            <option value="NOR">Norwegian</option>
                                            <option value="NOB">Norwegian Bokm�l</option>
                                            <option value="NNO">Norwegian Nynorsk</option>
                                            <option value="NYA">Nyanja</option>
                                            <option value="OCI">Occitan (post 1500)</option>
                                            <option value="OJI">Ojibwa</option>
                                            <option value="OJC">Oji-cree</option>
                                            <option value="ORI">Oriya</option>
                                            <option value="ORM">Oromo</option>
                                            <option value="OSS">Ossetian</option>
                                            <option value="PAN">Panjabi</option>
                                            <option value="FAS">Persian</option>
                                            <option value="POL">Polish</option>
                                            <option value="POR">Portuguese</option>
                                            <option value="PUS">Pashto</option>
                                            <option value="QUE">Quechua</option>
                                            <option value="RON">Romanian</option>
                                            <option value="ROH">Romansh</option>
                                            <option value="RUN">Rundi</option>
                                            <option value="RUS">Russian</option>
                                            <option value="SMO">Samoan</option>
                                            <option value="SAG">Sango</option>
                                            <option value="SRD">Sardinian</option>
                                            <option value="GLA">Scottish Gaelic</option>
                                            <option value="SRP">Serbian</option>
                                            <option value="SNA">Shona</option>
                                            <option value="III">Sichuan Yi</option>
                                            <option value="SND">Sindhi</option>
                                            <option value="SIN">Sinhala</option>
                                            <option value="SGN"><fmt:message key="form.formBCAR2020pg1.option.otherSignLanguage"/></option>
                                            <option value="SLK">Slovak</option>
                                            <option value="SLV">Slovenian</option>
                                            <option value="SOM">Somali</option>
                                            <option value="NBL">South Ndebele</option>
                                            <option value="SOT">Southern Sotho</option>
                                            <option value="SPA">Spanish</option>
                                            <option value="SUN">Sundanese</option>
                                            <option value="SWA">Swahili (macrolanguage)</option>
                                            <option value="SSW">Swati</option>
                                            <option value="SWE">Swedish</option>
                                            <option value="TGL">Tagalog</option>
                                            <option value="TAH">Tahitian</option>
                                            <option value="TGK">Tajik</option>
                                            <option value="TAM">Tamil</option>
                                            <option value="TAT">Tatar</option>
                                            <option value="TEL">Telugu</option>
                                            <option value="THA">Thai</option>
                                            <option value="BOD">Tibetan</option>
                                            <option value="TIR">Tigrinya</option>
                                            <option value="TON">Tonga (Tonga Islands)</option>
                                            <option value="TSO">Tsonga</option>
                                            <option value="TSN">Tswana</option>
                                            <option value="TUR">Turkish</option>
                                            <option value="TUK">Turkmen</option>
                                            <option value="TWI">Twi</option>
                                            <option value="UIG">Uighur</option>
                                            <option value="UKR">Ukrainian</option>
                                            <option value="URD">Urdu</option>
                                            <option value="UZB">Uzbek</option>
                                            <option value="VEN">Venda</option>
                                            <option value="VIE">Vietnamese</option>
                                            <option value="WLN">Walloon</option>
                                            <option value="CYM">Welsh</option>
                                            <option value="FRY">Western Frisian</option>
                                            <option value="WOL">Wolof</option>
                                            <option value="XHO">Xhosa</option>
                                            <option value="YID">Yiddish</option>
                                            <option value="YOR">Yoruba</option>
                                            <option value="ZHA">Zhuang</option>
                                            <option value="ZUL">Zulu</option>
                                            <option value="OTH"><fmt:message key="global.other"/></option>
                                            <option value="UN">Unknown</option>
                                        </select>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.relationshipStatus"/><br/>
                                        <select name="s_relationshipStatus" style="width: 100%">
                                            <option value="MA"><fmt:message key="form.formBCAR2020pg1.option.married"/></option>
                                            <option value="LI"><fmt:message key="form.formBCAR2020pg1.option.livingWithPartner"/></option>
                                            <option value="SI"><fmt:message key="form.formBCAR2020pg1.option.single"/></option>
                                            <option value="SE"><fmt:message key="form.formBCAR2020pg1.option.separatedOrDivorced"/></option>
                                            <option value="WI"><fmt:message key="form.formBCAR2020pg1.option.widowed"/></option>
                                            <option value="NA"><fmt:message key="form.formBCAR2020pg1.option.preferNotToAnswer"/></option>
                                            <option value="UN"><fmt:message key="form.formBCAR2020pg1.option.unknown"/></option>
                                        </select>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="50%" colspan="2">
                                        <fmt:message key="form.formBCAR2020pg1.label.highestEducation"/><br/>
                                        <select name="s_highestEducation" style="width: 100%">
                                            <option value="LH"><fmt:message key="form.formBCAR2020pg1.option.lessThanHighSchool"/></option>
                                            <option value="HS"><fmt:message key="form.formBCAR2020pg1.option.highSchoolDiploma"/></option>
                                            <option value="TD"><fmt:message key="form.formBCAR2020pg1.option.tradeCertificate"/></option>
                                            </option>
                                            <option value="UD"><fmt:message key="form.formBCAR2020pg1.option.undergraduateDegree"/></option>
                                            <option value="PD"><fmt:message key="form.formBCAR2020pg1.option.postgraduateDegree"/></option>
                                            <option value="UN"><fmt:message key="form.formBCAR2020pg1.option.unknown"/></option>
                                        </select>
                                    </td>
                                    <td width="50%" colspan="2">
                                        <fmt:message key="form.formBCAR2020pg1.label.occupation"/><br/>
                                        <input type="text" name="t_occupation" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_occupation", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="4">
                                        <table border="0">
                                            <tr>
                                                <td width="35%" class="alignTop borderRight">
                                                    <table border="0">
                                                        <tr>
                                                            <td width="55%">
                                                                <fmt:message key="form.formBCAR2020pg1.label.indigenousIdentity"/>
                                                            </td>
                                                            <td width="45%">
                                                                <input type="checkbox"
                                                                       name="c_indIdentFirstNations" <carlos:encode value='<%= props.getProperty("c_indIdentFirstNations", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.firstNations"/>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_indIdentNoResponse" <carlos:encode value='<%= props.getProperty("c_indIdentNoResponse", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.noResponse"/>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_indIdentMetis" <carlos:encode value='<%= props.getProperty("c_indIdentMetis", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.metis"/>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_indIdentNone" <carlos:encode value='<%= props.getProperty("c_indIdentNone", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.none"/>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_indIdentInuk" <carlos:encode value='<%= props.getProperty("c_indIdentInuk", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.inuk"/>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                                <td width="15%" class="alignTop borderRight">
                                                    <input type="checkbox"
                                                           name="c_indIdentStatus" <carlos:encode value='<%= props.getProperty("c_indIdentStatus", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.status"/>
                                                    <br/>
                                                    <input type="checkbox"
                                                           name="c_indIdentNonStatus" <carlos:encode value='<%= props.getProperty("c_indIdentNonStatus", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.nonStatus"/>
                                                </td>
                                                <td width="25%" class="alignTop borderRight">
                                                    <input type="checkbox"
                                                           name="c_indIdentLiveOnReserve" <carlos:encode value='<%= props.getProperty("c_indIdentLiveOnReserve", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.liveOnReserve"/> <br/>
                                                    <input type="checkbox"
                                                           name="c_indIdentLiveOffReserve" <carlos:encode value='<%= props.getProperty("c_indIdentLiveOffReserve", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.liveOffReserve"/> <br/>
                                                    <input type="checkbox"
                                                           name="c_indIdentLiveOnOffReserve" <carlos:encode value='<%= props.getProperty("c_indIdentLiveOnOffReserve", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.liveOnOffReserve"/>
                                                </td>
                                                <td width="25%">
                                                    <div>
                                                        <div class="div-left">
                                                            <fmt:message key="form.formBCAR2020pg1.label.ethnicity"/>
                                                        </div>
                                                        <div class="div-right">
                                                            <select class="multiselect-dropdown" id="ethnicitySelectPicker"
                                                                    multiple data-header="Select Ethnicity" data-width="80px">
                                                                <option value="Indigenous/Aboriginal">
                                                                    <fmt:message key="form.formBCAR2020pg1.option.indigenousAboriginal"/>
                                                                </option>
                                                                <option value="European-Western"
                                                                        data-subtext="(eg. English, Italian)">
                                                                    <fmt:message key="form.formBCAR2020pg1.option.europeanWestern"/>
                                                                </option>
                                                                <option value="European-Eastern"
                                                                        data-subtext="(eg. Russian, Polish)">
                                                                    <fmt:message key="form.formBCAR2020pg1.option.europeanEastern"/>
                                                                </option>
                                                                <option value="Asian-East"
                                                                        data-subtext="(eg. Chinese, Japanese, Korean)">
                                                                    <fmt:message key="form.formBCAR2020pg1.option.asianEast"/>
                                                                </option>
                                                                <option value="Asian-South"
                                                                        data-subtext="(eg. Indian, Pakistani, Sri Lankan)">
                                                                    <fmt:message key="form.formBCAR2020pg1.option.asianSouth"/>
                                                                </option>
                                                                <option value="Asian-South East"
                                                                        data-subtext="(eg. Malaysian, Filipino)">
                                                                    <fmt:message key="form.formBCAR2020pg1.option.asianSouthEast"/>
                                                                </option>
                                                                <option value="Middle Eastern"
                                                                        data-subtext="(eg. Iranian, Lebanese)">Middle
                                                                    <fmt:message key="form.formBCAR2020pg1.option.middleEastern"/>
                                                                </option>
                                                                <option value="African"><fmt:message key="form.formBCAR2020pg1.option.african"/></option>
                                                                <option value="Caribbean"><fmt:message key="form.formBCAR2020pg1.option.caribbean"/></option>
                                                                <option value="Latin American"
                                                                        data-subtext="(eg. Argentinian, Chilean)">Latin
                                                                    <fmt:message key="form.formBCAR2020pg1.option.latinAmerican"/>
                                                                </option>
                                                                <option value="Do not know" data-subtext=""><fmt:message key="form.formBCAR2020pg1.option.doNotKnow"/>
                                                                </option>
                                                                <option value="Prefer not to answer" data-subtext="">
                                                                    <fmt:message key="form.formBCAR2020pg1.option.preferNotToAnswer"/>
                                                                </option>
                                                            </select>
                                                        </div>
                                                    </div>
                                                    <textarea name="t_ethnicity" style="width: 100%" size="30"
                                                              maxlength="200"
                                                              title="<%= UtilMisc.htmlEscape(props.getProperty("t_ethnicity", "")) %>"><%= UtilMisc.htmlEscape(props.getProperty("t_ethnicity", "")) %></textarea>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                        <td width="40%">
                            <!-- Addressograph/Label -->
                            <table border="0">
                                <tr>
                                    <td width="50%">
                                        <fmt:message key="form.formBCAR2020pg1.label.surname"/><br/>
                                        <input type="text" name="t_patientSurname" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientSurname", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td width="50%" colspan="2">
                                        <fmt:message key="form.formBCAR2020pg1.label.givenName"/><br/>
                                        <input type="text" name="t_patientGivenName" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientGivenName", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3">
                                        <fmt:message key="form.formBCAR2020pg1.label.address"/><br/>
                                        <input type="text" name="t_patientAddress" style="width: 100%" size="60"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientAddress", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="50%">
                                        <fmt:message key="form.formBCAR2020pg1.label.city"/><br/>
                                        <input type="text" name="t_patientCity" style="width: 100%" size="60"
                                               maxlength="50"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientCity", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.province"/><br/>
                                        <input type="text" name="t_patientProvince" style="width: 100%" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientProvince", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.postalCode"/><br/>
                                        <input type="text" name="t_patientPostal" style="width: 100%" size="60"
                                               maxlength="10"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientPostal", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="3">
                                        <table border="0">
                                            <tr>
                                                <td width="33%">
                                                    <fmt:message key="form.formBCAR2020pg1.label.homePhoneNumber"/>
                                                </td>
                                                <td width="33%">
                                                    <fmt:message key="form.formBCAR2020pg1.label.workPhoneNumber"/>
                                                </td>
                                                <td width="34%">
                                                    <fmt:message key="form.formBCAR2020pg1.label.cellPhoneNumber"/>
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
                            <!-- Partner Informations -->
                            <table border="1">
                                <tr>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.partnerName"/><br/>
                                        <input type="text" name="t_partnerName" style="width: 100%" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_partnerName", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        <fmt:message key="form.formBCAR2020pg1.label.occupation"/><br/>
                                        <input type="text" name="t_partnerOccupation" style="width: 100%" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_partnerOccupation", "")) %>"/>
                                    </td>
                                    <td width="35%">
                                        <fmt:message key="form.formBCAR2020pg1.label.biologicalFather"/><br/>
                                        <div class="flex-row">
                                            <div class="flex-column" style="padding-right:5px;flex-wrap: nowrap;">
                                                <input type="checkbox" name="biologicalFatherSameCheck"/><fmt:message key="form.formBCAR2020pg1.option.sameAsPartner"/>
                                            </div>
                                            <div class="flex-double-column">
                                                <input type="text" name="t_biologicalFatherName" size="30"
                                                       maxlength="80"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_biologicalFatherName", "")) %>"/>
                                            </div>
                                        </div>
                                    </td>
                                    <td width="5%">
                                        <fmt:message key="form.formBCAR2020pg1.label.age"/><br/>
                                        <input type="text" name="t_biologicalFatherAge" style="width: 100%" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_biologicalFatherAge", "")) %>"/>
                                    </td>
                                    <td width="10%">
                                        <fmt:message key="form.formBCAR2020pg1.label.ethnicity"/><br/>
                                        <input type="text" name="t_biologicalFatherEthnicity" style="width: 100%"
                                               size="60" maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_biologicalFatherEthnicity", "")) %>"/>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2">
                            <!-- Allergy Information -->
                            <table border="1">
                                <tr>
                                    <td width="25%" class="alignTop">
                                        <div class="flex-row">
                                            <div class="flex-row flex-justify">
                                                <div>
                                                    <span class="title">2.</span> <span class="allergy"><fmt:message key="form.formBCAR2020pg1.label.allergies"/></span>
                                                    <span class="sub-text">(incl. reaction)</span>
                                                </div>
                                                <div>
                                                    <input type="checkbox"
                                                           name="c_allergiesNone" <carlos:encode value='<%= props.getProperty("c_allergiesNone", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.none"/>
                                                </div>
                                            </div>
                                            <div class="flex-row">
                                                <input type="text" name="t_allergies" style="width: 100%" size="45"
                                                       maxlength="37" class="calcField" ondblclick="appendNotify(this);"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_allergies", "")) %>"/>
                                            </div>
                                        </div>
                                    </td>
                                    <td width="50%">
                                        <div class="flex-row flex-justify">
                                            <div>
                                                <fmt:message key="form.formBCAR2020pg1.label.medicationsOtc"/><br \>
                                                <input type="text" name="t_medications" size="45" maxlength="40"
                                                       class="calcField" ondblclick="appendNotify(this);"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_medications", "")) %>"/>
                                            </div>
                                            <div class="flex-stack">
                                                <div>
                                                    <input type="checkbox"
                                                           name="c_preconceptionFolicAcid" <carlos:encode value='<%= props.getProperty("c_preconceptionFolicAcid", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.label.preconceptionFolicAcid"/>
                                                </div>
                                                <div>
                                                    <input type="checkbox"
                                                           name="c_t1FolicAcid" <carlos:encode value='<%= props.getProperty("c_t1FolicAcid", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.label.t1FolicAcid"/>
                                                </div>
                                            </div>
                                        </div>

                                    </td>
                                    <td width="25%">
                                        <div class="flex-row">
                                            <div>
                                                <fmt:message key="form.formBCAR2020pg1.label.beliefsPractices"/> <span
                                                    class="sub-text">(eg. Jehovah's Witness)</span>
                                            </div>
                                            <div>
                                                <input type="text" name="t_beliefs" style="width: 100%;" size="60"
                                                       maxlength="150"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_beliefs", "")) %>"/>
                                            </div>
                                        </div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2">
                            <!-- Contraceptive Information -->
                            <table border="1">
                                <tr>
                                    <td width="14%">
                                        <span class="title">3.</span> <fmt:message key="form.formBCAR2020pg1.label.contraceptivesType"/><br/>
                                        <input type="text" name="t_contraceptiveType" class="text-style" size="30"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_contraceptiveType", "")) %>"/>
                                    </td>
                                    <td width="12%">
                                        <fmt:message key="form.formBCAR2020pg1.label.lastUsed"/> <span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" id="d_contraceptiveLastUsed"
                                                   name="d_contraceptiveLastUsed"
                                                   title="3. Contraceptives - Last Used - Date" size="11"
                                                   maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_contraceptiveLastUsed", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_contraceptiveLastUsed_cal">
                                        </div>
                                    </td>
                                    <td width="12%">
                                        <fmt:message key="form.formBCAR2020pg1.label.pregnancyPlanned"/><br/>
                                        <div class="div-center">
                                            <input type="checkbox"
                                                   name="c_pregnancyPlannedYes" <carlos:encode value='<%= props.getProperty("c_pregnancyPlannedYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="global.yes"/>
                                            <input type="checkbox"
                                                   name="c_pregnancyPlannedNo" <carlos:encode value='<%= props.getProperty("c_pregnancyPlannedNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="global.no"/>
                                        </div>
                                    </td>
                                    <td width="11%">
                                        <fmt:message key="form.formBCAR2020pg1.label.lmp"/><span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_LMP" id="d_LMP" title="LMP" size="10"
                                                   maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_LMP", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_LMP_cal">
                                        </div>
                                    </td>
                                    <td width="14%">
                                        <fmt:message key="form.formBCAR2020pg1.label.eddByLMP"/><span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_EDDByLMP" id="d_EDDByLMP" title="EDD by LMP"
                                                   class="calcField" onDblClick="calculateByLMP(this);" size="10"
                                                   maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_EDDByLMP", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_EDDByLMP_cal">
                                        </div>
                                    </td>
                                    <td width="12%">
                                        <fmt:message key="form.formBCAR2020pg1.label.datingUS"/><span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_datingUS" id="d_datingUS" title="Dating US"
                                                   size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_datingUS", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_datingUS_cal">
                                        </div>
                                    </td>
                                    <td width="12%">
                                        <fmt:message key="form.formBCAR2020pg1.label.gaByUS"/><span class="sub-text">(wks/days)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="t_GAByUS" id="t_GAByUS" class="calcField"
                                                   title="GA by US" size="7" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_GAByUS", "")) %>"
                                                   onDblClick="getGAByFieldDate('t_GAByUS', 'd_EDDByUS', 'd_datingUS')"/>
                                        </div>
                                    </td>
                                    <td width="13%">
                                        <fmt:message key="form.formBCAR2020pg1.label.eddByUS"/><span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_EDDByUS" id="d_EDDByUS" title="EDD by US"
                                                   size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_EDDByUS", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_EDDByUS_cal">
                                        </div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2">
                            <!-- Obstetrical History -->
                            <table border="0">
                                <tr>
                                    <td width="16%">
                                        <span class="title"><fmt:message key="form.formBCAR2020pg1.section.obstetricalHistory"/></span>
                                    </td>
                                    <td width="84%">
                                        <span class="title">G</span>ravida
                                        <input type="text" name="t_gravida" size="4" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_gravida", "")) %>"/>
                                        <span class="title">T</span>erm
                                        <input type="text" name="t_term" size="4" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_term", "")) %>"/>
                                        <span class="title">P</span>reterm
                                        <input type="text" name="t_preterm" size="4" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_preterm", "")) %>"/>
                                        <span class="title">A</span>bortus (Induced
                                        <input type="text" name="t_abortusInduced" size="4" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_abortusInduced", "")) %>"/>
                                        <fmt:message key="form.formBCAR2020pg1.label.spontaneous"/>
                                        <input type="text" name="t_abortusSpontaneous" size="4" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_abortusSpontaneous", "")) %>"/>
                                        )
                                        <span class="title">L</span>iving
                                        <input type="text" name="t_living" size="4" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_living", "")) %>"/>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2">
                            <table border="1">
                                <tr class="th-small">
                                    <th width="11%"><fmt:message key="form.formBCAR2020pg1.label.dateHeader"/><br/><span class="sub-text">(dd/mm/yyyy)</span></th>
                                    <th width="10%"><fmt:message key="form.formBCAR2020pg1.label.placeOfBirth"/></th>
                                    <th width="5%"><fmt:message key="form.formBCAR2020pg1.label.ga"/><br/><span class="sub-text">(wks/days)</span></th>
                                    <th width="8%"><fmt:message key="form.formBCAR2020pg1.label.durationOfLabour"/><br/><span class="sub-text">(hrs)</span></th>
                                    <th width="12%"><fmt:message key="form.formBCAR2020pg1.label.modeOfBirth"/></th>
                                    <th width="30%"><fmt:message key="form.formBCAR2020pg1.label.perinatalComplicationsComments"/></th>
                                    <th width="4%"><fmt:message key="form.formBCAR2020pg1.label.sex"/></th>
                                    <th width="6%"><fmt:message key="form.formBCAR2020pg1.label.birthWeight"/><br> <span class="sub-text">(g)</span></th>
                                    <th><fmt:message key="form.formBCAR2020pg1.label.breastfed"/><br/><span class="sub-text">(mos)</span></th>
                                    <th width="9%"><fmt:message key="form.formBCAR2020pg1.label.childsPresentHealth"/></th>
                                </tr>
                                <!-- Past Pregnancy Row 1-->
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_obHistoryDate1" id="d_obHistoryDate1"
                                                   title="<fmt:message key='form.formBCAR2020pg1.title.obstetricalHistoryDate'/>" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_obHistoryDate1", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_obHistoryDate1_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthPlace1" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthPlace1", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryGA1" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryGA1", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryLabourDuration1" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryLabourDuration1", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <input type="text" list="birthmode" name="t_obHistoryBirthMode1"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthMode1", "")) %>"/>
                                            <datalist id="birthmode">
                                            <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="SVD">SVD</option>
                                                <option value="CSec"><fmt:message key="form.formBCAR2020pg1.option.cSection"/></option>
                                                <option value="Vac"><fmt:message key="form.formBCAR2020pg1.option.vacuum"/></option>
                                                <option value="For"><fmt:message key="form.formBCAR2020pg1.option.forceps"/></option>
                                            </datalist>

                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryComments1" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryComments1", "")) %>"/>
                                    </td>
                                    <td>
                                        <select name="s_obHistorySex1" style="width: 100%">
                                            <option value=""></option>
                                            <option value="M" title="<fmt:message key='form.formBCAR2020pg1.option.male'/>">M</option>
                                            <option value="F" title="<fmt:message key='form.formBCAR2020pg1.option.female'/>">F</option>
                                            <option value="U" title="<fmt:message key='form.formBCAR2020pg1.option.undifferentiated'/>">U</option>
                                            <option value="O" title="<fmt:message key='form.formBCAR2020pg1.option.other'/>">O</option>
                                        </select>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthWeight1" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthWeight1", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBreastFed1" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBreastFed1", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <datalist id="childhealth">
                                                <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="A&W"><fmt:message key="form.formBCAR2020pg1.option.aw"/></option>
                                            </datalist>
                                            <input type="text" list="childhealth"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style" name="t_obHistoryPresentHealth1"
                                                   maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryPresentHealth1", "")) %>"/>
                                        </div>
                                    </td>
                                </tr>
                                <!-- Past Pregnancy Row 2-->
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_obHistoryDate2" id="d_obHistoryDate2"
                                                   title="<fmt:message key='form.formBCAR2020pg1.title.obstetricalHistoryDate'/>" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_obHistoryDate2", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_obHistoryDate2_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthPlace2" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthPlace2", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryGA2" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryGA2", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryLabourDuration2" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryLabourDuration2", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <datalist id="birthmode2">
                                                <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="SVD">SVD</option>
                                                <option value="C-section"><fmt:message key="form.formBCAR2020pg1.option.cSection"/></option>
                                                <option value="Vacuum">Vacuum</option>
                                                <option value="Forceps">Forceps</option>
                                                <option value="Vacuum and Forceps"><fmt:message key="form.formBCAR2020pg1.option.vacuumAndForceps"/></option>
                                                <option value="Forceps Trial and C-section"><fmt:message key="form.formBCAR2020pg1.option.forcepsTrialAndCSection"/></option>
                                                </option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryBirthMode2" list="birthmode2"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthMode2", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryComments2" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryComments2", "")) %>"/>
                                    </td>
                                    <td>
                                        <select name="s_obHistorySex2" style="width: 100%">
                                            <option value=""></option>
                                            <option value="M" title="<fmt:message key='form.formBCAR2020pg1.option.male'/>">M</option>
                                            <option value="F" title="<fmt:message key='form.formBCAR2020pg1.option.female'/>">F</option>
                                            <option value="U" title="<fmt:message key='form.formBCAR2020pg1.option.undifferentiated'/>">U</option>
                                            <option value="O" title="<fmt:message key='form.formBCAR2020pg1.option.other'/>">O</option>
                                        </select>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthWeight2" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthWeight2", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBreastFed2" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBreastFed2", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <datalist id="childhealth2">
                                                <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="A&W"><fmt:message key="form.formBCAR2020pg1.option.aw"/></option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryPresentHealth2" list="childhealth2"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryPresentHealth2", "")) %>"/>
                                        </div>
                                    </td>
                                </tr>
                                <!-- Past Pregnancy Row 3-->
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_obHistoryDate3" id="d_obHistoryDate3"
                                                   title="<fmt:message key='form.formBCAR2020pg1.title.obstetricalHistoryDate'/>" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_obHistoryDate3", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_obHistoryDate3_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthPlace3" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthPlace3", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryGA3" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryGA3", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryLabourDuration3" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryLabourDuration3", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <datalist id="birthmode3">
                                                <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="SVD">SVD</option>
                                                <option value="C-section"><fmt:message key="form.formBCAR2020pg1.option.cSection"/></option>
                                                <option value="Vacuum">Vacuum</option>
                                                <option value="Forceps">Forceps</option>
                                                <option value="Vacuum and Forceps"><fmt:message key="form.formBCAR2020pg1.option.vacuumAndForceps"/></option>
                                                <option value="Forceps Trial and C-section"><fmt:message key="form.formBCAR2020pg1.option.forcepsTrialAndCSection"/></option>
                                                </option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryBirthMode3" list="birthmode3"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthMode3", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryComments3" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryComments3", "")) %>"/>
                                    </td>
                                    <td>
                                        <select name="s_obHistorySex3" style="width: 100%">
                                            <option value=""></option>
                                            <option value="M" title="<fmt:message key='form.formBCAR2020pg1.option.male'/>">M</option>
                                            <option value="F" title="<fmt:message key='form.formBCAR2020pg1.option.female'/>">F</option>
                                            <option value="U" title="<fmt:message key='form.formBCAR2020pg1.option.undifferentiated'/>">U</option>
                                            <option value="O" title="<fmt:message key='form.formBCAR2020pg1.option.other'/>">O</option>
                                        </select>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthWeight3" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthWeight3", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBreastFed3" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBreastFed3", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <datalist id="childhealth3">
                                                <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="A&W"><fmt:message key="form.formBCAR2020pg1.option.aw"/></option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryPresentHealth3" list="childhealth3"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryPresentHealth3", "")) %>"/>
                                        </div>
                                    </td>
                                </tr>
                                <!-- Past Pregnancy Row 4-->
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_obHistoryDate4" id="d_obHistoryDate4"
                                                   title="<fmt:message key='form.formBCAR2020pg1.title.obstetricalHistoryDate'/>" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_obHistoryDate4", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_obHistoryDate4_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthPlace4" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthPlace4", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryGA4" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryGA4", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryLabourDuration4" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryLabourDuration4", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <datalist id="birthmode4">
                                                <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="SVD">SVD</option>
                                                <option value="C-section"><fmt:message key="form.formBCAR2020pg1.option.cSection"/></option>
                                                <option value="Vacuum">Vacuum</option>
                                                <option value="Forceps">Forceps</option>
                                                <option value="Vacuum and Forceps"><fmt:message key="form.formBCAR2020pg1.option.vacuumAndForceps"/></option>
                                                <option value="Forceps Trial and C-section"><fmt:message key="form.formBCAR2020pg1.option.forcepsTrialAndCSection"/></option>
                                                </option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryBirthMode4" list="birthmode4"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthMode4", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryComments4" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryComments4", "")) %>"/>
                                    </td>
                                    <td>
                                        <select name="s_obHistorySex4" style="width: 100%">
                                            <option value=""></option>
                                            <option value="M" title="<fmt:message key='form.formBCAR2020pg1.option.male'/>">M</option>
                                            <option value="F" title="<fmt:message key='form.formBCAR2020pg1.option.female'/>">F</option>
                                            <option value="U" title="<fmt:message key='form.formBCAR2020pg1.option.undifferentiated'/>">U</option>
                                            <option value="O" title="<fmt:message key='form.formBCAR2020pg1.option.other'/>">O</option>
                                        </select>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthWeight4" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthWeight4", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBreastFed4" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBreastFed4", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <datalist id="childhealth4">
                                                <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="A&W"><fmt:message key="form.formBCAR2020pg1.option.aw"/></option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryPresentHealth4" list="childhealth4"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryPresentHealth4", "")) %>"/>
                                        </div>
                                    </td>
                                </tr>
                                <!-- Past Pregnancy Row 5-->
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_obHistoryDate5" id="d_obHistoryDate5"
                                                   title="<fmt:message key='form.formBCAR2020pg1.title.obstetricalHistoryDate'/>" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_obHistoryDate5", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_obHistoryDate5_cal">
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthPlace5" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthPlace5", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryGA5" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryGA5", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryLabourDuration5" class="text-style"
                                               size="60" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryLabourDuration5", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <datalist id="birthmode5">
                                                <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="SVD">SVD</option>
                                                <option value="C-section"><fmt:message key="form.formBCAR2020pg1.option.cSection"/></option>
                                                <option value="Vacuum">Vacuum</option>
                                                <option value="Forceps">Forceps</option>
                                                <option value="Vacuum and Forceps"><fmt:message key="form.formBCAR2020pg1.option.vacuumAndForceps"/></option>
                                                <option value="Forceps Trial and C-section"><fmt:message key="form.formBCAR2020pg1.option.forcepsTrialAndCSection"/></option>
                                                </option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryBirthMode5" list="birthmode5"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthMode5", "")) %>"/>
                                        </div>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryComments5" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryComments5", "")) %>"/>
                                    </td>
                                    <td>
                                        <select name="s_obHistorySex5" style="width: 100%">
                                            <option value=""></option>
                                            <option value="M" title="<fmt:message key='form.formBCAR2020pg1.option.male'/>">M</option>
                                            <option value="F" title="<fmt:message key='form.formBCAR2020pg1.option.female'/>">F</option>
                                            <option value="U" title="<fmt:message key='form.formBCAR2020pg1.option.undifferentiated'/>">U</option>
                                            <option value="O" title="<fmt:message key='form.formBCAR2020pg1.option.other'/>">O</option>
                                        </select>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBirthWeight5" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthWeight5", "")) %>"/>
                                    </td>
                                    <td>
                                        <input type="text" name="t_obHistoryBreastFed5" class="text-style" size="60"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBreastFed5", "")) %>"/>
                                    </td>
                                    <td>
                                        <div style="width:auto;">
                                            <datalist id="childhealth5">
                                                <option value=""><fmt:message key="form.formBCAR2020pg1.option.other"/></option>
                                                <option value="A&W"><fmt:message key="form.formBCAR2020pg1.option.aw"/></option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryPresentHealth5" list="childhealth5"
                                                   placeholder="<fmt:message key='form.formBCAR2020pg1.placeholder.doubleClickForSelection'/>"
                                                   class="data-list-input text-style" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryPresentHealth5", "")) %>"/>
                                        </div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="2">
                            <table border="1">
                                <tr height="320px">
                                    <td width="33%">
                                        <!-- Present Pregnancy -->
                                        <span class="title"><fmt:message key="form.formBCAR2020pg1.section.presentPregnancy"/></span><br/>
                                        <table border="0" class="noColumn">
                                            <tr>
                                                <th width="8%"><span class="title"><a href="#" id="presentPregnancyNo"
                                                                                      class="noLink"
                                                                                      title="<fmt:message key='form.formBCAR2020pg1.title.setAllPresentPregnancyToNo'/>"><fmt:message key="global.no"/></a></span>
                                                </th>
                                                <th width="8%"><span class="title"><fmt:message key="global.yes"/></span></th>
                                                <th width="84%"><span class="sub-text">(specify)</span></th>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_presentPregnancyARTNo" <carlos:encode value='<%= props.getProperty("c_presentPregnancyARTNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_presentPregnancyARTYes" <carlos:encode value='<%= props.getProperty("c_presentPregnancyARTYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <fmt:message key="form.formBCAR2020pg1.label.art"/> <span class="sub-text">(<fmt:message key="form.formBCAR2020pg1.label.selectOneOnly"/>)</span>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td><input type="checkbox"
                                                           name="c_presentPregnancyARTOvaStim" <carlos:encode value='<%= props.getProperty("c_presentPregnancyARTOvaStim", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.ovarStimOnly"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td><input type="checkbox"
                                                           name="c_presentPregnancyARTIUIOnly" <carlos:encode value='<%= props.getProperty("c_presentPregnancyARTIUIOnly", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.iuiOnly"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td><input type="checkbox"
                                                           name="c_presentPregnancyARTOvaStimIUI" <carlos:encode value='<%= props.getProperty("c_presentPregnancyARTOvaStimIUI", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> /><fmt:message key="form.formBCAR2020pg1.option.ovarStimAndIui"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td>
                                                    <div class="divFlex">
                                                        <input type="checkbox"
                                                               name="c_presentPregnancyARTIVF" <carlos:encode value='<%= props.getProperty("c_presentPregnancyARTIVF", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                        <fmt:message key="form.formBCAR2020pg1.option.ivf"/> <span class="sub-text">(<fmt:message key="form.formBCAR2020pg1.label.embryosTransferred"/>)</span>
                                                        <input type="text" name="t_presentPregnancyARTIVFDetails"
                                                               size="10" maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_presentPregnancyARTIVFDetails", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td>
                                                    <div class="divFlex">
                                                        <input type="checkbox"
                                                               name="c_presentPregnancyARTICSI" <carlos:encode value='<%= props.getProperty("c_presentPregnancyARTICSI", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                        <fmt:message key="form.formBCAR2020pg1.option.icsi"/> <span class="sub-text">(<fmt:message key="form.formBCAR2020pg1.label.embryosTransferred"/>)</span>
                                                        <input type="text" name="t_presentPregnancyARTICSIDetails"
                                                               size="10" maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_presentPregnancyARTICSIDetails", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td>
                                                    <div class="divFlex">
                                                        <input type="checkbox"
                                                               name="c_presentPregnancyARTOther" <carlos:encode value='<%= props.getProperty("c_presentPregnancyARTOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                        <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                        <input type="text" name="t_presentPregnancyARTOtherDetails"
                                                               size="20" maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_presentPregnancyARTOtherDetails", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyBleeding", "form.formBCAR2020pg1.toggle.presentPregnancyBleeding", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyNausea", "form.formBCAR2020pg1.toggle.presentPregnancyNausea", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyTravel", "form.formBCAR2020pg1.toggle.presentPregnancyTravel", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyInfection", "form.formBCAR2020pg1.toggle.presentPregnancyInfection", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyOther", "form.formBCAR2020pg1.option.other", request.getLocale())%>
                                        </table>
                                    </td>
                                    <td width="33%" rowspan="2">
                                        <!-- Medical History -->
                                        <span class="title"><fmt:message key="form.formBCAR2020pg1.section.medicalHistory"/></span><br/>
                                        <table border="0" class="noColumn">
                                            <tr>
                                                <th width="8%"><span class="title"><a href="#" id="medicalHistoryNo"
                                                                                      class="noLink"
                                                                                      title="<fmt:message key='form.formBCAR2020pg1.title.setAllMedicalHistoryToNo'/>"><fmt:message key="global.no"/></a></span>
                                                </th>
                                                <th width="8%"><span class="title"><fmt:message key="global.yes"/></span></th>
                                                <th width="84%"><span class="sub-text">(specify)</span></th>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistorySurgery", "form.formBCAR2020pg1.toggle.medicalHistorySurgery", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryAnestheticComplications", "form.formBCAR2020pg1.toggle.anestheticComplications", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryNeuro", "form.formBCAR2020pg1.toggle.neuro", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryResp", "form.formBCAR2020pg1.toggle.resp", request.getLocale())%>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryCVNo" <carlos:encode value='<%= props.getProperty("c_medicalHistoryCVNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryCVYes" <carlos:encode value='<%= props.getProperty("c_medicalHistoryCVYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    CV:
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryCVHypert" <carlos:encode value='<%= props.getProperty("c_medicalHistoryCVHypert", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />Hypertension
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryCVPrevHypert" <carlos:encode value='<%= props.getProperty("c_medicalHistoryCVPrevHypert", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />Prev.
                                                    hypert. in preg.
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td style="padding-left:25px;">
                                                    <div class="divFlex">
                                                        <input type="checkbox"
                                                               name="c_medicalHistoryCVOther" <carlos:encode value='<%= props.getProperty("c_medicalHistoryCVOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                    <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                        <input type="text" name="t_medicalHistoryCVOtherDetails"
                                                               size="30" maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_medicalHistoryCVOtherDetails", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryAbdo", "form.formBCAR2020pg1.toggle.abdoGI", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryGyne", "form.formBCAR2020pg1.toggle.gyneGU", request.getLocale())%>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryHematologyNo" <carlos:encode value='<%= props.getProperty("c_medicalHistoryHematologyNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryHematologyYes" <carlos:encode value='<%= props.getProperty("c_medicalHistoryHematologyYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <fmt:message key="form.formBCAR2020pg1.toggle.hematology"/>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td colspan="2">
                                                    <div class="divFlex">
                                                        <input type="text" name="t_medicalHistoryHematologyDetails"
                                                               size="30" maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_medicalHistoryHematologyDetails", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryEndocrineNo" <carlos:encode value='<%= props.getProperty("c_medicalHistoryEndocrineNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryEndocrineYes" <carlos:encode value='<%= props.getProperty("c_medicalHistoryEndocrineYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <div class="flex-container">
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.endocrineHeading"/>
                                                            </div>
                                                            <div class="flex-quad-column">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryEndocrineT1DM" <carlos:encode value='<%= props.getProperty("c_medicalHistoryEndocrineT1DM", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                T1DM
                                                            </div>
                                                            <div class="flex-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryEndocrineT2DM" <carlos:encode value='<%= props.getProperty("c_medicalHistoryEndocrineT2DM", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                T2DM
                                                            </div>
                                                            <div class="flex-double-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryEndocrinePrevGDM" <carlos:encode value='<%= props.getProperty("c_medicalHistoryEndocrinePrevGDM", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                Prev. GDM
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title"></div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryEndocrineThyroid" <carlos:encode value='<%= props.getProperty("c_medicalHistoryEndocrineThyroid", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                Thyroid
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title"></div>
                                                            <div class="flex-quad-column">
                                                                <div class="divFlex">
                                                                    <input type="checkbox"
                                                                           name="c_medicalHistoryEndocrineOther" <carlos:encode value='<%= props.getProperty("c_medicalHistoryEndocrineOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                        <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                                    <input type="text"
                                                                           name="t_medicalHistoryEndocrineOtherDetails"
                                                                           size="20" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_medicalHistoryEndocrineOtherDetails", "")) %>"/>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryMentalNo" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMentalNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryMentalYes" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMentalYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <div class="flex-container">
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.mentalHealthHeading"/>
                                                            </div>
                                                            <div class="flex-quad-column">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHAnxiety" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMHAnxiety", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.anxiety"/>
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title"></div>
                                                            <div class="flex-double-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHDepression" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMHDepression", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.depression"/>
                                                            </div>
                                                            <div class="flex-double-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHPrevPPD" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMHPrevPPD", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.prevPpd"/>
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHBipolar" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMHBipolar", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.bipolar"/>
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHEatingDisorder" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMHEatingDisorder", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.eatingDisorder"/>
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHSubstanceUse" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMHSubstanceUse", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.substanceUseDisorderHeading"/>
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title-alt">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHMethadone" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMHMethadone", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.methadone"/>
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title-alt">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHSuboxone" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMHSuboxone", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.suboxone"/>
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <div class="divFlex">
                                                                    <input type="checkbox"
                                                                           name="c_medicalHistoryMHOther" <carlos:encode value='<%= props.getProperty("c_medicalHistoryMHOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                    <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                                    <input type="text"
                                                                           name="t_medicalHistoryMHOtherDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_medicalHistoryMHOtherDetails", "")) %>"/>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryInfectiousNo" <carlos:encode value='<%= props.getProperty("c_medicalHistoryInfectiousNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryInfectiousYes" <carlos:encode value='<%= props.getProperty("c_medicalHistoryInfectiousYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <div class="flex-container">
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.infectiousDiseasesHeading"/>
                                                            </div>
                                                            <div class="flex-quad-column">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryIDVaricella" <carlos:encode value='<%= props.getProperty("c_medicalHistoryIDVaricella", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.varicella"/>
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryIDHSV" <carlos:encode value='<%= props.getProperty("c_medicalHistoryIDHSV", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.hsv"/>
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <div class="divFlex">
                                                                    <input type="checkbox"
                                                                           name="c_medicalHistoryIDOther" <carlos:encode value='<%= props.getProperty("c_medicalHistoryIDOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                    <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                                    <input type="text"
                                                                           name="t_medicalHistoryIDOtherDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_medicalHistoryIDOtherDetails", "")) %>"/>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryImmunizationsNo" <carlos:encode value='<%= props.getProperty("c_medicalHistoryImmunizationsNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryImmunizationsYes" <carlos:encode value='<%= props.getProperty("c_medicalHistoryImmunizationsYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <div class="flex-container">
                                                        <div class="flex-row">
                                                            <div class=flex-column-title">
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.immunizationsHeading"/>
                                                            </div>
                                                            <div class="flex-quad-column">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryImmunizationsFlu" <carlos:encode value='<%= props.getProperty("c_medicalHistoryImmunizationsFlu", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.flu"/>
                                                                <span class="sub-text">(dd/mm/yyyy)</span>
                                                                <input type="text"
                                                                       id="d_medicalHistoryImmunizationsFluDate"
                                                                       name="d_medicalHistoryImmunizationsFluDate"
                                                                       title="Medical History - Immunizations Flu - Date"
                                                                       size="10" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("d_medicalHistoryImmunizationsFluDate", "")) %>"/>
                                                                <img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                     id="d_medicalHistoryImmunizationsFluDate_cal">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryImmunizationsTDAP" <carlos:encode value='<%= props.getProperty("c_medicalHistoryImmunizationsTDAP", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.toggle.tdap"/>
                                                                <span class="sub-text">(dd/mm/yyyy)</span>
                                                                <input type="text"
                                                                       id="d_medicalHistoryImmunizationsTDAPDate"
                                                                       name="d_medicalHistoryImmunizationsTDAPDate"
                                                                       title="Medical History - Immunizations TDAP - Date"
                                                                       size="8" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("d_medicalHistoryImmunizationsFluDate", "")) %>"/>
                                                                <img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                     id="d_medicalHistoryImmunizationsTDAPDate_cal">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <div class="divFlex">
                                                                    <input type="checkbox"
                                                                           name="c_medicalHistoryImmunizationsOther" <carlos:encode value='<%= props.getProperty("c_medicalHistoryImmunizationsOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                    <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                                    <input type="text"
                                                                           name="t_medicalHistoryImmunizationsOtherDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_medicalHistoryImmunizationsOtherDetails", "")) %>"/>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryOther", "form.formBCAR2020pg1.option.other", request.getLocale())%>
                                        </table>
                                    </td>
                                    <td width="33%" rowspan="2">
                                        <table style="border-collapse: collapse;">
                                            <tr>
                                                <td>
                                                    <!-- Lifestyle/Social Concerns -->
                                                    <span class="title"><fmt:message key="form.formBCAR2020pg1.section.lifestyle"/></span><br/>
                                                    <table border="0" style="border-bottom:black thin solid;"
                                                           class="noColumn">
                                                        <tr>
                                                            <th width="8%"><span class="title"><a href="#"
                                                                                                  id="lifestyleSocialNo"
                                                                                                  class="noLink"
                                                                                                  title="<fmt:message key='form.formBCAR2020pg1.title.setAllLifestyleToNo'/>"><fmt:message key="global.no"/></a></span>
                                                            </th>
                                                            <th width="8%"><span class="title"><fmt:message key="global.yes"/></span></th>
                                                            <th width="84%"><span class="sub-text">(specify)</span></th>
                                                        </tr>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleDiet", "form.formBCAR2020pg1.toggle.lifestyleDiet", request.getLocale())%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleExercise", "form.formBCAR2020pg1.toggle.lifestyleExercise", request.getLocale())%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleFinancial", "form.formBCAR2020pg1.toggle.lifestyleFinancial", request.getLocale())%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleHousing", "form.formBCAR2020pg1.toggle.lifestyleHousing", request.getLocale())%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleTransportation", "form.formBCAR2020pg1.toggle.lifestyleTransportation", request.getLocale())%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleSafety", "form.formBCAR2020pg1.toggle.lifestyleSafety", request.getLocale())%>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_lifestyleGenderViolNo" <carlos:encode value='<%= props.getProperty("c_lifestyleGenderViolNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_lifestyleGenderViolYes" <carlos:encode value='<%= props.getProperty("c_lifestyleGenderViolYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td>
                                                                <div class="flex-container">
                                                                    <div class="flex-row">
                                                                        <div class="flex-column-title-alt">
                                                                            <fmt:message key="form.formBCAR2020pg1.section.genderBasedViolence"/>
                                                                        </div>
                                                                    </div>
                                                                    <div class="flex-row">
                                                                        <div class="flex-column-title">
                                                                        </div>
                                                                        <div class="flex-double-column">
                                                                            <input type="checkbox"
                                                                                   name="c_lifestyleGenderViolPartner" <carlos:encode value='<%= props.getProperty("c_lifestyleGenderViolPartner", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                            <fmt:message key="form.formBCAR2020pg1.option.partner"/>
                                                                        </div>
                                                                        <div class="flex-double-column">
                                                                            <input type="checkbox"
                                                                                   name="c_lifestyleGenderViolNonPartner" <carlos:encode value='<%= props.getProperty("c_lifestyleGenderViolNonPartner", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                            <fmt:message key="form.formBCAR2020pg1.option.nonPartner"/>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleRelationships", "form.formBCAR2020pg1.toggle.relationshipsSupport", request.getLocale())%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleOther", "form.formBCAR2020pg1.option.other", request.getLocale())%>

                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <!-- Substance Use -->
                                                    <table border="0" style="border-bottom:whitesmoke thin solid;">
                                                        <tr>
                                                            <th width="38%"><span class="title"><fmt:message key="form.formBCAR2020pg1.section.substanceUse"/></span>
                                                            </th>
                                                            <th width="33%"><span
                                                                    class="sub-title"><fmt:message key="form.formBCAR2020pg1.section.substanceUse3Mo"/></span></th>
                                                            <th width="29%"><span class="sub-title"><fmt:message key="form.formBCAR2020pg1.section.substanceUseDuring"/></span>
                                                            </th>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <span class="sub-title"><fmt:message key="form.formBCAR2020pg1.label.alcohol"/></span>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoAlcoholNo" <carlos:encode value='<%= props.getProperty("c_substance3MoAlcoholNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoAlcoholYes" <carlos:encode value='<%= props.getProperty("c_substance3MoAlcoholYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substancePregAlcoholNo" <carlos:encode value='<%= props.getProperty("c_substancePregAlcoholNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregAlcoholYes" <carlos:encode value='<%= props.getProperty("c_substancePregAlcoholYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <fmt:message key="form.formBCAR2020pg1.label.drinksPerWeek"/>
                                                            </td>
                                                            <td>
                                                                <input type="text" name="t_substance3MoAlcoholNumDrinks"
                                                                       size="10" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_substance3MoAlcoholNumDrinks", "")) %>"/>
                                                            </td>
                                                            <td>
                                                                <input type="text"
                                                                       name="t_substancePregAlcoholNumDrinks" size="10"
                                                                       maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_substancePregAlcoholNumDrinks", "")) %>"/>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <fmt:message key="form.formBCAR2020pg1.label.fourPlusDrinks"/>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoAlcohol4DrinksNo" <carlos:encode value='<%= props.getProperty("c_substance3MoAlcohol4DrinksNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoAlcohol4DrinksYes" <carlos:encode value='<%= props.getProperty("c_substance3MoAlcohol4DrinksYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substancePregAlcohol4DrinksNo" <carlos:encode value='<%= props.getProperty("c_substancePregAlcohol4DrinksNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregAlcohol4DrinksYes" <carlos:encode value='<%= props.getProperty("c_substancePregAlcohol4DrinksYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td colspan="3">
                                                                <fmt:message key="form.formBCAR2020pg1.label.quitAlcohol"/>
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitAlcoholNo" <carlos:encode value='<%= props.getProperty("c_substanceQuitAlcoholNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitAlcoholYes" <carlos:encode value='<%= props.getProperty("c_substanceQuitAlcoholYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                                <span class="sub-text">(dd/mm/yyyy)</span>
                                                                <input type="text" id="d_substanceQuitAlcoholDate"
                                                                       name="d_substanceQuitAlcoholDate"
                                                                       title="Substance Use - Quit Alchohol - Date"
                                                                       size="10" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("d_substanceQuitAlcoholDate", "")) %>"/>
                                                                <img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                     id="d_substanceQuitAlcoholDate_cal">
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <!-- Tobacco -->
                                                    <table border="0" style="border-bottom:whitesmoke thin solid;"
                                                           style="border-bottom:whitesmoke thin solid;">
                                                        <tr>
                                                            <td width="38%">
                                                                <span class="sub-title"><fmt:message key="form.formBCAR2020pg1.label.tobacco"/></span>
                                                            </td>
                                                            <td width="33%">
                                                                <input type="checkbox"
                                                                       name="c_substance3MoTobaccoNo" <carlos:encode value='<%= props.getProperty("c_substance3MoTobaccoNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoTobaccoYes" <carlos:encode value='<%= props.getProperty("c_substance3MoTobaccoYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                            <td width="29%">
                                                                <input type="checkbox"
                                                                       name="c_substancePregTobaccoNo" <carlos:encode value='<%= props.getProperty("c_substancePregTobaccoNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregTobaccoYes" <carlos:encode value='<%= props.getProperty("c_substancePregTobaccoYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                # Cigarette per week
                                                            </td>
                                                            <td>
                                                                <input type="text" name="t_substance3MoTobaccoNumCig"
                                                                       size="10" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_substance3MoTobaccoNumCig", "")) %>"/>
                                                            </td>
                                                            <td>
                                                                <input type="text" name="t_substancePregTobaccoNumCig"
                                                                       size="10" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_substancePregTobaccoNumCig", "")) %>"/>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                Exposed to 2nd-hand smoke
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoTobaccoSecHndSmkNo" <carlos:encode value='<%= props.getProperty("c_substance3MoTobaccoSecHndSmkNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoTobaccoSecHndSmkYes" <carlos:encode value='<%= props.getProperty("c_substance3MoTobaccoSecHndSmkYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substancePregTobaccoSecHndSmkNo" <carlos:encode value='<%= props.getProperty("c_substancePregTobaccoSecHndSmkNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregTobaccoSecHndSmkYes" <carlos:encode value='<%= props.getProperty("c_substancePregTobaccoSecHndSmkYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td colspan="3">
                                                                <fmt:message key="form.formBCAR2020pg1.label.quitTobacco"/>
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitTobaccoNo" <carlos:encode value='<%= props.getProperty("c_substanceQuitTobaccoNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitTobaccoYes" <carlos:encode value='<%= props.getProperty("c_substanceQuitTobaccoYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                                <span class="sub-text">(dd/mm/yyyy)</span>
                                                                <input type="text" id="d_substanceQuitTobaccoDate"
                                                                       name="d_substanceQuitTobaccoDate"
                                                                       title="<fmt:message key='form.formBCAR2020pg1.title.substanceUseQuitTobaccoDate'/>"
                                                                       size="9" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("d_substanceQuitTobaccoDate", "")) %>"/>
                                                                <img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                     id="d_substanceQuitTobaccoDate_cal">
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <!-- Cannabis -->
                                                    <table border="0" style="border-bottom:whitesmoke thin solid;">
                                                        <tr>
                                                            <td width="38%">
                                                                <span class="sub-title"><fmt:message key="form.formBCAR2020pg1.label.cannabis"/></span>
                                                            </td>
                                                            <td width="33%">
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisNo" <carlos:encode value='<%= props.getProperty("c_substance3MoCannabisNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisYes" <carlos:encode value='<%= props.getProperty("c_substance3MoCannabisYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                            <td width="29%">
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisNo" <carlos:encode value='<%= props.getProperty("c_substancePregCannabisNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisYes" <carlos:encode value='<%= props.getProperty("c_substancePregCannabisYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td width="38%">
                                                                <fmt:message key="form.formBCAR2020pg1.label.cbdProductsOnly"/>
                                                            </td>
                                                            <td width="33%">
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisCBDOnlyNo" <carlos:encode value='<%= props.getProperty("c_substance3MoCannabisCBDOnlyNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisCBDOnlyYes" <carlos:encode value='<%= props.getProperty("c_substance3MoCannabisCBDOnlyYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                            <td width="29%">
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisCBDOnlyNo" <carlos:encode value='<%= props.getProperty("c_substancePregCannabisCBDOnlyNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisCBDOnlyYes" <carlos:encode value='<%= props.getProperty("c_substancePregCannabisCBDOnlyYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <fmt:message key="form.formBCAR2020pg1.label.usesPerDay"/>
                                                            </td>
                                                            <td>
                                                                <input type="text" name="t_substance3MoCannabisNumUsed"
                                                                       size="10" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_substance3MoCannabisNumUsed", "")) %>"/>
                                                            </td>
                                                            <td>
                                                                <input type="text" name="t_substancePregCannabisNumUsed"
                                                                       size="10" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_substancePregCannabisNumUsed", "")) %>"/>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <fmt:message key="form.formBCAR2020pg1.label.primaryRoute"/><br/>
                                                                <span class="sub-text">(<fmt:message key="form.formBCAR2020pg1.label.selectOneOnly"/>)</span>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisSmoke" <carlos:encode value='<%= props.getProperty("c_substance3MoCannabisSmoke", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                Smoke
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisVapo" <carlos:encode value='<%= props.getProperty("c_substance3MoCannabisVapo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                Vaporize
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisEdi" <carlos:encode value='<%= props.getProperty("c_substance3MoCannabisEdi", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                Edible/oral
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisOther" <carlos:encode value='<%= props.getProperty("c_substance3MoCannabisOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisSmoke" <carlos:encode value='<%= props.getProperty("c_substancePregCannabisSmoke", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                Smoke
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisVapo" <carlos:encode value='<%= props.getProperty("c_substancePregCannabisVapo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                Vaporize
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisEdi" <carlos:encode value='<%= props.getProperty("c_substancePregCannabisEdi", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                Edible/oral
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisOther" <carlos:encode value='<%= props.getProperty("c_substancePregCannabisOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td colspan="3">
                                                                <fmt:message key="form.formBCAR2020pg1.label.quitCannabis"/>
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitCannabisNo" <carlos:encode value='<%= props.getProperty("c_substanceQuitCannabisNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitCannabisYes" <carlos:encode value='<%= props.getProperty("c_substanceQuitCannabisYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                                <span class="sub-text">(dd/mm/yyyy)</span>
                                                                <input type="text" id="d_substanceQuitCannabisDate"
                                                                       name="d_substanceQuitCannabisDate"
                                                                       title="<fmt:message key='form.formBCAR2020pg1.title.substanceUseQuitCannabisDate'/>"
                                                                       size="8" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("d_substanceQuitCannabisDate", "")) %>"/>
                                                                <img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                     id="d_substanceQuitCannabisDate_cal">
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <!-- Others During Preg -->
                                                    <table border="0">
                                                        <tr>
                                                            <td>
                                                                <span class="sub-title"><fmt:message key="form.formBCAR2020pg1.label.otherDuringPreg"/></span>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersNo" <carlos:encode value='<%= props.getProperty("c_substanceOthersNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.no"/>
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersYes" <carlos:encode value='<%= props.getProperty("c_substanceOthersYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="global.yes"/>:
                                                                <span class="sub-text">(check all that apply)</span>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersCocaine" <carlos:encode value='<%= props.getProperty("c_substanceOthersCocaine", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.label.cocaine"/>
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersOpioids" <carlos:encode value='<%= props.getProperty("c_substanceOthersOpioids", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.label.opioids"/>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersMeth" <carlos:encode value='<%= props.getProperty("c_substanceOthersMeth", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.label.methamphetamines"/>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td colspan="2">
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersIVDrugs" <carlos:encode value='<%= props.getProperty("c_substanceOthersIVDrugs", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.label.ivDrugs"/>
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersPrescDrugs" <carlos:encode value='<%= props.getProperty("c_substanceOthersPrescDrugs", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                <fmt:message key="form.formBCAR2020pg1.label.prescriptionDrugs"/>
                                                                <div class="divFlex">
                                                                    <input type="checkbox"
                                                                           name="c_substanceOthersOther" <carlos:encode value='<%= props.getProperty("c_substanceOthersOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                    <fmt:message key="form.formBCAR2020pg1.label.otherPlural"/>
                                                                    <input type="text"
                                                                           name="t_substanceOthersOtherDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_substanceOthersOtherDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td>
                                        <!-- Family History -->
                                        <span class="title"><fmt:message key="form.formBCAR2020pg1.section.familyHistory"/></span><br/>
                                        <table border="0" class="noColumn">
                                            <tr>
                                                <th width="8%"><span class="title"><a href="#" id="familyHistoryNo"
                                                                                      class="noLink"
                                                                                      title="<fmt:message key='form.formBCAR2020pg1.title.setAllFamilyHistoryToNo'/>"><fmt:message key="global.no"/></a></span>
                                                </th>
                                                <th width="8%"><span class="title"><fmt:message key="global.yes"/></span></th>
                                                <th width="84%"><span class="sub-text">(specify)</span></th>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryAnestheticComp", "form.formBCAR2020pg1.toggle.anestheticComplications", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryHypertension", "form.formBCAR2020pg1.toggle.hypertension", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryThromboembolic", "form.formBCAR2020pg1.toggle.thromboembolic", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryDiabetes", "form.formBCAR2020pg1.toggle.diabetes", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryMentalHealth", "form.formBCAR2020pg1.toggle.mentalHealth", request.getLocale())%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistorySubstanceUse", "form.formBCAR2020pg1.toggle.substanceUseDisorder", request.getLocale())%>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_familyHistoryInheritedConditionsNo" <carlos:encode value='<%= props.getProperty("c_familyHistoryInheritedConditionsNo", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_familyHistoryInheritedConditionsYes" <carlos:encode value='<%= props.getProperty("c_familyHistoryInheritedConditionsYes", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                </td>
                                                <td>
                                                    <fmt:message key="form.formBCAR2020pg1.toggle.inheritedConditions"/><br/>
                                                    (eg. Tay-Sachs, Sickle Cell, Congenital Heart Defect, Cystic
                                                    Fibrosis)
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td>
                                                    <div class="divFlex">
                                                        <span class="sub-text"><fmt:message key="form.formBCAR2020pg1.label.mother"/></span>
                                                        <input type="text"
                                                               name="t_familyHistoryInheritedConditionsMother" size="10"
                                                               maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_familyHistoryInheritedConditionsMother", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td>
                                                    <div class="divFlex">
                                                        <span class="sub-text"><fmt:message key="form.formBCAR2020pg1.label.biologicalFatherDonor"/></span>
                                                        <input type="text"
                                                               name="t_familyHistoryInheritedConditionsFather" size="10"
                                                               maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_familyHistoryInheritedConditionsFather", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryOther", "form.formBCAR2020pg1.option.other", request.getLocale())%>
                                        </table>
                                    </td>

                                </tr>
                                <tr>
                                    <td colspan="2">

                                        <!-- Initial Physical Examination -->
                                        <span class="title"><fmt:message key="form.formBCAR2020pg1.section.initialExam"/></span>
                                        <fmt:message key="form.formBCAR2020pg1.label.date"/>
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                        <input type="text" id="d_initialExamDate" name="d_initialExamDate"
                                               title="Initial Physical Examination - Date" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_initialExamDate", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_initialExamDate_cal">
                                        <fmt:message key="form.formBCAR2020pg1.label.completedBy"/>
                                        <span class="sub-text">(name)</span>
                                        <input type="text" name="t_initialExamCompletedBy" size="18" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamCompletedBy", "")) %>"/>
                                        <table style="border:whitesmoke thin solid;">
                                            <tr>
                                                <td width="10%">
                                                    <div class="divFlex">
                                                        BP
                                                        <input type="text" id="t_initialExamBP" name="t_initialExamBP"
                                                               size="6" maxlength="150" class="calcField"
                                                               ondblclick="displayDemographicMeasurements('t_initialExamBP', 'BP', '<%=demoNo%>', '<%= UtilMisc.htmlEscape(props.getProperty("t_patientDOB", "")) %>', '')"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamBP", "0")) %>"/>
                                                    </div>
                                                </td>
                                                <td width="20%">
                                                    <div class="divFlex">
                                                        HR
                                                        <span class="sub-text">(per min)</span>
                                                        <input type="text" id="t_initialExamHR" name="t_initialExamHR"
                                                               size="6" maxlength="150" class="calcField"
                                                               ondblclick="displayDemographicMeasurements('t_initialExamHR', 'HR', '<%=demoNo%>', '<%= UtilMisc.htmlEscape(props.getProperty("t_patientDOB", "")) %>', '')"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamHR", "0")) %>"/>
                                                    </div>
                                                </td>
                                                <td width="10%">
                                                    <div class="divFlex">
                                                        Ht
                                                        <span class="sub-text">(cm)</span>
                                                        <input type="text" id="t_initialExamHT" name="t_initialExamHT"
                                                               size="6" maxlength="150" class="calcField"
                                                               ondblclick="displayDemographicMeasurements('t_initialExamHT', 'HT', '<%=demoNo%>', '<%= UtilMisc.htmlEscape(props.getProperty("t_patientDOB", "")) %>', '')"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamHT", "0")) %>"/>
                                                    </div>
                                                </td>
                                                <td width="30%">
                                                    <div class="divFlex">
                                                        Pre-preg. Wt*
                                                        <span class="sub-text">(kg)</span>
                                                        <input type="text" id="t_initialExamWT" name="t_initialExamWT"
                                                               size="6" maxlength="150" class="calcField"
                                                               ondblclick="displayDemographicMeasurements('t_initialExamWT', 'WT', '<%=demoNo%>', '<%= UtilMisc.htmlEscape(props.getProperty("t_patientDOB", "")) %>', '')"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamWT", "0")) %>"/>
                                                    </div>
                                                </td>
                                                <td width="30%">
                                                    <div class="divFlex">
                                                        <fmt:message key="form.formBCAR2020pg1.label.prePregBmi"/>
                                                        <input type="text" name="t_initialExamBMI" size="6"
                                                               maxlength="150" class="calcField"
                                                               ondblclick="calculateBmi(this);"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamBMI", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                        </table>
                                        <table border="0">
                                            <tr>
                                                <td width="50%">
                                                    <table border="0" class="noColumn">
                                                        <tr>
                                                            <th width="8%"><span class="title"><a href="#"
                                                                                                  id="initialExam1Norm"
                                                                                                  class="noLink"
                                                                                                  title="<fmt:message key='form.formBCAR2020pg1.title.setAllInitialExamToNormal'/>"><fmt:message key="form.formBCAR2020pg1.option.normal"/></a></span>
                                                            </th>
                                                            <th width="8%" colspan="2"><span class="title"><fmt:message key="form.formBCAR2020pg1.option.abnormal"/></span>
                                                            </th>
                                                            <th width="84%"><span class="sub-text">(specify)</span></th>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamHeadNorm" <carlos:encode value='<%= props.getProperty("c_initialExamHeadNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamHeadAbNorm" <carlos:encode value='<%= props.getProperty("c_initialExamHeadAbNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    <fmt:message key="form.formBCAR2020pg1.label.headNeck"/>
                                                                    <input type="text" name="t_initialExamHeadDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamHeadDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamBreastsNorm" <carlos:encode value='<%= props.getProperty("c_initialExamBreastsNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamBreastsAbNorm" <carlos:encode value='<%= props.getProperty("c_initialExamBreastsAbNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    <fmt:message key="form.formBCAR2020pg1.label.breastsNipples"/>
                                                                    <input type="text"
                                                                           name="t_initialExamBreastsDetails" size="10"
                                                                           maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamBreastsDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamHeartNorm" <carlos:encode value='<%= props.getProperty("c_initialExamHeartNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamHeartAbNorm" <carlos:encode value='<%= props.getProperty("c_initialExamHeartAbNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    <fmt:message key="form.formBCAR2020pg1.label.heartLungs"/>
                                                                    <input type="text" name="t_initialExamHeartDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamHeartDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamAbdomenNorm" <carlos:encode value='<%= props.getProperty("c_initialExamAbdomenNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamAbdomenAbNorm" <carlos:encode value='<%= props.getProperty("c_initialExamAbdomenAbNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    <fmt:message key="form.formBCAR2020pg1.label.abdomen"/>
                                                                    <input type="text"
                                                                           name="t_initialExamAbdomenDetails" size="10"
                                                                           maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamAbdomenDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamMusculoNorm" <carlos:encode value='<%= props.getProperty("c_initialExamMusculoNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamMusculoAbNorm" <carlos:encode value='<%= props.getProperty("c_initialExamMusculoAbNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    <fmt:message key="form.formBCAR2020pg1.label.musculoskeletal"/>
                                                                    <input type="text"
                                                                           name="t_initialExamMusculoDetails" size="10"
                                                                           maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamMusculoDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                                <td width="50%">
                                                    <table border="0" class="noColumn">
                                                        <tr>
                                                            <th width="8%"><span class="title"><a href="#"
                                                                                                  id="initialExam2Norm"
                                                                                                  class="noLink"
                                                                                                  title="<fmt:message key='form.formBCAR2020pg1.title.setAllInitialExamToNorm'/>"><fmt:message key="form.formBCAR2020pg1.option.normal"/></a></span>
                                                            </th>
                                                            <th width="8%" colspan="2"><span class="title"><fmt:message key="form.formBCAR2020pg1.option.abnormal"/></span>
                                                            </th>
                                                            <th width="84%"><span class="sub-text">(specify)</span></th>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamSkinNorm" <carlos:encode value='<%= props.getProperty("c_initialExamSkinNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamSkinAbNorm" <carlos:encode value='<%= props.getProperty("c_initialExamSkinAbNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="flex-container">
                                                                    <div class="flex-row">
                                                                        <div class="flex-column-title"
                                                                             style="min-width: 20px">
                                                                            <fmt:message key="form.formBCAR2020pg1.label.skin"/>
                                                                        </div>
                                                                        <div class="flex-quad-column">
                                                                            <input type="checkbox"
                                                                                   name="c_initialExamSkinVaricosities" <carlos:encode value='<%= props.getProperty("c_initialExamSkinVaricosities", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                            <fmt:message key="form.formBCAR2020pg1.label.varicosities"/>
                                                                        </div>
                                                                    </div>
                                                                    <div class="flex-row">
                                                                        <div class="flex-column-title"
                                                                             style="min-width: 20px">
                                                                        </div>
                                                                        <div class="flex-quad-column">
                                                                            <div class="divFlex">
                                                                                <input type="checkbox"
                                                                                       name="c_initialExamSkinOther" <carlos:encode value='<%= props.getProperty("c_initialExamSkinOther", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                                                <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                                                <input type="text"
                                                                                       name="t_initialExamSkinOtherDetails"
                                                                                       size="10" maxlength="150"
                                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamSkinOtherDetails", "")) %>"/>
                                                                            </div>
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamPelvicNorm" <carlos:encode value='<%= props.getProperty("c_initialExamPelvicNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamPelvicAbNorm" <carlos:encode value='<%= props.getProperty("c_initialExamPelvicAbNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    <fmt:message key="form.formBCAR2020pg1.label.pelvic"/>
                                                                    <input type="text" name="t_initialExamPelvicDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamPelvicDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                            </td>
                                                            <td>
                                                            </td>
                                                            <td colspan="2">
                                                                <fmt:message key="form.formBCAR2020pg1.label.stiTest"/>
                                                                <span class="sub-text">(dd/mm/yyyy)</span>
                                                                <input type="text" id="d_initialExamPelvicSTITest"
                                                                       name="d_initialExamPelvicSTITest"
                                                                       title="Initial Physical Examination - Pelvic STI test - Date"
                                                                       size="11" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("d_initialExamPelvicSTITest", "")) %>"/>
                                                                <img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                     id="d_initialExamPelvicSTITest_cal">
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                            </td>
                                                            <td>
                                                            </td>
                                                            <td colspan="2">
                                                                <fmt:message key="form.formBCAR2020pg1.label.papTest"/>
                                                                <span class="sub-text">(dd/mm/yyyy)</span>
                                                                <input type="text" id="d_initialExamPelvicPapTest"
                                                                       name="d_initialExamPelvicPapTest"
                                                                       title="Initial Physical Examination - Pelvic PAP test - Date"
                                                                       size="11" maxlength="150"
                                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("d_initialExamPelvicPapTest", "")) %>"/>
                                                                <img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                     id="d_initialExamPelvicPapTest_cal">
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamOtherNorm" <carlos:encode value='<%= props.getProperty("c_initialExamOtherNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamOtherAbNorm" <carlos:encode value='<%= props.getProperty("c_initialExamOtherAbNorm", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    <fmt:message key="form.formBCAR2020pg1.option.other"/>
                                                                    <input type="text" name="t_initialExamOtherDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamOtherDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                            </tr>
                                        </table>

                                    </td>
                                    <td width="34%">
                                        <!-- Comments -->
                                        <span class="title"><fmt:message key="form.formBCAR2020pg1.section.commentsFollowup"/></span><span class="sub-text"><fmt:message key="form.formBCAR2020pg1.section.commentsFollowupDetails"/></span>
                                        <textarea name="t_comments" style="width: 100%; height:180px;" size="30"
                                                  maxlength="200"
                                                  title="<%= UtilMisc.htmlEscape(props.getProperty("t_comments", "")) %>"><%= UtilMisc.htmlEscape(props.getProperty("t_comments", "")) %></textarea>
                                        <div class="divFlex">
                                            <fmt:message key="form.formBCAR2020pg1.label.careProvider"/>
                                            <span class="sub-text"><fmt:message key="form.formBCAR2020pg1.label.signatureHint"/></span>
                                            <input type="text" name="t_commentsCareProvider" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_commentsCareProvider", "")) %>"/>
                                            <input type="checkbox"
                                                   name="c_commentsMD" <carlos:encode value='<%= props.getProperty("c_commentsMD", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                            <fmt:message key="form.formBCAR2020pg1.label.md"/>
                                            <input type="checkbox"
                                                   name="c_commentsRM" <carlos:encode value='<%= props.getProperty("c_commentsRM", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                            <fmt:message key="form.formBCAR2020pg1.label.rm"/>
                                            <input type="checkbox"
                                                   name="c_commentsNP" <carlos:encode value='<%= props.getProperty("c_commentsNP", "").equals("X") ? "checked" : "" %>' context="htmlAttribute"/> />
                                            <fmt:message key="form.formBCAR2020pg1.label.np"/>
                                        </div>
                                    </td>
                                </tr>
                            </table>
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
                <input type="checkbox" name="print_pr1" id="print_pr1" checked="checked"
                       class="text ui-widget-content ui-corner-all"/>
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
                <input type="checkbox" name="print_pr5" id="print_pr5" class="text ui-widget-content ui-corner-all"/>
                <label for="print_pr5"><fmt:message key="form.formBCAR2020pg3.link.referencePage2"/></label>
                <br/>
            </fieldset>
        </form>
    </div>
    </body>


</html>
