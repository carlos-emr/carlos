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
         errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>


<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="demographic.demographicprintdemographic.title"/></title>
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
            <tr>
                <td><font face="Courier New, Courier, mono" size="2"><b><carlos:encode value='<%= StringUtils.noNull(request.getParameter("last_name")) %>' context="html"/>
                    ,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("first_name")) %>' context="html"/>
                </b><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("hin")) %>' context="html"/><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("dob")) %>' context="html"/>&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("sex")) %>' context="html"/><br>
                    <br>
                    <b><carlos:encode value='<%= StringUtils.noNull(request.getParameter("last_name")) %>' context="html"/>,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("first_name")) %>' context="html"/>
                    </b><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("hin")) %>' context="html"/><br>
                    &nbsp;&nbsp;&nbsp;&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("dob")) %>' context="html"/>&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("sex")) %>' context="html"/><br>
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
            <tr>
                <td><font face="Courier New, Courier, mono" size="2"><b><carlos:encode value='<%= StringUtils.noNull(request.getParameter("last_name")) %>' context="html"/>
                    ,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("first_name")) %>' context="html"/>&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("chart_no")) %>' context="html"/>
                </b><br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("address")) %>' context="html"/><br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("city")) %>' context="html"/>
                    ,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("province")) %>' context="html"/>,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("postal")) %>' context="html"/><br>
                    <fmt:message key="demographic.demographiclabelprintsetting.msgHome"/>:&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("phone")) %>' context="html"/>
                    <br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("dob")) %>' context="html"/>&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("sex")) %>' context="html"/>
                    <br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("hin")) %>' context="html"/><br>
                    <fmt:message key="demographic.demographiclabelprintsetting.msgBus"/>:<carlos:encode value='<%= StringUtils.noNull(request.getParameter("phone2")) %>' context="html"/>&nbsp;<fmt:message key="demographic.demographiclabelprintsetting.msgDr"/>&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("providername")) %>' context="html"/>
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
            <tr>
                <td><font face="Courier New, Courier, mono" size="2"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("last_name")) %>' context="html"/>
                    ,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("first_name")) %>' context="html"/><br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("address")) %>' context="html"/>
                    <br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("city")) %>' context="html"/>,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("province")) %>' context="html"/>
                    ,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("postal")) %>' context="html"/><br>
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
            <tr>
                <td><font face="Courier New, Courier, mono"
                          size="2"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("first_name")) %>' context="html"/>&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("last_name")) %>' context="html"/>
                    <br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("address")) %>' context="html"/><br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("city")) %>' context="html"/>
                    ,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("province")) %>' context="html"/>,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("postal")) %>' context="html"/><br>
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
            <tr>
                <td><font face="Courier New, Courier, mono"
                          size="2"><carlos:encode value='<%= StringUtils.noNull(request.getParameter("chart_no")) %>' context="html"/>&nbsp;&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("last_name")) %>' context="html"/>
                    ,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("first_name")) %>' context="html"/><br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("address")) %>' context="html"/>
                    <br><carlos:encode value='<%= StringUtils.noNull(request.getParameter("city")) %>' context="html"/>,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("province")) %>' context="html"/>
                    ,&nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("postal")) %>' context="html"/><br>
                    <carlos:encode value='<%= StringUtils.noNull(request.getParameter("dob")) %>' context="html"/>&nbsp;&nbsp; <carlos:encode value='<%= StringUtils.noNull(request.getParameter("age")) %>' context="html"/>&nbsp;
                    <carlos:encode value='<%= StringUtils.noNull(request.getParameter("sex")) %>' context="html"/> &nbsp;<carlos:encode value='<%= StringUtils.noNull(request.getParameter("hin")) %>' context="html"/><br>
                    <carlos:encode value='<%= StringUtils.noNull(request.getParameter("phone")) %>' context="html"/>&nbsp;&nbsp; <carlos:encode value='<%= StringUtils.noNull(request.getParameter("phone2")) %>' context="html"/><br>
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
               value="<fmt:message key='global.btnPrint'/>" onClick="window.print();">
    </div>
    <div ID="blockDiv1"
         STYLE="position: absolute; visibility: visible; z-index: 2; left: 620px; top: 24px; width: 70px; height: 20px;">
        <input type="button" name="button"
               value="<fmt:message key='global.btnBack'/>"
               onClick="javascript:history.go(-1);return false;"></div>

    </body>
</html>
