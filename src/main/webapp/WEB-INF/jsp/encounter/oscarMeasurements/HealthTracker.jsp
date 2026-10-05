<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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
    Page role: entry view for the CARLOS Health Tracker.

    Reached only through the Struts route
    /encounter/oscarMeasurements/ViewHealthTracker (ViewClinical2Action, _eChart r);
    the page itself lives under /WEB-INF so it has no public JSP URL.

    Mirrors TemplateFlowSheet.jsp: this wrapper exists purely so the included
    page fragment can abort rendering by setting the "errorMessage" request
    attribute (an unknown or unreadable template) and have that turn into a real
    HTTP status instead of a half-rendered page.

    Parameters:
      demographic_no - required, the patient whose tracker is being opened
      template       - flowsheet name; defaults to "tracker"
      numEle/sdate/edate/show - history display filters
      ycoord         - scroll position restored after a save round-trip

    @since 2026-09-20
--%>
<jsp:include page="HealthTrackerPage.jspf"/>
<%
    String errorMessage = (String) request.getAttribute("errorMessage");
    if (errorMessage != null) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND, errorMessage);
        return;
    }
%>
