<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="java.util.*, ca.openosp.openo.hospitalReportManager.*, ca.openosp.openo.hospitalReportManager.model.HRMCategory, ca.openosp.openo.hospitalReportManager.dao.HRMCategoryDao, ca.openosp.openo.utility.SpringUtils" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<!DOCTYPE html >
<html>
    <head>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Show Mappings</title>
        <link rel="stylesheet" type="text/css"
              href="${ pageContext.request.contextPath }/library/bootstrap/3.0.0/css/bootstrap.min.css">
    </head>

    <body onunload="updateAjax()">
    <div class="table-responsive">

        <div class="col-sm-12">
            <h4>Add Mapping</h4>
            <p class="pull-right">
                <a href="javascript:popupStart(300,400,'Help.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.help"/></a> |
                <a href="javascript:popupStart(300,400,'About.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a> |
                <a href="javascript:popupStart(300,400,'License.jsp')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.license"/></a>
            </p>
            <form method="post" action="<%=request.getContextPath() %>/hospitalReportManager/Mapping.do">
                <fieldset>
                    <div class="control-group">
                        <label class="control-label">Report class:</label>
                        <div class="controls">
                            <select name="class">
                                <option value="Medical Records Report">Medical Records Report</option>
                                <option value="Diagnostic Imaging Report">Diagnostic Imaging Report</option>
                                <option value="Cardio Respiratory Report">Cardio Respiratory Report</option>
                            </select>
                        </div>
                    </div>
                    <div class="control-group">
                        <label class="control-label">Sub-class:</label>
                        <div class="controls">
                            <input type="text" class="form-control input-normal" name="subclass"/>
                        </div>
                    </div>
                    <div class="control-group">
                        <label class="control-label">Sub-class mmenoic:</label>
                        <div class="controls">
                            <input type="text" class="form-control input-normal" name="mnemonic"/>
                        </div>
                    </div>
                    <div class="control-group">
                        <label class="control-label">Sub-class description:</label>
                        <div class="controls">
                            <input type="text" class="form-control input-normal" name="description"/>
                        </div>
                    </div>
                    <div class="control-group">
                        <label class="control-label">Sending Facility ID (* for all):</label>
                        <div class="controls">
                            <input type="text" class="form-control input-normal" name="sendingFacilityId" value="*"/>
                        </div>
                    </div>
                    <div class="control-group">
                        <label class="control-label">Category:</label>
                        <div class="controls">
                            <select name="category">
                                <%
                                    HRMCategoryDao categoryDao = (HRMCategoryDao) SpringUtils.getBean(HRMCategoryDao.class);
                                    List<HRMCategory> categoryList = categoryDao.findAll();
                                    for (HRMCategory category : categoryList) {
                                %>
                                <option value="<%=category.getId() %>"><%=category.getCategoryName() %>
                                </option>
                                <%
                                    }
                                %>
                            </select>
                        </div>
                    </div>
                    <div class="control-group">
                        <input type="submit" class="btn btn-primary" name="submit" value="Save"/>
                    </div>

                </fieldset>
            </form>
        </div>
    </div>
    </body>
</html>