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
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRourkeRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.data.FrmData" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>

<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key='encounter.formRourke2.title'/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="screen"
              href="form/rourkeStyle.css">
        <link rel="stylesheet" type="text/css" media="print"
              href="form/printRourke.css">
    </head>

    <%
        String formClass = "Rourke";
        String formLink = "formrourkep2.jsp";

        int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
        int formId = Integer.parseInt(request.getParameter("formId"));
        int provNo = Integer.parseInt((String) session.getAttribute("user"));
        FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
        java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

        FrmData fd = new FrmData();
        String resource = fd.getResource();
        resource = resource + "Rourke/";
        props.setProperty("c_lastVisited", "p2");
    %>

    <script type="text/javascript" language="Javascript">
        function onPrint() {
//        document.forms[0].submit.value="print";
//        var ret = checkAllDates();
//        if(ret==true)
//        {
//            ret = confirm("<fmt:message key='encounter.formRourke2.msgSavePrintPreview'/>");
//        }
//        return ret;
            window.print();
        }

        function onSave() {
            document.forms[0].submit.value = "save";
            var ret = checkAllDates();
            if (ret == true) {
                ret = confirm("<fmt:message key='encounter.formRourke2.msgSave'/>");
            }
            return ret;
        }

        function onSaveExit() {
            document.forms[0].submit.value = "exit";
            var ret = checkAllDates();
            if (ret == true) {
                ret = confirm("<fmt:message key='encounter.formRourke2.msgSaveExit'/>");
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
            // All characters are numbers.
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
                alert("<fmt:message key='encounter.formRourke2.msgTypeNumbers'/>");
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
               value="<carlos:encode value='<%= props.getProperty("demographic_no", "0") %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="ID"
               value="<carlos:encode value='<%= props.getProperty("ID", "0") %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="provider_no"
               value="<carlos:encode value='<%= StringUtils.noNull(request.getParameter("provNo")) %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="formCreated"
               value="<carlos:encode value='<%= props.getProperty("formCreated", "") %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="form_class" value="<carlos:encode value='<%= formClass %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="form_link" value="<carlos:encode value='<%= formLink %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="formId" value="<carlos:encode value='<%= String.valueOf(formId) %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="c_lastVisited"
               value="<carlos:encode value='<%= props.getProperty("c_lastVisited", "p2") %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="submit" value="exit"/>

        <table cellspacing="0" cellpadding="0" class="Header" class="hidePrint">
            <tr>
                <td nowrap="true"><input type="submit"
                                         value="<fmt:message key='encounter.formRourke2.btnSave'/>"
                                         onclick="javascript:return onSave();"/> <input type="submit"
                                                                                        value="<fmt:message key='encounter.formRourke2.btnSaveExit'/>"
                                                                                        onclick="javascript:return onSaveExit();"/>
                    <input type="submit"
                           value="<fmt:message key='encounter.formRourke2.btnExit'/> "
                           onclick="javascript:return onExit();"/> <input type="button"
                                                                          value="<fmt:message key='encounter.formRourke2.btnPrint'/>"
                                                                          onclick="javascript:return onPrint();"/></td>
                <td align="center" width="100%"><a name="length"
                                                   href="javascript:popup('form/graphLengthWeight?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                    <fmt:message key='encounter.formRourke2.btnGraphLenght'/></a><br>
                    <a name="headCirc"
                       href="javascript:popup('form/graphHeadCirc?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                        <fmt:message key='encounter.formRourke2.btnGraphHead'/></a></td>
                <td nowrap="true"><a
                        href="form/formrourkep1?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>"><fmt:message key='encounter.formRourke2.btnpage1'/></a>&nbsp;|&nbsp; <a><fmt:message key='encounter.formRourke2.msgPage2'/></a>&nbsp;|&nbsp; <a
                        href="form/formrourkep3?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>"><fmt:message key='encounter.formRourke2.btnPage3'/></a></td>
            </tr>
        </table>

        <table cellspacing="0" cellpadding="0" border="0" width="100%">
            <tr class="titleBar">
                <th><fmt:message key='encounter.formRourke2.msgTitle'/></th>
            </tr>
        </table>
        <table cellspacing="0" cellpadding="0" width="100%" border="0">
            <tr valign="top">
                <td nowrap align="center"><fmt:message key='encounter.formRourke2.formBirthRemarks'/><br>
                    <textarea name="c_birthRemarks" cols="17"
                              rows="2"><carlos:encode value='<%= props.getProperty("c_birthRemarks", "") %>' context="html"/></textarea>
                </td>
                <td nowrap align="center"><fmt:message key='encounter.formRourke2.formRiskFactors'/><br>
                    <textarea name="c_riskFactors" rows="2"
                              cols="17"><carlos:encode value='<%= props.getProperty("c_riskFactors", "") %>' context="html"/></textarea>
                </td>
                <td width="65%" nowrap align="center">
                    <p><fmt:message key='encounter.formRourke2.msgName'/>: <input
                            type="text" name="c_pName" maxlength="60" size="30"
                            value="<carlos:encode value='<%= props.getProperty("c_pName", "") %>' context="htmlAttribute"/>" readonly="true"/>
                        &nbsp;&nbsp; <fmt:message key='encounter.formRourke2.msgBirthDate'/> (yyyy/mm/dd): <input
                                type="text" name="c_birthDate" size="10" maxlength="10"
                                value="<carlos:encode value='<%= props.getProperty("c_birthDate", "") %>' context="htmlAttribute"/>" readonly="true">
                        &nbsp;&nbsp; <% if (!((FrmRourkeRecord) rec).isFemale(demoNo)) {
                        %><fmt:message key='encounter.formRourke3.msgMale'/>
                        <%
                        } else {
                        %><fmt:message key='encounter.formRourke3.msgFemale'/>
                        <%
                            }
                        %>
                    </p>
                    <p><fmt:message key='encounter.formRourke2.formLenght'/>:
                        <input type="text" name="c_length" size="6" maxlength="6"
                               value="<carlos:encode value='<%= props.getProperty("c_length", "") %>' context="htmlAttribute"/>"/> cm &nbsp;&nbsp;
                        <fmt:message key='encounter.formRourke2.formHeadCirc'/>: <input
                                type="text" name="c_headCirc" size="6" maxlength="6"
                                value="<carlos:encode value='<%= props.getProperty("c_headCirc", "") %>' context="htmlAttribute"/>"/> cm
                        &nbsp;&nbsp; <fmt:message key='encounter.formRourke2.formBirthWt'/> <input type="text"
                                                                                      name="c_birthWeight" size="6"
                                                                                      maxlength="7"
                                                                                      value="<carlos:encode value='<%= props.getProperty("c_birthWeight", "") %>' context="htmlAttribute"/>"/>
                        <fmt:message key='encounter.formRourke2.msgBirthWtUnit'/> &nbsp;&nbsp; <fmt:message key='encounter.formRourke2.formDischargeWt'/>: <input
                                type="text" name="c_dischargeWeight" size="6" maxlength="7"
                                value="<carlos:encode value='<%= props.getProperty("c_dischargeWeight", "") %>' context="htmlAttribute"/>"> <fmt:message key='encounter.formRourke2.msgDischargeWtUnit'/></p>
                </td>
            </tr>
        </table>
        <table cellspacing="0" cellpadding="0" width="100%" border="1">
            <tr align="center">
                <td class="column"><a>AGE</a><br>
                </td>
                <td colspan="3" class="row"><a><fmt:message key='encounter.formRourke2.form4Months'/></a></td>
                <td colspan="3" class="row"><a><fmt:message key='encounter.formRourke2.form6Months'/></a></td>
                <td colspan="3" class="row"><a><fmt:message key='encounter.formRourke2.form9Months'/></a> <fmt:message key='encounter.formRourke2.msgOptional'/></td>
                <td colspan="3" class="row"><a><fmt:message key='encounter.formRourke2.form12Months'/></a></td>
            </tr>
            <tr align="center">
                <td class="column"><a><fmt:message key='encounter.formRourke2.msgDate'/></a></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p2_date4m"
                                                    size="10" value="<carlos:encode value='<%= props.getProperty("p2_date4m", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p2_date6m"
                                                    size="10" value="<carlos:encode value='<%= props.getProperty("p2_date6m", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p2_date9m"
                                                    size="10" value="<carlos:encode value='<%= props.getProperty("p2_date9m", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text"
                                                    name="p2_date12m" size="10"
                                                    value="<carlos:encode value='<%= props.getProperty("p2_date12m", "") %>' context="htmlAttribute"/>"/></td>
            </tr>
            <tr align="center">
                <td class="column" rowspan="2"><a>GROWTH</a></td>
                <td><fmt:message key='encounter.formRourke2.formHt'/></td>
                <td><fmt:message key='encounter.formRourke2.formWt'/></td>
                <td><fmt:message key='encounter.formRourke2.formHdCirc'/></td>
                <td><fmt:message key='encounter.formRourke2.formHt'/></td>
                <td><fmt:message key='encounter.formRourke2.formWt2'/></td>
                <td><fmt:message key='encounter.formRourke2.formHdCirc'/></td>
                <td><fmt:message key='encounter.formRourke2.formHt'/></td>
                <td><fmt:message key='encounter.formRourke2.formWt'/></td>
                <td><fmt:message key='encounter.formRourke2.formHdCirc'/></td>
                <td><fmt:message key='encounter.formRourke2.formHt'/></td>
                <td><fmt:message key='encounter.formRourke2.formWt3'/></td>
                <td><fmt:message key='encounter.formRourke2.HdCirc47'/></td>
            </tr>
            <tr align="center">
                <td><input type="text" class="wide" name="p2_ht4m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_ht4m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_wt4m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_wt4m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_hc4m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_hc4m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_ht6m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_ht6m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_wt6m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_wt6m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_hc6m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_hc6m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_ht9m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_ht9m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_wt9m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_wt9m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_hc9m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_hc9m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_ht12m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_ht12m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_wt12m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_wt12m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p2_hc12m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p2_hc12m", "") %>' context="htmlAttribute"/>"></td>
            </tr>
            <tr align="center">
                <td class="column"><a><fmt:message key='encounter.formRourke2.msgParentalConcerns'/></a></td>
                <td colspan="3"><textarea name="p2_pConcern4m" cols="25"
                                          rows="2" class="wide"
                                          style="width: 100%"><carlos:encode value='<%= props.getProperty("p2_pConcern4m", "") %>' context="html"/></textarea>
                </td>
                <td colspan="3"><textarea name="p2_pConcern6m" cols="25"
                                          rows="2" class="wide"
                                          style="width: 100%"><carlos:encode value='<%= props.getProperty("p2_pConcern6m", "") %>' context="html"/></textarea>
                </td>
                <td colspan="3"><textarea name="p2_pConcern9m" cols="25"
                                          rows="2" class="wide"
                                          style="width: 100%"><carlos:encode value='<%= props.getProperty("p2_pConcern9m", "") %>' context="html"/></textarea>
                </td>
                <td colspan="3"><textarea name="p2_pConcern12m" cols="25"
                                          rows="2" class="wide"
                                          style="width: 100%"><carlos:encode value='<%= props.getProperty("p2_pConcern12m", "") %>' context="html"/></textarea>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key='encounter.formRourke2.msgnutrition'/></a></td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_nutrition4m" cols="25"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_nutrition4m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_breastFeeding4m"
                                    <carlos:encode value='<%= props.getProperty("p2_breastFeeding4m", "") %>' context="htmlAttribute"/> /></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= (resource == null ? "" : resource) + "n_breastFeeding" %>' context="javaScriptAttribute"/>');return false;"><fmt:message key="encounter.formRourke2.btnBreastFeeding"/></a><fmt:message key="encounter.formRourke2.msgBreastFeedingUnit"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_formulaFeeding4m"
                                    <carlos:encode value='<%= props.getProperty("p2_formulaFeeding4m", "") %>' context="htmlAttribute"/> /></td>
                            <td><fmt:message key='encounter.formRourke2.formFormulaFeeding'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_cereal4m" <carlos:encode value='<%= props.getProperty("p2_cereal4m", "") %>' context="htmlAttribute"/> />
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formIronFortified'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_nutrition6m" cols="25"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_nutrition6m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_breastFeeding6m"
                                    <carlos:encode value='<%= props.getProperty("p2_breastFeeding6m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>n_breastFeeding');return false;"><fmt:message key="encounter.formRourke2.btnBreastFeeding"/></a><fmt:message key="encounter.formRourke2.msgBreastFeedingUnit"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_formulaFeeding6m"
                                    <carlos:encode value='<%= props.getProperty("p2_formulaFeeding6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formFormulaFeedingIronFortified'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_bottle6m" <carlos:encode value='<%= props.getProperty("p2_bottle6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formNoBottles'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_vegFruit6m" <carlos:encode value='<%= props.getProperty("p2_vegFruit6m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formVeg'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_egg6m" <carlos:encode value='<%= props.getProperty("p2_egg6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formNoEgg'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_choking6m" <carlos:encode value='<%= props.getProperty("p2_choking6m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><a href="#"
                                   onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>s_choking');return false;"><fmt:message key="encounter.formRourke2.formChokingSafeFood"/></a>*
                            </td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_nutrition9m" cols="25"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_nutrition9m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_breastFeeding9m"
                                    <carlos:encode value='<%= props.getProperty("p2_breastFeeding9m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>n_breastFeeding');return false;"><fmt:message key="encounter.formRourke2.btnBreastFeeding"/></a><fmt:message key="encounter.formRourke2.msgBreastFeedingUnit"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_formulaFeeding9m"
                                    <carlos:encode value='<%= props.getProperty("p2_formulaFeeding9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formFormulaFeeding'/><br>
                                <fmt:message key='encounter.formRourke2.formIronFortified'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_bottle9m" <carlos:encode value='<%= props.getProperty("p2_bottle9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formNoBottles'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_meat9m" <carlos:encode value='<%= props.getProperty("p2_meat9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formMeat'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_milk9m" <carlos:encode value='<%= props.getProperty("p2_milk9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formMilk'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_egg9m" <carlos:encode value='<%= props.getProperty("p2_egg9m", "") %>' context="htmlAttribute"/>></td>
                            <td>No egg white, nuts or honey</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_choking9m" <carlos:encode value='<%= props.getProperty("p2_choking9m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><a href="#"
                                   onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>s_choking');return false;"><fmt:message key="encounter.formRourke2.formChokingSafeFood"/></a>*
                            </td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_nutrition12m" cols="25"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_nutrition12m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_milk12m" <carlos:encode value='<%= props.getProperty("p2_milk12m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formHomogenizedMilk'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_bottle12m" <carlos:encode value='<%= props.getProperty("p2_bottle12m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formEncourageCup'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_appetite12m"
                                    <carlos:encode value='<%= props.getProperty("p2_appetite12m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formAppetiteReduced'/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr>
                            <td><a><fmt:message key='encounter.formRourke2.msgEducationAdvice'/></a></td>
                        </tr>
                        <tr>
                            <td align="right"><b><fmt:message key='encounter.formRourke2.msgSafety'/></b></td>
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
                            <td align="right"><b><fmt:message key='encounter.formRourke2.msgBehaviour'/></b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td align="right"><b><fmt:message key='encounter.formRourke2.msgFamily'/></b></td>
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
                            <td align="right"><b><fmt:message key='encounter.formRourke2.msgOther'/></b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_educationAdvice4m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_educationAdvice4m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_carSeat4m" <carlos:encode value='<%= props.getProperty("p2_carSeat4m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>s_motorVehicleAccidents');return false;"><fmt:message key="encounter.formRourke2.formCarSeat"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_stairs4m" <carlos:encode value='<%= props.getProperty("p2_stairs4m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formWalker'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_bath4m" <carlos:encode value='<%= props.getProperty("p2_bath4m", "") %>' context="htmlAttribute"/>></td>
                            <td><i><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>s_drowning');return false;"><fmt:message key="encounter.formRourke2.formBathSafety"/></a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_sleeping4m" <carlos:encode value='<%= props.getProperty("p2_sleeping4m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>b_nightWaking');return false;">Night
                                waking/crying</a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_parent4m" <carlos:encode value='<%= props.getProperty("p2_parent4m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formParentChildInteraction'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_childCare4m"
                                    <carlos:encode value='<%= props.getProperty("p2_childCare4m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formChildCare'/></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_family4m" <carlos:encode value='<%= props.getProperty("p2_family4m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formFamilyConflict'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_teething4m" <carlos:encode value='<%= props.getProperty("p2_teething4m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formSiblings'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_educationAdvice6m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_educationAdvice6m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_poison6m" <carlos:encode value='<%= props.getProperty("p2_poison6m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>s_poisons');return false;"><fmt:message key="encounter.formRourke2.btnPoisons"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_electric6m" <carlos:encode value='<%= props.getProperty("p2_electric6m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formElectricPlugs'/></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_sleeping6m" <carlos:encode value='<%= props.getProperty("p2_sleeping6m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>b_nightWaking');return false;"><fmt:message key="encounter.formRourke2.formNightWaking"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_parent6m" <carlos:encode value='<%= props.getProperty("p2_parent6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formParentChildInteraction'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_childCare6m"
                                    <carlos:encode value='<%= props.getProperty("p2_childCare6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formChildCare'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_educationAdvice9m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_educationAdvice9m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_childProof9m"
                                    <carlos:encode value='<%= props.getProperty("p2_childProof9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formChildProofing'/></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_separation9m"
                                    <carlos:encode value='<%= props.getProperty("p2_separation9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formSeparation'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_sleeping9m" <carlos:encode value='<%= props.getProperty("p2_sleeping9m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>b_nightWaking');return false;"><fmt:message key="encounter.formRourke2.formNightWaking"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_dayCare9m" <carlos:encode value='<%= props.getProperty("p2_dayCare9m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>hri_dayCare');return false;"><fmt:message key="encounter.formRourke2.formAssessDay"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_homeVisit9m"
                                    <carlos:encode value='<%= props.getProperty("p2_homeVisit9m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>hri_homeVisits');return false;"><fmt:message key="encounter.formRourke2.formAssessHomeVisit"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_smoke9m" <carlos:encode value='<%= props.getProperty("p2_smoke9m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>o_secondHandSmoke');return false;"><fmt:message key="encounter.formRourke2.formSecondHandSmoke"/></a>*</b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_educationAdvice12m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_educationAdvice12m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_poison12m" <carlos:encode value='<%= props.getProperty("p2_poison12m", "") %>' context="htmlAttribute"/> />
                            </td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>s_poisons');return false;"><fmt:message key="encounter.formRourke2.btnPoisons"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_electric12m"
                                    <carlos:encode value='<%= props.getProperty("p2_electric12m", "") %>' context="htmlAttribute"/> /></td>
                            <td><fmt:message key='encounter.formRourke2.formElectricPlugs'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_carbon12m" <carlos:encode value='<%= props.getProperty("p2_carbon12m", "") %>' context="htmlAttribute"/> />
                            </td>
                            <td>Carbon monoxide/<br>
                                &nbsp;&nbsp;<i><a href="#"
                                                  onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>s_burns');return false;"><fmt:message key="encounter.formRourke2.formSmokeDetectors"/></a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hotWater12m"
                                    <carlos:encode value='<%= props.getProperty("p2_hotWater12m", "") %>' context="htmlAttribute"/> /></td>
                            <td><fmt:message key='encounter.formRourke2.formHotWater'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_sleeping12m"
                                    <carlos:encode value='<%= props.getProperty("p2_sleeping12m", "") %>' context="htmlAttribute"/> /></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>b_nightWaking');return false;"><fmt:message key="encounter.formRourke2.formNightWaking"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_parent12m" <carlos:encode value='<%= props.getProperty("p2_parent12m", "") %>' context="htmlAttribute"/> />
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formParentChildInteraction'/></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_teething12m"
                                    <carlos:encode value='<%= props.getProperty("p2_teething12m", "") %>' context="htmlAttribute"/> /></td>
                            <td><fmt:message key='encounter.formRourke2.formTeething'/><b><a href="#"
                                                                                         onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>o_dentalCare');return false;"><fmt:message key="encounter.formRourke2.btnDentalCare"/></a>*</b></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key='encounter.formRourke2.msgDevelopment'/></a><br>
                    <fmt:message key='encounter.formRourke2.msgDecelopmentDesc'/>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_development4m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_development4m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_turnHead4m" <carlos:encode value='<%= props.getProperty("p2_turnHead4m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formTurnsHead'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_laugh4m" <carlos:encode value='<%= props.getProperty("p2_laugh4m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formLaughs'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_headSteady4m"
                                    <carlos:encode value='<%= props.getProperty("p2_headSteady4m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formHeadSteady'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_grasp4m" <carlos:encode value='<%= props.getProperty("p2_grasp4m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formGrasps'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_concern4m" <carlos:encode value='<%= props.getProperty("p2_concern4m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formNoParentConcern'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_development6m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_development6m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_follow6m" <carlos:encode value='<%= props.getProperty("p2_follow6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formFollowsMovingObjects'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_respond6m" <carlos:encode value='<%= props.getProperty("p2_respond6m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formRespondsName'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_babbles6m" <carlos:encode value='<%= props.getProperty("p2_babbles6m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formBabbles'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_rolls6m" <carlos:encode value='<%= props.getProperty("p2_rolls6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formRollsFromBack'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_sits6m" <carlos:encode value='<%= props.getProperty("p2_sits6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formSitsWithSupport'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_mouth6m" <carlos:encode value='<%= props.getProperty("p2_mouth6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formBringHandsToMouth'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_concern6m" <carlos:encode value='<%= props.getProperty("p2_concern6m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formNoParentConcern'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_development9m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_development9m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_looks9m" <carlos:encode value='<%= props.getProperty("p2_looks9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formLooksForHiddenToy'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_babbles9m" <carlos:encode value='<%= props.getProperty("p2_babbles9m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formDifferentSounds'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_sits9m" <carlos:encode value='<%= props.getProperty("p2_sits9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formSitsWithoutSupport'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_stands9m" <carlos:encode value='<%= props.getProperty("p2_stands9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formStandsWithSupport'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_opposes9m" <carlos:encode value='<%= props.getProperty("p2_opposes9m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formOpposesThumbAndIndex'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_reaches9m" <carlos:encode value='<%= props.getProperty("p2_reaches9m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formReachestobePicked'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_noParentsConcerns9m"
                                    <carlos:encode value='<%= props.getProperty("p2_noParentsConcerns9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formNoParentConcern'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_development12m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_development12m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_understands12m"
                                    <carlos:encode value='<%= props.getProperty("p2_understands12m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formUnderstandsSimpleRequests'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_chatters12m"
                                    <carlos:encode value='<%= props.getProperty("p2_chatters12m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formChatters'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_crawls12m" <carlos:encode value='<%= props.getProperty("p2_crawls12m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formCrawls'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_pulls12m" <carlos:encode value='<%= props.getProperty("p2_pulls12m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formPullsToStand'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_emotions12m"
                                    <carlos:encode value='<%= props.getProperty("p2_emotions12m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formShowsManyEmotions'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_noParentConcerns12m"
                                    <carlos:encode value='<%= props.getProperty("p2_noParentConcerns12m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formNoParentConcern'/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key='encounter.formRourke2.msgPhysicalExamination'/></a><br>
                    <fmt:message key='encounter.formRourke2.msgPhysicalExaminationDesc'/></td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_physical4m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_physical4m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_eyes4m" <carlos:encode value='<%= props.getProperty("p2_eyes4m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formRedReflex'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_cover4m" <carlos:encode value='<%= props.getProperty("p2_cover4m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>pe_cover');return false;"><fmt:message key="encounter.formRourke2.formCover"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hearing4m" <carlos:encode value='<%= props.getProperty("p2_hearing4m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formHearing'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_babbling4m" <carlos:encode value='<%= props.getProperty("p2_babbling4m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formBabbling'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hips4m" <carlos:encode value='<%= props.getProperty("p2_hips4m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formHips'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_physical6m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_physical6m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_fontanelles6m"
                                    <carlos:encode value='<%= props.getProperty("p2_fontanelles6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formFontanelles'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_eyes6m" <carlos:encode value='<%= props.getProperty("p2_eyes6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formRedReflex'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_cover6m" <carlos:encode value='<%= props.getProperty("p2_cover6m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>pe_cover');return false;"><fmt:message key="encounter.formRourke2.formCover"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hearing6m" <carlos:encode value='<%= props.getProperty("p2_hearing6m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formHearing'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hips6m" <carlos:encode value='<%= props.getProperty("p2_hips6m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formHips'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_physical9m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_physical9m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_eyes9m" <carlos:encode value='<%= props.getProperty("p2_eyes9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formRedReflex'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_cover9m" <carlos:encode value='<%= props.getProperty("p2_cover9m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>pe_cover');return false;"><fmt:message key="encounter.formRourke2.formCover"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hearing9m" <carlos:encode value='<%= props.getProperty("p2_hearing9m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formHearing'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_physical12m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_physical12m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_eyes12m" <carlos:encode value='<%= props.getProperty("p2_eyes12m", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><fmt:message key='encounter.formRourke2.formRedReflex'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_cover12m" <carlos:encode value='<%= props.getProperty("p2_cover12m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="#"
                                      onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>pe_cover');return false;"><fmt:message key="encounter.formRourke2.formCover"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hearing12m" <carlos:encode value='<%= props.getProperty("p2_hearing12m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td><fmt:message key='encounter.formRourke2.formHearing'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hips12m" <carlos:encode value='<%= props.getProperty("p2_hips12m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formHips'/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key='encounter.formRourke2.msgProblems'/></a></td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_problems6m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_problems6m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_problems4m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_problems4m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_tb6m" <carlos:encode value='<%= props.getProperty("p2_tb6m", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><fmt:message key='encounter.formRourke2.formTBexposure'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_problems9m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_problems9m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_antiHbs9m" <carlos:encode value='<%= props.getProperty("p2_antiHbs9m", "") %>' context="htmlAttribute"/>>
                            </td>
                            <td width="100%"><b><a href="#"
                                                   onclick="popup('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>i_hepB');return false;"><fmt:message key="encounter.formRourke2.btnAntiHB"/></a><fmt:message key="encounter.formRourke2.formAntiHB"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hgb9m" <carlos:encode value='<%= props.getProperty("p2_hgb9m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formHgb'/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table cellspacing="0" cellpadding="0" width="100%">
                        <tr align="center">
                            <td colspan="2"><textarea name="p2_problems12m"
                                                      class="wide"><carlos:encode value='<%= props.getProperty("p2_problems12m", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_hgb12m" <carlos:encode value='<%= props.getProperty("p2_hgb12m", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><fmt:message key='encounter.formRourke2.formHgb'/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" class="chk"
                                                    name="p2_serum12m" <carlos:encode value='<%= props.getProperty("p2_serum12m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key='encounter.formRourke2.formSerumLead'/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key='encounter.formRourke2.msgImmunization'/></a><br>
                    <fmt:message key='encounter.formRourke2.msgImmunizarionDesc'/>
                </td>
                <td colspan="3" valign="top"><textarea name="p2_immunization4m"
                                                       class="wide"><carlos:encode value='<%= props.getProperty("p2_immunization4m", "") %>' context="html"/></textarea>
                </td>
                <td colspan="3" valign="top"><textarea name="p2_immunization6m"
                                                       class="wide"><carlos:encode value='<%= props.getProperty("p2_immunization6m", "") %>' context="html"/></textarea>
                </td>
                <td colspan="3" valign="top"><textarea name="p2_immunization9m"
                                                       class="wide"><carlos:encode value='<%= props.getProperty("p2_immunization9m", "") %>' context="html"/></textarea>
                </td>
                <td colspan="3" valign="top"><textarea name="p2_immunization12m"
                                                       class="wide"><carlos:encode value='<%= props.getProperty("p2_immunization12m", "") %>' context="html"/></textarea>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key='encounter.formRourke2.formSignature'/></a></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p2_signature4m"
                                       value="<carlos:encode value='<%= props.getProperty("p2_signature4m", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p2_signature6m"
                                       value="<carlos:encode value='<%= props.getProperty("p2_signature6m", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p2_signature9m"
                                       value="<carlos:encode value='<%= props.getProperty("p2_signature9m", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p2_signature12m"
                                       value="<carlos:encode value='<%= props.getProperty("p2_signature12m", "") %>' context="htmlAttribute"/>"/></td>
            </tr>

        </table>

        <table cellspacing="0" cellpadding="0" class="Header" class="hidePrint">
            <tr>
                <td nowrap="true"><input type="submit"
                                         value="<fmt:message key='encounter.formRourke2.btnSave'/>"
                                         onclick="javascript:return onSave();"/> <input type="submit"
                                                                                        value="<fmt:message key='encounter.formRourke2.btnSaveExit'/>"
                                                                                        onclick="javascript:return onSaveExit();"/>
                    <input type="submit"
                           value="<fmt:message key='encounter.formRourke2.btnExit'/>"
                           onclick="javascript:return onExit();"> <input type="button"
                                                                         value="<fmt:message key='encounter.formRourke2.btnPrint'/>"
                                                                         onclick="javascript:return onPrint();"/></td>
                <td align="center" width="100%">
                    <% if (formId > 0) { %> <a name="length"
                                               href="javascript:popup('form/graphLengthWeight?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                    <fmt:message key='encounter.formRourke2.btnGraphLenght'/></a><br>
                    <a name="headCirc"
                       href="javascript:popup('form/graphHeadCirc?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                        <fmt:message key='encounter.formRourke2.btnGraphHead'/></a> <% } else {
                %>&nbsp;<%
                    }
                %>
                </td>
                <td nowrap="true"><a
                        href="form/formrourkep1?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>"><fmt:message key='encounter.formRourke2.btnpage1'/></a>&nbsp;|&nbsp; <a><fmt:message key='encounter.formRourke2.msgPage2'/></a>&nbsp;|&nbsp; <a
                        href="form/formrourkep3?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>"><fmt:message key='encounter.formRourke2.btnPage3'/></a></td>
            </tr>
        </table>

    </form>
    </body>
</html>
