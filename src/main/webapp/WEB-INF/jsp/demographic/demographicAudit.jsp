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

<%-- 
demographicAudit.jsp
- provides the log entries for the passed demographic_no for audit purposes
- DataTables enables filtering ("search") and pagation functions

@param demographic_no

@since 2019

--%>
<!DOCTYPE html>

<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="java.text.SimpleDateFormat" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.OscarLog" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.OscarLogDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>


<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title><fmt:message key="demographic.demographiceditdemographic.btnAuditInfo"/></title>

    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
    <!--
        The global-head.jspf fragment provides:
        - Viewport meta tag for responsive design
        - global.js (legacy focus/refresh helpers)
        - jQuery 3.7.1
        - Bootstrap 5.3.3 (JS bundle + CSS)
        - jQuery UI 1.14.2 CSS (JS must be included page-specifically where dialogs/widgets are needed)
        - Font Awesome 6.7.2 (icon library)
        - searchBox.css (shared search/form styles)
        - global.css (CARLOS design tokens and common classes)
    -->
    <link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css">
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
    <script type="text/javascript" src="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>
    <script>
    jQuery(document).ready(function () {
        const auditLogTable = jQuery("#auditLog").DataTable({
            searching: true,
            paging: true,
            pageLength: 10,
            lengthMenu: [ [10, 50, 100, 250, -1], [10, 50, 100, 250, "<fmt:message key="admin.logReport.all"/>"] ],
            language: {
                url: '${pageContext.request.contextPath}/library/DataTables/i18n/<fmt:message key="global.i18n.datatablescode"/>.json'
                }
        });
    });
  </script>
    <%!
        DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
        OscarLogDao oscarLogDao = SpringUtils.getBean(OscarLogDao.class);

    %>
    <%
        Demographic demographic = demographicDao.getDemographic(request.getParameter("demographic_no"));
        List<OscarLog> logs = oscarLogDao.findByDemographicId(demographic.getDemographicNo());
        Collections.sort(logs, new Comparator<OscarLog>() {
            public int compare(OscarLog o1, OscarLog o2) {
                return o1.getCreated().compareTo(o2.getCreated());
            }
        });
    %>

</head>
<body>
<h2><fmt:message key="demographic.demographiceditdemographic.btnAuditInfo"/>&nbsp;(<carlos:encode value='<%= String.valueOf(demographic.getDemographicNo()) %>' context="html"/>)</h2>
<table style="width:100%" class="table table-striped" id="auditLog">
    <thead>
    <tr>
    <th><fmt:message key="admin.logReport.table.time"/></th>
    <th><fmt:message key="admin.logReport.table.provider"/></th>
    <th><fmt:message key="admin.logReport.table.action"/></th>
    <th><fmt:message key="admin.logReport.table.content"/></th>
    <th><fmt:message key="admin.logReport.table.keyword"/></th>
    <th><fmt:message key="admin.logReport.table.data"/></th>
    </tr>
    </thead>
    <tbody>
    <%
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
        int index = 0;
        for (OscarLog log : logs) {

            if (log.getContent() == null && log.getContentId() == null) {
                continue;
            }

    %>
    <tr>
        <td><%=fmt.format(log.getCreated()) %>
        </td>
        <td><carlos:encode value='<%= providerDao.getProviderName(log.getProviderNo()) %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= log.getAction() %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= log.getContent() %>' context="html"/>
        </td>
        <td><%=log.getContentId() != null && !"null".equals(log.getContentId()) ? SafeEncode.forHtml(log.getContentId()) : "" %>
        </td>
        <td><%=log.getData() != null && !"null".equals(log.getData()) ? SafeEncode.forHtml(log.getData()) : "" %>
        </td>


    </tr>
    <% index++;
    } %>
    </tbody>
</table>

</body>
</html>
