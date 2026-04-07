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

<%

    String user_no = (String) session.getAttribute("user");
%>
<%@ page import="java.util.*, java.sql.*, java.io.*, io.github.carlos_emr.*"
         errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="javax.xml.parsers.SAXParser" %>
<%@ page import="javax.xml.parsers.ParserConfigurationException" %>
<%@ page import="org.xml.sax.InputSource" %>
<%@ page import="org.xml.sax.SAXException" %>
<%@ page import="org.xml.sax.helpers.DefaultHandler" %>
<%@ page import="io.github.carlos_emr.carlos.utility.XmlUtils" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<% java.util.Properties oscarVariables = CarlosProperties.getInstance(); %>

<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>ANTENATAL CHECK LIST</title>
    <link rel="stylesheet" href="antenatalrecord.css">
    <script language="JavaScript">
        <!--


        function onExit() {
            if (confirm("Are you sure to exit WITHOUT saving the form?")) window.close();
        }

        //-->
    </SCRIPT>
</head>
<body onLoad="setfocus()" bgcolor="#c4e9f6" bgproperties="fixed"
      topmargin="0" leftmargin="1" rightmargin="1">
<form name="checklistedit" action="obarchecklistedit_99_12.jsp"
      method="POST">
    <%
        char sep = oscarVariables.getProperty("file_separator").toCharArray()[0];
        String saveError = null;
        if (request.getParameter("submit") != null && request.getParameter("submit").compareTo(" Save ") == 0) {
            // Security check — only admins may overwrite the shared checklist template
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
            if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
                throw new SecurityException("missing required sec object: _admin (write access required)");
            }

            String checklist = request.getParameter("checklist");
            if (checklist != null) {
                // Validate submitted XML with a hardened SAX parser.
                // Parsing ensures the document is well-formed before it is written to disk
                // and later consumed by DesAntenatalPlannerChecklist_99_12.
                try {
                    SAXParser saxParser = XmlUtils.createSecureSAXParserFactory().newSAXParser();
                    saxParser.parse(new InputSource(new StringReader(checklist)), new DefaultHandler());

                    // XML is well-formed and safe — write the raw (un-encoded) document to disk
                    try (FileWriter inf = new FileWriter(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR") + "desantenatalplannerchecklist_99_12.xml")) {
                        inf.write(checklist);
                    }
                } catch (SAXException | ParserConfigurationException e) {
                    saveError = "Save failed: submitted content is not valid XML or contains unsafe constructs. Please correct the XML and try again.";
                }
            }
        }
    %>
    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr bgcolor="#486ebd">
            <th align=CENTER><font face="Arial, Helvetica, sans-serif"
                                   color="#FFFFFF">Antenatal Check List</font></th>
            <th width="25%" nowrap>
                <div align="right"><a href=#
                                      onClick="popupPage(450,900,'ar1risk_99_12.htm')"><font
                        color="#FFFF66">View Risk Number</font></a> <input type='submit'
                                                                           name='submit' value=' Save '> <input
                        type="button"
                        name="Button"
                        value="&nbsp;<%=request.getParameter("submit")!=null?" Exit ":"Cancel"%>&nbsp;"
                        onClick="onExit();">&nbsp;
                </div>
            </th>
        </tr>
        <% if (saveError != null) { %>
        <tr>
            <td colspan="2" style="color: red; padding: 4px;"><%= Encode.forHtml(saveError) %></td>
        </tr>
        <% } %>
        <tr>
            <td align=CENTER colspan="2"><font
                    face="Times New Roman, Times, serif"> <textarea
                    name="checklist" cols="100" rows="38" style="width: 100%">
<%
    boolean fileFound = true;
    File file = new File(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR") + "desantenatalplannerchecklist_99_12.xml");
    if (!file.isFile() || !file.canRead()) {
        file = new File(".." + sep + "webapps" + sep + oscarVariables.getProperty("project_home") + sep + "decision" + sep + "antenatal" + sep + "desantenatalplannerchecklist_99_12.xml");
        if (!file.isFile() || !file.canRead()) {
            fileFound = false; //throw new IOException();
        }
    }

    if (fileFound) {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        String aline = "";
        while (true) {
            aline = raf.readLine();
            if (aline != null) {
                // Encode for HTML context to prevent textarea-breakout XSS
                out.println(Encode.forHtml(aline));
            } else {
                break;
            }
        }
        raf.close();
    }
%>
</textarea> </font></td>
        </tr>
        <TR>
            <td><b>Note:</b> The XML document is validated on save. Malformed XML or documents containing DOCTYPE declarations will be rejected.
            </td>
        </tr>
    </table>
    <input type='submit' name='submit' value=' Save '> <input
        type="button" name="Button" value=" Exit " onClick="onExit();">
</form>
</body>
</html>
