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

<%@ page import="java.util.*" %>
<%@ page import="java.sql.*" %>
<%@ page import="io.github.carlos_emr.carlos.login.*, io.github.carlos_emr.carlos.db.*, io.github.carlos_emr.MyDateFormat" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>

<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%
    String tdTitleColor = "#CCCC99";
    String tdSubtitleColor = "#CCFF99";
    String tdInterlColor = "white";
    String startDate = request.getParameter("startDate");
    String endDate = request.getParameter("endDate");
    Properties prop = null;
    // Provider list and name map are populated by LogReport2Action using parameterized SQL.
    @SuppressWarnings("unchecked")
    java.util.Vector<Properties> vecProvider = (java.util.Vector<Properties>) request.getAttribute("vecProvider");
    Properties propName = (Properties) request.getAttribute("propName");
    if (vecProvider == null) vecProvider = new java.util.Vector<>();
    if (propName == null) propName = new Properties();
%>

<%@page import="io.github.carlos_emr.Misc" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>


        <script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

        <script type="text/javascript" src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>

        <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">

        <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet" type="text/css">


        <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
        <title><fmt:message key="admin.logReport.title"/></title>
        <script language="JavaScript">

            <!--
            function setfocus() {
                this.focus();
                //  document.titlesearch.keyword.select();
            }

            function onSub() {
                if (document.myform.startDate.value == "" || document.myform.endDate.value == "") {
                    alert("<fmt:message key='admin.logReport.alertDates'/>");
                    return false;
                } else {
                    return true;
                }
            }

            //-->
        </script>

        <style>

            label {
                margin-top: 6px;
                margin-bottom: 0px;
            }
        </style>

    </head>
    <body>
    <form name="myform" class="card card-body bg-body-tertiary" action="${pageContext.request.contextPath}/admin/LogReport" method="POST" onSubmit="return(onSub());">
        <fieldset>
            <h3><fmt:message key="admin.logReport.heading"/> <small><fmt:message key="admin.logReport.subheading"/></small></h3>

            <div class="row">
            <div class="col-md-4">
                <label><fmt:message key="admin.logReport.provider"/>:</label>

                <select name="providerNo">
                    <option value="*"><fmt:message key="admin.logReport.all"/></option>
                    <%
                        for (int i = 0; i < vecProvider.size(); i++) {
                            String prov = ((Properties) vecProvider.get(i)).getProperty("providerNo", "");
                            String selected = request.getParameter("providerNo");
                    %>
                    <option value="<carlos:encode value='<%= prov %>' context="htmlAttribute"/>"
                            <% if ((selected != null) && (selected.equals(prov))) { %> selected
                            <% } %>><carlos:encode value='<%= ((Properties) vecProvider.get(i)).getProperty("name", "") %>' context="html"/>
                    </option>
                    <%
                        }
                    %>
                </select>
            </div>

            <div class="col-md-4">
                <label><fmt:message key="admin.logReport.contentType"/>:</label>
                <select name="content">
                    <option value="admin"><fmt:message key="admin.logReport.content.admin"/></option>
                    <option value="login"><fmt:message key="admin.logReport.content.login"/></option>
                </select>
            </div>

            <div class="col-md-4">
                <label><fmt:message key="admin.logReport.startDate"/>:</label>
                <div class="input-group">
                    <input type="text" name="startDate" id="startDate1" value="<carlos:encode value='<%= startDate!=null?startDate:"" %>' context="htmlAttribute"/>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>

            <div class="col-md-4">
                <label><fmt:message key="admin.logReport.endDate"/>:</label>
                <div class="input-group">
                    <input type="text" name="endDate" id="endDate1" value="<carlos:encode value='<%= endDate!=null?endDate:"" %>' context="htmlAttribute"/>"
                           pattern="^\d{4}-((0\d)|(1[012]))-(([012]\d)|3[01])$" autocomplete="off"/>
                    <span class="input-group-text"><i class="fa-solid fa-calendar"></i></span>
                </div>
            </div>


            <div class="col-md-8" style="padding-top:10px;">
                <input class="btn btn-primary" type="submit" name="submit" value="<fmt:message key='admin.logReport.runReport'/>">
            </div>

            </div><!--row-->
        </fieldset>
    </form>
    <%
        String dateError = (String) request.getAttribute("dateError");
        if (dateError != null && !dateError.isEmpty()) {
    %>
    <div class="alert alert-danger" role="alert"><carlos:encode value='<%= dateError %>' context="html"/></div>
    <%
        }
        out.flush();
        //String startDate = "";
        //String endDate = "";
        Vector<Properties> vec = (Vector<Properties>) request.getAttribute("vec");
        Boolean bAllAttr = (Boolean) request.getAttribute("bAll");
        boolean bAll = bAllAttr != null && bAllAttr;
        String providerNo = (String) request.getAttribute("providerNo");
        if (providerNo == null) providerNo = "";
        if (vec == null) vec = new Vector<Properties>();
    %>
    <fmt:message var="allProviderLabel" key="admin.logReport.all"/>
    <%
        String allProviderLabel = (String) pageContext.findAttribute("allProviderLabel");
    %>
    <h4><%
        if (propName.getProperty(providerNo, "").equals("")) {
            out.print(allProviderLabel);
        } else {
            out.print(SafeEncode.forHtml(propName.getProperty(providerNo, "")));
        }
    %> - <fmt:message key="admin.logReport.title"/></h4>

    <button class="btn float-end" onClick="window.print()" style="margin-bottom:4px">
        <i class="fa-solid fa-print"></i> <fmt:message key="admin.logReport.print"/>
    </button>


    <p><fmt:message key="admin.logReport.period"/> ( <carlos:encode value='<%= startDate == null ? "" : startDate %>' context="html"/> ~ <carlos:encode value='<%= endDate == null ? "" : endDate %>' context="html"/>)</p>
    <table class="table table-bordered table-striped table-hover table-sm">
        <tr bgcolor="<%=tdTitleColor%>">
            <TH><fmt:message key="admin.logReport.table.time"/></TH>
            <TH><fmt:message key="admin.logReport.table.action"/></TH>
            <TH><fmt:message key="admin.logReport.table.content"/></TH>
            <TH><fmt:message key="admin.logReport.table.keyword"/></TH>
            <TH><fmt:message key="admin.logReport.table.ip"/></TH>
            <% if (bAll) { %>
            <TH><fmt:message key="admin.logReport.table.provider"/></TH>
            <% } %>
            <TH><fmt:message key="admin.logReport.table.demo"/></TH>
            <TH><fmt:message key="admin.logReport.table.data"/></TH>
        </tr>
                <%
String catName = "";
String color = "";
int codeNum = 0;
int vecNum = 0;
for (int i = 0; i < vec.size(); i++) {
	prop = (Properties) vec.get(i);
    color = i%2==0?tdInterlColor:"white";
%>
        <tr bgcolor="<%=color %>" align="center">
            <td><carlos:encode value='<%= prop.getProperty("dateTime", "") %>' context="html"/></td>
            <td><carlos:encode value='<%= prop.getProperty("action", "") %>' context="html"/></td>
            <td><carlos:encode value='<%= prop.getProperty("content", "") %>' context="html"/></td>
            <td><carlos:encode value='<%= prop.getProperty("contentId", "") %>' context="html"/></td>
            <td><carlos:encode value='<%= prop.getProperty("ip", "") %>' context="html"/></td>
            <% if (bAll) { %>
            <td><carlos:encode value='<%= propName.getProperty(prop.getProperty("provider_no"), "") %>' context="html"/></td>
            <% } %>
            <td><carlos:encode value='<%= prop.getProperty("demographic_no", "") %>' context="html"/></td>
            <c:set var="logData" value='<%= prop.getProperty("data", "") %>' scope="page"/>
            <td>${carlos:forHtmlContentWithBreaks(logData)}</td>
        </tr>

                <% } %>

        <script type="text/javascript">
            flatpickr("#startDate1", {dateFormat: "Y-m-d", allowInput: true});
            flatpickr("#endDate1", {dateFormat: "Y-m-d", allowInput: true});
        </script>
    </body>
</html>
