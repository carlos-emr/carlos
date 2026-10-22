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

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%
    String roleName2$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName2$%>" objectName="_form" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_form");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<fmt:setBundle basename="oscarResources"/>

<%@ page
        import="io.github.carlos_emr.carlos.util.*, io.github.carlos_emr.carlos.form.*, io.github.carlos_emr.carlos.form.data.*,java.util.*, io.github.carlos_emr.carlos.providers.data.*, io.github.carlos_emr.carlos.prevention.*" %>
<%@ page import="io.github.carlos_emr.carlos.prevention.PreventionData" %>
<%@ page import="io.github.carlos_emr.carlos.providers.data.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.util.UtilDateUtilities" %>

<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%
    // The panel of a saved RH form: the injections recorded against its workflow. The ViewForm2Action gate checks
    // _form read for demographicNo, so only preventions of that patient are listed; a workflow id names a row of
    // the shared workflow table and is not, on its own, proof of whose chart this is. Every stored value is
    // encoded: the panel stayed empty until #4992, so its raw output was never reached.
    String id = request.getParameter("id");
    String date = request.getParameter("date");
    String demographicNo = request.getParameter("demographicNo");
    if (id != null && demographicNo != null) {
        ArrayList<Map<String, Object>> alist = PreventionData.getPreventionDataFromExt("workflowId", id);
        int shown = 0;
        for (int k = 0; k < alist.size(); k++) {
            Map<String, Object> hdata = alist.get(k);
            if (!demographicNo.trim().equals(String.valueOf(hdata.get("demographicNo")))) {
                continue;
            }
            shown++;
            String preventionId = String.valueOf(hdata.get("id"));
            Map<String, String> hextended = PreventionData.getPreventionKeyValues(preventionId);
            String refused = (String) hdata.get("refused");
            Date injectionDate = (Date) hdata.get("prevention_date_asDate");
            Date dueDate = UtilDateUtilities.StringToDate(date);
            String weeks = injectionDate != null && dueDate != null
                    ? String.valueOf(UtilDateUtilities.calculateGestationAge(injectionDate, dueDate)) : "";
%>
<fieldset>
    <legend><fmt:message key="form.rhInjectionDisplay.legendTitle"/> <%= shown %> &nbsp;
        &nbsp; &nbsp; <fmt:message key="form.rhInjectionDisplay.date"/> <%= SafeEncode.forHtmlContent((String) hdata.get("preventionDate")) %> &nbsp; &nbsp;
        &nbsp;
        <fmt:message key="form.rhInjectionDisplay.weeks"/> <%= SafeEncode.forHtmlContent(weeks) %>
    </legend>
    <%if ("1".equals(refused)) { %> <fmt:message key="form.rhInjectionDisplay.refused"/> <button
        type="button" class="rh-injection-delete"
        onclick="deleteInjection('<%= SafeEncode.forJavaScriptAttribute(preventionId) %>')"><fmt:message key="form.rhInjectionDisplay.delete"/></button> <%} else {%> <fmt:message key="form.rhInjectionDisplay.given"/>
    <fmt:message key="form.rhInjectionDisplay.by"/> <%= SafeEncode.forHtmlContent(ProviderData.getProviderName((String) hdata.get("provider_no"))) %>
    <fmt:message key="form.rhInjectionDisplay.location"/> <%= SafeEncode.forHtmlContent(hextended.get("location")) %> <fmt:message key="form.rhInjectionDisplay.lotNumber"/> <%= SafeEncode.forHtmlContent(hextended.get("lot")) %>
    <fmt:message key="form.rhInjectionDisplay.productNumber"/> <%= SafeEncode.forHtmlContent(hextended.get("product")) %> <fmt:message key="form.rhInjectionDisplay.dosage"/> <%= SafeEncode.forHtmlContent(hextended.get("dosage")) %>
    <button type="button" class="rh-injection-delete"
       onclick="deleteInjection('<%= SafeEncode.forJavaScriptAttribute(preventionId) %>')"><fmt:message key="form.rhInjectionDisplay.delete"/></button> <br>
    <fmt:message key="form.rhInjectionDisplay.reason"/> <%= SafeEncode.forHtmlContent(hextended.get("reason")) %> <%}%>
</fieldset>
<% }
}%>
