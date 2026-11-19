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
    Result page of schedule/DateSave (ScheduleDateSave2Action). The action validates the
    command, replaces the provider's override for the date in one transaction and updates the
    session caches after it commits (#5297); this page only refreshes the calendar and closes
    the popup.
--%>
<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="schedule.scheduledatesave.title"/></title>
    </head>
    <script language="JavaScript">
        <!--
        // Redisplay the calendar (the only page that opens this popup) without posting: reloading it
        // would resubmit the week setting and regenerate every rule-made day in the range (issue #4880).
        if (opener && !opener.closed && typeof opener.showCalendarMonth === 'function') {
            opener.showCalendarMonth();
        }
        self.close();
        //-->
    </script>
    <body>
    </body>
</html>
