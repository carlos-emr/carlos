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
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.misc,_admin.flowsheet" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.misc&type=_admin.flowsheet");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.util.*,io.github.carlos_emr.carlos.report.reportByTemplate.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>


<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Flowsheet" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.FlowsheetDao" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%-- Enable/disable mutations are handled by ManageFlowsheets2Action (PRG pattern). --%>


<html>
    <head>
        <script src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="admin.manageFlowsheets.title"/></title>

        <link href="<%=request.getContextPath()%>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.structure-1.14.2.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.theme-1.14.2.min.css">
<script src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath()%>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

<script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
<script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.14.2.min.js"></script>


        <script src="<%=request.getContextPath()%>/share/javascript/Oscar.js"></script>

		<script>
			/** Submits a flowsheet action via the hidden #flowsheetActionForm. */
			function submitFlowsheetAction(method, name) {
				var form = document.getElementById('flowsheetActionForm');
				form.elements['method'].value = method;
				form.elements['name'].value = name;
				form.submit();
			}
		</script>

	<style>
		table {
			table-layout: fixed;
		}
		table td {
			word-wrap: break-word;
		}
	</style>

    </head>

    <body>

<form id="flowsheetActionForm" method="post" action="${pageContext.request.contextPath}/admin/ManageFlowsheets" style="display:none;">
	<input type="hidden" name="method" value=""/>
	<input type="hidden" name="name" value=""/>
</form>

<div class="container-fluid">
<div class="navbar" id="demoHeader"><div class="container-fluid">
	<a class="navbar-brand" href="javascript:void(0)"><fmt:message key="admin.manageFlowsheets.brand"/></a>
</div></div>

			<table class="table table-striped table-sm table-hover">
        <thead>
        <tr>
            <td><b><fmt:message key="admin.manageFlowsheets.table.name"/></b></td>
            <td><b><fmt:message key="admin.manageFlowsheets.table.universal"/></B></td>
            <td><b><fmt:message key="admin.manageFlowsheets.table.dxTriggers"/></B></td>
            <td><b><fmt:message key="admin.manageFlowsheets.table.programTriggers"/></B></td>
            <td><b><fmt:message key="admin.manageFlowsheets.table.type"/></b></td>
            <td><b><fmt:message key="admin.manageFlowsheets.table.enabled"/></b></td>
            <td><b><fmt:message key="admin.manageFlowsheets.table.actions"/></b></td>
        </tr>
        </thead>
        <tbody>
        <%
            Hashtable<String, String> systemFlowsheets = MeasurementTemplateFlowSheetConfig.getInstance().getFlowsheetDisplayNames();
            for (String name : systemFlowsheets.keySet()) {
                String displayName = systemFlowsheets.get(name);
                MeasurementFlowSheet flowSheet = MeasurementTemplateFlowSheetConfig.getInstance().getFlowSheet(name);

                //load from db to know if it's enabled or not.
                Flowsheet fs = MeasurementTemplateFlowSheetConfig.getInstance().getFlowsheetSettings().get(flowSheet.getName());
                boolean enabled = true;
                if (fs != null) {
                    enabled = fs.isEnabled();
                }
                String type = "System";
                if (fs != null) {
                    type = (fs.isExternal()) ? "System" : "Custom";
                }
        %>

						<tr>
							<td><carlos:encode value='<%= flowSheet.getDisplayName() %>' context="html"/></td>
							<td><%=flowSheet.isUniversal() %></td>
							<td><carlos:encode value='<%= flowSheet.getDxTriggersString() %>' context="html"/></td>
							<td><carlos:encode value='<%= flowSheet.getProgramTriggersString() %>' context="html"/></td>
							<td><carlos:encode value='<%= type %>' context="html"/></td>
							<td><%=enabled%></td>
							<td>
								<a href="<%=request.getContextPath()%>/encounter/oscarMeasurements/adminFlowsheet/ViewEditFlowsheet?flowsheet=<carlos:encode value='<%= flowSheet.getName() %>' context="uriComponent"/>&displayName=<carlos:encode value='<%= flowSheet.getDisplayName() %>' context="uriComponent"/>"><fmt:message key="admin.manageFlowsheets.edit"/></a>&nbsp;
								<%if(enabled) { %>
									<a href="javascript:void(0);" onclick="submitFlowsheetAction('disable','<carlos:encode value='<%= flowSheet.getName() %>' context="javaScriptAttribute"/>');"><fmt:message key="admin.manageFlowsheets.disable"/></a>
								<% } else { %>
									<a href="javascript:void(0);" onclick="submitFlowsheetAction('enable','<carlos:encode value='<%= flowSheet.getName() %>' context="javaScriptAttribute"/>');"><fmt:message key="admin.manageFlowsheets.enable"/></a>
								<% } %>
							</td>
						</tr>
					<%
				}
			%>
            </tbody>
			</table>

		<div class="card">
			<div class="card-header">
				<h4><fmt:message key="admin.manageFlowsheets.uploadCustom"/></h4>
			</div>
		<div class="card-body">
			<form enctype="multipart/form-data" method="POST" action="${pageContext.request.contextPath}/admin/ManageFlowsheetsUpload">
        <input type="file" name="flowsheet_file">
				<span title="<fmt:message key="global.uploadWarningBody"/>" style="vertical-align:middle;cursor:pointer"><img alt="alert" src="<%=request.getContextPath()%>/images/icon_alertsml.gif"/></span>
        <input type="submit" value="<fmt:message key='admin.manageFlowsheets.upload'/>" class="btn btn-primary">
    </form>
		</div>
		</div>
</div>
</html>
