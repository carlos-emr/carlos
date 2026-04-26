<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<!DOCTYPE html>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="java.util.*, io.github.carlos_emr.carlos.hospitalReportManager.*,io.github.carlos_emr.carlos.hospitalReportManager.model.HRMCategory" %>
<%@ page import="io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_hrm" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect("{ pageContext.request.contextPath }/securityError?type=_hrm");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

    String demographic_no = request.getParameter("demographic_no");
    String deepColor = "#CCCCFF", weakColor = "#EEEEFF";

    String orderBy = request.getParameter("orderBy") != null ? request.getParameter("orderBy") : "report_date";
    String orderAsc = request.getParameter("orderAsc") != null ? request.getParameter("orderAsc") : "false";
    boolean asc = new Boolean(orderAsc);

    ArrayList<HashMap<String, ? extends Object>> hrmdocs;
    hrmdocs = HRMUtil.listHRMDocuments(loggedInInfo, orderBy, asc, demographic_no, true);

%>

<html>

    <head>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <title>HRM Document List</title>

        <link href="${ pageContext.request.contextPath }/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <!-- Bootstrap 2.3.1 -->
        <link href="${ pageContext.request.contextPath }/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css">
        <link href="${ pageContext.request.contextPath }/library/DataTables/DataTables-1.13.11/css/jquery.dataTables.min.css"
              rel="stylesheet">
        <script src="${ pageContext.request.contextPath }/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="${ pageContext.request.contextPath }/js/global.js"></script>
        <script src="${ pageContext.request.contextPath }/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
        <script src="${ pageContext.request.contextPath }/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>

        <script>
            jQuery(document).ready(function () {
                jQuery('#tblHRM').DataTable({
                    "order": [],
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json"
                    }
                });
            });
        </script>
        <script>
            function popupPage(varpage, windowname) {
                var page = "" + varpage;
                windowprops = "height=700,width=800,location=no,"
                    + "scrollbars=yes,menubars=no,status=yes,toolbars=no,resizable=yes,top=10,left=200";
                var popup = window.open(page, windowname, windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }
        </script>
    </head>
    <body>

    <table class="MainTable" id="scrollNumber1">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn" style="width:175px"><h4>HRM</h4></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar" style="width:100%">
                    <tr>
                        <td><h4><fmt:message key="hrm.displayHRMDocList.displaydocs"/></h4></td>
                        <td>&nbsp;</td>
                        <td style="text-align: right"><a
                                href="javascript:popupStart(300,400,'Help.jsp')"><fmt:message key="global.help"/></a> | <a
                                href="javascript:popupStart(300,400,'About.jsp')"><fmt:message key="global.about"/></a> | <a
                                href="javascript:popupStart(300,400,'License.jsp')"><fmt:message key="global.license"/></a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn">
            </td>
            <td class="MainTableRightColumn">
                <table id="tblHRM" class="table table-striped table-hover table-sm" style="width:100%">
                    <thead>
                    <tr>

                        <th><fmt:message key="hrm.displayHRMDocList.reportType"/></th>
                        <th><fmt:message key="hrm.displayHRMDocList.description"/></th>
                        <th><fmt:message key="hrm.displayHRMDocList.reportStatus"/></th>
                        <th>Report Date</th>
                        <th><fmt:message key="hrm.displayHRMDocList.timeReceived"/></th>
                        <th>Category</th>
                        <th>Class/Subclass/Accompanying Subclass</th>
                    </tr>
                    </thead>
                    <%


                        for (int i = 0; i < hrmdocs.size(); i++) {
                            HashMap<String, ? extends Object> curhrmdoc = hrmdocs.get(i);
                    %>
                    <tr>

                        <td><a href="#"
                               ONCLICK="popupPage('<%=request.getContextPath() %>/hospitalReportManager/Display?id=<carlos:encode value='<%= String.valueOf(curhrmdoc.get("id")) %>' context="uriComponent"/>', 'HRM Report'); return false;"
                        ><carlos:encode value='<%= String.valueOf(curhrmdoc.get("report_type")) %>' context="html"/>
                        </a></td>
                        <td><carlos:encode value='<%= String.valueOf(curhrmdoc.get("description")) %>' context="html"/>
                        </td>
                        <td><carlos:encode value='<%= String.valueOf(curhrmdoc.get("report_status")) %>' context="html"/>
                        </td>
                        <td style="text-align: center;"><carlos:encode value='<%= String.valueOf(curhrmdoc.get("report_date")) %>' context="html"/>
                        </td>
                        <td style="text-align: center;"><carlos:encode value='<%= String.valueOf(curhrmdoc.get("time_received")) %>' context="html"/>
                        </td>
                        <td><%=curhrmdoc.get("category") != null ? SafeEncode.forHtml(String.valueOf(curhrmdoc.get("category"))) : "" %>
                        <td><%=curhrmdoc.get("class_subclass") != null ? SafeEncode.forHtml(String.valueOf(curhrmdoc.get("class_subclass"))) : "" %>
                    </tr>
                    <%
                        }
                        if (hrmdocs.size() <= 0) {
                    %>
                    <tr>
                        <td><fmt:message key="eform.showmyform.msgNoData"/></td>
                        <td></td>
                        <!-- this empty td is here so that the number of columns matches the <th>, an important requirement when using jquery datatables-->
                        <td></td>
                        <!-- this empty td is here so that the number of columns matches the <th>, an important requirement when using jquery datatables-->
                        <td></td>
                        <!-- this empty td is here so that the number of columns matches the <th>, an important requirement when using jquery datatables-->
                        <td></td>
                        <!-- this empty td is here so that the number of columns matches the <th>, an important requirement when using jquery datatables-->
                        <td></td>
                        <!-- this empty td is here so that the number of columns matches the <th>, an important requirement when using jquery datatables-->
                        <td></td>
                        <!-- this empty td is here so that the number of columns matches the <th>, an important requirement when using jquery datatables-->

                    </tr>
                    <%
                        }
                    %>
                </table>

            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>