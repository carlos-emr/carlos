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

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao" %>
<%
    ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
%>
<%
    if (session.getAttribute("user") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    }
    String strLimit1 = "0";
    String strLimit2 = "10";
    if (request.getParameter("limit1") != null) strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit2 = request.getParameter("limit2");

    Properties prop = null;
    ArrayList<Properties> alist = new ArrayList<Properties>();
    String param = request.getParameter("param") == null ? "" : request.getParameter("param");
    String param2 = request.getParameter("param2") == null ? "" : request.getParameter("param2");
    String toname = request.getParameter("toname") == null ? "" : request.getParameter("toname");
    String toaddress1 = request.getParameter("toaddress1") == null ? "" : request.getParameter("toaddress1");
    String tophone = request.getParameter("tophone") == null ? "" : request.getParameter("tophone");
    String tofax = request.getParameter("tofax") == null ? "" : request.getParameter("tofax");
    String keyword = request.getParameter("keyword");

    // Safely extract form index + element name from full JS path expressions like
    // "document.forms[0].elements['fieldname'].value" or "document.forms[1].elements['fieldname'].value"
    // passed by callers (e.g. billingON.jsp uses forms[0], billingONCorrection.jsp uses forms[1])
    // Allows dots in element names (e.g. "pref.default_dx_code" from UserPreferences.jsp)
    java.util.regex.Pattern pathPattern = java.util.regex.Pattern.compile(
        "^document\\.forms\\[(\\d+)\\]\\.elements\\['([a-zA-Z0-9_.]+)'\\]\\.value$");
    String[] paramParts = extractJsPath(pathPattern, param, "param");
    String paramFormIdx = paramParts != null ? paramParts[0] : null;
    String paramField = paramParts != null ? paramParts[1] : null;

    String[] param2Parts = extractJsPath(pathPattern, param2, "param2");
    String param2FormIdx = param2Parts != null ? param2Parts[0] : null;
    String param2Field = param2Parts != null ? param2Parts[1] : null;

    String[] tonameParts = extractJsPath(pathPattern, toname, "toname");
    String tonameFormIdx = tonameParts != null ? tonameParts[0] : null;
    String tonameField = tonameParts != null ? tonameParts[1] : null;

    String[] toaddress1Parts = extractJsPath(pathPattern, toaddress1, "toaddress1");
    String toaddress1FormIdx = toaddress1Parts != null ? toaddress1Parts[0] : null;
    String toaddress1Field = toaddress1Parts != null ? toaddress1Parts[1] : null;

    String[] tophoneParts = extractJsPath(pathPattern, tophone, "tophone");
    String tophoneFormIdx = tophoneParts != null ? tophoneParts[0] : null;
    String tophoneField = tophoneParts != null ? tophoneParts[1] : null;

    String[] tofaxParts = extractJsPath(pathPattern, tofax, "tofax");
    String tofaxFormIdx = tofaxParts != null ? tofaxParts[0] : null;
    String tofaxField = tofaxParts != null ? tofaxParts[1] : null;
    List<ProfessionalSpecialist> professionalSpecialists = null;

    if (request.getParameter("submit") != null && (request.getParameter("submit").equals("Search")
            || request.getParameter("submit").equals("Next Page") || request.getParameter("submit").equals("Last Page"))) {


        String search_mode = request.getParameter("search_mode") == null ? "search_name" : request.getParameter("search_mode");
        String orderBy = request.getParameter("orderby") == null ? "last_name,first_name" : request.getParameter("orderby");
        String where = "";


        if ("search_name".equals(search_mode)) {
            String[] temp = keyword.split("\\,\\p{Space}*");

            if (temp.length > 1) {
                professionalSpecialists = professionalSpecialistDao.findByFullName(temp[0], temp[1]);
            } else {
                professionalSpecialists = professionalSpecialistDao.findByLastName(temp[0]);
            }
        } else if ("specialty".equals(search_mode)) {
            professionalSpecialists = professionalSpecialistDao.findBySpecialty(keyword);
        } else if ("referral_no".equals(search_mode)) {
            professionalSpecialists = professionalSpecialistDao.findByReferralNo(keyword);
        }
    }

    if (professionalSpecialists == null) {
        professionalSpecialists = professionalSpecialistDao.findAll();
    }
    if (professionalSpecialists != null) {
        for (ProfessionalSpecialist professionalSpecialist : professionalSpecialists) {
            prop = new Properties();
            prop.setProperty("referral_no", (professionalSpecialist.getReferralNo() != null ? professionalSpecialist.getReferralNo() : ""));
            prop.setProperty("last_name", (professionalSpecialist.getLastName() != null ? professionalSpecialist.getLastName() : ""));
            prop.setProperty("first_name", (professionalSpecialist.getFirstName() != null ? professionalSpecialist.getFirstName() : ""));
            prop.setProperty("specialty", (professionalSpecialist.getSpecialtyType() != null ? professionalSpecialist.getSpecialtyType() : ""));
            prop.setProperty("phone", (professionalSpecialist.getPhoneNumber() != null ? professionalSpecialist.getPhoneNumber() : ""));
            prop.setProperty("to_fax", (professionalSpecialist.getFaxNumber() != null ? professionalSpecialist.getFaxNumber() : ""));
            prop.setProperty("to_name", "Dr. " + professionalSpecialist.getFirstName() + " " + professionalSpecialist.getLastName());
            prop.setProperty("to_address", (professionalSpecialist.getStreetAddress() != null ? professionalSpecialist.getStreetAddress() : ""));
            alist.add(prop);
        }
    }

%>

<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Properties" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>

<%!
    /**
     * Extracts form index and element name from a JS path expression like
     * "document.forms[0].elements['fieldname'].value".
     * @return String[]{formIdx, elementName} or null if value is empty or doesn't match
     */
    private String[] extractJsPath(java.util.regex.Pattern pattern, String value, String paramName) {
        if (value == null || value.isEmpty()) return null;
        java.util.regex.Matcher m = pattern.matcher(value);
        if (m.matches()) return new String[]{m.group(1), m.group(2)};
        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
            "searchRefDoc.jsp: '" + paramName + "' did not match expected JS path format (length="
            + value.length() + ")");
        return null;
    }
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<!DOCTYPE html>
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.optChooseSpec"/></title>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet"> <!-- Bootstrap 2.3.1 -->
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/jquery.dataTables.min.css"
              rel="stylesheet">
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->

        <script>
            <%if(paramField != null && param2Field != null) {%>

            function typeInData2(data1, data2) {
                opener.document.forms[<%= paramFormIdx %>].elements["<%= Encode.forJavaScript(paramField) %>"].value = data1;
                opener.document.forms[<%= param2FormIdx %>].elements["<%= Encode.forJavaScript(param2Field) %>"].value = data2;
                self.close();
            }

            <%}%>

            function typeInData3(billno, toname, toaddress, tophone, tofax) {
                var fieldsSet = false;
                <%if(paramField != null) {%>
                opener.document.forms[<%= paramFormIdx %>].elements["<%= Encode.forJavaScript(paramField) %>"].value = billno;
                fieldsSet = true;
                <%}
                  if(tonameField != null) {%>
                opener.document.forms[<%= tonameFormIdx %>].elements["<%= Encode.forJavaScript(tonameField) %>"].value = toname;
                fieldsSet = true;
                <%}
                  if(toaddress1Field != null) {%>
                opener.document.forms[<%= toaddress1FormIdx %>].elements["<%= Encode.forJavaScript(toaddress1Field) %>"].value = toaddress;
                fieldsSet = true;
                <%}
                  if(tophoneField != null) {%>
                opener.document.forms[<%= tophoneFormIdx %>].elements["<%= Encode.forJavaScript(tophoneField) %>"].value = tophone;
                fieldsSet = true;
                <%}
                  if(tofaxField != null) {%>
                opener.document.forms[<%= tofaxFormIdx %>].elements["<%= Encode.forJavaScript(tofaxField) %>"].value = tofax;
                fieldsSet = true;
                <%}%>
                if (!fieldsSet) {
                    alert("Error: Unable to transfer referral doctor data to the billing form. Please close this window and try again.");
                    return;
                }
                self.close();
            }
        </script>
        <script>
            jQuery(document).ready(function () {
                jQuery('#tblDocs').DataTable({
                    "lengthMenu": [[10, 25, 50, -1], [10, 25, 50, "<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.LeftNavBar.AllLabs"/>"]],
                    "order": [],
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18n.datatablescode"/>.json"
                    }
                });
            });
        </script>
    </head>
    <body>
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.ConsultationFormRequest.optChooseSpec"/></h3>&nbsp;<%= Encode.forHtml(keyword == null ? "" : keyword) %>&nbsp;<input
            type="button" class="btn-link" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.formAllProviders"/>"
            onclick="location = location.href.replace(/(\?|\&)(keyword)([^&]*)/, '').replace(/(\?|\&)(submit)([^&]*)/, '');">
    <div class="container-fluid">
        <table style="width:100%" id="tblDocs" class="table table-sm">
            <thead>
            <tr class="title">
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.referralNo"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.lastName"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.firstName"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.specialistType"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.phone"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.config.AddSpecialist.address"/></th>
            </tr>
            </thead>
            <tbody>
            <%
                for (int i = 0; i < alist.size(); i++) {
                    prop = (Properties) alist.get(i);
                    String bgColor = i % 2 == 0 ? "#f9f9f9" : "#ffffff";
                    String strOnClick;
                    // When param2 was provided and matched (two-field update), use typeInData2;
                    // otherwise fall back to typeInData3 for multi-field update
                    if (param2Field != null) {
                        strOnClick = "typeInData2('" + Encode.forJavaScriptAttribute(prop.getProperty("referral_no", "")) + "','" + Encode.forJavaScriptAttribute(prop.getProperty("last_name", "") + "," + prop.getProperty("first_name", "")) + "')";
                    } else {
                        strOnClick = "typeInData3('" + Encode.forJavaScriptAttribute(prop.getProperty("referral_no", "")) + "', '" + Encode.forJavaScriptAttribute(prop.getProperty("to_name", "")) + "', '" + Encode.forJavaScriptAttribute(prop.getProperty("to_address", "")) + "', '" + Encode.forJavaScriptAttribute(prop.getProperty("phone", "")) + "', '" + Encode.forJavaScriptAttribute(prop.getProperty("to_fax", "")) + "')";
                    }
            %>
            <tr style="background-color:<%=bgColor%>"
                onmouseover="this.style.cursor='pointer';this.style.backgroundColor='LightBlue';"
                onmouseout="this.style.backgroundColor='<%=bgColor%>'"
                onClick="<%=strOnClick%>">
                <td><%=Encode.forHtml(prop.getProperty("referral_no", ""))%>
                </td>
                <td><%=Encode.forHtml(prop.getProperty("last_name", ""))%>
                </td>
                <td><%=Encode.forHtml(prop.getProperty("first_name", ""))%>
                </td>
                <td><%=Encode.forHtml(prop.getProperty("specialty", ""))%>
                </td>
                <td title="<fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.config.EditSpecialists.fax"/> <%=Encode.forHtml(prop.getProperty("to_fax", ""))%>"><%=Encode.forHtml(prop.getProperty("phone", ""))%>
                </td>
                <td style="max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;"
                    title="<%=Encode.forHtml(prop.getProperty("to_address", ""))%>"><%=Encode.forHtml(prop.getProperty("to_address", ""))%>
                </td>
            </tr>
            <% } %>
            </tbody>
        </table>
        <br>
        <a class="btn btn-secondary"
           href="${pageContext.request.contextPath}/encounter/oscarConsultationRequest/config/EditSpecialists.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="encounter.oscarConsultationRequest.config.EditSpecialists.title"/></a>

    </div>
    </body>
</html>