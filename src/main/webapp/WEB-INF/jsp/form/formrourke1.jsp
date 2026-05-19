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
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRourkeRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.data.FrmData" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>



<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="form.rourke.title"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="screen"
              href="form/rourkeStyle.css">
        <link rel="stylesheet" type="text/css" media="print"
              href="form/printRourke.css">

        <%
            String formClass = "Rourke";
            String formLink = "formrourke1.jsp";

            int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
            int formId = Integer.parseInt(request.getParameter("formId"));
            int provNo = Integer.parseInt((String) session.getAttribute("user"));
            FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
            java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

            FrmData fd = new FrmData();
            String resource = fd.getResource();
            resource = resource + "Rourke/";
            props.setProperty("c_lastVisited", "1");
        %>
    </head>

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
            if (valDate(document.forms[0].p1_date1w) == false) {
                b = false;
            } else if (valDate(document.forms[0].p1_date2w) == false) {
                b = false;
            } else if (valDate(document.forms[0].p1_date1m) == false) {
                b = false;
            } else if (valDate(document.forms[0].p1_date2m) == false) {
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
               value="<carlos:encode value='<%= props.getProperty("c_lastVisited", "1") %>' context="htmlAttribute"/>"/>
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
                    Graph Length and Weight</a><br>
                    <a name="headCirc"
                       href="javascript:popup('form/graphHeadCirc?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                        Graph Head Circumference</a></td>
                <td nowrap="true"><a><fmt:message key="form.rourke.page1"/></a>&nbsp;|&nbsp; <a
                        href="formrourke2?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>">Page
                    2</a>&nbsp;|&nbsp; <a
                        href="formrourke3?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>">Page
                    3</a></td>
            </tr>
        </table>

        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr class="titleBar">
                <th><fmt:message key="form.rourke.title"/>: EVIDENCE BASED INFANT/CHILD HEALTH
                    MAINTENANCE GUIDE I
                </th>
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
                    <p>Name: <input type="text" name="c_pName" maxlength="60"
                                    size="30" value="<carlos:encode value='<%= props.getProperty("c_pName", "") %>' context="htmlAttribute"/>"
                                    readonly="true"/> &nbsp;&nbsp; Birth Date (yyyy/mm/dd): <input
                            type="text" name="c_birthDate" size="10" maxlength="10"
                            value="<carlos:encode value='<%= props.getProperty("c_birthDate", "") %>' context="htmlAttribute"/>" readonly="true">
                        &nbsp;&nbsp; <%= ((FrmRourkeRecord) rec).isFemale(demoNo) == true ? "Female" : "Male" %>
                    </p>
                    <p>Length: <input type="text" name="c_length" size="6"
                                      maxlength="6" value="<carlos:encode value='<%= props.getProperty("c_length", "") %>' context="htmlAttribute"/>"/> cm
                        &nbsp;&nbsp; Head Circ: <input type="text" name="c_headCirc" size="6"
                                                       maxlength="6"
                                                       value="<carlos:encode value='<%= props.getProperty("c_headCirc", "") %>' context="htmlAttribute"/>"/>
                        cm &nbsp;&nbsp; Birth Wt: <input type="text" name="c_birthWeight"
                                                         size="6" maxlength="7"
                                                         value="<carlos:encode value='<%= props.getProperty("c_birthWeight", "") %>' context="htmlAttribute"/>"/> kg
                        &nbsp;&nbsp; Discharge Wt: <input type="text"
                                                          name="c_dischargeWeight" size="6" maxlength="7"
                                                          value="<carlos:encode value='<%= props.getProperty("c_dischargeWeight", "") %>' context="htmlAttribute"/>">
                        kg</p>
                </td>
            </tr>
        </table>
        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr align="center">
                <td class="column"><a><fmt:message key="form.rourke.age"/></a></td>
                <td colspan="3" class="row">within <a>1 week</a></td>
                <td colspan="3" class="row"><a>2 weeks</a> (optional)</td>
                <td colspan="3" class="row"><a>1 month</a> (optional)</td>
                <td colspan="3" class="row"><a>2 months</a></td>
            </tr>
            <tr align="center">
                <td class="column"><a><fmt:message key="form.rourke.date"/></a></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p1_date1w"
                                                    size="10" value="<carlos:encode value='<%= props.getProperty("p1_date1w", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p1_date2w"
                                                    size="10" value="<carlos:encode value='<%= props.getProperty("p1_date2w", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p1_date1m"
                                                    size="10" value="<carlos:encode value='<%= props.getProperty("p1_date1m", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3">(yyyy/mm/dd) <input type="text" name="p1_date2m"
                                                    size="10" value="<carlos:encode value='<%= props.getProperty("p1_date2m", "") %>' context="htmlAttribute"/>"/></td>
            </tr>
            <tr align="center">
                <td class="column" rowspan="2"><a><fmt:message key="form.rourke.growth"/></a></td>
                <td><fmt:message key="form.rourke.heightShort"/></td>
                <td><fmt:message key="form.rourke.weightShort"/></td>
                <td>Hd. Circ <small>(cm)<br>
                    av. 35cm</small></td>
                <td><fmt:message key="form.rourke.heightShort"/></td>
                <td><fmt:message key="form.rourke.weightShort"/></td>
                <td><fmt:message key="form.rourke.headCircumferenceShort"/></td>
                <td><fmt:message key="form.rourke.heightShort"/></td>
                <td><fmt:message key="form.rourke.weightShort"/></td>
                <td><fmt:message key="form.rourke.headCircumferenceShort"/></td>
                <td><fmt:message key="form.rourke.heightShort"/></td>
                <td><fmt:message key="form.rourke.weightShort"/></td>
                <td><fmt:message key="form.rourke.headCircumferenceShort"/></td>
            </tr>
            <tr align="center">
                <td><input type="text" class="wide" name="p1_ht1w" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_ht1w", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_wt1w" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_wt1w", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_hc1w" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_hc1w", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_ht2w" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_ht2w", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_wt2w" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_wt2w", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_hc2w" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_hc2w", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_ht1m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_ht1m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_wt1m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_wt1m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_hc1m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_hc1m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_ht2m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_ht2m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_wt2m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_wt2m", "") %>' context="htmlAttribute"/>"></td>
                <td><input type="text" class="wide" name="p1_hc2m" size="4"
                           maxlength="5" value="<carlos:encode value='<%= props.getProperty("p1_hc2m", "") %>' context="htmlAttribute"/>"></td>
            </tr>
            <tr align="center">
                <td class="column"><a><fmt:message key="form.rourke.parentalConcerns"/></a></td>
                <td colspan="3"><textarea name="p1_pConcern1w"
                                          style="width: 100%" cols="10"
                                          rows="2"><carlos:encode value='<%= props.getProperty("p1_pConcern1w", "") %>' context="html"/></textarea>
                </td>
                <td colspan="3"><textarea name="p1_pConcern2w"
                                          style="width: 100%" cols="10"
                                          rows="2"><carlos:encode value='<%= props.getProperty("p1_pConcern2w", "") %>' context="html"/></textarea>
                </td>
                <td colspan="3"><textarea name="p1_pConcern1m"
                                          style="width: 100%" cols="10"
                                          rows="2"><carlos:encode value='<%= props.getProperty("p1_pConcern1m", "") %>' context="html"/></textarea>
                </td>
                <td colspan="3"><textarea name="p1_pConcern2m"
                                          style="width: 100%" cols="10"
                                          rows="2"><carlos:encode value='<%= props.getProperty("p1_pConcern2m", "") %>' context="html"/></textarea>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.nutrition"/>:</a></td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_nutrition1w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_nutrition1w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_breastFeeding1w"
                                    <carlos:encode value='<%= props.getProperty("p1_breastFeeding1w", "") %>' context="htmlAttribute"/> /></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>n_breastFeeding">Breast
                                feeding</a>*<br>
                                &nbsp;&nbsp;Vit.D 10ug=400IU/day*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_formulaFeeding1w"
                                    <carlos:encode value='<%= props.getProperty("p1_formulaFeeding1w", "") %>' context="htmlAttribute"/> /></td>
                            <td><i><fmt:message key="form.rourke.formulaFeeding"/></i> (Fe fortified) <br>
                                [150ml = 5oz/kg/day]
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_stoolUrine1w"
                                    <carlos:encode value='<%= props.getProperty("p1_stoolUrine1w", "") %>' context="htmlAttribute"/> /></td>
                            <td>Stool pattern &amp; urine output</td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_nutrition2w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_nutrition2w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_breastFeeding2w"
                                    <carlos:encode value='<%= props.getProperty("p1_breastFeeding2w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>n_breastFeeding">Breast
                                feeding</a>*<br>
                                &nbsp;&nbsp;Vit.D 10ug=400IU/day*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_formulaFeeding2w"
                                    <carlos:encode value='<%= props.getProperty("p1_formulaFeeding2w", "") %>' context="htmlAttribute"/>></td>
                            <td><i><fmt:message key="form.rourke.formulaFeeding"/></i> (Fe fortified) <br>
                                [150ml = 5oz/kg/day]
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_stoolUrine2w"
                                    <carlos:encode value='<%= props.getProperty("p1_stoolUrine2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Stool pattern &amp; urine output</td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table height="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_nutrition1m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_nutrition1m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_breastFeeding1m"
                                    <carlos:encode value='<%= props.getProperty("p1_breastFeeding1m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>n_breastFeeding">Breast
                                feeding</a>*<br>
                                &nbsp;&nbsp;Vit.D 10ug=400IU/day*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_formulaFeeding1m"
                                    <carlos:encode value='<%= props.getProperty("p1_formulaFeeding1m", "") %>' context="htmlAttribute"/>></td>
                            <td><i><fmt:message key="form.rourke.formulaFeeding"/></i> (Fe fortified)</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_stoolUrine1m"
                                    <carlos:encode value='<%= props.getProperty("p1_stoolUrine1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Stool pattern &amp; urine output</td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_nutrition2m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_nutrition2m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_breastFeeding2m"
                                    <carlos:encode value='<%= props.getProperty("p1_breastFeeding2m", "") %>' context="htmlAttribute"/>></td>
                            <td nowrap="true"><b><a
                                    href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>n_breastFeeding"><fmt:message key="form.rourke.breastFeeding"/></a>*<br>
                                &nbsp;&nbsp;Vit.D 10ug=400IU/day*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_formulaFeeding2m"
                                    <carlos:encode value='<%= props.getProperty("p1_formulaFeeding2m", "") %>' context="htmlAttribute"/>></td>
                            <td><i><fmt:message key="form.rourke.formulaFeeding"/></i> (Fe fortified)</td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column">
                    <table width="100%" class="column">
                        <tr>
                            <td nowrap="true"><a>EDUCATION &amp; ADVICE</a></td>
                        </tr>
                        <tr>
                            <td align="right"><fmt:message key="form.rourke.safety"/></td>
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
                            <td align="right"><fmt:message key="form.rourke.behaviour"/></td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td align="right"><fmt:message key="form.rourke.family"/></td>
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
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td align="right"><fmt:message key="form.rourke.other"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_educationAdvice1w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_educationAdvice1w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_carSeat1w"
                                    <carlos:encode value='<%= props.getProperty("p1_carSeat1w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_motorVehicleAccidents">Car
                                seat (infant)</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_cribSafety1w"
                                    <carlos:encode value='<%= props.getProperty("p1_cribSafety1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Crib safety</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sleeping1w"
                                    <carlos:encode value='<%= props.getProperty("p1_sleeping1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Sleeping/crying</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sooth1w"
                                    <carlos:encode value='<%= props.getProperty("p1_sooth1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Soothability/ responsiveness</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_bonding1w"
                                    <carlos:encode value='<%= props.getProperty("p1_bonding1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Parenting/bonding</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_fatigue1w"
                                    <carlos:encode value='<%= props.getProperty("p1_fatigue1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Fatigue/depression</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_family1w"
                                    <carlos:encode value='<%= props.getProperty("p1_family1w", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.family"/> conflict/stress</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_siblings1w"
                                    <carlos:encode value='<%= props.getProperty("p1_siblings1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Siblings</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_homeVisit1w"
                                    <carlos:encode value='<%= props.getProperty("p1_homeVisit1w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>hri_homeVisits">Assess
                                home visit need</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sleepPos1w"
                                    <carlos:encode value='<%= props.getProperty("p1_sleepPos1w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>o_sleepPosition">Sleep
                                position</a>*</b>
                            <td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_temp1w"
                                    <carlos:encode value='<%= props.getProperty("p1_temp1w", "") %>' context="htmlAttribute"/>></td>
                            <td><i>Temperature control &amp; overdressing</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_smoke1w"
                                    <carlos:encode value='<%= props.getProperty("p1_smoke1w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>o_secondHandSmoke">Second
                                hand smoke</a>*</b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_educationAdvice2w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_educationAdvice2w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_carSeat2w"
                                    <carlos:encode value='<%= props.getProperty("p1_carSeat2w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_motorVehicleAccidents">Car
                                seat (infant)</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_cribSafety2w"
                                    <carlos:encode value='<%= props.getProperty("p1_cribSafety2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Crib safety</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sleeping2w"
                                    <carlos:encode value='<%= props.getProperty("p1_sleeping2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Sleeping/crying</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sooth2w"
                                    <carlos:encode value='<%= props.getProperty("p1_sooth2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Soothability/ responsiveness</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_bonding2w"
                                    <carlos:encode value='<%= props.getProperty("p1_bonding2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Parenting/bonding</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_fatigue2w"
                                    <carlos:encode value='<%= props.getProperty("p1_fatigue2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Fatigue/depression</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_family2w"
                                    <carlos:encode value='<%= props.getProperty("p1_family2w", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.family"/> conflict/stress</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_siblings2w"
                                    <carlos:encode value='<%= props.getProperty("p1_siblings2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Siblings</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_homeVisit2w"
                                    <carlos:encode value='<%= props.getProperty("p1_homeVisit2w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>hri_homeVisits">Assess
                                home visit need</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sleepPos2w"
                                    <carlos:encode value='<%= props.getProperty("p1_sleepPos2w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>o_sleepPosition">Sleep
                                position</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_temp2w"
                                    <carlos:encode value='<%= props.getProperty("p1_temp2w", "") %>' context="htmlAttribute"/>></td>
                            <td><i>Temperature control &amp; overdressing</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_smoke2w"
                                    <carlos:encode value='<%= props.getProperty("p1_smoke2w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>o_secondHandSmoke">Second
                                hand smoke</a>* </b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_educationAdvice1m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_educationAdvice1m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_carbonMonoxide1m"
                                    <carlos:encode value='<%= props.getProperty("p1_carbonMonoxide1m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.carbonMonoxideSmoke"/> <i><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_burns">Smoke
                                detectors</a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sleepwear1m"
                                    <carlos:encode value='<%= props.getProperty("p1_sleepwear1m", "") %>' context="htmlAttribute"/>></td>
                            <td><i><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_burns">Non-inflam.
                                sleepwear</a></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hotWater1m"
                                    <carlos:encode value='<%= props.getProperty("p1_hotWater1m", "") %>' context="htmlAttribute"/>></td>
                            <td><i><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_burns">Hot water &lt;
                                54&deg;C</a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_toys1m"
                                    <carlos:encode value='<%= props.getProperty("p1_toys1m", "") %>' context="htmlAttribute"/>></td>
                            <td><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_choking"><fmt:message key="form.rourke.chokingSafeToys"/></a>*</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_crying1m"
                                    <carlos:encode value='<%= props.getProperty("p1_crying1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Sleep/crying</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sooth1m"
                                    <carlos:encode value='<%= props.getProperty("p1_sooth1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Soothability/ responsiveness</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_interaction1m"
                                    <carlos:encode value='<%= props.getProperty("p1_interaction1m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.parentChildInteraction"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_supports1m"
                                    <carlos:encode value='<%= props.getProperty("p1_supports1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Assess supports</td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_educationAdvice2m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_educationAdvice2m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_falls2m"
                                    <carlos:encode value='<%= props.getProperty("p1_falls2m", "") %>' context="htmlAttribute"/>></td>
                            <td><i><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_falls">Falls</a>*</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_toys2m"
                                    <carlos:encode value='<%= props.getProperty("p1_toys2m", "") %>' context="htmlAttribute"/>></td>
                            <td><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>s_choking"><fmt:message key="form.rourke.chokingSafeToys"/></a>*</td>
                        </tr>
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_crying2m"
                                    <carlos:encode value='<%= props.getProperty("p1_crying2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Sleep/crying</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sooth2m"
                                    <carlos:encode value='<%= props.getProperty("p1_sooth2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Soothability/ responsiveness</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_interaction2m"
                                    <carlos:encode value='<%= props.getProperty("p1_interaction2m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.parentChildInteraction"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_stress2m"
                                    <carlos:encode value='<%= props.getProperty("p1_stress2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Depression/family stress</td>
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
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_fever2m"
                                    <carlos:encode value='<%= props.getProperty("p1_fever2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Fever control</td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.development"/></a><br>
                    (Inquiry &amp; observation of milestones)<br>
                    Tasks are set after the time of normal milestone acquisition.<br>
                    Absence of any item suggests the need for further assessment of
                    development
                </td>
                <td colspan="3" valign="top" align="center">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_development1w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_development1w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top" align="center">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_development2w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_development2w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_development1m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_development1m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_focusGaze1m"
                                    <carlos:encode value='<%= props.getProperty("p1_focusGaze1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Focuses gaze</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_startles1m"
                                    <carlos:encode value='<%= props.getProperty("p1_startles1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Startles to loud or sudden noise</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sucks1m"
                                    <carlos:encode value='<%= props.getProperty("p1_sucks1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Sucks card card-body bg-body-tertiary on nipple</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_noParentsConcerns1m"
                                    <carlos:encode value='<%= props.getProperty("p1_noParentsConcerns1m", "") %>' context="htmlAttribute"/>></td>
                            <td><fmt:message key="form.rourke.noParentConcerns"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_development2m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_development2m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_followMoves2m"
                                    <carlos:encode value='<%= props.getProperty("p1_followMoves2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Follows movement with eyes</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_sounds2m"
                                    <carlos:encode value='<%= props.getProperty("p1_sounds2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Has a variety of sounds &amp; cries</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_headUp2m"
                                    <carlos:encode value='<%= props.getProperty("p1_headUp2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Holds head up when held at adult&#146;s shoulder</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_cuddled2m"
                                    <carlos:encode value='<%= props.getProperty("p1_cuddled2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Enjoys being touched &amp; cuddled</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_noParentConcerns2m"
                                    <carlos:encode value='<%= props.getProperty("p1_noParentConcerns2m", "") %>' context="htmlAttribute"/>></td>
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
                    </div>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_physical1w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_physical1w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_skin1w"
                                    <carlos:encode value='<%= props.getProperty("p1_skin1w", "") %>' context="htmlAttribute"/>></td>
                            <td><i>Skin (jaundice, dry)</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_fontanelles1w"
                                    <carlos:encode value='<%= props.getProperty("p1_fontanelles1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Fontanelles</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_eyes1w"
                                    <carlos:encode value='<%= props.getProperty("p1_eyes1w", "") %>' context="htmlAttribute"/>></td>
                            <td><i><fmt:message key="form.rourke.eyesRedReflex"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_ears1w"
                                    <carlos:encode value='<%= props.getProperty("p1_ears1w", "") %>' context="htmlAttribute"/>></td>
                            <td><i>Ears (drums)</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_heartLungs1w"
                                    <carlos:encode value='<%= props.getProperty("p1_heartLungs1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Heart/Lungs</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_umbilicus1w"
                                    <carlos:encode value='<%= props.getProperty("p1_umbilicus1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Umbilicus</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_femoralPulses1w"
                                    <carlos:encode value='<%= props.getProperty("p1_femoralPulses1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Femoral pulses</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hips1w"
                                    <carlos:encode value='<%= props.getProperty("p1_hips1w", "") %>' context="htmlAttribute"/>></td>
                            <td><b>Hips</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_testicles1w"
                                    <carlos:encode value='<%= props.getProperty("p1_testicles1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Testicles</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_maleUrinary1w"
                                    <carlos:encode value='<%= props.getProperty("p1_maleUrinary1w", "") %>' context="htmlAttribute"/>></td>
                            <td>Male urinary stream/foreskin care</td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_physical2w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_physical2w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_skin2w"
                                    <carlos:encode value='<%= props.getProperty("p1_skin2w", "") %>' context="htmlAttribute"/>></td>
                            <td><i>Skin (jaundice, dry)</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_fontanelles2w"
                                    <carlos:encode value='<%= props.getProperty("p1_fontanelles2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Fontanelles</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_eyes2w"
                                    <carlos:encode value='<%= props.getProperty("p1_eyes2w", "") %>' context="htmlAttribute"/>></td>
                            <td><i><fmt:message key="form.rourke.eyesRedReflex"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_ears2w"
                                    <carlos:encode value='<%= props.getProperty("p1_ears2w", "") %>' context="htmlAttribute"/>></td>
                            <td><i>Ears (drums)</i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_heartLungs2w"
                                    <carlos:encode value='<%= props.getProperty("p1_heartLungs2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Heart/Lungs</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_umbilicus2w"
                                    <carlos:encode value='<%= props.getProperty("p1_umbilicus2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Umbilicus</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_femoralPulses2w"
                                    <carlos:encode value='<%= props.getProperty("p1_femoralPulses2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Femoral pulses</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hips2w"
                                    <carlos:encode value='<%= props.getProperty("p1_hips2w", "") %>' context="htmlAttribute"/>></td>
                            <td><b>Hips</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_testicles2w"
                                    <carlos:encode value='<%= props.getProperty("p1_testicles2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Testicles<br>
                            </td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_maleUrinary2w"
                                    <carlos:encode value='<%= props.getProperty("p1_maleUrinary2w", "") %>' context="htmlAttribute"/>></td>
                            <td>Male urinary stream/foreskin care</td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_physical1m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_physical1m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_fontanelles1m"
                                    <carlos:encode value='<%= props.getProperty("p1_fontanelles1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Fontanelles</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_eyes1m"
                                    <carlos:encode value='<%= props.getProperty("p1_eyes1m", "") %>' context="htmlAttribute"/>></td>
                            <td><i><fmt:message key="form.rourke.eyesRedReflex"/></i></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_cover1m"
                                    <carlos:encode value='<%= props.getProperty("p1_cover1m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>pe_cover">Cover/uncover
                                test &amp; inquiry</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hearing1m"
                                    <carlos:encode value='<%= props.getProperty("p1_hearing1m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><fmt:message key="form.rourke.hearingInquiry"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_heart1m"
                                    <carlos:encode value='<%= props.getProperty("p1_heart1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Heart</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hips1m"
                                    <carlos:encode value='<%= props.getProperty("p1_hips1m", "") %>' context="htmlAttribute"/>></td>
                            <td><b>Hips</b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_physical2m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_physical2m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_fontanelles2m"
                                    <carlos:encode value='<%= props.getProperty("p1_fontanelles2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Fontanelles</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_eyes2m"
                                    <carlos:encode value='<%= props.getProperty("p1_eyes2m", "") %>' context="htmlAttribute"/>></td>
                            <td><i><fmt:message key="form.rourke.eyesRedReflex"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_cover2m"
                                    <carlos:encode value='<%= props.getProperty("p1_cover2m", "") %>' context="htmlAttribute"/>></td>
                            <td></i><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>pe_cover">Cover/uncover
                                test &amp; inquiry</a>*</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hearing2m"
                                    <carlos:encode value='<%= props.getProperty("p1_hearing2m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><fmt:message key="form.rourke.hearingInquiry"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_heart2m"
                                    <carlos:encode value='<%= props.getProperty("p1_heart2m", "") %>' context="htmlAttribute"/>></td>
                            <td>Heart</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hips2m"
                                    <carlos:encode value='<%= props.getProperty("p1_hips2m", "") %>' context="htmlAttribute"/>></td>
                            <td><b>Hips</b></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a>PROBLEMS &amp; PLANS</a></td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_problems1w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_problems1w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_pkuThyroid1w"
                                    <carlos:encode value='<%= props.getProperty("p1_pkuThyroid1w", "") %>' context="htmlAttribute"/>></td>
                            <td><b> PKU, Thyroid</b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hemoScreen1w"
                                    <carlos:encode value='<%= props.getProperty("p1_hemoScreen1w", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>pp_hemoglobinopathyScreening">Hemoglobinopathy
                                Screen</a> (if at risk)*</b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_problems2w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_problems2w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_problems1m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_problems1m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_problems2m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_problems2m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.immunization"/></a><br>
                    Guidelines may vary by province
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_immunization1w"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_immunization1w", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td colspan="2">If HBsAg-positive parent or sibling:</td>
                        </tr>
                        <tr>
                            <td><input type="checkbox" name="p1_hepB1w"
                                    <carlos:encode value='<%= props.getProperty("p1_hepB1w", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%"><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>i_hepB">Hep.
                                B vaccine</a>*</b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr>
                            <td colspan="2" align="center"><input type="text" class="wide"
                                                                  name="p1_immunization2w"
                                                                  value="<carlos:encode value='<%= props.getProperty("p1_immunization2w", "") %>' context="htmlAttribute"/>"/>
                            </td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table>
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_immunization1m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_immunization1m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td colspan="2">Give information:</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_immuniz1m"
                                    <carlos:encode value='<%= props.getProperty("p1_immuniz1m", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%">Immunization</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_acetaminophen1m"
                                    <carlos:encode value='<%= props.getProperty("p1_acetaminophen1m", "") %>' context="htmlAttribute"/>></td>
                            <td>Acetaminophen</td>
                        </tr>
                        <tr>
                            <td colspan="2">If HBsAg-positive parent or sibling:</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hepB1m"
                                    <carlos:encode value='<%= props.getProperty("p1_hepB1m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><a href="<carlos:encode value='<%= resource %>' context="htmlAttribute"/>i_hepB">Hep. B vaccine</a>*</b></td>
                        </tr>
                    </table>
                </td>
                <td colspan="3" valign="top">
                    <table width="100%">
                        <tr align="center">
                            <td colspan="2"><input type="text" class="wide"
                                                   name="p1_immunization2m"
                                                   value="<carlos:encode value='<%= props.getProperty("p1_immunization2m", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox"
                                                    name="p1_acetaminophen2m"
                                    <carlos:encode value='<%= props.getProperty("p1_acetaminophen2m", "") %>' context="htmlAttribute"/>></td>
                            <td width="100%">Acetaminophen</td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_hib2m"
                                    <carlos:encode value='<%= props.getProperty("p1_hib2m", "") %>' context="htmlAttribute"/>></td>
                            <td><b><fmt:message key="form.rourke.hib"/></b></td>
                        </tr>
                        <tr>
                            <td valign="top"><input type="checkbox" name="p1_polio2m"
                                    <carlos:encode value='<%= props.getProperty("p1_polio2m", "") %>' context="htmlAttribute"/>></td>
                            <td><b> aPDT polio </b></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="column"><a><fmt:message key="form.rourke.signature"/></a></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p1_signature1w"
                                       value="<carlos:encode value='<%= props.getProperty("p1_signature1w", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p1_signature2w"
                                       value="<carlos:encode value='<%= props.getProperty("p1_signature2w", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p1_signature1m"
                                       value="<carlos:encode value='<%= props.getProperty("p1_signature1m", "") %>' context="htmlAttribute"/>"/></td>
                <td colspan="3"><input type="text" class="wide"
                                       style="width: 100%" name="p1_signature2m"
                                       value="<carlos:encode value='<%= props.getProperty("p1_signature2m", "") %>' context="htmlAttribute"/>"/></td>
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
                    Graph Length and Weight</a><br>
                    <a name="headCirc"
                       href="javascript:popup('form/graphHeadCirc?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>');">
                        Graph Head Circumference</a></td>
                <td nowrap="true"><a><fmt:message key="form.rourke.page1"/></a>&nbsp;|&nbsp; <a
                        href="formrourke2?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>">Page
                    2</a>&nbsp;|&nbsp; <a
                        href="formrourke3?demographic_no=<carlos:encode value='<%= String.valueOf(demoNo) %>' context="uriComponent"/>&formId=<carlos:encode value='<%= String.valueOf(formId) %>' context="uriComponent"/>&provNo=<carlos:encode value='<%= String.valueOf(provNo) %>' context="uriComponent"/>">Page
                    3</a></td>
            </tr>
        </table>
    </form>
    </body>
</html>
