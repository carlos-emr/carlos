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

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="org.apache.commons.lang3.time.DateFormatUtils" %>
<%@page import="java.text.SimpleDateFormat" %>
<%@page import="ca.openosp.openo.PMmodule.model.Program" %>
<%@page import="java.util.HashSet" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="ca.openosp.openo.PMmodule.service.ProgramManager" %>
<%@page import="ca.openosp.openo.commn.model.Provider" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.managers.ProviderManager2" %>
<%@page import="java.util.Date" %>
<%@page import="java.util.List" %>
<%@page import="ca.openosp.openo.commn.model.CdsFormOption" %>
<%@page import="ca.openosp.openo.web.Cds4ReportUIBean" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    ProviderManager2 providerManager = (ProviderManager2) SpringUtils.getBean(ProviderManager2.class);
    ProgramManager programManager = (ProgramManager) SpringUtils.getBean(ProgramManager.class);

    SimpleDateFormat sdf = new SimpleDateFormat(DateFormatUtils.ISO_DATE_FORMAT.getPattern());
    Date startDate = sdf.parse(request.getParameter("startDate"));
    Date endDateInclusive = sdf.parse(request.getParameter("endDate"));


    String functionalCentreId = request.getParameter("functionalCentreId");

    // null for none selected, array of providerIds if selected
    String[] providerIdList = request.getParameterValues("providerIds");
    String[] programIdListTemp = request.getParameterValues("programIds");
    HashSet<Integer> programIds = null;
    if (programIdListTemp != null && programIdListTemp.length > 0) {
        programIds = new HashSet<Integer>();

        for (String s : programIdListTemp) {
            s = StringUtils.trimToNull(s);
            if (s != null) {
                programIds.add(new Integer(s));
            }
        }
    }

    Cds4ReportUIBean cds4ReportUIBean = new Cds4ReportUIBean(loggedInInfo, functionalCentreId, startDate, endDateInclusive, providerIdList, programIds);

    List<CdsFormOption> cdsFormOptions = Cds4ReportUIBean.getCdsFormOptions();

    StringBuilder providerNamesList = new StringBuilder();
    if (providerIdList != null && providerIdList.length > 0) {
        for (String providerId : providerIdList) {
            Provider provider = providerManager.getProvider(loggedInInfo, providerId);
            providerNamesList.append(provider.getFormattedName() + " (" + provider.getProviderNo() + "), ");
        }
    }

    StringBuilder programNamesList = new StringBuilder();
    if (programIds != null) {
        for (Integer programId : programIds) {
            Program program = programManager.getProgram(programId);
            programNamesList.append(program.getName() + " (" + program.getType() + "), ");
        }
    }
%>

<h3>CDS Report</h3>
<span style="font-weight:bold">Functional Centre : </span><%=cds4ReportUIBean.getFunctionalCentreDescription()%>
<br/>
<span style="font-weight:bold">Dates : </span><%=cds4ReportUIBean.getDateRangeForDisplay()%>
<br/>

<%
    if (providerIdList != null) {
%>
<span style="font-weight:bold">Providers : </span><%=StringEscapeUtils.escapeHtml4(providerNamesList.toString())%>
<br/>
<%
    }

    if (programIds != null) {
%>
<span style="font-weight:bold">Programs : </span><%=StringEscapeUtils.escapeHtml4(programNamesList.toString())%>
<br/>
<%
    }
%>

<table class="table table-bordered table-striped table-hover">
    <thead>
    <tr>
        <th>CDS Category ID</th>
        <th>CDS Category Description</th>
        <th>Multi<br/>Admn</th>
        <%
            for (int i = 0; i < Cds4ReportUIBean.NUMBER_OF_COHORT_BUCKETS; i++) {
        %>
        <th>Coh<br/><%=i%>
        </th>
        <%
            }
        %>
        <th>Coh<br/>Total</th>
    </tr>
    </thead>
    <tbody>
    <%
        for (CdsFormOption cdsFormOption : cdsFormOptions) {
            int[] dataRow = cds4ReportUIBean.getDataRow(cdsFormOption);
    %>
    <tr>
        <td><%=StringEscapeUtils.escapeHtml4(cdsFormOption.getCdsDataCategory())%>
        </td>
        <td><%=StringEscapeUtils.escapeHtml4(cdsFormOption.getCdsDataCategoryName())%>
        </td>
        <%
            for (int dataElement : dataRow) {
        %>
        <td><%=dataElement == -1 ? "N/A" : String.valueOf(dataElement)%>
        </td>
        <%
            }
        %>
        <td><%=Cds4ReportUIBean.getCohortTotal(dataRow)%>
        </td>
    </tr>
    <%
        }
    %>
    </tbody>
</table>