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
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%-- This JSP is the first page you see when you enter 'report by template' --%>
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
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        return;
    }
%>


<%@ page import="java.util.*,io.github.carlos_emr.carlos.report.reportByTemplate.*" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ page import="java.io.InputStream" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="jakarta.servlet.http.Part" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Flowsheet" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.FlowsheetDao" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%
    String contentType = request.getContentType();
    boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");

    if (isMultipart) {
        try {
            Collection<Part> parts = request.getParts();
            for (Part part : parts) {
                if (part.getSubmittedFileName() != null) {
                    String contents;
                    try (InputStream is = part.getInputStream()) {
                        contents = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    }

                    MeasurementFlowSheet fs = MeasurementTemplateFlowSheetConfig.getInstance().validateFlowsheet(contents);
                    if (fs != null) {
                        Flowsheet f = new Flowsheet();
                        f.setContent(contents);
                        f.setCreatedDate(new java.util.Date());
                        f.setEnabled(true);
                        f.setExternal(false);
                        f.setName(fs.getName());

                        FlowsheetDao flowsheetDao = (FlowsheetDao) SpringUtils.getBean(FlowsheetDao.class);
                        flowsheetDao.persist(f);
                        MeasurementTemplateFlowSheetConfig.getInstance().reloadFlowsheets();
                    }
                }
            }
        } catch (Exception e) {
            // Error handling - redirect will follow
        }
    }

    response.sendRedirect("manageFlowsheets.jsp");
%>
