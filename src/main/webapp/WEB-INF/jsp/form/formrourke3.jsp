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
<%@ taglib uri="carlos" prefix="carlos" %>
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
        String formLink = "formrourke3.jsp";

        int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
        int formId = Integer.parseInt(request.getParameter("formId"));
        int provNo = Integer.parseInt((String) session.getAttribute("user"));
        FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
        java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

        FrmData fd = new FrmData();
        String resource = fd.getResource();
        resource = resource + "Rourke/";
        props.setProperty("c_lastVisited", "3");
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
                alert('<fmt:message key='global.msgInvalidDatePrefix'/>' + dateBox.name);
                dateBox.focus();
                return false;
            }
            return true;
        }

        function checkAllDates() {
            var b = true;
            if (valDate(document.forms[0].p3_date18m) == false) {
                b = false;
            } else if (valDate(document.forms[0].p3_date2y) == false) {
                b = false;
            } else if (valDate(document.forms[0].p3_date4y) == false) {
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


    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">
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
               value="<carlos:encode value='<%= props.getProperty("c_lastVisited", "3") %>' context="htmlAttribute"/>"/>
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
                                                   href="javascript:popup('form/graphLengthWeight?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                    <fmt:message key="encounter.formRourke3.btnGraphLenght"/></a><br>
                    <a name="headCirc"
                       href="javascript:popup('form/graphHeadCirc?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                        <fmt:message key="encounter.formRourke3.btnGraphHead"/></a></td>
                <td nowrap="true"><a
                        href="formrourke1?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>">Page
                    <fmt:message key="encounter.formRourke3.btnPage1"/></a>&nbsp;|&nbsp; <a
                        href="formrourke2?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>">Page
                    <fmt:message key="encounter.formRourke3.btnPage2"/></a>&nbsp;|&nbsp; <a><fmt:message key="encounter.formRourke3.msgPage3"/></a></td>
            </tr>
        </table>

        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr class="titleBar">
                <th><fmt:message key="form.rourke.title"/>: <fmt:message key="encounter.formRourke3.msgMaintenanceGuide"/></th>
            </tr>
        </table>
        <table width="100%" border="0" cellspacing="1" cellpadding="2">
            <tr valign="top">
                <td nowrap align="center"><fmt:message key="form.rourke.birthRemarks"/><br>
                    <textarea name="c_birthRemarks" rows="2"
                              cols="17"><carlos:encode value='<%= props.getProperty("c_birthRemarks", "") %>' context="html"/></textarea>
                </td>
                <td nowrap align="center"><fmt:message key="form.rourke.riskFactorsFamilyHistory"/><br>
                    <textarea name="c_riskFactors" rows="2"
                              cols="17"><carlos:encode value='<%= props.getProperty("c_riskFactors", "") %>' context="html"/></textarea>
                </td>
                <td width="65%" nowrap align="center">
                    <%
                        String page3GenderKey = ((FrmRourkeRecord) rec).isFemale(demoNo)
                                ? "encounter.formRourke3.msgFemale"
                                : "encounter.formRourke3.msgMale";
                    %>
                    <p><fmt:message key="encounter.formRourke3.msgName"/>: <input type="text" name="c_pName" maxlength="60"
                                    size="30" value="<carlos:encode value='<%= props.getProperty("c_pName", "") %>' context="htmlAttribute"/>"
                                    readonly="true"/> &nbsp;&nbsp; <fmt:message key="encounter.formRourke3.msgBirthDate"/> (yyyy/mm/dd): <input
                            type="text" name="c_birthDate" size="10" maxlength="10"
                            value="<carlos:encode value='<%= props.getProperty("c_birthDate", "") %>' context="htmlAttribute"/>" readonly="true">
                        &nbsp;&nbsp; <fmt:message key="<%= page3GenderKey %>"/>
                    </p>
                    <p><fmt:message key="encounter.formRourke3.formLenght"/>: <input type="text" name="c_length" size="6"
                                      maxlength="6" value="<carlos:encode value='<%= props.getProperty("c_length", "") %>' context="htmlAttribute"/>"/> cm
                        &nbsp;&nbsp; <fmt:message key="encounter.formRourke3.formHeadCirc"/>: <input type="text" name="c_headCirc" size="6"
                                                       maxlength="6"
                                                       value="<carlos:encode value='<%= props.getProperty("c_headCirc", "") %>' context="htmlAttribute"/>"/>
                        <fmt:message key="encounter.formRourke3.msgHeadCircUnit"/> &nbsp;&nbsp; <fmt:message key="encounter.formRourke3.formBirthWt"/>: <input type="text" name="c_birthWeight"
                                                         size="6" maxlength="7"
                                                         value="<carlos:encode value='<%= props.getProperty("c_birthWeight", "") %>' context="htmlAttribute"/>"/> kg
                        &nbsp;&nbsp; <fmt:message key="encounter.formRourke3.formDischargeWt"/>: <input type="text"
                                                          name="c_dischargeWeight" size="6" maxlength="7"
                                                          value="<carlos:encode value='<%= props.getProperty("c_dischargeWeight", "") %>' context="htmlAttribute"/>">
                        <fmt:message key="encounter.formRourke3.msgDischargeWtUnit"/></p>
                </td>
            </tr>
        </table>
        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr align="center">
                <td class="column"><a><fmt:message key="encounter.formRourke3.msgAge"/></a></td>
                <td class="row"><a><fmt:message key="encounter.formRourke2006_4.msg18mos"/></a></td>
                <td class="row"><a><fmt:message key="encounter.formRourke3.msg2-3years"/></a></td>
                <td class="row"><a><fmt:message key="encounter.formRourke3.msg4-5years"/></a></td>
            </tr>
            <tr align="center">
                <td class="column"><a><fmt:message key="encounter.formRourke3.msgDate"/></a></td>
                <td>(yyyy/mm/dd) <input type="text" name="p3_date18m" size="10"
                                        value="<carlos:encode value='<%= props.getProperty("p3_date18m", "") %>' context="htmlAttribute"/>"/></td>
                <td>(yyyy/mm/dd) <input type="text" name="p3_date2y" size="10"
                                        value="<carlos:encode value='<%= props.getProperty("p3_date2y", "") %>' context="htmlAttribute"/>"/></td>
                <td>(yyyy/mm/dd) <input type="text" name="p3_date4y" size="10"
                                        value="<carlos:encode value='<%= props.getProperty("p3_date4y", "") %>' context="htmlAttribute"/>"/></td>
            </tr>
            <tr align="center">
                <td class="column"><a><fmt:message key="encounter.formRourke3.msgGrowth"/></a></td>
                <td>
                    <table width="100%">
                        <tr>
                            <td align="center"><fmt:message key="form.rourke.heightShort"/><br>
                                <input type="text" class="wide" name="p3_ht18m" size="4"
                                       maxlength="5" value="<carlos:encode value='<%= props.getProperty("p3_ht18m", "") %>' context="htmlAttribute"/>"></td>
                            <td align="center"><fmt:message key="form.rourke.weightShort"/><br>
                                <input type="text" class="wide" name="p3_wt18m" size="4"
                                       maxlength="5" value="<carlos:encode value='<%= props.getProperty("p3_wt18m", "") %>' context="htmlAttribute"/>"></td>
                            <td align="center"><fmt:message key="form.rourke.headCircumferenceShort"/><br>
                                <input type="text" class="wide" name="p3_hc18m" size="4"
                                       maxlength="5" value="<carlos:encode value='<%= props.getProperty("p3_hc18m", "") %>' context="htmlAttribute"/>"></td>
                        </tr>
                    </table>
                </td>
                <td>
                    <table width="100%">
                        <tr>
                            <td align="center"><fmt:message key="form.rourke.heightShort"/><br>
                                <input type="text" class="wide" name="p3_ht2y" size="4"
                                       maxlength="5" value="<carlos:encode value='<%= props.getProperty("p3_ht2y", "") %>' context="htmlAttribute"/>"></td>
                            <td align="center"><fmt:message key="form.rourke.weightShort"/><br>
                                <input type="text" class="wide" name="p3_wt2y" size="4"
                                       maxlength="5" value="<carlos:encode value='<%= props.getProperty("p3_wt2y", "") %>' context="htmlAttribute"/>"></td>
                        </tr>
                    </table>
                </td>
                <td>
                    <table width="100%">
                        <tr>
                            <td align="center"><fmt:message key="form.rourke.heightShort"/><br>
                                <input type="text" class="wide" name="p3_ht4y" size="4"
                                       maxlength="5" value="<carlos:encode value='<%= props.getProperty("p3_ht4y", "") %>' context="htmlAttribute"/>"></td>
                            <td align="center"><fmt:message key="form.rourke.weightShort"/><br>
                                <input type="text" class="wide" name="p3_wt4y" size="4"
                                       maxlength="5" value="<carlos:encode value='<%= props.getProperty("p3_wt4y", "") %>' context="htmlAttribute"/>"></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr align="center">
                <td class="column"><a><fmt:message key="form.rourke.parentalConcerns"/></a></td>
                <td><textarea name="p3_pConcern18m" style="width: 100%"
                              cols="10" rows="2"><carlos:encode value='<%= props.getProperty("p3_pConcern18m", "") %>' context="html"/></textarea>
                </td>
                <td><textarea name="p3_pConcern2y" style="width: 100%" cols="10"
                              rows="2"><carlos:encode value='<%= props.getProperty("p3_pConcern2y", "") %>' context="html"/></textarea></td>
                <td><textarea name="p3_pConcern4y" style="width: 100%" cols="10"
                              rows="2"><carlos:encode value='<%= props.getProperty("p3_pConcern4y", "") %>' context="html"/></textarea></td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.nutrition"/></a>:</td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_nutrition18m"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_nutrition18m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_bottle18m"
                                    <carlos:encode value='<%= props.getProperty("p3_bottle18m", "") %>' context="htmlAttribute"/> /></td>
                            <td width="100%"><fmt:message key="form.rourke.noBottles"/></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_nutrition2y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_nutrition2y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_milk2y"
                                    <carlos:encode value='<%= props.getProperty("p3_milk2y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><fmt:message key="encounter.formRourke3.formHomogenized"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_food2y"
                                    <carlos:encode value='<%= props.getProperty("p3_food2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="encounter.formRourke3.formFoodGuide"/></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_nutrition4y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_nutrition4y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_milk4y"
                                    <carlos:encode value='<%= props.getProperty("p3_milk4y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><fmt:message key="encounter.formRourke2006_4.form2percentMilk"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_food4y"
                                    <carlos:encode value='<%= props.getProperty("p3_food4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="encounter.formRourke3.formFoodGuide"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column" valign="top">
                    <table width="100%">
                        <tr>
                            <td align="center" nowrap="true"><b><fmt:message key="encounter.formRourke2.msgEducationAdvice"/></b></td>
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
                            <td align="right"><b><fmt:message key="form.rourke.other"/></b></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_educationAdvice18m"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_educationAdvice18m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_bath18m"
                                    <carlos:encode value='<%= props.getProperty("p3_bath18m", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><i><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_drowning"><fmt:message key="encounter.formRourke3.btnbathSafety"/></a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_choking18m"
                                    <carlos:encode value='<%= props.getProperty("p3_choking18m", "") %>' context="htmlAttribute"/>></td>
                            <td><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_choking"><fmt:message key="form.rourke.chokingSafeToys"/></a>*</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p3_temperment18m"
                                    <carlos:encode value='<%= props.getProperty("p3_temperment18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.temperment"/></td>
                        </tr>
                        <tr>
                            <td valign="top">&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_limit18m"
                                    <carlos:encode value='<%= props.getProperty("p3_limit18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.limitSetting"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_social18m"
                                    <carlos:encode value='<%= props.getProperty("p3_social18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.socializingOpportunities"/></td>
                        </tr>
                        <tr>
                            <td valign="top">&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_dental18m"
                                    <carlos:encode value='<%= props.getProperty("p3_dental18m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>o_dentalCare"><fmt:message key="form.rourke.dentalCare"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_toilet18m"
                                    <carlos:encode value='<%= props.getProperty("p3_toilet18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.toiletTraining"/></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_educationAdvice2y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_educationAdvice2y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_bike2y"
                                    <carlos:encode value='<%= props.getProperty("p3_bike2y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><i><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_falls">Bike
                                Helmets</a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_matches2y"
                                    <carlos:encode value='<%= props.getProperty("p3_matches2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.matches"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_carbon2y"
                                    <carlos:encode value='<%= props.getProperty("p3_carbon2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.carbonMonoxideSmoke"/> <i><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_burns">Smoke
                                detectors</a>*</i></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_parent2y"
                                    <carlos:encode value='<%= props.getProperty("p3_parent2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.parentChildInteraction"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_social2y"
                                    <carlos:encode value='<%= props.getProperty("p3_social2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.socializingOpportunities"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_dayCare2y"
                                    <carlos:encode value='<%= props.getProperty("p3_dayCare2y", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>hri_dayCare"><fmt:message key="encounter.formRourke3.formAssessDayCare"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_dental2y"
                                    <carlos:encode value='<%= props.getProperty("p3_dental2y", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>o_dentalCare"><fmt:message key="encounter.formRourke2006_4.formDentalCleaning"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_toilet2y"
                                    <carlos:encode value='<%= props.getProperty("p3_toilet2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.toiletTraining"/></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_educationAdvice4y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_educationAdvice4y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_bike4y"
                                    <carlos:encode value='<%= props.getProperty("p3_bike4y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><i><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_falls"><fmt:message key="encounter.formRourke3.formBikeHelmets"/></a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_matches4y"
                                    <carlos:encode value='<%= props.getProperty("p3_matches4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.matches"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_carbon4y"
                                    <carlos:encode value='<%= props.getProperty("p3_carbon4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.carbonMonoxideSmoke"/> <i><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_burns"><fmt:message key="encounter.formRourke3.formSmokeDetectors"/></a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_water4y"
                                    <carlos:encode value='<%= props.getProperty("p3_water4y", "") %>' context="htmlAttribute"/>></td>
                            <td><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_drowning"><fmt:message key="encounter.formRourke3.formWaterSafety"/></a></td>
                        </tr>
                        <tr>
                            <td valign="top">&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top">&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_social4y"
                                    <carlos:encode value='<%= props.getProperty("p3_social4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.socializingOpportunities"/></td>
                        </tr>
                        <tr>
                            <td valign="top">&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_dental4y"
                                    <carlos:encode value='<%= props.getProperty("p3_dental4y", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>o_dentalCare"><fmt:message key="encounter.formRourke2006_4.formDentalCleaning"/></a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_school4y"
                                    <carlos:encode value='<%= props.getProperty("p3_school4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.schoolReadiness"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column">
                    <div align="center"><b><fmt:message key="form.rourke.development"/></b><br>
                        (Inquiry &amp; observation of milestones)<br>
                        Tasks are set after the time of normal milestone acquisition.<br>
                        Absence of any item suggests the need for further assessment of
                        development<br>
                    </div>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_development18m"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_development18m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_points18m"
                                    <carlos:encode value='<%= props.getProperty("p3_points18m", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%">Points to pictures (eg. show me the ...) and
                                to 3 different body parts
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_words18m"
                                    <carlos:encode value='<%= props.getProperty("p3_words18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.atLeast5Words"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_picks18m"
                                    <carlos:encode value='<%= props.getProperty("p3_picks18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.picksUpAndEatsFingerFood"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_walks18m"
                                    <carlos:encode value='<%= props.getProperty("p3_walks18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.walksAlone"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_stacks18m"
                                    <carlos:encode value='<%= props.getProperty("p3_stacks18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.stacksAtLeast3Blocks"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_affection18m"
                                    <carlos:encode value='<%= props.getProperty("p3_affection18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.showsAffection"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p3_showParents18m"
                                    <carlos:encode value='<%= props.getProperty("p3_showParents18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.pointsToShowParentSomething"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_looks18m"
                                    <carlos:encode value='<%= props.getProperty("p3_looks18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.looksAtYouWhenTalkingPlayingTogether"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p3_noParentsConcerns18m"
                                    <carlos:encode value='<%= props.getProperty("p3_noParentsConcerns18m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.noParentConcerns"/></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_development2y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_development2y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td colspan="2"><b><fmt:message key="encounter.formRourke2006_4.form2yrs"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_word2y"
                                    <carlos:encode value='<%= props.getProperty("p3_word2y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><fmt:message key="form.rourke.atLeast1NewWordWeek"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_sentence2y"
                                    <carlos:encode value='<%= props.getProperty("p3_sentence2y", "") %>' context="htmlAttribute"/>></td>
                            <td>2-word sentences</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_run2y"
                                    <carlos:encode value='<%= props.getProperty("p3_run2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.triesToRun"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_container2y"
                                    <carlos:encode value='<%= props.getProperty("p3_container2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.putsObjectsIntoSmallContainer"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_copies2y"
                                    <carlos:encode value='<%= props.getProperty("p3_copies2y", "") %>' context="htmlAttribute"/>></td>
                            <td>Copies adult's actions</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_skills2y"
                                    <carlos:encode value='<%= props.getProperty("p3_skills2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.continuesToDevelopNewSkills"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p3_noParentsConcerns2y"
                                    <carlos:encode value='<%= props.getProperty("p3_noParentsConcerns2y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.noParentConcerns"/></td>
                        </tr>
                    </table>
                    <br>
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_development3y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_development3y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td colspan="2"><b><fmt:message key="encounter.formRourke2006_4.form3yrs"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p3_understands3y"
                                    <carlos:encode value='<%= props.getProperty("p3_understands3y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><fmt:message key="form.rourke.understands2StepDirection"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_twists3y"
                                    <carlos:encode value='<%= props.getProperty("p3_twists3y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.twistsLidsOffJarsOrTurnsKnobs"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_turnPages3y"
                                    <carlos:encode value='<%= props.getProperty("p3_turnPages3y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.turnsPagesOneAtATime"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_share3y"
                                    <carlos:encode value='<%= props.getProperty("p3_share3y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.shareSomeOfTheTime"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_listens3y"
                                    <carlos:encode value='<%= props.getProperty("p3_listens3y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.listensToMusicOrStories"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p3_noParentsConcerns3y"
                                    <carlos:encode value='<%= props.getProperty("p3_noParentsConcerns3y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.noParentConcerns"/></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_development4y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_development4y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td colspan="2"><b><fmt:message key="encounter.formRourke2006_4.form4yrs"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p3_understands4y"
                                    <carlos:encode value='<%= props.getProperty("p3_understands4y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><fmt:message key="form.rourke.understandsRelated3PartDirection"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_questions4y"
                                    <carlos:encode value='<%= props.getProperty("p3_questions4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.asksALotOfQuestions"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_oneFoot4y"
                                    <carlos:encode value='<%= props.getProperty("p3_oneFoot4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.standsOn1FootFor1To3Seconds"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_draws4y"
                                    <carlos:encode value='<%= props.getProperty("p3_draws4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.drawsAPersonWithAtLeast3BodyParts"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_toilet4y"
                                    <carlos:encode value='<%= props.getProperty("p3_toilet4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.toiletTrainedDuringTheDay"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_comfort4y"
                                    <carlos:encode value='<%= props.getProperty("p3_comfort4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.triesToComfortSomeoneUpset"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p3_noParentsConcerns4y"
                                    <carlos:encode value='<%= props.getProperty("p3_noParentsConcerns4y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.noParentConcerns"/></td>
                        </tr>
                    </table>
                    <br>
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_development5y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_development5y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td colspan="2"><b><fmt:message key="encounter.formRourke2006_4.form5yrs"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_counts5y"
                                    <carlos:encode value='<%= props.getProperty("p3_counts5y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><fmt:message key="encounter.formRourke3.formCounts10"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_speaks5y"
                                    <carlos:encode value='<%= props.getProperty("p3_speaks5y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.speaksClearlyInSentences"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_ball5y"
                                    <carlos:encode value='<%= props.getProperty("p3_ball5y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.throwsAndCatchesABall"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_hops5y"
                                    <carlos:encode value='<%= props.getProperty("p3_hops5y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.hopsOn1Foot"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_shares5y"
                                    <carlos:encode value='<%= props.getProperty("p3_shares5y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.sharesWillingly"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_alone5y"
                                    <carlos:encode value='<%= props.getProperty("p3_alone5y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.worksAloneAtActivity"/></td>
                        </tr>

                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_separate5y"
                                    <carlos:encode value='<%= props.getProperty("p3_separate5y", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.separatesEasilyFromParents"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p3_noParentsConcerns5y"
                                    <carlos:encode value='<%= props.getProperty("p3_noParentsConcerns5y", "") %>' context="htmlAttribute"/>></td>
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
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_physical18m"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_physical18m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_eyes18m"
                                    <carlos:encode value='<%= props.getProperty("p3_eyes18m", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><i><fmt:message key="form.rourke.eyesRedReflex"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_cover18m"
                                    <carlos:encode value='<%= props.getProperty("p3_cover18m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>pe_cover">Cover/uncover
                                test & inquiry</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_hearing18m"
                                    <carlos:encode value='<%= props.getProperty("p3_hearing18m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><fmt:message key="form.rourke.hearingInquiry"/></b></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_physical2y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_physical2y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_visual2y"
                                    <carlos:encode value='<%= props.getProperty("p3_visual2y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><i><fmt:message key="form.rourke.visualAcuity"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_cover2y"
                                    <carlos:encode value='<%= props.getProperty("p3_cover2y", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>pe_cover">Cover/uncover
                                test & inquiry</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_hearing2y"
                                    <carlos:encode value='<%= props.getProperty("p3_hearing2y", "") %>' context="htmlAttribute"/>></td>
                            <td><b><fmt:message key="form.rourke.hearingInquiry"/></b></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_physical4y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_physical4y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_visual4y"
                                    <carlos:encode value='<%= props.getProperty("p3_visual4y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><i><fmt:message key="form.rourke.visualAcuity"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_cover4y"
                                    <carlos:encode value='<%= props.getProperty("p3_cover4y", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>pe_cover">Cover/uncover
                                test &amp; inquiry</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_hearing4y"
                                    <carlos:encode value='<%= props.getProperty("p3_hearing4y", "") %>' context="htmlAttribute"/>></td>
                            <td><b><fmt:message key="form.rourke.hearingInquiry"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_blood4y"
                                    <carlos:encode value='<%= props.getProperty("p3_blood4y", "") %>' context="htmlAttribute"/>></td>
                            <td><i><fmt:message key="form.rourke.bloodPressure"/></i></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column">
                    <div align="center"><b><fmt:message key="encounter.formRourke3.msgProblemsPlans"/></b></div>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_problems18m"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_problems18m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_problems2y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_problems2y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_serum2y"
                                    <carlos:encode value='<%= props.getProperty("p3_serum2y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><i><a
                                    href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>pp_leadScreening"><fmt:message key="form.rourke.serumLeadIfAtRisk"/></a>*</i></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_problems4y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_problems4y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column">
                    <div align="center"><b><fmt:message key="form.rourke.immunization"/></b><br>
                        <fmt:message key="encounter.formRourke3.msgGuidelinesMayVary"/>
                    </div>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_immunization18m"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_immunization18m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_hib18m"
                                    <carlos:encode value='<%= props.getProperty("p3_hib18m", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><b><fmt:message key="form.rourke.hib"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_polio18m"
                                    <carlos:encode value='<%= props.getProperty("p3_polio18m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><fmt:message key="encounter.formRourke3.formPolio"/></b></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td><input type="text" class="wide" name="p3_immunization2y"
                                       value="<carlos:encode value='<%= props.getProperty("p3_immunization2y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                    </table>
                </td>
                <td valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p3_immunization4y"
                                                   value="<carlos:encode value='<%= props.getProperty("p3_immunization4y", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_mmr4y"
                                    <carlos:encode value='<%= props.getProperty("p3_mmr4y", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><b><fmt:message key="form.rourke.mmr"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p3_polio4y"
                                    <carlos:encode value='<%= props.getProperty("p3_polio4y", "") %>' context="htmlAttribute"/>></td>
                            <td><b><fmt:message key="encounter.formRourke3.formPolio"/></b></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.signature"/></a></td>
                <td><input type="text" class="wide" style="width: 100%"
                           name="p3_signature18m"
                           value="<carlos:encode value='<%= props.getProperty("p3_signature18m", "") %>' context="htmlAttribute"/>"/></td>
                <td><input type="text" class="wide" style="width: 100%"
                           name="p3_signature2y"
                           value="<carlos:encode value='<%= props.getProperty("p3_signature2y", "") %>' context="htmlAttribute"/>"/></td>
                <td><input type="text" class="wide" style="width: 100%"
                           name="p3_signature4y"
                           value="<carlos:encode value='<%= props.getProperty("p3_signature4y", "") %>' context="htmlAttribute"/>"/></td>
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
                <td align="center" width="100%"><a name="length"
                                                   href="javascript:popup('form/graphLengthWeight?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                    <fmt:message key="encounter.formRourke3.btnGraphLenght"/></a><br>
                    <a name="headCirc"
                       href="javascript:popup('form/graphHeadCirc?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                        <fmt:message key="encounter.formRourke3.btnGraphHead"/></a></td>
                <td nowrap="true"><a
                        href="formrourke1?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>"><fmt:message key="encounter.formRourke3.btnPage1"/></a>&nbsp;|&nbsp; <a
                        href="formrourke2?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>"><fmt:message key="encounter.formRourke3.btnPage2"/></a>&nbsp;|&nbsp; <a><fmt:message key="form.rourke.page3"/></a></td>
            </tr>
        </table>

    </form>
    </body>
</html>
