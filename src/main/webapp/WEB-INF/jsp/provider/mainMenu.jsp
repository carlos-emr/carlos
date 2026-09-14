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
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.GregorianCalendar" %>
<%@ page import="java.util.Calendar" %>
<%@page import="io.github.carlos_emr.carlos.managers.DashboardManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Dashboard" %>
<%@ page import="java.util.Properties" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="java.util.List" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.managers.AppManager" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.NavPath" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>

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
    Properties oscarVariables = CarlosProperties.getInstance();
    String prov = (oscarVariables.getProperty("billregion", "")).trim().toUpperCase();
    String resourcebaseurl = oscarVariables.getProperty("resource_base_url");
    String curUser_no = (String) session.getAttribute("user");

    String resourcehelpHtml = "";
    UserProperty rbuHtml = userPropertyDao.getProp("resource_helpHtml");
    if (rbuHtml != null) {
        resourcehelpHtml = rbuHtml.getValue();
    }

    Provider loggedInProvider = loggedInInfo != null ? loggedInInfo.getLoggedInProvider() : null;
    String userfirstname = loggedInProvider != null ? loggedInProvider.getFirstName() : "";
    String userlastname = loggedInProvider != null ? loggedInProvider.getLastName() : "";
    String encodedUserName = URLEncoder.encode(StringUtils.trim(userfirstname + " " + userlastname), StandardCharsets.UTF_8);
    boolean scheduleNavActive = "1".equals(request.getParameter("scheduleNav"));
    boolean scheduleTabActive = NavPath.requestPathMatches(request, "/provider/providercontrol",
            "/provider/appointmentprovideradmin", "/provider/appointmentprovideradminday");
    boolean searchTabActive = NavPath.requestPathMatches(request, "/demographic/ViewSearch",
            "/PMmodule/ClientSearch", "/PMmodule/ClientSearch2");
    boolean inboxTabActive = NavPath.requestPathMatches(request, "/web/inboxhub",
            "/documentManager/ViewInbox");
    boolean ticklerTabActive = NavPath.requestPathMatches(request, "/tickler/");
    boolean messengerTabActive = NavPath.requestPathMatches(request, "/messenger/");
    boolean consultationTabActive = NavPath.requestPathMatches(request, "/encounter/IncomingConsultation",
            "/encounter/oscarConsultationRequest");
    boolean documentTabActive = NavPath.requestPathMatches(request, "/documentManager/") && !inboxTabActive;
    boolean reportTabActive = NavPath.requestPathMatches(request, "/report/", "/oscarReport/");
    boolean adminTabActive = NavPath.requestPathMatches(request, "/administration", "/admin/");
    boolean econsultTabActive = NavPath.requestPathMatches(request, "/encounter/econsult");

    // Build menu destinations once so same-tab navigation and popup fallbacks cannot drift apart.
    String messengerUrl = request.getContextPath() + "/messenger/DisplayMessages?providerNo=" + curUser_no + "&userName=" + encodedUserName;
    String consultationUrl = request.getContextPath() + "/encounter/IncomingConsultation?providerNo=" + curUser_no + "&userName=" + encodedUserName;
    String documentReportUrl = request.getContextPath() + "/documentManager/ViewDocumentReport?function=providers&functionid=" + SafeEncode.forUriComponent(curUser_no);
    String reportIndexUrl = request.getContextPath() + "/report/ViewReportindex";
    String ticklerUrl = request.getContextPath() + "/tickler/ViewTicklerMain";
    String administrationUrl = request.getContextPath() + "/administration";
    String searchUrl = request.getContextPath() + "/demographic/ViewSearch";
    String econsultUrl = request.getContextPath() + "/encounter/econsult";
%>

<input type="hidden" value="${pageContext.servletContext.contextPath}" id="contextPath" />
<table id="firstTable" class="noprint">
    <tr>
        <td id="firstMenu">
            <div class="icon-container">
                <img alt="CARLOS EMR" src="<%=request.getContextPath()%>/images/oscar_logo_small.png" width="19">
            </div>
            <ul id="navlist">
                <c:if test="${infirmaryView_isOscar ne 'false'}">
                    <% if (request.getParameter("viewall") != null && request.getParameter("viewall").equals("1")) { %>
                    <li class="<%= scheduleTabActive ? "nav-active" : "" %>">
                        <a href=# onClick="review('0')"
                           title="<fmt:message key="provider.appointmentProviderAdminDay.viewProvAval"/>">
                            <fmt:message key="provider.appointmentProviderAdminDay.schedView"/>
                        </a>
                    </li>
                    <% } else { %>
                    <li class="<%= scheduleTabActive ? "nav-active" : "" %>">
                        <a href='<%= request.getContextPath() %>/provider/providercontrol?year=<%=curYear%>&month=<%=curMonth%>&day=<%=curDay%>&view=0&displaymode=day&dboperation=searchappointmentday&viewall=1'>
                            <fmt:message key="provider.appointmentProviderAdminDay.schedView"/>
                        </a>
                    </li>

                    <% } %>
                </c:if>

                <%
                    if (isMobileOptimized) {
                %>
                <!-- Add a menu button for mobile version, which opens menu contents when clicked on -->
                <li id="menu"><a class="leftButton top" onClick="showHideItem('navlistcontents');">
                    <fmt:message key="global.menu"/></a>
                    <ul id="navlistcontents" style="display:none;">
                        <% } %>

                        <security:oscarSec roleName="<%=roleName$%>" objectName="_search" rights="r">
                            <li id="search" class="<%= searchTabActive ? "nav-active" : "" %>">
                                <caisi:isModuleLoad moduleName="caisi">
                                    <%
                                        String caisiSearch = oscarVariables.getProperty("caisi.search.workflow", "true");
                                        if ("true".equalsIgnoreCase(caisiSearch)) {
                                    %>
                                    <a href="<%= request.getContextPath() %>/PMmodule/ClientSearch2"
                                       TITLE='<fmt:message key="global.searchPatientRecords"/>'
                                       OnMouseOver="window.status='<fmt:message key="global.searchPatientRecords"/>' ; return true"><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>

                                    <%
                                    } else {
                                    %>
                                    <a HREF="#" ONCLICK="popupPage2('<%=SafeEncode.forJavaScriptAttribute(searchUrl)%>');return false;"
                                       TITLE='<fmt:message key="global.searchPatientRecords"/>'
                                       OnMouseOver="window.status='<fmt:message key="global.searchPatientRecords"/>' ; return true"><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>
                                    <% } %>
                                </caisi:isModuleLoad>
                                <caisi:isModuleLoad moduleName="caisi" reverse="true">
                                    <a HREF="#" ONCLICK="popupPage2('<%=SafeEncode.forJavaScriptAttribute(searchUrl)%>');return false;"
                                       TITLE='<fmt:message key="global.searchPatientRecords"/>'
                                       OnMouseOver="window.status='<fmt:message key="global.searchPatientRecords"/>' ; return true"><fmt:message key="provider.appointmentProviderAdminDay.search"/></a>
                                </caisi:isModuleLoad>
                            </li>
                        </security:oscarSec>

                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <oscar:oscarPropertiesCheck property="NOT_FOR_CAISI" value="no" defaultVal="true">
                                <security:oscarSec roleName="<%=roleName$%>" objectName="_appointment.doctorLink"
                                                   rights="r">
                                    <li class="<%= inboxTabActive ? "nav-active" : "" %>">
                                        <a HREF="<%= scheduleNavActive ? request.getContextPath() + "/web/inboxhub/Inboxhub?method=displayInboxForm&scheduleNav=1" : "#" %>" id="inboxLink"
                                           TITLE='<fmt:message key="provider.appointmentProviderAdminDay.viewLabReports"/>'>
                                            <span id="oscar_new_lab">
                                                <oscar:newLab providerNo="<%=curUser_no%>"><fmt:message key="global.lab"/></oscar:newLab>
                                            </span>
                                        </a>
                                        <oscar:newUnclaimedLab>
                                            <a id="unclaimedLabLink" class="tabalert" HREF="<%= scheduleNavActive ? request.getContextPath() + "/web/inboxhub/Inboxhub?method=displayInboxForm&unclaimed=1&scheduleNav=1" : "javascript:void(0)" %>"
                                               title='<fmt:message key="provider.appointmentProviderAdminDay.viewLabReports"/>'>U</a>
                                        </oscar:newUnclaimedLab>
                                    </li>
                                </security:oscarSec>
                            </oscar:oscarPropertiesCheck>
                        </caisi:isModuleLoad>

                        <security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="r">
                            <li class="<%= ticklerTabActive ? "nav-active" : "" %>">
                                <a HREF="#"
                                   ONCLICK="return openScheduleMenuSection('<%=SafeEncode.forJavaScriptAttribute(ticklerUrl)%>', function(u){ popupPage2(u,'ticklerPage'); }, event);"
                                   TITLE='<fmt:message key="global.tickler"/>'>
                                    <span id="oscar_new_tickler">
                                        <oscar:newTickler providerNo="<%=curUser_no%>"><fmt:message key="global.btntickler"/></oscar:newTickler>
                                    </span></a>
                            </li>
                        </security:oscarSec>

                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r">
                                <li class="<%= messengerTabActive ? "nav-active" : "" %>">
                                    <a HREF="#"
                                       ONCLICK="return openScheduleMenuSection('<%=SafeEncode.forJavaScriptAttribute(messengerUrl)%>', function(u){ popupOscarRx(600,1024,u); }, event);"
                                       title="<fmt:message key="global.messenger"/>">
                                        <span id="oscar_new_msg">
                                            <oscar:newMessage providerNo="<%=curUser_no%>"><fmt:message key="global.msg"/></oscar:newMessage>
                                        </span></a>
                                </li>
                            </security:oscarSec>
                        </caisi:isModuleLoad>
                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r">
                                <li id="con" class="<%= consultationTabActive ? "nav-active" : "" %>">
                                    <a HREF="#"
                                       ONCLICK="return openScheduleMenuSection('<%=SafeEncode.forJavaScriptAttribute(consultationUrl)%>', function(u){ popupOscarRx(625,1024,u); }, event);"
                                       title="<fmt:message key="provider.appointmentProviderAdminDay.viewConReq"/>">
                                        <span id="oscar_aged_consults"><fmt:message key="global.con"/></span></a>
                                </li>
                            </security:oscarSec>
                        </caisi:isModuleLoad>
                        <%
                            boolean hide_eConsult = CarlosProperties.getInstance().isPropertyActive("hide_eConsult_link");
                            if ("on".equalsIgnoreCase(prov) && !hide_eConsult) {
                        %>
                        <li id="econ" class="<%= econsultTabActive ? "nav-active" : "" %>">
                            <a href="#" onclick="popupOscarRx(625, 1024, '<%=SafeEncode.forJavaScriptAttribute(econsultUrl)%>')"
                               title="eConsult">
                                <span><fmt:message key="provider.mainMenu.eConsult"/></span></a>
                        </li>
                        <% } %>

                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_edoc" rights="r">
                                <li class="<%= documentTabActive ? "nav-active" : "" %>">
                                    <a HREF="#"
                                       onclick="return openScheduleMenuSection('<%=SafeEncode.forJavaScriptAttribute(documentReportUrl)%>', function(u){ popup('700', '1024', u, 'edocView'); }, event);"
                                       TITLE='<fmt:message key="provider.appointmentProviderAdminDay.viewEdoc"/>'><fmt:message key="global.edoc"/></a>
                                </li>
                            </security:oscarSec>
                        </caisi:isModuleLoad>

                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_report" rights="r">
                                <li class="<%= reportTabActive ? "nav-active" : "" %>">
                                    <a HREF="#"
                                       ONCLICK="return openScheduleMenuSection('<%=SafeEncode.forJavaScriptAttribute(reportIndexUrl)%>', function(u){ popupPage2(u,'reportPage'); }, event);"
                                       TITLE='<fmt:message key="global.genReport"/>'
                                       OnMouseOver="window.status='<fmt:message key="global.genReport"/>' ; return true"><fmt:message key="global.report"/></a>
                                </li>
                            </security:oscarSec>
                        </caisi:isModuleLoad>

                        <oscar:oscarPropertiesCheck property="referral_menu" value="yes">
                            <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r">
                                <li id="ref">
                                    <a href="#"
                                   onclick="popupPage(550,800,'<%=request.getContextPath()%>/admin/ManageBillingReferral');return false;"><fmt:message key="global.manageReferrals"/></a>
                                </li>
                            </security:oscarSec>
                        </oscar:oscarPropertiesCheck>

                        <oscar:oscarPropertiesCheck property="WORKFLOW" value="yes">
                            <li><a href="javascript:void(0)"
                                   onClick="popup(700,1024,'<%= request.getContextPath() %>/oscarWorkflow/WorkFlowList','<fmt:message key="global.workflow"/>')"><fmt:message key="global.btnworkflow"/>
                            </a></li>
                        </oscar:oscarPropertiesCheck>

                        <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                            <security:oscarSec roleName="<%=roleName$%>"
                                               objectName="_admin,_admin.userAdmin,_admin.schedule,_admin.billing,_admin.resource,_admin.reporting,_admin.backup,_admin.messenger,_admin.eform,_admin.encounter,_admin.misc,_admin.fax,_admin.flowsheet"
                                               rights="r">

                                <li id="admin2" class="<%= adminTabActive ? "nav-active" : "" %>">
                                    <a href="javascript:void(0)" id="admin-panel" TITLE='<fmt:message key="admin.admin.page.title"/>'
                                       onclick="return openScheduleMenuSection('<%=SafeEncode.forJavaScriptAttribute(administrationUrl)%>', function(u){ newWindow(u,'admin'); }, event);"><fmt:message key="provider.mainMenu.administration"/></a>
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
                                    <a href="#" class="dashboardBtn"><fmt:message key="provider.mainMenu.dashboard"/></a>
                                    <div class="dashboardDropdown">
                                        <ul>
                                            <c:forEach items="${ dashboards }" var="dashboard">
                                                <li>
                                                    <a href="javascript:void(0)"
                                                       onclick="newWindow('<%=request.getContextPath()%>/web/dashboard/display/DashboardDisplay?method=getDashboard&dashboardId=${ dashboard.id }','dashboard')">
                                                        ${carlos:forHtml(dashboard.name)}
                                                    </a>
                                                </li>
                                            </c:forEach>
                                            <security:oscarSec roleName="<%=roleName$%>"
                                                               objectName="_dashboardCommonLink" rights="r">
                                                <li>
                                                    <a href="javascript:void(0)"
                                                       onclick="newWindow('<%=request.getContextPath()%>/web/dashboard/display/sharedOutcomesDashboard','shared_dashboard')">
                                                        <fmt:message key="provider.mainMenu.commonDashboard"/>
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
                               onClick="popupPage(600,750,'<%=resourcebaseurl%>')"><fmt:message key="global.help"/></a>
                            <%} else {%>
                            <div id="help-link">
                                <a href="javascript:void(0)"
                                   onclick="document.getElementById('helpHtml').style.display='block';document.getElementById('helpHtml').style.right='0px';"><fmt:message key="global.help"/></a>

                                <div id="helpHtml">
                                    <div class="help-title"><fmt:message key="provider.mainMenu.helpTitle"/></div>

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
                    <a title="<fmt:message key='ScratchPad.title'/>" href="javascript: function myFunction() {return false; }"
                       onClick="popup(700,1024,'<%= request.getContextPath() %>/Scratch','scratch')"><span
                            class="fa-solid fa-rectangle-list"></span></a>
                </li>
                <li>
                    <security:oscarSec roleName="<%=roleName$%>" objectName="_pref" rights="r">
                    <a href="javascript:void(0)"
                       onClick="popupPage(800,1000,'<%= request.getContextPath() %>/provider/ViewProviderPreference?provider_no=<carlos:encode value='<%= curUser_no %>' context="uriComponent"/>')"
                       title='<fmt:message key="provider.appointmentProviderAdminDay.msgSettings"/>'>

                        </security:oscarSec>
                        <span class="fa-solid fa-user"></span>

                        <span>
                                <carlos:encode value='<%= userfirstname + " " + userlastname %>' context="html"/>
                            </span>
                        <security:oscarSec roleName="<%=roleName$%>" objectName="_pref" rights="r">
                    </a>
                    </security:oscarSec>
                </li>
            </ul>
            <div>
                <a id="logoutButton" title="<fmt:message key="global.btnLogout"/>" href="<%= request.getContextPath() %>/logoutPage">
                    <span class="fa-solid fa-power-off"></span>
                </a>
            </div>
        </td>

    </tr>
</table>

<script>
    var scheduleNavActive = <%=scheduleNavActive%>;
    var contextPath = document.getElementById("contextPath").value;

    function normalizeScheduleMenuNavigationMode(mode) {
        if (mode === 'tab' || mode === 'focused') {
            return mode;
        }
        return 'popup';
    }

    function applyScheduleMenuNavigationPreference(mode) {
        var normalizedMode = normalizeScheduleMenuNavigationMode(mode);
        scheduleNavActive = normalizedMode === 'tab' || normalizedMode === 'focused';
    }

    var existingApplyScheduleNavigationPreference = window.applyScheduleNavigationPreference;
    window.applyScheduleNavigationPreference = function(mode) {
        applyScheduleMenuNavigationPreference(mode);
        if (typeof existingApplyScheduleNavigationPreference === 'function'
                && existingApplyScheduleNavigationPreference !== applyScheduleMenuNavigationPreference) {
            existingApplyScheduleNavigationPreference(mode);
        }
    };

    function handleScheduleMenuNavigationPreferenceMessage(message) {
        if (message && message.mode) {
            applyScheduleMenuNavigationPreference(message.mode);
        }
    }

    try {
        var scheduleNavigationPreferenceChannel = new BroadcastChannel('carlos_schedule_navigation_mode');
        scheduleNavigationPreferenceChannel.onmessage = function(event) {
            handleScheduleMenuNavigationPreferenceMessage(event.data);
        };
    } catch(e) { /* BroadcastChannel not supported */ }

    try {
        window.addEventListener('storage', function(event) {
            if (event.key !== 'carlos_schedule_navigation_mode' || !event.newValue) {
                return;
            }
            try {
                handleScheduleMenuNavigationPreferenceMessage(JSON.parse(event.newValue));
            } catch(e) {}
        });
    } catch(e) {}

    function appendScheduleMenuQueryParam(url, key, value) {
        var parts = String(url).split('#');
        var base = parts[0];
        var fragment = parts.length > 1 ? '#' + parts.slice(1).join('#') : '';
        var joiner = base.indexOf('?') === -1 ? '?' : '&';
        return base + joiner + encodeURIComponent(key) + '=' + encodeURIComponent(value) + fragment;
    }

    function openScheduleMenuSection(url, popupAction, clickEvent) {
        if (scheduleNavActive && !(clickEvent && clickEvent.altKey)) {
            window.location.href = appendScheduleMenuQueryParam(url, 'scheduleNav', '1');
            return false;
        }
        if (scheduleNavActive && clickEvent && clickEvent.altKey) {
            // Alt-click intentionally escapes the schedule shell without changing the user's saved mode.
            if (typeof popupTab === 'function') {
                popupTab(url);
            } else {
                window.open(url, '_blank', 'noopener');
            }
            return false;
        }
        if (typeof popupAction === 'function') {
            popupAction(url);
        }
        return false;
    }

    function fallbackMenuPopup(height, width, url, windowName) {
        var windowprops = "height=" + height + ",width=" + width
            + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
        var opened = window.open(url, windowName, windowprops);
        if (opened) {
            opened.focus();
        }
        return opened;
    }

    function openMenuPopup(height, width, url, windowName) {
        if (typeof window.popup === 'function' && !window.popup.scheduleMenuFallback) {
            return window.popup(height, width, url, windowName);
        }
        return fallbackMenuPopup(height, width, url, windowName);
    }
    fallbackMenuPopup.scheduleMenuFallback = true;

    if (typeof window.popup !== 'function') {
        // Some schedule-shell pages include this shared menu without the legacy popup helper.
        window.popup = fallbackMenuPopup;
    }

    if (typeof newWindow !== 'function') {
        window.newWindow = function(url, windowName) {
            return openMenuPopup(window.innerHeight || 700, window.innerWidth || 1024, url, windowName);
        };
    }

    if (typeof popupPage2 !== 'function') {
        window.popupPage2 = function(varpage, windowname, vheight, vwidth) {
            windowname = typeof windowname !== 'undefined' ? windowname : 'apptProviderSearch';
            vheight = typeof vheight !== 'undefined' ? vheight : 700;
            vwidth = typeof vwidth !== 'undefined' ? vwidth : 1024;
            return openMenuPopup(vheight, vwidth, varpage, windowname);
        };
    }

    if (typeof popupPage !== 'function') {
        window.popupPage = function(vheight, vwidth, varpage) {
            return openMenuPopup(vheight, vwidth, varpage, 'apptProviderSearch');
        };
    }

    if (typeof popupOscarRx !== 'function') {
        window.popupOscarRx = function(vheight, vwidth, varpage) {
            return openMenuPopup(vheight, vwidth, varpage, 'oscarRx_appt');
        };
    }

    if (typeof popupInboxManager !== 'function') {
        window.popupInboxManager = function(varpage, height, width) {
            return openMenuPopup(height || 700, width || 1215, varpage, 'apptProviderInbox');
        };
    }

    var inboxUrl = contextPath + "/web/inboxhub/Inboxhub?method=displayInboxForm";
    var unclaimedLabUrl = contextPath + "/web/inboxhub/Inboxhub?method=displayInboxForm&unclaimed=1";
    var inboxLinkClickEvent = "return openScheduleMenuSection('" + inboxUrl + "', function(u){ popupInboxManager(u, 800); }, event);";
    var unclaimedLabLinkClickEvent = "return openScheduleMenuSection('" + unclaimedLabUrl + "', function(u){ popupInboxManager(u, 800); }, event);";

    const inboxLink = document.getElementById("inboxLink");
    if (inboxLink) {
        inboxLink.setAttribute("onclick", inboxLinkClickEvent);
    }
    const unclaimedLabLink = document.getElementById("unclaimedLabLink");
    if (unclaimedLabLink) {
        unclaimedLabLink.setAttribute("onclick", unclaimedLabLinkClickEvent);
    }

    function openPreferences(providerNumber) {
        if (typeof jQuery !== 'function' || typeof jQuery.fn.dialog !== 'function') {
            // Some pages include this shared menu without jQuery UI. Use the same popup path instead of loading jQuery twice.
            popupPage(800, 1000, "<%= request.getContextPath() %>/provider/ViewProviderPreference?provider_no=" + encodeURIComponent(providerNumber));
            return;
        }
        const $div = jQuery('<div />').appendTo('body');
        const dialogContainer = $div.attr('id', 'preference-dialog');
        const data = {
            "provider_no": providerNumber
        };
        const url = "<%= request.getContextPath() %>/provider/ViewProviderPreference";
        const dialog = dialogContainer.load(url, data).dialog({
            modal: true,
            width: 685,
            height: 355,
            draggable: false,
            title: "Provider Preferences",
        }).dialog("open");
    }
</script>
