<!DOCTYPE html>
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
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicExt" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.managers.ProviderManager2" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ =
            session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r"
                   reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%!
    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    ProviderManager2 providerManager = SpringUtils.getBean(ProviderManager2.class);
%>
<%
    if (!authed) {
        return;
    }
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    List<String> names = new ArrayList<>();
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <title><fmt:message key="admin.admin.btnUpdatePatientProvider"/></title>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    </head>

    <%
        for (Provider p : providerManager.getProviders(loggedInInfo, true)) {
            names.add(p.getProviderNo());
            names.add(p.getFormattedName());
        }
    %>

    <body>
    <div class="container-fluid">
        <h3><fmt:message key="admin.admin.btnUpdatePatientProvider"/></h3>
        <c:if test="${not empty reassignedProviderCount}">
            <c:out value="${reassignedProviderCount}"/>
            <fmt:message key="admin.updatedemographicprovider.msgRecords"/><br>
        </c:if>

        <!-- for MRP -->
        <div class="card card-body bg-body-tertiary">
            <table class="table table-striped  table-sm">
                <FORM NAME="ADDMRP" METHOD="post"
                      ACTION="<%= request.getContextPath() %>/admin/UpdateDemographicProvider">
                    <tr>
                        <td>
                            <b><fmt:message key="admin.updatedemographicprovider.msgMrp"/></b>
                        </td>
                    </tr>
                    <tr>
                        <td><fmt:message key="admin.updatedemographicprovider.formReplace"/>
                            <select name="oldcust5">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<carlos:encode value='<%= names.get(i) %>' context="htmlAttribute"/>">
                                    <carlos:encode value='<%= names.get(i + 1) %>' context="html"/>
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust5">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<carlos:encode value='<%= names.get(i) %>' context="htmlAttribute"/>">
                                    <carlos:encode value='<%= names.get(i + 1) %>' context="html"/>
                                </option>
                                <% } %>
                            </select><br>
                            <fmt:message key="admin.updatedemographicprovider.formCondition"/>
                            <select name="last_name_from">
                                <%
                                    char cletter = 'A';
                                    for (int i = 0; i < 26; i++) {
                                %>
                                <option value="<%= (char) (cletter + i) %>">
                                    <%= (char) (cletter + i) %>
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formTo"/>
                            <select name="last_name_to">
                                <%
                                    cletter = 'A';
                                    for (int i = 0; i < 26; i++) {
                                %>
                                <option value="<%= (char) (cletter + i) %>">
                                    <%= (char) (cletter + i) %>
                                </option>
                                <% } %>
                            </select> <br>
                            <input type="hidden" name="update" value="UpdateMrp">
                            <input class="btn btn-primary" type="submit" value="<fmt:message key="global.update"/>">
                        </td>
                    </tr>
                </form>
            </table>
        </div>

        <!-- for nurse -->
        <div class="card card-body bg-body-tertiary">
            <table class="table table-striped  table-sm">
                <FORM NAME="ADDAPPT1" METHOD="post"
                      ACTION="<%= request.getContextPath() %>/admin/UpdateDemographicProvider">
                    <tr>
                        <td>
                            <b><fmt:message key="admin.updatedemographicprovider.msgNurse"/></b>
                        </td>
                    </tr>
                    <tr>
                        <td><fmt:message key="admin.updatedemographicprovider.formReplace"/>
                            <select name="oldcust1">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<carlos:encode value='<%= names.get(i) %>' context="htmlAttribute"/>">
                                    <carlos:encode value='<%= names.get(i + 1) %>' context="html"/>
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust1">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<carlos:encode value='<%= names.get(i) %>' context="htmlAttribute"/>">
                                    <carlos:encode value='<%= names.get(i + 1) %>' context="html"/>
                                </option>
                                <% } %>
                            </select><br>
                            <fmt:message key="admin.updatedemographicprovider.formCondition"/>
                            <select name="last_name_from">
                                <%
                                    cletter = 'A';
                                    for (int i = 0; i < 26; i++) {
                                %>
                                <option value="<%= (char) (cletter + i) %>">
                                    <%= (char) (cletter + i) %>
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formTo"/>
                            <select name="last_name_to">
                                <%
                                    cletter = 'A';
                                    for (int i = 0; i < 26; i++) {
                                %>
                                <option value="<%= (char) (cletter + i) %>">
                                    <%= (char) (cletter + i) %>
                                </option>
                                <% } %>
                            </select> <br>
                            <input type="hidden" name="update" value="UpdateNurse">
                            <INPUT class="btn btn-primary" type="submit"
                                   value="<fmt:message key="global.update"/>">
                        </td>
                    </tr>
                </form>
            </table>
        </div>

        <!-- for midwife -->
        <div class="card card-body bg-body-tertiary">
            <table class="table table-striped  table-sm">
                <FORM NAME="ADDAPPT2" METHOD="post"
                      ACTION="<%= request.getContextPath() %>/admin/UpdateDemographicProvider">
                    <tr>
                        <td><b><fmt:message key="admin.updatedemographicprovider.msgMidwife"/></b></td>
                    </tr>
                    <tr>
                        <td>
                            <fmt:message key="admin.updatedemographicprovider.formReplace"/>
                            <select name="oldcust4">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <%
                                    for (int i = 0; i < names.size(); i = i + 2) {
                                %>
                                <option value="<carlos:encode value='<%= names.get(i) %>' context="htmlAttribute"/>">
                                    <carlos:encode value='<%= names.get(i + 1) %>' context="html"/>
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust4">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<carlos:encode value='<%= names.get(i) %>' context="htmlAttribute"/>">
                                    <carlos:encode value='<%= names.get(i + 1) %>' context="html"/>
                                </option>
                                <% } %>
                            </select><br>
                            <fmt:message key="admin.updatedemographicprovider.formCondition"/>
                            <select name="last_name_from">
                                <%
                                    cletter = 'A';
                                    for (int i = 0; i < 26; i++) {
                                %>
                                <option value="<%=(char) (cletter+i) %>">
                                    <%= (char) (cletter + i) %>
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formTo"/>
                            <select
                                    name="last_name_to">
                                <%
                                    cletter = 'A';
                                    for (int i = 0; i < 26; i++) {
                                %>
                                <option value="<%=(char) (cletter+i) %>">
                                    <%= (char) (cletter + i) %>
                                </option>
                                <% } %>
                            </select> <br>
                            <input type="hidden" name="update" value="UpdateMidwife">
                            <input class="btn btn-primary" type="submit"
                                   value="<fmt:message key="global.update"/>">
                        </td>
                    </tr>
                </form>
            </table>
        </div>

        <!--  for resident -->
        <div class="card card-body bg-body-tertiary">
            <table class="table table-striped  table-sm">
                <FORM NAME="ADDAPPT" METHOD="post"
                      ACTION="<%= request.getContextPath() %>/admin/UpdateDemographicProvider">
                    <tr>
                        <td><b><fmt:message key="admin.updatedemographicprovider.msgResident"/></b></td>
                    </tr>
                    <tr>
                        <td><fmt:message key="admin.updatedemographicprovider.formReplace"/>
                            <select name="oldcust2">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<carlos:encode value='<%= names.get(i) %>' context="htmlAttribute"/>">
                                    <carlos:encode value='<%= names.get(i + 1) %>' context="html"/>
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust2">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<carlos:encode value='<%= names.get(i) %>' context="htmlAttribute"/>">
                                    <carlos:encode value='<%= names.get(i + 1) %>' context="html"/>
                                </option>
                                <% } %>
                            </select><br>
                            <fmt:message key="admin.updatedemographicprovider.formCondition"/>
                            <select name="last_name_from">
                                <%
                                    cletter = 'A';
                                    for (int i = 0; i < 26; i++) {
                                %>
                                <option value="<%= (char) (cletter+i) %>">
                                    <%= (char) (cletter + i) %>
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formTo"/>
                            <select name="last_name_to">
                                <%
                                    cletter = 'A';
                                    for (int i = 0; i < 26; i++) {
                                %>
                                <option value="<%=(char) (cletter+i) %>">
                                    <%= (char) (cletter + i) %>
                                </option>
                                <% } %>
                            </select> <br>
                            <input
                                    type="hidden" name="update" value="UpdateResident"> <INPUT
                                    class="btn btn-primary"
                                    TYPE="submit"
                                    VALUE="<fmt:message key="global.update"/>">
                        </td>
                    </tr>
                </form>
            </table>
        </div>
    </div>
    </body>
</html>
