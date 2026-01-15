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
<%
	if(session.getAttribute("userrole") == null )  response.sendRedirect(request.getContextPath() + "/logout.jsp");
	String roleName$ = (String)session.getAttribute("userrole") + "," + (String) session.getAttribute("user");

	String demographic_no = request.getParameter("demographic_no");
	String deepColor = "#CCCCFF" , weakColor = "#EEEEFF" ;
%>
<%@ page import="java.util.*, ca.openosp.openo.eform.*"%>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
	LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
	String country = request.getLocale().getCountry();
	String orderByRequest = request.getParameter("orderby");
	String orderBy = "";
	if (orderByRequest == null) orderBy = EFormUtil.DATE;
	else if (orderByRequest.equals("form_subject")) orderBy = EFormUtil.SUBJECT;
	else if (orderByRequest.equals("form_name")) orderBy = EFormUtil.NAME;

	String appointment = request.getParameter("appointment");
	String parentAjaxId = request.getParameter("parentAjaxId");
%>
<%!
	DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
%>
<%
	Demographic demographic = demographicManager.getDemographic(loggedInInfo, demographic_no);
%>

<fmt:setBundle basename="oscarResources"/>

<html>
	<head>
		<title>
            <fmt:message key="eform.showmyform.title" />
        </title>

		<link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet">
		<link href="${pageContext.request.contextPath}/library/DataTables/datatables.css" rel="stylesheet">
		<link href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.css" rel="stylesheet">


		<script src="${pageContext.request.contextPath}/js/global.js"></script>
		<script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
		<script src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.js"></script>
		<script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script>

		<script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>

		<script>

			function popupPage(varpage, windowname) {
				let page = "" + varpage;
				windowprops = "height=700,width=800,location=no,"
						+ "scrollbars=yes,menubars=no,status=yes,toolbars=no,resizable=yes,top=10,left=200";
				let popup = window.open(page, windowname, windowprops);
				if (popup != null) {
					if (popup.opener == null) {
						popup.opener = self;
					}
					popup.focus();
				}
			}

			function updateAjax() {
				let parentAjaxId = "<%=parentAjaxId%>";
				if( parentAjaxId !== "null" ) {
					window.opener.document.forms['encForm'].elements['reloadDiv'].value = parentAjaxId;
					window.opener.updateNeeded = true;
				}

			}

			$(document).ready(function() {

				let table = jQuery('#efmTable').DataTable({
					"pageLength": 15,
					"lengthMenu": [ [15, 30, 60, 120, -1], [15, 30, 60, 120, "<fmt:message key='demographic.search.All'/>"] ],
					"order": [2],
					"language": {
						"url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:message key='global.i18nLanguagecode'/>.json"
					}
				});

			});
		</script>
		<style>
			:root *:not(h2, .h2) {
				font-family: Arial, "Helvetica Neue", Helvetica, sans-serif !important;
				font-size: 12px;
				overscroll-behavior: none;
				-webkit-font-smoothing: antialiased;
				-moz-osx-font-smoothing: grayscale;
			}

			#heading h2 {
				display: inline-block;
			}

			#heading span {
				margin-left:50px;
			}


			:root a {
				color:blue;
			}

			div.menu-columns {
				display: flex;
				gap: 10px;
			}

			div.left-column {
				display: flex;
				flex-direction: column;
				gap: 5px;
				flex-basis: max-content;
				text-wrap: nowrap;
			}
		</style>
	</head>
	<body onunload="updateAjax()" >
	<div class="container">
	<div id="heading">
		<h2>
			<svg style="color:red;" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark-excel" viewBox="0 0 16 16">
				<path d="M5.884 6.68a.5.5 0 1 0-.768.64L7.349 10l-2.233 2.68a.5.5 0 0 0 .768.64L8 10.781l2.116 2.54a.5.5 0 0 0 .768-.641L8.651 10l2.233-2.68a.5.5 0 0 0-.768-.64L8 9.219l-2.116-2.54z"></path>
				<path d="M14 14V4.5L9.5 0H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2M9.5 3A1.5 1.5 0 0 0 11 4.5h2V14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V2a1 1 0 0 1 1-1h5.5z"></path>
			</svg>
			<fmt:message key="eform.independent.btnDeleted" />
		</h2>
		<span><%= Encode.forHtml(demographic.getDisplayName()) %></span>
	</div>
		<div class="menu-columns">
			<div class="left-column">

				<a href="${pageContext.request.contextPath}/demographic/demographiccontrol.jsp?demographic_no=<%=demographic_no%>&appointment=<%=appointment%>&displaymode=edit&dboperation=search_detail"><fmt:message key="demographic.demographiceditdemographic.btnMasterFile" /></a>

				
				<a href="efmformslistadd.jsp?demographic_no=<%=demographic_no%>&appointment=<%=appointment%>&parentAjaxId=<%=parentAjaxId%>" class="current"> 
                    <fmt:message key="eform.showmyform.btnAddEForm"/></a>
				<a href="efmpatientformlist.jsp?demographic_no=<%=demographic_no%>&appointment=<%=appointment%>&parentAjaxId=<%=parentAjaxId%>">
                    <fmt:message key="eform.calldeletedformdata.btnGoToForm"/></a>
<%--			<a href="efmpatientformlistdeleted.jsp?demographic_no=<%=demographic_no%>&appointment=<%=appointment%>&parentAjaxId=<%=parentAjaxId%>">
                    <fmt:message key="eform.showmyform.btnDeleted"/></a>--%>
				<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.eform" rights="r" reverse="<%=false%>" >
					<a href="#" onclick="javascript: return popup(600, 1200, '${pageContext.request.contextPath}/administration/?show=Forms', 'manageeforms');" style="color: #835921;">
                        <fmt:message key="eform.showmyform.msgManageEFrm"/></a>
				</security:oscarSec>
			</div>
			<div class="right-column">
				

				<table id="efmTable" class="table table-striped table-compact dataTable no-footer">
					<thead>
					<tr>
						<th>
                            <fmt:message key="eform.showmyform.btnFormName" />
						</th>
						<th>
                            <fmt:message key="eform.showmyform.btnSubject" />
                        </th>
						<th>
                            <fmt:message key="eform.showmyform.formDate" />
                        </th>
						<th>
                            <fmt:message key="eform.showmyform.msgAction" />
                        </th>
					</tr>
					</thead>
					<tbody>
					<%
						ArrayList<HashMap<String, ? extends Object>> forms = EFormUtil.listPatientEForms(orderBy, EFormUtil.DELETED, demographic_no, null);
						for (int i=0; i< forms.size(); i++) {
							HashMap<String, ? extends Object> curform = forms.get(i);
					%>
					<tr>
						<td><a href="#"
						       ONCLICK="popupPage('efmshowform_data.jsp?fdid=<%= curform.get("fdid")%>', '<%="FormPD" + i%>'); return false;"
						       TITLE="View Form"
						       onmouseover="window.status='View This Form'; return true"><%=Encode.forHtmlContent((String)curform.get("formName"))%></a></td>
						<td><%=Encode.forHtmlContent((String)curform.get("formSubject"))%></td>
						<td ><%=curform.get("formDate")%></td>
						<td ><a
								href="${pageContext.request.contextPath}/eform/unRemoveEForm.do?fdid=<%=curform.get("fdid")%>&demographic_no=<%=demographic_no%>&parentAjaxId=<%=parentAjaxId%>" onClick="javascript: return confirm('Are you sure you want to restore this eform?');">
                                    <fmt:message key="global.btnRestore" /></a></td>
					</tr>
					<%
						}
					%>
					</tbody>
				</table>
			</div>
		
		</div>
	</div>
	</body>
</html>