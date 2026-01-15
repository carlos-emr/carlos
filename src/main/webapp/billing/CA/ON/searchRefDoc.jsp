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

--%>

<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.model.ProfessionalSpecialist" %>
<%@page import="ca.openosp.openo.commn.dao.ProfessionalSpecialistDao" %>
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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<!DOCTYPE html>
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.optChooseSpec"/></title>
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet"> <!-- Bootstrap 2.3.1 -->
        <link href="${pageContext.request.contextPath}/css/DT_bootstrap.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css"
              rel="stylesheet">
        <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script>
        <!-- DataTables 1.13.4 -->

        <script>
            <%if(param.length()>0) {%>

            function typeInData1(data) {
                self.close();
                opener.<%=param%> = data;
            }

            <%if(param2.length()>0) {%>

            function typeInData2(data1, data2) {
                opener.<%=param%> = data1;
                opener.<%=param2%> = data2;
                self.close();
            }

            <%}}%>

            function typeInData3(billno, toname, toaddress, tophone, tofax) {
                self.close();
                <%if( param.length() > 0 ) {%>
                opener.<%=param%> = billno;
                <%}
                  if( toname.length() > 0 ) {%>
                opener.<%=toname%> = toname;
                <%}
                  if( toaddress1.length() > 0 ) {%>
                opener.<%=toaddress1%> = toaddress;
                <%}
                  if( tophone.length() > 0 ) {%>
                opener.<%=tophone%> = tophone;
                <%}
                  if( tofax.length() > 0 ) {%>
                opener.<%=tofax%> = tofax;
                <%}%>
            }
        </script>
        <script>
            jQuery(document).ready(function () {
                jQuery('#tblDocs').DataTable({
                    "lengthMenu": [[10, 25, 50, -1], [10, 25, 50, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.LeftNavBar.AllLabs"/>"]],
                    "order": [],
                    "language": {
                        "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.i18nLanguagecode"/>.json"
                    }
                });
            });
        </script>
    </head>
    <body>
    <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.optChooseSpec"/></h3>&nbsp;<%=keyword == null ? "" : keyword %>&nbsp;<input
            type="button" class="btn-link" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="report.reportindex.formAllProviders"/>"
            onclick="location = location.href.replace(/(\?|\&)(keyword)([^&]*)/, '').replace(/(\?|\&)(submit)([^&]*)/, '');">
    <div class="container-fluid">
        <table style="width:100%" id="tblDocs" class="table table-condensed">
            <thead>
            <tr class="title">
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.referralNo"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.lastName"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.firstName"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.specialistType"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.phone"/></th>
                <th><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.AddSpecialist.address"/></th>
            </tr>
            </thead>
            <tbody>
            <%
                for (int i = 0; i < alist.size(); i++) {
                    prop = (Properties) alist.get(i);
                    String bgColor = i % 2 == 0 ? "#f9f9f9" : "#ffffff";
                    String strOnClick;
                    if (param2.length() <= 0) {
                        strOnClick = "typeInData3('" + Encode.forJavaScript(prop.getProperty("referral_no", "")) + "', '" + Encode.forJavaScript(prop.getProperty("to_name", "")) + "', '" + Encode.forJavaScript(prop.getProperty("to_address", "")) + "', '" + Encode.forJavaScript(prop.getProperty("phone", "")) + "', '" + Encode.forJavaScript(prop.getProperty("to_fax", "")) + "')";
                    } else {
                        strOnClick = param2.length() > 0 ? "typeInData2('" + Encode.forJavaScript(prop.getProperty("referral_no", "")) + "','" + Encode.forJavaScript(prop.getProperty("last_name", "") + "," + prop.getProperty("first_name", "")) + "')"
                                : "typeInData1('" + prop.getProperty("referral_no", "") + "')";
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
                <td title="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.fax"/> <%=Encode.forHtml(prop.getProperty("to_fax", ""))%>"><%=Encode.forHtml(prop.getProperty("phone", ""))%>
                </td>
                <td style="max-width: 200px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;"
                    title="<%=Encode.forHtml(prop.getProperty("to_address", ""))%>"><%=Encode.forHtml(prop.getProperty("to_address", ""))%>
                </td>
            </tr>
            <% } %>
            </tbody>
        </table>
        <br>
        <a class="btn"
           href="${pageContext.request.contextPath}/oscarEncounter/oscarConsultationRequest/config/EditSpecialists.jsp"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.title"/></a>

    </div>
    </body>
</html>