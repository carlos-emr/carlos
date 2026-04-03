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


<%@ page import="java.util.*, java.sql.*, io.github.carlos_emr.*"
         errorPage="/errorpage.jsp" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>


<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicprintdemographic.title"/></title>
        <script language="JavaScript">
            <!--


            //-->
        </script>
    </head>
    <body bgcolor="ivory" onLoad="setfocus()" topmargin="0" leftmargin="0"
          rightmargin="0">
    <%
        int left = Integer.parseInt(request.getParameter("left"));
        int top = Integer.parseInt(request.getParameter("top"));
        int height = Integer.parseInt(request.getParameter("height"));
        int gap = Integer.parseInt(request.getParameter("gap"));
        int b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0;
        if (request.getParameter("label1checkbox") != null && request.getParameter("label1checkbox").compareTo("checked") == 0)
            b1 = Integer.parseInt(request.getParameter("label1no"));
        if (request.getParameter("label2checkbox") != null && request.getParameter("label2checkbox").compareTo("checked") == 0)
            b2 = Integer.parseInt(request.getParameter("label2no"));
        if (request.getParameter("label3checkbox") != null && request.getParameter("label3checkbox").compareTo("checked") == 0)
            b3 = Integer.parseInt(request.getParameter("label3no"));
        if (request.getParameter("label4checkbox") != null && request.getParameter("label4checkbox").compareTo("checked") == 0)
            b4 = Integer.parseInt(request.getParameter("label4no"));
        if (request.getParameter("label5checkbox") != null && request.getParameter("label5checkbox").compareTo("checked") == 0)
            b5 = Integer.parseInt(request.getParameter("label5no"));

        for (int i = 0; i < b1; i++) {
    %>
    <div ID="blockDiv1"
         STYLE="position:absolute; visibility:visible; z-index:2; left:<%=left%>px; top:<%=top+i*(height+gap/2)%>px; width:400px; height:100px;">
        <table width="100%" border="0" cellspacing="0" cellpadding="0">
                <%--    <tr><td><%= Encode.forHtml(StringUtils.noNull(request.getParameter("label1"))) %></td></tr>--%>
            <tr>
                <td><font face="Courier New, Courier, mono" size="2"><b><%= Encode.forHtml(StringUtils.noNull(request.getParameter("last_name"))) %>
                    ,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("first_name"))) %>
                </b><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("hin"))) %><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("dob"))) %>&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("sex"))) %><br>
                    <br>
                    <b><%= Encode.forHtml(StringUtils.noNull(request.getParameter("last_name"))) %>,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("first_name"))) %>
                    </b><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("hin"))) %><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("dob"))) %>&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("sex"))) %><br>
                </font></td>
            </tr>
        </table>
    </div>

    <%
        }
        for (int i = 0; i < b2; i++) {
    %>

    <div ID="blockDiv1"
         STYLE="position:absolute; visibility:visible; z-index:2; left:<%=left%>px; top:<%=top+b1*(height+gap)+i*(height+gap/2)%>px; width:400px; height:100px;">
        <table width="100%" border="0" cellspacing="0" cellpadding="0">
                <%--<tr><td><%= Encode.forHtml(StringUtils.noNull(request.getParameter("label2"))) %></td></tr>--%>
            <tr>
                <td><font face="Courier New, Courier, mono" size="2"><b><%= Encode.forHtml(StringUtils.noNull(request.getParameter("last_name"))) %>
                    ,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("first_name"))) %>&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("chart_no"))) %>
                </b><br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("address"))) %><br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("city"))) %>
                    ,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("province"))) %>,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("postal"))) %><br>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiclabelprintsetting.msgHome"/>:&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("phone"))) %>
                    <br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("dob"))) %>&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("sex"))) %>
                    <br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("hin"))) %><br>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiclabelprintsetting.msgBus"/>:<%= Encode.forHtml(StringUtils.noNull(request.getParameter("phone2"))) %>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiclabelprintsetting.msgDr"/>&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("providername"))) %>
                    <br>
                </font></td>
            </tr>
        </table>
    </div>
    <%
        }
        for (int i = 0; i < b3; i++) {
    %>

    <div ID="blockDiv1"
         STYLE="position:absolute; visibility:visible; z-index:2; left:<%=left%>px; top:<%=top+(b1+b2)*(height+gap)+i*(height+gap/2)%>px; width:400px; height:100px;">
        <table width="100%" border="0" cellspacing="0" cellpadding="0">
                <%--  <tr><td><%= Encode.forHtml(StringUtils.noNull(request.getParameter("label3"))) %></td></tr>--%>
            <tr>
                <td><font face="Courier New, Courier, mono" size="2"><%= Encode.forHtml(StringUtils.noNull(request.getParameter("last_name"))) %>
                    ,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("first_name"))) %><br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("address"))) %>
                    <br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("city"))) %>,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("province"))) %>
                    ,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("postal"))) %><br>
                </font></td>
            </tr>
        </table>
    </div>
    <%
        }
        for (int i = 0; i < b4; i++) {
    %>

    <div ID="blockDiv1"
         STYLE="position:absolute; visibility:visible; z-index:2; left:<%=left%>px; top:<%=top+(b1+b2+b3)*(height+gap)+i*(height+gap/2)%>px; width:400px; height:100px;">
        <table width="100%" border="0" cellspacing="0" cellpadding="0">
                <%--  <tr><td><%= Encode.forHtml(StringUtils.noNull(request.getParameter("label4"))) %></td></tr>--%>
            <tr>
                <td><font face="Courier New, Courier, mono"
                          size="2"><%= Encode.forHtml(StringUtils.noNull(request.getParameter("first_name"))) %>&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("last_name"))) %>
                    <br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("address"))) %><br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("city"))) %>
                    ,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("province"))) %>,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("postal"))) %><br>
                </font></td>
            </tr>
        </table>
    </div>
    <%
        }
        for (int i = 0; i < b5; i++) {
    %>
    <div ID="blockDiv1"
         STYLE="position:absolute; visibility:visible; z-index:2; left:<%=left%>px; top:<%=top+(b1+b2+b3+b4)*(height+gap)+i*(height+gap/2)%>px; width:400px; height:100px;">
        <table width="100%" border="0" cellspacing="0" cellpadding="0">
                <%--  <tr><td><%= Encode.forHtml(StringUtils.noNull(request.getParameter("label5"))) %></td></tr>--%>
            <tr>
                <td><font face="Courier New, Courier, mono"
                          size="2"><%= Encode.forHtml(StringUtils.noNull(request.getParameter("chart_no"))) %>&nbsp;&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("last_name"))) %>
                    ,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("first_name"))) %><br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("address"))) %>
                    <br><%= Encode.forHtml(StringUtils.noNull(request.getParameter("city"))) %>,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("province"))) %>
                    ,&nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("postal"))) %><br>
                    <%= Encode.forHtml(StringUtils.noNull(request.getParameter("dob"))) %>&nbsp;&nbsp; <%= Encode.forHtml(StringUtils.noNull(request.getParameter("age"))) %>&nbsp;
                    <%= Encode.forHtml(StringUtils.noNull(request.getParameter("sex"))) %> &nbsp;<%= Encode.forHtml(StringUtils.noNull(request.getParameter("hin"))) %><br>
                    <%= Encode.forHtml(StringUtils.noNull(request.getParameter("phone"))) %>&nbsp;&nbsp; <%= Encode.forHtml(StringUtils.noNull(request.getParameter("phone2"))) %><br>
                </font></td>
            </tr>
        </table>
    </div>
    <%
        }
    %>
    <div ID="blockDiv1"
         STYLE="position: absolute; visibility: visible; z-index: 2; left: 620px; top: 0px; width: 70px; height: 20px;">
        <input type="button" name="button"
               value="<fmt:setBundle basename='oscarResources'/><fmt:message key='global.btnPrint'/>" onClick="window.print();">
    </div>
    <div ID="blockDiv1"
         STYLE="position: absolute; visibility: visible; z-index: 2; left: 620px; top: 24px; width: 70px; height: 20px;">
        <input type="button" name="button"
               value="<fmt:setBundle basename='oscarResources'/><fmt:message key='global.btnBack'/>"
               onClick="javascript:history.go(-1);return false;"></div>

    </body>
</html>
