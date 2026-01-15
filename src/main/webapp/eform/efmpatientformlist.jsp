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

<%@page import="java.util.*,ca.openosp.openo.eform.*" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    String demographic_no = request.getParameter("demographic_no");
    String deepColor = "#CCCCFF", weakColor = "#EEEEFF";

	if (session.getAttribute("userrole") == null) {
		response.sendRedirect(request.getContextPath() + "/logout.jsp");
	}
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String country = request.getLocale().getCountry();
    String orderByRequest = request.getParameter("orderby");
    String orderBy = "";
	if (orderByRequest == null) {
		orderBy = EFormUtil.DATE;
	} else if (orderByRequest.equals("form_subject")) {
		orderBy = EFormUtil.SUBJECT;
	} else if (orderByRequest.equals("form_name")) {
		orderBy = EFormUtil.NAME;
	}

    String groupView = request.getParameter("group_view");
    if (groupView == null) {
        groupView = "";
    }

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
            <fmt:message key="eform.showmyform.title"/>
        </title>

        <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables/datatables.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/jquery/jquery-ui-1.12.1.min.css" rel="stylesheet">

        <script src="${pageContext.request.contextPath}/js/global.js"></script>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.js"></script>
        <script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script>

        <script src="${pageContext.request.contextPath}/js/jquery.fileDownload.js"></script>
        <script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>

        <script>

			$(document).ready(function () {
				let table = jQuery('#efmTable').DataTable({
					"lengthMenu": [[15, 30, 60, 120, -1], [15, 30, 60, 120, "<fmt:message key='demographic.search.All'/>"]],
					"order": [2],
					columnDefs: [{ orderable: false, targets: 3 }],
					"language": {
						"url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:message key='global.i18nLanguagecode'/>.json"
					}
				});

			});

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

			function checkSelectBox() {
				let selectVal = document.forms[0].group_view.value;
				if (selectVal === "default") {
					return false;
				}
			}

			function updateAjax() {
				let parentAjaxId = "<%=parentAjaxId%>";
				if (parentAjaxId !== "null") {
					window.opener.document.forms['encForm'].elements['reloadDiv'].value = parentAjaxId;
					window.opener.updateNeeded = true;
				}

			}
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

    <body onunload="updateAjax()">
    <div class="container">
        <div id="heading">
            <h2>
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-file-earmark-ruled" viewBox="0 0 16 16">
                    <path d="M14 14V4.5L9.5 0H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2M9.5 3A1.5 1.5 0 0 0 11 4.5h2V9H3V2a1 1 0 0 1 1-1h5.5zM3 12v-2h2v2zm0 1h2v2H4a1 1 0 0 1-1-1zm3 2v-2h7v1a1 1 0 0 1-1 1zm7-3H6v-2h7z"></path>
                </svg>
                <fmt:message key="eform.showmyform.msgFormLybrary"/>
            </h2>
            <span><%= Encode.forHtml(demographic.getDisplayName()) %></span>
        </div>
        <div class="menu-columns">

            <div class="left-column">

                <a href="${pageContext.request.contextPath}/demographic/demographiccontrol.jsp?demographic_no=<%=demographic_no%>&appointment=<%=appointment%>&displaymode=edit&dboperation=search_detail">
                    <fmt:message key="demographic.demographiceditdemographic.btnMasterFile"/></a>
                <a href="efmformslistadd.jsp?demographic_no=<%=demographic_no%>&appointment=<%=appointment%>&parentAjaxId=<%=parentAjaxId%>"
                   class="current"> <fmt:message key="eform.showmyform.btnAddEForm"/></a>
                <a href="efmpatientformlist.jsp?demographic_no=<%=demographic_no%>&appointment=<%=appointment%>&parentAjaxId=<%=parentAjaxId%>">
                    <fmt:message key="eform.calldeletedformdata.btnGoToForm"/></a>
                <jsp:include page="efmviewgroups.jsp">
                    <jsp:param name="url" value="${pageContext.request.contextPath}/eform/efmpatientformlist.jsp"/>
                    <jsp:param name="groupView" value="<%=groupView%>"/>
                    <jsp:param name="patientGroups" value="1"/>
                    <jsp:param name="parentAjaxId" value="<%=parentAjaxId%>"/>
                </jsp:include>

                <a href="efmpatientformlistdeleted.jsp?demographic_no=<%=demographic_no%>&appointment=<%=appointment%>&parentAjaxId=<%=parentAjaxId%>">
                    <fmt:message key="eform.showmyform.btnDeleted"/></a>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.eform" rights="r"
                                   reverse="<%=false%>">
                    <a href="#" onclick="javascript: return popup(600, 1200, '${pageContext.request.contextPath}/administration/?show=Forms', 'manageeforms');" style="color: #835921;">
                       <fmt:message key="eform.showmyform.msgManageEFrm"/></a>
                </security:oscarSec>

            </div>
            <div class="right-column">

                <form id="sendToPhrForm" action="efmpatientformlistSendPhrAction.jsp">
                    <input type="hidden" name="clientId" value="<%=demographic_no%>"/>
                    <table id="efmTable" class="table table-striped table-compact dataTable no-footer">
                        <thead>
                        <tr>
                            <th>
                                <fmt:message key="eform.showmyform.btnFormName"/>
                            </th>
                            <th>
                                <fmt:message key="eform.showmyform.btnSubject"/>
                            </th>
                            <th>
                                <fmt:message key="eform.showmyform.formDate"/>
                            </th>
                            <th>
                                <fmt:message key="eform.showmyform.msgAction"/>
                            </th>
                        </tr>
                        </thead>
                        <tbody>
                        <%
                            ArrayList<HashMap<String, ? extends Object>> eForms;
                            if (groupView.equals("")) {
                                eForms = EFormUtil.listPatientEForms(orderBy, EFormUtil.CURRENT, demographic_no, roleName$);
                            } else {
                                eForms = EFormUtil.listPatientEForms(orderBy, EFormUtil.CURRENT, demographic_no, groupView, roleName$);
                            }

                            for (int i = 0; i < eForms.size(); i++) {
                                HashMap<String, ? extends Object> curform = eForms.get(i);
                        %>
                        <tr>

                            <td><a href="#"
                                   ONCLICK="popupPage('efmshowform_data.jsp?fdid=<%=curform.get("fdid")%>&appointment=<%=appointment%>', '<%="FormP" + i%>'); return false;"
                                   TITLE="<fmt:message key="eform.showmyform.msgViewFrm"/>"
                                   onmouseover="window.status='
                                        <fmt:message key="eform.showmyform.msgViewFrm"/>'; return true"><%=Encode.forHtmlContent((String)curform.get("formName"))%>
                            </a></td>
                            <td><%=Encode.forHtmlContent((String)curform.get("formSubject"))%>
                            </td>
                            <td><%=curform.get("formDate")%>
                            </td>
                            <td><a style="color:red;" href="${pageContext.request.contextPath}/eform/removeEForm.do?fdid=<%=curform.get("fdid")%>&group_view=<%=groupView%>&demographic_no=<%=demographic_no%>&parentAjaxId=<%=parentAjaxId%>"
                                    onClick="return confirm('Are you sure you want to delete this eform?');">
                                        <fmt:message key="eform.uploadimages.btnDelete"/></a></td>
                        </tr>
                        <%
                            }
                        %>
                        </tbody>
                    </table>
                </form>
            </div>
        </div>
    </div>
    </body>
</html>