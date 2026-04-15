<%--
    Copyright (c) 2007 Peter Hutten-Czapski based on OSCAR general requirements
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
    billingDigSearchAjax.jsp

    Purpose:
        AJAX endpoint that returns a JSON array of ICD-9 diagnosis code suggestions
        for use with jQuery UI Autocomplete on the Ontario billing form (billingON.jsp).

    Features:
        - Session-protected: returns HTTP 401 if no active user session is found
        - Dispatches to code-prefix search when the term begins with a digit (ICD-9 format)
        - Dispatches to description keyword search when the term begins with a letter
        - Limits output to 20 items for performance
        - All output values are JSON-encoded via Jackson ObjectMapper for spec-compliant output

    Request Parameters:
        term  (String, required) - The text typed by the user; interpreted as an ICD-9
                                   code prefix when it starts with a digit (e.g. "250"),
                                   or as a description keyword otherwise (e.g. "diabetes")

    Response:
        Content-Type: application/json; charset=UTF-8
        Body: JSON array of suggestion objects, e.g.:
              [{"value":"250","label":"250 – Diabetes mellitus","code":"250","description":"Diabetes mellitus"}, ...]

    @since 2026-03-30
--%>
<%@ page contentType="application/json; charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.util.*, io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DiagnosticCode" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%! private static final ObjectMapper SHARED_MAPPER = new ObjectMapper(); %>
<%
    if (session.getAttribute("user") == null) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
    }
    String term = request.getParameter("term");
    if (term == null) term = "";
    term = term.trim();

    List<DiagnosticCode> results = new ArrayList<>();
    if (term.length() >= 2) {
        DiagnosticCodeDao diagnosticCodeDao = SpringUtils.getBean(DiagnosticCodeDao.class);
        if (Character.isDigit(term.charAt(0))) {
            // Code-prefix search (ICD-9 codes start with digits)
            results = diagnosticCodeDao.searchCode(term + "%");
        } else {
            // Description keyword search
            results = diagnosticCodeDao.searchText(term);
        }
    }

    // Use shared Jackson ObjectMapper for spec-compliant JSON string encoding (thread-safe, reused across requests)
    ObjectMapper jsonMapper = SHARED_MAPPER;
    int limit = Math.min(results.size(), 20);
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < limit; i++) {
        DiagnosticCode dc = results.get(i);
        if (i > 0) json.append(",");
        String code = dc.getDiagnosticCode() != null ? dc.getDiagnosticCode() : "";
        String desc = dc.getDescription() != null ? dc.getDescription() : "";
        String label = code + " \u2013 " + desc;
        json.append("{");
        json.append("\"value\":").append(jsonMapper.writeValueAsString(code));
        json.append(",\"label\":").append(jsonMapper.writeValueAsString(label));
        json.append(",\"code\":").append(jsonMapper.writeValueAsString(code));
        json.append(",\"description\":").append(jsonMapper.writeValueAsString(desc));
        json.append("}");
    }
    json.append("]");
    out.print(json.toString());
%>
