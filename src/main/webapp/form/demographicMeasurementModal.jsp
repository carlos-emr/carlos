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
<script src="<%=request.getContextPath() %>/library/jquery/jquery-1.12.0.min.js" type="text/javascript"></script>
<script type="text/javascript" src="${pageContext.request.contextPath}/library/moment.js"></script>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/alertify.core.css"/>
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/alertify.default.css"/>
<script type="text/javascript" src="${pageContext.request.contextPath}/js/alertify.js"></script>

<style type="text/css">
    .view-height-75-scroll {
        max-height: 75vh;
        overflow-y: auto;
    }

    .measurement-modal-header {
        text-align: center;
        margin: 0;
    }
</style>
<script>
    // Must include jQuery for ajax call, cannot assume the form this file will be included in has it already included
    let local_jQuery = jQuery.noConflict(true);

    // Map of measurementTypes to corresponding name and default instructions
    let measurementTypeMap = {
        'WT': {'name': 'Weight', 'instructions': 'in kg'},
        'HT': {'name': 'Height', 'instructions': 'in cm'},
        'HR': {'name': 'Heart Rate', 'instructions': 'in BPMB'},
        'BP': {'name': 'Blood Pressure', 'instructions': ''}
    };
    let existingMeasurementUsed = false;

    /**
     * This function will retrieve specific demographic measurement data and display it in a modal for the user to select and import into the desired form field
     *
     * @param elementId - The ID of the input element on the form that the measurement value will be inserted into
     * @param measurementType - The type of measurement that will be retrieved (height, weight, etc.)
     * @param measurementUnits - The birth date of the selected patient
     * @param demographicNo - The demographic number of the selected patient
     * @param demographicDobString - The birth date of the selected patient
     * @param appointmentNo - The appointment the form is created on
     */
    function displayDemographicMeasurements(elementId, measurementType, demographicNo, demographicDobString, appointmentNo) {
        let demographicDob = new Date(demographicDobString);

        local_jQuery.ajax({
            type: 'POST',
            url: '<%=request.getContextPath()%>/oscarEncounter/MeasurementData.do?action=getMeasurementsByType&demographicNo=' + demographicNo + '&measurementType=' + measurementType,
            async: false,
            dataType: 'json',
            success: function (data) {
                // On successful retrieval of the measurement data, a modal body will be constructed with the list for selection
                let modalHeader = "<h4 class=\"measurement-modal-header\">" + measurementTypeMap[measurementType].name + "</h4>";
                let measurementValueInput = "Current Value: <input type=\"text\" id=\"currentMeasurementValue\" value=\"" + document.getElementById(elementId).value + "\" onkeydown=\"resetInstructions('" + measurementType + "')\"/> <span id=\"measurementInstruction\">" + measurementTypeMap[measurementType].instructions + "</span>";
                let observationDateInput = "Observation Date: <input type=\"date\" id=\"currentMeasurementObservationDate\" value=\"" + moment(new Date()).format('YYYY-MM-DD') + "\"/>";
                let body = "<div class=\"view-height-75-scroll\">" + modalHeader + "<div>" + measurementValueInput + "<br/>" + observationDateInput + "</div>";

                if (data[-1] !== null && data[-1] !== "No Results Found") {
                    local_jQuery.each(data, function () {
                        // At the beginning of each iteration, the patients age in days, weeks, months and years at the date of observation will be calculated, and displayed based on what the result is
                        let ageDisplay = 'Age: ';
                        let dateObserved = new Date(this.dateObserved.time);
                        let ageDays = Math.floor((dateObserved.getTime() - demographicDob.getTime()) / 1000 / 60 / 60 / 24);
                        let tempAgeDays = ageDays;
                        let months = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
                        let currentMonth = 0;
                        let ageMonths = 0;
                        while (tempAgeDays > 0) {
                            tempAgeDays -= months[currentMonth];

                            if (ageDays > 0) {
                                ageMonths++;
                            }

                            if (currentMonth === 11) {
                                currentMonth = 0;
                            } else {
                                currentMonth++;
                            }
                        }
                        let ageWeeks = Math.floor((dateObserved.getTime() - demographicDob.getTime()) / 1000 / 60 / 60 / 24 / 7);
                        let ageYears = dateObserved.getFullYear() - demographicDob.getFullYear();

                        // Deciding which measurement of time to display based on the patients age at the time
                        if (ageDays <= 7) {
                            ageDisplay += ageDays + ' days old';
                        } else if (ageWeeks <= 4) {
                            ageDisplay += ageWeeks + ' weeks old';
                        } else if (ageMonths <= 12) {
                            ageDisplay += ageMonths + ' months old';
                        } else {
                            ageDisplay += ageYears + ' years old';
                        }

                        body += "<a href=\"#\"><p onclick=\"setDemographicMeasurementModalValues('" + this.dataField + "', '" + this.measuringInstruction + "', '" + moment(this.dateObserved.time).format('YYYY-MM-DD') + "'); return false;\">" + this.dataField + " " + this.measuringInstruction + " (" + moment(this.dateObserved.time).format('YYYY-MM-DD') + " - " + ageDisplay + ")</p></a>";
                    });
                }

                body += "</div>";

                alertify.set({labels: {ok: 'Save', cancel: 'Okay'}});
                alertify.confirm(body, function (save) {
                    if (save && !existingMeasurementUsed) {
                        // If the user clicks save, complete an ajax call that will save a new measurement record to the database
                        local_jQuery.ajax({
                            type: 'POST',
                            url: '<%=request.getContextPath()%>/oscarEncounter/MeasurementData.do?action=saveMeasurement&demographicNo=' + demographicNo + '&appointmentNo=' + appointmentNo + '&type=' + measurementType +
                                '&value=' + document.getElementById("currentMeasurementValue").value + '&instruction=' + document.getElementById('measurementInstruction').innerHTML + "&dateObserved=" + document.getElementById('currentMeasurementObservationDate').value,
                            dataType: 'json',
                            async: false,
                            success: function (data) {
                                // If the JSON data returned states success = true, display success message, else display failed
                                if (data && data.success) {
                                    alertify.success("Successfully saved measurement!");
                                } else {
                                    alertify.error("Failed to save measurement");
                                }
                            }
                        });
                    }
                    // After the desired measurement is selected and inserted into the input at the top, clicking OK or Save will close the modal and insert the value into the form field
                    document.getElementById(elementId).value = document.getElementById("currentMeasurementValue").value;
                }).set('modal', false);
            }
        });
    }

    function setDemographicMeasurementModalValues(currentMeasurementValue, measurementInstruction, currentMeasurementObservationDate) {
        let currentMeasurementValueElement = document.getElementById('currentMeasurementValue');
        let measurementInstructionElement = document.getElementById('measurementInstruction');
        let currentMeasurementObservationDateElement = document.getElementById('currentMeasurementObservationDate');
        currentMeasurementValueElement.value = currentMeasurementValue;
        measurementInstructionElement.innerHTML = measurementInstruction;
        currentMeasurementObservationDateElement.value = currentMeasurementObservationDate;
        existingMeasurementUsed = true;
    }

    function resetInstructions(measurementType) {
        let measurementInstructionElement = document.getElementById('measurementInstruction');
        measurementInstructionElement.innerHTML = measurementTypeMap[measurementType].instructions;
        existingMeasurementUsed = false;
    }
</script>