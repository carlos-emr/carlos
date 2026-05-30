<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%-- i18n scope: full (general form) --%>

<%@ taglib uri="http://displaytag.sf.net" prefix="display" %>

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


<%@ page
        import="io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*, io.github.carlos_emr.carlos.utility.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@ page
        import="org.springframework.context.*,org.springframework.web.context.support.*" %>

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%
    String formClass = "DischargeSummary";
    String formLink = "formDischargeSummary.jsp";
    int programNo = Integer.parseInt((String) request.getSession().getAttribute(SessionConstants.CURRENT_PROGRAM_ID));
    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));
    int provNo = Integer.parseInt((String) session.getAttribute("user"));
    FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
    //java.util.Properties props = rec.getFormRecord(demoNo, formId);
    java.util.Properties props = rec.getCaisiFormRecord(demoNo, formId, provNo, programNo);
    //FrmData fd = new FrmData();    String resource = fd.getResource(); resource = resource + "ob/riskinfo/";

    //get project_home
    String project_home = request.getContextPath().substring(1);
%>
<%
    boolean bView = false;
    if (request.getParameter("view") != null && request.getParameter("view").equals("1")) bView = true;
%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="form.dischargeSummaryPrint.title"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <style type="text/css" media="print">
            .header {
                display: none;
            }

            .header INPUT {
                display: none;
            }

            .header A {
                display: none;
            }
        </style>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
        <!-- <link rel="stylesheet" type="text/css" href="arStyle.css">  -->

    </head>

    <script type="text/javascript" language="Javascript">

        function reset() {
            document.forms[0].target = "apptProviderSearch";
            document.forms[0].action = "/<%=project_home%>/form/formname";
        }

        function onPrint() {
            document.forms[0].submit.value = "print"; //printAR1
            var ret = checkAllDates();
            if (ret == true) {
                //ret = confirm("Do you wish to save this form and view the print preview?");
                popupFixedPage(650, 850, '<%= request.getContextPath() %>/provider/notice.htm');
                document.forms[0].action = "<%= request.getContextPath() %>/form/createpdf?__title=&__cfgfile=&__cfgfile=&__template=";
                document.forms[0].target = "planner";
                document.forms[0].submit();
                document.forms[0].target = "apptProviderSearch";
            }
            return ret;
        }

        function onSave() {
            document.forms[0].submit.value = "save";
            var ret = checkAllDates();
            if (ret == true) {
                reset();
                ret = confirm("<fmt:message key='global.msgWannaSave'/>");
            }
            return ret;
        }

        function onSaveExit() {
            document.forms[0].submit.value = "exit";
            var ret = checkAllDates();
            if (ret == true) {
                reset();
                ret = confirm("<fmt:message key='global.msgSaveExit'/>");
            }
            return ret;
        }

        function popupPage(varpage) {
            windowprops = "height=700,width=960" +
                ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=no,screenX=50,screenY=50,top=20,left=20";
            var popup = window.open(varpage, "ar2", windowprops);
            if (popup.opener == null) {
                popup.opener = self;
            }
        }

        function popPage(varpage, pageName) {
            windowprops = "height=700,width=960" +
                ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=no,screenX=50,screenY=50,top=20,left=20";
            var popup = window.open(varpage, pageName, windowprops);
            //if (popup.opener == null) {
            //    popup.opener = self;
            //}
            popup.focus();
        }

        function popupFixedPage(vheight, vwidth, varpage) {
            var page = "" + varpage;
            windowprop = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=10,screenY=0,top=0,left=0";
            var popup = window.open(page, "planner", windowprop);
        }

        /**
         * DHTML date validation script. Courtesy of SmartWebby.com (http://www.smartwebby.com/dhtml/)
         */
// Declaring valid date character, minimum year and maximum year
        var dtCh = "/";
        var minYear = 1900;
        var maxYear = 9900;

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
                    //alert('dateString'+dateString);
                    return true;
                }
                var dt = dateString.split('/');
                //var y = dt[2];  var m = dt[1];  var d = dt[0];
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
            if (valDate(document.forms[0].pg1_eddByDate) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg1_eddByUs) == false) {
                b = false;
            }

            return b;
        }
    </script>

    <body bgproperties="fixed" topmargin="0" leftmargin="1" rightmargin="1">

    <table width="100%" class="header">
        <tr width="100%">
            <td align="left"><input type="button" value="<fmt:message key='global.btnExit'/>"
                                    onclick="window.close();"/> <input type="button" value="<fmt:message key="global.btnPrint"/>"
                                                                       onclick="window.print()"/></td>
        </tr>
    </table>


    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr>
            <td align="center"><b><fmt:message key="form.dischargeSummaryPrint.infirmary"/></b></td>
        </tr>
        <tr>
            <td align="center"><b>(ph) 416-324-4108</b></td>
        </tr>
        <tr>
            <td align="center"><b>(fax) 416-324-4258</b></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <td align="center"><b><fmt:message key="form.dischargeSummaryPrint.heading"/></b></td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
    </table>


    <table width="100%" border="0" cellspacing="0" cellpadding="0">
        <tr width="100%">
            <td align="left"><b><fmt:message key="form.dischargeSummaryPrint.clientName"/>:</b> <%= props.getProperty("clientName", "") %>
            </td>
            <td align="left"><b><fmt:message key="form.dischargeSummaryPrint.dob"/> <small>(yyyy/mm/dd)</small>: </b> <%= props.getProperty("birthDate", "") %>
            </td>
            <td align="left"><b><fmt:message key="form.dischargeSummaryPrint.ohip"/>: </b> <%= props.getProperty("ohip", "") %>
            </td>
        </tr>

        <tr width="100%">
            <td align="left"><b><fmt:message key="form.dischargeSummaryPrint.admitDate"/>:</b> <%= props.getProperty("admitDate", "") %>
            </td>
            <td align="left"><b><fmt:message key="form.dischargeSummaryPrint.dischargeDate"/> <small>(yyyy/mm/dd):</small>
            </b> <%= props.getProperty("dischargeDate", "") %>
            </td>
            <td align="left"><b><fmt:message key="form.dischargeSummaryPrint.allergies"/>: </b> <%= props.getProperty("allergies", "") %>
            </td>
    </table>
    <br>
    <table width="100%" border="0" cellspacing="0" cellpadding="0">
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.admittingDiagnosis"/>:</th>
        </tr>
        <tr>
            <td><%= props.getProperty("admissionNotes", "") %>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.problemList"/>:</th>
        </tr>
        <tr>
            <td><%= props.getProperty("currentIssues", "") %>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.briefSummary"/>:
            </th>
        </tr>
        <tr>
            <td><%= props.getProperty("briefSummary", "") %>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.dischargePlan"/>:
            </th>
        </tr>
        <tr>
            <td><%= props.getProperty("dischargePlan", "") %>
            </td>
        </tr>
        <tr>
            <td>&nbsp;</td>
        </tr>
    </table>

    <table width="100%" border="0" cellspacing="0" cellpadding="0">
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.followUpAppointments"/>:</th>
        </tr>
    </table>
    <table width="100%" border="1" cellspacing="0" cellpadding="0">
        <tr>
            <th><fmt:message key="form.dischargeSummaryPrint.agencyProvider"/></th>
            <th><fmt:message key="form.dischargeSummaryPrint.phoneNo"/></th>
            <th><fmt:message key="form.dischargeSummaryPrint.dateTime"/></th>
            <th><fmt:message key="form.dischargeSummaryPrint.location"/></th>
        </tr>
        <tr>
            <td><%= props.getProperty("doctor1", "") %>&nbsp;</td>
            <td><%= props.getProperty("phoneNumber1", "") %> &nbsp;</td>
            <td><%= props.getProperty("date1", "") %>&nbsp;</td>
            <td><%= props.getProperty("location1", "") %>&nbsp;</td>
        </tr>
        <tr>
            <td><%= props.getProperty("doctor2", "") %>&nbsp;</td>
            <td><%= props.getProperty("phoneNumber2", "") %>&nbsp;</td>
            <td><%= props.getProperty("date2", "") %>&nbsp;</td>
            <td><%= props.getProperty("location2", "") %>&nbsp;</td>
        </tr>
        <tr>
            <td><%= props.getProperty("doctor3", "") %>&nbsp;</td>
            <td><%= props.getProperty("phoneNumber3", "") %>&nbsp;</td>
            <td><%= props.getProperty("date3", "") %>&nbsp;</td>
            <td><%= props.getProperty("location3", "") %>&nbsp;</td>
        </tr>
        <tr>
            <td><%= props.getProperty("doctor4", "") %>&nbsp;</td>
            <td><%= props.getProperty("phoneNumber4", "") %>&nbsp;</td>
            <td><%= props.getProperty("date4", "") %>&nbsp;</td>
            <td><%= props.getProperty("location4", "") %>&nbsp;</td>
        </tr>
    </table>
    <br>
    <table width="100%" border="1" cellspacing="0" cellpadding="0">
        <tr>
            <td>
                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                    <tr>
                        <th><fmt:message key="form.dischargeSummaryPrint.currentMedications"/>:</th>
                    </tr>
                    <tr>
                        <td><fmt:message key="form.dischargeSummaryPrint.seeAttachedSummary"/></td>
                    </tr>
                </table>
            <td>
                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                    <tr>
                        <th align="left"><fmt:message key="form.dischargeSummaryPrint.prescriptionProvided"/>:</th>
                        <td align="left">
                            <%if (props.getProperty("prescriptionProvided", "").equals("1")) {%> <input
                                type="radio" name="prescriptionProvided" value="1" checked/> <%} else { %>
                            <input type="radio" name="prescriptionProvided" value="1"/> <%} %><fmt:message key="global.yes"/>
                        </td>
                        <td align="left">
                            <%if (props.getProperty("prescriptionProvided", "").equals("0")) {%> <input
                                type="radio" name="prescriptionProvided" value="0" checked/> <%} else { %>
                            <input type="radio" name="prescriptionProvided" value="0"/> <%} %><fmt:message key="global.no"/>
                        </td>

                    </tr>


                    <tr>
                        <th align="left"><fmt:message key="form.dischargeSummaryPrint.changesInMedications"/>:</th>
                        <td align="left">
                            <%if (props.getProperty("medicationProvided", "").equals("1")) {%> <input
                                type="radio" name="medicationProvided" value="1" checked/> <%} else { %>
                            <input type="radio" name="medicationProvided" value="1"/> <%} %><fmt:message key="global.yes"/>
                        </td>
                        <td align="left">
                            <%if (props.getProperty("medicationProvided", "").equals("0")) {%> <input
                                type="radio" name="medicationProvided" value="0" checked/> <%} else { %>
                            <input type="radio" name="medicationProvided" value="0"/> <%} %><fmt:message key="global.no"/>
                        </td>
                    </tr>
                    <tr>
                        <td colspan="3"><%= props.getProperty("changeMedications", "") %>
                        <td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <br>
    <table>
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.referrals"/>:</th>
        </tr>
    </table>
    <table width="100%" border="1" cellspacing="0" cellpadding="0">

        <tr>
            <th><fmt:message key="form.dischargeSummaryPrint.program"/></th>
            <th><fmt:message key="form.dischargeSummaryPrint.referralMade"/></th>
            <th><fmt:message key="form.dischargeSummaryPrint.outcome"/></th>
        </tr>
        <tr>
            <td><%= props.getProperty("referralProgram1", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralMade1", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralOutcome1", "") %>&nbsp;</td>
        </tr>
        <tr>
            <td><%= props.getProperty("referralProgram2", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralMade2", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralOutcome2", "") %>&nbsp;</td>
        </tr>
        <tr>
            <td><%= props.getProperty("referralProgram3", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralMade3", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralOutcome3", "") %>&nbsp;</td>
        </tr>
        <tr>
            <td><%= props.getProperty("referralProgram4", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralMade4", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralOutcome4", "") %>&nbsp;</td>
        </tr>
        <tr>
            <td><%= props.getProperty("referralProgram5", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralMade5", "") %>&nbsp;</td>
            <td><%= props.getProperty("referralOutcome5", "") %>&nbsp;</td>
        </tr>
    </table>

    <br>

    <table width="100%" border="0" cellspacing="0" cellpadding="0">
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.notes"/>:</th>
        </tr>
        <tr>
            <td><%= props.getProperty("notes", "") %>
            </td>
        </tr>
    </table>
    <br>
    <table>
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.infirmaryProvider"/>:</th>
            <td><%= props.getProperty("providerName", "") %>&nbsp;</td>
        </tr>
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.providerSignature"/>:</th>
            <td><%= props.getProperty("signature", "") %>&nbsp;</td>
        </tr>
        <tr>
            <th align="left"><fmt:message key="form.dischargeSummaryPrint.date"/> (yyyy/mm/dd):</th>
            <td><%= props.getProperty("signatureDate", "") %>&nbsp;</td>
        </tr>
    </table>
    <br>

    </body>
</html>
