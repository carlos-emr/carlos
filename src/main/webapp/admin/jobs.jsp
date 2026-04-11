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
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
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
<%@ page import="org.owasp.encoder.Encode" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Provider provider = loggedInInfo.getLoggedInProvider();
    java.util.ResourceBundle jobsResources =
        java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());
%>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
    <head>
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
                                html += '<td><a onclick="cancelJob(' + job.id + ');"><%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jslinkCancel")) %></a></td>';
                                html += '<td>' + ((job.enabled == true) ? "<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsLabelEnabled")) %> (<a onclick='updateJobStatus(" + job.id + ",false)'><%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jslinkDisable")) %></a>)" : "<span color='red'><%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsLabelDisabled")) %></span> (<a onclick='updateJobStatus(" + job.id + ",true)'><%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jslinkEnable")) %></a>)") + '</td>';
                                html += '<td><%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsLabelNA")) %></td>';
                                html += '<td>' + ((job.nextPlannedExecutionDate == null) ? '<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsLabelNA")) %>' : new Date(job.nextPlannedExecutionDate)) + '</td>';
                                html += '</tr>';

                                jQuery('#jobTable tbody').append(html);
                            }
                        } else {
                            alert('<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsMsgErrorRetrievingJobs")) %>');
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
                                    text: arr[i].name + ((arr[i].currentlyValid == true) ? '' : '(Not currently available)')
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
                        "<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsBtnSaveJob")) %>": {
                            class: "btn btn-primary", text: "<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsBtnSaveJob")) %>", click: function () {
                                if (validateSaveJob()) {
                                    $.post('<%= request.getContextPath() %>/ws/rs/jobs/saveJob', $('#jobForm').serialize(), function (data) {
                                        listJobs();
                                    });
                                    $(this).dialog("close");
                                }

                            }
                        },
                        "<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsBtnCancel")) %>": {
                            class: "btn", text: "<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsBtnCancel")) %>", click: function () {
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
                        "<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsBtnSave")) %>": {
                            class: "btn btn-primary", text: "<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsBtnSave")) %>", click: function () {
                                //TODO: validate the fields.
                                //submit the crontab-form , close the dialog.
                                $.post('<%= request.getContextPath() %>/ws/rs/jobs/saveCrontabExpression', $('#crontab-form').serialize(), function (data) {
                                    listJobs();
                                });
                                $(this).dialog("close");

                            }
                        },
                        "<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsBtnCancel")) %>": {
                            class: "btn", text: "<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsBtnCancel")) %>", click: function () {
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
                    errorMsg += '<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsMsgValidateName")) %>\n';
                }
                if ($('#jobType').val().length == 0 || $('#jobType').val() == '0') {
                    errorMsg += '<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsMsgValidateJobType")) %>\n';
                }
                if ($('#jobProvider').val().length == 0 || $('#jobProvider').val() == '0') {
                    errorMsg += '<%= Encode.forJavaScript(jobsResources.getString("admin.jobs.jsMsgValidateProvider")) %>\n';
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
    <h4><fmt:message key="admin.jobs.headingManageJobs"/></h4>
    <table id="jobTable" class="table table-bordered table-striped table-hover table-sm">
        <thead>
        <tr>
            <th></th>
            <th><fmt:message key="admin.jobs.thName"/></th>
            <th><fmt:message key="admin.jobs.thExecutionStatus"/></th>
            <th><fmt:message key="admin.jobs.thJobStatus"/></th>
            <th><fmt:message key="admin.jobs.thLastRun"/></th>
            <th><fmt:message key="admin.jobs.thNextPlannedRun"/></th>
        </tr>
        </thead>
        <tbody>
        </tbody>
    </table>
    <fmt:message key="admin.jobs.btnAddNew" var="btnAddNew"/>
    <input type="button" class="btn btn-primary" value="${btnAddNew}" onClick="addNewJob()"/>


    <fmt:message key="admin.jobs.dialogTitleJobEditor" var="dialogTitleJobEditor"/>
    <div id="new-job" title="${dialogTitleJobEditor}">
        <p class="validateTips"></p>

        <form id="jobForm">
            <input type="hidden" name="job.id" id="jobId" value="0"/>
            <fieldset>
                <div class="mb-3">
                    <label class="form-label" for="jobName"><fmt:message key="admin.jobs.labelName"/></label>
                    <div>
                        <input type="text" name="job.name" id="jobName" value=""/>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="jobType"><fmt:message key="admin.jobs.labelType"/></label>
                    <div>
                        <select name="job.oscarJobTypeId" id="jobType">
                            <option value="">&nbsp;</option>
                        </select>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="jobDescription"><fmt:message key="admin.jobs.labelDescription"/></label>
                    <div>
                        <textarea rows="5" name="job.description" id="jobDescription"></textarea>
                    </div>
                </div>
                <div class="mb-3">
                    <label class="form-label" for="jobEnabled"><fmt:message key="admin.jobs.labelEnabled"/> <input type="checkbox" name="job.enabled"
                                                                                  id="jobEnabled"/></label>
                    <div>

                    </div>
                </div>

                <div class="mb-3">
                    <label class="form-label" for="jobProvider"><fmt:message key="admin.jobs.labelRunAsProvider"/></label>
                    <div>
                        <select name="job.provider" id="jobProvider">
                            <option value="">&nbsp;</option>

                        </select>
                    </div>
                </div>

            </fieldset>
        </form>
    </div>


    <fmt:message key="admin.jobs.dialogTitleScheduleJob" var="dialogTitleScheduleJob"/>
    <div id="scheduleDialog" title="${dialogTitleScheduleJob}">
        <p class="validateTips"></p>

        <form id="crontab-form">
            <input type="hidden" name="scheduleJobId" id="scheduleJobId" value="0"/>

            <table>
                <tr>
                    <td>
                        <h4><fmt:message key="admin.jobs.scheduleMinute"/></h4>

                        <fmt:message key="admin.jobs.scheduleEveryMinute"/>
                        <input type="radio" name="minute_chooser" id="minute_chooser_every" value="0"
                               checked="checked"/><br/>
                        <fmt:message key="admin.jobs.scheduleChoose"/>
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
                        <h4><fmt:message key="admin.jobs.scheduleHour"/></h4>
                        <fmt:message key="admin.jobs.scheduleEveryHour"/>
                        <input type="radio" name="hour_chooser" id="hour_chooser_every" value="0"
                               checked="checked"/><br/>

                        <fmt:message key="admin.jobs.scheduleChoose"/>
                        <input type="radio" name="hour_chooser" id="hour_chooser_choose" value="1"/><br/>

                        <select name="hour" id="hour" multiple="multiple" disabled="disabled" style="width:120px">
                            <option value="0"><fmt:message key="admin.jobs.scheduleHour00"/></option>
                            <option value="1"><fmt:message key="admin.jobs.scheduleHour01"/></option>
                            <option value="2"><fmt:message key="admin.jobs.scheduleHour02"/></option>
                            <option value="3"><fmt:message key="admin.jobs.scheduleHour03"/></option>
                            <option value="4"><fmt:message key="admin.jobs.scheduleHour04"/></option>
                            <option value="5"><fmt:message key="admin.jobs.scheduleHour05"/></option>
                            <option value="6"><fmt:message key="admin.jobs.scheduleHour06"/></option>
                            <option value="7"><fmt:message key="admin.jobs.scheduleHour07"/></option>
                            <option value="8"><fmt:message key="admin.jobs.scheduleHour08"/></option>
                            <option value="9"><fmt:message key="admin.jobs.scheduleHour09"/></option>
                            <option value="10"><fmt:message key="admin.jobs.scheduleHour10"/></option>
                            <option value="11"><fmt:message key="admin.jobs.scheduleHour11"/></option>
                            <option value="12"><fmt:message key="admin.jobs.scheduleHour12"/></option>
                            <option value="13"><fmt:message key="admin.jobs.scheduleHour13"/></option>
                            <option value="14"><fmt:message key="admin.jobs.scheduleHour14"/></option>
                            <option value="15"><fmt:message key="admin.jobs.scheduleHour15"/></option>
                            <option value="16"><fmt:message key="admin.jobs.scheduleHour16"/></option>
                            <option value="17"><fmt:message key="admin.jobs.scheduleHour17"/></option>
                            <option value="18"><fmt:message key="admin.jobs.scheduleHour18"/></option>
                            <option value="19"><fmt:message key="admin.jobs.scheduleHour19"/></option>
                            <option value="20"><fmt:message key="admin.jobs.scheduleHour20"/></option>
                            <option value="21"><fmt:message key="admin.jobs.scheduleHour21"/></option>
                            <option value="22"><fmt:message key="admin.jobs.scheduleHour22"/></option>
                            <option value="23"><fmt:message key="admin.jobs.scheduleHour23"/></option>
                        </select>
                    </td>

                    <td>
                        <h4><fmt:message key="admin.jobs.scheduleDay"/></h4>
                        <fmt:message key="admin.jobs.scheduleEveryDay"/>
                        <input type="radio" name="day_chooser" id="day_chooser_every" value="0" checked="checked"/><br/>

                        <fmt:message key="admin.jobs.scheduleChoose"/>
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
                        <h4><fmt:message key="admin.jobs.scheduleMonth"/></h4>
                        <fmt:message key="admin.jobs.scheduleEveryMonth"/>
                        <input type="radio" name="month_chooser" id="month_chooser_every" value="0"
                               checked="checked"/><br/>

                        <fmt:message key="admin.jobs.scheduleChoose"/>
                        <input type="radio" name="month_chooser" id="month_chooser_choose" value="1"/><br/>

                        <select name="month" id="month" multiple="multiple" disabled="disabled" style="width:120px">
                            <option value="1"><fmt:message key="admin.jobs.scheduleMonthJan"/></option>
                            <option value="2"><fmt:message key="admin.jobs.scheduleMonthFeb"/></option>
                            <option value="3"><fmt:message key="admin.jobs.scheduleMonthMar"/></option>
                            <option value="4"><fmt:message key="admin.jobs.scheduleMonthApr"/></option>
                            <option value="5"><fmt:message key="admin.jobs.scheduleMonthMay"/></option>
                            <option value="6"><fmt:message key="admin.jobs.scheduleMonthJun"/></option>
                            <option value="7"><fmt:message key="admin.jobs.scheduleMonthJul"/></option>
                            <option value="8"><fmt:message key="admin.jobs.scheduleMonthAug"/></option>
                            <option value="9"><fmt:message key="admin.jobs.scheduleMonthSep"/></option>
                            <option value="10"><fmt:message key="admin.jobs.scheduleMonthOct"/></option>
                            <option value="11"><fmt:message key="admin.jobs.scheduleMonthNov"/></option>
                            <option value="12"><fmt:message key="admin.jobs.scheduleMonthDec"/></option>
                        </select>
                    </td>
                    <td>
                        <h4><fmt:message key="admin.jobs.scheduleWeekday"/></h4>
                        <fmt:message key="admin.jobs.scheduleEveryWeekday"/>
                        <input type="radio" name="weekday_chooser" id="weekday_chooser_every" value="0"
                               checked="checked"/><br/>

                        <fmt:message key="admin.jobs.scheduleChoose"/>
                        <input type="radio" name="weekday_chooser" id="weekday_chooser_choose" value="1"/><br/>

                        <select name="weekday" id="weekday" multiple="multiple" disabled="disabled" style="width:120px">
                            <option value="0"><fmt:message key="admin.jobs.scheduleWeekdaySun"/></option>
                            <option value="1"><fmt:message key="admin.jobs.scheduleWeekdayMon"/></option>
                            <option value="2"><fmt:message key="admin.jobs.scheduleWeekdayTue"/></option>
                            <option value="3"><fmt:message key="admin.jobs.scheduleWeekdayWed"/></option>
                            <option value="4"><fmt:message key="admin.jobs.scheduleWeekdayThu"/></option>
                            <option value="5"><fmt:message key="admin.jobs.scheduleWeekdayFri"/></option>
                            <option value="6"><fmt:message key="admin.jobs.scheduleWeekdaySat"/></option>
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