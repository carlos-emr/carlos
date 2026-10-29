<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License, version 2 or later.
    CARLOS EMR Project https://github.com/carlos-emr/carlos

    Test-only view for RequestBoundaryFilterOrderIntegrationTest (issue #5305). It leaks a
    tracked JDBC connection and then fails, so the test can prove the request-end cleanup runs
    when a forwarded JSP throws.
--%><%@ page session="false" contentType="text/plain; charset=UTF-8" %><%
    io.github.carlos_emr.carlos.utility.RequestBoundaryFilterOrderIntegrationTest.Probe.leakFromJsp();
    if (true) {
        throw new IllegalStateException("synthetic JSP failure after a leak");
    }
%>unreachable
