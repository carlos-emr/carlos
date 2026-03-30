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
<%-- AJAX endpoint: returns JSON array of billing service code suggestions for jQuery UI autocomplete.
     Accepts: term (string) - typed by the user.
     Returns: [{"value":"CODE","label":"CODE - Description","code":"CODE","description":"Description"}, ...]
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
        // search() applies the pattern to both serviceCode and description
        results = billingServiceDao.search(term + "%", "ON", new Date());
        // If the prefix search returns few results, also try a contains search on description
        if (results.size() < 5 && !term.matches(".*%.*")) {
            List<BillingService> descResults = billingServiceDao.search("%" + term + "%", "ON", new Date());
            for (BillingService bs : descResults) {
                boolean found = false;
                for (BillingService existing : results) {
                    if (bs.getServiceCode() != null && bs.getServiceCode().equals(existing.getServiceCode())) {
                        found = true;
                        break;
                    }
                }
                if (!found) results.add(bs);
            }
        }
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
