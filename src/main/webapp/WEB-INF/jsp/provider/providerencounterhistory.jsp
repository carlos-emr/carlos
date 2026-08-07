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
<%@ page import="java.util.ResourceBundle" %>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.EncounterDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Encounter" %>
<%@page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    EncounterDao encounterDao = SpringUtils.getBean(EncounterDao.class);
    ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", request.getLocale());

%>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
    <script language="JavaScript">
        <!--

        function start() {
            this.focus();
        }

        function closeit() {
            //self.opener.refresh();
            self.close();
        }

        //-->
    </script>
</head>
<body onload="start()" topmargin="0" leftmargin="0" rightmargin="0">
<center>
    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr bgcolor="#486ebd">
            <th align="CENTER"><font face="Helvetica" color="#FFFFFF">
                <%= bundle.getString("provider.providerencounterhistory.title") %></font></th>
        </tr>
    </table>
    <table width="90%" border="0">
        <tr>
            <td width="95%">
                <%
                    List<Encounter> encs = encounterDao.findByDemographicNo(Integer.parseInt(request.getParameter("demographic_no")));

                    for (Encounter enc : encs) {
                %>
                &nbsp;<%=ConversionUtils.toDateString(enc.getEncounterDate())%> <%=ConversionUtils.toTimeString(enc.getEncounterTime())%><font
                    color="yellow"><%
                String historysubject = enc.getSubject() == null ? bundle.getString("provider.providerencounterhistory.nullSubject") : (enc.getSubject()).equals("") ? bundle.getString("provider.providerencounterhistory.unknown") : enc.getSubject();
                StringTokenizer st = new StringTokenizer(historysubject, ":");
                String strForm = "", strTemplateURL = "";
                while (st.hasMoreTokens()) {
                    strForm = (new String(st.nextToken())).trim();
                    break;
                }

                if (strForm.toLowerCase().compareTo("form") == 0 && st.hasMoreTokens()) {
                    strTemplateURL = "template" + (new String(st.nextToken())).trim().toLowerCase() + ".jsp";
            %> <a href=#
                  onClick="popupPage(600,800,'<%= request.getContextPath() %>/provider/providercontrol?encounter_no=<%=enc.getId()%>&demographic_no=<carlos:encode value='<%= request.getParameter("demographic_no") != null ? request.getParameter("demographic_no") : "" %>' context="uriComponent"/>&dboperation=search_encountersingle&displaymodevariable=<carlos:encode value='<%= strTemplateURL %>' context="uriComponent"/>&displaymode=vary&bNewForm=0')"><carlos:encode value='<%= historysubject %>' context="html"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            </a></font><br>
                <%
                } else if (strForm.compareTo("") != 0) {
                %> <a href=#
                      onClick="popupPage(400,600,'<%= request.getContextPath() %>/provider/providercontrol?encounter_no=<%=enc.getId()%>&demographic_no=<carlos:encode value='<%= request.getParameter("demographic_no") != null ? request.getParameter("demographic_no") : "" %>' context="uriComponent"/>&template=<carlos:encode value='<%= strForm %>' context="uriComponent"/>&dboperation=search_encountersingle&displaymode=encountersingle')"><carlos:encode value='<%= historysubject %>' context="html"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
            </a></font><br>
                <%
                        }
                    }
                %>
            </td>
        </tr>
    </table>
    <form><input type="button" value="<%= bundle.getString("provider.providerencounterhistory.closeWindow") %>"
                 onClick="closeit()"></form>
</center>
</body>
</html>
