<%--
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
    Purpose: Test-only view for RequestBoundaryFilterOrderIntegrationTest (issue #5305), never
    packaged. A Struts action or servlet forwards here; the page opens a tracked JDBC connection
    and deliberately never closes it.
    Features: Lets the test prove the request-end cleanup in DbConnectionFilter still releases
    what a JSP leaves open after a forward, once the forward has returned.
    Parameters: none.
    @since 2026-10-29
--%><%@ page session="false" contentType="text/plain; charset=UTF-8" %><%
    io.github.carlos_emr.carlos.utility.RequestBoundaryFilterOrderIntegrationTest.Probe.leakFromJsp();
%>rendered
