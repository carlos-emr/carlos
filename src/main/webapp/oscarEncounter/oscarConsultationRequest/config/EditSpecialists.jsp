<%@ page import="org.owasp.encoder.Encode" %><%--

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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ page import="java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.consult" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.consult");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE html>
<html>
    <jsp:useBean id="displayServiceUtil" scope="request"
                 class="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConDisplayServiceUtil"/>
    <%
        displayServiceUtil.estSpecialistVector();
    %>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.title"/></title>
    </head>

    <body>
    <div class="container-fluid">
        <div class="page-header-bar">
            <h5 class="page-header-title">
                <fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.title"/>
            </h5>
        </div>

<%
    java.util.List<String> actionErrors = (java.util.List<String>) request.getAttribute("actionErrors");
    if (actionErrors != null && !actionErrors.isEmpty()) {
%>
        <div class="action-errors">
            <ul>
                <% for (String error : actionErrors) { %>
                    <li><%= Encode.forHtml(error) %></li>
                <% } %>
            </ul>
        </div>
<% } %>

        <div class="row">
            <div class="col-md-3 consult-sidebar">
                <%
                    EctConTitlebar titlebar = new EctConTitlebar(request);
                    out.print(titlebar.estBar(request));
                %>
            </div>

            <div class="col-md-9">
                <p><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.msgClickOn"/></p>

                <form action="${pageContext.request.contextPath}/oscarEncounter/EditSpecialists.do" method="post">
                    <input type="submit" class="btn btn-danger mb-3" name="delete"
                           value="<fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.btnDeleteSpecialist"/>"
                           onclick="return confirm('Are you sure you want to delete the selected specialists?');"/>
                    <table class="table table-sm table-hover table-bordered">
                        <thead>
                            <tr>
                                <th class="col-checkbox">&nbsp;</th>
                                <th><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.specialist"/></th>
                                <th><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.address"/></th>
                                <th><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.phone"/></th>
                                <th><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EditSpecialists.fax"/></th>
                            </tr>
                        </thead>
                        <tbody>
                            <%
                                for (int i = 0; i < displayServiceUtil.specIdVec.size(); i++) {
                                    String specId = displayServiceUtil.specIdVec.elementAt(i);
                                    String fName = displayServiceUtil.fNameVec.elementAt(i);
                                    String lName = displayServiceUtil.lNameVec.elementAt(i);
                                    String proLetters = displayServiceUtil.proLettersVec.elementAt(i);
                                    String address = displayServiceUtil.addressVec.elementAt(i);
                                    String phone = displayServiceUtil.phoneVec.elementAt(i);
                                    String fax = displayServiceUtil.faxVec.elementAt(i);
                                    String contextPath = request.getContextPath();
                                    String url = contextPath + "/oscarEncounter/EditSpecialists.do?specId=" + specId;
                            %>
                            <tr>
                                <td><input type="checkbox" name="specialists" value="<%=specId%>"></td>
                                <td><a href="<%= url %>"><%= Encode.forHtmlContent(lName + " " + fName + " " + (proLetters == null ? "" : proLetters)) %></a></td>
                                <td><%= Encode.forHtmlContent(address) %></td>
                                <td><%= Encode.forHtmlContent(phone) %></td>
                                <td><%= Encode.forHtmlContent(fax) %></td>
                            </tr>
                            <% } %>
                        </tbody>
                    </table>
                </form>
            </div>
        </div>
    </div>
    </body>
</html>
