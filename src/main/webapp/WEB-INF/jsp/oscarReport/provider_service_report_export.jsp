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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%-- Exports monthly and inclusive-range encounter counts as UTF-8 CSV.
     Parameters: startDate and endDate (MM/yyyy); invalid ranges return HTTP 400.
     @since 2026-10-02 --%>

<%@ page pageEncoding="UTF-8" contentType="text/csv; charset=UTF-8" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.util.SqlUtils" %>
<%@page import="io.github.carlos_emr.carlos.web.ProviderServiceReportUIBean" %>
<%@page import="java.util.*" %>
<%@page import="org.caisi.model.*" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.model.*" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.web.*" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@page import="io.github.carlos_emr.carlos.utility.*" %>
<%@page import="java.text.*" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%
    String agencyName = io.github.carlos_emr.CarlosProperties.getInstance().getProperty("db_name", "");
    String startDateString = request.getParameter("startDate");
    String endDateString = request.getParameter("endDate");
    SimpleDateFormat dateFormatter = new SimpleDateFormat("MM/yyyy", Locale.ROOT);
    dateFormatter.setLenient(false);
    Date startDate;
    Date endDate;
    try {
        if (startDateString == null || endDateString == null
                || !startDateString.matches("[0-9]{2}/[0-9]{4}")
                || !endDateString.matches("[0-9]{2}/[0-9]{4}")) throw new ParseException("Invalid report month", 0);
        startDate = dateFormatter.parse(startDateString);
        endDate = dateFormatter.parse(endDateString);
        if (startDate.after(endDate)) throw new ParseException("Reversed report range", 0);
    } catch (ParseException e) {
        response.sendError(400, "Select a valid start and end month");
        return;
    }

    String filename = "provider_service_" + new SimpleDateFormat("yyyy-MM", Locale.ROOT).format(startDate)
            + "_" + new SimpleDateFormat("yyyy-MM", Locale.ROOT).format(endDate) + ".csv";
    response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

    // print header
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Agency Name");
        sb.append(',');
        sb.append("Program Name");
        sb.append(',');
        sb.append("Program Type");
        sb.append(',');
        sb.append("Date");
        sb.append(',');
        sb.append("total encounters face to face");
        sb.append(',');
        sb.append("total encounters by phone");
        sb.append(',');
        sb.append("total encounters with out client");
        sb.append(',');
        sb.append("unique client encountered face to face");
        sb.append(',');
        sb.append("unique clients encountered by phone");
        sb.append(',');
        sb.append("unique clients encountered with out client");
        sb.append(',');
        sb.append("total unique clients encountered");

        out.write(sb.toString());
        out.write('\n');
    }

    ProviderServiceReportUIBean providerServiceReportUIBean = new ProviderServiceReportUIBean(startDate, endDate);
    for (ProviderServiceReportUIBean.DataRow row : providerServiceReportUIBean.getDataRows()) {
        StringBuilder sb = new StringBuilder();
        sb.append(StringEscapeUtils.escapeCsv(agencyName));
        sb.append(',');
        sb.append(StringEscapeUtils.escapeCsv(row.programName));
        sb.append(',');
        sb.append(StringEscapeUtils.escapeCsv(row.programType));
        sb.append(',');
        sb.append(StringEscapeUtils.escapeCsv(row.date));
        sb.append(',');
        sb.append(row.encounterCounts.nonUniqueCounts.get(EncounterUtil.EncounterType.FACE_TO_FACE_WITH_CLIENT));
        sb.append(',');
        sb.append(row.encounterCounts.nonUniqueCounts.get(EncounterUtil.EncounterType.TELEPHONE_WITH_CLIENT));
        sb.append(',');
        sb.append(row.encounterCounts.nonUniqueCounts.get(EncounterUtil.EncounterType.ENCOUNTER_WITH_OUT_CLIENT));
        sb.append(',');
        sb.append(row.encounterCounts.uniqueCounts.get(EncounterUtil.EncounterType.FACE_TO_FACE_WITH_CLIENT));
        sb.append(',');
        sb.append(row.encounterCounts.uniqueCounts.get(EncounterUtil.EncounterType.TELEPHONE_WITH_CLIENT));
        sb.append(',');
        sb.append(row.encounterCounts.uniqueCounts.get(EncounterUtil.EncounterType.ENCOUNTER_WITH_OUT_CLIENT));
        sb.append(',');
        sb.append(row.encounterCounts.totalUniqueCount);

        out.write(sb.toString());
        out.write('\n');
    }
%>
