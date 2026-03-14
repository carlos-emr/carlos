<%@ page import="io.github.carlos_emr.carlos.report.data.DoctorList" %>
<%@ page import="io.github.carlos_emr.carlos.providers.bean.ProviderNameBean" %>
<%@ page import="java.util.ArrayList" %>

<%@ include file="/taglibs.jsp" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"
       scope="request"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<!DOCTYPE html>
<html>
<head>
    <title>Patient List Report</title>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet">
    <script src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>
</head>
<body>

<div class="pb-2 mt-4 mb-3 border-bottom">
    <h4>Patient List</h4>
</div>

<form id="plForm" action="<%=request.getContextPath() %>/patientlistbyappt" class="card card-body bg-body-tertiary">

    <fieldset>
        <h4>
            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.exportPatientbyAppt"/> <br> <small>Please select
            the provider and appointment date from &amp; to.</small>
        </h4>
        <div class="row">
            <div class="control-group">
                <label class="form-label">Doctor</label>
                <div class="controls">
                    <select name="provider_no" class="col-md-3">
                        <option value="all">All Doctors</option>
                        <%
                            ArrayList<ProviderNameBean> dnl = new DoctorList().getDoctorNameList();
                            for (int i = 0; i < dnl.size(); i++) {
                                ProviderNameBean pb = (ProviderNameBean) dnl.get(i);
                        %>
                        <option value="<%=pb.getProviderID()%>"><%=pb.getProviderName()%>
                        </option>
                        <%
                            }
                        %>
                    </select>
                </div>
            </div>
            <div class="control-group">
                <label class="form-label">Date From</label>
                <div class="controls">
                    <input id="date_from" name="date_from" size="10"
                           type="text"/>
                </div>
            </div>
            <div class="control-group">
                <label class="form-label">Date To</label>
                <div class="controls">
                    <input id="date_to" name="date_to" size="10"
                           type="text"/>
                </div>
            </div>
            <div class="control-group">
                <div class="controls">
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

    $(document).ready(function () {
        $('#plForm').validate({
            rules: {
                date_from: {
                    required: true,
                    oscarDate: true
                },
                date_to: {
                    required: true,
                    oscarDate: true
                }
            },
            submitHandler: function (form) {
                form.submit();
            }
        });
    });
</script>
</body>
</html>