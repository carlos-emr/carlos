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
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin.fax" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin.fax");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.commn.model.FaxConfig" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.QueueDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@page import="java.util.List" %>
<%@page import="java.util.HashMap" %>
<%@ page import="io.github.carlos_emr.carlos.managers.FaxManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    // Declare Java variables at page scope before any HTML/JavaScript
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    List<FaxConfig> faxConfigList = faxManager.getFaxConfigurationAccounts(loggedInInfo);

    QueueDao queueDao = SpringUtils.getBean(QueueDao.class);
    HashMap<Integer,String> queueMap = queueDao.getHashMapOfQueues();
%>

<!DOCTYPE html>
<html>
<head>
    <title>Fax Configuration</title>

    <meta name="viewport" content="width=device-width,initial-scale=1.0">

    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/bootstrap.css" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/bootstrap-responsive.css" type="text/css">

    <script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery-1.9.1.js"></script>

    <style>
        body {
            background: #f8f9fc;
            color: #1f2937;
        }

        .fax-page {
            max-width: 1200px;
            margin: 24px auto;
            background: #ffffff;
            border: 1px solid #e5e7eb;
            border-radius: 12px;
            box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
            padding: 20px 20px 12px;
        }

        .fax-section-title {
            color: #0d6efd;
            font-size: 1.15rem;
            font-weight: 600;
            border-bottom: 1px solid #e5e7eb;
            padding-bottom: 8px;
            margin-bottom: 16px;
        }

        .fax-card {
            border: 1px solid #e6e9ef;
            border-radius: 10px;
            background: #fbfcfe;
            padding: 14px;
            margin-bottom: 14px;
        }

        .fax-muted {
            color: #6b7280;
            font-size: 12px;
        }

        #submit.btn {
            background: #0d6efd;
            color: #fff;
            border-color: #0d6efd;
        }

        #submit.btn:disabled {
            background: #9ec5fe;
            border-color: #9ec5fe;
            color: #fff;
        }

        #msg.alert {
            margin-top: 14px;
            border-radius: 8px;
            font-weight: 500;
        }

        .status-label {
            font-weight: 600;
            min-width: 175px;
            display: inline-block;
        }
    </style>

    <script type="text/javascript">
        // CSRF token for AJAX requests
        var csrfParameterName = '${_csrf.parameterName}';
        var csrfToken = '${_csrf.token}';

        // Warn user if they try to leave with unsaved changes
        window.addEventListener('beforeunload', function (e) {
            if (!$("#submit").prop("disabled")) {
                e.preventDefault();
                e.returnValue = ''; // Chrome requires returnValue to be set
                return 'You have unsaved changes. Are you sure you want to leave?';
            }
        });

        // Clear the unsaved changes flag after successful save
        var originalAjaxSuccess = function(data) {
            if (data.success) {
                $("#submit").prop("disabled", true); // Re-disable submit button after save
                $("#msg").text(data.message || "Configuration saved!");
                $('.alert').removeClass('alert-error');
                $('.alert').addClass('alert-success');
                $('.alert').show();
            } else {
                $("#msg").text(data.message || "There was a problem saving your configuration.  Check the logs for further details.");
                $('.alert').removeClass('alert-success');
                $('.alert').addClass('alert-error');
                $('.alert').show();
            }
        };

        $(document).keypress(function () {
            $("#submit").prop("disabled", false);
            $(this).off();
        });


        $(document).ready(function () {

            $("select").change(function () {
                $("#submit").prop("disabled", false);
                // Check if this is a provider type dropdown
                if ($(this).attr("name") === "providerType") {
                    updateMiddlewareFieldsVisibility();
                }
                $(this).off();
            });

            $("#submit").click(function (e) {
                e.preventDefault();

                var url = "<%=Encode.forJavaScript(request.getContextPath()) %>/admin/ManageFax.do?method=configure";
                var data = $("#configFrm").serialize();

                $.ajax({
                    url: url,
                    method: 'POST',
                    data: data,
                    dataType: "json",
                    success: originalAjaxSuccess
                });

            });

            $("input[type='radio']").click(function () {
                $("#submit").prop("disabled", false);
                setState(this);
            });

            getFaxSchedularStatus();
            updateMiddlewareFieldsVisibility();

        });

        function updateMiddlewareFieldsVisibility() {
            var providerType = $("#providerType").val();

            if (providerType === "MIDDLEWARE") {
                // Show middleware fields and make them required
                $("#middlewareFields").show();
                $("#faxServiceUser").prop("required", true);
                $("#faxServicePasswd").prop("required", true);
                $("#faxUrl").prop("required", true);
            } else {
                // Hide middleware fields and remove required validation
                $("#middlewareFields").hide();
                $("#faxServiceUser").prop("required", false);
                $("#faxServicePasswd").prop("required", false);
                $("#faxUrl").prop("required", false);

                // Clear middleware URL for SRFax mode (backend will use SRFax URL)
                $("#faxUrl").val("");
            }
        }

        function setState(elem) {
            // Update hidden state fields when radio buttons change
            if (elem.id.startsWith("download")) {
                $("#downloadState").val($(elem).val());
            } else {
                $("#activeState").val($(elem).val());
            }
        }

        function getFaxSchedularStatus() {
            $.ajax({
                url: "<%=Encode.forJavaScript(request.getContextPath()) %>/admin/ManageFax.do",
                method: 'POST',
                data: 'method=getFaxSchedularStatus&' + csrfParameterName + '=' + csrfToken,
                success: function (data) {
                    $('#restartFaxSchedulerBtn').prop('disabled', data.isRunning);
                    $("#faxStatusDetails").text(data.faxSchedularStatus).css("color", data.isRunning ? "black" : "red");

                    var lastRunText = "Never";
                    if (data.lastSuccessfulRunEpochMs && data.lastSuccessfulRunEpochMs > 0) {
                        lastRunText = new Date(data.lastSuccessfulRunEpochMs).toLocaleString();
                    }
                    $("#faxLastRunDetails").text(lastRunText);
                    $("#faxLastErrorDetails").text(data.lastError && data.lastError.length > 0 ? data.lastError : "None");
                    HideSpin();
                }
            });
        }

        function rebootFaxSchedular() {
            $.ajax({
                url: "<%=Encode.forJavaScript(request.getContextPath()) %>/admin/ManageFax.do",
                method: 'POST',
                data: 'method=restartFaxScheduler&' + csrfParameterName + '=' + csrfToken,
                success: function (data) {
                    console.log("Fax scheduler restarted successfully");
                    ShowSpin(true);
                    setTimeout(getFaxSchedularStatus, 3000);
                }
            });
        }


    </script>

</head>


<body>
<jsp:include page="/images/spinner.jsp" flush="true"/>
<div class="container-fluid fax-page">
    <form id="configFrm" method="post">
        <input type="hidden" name="method" value="configure"/>
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>

        <!-- Scheduler Health Status - Top of Page -->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.fax.restart" rights="r" reverse="<%=false%>">
            <div class="row">
                <div class="span12">
                    <legend class="fax-section-title"><i class="fas fa-heartbeat"></i> Scheduler Health Status</legend>
                    <small class="fax-muted" style="display: block; margin-bottom: 12px;">
                        The fax scheduler polls for inbound/outbound faxes at regular intervals. These metrics show scheduler health, not fax gateway connectivity.
                    </small>
                </div>
                <div class="span12" style="display: inline-flex; gap: 6px; align-items: baseline;">
                    <label class="status-label"><i class="fas fa-heartbeat"></i> Scheduler Status:</label><label id="faxStatusDetails"></label>
                </div>
                <div class="span12" style="display: inline-flex; gap: 6px; align-items: baseline;">
                    <label class="status-label"><i class="fas fa-clock"></i> Last Poll Cycle:</label><label id="faxLastRunDetails">Never</label>
                    <small class="fax-muted" style="margin-left: 8px;">(scheduler ran, may have had nothing to process)</small>
                </div>
                <div class="span12" style="display: inline-flex; gap: 6px; align-items: baseline;">
                    <label class="status-label"><i class="fas fa-exclamation-triangle"></i> Last Scheduler Error:</label><label id="faxLastErrorDetails">None</label>
                </div>
                <div class="span12" style="margin-bottom: 20px;">
                    <button id="restartFaxSchedulerBtn" class="btn btn-warning" type="button" onclick="rebootFaxSchedular()" disabled>
                        <i class="fas fa-sync-alt"></i> Restart Scheduler
                    </button>
                </div>
            </div>
        </security:oscarSec>

        <!-- Fax Gateway Configuration -->
        <div class="row">
            <div class="span12">
                <legend class="fax-section-title"><i class="fas fa-satellite-dish"></i> Fax Gateway Configuration</legend>

                <div class="row fax-card">
                    <div class="span12">
                        <%
                            // Get first config or create defaults for empty config
                            // Note: "config" is a JSP implicit object (ServletConfig), so use "faxCfg" instead
                            FaxConfig faxCfg = faxConfigList.isEmpty() ? null : faxConfigList.get(0);
                            String configId = faxCfg != null ? String.valueOf(faxCfg.getId()) : "-1";
                            String faxUser = faxCfg != null ? faxCfg.getFaxUser() : "";
                            String faxPassword = (faxCfg != null && faxCfg.getFaxPasswd() != null && !faxCfg.getFaxPasswd().isEmpty()) ? "**********" : "";
                            String faxNumber = faxCfg != null ? faxCfg.getFaxNumber() : "";
                            String senderEmail = faxCfg != null ? faxCfg.getSenderEmail() : "";
                            String accountName = faxCfg != null ? faxCfg.getAccountName() : "";
                            Integer queueId = faxCfg != null ? faxCfg.getQueue() : -1;
                            FaxConfig.ProviderType providerType = faxCfg != null ? faxCfg.getProviderType() : FaxConfig.ProviderType.MIDDLEWARE;
                            boolean isActive = faxCfg != null && faxCfg.isActive();
                            boolean isDownload = faxCfg != null && faxCfg.isDownload();
                            String faxUrl = faxCfg != null ? faxCfg.getUrl() : "";
                            String siteUser = faxCfg != null ? faxCfg.getSiteUser() : "";
                            String sitePasswd = (faxCfg != null && faxCfg.getPasswd() != null && !faxCfg.getPasswd().isEmpty()) ? "**********" : "";
                        %>

                        <!-- Provider Type Selection -->
                        <div class="row">
                            <div class="span6">
                                <label for="providerType">Fax Provider</label>
                                <select class="span6" id="providerType" name="providerType">
                                    <option value="MIDDLEWARE" <%=providerType == FaxConfig.ProviderType.MIDDLEWARE ? "selected" : ""%>>Middleware Relay (faxws)</option>
                                    <option value="SRFAX" <%=providerType == FaxConfig.ProviderType.SRFAX ? "selected" : ""%>>SRFax Direct API</option>
                                </select>
                                <small class="fax-muted"><i class="fas fa-info-circle"></i> Choose how to connect: via middleware relay server or directly to SRFax API</small>
                            </div>
                        </div>

                        <!-- Middleware Relay Fields (shown only for Middleware provider) -->
                        <div id="middlewareFields" style="display:none;">
                            <div class="row">
                                <div class="span12">
                                    <h6 style="color: #0d6efd; margin-top: 12px; margin-bottom: 8px;">Middleware Relay Server</h6>
                                    <small class="fax-muted" style="display: block; margin-bottom: 12px;">
                                        <i class="fas fa-info-circle"></i> Configure the relay server that will forward faxes to/from SRFax
                                    </small>
                                </div>
                                <div class="span12">
                                    <label for="faxUrl"><i class="fas fa-link"></i> Middleware Relay URL</label>
                                    <input class="span12" id="faxUrl" type="text" name="faxUrl" placeholder="https://your-middleware-server.com/fax"
                                           value="<%=Encode.forHtmlAttribute(faxUrl)%>"/>
                                    <small class="fax-muted">URL of your middleware relay server</small>
                                </div>
                            </div>
                            <div class="row">
                                <div class="span6">
                                    <label for="faxServiceUser"><i class="fas fa-user"></i> Middleware Username</label>
                                    <input class="span6" id="faxServiceUser" type="text" name="siteUser"
                                           value="<%=Encode.forHtmlAttribute(siteUser)%>"/>
                                    <small class="fax-muted">Username for middleware relay server</small>
                                </div>
                                <div class="span6">
                                    <label for="faxServicePasswd"><i class="fas fa-key"></i> Middleware Password</label>
                                    <input class="span6" id="faxServicePasswd" type="password" name="sitePasswd"
                                           value="<%=Encode.forHtmlAttribute(sitePasswd)%>"/>
                                    <small class="fax-muted">Password for middleware relay server</small>
                                </div>
                            </div>
                        </div>

                        <!-- SRFax Account Credentials (always shown) -->
                        <div class="row">
                            <div class="span12">
                                <h6 style="color: #0d6efd; margin-top: 12px; margin-bottom: 8px;">SRFax Account Credentials</h6>
                                <small class="fax-muted" style="display: block; margin-bottom: 12px;">
                                    <i class="fas fa-info-circle"></i> Required for both provider types - your SRFax account details
                                </small>
                            </div>
                            <div class="span6">
                                <label for="faxUser">SRFax Username</label>
                                <input class="span6" type="text" id="faxUser" name="faxUser"
                                       value="<%=Encode.forHtmlAttribute(faxUser)%>"/>
                                <input type="hidden" id="id" name="id" value="<%=configId%>"/>
                            </div>
                            <div class="span6">
                                <label for="faxPasswd">SRFax Password</label>
                                <input class="span6" type="password" id="faxPasswd" name="faxPassword"
                                       value="<%=Encode.forHtmlAttribute(faxPassword)%>"/>
                            </div>
                        </div>

                        <!-- Account Details -->
                        <div class="row">
                            <div class="span6">
                                <label for="faxNumber">Fax Number</label>
                                <input class="span6" type="text" id="faxNumber" name="faxNumber"
                                       value="<%=Encode.forHtmlAttribute(faxNumber)%>"/>
                            </div>
                            <div class="span6">
                                <label for="senderEmail">Email</label>
                                <input class="span6" type="email" id="senderEmail" name="senderEmail"
                                       placeholder="Account email"
                                       value="<%=Encode.forHtmlAttribute(senderEmail)%>"/>
                            </div>
                        </div>
                        <div class="row">
                            <div class="span6">
                                <label for="inBoxQueue">Inbox Queue</label>
                                <select class="span6" id="inBoxQueue" name="inboxQueue">
                                    <option value="-1">-</option>
                                    <%
                                        for (Integer qId : queueMap.keySet()) {
                                            out.print("<option value='" + qId + "'");
                                            if (qId.equals(queueId)) {
                                                out.print(" selected");
                                            }
                                            out.print(">" + Encode.forHtml(queueMap.get(qId)) + "</option>");
                                        }
                                    %>
                                </select>
                            </div>
                            <div class="span6">
                                <label for="accountName">Account Name</label>
                                <input class="span6" type="text" name="accountName" id="accountName"
                                       value="<%=Encode.forHtmlAttribute(accountName)%>"/>
                            </div>
                        </div>

                        <!-- Enable/Disable Toggle -->
                        <div class="row">
                            <div class="span6">
                                <label>Enable Fax Gateway</label>
                                <label class="radio inline control-label">
                                    <input type="radio" id="on" name="active" value="true" <%=isActive ? "checked" : ""%> />
                                    Enabled
                                </label>
                                <label class="radio inline control-label">
                                    <input type="radio" id="of" name="active" value="false" <%=!isActive ? "checked" : ""%> />
                                    Disabled
                                </label>
                                <input type="hidden" id="activeState" name="activeState" value="<%=isActive%>"/>
                                <br/>
                                <small class="fax-muted"><i class="fas fa-info-circle"></i> Turn the selected fax gateway on or off for sending and receiving</small>
                            </div>
                            <div class="span6">
                                <label>
                                    <input type="checkbox" id="downloadCheckbox" <%=isDownload ? "checked" : ""%> onchange="$('#download_on').prop('checked', this.checked); $('#download_of').prop('checked', !this.checked); $('#downloadState').val(this.checked); $('#submit').prop('disabled', false);" />
                                    Poll for incoming faxes
                                </label>
                                <input type="radio" id="download_on" name="download" value="true" style="display:none;" <%=isDownload ? "checked" : ""%> />
                                <input type="radio" id="download_of" name="download" value="false" style="display:none;" <%=!isDownload ? "checked" : ""%> />
                                <input type="hidden" id="downloadState" name="downloadState" value="<%=isDownload%>"/>
                                <br/>
                                <small class="fax-muted"><i class="fas fa-info-circle"></i> When checked, scheduler will automatically download incoming faxes. SRFax uses unread-only fetch with mark-as-read.</small>
                            </div>
                        </div>

                    </div>
                </div>
            </div>
        </div>

        <div class="row">
            <input class="btn btn-primary" id="submit" type="submit" disabled value="Save Configuration"/>
            <small class="fax-muted" style="margin-left: 12px; display: inline-block; line-height: 30px;">
                <i class="fas fa-info-circle"></i> Changes are not saved until you click "Save Configuration"
            </small>
        </div>
    </form>

    <div id="msg" class="row alert" role="alert" style="display:none;">
    </div>
</div>    <!-- end container -->


</body>
</html>
