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

<%--
    Antenatal add-on risk list editor.

    Purpose:
      Presents the shared antenatal risk-list XML for editing by a configuration
      administrator. The rendered document drives the risk checkboxes and popup
      links on every antenatal planner, so it is shared clinical-decision
      configuration rather than per-patient data.

    Flow:
      GET  -> decision/antenatal/obarriskedit_99_12 (ViewDecision2Action, _form r)
      POST -> decision/antenatal/SaveAntenatalRiskConfig
              (SaveAntenatalRiskConfig2Action, _form w plus _admin w or _admin.misc w)
      Persistence lives entirely in that action; this page performs no writes.

    Request attributes (set by the save action on its "input" result):
      riskEditorChecklist - the rejected submission, redisplayed so the edit is not lost
      riskEditorError     - a safe, user-displayable validation or storage message
    Session attribute:
      riskEditorSaved     - one-shot success flag consumed after a post/redirect/get

    Configuration source:
      DOCUMENT_DIR override if present, otherwise the default packaged in the webapp.
      Same precedence as antenatalplanner.jsp and antenatalplannerprint.jsp, but the
      packaged copy is read as a resource stream so it also works unexploded.

    @since 2026-08-11
--%>
<%@ page import="java.util.*, java.io.*, java.nio.charset.StandardCharsets, java.nio.file.Files" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>

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
       out.print(SafeEncode.forHtml(submittedChecklist));
   } else {
    // Same precedence every reader of this configuration uses (see
    // antenatalplanner.jsp and antenatalplannerprint.jsp): an administrator's
    // DOCUMENT_DIR copy wins, otherwise show the default shipped in the webapp.
    //
    // The previous fallback built a path relative to the JVM's working directory
    // out of "file_separator" and "project_home". That could not resolve under a
    // modern Tomcat layout — the working directory is wherever the server was
    // launched from, and "project_home" does not name the deployed context — so a
    // site that had never saved an override opened this editor on an empty box
    // with no way back to the packaged default, even though the planner was
    // rendering that default at the same moment.
    File overrideFile = new File(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"),
            "desantenatalplannerrisks_99_12.xml");
    Reader source = null;
    if (overrideFile.isFile() && overrideFile.canRead()) {
        source = Files.newBufferedReader(overrideFile.toPath(), StandardCharsets.UTF_8);
    } else {
        // getResourceAsStream rather than getRealPath: the latter returns null when
        // the application is served from an unexploded WAR, which would leave the
        // editor blank even though the packaged default is present.
        InputStream packaged = application.getResourceAsStream(
                "/decision/antenatal/desantenatalplannerrisks_99_12.xml");
        if (packaged != null) {
            source = new InputStreamReader(packaged, StandardCharsets.UTF_8);
        }
    }

    if (source != null) {
        try (BufferedReader reader = new BufferedReader(source)) {
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
