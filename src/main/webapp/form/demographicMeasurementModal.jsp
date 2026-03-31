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
<script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js" type="text/javascript"></script>
<script type="text/javascript" src="${pageContext.request.contextPath}/library/moment.js"></script>

<style type="text/css">
    .view-height-75-scroll {
        max-height: 75vh;
        overflow-y: auto;
    }

    .measurement-modal-header {
        text-align: center;
        margin: 0;
    }

    .meas-dialog-overlay {
        position: fixed;
        inset: 0;
        background: rgba(0,0,0,0.3);
        z-index: 10000;
        display: flex;
        align-items: center;
        justify-content: center;
    }
    .meas-dialog {
        background: #fff;
        border-radius: 6px;
        box-shadow: 0 4px 20px rgba(0,0,0,0.3);
        max-width: 600px;
        width: 90%;
        max-height: 90vh;
        display: flex;
        flex-direction: column;
    }
    .meas-dialog-body {
        padding: 20px;
        overflow-y: auto;
    }
    .meas-dialog-footer {
        padding: 10px 20px;
        text-align: right;
        border-top: 1px solid #ddd;
    }
    .meas-dialog-footer button {
        margin-left: 8px;
        padding: 6px 18px;
        border: 1px solid #ccc;
        border-radius: 4px;
        cursor: pointer;
        font-size: 14px;
    }
    .meas-dialog-footer .meas-btn-save {
        background: #337ab7;
        color: #fff;
        border-color: #337ab7;
    }
    .meas-toast {
        position: fixed;
        bottom: 20px;
        right: 20px;
        padding: 12px 20px;
        border-radius: 4px;
        color: #fff;
        font-size: 14px;
        z-index: 10001;
        opacity: 0;
        transition: opacity 0.3s;
    }
    .meas-toast-success { background: #5cb85c; }
    .meas-toast-error { background: #d9534f; }
    .meas-toast-visible { opacity: 1; }
</style>
<script>
    // Local jQuery reference for ajax calls in this modal
    let local_jQuery = jQuery;

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
            url: '<%=request.getContextPath()%>/encounter/MeasurementData.do?action=getMeasurementsByType&demographicNo=' + demographicNo + '&measurementType=' + measurementType,
            async: false,
            dataType: 'json',
            success: function (data) {
                // On successful retrieval of the measurement data, a modal body will be constructed with the list for selection
                let modalHeader = "<h4 class=\"measurement-modal-header\">" + measurementTypeMap[measurementType].name + "</h4>";
                let measurementValueInput = "Current Value: <input type=\"text\" id=\"currentMeasurementValue\" value=\"" + document.getElementById(elementId).value + "\" onkeydown=\"resetInstructions('" + measurementType + "')\"/> <span id=\"measurementInstruction\">" + measurementTypeMap[measurementType].instructions + "</span>";
                let observationDateInput = "Observation Date: <input type=\"date\" id=\"currentMeasurementObservationDate\" value=\"" + new Date().toISOString().slice(0, 10) + "\"/>";
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

                        let obsDate = new Date(this.dateObserved.time).toISOString().slice(0, 10);
                        body += "<a href=\"#\"><p onclick=\"setDemographicMeasurementModalValues('" + this.dataField + "', '" + this.measuringInstruction + "', '" + obsDate + "'); return false;\">" + this.dataField + " " + this.measuringInstruction + " (" + obsDate + " - " + ageDisplay + ")</p></a>";
                    });
                }

                body += "</div>";

                showMeasurementDialog(body, function (save) {
                    if (save && !existingMeasurementUsed) {
                        // If the user clicks save, complete an ajax call that will save a new measurement record to the database
                        local_jQuery.ajax({
                            type: 'POST',
                            url: '<%=request.getContextPath()%>/encounter/MeasurementData.do?action=saveMeasurement&demographicNo=' + demographicNo + '&appointmentNo=' + appointmentNo + '&type=' + measurementType +
                                '&value=' + document.getElementById("currentMeasurementValue").value + '&instruction=' + document.getElementById('measurementInstruction').innerHTML + "&dateObserved=" + document.getElementById('currentMeasurementObservationDate').value,
                            dataType: 'json',
                            async: false,
                            success: function (data) {
                                // If the JSON data returned states success = true, display success message, else display failed
                                if (data && data.success) {
                                    showMeasurementToast("Successfully saved measurement!", "success");
                                } else {
                                    showMeasurementToast("Failed to save measurement", "error");
                                }
                            }
                        });
                    }
                    // After the desired measurement is selected and inserted into the input at the top, clicking OK or Save will close the modal and insert the value into the form field
                    document.getElementById(elementId).value = document.getElementById("currentMeasurementValue").value;
                });
            }
        });
    }

    function setDemographicMeasurementModalValues(currentMeasurementValue, measurementInstruction, currentMeasurementObservationDate) {
        let currentMeasurementValueElement = document.getElementById('currentMeasurementValue');
        let measurementInstructionElement = document.getElementById('measurementInstruction');
        let currentMeasurementObservationDateElement = document.getElementById('currentMeasurementObservationDate');
        currentMeasurementValueElement.value = currentMeasurementValue;
        measurementInstructionElement.textContent = measurementInstruction;
        currentMeasurementObservationDateElement.value = currentMeasurementObservationDate;
        existingMeasurementUsed = true;
    }

    function resetInstructions(measurementType) {
        let measurementInstructionElement = document.getElementById('measurementInstruction');
        measurementInstructionElement.textContent = measurementTypeMap[measurementType].instructions;
        existingMeasurementUsed = false;
    }

    /**
     * Shows a confirm-style dialog with Save and Okay buttons.
     * Calls callback(true) on Save, callback(false) on Okay.
     * Supports keyboard dismissal (ESC cancels) and overlay-click to cancel.
     * Note: bodyHtml is constructed from server measurement data within displayDemographicMeasurements(),
     * not from user input. It contains onclick/onkeydown handlers that must be preserved, so
     * DOMPurify cannot be used here (it strips event handler attributes by default).
     */
    function showMeasurementDialog(bodyHtml, callback) {
        var overlay = document.createElement('div');
        overlay.className = 'meas-dialog-overlay';

        var dialog = document.createElement('div');
        dialog.className = 'meas-dialog';

        var bodyDiv = document.createElement('div');
        bodyDiv.className = 'meas-dialog-body';
        bodyDiv.innerHTML = bodyHtml;

        var footer = document.createElement('div');
        footer.className = 'meas-dialog-footer';

        var cancelBtn = document.createElement('button');
        cancelBtn.type = 'button';
        cancelBtn.className = 'meas-btn-cancel';
        cancelBtn.textContent = 'Okay';

        var saveBtn = document.createElement('button');
        saveBtn.type = 'button';
        saveBtn.className = 'meas-btn-save';
        saveBtn.textContent = 'Save';

        footer.appendChild(cancelBtn);
        footer.appendChild(saveBtn);
        dialog.appendChild(bodyDiv);
        dialog.appendChild(footer);
        overlay.appendChild(dialog);
        document.body.appendChild(overlay);

        // Move focus into the dialog so keyboard events are scoped correctly
        dialog.setAttribute('tabindex', '-1');
        dialog.focus();

        function close(result) {
            document.removeEventListener('keydown', keyHandler);
            document.body.removeChild(overlay);
            if (callback) callback(result);
        }

        // ESC key cancels the dialog
        function keyHandler(e) {
            if (e.key === 'Escape' || e.keyCode === 27) {
                close(false);
            }
        }
        document.addEventListener('keydown', keyHandler);

        // Click on the overlay background (outside the dialog) cancels
        overlay.addEventListener('click', function (e) {
            if (e.target === overlay) {
                close(false);
            }
        });

        saveBtn.addEventListener('click', function () { close(true); });
        cancelBtn.addEventListener('click', function () { close(false); });
    }

    /**
     * Shows a brief toast notification (auto-dismisses after 3 seconds).
     * @param message text to display
     * @param type "success" or "error"
     */
    function showMeasurementToast(message, type) {
        var toast = document.createElement('div');
        toast.className = 'meas-toast meas-toast-' + type;
        toast.textContent = message;
        document.body.appendChild(toast);
        requestAnimationFrame(function () { toast.classList.add('meas-toast-visible'); });
        setTimeout(function () {
            toast.classList.remove('meas-toast-visible');
            setTimeout(function () { document.body.removeChild(toast); }, 300);
        }, 3000);
    }
</script>