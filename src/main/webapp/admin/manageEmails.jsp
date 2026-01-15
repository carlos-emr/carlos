<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.email" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.email");%>
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
    <c:set var="ctx" value="${ pageContext.request.contextPath }" scope="page"/>
    <link href="${ctx}/library/bootstrap/5.0.2/css/bootstrap.min.css" rel="stylesheet" type="text/css"/>
    <link href="${ctx}/css/datepicker.css" rel="stylesheet" type="text/css"/>
    <link href="${ctx}/library/jquery/jquery-ui.min.css" rel="stylesheet" type="text/css"/>
    <link href="${ctx}/css/font-awesome.min.css" rel="stylesheet">

    <script type="text/javascript" src="${ctx}/library/jquery/jquery-3.6.4.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery.validate.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/jquery/jquery-ui.min.js"></script>
    <script type="text/javascript" src="${ctx}/library/bootstrap/5.0.2/js/bootstrap.bundle.js"></script>
    <script type="text/javascript" src="${ctx}/js/bootstrap-datepicker.js"></script>

    <style type="text/css">
        .search-email-menu {
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
            overflow: hidden;
        }

        .email-status-card {
            box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1);
            overflow: hidden;
        }

        .email-status-card table {
            margin-bottom: 0px;
        }

        .email-status-card table th,
        .email-status-card table td {
            padding: 0px;
        }

        .email-info-table th {
            width: 100px;
            font-weight: bold;
            color: #495057;
        }


        .email-info-table td {
            color: #6c757d;
        }

        .email-status-card .vertical-status-divider {
            border-left: 3px solid #008631;
        }

        .email-status-card .vertical-status-divider-success,
        .email-status-card .vertical-status-divider-resolved {
            border-left: 3px solid #008631 !important;
        }

        .email-status-card .vertical-status-divider-failed {
            border-left: 3px solid #c30010 !important;
        }

        .email-status-card .vertical-status-divider-outbox {
            border-left: 3px solid #0747a1 !important;
        }

        .email-status-card .action-menu {
            float: right;
        }

        .email-status-card .status-and-date {
            float: left;
            display: flex;
            align-items: baseline;
            margin-top: 13px;
        }

        .email-status-card .status-tag {
            padding: 4px 6px;
            font-size: 11px;
            font-weight: 700;
            background-color: #cefad0;
            border-radius: 4px;
            color: #008631;
            margin-right: 5px;
            cursor: pointer;
            user-select: none;
        }

        .email-status-card .status-tag-success,
        .email-status-card .status-tag-resolved {
            background-color: #cefad0 !important;
            color: #008631 !important;
        }

        .email-status-card .status-tag-failed {
            background-color: #ffe1e1 !important;
            color: #c30010 !important;
        }

        .email-status-card .status-tag-outbox {
            background-color: #bbdffb !important;
            color: #0747a1 !important;
        }

        .email-status-card .status-tag-resolved:hover,
        .email-status-card .status-tag-success:hover {
            background-color: #abf7b1 !important;
        }

        .email-status-card .status-tag-failed:hover {
            background-color: #ffcbd1 !important;
        }

        .email-status-card .status-tag-outbox:hover {
            background-color: #90cbf9 !important;
        }

        .email-status-card .date-time {
            font-size: 13px;
            font-weight: 600;
        }

        .popover {
            max-width: 600px !important;
        }
    </style>

    <script type="text/javascript">
        $(document).ready(function () {
            $("#autocompletedemo").autocomplete({
                source: "${ctx}/demographic/SearchDemographic.do?jqueryJSON=true&activeOnly=true",
                minLength: 2,

                change: function (event, ui) {
                    if (!ui.item) {
                        $("#demographic_no").val("");
                    }
                },
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

            $("#emailSearchForm").submit(function (event) {
                event.preventDefault();

                const searchData = $(this).serialize();
                const url = $(this).attr("action");

                const post = $.post(url, searchData);
                post.done(function (resultdata) {
                    HideSpin();
                    if (resultdata && resultdata.trim() !== "") {
                        $("#results").empty().append(resultdata);
                        $('#results [data-bs-toggle="popover"]').popover();
                    } else {
                        $("#results").empty().append("<p>No data available.</p>");
                    }
                });

                post.fail(function (jqXHR, textStatus, errorThrown) {
                    HideSpin();
                    alert("Failed to fetch emails. Please try again later.");
                });

                return false;
            });
        });

        function resolve(emailLogId) {
            const answer = confirm("Are you sure you want to mark this failed email as resolved?");
            if (!answer) {
                return false;
            }

            ShowSpin(true);

            const url = $("#emailSearchForm").attr("action");
            const data = "method=setResolved&logId=" + emailLogId;

            $.ajax({
                url: url,
                method: 'POST',
                data: data,
                success: function (data) {
                    $('#emailStatus' + emailLogId).removeClass('status-tag-failed').addClass('status-tag-resolved');
                    $('#cardBody' + emailLogId).removeClass('vertical-status-divider-failed').addClass('vertical-status-divider-resolved');
                    $("#btnResolve" + emailLogId).remove();
                    $('#emailStatus' + emailLogId).text("RESOLVED");
                    HideSpin();
                },
                error: function (xhr, status, error) {
                    HideSpin();
                    if (xhr.responseJSON) {
                        alert(xhr.responseJSON.errorMessage);
                    } else {
                        alert("500 Internal Server Error: The server encountered an unexpected condition that prevented it from fulfilling the request.");
                    }
                }
            });
        }

        function resend(emailLogId) {
            const url = $("#emailSearchForm").attr("action");
            const data = "method=resendEmail&logId=" + emailLogId;
            window.open(url + "?" + data, "_blank", "width=1100,height=1000");
        }

        function resetForm() {
            $("#emailSearchForm").trigger("reset");
            $("#results").empty();
            return false;
        }
    </script>

<body>
<jsp:include page="/images/spinner.jsp" flush="true"/>

<div id="bodyrow" class="container-fluid">
    <div id="bodycolumn" class="col-sm-12">

        <form id="emailSearchForm" action="${ctx}/admin/ManageEmails.do" onsubmit="ShowSpin(true);">

            <input type="hidden" name="method" value="fetchEmails"/>
            <fieldset class="search-email-menu border px-3 pb-3">
                <legend class="float-none w-auto p-2">Search Emails</legend>
                <div class="row">
                    <div class="col-sm-3 form-group">
                        <label for="dateBegin">From</label>
                        <div class="input-group">
                            <input class="form-control" type="text" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                                   placeholder="YYYY-MM-DD" id="dateBegin" name="dateBegin" required/>
                            <span class="input-group-text"><i class="icon-calendar"></i></span>
                        </div>
                    </div>
                    <div class="col-sm-3 form-group">
                        <label for="dateEnd">To</label>
                        <div class="input-group">
                            <input class="form-control" type="text" pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$"
                                   placeholder="YYYY-MM-DD" id="dateEnd" name="dateEnd" required/>
                            <span class="input-group-text"><i class="icon-calendar"></i></span>
                        </div>
                    </div>
                    <div class="col-sm-6 form-group">
                        <label for="autocompletedemo">Patient Name</label>
                        <input class="form-control" type="text" placeholder="Last, First" id="autocompletedemo"/>
                        <input type="hidden" id="demographic_no" name="demographic_no" value="">
                    </div>
                </div>
                <div class="row mt-3">
                    <div class="col-sm-8 form-group">
                        <label for="senderEmailAddress">Sender</label>
                        <select class="form-select" name="senderEmailAddress" id="senderEmailAddress">
                            <option value="-1">All</option>
                            <c:forEach items="${ senderAccountList }" var="senderAccount">
                                <option value="${ senderAccount.senderEmail }">
                                    <c:out value="${ senderAccount.senderFirstName }"/> <c:out
                                        value="${ senderAccount.senderLastName }"/> <c:out
                                        value="(${ senderAccount.senderEmail })"/>
                                </option>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="col-sm-4 form-group">
                        <label for="emailStatus">Status</label>
                        <select class="form-select" name="emailStatus" id="emailStatus">
                            <option value="-1">All</option>
                            <c:forEach items="${ emailStatusList }" var="status">
                                <option value="${ status }">
                                    <c:out value="${ status }"/>
                                </option>
                            </c:forEach>
                        </select>
                    </div>
                </div>
                <div class="row mt-3">
                    <div class="col-sm-12">
                        <button type="submit" id="btnFetch" class="btn btn-primary btn-md" value="Fetch">
                            <span class="btn-label"><i class="icon-search"></i></span>
                            Fetch Emails
                        </button>
                        <button class="btn btn-secondary btn-md" value="Reset" type="reset"
                                onclick="return resetForm();">
                            <span class="btn-label"><i class="icon-repeat"></i></span>
                            Reset
                        </button>
                    </div>
                </div>
            </fieldset>

        </form>

        <div class="row">
            <div class="col-sm-12">
                <div id="results" class="mt-2">
                    <!-- container -->
                </div>
            </div>
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
