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

<%@ page import="io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.CarlosProperties" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.FrmLabReqPreSetDao, io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmLabReqRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Laboratory Requisition</title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="screen" href="<%= request.getContextPath() %>/form/labReqStyle.css">
        <link rel="stylesheet" type="text/css" media="print" href="<%= request.getContextPath() %>/form/print.css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>

    <%
        String formClass = "LabReq";
        String formLink = "formlabreq.jsp";

        boolean readOnly = false;
        int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
        int formId = Integer.parseInt(request.getParameter("formId"));
        int provNo = Integer.parseInt((String) session.getAttribute("user"));
        FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
        java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

        props = ((FrmLabReqRecord) rec).getFormCustRecord(props, provNo);
        CarlosProperties oscarProps = CarlosProperties.getInstance();

        if (request.getParameter("labType") != null) {
            if (formId == 0) {
                FrmLabReqPreSetDao preSetDao = (FrmLabReqPreSetDao) SpringUtils.getBean(FrmLabReqPreSetDao.class);
                String labPreSet = request.getParameter("labType");
                props = preSetDao.fillPropertiesByLabType(labPreSet, props);
            }
        }

        if (request.getParameter("readOnly") != null) {
            readOnly = true;
        }

        String patientName = props.getProperty("patientName", " , ");
        String[] patientNames = patientName.split(",");

        String[] patientDOB = props.getProperty("birthDate", " / / ").split("/");
        request.removeAttribute("submit");
    %>

    <script type="text/javascript" language="Javascript">

        var temp;
        temp = "";


        function onPrint(pdf) {

            var ret = checkAllDates();
            if (ret == true) {

                //ret = confirm("Do you wish to save this form and view the print preview?");
                //popupFixedPage(650,850,'<%= request.getContextPath() %>/provider/notice.htm');
                temp = document.forms[0].action;

                if (pdf) {
                    document.forms[0].action = '<%= request.getContextPath() %>/form/formname?__title=Lab+Request&__cfgfile=labReqPrint&__template=newReqLab';
                    document.forms[0].submit.value = "printall";
                    document.forms[0].target = "_self";
                } else {
                    document.forms[0].action = '<%= request.getContextPath() %>/form/formname';
                    document.forms[0].submit.value = "printLabReq";
                    document.forms[0].target = "labReqPrint";
                }


            }
            return ret;
        }

        function onSave() {
            if (temp != "") {
                document.forms[0].action = temp;
            }
            document.forms[0].target = "_self";
            document.forms[0].submit.value = "save";
            var ret = checkAllDates();
            if (ret == true) {
                ret = confirm("<fmt:message key='global.msgWannaSave'/>");
            }
            return ret;
        }

        function onSaveExit() {
            if (temp != "") {
                document.forms[0].action = temp;
            }
            document.forms[0].target = "_self";
            document.forms[0].submit.value = "exit";
            var ret = checkAllDates();
            if (ret == true) {
                ret = confirm("<fmt:message key='global.msgSaveExit'/>");
            }
            return ret;
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
            if (valDate(document.forms[0].formDate) == false) {
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

    <body style="page: doublepage; page-break-after: right">
    <form action="${pageContext.request.contextPath}/form/formname" method="post">

        <input type="hidden" name="demographic_no"
               value="<carlos:encode value='<%= props.getProperty("demographic_no", "0") %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="patientLastName"
               value="<carlos:encode value='<%= patientNames[0].trim() %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="patientFirstName"
               value="<carlos:encode value='<%= patientNames[1].trim() %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="patientBirthYear"
               value="<carlos:encode value='<%= patientDOB[0].trim() %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="patientBirthMth"
               value="<carlos:encode value='<%= patientDOB[1].trim() %>' context="htmlAttribute"/>"/>
        <input type="hidden" name="patientBirthDay"
               value="<carlos:encode value='<%= patientDOB[2].trim() %>' context="htmlAttribute"/>"/>
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
        <input type="hidden" name="formId" value="<carlos:encode value='<%= String.valueOf(formId) %>' context="htmlAttribute"/>"/>

        <table class="Head" class="hidePrint">
            <tr>
                <td nowrap="true">
                    <% if (!readOnly) { %> <input type="submit" value="<fmt:message key='global.save'/>"
                                                  onclick="javascript:return onSave();"/> <input type="submit"
                                                                                                 value="<fmt:message key='global.saveExit'/>"
                                                                                                 onclick="javascript:return onSaveExit();"/> <% } %>
                    <input type="submit" value="<fmt:message key='global.btnExit'/>"
                           onclick="javascript:return onExit();"/> <input type="submit"
                                                                          value="Print Pdf"
                                                                          onclick="javascript:return onPrint(true);"/>
                </td>
            </tr>
        </table>

        <!-- class="TableWithBorder" -->
        <table class="outerTable" width="100%">
            <tr>
                <td>
                    <table width="100%">
                        <tr>
                            <td class="title" colspan="3" nowrap="true">LABORATORY
                                REQUISITION
                            </td>
                        </tr>
                        <tr>
                            <td colspan="3" nowrap="true">Requisitioning
                                Physician/Practitioner:<br>
                                <input type="hidden" style="width: 100%" name="provName"
                                       value="<carlos:encode value='<%= props.getProperty("provName", "") %>' context="htmlAttribute"/>"/> <input
                                        type="hidden" style="width: 100%" name="reqProvName"
                                        value="<carlos:encode value='<%= props.getProperty("reqProvName", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("reqProvName", "") %>' context="html"/>&nbsp;<br>
                                    <%-- Dr. Hunter wants the form to say "Physician" instead of "Family Physician".  This is a quick and dirty hack to make it work.  This
                     should really be rewritten more elegantly at some later point in time. --%>
                                <br><%=oscarProps.getProperty("clinic_no", "").startsWith("1022") ? "Physician:" : "Family Physician:"%>
                                <br>
                                <carlos:encode value='<%= props.getProperty("provName", "") == null ? "" : props.getProperty("provName", "") %>' context="html"/>&nbsp;<br>
                                <input type="hidden" style="width: 100%" name="clinicName"
                                       value="<carlos:encode value='<%= props.getProperty("clinicName","") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("clinicName", "") %>' context="html"/>&nbsp;<br>
                                <input type="hidden" style="width: 100%" name="clinicAddress"
                                       value="<carlos:encode value='<%= props.getProperty("clinicAddress", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("clinicAddress", "") %>' context="html"/>&nbsp;<br>
                                <input type="hidden" style="width: 100%" name="clinicCity"
                                       value="<carlos:encode value='<%= props.getProperty("clinicCity", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("clinicCity", "") %>' context="html"/>
                                ,<carlos:encode value='<%= props.getProperty("clinicProvince", "") %>' context="html"/><br>
                                <input type="hidden" style="width: 100%" name="clinicPC"
                                       value="<carlos:encode value='<%= props.getProperty("clinicPC", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("clinicPC", "") %>' context="html"/>&nbsp;<br>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table width="100%" border="1"
                                       style="border-right: 0; border-bottom: 0;" cellspacing="0">
                                    <tr>
                                        <td colspan="3">Physician/Practitioner Number<br>
                                            <input type="hidden" name="practitionerNo"
                                                   value="<carlos:encode value='<%= props.getProperty("practitionerNo", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("practitionerNo", "") %>' context="html"/>&nbsp;
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <table>
                                                <tr>
                                                    <td nowrap="true" valign="top">Check one:</td>
                                                    <td><input type="checkbox" name="ohip"
                                                            <%="checked='checked'".equals(props.getProperty("ohip", "")) ? "checked='checked'" : ""%> /><br>
                                                        <input type="checkbox" name="thirdParty"
                                                                <%="checked='checked'".equals(props.getProperty("thirdParty", "")) ? "checked='checked'" : ""%> /><br>
                                                        <input type="checkbox" name="wcb"
                                                                <%="checked='checked'".equals(props.getProperty("wcb", "")) ? "checked='checked'" : ""%> /><br>
                                                    </td>
                                                    <td nowrap="true">OHIP/Insured<br>
                                                        Third Party/Uninsured<br>
                                                        WCB
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="3">Additional Clinical Information<br>
                                            <textarea name="aci"
                                                      style="width: 100%; height: 59px;"><carlos:encode value='<%= props.getProperty("aci", "") %>' context="html"/></textarea>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </td>
                <td width="100%">
                    <table width="100%" cellspacing="0" border="1"
                           style="border-bottom: 0;">
                        <tr>
                            <td class="lab" valign="top">Laboratory Number <br>
                                <br>
                                <br>
                                <br>
                                <br>
                            </td>
                            <td class="lab" colspan="2" rowspan="2" valign="top" width="65%">
                                Laboratory Name and Address
                            </td>
                        </tr>
                        <tr>
                            <td class="lab" valign="top">Total Fee <br>
                                <br>
                                <br>
                                <br>
                            </td>
                        </tr>
                        <tr>
                            <td class="lab" valign="top">Laboratory Accounting Number<br>
                                <br>
                            </td>
                            <td class="lab" valign="top">Service Date (yyyy/mm/dd)</td>
                            <td class="lab" valign="top">Ref. Lab.</td>
                        </tr>
                        <tr>
                            <td colspan="3">
                                <table width="100%">
                                    <tr>
                                        <td width="33%"><input type="hidden" style="width: 90%"
                                                               name="patientName"
                                                               value="<carlos:encode value='<%= props.getProperty("patientName", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("patientName", "") %>' context="html"/>&nbsp;
                                        </td>
                                        <td>Health Number:</td>
                                        <td><input type="hidden" name="healthNumber" size="10"
                                                   value="<carlos:encode value='<%= props.getProperty("healthNumber", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("healthNumber", "") %>' context="html"/>&nbsp;
                                        </td>
                                        <td>Province:</td>
                                        <td><input type="hidden" name="province" size="12"
                                                   value="<carlos:encode value='<%= props.getProperty("province", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("province", "") %>' context="html"/>&nbsp;
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><input type="hidden" style="width: 90%"
                                                   name="patientAddress"
                                                   value="<carlos:encode value='<%= props.getProperty("patientAddress", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("patientAddress", "") %>' context="html"/>
                                        </td>
                                        <td>Version:</td>
                                        <td><input type="hidden" name="version" size="10"
                                                   value="<carlos:encode value='<%= props.getProperty("version", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("version", "") %>' context="html"/>
                                        </td>
                                        <td>Other Registration Number:</td>
                                        <td><input type="text" name="orn" size="12"
                                                   value="<carlos:encode value='<%= props.getProperty("orn", "") %>' context="htmlAttribute"/>"/></td>
                                    </tr>
                                    <td><input type="hidden" style="width: 90%"
                                               name="patientCity"
                                               value="<carlos:encode value='<%= props.getProperty("patientCity", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("patientCity", "") %>' context="html"/>
                                    </td>
                                    <td>Date of Birth:</td>
                                    <td><input type="hidden" name="birthDate" size="10"
                                               value="<carlos:encode value='<%= props.getProperty("birthDate", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("birthDate", "") %>' context="html"/>
                                    </td>
                                    <td>Phone Number:</td>
                                    <td><input type="hidden" name="phoneNumber" size="12"
                                               value="<carlos:encode value='<%= props.getProperty("phoneNumber", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("phoneNumber", "") %>' context="html"/>
                                    </td>
                        </tr>
                        <td><input type="hidden" style="width: 90%" name="patientPC"
                                   value="<carlos:encode value='<%= props.getProperty("patientPC", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("patientPC", "") %>' context="html"/>
                        </td>
                        <td>Payment Program:</td>
                        <td><input type="text" name="paymentProgram" size="10"
                                   value="<carlos:encode value='<%= props.getProperty("paymentProgram", "") %>' context="htmlAttribute"/>"/></td>
                        <td>Sex:</td>
                        <td><input type="hidden" name="sex" size="12"
                                   value="<carlos:encode value='<%= props.getProperty("sex", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("sex", "") %>' context="html"/>
                        </td>
            </tr>
        </table>
        </td>
        </tr>
        </table>
        </td>
        </tr>
        <tr>
            <td colspan="2">
                <table class="test" width="100%" border="1" cellspacing="0">
                    <tr>
                        <td width="33%">
                            <table border="1"
                                   style="border-top: 0; border-left: 0; border-right: 0; border-bottom: 0;"
                                   cellspacing="0">
                                <tr class="testType">
                                    <td style="border-right: 0;">&nbsp;</td>
                                    <td style="border-left: 0; width: 100%;"><a>Biochemistry</a>
                                    </td>
                                    <td class="code" nowrap="true">LAB CODE</td>
                                    <td class="code" nowrap="true">FEE CODE</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_glucose"
                                            <%="checked='checked'".equals(props.getProperty("b_glucose", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Glucose</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_creatine"
                                            <%="checked='checked'".equals(props.getProperty("b_creatine", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Creatinine</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_uricAcid"
                                            <%="checked='checked'".equals(props.getProperty("b_uricAcid", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Uric Acid</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_sodium"
                                            <%="checked='checked'".equals(props.getProperty("b_sodium", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Sodium</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_potassium"
                                            <%="checked='checked'".equals(props.getProperty("b_potassium", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Potassium</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_chloride"
                                            <%="checked='checked'".equals(props.getProperty("b_chloride", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Chloride</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_ast"
                                            <%="checked='checked'".equals(props.getProperty("b_ast", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>AST (SGOT)</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_alkPhosphate"
                                            <%="checked='checked'".equals(props.getProperty("b_alkPhosphate", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Alk. Phosphate</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_bilirubin"
                                            <%="checked='checked'".equals(props.getProperty("b_bilirubin", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Bilirubin</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_cholesterol"
                                            <%="checked='checked'".equals(props.getProperty("b_cholesterol", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Cholesterol</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_triglyceride"
                                            <%="checked='checked'".equals(props.getProperty("b_triglyceride", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Triglyceride</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="b_urinalysis"
                                            <%="checked='checked'".equals(props.getProperty("b_urinalysis", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Urinalysis (chemical)</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td colspan="4">
                                        <table width="100%">
                                            <tr>
                                                <td colspan="4">Viral Hepatitis (check <u>one</u> only)</td>
                                            </tr>
                                            <tr>
                                                <td><input type="checkbox" name="v_acuteHepatitis"
                                                        <%="checked='checked'".equals(props.getProperty("v_acuteHepatitis", "")) ? "checked='checked'" : ""%> /></td>
                                                <td colspan="3">Acute hepatitis</td>
                                            </tr>
                                            <tr>
                                                <td><input type="checkbox" name="v_chronicHepatitis"
                                                        <%="checked='checked'".equals(props.getProperty("v_chronicHepatitis", "")) ? "checked='checked'" : ""%> /></td>
                                                <td colspan="3">Chronic hepatitis</td>
                                            </tr>
                                            <tr>
                                                <td><input type="checkbox" name="v_immune"
                                                        <%="checked='checked'".equals(props.getProperty("v_immune", "")) ? "checked='checked'" : ""%> /></td>
                                                <td colspan="3">Immune status / prev. exposure</td>
                                            <tr>
                                                <td colspan="2">Specify:</td>
                                                <td>Hepatitis A</td>
                                                <td><input type="text" name="v_hepA"
                                                           value="<carlos:encode value='<%= props.getProperty("v_hepA", "") %>' context="htmlAttribute"/>"
                                                           style="width: 100%;"/></td>
                                            </tr>
                                            <tr>
                                                <td colspan="2">&nbsp;</td>
                                                <td>Hepatitis B</td>
                                                <td><input type="text" name="v_hepB"
                                                           value="<carlos:encode value='<%= props.getProperty("v_hepB", "") %>' context="htmlAttribute"/>"
                                                           style="width: 100%;"/></td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                                <tr>
                                    <td colspan="4">
                                        <table>
                                            <tr>
                                                <td colspan="2">"I certify the tests ordered are not for
                                                    registered in or out patients of a hospital".<br>
                                                    <br>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>Signature</td>
                                                <td>Date</td>
                                            </tr>
                                            <tr>
                                                <td>_________________</td>
                                                <td><input type="text" name="formDate" size="10"
                                                           value="<carlos:encode value='<%= props.getProperty("formDate", "") %>' context="htmlAttribute"/>"/></td>
                                            </tr>
                                        </table>
                                    </td>
                                </tr>
                            </table>
                        </td>
                        <td width="33%">
                            <table border="1"
                                   style="border-top: 0; border-left: 0; border-right: 0; border-bottom: 0;"
                                   cellspacing="0">
                                <tr class="testType">
                                    <td style="border-right: 0;">&nbsp;</td>
                                    <td style="border-left: 0; width: 100%;"><a>Hematology</a></td>
                                    <td class="code" nowrap="true">LAB CODE</td>
                                    <td class="code" nowrap="true">FEE CODE</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="h_bloodFilmExam"
                                            <%="checked='checked'".equals(props.getProperty("h_bloodFilmExam", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Blood Film Exam</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="h_hemoglobin"
                                            <%="checked='checked'".equals(props.getProperty("h_hemoglobin", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Hemoglobin</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="h_wcbCount"
                                            <%="checked='checked'".equals(props.getProperty("h_wcbCount", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">W.C.B. count</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="h_hematocrit"
                                            <%="checked='checked'".equals(props.getProperty("h_hematocrit", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Hematocrit</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="h_prothrombTime"
                                            <%="checked='checked'".equals(props.getProperty("h_prothrombTime", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Prothromb. time</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="h_otherC"
                                            <%="checked='checked'".equals(props.getProperty("h_otherC", "")) ? "checked='checked'" : ""%> /></td>
                                    <td style="padding-bottom: 1px"><input type="text"
                                                                           name="h_other"
                                                                           value="<carlos:encode value='<%= props.getProperty("h_other", "") %>' context="htmlAttribute"/>"/>
                                    </td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr class="testType">
                                    <td style="border-right: 0;">&nbsp;</td>
                                    <td colspan="3" style="border-left: 0; width: 100%;"><a>Immunology</a>
                                    </td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="i_pregnancyTest"
                                            <%="checked='checked'".equals(props.getProperty("i_pregnancyTest", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Pregnancy Test</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="i_heterophile"
                                            <%="checked='checked'".equals(props.getProperty("i_heterophile", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Heterophile antibodies screen</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="i_rubella"
                                            <%="checked='checked'".equals(props.getProperty("i_rubella", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Rubella</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="i_prenatal"
                                            <%="checked='checked'".equals(props.getProperty("i_prenatal", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Prenatal: <small>ABO, RhD, anitbody screen
                                        (titre and ident. if positive</small></td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="i_repeatPrenatal"
                                            <%="checked='checked'".equals(props.getProperty("i_repeatPrenatal", "")) ? "checked='checked'" : ""%> /></td>
                                    <td>Repeat Prenatal antibodies</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="i_prenatalHepatitisB"
                                            <%="checked='checked'".equals(props.getProperty("i_prenatalHepatitisB", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Prenatal Hepatitis B</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="i_vdrl"
                                            <%="checked='checked'".equals(props.getProperty("i_vdrl", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">VDRL</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="i_otherC"
                                            <%="checked='checked'".equals(props.getProperty("i_otherC", "")) ? "checked='checked'" : ""%> /></td>
                                    <td style="padding-bottom: 1px"><input type="text"
                                                                           name="i_other"
                                                                           value="<carlos:encode value='<%= props.getProperty("i_other", "") %>' context="htmlAttribute"/>"/>
                                    </td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr class="testType">
                                    <td style="border-right: 0;">&nbsp;</td>
                                    <td colspan="3" style="border-left: 0; width: 100%;"><a>Microbiology</a>
                                        Sensitivities if warranted
                                    </td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="m_cervicalVaginal"
                                            <%="checked='checked'".equals(props.getProperty("m_cervicalVaginal", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Cervical, vaginal</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="m_sputum"
                                            <%="checked='checked'".equals(props.getProperty("m_sputum", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Sputum</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="m_throat"
                                            <%="checked='checked'".equals(props.getProperty("m_throat", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Throat</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="m_urine"
                                            <%="checked='checked'".equals(props.getProperty("m_urine", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Urine</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td><input type="checkbox" name="m_stoolCulture"
                                            <%="checked='checked'".equals(props.getProperty("m_stoolCulture", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true">Stool culture</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td valign="top"><input type="checkbox" name="m_otherSwabs"
                                            <%="checked='checked'".equals(props.getProperty("m_otherSwabs", "")) ? "checked='checked'" : ""%> /></td>
                                    <td nowrap="true" style="padding-bottom: 2px"><input
                                            type="text" style="width: 100%;" name="m_other"
                                            value="<carlos:encode value='<%= props.getProperty("m_other", "") %>' context="htmlAttribute"/>"/></td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                            </table>
                        </td>
                        <td width="33%">
                            <table border="1"
                                   style="border-top: 0; border-left: 0; border-right: 0; border-bottom: 0;"
                                   cellspacing="0">
                                <tr class="testType">
                                    <td><a>Other test, one per line</a> (please use terminology
                                        of the Schedule of Benefits)
                                    </td>
                                    <td class="code">LAB CODE</td>
                                    <td class="code">FEE CODE</td>
                                    <td class="code">NO OF SERV</td>
                                </tr>
                                <tr>
                                    <td rowspan="9"><textarea name="otherTest"
                                                              style="width: 100%; height: 159px; overflow: auto;"><carlos:encode value='<%= props.getProperty("otherTest", "") %>' context="html"/></textarea>
                                    </td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr class="testType">
                                    <td colspan="4"><a>Laboratory use only</a></td>
                                </tr>
                                <tr>
                                    <td>Documentation Fee</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>Gyn. Specimen (Pap Smear)</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                                <tr>
                                    <td style="padding-bottom: 1px;">&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                    <td>&nbsp;</td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        </table>

        <table class="Head" class="hidePrint">
            <tr>
                <td nowrap="true">
                    <% if (!readOnly) { %> <input type="submit" value="<fmt:message key='global.save'/>"
                                                  onclick="javascript:return onSave();"/> <input type="submit"
                                                                                                 value="<fmt:message key='global.saveExit'/>"
                                                                                                 onclick="javascript:return onSaveExit();"/> <% } %>
                    <input type="submit" value="<fmt:message key='global.btnExit'/>"
                           onclick="javascript:return onExit();"/> <input type="submit"
                                                                          value="Print Pdf"
                                                                          onclick="javascript:return onPrint(true);"/>
                </td>
            </tr>
        </table>

    </form>
    </body>
</html>
