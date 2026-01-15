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
<!DOCTYPE html>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_admin.misc,_admin.flowsheet" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.misc&type=_admin.flowsheet");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.util.*,ca.openosp.openo.report.reportByTemplate.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="ca.openosp.openo.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig" %>
<%@ page import="ca.openosp.openo.encounter.oscarMeasurements.MeasurementFlowSheet" %>
<%@ page import="ca.openosp.openo.commn.model.Flowsheet" %>
<%@ page import="ca.openosp.openo.commn.dao.FlowsheetDao" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    String method = request.getParameter("method");
    if (method != null) {
        if (method.equals("disable")) {
            String name = request.getParameter("name");
            MeasurementTemplateFlowSheetConfig.getInstance().disableFlowsheet(name);
        }
        if (method.equals("enable")) {
            String name = request.getParameter("name");
            MeasurementTemplateFlowSheetConfig.getInstance().enableFlowsheet(name);
        }
        response.sendRedirect("manageFlowsheets.jsp");
    }

%>

<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
        <script src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Manage Flowsheets</title>

        <script src="<%=request.getContextPath()%>/share/javascript/prototype.js"></script>

        <link href="<%=request.getContextPath()%>/css/bootstrap.css" rel="stylesheet" type="text/css">
        <link href="<%=request.getContextPath()%>/css/bootstrap-responsive.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.structure-1.12.1.min.css">
        <link rel="stylesheet" href="<%=request.getContextPath() %>/library/jquery/jquery-ui.theme-1.12.1.min.css">
<script src="<%=request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="<%=request.getContextPath()%>/js/bootstrap.js"></script>

<script src="<%=request.getContextPath() %>/library/jquery/jquery-ui-1.12.1.min.js"></script>


        <script src="<%=request.getContextPath()%>/share/javascript/Oscar.js"></script>

	<style>
		table {
			table-layout: fixed;
		}
		table td {
			word-wrap: break-word;
		}
	</style>

        <script>
            jQuery.noConflict();
            jQuery(function () {
                jQuery(document).tooltip();
            });
        </script>


    </head>

    <body>

<div class="container-fluid">
<div class="navbar" id="demoHeader"><div class="navbar-inner">
	<a class="brand" href="javascript:void(0)">Flowsheets</a>
</div></div>

			<table class="table table-striped table-condensed table-hover">
        <thead>
        <tr>
            <td><b>Name</b></td>
            <td><b>Universal</B></td>
            <td><b>Dx Triggers</B></td>
            <td><b>Program Triggers</B></td>
            <td><b>Type</b></td>
            <td><b>Enabled</b></td>
            <td><b>Actions</b></td>
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
							<td><%=Encode.forHtmlContent(flowSheet.getDisplayName())%></td>
							<td><%=flowSheet.isUniversal() %></td>
							<td><%=Encode.forHtmlContent(flowSheet.getDxTriggersString()) %></td>
							<td><%=Encode.forHtmlContent(flowSheet.getProgramTriggersString()) %></td>
							<td><%=Encode.forHtmlContent(type) %></td>
							<td><%=enabled%></td>
							<td>
								<a href="<%=request.getContextPath()%>/oscarEncounter/oscarMeasurements/adminFlowsheet/EditFlowsheet.jsp?flowsheet=<%=flowSheet.getName()%>&displayName=<%=flowSheet.getDisplayName()%>">Edit</a>&nbsp;
								<%if(enabled) { %>
									<a href="manageFlowsheets.jsp?method=disable&name=<%=flowSheet.getName()%>">Disable</a>
								<% } else { %>
									<a href="manageFlowsheets.jsp?method=enable&name=<%=flowSheet.getName()%>">Enable</a>
								<% } %>
							</td>
						</tr>
					<%
				}
			%>
            </tbody>
			</table>

		<div class="panel panel-default">
			<div class="panel-heading">
				<h4>Upload Custom Flowsheet</h4>
			</div>
		<div class="panel-body">
			<form enctype="multipart/form-data" method="POST" action="<%=request.getContextPath()%>/admin/manageFlowsheetsUpload.jsp">
        <input type="file" name="flowsheet_file">
				<span title="<fmt:message key="global.uploadWarningBody"/>" style="vertical-align:middle;cursor:pointer"><img alt="alert" src="<%=request.getContextPath()%>/images/icon_alertsml.gif"/></span>
        <input type="submit" value="Upload" class="btn btn-primary">
    </form>
		</div>
		</div>
</div>
</html>
