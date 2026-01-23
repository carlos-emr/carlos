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

--%>

<%--
  Purpose: Display all consultation requests for a specific demographic/patient in a sortable, filterable table.

  Features:
    - Lists consultation requests with status, urgency, MRP, provider, service, specialist, and referral date
    - Implements Bootstrap DataTables for sorting, filtering, and pagination
    - Opens consultation request details in popup window
    - Allows creation of new consultation requests via popup form
    - Uses Spring-managed DemographicManager for demographic data retrieval
    - Encodes output to prevent XSS vulnerabilities

  Parameters:
    - de (required): Demographic number identifying the patient

  Session Attributes:
    - user: Current provider number
    - userrole: User's role for security authorization

  Security:
    - Requires _con read rights
    - Redirects to securityError.jsp if unauthorized

  Technologies:
    - Bootstrap 3.0.0 for responsive UI
    - DataTables 1.13.4 for enhanced table functionality
    - JSTL fmt tags for internationalization
    - OWASP Encoder for XSS prevention

  @since 2002-11-08
--%>

<!DOCTYPE html>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.encounter.data.*"%>
<%@ page import="ca.openosp.openo.encounter.pageUtil.*"%>
<%@ page import="ca.openosp.openo.providers.data.ProviderData" %>
<%@ page import="ca.openosp.openo.commn.dao.ConsultationRequestExtDao" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>
<%@page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil" %>
<%@ page import="ca.openosp.openo.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%
    String demo = request.getParameter("de");
    String proNo = (String) session.getAttribute("user");
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic demographic = null;

    ProviderData pdata = new ProviderData(proNo);
    String team = pdata.getTeam();

    if (demo != null) {
        demographic = demographicManager.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demo);
    } else
        request.getRequestDispatcher("/errorpage.jsp").forward(request, response);

    EctConsultationFormRequestUtil consultUtil;
    consultUtil = new EctConsultationFormRequestUtil();
    consultUtil.estPatient(LoggedInInfo.getLoggedInInfoFromSession(request), demo);

    EctViewConsultationRequestsUtil theRequests;
    theRequests = new EctViewConsultationRequestsUtil();
    theRequests.estConsultationVecByDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demo);
%>

<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.title"/>
    </title>
    <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">

    <!--META HTTP-EQUIV="Refresh" CONTENT="20;"-->

    <!-- jquery -->
    <script src="${pageContext.request.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/bootstrap/3.0.0/js/bootstrap.min.js"></script>
    <script src="${pageContext.request.contextPath}/library/DataTables/datatables.min.js"></script><!-- 1.13.4 -->

    <!-- css -->
    <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/css/bootstrap.min.css" rel="stylesheet"><!-- Bootstrap 2.3.1 -->
    <link href="${pageContext.request.contextPath}/library/bootstrap/3.0.0/assets/css/DT_bootstrap.css" rel="stylesheet">
    <link href="${pageContext.request.contextPath}/library/DataTables-1.10.12/media/css/jquery.dataTables.min.css" rel="stylesheet">

    <style>
      .MainTable .dataTables_length label {
        display: flex;
        align-items: center;
        gap: 5px;
      }
      .MainTable .dataTables_length select {
        display: inline-block;
        width: auto;
      }
  
      .MainTable .dataTables_filter label {
        display: flex;
        align-items: center;
        gap: 5px;
      }
      .MainTable .dataTables_filter input {
        display: inline-block;
        width: auto;
      }
  
      .MainTable tbody > tr > td {
        border-top: none;
      }
  
      tr.MainTableTopRow  td {
        padding: 0 8px 0 8px !important;
      }
    </style>
    <script language="javascript">
      jQuery(document).ready( function () {
        jQuery('#consultTable').DataTable({
          "lengthMenu": [ [25, 50, 100, -1], [25, 50, 100, "<fmt:message key="oscarEncounter.LeftNavBar.AllLabs"/>"] ],
          "order": [[7, 'desc']], // Column 7 = Referral Date
          "language": {
            "url": "<%=request.getContextPath() %>/library/DataTables/i18n/<fmt:message key="global.i18nLanguagecode"/>.json"
          },
          "initComplete": function () {
            // Add Bootstrap classes to dropdown and search input
            jQuery('.dataTables_length select').addClass('form-control input');
            jQuery('.dataTables_filter input').addClass('form-control input');
          }
        });
      });
      
      function BackToOscar() {
          window.close();
      }

      function popupOscarRx(vheight, vwidth, varpage) { //open a new popup window
          var page = varpage;
          windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
          var popup = window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgConsReq"/>", windowprops);
      }

      function popupOscarConS(vheight, vwidth, varpage) { //open a new popup window
          var page = varpage;
          windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
          var popup = window.open(varpage, "<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultChoice.oscarConS"/>", windowprops);
          window.close();
      }
    </script>

    </head>
    <body onload="window.focus()">

    <table class="MainTable table" id="scrollNumber1">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn" style="text-align: left;"><h4><fmt:message key="global.con"/></h4></td>
            <td class="MainTableTopRowRightColumn">
                <table class="table">
                    <tr>
                        <td class="Header" style="white-space:nowrap"><h4><fmt:setBundle basename="oscarResources"/>
                            <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgConsReqFor"/>
                          <%= Encode.forHtml(demographic.getLastName()) %>, <%= Encode.forHtml(demographic.getFirstName()) %> <%= Encode.forHtml(demographic.getSex()) %>
                          <%= Encode.forHtml(demographic.getAge()) %></h4></td>
                        <td></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr style="vertical-align: top">
            <td class="MainTableLeftColumn">
                <table class="table">
                    <tr>
                        <td style="white-space:nowrap; padding-left: 0"><a
                                href="javascript:popupOscarRx(700,960,'${pageContext.request.contextPath}/oscarEncounter/oscarConsultationRequest/ConsultationFormRequest.jsp?de=<%=Encode.forUriComponent(demo)%>&teamVar=<%=Encode.forUriComponent(team)%>')">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultChoice.btnNewCon"/></a>
                        </td>
                    </tr>
                </table>
            </td>
            <td class="MainTableRightColumn">
                <table class="table">
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgClickLink"/>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <table id="consultTable" class="table">
                                <thead>
                                  <tr>
                                      <th>
                                          <fmt:setBundle basename="oscarResources"/>
                                          <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgStatus"/>
                                      </th>
                                      <th>
                                          <fmt:setBundle basename="oscarResources"/>
                                          <fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.formUrgency" />
						              </th>
                                      <th>
                                          <fmt:setBundle basename="oscarResources"/>
                                          <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgPat"/>
                                      </th>
                                      <th>
                                          <fmt:setBundle basename="oscarResources"/>
                                          <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgMRP"/>
                                      </th>
                                      <th>
                                          <fmt:setBundle basename="oscarResources"/>  
                                          <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgProvider"/>
                                      </th>
                                      <th>
                                          <fmt:setBundle basename="oscarResources"/>
                                          <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgService"/>
                                      </th>
                                      <th >
                                          <fmt:setBundle basename="oscarResources"/>
                                          <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgSpecialist" />
						              </th>
                                      <th>
                                          <fmt:setBundle basename="oscarResources"/>
                                          <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgRefDate"/>
                                      </th>
                                  </tr>
                                </thead>
                                <tbody>
                                    <%
                                        for (int i = 0; i < theRequests.ids.size(); i++) {
                                            String id       = (String) theRequests.ids.get(i);
                                            String status   = (String) theRequests.status.get(i);
                                            String patient  = (String) theRequests.patient.get(i);
                                            String provider = (String) theRequests.provider.get(i);
                                            String service  = (String) theRequests.service.get(i);
                                            String specialist = (String) theRequests.vSpecialist.get(i);
                                            String date     = (String) theRequests.date.get(i);
                                            String urgency  = (String) theRequests.urgency.get(i);
                                            Provider cProv  = (Provider) theRequests.consultProvider.get(i);
                                    %>
                                    <tr>
                                        <td class="stat<%=Encode.forHtmlAttribute(status)%>">
                                            <% if ("1".equals(status)) { %>
                                                <fmt:setBundle basename="oscarResources"/>
                                                <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgNothingDone"/>
                                            <% } else if ("2".equals(status)) { %>
                                                <fmt:setBundle basename="oscarResources"/>
                                                <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgSpecialistCall"/>
                                            <% } else if ("3".equals(status)) { %>
                                                <fmt:setBundle basename="oscarResources"/>
                                                <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgPatCall"/>
                                            <% } else if ("4".equals(status)) { %>
                                                <fmt:setBundle basename="oscarResources"/>
                                                <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgAppMade"/>
                                            <% } else if ("5".equals(status)) { %>
                                                <fmt:setBundle basename="oscarResources"/>
                                                <fmt:message key="oscarEncounter.oscarConsultationRequest.DisplayDemographicConsultationRequests.msgBookCon"/>
                                            <% } %>
                                            </td>
                                            <td class="stat<%=Encode.forHtmlAttribute(status)%>" >
                                            <% if (urgency.equals("1")){ %> 
                                                <fmt:setBundle basename="oscarResources"/>
                                                <fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgUrgent" />
                                            <% }else if(urgency.equals("2")) { %> 
                                                <fmt:setBundle basename="oscarResources"/>
                                                <fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgNUrgent" />
                                            <% }else if(urgency.equals("3")) { %> 
                                                <fmt:setBundle basename="oscarResources"/>
                                                <fmt:message key="oscarEncounter.oscarConsultationRequest.ConsultationFormRequest.msgReturn" />
                                            <% } %>
                                        </td>
                                        <td class="stat<%=Encode.forHtmlAttribute(status)%>"><a
                  href="javascript:popupOscarRx(700,960,'<%=request.getContextPath()%>/oscarEncounter/ViewRequest.do?de=<%=Encode.forUriComponent(demo)%>&requestId=<%=Encode.forUriComponent(id)%>')">
                <%=Encode.forHtml(patient)%> </a></td>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>"><%=Encode.forHtml(provider)%></td>
                <td class="stat<%=Encode.forHtmlAttribute(status)%>"><%= (cProv != null) ? Encode.forHtml(cProv.getFormattedName()) : "" %></td>
                                        <td class="stat<%=Encode.forHtmlAttribute(status)%>">
                                            <a href="javascript:popupOscarRx(700,960,'<%= request.getContextPath() %>/oscarEncounter/ViewRequest.do?de=<%=Encode.forUriComponent(demo)%>&requestId=<%=Encode.forUriComponent(id)%>')">
                                                <%=Encode.forHtml(StringUtils.trimToEmpty(service))%>
                                            </a>
                                        </td>
                                        <td class="stat<%= Encode.forHtmlAttribute(status) %>">
                                            <%= Encode.forHtml(StringUtils.trimToEmpty(specialist)) %>
                                        </td>
                                        <td class="stat<%=Encode.forHtmlAttribute(status)%>"><%=Encode.forHtml(date)%>
                                        </td>
                                    </tr>
                                    <%}%>
                                </tbody>
                            </table>
                        </td>
                    </tr>

                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn"></td>
        </tr>
    </table>
    </body>
</html>
