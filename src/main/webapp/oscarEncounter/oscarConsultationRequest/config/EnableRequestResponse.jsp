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

<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.PropertyDao, io.github.carlos_emr.carlos.commn.model.Property" %>
<%@ page import="io.github.carlos_emr.carlos.managers.ConsultationManager" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.config.pageUtil.EctConTitlebar" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%
    PropertyDao dao = (PropertyDao) SpringUtils.getBean(PropertyDao.class);
    ConsultationManager manager = (ConsultationManager) SpringUtils.getBean(ConsultationManager.class);

    boolean consultRequestEnabled = false;
    boolean consultResponseEnabled = false;

    List<Property> results = dao.findByName(manager.CON_REQUEST_ENABLED);
    if (results.size() > 0 && manager.ENABLED_YES.equals(results.get(0).getValue())) consultRequestEnabled = true;
    results = dao.findByName(manager.CON_RESPONSE_ENABLED);
    if (results.size() > 0 && manager.ENABLED_YES.equals(results.get(0).getValue())) consultResponseEnabled = true;

    if (!consultRequestEnabled && !consultResponseEnabled) consultRequestEnabled = true;
%>
<!DOCTYPE html>
<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.title"/></title>
    </head>

    <body>
    <div class="container-fluid">
        <div class="page-header-bar">
            <h5 class="page-header-title">
                <fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.title"/>
            </h5>
        </div>

        <div class="row">
            <div class="col-md-3 consult-sidebar">
                <%
                    EctConTitlebar titlebar = new EctConTitlebar(request);
                    out.print(titlebar.estBar(request));
                %>
            </div>

            <div class="col-md-9">
                <%
                    String updated = (String) request.getAttribute("ENABLE_REQUEST_RESPONSE_UPDATED");
                    if (updated != null) {
                %>
                <div class="alert alert-success">
                    <fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.msgUpdated"/>
                </div>
                <% } %>

                <form action="${pageContext.request.contextPath}/oscarEncounter/EnableConRequestResponse.do" method="post">
                    <div class="mb-3">
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox"
                                   name="consultRequestEnabled" value="true" id="reqEnabled" <%=consultRequestEnabled ? "checked" : "" %>/>
                            <label class="form-check-label" for="reqEnabled">
                                <fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.enableRequest"/>
                            </label>
                        </div>
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox"
                                   name="consultResponseEnabled" value="true" id="respEnabled" <%=consultResponseEnabled ? "checked" : "" %>/>
                            <label class="form-check-label" for="respEnabled">
                                <fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.enableResponse"/>
                            </label>
                        </div>
                    </div>
                    <input type="submit" class="btn btn-primary"
                           value="<fmt:message key="oscarEncounter.oscarConsultationRequest.config.EnableRequestResponse.btnUpdate"/>"/>
                </form>
            </div>
        </div>
    </div>
    </body>
</html>
