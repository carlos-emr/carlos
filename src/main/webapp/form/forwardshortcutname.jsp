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

<%@ page import="java.net.URLDecoder, java.net.URLEncoder, io.github.carlos_emr.carlos.form.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.form.data.FrmData" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LogSanitizer" %>

<%

    // forward to the page 'form_link'
    if (true) {
        out.clearBuffer();
        //forward to the current specified form, e.g. contextPath/form/formar.jsp?demographic_no=
        String rawFormName = request.getParameter("formname");
        String strFrm = rawFormName == null ? null : URLDecoder.decode(rawFormName, "UTF-8");

        String demoNo = request.getParameter("demographic_no");
        if (demoNo != null && !demoNo.matches("\\d+")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        final String validatedDemoNo = demoNo;
        final jakarta.servlet.http.HttpServletRequest originalRequest = request;
        request = new jakarta.servlet.http.HttpServletRequestWrapper(originalRequest) {
            @Override
            public String getParameter(String name) {
                if ("demographic_no".equals(name)) {
                    return validatedDemoNo;
                }
                return super.getParameter(name);
            }

            @Override
            public String[] getParameterValues(String name) {
                if ("demographic_no".equals(name)) {
                    return validatedDemoNo != null ? new String[] { validatedDemoNo } : super.getParameterValues(name);
                }
                return super.getParameterValues(name);
            }

            @Override
            public java.util.Map<String, String[]> getParameterMap() {
                if (validatedDemoNo == null) {
                    return super.getParameterMap();
                }
                java.util.Map<String, String[]> parameterMap =
                        new java.util.HashMap<String, String[]>(super.getParameterMap());
                parameterMap.put("demographic_no", new String[] { validatedDemoNo });
                return java.util.Collections.unmodifiableMap(parameterMap);
            }
        };

        String formId = request.getParameter("formId");
        if (formId != null && !formId.matches("\\d+")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String[] formPath = (new FrmData()).getShortcutFormValue(demoNo, strFrm); // deepcode ignore SqlInjection: demoNo validated as digits-only (line 56); formName used in parameterized DAO query (EncounterFormDao.findByFormName); table name from DB validated by regex [a-zA-Z][a-zA-Z0-9_]* at FrmData.java:214
        formPath[0] = formPath[0].trim();

        // Normalize the deprecated "../" prefix used in older DB entries
        // (e.g. "../form/formX.jsp?..." → "/form/formX.jsp?...").
        if (formPath[0].startsWith("../")) {
            formPath[0] = formPath[0].substring(2);
        }

        // Validate the DB-resolved path to prevent path traversal (CWE-22) and
        // unsafe server-side dispatch into protected internal resources.
        // Extract the path portion (before the query string) and ensure it is an
        // absolute webapp path with no traversal sequences — checking both the
        // literal ".." and the common URL-encoded variant "%2e" (case-insensitive)
        // to defend against canonicalization bypasses.  Additionally, allowlist the
        // expected path prefixes so that even a compromised DB entry cannot reach
        // protected directories like /WEB-INF/ or /META-INF/.
        //
        // Allowed path patterns after normalization:
        //   /form/...    — form JSPs and .do files under the /form/ directory
        //   /billing/... — BC billing form (viewformwcb.do)
        //   /form*.do    — root-level form .do actions (formBPMH.do, formeCARES.do)
        //                  matched via /form[a-z0-9]+\.do to avoid over-permitting
        int queryIndex = formPath[0].indexOf('?');
        String pathPortion = queryIndex != -1 ? formPath[0].substring(0, queryIndex) : formPath[0];
        String normalizedPathPortion = pathPortion.toLowerCase(java.util.Locale.ROOT);
        boolean isAllowedFormPath = normalizedPathPortion.startsWith("/form/")
                || normalizedPathPortion.startsWith("/billing/")
                || normalizedPathPortion.matches("/form[a-z0-9]+\\.do");
        if (!pathPortion.startsWith("/")
                || pathPortion.contains("..")
                || normalizedPathPortion.contains("%2e")
                || normalizedPathPortion.startsWith("/web-inf/")
                || normalizedPathPortion.startsWith("/meta-inf/")
                || !isAllowedFormPath) {
            MiscUtils.getLogger().warn("forwardshortcutname.jsp: blocked invalid form path from DB: {}",
                    LogSanitizer.sanitize(pathPortion));
            response.sendError(400, "Invalid form path");
            return;
        }

        String appointmentNo = request.getParameter("appointmentNo");
        if (appointmentNo != null && !appointmentNo.matches("\\d+")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String nextPage = formPath[0] +
                demoNo +
                ((appointmentNo != null) ? "&appointmentNo=" + appointmentNo : "") +
                ((formId != null) ? "&formId=" + formId : "&formId=" + formPath[1]);
        MiscUtils.getLogger().info("Forwarding to page : {}", LogSanitizer.sanitize(nextPage)); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
        request.getRequestDispatcher(nextPage).include(request, response);
        return;
    }
%>
