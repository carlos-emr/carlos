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

<!-- page updated to support better use of CRUD operations -->

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page
        import="io.github.carlos_emr.carlos.demographic.data.*,java.util.*,io.github.carlos_emr.carlos.prevention.*,io.github.carlos_emr.carlos.providers.data.*,io.github.carlos_emr.carlos.util.*,io.github.carlos_emr.carlos.report.data.*,io.github.carlos_emr.carlos.prevention.pageUtil.*" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.DemographicSets" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<jsp:useBean id="providerBean" class="java.util.Properties"
             scope="session"/>

<%

    //int demographic_no = Integer.parseInt(request.getParameter("demographic_no"));
    String demographic_no = request.getParameter("demographic_no");

    DemographicSets ds = new DemographicSets();
    List<String> sets = ds.getDemographicSets();

    DemographicData dd = new DemographicData();

%>
<!DOCTYPE html>
<html>

    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title>Demographic Set Edit I18n</title>
        <script src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>

        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/jquery.dataTables.min.css"
              rel="stylesheet">

        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-compat.js"></script>
        <script src="${pageContext.request.contextPath}/js/global.js"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
        <script src="${ pageContext.request.contextPath }/library/DataTables/datatables.min.js"></script><!-- 1.13.4 -->

        <script>

            function showHideItem(id) {
                if (document.getElementById(id).style.display == 'none')
                    document.getElementById(id).style.display = '';
                else
                    document.getElementById(id).style.display = 'none';
            }

            function showItem(id) {
                document.getElementById(id).style.display = '';
            }

            function hideItem(id) {
                document.getElementById(id).style.display = 'none';
            }

            function showHideNextDate(id, nextDate, neverWarn) {
                if (document.getElementById(id).style.display == 'none') {
                    showItem(id);
                } else {
                    hideItem(id);
                    document.getElementById(nextDate).value = "";
                    document.getElementById(neverWarn).checked = false;

                }
            }

            function disableifchecked(ele, nextDate) {
                if (ele.checked == true) {
                    document.getElementById(nextDate).disabled = true;
                } else {
                    document.getElementById(nextDate).disabled = false;
                }
            }

        </SCRIPT>


    </head>

    <body class="preview" id="top" data-bs-spy="scroll" data-bs-target=".subnav" data-bs-offset="180">

    <div class="container">

        <div class="pb-2 mt-4 mb-3 border-bottom">
            <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgDemographic"/> - <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgSetEdit"/></h3>
        </div>

        <section id="mainContent">
            <% if (request.getAttribute("deleteSetSuccess") != null && (Boolean) request.getAttribute("deleteSetSuccess")) { %>
            <div class="alert alert-success fade show">
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                <h4 class="alert-heading">Success!</h4>
                <p>Patient set "${requestScope.setname}" has been successfully deleted.</p>
            </div>
            <% } %>
            <div class="row">
                <div class="col-md-12">
                    <form class="card card-body bg-body-tertiary form-search" method="post" action="${pageContext.request.contextPath}/report/DemographicSetEdit.do">
                    <div><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgPatientSet"/>: <select
                            name="patientSet">
                        <option value="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgOptionSet"/></option>
                        <% for (int i = 0; i < sets.size(); i++) {
                            String s = sets.get(i);%>
                        <option value="<%=s%>"><%=s%>
                        </option>
                        <%}%>
                    </select> <input type="submit" class="btn btn-secondary"
                                          value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.btnDisplaySet"/>"/>
                    </div>

                    </form> <%if( request.getAttribute("SET") != null ) {
                   List<Map<String,String>> list = (List<Map<String,String>>) request.getAttribute("SET");
                   String setName = (String) request.getAttribute("setname");%>
                    <div><form action="${pageContext.request.contextPath}/report/SetEligibility.do" method="post">
                        <input type="button" class="btn btn-secondary" data-bs-toggle="tooltip"
                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgIneligible"/>"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.btnSetIneligible"/>"
                               onclick="submit();">
                        <input type="submit" class="btn btn-secondary" name="delete"
                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgDelete"/>"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.btnDelete"/>"/>
                        <input type="hidden" name="setName" value="<%=setName%>">
                        <input type="hidden" name="deleteSet" id="deleteSet">

                        <table id="demoTable" class="ele table table-striped table-sm">
                            <thead>
                            <tr>
                                <th>&nbsp;<input type="checkbox" id="select_all"
                                                 onClick="check_uncheck_checkbox(this.checked);"></th>
                                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgDemo"/></th>
                                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgName"/></th>
                                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgDOB"/></th>
                                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgAge"/></th>
                                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgRoster"/></th>
                                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgDoctor"/></th>
                                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.oscarReportDemoSetEdit.msgEligibility"/></th>
                            </tr>
                            </thead>
                            <tbody>
                            <%
                                for (int i = 0; i < list.size(); i++) {
                                    Map<String, String> h = list.get(i);
                                    Demographic demo = dd.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), h.get("demographic_no"));
                            %>
                            <tr>
                                <td><input type="checkbox" name="demoNo"
                                           value="<%=h.get("demographic_no")%>" class="form-check-input"></td>
                                <td><%=h.get("demographic_no")%>
                                </td>
                                <td><%=demo.getLastName()%>, <%=demo.getFirstName()%>
                                </td>
                                <td><%=DemographicData.getDob(demo, "-")%>
                                </td>
                                <td><%=demo.getAge()%>
                                </td>
                                <td><%=demo.getRosterStatus()%>
                                </td>
                                <td><%=providerBean.getProperty(demo.getProviderNo(), "")%>
                                </td>
                                <td><%=elle(h.get("eligibility"))%>
                                </td>
                            </tr>
                            <%}%>
                            </tbody>
                        </table>
                        <!-- Button to trigger modal delete confirmation. Backend not implimented-->
                        <!--<a href="#delete-set-confirm" role="button" class="btn btn-alert" data-bs-toggle="modal"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.groups.delGroup"/></a>-->
                    </form></div>
                    <script>


                        function check_uncheck_checkbox(isChecked) {
                            if (isChecked) {
                                $('.checkbox').each(function () {
                                    this.checked = true;
                                });
                            } else {
                                $('.checkbox').each(function () {
                                    this.checked = false;
                                });
                            }
                        }

                        var table = jQuery('#demoTable').DataTable({
                            columnDefs: [
                                {
                                    searchable: false,
                                    orderable: false,
                                    targets: 0,
                                },],
                            "order": [[1, 'asc']],
                            "paging": false,
                            "language": {
                                "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                            }
                        });

                    </script>
                            <%}%>
                    </td>
                    </tr>
                    <tr>
                        <td class="MainTableBottomRowLeftColumn">&nbsp;</td>
                        <td class="MainTableBottomRowRightColumn" valign="top">&nbsp;</td>
                    </tr>
                    </table>
                    <script type="text/javascript">
                        //Calendar.setup( { inputField : "asofDate", ifFormat : "%Y-%m-%d", showsTime :false, button : "date", singleClick : true, step : 1 } );
                    </script>

        </section>
    </div>

    <div id="delete-set-confirm" class="modal fade" tabindex="-1">
        <div class="modal-dialog"><div class="modal-content">
        <div class="modal-header">
            <h3>Delete Set</h3>
            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
        </div>
        <div class="modal-body">
            <p>This will permanently delete the set, this procedure is
                irreversible.</p>
            <p>Are you sure you want to proceed?</p>
        </div>
        <div class="modal-footer">
            <a href="javascript:onDeleteConfirm();" class="btn btn-danger">Yes</a>
            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">No</button>
        </div>
    </div></div></div>


    <script>

        function onDeleteConfirm() {
            bootstrap.Modal.getOrCreateInstance(document.getElementById('delete-set-confirm')).hide();
            document.getElementById('deleteSet').value = 'deleteSet';
            document.getElementsByName("DemographicSetEditForm")[0].submit();
        }


        function onDeleteSetClick() {
            //e.preventDefault();

            var id = $(this).data('id');
            var deleteModal = new bootstrap.Modal(document.getElementById('delete-set-confirm'), {backdrop: true});
            $('#delete-set-confirm').data('id', id);
            deleteModal.show();
        };

    </script>
    </body>
</html>
<%!
    String elle(Object s) {
        ResourceBundle prop = ResourceBundle.getBundle("oscarResources");
        String ret = prop.getString("oscarReport.oscarReportDemoSetEdit.msgStatusEligibile");
        if (s != null && s instanceof String && ((String) s).equals("1")) {
            ret = prop.getString("oscarReport.oscarReportDemoSetEdit.msgStatusIneligibile");
        }
        return ret;
    }
%>