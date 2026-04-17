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
<html>
    <head>
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
        <%
            if (request.getParameter("update") != null
                    && request.getParameter("update").equals("UpdateResident")) {
                // Validate last_name_from and last_name_to as single letters A-Z (server-side)
                // to prevent ReDoS; the hidden "regexp" field is no longer accepted.
                String lastNameFrom = request.getParameter("last_name_from");
                String lastNameTo = request.getParameter("last_name_to");
                String regexp = null;
                if (lastNameFrom != null && lastNameFrom.matches("[A-Za-z]")
                        && lastNameTo != null && lastNameTo.matches("[A-Za-z]")) {
                    regexp = "^[" + lastNameFrom.toUpperCase() + "-" + lastNameTo.toUpperCase() + "]";
                }
                // find demographicNos for records with last name starting with and have a resident assigned
                List<Integer> noList = null;
                if (regexp != null) {
                    noList = demographicManager.getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(
                            loggedInInfo, request.getParameter("oldcust2"), regexp
                    );
                }
                int rowsAffected = 0;
                if (noList != null) {
                    int nosize = noList.size();
                    if (nosize != 0) {
                        String[] param = new String[nosize + 2];
                        param[0] = request.getParameter("newcust2");
                        param[1] = request.getParameter("oldcust2");
                        param[2] = noList.get(0).toString();
                        if (nosize > 1) {
                            for (int i = 1; i < nosize; i++) {
                                param[i + 2] = noList.get(i).toString();
                            }
                        }
                        List<Integer> demoList = new ArrayList<Integer>();
                        for (int x = 2; x < param.length; x++) {
                            demoList.add(Integer.parseInt(param[x]));
                        }
                        // get demographicExt entries in demo list with old providers
                        List<DemographicExt> residents = demographicManager
                                .getMultipleResidentForDemographicNumbersByProviderNumber(
                                        loggedInInfo, demoList, param[1]
                                );
                        for (DemographicExt resident : residents) {
                            resident.setValue(param[0]);
                            demographicManager.updateExtension(loggedInInfo, resident);
                        }
                        rowsAffected = residents.size();
                    }
                }
        %>
        <%=rowsAffected %>
        <fmt:message key="admin.updatedemographicprovider.msgRecords"/>
        <br>
        <%
            }
            if (request.getParameter("update") != null
                    && request.getParameter("update").equals("UpdateNurse")) {
                // Validate last_name_from and last_name_to as single letters A-Z (server-side)
                String lastNameFromNurse = request.getParameter("last_name_from");
                String lastNameToNurse = request.getParameter("last_name_to");
                String regexpNurse = null;
                if (lastNameFromNurse != null && lastNameFromNurse.matches("[A-Za-z]")
                        && lastNameToNurse != null && lastNameToNurse.matches("[A-Za-z]")) {
                    regexpNurse = "^[" + lastNameFromNurse.toUpperCase() + "-" + lastNameToNurse.toUpperCase() + "]";
                }
                List<Integer> noList = null;
                if (regexpNurse != null) {
                    noList = demographicManager.getDemographicNumbersByNurseNumberAndDemographicLastNameRegex(
                            loggedInInfo,
                            request.getParameter("oldcust1"),
                            regexpNurse
                    );
                }
                int rowsAffected = 0;
                if (noList != null) {
                    int nosize = noList.size();
                    if (nosize != 0) {
                        String[] param = new String[nosize + 2];
                        param[0] = request.getParameter("newcust1");
                        param[1] = request.getParameter("oldcust1");
                        param[2] = noList.get(0).toString();
                        if (nosize > 1) {
                            for (int i = 1; i < nosize; i++) {
                                param[i + 2] = noList.get(i).toString();
                            }
                        }
                        List<Integer> demoList = new ArrayList<Integer>();
                        for (int x = 2; x < param.length; x++) {
                            demoList.add(Integer.parseInt(param[x]));
                        }
                        List<DemographicExt> nurses = demographicManager.
                                getMultipleNurseForDemographicNumbersByProviderNumber(
                                        loggedInInfo, demoList, param[1]
                                );
                        for (DemographicExt nurse : nurses) {
                            nurse.setValue(param[0]);
                            demographicManager.updateExtension(loggedInInfo, nurse);
                        }
                        rowsAffected = nurses.size();
                    }
                }
        %>
        <%=rowsAffected %>
        <fmt:message key="admin.updatedemographicprovider.msgRecords"/>
        <br>
        <%
            }
            if (request.getParameter("update") != null
                    && request.getParameter("update").equals("UpdateMidwife")) {
                // Validate last_name_from and last_name_to as single letters A-Z (server-side)
                String lastNameFromMidwife = request.getParameter("last_name_from");
                String lastNameToMidwife = request.getParameter("last_name_to");
                String regexpMidwife = null;
                if (lastNameFromMidwife != null && lastNameFromMidwife.matches("[A-Za-z]")
                        && lastNameToMidwife != null && lastNameToMidwife.matches("[A-Za-z]")) {
                    regexpMidwife = "^[" + lastNameFromMidwife.toUpperCase() + "-" + lastNameToMidwife.toUpperCase() + "]";
                }
                List<Integer> noList = null;
                if (regexpMidwife != null) {
                    noList = demographicManager.getDemographicNumbersByMidwifeNumberAndDemographicLastNameRegex(
                            loggedInInfo,
                            request.getParameter("oldcust4"),
                            regexpMidwife
                    );
                }
                int rowsAffected = 0;
                if (noList != null) {
                    int nosize = noList.size();
                    if (nosize != 0) {
                        String[] param = new String[nosize + 2];
                        param[0] = request.getParameter("newcust4");
                        param[1] = request.getParameter("oldcust4");
                        param[2] = noList.get(0).toString();

                        if (nosize > 1) {
                            for (int i = 1; i < nosize; i++) {
                                param[i + 2] = noList.get(i).toString();
                            }
                        }

                        List<Integer> demoList = new ArrayList<>();
                        for (int x = 2; x < param.length; x++) {
                            demoList.add(Integer.parseInt(param[x]));
                        }
                        List<DemographicExt> midwives = demographicManager.getMultipleMidwifeForDemographicNumbersByProviderNumber(
                                loggedInInfo,
                                demoList,
                                param[1]
                        );
                        for (DemographicExt midwife : midwives) {
                            midwife.setValue(param[0]);
                            demographicManager.updateExtension(loggedInInfo, midwife);
                        }
                        rowsAffected = midwives.size();
                    }
                }
        %>
        <%= rowsAffected %>
        <fmt:message key="admin.updatedemographicprovider.msgRecords"/>
        <br>
        <%
            }
            if (request.getParameter("update") != null
                    && request.getParameter("update").equals("UpdateMrp")) {
                // Validate last_name_from and last_name_to as single letters A-Z (server-side)
                // to prevent ReDoS: the hidden "regexp" field is no longer accepted.
                String lastNameFromMrp = request.getParameter("last_name_from");
                String lastNameToMrp = request.getParameter("last_name_to");
                String regexpMrp = null;
                if (lastNameFromMrp != null && lastNameFromMrp.matches("[A-Za-z]")
                        && lastNameToMrp != null && lastNameToMrp.matches("[A-Za-z]")) {
                    regexpMrp = "^[" + lastNameFromMrp.toUpperCase() + "-" + lastNameToMrp.toUpperCase() + "]";
                }
                Provider provider = providerManager.getProvider(loggedInInfo, request.getParameter("oldcust5"));
                List<Demographic> noList = null;
                if (regexpMrp != null) {
                    noList = demographicManager.getDemographicsNameRangeByProvider(loggedInInfo, provider, regexpMrp);
                }

                int rowsAffected = 0;
                if (noList != null) {
                    String newmrp = request.getParameter("newcust5");
                    if (newmrp != null) {
                        for (Demographic demographic : noList) {
                            demographic.setProviderNo(newmrp);
                            demographicManager.updateDemographic(loggedInInfo, demographic);
                            rowsAffected++;
                        }
                    }
                }
        %>
        <%= rowsAffected %>
        <fmt:message key="admin.updatedemographicprovider.msgRecords"/>
        <br>
        <% } %>

        <!-- for MRP -->
        <div class="card card-body bg-body-tertiary">
            <table class="table table-striped  table-sm">
                <FORM NAME="ADDMRP" METHOD="post"
                      ACTION="updatedemographicprovider.jsp">
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
                                <option value="<e:forHtmlAttribute value='<%= names.get(i) %>' />">
                                    <e:forHtmlContent value='<%= names.get(i + 1) %>' />
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust5">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<e:forHtmlAttribute value='<%= names.get(i) %>' />">
                                    <e:forHtmlContent value='<%= names.get(i + 1) %>' />
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
                      ACTION="updatedemographicprovider.jsp">
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
                                <option value="<e:forHtmlAttribute value='<%= names.get(i) %>' />">
                                    <e:forHtmlContent value='<%= names.get(i + 1) %>' />
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust1">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<e:forHtmlAttribute value='<%= names.get(i) %>' />">
                                    <e:forHtmlContent value='<%= names.get(i + 1) %>' />
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
                      ACTION="updatedemographicprovider.jsp">
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
                                <option value="<e:forHtmlAttribute value='<%= names.get(i) %>' />">
                                    <e:forHtmlContent value='<%= names.get(i + 1) %>' />
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust4">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<e:forHtmlAttribute value='<%= names.get(i) %>' />">
                                    <e:forHtmlContent value='<%= names.get(i + 1) %>' />
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
                      ACTION="updatedemographicprovider.jsp">
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
                                <option value="<e:forHtmlAttribute value='<%= names.get(i) %>' />">
                                    <e:forHtmlContent value='<%= names.get(i + 1) %>' />
                                </option>
                                <% } %>
                            </select>
                            <fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust2">
                                <option value="">
                                    <fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<e:forHtmlAttribute value='<%= names.get(i) %>' />">
                                    <e:forHtmlContent value='<%= names.get(i + 1) %>' />
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
