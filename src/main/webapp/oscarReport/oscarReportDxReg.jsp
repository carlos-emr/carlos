<%--

    Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@ include file="/taglibs.jsp" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

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

<%@ page import="io.github.carlos_emr.carlos.commn.dao.DxresearchDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Dxresearch" %>
<%@ page import="io.github.carlos_emr.carlos.dxresearch.util.*" %>
<%@ page import="java.util.*, java.sql.*" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>

<%
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
%>

<!DOCTYPE html>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DiseaseRegistry"/></title>

        <meta charset="UTF-8">

        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.theme-1.12.1.min.css"/>
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui.structure-1.12.1.min.css"/>
        <link href="${pageContext.servletContext.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css"/>
        <link type="text/css" media="all" href="${pageContext.servletContext.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css"
              rel="stylesheet">
        <link href="${pageContext.servletContext.contextPath}/library/DataTables/DataTables-1.13.4/css/jquery.dataTables.min.css"
              rel="stylesheet" type="text/css"/>
        <link href="${pageContext.servletContext.contextPath}/library/DataTables/Responsive-2.4.1/css/responsive.dataTables.min.css"
              rel="stylesheet" type="text/css"/>
        <link href="${pageContext.servletContext.contextPath}/library/DataTables/Responsive-2.4.1/css/responsive.jqueryui.min.css"
              rel="stylesheet" type="text/css"/>

        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
                <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-compat.js"></script>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
        <script type="text/javascript" src="${pageContext.servletContext.contextPath}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
        <script type="text/javascript" src="${pageContext.servletContext.contextPath}/js/dxJSONCodeSearch.js"></script>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/DataTables/Responsive-2.4.1/js/dataTables.responsive.min.js"></script>
        <script type="text/javascript"
                src="${pageContext.servletContext.contextPath}/library/DataTables/Responsive-2.4.1/js/responsive.jqueryui.min.js"></script>

        <script type="text/javascript">
            var ctx = "${pageContext.servletContext.contextPath}";

            function setAction(target) {
                document.forms[0].action.value = target;
            };

            $(document).ready(function () {
                $('#listview').DataTable({
                    responsive: true
                });
            });

        </script>
        <style>
            .ui-autocomplete {
                max-height: 200px;
                overflow-y: auto;
                /* prevent horizontal scrollbar */
                overflow-x: hidden;
                width: 200px;
            }

            /* IE 6 doesn't support max-height
                   * we use height instead, but this forces the menu to always be this tall
                   */
            * html .ui-autocomplete {
                height: 100px;
            }
        </style>

    </head>
    <%
        ProviderPreference providerPreference = (ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE);
        String curUser_no = (String) session.getAttribute("user");
        String mygroupno = providerPreference.getMyGroupNo();
        pageContext.setAttribute("mygroupno", mygroupno);
        String radiostatus = (String) session.getAttribute("radiovaluestatus");
        if (radiostatus == null || radiostatus.isEmpty()) {
            radiostatus = "patientRegistedAll";
            session.setAttribute("radiovaluestatus", radiostatus);
        }
        String formAction = request.getContextPath() + "/report/DxresearchReport.do?method=" + radiostatus;
        request.setAttribute("radiostatus", radiostatus);
        request.setAttribute("listview", request.getSession().getAttribute("listview"));
        request.setAttribute("codeSearch", request.getSession().getAttribute("codeSearch"));
        //request.setAttribute("editingCode", request.getSession().getAttribute("editingCode"));
        String editingCodeType = (String) session.getAttribute("editingCodeType");
        String editingCodeCode = (String) session.getAttribute("editingCodeCode");
        String editingCodeDesc = (String) session.getAttribute("editingCodeDesc");

    %>
    <body>

    <div class="container-fluid">
        <div class="navbar">
            <div class="container-fluid">
                <a class="brand" href="#"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.DiseaseRegistry"/></a>
            </div>
        </div>

        <div class="card card-body bg-body-tertiary">
            <form action="${pageContext.request.contextPath}/report/DxresearchReport.do?method=addSearchCode" method="post" accept-charset="UTF-8">
                <div class="row">
                    <input type="hidden" name="action" value="NA"/>
                    <select name="quicklistname" class="sel">
                        <option value="">Add Dx QuickList</option>
                        <c:forEach var="quickLists" items="${allQuickLists.dxQuickListBeanVector}">
                            <option value="${quickLists.quickListName}" ${quickLists.lastUsed}>
                                ${quickLists.quickListName}
                            </option>
                        </c:forEach>
                    </select>
                    OR
                    <select name="codesystem" class="sel" id="codingSystem">
                        <option value="">Select Coding System</option>
                        <c:forEach var="codingSys" items="${codingSystem.codingSystems}">
                            <option value="${codingSys}">${codingSys}</option>
                        </c:forEach>
                    </select>
                    <input type="text" id="codesearch" placeholder="search description" name="codesearch"
                           class="col-md-4 jsonDxSearch"/>
                </div>
                <div class="row">
                    <input type="submit" class="btn btn-primary" value="Add" />
                    <input type="button" class="btn btn-danger" value="Clear"
                           onclick="javascript:this.form.action='${pageContext.servletContext.contextPath}/report/DxresearchReport.do?method=clearSearchCode';this.form.submit()"/>
                </div>
            </form>

        </div>
        <div class="row">
            <strong>Search all patients with disease codes:</strong>
        </div>

        <form action="<%=formAction%>" method="post" class="d-flex flex-wrap align-items-center gap-2" accept-charset="UTF-8">

            <div class="row">
                <display:table name="codeSearch" id="codeSearch" class="table table-sm table-striped">
                    <display:column property="type" title="Code System"/>
                    <display:column property="dxSearchCode" title="Code"/>
                    <display:column property="description" title="Description"/>
                </display:table>
            </div>
            <div class="row">
                <div class="form-check form-check-inline">
                    <input type="radio" class="form-check-input" name="SearchBy" value="patientRegistedDistincted"
                           id="SearchBy_Distincted" <c:if test="${radiostatus == 'patientRegistedDistincted'}">checked</c:if>
                           onclick="javascript:this.form.action='<%= request.getContextPath()%>/report/DxresearchReport.do?method=patientRegistedDistincted'">
                    <label class="form-check-label" for="SearchBy_Distincted">ALL(distincted)</label>
                </div>
                <div class="form-check form-check-inline">
                    <input type="radio" class="form-check-input" name="SearchBy" value="patientRegistedAll"
                           id="SearchBy_All" <c:if test="${radiostatus == 'patientRegistedAll'}">checked</c:if>
                           onclick="javascript:this.form.action='<%= request.getContextPath()%>/report/DxresearchReport.do?method=patientRegistedAll'">
                    <label class="form-check-label" for="SearchBy_All">ALL</label>
                </div>
                <div class="form-check form-check-inline">
                    <input type="radio" class="form-check-input" name="SearchBy" value="patientRegistedActive"
                           id="SearchBy_Active" <c:if test="${radiostatus == 'patientRegistedActive'}">checked</c:if>
                           onclick="javascript:this.form.action='<%= request.getContextPath()%>/report/DxresearchReport.do?method=patientRegistedActive'">
                    <label class="form-check-label" for="SearchBy_Active">Active</label>
                </div>
                <div class="form-check form-check-inline">
                    <input type="radio" class="form-check-input" name="SearchBy" value="patientRegistedDeleted"
                           id="SearchBy_Deleted" <c:if test="${radiostatus == 'patientRegistedDeleted'}">checked</c:if>
                           onclick="javascript:this.form.action='<%= request.getContextPath()%>/report/DxresearchReport.do?method=patientRegistedDeleted'">
                    <label class="form-check-label" for="SearchBy_Deleted">Deleted</label>
                </div>
                <div class="form-check form-check-inline">
                    <input type="radio" class="form-check-input" name="SearchBy" value="patientRegistedResolve"
                           id="SearchBy_Resolved" <c:if test="${radiostatus == 'patientRegistedResolve'}">checked</c:if>
                           onclick="javascript:this.form.action='<%= request.getContextPath()%>/report/DxresearchReport.do?method=patientRegistedResolve'">
                    <label class="form-check-label" for="SearchBy_Resolved">Resolved</label>
                </div>


                <select id="provider_no" name="provider_no" class="sel">
                    <option value="*"><fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.formAllProviders"/></option>

                    <option disabled>___________</option>

                    <security:oscarSec roleName="<%=roleName$%>" objectName="_team_schedule_only" rights="r"
                                       reverse="false">
                        <%
                            for (Provider p : providerDao.getActiveProviders()) {
                        %>
                        <option value="<%=p.getProviderNo()%>" <%=mygroupno.equals(p.getProviderNo()) ? "selected" : ""%>>
                            <%=p.getFormattedName()%>
                        </option>
                        <%
                            }
                        %>
                    </security:oscarSec>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_team_schedule_only" rights="r"
                                       reverse="true">

                        <%
                            for (MyGroup g : myGroupDao.searchmygroupno()) {

                        %>
                        <option value="<%="_grp_"+g.getId().getMyGroupNo()%>" <%=mygroupno.equals(g.getId().getMyGroupNo()) ? "selected" : ""%>><%=g.getId().getMyGroupNo()%>
                        </option>
                        <%
                            }

                            for (Provider p : providerDao.getActiveProviders()) {
                        %>
                        <option value="<%=p.getProviderNo()%>" <%=mygroupno.equals(p.getProviderNo()) ? "selected" : ""%>><%=p.getFormattedName()%>
                        </option>
                        <%
                            }
                        %>
                    </security:oscarSec>

                </select>


                <input type="submit" class="btn btn-primary" value="Search" />
            </div>

            <h3>Results</h3>
            <div class="row">
                <display:table name="listview" id="listview" class="table table-striped table-hover table-sm">
                    <display:column property="strFirstName" title="First Name"/>
                    <display:column property="strLastName" title="Last Name"/>
                    <display:column property="strSex" title="Sex"/>
                    <display:column property="strDOB" title="DOB"/>
                    <display:column property="strPhone" title="Phone"/>
                    <display:column property="strHIN" title="HIN"/>
                    <display:column property="strCodeSys" title="Code System"/>
                    <display:column property="strCode" title="Code"/>
                    <display:column property="strStartDate" title="Start Date"/>
                    <display:column property="strUpdateDate" title="Update Date"/>
                    <display:column property="strStatus" title="Status"/>
                </display:table>
            </div>


            <c:if test="${ not empty listview and not empty listview.strCode }">
                <input type="button" class="btn btn-secondary" value="Download Excel"
                       onclick="javascript:this.form.action='${pageContext.servletContext.contextPath}/report/DxresearchReport.do?method=patientExcelReport';this.form.submit()">
            </c:if>

        </form>
    </div>
    </body>
</html>
