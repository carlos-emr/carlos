<%--

    Copyright (c) 2012- Centre de Medecine Integree

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

    This software was written for
    Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
    as part of the OSCAR McMaster EMR System


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%
    String curUser_no = (String) session.getAttribute("user");
    UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
    String defaultPrinterName = "";
    boolean silentPrint = false;
    UserProperty prop = null;
    prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE);
    if (prop != null) {
        defaultPrinterName = prop.getValue();
    }
    prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE_SILENT_PRINT);
    if (prop != null) {
        if (prop.getValue().equalsIgnoreCase("yes")) {
            silentPrint = true;
        }
    }
%>
<html>
    <head>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="report.printLabel.title"/></title>
    </head>
    <body>
    <% if (!defaultPrinterName.isEmpty()) {
        if (silentPrint == true) {%>
    <fmt:setBundle basename="oscarResources"/><fmt:message key="report.printLabel.SilentlyPrintToDefaultPrinter"/>
    <%} else {%>
    <fmt:setBundle basename="oscarResources"/><fmt:message key="report.printLabel.DefaultPrinter"/>
    <%}%>
    <%=defaultPrinterName%>
    <%}%>
    <br>


    <object id="pdf" type="application/pdf"
            data="<%= request.getContextPath() %>/report/GenerateEnvelopes.do?demos=<%=request.getParameter("demos")%>"
            height="80%" width="100%" standby="Loading pdf...">

        Sorry the pdf failed to load...<a
            href="<%= request.getContextPath() %>/report/GenerateEnvelopes.do?demos=<%=request.getParameter("demos")%>">click here to download the
        PDF</a>.

    </object>
    </body>
</html>

