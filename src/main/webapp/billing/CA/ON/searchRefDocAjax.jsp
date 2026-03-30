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
        Supports lookup by any combination of name, address, phone, or billing number.

    Features:
        - Session-protected: returns HTTP 401 if no active user session is found
        - Simultaneously searches last name (contains), first name (contains),
          billing/referral number (prefix), address (contains), and phone (contains)
        - Results are merged and deduplicated by referralNo; name matches listed first
        - Returns rich per-doctor data to support the two-row autocomplete display:
          row 1 — last name, first name, and specialty type as a Bootstrap badge;
          row 2 — street address and phone number
        - Limits output to 20 items for performance
        - All output values are JSON-encoded via Jackson ObjectMapper for spec-compliant output

    Request Parameters:
        term  (String, required) - Any fragment of: last name, first name, street address,
                                   phone number, or billing/referral number

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
<%@ page import="jakarta.persistence.EntityManagerFactory" %>
<%@ page import="jakarta.persistence.EntityManager" %>
<%@ page import="jakarta.persistence.Query" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%
    if (session.getAttribute("user") == null) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
    }
    String term = request.getParameter("term");
    if (term == null) term = "";
    term = term.trim();

    java.util.LinkedHashMap<String, ProfessionalSpecialist> merged = new java.util.LinkedHashMap<>();

    if (term.length() >= 1) {
        ProfessionalSpecialistDao dao = SpringUtils.getBean(ProfessionalSpecialistDao.class);

        // 1. Last name contains search (also handles "Last, First" comma format)
        List<ProfessionalSpecialist> byLastName = dao.findByFullName(term, "");
        if (byLastName != null) {
            for (ProfessionalSpecialist ps : byLastName) {
                String k = ps.getReferralNo() != null && !ps.getReferralNo().isEmpty()
                    ? ps.getReferralNo() : ps.getLastName() + "|" + ps.getFirstName();
                merged.putIfAbsent(k, ps);
            }
        }

        // 2. First name contains search
        List<ProfessionalSpecialist> byFirstName = dao.findByFullName("", term);
        if (byFirstName != null) {
            for (ProfessionalSpecialist ps : byFirstName) {
                String k = ps.getReferralNo() != null && !ps.getReferralNo().isEmpty()
                    ? ps.getReferralNo() : ps.getLastName() + "|" + ps.getFirstName();
                merged.putIfAbsent(k, ps);
            }
        }

        // 3. Billing/referral number prefix search
        List<ProfessionalSpecialist> byRefNo = dao.findByReferralNo(term + "%");
        if (byRefNo != null) {
            for (ProfessionalSpecialist ps : byRefNo) {
                String k = ps.getReferralNo() != null && !ps.getReferralNo().isEmpty()
                    ? ps.getReferralNo() : ps.getLastName() + "|" + ps.getFirstName();
                merged.putIfAbsent(k, ps);
            }
        }

        // 4. Address contains search
        List<ProfessionalSpecialist> byAddr = dao.findByFullNameAndSpecialtyAndAddress("", "", "", term, true);
        if (byAddr != null) {
            for (ProfessionalSpecialist ps : byAddr) {
                String k = ps.getReferralNo() != null && !ps.getReferralNo().isEmpty()
                    ? ps.getReferralNo() : ps.getLastName() + "|" + ps.getFirstName();
                merged.putIfAbsent(k, ps);
            }
        }

        // 5. Phone number contains search (direct JPQL, best-effort)
        try {
            EntityManagerFactory emf = SpringUtils.getBean(EntityManagerFactory.class);
            EntityManager em = emf.createEntityManager();
            try {
                Query q = em.createQuery(
                    "SELECT x FROM ProfessionalSpecialist x WHERE x.deleted = false " +
                    "AND x.phoneNumber LIKE :phone ORDER BY x.lastName, x.firstName");
                q.setParameter("phone", "%" + term + "%");
                @SuppressWarnings("unchecked")
                List<ProfessionalSpecialist> byPhone = (List<ProfessionalSpecialist>) q.getResultList();
                if (byPhone != null) {
                    for (ProfessionalSpecialist ps : byPhone) {
                        String k = ps.getReferralNo() != null && !ps.getReferralNo().isEmpty()
                            ? ps.getReferralNo() : ps.getLastName() + "|" + ps.getFirstName();
                        merged.putIfAbsent(k, ps);
                    }
                }
            } finally {
                em.close();
            }
        } catch (Exception ignore) {
            // Phone search is best-effort; proceed without it if unavailable
        }
    }

    // Use Jackson ObjectMapper for spec-compliant JSON string encoding
    // (Encode.forJavaScript escapes '-' as '\-' and '/' as '\/' which is invalid JSON)
    ObjectMapper jsonMapper = new ObjectMapper();
    List<ProfessionalSpecialist> results = new ArrayList<>(merged.values());
    int limit = Math.min(results.size(), 20);
    StringBuilder json = new StringBuilder("[");
    for (int i = 0; i < limit; i++) {
        ProfessionalSpecialist ps = results.get(i);
        if (i > 0) json.append(",");
        String lastName   = ps.getLastName()      != null ? ps.getLastName()      : "";
        String firstName  = ps.getFirstName()     != null ? ps.getFirstName()     : "";
        String specialty  = ps.getSpecialtyType() != null ? ps.getSpecialtyType() : "";
        String address    = ps.getStreetAddress() != null ? ps.getStreetAddress() : "";
        String phone      = ps.getPhoneNumber()   != null ? ps.getPhoneNumber()   : "";
        String referralNo = ps.getReferralNo()    != null ? ps.getReferralNo()    : "";
        json.append("{");
        json.append("\"value\":").append(jsonMapper.writeValueAsString(referralNo));
        json.append(",\"lastName\":").append(jsonMapper.writeValueAsString(lastName));
        json.append(",\"firstName\":").append(jsonMapper.writeValueAsString(firstName));
        json.append(",\"specialtyType\":").append(jsonMapper.writeValueAsString(specialty));
        json.append(",\"streetAddress\":").append(jsonMapper.writeValueAsString(address));
        json.append(",\"phoneNumber\":").append(jsonMapper.writeValueAsString(phone));
        json.append(",\"referralNo\":").append(jsonMapper.writeValueAsString(referralNo));
        json.append("}");
    }
    json.append("]");
    out.print(json.toString());
%>
