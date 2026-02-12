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

        $(document).keypress(function () {
            $("#submit").prop("disabled", false);
            $(this).off();
        });


        $(document).ready(function () {

            $("select").change(function () {
                $("#submit").prop("disabled", false);
                $(this).off();
            });

            $("#submit").click(function (e) {
                e.preventDefault();

                var url = "<%=request.getContextPath() %>/admin/ManageFax.do?method=configure";
                var data = $("#configFrm").serialize();

                $.ajax({
                    url: url,
                    method: 'POST',
                    data: data,
                    dataType: "json",
                    success: function (data) {

                        if (data.success) {
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
                    }
                });

            });

            $("input[type='radio']").click(function () {
                $("#submit").prop("disabled", false);
                setState(this);
            });

            getFaxSchedularStatus();

        });

        <%
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        FaxManager faxManager = SpringUtils.getBean(FaxManager.class);
        List<FaxConfig> faxConfigList = faxManager.getFaxConfigurationAccounts(loggedInInfo);

        Integer count = 0;

        QueueDao queueDao = SpringUtils.getBean(QueueDao.class);
        HashMap<Integer,String>queueMap = queueDao.getHashMapOfQueues();

        %>


        var userCount = <%=faxConfigList.isEmpty() ? "0" : faxConfigList.size()%>;

        function addUser() {
            ++userCount;

            var userDivId = "user" + userCount;
            var div = $("#user").clone(true, true);

            $(div).attr("id", userDivId);
            $(div).find("#faxUser").attr("id", "faxUser" + userCount);
            $(div).find("#faxPasswd").attr("id", "faxPasswd" + userCount);
            $(div).find("#senderEmail").attr("id", "senderEmail" + userCount);
            $(div).find("#accountName").attr("id", "accountName" + userCount);
            $(div).find("#faxNumber").attr("id", "faxNumber" + userCount);

            $(div).find("#remove").attr("id", "r" + userCount);
            $(div).find("#r" + userCount).attr("onclick", "removeUser(" + userCount + ");return false;");
            $(div).find('input[type="text"], input[type="password"], input[type="email"]').val("");
            $(div).find('input[type="radio"]').prop('checked', false);
            $(div).find("#on").attr("name", "active" + userCount);
            $(div).find("#of").attr("name", "active" + userCount);
            $(div).find("#on").attr("id", "on" + userCount);
            $(div).find("#of").attr("id", "of" + userCount);
            $(div).find("#activeState").val("");
            $(div).find("#activeState").attr("id", "activeState" + userCount);
            $(div).find("#download_on").attr("name", "download" + userCount);
            $(div).find("#download_of").attr("name", "download" + userCount);
            $(div).find("#download_on").attr("id", "download_on" + userCount);
            $(div).find("#download_of").attr("id", "download_of" + userCount);
            $(div).find("#downloadState").val("");
            $(div).find("#downloadState").attr("id", "downloadState" + userCount);
            $(div).find("#providerType").attr("id", "providerType" + userCount);
            $(div).find("#providerType" + userCount).val("MIDDLEWARE");
            $(div).find("#id").val("-1");
            $(div).find("#id").attr("id", "id" + userCount);


            $(div).find("#id").val("-1");
            $(div).find("select[name='inboxQueue']").val("-1");

            var theSpan = document.createElement("span");
            //<div class="span12">
            theSpan.setAttribute("class", "span12");
            $(div).appendTo(theSpan);

            $("#content").append(theSpan);

            //$(div).appendTo("#content");
            $("#faxUser" + userCount).focus();
            $("#submit").prop("disabled", false);
        }

        function removeUser(divCount) {
            var divId;

            $("#submit").prop("disabled", false);

            if (divCount > 0) {
                divId = "user" + divCount;
                $("#" + divId).remove();
            } else {
                divId = "user";
                $('#' + divId + ' input[type="text"]').val("");
                $('#' + divId + ' input[type="password"]').val("");
                $('#' + divId + ' input[type="email"]').val("");
                $('#' + divId + ' input[type="radio"]').attr("checked", false);
                $('#' + divId + ' input[type="hidden"]').val("");
                $('#' + divId + ' select').val("-1");
            }
        }

        function setState(elem) {
            var id;
            if (elem.id.startsWith("download")) {
                id = "#downloadState" + elem.id.substring(11);
            } else {
                id = "#activeState" + elem.id.substring(2);
            }
            $(id).val($(elem).val());
        }

        function getFaxSchedularStatus() {
            $.ajax({
                url: "<%=request.getContextPath() %>/admin/ManageFax.do",
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
                url: "<%=request.getContextPath() %>/admin/ManageFax.do",
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
        <div id="bodyrow" class="row">

            <legend class="fax-section-title"><i class="fas fa-server"></i> Fax Server Credentials</legend>
            <div class="span12">

                <div class="row">
                    <div class="span12">
                        <label for="faxUrl"><i class="fas fa-link"></i> Fax Server URL</label>
                        <input class="span12" id="faxUrl" type="text" name="faxUrl" placeholder="fax web service URL"
                               value="<%=Encode.forHtmlAttribute( ! faxConfigList.isEmpty() ? faxConfigList.get(0).getUrl() : "")%>"/>
                        <small class="muted">For middleware mode use relay service URL. For SRFax mode use SRFax API endpoint URL.</small>
                    </div>
                </div>

                <div class="row">
                    <div class="span6">
                        <label for="faxServiceUser"><i class="fas fa-user"></i> Fax Server Username</label>
                        <input class="span6" id="faxServiceUser" type="text" name="siteUser"
                               value="<%=Encode.forHtmlAttribute( ! faxConfigList.isEmpty() ? faxConfigList.get(0).getSiteUser() : "" )%>"/>
                    </div>

                    <div class="span6">
                        <%
                            String faxServicePassword = "";

                            if (!faxConfigList.isEmpty() && faxConfigList.get(count) != null && faxConfigList.get(count).getPasswd() != null
                                    && faxConfigList.get(count).getPasswd().length() > 0) {
                                faxServicePassword = "**********";
                            }

                        %>
                        <label for="faxServicePasswd"><i class="fas fa-key"></i> Fax Server Password</label>
                        <input class="span6" id="faxServicePasswd" type="password" name="sitePasswd"
                               value="<%=Encode.forHtmlAttribute( faxServicePassword )%>"/>
                    </div>

                </div>

                <!-- #Fax Status -->
                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.fax.restart" rights="r"
                                   reverse="<%=false%>">
                    <div class="row">
                        <div class="span12" style="display: ruby">
                            <label class="status-label"><i class="fas fa-heartbeat"></i> Fax Server Connection Status:</label><label id="faxStatusDetails"></label>
                        </div>
                        <div class="span12" style="display: ruby">
                            <label class="status-label"><i class="fas fa-clock"></i> Last Successful Poll:</label><label id="faxLastRunDetails">Never</label>
                        </div>
                        <div class="span12" style="display: ruby">
                            <label class="status-label"><i class="fas fa-exclamation-triangle"></i> Last Error:</label><label id="faxLastErrorDetails">None</label>
                        </div>
                        <div class="span12">
                            <button id="restartFaxSchedulerBtn" class="btn btn-warning" type="button" onclick="rebootFaxSchedular()" disabled>
                                <i class="fas fa-sync-alt"></i> Restart Connection
                            </button>
                        </div>
                    </div>
                </security:oscarSec>

            </div>
        </div>
        <div id="content" class="row">
            <legend class="fax-section-title"><i class="fas fa-satellite-dish"></i> Fax Gateway Accounts <a class="pull-right btn btn-mini btn-primary" style="margin-right:20px;" href="" onclick="addUser();return false;"><i class="fas fa-plus"></i> Add Account</a></legend>

            <div class="span12">

                <% do { %>
                <div class="row fax-card" id="user<%=count == 0 ? "" : count%>">
                    <div class="span12">

                        <div class="row">
                            <div class="span6">
                                <label for="faxUser<%=count == 0 ? "" : count%>">User</label>
                                <input class="span6" type="text" id="faxUser<%=count == 0 ? "" : count%>" name="faxUser"
                                       value="<%=Encode.forHtmlAttribute( faxConfigList.isEmpty() ? "" : faxConfigList.get(count).getFaxUser() )%>"/>
                                <input type="hidden" id="id<%=count == 0 ? "" : count%>" name="id"
                                       value="<%=faxConfigList.isEmpty() ? "-1" : faxConfigList.get(count).getId()%>"/>

                            </div>

                            <div class="span6">
                                <label for="faxPasswd<%=count == 0 ? "" : count%>">Password</label>
                                <%
                                    String faxPassword = "";

                                    if (!faxConfigList.isEmpty() && faxConfigList.get(count) != null && faxConfigList.get(count).getFaxPasswd() != null
                                            && faxConfigList.get(count).getFaxPasswd().length() > 0) {
                                        faxPassword = "**********";
                                    }

                                %>
                                <input class="span6" type="password" id="faxPasswd<%=count == 0 ? "" : count%>"
                                       name="faxPassword" value="<%=Encode.forHtmlAttribute( faxPassword )%>"/>
                            </div>
                        </div>
                        <div class="row">
                            <div class="span6">

                                <label for="faxNumber<%=count == 0 ? "" : count%>">Fax Number</label>
                                <input class="span6" type="text" id="faxNumber<%=count == 0 ? "" : count%>"
                                       name="faxNumber"
                                       value="<%=Encode.forHtmlAttribute( faxConfigList.isEmpty() ? "" : faxConfigList.get(count).getFaxNumber() )%>"/>
                            </div>

                            <div class="span6">
                                <label for="senderEmail<%=count == 0 ? "" : count%>">Email</label>

                                <input class="span6" type="email" id="senderEmail<%=count == 0 ? "" : count%>"
                                       name="senderEmail" placeholder="Account email"
                                       value="<%=Encode.forHtmlAttribute(faxConfigList.isEmpty() ? "" : faxConfigList.get(count).getSenderEmail())%>"/>
                            </div>
                        </div>
                        <div class="row">
                            <div class="span6">
                                <label for="inBoxQueue<%=count == 0 ? "" : count%>">Inbox Queue</label>
                                <select class="span6" id="inBoxQueue<%=count == 0 ? "" : count%>" name="inboxQueue">
                                    <option value="-1">-</option>
                                    <%
                                        for (Integer queueId : queueMap.keySet()) {

                                            out.print("<option value='" + queueId + "'");

                                            if (!faxConfigList.isEmpty()) {

                                                if (faxConfigList.get(count).getQueue().compareTo(queueId) == 0) {
                                                    out.print(" selected");
                                                }
                                            }

                                            out.print(">" + Encode.forHtml(queueMap.get(queueId)) + "</option>");
                                        }
                                    %>
                                </select>

                            </div>
                            <div class="span6">
                                <label for="accountName<%= count == 0 ? "" : count %>">Account Name</label>
                                <input type="text" name="accountName" id='accountName<%= count == 0 ? "" : count %>'
                                       value='<%= Encode.forHtmlAttribute(faxConfigList.isEmpty() ? "" : faxConfigList.get(count).getAccountName()) %>'/>
                            </div>
                        </div>
                        <div class="row">
                            <div class="span6">
                                <label for="providerType<%=count == 0 ? "" : count%>">Provider Type</label>
                                <select class="span6" id="providerType<%=count == 0 ? "" : count%>" name="providerType">
                                    <option value="MIDDLEWARE" <%=faxConfigList.isEmpty() || faxConfigList.get(count).getProviderType() == FaxConfig.ProviderType.MIDDLEWARE ? "selected" : ""%>>Middleware Relay</option>
                                    <option value="SRFAX" <%=!faxConfigList.isEmpty() && faxConfigList.get(count).getProviderType() == FaxConfig.ProviderType.SRFAX ? "selected" : ""%>>SRFax Direct API</option>
                                </select>
                                <small class="fax-muted"><i class="fas fa-info-circle"></i> SRFax inbound duplicate control uses unread-only fetch and mark-as-read after download.</small>
                            </div>
                        </div>
                        <div class="row">
                            <div class="span6">
                                <label>Enable/Disable Gateway</label>

                                <label class="radio inline control-label">
                                    <input type="radio" id="on<%=count == 0 ? "" : count %>"
                                           name="active<%=count == 0 ? "" : count%>"
                                           value="true" <%=faxConfigList.isEmpty() ? "" : faxConfigList.get(count).isActive() ? "checked" : ""%>  />
                                    On</label>
                                <label class="radio inline control-label">
                                    <input type="radio" id="of<%=count == 0 ? "" : count %>"
                                           name="active<%=count == 0 ? "" : count%>"
                                           value="false" <%=faxConfigList.isEmpty() ? "" : faxConfigList.get(count).isActive() ? "" : "checked"%> />
                                    Off</label>

                                <input type="hidden" id="activeState<%=count == 0 ? "" : count%>" name="activeState"
                                       value="<%=faxConfigList.isEmpty() ? "" : faxConfigList.get(count).isActive()%>"/>
                            </div>
                            <div class="span6">
                                <label>Enable/Disable Receiving Faxes (If Gateway Enabled)</label>

                                <label class="radio inline control-label">
                                    <input type="radio" id="download_on<%=count == 0 ? "" : count %>"
                                           name="download<%=count == 0 ? "" : count%>"
                                           value="true" <%=faxConfigList.isEmpty() ? "" : faxConfigList.get(count).isDownload() ? "checked" : ""%>  />
                                    On</label>
                                <label class="radio inline control-label">
                                    <input type="radio" id="download_of<%=count == 0 ? "" : count %>"
                                           name="download<%=count == 0 ? "" : count%>"
                                           value="false" <%=faxConfigList.isEmpty() ? "" : faxConfigList.get(count).isDownload() ? "" : "checked"%> />
                                    Off</label>
                                <input type="hidden" id="downloadState<%=count == 0 ? "" : count%>" name="downloadState"
                                       value="<%=faxConfigList.isEmpty() ? "" : faxConfigList.get(count).isDownload()%>"/>
                            </div>
                        </div>

                        <% if (count <= faxConfigList.size()) { %>
                        <div class="row">
                            <div class="span12">
                                <a class="pull-right btn btn-mini btn-danger" id="remove" href="" onclick="removeUser(<%=count%>);return false;"><i class="fas fa-trash-alt"></i> Delete</a>
                            </div>
                        </div>
                        <%} %>
                    </div> <!--  end master column -->
                </div>    <!-- end account row -->
                <%
                        ++count;
                    } while (count < faxConfigList.size());
                %>

            </div> <!-- end master column -->
        </div> <!-- end content -->

        <div class="row">
            <input class="btn" id="submit" type="submit" disabled value="Save Configuration"/>
        </div>
    </form>

    <div id="msg" class="row alert" role="alert" style="display:none;">
    </div>
</div>    <!-- end container -->


</body>
</html>
