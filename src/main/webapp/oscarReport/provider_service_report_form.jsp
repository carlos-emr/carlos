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
    <title>Provider Service Report</title>
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <script src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath()%>/library/jquery/jquery-ui-1.14.2.min.js"></script>
    <link href="<%=request.getContextPath()%>/library/jquery/jquery-ui.min.css" rel="stylesheet" type="text/css"/>
    <script src="<%=request.getContextPath()%>/library/jquery/jquery.validate-1.21.0.min.js"></script>
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

<form action="${ctx}/oscarReport/provider_service_report_export.jsp"
      class="card card-body bg-body-tertiary" id="psrForm">

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
                <label class="form-label">Start Date</label>
                <div>
                    <input id="startDate" name="startDate" class="form-control form-control-sm d-inline-block w-auto" size="7"
                           type="text"/>
                </div>
            </div>
            <div class="mb-3">
                <label class="form-label">EndDate (inclusive)</label>
                <div>
                    <input id="endDate" name="endDate" class="form-control form-control-sm d-inline-block w-auto" size="7"
                           type="text"/>
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
    flatpickr("#startDate", {dateFormat: "m/Y", allowInput: true});
    flatpickr("#endDate", {dateFormat: "m/Y", allowInput: true});

    $(document).ready(function () {
        $('#psrForm').validate({
            rules: {
                startDate: {
                    required: true,
                    oscarMonth: true
                },
                endDate: {
                    required: true,
                    oscarMonth: true
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