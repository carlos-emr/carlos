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
    billingDigSearchAjax.jsp

    Purpose:
        AJAX endpoint that returns a JSON array of ICD-9 diagnosis code suggestions
        for use with jQuery UI Autocomplete on the Ontario billing form.

    Features:
        - Session-protected at the action layer (ViewBillingDigSearchAjax2Action enforces _billing r)
        - View model populated by BillingDxCodeDataAssembler.assembleAjax:
            - digit-prefix terms route to code-prefix search
            - letter-prefix terms route to description keyword search
            - limits to 20 items
        - JSON encoded via Jackson ObjectMapper (thread-safe shared instance)
--%>
<%@ page contentType="application/json; charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%@ page import="io.github.carlos_emr.carlos.billings.ca.on.data.BillingDigSearchAjaxViewModel" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%! private static final ObjectMapper SHARED_MAPPER = new ObjectMapper(); %>
<%
    if (session.getAttribute("user") == null) {
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return;
    }

    BillingDigSearchAjaxViewModel ajaxModel =
            (BillingDigSearchAjaxViewModel) request.getAttribute("ajaxModel");
    if (ajaxModel == null) {
        ajaxModel = BillingDigSearchAjaxViewModel.builder().build();
    }

    ObjectMapper jsonMapper = SHARED_MAPPER;
    StringBuilder json = new StringBuilder("[");
    boolean first = true;
    for (BillingDigSearchAjaxViewModel.Suggestion s : ajaxModel.getSuggestions()) {
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
