<%--

    Copyright (c) 2001-2002. Department of <fmt:message key="form.rourke.family"/> Medicine, McMaster University. <fmt:message key="form.rourke.all"/> Rights Reserved.
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
    Department of <fmt:message key="form.rourke.family"/> Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
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

<%@ page import="io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRourkeRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.data.FrmData" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="form.rourke.title"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="screen"
              href="form/rourkeStyle.css">
        <link rel="stylesheet" type="text/css" media="print"
              href="form/printRourke.css">
    </head>

    <%
        String formClass = "Rourke";
        String formLink = "formrourke2.jsp";

        int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
        int formId = Integer.parseInt(request.getParameter("formId"));
        int provNo = Integer.parseInt((String) session.getAttribute("user"));
        FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
        java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

        FrmData fd = new FrmData();
        String resource = fd.getResource();
        resource = resource + "Rourke/";
        props.setProperty("c_lastVisited", "2");
    %>

    <script type="text/javascript" language="Javascript">
        function onPrint() {
//        document.forms[0].submit.value="print";
//        var ret = checkAllDates();
//        if(ret==true)
//        {
//            ret = confirm("Do you wish to save this form and view the print preview?");
//        }
//        return ret;
            window.print();
        }

        function onSave() {
            document.forms[0].submit.value = "save";
            var ret = checkAllDates();
            if (ret == true) {
                ret = confirm("<fmt:message key='global.msgWannaSave'/>");
            }
            return ret;
        }

        function onSaveExit() {
            document.forms[0].submit.value = "exit";
            var ret = checkAllDates();
            if (ret == true) {
                ret = confirm("<fmt:message key='global.msgSaveExit'/>");
            }
            return ret;
        }

        /**
         * DHTML date validation script. Courtesy of SmartWebby.com (http://www.smartwebby.com/dhtml/)
         */
// Declaring valid date character, minimum year and maximum year
        var dtCh = "/";
        var minYear = 1900;
        var maxYear = 3100;

        function isInteger(s) {
            var i;
            for (i = 0; i < s.length; i++) {
                // Check that current character is number.
                var c = s.charAt(i);
                if (((c < "0") || (c > "9"))) return false;
            }
            // <fmt:message key="form.rourke.all"/> characters are numbers.
            return true;
        }

        function stripCharsInBag(s, bag) {
            var i;
            var returnString = "";
            // Search through string's characters one by one.
            // If character is not in bag, append to returnString.
            for (i = 0; i < s.length; i++) {
                var c = s.charAt(i);
                if (bag.indexOf(c) == -1) returnString += c;
            }
            return returnString;
        }

        function daysInFebruary(year) {
            // February has 29 days in any year evenly divisible by four,
            // EXCEPT for centurial years which are not also divisible by 400.
            return (((year % 4 == 0) && ((!(year % 100 == 0)) || (year % 400 == 0))) ? 29 : 28);
        }

        function DaysArray(n) {
            for (var i = 1; i <= n; i++) {
                this[i] = 31
                if (i == 4 || i == 6 || i == 9 || i == 11) {
                    this[i] = 30
                }
                if (i == 2) {
                    this[i] = 29
                }
            }
            return this
        }

        function isDate(dtStr) {
            var daysInMonth = DaysArray(12)
            var pos1 = dtStr.indexOf(dtCh)
            var pos2 = dtStr.indexOf(dtCh, pos1 + 1)
            var strMonth = dtStr.substring(0, pos1)
            var strDay = dtStr.substring(pos1 + 1, pos2)
            var strYear = dtStr.substring(pos2 + 1)
            strYr = strYear
            if (strDay.charAt(0) == "0" && strDay.length > 1) strDay = strDay.substring(1)
            if (strMonth.charAt(0) == "0" && strMonth.length > 1) strMonth = strMonth.substring(1)
            for (var i = 1; i <= 3; i++) {
                if (strYr.charAt(0) == "0" && strYr.length > 1) strYr = strYr.substring(1)
            }
            month = parseInt(strMonth)
            day = parseInt(strDay)
            year = parseInt(strYr)
            if (pos1 == -1 || pos2 == -1) {
                return "format"
            }
            if (month < 1 || month > 12) {
                return "month"
            }
            if (day < 1 || day > 31 || (month == 2 && day > daysInFebruary(year)) || day > daysInMonth[month]) {
                return "day"
            }
            if (strYear.length != 4 || year == 0 || year < minYear || year > maxYear) {
                return "year"
            }
            if (dtStr.indexOf(dtCh, pos2 + 1) != -1 || isInteger(stripCharsInBag(dtStr, dtCh)) == false) {
                return "date"
            }
            return true
        }


        function checkTypeIn(obj) {
            if (!checkTypeNum(obj.value)) {
                alert("<fmt:message key='global.msgTypeANumber'/>");
            }
        }

        function valDate(dateBox) {
            try {
                var dateString = dateBox.value;
                if (dateString == "") {
                    //            alert('dateString'+dateString);
                    return true;
                }
                var dt = dateString.split('/');
                var y = dt[0];
                var m = dt[1];
                var d = dt[2];
                var orderString = m + '/' + d + '/' + y;
                var pass = isDate(orderString);

                if (pass != true) {
                    var s = dateBox.name;

                    alert('Invalid ' + pass + ' in field ' + s.substring(3));
                    dateBox.focus();
                    return false;
                }
            } catch (ex) {
                alert(ex);
                alert('<fmt:message key='global.msgInvalidDatePrefix'/>' + dateBox.name);
                dateBox.focus();
                return false;
            }
            return true;
        }

        function checkAllDates() {
            var b = true;
            if (valDate(document.forms[0].p2_date4m) == false) {
                b = false;
            } else if (valDate(document.forms[0].p2_date6m) == false) {
                b = false;
            } else if (valDate(document.forms[0].p2_date9m) == false) {
                b = false;
            } else if (valDate(document.forms[0].p2_date12m) == false) {
                b = false;
            }

            return b;

        }

        function popup(link) {
            windowprops = "height=700, width=960,location=no,"
                + "scrollbars=yes, menubars=no, toolbars=no, resizable=no, top=0, left=0 titlebar=yes";
            window.open(link, "_blank", windowprops);
        }
    </script>


    <body bgproperties="fixed" onLoad="javascript:window.focus()"
          topmargin="0" leftmargin="0" rightmargin="0">
    <form action="${pageContext.request.contextPath}/form/formname" method="post">

        <input type="hidden" name="demographic_no"
               value="<e:forHtmlAttribute value='<%= props.getProperty("demographic_no", "0") %>' />"/>
        <input type="hidden" name="ID"
               value="<e:forHtmlAttribute value='<%= props.getProperty("ID", "0") %>' />"/>
        <input type="hidden" name="provider_no"
               value="<e:forHtmlAttribute value='<%= StringUtils.noNull(request.getParameter("provNo")) %>' />"/>
        <input type="hidden" name="formCreated"
               value="<e:forHtmlAttribute value='<%= props.getProperty("formCreated", "") %>' />"/>
        <input type="hidden" name="form_class" value="<e:forHtmlAttribute value='<%= formClass %>' />"/>
        <input type="hidden" name="form_link" value="<e:forHtmlAttribute value='<%= formLink %>' />"/>
        <input type="hidden" name="formId" value="<e:forHtmlAttribute value='<%= String.valueOf(formId) %>' />"/>
        <input type="hidden" name="c_lastVisited"
               value="<e:forHtmlAttribute value='<%= props.getProperty("c_lastVisited", "2") %>' />"/>
        <input type="hidden" name="submit" value="exit"/>

        <table class="Header" class="hidePrint">
            <tr>
                <td nowrap="true"><input type="submit" value="<fmt:message key='global.save'/>"
                                         onclick="javascript:return onSave();"/> <input type="submit"
                                                                                        value="<fmt:message key='global.saveExit'/>"
                                                                                        onclick="javascript:return onSaveExit();"/>
                    <input
                            type="submit" value="<fmt:message key="global.btnExit"/>" onclick="javascript:return onExit();">
                    <input type="button" value="<fmt:message key='global.btnPrint'/>"
                           onclick="javascript:return onPrint();"/></td>
                <td align="center" width="100%"><a name="length"
                                                   href="javascript:popup('form/graphLengthWeight?demographic_no=<e:forUriComponent value='<%= String.valueOf(demoNo) %>' />&formId=<e:forUriComponent value='<%= String.valueOf(formId) %>' />&provNo=<e:forUriComponent value='<%= String.valueOf(provNo) %>' />');">
                    <fmt:message key="encounter.formRourke2.btnGraphLenght"/></a><br>
                    <a name="headCirc"
                       href="javascript:popup('form/graphHeadCirc?demographic_no=<e:forUriComponent value='<%= String.valueOf(demoNo) %>' />&formId=<e:forUriComponent value='<%= String.valueOf(formId) %>' />&provNo=<e:forUriComponent value='<%= String.valueOf(provNo) %>' />');">
                        <fmt:message key="encounter.formRourke2.btnGraphHead"/></a></td>
                <td nowrap="true"><a
                        href="formrourke1?demographic_no=<e:forUriComponent value='<%= String.valueOf(demoNo) %>' />&formId=<e:forUriComponent value='<%= String.valueOf(formId) %>' />&provNo=<e:forUriComponent value='<%= String.valueOf(provNo) %>' />"><fmt:message key="encounter.formRourke2.btnpage1"/></a>&nbsp;|&nbsp; <a><fmt:message key="encounter.formRourke2.msgPage2"/></a>&nbsp;|&nbsp; <a
                        href="formrourke3?demographic_no=<e:forUriComponent value='<%= String.valueOf(demoNo) %>' />&formId=<e:forUriComponent value='<%= String.valueOf(formId) %>' />&provNo=<e:forUriComponent value='<%= String.valueOf(provNo) %>' />"><fmt:message key="encounter.formRourke2.btnPage3"/></a></td>
            </tr>
        </table>

        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr class="titleBar">
                <th><fmt:message key="form.rourke.title"/>: <fmt:message key="encounter.formRourke2.msgMaintenanceGuide"/>
                </th>
            </tr>
        </table>
        <table width="100%" border="0" cellspacing="1" cellpadding="2">
            <tr valign="top">
                <td nowrap align="center"><fmt:message key="form.rourke.birthRemarks"/><br>
                    <textarea name="c_birthRemarks" rows="2"
                              cols="17"><e:forHtmlContent value='<%= props.getProperty("c_birthRemarks", "") %>' /></textarea>
                </td>
                <td nowrap align="center"><fmt:message key="form.rourke.riskFactorsFamilyHistory"/><br>
                    <textarea name="c_riskFactors" rows="2"
                              cols="17"><e:forHtmlContent value='<%= props.getProperty("c_riskFactors", "") %>' /></textarea>
                </td>
                <td width="65%" nowrap align="center">
                    <%
                        String page2GenderKey = ((FrmRourkeRecord) rec).isFemale(demoNo)
                                ? "encounter.formRourke2.msgFemale"
                                : "encounter.formRourke2.msgMale";
                    %>
                    <p><fmt:message key="encounter.formRourke2.msgName"/>: <input type="text" name="c_pName" maxlength="60"
                                    size="30" value="<e:forHtmlAttribute value='<%= props.getProperty("c_pName", "") %>' />"
                                    readonly="true"/> &nbsp;&nbsp; <fmt:message key="encounter.formRourke2.msgBirthDate"/> (yyyy/mm/dd): <input
                            type="text" name="c_birthDate" size="10" maxlength="10"
                            value="<e:forHtmlAttribute value='<%= props.getProperty("c_birthDate", "") %>' />" readonly="true">
                        &nbsp;&nbsp; <fmt:message key="<%= page2GenderKey %>"/>
                    </p>
                    <p><fmt:message key="encounter.formRourke2.formLenght"/>: <input type="text" name="c_length" size="6"
                                      maxlength="6" value="<e:forHtmlAttribute value='<%= props.getProperty("c_length", "") %>' />"/> cm
                        &nbsp;&nbsp; <fmt:message key="encounter.formRourke2.formHeadCirc"/>: <input type="text" name="c_headCirc" size="6"
                                                       maxlength="6"
                                                       value="<e:forHtmlAttribute value='<%= props.getProperty("c_headCirc", "") %>' />"/>
                        <fmt:message key="encounter.formRourke3.msgHeadCircUnit"/> &nbsp;&nbsp; <fmt:message key="encounter.formRourke2.formBirthWt"/> <input type="text" name="c_birthWeight"
                                                         size="6" maxlength="7"
                                                         value="<e:forHtmlAttribute value='<%= props.getProperty("c_birthWeight", "") %>' />"/> <fmt:message key="encounter.formRourke2.msgBirthWtUnit"/>
                        &nbsp;&nbsp; <fmt:message key="encounter.formRourke2.formDischargeWt"/> <input type="text"
                                                          name="c_dischargeWeight" size="6" maxlength="7"
                                                          value="<e:forHtmlAttribute value='<%= props.getProperty("c_dischargeWeight", "") %>' />">
                        <fmt:message key="encounter.formRourke2.msgDischargeWtUnit"/></p>
                </td>
            </tr>
        </table>
        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr align="center">
                <td class="column"><a><fmt:message key="form.rourke.age"/></a><br>
                </td>
                <td colspan="3" class="row"><a><fmt:message key="encounter.formRourke2.form4Months"/></a></td>
                <td colspan="3" class="row"><a><fmt:message key="encounter.formRourke2.form6Months"/></a></td>
                <td colspan="3" class="row"><a><fmt:message key="encounter.formRourke2.form9Months"/></a> (optional)</td>
                <td colspan="3" class="row"><a><fmt:message key="encounter.formRourke2.form12Months"/></a></td>
            </tr>
            <tr align="center">
                <td class="column"><a><fmt:message key="form.rourke.date"/></a></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p2_date4m"
                                                    size="10" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_date4m", "") %>' />"/></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p2_date6m"
                                                    size="10" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_date6m", "") %>' />"/></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p2_date9m"
                                                    size="10" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_date9m", "") %>' />"/></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text"
                                                    name="p2_date12m" size="10"
                                                    value="<e:forHtmlAttribute value='<%= props.getProperty("p2_date12m", "") %>' />"/></td>
            </tr>
            <tr align="center">
                <td class="column" rowspan="2"><a><fmt:message key="form.rourke.growth"/></a></td>
                <td><fmt:message key="form.rourke.heightShort"/></td>
                <td><fmt:message key="form.rourke.weightShort"/></td>
                <td><fmt:message key="encounter.formRourke2.formHdCirc"/></td>
                <td><fmt:message key="form.rourke.heightShort"/></td>
                <td><fmt:message key="encounter.formRourke2.formWt2"/></td>
                <td><fmt:message key="encounter.formRourke2.formHdCirc"/></td>
                <td><fmt:message key="form.rourke.heightShort"/></td>
                <td><fmt:message key="form.rourke.weightShort"/></td>
                <td><fmt:message key="encounter.formRourke2.formHdCirc"/></td>
                <td><fmt:message key="form.rourke.heightShort"/></td>
                <td><fmt:message key="encounter.formRourke2.formWt3"/></td>
                <td><fmt:message key="encounter.formRourke2.HdCirc47"/></td>
            </tr>
            <tr align="center">
                <td><input type="text" class="wide" name="p2_ht4m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_ht4m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_wt4m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_wt4m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_hc4m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_hc4m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_ht6m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_ht6m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_wt6m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_wt6m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_hc6m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_hc6m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_ht9m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_ht9m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_wt9m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_wt9m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_hc9m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_hc9m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_ht12m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_ht12m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_wt12m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_wt12m", "") %>' />"></td>
                <td><input type="text" class="wide" name="p2_hc12m" size="4"
                           maxlength="5" value="<e:forHtmlAttribute value='<%= props.getProperty("p2_hc12m", "") %>' />"></td>
            </tr>
            <tr align="center">
                <td class="column"><a><fmt:message key="form.rourke.parentalConcerns"/></a></td>
                <td colspan="3"><textarea name="p2_pConcern4m"
                                          style="width: 100%" cols="10"
                                          rows="2"><e:forHtmlContent value='<%= props.getProperty("p2_pConcern4m", "") %>' /></textarea>
                </td>
                <td colspan="3"><textarea name="p2_pConcern6m"
                                          style="width: 100%" cols="10"
                                          rows="2"><e:forHtmlContent value='<%= props.getProperty("p2_pConcern6m", "") %>' /></textarea>
                </td>
                <td colspan="3"><textarea name="p2_pConcern9m"
                                          style="width: 100%" cols="10"
                                          rows="2"><e:forHtmlContent value='<%= props.getProperty("p2_pConcern9m", "") %>' /></textarea>
                </td>
                <td colspan="3"><textarea name="p2_pConcern12m"
                                          style="width: 100%" cols="10"
                                          rows="2"><e:forHtmlContent value='<%= props.getProperty("p2_pConcern12m", "") %>' /></textarea>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.nutrition"/></a></td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_nutrition4m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_nutrition4m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_breastFeeding4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_breastFeeding4m", "") %>' /> /></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />n_breastFeeding"><fmt:message key="encounter.formRourke2.btnBreastFeeding"/></a>*<br>
                                &nbsp;&nbsp;Vit.D 10ug=400IU/day*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_formulaFeeding4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_formulaFeeding4m", "") %>' /> /></td>
                            <td><i><fmt:message key="form.rourke.formulaFeeding"/></i> (Fe fortified)</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_cereal4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_cereal4m", "") %>' /> /></td>
                            <td><i><fmt:message key="form.rourke.ironFortifiedCereal"/></i></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_nutrition6m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_nutrition6m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_breastFeeding6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_breastFeeding6m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />n_breastFeeding">Breast
                                feeding</a>*<br>
                                &nbsp;&nbsp;Vit.D 10ug=400IU/day*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_formulaFeeding6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_formulaFeeding6m", "") %>' />></td>
                            <td><i><fmt:message key="form.rourke.formulaFeeding"/><br>
                                Iron fortified follow-up formula</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_bottle6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_bottle6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formNoBottles"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_vegFruit6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_vegFruit6m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.vegFruits"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_egg6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_egg6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formNoEgg"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_choking6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_choking6m", "") %>' />></td>
                            <td><a href="<e:forHtmlAttribute value='<%= resource %>' />s_choking"><fmt:message key="form.rourke.chokingSafeFood"/></a>*</td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_nutrition9m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_nutrition9m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_breastFeeding9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_breastFeeding9m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />n_breastFeeding">Breast
                                feeding</a>*<br>
                                &nbsp;&nbsp;Vit.D 10ug=400IU/day*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_formulaFeeding9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_formulaFeeding9m", "") %>' />></td>
                            <td><i><fmt:message key="form.rourke.formulaFeeding"/><br>
                                Iron fortified follow-up formula</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_bottle9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_bottle9m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.noBottles"/> in bed</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_meat9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_meat9m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formMeat"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_milk9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_milk9m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formMilk"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_egg9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_egg9m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formNoEgg"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_choking9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_choking9m", "") %>' />></td>
                            <td><a href="<e:forHtmlAttribute value='<%= resource %>' />s_choking"><fmt:message key="form.rourke.chokingSafeFood"/></a>*</td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_nutrition12m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_nutrition12m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_milk12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_milk12m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2006_4.Homo2percent"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_bottle12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_bottle12m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.encourageCupVsBottle"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_appetite12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_appetite12m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.appetiteReduced"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column">
                    <table width="100%">
                        <tr>
                            <td><a>EDUCATION &amp; ADVICE</a></td>
                        </tr>
                        <tr>
                            <td align="right"><b><fmt:message key="form.rourke.safety"/></b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td align="right"><b><fmt:message key="form.rourke.behaviour"/></b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td align="right"><b><fmt:message key="form.rourke.family"/></b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td align="right"><b><fmt:message key="form.rourke.other"/></b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_educationAdvice4m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_educationAdvice4m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_carSeat4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_carSeat4m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />s_motorVehicleAccidents">Car
                                seat (toddler)</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_stairs4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_stairs4m", "") %>' />></td>
                            <td><i><fmt:message key="form.rourke.stairsWalker"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_bath4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_bath4m", "") %>' />></td>
                            <td><i><a href="<e:forHtmlAttribute value='<%= resource %>' />s_drowning">Bath safety*;
                                safe toys</a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_sleeping4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_sleeping4m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />b_nightWaking">Night
                                waking/crying</a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_parent4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_parent4m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.parentChildInteraction"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_childCare4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_childCare4m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formChildCare"/></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_family4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_family4m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.family"/> conflict/stress</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_teething4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_teething4m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formSiblings"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_educationAdvice6m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_educationAdvice6m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_poison6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_poison6m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />s_poisons"><fmt:message key="encounter.formRourke2.btnPoisons"/></a></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_electric6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_electric6m", "") %>' />></td>
                            <td><i><fmt:message key="form.rourke.electricPlugs"/></i></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_sleeping6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_sleeping6m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />b_nightWaking">Night
                                waking/crying</a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_parent6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_parent6m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.parentChildInteraction"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_childCare6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_childCare6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formChildCare"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_educationAdvice9m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_educationAdvice9m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_childProof9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_childProof9m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.childproofing"/></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_separation9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_separation9m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.separationAnxiety"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_sleeping9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_sleeping9m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />b_nightWaking">Night
                                waking/crying</a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_dayCare9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_dayCare9m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />hri_dayCare">Assess day
                                care need</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_homeVisit9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_homeVisit9m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />hri_homeVisits">Assess
                                home visit need</a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_smoke9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_smoke9m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />o_secondHandSmoke">Second
                                hand smoke</a>*</b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_educationAdvice12m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_educationAdvice12m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_poison12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_poison12m", "") %>' /> /></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />s_poisons">Poisons/PCC#</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_electric12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_electric12m", "") %>' /> /></td>
                            <td><i><fmt:message key="encounter.formRourke2017.formElectricPlugs"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_carbon12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_carbon12m", "") %>' /> /></td>
                            <td><fmt:message key="form.rourke.carbonMonoxideSmoke"/>
                                &nbsp;&nbsp;<i><a href="<e:forHtmlAttribute value='<%= resource %>' />s_burns">Smoke
                                    detectors</a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hotWater12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hotWater12m", "") %>' /> /></td>
                            <td><i><fmt:message key="encounter.formRourke2.formHotWater"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_sleeping12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_sleeping12m", "") %>' /> /></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />b_nightWaking">Night
                                waking/crying</a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_parent12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_parent12m", "") %>' /> /></td>
                            <td><fmt:message key="form.rourke.parentChildInteraction"/></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_teething12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_teething12m", "") %>' /> /></td>
                            <td><fmt:message key="form.rourke.teething"/>/<b><a href="<e:forHtmlAttribute value='<%= resource %>' />o_dentalCare"><fmt:message key="form.rourke.dentalCare"/>
                                care</a>*</b></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.development"/></a><br>
                    <fmt:message key="encounter.formRourke2009.msgDevelopmentDesc"/>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_development4m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_development4m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_turnHead4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_turnHead4m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formTurnsHead"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_laugh4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_laugh4m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formLaughs"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_headSteady4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_headSteady4m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formHeadSteady"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_grasp4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_grasp4m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formGrasps"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_concern4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_concern4m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formNoParentConcern"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_development6m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_development6m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_follow6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_follow6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2006_2.formMovingObj"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_respond6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_respond6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formRespondsName"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_babbles6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_babbles6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formBabbles"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_rolls6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_rolls6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formRollsFromBack"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_sits6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_sits6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formSitsWithSupport"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_mouth6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_mouth6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formBringHandsToMouth"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_concern6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_concern6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formNoParentConcern"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_development9m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_development9m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_looks9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_looks9m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formLooksForHiddenToy"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_babbles9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_babbles9m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formDifferentSounds"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_sits9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_sits9m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2006_3.formSits"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_stands9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_stands9m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formStandsWithSupport"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_opposes9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_opposes9m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formOpposesThumbAndIndex"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_reaches9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_reaches9m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2009_2.formreachesGrasps"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_noParentsConcerns9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_noParentsConcerns9m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.noParentConcerns"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_development12m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_development12m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_understands12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_understands12m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2006_3.formSimpleReq"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_chatters12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_chatters12m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formChatters"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_crawls12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_crawls12m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formCrawls"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_pulls12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_pulls12m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formPullsToStand"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_emotions12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_emotions12m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formShowsManyEmotions"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_noParentConcerns12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_noParentConcerns12m", "") %>' />></td>
                            <td><fmt:message key="form.rourke.noParentConcerns"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.physicalExamination"/></a><br>
                    Evidence based screening for specific conditions is highlighted, but
                    an appropriate age-specific focused physical examination is
                    recommended at each visit
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_physical4m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_physical4m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_eyes4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_eyes4m", "") %>' />></td>
                            <td><i><fmt:message key="form.rourke.eyesRedReflex"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_cover4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_cover4m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />pe_cover">Cover/uncover
                                test & inquiry</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hearing4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hearing4m", "") %>' />></td>
                            <td><b><fmt:message key="form.rourke.hearingInquiry"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_babbling4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_babbling4m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formBabbling"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hips4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hips4m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formHips"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_physical6m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_physical6m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p2_fontanelles6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_fontanelles6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formFontanelles"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_eyes6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_eyes6m", "") %>' />></td>
                            <td><i><fmt:message key="form.rourke.eyesRedReflex"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_cover6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_cover6m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />pe_cover">Cover/uncover
                                test & inquiry</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hearing6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hearing6m", "") %>' />></td>
                            <td><b><fmt:message key="form.rourke.hearingInquiry"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hips6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hips6m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formHips"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_physical9m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_physical9m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_eyes9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_eyes9m", "") %>' />></td>
                            <td><i><fmt:message key="form.rourke.eyesRedReflex"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_cover9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_cover9m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />pe_cover">Cover/uncover
                                test &amp; inquiry</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hearing9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hearing9m", "") %>' />></td>
                            <td><b> <fmt:message key="form.rourke.hearingInquiry"/></b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_physical12m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_physical12m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_eyes12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_eyes12m", "") %>' />></td>
                            <td width="100%"><i><fmt:message key="form.rourke.eyesRedReflex"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_cover12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_cover12m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />pe_cover">Cover/uncover
                                test &amp; inquiry</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hearing12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hearing12m", "") %>' />></td>
                            <td><b> <fmt:message key="form.rourke.hearingInquiry"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hips12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hips12m", "") %>' />></td>
                            <td><fmt:message key="encounter.formRourke2.formHips"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a>PROBLEMS &amp; PLANS</a></td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_problems6m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_problems6m", "") %>' />"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_problems4m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_problems4m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_tb6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_tb6m", "") %>' />></td>
                            <td width="100%"><fmt:message key="form.rourke.inquireAboutPossibleTBExposure"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_problems9m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_problems9m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_antiHbs9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_antiHbs9m", "") %>' />></td>
                            <td width="100%"><b><a href="<e:forHtmlAttribute value='<%= resource %>' />i_hepB">Anti-HBs
                                & HbsAG</a>*</b><br>
                                &nbsp;&nbsp;(If HbsAg pos mother)
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hgb9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hgb9m", "") %>' />></td>
                            <td>Hgb. (If at risk)*</td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_problems12m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_problems12m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hgb12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hgb12m", "") %>' />></td>
                            <td width="100%">Hgb. (If at risk)*</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_serum12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_serum12m", "") %>' />></td>
                            <td><i><fmt:message key="form.rourke.serumLeadIfAtRisk"/>*</i></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.immunization"/></a><br>
                    <fmt:message key="encounter.formRourke2.msgGuidelinesMayVary"/>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_immunization4m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_immunization4m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hib4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hib4m", "") %>' />></td>
                            <td width="100%"><b><fmt:message key="form.rourke.hib"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_polio4m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_polio4m", "") %>' />></td>
                            <td><b><fmt:message key="encounter.formRourke2.formAPDT"/></b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_immunization6m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_immunization6m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hib6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hib6m", "") %>' />></td>
                            <td width="100%"><b><fmt:message key="form.rourke.hib"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_polio6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_polio6m", "") %>' />></td>
                            <td><b><fmt:message key="encounter.formRourke2.formAPDT"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_hepB6m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_hepB6m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />i_hepB"><fmt:message key="form.rourke.hepBVaccine"/></a>*</b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_immunization9m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_immunization9m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_tbSkin9m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_tbSkin9m", "") %>' />></td>
                            <td width="100%"><a href="<e:forHtmlAttribute value='<%= resource %>' />i_tbSkinTesting"><fmt:message key="encounter.formRourke2.formTBSkinTest"/></a>
                            </td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p2_immunization12m"
                                                   value="<e:forHtmlAttribute value='<%= props.getProperty("p2_immunization12m", "") %>' />"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_mmr12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_mmr12m", "") %>' />></td>
                            <td width="100%"><b><fmt:message key="form.rourke.mmr"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p2_varicella12m"
                                    <e:forHtmlAttribute value='<%= props.getProperty("p2_varicella12m", "") %>' />></td>
                            <td><b><a href="<e:forHtmlAttribute value='<%= resource %>' />i_varicellaVaccine">Varicella
                                vaccine</a>*</b></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.signature"/></a></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p2_signature4m"
                                       value="<e:forHtmlAttribute value='<%= props.getProperty("p2_signature4m", "") %>' />"/></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p2_signature6m"
                                       value="<e:forHtmlAttribute value='<%= props.getProperty("p2_signature6m", "") %>' />"/></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p2_signature9m"
                                       value="<e:forHtmlAttribute value='<%= props.getProperty("p2_signature9m", "") %>' />"/></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p2_signature12m"
                                       value="<e:forHtmlAttribute value='<%= props.getProperty("p2_signature12m", "") %>' />"/></td>
            </tr>

        </table>

        <table class="Header" class="hidePrint">
            <tr>
                <td nowrap="true"><input type="submit" value="<fmt:message key='global.save'/>"
                                         onclick="javascript:return onSave();"/> <input type="submit"
                                                                                        value="<fmt:message key='global.saveExit'/>"
                                                                                        onclick="javascript:return onSaveExit();"/>
                    <input
                            type="submit" value="<fmt:message key="global.btnExit"/>" onclick="javascript:return onExit();">
                    <input type="button" value="<fmt:message key='global.btnPrint'/>"
                           onclick="javascript:return onPrint();"/></td>
                <td align="center" width="100%">
                    <% if (formId > 0) { %> <a name="length"
                                               href="javascript:popup('form/graphLengthWeight?demographic_no=<e:forUriComponent value='<%= String.valueOf(demoNo) %>' />&formId=<e:forUriComponent value='<%= String.valueOf(formId) %>' />&provNo=<e:forUriComponent value='<%= String.valueOf(provNo) %>' />');">
                    <fmt:message key="encounter.formRourke2.btnGraphLenght"/></a><br>
                    <a name="headCirc"
                       href="javascript:popup('form/graphHeadCirc?demographic_no=<e:forUriComponent value='<%= String.valueOf(demoNo) %>' />&formId=<e:forUriComponent value='<%= String.valueOf(formId) %>' />&provNo=<e:forUriComponent value='<%= String.valueOf(provNo) %>' />');">
                        <fmt:message key="encounter.formRourke2.btnGraphHead"/></a> <% } else {
                %>&nbsp;<%
                    }
                %>
                </td>
                <td nowrap="true"><a
                        href="formrourke1?demographic_no=<e:forUriComponent value='<%= String.valueOf(demoNo) %>' />&formId=<e:forUriComponent value='<%= String.valueOf(formId) %>' />&provNo=<e:forUriComponent value='<%= String.valueOf(provNo) %>' />"><fmt:message key="encounter.formRourke2.btnpage1"/></a>&nbsp;|&nbsp; <a><fmt:message key="form.rourke.page2"/></a>&nbsp;|&nbsp; <a
                        href="formrourke3?demographic_no=<e:forUriComponent value='<%= String.valueOf(demoNo) %>' />&formId=<e:forUriComponent value='<%= String.valueOf(formId) %>' />&provNo=<e:forUriComponent value='<%= String.valueOf(provNo) %>' />"><fmt:message key="encounter.formRourke2.btnPage3"/></a></td>
            </tr>
        </table>

    </form>
    </body>
</html>
