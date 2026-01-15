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

<%@page import="ca.openosp.openo.commn.model.FaxConfig" %>
<%@page import="ca.openosp.openo.commn.dao.QueueDao" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>

<%@page import="java.util.List" %>
<%@page import="java.util.HashMap" %>
<%@ page import="ca.openosp.openo.managers.FaxManager" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="org.owasp.encoder.Encode" %>

<!DOCTYPE html>
<html>
<head>
    <title>Manage Fax</title>

    <meta name="viewport" content="width=device-width,initial-scale=1.0">

    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/bootstrap.css" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/font-awesome.min.css" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/bootstrap-responsive.css" type="text/css">

    <script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery-1.9.1.js"></script>

    <script type="text/javascript">

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
                            $("#msg").html("Configuration saved!");
                            $('.alert').removeClass('alert-error');
                            $('.alert').addClass('alert-success');
                            $('.alert').show();
                        } else {
                            $("#msg").html("There was a problem saving your configuration.  Check the logs for further details.");
                            $('.alert').removeClass('alert-success');
                            $('.alert').adqdClass('alert-error');
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
            $(div).find("#id").val("-1");
            $(div).find("#id").attr("id", "id" + userCount);


            $(div).find("#id").val("-1");
            $(div).find("select").val("-1");

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
                data: 'method=getFaxSchedularStatus',
                success: function (data) {
                    $('#restartFaxSchedulerBtn').prop('disabled', data.isRunning);
                    $("#faxStatusDetails").text(data.faxSchedularStatus).css("color", data.isRunning ? "black" : "red");
                    HideSpin();
                }
            });
        }

        function rebootFaxSchedular() {
            $.ajax({
                url: "<%=request.getContextPath() %>/admin/ManageFax.do",
                method: 'POST',
                data: 'method=restartFaxScheduler',
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
<div class="container-fluid">
    <form id="configFrm" method="post">
        <input type="hidden" name="method" value="configure"/>
        <div id="bodyrow" class="row">

            <legend>Fax Server Credentials</legend>
            <div class="span12">

                <div class="row">
                    <div class="span12">
                        <label for="faxUrl"> Fax Server URL</label>
                        <input class="span12" id="faxUrl" type="text" name="faxUrl" placeholder="fax web service URL"
                               value="<%=Encode.forHtmlAttribute( ! faxConfigList.isEmpty() ? faxConfigList.get(0).getUrl() : "")%>"/>
                    </div>
                </div>

                <div class="row">
                    <div class="span6">
                        <label for="faxServiceUser">Fax Server Username</label>
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
                        <label for="faxServicePasswd">Fax Server Password</label>
                        <input class="span6" id="faxServicePasswd" type="password" name="sitePasswd"
                               value="<%=Encode.forHtmlAttribute( faxServicePassword )%>"/>
                    </div>

                </div>

                <!-- #Fax Status -->
                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin.fax.restart" rights="r"
                                   reverse="<%=false%>">
                    <div class="row">
                        <div class="span12" style="display: ruby">
                            <label>Fax Server Connection Status:&nbsp;</label><label id="faxStatusDetails"></label>
                        </div>
                        <div class="span12">
                            <button id="restartFaxSchedulerBtn" type="button" onclick="rebootFaxSchedular()" disabled>
                                Restart Connection
                            </button>
                        </div>
                    </div>
                </security:oscarSec>

            </div>
        </div>
        <div id="content" class="row">
            <legend>Fax Gateway Accounts <a class="pull-right" style="margin-right:40px;" href=""
                                            onclick="addUser();return false;">+Add</a></legend>

            <div class="span12">

                <% do { %>
                <div class="row" id="user<%=count == 0 ? "" : count%>">
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

                                            out.print(">" + queueMap.get(queueId) + "</option>");
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
                                <a class="pull-right" style="color:red;" id="remove" href=""
                                   onclick="removeUser(<%=count%>);return false;">-Delete</a>
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
            <input class="btn btn-default" id="submit" type="submit" disabled value="Save"/>
        </div>
    </form>

    <div id="msg" class="row alert" style="display:none;">
    </div>
</div>    <!-- end container -->


</body>
</html>
