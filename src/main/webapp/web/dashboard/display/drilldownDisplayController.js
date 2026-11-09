/*
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
*/

var dateRegex = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/;


//*--> METHODS AND FUNCTIONS FOR TICKLER ASSIGNMENT <--*//

//--> check that all fields tagged "required" contain data.
function checkFields() {
    var verified = true;

    $("#ticklerAddForm .required").each(function () {
        if ($(this).val().length == 0) {
            verified = false;
            paintErrorField($(this));
        }
    })

    $("#ticklerAddForm .date-picker, #ticklerAddForm .time-picker").each(function () {
        var format = this.name === 'serviceDate' ? 'Y-m-d' : 'h:i K';
        if (!parseTicklerValue(this.value, format)) {
            this.setCustomValidity(ticklerFormatMessage(format));
            this.reportValidity();
            paintErrorField($(this));
            verified = false;
        }
    });
    return verified;
}

// Field-specific guidance shared by the picker and save-time validation.
function ticklerFormatMessage(format) {
    var form = document.getElementById && document.getElementById('ticklerAddForm');
    if (form) return form.getAttribute(format === 'Y-m-d' ? 'data-date-error' : 'data-time-error');
    return format === 'Y-m-d'
        ? 'Invalid service date. Use yyyy-MM-dd (for example 2030-12-31).'
        : 'Invalid service time. Use hh:mm AM/PM or 24-hour HH:mm (for example 14:30).';
}

// Flatpickr's default parser accepts partial matches and rolls impossible dates/times forward.
// Only exact, real dates and times may reach its formatter or the save request.
function parseTicklerValue(value, format) {
    var match;
    var date = new Date(2000, 0, 1);
    if (format === 'Y-m-d') {
        match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
        if (!match || +match[1] === 0) return undefined;
        date.setHours(0, 0, 0, 0);
        date.setFullYear(+match[1], +match[2] - 1, +match[3]);
        if (date.getFullYear() !== +match[1] || date.getMonth() !== +match[2] - 1
                || date.getDate() !== +match[3]) return undefined;
    } else if (format === 'h:i K') {
        match = /^(0?[1-9]|1[0-2]):([0-5]\d) (AM|PM)$/i.exec(value);
        if (match) {
            date.setHours(+match[1] % 12 + (match[3].toUpperCase() === 'PM' ? 12 : 0), +match[2], 0, 0);
        } else {
            match = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(value);
            if (!match) return undefined;
            date.setHours(+match[1], +match[2], 0, 0);
        }
    } else {
        return undefined;
    }
    return date;
}

/**
 * Start the Service Date / Time pickers inside the Assign Tickler modal. Must run after the modal body is
 * inserted: AssignTickler.jsp cannot carry its own script because DOMPurify strips it. The formats are
 * accepted by TicklerRequest (yyyy-MM-dd; hh:mm AM/PM).
 */
function initTicklerPickers() {
    if (typeof flatpickr === 'undefined') return;
    $('#ticklerAddForm .date-picker, #ticklerAddForm .time-picker').each(function () {
        var input = this;
        var isTime = input.name === 'serviceTime';
        var format = isTime ? 'h:i K' : 'Y-m-d';
        var message = ticklerFormatMessage(format);
        var feedback = document.createElement('div');
        feedback.className = 'text-danger';
        feedback.id = input.id + 'Error';
        feedback.hidden = true;
        feedback.textContent = message;
        input.parentNode.parentNode.appendChild(feedback);
        input.setAttribute('aria-describedby', feedback.id);
        function clearError() {
            input.setCustomValidity('');
            input.removeAttribute('aria-invalid');
            input.style.border = '';
            feedback.hidden = true;
        }
        function showError() {
            input.setCustomValidity(message);
            input.setAttribute('aria-invalid', 'true');
            feedback.hidden = false;
        }
        // Flatpickr recognizes "today" before calling parseDate. Check raw text before any
        // commit event, including outside pointer presses (which precede blur).
        var invalidText = null;
        var interactingWithPicker = false;
        function rejectInvalidText(event) {
            if (input.value && !parseTicklerValue(input.value, format)) {
                // Stop Flatpickr's commit handler from clearing/reinterpreting raw input.
                if (event) {
                    event.stopImmediatePropagation();
                    // A pointer press inside the calendar blurs this input before
                    // its arrow/day handler runs. Keep the picker open for correction.
                    if (event.type === 'blur' && interactingWithPicker) {
                        showError();
                        return;
                    }
                }
                invalidText = input.value;
                if (event) {
                    if (event.type === 'keydown' && event.key === 'Enter') event.preventDefault();
                    // Closing explicitly also retires the popup after a refused Tab/Enter.
                    // Flatpickr's own commit handler would clear or reinterpret this text.
                    input._flatpickr.close();
                }
                showError();
            }
        }
        function rejectOnCommitKey(event) {
            if (event.key === 'Enter' || event.key === 'Tab') rejectInvalidText(event);
        }
        function rejectOnOutsidePress(event) {
            interactingWithPicker = input._flatpickr.calendarContainer.contains(event.target);
            if (interactingWithPicker) invalidText = null;
            if (event.target !== input && !input._flatpickr.calendarContainer.contains(event.target)) {
                // Let Flatpickr close its popup; onClose restores the refused text.
                rejectInvalidText();
            }
        }
        input.addEventListener('blur', rejectInvalidText, true);
        input.addEventListener('keydown', rejectOnCommitKey, true);
        document.addEventListener('pointerdown', rejectOnOutsidePress, true);
        document.addEventListener('mousedown', rejectOnOutsidePress, true);
        document.addEventListener('touchstart', rejectOnOutsidePress, true);
        function clearIfValid() {
            invalidText = input.value && !parseTicklerValue(input.value, format) ? input.value : null;
            if (!invalidText) clearError();
        }
        input.addEventListener('input', clearIfValid);
        flatpickr(input, {
            dateFormat: format,
            enableTime: isTime,
            noCalendar: isTime,
            monthSelectorType: isTime ? 'static' : 'dropdown',
            allowInput: true,
            disableMobile: true,
            parseDate: parseTicklerValue,
            // Preserve refused text when Flatpickr closes after an outside press.
            // Do not log flatpickr's error: it includes the user's input.
            errorHandler: showError,
            onDestroy: function () {
                document.removeEventListener('pointerdown', rejectOnOutsidePress, true);
                document.removeEventListener('mousedown', rejectOnOutsidePress, true);
                document.removeEventListener('touchstart', rejectOnOutsidePress, true);
                input.removeEventListener('input', clearIfValid);
                input.removeEventListener('blur', rejectInvalidText, true);
                input.removeEventListener('keydown', rejectOnCommitKey, true);
                feedback.remove();
            },
            onClose: function () {
                if (invalidText !== null) { input.value = invalidText; showError(); }
            },
            onChange: function (dates) {
                if (dates.length && invalidText === null) clearError();
            }
        });
    });
}

function destroyTicklerPickers() {
    $('#ticklerAddForm .date-picker, #ticklerAddForm .time-picker').each(function () {
        if (this._flatpickr) this._flatpickr.destroy();
    });
}

//paint a red border around missing fields
function paintErrorField(fieldobject) {
    fieldobject.css("border", "medium solid red");
}


//*--> MASTER AJAX METHOD <--*//
function sendData(path, param, target, button) {
    if (target == "close" || target == "modal") {
        if (button && button.disabled) return;
        if (button) button.disabled = true;
        // AJAX for modal interactions — sanitize HTML before DOM insertion
        $.ajax({
            url: ctx + path,
            type: 'POST',
            data: param,
            dataType: target === 'close' ? 'json' : 'html',
            success: function (data) {
                if (target == "close") {
                    if (data && (data.success === true || data.success === 'true')) {
                        bootstrap.Modal.getOrCreateInstance(document.getElementById('assignTickler')).hide();
                    } else {
                        alert('Ticklers could not all be saved. Some may already have been created; review the selected patients before trying again.');
                    }
                } else if (target == "modal") {
                    destroyTicklerPickers();
                    if (typeof DOMPurify !== 'undefined') {
                        // DOMPurify sanitization with defaults plus form elements. Event handlers are stripped by DOMPurify defaults.
                        try {
                            $('#assignTickler').find('.modal-body').html(DOMPurify.sanitize(data, {ADD_TAGS: ['input', 'select', 'option', 'textarea'], ADD_ATTR: ['value', 'selected']}));
                        } catch (e) {
                            console.error('Error sanitizing modal content:', e);
                            $('#assignTickler').find('.modal-body').html('<p style="color:red">Unable to display content safely.</p>');
                        }
                    } else {
                        console.error('DOMPurify is required but not loaded. Modal content blocked to prevent XSS.');
                        $('#assignTickler').find('.modal-body').html('<p style="color:red">Unable to display content safely. Please reload the page.</p>');
                    }
                    initTicklerPickers();
                    bootstrap.Modal.getOrCreateInstance(document.getElementById('assignTickler')).show();
                }
            },
            error: function (xhr, status, error) {
                if (target === 'close' && xhr.status === 400 && xhr.responseJSON && xhr.responseJSON.message) {
                    // Validation refused the request before anything was written; say what to fix.
                    alert('Nothing was saved. ' + xhr.responseJSON.message);
                } else if (target === 'close') {
                    alert('The tickler save could not be confirmed. Review the selected patients before trying again.');
                } else {
                    console.error('Drilldown request failed:', status, error);
                }
                if (target == "modal") {
                    destroyTicklerPickers();
                    $('#assignTickler').find('.modal-body').html(
                        '<p style="color:red">Request failed. Please reload the page.</p>');
                    bootstrap.Modal.getOrCreateInstance(document.getElementById('assignTickler')).show();
                }
            },
            complete: function () {
                if (button) button.disabled = false;
            }
        });
    } else {
        // Full-page navigation — use form submission so the browser handles
        // the response natively (including scripts, stylesheets, etc.)
        var form = document.createElement('form');
        form.method = 'POST';
        form.action = ctx + path;
        if (typeof param === 'string') {
            param.split('&').forEach(function(pair) {
                var parts = pair.split('=', 2);
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = decodeURIComponent(parts[0]);
                input.value = decodeURIComponent(parts[1] || '');
                form.appendChild(input);
            });
        } else {
            Object.keys(param).forEach(function(key) {
                var input = document.createElement('input');
                input.type = 'hidden';
                input.name = key;
                input.value = param[key];
                form.appendChild(input);
            });
        }
        // Explicitly add CSRF token rather than relying on MutationObserver timing
        var csrfTokenEl = document.querySelector('input[name="CSRF-TOKEN"]');
        if (csrfTokenEl) {
            var csrfInput = document.createElement('input');
            csrfInput.type = 'hidden';
            csrfInput.name = 'CSRF-TOKEN';
            csrfInput.value = csrfTokenEl.value;
            form.appendChild(csrfInput);
        } else {
            console.warn('CSRF token not found on page; form submission may be rejected by server.');
        }
        document.body.appendChild(form);
        form.submit();
    }
}

//--> Datatable Filter
$.fn.dataTableExt.afnFiltering.push(function (oSettings, aData, iDataIndex) {
    var inputLow = $('#datatableFilterConditionGreaterThan').val();
    var inputHigh = $('#datatableFilterConditionLessThan').val();
    var iStartDateCol = $('#datatableFilterColumnSelector').val();
    var iEndDateCol = $('#datatableFilterColumnSelector').val();
    var condition = $('#datatableFilterConditionSelector').val();

    inputLow = inputLow.trim();
    inputHigh = inputHigh.trim();

    iFini = filterDate(inputLow) * 1;
    iFfin = filterDate(inputHigh) * 1;
    var datofini = filterDate(aData[iStartDateCol]);
    var datoffin = filterDate(aData[iEndDateCol]);

    if (condition === 'equal'
        && iFini !== iFini
        && new RegExp('^' + inputLow.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '.*$', 'i').test(datofini)) {
        return true;
    } else if (condition === 'equal' && parseFloat(iFini) == parseFloat(datofini)) {
        return true;
    } else if ((iFini === "" && iFfin === "") || (iFini === 0 && iFfin === 0)) {
        return true;
    } else if (condition !== 'equal' && parseFloat(iFini) <= parseFloat(datofini)
        && (iFfin === "" || iFfin === 0)) {
        return true;
    } else if (condition !== 'equal' && parseFloat(iFfin) >= parseFloat(datoffin)
        && (iFini === "" || iFini === 0)) {
        return true;
    } else if (condition !== 'equal' && parseFloat(iFini) <= parseFloat(datofini)
        && parseFloat(iFfin) >= parseFloat(datoffin)) {
        return true;
    }

    return false;
});

// Returns Epoch time for easy sorting.
function filterDate(str) {

    if (str === null) {
        str = "";
    }

    var time = str.replace(/-/g, "/");
    time = time.match(dateRegex);

    if (time !== null) {
        var m = +time[1], d = +time[2], y = +time[3];
        var date = new Date(y, m - 1, d);
        if (date.getFullYear() === y && date.getMonth() === m - 1) {
            return date.getTime();
        }
    }
    return str;
}

function isDate(str) {
    var time = str.replace(/-/g, "/");
    return dateRegex.test(time);
}

//--> date format detection
$.fn.dataTable.moment('MM-DD-YYYY');
$.fn.dataTable.moment('MM-D-YYYY');
$.fn.dataTable.moment('M-DD-YYYY');
$.fn.dataTable.moment('M-D-YYYY');

//--> sort the checkboxes
$.fn.dataTable.ext.order['dom-checkbox'] = function (settings, col) {
    return this.api().column(col, {order: 'index'}).nodes().map(function (td, i) {
        return $('.patientChecked', td).prop('checked') ? '1' : '0';
    });
};

$(document).ready(function () {

    //--> table init
    var drilldownTable = $('#drilldownTable').DataTable({

        // 1. disable sorting and searching on the first column
        // 2. make the tickler checkboxes sortable.
        "columnDefs": [
            {
                "searchable": false,
                "orderable": false,
                "targets": 0
            },
            {
                "orderDataType": "dom-checkbox",
                "targets": 1
            }
        ],

        // turns the first column of the table into an ordered list.
        "order": [[1, 'asc']]
    });

    // --> Number the Drilldown rows with static numbers.
    drilldownTable.on('order.dt search.dt', function () {
        drilldownTable.column(0, {search: 'applied', order: 'applied'}).nodes().each(function (cell, i) {
            cell.textContent = String(i + 1);
        });
    }).draw();


    //--> add a customizable user filter to the table.
    $("#drilldownTable_filter").empty().prepend(function () {

        var filterGroup = $('#datatableFilterGroup');

        // add the columns and move the html into the header of the DataTable
        var select = filterGroup.find('#datatableFilterColumnSelector');

        $('#drilldownTable thead th').each(function () {
            var id = this.id;
            if (id > 1) {
                var option = document.createElement('option');
                option.value = id;
                option.textContent = $(this).text();
                select.append(option);
            }
        });

        filterGroup.find('#datatableFilterColumnSelector').replaceWith(select);

        // execute a filter based on the parameters in the tool set.
        filterGroup.find('#datatableFilterExecuteButton').on('click', function () {
            drilldownTable.draw();
        });

        // reset all the search values and restore the table.
        filterGroup.find('#datatableFilterResetButton').on('click', function () {
            $('#datatableFilterConditionGreaterThan').val("").show();
            $('#datatableFilterConditionLessThan').val("");
            $('#datatableFilterColumnSelector').val('0');
            $('#datatableFilterConditionSelector').val('all');
            $('.andcondition').hide();
            drilldownTable.draw();
        });

        // bind a show hide event to the additional field for range filtering
        filterGroup.find('#datatableFilterConditionSelector').on('change', function () {
            if ($(this).val() === 'between') {
                $('#datatableFilterConditionGreaterThan').show();
                $('.andcondition').show();
            } else {
                $('.andcondition').hide();
            }

            if ($(this).val() === 'gt' || $(this).val() === 'equal') {
                $('#datatableFilterConditionGreaterThan').val($('#datatableFilterConditionLessThan').val());
                $('#datatableFilterConditionLessThan').val("")
                $('#datatableFilterConditionGreaterThan').show();
                $('.lessthancondition').hide();
            }

            if ($(this).val() === 'lt') {
                $('#datatableFilterConditionLessThan').val($('#datatableFilterConditionGreaterThan').val());
                $('#datatableFilterConditionGreaterThan').val("");
                $('#datatableFilterConditionGreaterThan').hide();
                $('.lessthancondition').show();
            }
        });

        return filterGroup.show();
    });

    // --> add the filters to the bottom footer.
    $("#drilldownTable tfoot th.filter").each(function (i) {
        var columnId = this.id;

        // exclude the first column.
        if (i > 0) {
            var select = $('<select class="form-select" ><option value="">All</option></select>')
                .appendTo($(this).empty())
                .on('change', function () {
                    drilldownTable.column(columnId).search($(this).val()).draw();
                });

            drilldownTable.column(columnId).data().unique().sort().each(function (d, j) {
                var option = document.createElement('option');
                option.value = d;
                option.textContent = d;
                select.append(option);
            });
        }

    });

    //--> Re-draw the dashboard.
    $(".backtoDashboardBtn").on('click', function (event) {
        event.preventDefault();
        var url = "/web/dashboard/display/DashboardDisplay";
        var data = new Object();
        data.dashboardId = (this.id).split("_")[1];
        data.method = (this.id).split("_")[0];

        sendData(url, data, "reload");
    });

    //--> Check all for actions
    $("#selectAllDrilldown").on('click', function (event) {
        event.preventDefault();
        $('.patientChecked').prop('checked', 'checked');
    });

    //--> Uncheck all for actions
    $("#selectNoneDrilldown").on('click', function (event) {
        event.preventDefault();
        $('.patientChecked').prop('checked', '');
    });

    function getSelectedPatientIds() {
        var patientIds = [];

        $("input:checkbox.patientChecked").each(function () {
            if (this.checked) {
                patientIds.push(this.id);
            }
        });

        return patientIds;
    }

    //--> Assign Tickler to all checked items - returns the tickler dialog.
    $("#assignTicklerChecked").on('click', function (event) {

        event.preventDefault();
        var demographics = getSelectedPatientIds();
        var param = "demographics=" + demographics;

        if (demographics.length > 0) {
            sendData("/web/dashboard/display/AssignTickler", param, "modal");
        } else {
            alert("Select at least 1 row to assign a Tickler.");
        }
    });

    //--> Execute the tickler assignment - save
    $("#saveTicklerBtn").on('click', function (event) {
        event.preventDefault();
        if (checkFields()) {
            // DOMPurify removes name="method" to prevent form-property clobbering.
            // Supply this fixed operation explicitly without relaxing sanitization.
            var data = $("#ticklerAddForm").serializeArray().filter(function (field) {
                return field.name !== 'method';
            });
            data.push({name: 'method', value: 'saveTickler'});
            sendData("/web/dashboard/display/AssignTickler", data, "close", this);
        }
    });


    $("#addToDiseaseRegistryChecked").on('click', function (event) {
        event.preventDefault();

        var patientIds = getSelectedPatientIds();

        if (patientIds.length < 1) {
            alert("At least one patient must be selected to perform this action.");
            return;
        }

        var url = $(this).attr("href");

        $.ajax({
            url: url,
            dataType: "json",
            success: function (data) {
                bootstrap.Modal.getOrCreateInstance(document.getElementById('modalConfirmAddToDiseaseRegistry')).show();
                $("#icd9code").text(data.icd9code);
                $("#icd9description").text(data.description);
            }
        });
    });

    /** Preserve each confirmation form's route, operation and CSRF fields in one POST path. */
    function submitSelectedPatientForm(button, event, modalId) {
        event.preventDefault();
        if (button.disabled) return;
        var patientIds = getSelectedPatientIds();
        if (patientIds.length < 1) {
            alert("At least one patient must be selected to perform this action.");
            return;
        }
        var form = $(button).closest('form');
        var data = form.serializeArray();
        data.push({name: 'patientIds', value: patientIds.join(',')});
        button.disabled = true;
        $.ajax({
            type: 'POST',
            url: form.attr('action'),
            data: data,
            success: function () {
                bootstrap.Modal.getOrCreateInstance(document.getElementById(modalId)).hide();
            },
            error: function () {
                alert('The selected-patient action could not be confirmed. Review the patients before trying again.');
            },
            complete: function () {
                button.disabled = false;
            }
        });
    }

    $("#confirmAddToDiseaseRegistry").on('click', function (event) {
        submitSelectedPatientForm(this, event, "modalConfirmAddToDiseaseRegistry");
    });

    $("#excludePatientsChecked").on('click', function (event) {
        event.preventDefault();

        var patientIds = getSelectedPatientIds();

        if (patientIds.length < 1) {
            alert("At least one patient must be selected to perform this action.");
            return;
        }

        bootstrap.Modal.getOrCreateInstance(document.getElementById('modalConfirmPatientExclusion')).show();
    });

    $("#confirmPatientExclusion").on('click', function (event) {
        submitSelectedPatientForm(this, event, "modalConfirmPatientExclusion");
    });

    $("#patientStatusUpdateChecked").on('click', function (event) {
        event.preventDefault();

        var patientIds = getSelectedPatientIds();

        if (patientIds.length < 1) {
            alert("At least one patient must be selected to perform this action.");
            return;
        }

        bootstrap.Modal.getOrCreateInstance(document.getElementById('modalConfirmPatientStatusUpdate')).show();

    });

    $("#confirmPatientStatusUpdate").on('click', function (event) {
        submitSelectedPatientForm(this, event, "modalConfirmPatientStatusUpdate");
    });

})
