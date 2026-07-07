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


<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.jobs.OscarJobExecutingManager" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

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

        <script>
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

            function scheduleJob(jobId) {
                $('#scheduleJobId').val(jobId);

                $('input:radio[name=minute_chooser]')[0].checked = true;
                $('input:radio[name=hour_chooser]')[0].checked = true;
                $('input:radio[name=day_chooser]')[0].checked = true;
                $('input:radio[name=month_chooser]')[0].checked = true;
                $('input:radio[name=weekday_chooser]')[0].checked = true;

                $("#minute option:selected").removeAttr("selected");
                $("#hour option:selected").removeAttr("selected");
                $("#day option:selected").removeAttr("selected");
                $("#month option:selected").removeAttr("selected");
                $("#weekday option:selected").removeAttr("selected");

                $("#minute").attr('disabled', 'disabled');
                $("#hour").attr('disabled', 'disabled');
                $("#day").attr('disabled', 'disabled');
                $("#month").attr('disabled', 'disabled');
                $("#weekday").attr('disabled', 'disabled');

                //do we already have an existing cronExpression
                jQuery.getJSON("<%= request.getContextPath() %>/ws/rs/jobs/job/" + jobId, {async: false},
                    function (xml) {
                        var existingCron = xml.jobs.cronExpression;
                        if (existingCron != undefined && existingCron.length > 0) {
                            var parts = existingCron.split(' ');
                            //ignore seconds parts[0]
                            setCronPart(parts[1], 'minute');
                            setCronPart(parts[2], 'hour');
                            setCronPart(parts[3], 'day');
                            setCronPart(parts[4], 'month');
                            setCronPart(parts[5], 'weekday');

                            $('#scheduleDialog').dialog('open');
                        } else {
                            $('#scheduleDialog').dialog('open');
                        }
                    });
            }

            function setCronPart(value, type) {
                if (value == '*') {
                    $('input:radio[name=' + type + '_chooser]')[0].checked = true;
                } else {
                    $('input:radio[name=' + type + '_chooser]')[1].checked = true;
                    $("#" + type).removeAttr('disabled');
                    var mins = value.split(',');
                    $('#' + type).val(mins);

                }
            }

            function editJob(jobId) {
                jQuery.getJSON("<%= request.getContextPath() %>/ws/rs/jobs/job/" + jobId, {},
                    function (xml) {
                        if (xml.jobs) {
                            var job;
                            if (xml.jobs instanceof Array) {
                                job = xml.jobs[0];
                            } else {
                                job = xml.jobs;
                            }

                            $('#jobName').val(job.name);
                            $('#jobType').val(job.oscarJobTypeId);
                            $('#jobDescription').val(job.description);
                            $('#jobEnabled').prop('checked', job.enabled);
                            $('#jobProvider').val(job.providerNo);
                            $('#jobId').val(job.id);
                        }
                    });
                $('#new-job').dialog('open');
            }

            function addNewJob() {
                $('#jobName').val('');
                $('#jobType').val('');
                $('#jobDescription').val('');
                $('#jobEnabled').prop('checked', true);
                $('#jobProvider').val('<%=provider.getProviderNo()%>');
                $('#jobId').val('0');
                $('#new-job').dialog('open');
            }

            function clearJobs() {
                $("#jobTable tbody tr").remove();
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
                                var job = arr[i];
                                var extraClass = (job.cronExpression != undefined) ? "blue" : "red";
                                var html = '<tr>';
                                html += '<td><a onclick="scheduleJob(' + job.id + ');"><i class="fa-solid fa-calendar ' + extraClass + '"></i></a></td>';
                                html += '<td><u><a href="javascript:void();" onclick="editJob(' + job.id + ');">' + job.name + '</a></u></td>';
                                html += '<td><a onclick="cancelJob(' + job.id + ');"><fmt:message key="admin.jobs.cancel"/></a></td>';
                                html += '<td>' + ((job.enabled == true)
                                    ? '<fmt:message key="admin.jobs.enabledStatus"/> (<a onclick="updateJobStatus(' + job.id + ',false)"><fmt:message key="admin.jobs.disable"/></a>)'
                                    : '<span color="red"><fmt:message key="admin.jobs.disabledStatus"/></span> (<a onclick="updateJobStatus(' + job.id + ',true)"><fmt:message key="admin.jobs.enable"/></a>)') + '</td>';
                                html += '<td><fmt:message key="admin.jobs.notAvailable"/></td>';
                                html += '<td>' + ((job.nextPlannedExecutionDate == null) ? '<fmt:message key="admin.jobs.notAvailable"/>' : new Date(job.nextPlannedExecutionDate)) + '</td>';
                                html += '</tr>';

                                jQuery('#jobTable tbody').append(html);
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
                            class: "btn btn-primary", text: "<fmt:message key="admin.jobs.saveJob"/>", click: function () {
                                if (validateSaveJob()) {
                                    $.post('<%= request.getContextPath() %>/ws/rs/jobs/saveJob', $('#jobForm').serialize(), function (data) {
                                        listJobs();
                                    });
                                    $(this).dialog("close");
                                }

                            }
                        },
                        "<fmt:message key="admin.jobs.cancel"/>": {
                            class: "btn", text: "<fmt:message key="admin.jobs.cancel"/>", click: function () {
                                $(this).dialog("close");
                            }
                        }
                    },
                    close: function () {

                    }
                });

                $("#scheduleDialog").dialog({
                    autoOpen: false,
                    height: 400,
                    width: 780,
                    modal: true,
                    buttons: {
                        "<fmt:message key="admin.jobs.save"/>": {
                            class: "btn btn-primary", text: "<fmt:message key="admin.jobs.save"/>", click: function () {
                                //TODO: validate the fields.
                                //submit the crontab-form , close the dialog.
                                $.post('<%= request.getContextPath() %>/ws/rs/jobs/saveCrontabExpression', $('#crontab-form').serialize(), function (data) {
                                    listJobs();
                                });
                                $(this).dialog("close");

                            }
                        },
                        "<fmt:message key="admin.jobs.cancel"/>": {
                            class: "btn", text: "<fmt:message key="admin.jobs.cancel"/>", click: function () {
                                $(this).dialog("close");
                            }
                        }
                    },
                    close: function () {

                    }
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


    <div id="new-job" title="<fmt:message key="admin.jobs.editorTitle"/>">
        <p class="validateTips"></p>

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


    <div id="scheduleDialog" title="<fmt:message key="admin.jobs.scheduleJob"/>">
        <p class="validateTips"></p>

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
                                for (int x = 0; x < 59; x++) {
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
                                for (int x = 1; x < 30; x++) {
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
