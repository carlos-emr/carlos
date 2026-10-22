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
<%--
  Purpose: Render the clinical measurement-entry popup for an authorized demographic.
  Features: Displays measurement groups, prior values, validation feedback, and calculator/history links.
  Parameters: demographicNo (numeric patient identifier); groupName (configured measurement group).
  @since 2016-06-28
--%>
<!DOCTYPE html>
<%
    if (session.getAttribute("user") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString" %>
<%@ page import="io.github.carlos_emr.carlos.managers.MeasurementManager" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%
    Object demographicNoSource = request.getAttribute("demographicNo");
    if (demographicNoSource == null) {
        demographicNoSource = request.getParameter("demographicNo");
    }
    Integer demographicNo = ConversionUtils.fromIntString(demographicNoSource);
    String demo = demographicNo > 0 ? demographicNo.toString() : "";
    String patientNameAge = demo.isEmpty()
            ? ""
            : DemographicNameAgeString.getInstance().getNameAgeString(
                    LoggedInInfo.getLoggedInInfoFromSession(session), demographicNo);
    request.setAttribute("demo", demo);
    request.setAttribute("patientNameAge", patientNameAge);

    MeasurementManager measurementManager = SpringUtils.getBean(MeasurementManager.class);
    String groupName = (String) request.getAttribute("groupName");
%>
<fmt:message key="encounter.oscarMeasurements.Measurements.msgParentChanged" var="parentChangedMessage">
    <fmt:param value="${patientNameAge}"/>
</fmt:message>

<html>

    <head>
    <link rel="icon" href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/images/favicon.ico"/>
        <script type="text/javascript" src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/js/global.js"></script>
        <title><c:if test="${not empty groupName}">
            ${carlos:forHtmlContent(groupName)}
        </c:if> <fmt:message key="encounter.Index.measurements"/></title>

        <base href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/">


        <link href="library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">


        <link rel="stylesheet" href="css/fontawesome-all.min.css">

        <style>
            body {
                line-height: 14px;
            }

            h3 {
                line-height: 14px;
            }

            .note {
                padding: 0px;
                font-size: 12px;
            }

            .table td {
                line-height: 14px;
                padding: 3px;
            }

            .MainTableLeftColumn {
                vertical-align: top;
                padding: 14px;
            }
        </style>
        <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/calendar/calendar.js"></script>
        <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/calendar/lang/<fmt:message key="global.javascript.calendar"/>"></script>
        <script src="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/calendar/calendar-setup.js"></script>
        <link rel="stylesheet" type="text/css" media="all"
              href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/share/calendar/calendar.css" title="win2k-cold-2"/>

        <script type="text/javascript">

            function write2Parent(text) {

                self.close();
                opener.document.encForm.enTextarea.value = opener.document.encForm.enTextarea.value + text;
            }

            function getDropboxValue(ctr) {
                var selectedItem = document.forms[0].value(inputMInstrc - ctr).options[document.forms[0].value(inputMInstrc - ctr).selectedIndex].value;
                alert("hello!");
            }

            function popupPage(vheight, vwidth, page) { //open a new popup window

                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(page, "blah", windowprops);
            }

            parentChanged = false;

            // One Submit saves once (issues #4410, #4225): a double click or double Enter used to send
            // two posts that could both be stored. A second Submit while a save is out is ignored; the
            // button comes back after validation errors or a failure so a retry is not suppressed.
            var measurementSaveInFlight = false;

            function setMeasurementSaving(saving) {
                measurementSaveInFlight = saving;
                var submit = document.getElementById('measurementSubmit');
                if (submit) {
                    submit.disabled = saving;
                }
            }

            function check() {
                if (measurementSaveInFlight) {
                    return;
                }
                var ret = true;

                if (parentChanged) {
                    document.forms[0].elements["parentChanged"].value = "true";

                    if (!confirm("${carlos:forJavaScript(parentChangedMessage)}"))
                        ret = false;
                }

                if (ret) {
                    // Convert form data to URL-encoded string
                    const formData = new FormData(document.getElementById('theForm'));
                    const urlParams = new URLSearchParams(formData);
                    if (!urlParams.has('CSRF-TOKEN')) {
                        var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
                        if (csrfEl) urlParams.append('CSRF-TOKEN', csrfEl.value);
                    }

                    var csrfEl = document.querySelector('input[name="CSRF-TOKEN"]');
                    var csrfToken = csrfEl ? csrfEl.value : '';
                    setMeasurementSaving(true);
                    fetch('${carlos:forJavaScript(pageContext.request.contextPath)}/encounter/Measurements?ajax=true&skipCreateNote=true', {
                        method: 'POST',
                        credentials: 'same-origin',
                        headers: {
                            'Content-Type': 'application/x-www-form-urlencoded',
                            'CSRF-TOKEN': csrfToken,
                            'X-Requested-With': 'XMLHttpRequest'
                        },
                        body: urlParams.toString()
                    })
                    .then(response => {
                        if (!response.ok) {
                            throw new Error("Measurement submission failed with status " + response.status);
                        }
                        return response.json();
                    })
                    .then(data => {
                        const errorsList = document.getElementById('errors_list');
                        const errorDiv = document.getElementById('errorDiv');
                        
                        errorsList.innerHTML = '';
                        
                        if (data.errors && data.errors.length > 0) {
                            setMeasurementSaving(false);
                            for (let x = 0; x < data.errors.length; x++) {
                                const li = document.createElement('li');
                                li.textContent = data.errors[x];
                                errorsList.appendChild(li);
                            }
                            errorDiv.style.display = 'block';
                            // Scroll to top to show validation errors
                            errorDiv.scrollIntoView({ behavior: 'smooth', block: 'start' });
                        } else {
                            if (window.opener && !window.opener.closed) {
                                window.opener.postMessage(data, window.location.origin);
                            }
                            window.close();
                        }
                    })
                    .catch(error => {
                        setMeasurementSaving(false);
                        console.error('Error submitting form:', error);
                        const errorsList = document.getElementById('errors_list');
                        const errorDiv = document.getElementById('errorDiv');
                        if (errorsList && errorDiv) {
                            errorsList.textContent = '';
                            var li = document.createElement('li');
                            li.textContent = 'Failed to save measurements. Please try again. If the problem persists, refresh the page.';
                            errorsList.appendChild(li);
                            errorDiv.style.display = 'block';
                            errorDiv.scrollIntoView({ behavior: 'smooth', block: 'start' });
                        }
                    });
                }
            }
        </script>
    </head>
    <body class="BodyStyle" onload="window.focus();">
    <form action="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/encounter/Measurements" method="post" id="theForm" name="theForm">
        <c:if test="${not empty css}">
            <link rel="stylesheet" type="text/css" href="${carlos:forHtmlAttribute(css)}">
        </c:if>
        <c:if test="${empty css}">
            <!--<link rel="stylesheet" type="text/css" href="styles/measurementStyle.css">-->
        </c:if>

        <table class="MainTable" id="scrollNumber1">
            <tr class="MainTableTopRow">
                <td class="MainTableTopRowLeftColumn">
                    <c:if test="${not empty groupName}">
                        <h4>${carlos:forHtmlContent(groupName)}</h4>
                    </c:if>
                </td>
                <td class="MainTableTopRowRightColumn" style="padding:0px">
                    <table class="TopStatusBar" style="width:100%; height:100%;">
                        <tr>
                            <td class="Header"><h3>${carlos:forHtmlContent(patientNameAge)}</h3></td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableLeftColumn">
                    <table>
                        <tr>
                            <td><a
                                    href="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/encounter/ViewCalculators?demo=${carlos:forHtmlAttribute(carlos:forUriComponent(demo))}"
                                    onClick="popupPage(150,200,this.href); return false;"><fmt:message key="encounter.Index.calculators"/></a></td>
                        </tr>
                    </table>
                </td>
                <td class="MainTableRightColumn">

                    <%-- Trusted clinic decision-support template HTML loaded from the configured allowlisted directory. --%>
                    <%=measurementManager.getDShtml(groupName)%>
                    <div id="errorDiv" style="display:none; margin: 10px 0; padding: 10px; border: 1px solid #dc3545; background-color: #f8d7da; border-radius: 4px;">
                        <strong style="color: #721c24;">Error:</strong>
                        <ul id="errors_list" style="color: #721c24; margin: 5px 0 0 0; padding-left: 20px;">
                        </ul>
                    </div>

                    <table>
                        <tr>
                            <td>
                                <table>
                                    <tr>
                                        <td>
                                            <div class="card card-body bg-body-tertiary">
                                                <table class="table table-striped">
                                                    <% 
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
    <div class="action-errors">
        <ul>
            <% for (String error : actionErrors) { %>
                <li><carlos:encode value='<%= error %>' context="html"/></li>
            <% } %>
        </ul>
    </div>
<% } %>
                                                    <tr class="Header">
                                                        <th style="width:120px"><fmt:message key="encounter.oscarMeasurements.Measurements.headingType"/>
                                                        </th>
                                                        <th style="width:160px"><fmt:message key="encounter.oscarMeasurements.Measurements.headingMeasuringInstrc"/>
                                                        </th>
                                                        <th style="width:80px"><fmt:message key="encounter.oscarMeasurements.Measurements.headingValue"/>
                                                        </th>
                                                        <th style="width:120px"><fmt:message key="encounter.oscarMeasurements.Measurements.headingObservationDate"/>
                                                        </th>
                                                        <th style="width:240px"><fmt:message key="encounter.oscarMeasurements.Measurements.headingComments"/>
                                                        </th>
                                                        <th style="width:10px"></th>
                                                    </tr>
                                                    <% int i = 0;%>
                                                    <c:forEach var="measurementType" items="${measurementTypes.measurementTypeVector}" varStatus="ctr">
                                                        <tr class="data" id="row-${carlos:forHtmlAttribute(measurementType.type)}">
                                                            <td>
                                                                <span title="${carlos:forHtmlAttribute(measurementType.typeDesc)}">${carlos:forHtmlContent(measurementType.typeDisplayName)}</span>
                                                            </td>
                                                            <td>
                                                                <c:set var="attributeName" value="mInstrcs${ctr.index}" />
                                                                <c:forEach var="mInstrc" items="${requestScope[attributeName].measuringInstructionList}" varStatus="instructionStatus">
                                                                    <input type="radio" name="inputMInstrc-${ctr.index}" id="inputMInstrc-${ctr.index}-${instructionStatus.index}" value="${carlos:forHtmlAttribute(mInstrc.measuringInstrc)}" checked />
                                                                    <label for="inputMInstrc-${ctr.index}-${instructionStatus.index}">${carlos:forHtmlContent(mInstrc.measuringInstrc)}</label><br>
                                                                </c:forEach>
                                                            </td>

                                                            <c:choose>
                                                                <c:when test="${measurementType.measuringInstrc.startsWith('Choose radio')}">
                                                                    <td>
                                                                        <c:forEach var="option" items="${fn:split(measurementType.measuringInstrc.substring(12), ',')}" varStatus="optionStatus">
                                                                            <input type="radio" name="inputValue-${ctr.index}" id="inputValue-${ctr.index}-${optionStatus.index}" value="${carlos:forHtmlAttribute(fn:trim(option))}">
                                                                            <label for="inputValue-${ctr.index}-${optionStatus.index}">${carlos:forHtmlContent(fn:trim(option))}</label>&nbsp;
                                                                        </c:forEach>
                                                                    </td>
                                                                </c:when>
                                                                <c:otherwise>
                                                                    <td><input type="text" class="form-control form-control-sm" name="inputValue-${ctr.index}" id="inputValue-${ctr.index}"/></td>
                                                                </c:otherwise>
                                                            </c:choose>

                                                            <td><input type="text" class="form-control" name="date-${ctr.index}" id="date-${ctr.index}"/></td>
                                                            <script>
                                                                Calendar.setup({
                                                                    inputField: "date-${ctr.index}",
                                                                    ifFormat: "%Y-%m-%d",
                                                                    button: "date-${ctr.index}"
                                                                });
                                                            </script>

                                                            <td><input type="text" class="form-control" name="comments-${ctr.index}" id="comments-${ctr.index}"/></td>
                                                            <td>
                                                                <input type="hidden" name="inputType-${ctr.index}" value="${carlos:forHtmlAttribute(measurementType.type)}"/>
                                                                <input type="hidden" name="inputTypeDisplayName-${ctr.index}" value="${carlos:forHtmlAttribute(measurementType.typeDisplayName)}"/>
                                                                <input type="hidden" name="validation-${ctr.index}" value="${carlos:forHtmlAttribute(measurementType.validation)}"/>
                                                            </td>
                                                        </tr>

                                                        <c:if test="${not empty measurementType.lastData}">
                                                            <tr class="note">
                                                                <td><fmt:message key="oscarEncoutner.oscarMeasurements.msgTheLastValue"/>:</td>
                                                                <td>&nbsp;${carlos:forHtmlContent(measurementType.lastMInstrc)}</td>
                                                                <td>&nbsp;${carlos:forHtmlContent(measurementType.lastData)}</td>
                                                                <td>&nbsp;${carlos:forHtmlContent(measurementType.lastDateEntered)}</td>
                                                                <td>&nbsp;${carlos:forHtmlContent(measurementType.lastComments)}</td>
                                                                <td>
                                                                    <i class="fa-solid fa-clock fa-lg" title="<fmt:message key='encounter.Index.oldMeasurements'/>"
                                                                       onclick="popupPage(300,800,'encounter/oscarMeasurements/SetupDisplayHistory?type=${carlos:forJavaScriptAttribute(carlos:forUriComponent(measurementType.type))}&amp;demographicNo=${carlos:forJavaScriptAttribute(demo)}'); return false;">
                                                                    </i>
                                                                </td>
                                                            </tr>
                                                        </c:if>
                                                    </c:forEach>

                                                    <input type="hidden" name="numType" value="${fn:length(measurementTypes.measurementTypeVector)}"/>
                                                    <input type="hidden" name="groupName" value="${carlos:forHtmlAttribute(groupName)}"/>
                                                    <input type="hidden" name="parentChanged" value="false"/>
                                                    <input type="hidden" name="demographicNo" value="${carlos:forHtmlAttribute(demo)}"/>
                                                    <input type="hidden" name="demographic_no" value="${carlos:forHtmlAttribute(demo)}"/>

                                                    <c:if test="${not empty css}">
                                                        <input type="hidden" name="css" value="${carlos:forHtmlAttribute(css)}"/>
                                                    </c:if>
                                                    <c:if test="${empty css}">
                                                        <input type="hidden" name="css" value=""/>
                                                    </c:if>

                                                </table>
                                            </div> <!-- card card-body bg-body-tertiary -->
                                            <table>
                                                <tr>
                                                    <td><input type="button" name="Button" class="btn btn-secondary"
                                                               value="<fmt:message key="global.btnCancel"/>"
                                                               onClick="window.close()"></td>
                                                    <td><input type="button" name="Button" id="measurementSubmit" class="btn btn-primary"
                                                               value="<fmt:message key="global.btnSubmit"/>"
                                                               onclick="check();"/></td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="MainTableBottomRowLeftColumn"></td>
                <td class="MainTableBottomRowRightColumn"></td>
            </tr>
        </table>
    </form>

    <script>

        function wtEnglish2Metric(obj) {
            const weight = parseFloat(obj.value);
            if (isNaN(weight)) return;
            const weightM = Math.round(weight * 0.4536 * 10) / 10;
            if (confirm("Are you sure you want to change " + weight + " pounds to " + weightM + "kg?")) {
                obj.value = weightM;
            }
        }

        function htEnglish2Metric(obj) {
            // allow for conversio of inputs 5' 5'0" 5'0 60" - 152.4cm
            const val = obj.value.trim();
            const tickIdx = val.indexOf("'");
            const quotIdx = val.indexOf('"');
            if (tickIdx <= 0 && quotIdx <= 0) return;
            let feet = 0;
            if (tickIdx > 0 ) feet = parseFloat(val.substring(0, tickIdx));
            let inchStr = val;
            if (tickIdx > 0 ) inchStr = val.substring(tickIdx + 1);
            if (inchStr.endsWith('"')) {
                inchStr = inchStr.substring(0, inchStr.length - 1);
            }
            const inch = parseFloat(inchStr) || 0;
            if (isNaN(feet)) return;
            const heightM = Math.round((feet * 30.48 + inch * 2.54) * 10) / 10;
            if (confirm("Are you sure you want to change " + feet + " feet " + inch + " inch(es) to " + heightM + "cm?")) {
                obj.value = heightM;
            }
        }

        // Waist is stored in cm. A double click treats the typed value as inches, with or without
        // an inch mark (34 or 34"), and converts it once the user confirms, as WT does for pounds.
        function waistEnglish2Metric(obj) {
            let val = obj.value.trim();
            if (val.endsWith('"')) {
                val = val.substring(0, val.length - 1).trim();
            }
            const inch = parsePositiveMeasurement(val);
            if (inch === null) return;
            const waistM = Math.round(inch * 2.54 * 10) / 10;
            if (confirm("Are you sure you want to change " + inch + " inch(es) to " + waistM + "cm?")) {
                obj.value = waistM;
            }
        }

        // A value a calculated measurement may use: a plain positive decimal and nothing else.
        // parseFloat would accept "80 cm" or 5'5" and let the unit or the feet mark into a division.
        function parsePositiveMeasurement(text) {
            const value = String(text == null ? '' : text).trim();
            if (!/^(\d+\.?\d*|\.\d+)$/.test(value)) return null;
            const number = Number(value);
            return Number.isFinite(number) && number > 0 ? number : null;
        }

        // The row of a clinic-added measurement type, matched without regard to case: type codes
        // compare case-insensitively in the database, and Add Measurement Type stores them
        // upper-cased, so a type entered as WHtR is rendered as row-WHTR.
        function measurementRowAnyCase(type) {
            return document.querySelector('tr[id="row-' + type + '" i]');
        }

        // The value input of a measurement row (its third cell), or null when the group has no such row.
        function measurementValueInput(row) {
            return row?.querySelectorAll('td')[2]?.querySelector('input') || null;
        }

        document.addEventListener('DOMContentLoaded', function () {
            // If WT, HT and BMI exists then allow the link
            const rowWT = document.getElementById('row-WT');
            const rowHT = document.getElementById('row-HT');
            const rowWAIS = document.getElementById('row-WAIS');
            const rowWHtR = measurementRowAnyCase('WHtR');
            const rowBMI = document.getElementById('row-BMI');

            if (rowWT) {
                const wtInput = rowWT.querySelectorAll('td')[2]?.querySelector('input');
                if (wtInput) {
                    wtInput.title = "Double click to convert lb to kg";
                    wtInput.style.backgroundColor = "#d9e6f2";
                    wtInput.addEventListener('dblclick', function () {
                        wtEnglish2Metric(this);
                    });
                }
            }

            if (rowHT) {
                const htInput = rowHT.querySelectorAll('td')[2]?.querySelector('input');
                if (htInput) {
                    htInput.title = "Double click to convert feet and inches (5'2\") to cm";
                    htInput.style.backgroundColor = "#d9e6f2";
                    htInput.addEventListener('dblclick', function () {
                        htEnglish2Metric(this);
                    });
                }
            }

            if (rowWAIS) {
                const waisInput = rowWAIS.querySelectorAll('td')[2]?.querySelector('input');
                if (waisInput) {
                    waisInput.title = "Double click to convert inches to cm";
                    waisInput.style.backgroundColor = "#d9e6f2";
                    waisInput.addEventListener('dblclick', function () {
                        waistEnglish2Metric(this);
                    });
                }
            }

            if (rowWT && rowHT && rowBMI) {
                const wtInput = rowWT.querySelectorAll('td')[2]?.querySelector('input');
                const htInput = rowHT.querySelectorAll('td')[2]?.querySelector('input');

                if (wtInput && htInput) {
                    wtInput.addEventListener('blur', function () {
                        calcBMI(this.value, htInput.value);
                    });

                    htInput.addEventListener('blur', function () {
                        calcBMI(wtInput.value, this.value);
                    });
                }
            }

            // WHtR is calculated only where the clinic has added a measurement type coded WHtR (in
            // any case) to the group alongside HT and WAIS; CARLOS does not ship that type.
            if (rowWAIS && rowHT && rowWHtR) {
                const waisInput = rowWAIS.querySelectorAll('td')[2]?.querySelector('input');
                const htInput = rowHT.querySelectorAll('td')[2]?.querySelector('input');
                const whtrInput = measurementValueInput(rowWHtR);
                if (waisInput && htInput && whtrInput) {
                    const updateWHtR = function () {
                        calcWHtR(waisInput.value, htInput.value);
                    };
                    // Typing in WHtR makes the value the user's own, even when it matches the last
                    // calculation; a value set by script fires no input event.
                    whtrInput.addEventListener('input', function () {
                        delete whtrInput.dataset.calculatedValue;
                    });
                    // The dblclick listeners run after the unit conversions registered above, so a
                    // value converted to cm is used at once rather than on the next blur.
                    [waisInput, htInput].forEach(function (input) {
                        input.addEventListener('blur', updateWHtR);
                        input.addEventListener('dblclick', updateWHtR);
                    });
                }
            }

            // Set today's date for all date fields
            const utc = new Date().toJSON().slice(0, 10);
            document.querySelectorAll('[id^="date-"]').forEach(function(elem) {
                elem.value = utc;
            });
        });

        function calcBMI(w, h) {
            let b = '';

            if (!isNaN(parseFloat(w)) && !isNaN(parseFloat(h)) && h !== "" && w !== "") {
                if (h > 0) {
                    b = (w / Math.pow(h / 100, 2)).toFixed(1);
                    const bmiInput = document.getElementById('row-BMI')?.querySelectorAll('td')[2]?.querySelector('input');
                    
                    if (bmiInput) {
                        bmiInput.value = b;
                        bmiInput.style.backgroundColor = "#d9e6f2";
                    }
                }
            }
        }

        // Waist-to-height ratio: waist (cm) / height (cm), to two decimals. When either input stops
        // being a usable number, a ratio this page wrote is cleared, so Submit cannot save a WHtR for
        // values the form no longer holds; a WHtR the user typed in is left as it is.
        function calcWHtR(wais, h) {
            const whtrInput = measurementValueInput(measurementRowAnyCase('WHtR'));
            if (!whtrInput) return;

            const waist = parsePositiveMeasurement(wais);
            const height = parsePositiveMeasurement(h);
            if (waist === null || height === null) {
                if (whtrInput.dataset.calculatedValue !== undefined
                        && whtrInput.value === whtrInput.dataset.calculatedValue) {
                    whtrInput.value = '';
                    whtrInput.style.backgroundColor = '';
                }
                delete whtrInput.dataset.calculatedValue;
                return;
            }

            const ratio = (waist / height).toFixed(2);
            whtrInput.value = ratio;
            whtrInput.dataset.calculatedValue = ratio;
            whtrInput.style.backgroundColor = "#d9e6f2";
        }

    </script>

    </body>
</html>
