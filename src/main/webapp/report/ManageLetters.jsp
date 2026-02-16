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

<%@page
        import="io.github.carlos_emr.carlos.demographic.data.*,java.util.*,io.github.carlos_emr.carlos.prevention.*,io.github.carlos_emr.carlos.providers.data.*,io.github.carlos_emr.carlos.util.*,io.github.carlos_emr.carlos.report.data.*,io.github.carlos_emr.carlos.prevention.pageUtil.*,java.net.*,io.github.carlos_emr.carlos.eform.*,org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.ManageLetters" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>

<%
    String demographic_no = request.getParameter("demographic_no");
    String[] demos = request.getParameterValues("demo");
%>

<html>
    <head>
        <title>Manage Letters</title>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/Oscar.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/prototype.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/share/calendar/lang/<fmt:setBundle basename="oscarResources"/><fmt:message key="global.javascript.calendar"/>"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/share/calendar/calendar-setup.js"></script>
        <link href="<%= request.getContextPath() %>/library/bootstrap/3.0.0/css/bootstrap.css" rel="stylesheet" type="text/css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/searchBox.css">
        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/calendar/calendar.css" title="win2k-cold-1">

        <script type="text/javascript">

            function showHideItem(id) {
                if (document.getElementById(id).style.display == 'none')
                    document.getElementById(id).style.display = '';
                else
                    document.getElementById(id).style.display = 'none';
            }

            function showItem(id) {
                document.getElementById(id).style.display = '';
            }

            function hideItem(id) {
                document.getElementById(id).style.display = 'none';
            }

            function showHideNextDate(id, nextDate, neverWarn) {
                if (document.getElementById(id).style.display == 'none') {
                    showItem(id);
                } else {
                    hideItem(id);
                    document.getElementById(nextDate).value = "";
                    document.getElementById(neverWarn).checked = false;
                }
            }

            function disableifchecked(ele, nextDate) {
                if (ele.checked == true) {
                    document.getElementById(nextDate).disabled = true;
                } else {
                    document.getElementById(nextDate).disabled = false;
                }
            }

            function completedProcedure(idval, followUpType, procedure, demographic) {
                var comment = prompt('Are you sure you want to added this to patients record \n\nAdd Comment Below ', '');
                if (comment != null) {
                    var params = "id=" + idval + "&followupType=" + followUpType + "&followupValue=" + procedure + "&demos=" + demographic + "&message=" + comment;
                    var url = "<%=request.getContextPath()%>/oscarMeasurement/AddShortMeasurement.do";

                    new Ajax.Request(url, {
                        method: 'get',
                        parameters: params,
                        asynchronous: true,
                        onComplete: followUp
                    });
                }
                return false;
            }

            function followUp(origRequest) {
                var hash = origRequest.responseText.parseQuery();
                var lastFollowupTD = $(hash['id'] + 'lastFollowup');
                var nextProcedureTD = $(hash['id'] + 'nextSuggestedProcedure');
                nextProcedureTD.textContent = "----";
                lastFollowupTD.textContent = hash['followupValue'] + " " + hash['Date'];
            }

        </script>

        <style type="text/css" media="print">
            .searchBox { display: none; }
        </style>
    </head>

    <body>
    <div class="container">
    <div class="searchBox">

        <div style="background:#f5f5f5; padding:8px 15px; border-bottom:1px solid #ddd; margin-bottom:10px;">
            <h4 style="margin:0; font-size:18px;">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" fill="currentColor" viewBox="0 0 16 16" style="vertical-align:text-bottom">
                    <path d="M1 2.5A1.5 1.5 0 0 1 2.5 1h3A1.5 1.5 0 0 1 7 2.5v3A1.5 1.5 0 0 1 5.5 7h-3A1.5 1.5 0 0 1 1 5.5zM2.5 2a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 1h3A1.5 1.5 0 0 1 15 2.5v3A1.5 1.5 0 0 1 13.5 7h-3A1.5 1.5 0 0 1 9 5.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zM1 10.5A1.5 1.5 0 0 1 2.5 9h3A1.5 1.5 0 0 1 7 10.5v3A1.5 1.5 0 0 1 5.5 15h-3A1.5 1.5 0 0 1 1 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5zm6.5.5A1.5 1.5 0 0 1 10.5 9h3a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-3A1.5 1.5 0 0 1 9 13.5zm1.5-.5a.5.5 0 0 0-.5.5v3a.5.5 0 0 0 .5.5h3a.5.5 0 0 0 .5-.5v-3a.5.5 0 0 0-.5-.5z"/>
                </svg>
                &nbsp;Manage Letters
            </h4>
        </div>

        <form method="post" action="${pageContext.request.contextPath}/report/ManageLetters.do" enctype="multipart/form-data">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
            <input type="hidden" name="goto" value="<%=Encode.forHtmlAttribute(request.getParameter("goto"))%>"/>
            <table class="table table-condensed" style="font-size:13px;">
                <tr>
                    <td style="width:120px; font-weight:bold;">Select Letter:</td>
                    <td>
                        <input type="file" name="reportFile" value="upload"/>
                        <span title="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.uploadWarningBody"/>"
                              style="vertical-align:middle; cursor:pointer;">
                            <img border="0" src="<%= request.getContextPath() %>/images/icon_alertsml.gif"/>
                        </span>
                    </td>
                </tr>
                <tr>
                    <td style="font-weight:bold;">Report Name:</td>
                    <td><input type="text" name="reportName" class="form-control input-sm" style="width:auto; display:inline-block;"/></td>
                </tr>
            </table>
            <div style="padding:5px 0 15px 0;">
                <input type="submit" value="Upload" class="btn btn-sm btn-primary"/>
            </div>
        </form>

        <%
            ManageLetters mLetter = new ManageLetters();
            ArrayList list = mLetter.getActiveReportList();

            if (list.size() > 0) {
        %>
        <table class="table table-condensed table-striped" style="font-size:13px;">
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Provider</th>
                    <th>Report Name</th>
                    <th>File</th>
                    <th>Date</th>
                    <th></th>
                </tr>
            </thead>
            <tbody>
                <% for (int i = 0; i < list.size(); i++) {
                    Hashtable h = (Hashtable) list.get(i);
                %>
                <tr>
                    <td><%= Encode.forHtml(String.valueOf(h.get("ID"))) %></td>
                    <td><%= Encode.forHtml(String.valueOf(h.get("provider_no"))) %></td>
                    <td><%= Encode.forHtml(String.valueOf(h.get("report_name"))) %></td>
                    <td><a href="<%= request.getContextPath() %>/report/DownloadLetter.do?reportID=<%= Encode.forHtmlAttribute(String.valueOf(h.get("ID")))%>"><%= Encode.forHtml(String.valueOf(h.get("file_name")))%></a></td>
                    <td><%= Encode.forHtml(String.valueOf(h.get("date_time"))) %></td>
                    <td>
                        <form method="POST" action="<%= request.getContextPath() %>/report/DeleteLetter.do" style="display:inline; margin:0;">
                            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
                            <input type="hidden" name="reportID" value="<%= Encode.forHtmlAttribute(String.valueOf(h.get("ID"))) %>"/>
                            <button type="submit" class="btn btn-xs btn-danger">Delete</button>
                        </form>
                    </td>
                </tr>
                <%}%>
            </tbody>
        </table>
        <%}%>

    </div>
    </div>
    </body>
</html>

<%!
    String getUrlParamList(ArrayList list, String paramName) {
        String queryStr = "";
        for (int i = 0; i < list.size(); i++) {
            String demo = (String) list.get(i);
            if (i == 0) {
                queryStr += paramName + "=" + demo;
            } else {
                queryStr += "&" + paramName + "=" + demo;
            }
        }
        return queryStr;
    }
%>
