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
<%@ page
        import="io.github.carlos_emr.carlos.commn.dao.FaxConfigDao, io.github.carlos_emr.carlos.commn.model.FaxConfig, io.github.carlos_emr.carlos.commn.model.FaxJob, io.github.carlos_emr.carlos.commn.dao.FaxJobDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao, io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="java.util.List, java.util.Collections" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.fax" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.fax");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
%>


<!DOCTYPE html>

<html>
<head>

    <title><fmt:message key="admin.manageFaxes.title"/></title>
    <meta name="viewport" content="width=device-width,initial-scale=1.0">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" type="text/css"/>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css" type="text/css"/>
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet" type="text/css"/>
    <link rel="stylesheet"
          href="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.css"/>

    <script type="text/javascript" src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <script type="text/javascript" src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>

    <script type="text/javascript">
        const manageFaxesResendPrompt = "<fmt:message key='admin.manageFaxes.resendPrompt'/>";
        const manageFaxesResendFailed = "<fmt:message key='admin.manageFaxes.resendFailed'/>";
        const manageFaxesFaxNumbersInvalid = "<fmt:message key='admin.manageFaxes.faxNumbersInvalid'/>";
        const manageFaxesCancelConfirm = "<fmt:message key='admin.manageFaxes.cancelConfirm'/>";
        const manageFaxesCancelFailed = "<fmt:message key='admin.manageFaxes.cancelFailed'/>";
        const manageFaxesCompleteConfirm = "<fmt:message key='admin.manageFaxes.completeConfirm'/>";
        const manageFaxesResentLabel = "<fmt:message key='admin.manageFaxes.resentLabel'/>";
        const manageFaxesErrorLabel = "<fmt:message key='admin.manageFaxes.errorLabel'/>";
        const manageFaxesResentStatusLabel = "<fmt:message key='admin.manageFaxes.status.RESENT'/>";
        const manageFaxesCancelledStatusLabel = "<fmt:message key='admin.manageFaxes.status.CANCELLED'/>";
        const manageFaxesResolvedStatusLabel = "<fmt:message key='admin.manageFaxes.status.RESOLVED'/>";

        $(document).ready(function () {

            var searchDemoUrl = "<%= request.getContextPath() %>/demographic/SearchDemographic";

            $("#autocompletedemo").autocomplete({
                source: function (req, res) {
                    $.ajax({
                        url: searchDemoUrl,
                        type: 'POST',
                        data: { jqueryJSON: 'true', activeOnly: 'true', term: req.term },
                        success: function (data) { res(data); },
                        error: function () { res([]); }
                    });
                },
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
            var url = "<%=request.getContextPath()%>/admin/ManageFaxes?method=getPageCount&jobId=" + id;
            var post = $.post(url, function (resultdata) {
                if (id == resultdata.jobId && typeof callback == "function") {
                    callback(resultdata.pageCount);
                }
            });
        }

        function view(id) {

            getPageCount(id, function (pageCount) {

                var url = "<%=request.getContextPath()%>/admin/ManageFaxes?method=viewFax&showAs=image&jobId=" + id;
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

            var answer = prompt(manageFaxesResendPrompt, faxNumber);

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
                            $("#resend_" + id).prop("disabled", true).css("color", "green").css("font-weight", "bold").text(manageFaxesResentLabel);
                            $("#cancel_" + id).remove();
                            $("#complete_" + id).remove();
                            $('#' + status).text(manageFaxesResentStatusLabel);
                        } else {
                            $("#resend_" + id).prop("disabled", true).css("color", "red").css("font-weight", "bold").text(manageFaxesErrorLabel);
                            alert(manageFaxesResendFailed);
                        }
                    }
                });
            } else {
                alert(manageFaxesFaxNumbersInvalid);

            }

            return false;

        }

        function cancel(jobId, status) {

            var answer = confirm(manageFaxesCancelConfirm);

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
                        $('#' + status).text(manageFaxesCancelledStatusLabel);
                    } else {
                        $("#cancel_" + jobId).prop("disabled", true).css("color", "red").css("font-weight", "bold").text(manageFaxesErrorLabel);
                        alert(manageFaxesCancelFailed);
                    }
                }
            });

            return false;
        }

        function complete(id, status) {

            var answer = confirm(manageFaxesCompleteConfirm);

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
                    $('#' + status).text(manageFaxesResolvedStatusLabel);
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

<jsp:include page="/WEB-INF/jsp/includes/spinner.jspf" flush="true"/>

<div id="bodyrow" class="container-fluid">
    <div id="bodycolumn">

        <form id="reportForm" action="<%=request.getContextPath()%>/admin/ManageFaxes" onsubmit="ShowSpin(true);">

            <input type="hidden" name="method" value="fetchFaxStatus"/>

            <div class="row">
                <legend><fmt:message key="admin.manageFaxes.searchLegend"/></legend>
                <div class="input-group col-md-3">

                    <input class="form-control" type="text" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                           placeholder="<fmt:message key='admin.manageFaxes.from'/>" id="dateBegin" name="dateBegin" required/>
                    <span class="input-group-text">
                		<i class="fa-solid fa-calendar"></i>
                	</span>
                </div>

                <div class="input-group col-md-3">

                    <input class="form-control" type="text" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                           placeholder="<fmt:message key='admin.manageFaxes.to'/>" id="dateEnd" name="dateEnd" required/>
                    <span class="input-group-text">
                		<i class="fa-solid fa-calendar"></i>
                	</span>
                </div>
                <div class="col-md-6">
                    <input class="form-control" type="text" placeholder="<fmt:message key='admin.manageFaxes.patientPlaceholder'/>" id="autocompletedemo"/>
                    <input type="hidden" id="demographic_no" name="demographic_no" value="">
                </div>

            </div>

            <div class="row">
                <div class="col-md-5">
                    <select class="form-select" name="oscarUser">
                        <option value="-1"><fmt:message key="admin.manageFaxes.provider"/></option>

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
                <div class="col-md-5">
                    <select class="form-select" name="team">
                        <option value="-1"><fmt:message key="admin.manageFaxes.team"/></option>
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
                <div class="col-md-2">
                    <select class="form-select" name="status">
                        <option value="-1"><fmt:message key="admin.manageFaxes.statusLabel"/></option>

                        <%
                            for (FaxJob.STATUS status : FaxJob.STATUS.values()) {
                                String statusLabel = bundle.getString("admin.manageFaxes.status." + status.name());
                        %>
                        <option value="<%=status%>"><%=statusLabel%>
                        </option>
                        <%
                            }
                        %>
                    </select>
                </div>
            </div>

            <div class="row">
                <div class="col-md-12">
                    <input class="btn btn-secondary" type="submit" value="<fmt:message key='admin.manageFaxes.fetch'/>"/>
                    <input class="btn btn-secondary" type="button" value="<fmt:message key='global.reset'/>" onclick="return resetForm();"/>
                </div>
            </div>

        </form>

        <div class="row">
            <div class="col-md-12">
                <div id="results" style="margin-top:20px;">
                    <!-- container -->
                </div>
            </div>
        </div>
        <div>
            <p><fmt:message key="admin.manageFaxes.statusDefinitions"/></p>
            <dl class="row">
                <dt><fmt:message key="admin.manageFaxes.status.RECEIVED"/></dt>
                <dd><fmt:message key="admin.manageFaxes.statusDescription.RECEIVED"/></dd>
                <dt><fmt:message key="admin.manageFaxes.status.CANCELLED"/></dt>
                <dd><fmt:message key="admin.manageFaxes.statusDescription.CANCELLED"/></dd>
                <dt><fmt:message key="admin.manageFaxes.status.UNKNOWN"/></dt>
                <dd><fmt:message key="admin.manageFaxes.statusDescription.UNKNOWN"/></dd>
                <dt><fmt:message key="admin.manageFaxes.status.WAITING"/></dt>
                <dd><fmt:message key="admin.manageFaxes.statusDescription.WAITING"/></dd>
                <dt><fmt:message key="admin.manageFaxes.status.SENT"/></dt>
                <dd><fmt:message key="admin.manageFaxes.statusDescription.SENT"/></dd>
                <dt><fmt:message key="admin.manageFaxes.status.COMPLETE"/></dt>
                <dd><fmt:message key="admin.manageFaxes.statusDescription.COMPLETE"/></dd>
                <dt><fmt:message key="admin.manageFaxes.status.ERROR"/></dt>
                <dd><fmt:message key="admin.manageFaxes.statusDescription.ERROR"/></dd>
                <dt><fmt:message key="admin.manageFaxes.status.RESENT"/></dt>
                <dd><fmt:message key="admin.manageFaxes.statusDescription.RESENT"/></dd>
                <dt><fmt:message key="admin.manageFaxes.status.RESOLVED"/></dt>
                <dd><fmt:message key="admin.manageFaxes.statusDescription.RESOLVED"/></dd>
            </dl>
        </div>

    </div>    <!-- body column -->
</div> <!-- body row -->

<script language="javascript">
    flatpickr("#dateBegin", {dateFormat: "Y-m-d", allowInput: true});
    flatpickr("#dateEnd", {dateFormat: "Y-m-d", allowInput: true});
</script>

</body>
</html>
