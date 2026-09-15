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

<%@ page import="java.sql.*, java.util.*, io.github.carlos_emr.MyDateFormat" errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="java.util.ResourceBundle" %>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.EncounterTemplate" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.EncounterDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Encounter" %>
<%@page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@page import="io.github.carlos_emr.carlos.util.StringUtils" %>
<%@page import="io.github.carlos_emr.SxmlMisc" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    EncounterTemplateDao encounterTemplateDao = SpringUtils.getBean(EncounterTemplateDao.class);
    EncounterDao encounterDao = SpringUtils.getBean(EncounterDao.class);
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());
%>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><%= bundle.getString("provider.providerencountersingle.title") %></title>
    <script LANGUAGE="JavaScript">
        <!--
        function start() {
            this.focus();
        }

        //-->
    </script>
</head>
<body onload="start()" topmargin="0" leftmargin="0" rightmargin="0">
<table border="0" cellspacing="0" cellpadding="0" width="100%">
    <tr bgcolor="#CCCCFF">
        <th align="CENTER"><%= bundle.getString("provider.providerencountersingle.heading") %></th>
    </tr>
</table>
<%

    String content = "";
    String encounterattachment = "";
    String xmlContent = "";
    String xmlUsername = "";
    String temp = "";
    Encounter enc = encounterDao.find(Integer.parseInt(request.getParameter("encounter_no")));
    if (enc != null) {
        content = enc.getContent();
        encounterattachment = enc.getEncounterAttachment();
        xmlContent = SxmlMisc.getXmlContent(content, "xml_content");
        xmlUsername = SxmlMisc.getXmlContent(content, "xml_username");
%>
<font size="-1"><carlos:encode value='<%= ConversionUtils.toDateString(enc.getEncounterDate()) %>' context="html"/> <carlos:encode value='<%= ConversionUtils.toTimeString(enc.getEncounterTime()) %>' context="html"/>
    &nbsp;<font color="green"><carlos:encode value='<%= StringUtils.noNull(enc.getSubject()).isEmpty() ? bundle.getString("provider.providerencountersingle.unknown") : enc.getSubject() %>' context="html"/>
    </font></font>
<br>
<xml id="xml_list">
    <encounter>
        <carlos:encode value='<%= content %>' context="xml"/>
    </encounter>
</xml>
<%
    }
%>
<table width='100%' border='0' BGCOLOR="#EEEEFF">
    <tr>
        <td><%= bundle.getString("provider.providerencountersingle.attachment") %>: <%
            StringTokenizer st = new StringTokenizer(encounterattachment);
            while (st.hasMoreTokens()) {
                temp = st.nextToken(">").substring(1);
        %> <a href=#
              onClick="popupPage(600,800, '<carlos:encode value='<%= st.nextToken("<").substring(1) %>' context="javaScriptAttribute"/>')">
            <carlos:encode value='<%= temp %>' context="html"/>
        </a> <%
                st.nextToken(">");
            }
        %>
        </td>
        <td align='right' width='20%' nowrap>
            <carlos:encode value='<%= xmlUsername %>' context="html"/>
        </td>
    </tr>
</table>
<%
    if (request.getParameter("template") != null && !(request.getParameter("template").equals("."))) {

        for (EncounterTemplate template : encounterTemplateDao.findByName(request.getParameter("template"))) {
            String val = template.getEncounterTemplateValue();
            if (val != null) {
                out.println(SafeEncode.forHtml(val));
            }
        }


    } else {
        out.println("<table border='0'><tr><td><font color='blue'>" + bundle.getString("provider.providerencountersingle.content") + ":</font></td></tr><tr><td>" + SafeEncode.forHtml(xmlContent) + "</td></tr></table>");
    }
%>

<center><input type="button" value="<%= bundle.getString("provider.providerencountersingle.printPreview") %>"
               onClick="popupPage(600,800, '<%= request.getContextPath() %>/provider/ViewProviderEncounterPrint?encounter_no=<carlos:encode value='<%= request.getParameter("encounter_no") != null ? request.getParameter("encounter_no") : "" %>' context="uriComponent"/>&demographic_no=<carlos:encode value='<%= request.getParameter("demographic_no") != null ? request.getParameter("demographic_no") : "" %>' context="uriComponent"/>&username=<carlos:encode value='<%= request.getParameter("username") != null ? request.getParameter("username") : "" %>' context="uriComponent"/>')"><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
    <input type="button" value="<%= bundle.getString("provider.providerencountersingle.closeWindow") %>" onClick="self.close()">
</center>
</body>
</html>
