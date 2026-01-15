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

<%@page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="java.text.SimpleDateFormat" %>
<%@page import="ca.openosp.openo.commn.model.OscarLog" %>
<%@page import="ca.openosp.openo.commn.dao.OscarLogDao" %>
<%@page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@page import="ca.openosp.openo.commn.model.Demographic" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.util.*" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>


<head>

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

<body class="BodyStyle" vlink="#0000FF">


<table style="width:100%">
    <thead>
    <th align="left">Time of Event</th>
    <th align="left">Provider</th>
    <th align="left">Action</th>
    <th align="left">Content</th>
    <th align="left">Content ID</th>
    <th align="left">Data</th>

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
    <tr bgcolor="<%=(index%2==0)?"ivory":"white"%>">
        <td><%=fmt.format(log.getCreated()) %>
        </td>
        <td><%=providerDao.getProviderName(log.getProviderNo()) %>
        </td>
        <td><%=log.getAction() %>
        </td>
        <td><%=log.getContent() %>
        </td>
        <td><%=log.getContentId() != null && !"null".equals(log.getContentId()) ? Encode.forHtml(log.getContentId()) : "" %>
        </td>
        <td><%=log.getData() != null && !"null".equals(log.getData()) ? Encode.forHtml(log.getData()) : "" %>
        </td>


    </tr>
    <% index++;
    } %>
    </tbody>
</table>

</body>
</html>
