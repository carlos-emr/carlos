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
    Purpose:
        Administration > Jobs Management: list, create, edit, enable / disable, cancel and
        schedule OscarJob rows through the /ws/rs/jobs REST service.

    Features:
        Job rows are built with DOM APIs (the stored name is text, never markup). The schedule
        dialog restores a stored six-field cron exactly or, when it cannot, explains why and
        disables Save so the stored schedule is never rewritten.

    Parameters:
        None (all data is loaded from /ws/rs/jobs; _admin r to view).

    @since CARLOS heritage page; documented 2026-10 for issue #4131
--%>
<!DOCTYPE html>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>



<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.jobs.OscarJobExecutingManager" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:message key="admin.jobs.scheduleNotEditable" var="msgScheduleNotEditable"/>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Provider provider = loggedInInfo.getLoggedInProvider();
%>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="admin.jobs.title"/></title>

        <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.structure-1.14.2.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.theme-1.14.2.min.css">

        <script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
        <script src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>


        <%
            java.util.Map<Integer, java.util.concurrent.ScheduledFuture<Object>> futures = OscarJobExecutingManager.getFutures();
        %>
        <style>
            .red {
                color: red
            }

        </style>

        <fmt:message key="admin.jobs.saveError" var="jobsSaveErrorLabel"/>
        <fmt:message key="admin.jobs.saveUnconfirmed" var="jobsSaveUnconfirmedLabel"/>
        <fmt:message key="admin.jobs.loadError" var="jobsLoadErrorLabel"/>
        <fmt:message key="admin.jobs.savedValuesUnavailable" var="jobsSavedValuesUnavailableLabel"/>
        <fmt:message key="admin.jobs.scheduleActivationFailed" var="jobsScheduleActivationFailedLabel"/>
        <script>
            const jobsSaveErrorLabel = "${carlos:forJavaScript(jobsSaveErrorLabel)}";
            const jobsSaveUnconfirmedLabel = "${carlos:forJavaScript(jobsSaveUnconfirmedLabel)}";
            const jobsLoadErrorLabel = "${carlos:forJavaScript(jobsLoadErrorLabel)}";
            const jobsSavedValuesUnavailableLabel = "${carlos:forJavaScript(jobsSavedValuesUnavailableLabel)}";
            const jobsScheduleActivationFailedLabel = "${carlos:forJavaScript(jobsScheduleActivationFailedLabel)}";
            const jobsRequestTimeout = 20000;
            const jobSaveState = {pending: false, createUnconfirmed: false};
            const scheduleSaveState = {pending: false};
            let jobEditorGeneration = 0;
            let jobEditorLoading = false;
            let jobEditorLoadFailed = false;

            function updateJobSaveButton() {
                $('#new-job').dialog('widget').find('.jobs-save').prop('disabled', jobSaveState.pending
                    || jobEditorLoading || jobEditorLoadFailed || Boolean(jobSaveState.readback)
                    || (jobSaveState.createUnconfirmed && $('#jobId').val() === '0'));
            }

            function clearJobSaveFeedback(dialog, state) {
                state.readback = null;
                dialog.find('.save-error, .saved-job-values').text('');
                dialog.find('.check-saved-job').off('click').prop({hidden: true, disabled: false});
            }

            function showJobReadback(dialog, state, id, schedule) {
                const readback = {id: id};
                state.readback = readback;
                const button = dialog.find('.check-saved-job');
                const output = dialog.find('.saved-job-values');
                button.prop('hidden', false).on('click', function () {
                    button.prop('disabled', true);
                    function unavailable() {
                        if (state.readback !== readback) return;
                        output.text(jobsSavedValuesUnavailableLabel);
                        button.prop('disabled', false);
                    }
                    $.ajax({
                        url: '<%= request.getContextPath() %>/ws/rs/jobs/job/' + id,
                        dataType: 'json',
                        timeout: jobsRequestTimeout
                    }).done(function (data) {
                        if (state.readback !== readback) return;
                        const saved = firstJob(data);
                        if (!saved || String(saved.id) !== id) {
                            unavailable();
                            return;
                        }
                        if (schedule) {
                            output.text(saved.cronExpression || dialog.data('not-available'));
                        } else {
                            const type = $('#jobType option').filter(function () {
                                return this.value === String(saved.oscarJobTypeId);
                            }).text() || saved.oscarJobTypeId;
                            const provider = $('#jobProvider option').filter(function () {
                                return this.value === String(saved.providerNo);
                            }).text() || saved.providerNo;
                            const fields = {jobName: saved.name, jobType: type,
                                jobDescription: saved.description, jobProvider: provider};
                            output.text(Object.entries(fields).map(function ([field, value]) {
                                return $('label[for="' + field + '"]').text().replace(/[:*]+$/, '') + ': ' + (value || '');
                            }).concat(saved.enabled ? dialog.data('enabled') : dialog.data('disabled')).join('\n'));
                        }
                        state.readback = null;
                        button.off('click').prop({hidden: true, disabled: false});
                        dialog.find('.save-error').text('');
                        updateJobSaveButton();
                        setScheduleEditable(scheduleEditable);
                    }).fail(unavailable);
                });
                updateJobSaveButton();
                setScheduleEditable(scheduleEditable);
            }

            function saveJobDialog(dialog, form, endpoint, expectedId, state) {
                if (state.pending || state.readback) return;
                if (expectedId === '0' && state.createUnconfirmed) {
                    dialog.find('.save-error').text(jobsSaveUnconfirmedLabel);
                    return;
                }
                clearJobSaveFeedback(dialog, state);
                const schedule = state === scheduleSaveState;
                const serialized = form.serialize();
                const controls = form.find(':input').add(dialog.dialog('widget').find('button'));
                const disabled = controls.map(function () { return this.disabled; }).get();
                state.pending = true;
                controls.prop('disabled', true);
                dialog.find('.save-error').text('');
                function finish() {
                    state.pending = false;
                    controls.each(function (index) { this.disabled = disabled[index]; });
                    updateJobSaveButton();
                }
                function refused() {
                    if (expectedId === '0') state.createUnconfirmed = true;
                    finish();
                    dialog.find('.save-error').text(expectedId === '0' ? jobsSaveUnconfirmedLabel : jobsSaveErrorLabel);
                    if (expectedId !== '0') showJobReadback(dialog, state, expectedId, schedule);
                }
                try {
                    $.ajax({url: endpoint, type: 'POST', data: serialized, dataType: 'json', timeout: jobsRequestTimeout})
                        .done(function (data) {
                            const jobs = data && data.jobs;
                            const saved = Array.isArray(jobs) ? (jobs.length === 1 ? jobs[0] : null) : jobs;
                            if (!saved || !/^[1-9][0-9]*$/.test(String(saved.id))
                                    || (expectedId !== '0' && String(saved.id) !== expectedId)
                                    || (schedule && !['ACTIVE', 'DISABLED', 'FAILED'].includes(data.scheduleActivation))) {
                                refused();
                                return;
                            }
                            finish();
                            if (schedule && data.scheduleActivation === 'FAILED') {
                                dialog.find('.save-error').text(jobsScheduleActivationFailedLabel);
                                return;
                            }
                            dialog.dialog('close');
                            listJobs();
                        }).fail(refused);
                } catch (error) {
                    refused();
                }
            }

            function cancelJob(jobId) {
                jQuery.post("<%= request.getContextPath() %>/ws/rs/jobs/cancelJob", {jobId: jobId},
                    function (xml) {
                        listJobs();
                    }, "json");
            }

            function updateJobStatus(jobId, status) {
                var action = status ? "enableJob" : "disableJob";
                jQuery.post("<%= request.getContextPath() %>/ws/rs/jobs/" + action, {jobId: jobId},
                    function (xml) {
                        listJobs();
                    }, "json");
            }

            // Each schedule request gets a sequence number; only the latest one may fill the
            // dialog. Otherwise clicking job A then job B could show A's schedule under B's id,
            // and Save would write A's schedule onto B.
            var scheduleRequestSeq = 0;
            // False while the dialog shows a stored schedule it cannot represent exactly; Save is
            // then refused so opening and saving can never rewrite the job's real schedule.
            var scheduleEditable = true;

            function resetScheduleDialog() {
                clearJobSaveFeedback($('#scheduleDialog'), scheduleSaveState);
                ['minute', 'hour', 'day', 'month', 'weekday'].forEach(function (type) {
                    $('input:radio[name=' + type + '_chooser]')[0].checked = true;
                    $('#' + type + ' option:selected').prop('selected', false);
                    $('#' + type).attr('disabled', 'disabled');
                });
                $('#scheduleDialog .validateTips').text('');
                setScheduleEditable(true);
            }

            function setScheduleEditable(editable) {
                scheduleEditable = editable;
                $('.ui-dialog:has(#scheduleDialog) .ui-dialog-buttonpane button.btn-primary')
                    .prop('disabled', !editable || scheduleSaveState.pending || Boolean(scheduleSaveState.readback));
            }

            function scheduleJob(jobId) {
                if (scheduleSaveState.pending) return;
                var request = ++scheduleRequestSeq;
                //do we already have an existing cronExpression
                jQuery.getJSON("<%= request.getContextPath() %>/ws/rs/jobs/job/" + jobId, {},
                    function (xml) {
                        if (request !== scheduleRequestSeq) {
                            return;
                        }
                        // OscarJobResponse serialises "jobs" as an array (one element for
                        // job/{id}); reading cronExpression off the array itself was always
                        // undefined, so the dialog opened on "every minute" and Save then
                        // overwrote the stored schedule with 0 * * * * *.
                        var job = firstJob(xml);
                        if (!job) {
                            return;
                        }
                        resetScheduleDialog();
                        // Bound to the job whose schedule was loaded, not to the click.
                        $('#scheduleJobId').val(job.id);
                        var existingCron = job.cronExpression;
                        if (existingCron != undefined && existingCron.length > 0) {
                            var parsed = parseCronExpression(existingCron);
                            if (parsed) {
                                ['minute', 'hour', 'day', 'month', 'weekday'].forEach(function (type) {
                                    setCronPart(parsed[type], type);
                                });
                            } else {
                                setScheduleEditable(false);
                                $('#scheduleDialog .validateTips')
                                    .text('${carlos:forJavaScript(msgScheduleNotEditable)} ' + existingCron);
                            }
                        }
                        $('#scheduleDialog').dialog('open');
                        setScheduleEditable(scheduleEditable);
                    });
            }

            /** The single job in a job/{id} response, whether "jobs" arrives as an array or an object. */
            function firstJob(xml) {
                if (!xml || !xml.jobs) {
                    return null;
                }
                return Array.isArray(xml.jobs) ? xml.jobs[0] : xml.jobs;
            }

            // Parses a stored six-field cron expression (seconds minute hour day month weekday,
            // the only form saveCrontabExpression writes) into what the dialog's pickers show.
            // Each field becomes null ("every") or the exact list of option values it means.
            // Returns null when ANY field cannot be shown exactly, for example a five-field Unix
            // expression, seconds other than 0, names (MON-FRI, JAN), or Spring's W, L and #
            // forms, so the caller can refuse to save instead of silently rewriting the schedule.
            // Ranges (1-5) and steps (*/15, 0-30/10) are expanded; weekday 7 is Sunday (0).
            function parseCronExpression(expression) {
                var fields = String(expression).trim().split(/\s+/);
                if (fields.length !== 6 || fields[0] !== '0') {
                    return null;
                }
                var parsed = {};
                var types = ['minute', 'hour', 'day', 'month', 'weekday'];
                for (var i = 0; i < types.length; i++) {
                    var values = expandCronPart(fields[i + 1], types[i]);
                    if (values === undefined) {
                        return null;
                    }
                    parsed[types[i]] = values;
                }
                return parsed;
            }

            // One field: null for "every" (* or ?), an array of option values, or undefined
            // when the field cannot be represented exactly by the select.
            function expandCronPart(value, type) {
                if (value == '*' || value == '?') {
                    return null;
                }
                var options = $('#' + type + ' option').map(function () {
                    return parseInt(this.value, 10);
                }).get();
                var min = Math.min.apply(null, options);
                var max = Math.max.apply(null, options);
                // Spring accepts 0-7 for the weekday, 0 and 7 both being Sunday.
                var limit = (type == 'weekday') ? 7 : max;
                var number = function (text) {
                    return /^\d+$/.test(text) ? parseInt(text, 10) : NaN;
                };
                var seen = {};
                var items = value.split(',');
                for (var i = 0; i < items.length; i++) {
                    var match = /^(\*|\d+(?:-\d+)?)(?:\/(\d+))?$/.exec(items[i]);
                    if (!match) {
                        return undefined;
                    }
                    var step = match[2] === undefined ? 1 : number(match[2]);
                    var from;
                    var to;
                    if (match[1] == '*') {
                        from = min;
                        to = max;
                    } else if (match[1].indexOf('-') > 0) {
                        from = number(match[1].substring(0, match[1].indexOf('-')));
                        to = number(match[1].substring(match[1].indexOf('-') + 1));
                    } else {
                        from = number(match[1]);
                        to = match[2] === undefined ? from : max;
                    }
                    if (isNaN(from) || isNaN(to) || isNaN(step) || step < 1 || from < min || to > limit || from > to) {
                        return undefined;
                    }
                    for (var v = from; v <= to; v += step) {
                        var option = (type == 'weekday' && v == 7) ? 0 : v;
                        if (option < min || option > max) {
                            return undefined;
                        }
                        seen[option] = true;
                    }
                }
                return Object.keys(seen).map(Number).sort(function (a, b) { return a - b; }).map(String);
            }

            function setCronPart(values, type) {
                if (values === null) {
                    $('input:radio[name=' + type + '_chooser]')[0].checked = true;
                } else {
                    $('input:radio[name=' + type + '_chooser]')[1].checked = true;
                    $("#" + type).removeAttr('disabled');
                    $('#' + type).val(values);
                }
            }

            function editJob(jobId) {
                if (jobSaveState.pending) return;
                clearJobSaveFeedback($('#new-job'), jobSaveState);
                const generation = ++jobEditorGeneration;
                jobEditorLoading = true;
                jobEditorLoadFailed = false;
                $('#jobId').val(String(jobId));
                $('#jobForm :input').prop('disabled', true);
                $('#new-job .save-error').text('');
                $('#new-job').dialog('open');
                updateJobSaveButton();
                function failedLoad() {
                    if (generation !== jobEditorGeneration) return;
                    jobEditorLoading = false;
                    jobEditorLoadFailed = true;
                    $('#new-job .save-error').text(jobsLoadErrorLabel);
                    updateJobSaveButton();
                }
                jQuery.getJSON("<%= request.getContextPath() %>/ws/rs/jobs/job/" + jobId, {}, function (xml) {
                    if (generation !== jobEditorGeneration) return;
                    var job = firstJob(xml);
                    if (!job || String(job.id) !== String(jobId)) { failedLoad(); return; }
                    $('#jobName').val(job.name);
                    $('#jobType').val(job.oscarJobTypeId);
                    $('#jobDescription').val(job.description);
                    $('#jobEnabled').prop('checked', job.enabled);
                    $('#jobProvider').val(job.providerNo);
                    jobEditorLoading = false;
                    $('#jobForm :input').prop('disabled', false);
                    updateJobSaveButton();
                }).fail(failedLoad);
            }

            function addNewJob() {
                if (jobSaveState.pending) return;
                clearJobSaveFeedback($('#new-job'), jobSaveState);
                ++jobEditorGeneration;
                jobEditorLoading = false;
                jobEditorLoadFailed = false;
                $('#jobForm :input').prop('disabled', false);
                $('#new-job .save-error').text(jobSaveState.createUnconfirmed ? jobsSaveUnconfirmedLabel : '');
                $('#jobName').val('');
                $('#jobType').val('');
                $('#jobDescription').val('');
                $('#jobEnabled').prop('checked', true);
                $('#jobProvider').val('<%=provider.getProviderNo()%>');
                $('#jobId').val('0');
                $('#new-job').dialog('open');
                updateJobSaveButton();
            }

            function clearJobs() {
                $("#jobTable tbody tr").remove();
            }

            /** A control link: href="#" keeps it focusable, and the handler cancels the navigation. */
            function jobLink(label, handler) {
                return $('<a href="#"></a>').text(label).on('click', function (e) {
                    e.preventDefault();
                    handler();
                });
            }

            /**
             * Builds one job row with DOM APIs. The stored job name is set with text(), never
             * concatenated into HTML, so a name containing markup is shown literally. The
             * numeric id is coerced before it reaches a handler.
             */
            function jobRow(job) {
                var jobId = parseInt(job.id, 10);
                var scheduled = job.cronExpression != undefined && job.cronExpression != null;
                var row = $('<tr></tr>');

                var calendar = $('<i class="fa-solid fa-calendar"></i>').addClass(scheduled ? 'blue' : 'red');
                row.append($('<td></td>').append(jobLink('', function () { scheduleJob(jobId); })
                    .attr('aria-label', '<fmt:message key="admin.jobs.scheduleJob"/>').append(calendar)));
                row.append($('<td></td>').append($('<u></u>').append(jobLink(job.name, function () { editJob(jobId); }))));
                row.append($('<td></td>').append(jobLink('<fmt:message key="admin.jobs.cancel"/>', function () { cancelJob(jobId); })));

                var status = $('<td></td>');
                if (job.enabled == true) {
                    status.append(document.createTextNode('<fmt:message key="admin.jobs.enabledStatus"/> ('))
                        .append(jobLink('<fmt:message key="admin.jobs.disable"/>', function () { updateJobStatus(jobId, false); }))
                        .append(document.createTextNode(')'));
                } else {
                    status.append($('<span class="red"></span>').text('<fmt:message key="admin.jobs.disabledStatus"/>'))
                        .append(document.createTextNode(' ('))
                        .append(jobLink('<fmt:message key="admin.jobs.enable"/>', function () { updateJobStatus(jobId, true); }))
                        .append(document.createTextNode(')'));
                }
                row.append(status);
                row.append($('<td></td>').text('<fmt:message key="admin.jobs.notAvailable"/>'));
                row.append($('<td></td>').text((job.nextPlannedExecutionDate == null)
                    ? '<fmt:message key="admin.jobs.notAvailable"/>'
                    : String(new Date(job.nextPlannedExecutionDate))));
                return row;
            }

            function listJobs() {
                jQuery.getJSON("<%= request.getContextPath() %>/ws/rs/jobs/all", {},
                    function (xml) {
                        clearJobs();

                        if (xml.jobs) {
                            var arr = new Array();
                            if (xml.jobs instanceof Array) {
                                arr = xml.jobs;
                            } else {
                                arr[0] = xml.jobs;
                            }

                            for (var i = 0; i < arr.length; i++) {
                                jQuery('#jobTable tbody').append(jobRow(arr[i]));
                            }
                        } else {
                            alert('<fmt:message key="admin.jobs.errorRetrievingJobs"/>');
                        }
                    });
            }

            function getJobTypes() {
                jQuery.getJSON("<%= request.getContextPath() %>/ws/rs/jobs/types/all", {async: false},
                    function (xml) {
                        if (xml.types) {
                            var arr = new Array();
                            if (xml.types instanceof Array) {
                                arr = xml.types;
                            } else {
                                arr[0] = xml.types;
                            }

                            for (var i = 0; i < arr.length; i++) {
                                $('#jobType').append($('<option>', {
                                    value: arr[i].id,
                                    text: arr[i].name + ((arr[i].currentlyValid == true) ? '' : ' (<fmt:message key="admin.jobs.notAvailable"/>)')
                                }));
                            }

                        }

                    });
            }

            function getProviders() {
                jQuery.getJSON("<%= request.getContextPath() %>/ws/rs/providerService/providers_json", {async: false},
                    function (xml) {
                        if (xml.content instanceof Array) {
                            for (var i = 0; i < xml.content.length; i++) {
                                $('#jobProvider').append($('<option>', {
                                    value: xml.content[i].providerNo,
                                    text: xml.content[i].lastName + ',' + xml.content[i].firstName
                                }));
                            }
                        }
                    });
            }

            $(document).ready(function () {
                getJobTypes();
                getProviders();
                listJobs();

                $("#new-job").dialog({
                    autoOpen: false,
                    height: 560,
                    width: 620,
                    modal: true,
                    buttons: {
                        "<fmt:message key="admin.jobs.saveJob"/>": {
                            class: "btn btn-primary jobs-save", text: "<fmt:message key="admin.jobs.saveJob"/>", click: function () {
                                if (!jobEditorLoading && !jobEditorLoadFailed && validateSaveJob()) {
                                    saveJobDialog($(this), $('#jobForm'), '<%= request.getContextPath() %>/ws/rs/jobs/saveJob',
                                        $('#jobId').val(), jobSaveState);
                                }

                            }
                        },
                        "<fmt:message key="admin.jobs.cancel"/>": {
                            class: "btn", text: "<fmt:message key="admin.jobs.cancel"/>", click: function () {
                                $(this).dialog("close");
                            }
                        }
                    },
                    beforeClose: function () { return !jobSaveState.pending; },
                    close: function () { ++jobEditorGeneration; }
                });

                $("#scheduleDialog").dialog({
                    autoOpen: false,
                    height: 400,
                    width: 780,
                    modal: true,
                    buttons: {
                        "<fmt:message key="admin.jobs.save"/>": {
                            class: "btn btn-primary", text: "<fmt:message key="admin.jobs.save"/>", click: function () {
                                if (!scheduleEditable) {
                                    return;
                                }
                                saveJobDialog($(this), $('#crontab-form'), '<%= request.getContextPath() %>/ws/rs/jobs/saveCrontabExpression',
                                    $('#scheduleJobId').val(), scheduleSaveState);

                            }
                        },
                        "<fmt:message key="admin.jobs.cancel"/>": {
                            class: "btn", text: "<fmt:message key="admin.jobs.cancel"/>", click: function () {
                                $(this).dialog("close");
                            }
                        }
                    },
                    beforeClose: function () { return !scheduleSaveState.pending; },
                    close: function () { ++scheduleRequestSeq; }
                });

                $(":radio").on('change', function () {
                    var chooser = $(this).attr('name');

                    var checked = $("input:radio[name=" + chooser + "]:checked").val();
                    var rootName = chooser.substring(0, chooser.indexOf("_"));

                    if (checked == 0) {
                        $("#" + rootName).attr('disabled', 'disabled');
                    } else {
                        $("#" + rootName).removeAttr('disabled');
                    }
                });
            });

            function validateSaveJob() {
                var errorMsg = '';

                if ($('#jobName').val().length == 0) {
                    errorMsg += '<fmt:message key="admin.jobs.msgProvideName"/>\n';
                }
                if ($('#jobType').val().length == 0 || $('#jobType').val() == '0') {
                    errorMsg += '<fmt:message key="admin.jobs.msgProvideType"/>\n';
                }
                if ($('#jobProvider').val().length == 0 || $('#jobProvider').val() == '0') {
                    errorMsg += '<fmt:message key="admin.jobs.msgProvideProvider"/>\n';
                }

                if (errorMsg.length > 0) {
                    alert(errorMsg);
                    return false;
                }
                return true;

            }
        </script>
    </head>

    <body class="BodyStyle">
    <h4><fmt:message key="admin.jobs.manageJobs"/></h4>
    <table id="jobTable" class="table table-bordered table-striped table-hover table-sm">
        <thead>
        <tr>
            <th></th>
            <th><fmt:message key="admin.jobs.name"/></th>
            <th><fmt:message key="admin.jobs.executionStatus"/></th>
            <th><fmt:message key="admin.jobs.jobStatus"/></th>
            <th><fmt:message key="admin.jobs.lastRun"/></th>
            <th><fmt:message key="admin.jobs.nextPlannedRun"/></th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
    <input type="button" class="btn btn-primary" value="<fmt:message key="admin.jobs.addNew"/>" onClick="addNewJob()"/>


    <div id="new-job" title="<fmt:message key="admin.jobs.editorTitle"/>"
         data-enabled="<fmt:message key='admin.jobs.enabledStatus'/>"
         data-disabled="<fmt:message key='admin.jobs.disabledStatus'/>">
        <p class="validateTips"></p>
        <p class="save-error text-danger" role="alert" aria-live="polite"></p>
        <button type="button" class="btn btn-secondary check-saved-job" hidden><fmt:message key="admin.jobs.checkSavedJob"/></button>
        <pre class="saved-job-values text-wrap" role="status"></pre>

        <form id="jobForm">
            <input type="hidden" name="job.id" id="jobId" value="0"/>
            <fieldset>
                <div class="mb-3">
                    <label class="form-label" for="jobName"><fmt:message key="admin.jobs.name"/>:*</label>
                    <div>
                        <input type="text" name="job.name" id="jobName" value=""/>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="jobType"><fmt:message key="admin.jobs.type"/>:*</label>
                    <div>
                        <select name="job.oscarJobTypeId" id="jobType">
                            <option value="">&nbsp;</option>
                        </select>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="jobDescription"><fmt:message key="admin.jobs.description"/>:</label>
                    <div>
                        <textarea rows="5" name="job.description" id="jobDescription"></textarea>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="jobEnabled"><fmt:message key="admin.jobs.enabled"/> <input type="checkbox" name="job.enabled"
                                                                                  id="jobEnabled"/></label>
                    <div>

                    </div>
                </div>

                <div class="mb-3">
                    <label class="form-label" for="jobProvider"><fmt:message key="admin.jobs.runAsProvider"/>:</label>
                    <div>
                        <select name="job.provider" id="jobProvider">
                            <option value="">&nbsp;</option>

                        </select>
                    </div>
                </div>

            </fieldset>
        </form>
    </div>


    <div id="scheduleDialog" title="<fmt:message key="admin.jobs.scheduleJob"/>"
         data-not-available="<fmt:message key='admin.jobs.notAvailable'/>">
        <p class="validateTips"></p>
        <p class="save-error text-danger" role="alert" aria-live="polite"></p>
        <button type="button" class="btn btn-secondary check-saved-job" hidden><fmt:message key="admin.jobs.checkSavedJob"/></button>
        <pre class="saved-job-values text-wrap" role="status"></pre>

        <form id="crontab-form">
            <input type="hidden" name="scheduleJobId" id="scheduleJobId" value="0"/>

            <table>
                <tr>
                    <td>
                        <h4><fmt:message key="admin.jobs.minute"/></h4>

                        <fmt:message key="admin.jobs.everyMinute"/>
                        <input type="radio" name="minute_chooser" id="minute_chooser_every" value="0"
                               checked="checked"/><br/>
                        <fmt:message key="admin.jobs.choose"/>
                        <input type="radio" name="minute_chooser" id="minute_chooser_choose" value="1"/><br/>

                        <select name="minute" id="minute" multiple="multiple" disabled="disabled" style="width:120px">
                            <%
                                // Cron minutes are 0-59 inclusive.
                                for (int x = 0; x <= 59; x++) {
                            %>
                            <option value="<%=x%>"><%=x%>
                            </option>
                            <% } %>
                        </select>
                    </td>
                    <td>
                        <h4><fmt:message key="admin.jobs.hour"/></h4>
                        <fmt:message key="admin.jobs.everyHour"/>
                        <input type="radio" name="hour_chooser" id="hour_chooser_every" value="0"
                               checked="checked"/><br/>

                        <fmt:message key="admin.jobs.choose"/>
                        <input type="radio" name="hour_chooser" id="hour_chooser_choose" value="1"/><br/>

                        <select name="hour" id="hour" multiple="multiple" disabled="disabled" style="width:120px">
                            <option value="0"><fmt:message key="admin.jobs.midnight"/></option>
                            <option value="1"><fmt:message key="admin.jobs.hour1am"/></option>
                            <option value="2"><fmt:message key="admin.jobs.hour2am"/></option>
                            <option value="3"><fmt:message key="admin.jobs.hour3am"/></option>
                            <option value="4"><fmt:message key="admin.jobs.hour4am"/></option>
                            <option value="5"><fmt:message key="admin.jobs.hour5am"/></option>
                            <option value="6"><fmt:message key="admin.jobs.hour6am"/></option>
                            <option value="7"><fmt:message key="admin.jobs.hour7am"/></option>
                            <option value="8"><fmt:message key="admin.jobs.hour8am"/></option>
                            <option value="9"><fmt:message key="admin.jobs.hour9am"/></option>
                            <option value="10"><fmt:message key="admin.jobs.hour10am"/></option>
                            <option value="11"><fmt:message key="admin.jobs.hour11am"/></option>
                            <option value="12"><fmt:message key="admin.jobs.noon"/></option>
                            <option value="13"><fmt:message key="admin.jobs.hour1pm"/></option>
                            <option value="14"><fmt:message key="admin.jobs.hour2pm"/></option>
                            <option value="15"><fmt:message key="admin.jobs.hour3pm"/></option>
                            <option value="16"><fmt:message key="admin.jobs.hour4pm"/></option>
                            <option value="17"><fmt:message key="admin.jobs.hour5pm"/></option>
                            <option value="18"><fmt:message key="admin.jobs.hour6pm"/></option>
                            <option value="19"><fmt:message key="admin.jobs.hour7pm"/></option>
                            <option value="20"><fmt:message key="admin.jobs.hour8pm"/></option>
                            <option value="21"><fmt:message key="admin.jobs.hour9pm"/></option>
                            <option value="22"><fmt:message key="admin.jobs.hour10pm"/></option>
                            <option value="23"><fmt:message key="admin.jobs.hour11pm"/></option>
                        </select>
                    </td>

                    <td>
                        <h4><fmt:message key="admin.jobs.day"/></h4>
                        <fmt:message key="admin.jobs.everyDay"/>
                        <input type="radio" name="day_chooser" id="day_chooser_every" value="0" checked="checked"/><br/>

                        <fmt:message key="admin.jobs.choose"/>
                        <input type="radio" name="day_chooser" id="day_chooser_choose" value="1"/><br/>

                        <select name="day" id="day" multiple="multiple" disabled="disabled" style="width:120px">
                            <%
                                // Cron days of the month are 1-31 inclusive.
                                for (int x = 1; x <= 31; x++) {
                            %>
                            <option value="<%=x%>"><%=x%>
                            </option>
                            <% } %>
                        </select>
                    </td>
                    <td>
                        <h4><fmt:message key="admin.jobs.month"/></h4>
                        <fmt:message key="admin.jobs.everyMonth"/>
                        <input type="radio" name="month_chooser" id="month_chooser_every" value="0"
                               checked="checked"/><br/>

                        <fmt:message key="admin.jobs.choose"/>
                        <input type="radio" name="month_chooser" id="month_chooser_choose" value="1"/><br/>

                        <select name="month" id="month" multiple="multiple" disabled="disabled" style="width:120px">
                            <option value="1"><fmt:message key="admin.jobs.january"/></option>
                            <option value="2"><fmt:message key="admin.jobs.february"/></option>
                            <option value="3"><fmt:message key="admin.jobs.march"/></option>
                            <option value="4"><fmt:message key="admin.jobs.april"/></option>
                            <option value="5"><fmt:message key="admin.jobs.may"/></option>
                            <option value="6"><fmt:message key="admin.jobs.june"/></option>
                            <option value="7"><fmt:message key="admin.jobs.july"/></option>
                            <option value="8"><fmt:message key="admin.jobs.august"/></option>
                            <option value="9"><fmt:message key="admin.jobs.september"/></option>
                            <option value="10"><fmt:message key="admin.jobs.october"/></option>
                            <option value="11"><fmt:message key="admin.jobs.november"/></option>
                            <option value="12"><fmt:message key="admin.jobs.december"/></option>
                        </select>
                    </td>
                    <td>
                        <h4><fmt:message key="admin.jobs.weekday"/></h4>
                        <fmt:message key="admin.jobs.everyWeekday"/>
                        <input type="radio" name="weekday_chooser" id="weekday_chooser_every" value="0"
                               checked="checked"/><br/>

                        <fmt:message key="admin.jobs.choose"/>
                        <input type="radio" name="weekday_chooser" id="weekday_chooser_choose" value="1"/><br/>

                        <select name="weekday" id="weekday" multiple="multiple" disabled="disabled" style="width:120px">
                            <option value="0"><fmt:message key="admin.jobs.sunday"/></option>
                            <option value="1"><fmt:message key="admin.jobs.monday"/></option>
                            <option value="2"><fmt:message key="admin.jobs.tuesday"/></option>
                            <option value="3"><fmt:message key="admin.jobs.wednesday"/></option>
                            <option value="4"><fmt:message key="admin.jobs.thursday"/></option>
                            <option value="5"><fmt:message key="admin.jobs.friday"/></option>
                            <option value="6"><fmt:message key="admin.jobs.saturday"/></option>
                        </select>
                    </td>
                </tr>
            </table>
            <!--
            <br />
            Result Crontab Line:<br />
            <input type="text" name="cron" id="cron" size="100">
            -->
        </form>
    </div>


    </body>
</html>
