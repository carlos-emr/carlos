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



<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.data.FrmData" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Palliative Care</title>
        <link rel="stylesheet" type="text/css" href="palliativeCareStyles.css"/>
        <link rel="stylesheet" type="text/css" media="print" href="print.css"/>
        <%-- S5131: getServerName() returns the Host header — safe when deployed behind a reverse proxy that validates the Host header (required for production) --%>
        <base href="<carlos:encode value='<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>' context="htmlAttribute"/>"> <%-- NOSONAR --%>
    </head>

    <%
        String formClass = "PalliativeCare";
        String formLink = "formpalliativecare.jsp";

        int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
        int formId = Integer.parseInt(request.getParameter("formId"));
        int provNo = Integer.parseInt((String) session.getAttribute("user"));
        FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
        java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

        FrmData fd = new FrmData();
        String resource = fd.getResource();
        resource = resource + "PalliativeCare/";
    %>

    <script type="text/javascript" language="Javascript">

        function onPrint() {
            document.forms[0].submit.value = "print";
            var ret = checkAllDates();
            if (ret == true) {
                window.print();
            }
            return ret;
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

        function popupPage(varpage) {
            windowprops = "height=700,width=960" +
                ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=no,screenX=50,screenY=50,top=20,left=20";
            var popup = window.open(varpage, "palliativeCare", windowprops);
            if (popup.opener == null) {
                popup.opener = self;
            }
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
                    alert('Invalid ' + pass + ' in field ' + dateBox.name);
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
            if (valDate(document.forms[0].date1) == false) {
                b = false;
            } else if (valDate(document.forms[0].date2) == false) {
                b = false;
            } else if (valDate(document.forms[0].date3) == false) {
                b = false;
            } else if (valDate(document.forms[0].date4) == false) {
                b = false;
            }

            return b;
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
        <input type="hidden" name="provNo"
               value="<carlos:encode value='<%= StringUtils.noNull(request.getParameter("provNo")) %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="submit" value="exit"/>

        <table class="Head" class="hidePrint">
            <tr>
                <td align="left"><input type="submit" value="<fmt:message key='global.save'/>"
                                        onclick="javascript:return onSave();"/> <input type="submit"
                                                                                       value="<fmt:message key='global.saveExit'/>"
                                                                                       onclick="javascript:return onSaveExit();"/>
                    <input
                            type="submit" value="<fmt:message key="global.btnExit"/>" onclick="javascript:return onExit();"/>
                    <input type="button" value="<fmt:message key='global.btnPrint'/>"
                           onclick="javascript:return onPrint();"/></td>
            </tr>
        </table>

        <table width="100%">
            <tr>
                <td class="title" colspan="2">Palliative Care<br>
                    Patient Care Flowsheet
                </td>
            </tr>
            <tr>
                <td colspan="2">&nbsp;</td>
            </tr>
            <tr>
                <td width="50%" align="center">Patient Name: <input
                        type="hidden" name="pName"
                        value="<carlos:encode value='<%= props.getProperty("pName", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("pName", "") %>' context="html"/>
                </td>
                <td width="50%" align="center">Diagnosis: <input type="text"
                                                                 name="diagnosis" size="40"
                                                                 value="<carlos:encode value='<%= props.getProperty("diagnosis", "") %>' context="htmlAttribute"/>"/><br>
                    <br>
                </td>
            </tr>
            <tr>
                <td class="format" colspan="2">
                    <table border="1" cellspacing="0" width="100%">
                        <tr class="date">
                            <td width="12%"><b>DATE</b></td>
                            <td width="22%" align="right">(yyyy/mm/dd) <input type="text"
                                                                              name="date1"
                                                                              value="<carlos:encode value='<%= props.getProperty("date1", "") %>' context="htmlAttribute"/>"/>
                            </td>
                            <td width="22%" align="right">(yyyy/mm/dd) <input type="text"
                                                                              name="date2"
                                                                              value="<carlos:encode value='<%= props.getProperty("date2", "") %>' context="htmlAttribute"/>"/>
                            </td>
                            <td width="22%" align="right">(yyyy/mm/dd) <input type="text"
                                                                              name="date3"
                                                                              value="<carlos:encode value='<%= props.getProperty("date3", "") %>' context="htmlAttribute"/>"/>
                            </td>
                            <td width="22%" align="right">(yyyy/mm/dd) <input type="text"
                                                                              name="date4"
                                                                              value="<carlos:encode value='<%= props.getProperty("date4", "") %>' context="htmlAttribute"/>"/>
                            </td>
                        </tr>
                        <tr class="pain">
                            <td><b><a
                                    href="javascript: popupPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>pain');">PAIN</a></b></td>
                            <td><textarea name="pain1"><carlos:encode value='<%= props.getProperty("pain1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="pain2"><carlos:encode value='<%= props.getProperty("pain2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="pain3"><carlos:encode value='<%= props.getProperty("pain3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="pain4"><carlos:encode value='<%= props.getProperty("pain4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="giBowels">
                            <td><b><a href="javascript: popupPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>gi');">GI:</a></b><br>
                                Bowels<br>
                                -diarrhea -constipation
                            </td>
                            <td><textarea name="giBowels1"><carlos:encode value='<%= props.getProperty("giBowels1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giBowels2"><carlos:encode value='<%= props.getProperty("giBowels2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giBowels3"><carlos:encode value='<%= props.getProperty("giBowels3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giBowels4"><carlos:encode value='<%= props.getProperty("giBowels4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="giNausea">
                            <td>Nausea & Vomiting</td>
                            <td><textarea name="giNausea1"><carlos:encode value='<%= props.getProperty("giNausea1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giNausea2"><carlos:encode value='<%= props.getProperty("giNausea2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giNausea3"><carlos:encode value='<%= props.getProperty("giNausea3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giNausea4"><carlos:encode value='<%= props.getProperty("giNausea4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="giDysphagia">
                            <td>Dysphagia</td>
                            <td><textarea name="giDysphagia1"><carlos:encode value='<%= props.getProperty("giDysphagia1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giDysphagia2"><carlos:encode value='<%= props.getProperty("giDysphagia2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giDysphagia3"><carlos:encode value='<%= props.getProperty("giDysphagia3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giDysphagia4"><carlos:encode value='<%= props.getProperty("giDysphagia4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="giHiccups">
                            <td>Hiccups</td>
                            <td><textarea name="giHiccups1"><carlos:encode value='<%= props.getProperty("giHiccups1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giHiccups2"><carlos:encode value='<%= props.getProperty("giHiccups2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giHiccups3"><carlos:encode value='<%= props.getProperty("giHiccups3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giHiccups4"><carlos:encode value='<%= props.getProperty("giHiccups4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="giMouth">
                            <td>Mouth problems</td>
                            <td><textarea name="giMouth1"><carlos:encode value='<%= props.getProperty("giMouth1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giMouth2"><carlos:encode value='<%= props.getProperty("giMouth2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giMouth3"><carlos:encode value='<%= props.getProperty("giMouth3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="giMouth4"><carlos:encode value='<%= props.getProperty("giMouth4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="gu">
                            <td><b><a href="javascript: popupPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>gu');">GU:</a></b><br>
                                Retention<br>
                                Incontinence
                            </td>
                            <td><textarea name="gu1"><carlos:encode value='<%= props.getProperty("gu1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="gu2"><carlos:encode value='<%= props.getProperty("gu2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="gu3"><carlos:encode value='<%= props.getProperty("gu3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="gu4"><carlos:encode value='<%= props.getProperty("gu4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="skinUlcers">
                            <td><b><a
                                    href="javascript: popupPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>skin');">SKIN:</a></b><br>
                                Ulcers
                            </td>
                            <td><textarea name="skinUlcers1"><carlos:encode value='<%= props.getProperty("skinUlcers1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="skinUlcers2"><carlos:encode value='<%= props.getProperty("skinUlcers2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="skinUlcers3"><carlos:encode value='<%= props.getProperty("skinUlcers3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="skinUlcers4"><carlos:encode value='<%= props.getProperty("skinUlcers4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="skinPruritis">
                            <td>Pruritis</td>
                            <td><textarea name="skinPruritis1"><carlos:encode value='<%= props.getProperty("skinPruritis1", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="skinPruritis2"><carlos:encode value='<%= props.getProperty("skinPruritis2", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="skinPruritis3"><carlos:encode value='<%= props.getProperty("skinPruritis3", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="skinPruritis4"><carlos:encode value='<%= props.getProperty("skinPruritis4", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr class="psychAgitation">
                            <td><b><a
                                    href="javascript: popupPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>psych');">PSYCH:</a></b><br>
                                Agitation<br>
                                Myoclonus
                            </td>
                            <td><textarea
                                    name="psychAgitation1"><carlos:encode value='<%= props.getProperty("psychAgitation1", "") %>' context="html"/></textarea></td>
                            <td><textarea
                                    name="psychAgitation2"><carlos:encode value='<%= props.getProperty("psychAgitation2", "") %>' context="html"/></textarea></td>
                            <td><textarea
                                    name="psychAgitation3"><carlos:encode value='<%= props.getProperty("psychAgitation3", "") %>' context="html"/></textarea></td>
                            <td><textarea
                                    name="psychAgitation4"><carlos:encode value='<%= props.getProperty("psychAgitation4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="psychAnorexia">
                            <td>Anorexia</td>
                            <td><textarea name="psychAnorexia1"><carlos:encode value='<%= props.getProperty("psychAnorexia1", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="psychAnorexia2"><carlos:encode value='<%= props.getProperty("psychAnorexia2", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="psychAnorexia3"><carlos:encode value='<%= props.getProperty("psychAnorexia3", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="psychAnorexia4"><carlos:encode value='<%= props.getProperty("psychAnorexia4", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr class="psychAnxiety">
                            <td>Anxiety</td>
                            <td><textarea name="psychAnxiety1"><carlos:encode value='<%= props.getProperty("psychAnxiety1", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="psychAnxiety2"><carlos:encode value='<%= props.getProperty("psychAnxiety2", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="psychAnxiety3"><carlos:encode value='<%= props.getProperty("psychAnxiety3", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="psychAnxiety4"><carlos:encode value='<%= props.getProperty("psychAnxiety4", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr class="psychDepression">
                            <td>Depression</td>
                            <td><textarea
                                    name="psychDepression1"><carlos:encode value='<%= props.getProperty("psychDepression1", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea
                                    name="psychDepression2"><carlos:encode value='<%= props.getProperty("psychDepression2", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea
                                    name="psychDepression3"><carlos:encode value='<%= props.getProperty("psychDepression3", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea
                                    name="psychDepression4"><carlos:encode value='<%= props.getProperty("psychDepression4", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr class="psychFatigue">
                            <td>Fatigue</td>
                            <td><textarea name="psychFatigue1"><carlos:encode value='<%= props.getProperty("psychFatigue1", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="psychFatigue2"><carlos:encode value='<%= props.getProperty("psychFatigue2", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="psychFatigue3"><carlos:encode value='<%= props.getProperty("psychFatigue3", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="psychFatigue4"><carlos:encode value='<%= props.getProperty("psychFatigue4", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr class="psychSomnolence">
                            <td>Somnolence</td>
                            <td><textarea
                                    name="psychSomnolence1"><carlos:encode value='<%= props.getProperty("psychSomnolence1", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea
                                    name="psychSomnolence2"><carlos:encode value='<%= props.getProperty("psychSomnolence2", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea
                                    name="psychSomnolence3"><carlos:encode value='<%= props.getProperty("psychSomnolence3", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea
                                    name="psychSomnolence4"><carlos:encode value='<%= props.getProperty("psychSomnolence4", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr class="respCough">
                            <td><b><a
                                    href="javascript: popupPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>resp');">RESP:</a></b><br>
                                Cough
                            </td>
                            <td><textarea name="respCough1"><carlos:encode value='<%= props.getProperty("respCough1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="respCough2"><carlos:encode value='<%= props.getProperty("respCough2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="respCough3"><carlos:encode value='<%= props.getProperty("respCough3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="respCough4"><carlos:encode value='<%= props.getProperty("respCough4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="respDyspnea">
                            <td>Dyspnea</td>
                            <td><textarea name="respDyspnea1"><carlos:encode value='<%= props.getProperty("respDyspnea1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="respDyspnea2"><carlos:encode value='<%= props.getProperty("respDyspnea2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="respDyspnea3"><carlos:encode value='<%= props.getProperty("respDyspnea3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="respDyspnea4"><carlos:encode value='<%= props.getProperty("respDyspnea4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="respFever">
                            <td>Fever</td>
                            <td><textarea name="respFever1"><carlos:encode value='<%= props.getProperty("respFever1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="respFever2"><carlos:encode value='<%= props.getProperty("respFever2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="respFever3"><carlos:encode value='<%= props.getProperty("respFever3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="respFever4"><carlos:encode value='<%= props.getProperty("respFever4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="respCaregiver">
                            <td>Caregiver coping</td>
                            <td><textarea name="respCaregiver1"><carlos:encode value='<%= props.getProperty("respCaregiver1", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="respCaregiver2"><carlos:encode value='<%= props.getProperty("respCaregiver2", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="respCaregiver3"><carlos:encode value='<%= props.getProperty("respCaregiver3", "") %>' context="html"/></textarea>
                            </td>
                            <td><textarea name="respCaregiver4"><carlos:encode value='<%= props.getProperty("respCaregiver4", "") %>' context="html"/></textarea>
                            </td>
                        </tr>
                        <tr class="other">
                            <td><b><a
                                    href="javascript: popupPage('<carlos:encode value='<%= StringUtils.noNull(resource) %>' context="javaScriptAttribute"/>other');">Other
                                Issues / FU Plan</a></b></td>
                            <td><textarea name="other1"><carlos:encode value='<%= props.getProperty("other1", "") %>' context="html"/></textarea></td>
                            <td><textarea name="other2"><carlos:encode value='<%= props.getProperty("other2", "") %>' context="html"/></textarea></td>
                            <td><textarea name="other3"><carlos:encode value='<%= props.getProperty("other3", "") %>' context="html"/></textarea></td>
                            <td><textarea name="other4"><carlos:encode value='<%= props.getProperty("other4", "") %>' context="html"/></textarea></td>
                        </tr>
                        <tr class="signature">
                            <td>Signature</td>
                            <td><input type="text" name="signature1"
                                       value="<carlos:encode value='<%= props.getProperty("signature1", "") %>' context="htmlAttribute"/>"/></td>
                            <td><input type="text" name="signature2"
                                       value="<carlos:encode value='<%= props.getProperty("signature2", "") %>' context="htmlAttribute"/>"/></td>
                            <td><input type="text" name="signature3"
                                       value="<carlos:encode value='<%= props.getProperty("signature3", "") %>' context="htmlAttribute"/>"/></td>
                            <td><input type="text" name="signature4"
                                       value="<carlos:encode value='<%= props.getProperty("signature4", "") %>' context="htmlAttribute"/>"/></td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>

        <table class="Head" class="hidePrint">
            <tr>
                <td align="left"><input type="submit" value="<fmt:message key='global.save'/>"
                                        onclick="javascript:return onSave();"/> <input type="submit"
                                                                                       value="<fmt:message key='global.saveExit'/>"
                                                                                       onclick="javascript:return onSaveExit();"/>
                    <input
                            type="submit" value="<fmt:message key="global.btnExit"/>" onclick="javascript:return onExit();"/>
                    <input type="button" value="<fmt:message key='global.btnPrint'/>"
                           onclick="javascript:return onPrint();"/></td>
            </tr>
        </table>

    </form>
    </body>
</html>
