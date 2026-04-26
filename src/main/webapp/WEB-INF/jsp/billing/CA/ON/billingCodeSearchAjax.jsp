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
<%--
    billingCodeSearchAjax.jsp

    Purpose:
        AJAX endpoint that returns a JSON array of Ontario billing service code suggestions
        for use with jQuery UI Autocomplete on the Ontario billing form.

    Features:
        - Session-protected at the action layer (ViewBillingCodeSearchAjax2Action enforces _billing r)
        - View model populated by BillingCodeSearchAjaxDataAssembler:
            - merges code-prefix and description-keyword search results
            - deduplicates by serviceCode
            - limits to 20 items
        - JSON encoded via Jackson ObjectMapper (thread-safe shared instance)

    Request Parameters:
        term  (String, required) - autocomplete query, used by both code-prefix and
                                   description-substring searches simultaneously

    Response:
        Content-Type: application/json; charset=UTF-8
        Body: JSON array of suggestion objects
--%>
<%@ page contentType="application/json; charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingCodeSearchAjaxViewModel" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%! private static final ObjectMapper SHARED_MAPPER = new ObjectMapper(); %>
<%
    if (session.getAttribute("user") == null) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
    }

    BillingCodeSearchAjaxViewModel ajaxModel =
            (BillingCodeSearchAjaxViewModel) request.getAttribute("ajaxModel");
    if (ajaxModel == null) {
        ajaxModel = BillingCodeSearchAjaxViewModel.builder().build();
    }

    ObjectMapper jsonMapper = SHARED_MAPPER;
    StringBuilder json = new StringBuilder("[");
    boolean first = true;
    for (BillingCodeSearchAjaxViewModel.Suggestion s : ajaxModel.getSuggestions()) {
        if (!first) json.append(",");
        first = false;
        json.append("{");
        json.append("\"value\":").append(jsonMapper.writeValueAsString(s.value()));
        json.append(",\"label\":").append(jsonMapper.writeValueAsString(s.label()));
        json.append(",\"code\":").append(jsonMapper.writeValueAsString(s.code()));
        json.append(",\"description\":").append(jsonMapper.writeValueAsString(s.description()));
        json.append("}");
    }
    json.append("]");
    out.print(json.toString());
%>
