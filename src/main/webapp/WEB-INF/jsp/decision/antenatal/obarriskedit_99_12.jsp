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
<%@ page import="java.util.*, java.io.*, java.nio.charset.StandardCharsets, java.nio.file.Files" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<% java.util.Properties oscarVariables = CarlosProperties.getInstance(); %>

<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
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
<form name="checklistedit" action="<%= SafeEncode.forHtmlAttribute(request.getContextPath()) %>/decision/antenatal/SaveAntenatalRiskConfig" method="POST">
    <%
        char sep = oscarVariables.getProperty("file_separator").toCharArray()[0];
        String submittedChecklist = (String) request.getAttribute("riskEditorChecklist");
        String editorError = (String) request.getAttribute("riskEditorError");
        boolean editorSaved = Boolean.TRUE.equals(session.getAttribute("riskEditorSaved"));
        if (editorSaved) {
            session.removeAttribute("riskEditorSaved");
        }
    %>
    <table border="0" cellspacing="0" cellpadding="0" width="100%">
        <tr bgcolor="#486ebd">
            <th align=CENTER><font face="Arial, Helvetica, sans-serif"
                                   color="#FFFFFF">Antenatal Add-On Risk List</font></th>
            <th width="25%" nowrap>
                <div align="right"><a href=#
                                      onClick="popupPage(450,900,'ar1risk_99_12.htm')"><font
                        color="#FFFF66">View Risk Number</font></a> <input type='submit'
                                                                           name='submit' value=' Save '> <input
                        type="button"
                        name="Button"
                        value="&nbsp;<%=request.getParameter("submit")!=null?" Exit ":"Cancel"%>&nbsp;"<%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
                        onClick="onExit();">&nbsp;
                </div>
            </th>
        </tr>
        <% if (editorError != null) { %>
        <tr>
            <td colspan="2" role="alert" style="color: #a00; font-weight: bold; padding: 0.5em;">
                <%= SafeEncode.forHtmlContent(editorError) %>
            </td>
        </tr>
        <% } else if (editorSaved) { %>
        <tr>
            <td colspan="2" role="status" style="color: #063; font-weight: bold; padding: 0.5em;">
                The antenatal risk list was saved.
            </td>
        </tr>
        <% } %>
        <tr>
            <td align=CENTER colspan="2"><font
                    face="Times New Roman, Times, serif"> <textarea
                    name="checklist" cols="100" rows="38" style="width: 100%">
<% if (submittedChecklist != null) {
       out.print(SafeEncode.forHtmlContent(submittedChecklist));
   } else {
    boolean fileFound = true;
    File file = new File(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"),
            "desantenatalplannerrisks_99_12.xml");
    if (!file.isFile() || !file.canRead()) {
        file = new File(".." + sep + "webapps" + sep + oscarVariables.getProperty("project_home") + sep + "decision" + sep + "antenatal" + sep + "desantenatalplannerrisks_99_12.xml");
        if (!file.isFile() || !file.canRead()) {
            fileFound = false; //throw new IOException();
        }
    }

    if (fileFound) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String aline;
            while ((aline = reader.readLine()) != null) {
                out.println(SafeEncode.forHtml(aline));
            }
        }
    }
   }
%>
</textarea> </font></td>
        </tr>
        <TR>
            <td><b>*</b> Enter a complete risk-list XML document. In text content,
                write the symbols &amp;, &lt;, and &gt; as &amp;amp;, &amp;lt;, and &amp;gt;.
            </td>
        </tr>
    </table>
    <input type='submit' name='submit' value=' Save '> <input
        type="button" name="Button" value=" Exit " onClick="onExit();">
</form>
</body>
</html>
