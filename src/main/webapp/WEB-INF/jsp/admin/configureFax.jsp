<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    Copyright (c) 2017-2024. Juno EMR. All Rights Reserved.
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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

    Originally written for the Department of Family Medicine, McMaster University.
    Portions contributed by Juno EMR.
    Now maintained by the CARLOS EMR Project.
    https://github.com/carlos-emr/carlos

--%>

<%--
/**
 * Fax Gateway Configuration Administration Interface
 *
 * <p><strong>Purpose:</strong> Provides admin UI for configuring fax provider accounts,
 * scheduler health monitoring, and gateway enable/disable controls.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Multi-provider support (Middleware relay, SRFax direct API)</li>
 *   <li>Real-time scheduler health status polling</li>
 *   <li>Encrypted credential storage with auto-migration from legacy plain text</li>
 *   <li>Provider-specific field visibility (middleware vs SRFax)</li>
 *   <li>Unsaved changes warning on page navigation</li>
 * </ul>
 *
 * <p><strong>Security:</strong> Requires `_admin.fax` write privilege for configuration;
 * `_admin.fax.restart` read/write for scheduler controls.</p>
 *
 * <p><strong>Parameters:</strong> None (uses session-based LoggedInInfo)</p>
 *
 * @since 2014-08-29 (original), 2026-02-11 (multi-provider refactor)
 */
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin.fax" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin.fax");%>
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
<%@ page import="io.github.carlos_emr.carlos.fax.provider.SRFaxProviderClient" %>
<%@ page import="io.github.carlos_emr.carlos.fax.admin.ConfigureFax2Action" %>

<%
    // Declare Java variables at page scope before any HTML/JavaScript
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
    List<FaxConfig> faxConfigList = faxManager.getFaxConfigurationAccounts(loggedInInfo);

    QueueDao queueDao = SpringUtils.getBean(QueueDao.class);
    HashMap<Integer,String> queueMap = queueDao.getHashMapOfQueues();
    // Default to the first available queue (typically "default", id=1)
    Integer defaultQueueId = queueMap.isEmpty() ? 1 : queueMap.keySet().iterator().next();
%>

<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.configureFax.title"/></title>

    <meta name="viewport" content="width=device-width,initial-scale=1.0">

    <link rel="stylesheet" href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css" type="text/css">

    <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>

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
        const configureFaxSaved = "<fmt:message key='admin.configureFax.saved'/>";
        const configureFaxSaveProblem = "<fmt:message key='admin.configureFax.saveProblem'/>";
        const configureFaxSaveFailed = "<fmt:message key='admin.configureFax.saveFailed'/>";
        const configureFaxNever = "<fmt:message key='admin.configureFax.never'/>";
        const configureFaxNone = "<fmt:message key='admin.configureFax.none'/>";
        const configureFaxUnknown = "<fmt:message key='admin.configureFax.unknown'/>";
        const configureFaxUnableRetrieve = "<fmt:message key='admin.configureFax.unableRetrieve'/>";
        const configureFaxRestartFailed = "<fmt:message key='admin.configureFax.restartFailed'/>";
        const configureFaxUnsavedChanges = "<fmt:message key='admin.configureFax.unsavedChangesPrompt'/>";
        // Warn user if they try to leave with unsaved changes
        window.addEventListener('beforeunload', function (e) {
            if (!$("#submit").prop("disabled")) {
                e.preventDefault();
                e.returnValue = ''; // Chrome requires returnValue to be set
                return configureFaxUnsavedChanges;
            }
        });

        // Clear the unsaved changes flag after successful save
        var originalAjaxSuccess = function(data) {
            if (data.success) {
                $("#submit").prop("disabled", true); // Re-disable submit button after save
                $("#msg").text(data.message || configureFaxSaved);
                $('.alert').removeClass('alert-danger');
                $('.alert').addClass('alert-success');
                $('.alert').show();
            } else {
                $("#msg").text(data.message || configureFaxSaveProblem);
                $('.alert').removeClass('alert-success');
                $('.alert').addClass('alert-danger');
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

                var url = "<carlos:encode value='<%= request.getContextPath() %>' context="javaScript"/>/admin/ManageFax?method=configure";
                var data = $("#configFrm").serialize();

                $.ajax({
                    url: url,
                    method: 'POST',
                    data: data,
                    dataType: "json",
                    success: originalAjaxSuccess,
                    error: function(jqXHR, textStatus, errorThrown) {
                        $("#msg").text(configureFaxSaveFailed + " " + (errorThrown || textStatus));
                        $('.alert').removeClass('alert-success');
                        $('.alert').addClass('alert-danger');
                        $('.alert').show();
                    }
                });

            });

            $("input[type='radio']").click(function () {
                $("#submit").prop("disabled", false);
                setState(this);
            });

            getFaxSchedularStatus();
            updateMiddlewareFieldsVisibility();
            getPendingIncomingFaxes();

        });

        function updateMiddlewareFieldsVisibility() {
            var providerType = $("#providerType").val();

            if (providerType === "MIDDLEWARE") {
                $("#middlewareFields").show();
                $("#srfaxUrlInfo").hide();
                $("#faxServiceUser").prop("required", true);
                $("#faxServicePasswd").prop("required", true);
                $("#faxUrl").prop("required", true);
            } else {
                $("#middlewareFields").hide();
                $("#srfaxUrlInfo").show();
                $("#faxServiceUser").prop("required", false);
                $("#faxServicePasswd").prop("required", false);
                $("#faxUrl").prop("required", false);
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
                url: "<carlos:encode value='<%= request.getContextPath() %>' context="javaScript"/>/admin/ManageFax",
                method: 'POST',
                data: 'method=getFaxSchedularStatus',
                success: function (data) {
                    $('#restartFaxSchedulerBtn').prop('disabled', data.isRunning);
                    $("#faxStatusDetails").text(data.faxSchedularStatus || configureFaxUnknown).css("color", data.isRunning ? "black" : "red");

                    var lastRunText = configureFaxNever;
                    if (data.lastSuccessfulRunEpochMs && data.lastSuccessfulRunEpochMs > 0) {
                        lastRunText = new Date(data.lastSuccessfulRunEpochMs).toLocaleString();
                    }
                    $("#faxLastRunDetails").text(lastRunText);
                    $("#faxLastErrorDetails").text(data.lastError && data.lastError.length > 0 ? data.lastError : configureFaxNone);
                    HideSpin();
                },
                error: function(jqXHR, textStatus, errorThrown) {
                    $("#faxStatusDetails").text(configureFaxUnableRetrieve).css("color", "red");
                    HideSpin();
                }
            });
        }

        function rebootFaxSchedular() {
            $.ajax({
                url: "<carlos:encode value='<%= request.getContextPath() %>' context="javaScript"/>/admin/ManageFax",
                method: 'POST',
                data: 'method=restartFaxScheduler',
                success: function (data) {
                    console.log("Fax scheduler restarted successfully");
                    ShowSpin(true);
                    setTimeout(getFaxSchedularStatus, 3000);
                },
                error: function(jqXHR, textStatus, errorThrown) {
                    $("#faxStatusDetails").text(configureFaxRestartFailed + " " + (errorThrown || textStatus)).css("color", "red");
                }
            });
        }

        function getPendingIncomingFaxes() {
            $.ajax({
                url: "<carlos:encode value='<%= request.getContextPath() %>' context="javaScript"/>/admin/ManageFax",
                method: 'POST',
                data: 'method=getPendingIncomingFaxes',
                success: function (data) {
                    if (!data.success) {
                        $("#pendingFaxesSection").hide();
                        return;
                    }

                    var count = data.count || 0;
                    $("#pendingFaxCount").text(count);
                    if (count === 0) {
                        $("#pendingFaxBadge").hide();
                        $("#pendingFaxesTable").hide();
                        $("#pendingFaxesNone").show();
                    } else {
                        $("#pendingFaxBadge").show().text(count);
                        $("#pendingFaxesNone").hide();

                        var tbody = $("#pendingFaxesTableBody");
                        tbody.empty();
                        $.each(data.faxes, function(i, fax) {
                            var sizeKb = Math.round((fax.sizeBytes || 0) / 1024);
                            var dateStr = fax.lastModifiedMs > 0 ? new Date(fax.lastModifiedMs).toLocaleString() : "Unknown";
                            var row = $("<tr></tr>");
                            row.append($("<td></td>").text(fax.fileName));
                            row.append($("<td></td>").text(sizeKb + " KB"));
                            row.append($("<td></td>").text(dateStr));
                            row.append($("<td></td>").text("Config #" + fax.configId));
                            tbody.append(row);
                        });
                        $("#pendingFaxesTable").show();
                    }
                    $("#pendingFaxesSection").show();
                },
                error: function() {
                    $("#pendingFaxesSection").hide();
                }
            });
        }


    </script>

</head>


<body>
<jsp:include page="/WEB-INF/jsp/includes/spinner.jspf" flush="true"/>
<div class="container-fluid fax-page">
    <form id="configFrm" method="post">
        <input type="hidden" name="method" value="configure"/>

        <!-- Scheduler Health Status - Top of Page -->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.fax.restart" rights="r" reverse="<%=false%>">
            <div class="row">
                <div class="col-md-12">
                    <legend class="fax-section-title"><i class="fas fa-heartbeat"></i> <fmt:message key="admin.configureFax.schedulerHealth"/></legend>
                    <small class="fax-muted" style="display: block; margin-bottom: 12px;">
                        <fmt:message key="admin.configureFax.schedulerHealthHelp"/>
                    </small>
                </div>
                <div class="col-md-12" style="display: inline-flex; gap: 6px; align-items: baseline;">
                    <label class="status-label"><i class="fas fa-heartbeat"></i> <fmt:message key="admin.configureFax.schedulerStatus"/>:</label><label id="faxStatusDetails"></label>
                </div>
                <div class="col-md-12" style="display: inline-flex; gap: 6px; align-items: baseline;">
                    <label class="status-label"><i class="fas fa-clock"></i> <fmt:message key="admin.configureFax.lastPollCycle"/>:</label><label id="faxLastRunDetails"><fmt:message key="admin.configureFax.never"/></label>
                    <small class="fax-muted" style="margin-left: 8px;"><fmt:message key="admin.configureFax.schedulerRanHelp"/></small>
                </div>
                <div class="col-md-12" style="display: inline-flex; gap: 6px; align-items: baseline;">
                    <label class="status-label"><i class="fas fa-exclamation-triangle"></i> <fmt:message key="admin.configureFax.lastSchedulerError"/>:</label><label id="faxLastErrorDetails"><fmt:message key="admin.configureFax.none"/></label>
                </div>
                <div class="col-md-12" style="margin-bottom: 20px;">
                    <button id="restartFaxSchedulerBtn" class="btn btn-warning" type="button" onclick="rebootFaxSchedular()" disabled>
                        <i class="fas fa-sync-alt"></i> <fmt:message key="admin.configureFax.restartScheduler"/>
                    </button>
                </div>
            </div>
        </security:oscarSec>

        <!-- Pending Incoming Faxes -->
        <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.fax" rights="r" reverse="<%=false%>">
            <div id="pendingFaxesSection" class="row" style="display:none;">
                <div class="col-md-12">
                    <legend class="fax-section-title">
                        <i class="fas fa-inbox"></i> <fmt:message key="admin.configureFax.pendingIncomingFaxes"/>
                        <span id="pendingFaxBadge" class="badge bg-warning text-dark" style="display:none; font-size: 12px; vertical-align: middle; margin-left: 8px;"></span>
                    </legend>
                    <small class="fax-muted" style="display: block; margin-bottom: 12px;">
                        <fmt:message key="admin.configureFax.pendingIncomingHelp"/>
                    </small>
                </div>
                <div class="col-md-12">
                    <div id="pendingFaxesNone" style="display:none; margin-bottom: 14px;">
                        <span style="color: #059669;"><i class="fas fa-check-circle"></i> <fmt:message key="admin.configureFax.allImported"/></span>
                    </div>
                    <table id="pendingFaxesTable" class="table table-striped table-sm" style="display:none; margin-bottom: 14px;">
                        <thead>
                            <tr>
                                <th><fmt:message key="admin.configureFax.table.filename"/></th>
                                <th><fmt:message key="admin.configureFax.table.size"/></th>
                                <th><fmt:message key="admin.configureFax.table.downloaded"/></th>
                                <th><fmt:message key="admin.configureFax.table.account"/></th>
                            </tr>
                        </thead>
                        <tbody id="pendingFaxesTableBody"></tbody>
                    </table>
                </div>
            </div>
        </security:oscarSec>

        <!-- Fax Gateway Configuration -->
        <div class="row">
            <div class="col-md-12">
                <legend class="fax-section-title"><i class="fas fa-satellite-dish"></i> <fmt:message key="admin.configureFax.gatewayConfiguration"/></legend>

                <div class="row fax-card">
                    <div class="col-md-12">
                        <%
                            // Current UI supports configuring one fax account at a time.
                            // Backend array-based save infrastructure is preserved for potential future multi-account support.

                            // Get first config or create defaults for empty config
                            // Note: "config" is a JSP implicit object (ServletConfig), so use "faxCfg" instead
                            FaxConfig faxCfg = faxConfigList.isEmpty() ? null : faxConfigList.get(0);
                            String configId = faxCfg != null ? String.valueOf(faxCfg.getId()) : "-1";
                            String faxUser = faxCfg != null ? faxCfg.getFaxUser() : "";
                            String faxPassword = faxCfg != null ? ConfigureFax2Action.maskPasswordForDisplay(faxCfg.getFaxPasswd()) : "";
                            String faxNumber = faxCfg != null ? faxCfg.getFaxNumber() : "";
                            String senderEmail = faxCfg != null ? faxCfg.getSenderEmail() : "";
                            String accountName = faxCfg != null ? faxCfg.getAccountName() : "";
                            Integer queueId = (faxCfg != null && faxCfg.getQueue() != null && faxCfg.getQueue() > 0) ? faxCfg.getQueue() : defaultQueueId;
                            FaxConfig.ProviderType providerType = faxCfg != null ? faxCfg.getProviderType() : FaxConfig.ProviderType.MIDDLEWARE;
                            boolean isActive = faxCfg != null && faxCfg.isActive();
                            boolean isDownload = faxCfg != null && faxCfg.isDownload();
                            String faxUrl = faxCfg != null ? faxCfg.getUrl() : "";
                            String siteUser = faxCfg != null ? faxCfg.getSiteUser() : "";
                            String sitePasswd = faxCfg != null ? ConfigureFax2Action.maskPasswordForDisplay(faxCfg.getPasswd()) : "";
                        %>

                        <!-- Provider Type Selection -->
                        <div class="row">
                            <div class="col-md-6">
                                <label for="providerType"><fmt:message key="admin.configureFax.faxProvider"/></label>
                                <select class="form-select" id="providerType" name="providerType">
                                    <option value="MIDDLEWARE" <%=providerType == FaxConfig.ProviderType.MIDDLEWARE ? "selected" : ""%>><fmt:message key="admin.configureFax.provider.middlewareRelay"/></option>
                                    <option value="SRFAX" <%=providerType == FaxConfig.ProviderType.SRFAX ? "selected" : ""%>><fmt:message key="admin.configureFax.provider.srfaxDirectApi"/></option>
                                </select>
                                <small class="fax-muted"><i class="fas fa-info-circle"></i> <fmt:message key="admin.configureFax.chooseConnection"/></small>
                            </div>
                        </div>

                        <!-- Middleware Relay Fields (shown only for Middleware provider) -->
                        <div id="middlewareFields" style="display:none;">
                            <div class="row">
                                <div class="col-md-12">
                                    <h6 style="color: #0d6efd; margin-top: 12px; margin-bottom: 8px;"><fmt:message key="admin.configureFax.middlewareRelayServer"/></h6>
                                    <small class="fax-muted" style="display: block; margin-bottom: 12px;">
                                        <i class="fas fa-info-circle"></i> <fmt:message key="admin.configureFax.configureRelay"/>
                                    </small>
                                </div>
                                <div class="col-md-12">
                                    <label for="faxUrl"><i class="fas fa-link"></i> <fmt:message key="admin.configureFax.middlewareRelayUrl"/></label>
                                    <input class="form-control" id="faxUrl" type="text" name="faxUrl" placeholder="<fmt:message key='admin.configureFax.middlewareRelayUrlPlaceholder'/>"
                                           value="<carlos:encode value='<%= faxUrl %>' context="htmlAttribute"/>"/>
                                    <small class="fax-muted"><fmt:message key="admin.configureFax.middlewareRelayUrlHelp"/></small>
                                </div>
                            </div>
                            <div class="row">
                                <div class="col-md-6">
                                    <label for="faxServiceUser"><i class="fas fa-user"></i> <fmt:message key="admin.configureFax.middlewareUsername"/></label>
                                    <input class="form-control" id="faxServiceUser" type="text" name="siteUser"
                                           value="<carlos:encode value='<%= siteUser %>' context="htmlAttribute"/>"/>
                                    <small class="fax-muted"><fmt:message key="admin.configureFax.middlewareUsernameHelp"/></small>
                                </div>
                                <div class="col-md-6">
                                    <label for="faxServicePasswd"><i class="fas fa-key"></i> <fmt:message key="admin.configureFax.middlewarePassword"/></label>
                                    <input class="form-control" id="faxServicePasswd" type="password" name="sitePasswd"
                                           value="<carlos:encode value='<%= sitePasswd %>' context="htmlAttribute"/>"/>
                                    <small class="fax-muted"><fmt:message key="admin.configureFax.middlewarePasswordHelp"/></small>
                                </div>
                            </div>
                        </div>

                        <!-- SRFax API Endpoint (read-only, shown only for SRFax provider) -->
                        <div id="srfaxUrlInfo" style="display:none;">
                            <div class="row">
                                <div class="col-md-12">
                                    <h6 style="color: #0d6efd; margin-top: 12px; margin-bottom: 8px;"><fmt:message key="admin.configureFax.srfaxApiEndpoint"/></h6>
                                    <label><i class="fas fa-link"></i> <fmt:message key="admin.configureFax.apiUrl"/></label>
                                    <input class="form-control" type="text" readonly
                                           value="<carlos:encode value='<%= SRFaxProviderClient.DEFAULT_SRFAX_API_URL %>' context="htmlAttribute"/>"
                                           style="background: #f3f4f6; color: #6b7280; cursor: not-allowed;"/>
                                    <small class="fax-muted"><i class="fas fa-lock"></i> <fmt:message key="admin.configureFax.defaultEndpointHelp"/></small>
                                </div>
                            </div>
                        </div>

                        <!-- SRFax Account Credentials (always shown) -->
                        <div class="row">
                            <div class="col-md-12">
                                <h6 style="color: #0d6efd; margin-top: 12px; margin-bottom: 8px;"><fmt:message key="admin.configureFax.srfaxAccountCredentials"/></h6>
                                <small class="fax-muted" style="display: block; margin-bottom: 12px;">
                                    <i class="fas fa-info-circle"></i> <fmt:message key="admin.configureFax.srfaxAccountCredentialsHelp"/>
                                </small>
                            </div>
                            <div class="col-md-6">
                                <label for="faxUser"><fmt:message key="admin.configureFax.srfaxUsername"/></label>
                                <input class="form-control" type="text" id="faxUser" name="faxUser"
                                       value="<carlos:encode value='<%= faxUser %>' context="htmlAttribute"/>"/>
                                <input type="hidden" id="id" name="id" value="<carlos:encode value='<%= configId %>' context="htmlAttribute"/>"/>
                            </div>
                            <div class="col-md-6">
                                <label for="faxPasswd"><fmt:message key="admin.configureFax.srfaxPassword"/></label>
                                <input class="form-control" type="password" id="faxPasswd" name="faxPassword"
                                       value="<carlos:encode value='<%= faxPassword %>' context="htmlAttribute"/>"/>
                            </div>
                        </div>

                        <!-- Account Details -->
                        <div class="row">
                            <div class="col-md-6">
                                <label for="faxNumber"><fmt:message key="admin.configureFax.faxNumber"/></label>
                                <input class="form-control" type="text" id="faxNumber" name="faxNumber"
                                       value="<carlos:encode value='<%= faxNumber %>' context="htmlAttribute"/>"/>
                            </div>
                            <div class="col-md-6">
                                <label for="senderEmail"><fmt:message key="admin.configureFax.email"/></label>
                                <input class="form-control" type="email" id="senderEmail" name="senderEmail"
                                       placeholder="<fmt:message key='admin.configureFax.accountEmailPlaceholder'/>"
                                       value="<carlos:encode value='<%= senderEmail %>' context="htmlAttribute"/>"/>
                            </div>
                        </div>
                        <div class="row">
                            <div class="col-md-6">
                                <label><fmt:message key="admin.configureFax.inboxQueue"/></label>
                                <input class="form-control" type="text" readonly
                                       value="<carlos:encode value='<%= queueMap.getOrDefault(queueId, "default") %>' context="htmlAttribute"/>"
                                       style="background: #f3f4f6; color: #6b7280; cursor: not-allowed;"/>
                                <input type="hidden" name="inboxQueue" value="<%=queueId%>"/>
                                <small class="fax-muted"><fmt:message key="admin.configureFax.inboxQueueHelp"/></small>
                            </div>
                            <div class="col-md-6">
                                <label for="accountName"><fmt:message key="admin.configureFax.accountName"/></label>
                                <input class="form-control" type="text" name="accountName" id="accountName"
                                       value="<carlos:encode value='<%= accountName %>' context="htmlAttribute"/>"/>
                            </div>
                        </div>

                        <!-- Enable/Disable Toggle -->
                        <div class="row">
                            <div class="col-md-6">
                                <label><fmt:message key="admin.configureFax.enableFaxGateway"/></label>
                                <div class="form-check form-check-inline">
                                    <input class="form-check-input" type="radio" id="on" name="active" value="true" <%=isActive ? "checked" : ""%> />
                                    <label class="form-check-label" for="on"><fmt:message key="admin.configureFax.enabled"/></label>
                                </div>
                                <div class="form-check form-check-inline">
                                    <input class="form-check-input" type="radio" id="of" name="active" value="false" <%=!isActive ? "checked" : ""%> />
                                    <label class="form-check-label" for="of"><fmt:message key="admin.configureFax.disabled"/></label>
                                </div>
                                <input type="hidden" id="activeState" name="activeState" value="<%=isActive%>"/>
                                <br/>
                                <small class="fax-muted"><i class="fas fa-info-circle"></i> <fmt:message key="admin.configureFax.toggleGatewayHelp"/></small>
                            </div>
                            <div class="col-md-6">
                                <label>
                                    <input type="checkbox" id="downloadCheckbox" <%=isDownload ? "checked" : ""%> onchange="$('#download_on').prop('checked', this.checked); $('#download_of').prop('checked', !this.checked); $('#downloadState').val(this.checked); $('#submit').prop('disabled', false);" />
                                    <fmt:message key="admin.configureFax.pollIncomingFaxes"/>
                                </label>
                                <input type="radio" id="download_on" name="download" value="true" style="display:none;" <%=isDownload ? "checked" : ""%> />
                                <input type="radio" id="download_of" name="download" value="false" style="display:none;" <%=!isDownload ? "checked" : ""%> />
                                <input type="hidden" id="downloadState" name="downloadState" value="<%=isDownload%>"/>
                                <br/>
                                <small class="fax-muted"><i class="fas fa-info-circle"></i> <fmt:message key="admin.configureFax.downloadHelp"/></small>
                            </div>
                        </div>

                    </div>
                </div>
            </div>
        </div>

        <div class="row">
        <input class="btn btn-primary" id="submit" type="submit" disabled value="<fmt:message key='admin.configureFax.saveConfiguration'/>"/>
            <small class="fax-muted" style="margin-left: 12px; display: inline-block; line-height: 30px;">
                <i class="fas fa-info-circle"></i> <fmt:message key="admin.configureFax.unsavedChanges"/>
            </small>
        </div>
    </form>

    <div id="msg" class="row alert" role="alert" style="display:none;">
    </div>
</div>    <!-- end container -->


</body>
</html>
