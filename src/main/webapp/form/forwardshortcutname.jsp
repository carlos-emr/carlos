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

<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart"
                   rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logout.jsp");%>
</security:oscarSec>

<%@ page import="java.net.URLDecoder, io.github.carlos_emr.carlos.form.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.form.data.FrmData" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LogSanitizer" %>

<%

    // forward to the page 'form_link'
    if (true) {
        out.clearBuffer();
        //forward to the current specified form, e.g. contextPath/form/formar.jsp?demographic_no=
        String strFrm = URLDecoder.decode(request.getParameter("formname"), "UTF-8");
        String[] formPath = (new FrmData()).getShortcutFormValue(request.getParameter("demographic_no"), strFrm);
        formPath[0] = formPath[0].trim();

        // Normalize the deprecated "../" prefix used in older DB entries
        // (e.g. "../form/formX.jsp?..." → "/form/formX.jsp?...").
        if (formPath[0].startsWith("../")) {
            formPath[0] = formPath[0].substring(2);
        }

        // Validate the DB-resolved path to prevent path traversal (CWE-22).
        // Extract the path portion (before the query string) and ensure it is an
        // absolute webapp path with no traversal sequences — checking both the
        // literal ".." and the common URL-encoded variant "%2e" to defend against
        // canonicalization bypasses.
        String pathPortion = formPath[0].contains("?")
                ? formPath[0].substring(0, formPath[0].indexOf('?'))
                : formPath[0];
        String lowerPath = pathPortion.toLowerCase(java.util.Locale.ROOT);
        if (!pathPortion.startsWith("/") || lowerPath.contains("..") || lowerPath.contains("%2e")) {
            MiscUtils.getLogger().warn("forwardshortcutname.jsp: blocked invalid form path from DB: {}",
                    LogSanitizer.sanitize(pathPortion));
            response.sendError(400, "Invalid form path");
            return;
        }

        String appointmentNo = request.getParameter("appointmentNo");

        String nextPage = formPath[0] +
                request.getParameter("demographic_no") +
                ((appointmentNo != null) ? "&appointmentNo=" + appointmentNo : "") +
                ((request.getParameter("formId") != null) ? "&formId=" + request.getParameter("formId") : "&formId=" + formPath[1]);
        MiscUtils.getLogger().info("Forwarding to page : {}", LogSanitizer.sanitize(nextPage));
        request.getRequestDispatcher(nextPage).include(request, response);
        return;
    }
%>
