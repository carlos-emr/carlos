<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

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
    <title>Provider Service Report</title>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/library/flatpickr/flatpickr.min.css">
    <script src="<%=request.getContextPath()%>/library/flatpickr/flatpickr.min.js"></script>
</head>
<body>

<%@page import="java.util.*" %>
<%@page import="org.caisi.dao.*" %>
<%@page import="org.caisi.model.*" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.model.*" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.*" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@ include file="/taglibs.jsp" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"
       scope="request"/>

<div class="pb-2 mt-4 mb-3 border-bottom">
    <h4>Provider Service Report Form</h4>
</div>

<form action="${ctx}/oscarReport/ViewProviderServiceReportExport"
      class="card card-body bg-body-tertiary" id="psrForm" novalidate>

    <fieldset>
        <h4>
            Export to csv <br>
            <small>This will provide a break down of all unique
                encounters of a demographic to a provider, broken down by month and
                for the entire interval as well. This only does the numbers for a
                program of type bed or service.</small>
        </h4>
        <div class="row">
            <div class="mb-3">
                <label class="form-label" for="startDate">Start Date</label>
                <div>
                    <input id="startDate" name="startDate" class="form-control form-control-sm d-inline-block w-auto" size="7"
                           type="text" required pattern="(0[1-9]|1[0-2])/(?!0000)[0-9]{4}" placeholder="MM/YYYY"
                           aria-describedby="startDateError"/>
                    <span id="startDateError" class="text-danger" role="alert" hidden>Please enter a month as MM/YYYY.</span>
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label" for="endDate">End Date (inclusive)</label>
                <div>
                    <input id="endDate" name="endDate" class="form-control form-control-sm d-inline-block w-auto" size="7"
                           type="text" required pattern="(0[1-9]|1[0-2])/(?!0000)[0-9]{4}" placeholder="MM/YYYY"
                           aria-describedby="endDateError"/>
                    <span id="endDateError" class="text-danger" role="alert" hidden>Please enter a month as MM/YYYY.</span>
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
    (function () {
        const form = document.getElementById('psrForm');
        const fields = [form.elements.startDate, form.elements.endDate];
        function showValidity(field) {
            const valid = field.validity.valid;
            field.setAttribute('aria-invalid', String(!valid));
            document.getElementById(field.id + 'Error').hidden = valid;
            return valid;
        }
        function parseMonth(value) {
            const parts = /^(0[1-9]|1[0-2])\/([0-9]{4})$/.exec(value);
            if (!parts || Number(parts[2]) === 0) return undefined;
            const date = new Date();
            date.setFullYear(Number(parts[2]), Number(parts[1]) - 1, 1);
            date.setHours(0, 0, 0, 0);
            return date;
        }
        fields.forEach(function (field) {
            flatpickr(field, {
                dateFormat: 'm/Y', allowInput: true, parseDate: parseMonth,
                errorHandler: function () {
                    field.setCustomValidity('Please enter a month as MM/YYYY.');
                    showValidity(field);
                },
                onChange: function (dates) {
                    if (dates.length) field.setCustomValidity('');
                    showValidity(field);
                }
            });
            field.addEventListener('blur', function () { showValidity(field); });
            field.addEventListener('input', function () { field.setCustomValidity(''); showValidity(field); });
        });
        form.addEventListener('submit', function (event) {
            const valid = fields.map(showValidity).every(Boolean);
            if (!valid) {
                event.preventDefault();
                fields.find(field => !field.validity.valid).focus();
            }
        });
    })();
</script>
</body>
</html>