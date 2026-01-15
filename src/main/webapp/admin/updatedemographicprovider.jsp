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

--%>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="ca.openosp.openo.commn.model.DemographicExt" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="ca.openosp.openo.managers.DemographicManager" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.managers.ProviderManager2" %>
<%@ page import="ca.openosp.openo.commn.model.Demographic" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ =
            session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r"
                   reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.misc");%>
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
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnUpdatePatientProvider"/></title>

        <link href="<%=request.getContextPath() %>/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet">
    </head>
    <script type="text/javascript">
        function setregexp() {
            document.ADDAPPT.regexp.value = "^[" + document.ADDAPPT.last_name_from.value + "-"
                + document.ADDAPPT.last_name_to.value + "]";
        }

        function setregexp1() {
            document.ADDAPPT1.regexp.value = "^[" + document.ADDAPPT1.last_name_from.value + "-"
                + document.ADDAPPT1.last_name_to.value + "]";
        }

        function setregexp2() {
            document.ADDAPPT2.regexp.value = "^[" + document.ADDAPPT2.last_name_from.value + "-"
                + document.ADDAPPT2.last_name_to.value + "]";
        }

        function setregexp3() {
            document.ADDMRP.regexp.value = "^[" + document.ADDMRP.last_name_from.value + "-"
                + document.ADDMRP.last_name_to.value + "]";
        }
    </script>
    <%
        for (Provider p : providerManager.getProviders(loggedInInfo, true)) {
            names.add(p.getProviderNo());
            names.add(p.getFormattedName());
        }
    %>

    <body>
    <div class="container-fluid">
        <h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnUpdatePatientProvider"/></h3>
        <%
            if (request.getParameter("update") != null
                    && request.getParameter("update").equals("UpdateResident")) {
                // find demographicNos for records with last name starting with and have a resident assigned
                List<Integer> noList = demographicManager.getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(
                        loggedInInfo, request.getParameter("oldcust2"), request.getParameter("regexp")
                );
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
        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgRecords"/>
        <br>
        <%
            }
            if (request.getParameter("update") != null
                    && request.getParameter("update").equals("UpdateNurse")) {
                List<Integer> noList = demographicManager.getDemographicNumbersByNurseNumberAndDemographicLastNameRegex(
                        loggedInInfo,
                        request.getParameter("oldcust1"),
                        request.getParameter("regexp")
                );
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
        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgRecords"/>
        <br>
        <%
            }
            if (request.getParameter("update") != null
                    && request.getParameter("update").equals("UpdateMidwife")) {
                List<Integer> noList = demographicManager.getDemographicNumbersByMidwifeNumberAndDemographicLastNameRegex(
                        loggedInInfo,
                        request.getParameter("oldcust4"),
                        request.getParameter("regexp")
                );
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
        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgRecords"/>
        <br>
        <%
            }
            if (request.getParameter("update") != null
                    && request.getParameter("update").equals("UpdateMrp")) {
                Provider provider = providerManager.getProvider(loggedInInfo, request.getParameter("oldcust5"));
                List<Demographic> noList = demographicManager.getDemographicsNameRangeByProvider(loggedInInfo, provider, request.getParameter("regexp"));

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
        <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgRecords"/>
        <br>
        <% } %>

        <!-- for MRP -->
        <div class="well well-small">
            <table class="table table-striped  table-condensed">
                <FORM NAME="ADDMRP" METHOD="post"
                      ACTION="updatedemographicprovider.jsp" onsubmit="return(setregexp3())">
                    <tr>
                        <td>
                            <b><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgMrp"/></b>
                        </td>
                    </tr>
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formReplace"/>
                            <select name="oldcust5">
                                <option value="">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<%= Encode.forHtmlContent(names.get(i)) %>">
                                    <%= Encode.forHtmlContent(names.get(i + 1)) %>
                                </option>
                                <% } %>
                            </select>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust5">
                                <option value="">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<%=Encode.forHtmlContent(names.get(i))%>">
                                    <%=Encode.forHtmlContent(names.get(i + 1))%>
                                </option>
                                <% } %>
                            </select><br>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formCondition"/>
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
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formTo"/>
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
                            <INPUT type="hidden" name="regexp" value="">
                            <input type="hidden" name="update" value="UpdateMrp">
                            <INPUT class="btn btn-primary" type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.update"/>">
                        </td>
                    </tr>
                </form>
            </table>
        </div>

        <!-- for nurse -->
        <div class="well well-small">
            <table class="table table-striped  table-condensed">
                <FORM NAME="ADDAPPT1" METHOD="post"
                      ACTION="updatedemographicprovider.jsp" onsubmit="return(setregexp1())">
                    <tr>
                        <td>
                            <b><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgNurse"/></b>
                        </td>
                    </tr>
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formReplace"/>
                            <select name="oldcust1">
                                <option value="">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<%= Encode.forHtmlContent(names.get(i)) %>">
                                    <%= Encode.forHtmlContent(names.get(i + 1)) %>
                                </option>
                                <% } %>
                            </select>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust1">
                                <option value="">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<%=Encode.forHtmlContent(names.get(i))%>">
                                    <%=Encode.forHtmlContent(names.get(i + 1))%>
                                </option>
                                <% } %>
                            </select><br>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formCondition"/>
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
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formTo"/>
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
                            <INPUT type="hidden" name="regexp" value="">
                            <input type="hidden" name="update" value="UpdateNurse">
                            <INPUT class="btn btn-primary" type="submit"
                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.update"/>">
                        </td>
                    </tr>
                </form>
            </table>
        </div>

        <!-- for midwife -->
        <div class="well well-small">
            <table class="table table-striped  table-condensed">
                <FORM NAME="ADDAPPT2" METHOD="post"
                      ACTION="updatedemographicprovider.jsp" onsubmit="return(setregexp2())">
                    <tr>
                        <td><b><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgMidwife"/></b></td>
                    </tr>
                    <tr>
                        <td>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formReplace"/>
                            <select name="oldcust4">
                                <option value="">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <%
                                    for (int i = 0; i < names.size(); i = i + 2) {
                                %>
                                <option value="<%= Encode.forHtmlContent(names.get(i)) %>">
                                    <%= Encode.forHtmlContent(names.get(i + 1)) %>
                                </option>
                                <% } %>
                            </select>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust4">
                                <option value="">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<%= Encode.forHtmlContent(names.get(i)) %>">
                                    <%= Encode.forHtmlContent(names.get(i + 1)) %>
                                </option>
                                <% } %>
                            </select><br>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formCondition"/>
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
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formTo"/>
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
                            <input type="hidden" NAME="regexp" value="">
                            <input type="hidden" name="update" value="UpdateMidwife">
                            <input class="btn btn-primary" type="submit"
                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.update"/>">
                        </td>
                    </tr>
                </form>
            </table>
        </div>

        <!--  for resident -->
        <div class="well well-small">
            <table class="table table-striped  table-condensed">
                <FORM NAME="ADDAPPT" METHOD="post"
                      ACTION="updatedemographicprovider.jsp" onsubmit="return(setregexp())">
                    <tr>
                        <td><b><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgResident"/></b></td>
                    </tr>
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formReplace"/>
                            <select name="oldcust2">
                                <option value="">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<%= Encode.forHtmlContent(names.get(i)) %>">
                                    <%= Encode.forHtmlContent(names.get(i + 1)) %>
                                </option>
                                <% } %>
                            </select>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formWith"/>
                            <select name="newcust2">
                                <option value="">
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.msgNoProvider"/>
                                </option>
                                <% for (int i = 0; i < names.size(); i = i + 2) { %>
                                <option value="<%= Encode.forHtmlContent(names.get(i)) %>">
                                    <%= Encode.forHtmlContent(names.get(i + 1)) %>
                                </option>
                                <% } %>
                            </select><br>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formCondition"/>
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
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.updatedemographicprovider.formTo"/>
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
                            <INPUT TYPE="hidden" NAME="regexp" VALUE=""> <input
                                    type="hidden" name="update" value="UpdateResident"> <INPUT
                                    class="btn btn-primary"
                                    TYPE="submit"
                                    VALUE="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.update"/>">
                        </td>
                    </tr>
                </form>
            </table>
        </div>
    </div>
    </body>
</html>
