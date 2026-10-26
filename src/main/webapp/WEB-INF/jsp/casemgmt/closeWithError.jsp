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
<%-- Shown in place of the chart when a note save is refused (lost note lock or an invalid date).
     Nothing was saved, so the clinician's submitted note text is echoed back to be copied. --%>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String error = "Your note was not saved as you did not have the exclusive note lock";

    if (request.getAttribute("DateError") != null) {
        error = (String) request.getAttribute("DateError");
    }
    // The page carries clinical text the user just typed; it must never be cached or replayed.
    response.setHeader("Cache-Control", "no-store");
    String unsavedNote = request.getParameter("caseNote_note");
    boolean showUnsavedNote = unsavedNote != null && !unsavedNote.isBlank();
%>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title>CARLOS</title>
    <script type="text/javascript">

        window.innerHeight = 400;
        window.innerWidth = 400;
    </script>

</head>
<body>

<h3 style="text-align:center;">
    <%=error %>
</h3>
<% if (showUnsavedNote) { %>
<p id="unsavedNoteHint"><fmt:message key="casemgmt.closeWithError.unsavedNoteHint"/></p>
<label for="unsavedNoteText"><fmt:message key="casemgmt.closeWithError.unsavedNoteLabel"/></label>
<textarea id="unsavedNoteText" readonly rows="12" style="width:100%;box-sizing:border-box;"
          aria-describedby="unsavedNoteHint"><carlos:encode value="<%= unsavedNote %>"/></textarea>
<% } %>
</body>
</html>