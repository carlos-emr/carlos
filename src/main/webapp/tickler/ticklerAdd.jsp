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

<%@page import="ca.openosp.openo.commn.dao.DemographicDao" %>
<%@page import="ca.openosp.openo.commn.model.Demographic" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_tickler" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_tickler");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    String user_no = (String) session.getAttribute("user");
    int nItems = 0;
    String strLimit1 = "0";
    String strLimit2 = "5";
    if (request.getParameter("limit1") != null) strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null) strLimit2 = request.getParameter("limit2");
//String providerview = request.getParameter("providerview")==null?"all":request.getParameter("providerview") ;
    boolean bFirstDisp = true; //this is the first time to display the window
    if (request.getParameter("bFirstDisp") != null) bFirstDisp = (request.getParameter("bFirstDisp")).equals("true");
    String ChartNo;
    String demoMRP = "";
    String demoName = request.getParameter("name");
    String defaultTaskAssignee = "";

    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    Demographic demographic = demographicDao.getDemographic(request.getParameter("demographic_no"));
    if (demographic != null) {
        demoName = demographic.getFormattedName();
        demoMRP = demographic.getProviderNo();
        bFirstDisp = false;
    }

    if (demoName == null) {
        demoName = "";
    }

    Boolean writeToEncounter = false;
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    Boolean caisiEnabled = OscarProperties.getInstance().isPropertyActive("caisi");
    Integer defaultProgramId = null;
    List<ProgramProvider> programProviders = new ArrayList<ProgramProvider>();

    if (caisiEnabled) {
        ProgramProviderDAO programProviderDao = SpringUtils.getBean(ProgramProviderDAO.class);
        programProviders = programProviderDao.getProgramProviderByProviderNo(loggedInInfo.getLoggedInProviderNo());
        if (programProviders.size() == 1) {
            defaultProgramId = programProviders.get(0).getProgram().getId();
        }
    }

//Retrieve encounter id for updating encounter navbar if info this page changes anything
    String parentAjaxId;
    if (request.getParameter("parentAjaxId") != null)
        parentAjaxId = request.getParameter("parentAjaxId");
    else
        parentAjaxId = "";

    String updateParent;
    if (request.getParameter("updateParent") != null)
        updateParent = request.getParameter("updateParent");
    else
        updateParent = "true";

    boolean recall = false;
    String taskTo = user_no; //default current user
    String priority = "Normal";
    if (request.getParameter("taskTo") != null) taskTo = request.getParameter("taskTo");
    if (request.getParameter("priority") != null) priority = request.getParameter("priority");
    if (request.getParameter("recall") != null) recall = true;

    UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
    UserProperty prop = propertyDao.getProp(user_no, "tickler_task_assignee");

//don't over ride taskTo query param
    if (request.getParameter("taskTo") == null) {

        if (prop != null) {
            defaultTaskAssignee = prop.getValue();
            if (!defaultTaskAssignee.equals("mrp")) {
                taskTo = defaultTaskAssignee;
            } else if (defaultTaskAssignee.equals("mrp")) {
                taskTo = demoMRP;
            }
        }

    }


%>
<%@ page import="java.util.*, ca.openosp.*" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.commn.model.Appointment" %>
<%@page import="ca.openosp.openo.commn.dao.OscarAppointmentDao" %>
<%@page import="ca.openosp.openo.commn.model.Provider" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>

<%
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    OscarAppointmentDao appointmentDao = SpringUtils.getBean(OscarAppointmentDao.class);
%>

<%
    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);

%><%
    String xml_vdate = request.getParameter("xml_vdate") == null ? "" : request.getParameter("xml_vdate");
    String xml_appointment_date = request.getParameter("xml_appointment_date") == null ? MyDateFormat.getMysqlStandardDate(curYear, curMonth, curDay) : request.getParameter("xml_appointment_date");
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.openo.commn.model.Site" %>
<%@page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProgramProviderDAO" %>
<%@ page import="ca.openosp.openo.PMmodule.model.ProgramProvider" %>
<%@ page import="org.owasp.encoder.Encode" %>
<!DOCTYPE html>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.title"/></title>

        <script language="JavaScript">

            // Add options 1 to 10 for days, weeks, months, and years
            function addQuickPick() {
                const quickPickDiv = document.getElementById('quickPickDateOptions');
                for (let i = 0; i < 40; i++) {
                    const linkButton = document.createElement('a');
                    linkButton.href = '#';
                    switch (Math.floor(i / 10)) {
                        case 0:
                            linkButton.innerText = (i % 10) + 1 + "d";
                            linkButton.onclick = function () {
                                addTime((i % 10) + 1, "days");
                            };
                            break;//1 through 10 days
                        case 1:
                            linkButton.innerText = (i % 10) + 1 + "w";
                            linkButton.onclick = function () {
                                addTime(((i % 10) + 1) * 7, "days");
                            };
                            break;//1 through 10 weeks
                        case 2:
                            linkButton.innerText = (i % 10) + 1 + "m";
                            linkButton.onclick = function () {
                                addTime((i % 10) + 1, "months");
                            };
                            break;//1 through 10 months
                        case 3:
                            linkButton.innerText = (i % 10) + 1 + "y";
                            linkButton.onclick = function () {
                                addTime(((i % 10) + 1) * 12, "months");
                            };
                            break;//1 through 10 years
                    }
                    quickPickDiv.appendChild(linkButton);
                }
            }

            function addTime(num, type) {
                let currentDate = new Date();
                if (type === "months") {
                    currentDate.setMonth(currentDate.getMonth() + num);
                } else {
                    currentDate.setDate(currentDate.getDate() + num);
                }
                const year = currentDate.getFullYear();
                const month = String(currentDate.getMonth() + 1).padStart(2, '0');
                const day = String(currentDate.getDate()).padStart(2, '0');
                const newDate = year + "-" + month + "-" + day;
                document.serviceform.xml_appointment_date.value = newDate;
            }

            function popupPage(vheight, vwidth, varpage) { //open a new popup window
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
                var popup = window.open(page, "attachment", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            function selectprovider(s) {
                if (self.location.href.lastIndexOf("&providerview=") > 0) a = self.location.href.substring(0, self.location.href.lastIndexOf("&providerview="));
                else a = self.location.href;
                self.location.href = a + "&providerview=" + s.options[s.selectedIndex].value;
            }

            function openBrWindow(theURL, winName, features) {
                window.open(theURL, winName, features);
            }

            function setfocus() {
                this.focus();
                document.ADDAPPT.keyword.focus();
                document.ADDAPPT.keyword.select();
                addQuickPick();
            }

            function initResize() {
                window.addEventListener("resize", resizeTextMessage);
                resizeTextMessage();
            }

            /****
             *This function resizes the messageBox so that the overall browser window is filled.
             ****/
            function resizeTextMessage() {
                const messageBox = document.getElementById("ticklerMessage");
                //this formula checks for empty space at the bottom, less the 20 px that corresponds to the margin-bottom
                const newHeight = messageBox.offsetHeight + window.innerHeight - document.body.clientHeight - 20;
                //only resize if the new height will be greater than 50 pixels, the original default height.
                if (newHeight > 50) messageBox.style.height = newHeight + "px";
            }

            function validate(form) {
                validate(form, false);
            }

            function validate(form, writeToEncounter) {
                if (validateDemoNo(form) <%=caisiEnabled?"&& validateSelectedProgram()":""%>) {
                    if (writeToEncounter) {
                        form.action = "<%= request.getContextPath() %>/tickler/dbTicklerAdd.jsp?writeToEncounter=true";
                    } else {
                        form.action = "<%= request.getContextPath() %>/tickler/dbTicklerAdd.jsp?updateTicklerNav=true";
                    }
                    form.submit();
                }
            }

            function validateSelectedProgram() {
                if (document.serviceform.program_assigned_to.value === "none") {
                    document.getElementById("error").insertAdjacentText("beforeend", "<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.msgNoProgramSelected"/>");
                    document.getElementById("error").style.display = 'block';
                    return false;
                }
                return true;
            }

            function IsDate(value) {
                let dateWrapper = new Date(value);
                return !isNaN(dateWrapper.getDate());
            }

            function validateDemoNo() {
                if (document.serviceform.demographic_no.value == "") {
                    document.getElementById("error").insertAdjacentText("beforeend", "<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.msgInvalidDemographic"/>");
                    document.getElementById("error").style.display = 'block';
                    return false;
                } else {
                    if (document.serviceform.xml_appointment_date.value == "" || !IsDate(document.serviceform.xml_appointment_date.value)) {
                        document.getElementById("error").insertAdjacentText("beforeend", "<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.msgMissingDate"/>");
                        document.getElementById("error").style.display = 'block';
                        return false;
                    }
                        <% if (ca.openosp.openo.commn.IsPropertiesOn.isMultisitesEnable()) { %>
                    else if (document.serviceform.site.value == "none" || document.serviceform.site.value == "0") {
                        document.getElementById("error").insertAdjacentText("beforeend", "Must assign task to a providers.");
                        document.getElementById("error").style.display = 'block';
                        return false;
                    }
                        <% } %>
                    else {
                        return true;
                    }
                }
            }

            function refresh() {
                var u = self.location.href;
                if (u.lastIndexOf("view=1") > 0) {
                    self.location.href = u.substring(0, u.lastIndexOf("view=1")) + "view=0" + u.substring(eval(u.lastIndexOf("view=1") + 6));
                } else {
                    history.go(0);
                }
            }

            //-->
        </script>

        <link href="<%=request.getContextPath() %>/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet"
              type="text/css">
        <style media="all">
            .tickler-label {
                color: #003366;
                font-weight: bold;
            }

            table {
                width: 100%;
            }

            * {
                font-size: 12px !important;
            }

            .grid {
                display: grid;
                grid-template-columns: repeat(10, 1fr);
                grid-gap: 2px;
                width: 270px;
            }

            .grid a, .today-button {
                background-color: #E6E6FA;
                text-align: center;
                width: auto;
                height: auto;
                padding: 2px;
                margin: 1px;
                display: flex;
                justify-content: center;
                text-decoration: none;
                color: black;
                font-size: 11px !important;
                border-radius: 3px;
            }

            .grid a:hover, .today-button:hover {
                background-color: #EE82EE;
                color: white;
            }

            .today-button {
                width: 128px;
                cursor: pointer;
            }
        </style>
    </head>

    <body onLoad="setfocus();initResize()">
    <table>
        <tr style="background-color: black">
            <td class="table-condensed"
                style="text-align:left; padding:10px; font-weight: 900; height:40px;font-size:large;font-family:arial,sans-serif;color:white">
                Add <fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.msgTickler"/></td>
        </tr>
    </table>

    <div class="container-fluid well">
        <form name="ADDAPPT" method="post" action="<%= request.getContextPath() %>/appointment/appointmentcontrol.jsp">
            <table class="table-condensed">
                <tr>
                    <td colspan="2">
                        <div id="error" class="alert alert-error" style="display:none;"></div>
                    </td>
                </tr>
                <tr>
                    <td width="35%" class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formDemoName"/>:</td>
                    <td width="65%">

                        <div class="input-group">
                            <input type="text" class="form-control" name="keyword" placeholder="Search Demographic"
                                   size="25" value="<%=Encode.forHtmlAttribute(demoName)%>">
                            <span class="input-group-btn">
                            <input type="submit" name="Submit" class="btn btn-default"
                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.btnSearch"/>">
                        </span>
                        </div>

                    </td>
                </tr>
                <INPUT TYPE="hidden" name="orderby" VALUE="last_name">
                <%
                    String searchMode = request.getParameter("search_mode");
                    if (searchMode == null || searchMode.isEmpty()) {
                        searchMode = OscarProperties.getInstance().getProperty("default_search_mode", "search_name");
                    }
                %>
                <INPUT TYPE="hidden" name="search_mode" VALUE="<%=searchMode%>">
                <INPUT TYPE="hidden" name="originalpage" VALUE="<%= request.getContextPath() %>/tickler/ticklerAdd.jsp">
                <INPUT TYPE="hidden" name="limit1" VALUE="0">
                <INPUT TYPE="hidden" name="limit2" VALUE="5">
                <!--input type="hidden" name="displaymode" value="TicklerSearch" -->
                <INPUT TYPE="hidden" name="displaymode" VALUE="Search ">

                <% ChartNo = bFirstDisp ? "" : request.getParameter("chart_no") == null ? "" : request.getParameter("chart_no"); %>
                <INPUT TYPE="hidden" name="appointment_date" VALUE="2002-10-01" WIDTH="25" HEIGHT="20" border="0"
                       hspace="2">
                <INPUT TYPE="hidden" name="status" VALUE="t" WIDTH="25" HEIGHT="20" border="0" hspace="2">
                <INPUT TYPE="hidden" name="start_time" VALUE="10:45" WIDTH="25" HEIGHT="20" border="0"
                       onChange="checkTimeTypeIn(this)">
                <INPUT TYPE="hidden" name="type" VALUE="" WIDTH="25" HEIGHT="20" border="0" hspace="2">
                <INPUT TYPE="hidden" name="duration" VALUE="15" WIDTH="25" HEIGHT="20" border="0" hspace="2">
                <INPUT TYPE="hidden" name="end_time" VALUE="10:59" WIDTH="25" HEIGHT="20" border="0" hspace="2"
                       onChange="checkTimeTypeIn(this)">


                <input type="hidden" name="demographic_no" readonly value="" width="25" height="20" border="0"
                       hspace="2">
                <input type="hidden" name="location" tabindex="4" value="" width="25" height="20" border="0" hspace="2">
                <input type="hidden" name="resources" tabindex="5" value="" width="25" height="20" border="0"
                       hspace="2">
                <INPUT TYPE="hidden" name="user_id" readonly VALUE='oscardoc, doctor' WIDTH="25" HEIGHT="20" border="0"
                       hspace="2">
                <INPUT TYPE="hidden" name="dboperation" VALUE="search_demorecord">
                <INPUT TYPE="hidden" name="createdatetime" readonly VALUE="2002-10-1 17:53:50" WIDTH="25" HEIGHT="20"
                       border="0" hspace="2">
                <INPUT TYPE="hidden" name="provider_no" VALUE="115">
                <INPUT TYPE="hidden" name="creator" VALUE="oscardoc, doctor">
                <INPUT TYPE="hidden" name="remarks" VALUE="">
                <input type="hidden" name="parentAjaxId" value="<%=parentAjaxId%>"/>
                <input type="hidden" name="updateParent" value="<%=updateParent%>"/>

            </table>
        </form>
        <form name="serviceform" method="post">
            <table class="table-condensed">

                <input type="hidden" name="parentAjaxId" value="<%=parentAjaxId%>"/>
                <input type="hidden" name="updateParent" value="<%=updateParent%>"/>

                <tr>
                    <td width="35%" class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formChartNo"/>:</td>
                    <td width="65%"><span><INPUT TYPE="hidden" name="demographic_no"
                                                 VALUE="<%=bFirstDisp?"":request.getParameter("demographic_no").equals("")?"":request.getParameter("demographic_no")%>"><%=ChartNo%></span>
                    </td>
                </tr>

                <tr>
                    <td class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formServiceDate"/></td>
                    <td><input type="date" class="form-control" name="xml_appointment_date"
                               value="<%=xml_appointment_date%>">
                        <font color="#003366" size="1" face="Verdana, Arial, Helvetica, sans-serif">
                            <!-- Today button placed before the grid -->
                            <div id="todayButton" class="today-button" onclick="addTime(0, 'days')">Today</div>
                            <div id="quickPickDateOptions" class="grid">
                                <!-- Quick pick will be added here using JavaScript -->
                            </div>
                        </font></td>
                </tr>
                <tr>
                    <td class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.Priority"/>:</td>
                    <td>
                        <select name="priority" class="form-control">
                            <option value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.high"/>" <%=priority.equals("High")?"selected":""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.high"/>
                            <option value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.normal"/>" <%=priority.equals("Normal")?"selected":""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.normal"/>
                            <option value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.low"/>" <%=priority.equals("Low")?"selected":""%>><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerMain.priority.low"/>
                        </select>
                    </td>
                </tr>

                <tr>
                    <td class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.assignTaskTo"/>:</td>
                    <td>
                        <% if (ca.openosp.openo.commn.IsPropertiesOn.isMultisitesEnable()) { // multisite start ==========================================
                            SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                            List<Site> sites = siteDao.getActiveSitesByProviderNo(user_no);
                            String appNo = (String) session.getAttribute("cur_appointment_no");
                            String location = null;
                            if (appNo != null) {
                                Appointment a = appointmentDao.find(Integer.parseInt(appNo));
                                if (a != null) {
                                    location = a.getLocation();
                                }

                            }
                        %>
                        <script>
                            var _providers = [];

                            <%
                            String taskToName = "";

                            if(defaultTaskAssignee.equals("mrp")) {
                                taskToName = "Preference set to MRP, attach a patient.";
                            }
                            if(!taskTo.isEmpty()) {
                                taskToName = providerDao.getProviderNameLastFirst(taskTo);
                            }
                            Site site = null;
                            for (int i=0; i<sites.size(); i++) {
                                Set<Provider> siteProviders = sites.get(i).getProviders();
                                List<Provider>  siteProvidersList = new ArrayList<Provider> (siteProviders);
                                 Collections.sort(siteProvidersList,(new Provider()).ComparatorName());%>
                            _providers["<%= sites.get(i).getName() %>"] = "<% Iterator<Provider> iter = siteProvidersList.iterator();
	while (iter.hasNext()) {
		Provider p=iter.next();
		if ("1".equals(p.getStatus())) {
	%><option value='<%= p.getProviderNo() %>'><%= p.getLastName() %>, <%= p.getFirstName() %></option><% }} %>";
                            <%
                                } %>

                            function changeSite(sel) {
                                sel.form.task_assigned_to.innerHTML = sel.value == "none" ? "" : _providers[sel.value];
                                sel.style.backgroundColor = sel.options[sel.selectedIndex].style.backgroundColor;
                            }
                        </script>

                        <div id="selectWrapper">
                            <select id="site" class="form-control" name="site" onchange="changeSite(this)">
                                <option value="none" style="background-color: white">---select clinic---</option>
                                <%
                                    for (int i = 0; i < sites.size(); i++) {
                                %>
                                <option value="<%=sites.get(i).getName()%>"
                                        style="background-color:<%=sites.get(i).getBgColor()%>"><%=sites.get(i).getName()%>
                                </option>
                                <% } %>
                            </select>

                            <select name="task_assigned_to" id="task_assigned_to" class="form-control"></select>

                            <h4 id="preferenceLink" style="display:none"><small><a href="#" onClick="toggleWrappers()">[preference]</a></small>
                            </h4>
                        </div>

                        <div id="nameWrapper" style="display:none">
                            <h4><%=taskToName%> <small><a href="#" onClick="toggleWrappers()">[change]</a></small></h4>
                            <input type="hidden" id="taskToBin" value="<%=taskTo%>">
                            <input type="hidden" id="taskToNameBin" value="<%=taskToName%>">
                        </div>
                        <script>
                            document.getElementById("site").value = '<%= site==null?"none":site.getSiteId() %>';
                            changeSite(document.getElementById("site"));
                        </script>

                        <% if (prop != null) {%>
                        <script>
                            //prop exists so hide selectWrapper
                            document.getElementById("selectWrapper").style.display = "none";
                            document.getElementById("nameWrapper").style.display = "block";
                            document.getElementById("preferenceLink").style.display = "inline-block";

                            var taskToValue = document.getElementById("taskToBin").value;
                            var taskToName = document.getElementById('taskToNameBin').value;

                            function toggleWrappers() {
                                if (document.getElementById("selectWrapper").style.display == "none") {
                                    document.getElementById("selectWrapper").style.display = "block";
                                    document.getElementById("nameWrapper").style.display = "none";
                                } else {
                                    document.getElementById("selectWrapper").style.display = "none";
                                    document.getElementById("nameWrapper").style.display = "block";
                                }
                            }

                            _providers.push("<option value=\"" + taskToValue + "\" selected>" + taskToName + "</option>");

                            var newItemKey = _providers.length - 1;

                            var selSite = document.getElementById('site');
                            var optSite = document.createElement('option');
                            optSite.appendChild(document.createTextNode("** preference **"));
                            optSite.value = newItemKey;
                            optSite.setAttribute('selected', 'selected');
                            selSite.appendChild(optSite);
                            changeSite(selSite);
                        </script>
                        <%}%>

                        <% // multisite end ==========================================
                        } else {
                        %>

                        <select name="task_assigned_to" class="form-control">
                            <% String proFirst = "";
                                String proLast = "";
                                String proOHIP = "";

                                for (Provider p : providerDao.getActiveProviders()) {

                                    proFirst = p.getFirstName();
                                    proLast = p.getLastName();
                                    proOHIP = p.getProviderNo();

                            %>
                            <option value="<%=proOHIP%>" <%=taskTo.equals(proOHIP) ? "selected" : ""%>><%=Encode.forHtmlContent(proLast)%>
                                , <%=Encode.forHtmlContent(proFirst)%>
                            </option>
                            <%
                                }
                            %>
                        </select>
                        <% } %>

                        <input type="hidden" name="docType" value="<%=request.getParameter("docType")%>"/>
                        <input type="hidden" name="docId" value="<%=request.getParameter("docId")%>"/>
                    </td>
                </tr>
                <tr>
                    <td class="tickler-label"><fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.formReminder"/>:</td>
                    <td><textarea name="ticklerMessage" id="ticklerMessage" class="form-control"></textarea></td>
                </tr>
                <INPUT TYPE="hidden" name="user_no" VALUE="<%=user_no%>">
                <input type="hidden" name="writeToEncounter" value="<%=writeToEncounter%>"/>
                <tr>

                    <td><input type="button" name="Button" class="btn btn-primary"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.btnSubmit"/>"
                               onClick="event.preventDefault();validate(this.form);">

                        <input type="button" name="Button" class="btn btn-danger"
                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="tickler.ticklerAdd.btnCancel"/>" onClick="window.close()">
                    </td>
                </tr>

            </table>
        </form>
    </div>
    </body>
</html>