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
<%-- AJAX endpoint: returns JSON array of diagnosis code suggestions for jQuery UI autocomplete.
     Accepts: term (string) - typed by the user.
     Returns: [{"value":"CODE","label":"CODE - Description","code":"CODE","description":"Description"}, ...]
     @since 2026-03-30
--%>
<%@ page contentType="application/json; charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.util.*, io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DiagnosticCode" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    if (session.getAttribute("user") == null) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
    }
    String term = request.getParameter("term");
    if (term == null) term = "";
    term = term.trim();

    List<DiagnosticCode> results = new ArrayList<>();
    if (term.length() >= 1) {
        DiagnosticCodeDao diagnosticCodeDao = SpringUtils.getBean(DiagnosticCodeDao.class);
        if (Character.isDigit(term.charAt(0))) {
            // Code-prefix search (ICD-9 codes start with digits)
            results = diagnosticCodeDao.searchCode(term + "%");
        } else {
            // Description keyword search
            results = diagnosticCodeDao.searchText(term);
        }
    }

    int limit = Math.min(results.size(), 20);
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < limit; i++) {
        DiagnosticCode dc = results.get(i);
        if (i > 0) json.append(",");
        String code = dc.getDiagnosticCode() != null ? dc.getDiagnosticCode() : "";
        String desc = dc.getDescription() != null ? dc.getDescription() : "";
        json.append("{");
        json.append("\"value\":\"").append(Encode.forJavaScript(code)).append("\"");
        json.append(",\"label\":\"").append(Encode.forJavaScript(code)).append(" \u2013 ").append(Encode.forJavaScript(desc)).append("\"");
        json.append(",\"code\":\"").append(Encode.forJavaScript(code)).append("\"");
        json.append(",\"description\":\"").append(Encode.forJavaScript(desc)).append("\"");
        json.append("}");
    }
    json.append("]");
    out.print(json.toString());
%>
