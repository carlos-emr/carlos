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
<%--
    Purpose: Entry point for schedule template management. Allows providers and scheduling
             staff to select a provider and navigate to schedule template creation,
             application, or holiday configuration.

    Features:
    - Provider selection dropdown (filtered by _admin.schedule.curprovider_only privilege)
    - Links to holiday settings, template code settings (admin-only)
    - Link to schedule template editor for a selected provider
    - Site/team access awareness (hides admin links when privacy flags are enabled)

    Parameters (session):
    - user      (String) — logged-in provider number
    - userrole  (String) — role name for security checks

    @since 2001-02-01
--%>
<!DOCTYPE html>
<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.text.*, java.lang.*" errorPage="/errorpage.jsp" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>


<%
    String curProvider_no = (String) session.getAttribute("user");
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>

<%
    GregorianCalendar now = new GregorianCalendar();
    int year = now.get(Calendar.YEAR);
    int month = (now.get(Calendar.MONTH) + 1);
    int day = now.get(Calendar.DAY_OF_MONTH);
%>

<%
    boolean isSiteAccessPrivacy = false;
    boolean isTeamAccessPrivacy = false;
    boolean grantOnlyCurProviderScheduleData = false;
%>
<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%
        isSiteAccessPrivacy = true;
    %>
</security:oscarSec>
<security:oscarSec objectName="_team_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
    <%
        isTeamAccessPrivacy = true;
    %>
</security:oscarSec>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin.schedule.curprovider_only" rights="r" reverse="<%=false%>">
    <%
        grantOnlyCurProviderScheduleData = true;
    %>
</security:oscarSec>

<html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title><fmt:message key="schedule.scheduletemplatesetting.title"/></title>
        <link href="${pageContext.request.contextPath}/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <script>
            function setfocus() {
                this.focus();
            }

            function selectprovider(s) {
                // Use form submission instead of direct URL construction to prevent
                // DOM text from flowing into a navigation sink (js/xss-through-dom).
                var form = document.createElement('form');
                form.method = 'get';
                form.action = 'scheduletemplateapplying.jsp';
                var fNo = document.createElement('input');
                fNo.type = 'hidden';
                fNo.name = 'provider_no';
                fNo.value = s.options[s.selectedIndex].value;
                form.appendChild(fNo);
                var fName = document.createElement('input');
                fName.type = 'hidden';
                fName.name = 'provider_name';
                fName.value = s.options[s.selectedIndex].text;
                form.appendChild(fName);
                document.body.appendChild(form);
                form.submit();
            }

            function go() {
                // Use a named popup window with form submission to prevent DOM text
                // from flowing into window.open() URL (js/xss-through-dom).
                var popupName = 'attachment';
                var popupProps = 'height=390,width=700,location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes';
                window.open('', popupName, popupProps);
                var s = document.schedule.providerid;
                var form = document.createElement('form');
                form.method = 'get';
                form.action = 'scheduleedittemplate.jsp';
                form.target = popupName;
                var fId = document.createElement('input');
                fId.type = 'hidden';
                fId.name = 'providerid';
                fId.value = s.value;
                form.appendChild(fId);
                var fName = document.createElement('input');
                fName.type = 'hidden';
                fName.name = 'providername';
                fName.value = s.options[s.selectedIndex].text;
                form.appendChild(fName);
                document.body.appendChild(form);
                form.submit();
                document.body.removeChild(form);
            }
        </script>
    </head>
    <body onload="setfocus()">
    <div class="container-fluid py-3">

        <h4><fmt:message key="schedule.scheduletemplatesetting.msgMainLabel"/></h4>

        <div class="alert alert-info">
            <fmt:message key="schedule.scheduletemplatesetting.msgStepOne"/>
            <br>
            <fmt:message key="schedule.scheduletemplatesetting.msgStepTwo"/>
        </div>

        <form method="post" name="schedule" action="schedulecreatedate.jsp">
        <div class="card card-body bg-body-tertiary">

            <div class="mb-3">
                <label class="form-label"><fmt:message key="schedule.scheduletemplatesetting.formSelectProvider"/>:</label>
                <select name="provider_no" class="form-select" onchange="selectprovider(this)">
                    <option value=""><fmt:message key="schedule.scheduletemplatesetting.msgNoProvider"/></option>
                    <%
                        ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
                        List<Provider> providers = null;

                        if (grantOnlyCurProviderScheduleData) {
                            // Only allow the user to manipulate their own schedule
                            providers = new ArrayList<Provider>();
                            Provider curProvider = providerDao.getProvider(curProvider_no);
                            if (curProvider != null) {
                                providers.add(curProvider);
                            }
                        } else {
                            providers = providerDao.getActiveProviders();
                        }
                        //TODO: filter by site/team if necessary

                        for (Provider p : providers) {
                    %>
                    <option value="<%=Encode.forHtmlAttribute(p.getProviderNo())%>"><%=Encode.forHtml(p.getFormattedName())%></option>
                    <% } %>
                </select>
            </div>

            <p><fmt:message key="schedule.scheduletemplatesetting.formOrDo"/>:</p>

            <div class="d-flex flex-column gap-2">
                <%if (!(isSiteAccessPrivacy || isTeamAccessPrivacy || grantOnlyCurProviderScheduleData)) {%>
                <div class="p-2 bg-success-subtle rounded">
                    <a href="#"
                       onclick="popupPage(440,530,'scheduleholidaysetting.jsp?year=<%=year%>&month=<%=month%>&day=<%=day%>')"
                       title="<fmt:message key="schedule.scheduletemplatesetting.msgHolidaySettingTip"/>">
                        <fmt:message key="schedule.scheduletemplatesetting.btnHolidaySetting"/>
                    </a>
                </div>
                <div class="p-2 bg-success-subtle rounded">
                    <a href="#" onclick="popupPage(600,700,'scheduletemplatecodesetting.jsp')">
                        <fmt:message key="schedule.scheduletemplatesetting.btnTemplateCodeSetting"/>
                    </a>
                </div>
                <%}%>
                <div class="p-2 bg-success-subtle rounded d-flex align-items-center gap-2 flex-wrap">
                    <a href="#" onclick="go()">
                        <fmt:message key="schedule.scheduletemplatesetting.btnTemplateSetting"/>
                    </a>
                    <span><fmt:message key="schedule.scheduletemplatesetting.msgForProvider"/>:</span>
                    <select name="providerid" class="form-select form-select-sm" style="width: auto;">
                        <option value="Public"><fmt:message key="schedule.scheduletemplatesetting.msgPublic"/></option>
                        <%
                            for (Provider p : providers) {
                        %>
                        <option value="<%=Encode.forHtmlAttribute(p.getProviderNo())%>"><%=Encode.forHtml(p.getFormattedName())%></option>
                        <% } %>
                    </select>
                </div>
            </div>

        </div>
        </form>

    </div>
    </body>
</html>
