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

<%@ page import="java.math.*, java.util.*, java.io.*, java.sql.*, io.github.carlos_emr.*, java.net.*,io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.MyGroupDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.MyGroup" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ReportProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ReportProvider" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%
    MyGroupDao myGroupDao = SpringUtils.getBean(MyGroupDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    ReportProviderDao reportProviderDao = SpringUtils.getBean(ReportProviderDao.class);
%>
<%

    GregorianCalendar now = new GregorianCalendar();
    int curYear = now.get(Calendar.YEAR);
    int curMonth = (now.get(Calendar.MONTH) + 1);
    int curDay = now.get(Calendar.DAY_OF_MONTH);

    String nowDate = String.valueOf(curYear) + "-" + String.valueOf(curMonth) + "-" + String.valueOf(curDay);
    int dob_yy = 0, dob_dd = 0, dob_mm = 0, age = 0;
    String demo_no = "", demo_sex = "", provider_no = "", roster = "", patient_status = "", status = "";
    String demographic_dob = "1800";
    String action = request.getParameter("action");
    String last_name = "", first_name = "", mygroup = "";
%>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<html>
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.manageProvider.title"/></title>
    </head>
    <body>
    <div class="container">
    <div class="searchBox">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" class="page-header-icon">
                    <path d="M7 14s-1 0-1-1 1-4 5-4 5 3 5 4-1 1-1 1zm4-6a3 3 0 1 0 0-6 3 3 0 0 0 0 6m-5.784 6A2.24 2.24 0 0 1 5 13c0-1.355.68-2.75 1.936-3.72A6.3 6.3 0 0 0 5 9c-4 0-5 3-5 4s1 1 1 1zM4.5 8a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5"/>
                </svg>
                &nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.manageProvider.msgManageProvider"/>
                <span class="text-info"><%=Encode.forHtml(action != null ? action.toUpperCase() : "")%></span>
            </h4>
            </div>

        <form name="form1" action="dbManageProvider.jsp" method="post">
            <table class="table table-hover table-condensed table-striped">
                <thead>
                <tr>
                    <th width="40%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.manageProvider.msgTeam"/></th>
                    <th width="50%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.manageProvider.msgProviderName"/></th>
                    <th width="10%"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.manageProvider.msgCheck"/></th>
                </tr>
                </thead>
                <tbody>
                <%
                    int count1 = 0;

                    for (String myGroup : myGroupDao.getGroups()) {
                        for (MyGroup mg : myGroupDao.getGroupByGroupNo(myGroup)) {
                            Provider p = providerDao.getProvider(mg.getId().getProviderNo());
                            status = "";
                            if (p != null) {
                                for (ReportProvider rp : reportProviderDao.findByProviderNoTeamAndAction(p.getProviderNo(), mg.getId().getMyGroupNo(), action)) {
                                    status = rp.getStatus();
                                }
                            } else {
                                continue;
                            }

                %>
                <tr>
                    <td><%=Encode.forHtml(mg.getId().getMyGroupNo())%></td>
                    <td><%=Encode.forHtml(p.getLastName() + ", " + p.getFirstName())%></td>
                    <td>
                        <input type="checkbox"
                               name="provider<%=count1%>"
                               value="<%=Encode.forHtmlAttribute(p.getProviderNo() + "|" + mg.getId().getMyGroupNo())%>"
                                <%=status.equals("A")?"checked":""%>>
                    </td>
                </tr>
                <%
                            count1 = count1 + 1;
                        }

                    }

                %>
                </tbody>
            </table>

            <input type="hidden" name="submit" value="Submit">
            <input type="hidden" name="action" value="<%=Encode.forHtmlAttribute(action != null ? action : "")%>">
            <input type="hidden" name="count" value="<%=count1%>">
            <input class="btn btn-sm btn-primary" type="submit" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.manageProvider.btnSubmit"/>">
        </form>

    </div>
    </div>
    </body>
</html>
