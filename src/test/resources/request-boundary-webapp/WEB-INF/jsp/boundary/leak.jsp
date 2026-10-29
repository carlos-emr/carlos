<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    This software is published under the GPL GNU General Public License, version 2 or later.
    CARLOS EMR Project https://github.com/carlos-emr/carlos

    Test-only view for RequestBoundaryFilterOrderIntegrationTest (issue #5305). It opens a
    tracked JDBC connection and deliberately never closes it, so the test can prove the
    request-end cleanup still releases what a JSP leaves open after a Struts forward.
--%><%@ page session="false" contentType="text/plain; charset=UTF-8" %><%
    io.github.carlos_emr.carlos.utility.RequestBoundaryFilterOrderIntegrationTest.Probe.leakFromJsp();
%>rendered
