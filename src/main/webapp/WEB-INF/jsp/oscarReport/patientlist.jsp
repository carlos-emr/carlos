<%@ page import="io.github.carlos_emr.carlos.report.data.DoctorList" %>
<%@ page import="io.github.carlos_emr.carlos.providers.bean.ProviderNameBean" %>
<%@ page import="java.util.ArrayList" %>

<%@ include file="/taglibs.jsp" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"
       scope="request"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<!DOCTYPE html>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title>Patient List Report</title>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet">
    <script src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>
</head>
<body>

<div class="pb-2 mt-4 mb-3 border-bottom">
    <h4>Patient List</h4>
</div>

<form id="plForm" method="post" action="${carlos:forHtmlAttribute(ctx)}/patientlistbyappt" class="card card-body bg-body-tertiary">

    <fieldset>
        <h4>
            <fmt:message key="admin.admin.exportPatientbyAppt"/> <br> <small>Please select
            the provider and appointment date from &amp; to.</small>
        </h4>
        <div class="row">
            <div class="mb-3">
                <label class="form-label" for="provider_no">Doctor</label>
                <div>
                    <select id="provider_no" name="provider_no" class="form-select">
                        <option value="all">All Doctors</option>
                        <%
                            ArrayList<ProviderNameBean> dnl = new DoctorList().getDoctorNameList();
                            for (int i = 0; i < dnl.size(); i++) {
                                ProviderNameBean pb = (ProviderNameBean) dnl.get(i);
                        %>
                        <option value="<carlos:encode value='<%= pb.getProviderID() %>' context="htmlAttribute"/>"><carlos:encode value='<%= pb.getProviderName() %>' context="html"/>
                        </option>
                        <%
                            }
                        %>
                    </select>
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="date_from">Date From</label>
                <div>
                    <input id="date_from" name="date_from" size="10"
                           type="text" required pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}"
                           title="Enter a date in YYYY-MM-DD format"/>
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="date_to">Date To</label>
                <div>
                    <input id="date_to" name="date_to" size="10"
                           type="text" required pattern="[0-9]{4}-[0-9]{2}-[0-9]{2}"
                           title="Enter a date in YYYY-MM-DD format"/>
                </div>
            </div>
            <div class="mb-3">
                <div>
                    <button type="submit" class="btn btn-primary">
                        <i class="fa-solid fa-download"></i> Export
                    </button>
                </div>
            </div>
        </div>
    </fieldset>
</form>

<script>
    flatpickr("#date_from", {dateFormat: "Y-m-d", allowInput: true});
    flatpickr("#date_to", {dateFormat: "Y-m-d", allowInput: true});

    const reportForm = document.getElementById("plForm");
    const dateFrom = document.getElementById("date_from");
    const dateTo = document.getElementById("date_to");
    const datePattern = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;

    function isValidDate(value) {
        if (!datePattern.test(value)) {
            return false;
        }
        const parsed = new Date(value + "T00:00:00Z");
        return !Number.isNaN(parsed.getTime()) && parsed.toISOString().substring(0, 10) === value;
    }

    function validateDateRange() {
        dateFrom.setCustomValidity("");
        dateTo.setCustomValidity("");
        if (dateFrom.value && !isValidDate(dateFrom.value)) {
            dateFrom.setCustomValidity("Date From must be a valid date in YYYY-MM-DD format.");
        }
        if (dateTo.value && !isValidDate(dateTo.value)) {
            dateTo.setCustomValidity("Date To must be a valid date in YYYY-MM-DD format.");
        }
        if (isValidDate(dateFrom.value) && isValidDate(dateTo.value) && dateFrom.value > dateTo.value) {
            dateTo.setCustomValidity("Date To must be on or after Date From.");
        }
    }

    dateFrom.addEventListener("input", validateDateRange);
    dateTo.addEventListener("input", validateDateRange);
    reportForm.addEventListener("submit", function (event) {
        validateDateRange();
        if (!reportForm.checkValidity()) {
            event.preventDefault();
            reportForm.reportValidity();
        }
    });
</script>
</body>
</html>
