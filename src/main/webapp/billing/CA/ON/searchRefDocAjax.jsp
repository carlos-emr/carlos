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
    searchRefDocAjax.jsp

    Purpose:
        AJAX endpoint that returns a JSON array of referring doctor suggestions
        for use with jQuery UI Autocomplete on the Ontario billing form (billingON.jsp).
        Supports lookup by both doctor name and referral number.

    Features:
        - Session-protected: returns HTTP 401 if no active user session is found
        - Dispatches to referral-number search when the term is entirely numeric
        - Dispatches to name search (last name, first name) for all other input
        - Returns rich per-doctor data to support the two-row autocomplete display:
          row 1 — last name, first name, and specialty type as a Bootstrap badge;
          row 2 — street address and phone number
        - Limits output to 20 items for performance
        - All output values are OWASP-encoded for JavaScript safety

    Request Parameters:
        term  (String, required) - The text typed by the user; treated as a referral number
                                   when it consists entirely of digits (e.g. "12345"),
                                   or as a doctor name fragment otherwise (e.g. "Smith")

    Response:
        Content-Type: application/json; charset=UTF-8
        Body: JSON array of suggestion objects, e.g.:
              [{"value":"12345","lastName":"Smith","firstName":"John","specialtyType":"Cardiology",
                "streetAddress":"100 King St W","phoneNumber":"416-555-0100","referralNo":"12345"}, ...]

    @since 2026-03-30
--%>
<%@ page contentType="application/json; charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    if (session.getAttribute("user") == null) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
    }
    String term = request.getParameter("term");
    if (term == null) term = "";
    term = term.trim();

    List<ProfessionalSpecialist> results = new ArrayList<>();
    if (term.length() >= 1) {
        ProfessionalSpecialistDao dao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
        if (term.matches("[0-9]+")) {
            // Search by referral number
            results = dao.findByReferralNo(term);
        } else {
            // Search by name using contains matching on both last and first name.
            // Split on ", " to support "Last, First" typed format.
            String[] parts = term.split(",\\s*", 2);
            String lastPattern  = "%" + parts[0].trim() + "%";
            String firstPattern = parts.length > 1 ? "%" + parts[1].trim() + "%" : "%";
            // lastName LIKE '%term%' AND firstName LIKE '%'  (effective: lastName contains term)
            List<ProfessionalSpecialist> byLast  = dao.findByFullNameAndSpecialtyAndAddress(lastPattern, "%", null, null, false);
            // lastName LIKE '%' AND firstName LIKE '%term%' (effective: firstName contains term)
            List<ProfessionalSpecialist> byFirst = parts.length == 1
                    ? dao.findByFullNameAndSpecialtyAndAddress("%", lastPattern, null, null, false)
                    : new ArrayList<>();
            // Merge, deduplicating by referralNo
            java.util.LinkedHashMap<String, ProfessionalSpecialist> merged = new java.util.LinkedHashMap<>();
            for (ProfessionalSpecialist ps : byLast)  { merged.put(ps.getReferralNo() != null ? ps.getReferralNo() : ps.getLastName() + ps.getFirstName(), ps); }
            for (ProfessionalSpecialist ps : byFirst) { merged.put(ps.getReferralNo() != null ? ps.getReferralNo() : ps.getLastName() + ps.getFirstName(), ps); }
            results = new ArrayList<>(merged.values());
        }
    }

    int limit = Math.min(results.size(), 20);
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < limit; i++) {
        ProfessionalSpecialist ps = results.get(i);
        if (i > 0) json.append(",");
        String lastName    = ps.getLastName()       != null ? ps.getLastName()       : "";
        String firstName   = ps.getFirstName()      != null ? ps.getFirstName()      : "";
        String specialty   = ps.getSpecialtyType()  != null ? ps.getSpecialtyType()  : "";
        String address     = ps.getStreetAddress()  != null ? ps.getStreetAddress()  : "";
        String phone       = ps.getPhoneNumber()    != null ? ps.getPhoneNumber()    : "";
        String referralNo  = ps.getReferralNo()     != null ? ps.getReferralNo()     : "";
        json.append("{");
        json.append("\"value\":\"").append(Encode.forJavaScript(referralNo)).append("\"");
        json.append(",\"lastName\":\"").append(Encode.forJavaScript(lastName)).append("\"");
        json.append(",\"firstName\":\"").append(Encode.forJavaScript(firstName)).append("\"");
        json.append(",\"specialtyType\":\"").append(Encode.forJavaScript(specialty)).append("\"");
        json.append(",\"streetAddress\":\"").append(Encode.forJavaScript(address)).append("\"");
        json.append(",\"phoneNumber\":\"").append(Encode.forJavaScript(phone)).append("\"");
        json.append(",\"referralNo\":\"").append(Encode.forJavaScript(referralNo)).append("\"");
        json.append("}");
    }
    json.append("]");
    out.print(json.toString());
%>
