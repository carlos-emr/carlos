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
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Calendar" %>
<%@page import="ca.openosp.openo.managers.DashboardManager" %>
<%@ page import="ca.openosp.openo.commn.model.Dashboard" %>
<%@ page import="java.util.Properties" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="java.util.List" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.managers.AppManager" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<%
    GregorianCalendar cal = new GregorianCalendar();
    int curYear = cal.get(Calendar.YEAR);
    int curMonth = (cal.get(Calendar.MONTH) + 1);
    int curDay = cal.get(Calendar.DAY_OF_MONTH);

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);
    AppManager appManager = SpringUtils.getBean(AppManager.class);
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean isMobileOptimized = session.getAttribute("mobileOptimized") != null;
    Properties oscarVariables = OscarProperties.getInstance();
    String prov = (oscarVariables.getProperty("billregion", "")).trim().toUpperCase();
    String resourcebaseurl = oscarVariables.getProperty("resource_base_url");
    String curUser_no = (String) session.getAttribute("user");

    String resourcehelpHtml = "";
    UserProperty rbuHtml = userPropertyDao.getProp("resource_helpHtml");
    if (rbuHtml != null) {
        resourcehelpHtml = rbuHtml.getValue();
    }

    String userfirstname = loggedInInfo.getLoggedInProvider().getFirstName();
    String userlastname = loggedInInfo.getLoggedInProvider().getLastName();
%>

<input type="hidden" value="${pageContext.servletContext.contextPath}" id="contextPath" />
<table id="firstTable" class="noprint">
    <tr>
        <td class="icon-container">
            <img alt="OSCAR EMR" src="<%=request.getContextPath()%>/images/oscar_logo_small.png" width="19">
        </td>
        <td id="firstMenu">
            <ul id="navlist">
                <c:if test="${infirmaryView_isOscar ne 'false'}">
                    <% if (request.getParameter("viewall") != null && request.getParameter("viewall").equals("1")) { %>
                    <li>
                        <a href=# onClick="review('0')"
                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewProvAval"/>">
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.schedView"/>
                        </a>
                    </li>
                    <% } else { %>
                    <li>
                        <a href='providercontrol.jsp?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%>&view=0&displaymode=day&dboperation=searchappointmentday&viewall=1'>
                            <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.schedView"/>
                        </a>
                    </li>

                    <% } %>
                </c:if>

                <li>
                    <a href='providercontrol.jsp?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%>&view=0&displaymode=day&dboperation=searchappointmentday&caseload=1&clProv=<%=curUser_no%>'><fmt:setBundle basename="oscarResources"/><fmt:message key="global.caseload"/></a>
                </li>

                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_resource" rights="r">
                        <li>
                            <a href="#" ONCLICK="popupPage2('<%=resourcebaseurl%>');return false;"
                               title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewResources"/>"
                               onmouseover="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewResources"/>';return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.clinicalResources"/></a>
                        </li>
                    </security:oscarSec>
                </caisi:isModuleLoad>

                <%
                    if (isMobileOptimized) {
                %>
                <!-- Add a menu button for mobile version, which opens menu contents when clicked on -->
                <li id="menu"><a class="leftButton top" onClick="showHideItem('navlistcontents');">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="global.menu"/></a>
                    <ul id="navlistcontents" style="display:none;">
                        <% } %>

                        <security:oscarSec roleName="<%=roleName$%>" objectName="_search" rights="r">
                            <li id="search">
                                <caisi:isModuleLoad moduleName="caisi">
                                    <%
                                        String caisiSearch = oscarVariables.getProperty("caisi.search.workflow", "true");
                                        if ("true".equalsIgnoreCase(caisiSearch)) {
                                    %>
                                    <a href="<%= request.getContextPath() %>/PMmodule/ClientSearch2.do"
                                       TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>'
                                       OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>

                                    <%
                                    } else {
                                    %>
                                    <a HREF="#" ONCLICK="popupPage2('<%= request.getContextPath() %>/demographic/search.jsp');return false;"
                                       TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>'
                                       OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>
                                    <% } %>
                                </caisi:isModuleLoad>
                                <caisi:isModuleLoad moduleName="caisi" reverse="true">
                                    <a HREF="#" ONCLICK="popupPage2('<%= request.getContextPath() %>/demographic/search.jsp');return false;"
                                       TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>'
                                       OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.searchPatientRecords"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>
                                </caisi:isModuleLoad>
                            </li>
                        </security:oscarSec>

                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_report" rights="r">
                                <li>
                                    <a HREF="#"
                                       ONCLICK="popupPage2('<%= request.getContextPath() %>/report/reportindex.jsp','reportPage');return false;"
                                       TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.genReport"/>'
                                       OnMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.genReport"/>' ; return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.report"/></a>
                                </li>
                            </security:oscarSec>
                            <oscar:oscarPropertiesCheck property="NOT_FOR_CAISI" value="no" defaultVal="true">

                                <security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="r">
                                    <li>
                                        <a HREF="#"
                                           ONCLICK="popupPage2('<%= request.getContextPath() %>/billing/CA/<%=prov%>/billingReportCenter.jsp?displaymode=billreport&providerview=<%=curUser_no%>');return false;"
                                           TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.genBillReport"/>'
                                           onMouseOver="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.genBillReport"/>';return true"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.billing"/></a>
                                    </li>
                                </security:oscarSec>

                                <security:oscarSec roleName="<%=roleName$%>" objectName="_appointment.doctorLink"
                                                   rights="r">
                                    <li>
                                        <a HREF="#" id="inboxLink"
                                           TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewLabReports"/>'>
                                            <span id="oscar_new_lab"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.lab"/></span>
                                        </a>
                                        <oscar:newUnclaimedLab>
                                            <a id="unclaimedLabLink" class="tabalert" HREF="javascript:void(0)"
                                               title='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewLabReports"/>'>U</a>
                                        </oscar:newUnclaimedLab>
                                    </li>
                                </security:oscarSec>
                            </oscar:oscarPropertiesCheck>
                        </caisi:isModuleLoad>
                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r">
                                <li>
                                    <a HREF="#"
                                       ONCLICK="popupOscarRx(600,1024,'<%=request.getContextPath()%>/messenger/DisplayMessages.do?providerNo=<%=curUser_no%>&userName=<%=URLEncoder.encode(loggedInInfo.getLoggedInProvider().getFirstName()+" "+loggedInInfo.getLoggedInProvider().getLastName(), StandardCharsets.UTF_8)%>')"
                                       title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.messenger"/>">
                                        <span id="oscar_new_msg"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.msg"/></span></a>
                                </li>
                            </security:oscarSec>
                        </caisi:isModuleLoad>
                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r">
                                <li id="con">
                                    <a HREF="#"
                                       ONCLICK="popupOscarRx(625,1024,'<%=request.getContextPath()%>/oscarEncounter/IncomingConsultation.do?providerNo=<%=curUser_no%>&userName=<%=URLEncoder.encode(loggedInInfo.getLoggedInProvider().getFirstName()+" "+loggedInInfo.getLoggedInProvider().getLastName(), StandardCharsets.UTF_8)%>')"
                                       title="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewConReq"/>">
                                        <span id="oscar_aged_consults"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.con"/></span></a>
                                </li>
                            </security:oscarSec>
                        </caisi:isModuleLoad>
                        <%
                            boolean hide_eConsult = OscarProperties.getInstance().isPropertyActive("hide_eConsult_link");
                            if ("on".equalsIgnoreCase(prov) && !hide_eConsult) {
                        %>
                        <li id="econ">
                            <a href="#" onclick="popupOscarRx(625, 1024, '<%=request.getContextPath()%>/oscarEncounter/econsult.do')"
                               title="eConsult">
                                <span>eConsult</span></a>
                        </li>
                        <% } %>
                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_edoc" rights="r">
                                <li>
                                    <a HREF="#"
                                       onclick="popup('700', '1024', '<%= request.getContextPath() %>/documentManager/documentReport.jsp?function=providers&functionid=<%=curUser_no%>&curUser=<%=curUser_no%>', 'edocView');"
                                       TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.viewEdoc"/>'><fmt:setBundle basename="oscarResources"/><fmt:message key="global.edoc"/></a>
                                </li>
                            </security:oscarSec>
                        </caisi:isModuleLoad>
                        <security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="r">
                            <li>
                                <a HREF="#"
                                   ONCLICK="popupPage2('<%= request.getContextPath() %>/tickler/ticklerMain.jsp','<fmt:setBundle basename="oscarResources"/><fmt:message key="global.tickler"/>');return false;"
                                   TITLE='<fmt:setBundle basename="oscarResources"/><fmt:message key="global.tickler"/>'>
                                    <span id="oscar_new_tickler"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btntickler"/></span></a>
                            </li>
                        </security:oscarSec>

                        <oscar:oscarPropertiesCheck property="referral_menu" value="yes">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r">
                                <li id="ref">
                                    <a href="#"
                                       onclick="popupPage(550,800,'<%=request.getContextPath()%>/admin/ManageBillingReferral.do');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.manageReferrals"/></a>
                                </li>
                            </security:oscarSec>
                        </oscar:oscarPropertiesCheck>

                        <oscar:oscarPropertiesCheck property="WORKFLOW" value="yes">
                            <li><a href="javascript:void(0)"
                                   onClick="popup(700,1024,'<%= request.getContextPath() %>/oscarWorkflow/WorkFlowList.jsp','<fmt:setBundle basename="oscarResources"/><fmt:message key="global.workflow"/>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnworkflow"/>
                            </a></li>
                        </oscar:oscarPropertiesCheck>


                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>"
                                               objectName="_admin,_admin.userAdmin,_admin.schedule,_admin.billing,_admin.resource,_admin.reporting,_admin.backup,_admin.messenger,_admin.eform,_admin.encounter,_admin.misc,_admin.fax,_admin.flowsheet"
                                               rights="r">

                                <li id="admin2">
                                    <a href="javascript:void(0)" id="admin-panel" TITLE='Administration Panel'
                                       onclick="newWindow('<%=request.getContextPath()%>/administration/','admin')">Administration</a>
                                </li>

                            </security:oscarSec>
                        </caisi:isModuleLoad>

                        <security:oscarSec roleName="<%=roleName$%>" objectName="_dashboardDisplay" rights="r">
                            <%
                                DashboardManager dashboardManager = SpringUtils.getBean(DashboardManager.class);
                                List<Dashboard> dashboards = dashboardManager.getActiveDashboards(loggedInInfo);
                                pageContext.setAttribute("dashboards", dashboards);
                            %>

                            <li id="dashboardList">
                                <div class="dropdown">
                                    <a href="#" class="dashboardBtn">Dashboard</a>
                                    <div class="dashboardDropdown">
                                        <ul>
                                            <c:forEach items="${ dashboards }" var="dashboard">
                                                <li>
                                                    <a href="javascript:void(0)"
                                                       onclick="newWindow('<%=request.getContextPath()%>/web/dashboard/display/DashboardDisplay.do?method=getDashboard&dashboardId=${ dashboard.id }','dashboard')">
                                                        <c:out value="${ dashboard.name }"/>
                                                    </a>
                                                </li>
                                            </c:forEach>
                                            <security:oscarSec roleName="<%=roleName$%>"
                                                               objectName="_dashboardCommonLink" rights="r">
                                                <li>
                                                    <a href="javascript:void(0)"
                                                       onclick="newWindow('<%=request.getContextPath()%>/web/dashboard/display/sharedOutcomesDashboard.jsp','shared_dashboard')">
                                                        Common Provider Dashboard
                                                    </a>
                                                </li>
                                            </security:oscarSec>
                                        </ul>
                                    </div>

                                </div>
                            </li>

                        </security:oscarSec>
                        <li id="helpLink">
                            <%if (resourcehelpHtml == "") { %>
                            <a href="javascript:void(0)"
                               onClick="popupPage(600,750,'<%=resourcebaseurl%>')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.help"/></a>
                            <%} else {%>
                            <div id="help-link">
                                <a href="javascript:void(0)"
                                   onclick="document.getElementById('helpHtml').style.display='block';document.getElementById('helpHtml').style.right='0px';"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.help"/></a>

                                <div id="helpHtml">
                                    <div class="help-title">Help</div>

                                    <div class="help-body">

                                        <%=resourcehelpHtml%>
                                    </div>
                                    <a href="javascript:void(0)" class="help-close"
                                       onclick="document.getElementById('helpHtml').style.right='-280px';document.getElementById('helpHtml').style.display='none'">(X)</a>
                                </div>

                            </div>
                            <%}%>
                        </li>

                        <% if (isMobileOptimized) { %>
                    </ul>
                </li> <!-- end menu list for mobile-->
                <% } %>

            </ul>  <!--- old TABLE -->

        </td>

        <td id="userSettings">
            <ul id="userSettingsMenu">
                <li>
                    <a title="Scratch Pad" href="javascript: function myFunction() {return false; }"
                       onClick="popup(700,1024,'<%= request.getContextPath() %>/scratch/index.jsp','scratch')"><span
                            class="glyphicon glyphicon-list-alt"></span></a>
                </li>
                <li>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_pref" rights="r">
                    <a href="javascript:void(0)"
                       onClick="popupPage(715,680,'providerpreference.jsp?provider_no=<%=curUser_no%>')"
                       title='<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.msgSettings"/>'>

                        </security:oscarSec>
                        <span class="glyphicon glyphicon-user"></span>

                        <span>
                                <c:out value='<%= userfirstname + " " + userlastname %>'/>
                            </span>
                        <security:oscarSec roleName="<%=roleName$%>" objectName="_pref" rights="r">
                    </a>
                    </security:oscarSec>
                </li>
            </ul>
        </td>
        <td>
            <a id="logoutButton" title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnLogout"/>" href="<%= request.getContextPath() %>/logout.jsp">
                <span class="glyphicon glyphicon-off"></span>
            </a>
        </td>

    </tr>
</table>

<script type="text/javascript"
        src="${pageContext.servletContext.contextPath}/library/jquery/jquery-1.12.0.min.js"></script>
<script type="text/javascript"
        src="${pageContext.servletContext.contextPath}/library/jquery/jquery-ui-1.12.1.min.js"></script>
<script>
    const contextPath = document.getElementById("contextPath").value;
    const originalInboxLinkClickEvent = "popupInboxManager('" + contextPath + "/documentManager/inboxManage.do?method=prepareForIndexPage&providerNo=<%=curUser_no%>');return false;";
    const newInboxLinkClickEvent = "popupInboxManager('" + contextPath + "/www/inboxhub/Inboxhub.do?method=displayInboxForm', 800);return false;";

    const originalUnclaimedLabLinkClickEvent = "popupInboxManager('" + contextPath + "/documentManager/inboxManage.do?method=prepareForIndexPage&providerNo=0&searchProviderNo=0&status=N&lname=&fname=&hnum=&pageNum=1&startIndex=0');return false;";
    const newUnclaimedLabLinkClickEvent = "popupInboxManager('" + contextPath + "/www/inboxhub/Inboxhub.do?method=displayInboxForm&unclaimed=1', 800);return false;";

     document.getElementById("inboxLink").addEventListener("mouseup", function(event) {
        if(event.altKey) {
            document.getElementById("inboxLink").setAttribute("onclick", newInboxLinkClickEvent);
        } else {
            document.getElementById("inboxLink").setAttribute("onclick", originalInboxLinkClickEvent);
        }
    });

    document.getElementById("unclaimedLabLink").addEventListener("mouseup", function(event) {
        if(event.altKey) {
            document.getElementById("unclaimedLabLink").setAttribute("onclick", newUnclaimedLabLinkClickEvent);
        } else {
            document.getElementById("unclaimedLabLink").setAttribute("onclick", originalUnclaimedLabLinkClickEvent);
        }
    });

    function openPreferences(providerNumber) {
        const $div = jQuery('<div />').appendTo('body');
        const dialogContainer = $div.attr('id', 'preference-dialog');
        const data = {
            "provider_no": providerNumber
        };
        const url = "providerpreference.jsp";
        const dialog = dialogContainer.load(url, data).dialog({
            modal: true,
            width: 685,
            height: 355,
            draggable: false,
            title: "Provider Preferences",
        }).dialog("open");
    }
</script>

