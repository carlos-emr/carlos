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
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>

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

<%@ page import="io.github.carlos_emr.carlos.form.graphic.*, io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>


<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecord" %>
<%@ page import="io.github.carlos_emr.carlos.form.data.FrmData" %>
<%@ page import="io.github.carlos_emr.carlos.form.FrmRecordFactory" %>
<%@ page import="io.github.carlos_emr.carlos.form.graphic.FrmGraphicAR" %>
<%
    String formClass = "BCAR2007";
    String formLink = "formbcar2007pg2.jsp";

    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));
    int provNo = Integer.parseInt((String) session.getAttribute("user"));
    FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
    java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);

    FrmData fd = new FrmData();
    String resource = fd.getResource();
//resource = resource + "ob/riskinfo/";    props.setProperty("c_lastVisited", "pg2");

//get project_home
    String project_home = request.getContextPath().substring(1);
//sync
    boolean bSync = false;
    if (!props.getProperty("c_surname_cur", "").equals("") && !(props.getProperty("c_surname_cur", "").equals(props.getProperty("c_surname", ""))
            && props.getProperty("c_givenName_cur", "").equals(props.getProperty("c_givenName", ""))
            && props.getProperty("c_address_cur", "").equals(props.getProperty("c_address", ""))
            && props.getProperty("c_city_cur", "").equals(props.getProperty("c_city", ""))
            && props.getProperty("c_province_cur", "").equals(props.getProperty("c_province", ""))
            && props.getProperty("c_postal_cur", "").equals(props.getProperty("c_postal", ""))
            && props.getProperty("c_phn_cur", "").equals(props.getProperty("c_phn", ""))
            && props.getProperty("c_phone_cur", "").trim().equals(props.getProperty("c_phone", "").trim())
            && props.getProperty("c_phoneAlt1_cur", "").trim().equals(props.getProperty("c_phoneAlt1", "").trim())
            && props.getProperty("c_phoneAlt2_cur", "").trim().equals(props.getProperty("c_phoneAlt2", "").trim())
    )) {
        bSync = true;
    }
%>
<%
    boolean bView = false;
    if (request.getParameter("view") != null && request.getParameter("view").equals("1")) bView = true;

//1. <fmt:message key="form.bcar.lmp"/> from AR1 should copy over to AR2 #16 <fmt:message key="form.bcar.lmp"/>
//2. <fmt:message key="form.bcar.age"/> at <fmt:message key="form.bcar.edd"/> on AR1 should copy over to AR2 #16 'age'
    if (props.getProperty("pg1_lmp", "").equals("")) props.setProperty("pg1_lmp", props.getProperty("pg1_lmp", ""));
    if (props.getProperty("ar2_age", "").equals(""))
        props.setProperty("ar2_age", props.getProperty("pg1_ageAtEDD", ""));
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="form.bcar.titlePage2"/></title>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <link rel="stylesheet" type="text/css" href="<%=bView?"bcArStyleView.css" : "bcAr2007Style.css"%>">
        <!-- calendar stylesheet -->
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>

        <!-- main calendar program -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>

        <!-- language for the calendar -->
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:message key='global.javascript.calendar'/>"></script>

        <!-- the following script defines the Calendar.setup helper function, which makes
       adding a calendar a matter of 1 or 2 lines of code. -->
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <style type="text/css">
            <!--
            .demo {
                color: #000033;
                background-color: #cccccc;
                layer-background-color: #cccccc;
                position: absolute;
                top: 150px;
                left: 270px;
                width: 80px;
                height: 120px;
                z-index: 99;
                visibility: hidden;
            }

            .demo1 {
                color: #000033;
                background-color: silver;
                layer-background-color: #cccccc;
                position: absolute;
                top: 40px;
                left: 370px;
                width: 190px;
                height: 80px;
                z-index: 99;
                visibility: hidden;
            }

            .demo2 {
                color: #000033;
                background-color: silver;
                layer-background-color: #cccccc;
                position: absolute;
                top: 40px;
                left: 370px;
                width: 190px;
                height: 80px;
                z-index: 99;
                visibility: hidden;
            }

            .demo3 {
                color: #000033;
                background-color: silver;
                layer-background-color: #cccccc;
                position: absolute;
                top: 220px;
                left: 300px;
                width: 80px;
                height: 30px;
                z-index: 99;
                visibility: hidden;
            }

            .demo4 {
                color: #000033;
                background-color: silver;
                layer-background-color: #cccccc;
                position: absolute;
                top: 50px;
                left: 280px;
                width: 80px;
                height: 30px;
                z-index: 99;
                visibility: hidden;
            }

            .demo5 {
                color: #000033;
                background-color: silver;
                layer-background-color: #cccccc;
                position: absolute;
                z-index: 99;
                visibility: hidden;
                font-size: 11px;
                border: 1px solid black;
            }

            .epdsAnswer {
                padding-left: 20px;
            }

            .tweakcell {
                border: 1px solid black;
                padding: 10px
            }

            -->
        </style>
    </head>
    <script type="text/javascript">
        <!--
        var fieldObj;

        function showHideBox(layerName, iState) { // 1 visible, 0 hidden
            if (document.layers)	   //NN4+
            {
                document.layers[layerName].visibility = iState ? "show" : "hide";
            } else if (document.getElementById)	  //gecko(NN6) + IE 5+
            {
                var obj = document.getElementById(layerName);
                obj.style.visibility = iState ? "visible" : "hidden";
            } else if (document.all)	// IE 4
            {
                document.all[layerName].style.visibility = iState ? "visible" : "hidden";
            }
        }

        function showBox(layerName, iState, field, e) { // 1 visible, 0 hidden
            fieldObj = field;
            //get the number of the field
            fieldName = fieldObj.name;
            fieldName = fieldName.substring("pg2_pos".length);

            if (document.layers) {   //NN4+
                document.layers[layerName].visibility = iState ? "show" : "hide";
            } else if (document.getElementById) {	  //gecko(NN6) + IE 5+
                var obj = document.getElementById(layerName);
                obj.style.top = e.screenY + (481 - e.screenY + 26 * fieldName);
                obj.style.left = "390px";
                obj.style.visibility = iState ? "visible" : "hidden";
            } else if (document.all)	// IE 4
            {
                document.all[layerName].style.visibility = iState ? "visible" : "hidden";
            }
            fieldObj = field;
        }

        function showBMIBox(layerName, iState, field, e) { // 1 visible, 0 hidden
            fieldObj = field;
            //get the number of the field
            fieldName = fieldObj.name;
            //fieldName = fieldName.substring("pg2_pos".length);

            if (document.layers) {   //NN4+
                document.layers[layerName].visibility = iState ? "show" : "hide";
            } else if (document.getElementById) {	  //gecko(NN6) + IE 5+
                var obj = document.getElementById(layerName);
                obj.style.top = e.screenY + (401 - e.screenY + 26 * fieldName);
                obj.style.left = "30px";
                obj.style.visibility = iState ? "visible" : "hidden";
            } else if (document.all)	// IE 4
            {
                document.all[layerName].style.visibility = iState ? "visible" : "hidden";
            }
            fieldObj = field;
        }

        function showPGBox(layerName, iState, field, e, prefix, origX, origY, deltaY) { // 1 visible, 0 hidden
            fieldObj = field;
            //get the number of the field
            fieldName = fieldObj.name;
            fieldName = fieldName.substring(prefix.length);
            if (fieldName == "")
                fieldName = 0;

            if (document.layers) {   //NN4+
                document.layers[layerName].visibility = iState ? "show" : "hide";
            } else if (document.getElementById) {	  //gecko(NN6) + IE 5+
                var obj = document.getElementById(layerName);
                obj.style.top = e.screenY + (origY - e.screenY + deltaY * fieldName);
                obj.style.left = origX;
                obj.style.visibility = iState ? "visible" : "hidden";
            } else if (document.all) {// IE 4
                document.all[layerName].style.visibility = iState ? "visible" : "hidden";
            }
            fieldObj = field;
        }

        function insertBox(str, layerName) { // 1 visible, 0 hidden
            if (document.getElementById) {
                //var obj = document.getElementById(field);
                fieldObj.value = str;
            }
            showHideBox(layerName, 0);
        }

        function showDef(str, field) {
            if (document.getElementById) {
                field.value = str;
            }
        }

        function syncDemo() {
            document.forms[0].c_surname.value = "<%=props.getProperty("c_surname_cur", "")%>";
            document.forms[0].c_givenName.value = "<%=props.getProperty("c_givenName_cur", "")%>";
            document.forms[0].c_address.value = "<%=props.getProperty("c_address_cur", "")%>";
            document.forms[0].c_city.value = "<%=props.getProperty("c_city_cur", "")%>";
            document.forms[0].c_province.value = "<%=props.getProperty("c_province_cur", "")%>";
            document.forms[0].c_postal.value = "<%=props.getProperty("c_postal_cur", "")%>";
            document.forms[0].c_phn.value = "<%=props.getProperty("c_phn_cur", "")%>";
            document.forms[0].c_phone.value = "<%=props.getProperty("c_phone_cur", "")%>";
            document.forms[0].c_phoneAlt1.value = "<%=props.getProperty("c_phoneAlt1_cur", "")%>";
            document.forms[0].c_phoneAlt2.value = "<%=props.getProperty("c_phoneAlt2_cur", "")%>";

        }


        function wtEnglish2Metric(obj) {
            //if(isNumber(document.forms[0].c_ppWt) ) {
            //	weight = document.forms[0].c_ppWt.value;
            if (isNumber(obj)) {
                weight = obj.value;
                weightM = Math.round(weight * 10 * 0.4536) / 10;
                if (confirm("<fmt:message key='form.bcar2007.msgChangeWeight'/>".replace("{0}", weight).replace("{1}", weightM))) {
                    //document.forms[0].c_ppWt.value = weightM;
                    obj.value = weightM;
                }
            }
        }

        function calcEDDAge() {

            var DOB_array = DOB.value.split("/");
            var EDD = c_EDD.value;
            var age = 0;
            if (EDD.length != 10) {
                alert("<fmt:message key='form.bcar2007.msgEnterEddFirst'/>");
            } else if (DOB.value.length != 10) {
                alert("<fmt:message key='form.bcar2007.msgEnterDobFirst'/>");
            } else {
                var EDD_array = EDD.split("/");
                age = EDD_array[2] - DOB_array[2];
                if (EDD_array[1] < DOB_array[1]) {
                    age--;
                } else if (EDD_array[1] == DOB_array[1] && EDD_array[0] < DOB_array[0]) {
                    age--;
                }

                ar2_age.value = age;
            }
        }

        function calcBMI(field, weight) {
            if (confirm("<fmt:message key='form.bcar2007.msgBeforeBmi'/>")) {

                if (isNumber(weight) && isNumber(document.forms[0].height)) {
                    var ht = document.forms[0].height.value / 100;
                    var wt = weight.value;
                    if (wt != "" && wt != "0" && ht != "" && ht != "0") {
                        field.value = Math.round(wt * 10 / ht / ht) / 10;
                    } else {
                        alert("<fmt:message key='form.bcar2007.msgValidWeightHeight'/>");
                }
            } else {
                    alert("<fmt:message key='form.bcar2007.msgValidWeightHeight'/>");
                }
            }
        }

        function calcBMIMetric() {
            if (isNumber(document.forms[0].c_ppWt) && isNumber(document.forms[0].c_ppHt)) {
                weight = document.forms[0].c_ppWt.value;
                height = document.forms[0].c_ppHt.value / 100;
                if (weight != "" && weight != "0" && height != "" && height != "0") {
                    document.forms[0].c_ppBMI.value = Math.round(weight * 10 / height / height) / 10;
                }
            }
        }

        // -->
    </script>

    <script type="text/javascript" language="Javascript">
        function reset() {
            document.forms[0].target = "";
            document.forms[0].action = "/<%=project_home%>/form/formname";
        }

        function onPrint() {
            document.forms[0].submit.value = "print";
            var ret = checkAllDates();
            if (ret == true) {
                document.forms[0].action = "<%= request.getContextPath() %>/form/createpdf?__title=British+Columbia+Antenatal+Record+Part+2&__cfgfile=bcar2PrintCfgPg1_2007&__cfgGraphicFile=bcar2PrintGraphCfgPg1_2007&__template=bcar2_2007";
                document.forms[0].target = "_blank";
            }
            return ret;
        }

        function onPrintScores() {
            document.forms[0].submit.value = "print";
            var ret = checkAllDates();
            if (ret == true) {
                document.forms[0].action = "<%= request.getContextPath() %>/form/createpdf?__title=British+Columbia+Antenatal+Record+EPDS/TWEAK+Scores&__cfgfile=bcar2PrintCfgScores_2007&__template=EPDS_TWEAK";
                document.forms[0].target = "_blank";
            }
            return ret;
        }

        function onPrint12() {
            document.forms[0].submit.value = "printAll";

            document.forms[0].action = "<%= request.getContextPath() %>/form/formname?__title=British+Columbia+Antenatal+Record&__cfgfile=bcar1PrintCfgPg1_2007&__cfgfile=bcar2PrintCfgPg1_2007&__cfgGraphicFile=bcar2PrintGraphCfgPg1_2007&__graphicPage=2<%= props.getProperty("pg3_date1", "") == "" ? "&__template=bcarARs2_2007" : "&__cfgfile=bcar2PrintCfgPg2_2007&__graphicPage=3&__template=bcarARs1_2007" %>";
            document.forms[0].target = "_blank";

            return true;
        }

        function onPrintAll() {
            document.forms[0].submit.value = "printAll";

            document.forms[0].action = "<%= request.getContextPath() %>/form/formname?__title=British+Columbia+Antenatal+Record&__cfgfile=bcar1PrintCfgPg1_2007&__cfgfile=bcar2PrintCfgPg1_2007&__cfgGraphicFile=bcar2PrintGraphCfgPg1_2007&__graphicPage=2<%= props.getProperty("pg3_date1", "") == "" ? "&__cfgfile=bcar1PrintCfgPg2_2007&__cfgfile=bcar2PrintCfgScores_2007&__template=bcarAll2_2007" : "&__cfgfile=bcar2PrintCfgPg2_2007&__graphicPage=3&__cfgfile=bcar1PrintCfgPg2_2007&__cfgfile=bcar2PrintCfgScores_2007&__template=bcarAll1_2007" %>";
            document.forms[0].target = "_blank";

            return true;
        }

        function getFormEntity(name) {
            if (name.value.length > 0) {
                return true;
            } else {
                return false;
            }
            /*
		for (var j=0; j<document.forms[0].length; j++) {
				if (document.forms[0].elements[j] != null && document.forms[0].elements[j].name ==  name ) {
					 return document.forms[0].elements[j] ;
				}
		}*/
        }

        function onSave() {

            document.forms[0].submit.value = "save";
            var ret = checkAllDates();
            ret = checkAllNumber();
            if (ret == true) {
                reset();
                ret = confirm("<fmt:message key='global.msgWannaSave'/>");
            }
            return ret;
        }

        function onExit() {
            if (confirm("<fmt:message key='global.msgNotSave'/>") == true) {
                window.close();
            }
            return (false);
        }

        function onSaveExit() {
            document.forms[0].submit.value = "exit";
            var ret = checkAllDates();
            ret = checkAllNumber();
            if (ret == true) {
                reset();
                ret = confirm("<fmt:message key='global.msgSaveExit'/>");
            }
            return ret;
        }

        function popupPage(varpage) {
            windowprops = "height=700,width=960" +
                ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=no,screenX=50,screenY=50,top=20,left=20";
            var popup = window.open(varpage, "ar1", windowprops);
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

        function isNumber(ss) {
            var s = ss.value;
            var i;
            for (i = 0; i < s.length; i++) {
                // Check that current character is number.
                var c = s.charAt(i);
                if (c == '.') {
                    continue;
                } else if (((c < "0") || (c > "9"))) {
                    alert('Invalid ' + s + ' in field ' + ss.name);
                    ss.focus();
                    return false;
                }
            }
            // All characters are numbers.
            return true;
        }

        function checkAllNumber() {
            var b = true;
            if (!isNumber(document.forms[0].pg2_ht1)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht2)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht3)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht4)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht5)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht6)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht7)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht8)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht9)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht10)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht11)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht12)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht13)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht14)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht15)) {
                b = false;
            } else if (!isNumber(document.forms[0].pg2_ht16)) {
                b = false;
            }
            return b;
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
                    return true;
                }
                var dt = dateString.split('/');
                var y = dt[2];
                var m = dt[1];
                var d = dt[0];
                //var y = dt[0];  var m = dt[1];  var d = dt[2];
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
            if (valDate(document.forms[0].c_EDD) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg1_lmp) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_labRATDate1) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_labRATDate2) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_labGGTDate) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_labGBSDate) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_labEdinDate) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_labRhIgG) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_labHBsAgDate) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_labRhIgG2) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_labDiabDate) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg1_lmp) == false) {
                b = false;
            } else if (valDate(document.forms[0].ar2_1USoundDate) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date1) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date2) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date3) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date4) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date5) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date6) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date7) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date8) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date9) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date10) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date11) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date12) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date13) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date14) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date15) == false) {
                b = false;
            } else if (valDate(document.forms[0].pg2_date16) == false) {
                b = false;
            }
            return b;
        }

        function calcWeek(source) {
            <%
String fedb = props.getProperty("c_EDD", "");
String sDate = "";
java.util.Date edbDate = UtilDateUtilities.StringToDate(fedb, "dd/MM/yyyy");
fedb = UtilDateUtilities.DateToString(edbDate, "yyyy/MM/dd");
if (!fedb.equals("") && fedb.length()==10 ) {
    FrmGraphicAR arG = new FrmGraphicAR();
    edbDate = arG.getStartDate(fedb);
    sDate = UtilDateUtilities.DateToString(edbDate, "MMMMM dd, yyyy"); //"yy,MM,dd");
%>
            var delta = 0;
            var str_date = getDateField(source.name);
            if (str_date.length < 10) return;
            //var yyyy = str_date.substring(0, str_date.indexOf("/"));
            //var mm = eval(str_date.substring(eval(str_date.indexOf("/")+1), str_date.lastIndexOf("/")) - 1);
            //var dd = str_date.substring(eval(str_date.lastIndexOf("/")+1));
            var dd = str_date.substring(0, str_date.indexOf("/"));
            var mm = eval(str_date.substring(eval(str_date.indexOf("/") + 1), str_date.lastIndexOf("/")) - 1);
            var yyyy = str_date.substring(eval(str_date.lastIndexOf("/") + 1));
            var check_date = new Date(yyyy, mm, dd);
            var start = new Date("<%=sDate%>");

            if (check_date.getUTCHours() != start.getUTCHours()) {
                if (check_date.getUTCHours() > start.getUTCHours()) {
                    delta = -1 * 60 * 60 * 1000;
                } else {
                    delta = 1 * 60 * 60 * 1000;
                }
            }

            var day = eval((check_date.getTime() - start.getTime() + delta) / (24 * 60 * 60 * 1000));
            var week = Math.floor(day / 7);
            var weekday = day % 7;
            source.value = week + "w+" + weekday;
            <% } %>
        }

        function getDateField(name) {
            var temp = ""; //pg2_gest1 - pg2_date1
            var n1 = name.substring(eval(name.indexOf("t") + 1));

            if (n1 > 17) {
                name = "pg3_date" + n1;
            } else {
                name = "pg2_date" + n1;
            }

            for (var i = 0; i < document.forms[0].elements.length; i++) {
                if (document.forms[0].elements[i].name == name) {
                    return document.forms[0].elements[i].value;
                }
            }
            return temp;
        }

        function calToday(field) {
            var calDate = new Date();
            varMonth = calDate.getMonth() + 1;
            varMonth = varMonth > 9 ? varMonth : ("0" + varMonth);
            varDate = calDate.getDate() > 9 ? calDate.getDate() : ("0" + calDate.getDate());
            field.value = varDate + '/' + (varMonth) + '/' + calDate.getFullYear();
        }

        function calcTweakScore() {

            var val = 0;
            var tempVal = parseInt(document.forms[0].ar2_tweakT.value);

            if (tempVal > 2)
                val = val + 2;

            tempVal = document.forms[0].ar2_tweakW.value;
            if (tempVal == "Yes")
                val = val + 2;

            tempVal = document.forms[0].ar2_tweakE.value;
            if (tempVal == "Yes")
                val = val + 1;

            tempVal = document.forms[0].ar2_tweakA.value;
            if (tempVal == "Yes")
                val = val + 1;

            tempVal = document.forms[0].ar2_tweakK.value;
            if (tempVal == "Yes")
                val = val + 1;

            document.forms[0].pg1_tweakScore.value = val;
        }

        function calcEPDSscore() {
            var val = 0;
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS1);
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS2);
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS3);
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS4);
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS5);
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS6);
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS7);
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS8);
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS9);
            val = val + getRadioButtonVal(document.forms[0].ar2_EPDS10);

            document.forms[0].ar2_EPDSscore.value = val;
        }

        function getRadioButtonVal(field) {
            var retVal = 0;
            for (var i = 0; i < field.length; i++) {
                if (field[i].checked) {
                    retVal = field[i].value;
                    break;
                }
            }

            return parseInt(retVal);
        }


        function setEPDSscores() {

            var val = <%= props.getProperty("ar2_EPDS1", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS1", "'null'") %>;
            if (val != 'null') {
                document.forms[0].ar2_EPDS1[val].checked = true;
            }

            val = <%= props.getProperty("ar2_EPDS2", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS2", "'null'") %>;
            if (val != 'null') {
                document.forms[0].ar2_EPDS2[val].checked = true;
            }

            val = <%= props.getProperty("ar2_EPDS3", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS3", "'null'") %>;
            if (val != 'null') {
                val = 3 - val;
                document.forms[0].ar2_EPDS3[val].checked = true;
            }

            val = <%= props.getProperty("ar2_EPDS4", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS4", "'null'") %>;
            if (val != 'null') {
                document.forms[0].ar2_EPDS4[val].checked = true;
            }

            val = <%= props.getProperty("ar2_EPDS5", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS5", "'null'") %>;
            if (val != 'null') {
                val = 3 - val;
                document.forms[0].ar2_EPDS5[val].checked = true;
            }

            val = <%= props.getProperty("ar2_EPDS6", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS6", "'null'") %>;
            if (val != 'null') {
                val = 3 - val;
                document.forms[0].ar2_EPDS6[val].checked = true;
            }

            val = <%= props.getProperty("ar2_EPDS7", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS7", "'null'") %>;
            if (val != 'null') {
                val = 3 - val;
                document.forms[0].ar2_EPDS7[val].checked = true;
            }

            val = <%= props.getProperty("ar2_EPDS8", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS8", "'null'") %>;
            if (val != 'null') {
                val = 3 - val;
                document.forms[0].ar2_EPDS8[val].checked = true;
            }

            val = <%= props.getProperty("ar2_EPDS9", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS9", "'null'") %>;
            if (val != 'null') {
                val = 3 - val;
                document.forms[0].ar2_EPDS9[val].checked = true;
            }

            val = <%= props.getProperty("ar2_EPDS10", "'null'").equals("") ? "'null'" : props.getProperty("ar2_EPDS10", "'null'") %>;
            if (val != 'null') {
                val = 3 - val;
                document.forms[0].ar2_EPDS10[val].checked = true;
            }
        }

    </script>


    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0" onload="setEPDSscores();">
    <div ID="Langdiv" class="demo">
        <table bgcolor='silver' width='100%'>
            <tr>
                <td align='right'><a href="javascript: function myFunction() {return false; }"
                                     onclick="showHideBox('Langdiv',0); return false;">X</a></td>
            </tr>
            <tr>
                <td><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('ceph', 'Langdiv'); return false;">ceph</a></td>
            </tr>
            <tr>
                <td><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('breech', 'Langdiv'); return false;">breech</a></td>
            </tr>
            <tr>
                <td><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('transv', 'Langdiv'); return false;">transv</a></td>
            </tr>
        </table>
    </div>
    <div ID="GBSdiv" class="demo3">
        <table bgcolor='silver' width='100%'>
            <tr>
                <td align='right'><a href="javascript: function myFunction() {return false; }"
                                     onclick="showHideBox('GBSdiv',0); return false;">X</a></td>
            </tr>
            <tr>
                <th><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('pos', 'GBSdiv'); return false;">pos</a></th>
            </tr>
            <tr>
                <th><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('neg', 'GBSdiv'); return false;">neg</a></th>
            </tr>
        </table>
    </div>
    <div ID="BirthPlacediv" class="demo4">
        <table bgcolor='silver' width='100%'>
            <tr>
                <td align='right'><a href="javascript: function myFunction() {return false; }"
                                     onclick="showHideBox('BirthPlacediv',0); return false;">X</a></td>
            </tr>
            <tr>
                <th><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('hospital', 'BirthPlacediv'); return false;">hospital</a></th>
            </tr>
            <tr>
                <th><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('home', 'BirthPlacediv'); return false;">home</a></th>
            </tr>
        </table>
    </div>
    <div ID="UrineDiv" class="demo">
        <table bgcolor='silver' width='100%'>
            <tr>
                <td align='right'><a href="javascript: function myFunction() {return false; }"
                                     onclick="showHideBox('UrineDiv',0); return false;">X</a></td>
            </tr>
            <tr>
                <td><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('-ve', 'UrineDiv'); return false;">-ve</a></td>
            </tr>
            <tr>
                <td><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('+', 'UrineDiv'); return false;">+</a></td>
            </tr>
            <tr>
                <td><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('++', 'UrineDiv'); return false;">++</a></td>
            </tr>
            <tr>
                <td><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('+++', 'UrineDiv'); return false;">+++</a></td>
            </tr>
            <tr>
                <td><a href="javascript: function myFunction() {return false; }"
                       onclick="insertBox('++++', 'UrineDiv'); return false;">++++</a></td>
            </tr>
        </table>
    </div>
    <div ID="Instrdiv" class="demo1">
        <center>
            <table bgcolor='#007FFF' width='99%'>
                <tr>
                    <th align='right'><a href="javascript: function myFunction() {return false; }"
                                         onclick="showHideBox('Instrdiv',0); return false;"><font
                            color="red">X</font></a></th>
                </tr>
                <tr>
                    <th><a href="javascript: function myFunction() {return false; }"
                           onclick="showHideBox('Instrdiv',0); return false;"><font color="#66FF66">Double click shaded
                        fields for drop down or calculation.</font><br>&nbsp;</a></th>
                </tr>
            </table>
        </center>
    </div>
    <div ID="BMIdiv" class="demo2">
        <table bgcolor='#007FFF' width='99%'>
            <tr>
                <th align='right'><a href="javascript: function myFunction() {return false; }"
                                     onclick="showHideBox('BMIdiv',0); return false;"><font color="red">X</font></a>
                </th>
            </tr>
            <tr>
                <td><a href="javascript: function myFunction() {return false; }"
                       onclick="showHideBox('BMIdiv',0); return false;">
                    <font color="#66FF66"><fmt:message key="form.bcar.bmiHelper"/></font><br>&nbsp;</a>
                </td>
            </tr>
        </table>
    </div>
    <div id="comment2Div" class="demo5" style="top:570px; left:735px; width:200px; height:20px;">
        <center><i><fmt:message key='form.bcar.trimesterOneNote'/></i></center>
    </div>
    <div id="comment3Div" class="demo5" style="top:592px; left:735px; width:200px; height:20px;">
        <center><i><fmt:message key='form.bcar.trimesterTwoNote'/></i></center>
    </div>
    <div id="comment6Div" class="demo5" style="top:696px; left:735px; width:200px; height:20px;">
        <center><i><fmt:message key='form.bcar.gestDiabetesScreenNote'/></i></center>
    </div>
    <div id="comment7Div" class="demo5" style="top:718px; left:735px; width:200px; height:20px;">
        <center><i><fmt:message key='form.bcar.fetalMovementNote'/></i></center>
    </div>
    <div id="comment8Div" class="demo5" style="top:740px; left:735px; width:200px; height:20px;">
        <center><i><fmt:message key='form.bcar.epdsAtWeeksNote'/></i></center>
    </div>
    <div id="comment9Div" class="demo5" style="top:762px; left:735px; width:200px; height:30px;">
        <center><i><fmt:message key='form.bcar.reassessLifestyleNote'/></i></center>
    </div>
    <div id="comment12Div" class="demo5" style="top:828px; left:735px; width:200px; height:20px;">
        <center><i><fmt:message key='form.bcar.gbsScreenNote'/></i></center>
    </div>
    <div id="comment14Div" class="demo5" style="top:872px; left:735px; width:200px; height:20px;">
        <center><i><fmt:message key='form.bcar.sendHospitalCopyNote'/></i></center>
    </div>

    <form action="${pageContext.request.contextPath}/form/formname" method="post">

        <input type="hidden" name="commonField" value="ar2_"/>
        <input type="hidden" name="c_lastVisited" value="pg2"/>
        <input type="hidden" name="demographic_no" value="<%= props.getProperty("demographic_no", "0") %>"/>
        <input type="hidden" name="formCreated" value="<%= props.getProperty("formCreated", "") %>"/>
        <input type="hidden" name="form_class" value="<%=formClass%>"/>
        <input type="hidden" name="form_link" value="<%=formLink%>"/>
        <input type="hidden" name="formId" value="<%=formId%>"/>
        <input type="hidden" name="ID" value="<%= props.getProperty("ID", "0") %>"/>
        <input type="hidden" name="provider_no" value="<e:forHtmlAttribute value='<%= StringUtils.noNull(request.getParameter("provNo")) %>' />"/>
        <input type="hidden" name="provNo" value="<e:forHtmlAttribute value='<%= StringUtils.noNull(request.getParameter("provNo")) %>' />"/>
        <input type="hidden" name="submit" value="exit"/>
        <input type="hidden" name="DOB" id="DOB" value="<%= props.getProperty("pg1_dateOfBirth", "") %>"/>
        <input type="hidden" name="height" id="height" value="<%= props.getProperty("c_ppHt", "") %>"/>
        <input type="hidden" name="pg3_date1" value="<%= props.getProperty("pg3_date1", "") %>"/>
        <input type="hidden" name="pg3_date2" value="<%= props.getProperty("pg3_date2", "") %>"/>
        <input type="hidden" name="pg3_date3" value="<%= props.getProperty("pg3_date3", "") %>"/>
        <input type="hidden" name="pg3_date4" value="<%= props.getProperty("pg3_date4", "") %>"/>
        <input type="hidden" name="pg3_date5" value="<%= props.getProperty("pg3_date5", "") %>"/>
        <input type="hidden" name="pg3_date6" value="<%= props.getProperty("pg3_date6", "") %>"/>
        <input type="hidden" name="pg3_date7" value="<%= props.getProperty("pg3_date7", "") %>"/>
        <input type="hidden" name="pg3_date8" value="<%= props.getProperty("pg3_date8", "") %>"/>
        <input type="hidden" name="pg3_date9" value="<%= props.getProperty("pg3_date9", "") %>"/>
        <input type="hidden" name="pg3_date10" value="<%= props.getProperty("pg3_date10", "") %>"/>
        <input type="hidden" name="pg3_date11" value="<%= props.getProperty("pg3_date11", "") %>"/>
        <input type="hidden" name="pg3_date12" value="<%= props.getProperty("pg3_date12", "") %>"/>
        <input type="hidden" name="pg3_date13" value="<%= props.getProperty("pg3_date13", "") %>"/>
        <input type="hidden" name="pg3_date14" value="<%= props.getProperty("pg3_date14", "") %>"/>
        <input type="hidden" name="pg3_date15" value="<%= props.getProperty("pg3_date15", "") %>"/>
        <input type="hidden" name="pg3_date16" value="<%= props.getProperty("pg3_date16", "") %>"/>
        <input type="hidden" name="pg3_ht1" value="<%= props.getProperty("pg3_ht1", "") %>"/>
        <input type="hidden" name="pg3_ht2" value="<%= props.getProperty("pg3_ht2", "") %>"/>
        <input type="hidden" name="pg3_ht3" value="<%= props.getProperty("pg3_ht3", "") %>"/>
        <input type="hidden" name="pg3_ht4" value="<%= props.getProperty("pg3_ht4", "") %>"/>
        <input type="hidden" name="pg3_ht5" value="<%= props.getProperty("pg3_ht5", "") %>"/>
        <input type="hidden" name="pg3_ht6" value="<%= props.getProperty("pg3_ht6", "") %>"/>
        <input type="hidden" name="pg3_ht7" value="<%= props.getProperty("pg3_ht7", "") %>"/>
        <input type="hidden" name="pg3_ht8" value="<%= props.getProperty("pg3_ht8", "") %>"/>
        <input type="hidden" name="pg3_ht9" value="<%= props.getProperty("pg3_ht9", "") %>"/>
        <input type="hidden" name="pg3_ht10" value="<%= props.getProperty("pg3_ht10", "") %>"/>
        <input type="hidden" name="pg3_ht11" value="<%= props.getProperty("pg3_ht11", "") %>"/>
        <input type="hidden" name="pg3_ht12" value="<%= props.getProperty("pg3_ht12", "") %>"/>
        <input type="hidden" name="pg3_ht13" value="<%= props.getProperty("pg3_ht13", "") %>"/>
        <input type="hidden" name="pg3_ht14" value="<%= props.getProperty("pg3_ht14", "") %>"/>
        <input type="hidden" name="pg3_ht15" value="<%= props.getProperty("pg3_ht15", "") %>"/>
        <input type="hidden" name="pg3_ht16" value="<%= props.getProperty("pg3_ht16", "") %>"/>

        <table class="Head" class="hidePrint">
            <tr>
                <td align="left">
                    <%
                        if (!bView) {
                    %>
                    <input type="submit" style="width:40px;" value="<fmt:message key="global.save"/>" onclick="javascript:return onSave();"/>
                    <input type="submit" value="<fmt:message key="global.saveExit"/>" onclick="javascript:return onSaveExit();"/>
                    <%
                        }
                    %>
                    <input type="submit" style="width:40px;" value="<fmt:message key="global.btnExit"/>" onclick="javascript:return onExit();"/>
                    <input type="submit" style="width:50px;" value="<fmt:message key="global.btnPrint"/>" onclick="javascript:return onPrint();"/>
                    <input type="submit" style="width:125px;" value="<fmt:message key='form.bcar.printEpdsTweak'/>"
                           onclick="javascript:return onPrintScores();"/>
                    <input type="submit" value="<fmt:message key='form.bcar.printAr1Ar2'/>" onclick="javascript:return onPrint12();"/>
                    <input type="submit" style="width:75px;" value="<fmt:message key='form.bcar.printAll'/>"
                           onclick="javascript:return onPrintAll();"/>
                </td>

                <%
                    if (!bView) {
                %>
                <td>
                    <a href="javascript: function myFunction() {return false; }"
                       title="<fmt:message key='form.bcar.doubleClickShadedFields'/>"
                       onClick="showHideBox('Instrdiv',1);return false;"><font color='red'><fmt:message key="form.bcar.instruction"/></font></a>
                </td>

                <!--<td align="right">  <b>View:</b>
        <a href="javascript: popupPage('form/formbcarpg1?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>&view=1');"> AR1</a> |
        <a href="javascript: popupPage('form/formbcarpg3?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>&view=1');">AR2 <font size=-2>(pg.2)</font></a>
        </td>
        -->
                <td align="right"><b><fmt:message key='global.btnEdit'/>:</b>
                    <a href="form/formbcar2007pg1?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>">AR1</a>
                    |
                    AR2<font size=-2>(pg.1)</font> |
                    <a href="form/formbcar2007pg3?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>">AR2
                        <font size=-2>(pg.2)</font></a> |
                    <!--a href="javascript: popupFixedPage(700,950,'<%= request.getContextPath() %>/decision/antenatal/antenatalplanner?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>');">AR Planner</a-->
                </td>
                <%
                    }
                %>
            </tr>
        </table>

        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr>
                <td width="60%">

                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <th><%=bView ? "<font color='yellow'><fmt:message key='form.bcar.viewPage'/></font>" : ""%>
                                <fmt:message key="form.bcar.recordTitlePart2"/> <font size="-2">BCPHP (HLTH) 1582-2 Rev.
                                    2007/06/05</font></th>
                        </tr>
                    </table>

                    <table width="100%" border="1" cellspacing="0" cellpadding="0">
                        <tr>
                            <td width="50%"><b>12.</b><fmt:message key='form.bcar.intendedPlaceOfBirth'/><br>
                                <input type="text" name="ar2_inBirthPlace" class="spe"
                                       onDblClick="showPGBox('BirthPlacediv',1, this, event, 'ar2_inBirthPlace', 300, 80, 26);"
                                       style="width:100%" size="40" maxlength="60"
                                       value="<%= props.getProperty("ar2_inBirthPlace", "") %>" @oscar.formDB/>
                            </td>
                            <td width="50%"><fmt:message key='form.bcar.alternatePlaceOfBirthHospital'/><br>
                                <input type="text" name="ar2_inBirthPlaceAlt" style="width:100%" size="40"
                                       maxlength="60" value="<%= props.getProperty("ar2_inBirthPlaceAlt", "") %>"
                                       @oscar.formDB/>
                            </td>
                        </tr>
                    </table>

                    <table width="100%" border="1" cellspacing="0" cellpadding="0">
                        <tr>
                            <td width="33%" valign="top">
                                <table width="100%" border="1" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td colspan="2" align="left"><b>13. <fmt:message key='form.bcar.investigationsResults'/></b></td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                                <tr>
                                                    <td><span class="small9"><fmt:message key="form.bcar.aboGroup"/></span><br>
                                                        <!--input type="text" name="ar2_labBlood" style="width:100%" size="10" maxlength="12" value="<%--= props.getProperty("ar2_labBlood", "") --%>" @oscar.formDB /-->
                                                        <select name="ar2_labBlood" style="width:100%">
                                                            <%
                                                                String[] optBG = {"", "O", "A", "B", "AB"};
                                                                for (int i = 0; i < optBG.length; i++) {
                                                            %>
                                                            <option value="<%=optBG[i]%>" <%=props.getProperty("ar2_labBlood", "").equals(optBG[i]) ? "selected" : ""%> ><%=optBG[i]%>
                                                            </option>
                                                            <%}%>
                                                        </select>
                                                    </td>
                                                    <td><span class="small9"><fmt:message key="form.bcar.rhFactor"/></span><br>
                                                        <!--input type="text" name="ar2_labRh" style="width:100%" size="10" maxlength="12" value="<%--= props.getProperty("ar2_labRh", "") --%>" @oscar.formDB /-->
                                                        <select name="ar2_labRh" style="width:100%">
                                                            <option value="" <%=props.getProperty("ar2_labRh", "").equals("") ? "selected" : ""%> ></option>
                                                            <option value="+ve" <%=props.getProperty("ar2_labRh", "").equals("+ve") ? "selected" : ""%> >
                                                                +ve
                                                            </option>
                                                            <option value="-ve" <%=props.getProperty("ar2_labRh", "").equals("-ve") ? "selected" : ""%> >
                                                                -ve
                                                            </option>
                                                        </select>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                                <tr>
                                                    <td colspan="3"><span class="small9"><fmt:message key="form.bcar.antibodyTitre"/></span></td>
                                                </tr>
                                                <tr>
                                                    <td></td>
                                                    <td width="60%"><span class="small8"><I><fmt:message key="ddmmyyyy"/></I></font></td>
                                                    <td><span class="small8"><I><fmt:message key="form.bcar.results"/></I></font></td>
                                                </tr>
                                                <tr>
                                                    <td><span class="small9">1</span></td>
                                                    <td>
                                                        <input type="text" name="ar2_labRATDate1" id="ar2_labRATDate1"
                                                               size="8" maxlength="10"
                                                               value="<%= props.getProperty("ar2_labRATDate1", "") %>"
                                                               @oscar.formDB dbType="date"/>
                                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_labRATDate1_cal">
                                                    </td>
                                                    <td>
                                                        <input type="text" name="ar2_labRATRes1" style="width:100%"
                                                               size="10" maxlength="10"
                                                               value="<%= props.getProperty("ar2_labRATRes1", "") %>"
                                                               @oscar.formDB/>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td><span class="small9">2</span></td>
                                                    <td>
                                                        <input type="text" name="ar2_labRATDate2" id="ar2_labRATDate2"
                                                               size="8" maxlength="10"
                                                               value="<%= props.getProperty("ar2_labRATDate2", "") %>"
                                                               @oscar.formDB dbType="date"/>
                                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_labRATDate2_cal">
                                                    </td>
                                                    <td>
                                                        <input type="text" name="ar2_labRATRes2" style="width:100%"
                                                               size="10" maxlength="10"
                                                               value="<%= props.getProperty("ar2_labRATRes2", "") %>"
                                                               @oscar.formDB/>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                                <tr>
                                                    <td colspan="2"><span class="small9"><fmt:message key="form.bcar.rhIgGiven"/></span>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td></td>
                                                    <td><span class="small8"><i><fmt:message key="ddmmyyyy"/></i></span></td>
                                                </tr>
                                                <tr>
                                                    <td>1</td>
                                                    <td>
                                                        <input type="text" name="ar2_labRhIgG" id="ar2_labRhIgG"
                                                               style="width:80%" size="10" maxlength="10"
                                                               value="<%= props.getProperty("ar2_labRhIgG", "") %>"
                                                               @oscar.formDB dbType="date"/>
                                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_labRhIgG_cal">
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td>2</td>
                                                    <td>
                                                        <input type="text" name="ar2_labRhIgG2" id="ar2_labRhIgG2"
                                                               style="width:80%" size="10" maxlength="10"
                                                               value="<%= props.getProperty("ar2_labRhIgG2", "") %>"
                                                               @oscar.formDB dbType="date"/>
                                                        <img src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_labRhIgG2_cal">
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                                <tr>
                                                    <td colspan="2"><span class="small9"><fmt:message key="form.bcar.hemoglobin"/></span>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td><i><span class="small8">1st</span></i><br>
                                                        <input type="text" name="ar2_labHem1st" style="width:100%"
                                                               size="10" maxlength="12"
                                                               value="<%= props.getProperty("ar2_labHem1st", "") %>"
                                                               @oscar.formDB/>
                                                    </td>
                                                    <td><i><span class="small8">3rd</span></i><br>
                                                        <input type="text" name="ar2_labHem3rd" style="width:100%"
                                                               size="10" maxlength="12"
                                                               value="<%= props.getProperty("ar2_labHem3rd", "") %>"
                                                               @oscar.formDB/>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                                <tr>
                                                    <td colspan="2">
                                                        <span class="small9"><fmt:message key="form.bcar.urineCS"/></span>
                                                        <select name="ar2_urineCS" style="width:100%">
                                                            <option value="" <%=props.getProperty("ar2_urineCS", "").equals("") ? "selected" : ""%> ></option>
                                                            <option value="+ve" <%=props.getProperty("ar2_urineCS", "").equals("+ve") ? "selected" : ""%> >
                                                                +ve
                                                            </option>
                                                            <option value="-ve" <%=props.getProperty("ar2_urineCS", "").equals("-ve") ? "selected" : ""%> >
                                                                -ve
                                                            </option>
                                                        </select>
                                                        <input type="text" name="ar2_urineCStxt"
                                                               value="<%=props.getProperty("ar2_urineCStxt","")%>"
                                                               maxlength="17"/>

                                                    </td>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>

                </td>
                <td width="33%" valign="top">

                    <table width="100%" border="1" cellspacing="0" cellpadding="0">
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td><span class="small9"><fmt:message key="form.bcar.rubellaTitre"/></span></td>
                                    </tr>
                                    <tr>
                                        <td><input type="checkbox"
                                                   name="ar2_labPPvac" <%= props.getProperty("ar2_labPPvac", "")%>
                                                   @oscar.formDB dbType="tinyint(1)"/>
                                            <span class="small8"><fmt:message key="form.bcar.ppVaccinationIndicated"/></span></td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <input type="text" name="ar2_labRubella" style="width:100%" size="10"
                                                   maxlength="12" value="<%= props.getProperty("ar2_labRubella", "") %>"
                                                   @oscar.formDB/>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td><span class="small9"><fmt:message key="form.bcar.sts"/></span></td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <!--input type="text" name="ar2_labSTS" style="width:100%" size="10" maxlength="10" value="<%--= props.getProperty("ar2_labSTS", "") --%>" @oscar.formDB /-->
                                            <select name="ar2_labSTS" style="width:100%">
                                                <option value="" <%= props.getProperty("ar2_labSTS", "").equals("") ? "selected" : ""%> ></option>
                                                <option value="NR" <%= props.getProperty("ar2_labSTS", "").equals("NR") ? "selected" : ""%> >
                                                    NR
                                                </option>
                                                <option value="R" <%= props.getProperty("ar2_labSTS", "").equals("R") ? "selected" : ""%> >
                                                    R
                                                </option>
                                            </select>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr valign="top">
                                        <td width="50%" valign="bottom">
                                            <span class="small9"><fmt:message key="form.bcar.hivTestDone"/></span>
                                        </td>
                                        <td>
                                            <input type="checkbox"
                                                   name="ar2_labHivTestN" <%= props.getProperty("ar2_labHivTestN", "")%>
                                                   @oscar.formDB dbType="tinyint(1)"/>
                                            <span class="small8"><fmt:message key="form.bcar.no"/>
                                <input type="checkbox"
                                       name="ar2_labHivTestY" <%= props.getProperty("ar2_labHivTestY", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                                   Yes</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="2">
                                            <select name="ar2_labHIV" style="width:100%" @oscar.formDB
                                                    dbType="varchar(10)">
                                                <option value="" <%= props.getProperty("ar2_labHIV", "").equals("") ? "selected" : ""%> ></option>
                                                <option value="NR" <%= props.getProperty("ar2_labHIV", "").equals("NR") ? "selected" : ""%> >
                                                    NR
                                                </option>
                                                <option value="R" <%= props.getProperty("ar2_labHIV", "").equals("R") ? "selected" : ""%> >
                                                    R
                                                </option>
                                            </select>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td valign="bottom"><span class="small9"><fmt:message key="form.bcar.hbsAgDone"/></span></td>
                                        <td>
                                            <input type="checkbox"
                                                   name="ar2_labHBsAgN" <%= props.getProperty("ar2_labHBsAgN", "")%>
                                                   @oscar.formDB dbType="tinyint(1)"/>
                                            <span class="small8"><fmt:message key="form.bcar.no"/>
                                <input type="checkbox" name="ar2_labHBsAgY" <%= props.getProperty("ar2_labHBsAgY", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                                   Yes</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><span class="small8"><i><fmt:message key="ddmmyyyy"/></i>
                            </span><img src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_labHBsAgDate_cal">
                                        </td>
                                        <td align="center">
                                            <span class="small8"><i><fmt:message key="form.bcar.results"/></i></span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <input type="text" name="ar2_labHBsAgDate" id="ar2_labHBsAgDate"
                                                   style="width:100%" size="10" maxlength="10"
                                                   value="<%= props.getProperty("ar2_labHBsAgDate", "") %>"
                                                   @oscar.formDB dbType="date"/>
                                        </td>
                                        <td>
                                            <select name="ar2_labHBsAg" style="width:100%">
                                                <option value="" <%= props.getProperty("ar2_labHBsAg", "").equals("") ? "selected" : ""%> ></option>
                                                <option value="NR" <%= props.getProperty("ar2_labHBsAg", "").equals("NR") ? "selected" : ""%> >
                                                    NR
                                                </option>
                                                <option value="R" <%= props.getProperty("ar2_labHBsAg", "").equals("R") ? "selected" : ""%> >
                                                    R
                                                </option>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="2">
                                            <input type="checkbox"
                                                   name="ar2_labHBsAgContact" <%= props.getProperty("ar2_labHBsAgContact", "")%>
                                                   @oscar.formDB dbType="tinyint(1)"/>
                                            <span class="small8"><fmt:message key="form.bcar.partnerHouseholdContact"/></span><br>
                                            <input type="checkbox"
                                                   name="ar2_labHBsAgVac" <%= props.getProperty("ar2_labHBsAgVac", "")%>
                                                   @oscar.formDB dbType="tinyint(1)"/>
                                            <span class="small8"><fmt:message key="form.bcar.nbVaccinationIndicated"/></span>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td colspan="2">
                                            <span class="small9"><fmt:message key="form.bcar.otherTests"/></span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td width="30%"><span class="small8"><fmt:message key="form.bcar.hepC"/></span></td>
                                        <td width="60%">
                                            <select name="ar2_labOtherHepC" style="width:100%">
                                                <option value="" <%= props.getProperty("ar2_labOtherHepC", "").equals("") ? "selected" : ""%> ></option>
                                                <option value="NR" <%= props.getProperty("ar2_labOtherHepC", "").equals("NR") ? "selected" : ""%> >
                                                    NR
                                                </option>
                                                <option value="R" <%= props.getProperty("ar2_labOtherHepC", "").equals("R") ? "selected" : ""%> >
                                                    R
                                                </option>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><span class="small8"><fmt:message key="form.bcar.tsh"/></span></td>
                                        <td>
                                            <input type="text" name="ar2_labOtherTSH" style="width:100%" size="10"
                                                   maxlength="255"
                                                   value="<%= props.getProperty("ar2_labOtherTSH", "") %>"
                                                   @oscar.formDB/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><span class="small8"><fmt:message key="form.bcar.varicella"/></span></td>
                                        <td>
                                            <select name="ar2_labOtherVar" style="width:100%">
                                                <option value="" <%= props.getProperty("ar2_labOtherVar", "").equals("") ? "selected" : ""%> ></option>
                                                <option value="NR" <%= props.getProperty("ar2_labOtherVar", "").equals("NR") ? "selected" : ""%> >
                                                    NR
                                                </option>
                                                <option value="R" <%= props.getProperty("ar2_labOtherVar", "").equals("R") ? "selected" : ""%> >
                                                    R
                                                </option>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="2">
                                            <input type="text" name="ar2_labOtherTest" style="width:100%" size="10"
                                                   maxlength="255"
                                                   value="<%= props.getProperty("ar2_labOtherTest", "") %>"
                                                   @oscar.formDB/>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>

                </td>
                <td valign="top">

                    <table width="100%" border="1" cellspacing="0" cellpadding="0">
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td><span class="small9"><fmt:message key="form.bcar.maternalPrenatalScreening"/></span></td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <!--input type="text" name="ar2_labAfpTS" style="width:100%" size="10" maxlength="10" value="<%--= props.getProperty("ar2_labAfpTS", "") --%>" @oscar.formDB /-->
                                            <select name="ar2_labScreen" style="width:95%;float:right;">
                                                <%
                                                    String[] optAfp = {"", "TMS", "IPS", "SIPS", "declined"};
                                                    for (int i = 0; i < optAfp.length; i++) {
                                                %>
                                                <option value="<%=optAfp[i]%>" <%=props.getProperty("ar2_labScreen", "").equals(optAfp[i]) ? "selected" : ""%> ><%=optAfp[i]%>
                                                </option>
                                                <%}%>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <input type="text" name="ar2_labScreenSpec" style="width:95%;float:right;"
                                                   size="10" maxlength="255"
                                                   value="<%= props.getProperty("ar2_labScreenSpec", "") %>"
                                                   @oscar.formDB/>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td>
                                            <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                                <tr>
                                                    <td colspan="3"><span class="small8"><fmt:message key="form.bcar.gestDiabetesScreen"/></span>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td><span class="small8"><i><fmt:message key="form.bcar.wks"/></i></span></td>
                                                    <td colspan="2"><span class="small8"><i><fmt:message key="ddmmyyyy"/> <img
                                                            src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_labDiabDate_cal"> Result</i></span>
                                                    </td>
                                                </tr>
                                                <tr>
                                                    <td>
                                                        <input type="text" name="ar2_labGWeek" style="width:100%"
                                                               size="3" maxlength="5"
                                                               value="<%= props.getProperty("ar2_labGWeek", "") %>"
                                                               @oscar.formDB/>
                                                    </td>
                                                    <td nowrap>
                                                        <input type="text" name="ar2_labDiabDate" id="ar2_labDiabDate"
                                                               size="8" maxlength="10"
                                                               value="<%= props.getProperty("ar2_labDiabDate", "") %>"
                                                               @oscar.formDB dbType="date"/>
                                                    </td>
                                                    <td>
                                                        <input type="text" name="ar2_labDiabRes" style="width:100%"
                                                               size="10" maxlength="10"
                                                               value="<%= props.getProperty("ar2_labDiabRes", "") %>"
                                                               @oscar.formDB/>
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
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td colspan="2"><span class="small9"><fmt:message key="form.bcar.threeHourGGT"/></span></td>
                                    </tr>
                                    <tr>
                                        <td><span class="small8"><I><fmt:message key="ddmmyyyy"/></I></span><img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                                              id="ar2_labGGTDate_cal">
                                        </td>
                                        <td align="center"><span class="small8"><I><fmt:message key="form.bcar.results"/></I></font></td>
                                    </tr>
                                    <tr>
                                        <td width="55%">
                                            <input type="text" name="ar2_labGGTDate" id="ar2_labGGTDate"
                                                   style="width:100%" size="8" maxlength="10"
                                                   value="<%= props.getProperty("ar2_labGGTDate", "") %>" @oscar.formDB
                                                   dbType="date"/>

                                        </td>
                                        <td width="45%">
                                            <select name="ar2_labGGTRes" style="width:100%">
                                                <option value="" <%= props.getProperty("ar2_labGGTRes", "").equals("") ? "selected" : ""%> ></option>
                                                <option value="+ve" <%= props.getProperty("ar2_labGGTRes", "").equals("+ve") ? "selected" : ""%> >
                                                    +ve
                                                </option>
                                                <option value="-ve" <%= props.getProperty("ar2_labGGTRes", "").equals("-ve") ? "selected" : ""%> >
                                                    -ve
                                                </option>
                                                <option value="one abn value" <%= props.getProperty("ar2_labGGTRes", "").equals("one abn value") ? "selected" : ""%> >
                                                    one abn value
                                                </option>
                                            </select>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td colspan="2"><span class="small9"><fmt:message key="form.bcar.gbsScreen"/></span></td>
                                    </tr>
                                    <tr>
                                        <td colspan="2">
                                            <input type="checkbox"
                                                   name="ar2_labGBSTestN" <%= props.getProperty("ar2_labGBSTestN", "")%>
                                                   @oscar.formDB dbType="tinyint(1)">
                                            <span class="small8"><fmt:message key="form.bcar.no"/>
                <input type="checkbox" name="ar2_labGBSTestY" <%= props.getProperty("ar2_labGBSTestY", "")%>
                       @oscar.formDB dbType="tinyint(1)">
                   Yes</span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td><span class="small8"><I><fmt:message key="ddmmyyyy"/></I></span><img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                                              id="ar2_labGBSDate_cal">
                                        </td>
                                        <td align="center"><span class="small8"><I><fmt:message key="form.bcar.results"/></I></font></td>
                                    </tr>
                                    <tr>
                                        <td width="55%">
                                            <input type="text" name="ar2_labGBSDate" id="ar2_labGBSDate"
                                                   style="width:100%" size="8" maxlength="10"
                                                   value="<%= props.getProperty("ar2_labGBSDate", "") %>" @oscar.formDB
                                                   dbType="date"/>

                                        </td>
                                        <td width="45%">
                                            <select name="ar2_labGBSRes" style="width:100%">
                                                <option value="" <%= props.getProperty("ar2_labGBSRes", "").equals("") ? "selected" : ""%> ></option>
                                                <option value="Pos" <%= props.getProperty("ar2_labGBSRes", "").equals("Pos") ? "selected" : ""%> >
                                                    Pos
                                                </option>
                                                <option value="Neg" <%= props.getProperty("ar2_labGBSRes", "").equals("Neg") ? "selected" : ""%> >
                                                    Neg
                                                </option>
                                            </select>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="2">
                                            <input type="checkbox"
                                                   name="ar2_labGBScopy" <%= props.getProperty("ar2_labGBScopy", "")%>
                                                   @oscar.formDB dbType="tinyint(1)">
                                            <span class="small8"><fmt:message key="form.bcar.copyToHospital"/></span>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <table width="100%" border="0" cellspacing="0" cellpadding="0">
                                    <tr>
                                        <td colspan="2">
                                            <span class="small9"><fmt:message key="form.bcar.epdsHeader28to32Weeks"/></span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td width="50%"></td>
                                        <td width="50%">
                                            <span class="small8"><i><fmt:message key="ddmmyyyy"/></i><img src="<%= request.getContextPath() %>/images/cal.gif"
                                                                                       id="ar2_labEdinDate_cal"></span>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <span class="small9"><fmt:message key="form.bcar.score"/> </span><input type="text" name="ar2_labEdinScore"
                                                                                     size="4" maxlength="5"
                                                                                     value="<%= props.getProperty("ar2_labEdinScore", "") %>"
                                                                                     @oscar.formDB/>
                                        </td>
                                        <td>
                                            <input type="text" name="ar2_labEdinDate" id="ar2_labEdinDate"
                                                   style="width:100%" size="8" maxlength="10"
                                                   value="<%= props.getProperty("ar2_labEdinDate", "") %>" @oscar.formDB
                                                   dbType="date"/>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td colspan="2">
                                            <span class="small9"><fmt:message key="form.bcar.followUp"/></span>
                                            <input type="checkbox"
                                                   name="ar2_labEdinN" <%= props.getProperty("ar2_labEdinN", "")%>
                                                   @oscar.formDB dbType="tinyint(1)">
                                            <span class="small8"><fmt:message key="form.bcar.no"/>
                        <input type="checkbox" name="ar2_labEdinY" <%= props.getProperty("ar2_labEdinY", "")%>
                               @oscar.formDB dbType="tinyint(1)">
                           Yes</span>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>

                </td>
            </tr>
            <tr>
                <td colspan="3">
                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <td><B>14.</B></td>
                            <td><span class="small9"><fmt:message key="form.bcar.age"/></span></td>
                            <td><span class="small9"><fmt:message key="form.bcar.prePregnantWeight"/></span></td>
                            <td><span class="small9"><fmt:message key="form.bcar.lmp"/></span> <span class="small8"><i><fmt:message key="ddmmyyyy"/></i></span></td>
                            <td><span class="small9"><fmt:message key="form.bcar.edd"/></span> <span class="small8"><i><fmt:message key="ddmmyyyy"/></i></span></td>
                        </tr>
                        <tr>
                            <td></td>
                            <td>
                                <input type="text" name="ar2_age" id="ar2_age" style="width:80%" class="spe"
                                       onDblClick="calcEDDAge();" size="3" maxlength="5"
                                       value="<%= props.getProperty("ar2_age", "") %>" @oscar.formDB/>
                            </td>
                            <td>
                                <input type="text" name="c_ppWt" style="width:80%;" class="spe"
                                       onDblClick="wtEnglish2Metric(this);" size="5" maxlength="5"
                                       value="<%= props.getProperty("c_ppWt", "") %>" @oscar.formDB/>
                            </td>
                            <td>
                                <input type="text" name="pg1_lmp" id="pg1_lmp" size="10" maxlength="10"
                                       value="<%= props.getProperty("pg1_lmp", "") %>" @oscar.formDB dbType="date"/>
                                <img src="<%= request.getContextPath() %>/images/cal.gif" id="pg1_lmp_cal">
                            </td>
                            <td>
                                <input type="text" name="c_EDD" id="c_EDD" size="10" maxlength="10"
                                       value="<%= props.getProperty("c_EDD", "") %>" @oscar.formDB dbType="date"/>
                                <img src="<%= request.getContextPath() %>/images/cal.gif" id="c_EDD_cal">
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>


        </td>
        <td valign="bottom">

            <table width="100%" border="1" cellspacing="0" cellpadding="0">
                <tr>
                    <td>
                        <table width="100%" border="0" cellspacing="0" cellpadding="0">
                            <tr>
                            <td align="center"
                                    colspan="2"><%=bSync ? ("<b><a href=\"javascript: function myFunction() {return false; }\" onClick='syncDemo(); return false;'><font size='+1' color='red'><fmt:message key='form.bcar.synchronize'/></font></a></b>") : "" %>
                                </td>
                            </tr>
                            <tr>
                                <td width="55%"><fmt:message key="form.bcar.surname"/><br>
                                    <input type="text" name="c_surname" style="width:100%" size="30" maxlength="30"
                                           value="<%= props.getProperty("c_surname", "") %>" @oscar.formDB/>
                                </td>
                                <td><fmt:message key="form.bcar.givenName"/><br>
                                    <input type="text" name="c_givenName" style="width:100%" size="30" maxlength="30"
                                           value="<%= props.getProperty("c_givenName", "") %>" @oscar.formDB/>
                                </td>
                            </tr>
                            <tr>
                                <td colspan="2"><fmt:message key="form.bcar.address"/><br>
                                    <input type="text" name="c_address" style="width:100%" size="50" maxlength="60"
                                           value="<%= props.getProperty("c_address", "") %>" @oscar.formDB/>
                                    <input type="text" name="c_city" style="width:50%" size="50" maxlength="60"
                                           value="<%= props.getProperty("c_city", "") %>" @oscar.formDB/>
                                    <input type="text" name="c_province" size="10" maxlength="50"
                                           value="<%= props.getProperty("c_province", "") %>" @oscar.formDB/>
                                    <input type="text" name="c_postal" size="7" maxlength="8"
                                           value="<%= props.getProperty("c_postal", "") %>" @oscar.formDB/>
                                </td>
                            </tr>
                            <tr>
                                <td valign="top"><fmt:message key="form.bcar.phoneNumber"/><br>
                                    <input type="text" name="c_phone" style="width:100%" size="60" maxlength="60"
                                           value="<%= props.getProperty("c_phone", "") %>" @oscar.formDB/>
                                </td>
                                <td><fmt:message key="form.bcar.personalHealthNumber"/><br>
                                    <input type="text" name="c_phn" style="width:100%" size="20" maxlength="20"
                                           value="<%= props.getProperty("c_phn", "") %>" @oscar.formDB/>
                                </td>
                            </tr>
                            <tr>
                                <td>
                                    Alternate <fmt:message key="form.bcar.phoneNumber"/> #1<br>
                                    <input type="text" name="c_phoneAlt1" style="width:100%" size="60" maxlength="60"
                                           value="<%= props.getProperty("c_phoneAlt1", "") %>" @oscar.formDB/>
                                </td>
                                <td></td>
                            </tr>
                            <tr>
                                <td>
                                    Alternate <fmt:message key="form.bcar.phoneNumber"/> #2<br>
                                    <input type="text" name="c_phoneAlt2" style="width:100%" size="60" maxlength="60"
                                           value="<%= props.getProperty("c_phoneAlt2", "") %>" @oscar.formDB/>
                                </td>
                                <td></td>
                            </tr>
                        </table>
                    </td>
                </tr>
                <tr>
                    <td height="95px"></td>
                </tr>
                <tr>
                    <td>
                        <table width="100%" border="0" cellspacing="0" cellpadding="0">
                            <tr>
                                <td colspan="2" align="left"><b>15. Potential or Actual Concerns:</b></td>
                            </tr>
                            <tr>
                                <td width="20%"><span class="small9"><fmt:message key="form.bcar.lifestyle"/></span></td>
                                <td>
                                    <input type="text" name="ar2_proLife" style="width:100%" size="40" maxlength="50"
                                           value="<%= props.getProperty("ar2_proLife", "") %>" @oscar.formDB/>
                            </tr>
                            <tr>
                                <td width="20%"><span class="small9"><fmt:message key="form.bcar.pregnancy"/></span></td>
                                <td>
                                    <input type="text" name="ar2_proPreg" style="width:100%" size="40" maxlength="50"
                                           value="<%= props.getProperty("ar2_proPreg", "") %>" @oscar.formDB/>
                            </tr>
                            <tr>
                                <td width="20%"><span class="small9"><fmt:message key="form.bcar.labour"/></span></td>
                                <td>
                                    <input type="text" name="ar2_proLabour" style="width:100%" size="40" maxlength="50"
                                           value="<%= props.getProperty("ar2_proLabour", "") %>" @oscar.formDB/>
                            </tr>
                            <tr>
                                <td width="20%"><span class="small9"><fmt:message key="form.bcar.postpartum"/></span></td>
                                <td>
                                    <input type="text" name="ar2_proPostPartum" style="width:100%" size="40"
                                           maxlength="50" value="<%= props.getProperty("ar2_proPostPartum", "") %>"
                                           @oscar.formDB/>
                            </tr>
                            <tr>
                                <td width="20%"><span class="small9"><fmt:message key="form.bcar.newborn"/></span></td>
                                <td>
                                    <input type="text" name="ar2_proNewBorn" style="width:100%" size="40" maxlength="50"
                                           value="<%= props.getProperty("ar2_proNewBorn", "") %>" @oscar.formDB/>
                                </td>
                            </tr>
                        </table>
                    </td>
                </tr>

            </table>


        </td>
        </tr>

        </table>


        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr>
                <td width="7%" valign="top"><b>16.</b> <fmt:message key="form.bcar.date"/><br><br><span class="small8"><i><fmt:message key="ddmmyyyy"/></i></span></td>
                <td width="7%" valign="top" align="center"><fmt:message key="form.bcar.bp"/></td>
                <td width="7%" valign="top" align="center"><fmt:message key="form.bcar.urinePg"/></td>
                <td width="5%" valign="top" align="center"><fmt:message key="form.bcar.wt"/><br><br><span class="small8"><i><fmt:message key="form.bcar.kg"/></i></span></td>
                <td width="5%" valign="top" align="center"><fmt:message key="form.bcar.bmi"/></td>
                <td width="6%" valign="top" align="center"><span class="small9"><fmt:message key='form.bcar.gestWeeks'/></span></td>
                <td width="7%" valign="top" align="center"><span class="small9"><fmt:message key="form.bcar.fundusCms"/></span></td>
                <td width="7%" valign="top" align="center"><fmt:message key="form.bcar.fhr"/></td>
                <td width="3%" valign="top" align="center"><fmt:message key="form.bcar.fm"/></td>
                <td width="7%" valign="top" align="center"><span class="small9"><fmt:message key="form.bcar.presAndPos"/></span></td>
                <td width="30%" valign="bottom" align="center"><span class="small9"><fmt:message key="form.bcar.comments"/></span></td>
                <td width="6%" align="center" valign="bottom"><span class="small8"><fmt:message key="form.bcar.returnIn"/></span></td>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date1" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date1", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp1" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp1", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine1" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine1", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG1" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG1", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt1" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt1", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi1" class="spe" onDblClick="calcBMI(this, pg2_wt1);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi1", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest1" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest1", "") %>" onDblClick="calcWeek(this)" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht1" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht1", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct1" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct1", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm1" <%= props.getProperty("pg2_fm1", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos1" class="spe" onDblClick="showBox('Langdiv',1, this, event);"
                           style="width:100%" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos1", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment1" style="width:100%" size="50" maxlength="80"
                           value="<%= props.getProperty("pg2_comment1", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn1" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn1", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date2" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date2", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp2" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp2", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine2" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine2", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG2" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG2", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt2" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt2", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi2" class="spe" onDblClick="calcBMI(this, pg2_wt2);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi2", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest2" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest2", "") %>" onDblClick="calcWeek(this)" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht2" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht2", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct2" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct2", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm2" <%= props.getProperty("pg2_fm2", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos2" class="spe" style="width:100%;"
                           onDblClick="showBox('Langdiv',1, this, event);" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_pos2", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment2" style="width:100%" size="50" maxlength="80"
                           onmouseover="showHideBox('comment2Div',1)" onmouseout="showHideBox('comment2Div',0)"
                           value="<%= props.getProperty("pg2_comment2", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn2" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn2", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date3" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date3", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp3" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp3", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine3" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine3", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG3" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG3", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt3" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt3", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi3" class="spe" onDblClick="calcBMI(this, pg2_wt3);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi3", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest3" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest3", "") %>" onDblClick="calcWeek(this)" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht3" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht3", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct3" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct3", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm3" <%= props.getProperty("pg2_fm3", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos3" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos3", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment3" style="width:100%" size="50" maxlength="80"
                           onmouseover="showHideBox('comment3Div',1)" onmouseout="showHideBox('comment3Div',0)"
                           value="<%= props.getProperty("pg2_comment3", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn3" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn3", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr valign="bottom">
                <td>
                    <input type="text" name="pg2_date4" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date4", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp4" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp4", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine4" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine4", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG4" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG4", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt4" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt4", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi4" class="spe" onDblClick="calcBMI(this, pg2_wt4);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi4", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest4" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest4", "") %>" onDblClick="calcWeek(this)" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht4" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht4", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct4" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct4", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm4" <%= props.getProperty("pg2_fm4", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos4" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos4", "") %>"
                           @oscar.formDB/>
                </td>
                <td><span class="small8"><font color="red">
        <input type="checkbox" name="pg2_toPatient20" <%= props.getProperty("pg2_toPatient20", "")%> @oscar.formDB
               dbType="tinyint(1)">
        <fmt:message key="form.bcar.copyGivenToPatient"/><br>
        <input type="checkbox" name="pg2_SentHosp20" <%= props.getProperty("pg2_SentHosp20", "")%> @oscar.formDB
               dbType="tinyint(1)">
        <fmt:message key="form.bcar.copySentToHospitalAt20Weeks"/>
        </font><br></span>
                    <input type="text" name="pg2_comment4" style="width:100%" size="50" maxlength="80"
                           value="<%= props.getProperty("pg2_comment4", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn4" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn4", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date5" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date5", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp5" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp5", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine5" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine5", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG5" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG5", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt5" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt5", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi5" class="spe" onDblClick="calcBMI(this, pg2_wt5);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi5", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest5" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest5", "") %>" onDblClick="calcWeek(this)" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht5" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht5", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct5" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct5", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm5" <%= props.getProperty("pg2_fm5", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos5" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos5", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment5" style="width:100%" size="50" maxlength="80"
                           value="<%= props.getProperty("pg2_comment5", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn5" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn5", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date6" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date6", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp6" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp6", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine6" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine6", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG6" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG6", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt6" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt6", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi6" class="spe" onDblClick="calcBMI(this, pg2_wt6);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi6", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest6" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest6", "") %>" onDblClick="calcWeek(this)" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht6" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht6", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct6" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct6", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm6" <%= props.getProperty("pg2_fm6", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos6" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos6", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment6" style="width:100%" size="50" maxlength="80"
                           onmouseover="showHideBox('comment6Div',1)" onmouseout="showHideBox('comment6Div',0)"
                           value="<%= props.getProperty("pg2_comment6", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn6" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn6", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date7" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date7", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp7" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp7", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine7" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine7", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG7" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG7", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt7" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt7", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi7" class="spe" onDblClick="calcBMI(this, pg2_wt7);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi7", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest7" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest7", "") %>" onDblClick="calcWeek(this)" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht7" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht7", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct7" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct7", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm7" <%= props.getProperty("pg2_fm7", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos7" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos7", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment7" style="width:100%" size="50" maxlength="80"
                           onmouseover="showHideBox('comment7Div',1)" onmouseout="showHideBox('comment7Div',0)"
                           value="<%= props.getProperty("pg2_comment7", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn7" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn7", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date8" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date8", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp8" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp8", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine8" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine8", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG8" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG8", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt8" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt8", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi8" class="spe" onDblClick="calcBMI(this, pg2_wt8);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi8", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest8" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest8", "") %>" onDblClick="calcWeek(this)" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht8" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht8", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct8" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct8", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm8" <%= props.getProperty("pg2_fm8", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos8" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos8", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment8" style="width:100%" size="50" maxlength="80"
                           onmouseover="showHideBox('comment8Div',1)" onmouseout="showHideBox('comment8Div',0)"
                           value="<%= props.getProperty("pg2_comment8", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn8" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn8", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date9" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date9", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp9" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp9", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine9" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine9", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG9" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG9", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt9" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt9", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi9" class="spe" onDblClick="calcBMI(this, pg2_wt9);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi9", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest9" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest9", "") %>" onDblClick="calcWeek(this)" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht9" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht9", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct9" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct9", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm9" <%= props.getProperty("pg2_fm9", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos9" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos9", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment9" style="width:100%" size="50" maxlength="80"
                           onmouseover="showHideBox('comment9Div',1)" onmouseout="showHideBox('comment9Div',0)"
                           value="<%= props.getProperty("pg2_comment9", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn9" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn9", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date10" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date10", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp10" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp10", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine10" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine10", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG10" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG10", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt10" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt10", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi10" class="spe" onDblClick="calcBMI(this, pg2_wt10);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi10", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest10" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest10", "") %>" onDblClick="calcWeek(this)"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht10" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht10", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct10" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct10", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm10" <%= props.getProperty("pg2_fm10", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos10" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos10", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment10" style="width:100%" size="50" maxlength="80"
                           value="<%= props.getProperty("pg2_comment10", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn10" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn10", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date11" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date11", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp11" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp11", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine11" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine11", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG11" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG11", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt11" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt11", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi11" class="spe" onDblClick="calcBMI(this, pg2_wt11);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi11", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest11" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest11", "") %>" onDblClick="calcWeek(this)"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht11" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht11", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct11" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct11", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm11" <%= props.getProperty("pg2_fm11", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos11" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos11", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment11" style="width:100%" size="50" maxlength="80"
                           value="<%= props.getProperty("pg2_comment11", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn11" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn11", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date12" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date12", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp12" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp12", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine12" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine12", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG12" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG12", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt12" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt12", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi12" class="spe" onDblClick="calcBMI(this, pg2_wt12);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi12", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest12" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest12", "") %>" onDblClick="calcWeek(this)"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht12" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht12", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct12" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct12", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm12" <%= props.getProperty("pg2_fm12", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos12" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos12", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment12" style="width:100%" size="50" maxlength="80"
                           onmouseover="showHideBox('comment12Div',1)" onmouseout="showHideBox('comment12Div',0)"
                           value="<%= props.getProperty("pg2_comment12", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn12" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn12", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date13" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date13", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp13" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp13", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine13" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine13", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG13" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG13", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt13" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt13", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi13" class="spe" onDblClick="calcBMI(this, pg2_wt13);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi13", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest13" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest13", "") %>" onDblClick="calcWeek(this)"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht13" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht13", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct13" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct13", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm13" <%= props.getProperty("pg2_fm13", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos13" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos13", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment13" style="width:100%" size="50" maxlength="80"
                           value="<%= props.getProperty("pg2_comment13", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn13" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn13", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date14" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date14", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp14" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp14", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine14" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine14", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG14" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG14", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt14" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt14", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi14" class="spe" onDblClick="calcBMI(this, pg2_wt14);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi14", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest14" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest14", "") %>" onDblClick="calcWeek(this)"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht14" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht14", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct14" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct14", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm14" <%= props.getProperty("pg2_fm14", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos14" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos14", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment14" style="width:100%" size="50" maxlength="80"
                           onmouseover="showHideBox('comment14Div',1)" onmouseout="showHideBox('comment14Div',0)"
                           value="<%= props.getProperty("pg2_comment14", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn14" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn14", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date15" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date15", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp15" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp15", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine15" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine15", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG15" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG15", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt15" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt15", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi15" class="spe" onDblClick="calcBMI(this, pg2_wt15);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi15", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest15" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest15", "") %>" onDblClick="calcWeek(this)"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht15" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht15", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct15" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct15", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm15" <%= props.getProperty("pg2_fm15", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos15" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos15", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment15" style="width:100%" size="50" maxlength="80"
                           value="<%= props.getProperty("pg2_comment15", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn15" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn15", "") %>" @oscar.formDB/>
                </td>
            </tr>
            <tr>
                <td>
                    <input type="text" name="pg2_date16" class="spe" style="width:100%;" size="10" maxlength="10"
                           onDblClick="calToday(this)" value="<%= props.getProperty("pg2_date16", "") %>" @oscar.formDB
                           dbType="date"/>
                </td>
                <td>
                    <input type="text" name="pg2_bp16" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_bp16", "") %>" @oscar.formDB/>
                </td>
                <td nowrap>
                    <input type="text" name="pg2_urine16" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urine', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urine16", "") %>" @oscar.formDB/>
                    <input type="text" name="pg2_urineG16" class="spe"
                           onDblClick="showPGBox('UrineDiv',1, this, event, 'pg2_urineG', 200, 481, 26);" size="2"
                           maxlength="8" value="<%= props.getProperty("pg2_urineG16", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_wt16" class="spe" onDblClick="wtEnglish2Metric(this);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_wt16", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_bmi16" class="spe" onDblClick="calcBMI(this, pg2_wt16);"
                           style="width:100%" size="5" maxlength="5" value="<%= props.getProperty("pg2_bmi16", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_gest16" class="spe" style="width:100%;" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_gest16", "") %>" onDblClick="calcWeek(this)"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_ht16" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_ht16", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_fhrAct16" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_fhrAct16", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="checkbox" name="pg2_fm16" <%= props.getProperty("pg2_fm16", "")%> @oscar.formDB
                           dbType="tinyint(1)"/>
                </td>
                <td>
                    <input type="text" name="pg2_pos16" onDblClick="showBox('Langdiv',1, this, event);" class="spe"
                           style="width:100%;" size="8" maxlength="8" value="<%= props.getProperty("pg2_pos16", "") %>"
                           @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_comment16" style="width:100%" size="50" maxlength="80"
                           value="<%= props.getProperty("pg2_comment16", "") %>" @oscar.formDB/>
                </td>
                <td>
                    <input type="text" name="pg2_retIn16" style="width:100%" size="8" maxlength="8"
                           value="<%= props.getProperty("pg2_retIn16", "") %>" @oscar.formDB/>
                </td>
            </tr>
        </table>


        <table width="100%" border="1" cellspacing="0" cellpadding="0">
            <tr>
                <td width="23%"></td>
                <td>
                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <td><b>17.</b></td>
                            <td colspan="10"><b><fmt:message key='form.bcar.secondThirdTrimesterTopicsDiscussed'/></b></td>
                        </tr>
                        <tr>
                            <td></td>
                            <td>
                                <input type="checkbox" name="ar2_topCall" <%= props.getProperty("ar2_topCall", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.callSchedule"/></span>
                            </td>
                            <td>
                                <input type="checkbox"
                                       name="ar2_topPreterm" <%= props.getProperty("ar2_topPreterm", "")%> @oscar.formDB
                                       dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.pretermLabour"/></span>
                            </td>
                            <td>
                                <input type="checkbox" name="ar2_topHosp" <%= props.getProperty("ar2_topHosp", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.hospitalAdmission"/></span>
                            </td>
                            <td>
                                <input type="checkbox" name="ar2_topDoula" <%= props.getProperty("ar2_topDoula", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.doula"/></span>
                            </td>
                            <td>
                                <input type="checkbox" name="ar2_topSleep" <%= props.getProperty("ar2_topSleep", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.backToSleep"/></span>
                            </td>
                        </tr>
                        <tr>
                            <td></td>
                            <td>
                                <input type="checkbox" name="ar2_topRisks" <%= props.getProperty("ar2_topRisks", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td rowspan="2">
                                <span class="small9"><fmt:message key="form.bcar.riskBenefitBloodProducts"/></span>
                            </td>
                            <td>
                                <input type="checkbox" name="ar2_topMove" <%= props.getProperty("ar2_topMove", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.fetalMovement"/></span>
                            </td>
                            <td>
                                <input type="checkbox" name="ar2_topPlan" <%= props.getProperty("ar2_topPlan", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.birthPlan"/></span>
                            </td>
                            <td>
                                <input type="checkbox" name="ar2_topVbac" <%= props.getProperty("ar2_topVbac", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.vbac"/></span>
                            </td>
                            <td>
                                <input type="checkbox" name="ar2_topSeats" <%= props.getProperty("ar2_topSeats", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.infantCarSeats"/></span>
                            </td>
                        </tr>
                        <tr>
                            <td></td>
                            <td></td>
                            <td>
                                <input type="checkbox" name="ar2_topFeed" <%= props.getProperty("ar2_topFeed", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.breastfeeding"/></span>
                            </td>
                            <td>
                                <input type="checkbox" name="ar2_topPain" <%= props.getProperty("ar2_topPain", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.painManagement"/></span>
                            </td>
                            <td>
                                <input type="checkbox" name="ar2_topCSec" <%= props.getProperty("ar2_topCSec", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.cesarean"/></span>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td width="23%"></td>
                <td>
                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <th colspan="6" align="left">
                                <fmt:message key="form.bcar.otherInvestigationsComments"/>
                            </th>
                        </tr>
                        <tr>
                            <td></td>
                            <td>
                                <span class="small8"><i><fmt:message key="ddmmyyyy"/></i></span>
                            </td>
                            <td></td>
                            <td>
                                <span class="small8"><i><fmt:message key="form.bcar.weeksPlusDays"/></i></span>
                            </td>
                            <td colspan="2"></td>
                        </tr>
                        <tr>
                            <td>
                                <span class="small9">1st US</span>
                            </td>
                            <td>
                                <input type="text" name="ar2_1USoundDate" id="ar2_1USoundDate" size="10" maxlength="10"
                                       value="<%= props.getProperty("ar2_1USoundDate", "") %>" @oscar.formDB
                                       dbType="date"/>
                                <img src="<%= request.getContextPath() %>/images/cal.gif" id="ar2_1USoundDate_cal">
                            </td>
                            <td>
                                <span class="small9"><fmt:message key="form.bcar.gaByUs"/></span>
                            </td>
                            <td>
                                <input type="text" name="ar2_gestAgeUs" style="width:100%" size="10" maxlength="10"
                                       value="<%= props.getProperty("ar2_gestAgeUs", "") %>" @oscar.formDB/>
                            </td>
                            <td align="right">
                                <span class="small9"><fmt:message key="form.bcar.amnioIfAboveCutoff"/></span>
                            </td>
                            <td>
                                <input type="checkbox"
                                       name="ar2_amnioCutOffY" <%= props.getProperty("ar2_amnioCutOffY", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                                <span class="small8"><fmt:message key="form.bcar.yes"/></span>
                                <input type="checkbox"
                                       name="ar2_amnioCutOffN" <%= props.getProperty("ar2_amnioCutOffN", "")%>
                                       @oscar.formDB dbType="tinyint(1)"/>
                                <span class="small8"><fmt:message key="form.bcar.no"/></span>
                            </td>
                        </tr>
                    </table>

                    <table width="100%" border="0" cellspacing="2" cellpadding="0">
                        <tr>
                            <td colspan="4" width="70%">
                                <span class="small9"><fmt:message key="form.bcar.comments"/></span>
                            </td>
                            <td width="30%"></td>
                        </tr>
                        <tr>
                            <td colspan="4">
                                <textarea name="pg2_probComment" style="width:100%" cols="40" rows="1" @oscar.formDB
                                          dbType="varchar(255)"><%= props.getProperty("pg2_probComment", "") %></textarea>
                            </td>
                            <td></td>
                        </tr>
                        <tr>
                            <td colspan="4">
                                <span class="small9"><fmt:message key="form.bcar.otherInvestigations"/></span>
                            </td>
                            <td></td>
                        </tr>
                        <tr>
                            <td colspan="4">
                                <textarea name="pg2_investigation" style="width:100%" cols="50" rows="3" @oscar.formDB
                                          dbType="text"><%= props.getProperty("pg2_investigation", "") %></textarea>
                            </td>
                            <td></td>
                        </tr>
                        <tr>
                            <td><span class="small9"><fmt:message key="form.bcar.doula"/>:</span></td>
                            <td
                            <input type="text" name="pg2_doula" style="width:100%" size="50" maxlength="100"
                                   value="<%= props.getProperty("pg2_doula", "") %>" @oscar.formDB/>
                </td>
                </td>
                <td align="right"><span class="small9">#:</span></td>
                <td><input type="text" name="pg2_doulaNo" style="width:100%" size="10" maxlength="30"
                           value="<%= props.getProperty("pg2_doulaNo", "") %>" @oscar.formDB/></td>
                </td>
                <td></td>
            </tr>
            <tr>
                <td colspan="4"><br>
                    <span class="small9"><fmt:message key="form.bcar.signature"/></span>
                </td>
            </tr>
            <tr>
                <td colspan="2">
                    <input type="text" name="pg2_signature" style="width:100%" size="50" maxlength="60"
                           value="<%= props.getProperty("pg2_signature", "") %>" @oscar.formDB/>
                </td>
                <td colspan="2">
                    <span class="small9"><fmt:message key="form.bcar.mdRm"/></span>
                </td>
            </tr>
        </table>

        </td>
        </tr>
        </table>


        <table class="Head" class="hidePrint">
            <tr>
                <td align="left">
                    <%
                        if (!bView) {
                    %>
                    <input type="submit" style="width:40px;" value="<fmt:message key="global.save"/>" onclick="javascript:return onSave();"/>
                    <input type="submit" value="<fmt:message key="global.saveExit"/>" onclick="javascript:return onSaveExit();"/>
                    <%
                        }
                    %>
                    <input type="submit" style="width:40px;" value="<fmt:message key="global.btnExit"/>" onclick="javascript:return onExit();"/>
                    <input type="submit" style="width:50px;" value="<fmt:message key="global.btnPrint"/>" onclick="javascript:return onPrint();"/>
                    <input type="submit" style="width:125px;" value="<fmt:message key='form.bcar.printEpdsTweak'/>"
                           onclick="javascript:return onPrintScores();"/>
                    <input type="submit" value="<fmt:message key='form.bcar.printAr1Ar2'/>" onclick="javascript:return onPrint12();"/>
                    <input type="submit" style="width:75px;" value="<fmt:message key='form.bcar.printAll'/>"
                           onclick="javascript:return onPrintAll();"/>
                </td>

                <%
                    if (!bView) {
                %>
                <!--td>
        <a href="javascript: popPage('form/formlabreq?demographic_no=<%=demoNo%>&formId=0&provNo=<%=provNo%>&labType=AR','LabReq');">LAB</a>
        </td-->

                <!--  <td align="right"><b>View:</b>
        <a href="javascript: popupPage('form/formbcarpg1?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>&view=1');"> AR1</a> |
        <a href="javascript: popupPage('form/formbcarpg3?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>&view=1');">AR2 <font size=-2>(pg.2)</font></a>
        </td>-->
                <td align="right"><b><fmt:message key="global.btnEdit"/>:</b>
                    <a href="form/formbcar2007pg1?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>">AR1</a>
                    |
                    AR2<font size=-2>(pg.1)</font> |
                    <a href="form/formbcar2007pg3?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>">AR2
                        <font size=-2>(pg.2)</font></a> |
                    <!--a href="javascript: popupFixedPage(700,950,'<%= request.getContextPath() %>/decision/antenatal/antenatalplanner?demographic_no=<%=demoNo%>&formId=<%=formId%>&provNo=<%=provNo%>');">AR Planner</a-->
                </td>
                <%
                    }
                %>
            </tr>
        </table>


        <table width="100%" border="1" cellspacing="0" cellpadding="5">
            <tr valign="top">
                <td>
                    <table width="100%" cellspacing="0" cellpadding="0">
                        <tr>
                            <th align="left"><fmt:message key='form.bcar.epdsTitle'/><br/><br/></th>
                        </tr>
                        <tr>
                            <th align="center"><fmt:message key='form.bcar.scoringGuide'/></th>
                        </tr>
                        <tr>
                            <th align="left"><fmt:message key='form.bcar.inThePast7Days'/></th>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q1'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS1" value="0"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.asMuchAsIAlwaysCould'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS1" value="1"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.notQuiteAsMuchNow'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS1" value="2"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.definitelyNotSoMuchNow'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS1" value="3"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.notAtAll'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q2'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS2" value="0"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.asMuchAsIEverDid'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS2" value="1"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.ratherLessThanIUsedTo'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS2" value="2"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.definitelyLessThanIUsedTo'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS2" value="3"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.hardlyAtAll'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q3'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS3" value="3"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesMostOfTheTime'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS3" value="2"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesSomeOfTheTime'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS3" value="1"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.notVeryOften'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS3" value="0"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.noNever'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q4'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS4" value="0"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.noNotAtAll'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS4" value="1"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.hardlyEver'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS4" value="2"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesSometimes'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS4" value="3"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesVeryOften'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q5'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS5" value="3"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesQuiteALot'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS5" value="2"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesSometimes'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS5" value="1"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.noNotMuch'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS5" value="0"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.noNotAtAll'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q6'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS6" value="3"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesMostOfTheTimeICouldNotCoped'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS6" value="2"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesSometimesICopedLessThanUsual'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS6" value="1"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.noMostOfTheTimeICopedQuiteWell'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS6" value="0"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.noIHaveBeenCopingAsWellAsEver'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q7'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS7" value="3"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesMostOfTheTime'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS7" value="2"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesSometimes'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS7" value="1"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.notVeryOften'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS7" value="0"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.noNotAtAll'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q8'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS8" value="3"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesMostOfTheTime'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS8" value="2"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.yesQuiteOften'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS8" value="1"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.notVeryOften'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS8" value="0"
                                                                onclick="calcEPDSscore()"/>
                        <fmt:message key='form.bcar.epds.noNotAtAll'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q9'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS9" value="3"
                                                                onclick="calcEPDSscore();"/>
                        <fmt:message key='form.bcar.epds.yesMostOfTheTime'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS9" value="2"
                                                                onclick="calcEPDSscore();"/>
                        <fmt:message key='form.bcar.epds.yesQuiteOften'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS9" value="1"
                                                                onclick="calcEPDSscore();"/>
                        <fmt:message key='form.bcar.epds.onlyOccasionally'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS9" value="0"
                                                                onclick="calcEPDSscore();"/>
                        <fmt:message key='form.bcar.epds.noNotAtAll'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td>
                        <fmt:message key='form.bcar.epds.q10'/><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS10" value="3"
                                                                onclick="calcEPDSscore();"/>
                        <fmt:message key='form.bcar.epds.yesQuiteOften'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS10" value="2"
                                                                onclick="calcEPDSscore();"/>
                        <fmt:message key='form.bcar.epds.sometimes'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS10" value="1"
                                                                onclick="calcEPDSscore();"/>
                        <fmt:message key='form.bcar.epds.hardlyEver'/>
                        </span><br/>
                                <span class="epdsAnswer"><input type="radio" name="ar2_EPDS10" value="0"
                                                                onclick="calcEPDSscore();"/>
                        <fmt:message key='form.bcar.epds.never'/>
                        </span>
                            </td>
                        </tr>
                        <tr>
                            <td class="small9">
                                <b><fmt:message key='form.bcar.epds.selfHarmWarning'/></b><br/><br/>
                            </td>
                        </tr>
                        <tr>
                            <td>
                                <fmt:message key='form.bcar.score'/>: <input type="text" name="ar2_EPDSscore" readonly="true" size="3"
                                              value="<%= props.getProperty("ar2_EPDSscore", "")%>"/>
                            </td>
                        </tr>
                        <tr>
                            <td class="small9">
                                <i><fmt:message key='form.bcar.epds.score11to13'/></i>
                            </td>
                        </tr>
                        <tr>
                            <td class="small9">
                                <i><fmt:message key='form.bcar.epds.score14plus'/></i><br/><br/>
                            </td>
                        </tr>
                        <tr>
                            <td class="small9">
                                <fmt:message key='form.bcar.epds.source'/><br/>
                                <fmt:message key='form.bcar.epds.sourceDept'/>
                            </td>
                        </tr>
                    </table>
                </td>
                <td>
                    <table width="100%" cellspacing="0" cellpadding="5">
                        <tr>
                            <th align="center"><fmt:message key='form.bcar.tweakScoringGuide'/><br/><br/></th>
                        </tr>
                    </table>
                    <table width="100%" cellspacing="0" cellpadding="0">
                        <tr>
                            <th class="tweakcell" valign="top">T</th>
                            <td class="tweakcell">
                                <b><fmt:message key='form.bcar.tolerance'/>:</b><br/>
                                <fmt:message key='form.bcar.tweak.toleranceQuestion'/><br/>
                                <fmt:message key='form.bcar.tweak.toleranceQuestionAlt'/><br/>
                                <i><fmt:message key='form.bcar.tweak.recordNumberOfDrinks'/></i>
                            </td>
                            <td class="tweakcell">
                                <input type="text" name="ar2_tweakT" size="3"
                                       value="<%= props.getProperty("ar2_tweakT", "")%>" onchange="calcTweakScore();"/>
                            </td>
                        </tr>
                        <tr>
                            <th class="tweakcell" valign="top">W</th>
                            <td class="tweakcell">
                                <b><fmt:message key='form.bcar.worry'/>:</b><br/>
                                <fmt:message key='form.bcar.tweak.worryQuestion'/>
                            </td>
                            <td class="tweakcell">
                                <select name="ar2_tweakW" style="width:100%" onchange="calcTweakScore();">
                                    <option value="" <%= props.getProperty("ar2_tweakW", "").equals("") ? "selected" : ""%> ></option>
                                    <option value="Yes" <%= props.getProperty("ar2_tweakW", "").equals("Yes") ? "selected" : ""%> >
                                        <fmt:message key='form.bcar.yes'/>
                                    </option>
                                    <option value="No" <%= props.getProperty("ar2_tweakW", "").equals("No") ? "selected" : ""%> >
                                        <fmt:message key='form.bcar.no'/>
                                    </option>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <th class="tweakcell" valign="top">E</th>
                            <td class="tweakcell">
                                <b><fmt:message key='form.bcar.eyeOpener'/>:</b><br/>
                                <fmt:message key='form.bcar.tweak.eyeOpenerQuestion'/>
                            </td>
                            <td class="tweakcell">
                                <select name="ar2_tweakE" style="width:100%" onchange="calcTweakScore();">
                                    <option value="" <%= props.getProperty("ar2_tweakE", "").equals("") ? "selected" : ""%> ></option>
                                    <option value="Yes" <%= props.getProperty("ar2_tweakE", "").equals("Yes") ? "selected" : ""%> >
                                        <fmt:message key='form.bcar.yes'/>
                                    </option>
                                    <option value="No" <%= props.getProperty("ar2_tweakE", "").equals("No") ? "selected" : ""%> >
                                        <fmt:message key='form.bcar.no'/>
                                    </option>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <th class="tweakcell" valign="top">A</th>
                            <td class="tweakcell">
                                <b><fmt:message key='form.bcar.amnesiaBlackout'/>:</b><br/>
                                <fmt:message key='form.bcar.tweak.amnesiaQuestion'/>
                            </td>
                            <td class="tweakcell">
                                <select name="ar2_tweakA" style="width:100%" onchange="calcTweakScore();">
                                    <option value="" <%= props.getProperty("ar2_tweakA", "").equals("") ? "selected" : ""%> ></option>
                                    <option value="Yes" <%= props.getProperty("ar2_tweakA", "").equals("Yes") ? "selected" : ""%> >
                                        <fmt:message key='form.bcar.yes'/>
                                    </option>
                                    <option value="No" <%= props.getProperty("ar2_tweakA", "").equals("No") ? "selected" : ""%> >
                                        <fmt:message key='form.bcar.no'/>
                                    </option>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <th class="tweakcell" valign="top">K<br/>(C)</th>
                            <td class="tweakcell">
                                <b><fmt:message key='form.bcar.cutDown'/>:</b><br/>
                                <fmt:message key='form.bcar.tweak.cutDownQuestion'/>
                            </td>
                            <td class="tweakcell">
                                <select name="ar2_tweakK" style="width:100%" onchange="calcTweakScore();">
                                    <option value="" <%= props.getProperty("ar2_tweakK", "").equals("") ? "selected" : ""%> ></option>
                                    <option value="Yes" <%= props.getProperty("ar2_tweakK", "").equals("Yes") ? "selected" : ""%> >
                                        <fmt:message key='form.bcar.yes'/>
                                    </option>
                                    <option value="No" <%= props.getProperty("ar2_tweakK", "").equals("No") ? "selected" : ""%> >
                                        <fmt:message key='form.bcar.no'/>
                                    </option>
                                </select>
                            </td>
                        </tr>
                        <tr>
                            <td></td>
                            <td colspan="2"><br/><fmt:message key='form.bcar.score'/>: <input type="text" name="pg1_tweakScore" readonly="true"
                                                               size="3"
                                                               value="<%= props.getProperty("pg1_tweakScore", "")%>"/>
                            </td>
                        </tr>
                        <tr>
                            <td></td>
                            <td colspan="2">
                                <b><i><fmt:message key='form.bcar.tweak.scoreWarning'/></i></b>
                            </td>
                        </tr>
                        <tr>
                            <td></td>
                            <td colspan="2" class="small9">
                                <br/><i><fmt:message key='form.bcar.tweak.source'/></i>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>


    </form>
    <script type="text/javascript">
        Calendar.setup({
            inputField: "ar2_labDiabDate",      // id of the input field
            ifFormat: "%d/%m/%Y",       // format of the input field
            showsTime: false,            // will display a time selector
            button: "ar2_labDiabDate_cal",   // trigger for the calendar (button ID)
            singleClick: true,           // double-click mode
            step: 1                // show all years in drop-down boxes (instead of every other year as default)
        });
        Calendar.setup({
            inputField: "c_EDD",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "c_EDD_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "pg1_lmp",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "pg1_lmp_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_labRATDate1",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_labRATDate1_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_labRATDate2",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_labRATDate2_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_labGGTDate",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_labGGTDate_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_labGBSDate",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_labGBSDate_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_labEdinDate",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_labEdinDate_cal",
            singleClick: true,
            step: 1
        });

        Calendar.setup({
            inputField: "ar2_labRhIgG",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_labRhIgG_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_labRhIgG2",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_labRhIgG2_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_labHBsAgDate",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_labHBsAgDate_cal",
            singleClick: true,
            step: 1
        });
        Calendar.setup({
            inputField: "ar2_1USoundDate",
            ifFormat: "%d/%m/%Y",
            showsTime: false,
            button: "ar2_1USoundDate_cal",
            singleClick: true,
            step: 1
        });
    </script>

    </body>
</html>
