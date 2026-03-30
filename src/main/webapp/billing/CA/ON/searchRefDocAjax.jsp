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

    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.
--%>
<%-- AJAX endpoint: returns JSON array of referral doctor suggestions for jQuery UI autocomplete.
     Accepts: term (string) - typed by the user; searched as name (last, first) or referral number.
     Returns: [{"value":"referralNo","lastName":"...","firstName":"...","specialtyType":"...",
                "streetAddress":"...","phoneNumber":"...","referralNo":"..."}, ...]
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
            // Search by name (supports "Last, First" format)
            results = dao.search(term);
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
