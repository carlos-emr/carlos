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

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.LocaleUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.FrmLabReqPreSetDao, io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.CarlosProperties, java.util.Date, io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@page import="io.github.carlos_emr.carlos.prescript.data.RxProviderData, io.github.carlos_emr.carlos.prescript.data.RxProviderData.Provider" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils,io.github.carlos_emr.carlos.clinic.ClinicData" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordHelp" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmLabReq10Record" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Laboratory Requisition</title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" media="screen"
              href="<%= request.getContextPath() %>/form/labReq07Style.css">
        <link rel="stylesheet" type="text/css" media="print" href="<%= request.getContextPath() %>/form/print.css">
        <script src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>

    <%
        String formClass = "LabReq10";
        String formLink = "formlabreq10.jsp";

        ClinicData clinic = new ClinicData();
        RxProviderData rx = new RxProviderData();
        List<Provider> prList = rx.getAllProviders();

        ProgramDao programDao = SpringUtils.getBean(ProgramDao.class);
        List<Program> programList = programDao.getAllActivePrograms();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);


        boolean readOnly = false;
        int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
        int formId = Integer.parseInt(request.getParameter("formId"));
        String provNo = (String) session.getAttribute("user");
        String fromSession = request.getParameter("fromSession");
        java.util.Properties props = null;

        FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
        if (fromSession != null && fromSession.equals("true")) {
            props = (java.util.Properties) request.getSession().getAttribute("labReq10" + demoNo);
        }
        if (props == null) {
            props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);
            props = ((FrmLabReq10Record) rec).getFormCustRecord(props, provNo);
        }

        MiscUtils.getLogger().debug("properties : " + props);

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


        function onPrintPDF() {

            var ret = checkAllDates();
            if (ret == true) {

                //ret = confirm("Do you wish to save this form and view the print preview?");
                //popupFixedPage(650,850,'<%= request.getContextPath() %>/provider/notice.htm');
                temp = document.forms[0].action;
                document.forms[0].action = "form/formname?__title=Lab+Request&__cfgfile=labReqPrintEncounterForm2010&__template=labReqForm2010";
                document.forms[0].submit.value = "printall";
                document.forms[0].target = "_self";
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
                if (dt[1] == null)
                    dt = dateString.split('-');
                var y = dt[0];
                var m = dt[1];
                var d = dt[2];
                var orderString = m + '/' + d + '/' + y;
                var pass = isDate(orderString);

                if (pass != true) {
                    var s = dateBox.name;
                    //alert('Invalid '+pass+' in field ' + s.substring(3));
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
            if (valDate(document.forms[0].b_dateSigned) == false) {
                alert("The 'Signature Date' field is not valid");
                b = false;
            }
            if (valDate(document.forms[0].o_specimenCollectionDate) == false) {
                alert("The 'Specimen Collection Date' field is not valid");
                b = false;
            }
            return b;

        }

        function popup(link) {
            windowprops = "height=700, width=960,location=no,"
                + "scrollbars=yes, menubars=no, toolbars=no, resizable=no, top=0, left=0 titlebar=yes";
            window.open(link, "_blank", windowprops);
        }


        var providerData = new Object(); //{};
        <%
        for (Provider p : prList) {
            if (!p.getProviderNo().equalsIgnoreCase("-1")) {
                String prov_no = "prov_"+p.getProviderNo();

                %>
        providerData['<carlos:encode value='<%= prov_no %>' context="javaScriptBlock"/>'] = new Object(); //{};

        providerData['<carlos:encode value='<%= prov_no %>' context="javaScriptBlock"/>'].address = "<carlos:encode value='<%= StringUtils.noNull(p.getClinicAddress()) %>' context="javaScriptBlock"/>";
        providerData['<carlos:encode value='<%= prov_no %>' context="javaScriptBlock"/>'].city = "<carlos:encode value='<%= StringUtils.noNull(p.getClinicCity()) %>' context="javaScriptBlock"/>";
        providerData['<carlos:encode value='<%= prov_no %>' context="javaScriptBlock"/>'].province = "<carlos:encode value='<%= StringUtils.noNull(p.getClinicProvince()) %>' context="javaScriptBlock"/>";
        providerData['<carlos:encode value='<%= prov_no %>' context="javaScriptBlock"/>'].postal = "<carlos:encode value='<%= StringUtils.noNull(p.getClinicPostal()) %>' context="javaScriptBlock"/>";


        <%	}
        }


    if (CarlosProperties.getInstance().getBooleanProperty("consultation_program_letterhead_enabled", "true")) {
        if (programList != null) {
            for (Program p : programList) {
                String progNo = "prog_" + p.getId();
    %>
        providerData['<carlos:encode value='<%= progNo %>' context="javaScriptBlock"/>'] = new Object();
        providerData['<carlos:encode value='<%= progNo %>' context="javaScriptBlock"/>'].address = "<carlos:encode value='<%= (p.getAddress() != null && p.getAddress().trim().length() > 0) ? p.getAddress().trim() : ((StringUtils.noNull(clinic.getClinicAddress()) + "  " + StringUtils.noNull(clinic.getClinicCity()) + "   " + StringUtils.noNull(clinic.getClinicProvince()) + "  " + StringUtils.noNull(clinic.getClinicPostal())).trim()) %>' context="javaScriptBlock"/>";
        providerData['<carlos:encode value='<%= progNo %>' context="javaScriptBlock"/>'].city = "";
        providerData['<carlos:encode value='<%= progNo %>' context="javaScriptBlock"/>'].province = "";
        providerData['<carlos:encode value='<%= progNo %>' context="javaScriptBlock"/>'].postal = "";
        <%
                }
            }
        } %>


        function switchProvider(value) {

            if (value == -1) {
                $("select[name='letterhead']").val(value);
                $("input[name='clinicName']").val("<carlos:encode value='<%= StringUtils.noNull(clinic.getClinicName()) %>' context="javaScriptBlock"/>");
                $("input[name='clinicAddress']").val("<carlos:encode value='<%= StringUtils.noNull(clinic.getClinicAddress()) %>' context="javaScriptBlock"/>");
                $("input[name='clinicCity']").val("<carlos:encode value='<%= StringUtils.noNull(clinic.getClinicCity()) + " " + StringUtils.noNull(clinic.getClinicProvince()) %>' context="javaScriptBlock"/>");
                $("input[name='clinicPC']").val("<carlos:encode value='<%= StringUtils.noNull(clinic.getClinicPostal()) %>' context="javaScriptBlock"/>");

                $("#clinicName").text("<carlos:encode value='<%= StringUtils.noNull(clinic.getClinicName()) %>' context="javaScriptBlock"/>");
                $("#clinicAddress").text("<carlos:encode value='<%= StringUtils.noNull(clinic.getClinicAddress()) %>' context="javaScriptBlock"/>");
                $("#clinicCity").text("<carlos:encode value='<%= StringUtils.noNull(clinic.getClinicCity()) + " " + StringUtils.noNull(clinic.getClinicProvince()) %>' context="javaScriptBlock"/>");
                $("#clinicPC").text("<carlos:encode value='<%= StringUtils.noNull(clinic.getClinicPostal()) %>' context="javaScriptBlock"/>");

            } else {

                if (typeof providerData["prov_" + value] != "undefined")
                    value = "prov_" + value;

                $("select[name='letterhead']").val(value);

                $("input[name='clinicName']").val("");
                $("input[name='clinicAddress']").val(providerData[value]['address']);
                $("input[name='clinicCity']").val(providerData[value]['city'] + providerData[value]['province']);
                $("input[name='clinicPC']").val(providerData[value]['postal']);

                $("#clinicName").text("");
                $("#clinicAddress").text(providerData[value]['address']);
                $("#clinicCity").text(providerData[value]['city'] + " " + providerData[value]['province']);
                $("#clinicPC").text(providerData[value]['postal']);
            }
        }

        $(document).ready(function () {
            switchProvider($("select[name='letterhead']").val());
        });
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
                    <% if (!readOnly) { %>
                    <input type="submit" value="<fmt:message key="global.save"/>" onclick="javascript:return onSave();"/>
                    <input type="submit" value="<fmt:message key="global.saveExit"/>" onclick="javascript:return onSaveExit();"/>
                    <% } %>
                    <input type="submit" value="<fmt:message key="global.btnExit"/>" onclick="javascript:return onExit();"/>
                    <input type="submit" value="Print Pdf" onclick="javascript:return onPrintPDF();"/>

                    <select name="letterhead" id="letterhead" onchange="switchProvider(this.value)">
                        <option value="-1"><carlos:encode value='<%= StringUtils.noNull(clinic.getClinicName()) %>' context="html"/>
                        </option>
                        <%
                            for (Provider p : prList) {
                                if (p.getProviderNo().compareTo("-1") != 0 && (p.getFirstName() != null || p.getSurname() != null)) {
                        %>
                        <option value="<carlos:encode value='<%= StringUtils.noNull(p.getProviderNo()) %>' context="htmlAttribute"/>" <%=(!props.getProperty("letterhead", "-1").equals("-1") && p.getProviderNo().equals(props.getProperty("letterhead", "-1"))) ? " selected=\"selected\" " : "" %>>

                            <carlos:encode value='<%= StringUtils.noNull(p.getFirstName()) %>' context="html"/> <carlos:encode value='<%= StringUtils.noNull(p.getSurname()) %>' context="html"/>
                        </option>
                        <% }
                        }

                            if (CarlosProperties.getInstance().getBooleanProperty("consultation_program_letterhead_enabled", "true")) {
                                for (Program p : programList) {
                        %>
                        <option value="prog_<carlos:encode value='<%= String.valueOf(p.getId()) %>' context="htmlAttribute"/>" <%=(!props.getProperty("letterhead", "-1").equals("-1") && props.getProperty("letterhead", "-1").equals("prog_" + p.getId())) ? " selected=\"selected\" " : "" %>>
                            <carlos:encode value='<%= StringUtils.noNull(p.getName()) %>' context="html"/>
                        </option>
                        <% }
                        }%>
                    </select>

                </td>
            </tr>
        </table>

        <!-- class="TableWithBorder" -->
        <table class="outerTable" width="100%">
            <tr>
                <td width="30%" class="outerTable">


                    <table width="100%" class="topTable">
                        <tr>
                            <td class="title" colspan="3" nowrap="nowrap">LABORATORY REQUISITION</td>
                        </tr>
                        <tr>
                            <td colspan="3" nowrap="nowrap">Requisitioning
                                Physician/Practitioner:<br>
                                <input type="hidden" style="width: 100%" name="provName"
                                       value="<carlos:encode value='<%= props.getProperty("provName", "") %>' context="htmlAttribute"/>"/> <input
                                        type="hidden" style="width: 100%" name="reqProvName"
                                        value="<carlos:encode value='<%= props.getProperty("reqProvName", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("reqProvName", "") %>' context="html"/>&nbsp;<br>
                                    <%-- Dr. Hunter wants the form to say "Physician" instead of "Family Physician".  This is a quick and dirty hack to make it work.  This
                     should really be rewritten more elegantly at some later point in time. --%>

                                <% if (!oscarProps.getProperty("lab_req_override", "true").equals("true")) { %>
                                <%=oscarProps.getProperty("clinic_no", "").startsWith("1022") ? "Physician:" : "Family Physician:"%>
                                <br>
                                <carlos:encode value='<%= props.getProperty("provName", "") == null ? "" : props.getProperty("provName", "") %>' context="html"/>&nbsp;<br>
                                <% } %>

                                <input type="hidden" style="width: 100%" name="clinicName"
                                       value="<carlos:encode value='<%= props.getProperty("clinicName","") %>' context="htmlAttribute"/>"/><span
                                        id="clinicName"><carlos:encode value='<%= props.getProperty("clinicName", "") %>' context="html"/></span><br>
                                <input type="hidden" style="width: 100%" name="clinicAddress"
                                       value="<carlos:encode value='<%= props.getProperty("clinicAddress", "") %>' context="htmlAttribute"/>"/> <span
                                        id="clinicAddress"><carlos:encode value='<%= props.getProperty("clinicAddress", "") %>' context="html"/></span><br>
                                <input type="hidden" style="width: 100%" name="clinicCity"
                                       value="<carlos:encode value='<%= props.getProperty("clinicCity", "") %>' context="htmlAttribute"/>"/><span
                                        id="clinicCity"> <carlos:encode value='<%= props.getProperty("clinicCity", "") %>' context="html"/>,<carlos:encode value='<%= props.getProperty("clinicProvince", "") %>' context="html"/></span><br>
                                <input type="hidden" style="width: 100%" name="clinicPC"
                                       value="<carlos:encode value='<%= props.getProperty("clinicPC", "") %>' context="htmlAttribute"/>"/><span
                                        id="clinicPC"> <carlos:encode value='<%= props.getProperty("clinicPC", "") %>' context="html"/></span><br>
                            </td>
                        </tr>
                        <tr>
                            <td class="borderGrayTopBottom" style="border-bottom: 0px;"><font
                                    class="subHeading">Physician/Practitioner Number</font><br>
                                <input type="hidden" name="practitionerNo"
                                       value="<carlos:encode value='<%= props.getProperty("practitionerNo", "") %>' context="htmlAttribute"/>"/>
                                <center><carlos:encode value='<%= props.getProperty("practitionerNo", "") %>' context="html"/>&nbsp;</center>
                            </td>
                        </tr>
                        <tr>
                            <td class="borderBlackTopBottom"><b><font
                                    class="subHeading">Check one:</font></b></br>
                                <font style="font-size: 10px;">
                                    <div style="margin-left: 10px;"><input type="checkbox"
                                                                           name="ohip" <%="checked='checked'".equals(props.getProperty("ohip", "")) ? "checked='checked'" : ""%> /><b>OHIP/Insured</b>&nbsp;
                                        &nbsp; <input type="checkbox" name="thirdParty"
                                                <%="checked='checked'".equals(props.getProperty("thirdParty", "")) ? "checked='checked'" : ""%> /><b>Third
                                            Party/Uninsured</b><br>
                                        <input type="checkbox" name="wcb" <%="checked='checked'".equals(props.getProperty("wcb", "")) ? "checked='checked'" : ""%> /><b>WCB</b><br>
                                    </div>
                                </font></td>
                        </tr>
                        <tr>
                            <td class="borderGrayTopBottom" style="border-top: 0px;"><font
                                    class="subHeading">Additional Clinical Information <i
                                    style="font-size: -1;">(e.g. diagnosis)</i></font><br>
                                <textarea name="aci" style="width: 100%; height: 59px;"
                                          tabindex="1">
					<%
                        if (props.getProperty("aci") == null) {
                            if (oscarProps.getProperty("clinic_code") != null)
                                out.print(SafeEncode.forHtml(" \n" + StringUtils.noNull(oscarProps.getProperty("clinic_code"))));
                        } else {
                            out.print(SafeEncode.forHtml(props.getProperty("aci", "").trim()));
                        }
                    %>
					</textarea></td>
                        </tr>
                        <tr>
                            <td style="height: 15px; vertical-align: top;"><font
                                    class="subHeading"><input type="checkbox"
                                                              name="copy2clinician" <%="checked='checked'".equals(props.getProperty("copy2clinician", "")) ? "checked='checked'" : ""%> />Copy
                                to: Clinician/Practitioner</font><br/>
                                <table width="100%">
                                    <tr>
                                        <td style="color:grey;">Last Name</td>
                                        <td style="color:grey;">First Name</td>
                                    </tr>
                                    <tr>
                                        <td><input type="text" name="copyLname"
                                                   value="<carlos:encode value='<%= props.getProperty("copyLname", "") %>' context="htmlAttribute"/>"></td>
                                        <td><input type="text" name="copyFname"
                                                   value="<carlos:encode value='<%= props.getProperty("copyFname", "") %>' context="htmlAttribute"/>"></td>
                                    </tr>
                                    <tr>
                                        <td style="color:grey;">Address</td>
                                        <td style="color:grey;">&nbsp;</td>
                                    </tr>
                                    <tr>
                                        <td colspan="2"><textarea name="copyAddress"
                                                                  style="width:100%;"><carlos:encode value='<%= props.getProperty("copyAddress", "") %>' context="html"/></textarea>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </td>


                <td width="70%" class="outerTable">


                    <table width="100%" class="topTable">
                        <tr>
                            <td class="labArea" style="vertical-align: top; height: 154px;"
                                colspan="3"><b><i><font class="subHeading">Laboratory
                                Use Only:</font></i></b></br>
                            </td>
                        </tr>
                        <tr>
                            <td class="borderGray"
                                style="border-left: 0; height: 33px; vertical-align: top; border-bottom: 1px solid black;">
                                <font class="subHeading">Clinician/Practitioner's Contact
                                    Number for Urgent Results</font> <input type="text"
                                                                            style="margin-left: 10px; font-size: 10px; width: 200px;"
                                                                            tabindex="2" name="clinicianContactUrgent"
                                                                            value="<carlos:encode value='<%= props.getProperty("clinicianContactUrgent", "") %>' context="htmlAttribute"/>"/>
                            </td>
                            <td class="labArea borderGray"
                                style="border-right: 0; border-left: 0; border-bottom: 1px solid black; vertical-align: top; width: 200px;">
                                <font
                                        class="subHeading">Service Date (yyyy/mm/dd)</font></td>
                        </tr>
                        <tr>
                            <td colspan="2">
                                <table width="100%">
                                    <tr>
                                        <td class="borderGrayBottomRight"><font class="subHeading">Patient's
                                            Name:</font><br/>
                                            <input type="hidden" style="width: 90%" name="patientName"
                                                   value="<carlos:encode value='<%= props.getProperty("patientName", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("patientName", "") %>' context="html"/>&nbsp;
                                        </td>
                                        <td class="borderGrayBottomRight" style="width: 200px;"><font
                                                class="subHeading">Health Number:</font><br/>
                                            <input type="hidden" name="healthNumber" size="10"
                                                   value="<carlos:encode value='<%= props.getProperty("healthNumber", "") %>' context="htmlAttribute"/>"/>
                                            <center><carlos:encode value='<%= props.getProperty("healthNumber", "") %>' context="html"/>&nbsp;</center>
                                        </td>
                                        <td class="borderGrayBottomRight" style="width: 20px;"><font
                                                class="subHeading">Version:</font><br/>
                                            <input type="hidden" name="version" size="10"
                                                   value="<carlos:encode value='<%= props.getProperty("version", "") %>' context="htmlAttribute"/>"/>
                                            <center><carlos:encode value='<%= props.getProperty("version", "") %>' context="html"/>
                                            </center>
                                        </td>
                                        <td class="borderGrayBottomRight"
                                            style="border-right: 0px; width: 50px;"><font
                                                class="subHeading">HC Type:</font><br/>
                                            <input type="hidden" name="hcType" size="12"
                                                   value="<carlos:encode value='<%= props.getProperty("hcType", "") %>' context="htmlAttribute"/>"/>
                                            <center><carlos:encode value='<%= props.getProperty("hcType", "") %>' context="html"/>&nbsp;</center>
                                        </td>
                                    </tr>
                                </table>
                                <table width="100%">
                                    <tr>
                                        <td class="borderGrayBottomRight"><font class="subHeading">Patient's
                                            Address:</font><br/>
                                            <input type="hidden" style="width: 90%" name="patientAddress"
                                                   value="<carlos:encode value='<%= props.getProperty("patientAddress", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("patientAddress", "") %>' context="html"/>
                                        </td>
                                        <td class="borderGrayBottomRight" style="width: 100px;"><font
                                                class="subHeading">City:</font><br/>
                                            <input type="hidden" style="width: 90%" name="patientCity"
                                                   value="<carlos:encode value='<%= props.getProperty("patientCity", "") %>' context="htmlAttribute"/>"/>
                                            <center><carlos:encode value='<%= props.getProperty("patientCity", "") %>' context="html"/>
                                            </center>
                                        </td>
                                        <td class="borderGrayBottomRight"
                                            style="width: 130px;"><font
                                                class="subHeading">Postal Code:</font><br/>
                                            <input type="hidden" style="width: 90%" name="patientPC"
                                                   value="<carlos:encode value='<%= props.getProperty("patientPC", "") %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("patientPC", "") %>' context="html"/>
                                        </td>
                                        <%
                                            String demoChartNo = "";
                                            if (oscarProps.getProperty("lab_req_include_chartno", "false").equals("true")) {
                                                demoChartNo = LocaleUtils.getMessage(request.getLocale(), "encounter.form.labreq.patientChartNo") + ":" + props.getProperty("patientChartNo", "");
                                            }
                                        %>
                                        <td class="borderGrayBottomRight"
                                            style="border-right: 0px; width: 130px;"><font
                                                class="subHeading"><fmt:message key='encounter.form.labreq.patientChartNo'/></font><br/>
                                            <input type="hidden" style="width: 90%" name="patientChartNo"
                                                   value="<carlos:encode value='<%= demoChartNo %>' context="htmlAttribute"/>"/> <carlos:encode value='<%= props.getProperty("patientChartNo", "") %>' context="html"/>
                                        </td>
                                    </tr>
                                </table>
                                <table width="100%">
                                    <tr>
                                        <td class="borderGrayBottomRight" style="width: 80px;"><font
                                                class="subHeading">Date of Birth:</font><br/>
                                            <input type="hidden" name="birthDate" size="10"
                                                   value="<carlos:encode value='<%= props.getProperty("birthDate", "") %>' context="htmlAttribute"/>"/>
                                            <center><carlos:encode value='<%= props.getProperty("birthDate", "") %>' context="html"/>
                                            </center>
                                        </td>
                                        <td class="borderGrayBottomRight" style="width: 120px"><font
                                                class="subHeading">Other Provincial Registration Number:</font><br/>
                                            <input type="text" name="oprn" tabindex="3"
                                                   value="<carlos:encode value='<%= props.getProperty("oprn", "") %>' context="htmlAttribute"/>"
                                                   style="font-size: 10px; margin-left: 10px;"/></td>
                                        <td class="borderGrayBottomRight" style="width: 70px;"><font
                                                class="subHeading">Sex:</font><br/>
                                            <input type="hidden" name="sex" size="12"
                                                   value="<carlos:encode value='<%= props.getProperty("sex", "") %>' context="htmlAttribute"/>"/>
                                            <input type="hidden" name="male" size="12"
                                                   value="<carlos:encode value='<%= props.getProperty("male", "") %>' context="htmlAttribute"/>"/>
                                            <input type="hidden" name="female" size="12"
                                                   value="<carlos:encode value='<%= props.getProperty("female", "") %>' context="htmlAttribute"/>"/>
                                            <center><carlos:encode value='<%= props.getProperty("sex", "") %>' context="html"/>
                                            </center>
                                        </td>
                                        <td class="borderGrayBottomRight"
                                            style="width: 80px; border-right: 0px;"><font
                                                class="subHeading">Phone Number:</font><br/>
                                            <input type="hidden" name="phoneNumber" size="12"
                                                   value="<carlos:encode value='<%= props.getProperty("phoneNumber", "") %>' context="htmlAttribute"/>"/>
                                            <center><carlos:encode value='<%= props.getProperty("phoneNumber", "") %>' context="html"/>
                                            </center>
                                        </td>
                                    </tr>
                                </table>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="outerTable" colspan="2"><b><i>Note: Separate
                    requisitions are required for cytology, histology / pathology and
                    tests performed by Public Health Laboratory</i></b></td>
            </tr>
            <tr>
                <td colspan="2">
                    <table class="bottomTable" width="100%">
                        <tr>
                            <td width="40%" class="bottomTableTd" rowspan="2">


                                <table class="bottomInnerTable">
                                    <tr>
                                        <th class="checkboxTd">X</th>
                                        <th class="checkboxLabelTd" colspan="2">Biochemistry</th>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_glucose" <%="checked='checked'".equals(props.getProperty("b_glucose", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Glucose &nbsp;
                                            &nbsp; &nbsp; <input type="checkbox" name="b_glucose_random"
                                                    <%="checked='checked'".equals(props.getProperty("b_glucose_random", "")) ? "checked='checked'" : ""%>> Random
                                            &nbsp; &nbsp; <input type="checkbox" name="b_glucose_fasting"
                                                    <%="checked='checked'".equals(props.getProperty("b_glucose_fasting", "")) ? "checked='checked'" : ""%>> Fasting
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox" name="b_hba1c"
                                                <%="checked='checked'".equals(props.getProperty("b_hba1c", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd" colspan="2">HbA1C</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox" name="b_tsh"
                                                <%="checked='checked'".equals(props.getProperty("b_tsh", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd" colspan="2">TSH</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_creatinine" <%="checked='checked'".equals(props.getProperty("b_creatinine", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Creatinine (eGFR)</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_uricAcid" <%="checked='checked'".equals(props.getProperty("b_uricAcid", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Uric Acid</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_sodium" <%="checked='checked'".equals(props.getProperty("b_sodium", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Sodium</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_potassium" <%="checked='checked'".equals(props.getProperty("b_potassium", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Potassium</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_chloride" <%="checked='checked'".equals(props.getProperty("b_chloride", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Chloride</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox" name="b_ck"
                                                <%="checked='checked'".equals(props.getProperty("b_ck", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd" colspan="2">CK</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox" name="b_alt"
                                                <%="checked='checked'".equals(props.getProperty("b_alt", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd" colspan="2">ALT</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_alkPhosphatase"
                                                <%="checked='checked'".equals(props.getProperty("b_alkPhosphatase", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd" colspan="2">Alk. Phosphatase</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_bilirubin" <%="checked='checked'".equals(props.getProperty("b_bilirubin", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Bilirubin</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_albumin" <%="checked='checked'".equals(props.getProperty("b_albumin", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Albumin</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_lipidAssessment"
                                                <%="checked='checked'".equals(props.getProperty("b_lipidAssessment", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd" colspan="2">Lipid Assessment
                                            (includes Cholesterol, HDL-C, Triglycerides, calculated LDL-C &
                                            Chol/HDL-C ratio; individual lipid tests may be ordered in the
                                            "Other Tests" section of this form)
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_vitaminB12" <%="checked='checked'".equals(props.getProperty("b_vitaminB12", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Vitamin B12</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_ferritin" <%="checked='checked'".equals(props.getProperty("b_ferritin", "")) ? "checked='checked'" : ""%>>
                                        </td>
                                        <td class="checkboxLabelTd" colspan="2">Ferritin</td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd"><input type="checkbox"
                                                                      name="b_acRatioUrine"
                                                <%="checked='checked'".equals(props.getProperty("b_acRatioUrine", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd" colspan="2">Albumin/Creatinine
                                            Ratio, Urine
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd bottomEndSection"><input
                                                type="checkbox" name="b_urinalysis"
                                                <%="checked='checked'".equals(props.getProperty("b_urinalysis", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd bottomEndSection" colspan="2">Urinalysis
                                            (Chemical)
                                        </td>
                                    </tr>
                                    <!-----Neonatal heading -->
                                    <tr>
                                        <td class="checkboxTd subSectionHeading"><input
                                                type="checkbox" name="b_neonatalBilirubin"
                                                <%="checked='checked'".equals(props.getProperty("b_neonatalBilirubin", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd subSectionHeading" colspan="2">Neonatal
                                            Bilirubin:
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd" style="border-bottom: 0px;">&nbsp;</td>
                                        <td class="checkboxLabelTd" colspan="2">Child's Age: <input
                                                type="text"
                                                style="width: 28px; text-align: center; margin-left: 10px;"
                                                tabindex="4" name="b_childsAgeDays"
                                                value="<carlos:encode value='<%= props.getProperty("b_childsAgeDays", "") %>' context="htmlAttribute"/>">
                                            days <input type="text"
                                                        style="width: 28px; text-align: center; margin-left: 10px;"
                                                        tabindex="5" name="b_childsAgeHours"
                                                        value="<carlos:encode value='<%= props.getProperty("b_childsAgeHours", "") %>' context="htmlAttribute"/>">
                                            hours
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd" style="border-bottom: 0px;">&nbsp;</td>
                                        <td class="checkboxLabelTd" colspan="2">Clinician/Practitioner's
                                            tel. no. <input type="text"
                                                            style="width: 140px; margin-left: 10px;" tabindex="6"
                                                            name="b_cliniciansTelNo"
                                                            value="<carlos:encode value='<%= props.getProperty("b_cliniciansTelNo", "") %>' context="htmlAttribute"/>">
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd bottomEndSection">&nbsp;</td>
                                        <td class="checkboxLabelTd bottomEndSection" colspan="2">Patient's
                                            24 hr telephone no. <input type="text"
                                                                       style="width: 140px; margin-left: 10px;"
                                                                       tabindex="7"
                                                                       name="b_patientsTelNo"
                                                                       value="<carlos:encode value='<%= props.getProperty("b_patientsTelNo", "") %>' context="htmlAttribute"/>">
                                        </td>
                                    </tr>
                                    <!-----Theraputic Drug Monitoring -->
                                    <tr>
                                        <td class="checkboxTd subSectionHeading"><input
                                                type="checkbox" name="b_therapeuticDrugMonitoring"
                                                <%="checked='checked'".equals(props.getProperty("b_therapeuticDrugMonitoring", "")) ? "checked='checked'" : ""%>></td>
                                        <td class="checkboxLabelTd subSectionHeading" colspan="2">Therapeutic
                                            Drug Monitoring.
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd" style="border-bottom: 0px;">&nbsp;</td>
                                        <td class="checkboxLabelTd" colspan="2">Name of Drug #1: <input
                                                type="text" style="width: 160px; margin-left: 10px;"
                                                tabindex="8" name="b_nameDrug1"
                                                value="<carlos:encode value='<%= props.getProperty("b_nameDrug1", "") %>' context="htmlAttribute"/>"></td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd" style="border-bottom: 0px;">&nbsp;</td>
                                        <td class="checkboxLabelTd" colspan="2">Name of Drug #2: <input
                                                type="text" style="width: 160px; margin-left: 10px;"
                                                tabindex="9" name="b_nameDrug2"
                                                value="<carlos:encode value='<%= props.getProperty("b_nameDrug2", "") %>' context="htmlAttribute"/>"></td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd" style="border-bottom: 0px;">&nbsp;</td>
                                        <td class="checkboxLabelTd">Time Collected #1: <input
                                                type="text"
                                                style="width: 50px; margin-left: 3px; text-align: center;"
                                                tabindex="10" name="b_timeCollected1"
                                                value="<carlos:encode value='<%= props.getProperty("b_timeCollected1", "") %>' context="htmlAttribute"/>">hr
                                        </td>
                                        <td class="checkboxLabelTd2">#2:<input type="text"
                                                                               style="width: 50px; margin-left: 3px; text-align: center;"
                                                                               tabindex="11" name="b_timeCollected2"
                                                                               value="<carlos:encode value='<%= props.getProperty("b_timeCollected2", "") %>' context="htmlAttribute"/>">hr
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd" style="border-bottom: 0px;">&nbsp;</td>
                                        <td class="checkboxLabelTd">Time of Last Dose #1:<input
                                                type="text"
                                                style="width: 50px; margin-left: 3px; text-align: center;"
                                                tabindex="12" name="b_timeLastDose1"
                                                value="<carlos:encode value='<%= props.getProperty("b_timeLastDose1", "") %>' context="htmlAttribute"/>">hr
                                        </td>
                                        <td class="checkboxLabelTd2">#2:<input type="text"
                                                                               style="width: 50px; margin-left: 3px; text-align: center;"
                                                                               tabindex="13" name="b_timeLastDose2"
                                                                               value="<carlos:encode value='<%= props.getProperty("b_timeLastDose2", "") %>' context="htmlAttribute"/>">hr
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxTd bottomEndSection">&nbsp;</td>
                                        <td class="checkboxLabelTd bottomEndSection">Time of Next
                                            Dose #1:<input type="text"
                                                           style="width: 50px; margin-left: 3px; text-align: center;"
                                                           tabindex="14" name="b_timeNextDose1"
                                                           value="<carlos:encode value='<%= props.getProperty("b_timeNextDose1", "") %>' context="htmlAttribute"/>">hr
                                        </td>
                                        <td class="checkboxLabelTd2 bottomEndSection">#2:<input
                                                type="text"
                                                style="width: 50px; margin-left: 3px; text-align: center;"
                                                tabindex="15" name="b_timeNextDose2"
                                                value="<carlos:encode value='<%= props.getProperty("b_timeNextDose2", "") %>' context="htmlAttribute"/>">hr
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxLabelTd" colspan="3"
                                            style="border-bottom: 0px;"><b><i>I hereby certify
                                            the tests ordered are not for registered in or out patients of a
                                            hospital.</i></b><br/>
                                            <br/>
                                            <br/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxLabelTd" colspan="2"
                                            style="border-bottom: 0px;">
                                            _________________________________
                                        </td>
                                        <td class="checkboxLabelTd" style="border-bottom: 0px;"><input
                                                type="text" style="width: 70px;" tabindex="16"
                                                name="b_dateSigned"
                                                value="<carlos:encode value='<%= props.getProperty("b_dateSigned", UtilDateUtilities.getToday("yyyy-MM-dd")) %>' context="htmlAttribute"/>">
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="checkboxLabelTd" colspan="2"
                                            style="border-bottom: 0px;">Clinician/Practitioner
                                            Signature
                                        </td>
                                        <td class="checkboxLabelTd" style="border-bottom: 0px;">
                                            Date
                                        </td>
                                    </tr>

                                </table>


                            </td>
                            <td width="50%" class="bottomTableTd">
                                <table>
                                    <tr>
                                        <td>

                                            <table class="bottomInnerTable">
                                                <tr>
                                                    <th class="checkboxTd">X</th>
                                                    <th class="checkboxLabelTd">Hematology</th>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox" name="h_cbc"
                                                            <%="checked='checked'".equals(props.getProperty("h_cbc", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">CBC</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd bottomEndSection"><input
                                                            type="checkbox" name="h_prothrombinTime"
                                                            <%="checked='checked'".equals(props.getProperty("h_prothrombinTime", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd bottomEndSection">Prothrombin
                                                        Time (INR)"
                                                    </td>
                                                </tr>

                                                <tr>
                                                    <th class="checkboxTd">&nbsp;</th>
                                                    <th class="checkboxLabelTd">Immunology</th>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="i_pregnancyTest"
                                                            <%="checked='checked'".equals(props.getProperty("i_pregnancyTest", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">Pregnancy Test (Urine)</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="i_mononucleosisScreen"
                                                            <%="checked='checked'".equals(props.getProperty("i_mononucleosisScreen", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">Mononucleosis Screen</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="i_rubella" <%="checked='checked'".equals(props.getProperty("i_rubella", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd">Rubella</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="i_prenatal" <%="checked='checked'".equals(props.getProperty("i_prenatal", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd">Prenatal: ABO, RhD, Antibody
                                                        Screen (titre and ident. if positive)
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd bottomEndSection"><input
                                                            type="checkbox" name="i_repeatPrenatalAntibodies"
                                                            <%="checked='checked'".equals(props.getProperty("i_repeatPrenatalAntibodies", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd bottomEndSection">Repeat
                                                        Prenatal Antibodies
                                                    </td>
                                                </tr>

                                                <tr>
                                                    <th class="checkboxTd">&nbsp;</th>
                                                    <th class="checkboxLabelTd">Microbiology ID &
                                                        Sensitivities (if warranted)
                                                    </th>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_cervical" <%="checked='checked'".equals(props.getProperty("m_cervical", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd">Cervical</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_vaginal" <%="checked='checked'".equals(props.getProperty("m_vaginal", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd">Vaginal</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_vaginalRectal"
                                                            <%="checked='checked'".equals(props.getProperty("m_vaginalRectal", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">Vaginal / Rectal - Group B
                                                        Strep
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_chlamydia" <%="checked='checked'".equals(props.getProperty("m_chlamydia", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd">Chlamydia <i>(specify
                                                        source):</i> <input type="text" name="m_chlamydiaSource"
                                                                            style="width: 110px;" tabindex="17"
                                                                            value="<carlos:encode value='<%= props.getProperty("m_chlamydiaSource", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox" name="m_gc"
                                                            <%="checked='checked'".equals(props.getProperty("m_gc", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">GC <i>(specify source):</i> <input
                                                            type="text" name="m_gcSource" style="width: 110px;"
                                                            tabindex="18"
                                                            value="<carlos:encode value='<%= props.getProperty("m_gcSource", "") %>' context="htmlAttribute"/>"></td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_sputum" <%="checked='checked'".equals(props.getProperty("m_sputum", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd">Sputum</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_throat" <%="checked='checked'".equals(props.getProperty("m_throat", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd">Throat</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_wound" <%="checked='checked'".equals(props.getProperty("m_wound", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd">Wound <i>(specify source):</i>
                                                        <input type="text" name="m_woundSource" style="width: 100px;"
                                                               tabindex="19"
                                                               value="<carlos:encode value='<%= props.getProperty("m_woundSource", "") %>' context="htmlAttribute"/>"></td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_urine" <%="checked='checked'".equals(props.getProperty("m_urine", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd">Urine</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_stoolCulture"
                                                            <%="checked='checked'".equals(props.getProperty("m_stoolCulture", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">Stool Culture</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_stoolOvaParasites"
                                                            <%="checked='checked'".equals(props.getProperty("m_stoolOvaParasites", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">Stool Ova & Parasites</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_otherSwabsPus"
                                                            <%="checked='checked'".equals(props.getProperty("m_otherSwabsPus", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">Other Swabs / Pus <i>(specify
                                                        source):</i> <input type="text" name="m_otherSwabsSource"
                                                                            style="width: 100px; margin-left: 10px;"
                                                                            tabindex="20"
                                                                            value="<carlos:encode value='<%= props.getProperty("m_otherSwabsSource", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="m_blank" <%="checked='checked'".equals(props.getProperty("m_blank", "")) ? "checked='checked'" : ""%>>
                                                    </td>
                                                    <td class="checkboxLabelTd"><input type="text"
                                                                                       name="m_blankText"
                                                                                       style="width: 93%;" tabindex="21"
                                                                                       value="<carlos:encode value='<%= props.getProperty("m_blankText", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd">
                                                        <%-- <input type="checkbox" name="m_fecalOccultBlood" <%=props.getProperty("m_fecalOccultBlood", "")%>> --%>&nbsp;
                                                    </td>
                                                    <td class="checkboxLabelTd"><!--Fecal Occult Blood-->&nbsp;</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"
                                                        style="border-bottom: 0px;">Specimen Collection Time
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd bottomEndSection" colspan="2"
                                                        style="text-align: center; border-bottom: 0px;"><input
                                                            type="text" style="width: 60; text-align: center;"
                                                            tabindex="22" name="m_specimenCollectionTime"
                                                            value="<carlos:encode value='<%= props.getProperty("m_specimenCollectionTime", "") %>' context="htmlAttribute"/>">
                                                        hr.
                                                    </td>
                                                </tr>

                                            </table>


                                        </td>
                                        <td width="50%" class="bottomTableTd">


                                            <table class="bottomInnerTable">
                                                <tr>
                                                    <th class="checkboxTd">X</th>
                                                    <th class="checkboxLabelTd">Viral Hepatitis <i>(check
                                                        <b>one</b> only)</i></th>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="v_acuteHepatitis"
                                                            <%="checked='checked'".equals(props.getProperty("v_acuteHepatitis", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">Acute Hepatitis</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="v_chronicHepatitis"
                                                            <%="checked='checked'".equals(props.getProperty("v_chronicHepatitis", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd">Chronic Hepatitis</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="v_immuneStatus"
                                                            <%="checked='checked'".equals(props.getProperty("v_immuneStatus", "")) ? "checked='checked'" : ""%>></td>
                                                    <td class="checkboxLabelTd" style="border-bottom: 0px;">Immune
                                                        Status / Previous Exposure
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxId bottomEndSection">&nbsp;</td>
                                                    <td class="checkboxLabelTd bottomEndSection">
                                                        <table style="font-size: 11px; width: 80%;">
                                                            <tr>
                                                                <td><i>Specify:</i></td>
                                                                <td><input type="checkbox" name="v_immune_HepatitisA"
                                                                        <%="checked='checked'".equals(props.getProperty("v_immune_HepatitisA", "")) ? "checked='checked'" : ""%>>
                                                                </td>
                                                                <td>Hepatitis A</td>
                                                            </tr>
                                                            <tr>
                                                                <td>&nbsp;</td>
                                                                <td><input type="checkbox" name="v_immune_HepatitisB"
                                                                        <%="checked='checked'".equals(props.getProperty("v_immune_HepatitisB", "")) ? "checked='checked'" : ""%>>
                                                                </td>
                                                                <td>Hepatitis B</td>
                                                            </tr>
                                                            <tr>
                                                                <td>&nbsp;</td>
                                                                <td><input type="checkbox" name="v_immune_HepatitisC"
                                                                        <%="checked='checked'".equals(props.getProperty("v_immune_HepatitisC", "")) ? "checked='checked'" : ""%>>
                                                                </td>
                                                                <td>Hepatitis C</td>
                                                            </tr>
                                                            <tr>
                                                                <td colspan="3">or order individual hepatitis tests in
                                                                    the "Other Tests" section below
                                                                </td>
                                                            </tr>
                                                        </table>

                                                    </td>
                                                </tr>

                                                <tr>
                                                    <th colspan="2" class="checkboxLabelTd">Prostate Specific Antigen
                                                        (PSA)
                                                    </th>
                                                </tr>
                                                <tr>
                                                    <td colspan="2" class="checkboxLabelTd bottomEndSection">
                                                                         <span style="float:right; margin-right:5px;">
                                                                            <input type="checkbox" name="psa_free"
                                                                                    <%="checked='checked'".equals(props.getProperty("psa_free", "")) ? "checked='checked'" : ""%>>Free PSA
                                                                        </span>
                                                        <input type="checkbox" name="psa_total"
                                                                <%="checked='checked'".equals(props.getProperty("psa_total", "")) ? "checked='checked'" : ""%>>Total PSA
                                                        <p>Specify one below:<br>

                                                            <input type="checkbox" name="psa_uninsured"
                                                                    <%="checked='checked'".equals(props.getProperty("psa_uninsured", "")) ? "checked='checked'" : ""%>>Screening
                                                            purposes - Uninsured test<br>
                                                            <input type="checkbox" name="psa_insured"
                                                                    <%="checked='checked'".equals(props.getProperty("psa_insured", "")) ? "checked='checked'" : ""%>>Meets OHIP
                                                            elibility criteria - Insured test

                                                        </p>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <th colspan="2" class="checkboxLabelTd">Vitamin D (25-Hydroxy)</th>
                                                </tr>
                                                <tr>
                                                    <td colspan="2" class="checkboxLabelTd bottomEndSection">

                                                        <input type="checkbox" name="vitd_uninsured"
                                                                <%="checked='checked'".equals(props.getProperty("vitd_uninsured", "")) ? "checked='checked'" : ""%>>Uninsured -
                                                        Patient responsible for payment<br>
                                                        <input type="checkbox" name="vitd_insured"
                                                                <%="checked='checked'".equals(props.getProperty("vitd_insured", "")) ? "checked='checked'" : ""%>>Insured -
                                                        Meets OHIP eligibility criteria: osteopenia; osteoporosis;
                                                        rickets; renal disease; malabsorption syndromes; medications
                                                        affecting vitamin D metabolism

                                                        </p>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <th class="checkboxTd">&nbsp;</th>
                                                    <th class="checkboxLabelTd">Other Tests - <font
                                                            style="font-weight: normal;">one test per line</font></th>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"><input type="text"
                                                                                                   style="width: 80%"
                                                                                                   tabindex="23"
                                                                                                   name="o_otherTests1"
                                                                                                   value="<carlos:encode value='<%= props.getProperty("o_otherTests1", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"><input type="text"
                                                                                                   style="width: 80%"
                                                                                                   tabindex="24"
                                                                                                   name="o_otherTests2"
                                                                                                   value="<carlos:encode value='<%= props.getProperty("o_otherTests2", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"><input type="text"
                                                                                                   style="width: 80%"
                                                                                                   tabindex="25"
                                                                                                   name="o_otherTests3"
                                                                                                   value="<carlos:encode value='<%= props.getProperty("o_otherTests3", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"><input type="text"
                                                                                                   style="width: 80%"
                                                                                                   tabindex="26"
                                                                                                   name="o_otherTests4"
                                                                                                   value="<carlos:encode value='<%= props.getProperty("o_otherTests4", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"><input type="text"
                                                                                                   style="width: 80%"
                                                                                                   tabindex="27"
                                                                                                   name="o_otherTests5"
                                                                                                   value="<carlos:encode value='<%= props.getProperty("o_otherTests5", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"><input type="text"
                                                                                                   style="width: 80%"
                                                                                                   tabindex="28"
                                                                                                   name="o_otherTests6"
                                                                                                   value="<carlos:encode value='<%= props.getProperty("o_otherTests6", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"><input type="text"
                                                                                                   style="width: 80%"
                                                                                                   tabindex="29"
                                                                                                   name="o_otherTests7"
                                                                                                   value="<carlos:encode value='<%= props.getProperty("o_otherTests7", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"><input type="text"
                                                                                                   style="width: 80%"
                                                                                                   tabindex="30"
                                                                                                   name="o_otherTests8"
                                                                                                   value="<carlos:encode value='<%= props.getProperty("o_otherTests8", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"><input type="text"
                                                                                                   style="width: 80%"
                                                                                                   tabindex="31"
                                                                                                   name="o_otherTests9"
                                                                                                   value="<carlos:encode value='<%= props.getProperty("o_otherTests9", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2">&nbsp;</td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2">&nbsp;</td>
                                                </tr>
                                                <tr>
                                                    <td style="height: 0px;"></td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd" colspan="2"
                                                        style="border-bottom: 0px;">Specimen Collection Date
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxLabelTd bottomEndSection" colspan="2"
                                                        style="text-align: center; border-bottom: 0px;"><input
                                                            type="text" name="o_specimenCollectionDate"
                                                            style="width: 70; text-align: center;" tabindex="39"
                                                            value="<carlos:encode value='<%= props.getProperty("o_specimenCollectionDate", "") %>' context="htmlAttribute"/>">
                                                    </td>
                                                </tr>
                                                <!--<tr>-->
                                            </table>


                                        </td>
                                    </tr>


                                    <tr>
                                        <td colspan="2">
                                            <table class="bottomInnerTable">
                                                <tr>
                                                    <th class="checkboxLabelTd" colspan="2">Fecal Occult Blood
                                                        Test (FOBT) (check one only)
                                                    </th>
                                                </tr>
                                                <tr>
                                                    <td class="checkboxTd" style="vertical-align: top; width: 30%;">
                                                        <input
                                                                type="checkbox" name="fobt_nonCCC"
                                                                <%="checked='checked'".equals(props.getProperty("fobt_nonCCC", "")) ? "checked='checked'" : ""%>><span
                                                            class="checkboxLabelTd">&nbsp; FOBT (non CCC)</span></td>
                                                    <td class="checkboxTd"><input type="checkbox"
                                                                                  name="fobt_CCC" <%="checked='checked'".equals(props.getProperty("fobt_CCC", "")) ? "checked='checked'" : ""%>><span
                                                            class="checkboxLabelTd">&nbsp; ColonCancerCheck FOBT
									(CCC) no other test can be ordered on this form</span></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="labArea"
                                            style="vertical-align: top; height: 150px; font-size: 11px; padding-left: 10px;"
                                            colspan="2"><b><i>Laboratory Use Only</i></b></td>
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
                                                                          onclick="javascript:return onPrintPDF();"/>
                </td>
            </tr>
        </table>

    </form>
    </body>
</html>
