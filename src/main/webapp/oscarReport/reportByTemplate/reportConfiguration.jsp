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

<%
    if (session.getValue("user") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>

<%@ page import="java.util.*,io.github.carlos_emr.carlos.report.reportByTemplate.*" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.ReportManager" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.Choice" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.Parameter" %>
<%@ page import="io.github.carlos_emr.carlos.report.reportByTemplate.ReportObject" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<security:oscarSec roleName="<%=roleName$%>"
                   objectName="_admin,_report" rights="r" reverse="<%=true%>">
    <%
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    %>
</security:oscarSec>
<!DOCTYPE html>
<html>
    <head>

        <title>Report by Template</title>

        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css" rel="stylesheet">
        <link href="${pageContext.request.contextPath}/share/calendar/calendar.css" title="win2k-cold-1"
              rel="stylesheet">
        <script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>

        <script src="${pageContext.request.contextPath}/share/calendar/calendar.js"></script>
        <script src="${pageContext.request.contextPath}/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script src="${pageContext.request.contextPath}/share/calendar/calendar-setup.js"></script>
        <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-3.6.4.min.js"></script>
        <script src="${pageContext.servletContext.contextPath}/library/jquery/jquery-compat.js"></script>
        <script src="${pageContext.request.contextPath}/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
        <script>
            function checkform(formobj) {
                if (!validDateFieldsByClass('datefield', formobj)) {
                    alert("Invalid Date: Must be in the format YYYY/MM/DD");
                    return false;
                }
                return true;
            }
        </script>
        <style>
            div#optionsDiv a {
                padding-left: 5px;
                border-left: #0088cc 2px solid;
            }

            div#optionsDiv a:first-of-type {
                border-left: none;
            }

        </style>

        <script>
            function deleteTemplate(templateId) {
                if (confirm('Are you sure you want to delete this report template?')) {
                    var form = document.createElement('form');
                    form.method = 'post';
                    form.action = 'addEditTemplatesAction.do';
                    var fields = {templateid: templateId, action: 'delete'};
                    for (var key in fields) {
                        var input = document.createElement('input');
                        input.type = 'hidden';
                        input.name = key;
                        input.value = fields[key];
                        form.appendChild(input);
                    }
                    document.body.appendChild(form);
                    form.submit();
                }
            }
        </script>

    </head>

    <%
        String templateid = request.getParameter("templateid");
        if (templateid == null) templateid = (String) request.getAttribute("templateid");
        ReportObject curreport = (new ReportManager()).getReportTemplate(templateid);
        ArrayList parameters = curreport.getParameters();
        pageContext.setAttribute("curreport", curreport);
        int step = 0;
    %>

    <body>

        <%@ include file="rbtTopNav.jspf" %>

            <%if (templateid == null) { %>
        <jsp:forward page="homePage.jsp"/>
            <%}%>

    <h3>
        <c:out value="${ curreport.title }"/><br>
        <small><c:out value="${ curreport.description }"/></small>
    </h3>

    <c:if test="${ not empty errormsg }">
    <div class="alert alert-danger">
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        <c:out value="${ errormsg }"/>
    </div>
    </c:if>

    <div class="card card-body bg-body-tertiary configDiv" id=manageGroups>
        <form class="form" action="${pageContext.request.contextPath}/oscarReport/reportByTemplate/GenerateReportAction.do"
                   method="post" onsubmit="return checkform(this);">
            <input type="hidden" name="templateId" value="${ curreport.templateId }">
            <input type="hidden" name="type" value="${ curreport.type }">

            <%
                for (int i = 0; i < parameters.size(); i++) {
                    step++;
                    Parameter curparam = (Parameter) parameters.get(i);
            %>
            <div class="mb-3">
                <label class="form-label" for="<%=curparam.getParamId()%>"><strong>Step <%=step%>
                    : </strong> <%=curparam.getParamDescription()%>
                </label>

                    <%-- If LIST field --%>
                <%if (curparam.getParamType().equals(curparam.LIST)) {%>
                <div>
                    <select name="<%=curparam.getParamId()%>" id="<%=curparam.getParamId()%>">
                        <%
                            ArrayList paramChoices = curparam.getParamChoices();
                            for (int i2 = 0; i2 < paramChoices.size(); i2++) {
                                Choice curchoice = (Choice) paramChoices.get(i2);
                        %>
                        <option value="<%=curchoice.getChoiceId()%>"><%=curchoice.getChoiceText()%>
                        </option>
                        <%}%>
                    </select>
                </div>

                    <%--If TEXT field --%>
                <% } else if (curparam.getParamType().equals(curparam.TEXT)) {%>
                <div>
                    <input type="text" name="<%=curparam.getParamId()%>" id="<%=curparam.getParamId()%>"/>
                </div>

                    <%--If DATE field --%>
                <% } else if (curparam.getParamType().equals(curparam.DATE)) {%>
                <div>
                    <div class="input-group" id="<%=curparam.getParamId()%>">
                        <input type="text" class="datefield" id="datefield<%=i%>" name="<%=curparam.getParamId()%>"/>
                        <span class="input-group-text">
									<a id="obsdate<%=i%>">
										<img title="Calendar" src="${pageContext.request.contextPath}/images/cal.gif"
                                             alt="Calendar">
									</a>
								</span>
                    </div>
                </div>
                <script> Calendar.setup({
                    inputField: "datefield<%=i%>",
                    ifFormat: "%Y-%m-%d",
                    showsTime: false,
                    button: "obsdate<%=i%>",
                    singleClick: true,
                    step: 1
                });
                </script>

                    <%--If CHECK field --%>
                <% } else if (curparam.getParamType().equals(curparam.CHECK)) {%>
                <input type="hidden" name="<%=curparam.getParamId()%>:check" value=""/>
                <div>

                    <input type="checkbox" name="mastercheck" id="mastercheck"
                           onclick="checkAll(this, 'enclosingCol<%=i%>', 'checkclass<%=i%>')"/>

                    <%
                        ArrayList paramChoices = curparam.getParamChoices();
                        for (int i2 = 0; i2 < paramChoices.size(); i2++) {
                            Choice curchoice = (Choice) paramChoices.get(i2);
                    %>
                    <div class="form-check">
                        <input type="checkbox" class="form-check-input checkclass<%=i%>" name="<%=curparam.getParamId()%>"
                               id="<%=curparam.getParamId() + curchoice.getChoiceId()%>"
                               value="<%=curchoice.getChoiceId()%>"/>
                        <label class="form-check-label" for="<%=curparam.getParamId() + curchoice.getChoiceId()%>"><%=curchoice.getChoiceText()%></label>
                    </div>
                    <%}%>
                </div>
                <% } else if (curparam.getParamType().equals(curparam.TEXTLIST)) {%>
                <div>
                    <input type="text" placeholder="Comma Separated" name="<%=curparam.getParamId()%>:list"
                           id="<%=curparam.getParamId()%>"/>
                </div>
                <% }%>

            </div>

            <%} %> <%--end for loop --%>

            <div class="mb-3">
                <label class="form-label"><strong>Step <%=step + 1%>:</strong></label>
                <div>
                    <input type="submit" class="btn btn-primary" name="submitButton" value="Run Query"/>
                </div>
            </div>
        </form>
    </div>

    <div id="optionsDiv" class="form-actions">
        <a href="viewTemplate.jsp?templateid=<%=curreport.getTemplateId()%>" class="link">View Template XML</a>
        <a href="addEditTemplate.jsp?templateid=<%=curreport.getTemplateId()%>&opentext=1" class="link">Edit
            Template</a>
        <a href="javascript:void(0);" onclick="deleteTemplate('<%=curreport.getTemplateId()%>');" class="link">
            Delete Template
        </a>
    </div>

</html>