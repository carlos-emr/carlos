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

<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LogSanitizer" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart"
                   rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/logout.jsp");%>
</security:oscarSec>
<%
    // forward to the page 'form_link'
    if (true) {
        out.clearBuffer();

        String formLink = request.getParameter("form_link");
        String demographicNo = request.getParameter("demographic_no");

        // Validate form_link to prevent path traversal (CWE-22 / CWE-98).
        // Only permit simple JSP filenames — no slashes, ".." sequences, or other
        // path-manipulation characters.  All legitimate form links are plain filenames
        // such as "formrourke1.jsp" that live in the /form/ directory.
        if (formLink == null || !formLink.matches("[A-Za-z0-9_\\-]+\\.jsp")) {
            MiscUtils.getLogger().warn("forwardname.jsp: blocked invalid form_link parameter: {}",
                    LogSanitizer.sanitize(formLink));
            response.sendError(400, "Invalid form link");
            return;
        }

        // Dispatch to the validated form within the /form/ directory only.
        request.getRequestDispatcher("/form/" + formLink + "?demographic_no=" + demographicNo).include(request, response);
        return;
    }
%>
