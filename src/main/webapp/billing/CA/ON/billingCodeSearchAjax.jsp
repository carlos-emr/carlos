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
    billingCodeSearchAjax.jsp

    Purpose:
        AJAX endpoint that returns a JSON array of Ontario billing service code suggestions
        for use with jQuery UI Autocomplete on the Ontario billing form (billingON.jsp).

    Features:
        - Session-protected: returns HTTP 401 if no active user session is found
        - Always searches both code prefix (e.g. "A00") and description keyword (e.g. "visit")
        - Merges both result sets, code-prefix matches listed first, deduplicated by serviceCode
        - Limits output to 20 items for performance
        - All output values are OWASP-encoded for JavaScript safety

    Request Parameters:
        term  (String, required) - The text typed by the user; matched against both the
                                   service code prefix (e.g. "A001") and the description
                                   text (e.g. "general assessment") simultaneously

    Response:
        Content-Type: application/json; charset=UTF-8
        Body: JSON array of suggestion objects, e.g.:
              [{"value":"A001","label":"A001 – General assessment","code":"A001","description":"General assessment"}, ...]

    @since 2026-03-30
--%>
<%@ page contentType="application/json; charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.util.*, java.util.Date" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.BillingServiceDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.BillingService" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    if (session.getAttribute("user") == null) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
    }
    String term = request.getParameter("term");
    if (term == null) term = "";
    term = term.trim();

    List<BillingService> results = new ArrayList<>();
    if (term.length() >= 1) {
        BillingServiceDao billingServiceDao = SpringUtils.getBean(BillingServiceDao.class);
        java.util.LinkedHashMap<String, BillingService> merged = new java.util.LinkedHashMap<>();

        // Code-prefix search: finds codes starting with the term (e.g. "A00" → A001, A002...)
        List<BillingService> codeResults = billingServiceDao.search(term.toUpperCase() + "%", "ON", new Date());
        if (codeResults != null) {
            for (BillingService bs : codeResults) {
                String key = bs.getServiceCode() != null ? bs.getServiceCode() : "";
                if (!key.isEmpty()) merged.put(key, bs);
            }
        }

        // Description-contains search: finds codes whose description contains the term
        List<BillingService> descResults = billingServiceDao.search("%" + term + "%", "ON", new Date());
        if (descResults != null) {
            for (BillingService bs : descResults) {
                String key = bs.getServiceCode() != null ? bs.getServiceCode() : "";
                if (!key.isEmpty()) merged.putIfAbsent(key, bs);
            }
        }

        results = new ArrayList<>(merged.values());
    }

    int limit = Math.min(results.size(), 20);
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < limit; i++) {
        BillingService bs = results.get(i);
        if (i > 0) json.append(",");
        String code = bs.getServiceCode() != null ? bs.getServiceCode() : "";
        String desc = bs.getDescription() != null ? bs.getDescription() : "";
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
