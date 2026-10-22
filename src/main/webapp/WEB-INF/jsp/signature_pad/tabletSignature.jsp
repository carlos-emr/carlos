<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%--
When including the "inWindow" parameter as "true" it is assumed that tabletSignature.jsp
is hosted in an IFrame and that the IFrame's parent window implements signatureHandler(e)
--%>
<%@ page import="io.github.carlos_emr.carlos.utility.DigitalSignatureUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.ui.servlet.ImageRenderingServlet" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null) {
        response.sendRedirect(request.getContextPath() + "/index");
        return;
    }
%>
<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
    <title>Signature Pad</title>
    <meta name="viewport" content="width=device-width; initial-scale=1.0; maximum-scale=1.0; user-scalable=0;"/>
    <meta name="apple-mobile-web-app-capable" content="yes"/>
    <meta name="apple-mobile-web-app-status-bar-style"/>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/share/css/TabletSignature.css" media="screen"/>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/TabletSignature.js"></script>
    <script type="text/javascript"
            src="<%= request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
    <script type="text/javascript"
            src="<%= request.getContextPath() %>/library/jquery/jquery.form.js"></script>

</head>
<%
    String requestIdKey = request.getParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY);
    if (requestIdKey == null) {
        requestIdKey = DigitalSignatureUtils.generateSignatureRequestId(loggedInInfo.getLoggedInProviderNo());
    }
    String imageUrl = request.getContextPath() + "/imageRenderingServlet?source=" + ImageRenderingServlet.Source.signature_preview.name() + "&" + DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY + "=" + SafeEncode.forUriComponent(requestIdKey);
    String storedImageUrl = request.getContextPath() + "/imageRenderingServlet?source=" + ImageRenderingServlet.Source.signature_stored.name() + "&digitalSignatureId=";
    boolean saveToDB = "true".equals(request.getParameter("saveToDB"));
%>
<script type="text/javascript">
    var _in_window = <%= "true".equals(request.getParameter("inWindow"))%>;

    var requestIdKey = "<carlos:encode value='<%= requestIdKey %>' context="javaScriptBlock"/>";

    var previewImageUrl = "<carlos:encode value='<%= imageUrl %>' context="javaScriptBlock"/>";

    var storedImageUrl = "<carlos:encode value='<%= storedImageUrl %>' context="javaScriptBlock"/>";

    var contextPath = "<carlos:encode value='<%= request.getContextPath() %>' context="javaScriptBlock"/>";

</script>

<body style="background-color: #555; font-family: arial, helvetica, sans-serif;">

<div class="verticalCenterDiv">
    <div class="centerDiv">
        <canvas id='canvas'></canvas>
        <div><span id="signMessage" style="color:#FFFFFF; font-family: arial, helvetica, sans-serif;"><fmt:message key="tabletSignature.msgSignAbove"/></span>
            <button id="clear" style="display:none"><fmt:message key="tabletSignature.btnClear"/></button>
            <button id="save" style="display:none;"><fmt:message key="tabletSignature.btnSave"/></button>
        </div>
    </div>
</div>

<form onsubmit="return submitSignature();" action="<%=request.getContextPath() %>/signature_pad/SaveSignatureUpload"
      id="signatureForm" method="POST">
    <input type="hidden" id="signatureImage" name="signatureImage" value=""/>
    <input type="hidden" name="source" value="IPAD"/>
    <input type="hidden" name="<%=DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY %>" value="<carlos:encode value='<%= requestIdKey %>' context="htmlAttribute"/>"/>
    <input type="hidden" name="demographicNo" value="<carlos:encode value='<%= request.getParameter("demographicNo") != null ? request.getParameter("demographicNo") : "" %>' context="htmlAttribute"/>"/><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
	<input type="hidden" name="<%= ModuleType.class.getSimpleName()%>"
			value="<carlos:encode value='<%= request.getParameter(ModuleType.class.getSimpleName()) != null ? request.getParameter(ModuleType.class.getSimpleName()) : "" %>' context="htmlAttribute"/>" /><%-- nosemgrep: java.jsp.jsp-scriptlet-xss.jsp-scriptlet-xss --%>
    <input type="hidden" name="saveToDB" value="<%=saveToDB%>"/>
</form>

</body>
</html>
