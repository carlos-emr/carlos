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
<%@ page import="java.sql.SQLException" %>
<%@ page import="ca.openosp.openo.form.FrmRecord" %>
<%@ page import="ca.openosp.openo.form.FrmBCAR2020Record" %>
<%@ page import="ca.openosp.openo.form.FrmRecordFactory" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>




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
        <title>BC Antenatal Record 2020 Part 1</title>

        <link rel="stylesheet" type="text/css" media="all"
              href="<%=request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/bootstrap4.1.1.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/bootstrap-select.css"/>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/jquery/jquery-ui.structure-1.12.1.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/library/jquery/jquery-ui.theme-1.12.1.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/formBCAR2020.css">

        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%=request.getContextPath()%>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/share/calendar/calendar-setup.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/formBCAR2020Record.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-1.12.0.min.js"
                type="text/javascript"></script>

        <!-- Checkbox multi-select -->
        <script src="<%=request.getContextPath() %>/js/bootstrap.bundle.min.js" type="text/javascript"></script>
        <script src="<%=request.getContextPath() %>/js/bootstrap-select.min.js" type="text/javascript"></script>

        <script src="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.12.1.min.js"
                type="text/javascript"></script>
        <script src="<%=request.getContextPath()%>/js/fg.menu.js"></script>
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
                $("select[name='s_relationshipStatus']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_relationshipStatus", "UN")) %>');
                $("select[name='s_highestEducation']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_highestEducation", "UN")) %>');
                $("select[name='s_languagePreferred']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_languagePreferred", "")) %>');
                $("select[name='s_obHistorySex1']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_obHistorySex1", "")) %>');
                $("select[name='s_obHistorySex2']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_obHistorySex2", "")) %>');
                $("select[name='s_obHistorySex3']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_obHistorySex3", "")) %>');
                $("select[name='s_obHistorySex4']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_obHistorySex4", "")) %>');
                $("select[name='s_obHistorySex5']").val('<%= Encode.forJavaScriptBlock(props.getProperty("s_obHistorySex5", "")) %>');

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

        <%@ include file="demographicMeasurementModal.jsp" %>

        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    </head>

    <body bgproperties="fixed">
    <div id="maincontent" class="flex-container">

        <%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

        <c:if test="${param.warning eq 'history'}">
            <script type="text/javascript">
                if (!confirm("Warning: older version.\n\nContents of this form will overwrite newer versions if saved.\n\nSelect 'OK' to continue.")) {
                    window.close();
                }
            </script>
        </c:if>

        <div id="content_bar" class="innertube">
            <form action="${pageContext.request.contextPath}/form/BCAR2020.do" method="post">
                <input type="hidden" id="demographicNo" name="demographicNo" value="<%=demoNo%>"/>
                <input type="hidden" id="formId" name="formId" value="<%=formId%>"/>
                <input type="hidden" name="provider_no" value=<%=Encode.forHtmlAttribute(providerNo)%>/>
                <input type="hidden" id="user" name="provNo" value=<%=provNo%>/>
                <input type="hidden" name="method" value="exit"/>

                <input type="hidden" name="forwardTo" value="<%=Encode.forHtmlAttribute(pageNo)%>"/>
                <input type="hidden" name="pageNo" value="<%=Encode.forHtmlAttribute(pageNo)%>"/>
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
                            <b>
                                <a href="javascript:void(0);" onclick="return onPageChange('1');">Part 1</a>
                            </b>
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
                            <a href="javascript:void(0);" onclick="return onPageChange('5');" class="small10">Reference
                                Page 2</a>
                        </td>
                    </tr>
                </table>

                <!-- Page Heading -->
                <table border="0">
                    <tr>
                        <th align="left">British Columbia Antenatal Record Part 1 <font size="-2">PSBC 1905 - January
                            2020</font></th>
                    </tr>
                </table>

                <table border="0" class="small9">
                    <tr>
                        <td width="60%">
                            <!-- Demographics and Background-->
                            <table border="1">
                                <tr>
                                    <td width="50%" colspan="2">
                                        <span class="title">1.</span> Primary maternity care provider name<br/>
                                        <input type="text" name="t_primaryCareProvider" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_primaryCareProvider", "")) %>"/>
                                    </td>
                                    <td width="50%" colspan="2">
                                        Family physician/nurse practioner name<br/>
                                        <input type="text" name="t_familyPhysician" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_familyPhysician", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="25%">
                                        Patient Surname<br/>
                                        <input type="text" name="t_patientSurname" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientSurname", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td width="25%">
                                        Patient given name(s)<br/>
                                        <input type="text" name="t_patientGivenName" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientGivenName", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td width="25%">
                                        DOB <span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <input type="text" name="t_patientDOB" style="width: 100%" size="30"
                                               maxlength="12"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientDOB", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td width="25%">
                                        Age at EDD<br/>
                                        <input type="text" name="t_ageAtEDD" class="calcField"
                                               ondblclick="calcAgeAtEDD('<%= props.getProperty("d_confirmedEDD", "") %>', document.forms[0].t_patientDOB.value, this);"
                                               style="width: 100%" size="30" maxlength="10"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_ageAtEDD", "")) %>"/>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="25%">
                                        Surname at birth<br/>
                                        <input type="text" name="t_patientSurnameAtBirth" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientSurnameAtBirth", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        Preferred name / pronoun<br/>
                                        <input type="text" name="t_patientPreferredName" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientPreferredName", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        Language preferred<br/>
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
                                            <option value="SGN">Other Sign Language</option>
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
                                            <option value="OTH">Other</option>
                                            <option value="UN">Unknown</option>
                                        </select>
                                    </td>
                                    <td width="25%">
                                        Relationship status *<br/>
                                        <select name="s_relationshipStatus" style="width: 100%">
                                            <option value="MA">Married</option>
                                            <option value="LI">Living with partner</option>
                                            <option value="SI">Single</option>
                                            <option value="SE">Separated or divorced</option>
                                            <option value="WI">Widowed</option>
                                            <option value="NA">Prefer not to answer</option>
                                            <option value="UN">Unknown</option>
                                        </select>
                                    </td>
                                </tr>
                                <tr>
                                    <td width="50%" colspan="2">
                                        Highest level of education completed *<br/>
                                        <select name="s_highestEducation" style="width: 100%">
                                            <option value="LH">Less than high school</option>
                                            <option value="HS">High school diploma</option>
                                            <option value="TD">Trade or other certificate/ diploma (not Bachelors)
                                            </option>
                                            <option value="UD">Undergraduate university degree(s)</option>
                                            <option value="PD">Postgraduate university degree(s)</option>
                                            <option value="UN">Unknown</option>
                                        </select>
                                    </td>
                                    <td width="50%" colspan="2">
                                        Occupation<br/>
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
                                                                Indigenous identity*
                                                            </td>
                                                            <td width="45%">
                                                                <input type="checkbox"
                                                                       name="c_indIdentFirstNations" <%= Encode.forHtmlAttribute(props.getProperty("c_indIdentFirstNations", "").equals("X") ? "checked" : "") %> />First
                                                                Nations
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_indIdentNoResponse" <%=Encode.forHtmlAttribute(props.getProperty("c_indIdentNoResponse", "").equals("X") ? "checked" : "") %> />No
                                                                response
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_indIdentMetis" <%= Encode.forHtmlAttribute(props.getProperty("c_indIdentMetis", "").equals("X") ? "checked" : "") %> />M&eacute;tis
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_indIdentNone" <%= Encode.forHtmlAttribute(props.getProperty("c_indIdentNone", "").equals("X") ? "checked" : "") %> />None
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_indIdentInuk" <%= Encode.forHtmlAttribute(props.getProperty("c_indIdentInuk", "").equals("X") ? "checked" : "") %> />Inuk
                                                                (Inuit)
                                                            </td>
                                                        </tr>
                                                    </table>
                                                </td>
                                                <td width="15%" class="alignTop borderRight">
                                                    <input type="checkbox"
                                                           name="c_indIdentStatus" <%=Encode.forHtmlAttribute(props.getProperty("c_indIdentStatus", "").equals("X") ? "checked" : "") %> />Status
                                                    <br/>
                                                    <input type="checkbox"
                                                           name="c_indIdentNonStatus" <%= Encode.forHtmlAttribute(props.getProperty("c_indIdentNonStatus", "").equals("X") ? "checked" : "") %> />Non-status
                                                </td>
                                                <td width="25%" class="alignTop borderRight">
                                                    <input type="checkbox"
                                                           name="c_indIdentLiveOnReserve" <%=Encode.forHtmlAttribute(props.getProperty("c_indIdentLiveOnReserve", "").equals("X") ? "checked" : "") %> />Live
                                                    on reserve <br/>
                                                    <input type="checkbox"
                                                           name="c_indIdentLiveOffReserve" <%= Encode.forHtmlAttribute(props.getProperty("c_indIdentLiveOffReserve", "").equals("X") ? "checked" : "") %> />Live
                                                    off reserve <br/>
                                                    <input type="checkbox"
                                                           name="c_indIdentLiveOnOffReserve" <%= Encode.forHtmlAttribute(props.getProperty("c_indIdentLiveOnOffReserve", "").equals("X") ? "checked" : "") %> />Live
                                                    on & off reserve
                                                </td>
                                                <td width="25%">
                                                    <div>
                                                        <div class="div-left">
                                                            Ethnicity
                                                        </div>
                                                        <div class="div-right">
                                                            <select class="selectpicker" id="ethnicitySelectPicker"
                                                                    multiple data-selected-text-format="count > 1"
                                                                    data-header="Select Ethnicity" data-width="80px">
                                                                <option value="Indigenous/Aboriginal">
                                                                    Indigenous/Aboriginal
                                                                </option>
                                                                <option value="European-Western"
                                                                        data-subtext="(eg. English, Italian)">
                                                                    European-Western
                                                                </option>
                                                                <option value="European-Eastern"
                                                                        data-subtext="(eg. Russian, Polish)">
                                                                    European-Eastern
                                                                </option>
                                                                <option value="Asian-East"
                                                                        data-subtext="(eg. Chinese, Japanese, Korean)">
                                                                    Asian-East
                                                                </option>
                                                                <option value="Asian-South"
                                                                        data-subtext="(eg. Indian, Pakistani, Sri Lankan)">
                                                                    Asian-South
                                                                </option>
                                                                <option value="Asian-South East"
                                                                        data-subtext="(eg. Malaysian, Filipino)">
                                                                    Asian-South East
                                                                </option>
                                                                <option value="Middle Eastern"
                                                                        data-subtext="(eg. Iranian, Lebanese)">Middle
                                                                    Eastern
                                                                </option>
                                                                <option value="African">African</option>
                                                                <option value="Caribbean">Caribbean</option>
                                                                <option value="Latin American"
                                                                        data-subtext="(eg. Argentinian, Chilean)">Latin
                                                                    American
                                                                </option>
                                                                <option value="Do not know" data-subtext="">Do not
                                                                    know
                                                                </option>
                                                                <option value="Prefer not to answer" data-subtext="">
                                                                    Prefer not to answer
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
                                        Surname<br/>
                                        <input type="text" name="t_patientSurname" style="width: 100%" size="30"
                                               maxlength="100"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_patientSurname", "")) %>"
                                               title="This field is readonly, please update the master demographic"
                                               readonly/>
                                    </td>
                                    <td width="50%" colspan="2">
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
                                        <table border="0">
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
                            <!-- Partner Informations -->
                            <table border="1">
                                <tr>
                                    <td width="25%">
                                        Partner: Surname, given name(s)<br/>
                                        <input type="text" name="t_partnerName" style="width: 100%" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_partnerName", "")) %>"/>
                                    </td>
                                    <td width="25%">
                                        Occupation<br/>
                                        <input type="text" name="t_partnerOccupation" style="width: 100%" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_partnerOccupation", "")) %>"/>
                                    </td>
                                    <td width="35%">
                                        Biological father/donor: Surname, given name(s)<br/>
                                        <div class="flex-row">
                                            <div class="flex-column" style="padding-right:5px;flex-wrap: nowrap;">
                                                <input type="checkbox" name="biologicalFatherSameCheck"/>Same as partner
                                            </div>
                                            <div class="flex-double-column">
                                                <input type="text" name="t_biologicalFatherName" size="30"
                                                       maxlength="80"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_biologicalFatherName", "")) %>"/>
                                            </div>
                                        </div>
                                    </td>
                                    <td width="5%">
                                        Age<br/>
                                        <input type="text" name="t_biologicalFatherAge" style="width: 100%" size="60"
                                               maxlength="80"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_biologicalFatherAge", "")) %>"/>
                                    </td>
                                    <td width="10%">
                                        Ethnicity<br/>
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
                                                    <span class="title">2.</span> <span class="allergy">Allergies</span>
                                                    <span class="sub-text">(incl. reaction)</span>
                                                </div>
                                                <div>
                                                    <input type="checkbox"
                                                           name="c_allergiesNone" <%=Encode.forHtmlAttribute(props.getProperty("c_allergiesNone", "").equals("X") ? "checked" : "") %> />None
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
                                                Medications / OTC drugs / herbals / vitamins<br \>
                                                <input type="text" name="t_medications" size="45" maxlength="40"
                                                       class="calcField" ondblclick="appendNotify(this);"
                                                       value="<%= UtilMisc.htmlEscape(props.getProperty("t_medications", "")) %>"/>
                                            </div>
                                            <div class="flex-stack">
                                                <div>
                                                    <input type="checkbox"
                                                           name="c_preconceptionFolicAcid" <%=Encode.forHtmlAttribute(props.getProperty("c_preconceptionFolicAcid", "").equals("X") ? "checked" : "") %> />Preconception
                                                    folic acid
                                                </div>
                                                <div>
                                                    <input type="checkbox"
                                                           name="c_t1FolicAcid" <%=Encode.forHtmlAttribute(props.getProperty("c_t1FolicAcid", "").equals("X") ? "checked" : "") %> />T1
                                                    folic acid
                                                </div>
                                            </div>
                                        </div>

                                    </td>
                                    <td width="25%">
                                        <div class="flex-row">
                                            <div>
                                                Beliefs / practices <span
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
                                        <span class="title">3.</span> Contraceptives: Type<br/>
                                        <input type="text" name="t_contraceptiveType" class="text-style" size="30"
                                               maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_contraceptiveType", "")) %>"/>
                                    </td>
                                    <td width="12%">
                                        Last used <span class="sub-text">(dd/mm/yyyy)</span><br/>
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
                                        Pregnancy planned:<br/>
                                        <div class="div-center">
                                            <input type="checkbox"
                                                   name="c_pregnancyPlannedYes" <%=Encode.forHtmlAttribute(props.getProperty("c_pregnancyPlannedYes", "").equals("X") ? "checked" : "") %> />Yes
                                            <input type="checkbox"
                                                   name="c_pregnancyPlannedNo" <%=Encode.forHtmlAttribute(props.getProperty("c_pregnancyPlannedNo", "").equals("X") ? "checked" : "") %> />No
                                        </div>
                                    </td>
                                    <td width="11%">
                                        LMP<span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_LMP" id="d_LMP" title="LMP" size="10"
                                                   maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_LMP", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_LMP_cal">
                                        </div>
                                    </td>
                                    <td width="14%">
                                        EDD by LMP<span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_EDDByLMP" id="d_EDDByLMP" title="EDD by LMP"
                                                   class="calcField" onDblClick="calculateByLMP(this);" size="10"
                                                   maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_EDDByLMP", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_EDDByLMP_cal">
                                        </div>
                                    </td>
                                    <td width="12%">
                                        Dating US<span class="sub-text">(dd/mm/yyyy)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_datingUS" id="d_datingUS" title="Dating US"
                                                   size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("d_datingUS", "")) %>"/>
                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_datingUS_cal">
                                        </div>
                                    </td>
                                    <td width="12%">
                                        GA by US<span class="sub-text">(wks/days)</span><br/>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="t_GAByUS" id="t_GAByUS" class="calcField"
                                                   title="GA by US" size="7" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_GAByUS", "")) %>"
                                                   onDblClick="getGAByFieldDate('t_GAByUS', 'd_EDDByUS', 'd_datingUS')"/>
                                        </div>
                                    </td>
                                    <td width="13%">
                                        EDD by US<span class="sub-text">(dd/mm/yyyy)</span><br/>
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
                                        <span class="title">4. Obstetrical History</span>
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
                                        Spontaneous
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
                                    <th width="11%">Date<br/><span class="sub-text">(dd/mm/yyyy)</span></th>
                                    <th width="10%">Place of<br/>birth</th>
                                    <th width="5%">GA<br/><span class="sub-text">(wks/days)</span></th>
                                    <th width="8%">Duration of<br/>labour <span class="sub-text">(hrs)</span></th>
                                    <th width="12%">Mode<br/>of birth</th>
                                    <th width="30%">Perinatal Complications/comments</th>
                                    <th width="4%">Sex</th>
                                    <th width="6%">Birth<br>weight <span class="sub-text">(g)</span></th>
                                    <th>Breastfed<br/><span class="sub-text">(mos)</span></th>
                                    <th width="9%">Child's<br/>present health</th>
                                </tr>
                                <!-- Past Pregnancy Row 1-->
                                <tr>
                                    <td>
                                        <div class="div-center" style="margin-top:1px;">
                                            <input type="text" name="d_obHistoryDate1" id="d_obHistoryDate1"
                                                   title="Obstetrical History Line 1 - Date" size="10" maxlength="150"
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
                                                   placeholder="double click for selection"
                                                   class="data-list-input text-style"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_obHistoryBirthMode1", "")) %>"/>
                                            <datalist id="birthmode">
                                                <option value="">Other</option>
                                                <option value="SVD">SVD</option>
                                                <option value="CSec">C-section</option>
                                                <option value="Vac">Vacuum</option>
                                                <option value="For">Forceps</option>
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
                                            <option value="M" title="Male">M</option>
                                            <option value="F" title="Female">F</option>
                                            <option value="U" title="Undifferentiated">U</option>
                                            <option value="O" title="Other">O</option>
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
                                                <option value="">Other</option>
                                                <option value="A&W">A&W</option>
                                            </datalist>
                                            <input type="text" list="childhealth"
                                                   placeholder="double click for selection"
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
                                                   title="Obstetrical History Line 2 - Date" size="10" maxlength="150"
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
                                                <option value="">Other</option>
                                                <option value="SVD">SVD</option>
                                                <option value="C-section">C-section</option>
                                                <option value="Vacuum">Vacuum</option>
                                                <option value="Forceps">Forceps</option>
                                                <option value="Vacuum and Forceps">Vacuum and Forceps</option>
                                                <option value="Forceps Trial and C-section">Forceps Trial and
                                                    C-section
                                                </option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryBirthMode2" list="birthmode2"
                                                   placeholder="double click for selection"
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
                                            <option value="M" title="Male">M</option>
                                            <option value="F" title="Female">F</option>
                                            <option value="U" title="Undifferentiated">U</option>
                                            <option value="O" title="Other">O</option>
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
                                                <option value="">Other</option>
                                                <option value="A&W">A&W</option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryPresentHealth2" list="childhealth2"
                                                   placeholder="double click for selection"
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
                                                   title="Obstetrical History Line 3 - Date" size="10" maxlength="150"
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
                                                <option value="">Other</option>
                                                <option value="SVD">SVD</option>
                                                <option value="C-section">C-section</option>
                                                <option value="Vacuum">Vacuum</option>
                                                <option value="Forceps">Forceps</option>
                                                <option value="Vacuum and Forceps">Vacuum and Forceps</option>
                                                <option value="Forceps Trial and C-section">Forceps Trial and
                                                    C-section
                                                </option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryBirthMode3" list="birthmode3"
                                                   placeholder="double click for selection"
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
                                            <option value="M" title="Male">M</option>
                                            <option value="F" title="Female">F</option>
                                            <option value="U" title="Undifferentiated">U</option>
                                            <option value="O" title="Other">O</option>
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
                                                <option value="">Other</option>
                                                <option value="A&W">A&W</option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryPresentHealth3" list="childhealth3"
                                                   placeholder="double click for selection"
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
                                                   title="Obstetrical History Line 4 - Date" size="10" maxlength="150"
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
                                                <option value="">Other</option>
                                                <option value="SVD">SVD</option>
                                                <option value="C-section">C-section</option>
                                                <option value="Vacuum">Vacuum</option>
                                                <option value="Forceps">Forceps</option>
                                                <option value="Vacuum and Forceps">Vacuum and Forceps</option>
                                                <option value="Forceps Trial and C-section">Forceps Trial and
                                                    C-section
                                                </option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryBirthMode4" list="birthmode4"
                                                   placeholder="double click for selection"
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
                                            <option value="M" title="Male">M</option>
                                            <option value="F" title="Female">F</option>
                                            <option value="U" title="Undifferentiated">U</option>
                                            <option value="O" title="Other">O</option>
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
                                                <option value="">Other</option>
                                                <option value="A&W">A&W</option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryPresentHealth4" list="childhealth4"
                                                   placeholder="double click for selection"
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
                                                   title="Obstetrical History Line 5 - Date" size="10" maxlength="150"
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
                                                <option value="">Other</option>
                                                <option value="SVD">SVD</option>
                                                <option value="C-section">C-section</option>
                                                <option value="Vacuum">Vacuum</option>
                                                <option value="Forceps">Forceps</option>
                                                <option value="Vacuum and Forceps">Vacuum and Forceps</option>
                                                <option value="Forceps Trial and C-section">Forceps Trial and
                                                    C-section
                                                </option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryBirthMode5" list="birthmode5"
                                                   placeholder="double click for selection"
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
                                            <option value="M" title="Male">M</option>
                                            <option value="F" title="Female">F</option>
                                            <option value="U" title="Undifferentiated">U</option>
                                            <option value="O" title="Other">O</option>
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
                                                <option value="">Other</option>
                                                <option value="A&W">A&W</option>
                                            </datalist>
                                            <input type="text" name="t_obHistoryPresentHealth5" list="childhealth5"
                                                   placeholder="double click for selection"
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
                                        <span class="title">5. Present Pregnancy</span><br/>
                                        <table border="0" class="noColumn">
                                            <tr>
                                                <th width="8%"><span class="title"><a href="#" id="presentPregnancyNo"
                                                                                      class="noLink"
                                                                                      title="Set all Present Pregnancy Questions to No">No</a></span>
                                                </th>
                                                <th width="8%"><span class="title">Yes</span></th>
                                                <th width="84%"><span class="sub-text">(specify)</span></th>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_presentPregnancyARTNo" <%=Encode.forHtmlAttribute(props.getProperty("c_presentPregnancyARTNo", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_presentPregnancyARTYes" <%=Encode.forHtmlAttribute(props.getProperty("c_presentPregnancyARTYes", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    ART: <span class="sub-text">(select one only)</span>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td><input type="checkbox"
                                                           name="c_presentPregnancyARTOvaStim" <%=Encode.forHtmlAttribute(props.getProperty("c_presentPregnancyARTOvaStim", "").equals("X") ? "checked" : "") %> />Ovarian
                                                    stimulation only
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td><input type="checkbox"
                                                           name="c_presentPregnancyARTIUIOnly" <%=Encode.forHtmlAttribute(props.getProperty("c_presentPregnancyARTIUIOnly", "").equals("X") ? "checked" : "") %> />IUI
                                                    only
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td><input type="checkbox"
                                                           name="c_presentPregnancyARTOvaStimIUI" <%=Encode.forHtmlAttribute(props.getProperty("c_presentPregnancyARTOvaStimIUI", "").equals("X") ? "checked" : "") %> />Ovarian
                                                    stimulation + IUI
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td>
                                                    <div class="divFlex">
                                                        <input type="checkbox"
                                                               name="c_presentPregnancyARTIVF" <%=Encode.forHtmlAttribute(props.getProperty("c_presentPregnancyARTIVF", "").equals("X") ? "checked" : "") %> />
                                                        IVF <span class="sub-text">(# of embryos transferred)</span>
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
                                                               name="c_presentPregnancyARTICSI" <%=Encode.forHtmlAttribute(props.getProperty("c_presentPregnancyARTICSI", "").equals("X") ? "checked" : "") %> />
                                                        ICSI <span class="sub-text">(# of embryos transferred)</span>
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
                                                               name="c_presentPregnancyARTOther" <%=Encode.forHtmlAttribute(props.getProperty("c_presentPregnancyARTOther", "").equals("X") ? "checked" : "") %> />
                                                        Other
                                                        <input type="text" name="t_presentPregnancyARTOtherDetails"
                                                               size="20" maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_presentPregnancyARTOtherDetails", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyBleeding", "Bleeding")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyNausea", "Nausea")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyTravel", "Travel (self/partner)")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyInfection", "Infection/rash/fever")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "presentPregnancyOther", "Other")%>
                                        </table>
                                    </td>
                                    <td width="33%" rowspan="2">
                                        <!-- Medical History -->
                                        <span class="title">7. Medical History</span><br/>
                                        <table border="0" class="noColumn">
                                            <tr>
                                                <th width="8%"><span class="title"><a href="#" id="medicalHistoryNo"
                                                                                      class="noLink"
                                                                                      title="Set all Medical History Questions to No">No</a></span>
                                                </th>
                                                <th width="8%"><span class="title">Yes</span></th>
                                                <th width="84%"><span class="sub-text">(specify)</span></th>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistorySurgery", "Surgery")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryAnestheticComplications", "Anesthetic complications")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryNeuro", "Neuro.")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryResp", "Resp.")%>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryCVNo" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryCVNo", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryCVYes" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryCVYes", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    CV:
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryCVHypert" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryCVHypert", "").equals("X") ? "checked" : "") %> />Hypertension
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryCVPrevHypert" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryCVPrevHypert", "").equals("X") ? "checked" : "") %> />Prev.
                                                    hypert. in preg.
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td style="padding-left:25px;">
                                                    <div class="divFlex">
                                                        <input type="checkbox"
                                                               name="c_medicalHistoryCVOther" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryCVOther", "").equals("X") ? "checked" : "") %> />
                                                        Other
                                                        <input type="text" name="t_medicalHistoryCVOtherDetails"
                                                               size="30" maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_medicalHistoryCVOtherDetails", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryAbdo", "Abdo./GI")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryGyne", "Gyne./GU")%>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryHematologyNo" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryHematologyNo", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryHematologyYes" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryHematologyYes", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    Hematology (eg.transfusion,thromboembolic/coag)
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
                                                           name="c_medicalHistoryEndocrineNo" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryEndocrineNo", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryEndocrineYes" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryEndocrineYes", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <div class="flex-container">
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                                Endocrine:
                                                            </div>
                                                            <div class="flex-quad-column">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryEndocrineT1DM" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryEndocrineT1DM", "").equals("X") ? "checked" : "") %> />
                                                                T1DM
                                                            </div>
                                                            <div class="flex-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryEndocrineT2DM" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryEndocrineT2DM", "").equals("X") ? "checked" : "") %> />
                                                                T2DM
                                                            </div>
                                                            <div class="flex-double-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryEndocrinePrevGDM" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryEndocrinePrevGDM", "").equals("X") ? "checked" : "") %> />
                                                                Prev. GDM
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title"></div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryEndocrineThyroid" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryEndocrineThyroid", "").equals("X") ? "checked" : "") %> />
                                                                Thyroid
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title"></div>
                                                            <div class="flex-quad-column">
                                                                <div class="divFlex">
                                                                    <input type="checkbox"
                                                                           name="c_medicalHistoryEndocrineOther" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryEndocrineOther", "").equals("X") ? "checked" : "") %> />
                                                                    Other
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
                                                           name="c_medicalHistoryMentalNo" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMentalNo", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryMentalYes" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMentalYes", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <div class="flex-container">
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                                Mental health:
                                                            </div>
                                                            <div class="flex-quad-column">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHAnxiety" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMHAnxiety", "").equals("X") ? "checked" : "") %> />
                                                                Anxiety
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title"></div>
                                                            <div class="flex-double-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHDepression" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMHDepression", "").equals("X") ? "checked" : "") %> />
                                                                Depression
                                                            </div>
                                                            <div class="flex-double-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHPrevPPD" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMHPrevPPD", "").equals("X") ? "checked" : "") %> />
                                                                Prev. PPD
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHBipolar" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMHBipolar", "").equals("X") ? "checked" : "") %> />
                                                                Bipolar
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHEatingDisorder" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMHEatingDisorder", "").equals("X") ? "checked" : "") %> />
                                                                Eating disorder
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHSubstanceUse" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMHSubstanceUse", "").equals("X") ? "checked" : "") %> />
                                                                Substance use disorder:
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title-alt">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHMethadone" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMHMethadone", "").equals("X") ? "checked" : "") %> />
                                                                Methadone
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title-alt">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryMHSuboxone" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMHSuboxone", "").equals("X") ? "checked" : "") %> />
                                                                Suboxone
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <div class="divFlex">
                                                                    <input type="checkbox"
                                                                           name="c_medicalHistoryMHOther" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryMHOther", "").equals("X") ? "checked" : "") %> />
                                                                    Other
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
                                                           name="c_medicalHistoryInfectiousNo" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryInfectiousNo", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryInfectiousYes" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryInfectiousYes", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <div class="flex-container">
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                                Infectious diseases:
                                                            </div>
                                                            <div class="flex-quad-column">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryIDVaricella" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryIDVaricella", "").equals("X") ? "checked" : "") %> />
                                                                Varicella
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryIDHSV" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryIDHSV", "").equals("X") ? "checked" : "") %> />
                                                                HSV
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <div class="divFlex">
                                                                    <input type="checkbox"
                                                                           name="c_medicalHistoryIDOther" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryIDOther", "").equals("X") ? "checked" : "") %> />
                                                                    Other
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
                                                           name="c_medicalHistoryImmunizationsNo" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryImmunizationsNo", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_medicalHistoryImmunizationsYes" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryImmunizationsYes", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <div class="flex-container">
                                                        <div class="flex-row">
                                                            <div class=flex-column-title">
                                                                Immunizations:
                                                            </div>
                                                            <div class="flex-quad-column">
                                                            </div>
                                                        </div>
                                                        <div class="flex-row">
                                                            <div class="flex-column-title">
                                                            </div>
                                                            <div class="flex-quad-column">
                                                                <input type="checkbox"
                                                                       name="c_medicalHistoryImmunizationsFlu" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryImmunizationsFlu", "").equals("X") ? "checked" : "") %> />
                                                                Flu
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
                                                                       name="c_medicalHistoryImmunizationsTDAP" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryImmunizationsTDAP", "").equals("X") ? "checked" : "") %> />
                                                                Tdap
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
                                                                           name="c_medicalHistoryImmunizationsOther" <%=Encode.forHtmlAttribute(props.getProperty("c_medicalHistoryImmunizationsOther", "").equals("X") ? "checked" : "") %> />
                                                                    Other
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
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "medicalHistoryOther", "Other")%>
                                        </table>
                                    </td>
                                    <td width="33%" rowspan="2">
                                        <table style="border-collapse: collapse;">
                                            <tr>
                                                <td>
                                                    <!-- Lifestyle/Social Concerns -->
                                                    <span class="title">8. Lifestyle/Social Concerns</span><br/>
                                                    <table border="0" style="border-bottom:black thin solid;"
                                                           class="noColumn">
                                                        <tr>
                                                            <th width="8%"><span class="title"><a href="#"
                                                                                                  id="lifestyleSocialNo"
                                                                                                  class="noLink"
                                                                                                  title="Set all Lifestyle/Social concerns Questions to No">No</a></span>
                                                            </th>
                                                            <th width="8%"><span class="title">Yes</span></th>
                                                            <th width="84%"><span class="sub-text">(specify)</span></th>
                                                        </tr>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleDiet", "Diet/nutrition")%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleExercise", "Exercise")%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleFinancial", "Financial")%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleHousing", "Housing/food sec")%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleTransportation", "Transportation")%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleSafety", "Safety")%>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_lifestyleGenderViolNo" <%=Encode.forHtmlAttribute(props.getProperty("c_lifestyleGenderViolNo", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_lifestyleGenderViolYes" <%=Encode.forHtmlAttribute(props.getProperty("c_lifestyleGenderViolYes", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td>
                                                                <div class="flex-container">
                                                                    <div class="flex-row">
                                                                        <div class="flex-column-title-alt">
                                                                            Gender-based violence:
                                                                        </div>
                                                                    </div>
                                                                    <div class="flex-row">
                                                                        <div class="flex-column-title">
                                                                        </div>
                                                                        <div class="flex-double-column">
                                                                            <input type="checkbox"
                                                                                   name="c_lifestyleGenderViolPartner" <%=Encode.forHtmlAttribute(props.getProperty("c_lifestyleGenderViolPartner", "").equals("X") ? "checked" : "") %> />
                                                                            Partner
                                                                        </div>
                                                                        <div class="flex-double-column">
                                                                            <input type="checkbox"
                                                                                   name="c_lifestyleGenderViolNonPartner" <%=Encode.forHtmlAttribute(props.getProperty("c_lifestyleGenderViolNonPartner", "").equals("X") ? "checked" : "") %> />
                                                                            Non-partner
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleRelationships", "Relationships/support")%>
                                                        <%=((FrmBCAR2020Record) rec).createToggleOption(props, "lifestyleOther", "Other")%>

                                                    </table>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>
                                                    <!-- Substance Use -->
                                                    <table border="0" style="border-bottom:whitesmoke thin solid;">
                                                        <tr>
                                                            <th width="38%"><span class="title">9. Substance Use</span>
                                                            </th>
                                                            <th width="33%"><span
                                                                    class="sub-title">3Mo Before Preg</span></th>
                                                            <th width="29%"><span class="sub-title">During Preg</span>
                                                            </th>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <span class="sub-title">Alcohol</span>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoAlcoholNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoAlcoholNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substance3MoAlcoholYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoAlcoholYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substancePregAlcoholNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregAlcoholNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substancePregAlcoholYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregAlcoholYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                # Drinks per week
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
                                                                4+ drinks at one time
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoAlcohol4DrinksNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoAlcohol4DrinksNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substance3MoAlcohol4DrinksYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoAlcohol4DrinksYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substancePregAlcohol4DrinksNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregAlcohol4DrinksNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substancePregAlcohol4DrinksYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregAlcohol4DrinksYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td colspan="3">
                                                                Quit alcohol:
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitAlcoholNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceQuitAlcoholNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitAlcoholYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceQuitAlcoholYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
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
                                                                <span class="sub-title">Tobacco</span>
                                                            </td>
                                                            <td width="33%">
                                                                <input type="checkbox"
                                                                       name="c_substance3MoTobaccoNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoTobaccoNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substance3MoTobaccoYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoTobaccoYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                            <td width="29%">
                                                                <input type="checkbox"
                                                                       name="c_substancePregTobaccoNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregTobaccoNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substancePregTobaccoYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregTobaccoYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
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
                                                                       name="c_substance3MoTobaccoSecHndSmkNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoTobaccoSecHndSmkNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substance3MoTobaccoSecHndSmkYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoTobaccoSecHndSmkYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substancePregTobaccoSecHndSmkNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregTobaccoSecHndSmkNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substancePregTobaccoSecHndSmkYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregTobaccoSecHndSmkYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td colspan="3">
                                                                Quit tobacco:
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitTobaccoNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceQuitTobaccoNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitTobaccoYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceQuitTobaccoYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                                <span class="sub-text">(dd/mm/yyyy)</span>
                                                                <input type="text" id="d_substanceQuitTobaccoDate"
                                                                       name="d_substanceQuitTobaccoDate"
                                                                       title="Substance Use - Quit Tobacco - Date"
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
                                                                <span class="sub-title">Cannabis</span>
                                                            </td>
                                                            <td width="33%">
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoCannabisNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoCannabisYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                            <td width="29%">
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregCannabisNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregCannabisYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td width="38%">
                                                                CBD product(s) only
                                                            </td>
                                                            <td width="33%">
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisCBDOnlyNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoCannabisCBDOnlyNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisCBDOnlyYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoCannabisCBDOnlyYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                            <td width="29%">
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisCBDOnlyNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregCannabisCBDOnlyNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisCBDOnlyYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregCannabisCBDOnlyYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                # Uses per day
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
                                                                Primary route:<br/>
                                                                <span class="sub-text">(select one only)</span>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisSmoke" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoCannabisSmoke", "").equals("X") ? "checked" : "") %> />
                                                                Smoke
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisVapo" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoCannabisVapo", "").equals("X") ? "checked" : "") %> />
                                                                Vaporize
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisEdi" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoCannabisEdi", "").equals("X") ? "checked" : "") %> />
                                                                Edible/oral
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substance3MoCannabisOther" <%=Encode.forHtmlAttribute(props.getProperty("c_substance3MoCannabisOther", "").equals("X") ? "checked" : "") %> />
                                                                Other
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisSmoke" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregCannabisSmoke", "").equals("X") ? "checked" : "") %> />
                                                                Smoke
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisVapo" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregCannabisVapo", "").equals("X") ? "checked" : "") %> />
                                                                Vaporize
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisEdi" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregCannabisEdi", "").equals("X") ? "checked" : "") %> />
                                                                Edible/oral
                                                                <br/>
                                                                <input type="checkbox"
                                                                       name="c_substancePregCannabisOther" <%=Encode.forHtmlAttribute(props.getProperty("c_substancePregCannabisOther", "").equals("X") ? "checked" : "") %> />
                                                                Other
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td colspan="3">
                                                                Quit cannabis:
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitCannabisNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceQuitCannabisNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substanceQuitCannabisYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceQuitCannabisYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes
                                                                <span class="sub-text">(dd/mm/yyyy)</span>
                                                                <input type="text" id="d_substanceQuitCannabisDate"
                                                                       name="d_substanceQuitCannabisDate"
                                                                       title="Substance Use - Quit Cannabis - Date"
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
                                                                <span class="sub-title">Other(s) During Preg</span>
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersNo" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceOthersNo", "").equals("X") ? "checked" : "") %> />
                                                                No
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersYes" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceOthersYes", "").equals("X") ? "checked" : "") %> />
                                                                Yes:
                                                                <span class="sub-text">(check all that apply)</span>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersCocaine" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceOthersCocaine", "").equals("X") ? "checked" : "") %> />
                                                                Cocaine
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersOpioids" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceOthersOpioids", "").equals("X") ? "checked" : "") %> />
                                                                Opioids
                                                            </td>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersMeth" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceOthersMeth", "").equals("X") ? "checked" : "") %> />
                                                                Methamphetamines
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td colspan="2">
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersIVDrugs" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceOthersIVDrugs", "").equals("X") ? "checked" : "") %> />
                                                                IV Drugs
                                                                <input type="checkbox"
                                                                       name="c_substanceOthersPrescDrugs" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceOthersPrescDrugs", "").equals("X") ? "checked" : "") %> />
                                                                Prescription Drugs
                                                                <div class="divFlex">
                                                                    <input type="checkbox"
                                                                           name="c_substanceOthersOther" <%=Encode.forHtmlAttribute(props.getProperty("c_substanceOthersOther", "").equals("X") ? "checked" : "") %> />
                                                                    Other(s)
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
                                        <span class="title">6. Family History</span><br/>
                                        <table border="0" class="noColumn">
                                            <tr>
                                                <th width="8%"><span class="title"><a href="#" id="familyHistoryNo"
                                                                                      class="noLink"
                                                                                      title="Set all Family History Questions to No">No</a></span>
                                                </th>
                                                <th width="8%"><span class="title">Yes</span></th>
                                                <th width="84%"><span class="sub-text">(specify)</span></th>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryAnestheticComp", "Anesthetic complications")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryHypertension", "Hypertension")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryThromboembolic", "Thromboembolic")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryDiabetes", "Diabetes")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryMentalHealth", "Mental health")%>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistorySubstanceUse", "Substance use disorder")%>
                                            <tr>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_familyHistoryInheritedConditionsNo" <%=Encode.forHtmlAttribute(props.getProperty("c_familyHistoryInheritedConditionsNo", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    <input type="checkbox"
                                                           name="c_familyHistoryInheritedConditionsYes" <%=Encode.forHtmlAttribute(props.getProperty("c_familyHistoryInheritedConditionsYes", "").equals("X") ? "checked" : "") %> />
                                                </td>
                                                <td>
                                                    Inherited conditions/defects<br/>
                                                    (eg. Tay-Sachs, Sickle Cell, Congenital Heart Defect, Cystic
                                                    Fibrosis)
                                                </td>
                                            </tr>
                                            <tr>
                                                <td></td>
                                                <td></td>
                                                <td>
                                                    <div class="divFlex">
                                                        <span class="sub-text">(Mother)</span>
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
                                                        <span class="sub-text">(Biological father/donor)</span>
                                                        <input type="text"
                                                               name="t_familyHistoryInheritedConditionsFather" size="10"
                                                               maxlength="150"
                                                               value="<%= UtilMisc.htmlEscape(props.getProperty("t_familyHistoryInheritedConditionsFather", "")) %>"/>
                                                    </div>
                                                </td>
                                            </tr>
                                            <%=((FrmBCAR2020Record) rec).createToggleOption(props, "familyHistoryOther", "Other")%>
                                        </table>
                                    </td>

                                </tr>
                                <tr>
                                    <td colspan="2">

                                        <!-- Initial Physical Examination -->
                                        <span class="title">10. Initial Physical Examination</span>
                                        Date
                                        <span class="sub-text">(dd/mm/yyyy)</span>
                                        <input type="text" id="d_initialExamDate" name="d_initialExamDate"
                                               title="Initial Physical Examination - Date" size="10" maxlength="150"
                                               value="<%= UtilMisc.htmlEscape(props.getProperty("d_initialExamDate", "")) %>"/>
                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="d_initialExamDate_cal">
                                        Completed by
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
                                                        Pre-preg. BMI*
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
                                                                                                  title="Set all Initial Exam Questions to Normal">Norm</a></span>
                                                            </th>
                                                            <th width="8%" colspan="2"><span class="title">Abnorm</span>
                                                            </th>
                                                            <th width="84%"><span class="sub-text">(specify)</span></th>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamHeadNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamHeadNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamHeadAbNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamHeadAbNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    Head & neck
                                                                    <input type="text" name="t_initialExamHeadDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamHeadDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamBreastsNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamBreastsNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamBreastsAbNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamBreastsAbNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    Breasts & nipples
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
                                                                       name="c_initialExamHeartNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamHeartNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamHeartAbNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamHeartAbNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    Heart & lungs
                                                                    <input type="text" name="t_initialExamHeartDetails"
                                                                           size="10" maxlength="150"
                                                                           value="<%= UtilMisc.htmlEscape(props.getProperty("t_initialExamHeartDetails", "")) %>"/>
                                                                </div>
                                                            </td>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamAbdomenNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamAbdomenNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamAbdomenAbNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamAbdomenAbNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    Abdomen
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
                                                                       name="c_initialExamMusculoNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamMusculoNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamMusculoAbNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamMusculoAbNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    Musculoskeletal
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
                                                                                                  title="Set all Initial Exam Questions to Norm">Norm</a></span>
                                                            </th>
                                                            <th width="8%" colspan="2"><span class="title">Abnorm</span>
                                                            </th>
                                                            <th width="84%"><span class="sub-text">(specify)</span></th>
                                                        </tr>
                                                        <tr>
                                                            <td>
                                                                <input type="checkbox"
                                                                       name="c_initialExamSkinNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamSkinNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamSkinAbNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamSkinAbNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="flex-container">
                                                                    <div class="flex-row">
                                                                        <div class="flex-column-title"
                                                                             style="min-width: 20px">
                                                                            Skin:
                                                                        </div>
                                                                        <div class="flex-quad-column">
                                                                            <input type="checkbox"
                                                                                   name="c_initialExamSkinVaricosities" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamSkinVaricosities", "").equals("X") ? "checked" : "") %> />
                                                                            Varicosities
                                                                        </div>
                                                                    </div>
                                                                    <div class="flex-row">
                                                                        <div class="flex-column-title"
                                                                             style="min-width: 20px">
                                                                        </div>
                                                                        <div class="flex-quad-column">
                                                                            <div class="divFlex">
                                                                                <input type="checkbox"
                                                                                       name="c_initialExamSkinOther" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamSkinOther", "").equals("X") ? "checked" : "") %> />
                                                                                Other
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
                                                                       name="c_initialExamPelvicNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamPelvicNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamPelvicAbNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamPelvicAbNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    Pelvic
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
                                                                STI test
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
                                                                Pap test
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
                                                                       name="c_initialExamOtherNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamOtherNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td width="30px">
                                                                <input type="checkbox"
                                                                       name="c_initialExamOtherAbNorm" <%=Encode.forHtmlAttribute(props.getProperty("c_initialExamOtherAbNorm", "").equals("X") ? "checked" : "") %> />
                                                            </td>
                                                            <td colspan="2">
                                                                <div class="divFlex">
                                                                    Other
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
                                        <span class="title">11. Comments/Follow-up</span><span class="sub-text">(incl. details from sections 5-10)</span>
                                        <textarea name="t_comments" style="width: 100%; height:180px;" size="30"
                                                  maxlength="200"
                                                  title="<%= UtilMisc.htmlEscape(props.getProperty("t_comments", "")) %>"><%= UtilMisc.htmlEscape(props.getProperty("t_comments", "")) %></textarea>
                                        <div class="divFlex">
                                            Care provider
                                            <span class="sub-text">(signature)</span>
                                            <input type="text" name="t_commentsCareProvider" size="10" maxlength="150"
                                                   value="<%= UtilMisc.htmlEscape(props.getProperty("t_commentsCareProvider", "")) %>"/>
                                            <input type="checkbox"
                                                   name="c_commentsMD" <%=Encode.forHtmlAttribute(props.getProperty("c_commentsMD", "").equals("X") ? "checked" : "") %> />
                                            MD
                                            <input type="checkbox"
                                                   name="c_commentsRM" <%=Encode.forHtmlAttribute(props.getProperty("c_commentsRM", "").equals("X") ? "checked" : "") %> />
                                            RM
                                            <input type="checkbox"
                                                   name="c_commentsNP" <%=Encode.forHtmlAttribute(props.getProperty("c_commentsNP", "").equals("X") ? "checked" : "") %> />
                                            NP
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
