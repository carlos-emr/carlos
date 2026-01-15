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
<%@ page
        import="ca.openosp.openo.commn.dao.FaxConfigDao, ca.openosp.openo.commn.model.FaxConfig, ca.openosp.openo.commn.model.FaxJob, ca.openosp.openo.commn.dao.FaxJobDao" %>
<%@ page import="ca.openosp.openo.commn.dao.ProviderDataDao, ca.openosp.openo.commn.model.ProviderData" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="java.util.List, java.util.Collections" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.fax" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.fax");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<!DOCTYPE html>

<html>
<head>

    <title>Manage Faxes</title>
    <meta name="viewport" content="width=device-width,initial-scale=1.0">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/bootstrap.min.css" type="text/css"/>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/bootstrap-responsive.css" type="text/css"/>
    <link href="<%=request.getContextPath() %>/css/datepicker.css" rel="stylesheet" type="text/css"/>
    <link rel="stylesheet"
          href="<%=request.getContextPath() %>/js/jquery_css/smoothness/jquery-ui-1.10.2.custom.min.css"/>

    <script type="text/javascript" src="<%=request.getContextPath() %>/js/bootstrap.min.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery-1.9.1.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery-ui-1.10.2.custom.min.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/js/bootstrap-datepicker.js"></script>

    <script type="text/javascript">


        $(document).ready(function () {
            $(document).tooltip();

            var url = "<%= request.getContextPath() %>/demographic/SearchDemographic.do?jqueryJSON=true&activeOnly=true";

            $("#autocompletedemo").autocomplete({
                source: url,
                minLength: 2,

                focus: function (event, ui) {
                    $("#autocompletedemo").val(ui.item.label);
                    return false;
                },
                select: function (event, ui) {
                    $("#autocompletedemo").val(ui.item.label);
                    $("#demographic_no").val(ui.item.value);
                    return false;
                }
            });

            $("#reportForm").submit(function (event) {
                // Stop form from submitting normally
                event.preventDefault();
                // Get some values from elements on the page:
                var data = $(this).serialize();
                var url = $(this).attr("action");

                var post = $.post(url, data);

                post.done(function (resultdata) {
                    $("#results").empty().append(resultdata);
                });

                return false;
            });
        });

        function getPageCount(id, callback) {
            var url = "<%=request.getContextPath()%>/admin/ManageFaxes.do?method=getPageCount&jobId=" + id;
            var post = $.post(url, function (resultdata) {
                if (id == resultdata.jobId && typeof callback == "function") {
                    callback(resultdata.pageCount);
                }
            });
        }

        function view(id) {

            getPageCount(id, function (pageCount) {

                var url = "<%=request.getContextPath()%>/admin/ManageFaxes.do?method=viewFax&showAs=image&jobId=" + id;
                var imageContainer = $("<ul />")
                    .css("list-style-type", "none")
                    .css("padding", "0px")
                    .css("margin", "0px");

                // run loop for number of pages in PDF
                for (var i = 0; i < pageCount; i++) {
                    var imageItem = $("<li />");
                    var image = $("<img />")
                        .attr("src", url + "&pageNumber=" + (i + 1))
                        .css("background-image", "url('<%=request.getContextPath()%>/images/loader.gif')")
                        .css("background-position", "50% 50%")
                        .css("background-repeat", "no-repeat");

                    image.appendTo(imageItem);
                    imageItem.appendTo(imageContainer);
                }

                var modal = $("<div />")
                    .attr("id", "viewFaxModal")
                    .html(imageContainer);

                const documentTitle = $("#faxType_" + id).text() + ": " + $("#patientName_" + id).text();

                modal.dialog({
                    title: documentTitle,
                    height: 600,
                    modal: true,
                    draggable: false,
                    resizable: false,
                    width: 750
                });

                modal.dialog("open");

            });
        }

        function resend(id, faxNumber, status) {

            var answer = prompt("Is this the correct fax number?", faxNumber);

            if (answer == null) {
                return false;
            }

            if (answer.match("^\\d{10,11}$")) {

                var url = $("#reportForm").attr("action");
                var data = "method=ResendFax&jobId=" + id + "&faxNumber=" + answer;

                // disable the action buttons
                $(".btn-link").prop("disabled", true);

                $.ajax({
                    url: url,
                    method: 'POST',
                    data: data,
                    dataType: "json",
                    success: function (data) {
                        $(".btn-link").prop("disabled", false);
                        if (data.success) {
                            $("#resend_" + id).prop("disabled", true).css("color", "green").css("font-weight", "bold").text("re-sent");
                            $("#cancel_" + id).remove();
                            $("#complete_" + id).remove();
                            $('#' + status).text("RESENT");
                        } else {
                            $("#resend_" + id).prop("disabled", true).css("color", "red").css("font-weight", "bold").text("error");
                            alert("An error occurred trying to resend your fax.  Please contact your system administrator");
                        }
                    }
                });
            } else {
                alert("Fax numbers must not contain punctuation");

            }

            return false;

        }

        function cancel(jobId, status) {

            var answer = confirm("Are you sure you want to remove this fax from the queue?");

            if (answer == null || !answer) {
                return false;
            }

            // disable the action buttons
            $(".btn-link").prop("disabled", true);

            var url = $("#reportForm").attr("action");
            var data = "method=CancelFax&jobId=" + jobId;

            $.ajax({
                url: url,
                method: 'POST',
                data: data,
                dataType: "json",
                success: function (data) {
                    $(".btn-link").prop("disabled", false);
                    if (data.success) {
                        $("#cancel_" + jobId).remove();
                        $('#' + status).text("CANCELLED");
                    } else {
                        $("#cancel_" + jobId).prop("disabled", true).css("color", "red").css("font-weight", "bold").text("error");
                        alert("OSCAR WAS UNABLE TO CANCEL THE FAX");
                    }
                }
            });

            return false;
        }

        function complete(id, status) {

            var answer = confirm("Are you sure you want to resolve this fax status?");

            if (answer == null || !answer) {
                return false;
            }

            // disable the action buttons
            $(".btn-link").prop("disabled", true);

            var url = $("#reportForm").attr("action");
            var data = "method=SetCompleted&jobId=" + id;

            $.ajax({
                url: url,
                method: 'POST',
                data: data,
                success: function (data) {
                    $(".btn-link").prop("disabled", false);
                    $("#complete_" + id).remove();
                    $('#' + status).text("RESOLVED");
                }
            });
        }

        function resetForm() {
            $("#demographic_no").val("");
            $("#reportForm").trigger("reset");
            $("#results").empty();
            return false;
        }

    </script>
    <style type="text/css">
        form {
            border: none;
            margin: 0px;
            padding: 0px;
        }
    </style>

</head>
<body>

<jsp:include page="/images/spinner.jsp" flush="true"/>

<div id="bodyrow" class="container-fluid">
    <div id="bodycolumn">

        <form id="reportForm" action="<%=request.getContextPath()%>/admin/ManageFaxes.do" onsubmit="ShowSpin(true);">

            <input type="hidden" name="method" value="fetchFaxStatus"/>

            <div class="row">
                <legend>Search Faxes</legend>
                <div class="input-append span3">

                    <input class="span2" type="text" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                           placeholder="From" id="dateBegin" name="dateBegin" required/>
                    <span class="add-on">
                		<i class="icon-calendar"></i>
                	</span>
                </div>

                <div class="input-append span3">

                    <input class="span2" type="text" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                           placeholder="To" id="dateEnd" name="dateEnd" required/>
                    <span class="add-on">
                		<i class="icon-calendar"></i>
                	</span>
                </div>
                <div class="span6">
                    <input class="span6" type="text" placeholder="Pt. Name (last, first)" id="autocompletedemo"/>
                    <input type="hidden" id="demographic_no" name="demographic_no" value="">
                </div>

            </div>

            <div class="row">
                <div class="span5">
                    <select class="span5" name="oscarUser">
                        <option value="-1">Provider</option>

                        <%
                            ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);
                            List<ProviderData> providerDataList = providerDataDao.findAll(false);
                            Collections.sort(providerDataList, ProviderData.LastNameComparator);

                            for (ProviderData providerData : providerDataList) {
                        %>
                        <option value="<%=providerData.getId()%>"><%=providerData.getLastName() + ", " + providerData.getFirstName()%>
                        </option>

                        <%
                            }
                        %>
                    </select>
                </div>
                <div class="span5">
                    <select class="span5" name="team">
                        <option value="-1">Team</option>
                        <%
                            FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
                            List<FaxConfig> faxConfigList = faxConfigDao.findAll(null, null);

                            for (FaxConfig faxConfig : faxConfigList) {
                        %>
                        <option value="<%=faxConfig.getFaxUser()%>"><%=faxConfig.getFaxUser() %>
                        </option>
                        <%
                            }
                        %>
                    </select>
                </div>
                <div class="span2">
                    <select class="span2" name="status">
                        <option value="-1">Status</option>

                        <%
                            for (FaxJob.STATUS status : FaxJob.STATUS.values()) {
                        %>
                        <option value="<%=status%>"><%=status%>
                        </option>
                        <%
                            }
                        %>
                    </select>
                </div>
            </div>

            <div class="row">
                <div class="span12">
                    <input class="btn btn-default" type="submit" value="Fetch Faxes"/>
                    <input class="btn btn-default" type="button" value="Reset" onclick="return resetForm();"/>
                </div>
            </div>

        </form>

        <div class="row">
            <div class="span12">
                <div id="results" style="margin-top:20px;">
                    <!-- container -->
                </div>
            </div>
        </div>
        <div>
            <p>Fax status definitions:</p>
            <dl class="dl-horizontal">
                <dt>RECEIVED</dt>
                <dd>is the status of a fax that was successfully RECEIVED from the fax gateway service. Click on View in
                    the Action column for a preview of the fax.
                </dd>
                <dt>CANCELLED</dt>
                <dd>fax that was cancelled by the EMR user while the fax had a SENT or WAITING status - or - the fax was
                    cancelled by the user from the fax gateway web interface
                </dd>
                <dt>UNKNOWN</dt>
                <dd>on an extremely rare occasion when the status of a fax job is unidentifiable</dd>
                <dt>WAITING</dt>
                <dd>fax that is waiting to be sent to the fax gateway.</dd>
                <dt>SENT</dt>
                <dd>fax that has been successfully sent to the fax gateway.</dd>
                <dt>COMPLETE</dt>
                <dd>fax that was successfully sent to, and fully processed by, the fax gateway service. Click on View in
                    the Action column for a preview of the fax. The ability to Resend the fax is available during this
                    status.
                </dd>
                <dt>ERROR</dt>
                <dd>fax failure. Fax job failed sending to the fax gateway - OR - the fax gateway failed to send the fax
                    after n attempts. Hover the mouse pointer over the ERROR status to display the reason for the error.
                    Click on Resend in the Action column to send the fax again. Re-send will not work for errors such as
                    &quot;destination not a fax&quot;, &quot;no answer&quot;, &quot;invalid fax number&quot; etc...
                    Re-sending the fax will allow input of a different phone number if needed.
                </dd>
                <dt>RE-SENT</dt>
                <dd>a previously failed or completed fax job that was re-sent to the fax gateway</dd>
                <dt>RESOLVED</dt>
                <dd>a fax ERROR that was dismissed by selecting &quot;Mark Complete&quot; in the Action column. &quot;Mark
                    Complete&quot; is useful to hide ERRORs that have been resolved by other means.
                </dd>
            </dl>
        </div>

    </div>    <!-- body column -->
</div> <!-- body row -->

<script language="javascript">
    var startDate = $("#dateBegin").datepicker({
        format: "yyyy-mm-dd"
    });

    var endDate = $("#dateEnd").datepicker({
        format: "yyyy-mm-dd"
    });
</script>

</body>
</html>
