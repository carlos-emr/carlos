<%-- Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. --%>
<!-- ruleid: carlos-no-reflective-object-methods -->
<%@ page import="org.apache.commons.lang3.builder.ReflectionToStringBuilder" %>
<%
    // ruleid: carlos-no-reflective-object-methods
    String unsafe = ToStringBuilder.reflectionToString(patient);
    // ok: carlos-no-reflective-object-methods
    String safe = "Summary prepared";
%>

<%
    // ruleid: carlos-no-reflective-object-methods
    boolean recursive = new EqualsBuilder().setTestRecursive(true).append(left, right).isEquals();
%>
