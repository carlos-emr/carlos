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
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib prefix="csrf" uri="http://www.owasp.org/index.php/Category:OWASP_CSRFGuard_Project/Owasp.CsrfGuard.tld" %>

<%@ page import="ca.openosp.openo.util.*, ca.openosp.openo.form.*, ca.openosp.openo.form.data.*" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.form.FrmRecord" %>
<%@ page import="ca.openosp.openo.form.FrmRecordFactory" %>
<%@ include file="demographicMeasurementModal.jsp" %>

<%

    String formClass = "Rourke2020";
    int demoNo = Integer.parseInt(request.getParameter("demographic_no"));
    int formId = Integer.parseInt(request.getParameter("formId"));
    int provNo = Integer.parseInt((String) session.getAttribute("user"));
    FrmRecord rec = (new FrmRecordFactory()).factory(formClass);
    java.util.Properties props = rec.getFormRecord(LoggedInInfo.getLoggedInInfoFromSession(request), demoNo, formId);
    request.setAttribute("frmProperties", props);
    request.setAttribute("frmRecord", rec);
%>
<!DOCTYPE html>
<html>
<head>
    <link rel="stylesheet" type="text/css"
          href="<%= request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.css"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
    <script type="text/javascript"
            src="<%= request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>

    <script>
        jQuery(document).ready(function () {
            jQuery(function () {
                jQuery("#rourke2020-tabs").tabs({
                    activate: function (event, ui) {
                        let selected = ui.newPanel[0];
                        if (selected.id === "tab-all") {
                            // tab button that shows all tabs.
                            jQuery(".tab-page").show();
                            // selected.hide();
                        } else {
                            jQuery(".tab-page").hide();
                            selected.show();
                        }
                        adjustSizes();
                    }
                });
            });
        })
    </script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <script type="text/javascript"
            src="<%=request.getContextPath()%>/share/javascript/forms/forms-timed-autosave.js"></script>
    <title><%= "Rourke2020 Record for " + Encode.forHtmlAttribute(props.getProperty("c_pName", "")) + " DOB: " + props.getProperty("c_birthDate", "")%>
    </title>

    <link rel="stylesheet" type="text/css" href="rourkeStyle.css">
    <!-- calendar stylesheet -->
    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1"/>

    <!-- main calendar program -->
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>

    <!-- language for the calendar -->
    <script type="text/javascript"
            src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>

    <!-- the following script defines the Calendar.setup helper function, which makes
           adding a calendar a matter of 1 or 2 lines of code. -->
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>

    <!-- popup mouseover js code -->
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/mouseover.js"></script>

    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>

    <!--Text Area text max limit code -->
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/txtCounter/x_core.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/txtCounter/x_dom.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/txtCounter/x_event.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/txtCounter/ylib.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/txtCounter/y_TextCounter.js"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/txtCounter/y_util.js"></script>
    <script src="${pageContext.request.contextPath}/csrfguard"></script>
    <%
        String project_home = request.getContextPath().substring(1);
    %>

    <script type="text/javascript">

        function showNotes() {
            popPage('./Rourke2020_Ontario_English_Notes.pdf', 'Rourke2020_Ontario_English_Notes.pdf');
        }

        function del(names) {
            var arrElem = names.split(",");
            var elem;

            for (var idx = 0; idx < arrElem.length; ++idx) {
                elem = document.getElementById(arrElem[idx]);
                elem.checked = false;
            }

        }

        var redElements = new Array("p1_breastFeeding1wOkConcerns", "p1_breastFeeding2wOkConcerns", "p1_breastFeeding1mOkConcerns", "p2_breastFeeding2mOkConcerns", "p2_breastFeeding4mOkConcerns", "p2_breastFeeding6mOkConcerns", "p2_vitaminD2mOkConcerns", "p1_vitaminD1mOkConcerns", "p1_vitaminD2wOkConcerns", "p1_vitaminD1wOkConcerns", "p2_vitaminD6mOkConcerns", "p2_vitaminD4mOkConcerns", "p3_breastFeeding9mOkConcerns", "p3_breastFeeding12mOkConcerns", "p3_breastFeeding15mOkConcerns", "p4_breastFeeding18mOkConcerns", "p1_carSeatOkConcerns", "p1_sleepPosOkConcerns", "p1_cribSafetyOkConcerns", "p1_firearmSafetyOkConcerns", "p2_carSeatOkConcerns", "p2_sleepPosOkConcerns", "p3_carSeatOkConcerns", "p3_poisonsOkConcerns", "p3_firearmSafetyOkConcerns", "p4_carSeat18mOkConcerns", "p4_firearmSafetyOkConcerns",
            "p3_sleepCryOkConcerns", "p2_sleepCryOkConcerns", "p1_homeVisitOkConcerns", "p2_homeVisitOkConcerns", "p3_homeVisitOkConcerns", "p2_teethingOkConcerns", "p3_teethingOkConcerns", "p4_dentalCareOkConcerns", "p4_dentalCleaningOkConcerns", "p1_2ndSmokeOkConcerns", "p2_2ndSmokeOkConcerns", "p3_2ndSmokeOkConcerns", "p4_2ndSmokeOkConcerns", "p4_dayCareOkConcerns", "p1_eyes1wNo", "p1_eyes2wNo", "p1_corneal1mNo", "p1_eyes1mNo", "p2_eyes2mOkConcerns", "p2_corneal2mOkConcerns", "p2_eyes4mOkConcerns", "p2_corneal4mOkConcerns", "p2_eyes6mOkConcerns", "p2_corneal6mOkConcerns", "p3_eyes9mOkConcerns", "p3_corneal9mOkConcerns", "p3_eyes12mOkConcerns",
            "p3_corneal12mOkConcerns", "p3_eyes15mOkConcerns", "p3_corneal15mOkConcerns", "p4_eyes18mOkConcerns", "p4_corneal18mOkConcerns", "p4_eyes24mOkConcerns", "p4_corneal24mOkConcerns", "p4_eyes48mOkConcerns", "p4_corneal48mOkConcerns", "p1_pkuThyroid1wOkConcerns", "p1_hemoScreen1wOkConcerns", "p3_antiHB9mOkConcerns");

        var amberElements = new Array("p1_breastFeeding1wNo", "p1_breastFeeding1wNotDiscussed", "p1_breastFeeding2wNo", "p1_breastFeeding2wNotDiscussed", "p1_breastFeeding1mNo", "p1_breastFeeding1mNotDiscussed", "p2_breastFeeding2mNo", "p2_breastFeeding2mNotDiscussed", "p2_breastFeeding4mNo", "p2_breastFeeding4mNotDiscussed", "p2_breastFeeding6mNo", "p2_breastFeeding6mNotDiscussed", "p3_breastFeeding9mNo", "p3_breastFeeding9mNotDiscussed", "p3_breastFeeding12mNo", "p3_breastFeeding12mNotDiscussed", "p3_breastFeeding15mNo", "p3_breastFeeding15mNotDiscussed", "p4_breastFeeding18mNo", "p4_breastFeeding18mNotDiscussed", "p1_vitaminD1wNo", "p1_vitaminD1wNotDiscussed", "p1_vitaminD2wNo", "p1_vitaminD2wNotDiscussed", "p1_vitaminD1mNo", "p1_vitaminD1mNotDiscussed", "p1_vitaminD2mNo", "p1_vitaminD2mNotDiscussed", "p1_vitaminD4mNo", "p1_vitaminD4mNotDiscussed", "p1_vitaminD6mNo", "p1_vitaminD6mNotDiscussed",
            "p1_carSeatNotDiscussed", "p4_foodguide24mNo", "p4_foodguide48mNo", "p1_sleepPosNotDiscussed", "p1_cribSafetyNotDiscussed", "p1_firearmSafetyNotDiscussed", "p2_carSeatNotDiscussed", "p2_sleepPosNotDiscussed", "p3_carSeatNotDiscussed", "p3_poisonsNotDiscussed", "p3_firearmSafetyNotDiscussed", "p4_carSeat18mNotDiscussed", "p4_firearmSafetyNotDiscussed", "p3_sleepCryNotDiscussed", "p2_sleepCryNotDiscussed", "p1_homeVisitNotDiscussed", "p2_homeVisitNotDiscussed", "p3_homeVisitNotDiscussed", "p2_teethingNotDiscussed", "p3_teethingNotDiscussed", "p4_dentalCareNotDiscussed", "p4_dentalCleaningNotDiscussed",
            "p1_2ndSmokeNotDiscussed", "p2_2ndSmokeNotDiscussed", "p3_2ndSmokeNotDiscussed", "p4_2ndSmokeNotDiscussed", "p4_dayCareNotDiscussed", "p1_eyes1wNotDiscussed", "p1_eyes2wNotDiscussed", "p1_corneal1mNotDiscussed", "p1_eyes1mNotDiscussed", "p2_eyes2mNotDiscussed", "p2_corneal2mNotDiscussed", "p2_eyes4mNotDiscussed", "p2_corneal4mNotDiscussed", "p2_eyes6mNotDiscussed", "p2_corneal6mNotDiscussed", "p3_eyes9mNotDiscussed", "p3_corneal9mNotDiscussed", "p3_eyes12mNotDiscussed", "p3_corneal12mNotDiscussed", "p3_eyes15mNotDiscussed", "p3_corneal15mNotDiscussed", "p4_eyes18mNotDiscussed", "p4_corneal18mNotDiscussed",
            "p4_eyes24mNotDiscussed", "p4_corneal24mNotDiscussed", "p4_eyes48mNotDiscussed", "p4_corneal48mNotDiscussed", "p1_pkuThyroid1wNotDiscussed", "p1_hemoScreen1wNotDiscussed", "p1_hepatitisVaccine1wNo", "p3_antiHB9mNotDiscussed", "p1_hepatitisVaccine1mNo", "p2_hepatitisVaccine6mNo");

        function setColour(element) {
            var red = "#C11B17";
            var amber = "#FDD017";
            var white = "#FFFFFF";
            var black = "#000000";
            var linkColour = "#486EBD";
            var colour = "";
            var txtElement = element;
            var idx;
            var colourIsSet = false;
            var pattern1 = /No$/;
            var pattern2 = /Concerns$/;
            //if( element.id == "p1_sucks2wNo") {
            for (idx = 0; idx < redElements.length; ++idx) {
                if (element.id == redElements[idx]) {
                    colour = red;
                    colourIsSet = true;
                    break;
                }
            }

            if (!colourIsSet) {
                for (idx = 0; idx < amberElements.length; ++idx) {
                    if (element.id == amberElements[idx]) {
                        colour = amber;
                        colourIsSet = true;
                        break;
                    }
                }
            }

            if (!colourIsSet && (element.id.match(pattern1) || element.id.match(pattern2) != null)) {
                colour = amber;
            }

            var cache = element;
            element = element.parentNode;
            var child;
            while ((element = element.nextSibling) != null) {

                colourIsSet = false;
                if (element.nodeName == "TD") {
                    cache = element;

                    for (idx = 0; idx < element.childNodes.length; ++idx) {
                        child = element.childNodes[idx];

                        if (child.nodeName == "#text" && child.nodeValue.replace(/^\s+|\s+$/, '') != "") {
                            element.style.backgroundColor = colour;
                            colourIsSet = true;
                            break;
                        } else if (child.nodeName == "A" || child.nodeName == "B" || child.nodeName == "I") {
                            element.style.backgroundColor = colour;
                            colourIsSet = true;

                            if (colour == red) {
                                $(child).setStyle({color: white})
                                var anchorCheck = child.childNodes[0];
                                if (anchorCheck.nodeName == "A") {
                                    $(anchorCheck).setStyle({color: white});
                                }
                            } else {
                                $(child).setStyle({color: black})
                                var anchorCheck = child.childNodes[0];
                                if (anchorCheck.nodeName == "A") {
                                    $(anchorCheck).setStyle({color: linkColour});
                                }
                            }
                            break;
                        }
                    } //while there are children to check

                    if (colourIsSet) {
                        break;
                    }
                }
            }

            //if we are on last element then above loop will not execute
            //check for that here
            if (element == null && !colourIsSet) {
                cache = cache.nextSibling;  //most likely element was input so check for text next
                if (cache != null && cache.nodeName == "#text" && cache.nodeValue.replace(/^\s+|\s+$/, '') != "") {
                    cache.parentNode.style.backgroundColor = colour;
                }
            }
            //}
        }


        function onCheck(a, groupName) {
            if (a.checked) {
                var s = groupName;
                unCheck(s);
                a.checked = true;
                setColour(a);
            }
        }

        function unCheck(s) {

            for (var frm = 0; frm < document.forms.length; ++frm) {
                for (var i = 0; i < document.forms[frm].elements.length; i++) {

                    if (document.forms[frm].elements[i].id.indexOf(s) != -1 && document.forms[frm].elements[i].id.indexOf(s) < 1) {
                        document.forms[frm].elements[i].checked = false;

                    }
                }
            }
        }

        function reset() {
            document.forms[0].target = "";
            document.forms[0].action = "/<%=project_home%>/form/formname.do";
        }

        function onGraph(url, name) {
            if (checkMeasures()) {
                popPage(url, name);
            } else {
                alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.frmError"/>');
            }
        }

        function onPrint() {
            return onPrintAll();
        }

        function onPrintAll() {
            document.forms["frmP1"].action = "<%= request.getContextPath() %>/form/formname.do?__title=Rourke+Baby+Report&__cfgfile=Rourke2020printCfgPg1&__cfgfile=Rourke2020printCfgPg2&__cfgfile=Rourke2020printCfgPg3&__cfgfile=Rourke2020printCfgPg4&__template=Rourke2020";
            document.forms["frmP1"].action += "&submit=printAllJasperReport";
            document.forms["frmP1"].target = "_blank";

            return true;
        }

        /*We have to check that measurements are numeric and an observation date has
         *been entered for that measurment
         */
        function checkMeasures() {
            var measurements = new Array(12);
            measurements[0] = ["p1_ht1w", "p1_wt1w", "p1_hc1w"];
            measurements[1] = ["p1_ht2w", "p1_wt2w", "p1_hc2w"];
            measurements[2] = ["p1_ht1m", "p1_wt1m", "p1_hc1m"];
            measurements[3] = ["p2_ht2m", "p2_wt2m", "p2_hc2m"];
            measurements[4] = ["p2_ht4m", "p2_wt4m", "p2_hc4m"];
            measurements[5] = ["p2_ht6m", "p2_wt6m", "p2_hc6m"];
            measurements[6] = ["p3_ht9m", "p3_wt9m", "p3_hc9m"];
            measurements[7] = ["p3_ht12m", "p3_wt12m", "p3_hc12m"];
            measurements[8] = ["p3_ht15m", "p3_wt15m", "p3_hc15m"];
            measurements[9] = ["p4_ht18m", "p4_wt18m", "p4_hc18m"];
            measurements[10] = ["p4_ht24m", "p4_wt24m", "p4_hc24m", "p4_bmi24m"];
            measurements[11] = ["p4_ht48m", "p4_wt48m", "p4_bmi48m"];
            let dates = ["p1_date1w", "p1_date2w", "p1_date1m", "p2_date2m", "p2_date4m", "p2_date6m", "p3_date9m", "p3_date12m", "p3_date15m", "p4_date18m", "p4_date24m", "p4_date48m"];
            for (let dateIdx = 0; dateIdx < dates.length; ++dateIdx) {
                let date = dates[dateIdx];
                for (let elemIdx = 0; elemIdx < measurements[dateIdx].length; ++elemIdx) {
                    let elem = document.forms["frmP1"].elements[measurements[dateIdx][elemIdx]];

                    if ($(elem).value.length > 0 && (isNaN($(elem).value) || $(date).value.length == 0)) {
                        return false;
                    }
                }

            }

            return true;
        }

        function popPage(pageUrl, pageName) {
            let windowProps = "height=700,width=960,location=yes,scrollbars=yes,menubar=yes,toolbars=yes,resizable=yes,status=yes,screenX=50,screenY=50,top=20,left=20";
            let popup = window.open(pageUrl, pageName, windowProps);
            popup.focus();
        }


        function wtEnglish2Metric(obj) {
            const decimalNumberRegexPattern = /^\d+(\.\d)?$/; //using regex to test for a decimal number is better than something like !isNaN(parseFloat(inputString)) because parseFloat will accept scientific notation (E.g. 1e10 would be considered a valid number)
            if (obj.value == "") {
                alert("You have attempted to convert the value in this field from pounds to kilograms.\r\n\r\nThere is no value though. Please enter a value in pounds (e.g. 7.9) and try again.");
            } else if (!decimalNumberRegexPattern.test(obj.value)) {
                alert("You have attempted to convert the value in this field from pounds to kilograms.\r\n\r\nThe value does not appear to be numeric though. Please enter a value in pounds (e.g. 7.9) and try again.");
            } else {
                let weight = obj.value;
                let weightM = Math.round(weight * 10 * 0.4536) / 10;
                if (confirm("You have triggered an tool that converts values from pounds to kilograms automatically.\r\n\r\nAre you sure you want to replace " + weight + " with " + weightM + "?")) {
                    obj.value = weightM;
                }
            }
        }

        function htEnglish2Metric(obj) {
            const imperialRegexPattern = /^\d+'(\d+")?$/;
            if (obj.value == "") {
                alert("You have attempted to convert the value in this field from imperial to metric.\r\n\r\nThere is no value though. Please enter a value in feet (e.g. 5'8\") and try again.");
            } else if (!imperialRegexPattern.test(obj.value)) {
                alert("You have attempted to convert the value in this field from imperial to metric.\r\n\r\nThe value is not in exactly this format though #'#\" (e.g. 5'8\"). Please try again.");
            } else {
                let height = obj.value;
                feet = height.substring(0, height.indexOf("'"));
                inch = height.substring(height.indexOf("'"));
                if (inch.length == 1) {
                    inch = 0;
                } else {
                    inch = inch.charAt(inch.length - 1) == '"' ? inch.substring(0, inch.length - 1) : inch;
                    inch = inch.substring(1);
                }
                let heightM = Math.round((feet * 30.48 + inch * 2.54) * 10) / 10;
                if (confirm("You have triggered an tool that converts values from imperial to metric automatically.\r\n\r\nAre you sure you want to replace " + height + " with " + heightM + "?")) {
                    obj.value = heightM;
                }
            }
        }

        //Remove date from textbox
        function resetDate(textbox) {
            if (textbox.value.length > 0)
                textbox.value = "";
            else {
                var date = new Date();
                var mth = date.getMonth() + 1;
                textbox.value = date.getDate() + "/" + mth + "/" + date.getFullYear();
            }
        }

        function resetDateUsingID(id) {
            const inputField = document.getElementById(id);
            if (inputField.value.length > 0) {
                inputField.value = "";
            }
        }

        var ageUnits = new Array("days", "weeks", "months", "years");
        var curUnit = 0;

        function calcDefaultCurUnit() {
            // In Days if Baby is under 30 days old
            // In Months if Baby is under 24 months old
            // In Years if Toddler is above 24 months old
            const birthDateStr = $("c_birthDate").value;

            if (birthDateStr != "") {
                const birthDateArr = birthDateStr.split("/");
                const birthDate = new Date(birthDateArr[2], birthDateArr[1] - 1, birthDateArr[0], 0, 0, 0, 0);
                const age = calcAgeInMths(birthDate);
                if (age < 1) {
                    curUnit = 0;
                } else if (age >= 1 && age < 24) {
                    curUnit = 2;
                } else {
                    curUnit = 3;
                }
            }
        }

        function calcAge() {
            var ageElements = new Array("currentAge", "currentAge2", "currentAge3", "currentAge4");
            var age;

            if (curUnit == ageUnits.length) {
                curUnit = 0;
            }

            var birthDateStr = $("c_birthDate").value;

            if (birthDateStr != "") {
                var birthDateArr = birthDateStr.split("/");
                var birthDate = new Date(birthDateArr[2], birthDateArr[1] - 1, birthDateArr[0], 0, 0, 0, 0);

                if (ageUnits[curUnit] == "days") {
                    age = calcAgeInDays(birthDate);
                } else if (ageUnits[curUnit] == "weeks") {
                    age = calcAgeInWeeks(birthDate);
                } else if (ageUnits[curUnit] == "months") {
                    age = calcAgeInMths(birthDate);
                } else if (ageUnits[curUnit] == "years") {
                    age = calcAgeInYears(birthDate);
                }

                age = age + " " + ageUnits[curUnit];
                ++curUnit;
            } else {
                age = "N/A";
            }

            for (var idx = 0; idx < ageElements.length; ++idx) {
                $(ageElements[idx]).value = age;
            }
        }

        function calcAgeInDays(birthDate) {
            var now = new Date();
            var numDays = Math.floor((now.getTime() - birthDate.getTime()) / 1000 / 60 / 60 / 24);

            return numDays;

        }

        function calcAgeInWeeks(birthDate) {
            var now = new Date();
            var numDays = Math.floor((now.getTime() - birthDate.getTime()) / 1000 / 60 / 60 / 24 / 7);

            return numDays;

        }

        function calcAgeInMths(birthDate) {
            var mths = new Array(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);
            var numDays = calcAgeInDays(birthDate);
            var numMths = 0;
            var mth = 0;

            while (numDays > 0) {
                numDays -= mths[mth];

                if (numDays > 0) {
                    ++numMths;
                }

                if (mth == 11) {
                    mth = 0;
                } else {
                    ++mth;
                }

            }

            return numMths;
        }

        function calcAgeInYears(birthDate) {
            var now = new Date();
            var numYrs = now.getFullYear() - birthDate.getFullYear();

            return numYrs;
        }

        //set as default "not discussed" for all radio buttons if no value is set for a group
        //e.g. if no value is found for p3_formulaFeeding9m then set p3_formulaFeeding9mNotDiscussed to checked
        function setDefaultValues() {
            var currentElem;
            var currentgroup;
            var notDiscussed;
            var isChecked = false;
            var pattern = /\'.+\'/;
            var clickHandler;

            for (var frms = 0; frms < document.forms.length; ++frms) {
                for (var idx = 0; idx < document.forms[frms].elements.length; ++idx) {
                    if (document.forms[frms].elements[idx].type == "radio") {
                        clickHandler = document.forms[frms].elements[idx].getAttributeNode('onclick');
                        if (clickHandler != null) {
                            //We need to capture the group name which is present in onclick call to onCheck(this,group)
                            currentElem = new String(pattern.exec(clickHandler.value));
                            currentElem = currentElem.substring(1, currentElem.length - 1);
                            if (idx == 0) {
                                currentgroup = currentElem;
                            }

                            if (currentgroup != currentElem) {
                                if (!isChecked) {
                                    notDiscussed = document.getElementById(currentgroup + "NotDiscussed");
                                    if (notDiscussed != null) {
                                        notDiscussed.checked = true;
                                        setColour(notDiscussed);
                                    }
                                }
                                currentgroup = currentElem;
                                isChecked = false;
                            }

                            if (document.forms[frms].elements[idx].checked) {
                                isChecked = true;
                                setColour(document.forms[frms].elements[idx]);
                            }
                        }
                    }
                }

                //capture last element if necessary
                if (!isChecked) {
                    notDiscussed = document.getElementById(currentgroup + "NotDiscussed");
                    if (notDiscussed != null) {
                        notDiscussed.checked = true;
                    }
                }

            }
        }

        function initTextAreas() {

            //set text box counters
            var txtBirthRem = new ylib.widget.TextCounter("c_birthRemarks", 84, 3);
            var txtriskFact = new ylib.widget.TextCounter("c_riskFactors", 90, 3);
            var txtConcern1w = new ylib.widget.TextCounter("p1_pConcern1w", 200, 5);
            var txtConcern2w = new ylib.widget.TextCounter("p1_pConcern2w", 220, 5);
            var txtConcern1m = new ylib.widget.TextCounter("p1_pConcern1m", 230, 5);
            var txtDevelopment1w = new ylib.widget.TextCounter("p1_development1w", 360, 9);
            var txtDevelopment2w = new ylib.widget.TextCounter("p1_development2w", 396, 9);
            var txtDevelopment1m = new ylib.widget.TextCounter("p1_development1m", 230, 5);
            var txtProblems1w = new ylib.widget.TextCounter("p1_problems1w", 120, 3);
            var txtProblems2w = new ylib.widget.TextCounter("p1_problems2w", 220, 5);
            var txtProblems1m = new ylib.widget.TextCounter("p1_problems1m", 230, 5);

            var txttwo = new ylib.widget.TextCounter("p2_pConcern2m", 195, 5);
            var txtthree = new ylib.widget.TextCounter("p2_pConcern4m", 220, 5);
            var txtfour = new ylib.widget.TextCounter("p2_pConcern6m", 235, 5);
            var txtfive = new ylib.widget.TextCounter("p2_nutrition2m", 234, 6);
            var txtsix = new ylib.widget.TextCounter("p2_nutrition4m", 264, 6);
            var txtseven = new ylib.widget.TextCounter("p2_development2m", 123, 3);
            var txteight = new ylib.widget.TextCounter("p2_development4m", 205, 5);
            var txtnine = new ylib.widget.TextCounter("p2_development6m", 140, 3);
            var txtten = new ylib.widget.TextCounter("p2_problems2m", 246, 6);
            var txteleven = new ylib.widget.TextCounter("p2_problems4m", 246, 6);
            var txttwelve = new ylib.widget.TextCounter("p2_problems6m", 230, 5);

            var txtConcern9m = new ylib.widget.TextCounter("p3_pConcern9m", 195, 5);
            var txtConcern12m = new ylib.widget.TextCounter("p3_pConcern12m", 245, 5);
            var txtConcern15m = new ylib.widget.TextCounter("p3_pConcern15m", 205, 5);
            var txtNutrition12m = new ylib.widget.TextCounter("p3_nutrition12m", 245, 5);
            var txtNutrition15m = new ylib.widget.TextCounter("p3_nutrition15m", 245, 6);
            var txtDevelopment9m = new ylib.widget.TextCounter("p3_development9m", 90, 2);
            var txtDevelopment12m = new ylib.widget.TextCounter("p3_development12m", 170, 4);
            var txtDevelopment15m = new ylib.widget.TextCounter("p3_development15m", 208, 4);
            var txtProblems9m = new ylib.widget.TextCounter("p3_problems9m", 117, 3);
            var txtProblems12m = new ylib.widget.TextCounter("p3_problems12m", 208, 4);
            var txtProblems15m = new ylib.widget.TextCounter("p3_problems15m", 190, 5);

            var txtConcern18m = new ylib.widget.TextCounter("p4_pConcern18m", 180, 4);
            var txtConcern24m = new ylib.widget.TextCounter("p4_pConcern24m", 180, 4);
            var txtConcern48m = new ylib.widget.TextCounter("p4_pConcern48m", 180, 4);
            var txtProblems18m = new ylib.widget.TextCounter("p4_problems18m", 180, 4);
            var txtProblems24m = new ylib.widget.TextCounter("p4_problems24m", 180, 4);
            var txtProblems48m = new ylib.widget.TextCounter("p4_problems48m", 180, 4);
        }

        function adjustSizes() {

            var divs = new Array("patientInfo", "growthA", "growthB", "nutrition", "education", "development", "physicalExam", "problemsPlans", "immunization");
            var curDiv;
            var subDiv;
            var maxHeight = 0;
            var tmpHeight;
            var page;
            var divIdx;

            //calculate max height of related divs across all 4 pages
            for (divIdx = 0; divIdx < divs.length; ++divIdx) {
                maxHeight = 0;

                for (page = 1; page < 5; ++page) {
                    curDiv = divs[divIdx] + "p" + page;
                    tmpHeight = $(curDiv).getHeight();

                    if (tmpHeight > maxHeight) {
                        maxHeight = tmpHeight;
                    }

                } //end for each page

                for (page = 1; page < 5; ++page) {
                    curDiv = divs[divIdx] + "p" + page;
                    $(curDiv).style.height = maxHeight;
                }
            }

            //Firefox expands tables fine, goolge chrome does not; need to set height of tables
            //wish i had time to dispense with the tables and format via divs alone
            var tables = new Array("ntp1", "ntp2", "ntp3", "ntp4");
            var curTable;

            //set nutrition tables height
            curDiv = "nutritionp1";
            for (var idx = 0; idx < tables.length; ++idx) {
                for (var tableIdx = 1; tableIdx < 4; ++tableIdx) {
                    curTable = tables[idx] + tableIdx;
                    $(curTable).style.height = $(curDiv).getHeight();
                }
            }

            //set education tables height
            let educationP1ElemHeight = $("educationp1").getHeight();
            $("edt1").style.height = educationP1ElemHeight;
            $("edt2").style.height = educationP1ElemHeight;
            $("edt3").style.height = educationP1ElemHeight;
            $("edt41").style.height = educationP1ElemHeight;
            $("edt42").style.height = educationP1ElemHeight;

            //set development tables height
            let developmentP1ElemHeight = $("developmentp1").getHeight();
            $("dt11").style.height = developmentP1ElemHeight;
            $("dt12").style.height = developmentP1ElemHeight;
            $("dt13").style.height = developmentP1ElemHeight;
            $("dt21").style.height = developmentP1ElemHeight;
            $("dt22").style.height = developmentP1ElemHeight;
            $("dt23").style.height = developmentP1ElemHeight;
            $("dt31").style.height = developmentP1ElemHeight;
            $("dt32").style.height = developmentP1ElemHeight;
            $("dt33").style.height = developmentP1ElemHeight;
            $("dt41").style.height = developmentP1ElemHeight;

            //set physical exam tables height
            tables = new Array("pt1", "pt2", "pt3", "pt4");
            curDiv = "physicalExamp1";
            for (var idx = 0; idx < tables.length; ++idx) {
                for (var tableIdx = 1; tableIdx < 4; ++tableIdx) {
                    curTable = tables[idx] + tableIdx;
                    $(curTable).style.height = $(curDiv).getHeight();
                }
            }

            //set problems and plan tables height
            let problemsPlansP1ElemHeight = $("problemsPlansp1").getHeight();
            $("prbt23").style.height = problemsPlansP1ElemHeight;
            $("prbt31").style.height = problemsPlansP1ElemHeight;
            $("prbt32").style.height = problemsPlansP1ElemHeight;

            //set immunization tables height
            let immunizationP1ElemHeight = $("immunizationp1").getHeight();
            $("immt11").style.height = immunizationP1ElemHeight;
            $("immt12").style.height = immunizationP1ElemHeight;
            $("immt13").style.height = immunizationP1ElemHeight;
            $("immt23").style.height = $("immunizationp2").getHeight();
            $("immt31").style.height = $("immunizationp3").getHeight();
            $("immt41").style.height = $("immunizationp4").getHeight();
        }

        function init() {

            //Set default choices where no saved data is present
            //Change colour of elements red and amber depending on
            //level of evidence
            setDefaultValues();

            //set age
            calcDefaultCurUnit();
            calcAge();

            //adjust height of divs/tables so they align properly
            adjustSizes();

            var updated = "<%=props.getProperty("updated","")%>";
            if (updated == "true") {
                alert("Synchronizing demographic information\nRemember to save changes");
            }
        }

    </script>
    <style>
        .panel-page {
            display: flex;
            flex-direction: row;
            justify-content: flex-start;
            width: 100% !important;
            align-items: stretch;
        }

        .ui-widget-content {
            border: none !important;
        }

        /* For checkmark image underline */
        .checkmark-img-wrapper {
            position: relative;
            cursor: pointer;
        }

        .checkmark-img-wrapper:after {
            content: '';
            position: absolute;
            bottom: 2px;
            left: 6px;
            width: 10px;
            height: 1px;
            background-color: black;
        }
    </style>
</head>
<body onload="init()">
<form id="frmP1" name="frmP1" action="${pageContext.request.contextPath}/form/formname.do" method="post">
    <div id="rourke2020-tabs">
        <ul id="tab-list">
            <li><a href="#tab-cp1">Page I</a></li>
            <li><a href="#tab-cp2">Page II</a></li>
            <li><a href="#tab-cp3">Page III</a></li>
            <li><a href="#tab-cp4">Page IV</a></li>
            <li><a href="#tab-all">All</a></li>
        </ul>
        <div class="panel-page" id="panel-page">
            <div class="tab-page" id="tab-cp1">
                <jsp:include page="formRourke2020p1.jsp"/>
            </div>
            <div class="tab-page" id="tab-cp2">
                <jsp:include page="formRourke2020p2.jsp"/>
            </div>
            <div class="tab-page" id="tab-cp3">
                <jsp:include page="formRourke2020p3.jsp"/>
            </div>
            <div class="tab-page" id="tab-cp4">
                <jsp:include page="formRourke2020p4.jsp"/>
            </div>
            <div class="tab-page" id="tab-all"></div>
        </div>
    </div>
</form>
<form id="frmPopUp" method="get" action=""></form>
<form id="graph" method="post" action=""></form>
<script type="text/javascript">
    let csrfToken = {name: '<csrf:tokenname/>', value: '<csrf:tokenvalue/>'};
    let validationFailedMessage = 'Unable to autosave: Measurements must be numeric and observation dates for each measurement are required.';
    let formsTimedAutosaver = new FormsTimedAutosave('frmP1', 10000, '<%=request.getContextPath()%>/form/formname.do', csrfToken, checkMeasures, validationFailedMessage, 'saveMessageDiv', true);

    function onSave() {
        if (checkMeasures()) {
            reset();
            $("frmP1").action += "?submit=save";
            if (confirm("Are you sure you want to save this form?")) {
                formsTimedAutosaver.setChangedFalse();
                document.forms["frmP1"].submit();
            }
        } else {
            alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.frmError"/>');
        }
        return false;
    }

    function onSaveExit() {
        if (checkMeasures()) {
            reset();
            $("frmP1").action += "?submit=exit";
            if (confirm("Are you sure you wish to save and close this window?")) {
                formsTimedAutosaver.setChangedFalse();
                document.forms["frmP1"].submit();
            }
        } else {
            alert('<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.formRourke2006.frmError"/>');
        }
        return false;
    }

    let limitRowsTextAreas = document.getElementsByClassName('limit-rows');
    Array.from(limitRowsTextAreas).forEach(function (element) {
        element.addEventListener('keydown', onNoNewLineTextareaKeyDown);
    });


    function onNoNewLineTextareaKeyDown(event) {
        if (event.keyCode === 13 && !event.shiftKey) { // disable hitting enter on textarea
            event.preventDefault();
        }
    }

    function selectAllOkRadioButtons(column) {
        const columnIndex = jQuery(column).index() + 1;
        let columnCellCount = 0;
        for (let i = 0; i < columnIndex; i++) {
            const prevColspan = parseInt(jQuery(column).prevAll().eq(i).attr('colspan')) || 1;
            columnCellCount += prevColspan;
        }

        jQuery(column).closest('table').find('tbody tr').each(function () {
            let colCount = 0;
            jQuery(this).find('td').each(function () {
                const colspan = jQuery(this).attr('colspan');
                if (colspan === undefined || colspan === false) {
                    colCount += 1;
                } else {
                    colCount += parseInt(colspan);
                }
                if (colCount >= columnCellCount) {
                    jQuery(this).find('input[type="radio"][id$="Ok"]').each(function () {
                        jQuery(this).prop('checked', true);
                        if (typeof jQuery(this).attr('onclick') !== 'undefined') {
                            jQuery(this).click();
                        }
                    });
                    return false; // Break out of the loop
                }
            });
        });
    }
</script>
</body>
</html>
