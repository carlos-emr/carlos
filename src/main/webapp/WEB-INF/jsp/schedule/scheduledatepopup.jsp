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

<%! boolean bMultisites = io.github.carlos_emr.carlos.commn.IsPropertiesOn.isMultisitesEnable(); %>

<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*, java.text.*, java.lang.*" errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ScheduleTemplate" %>
<%
    ScheduleTemplateDao scheduleTemplateDao = SpringUtils.getBean(ScheduleTemplateDao.class);
%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>



<jsp:useBean id="scheduleDateBean" class="java.util.Hashtable" scope="session"/>
<%
    String[] bgColors = null;
    String year = request.getParameter("year");
    String month = MyDateFormat.getDigitalXX(Integer.parseInt(request.getParameter("month")));
    String day = MyDateFormat.getDigitalXX(Integer.parseInt(request.getParameter("day")));

    String available = "checked", strHour = "", strReason = "value=''", strCreator = "Me";
    ResourceBundle scheduleBundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
    String submitSave = scheduleBundle.getString("schedule.scheduledatepopup.btnSave");
    String submitDelete = scheduleBundle.getString("schedule.scheduledatepopup.btnDelete");
    strCreator = scheduleBundle.getString("schedule.scheduledatepopup.me");
    HScheduleDate aHScheduleDate = (HScheduleDate) scheduleDateBean.get(year + "-" + month + "-" + day);
    if (aHScheduleDate != null) {
        available = aHScheduleDate.available.compareTo("1") == 0 ? "checked" : "";
        strHour = aHScheduleDate.hour;
        strReason = aHScheduleDate.reason;
        strCreator = aHScheduleDate.creator;
    }

%>

<%@page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="schedule.scheduledatepopup.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css"/>

        <script language="JavaScript">
            <!--
            function setfocus() {
                this.focus();
                document.schedule.hour.focus();
                //document.schedule.hour.select();
            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            //-->
        </script>
    </head>
    <body bgcolor="ivory" bgproperties="fixed" onLoad="setfocus()"
          topmargin="0" leftmargin="0" rightmargin="0">
    <form method="post" name="schedule" action="${pageContext.request.contextPath}/schedule/DateSave">

        <table border="0" width="100%">
            <tr>
                <td width="50" bgcolor="#009966">&nbsp;</td>
                <td>
                    <table width="95%" border="0" cellspacing="0" cellpadding="5">
                        <tr>
                            <td bgcolor="#CCFFCC">
                                <p align="right"><fmt:message key="schedule.scheduledatepopup.formDate"/>:</p>
                            </td>
                            <td bgcolor="#CCFFCC"><carlos:encode value='<%= year %>' context="html"/>-<carlos:encode value='<%= month %>' context="html"/>-<carlos:encode value='<%= day %>' context="html"/>
                            </td>
                            <input type="hidden" name="date"
                                   value="<carlos:encode value='<%= year %>' context="htmlAttribute"/>-<carlos:encode value='<%= month %>' context="htmlAttribute"/>-<carlos:encode value='<%= day %>' context="htmlAttribute"/>">
                        </tr>
                        <tr>
                            <td>
                                <div align="right"><fmt:message key="schedule.scheduledatepopup.formAvailable"/>:
                                </div>
                            </td>
                            <td><input type="radio" name="available" value="1"
                                    <%=available.equals("checked")?"checked":""%>> <fmt:message key="schedule.scheduledatepopup.formAvailableYes"/> <input
                                    type="radio" name="available" value="0"
                                    <%=available.equals("checked")?"":"checked"%>> <fmt:message key="schedule.scheduledatepopup.formAvailableNo"/></td>
                        </tr>
                        <tr>
                            <td>
                                <div align="right"><fmt:message key="schedule.scheduledatepopup.formTemplate"/>:
                                </div>
                            </td>
                            <td><select
                                    name="hour">
                                <%

                                    for (ScheduleTemplate st : scheduleTemplateDao.findByProviderNo("Public")) {

                                %>
                                <option value="<carlos:encode value='<%= st.getId().getName() %>' context="htmlAttribute"/>"
                                        <%=strHour.equals(st.getId().getName()) ? "selected" : ""%>><carlos:encode value='<%= st.getId().getName() + " |" + st.getSummary() %>' context="html"/>
                                </option>
                                <% }
                                    for (ScheduleTemplate st : scheduleTemplateDao.findByProviderNo(request.getParameter("provider_no"))) {

                                %>
                                <option value="<carlos:encode value='<%= st.getId().getName() %>' context="htmlAttribute"/>"
                                        <%=st.getId().getName().equals(strHour) ? "selected" : ""%>><carlos:encode value='<%= st.getId().getName() + " |" + st.getSummary() %>' context="html"/>
                                </option>
                                <% } %>
                            </select></td>
                        </tr>
                        <%
                            CarlosProperties props = CarlosProperties.getInstance();
                            boolean bMoreAddr = bMultisites
                                    ? true
                                    : props.getProperty("scheduleSiteID", "").equals("") ? false : true;
                            String[] siteList;
                            if (bMultisites) {
                                //multisite starts =====================
                                SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                                List<Site> sites = siteDao.getActiveSitesByProviderNo(request.getParameter("provider_no"));
                                siteList = new String[sites.size() + 1];
                                bgColors = new String[sites.size() + 1];
                                for (int i = 0; i < sites.size(); i++) {
                                    siteList[i] = sites.get(i).getName();
                                    bgColors[i] = sites.get(i).getBgColor();
                                }
                                siteList[sites.size()] = "NONE";
                                bgColors[sites.size()] = "white";
                                //multisite ends =====================
                            } else {
                                siteList = props.getProperty("scheduleSiteID", "").split("\\|");
                            }

                            if (bMoreAddr) {
                        %>
                        <tr>
                            <td>
                                <div align="right"><fmt:message key="schedule.scheduledatepopup.formLocation"/>:</div>
                            </td>
                            <td><select id="reason" name="reason"
                                        onchange='this.style.backgroundColor=this.options[this.selectedIndex].style.backgroundColor'>
                                <% for (int i = 0; i < siteList.length; i++) { %>
                                <option value="<carlos:encode value='<%= siteList[i] %>' context="htmlAttribute"/>" <%=(bMultisites ? " style='background-color:" + SafeEncode.forCssString(bgColors[i]) + "'" : "")%>
                                        <%=strReason.equals(siteList[i]) ? "selected" : ""%>><b><carlos:encode value='<%= siteList[i] %>' context="html"/>
                                </b></option>
                                <% } %>
                            </select></td>
                        </tr>
                        <% } %>
                        <!--  input type="hidden" name="reason" <%--=strReason--%> -->
                        <tr>
                            <td>
                                <div align="right"><fmt:message key="schedule.scheduledatepopup.formCreator"/>:
                                </div>
                            </td>
                            <td><carlos:encode value='<%= strCreator %>' context="html"/>
                            </td>
                        </tr>
                    </table>
                    <% if (bMultisites)
                        out.println("<script>var _r=document.getElementById('reason'); _r.style.backgroundColor=_r.options[_r.selectedIndex].style.backgroundColor;</script>");
                    %>
                    <table width="100%" border="0" cellspacing="0" cellpadding="0">
                        <tr>
                            <td>&nbsp;</td>
                        </tr>
                        <tr>
                            <td bgcolor="#CCFFCC">
                                <div align="right"><input type="hidden" name="Submit" value="">
                                    <input type="hidden" name="provider_no"
                                           value="<carlos:encode value='<%= StringUtils.noNull(request.getParameter("provider_no")) %>' context="htmlAttribute"/>"> <input
                                            type="button"
                                            value='<fmt:message key="schedule.scheduledatepopup.btnSave"/>'
                                            onclick="document.forms['schedule'].Submit.value='<%= submitSave %>'; document.forms['schedule'].submit();">
                                    <input type="button" name="Button"
                                           value='<fmt:message key="schedule.scheduledatepopup.btnCancel"/>'
                                           onClick="window.close()"> <input type="button"
                                                                            value='<fmt:message key="schedule.scheduledatepopup.btnDelete"/>'
                                                                            onclick="document.forms['schedule'].Submit.value='<%= submitDelete %>'; document.forms['schedule'].submit();">
                                </div>
                            </td>
                        </tr>
                    </table>
                    <br>
                </td>
            </tr>
        </table>

    </form>
    </body>
</html>
