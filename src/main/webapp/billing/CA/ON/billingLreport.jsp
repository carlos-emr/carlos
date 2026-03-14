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
<%@page import="java.nio.charset.Charset" %>
<%@page import="org.apache.commons.text.StringEscapeUtils" %>
<%@ page language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ page import="java.util.*,io.github.carlos_emr.*,java.io.*,java.net.*,io.github.carlos_emr.carlos.util.*,org.apache.commons.io.FileUtils"
         errorPage="/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<jsp:useBean id="oscarVariables" class="java.util.Properties" scope="session"/>

<html>
    <head>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <script type="text/javascript"
                src="<%= request.getContextPath() %>/library/jquery/jquery-3.6.4.min.js"></script>
                <script src="<%= request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <title>MOH Report</title>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/billing.css">
        <link rel="stylesheet" type="text/css" media="all"
              href="<%=request.getContextPath()%>/share/css/extractedFromPages.css"/>

        <%
            String INBOX = OscarProperties.getInstance().getProperty("ONEDT_INBOX");
            String filename = (String) request.getAttribute("filename");
            if (filename == null) {
                filename = request.getParameter("filename");
            }

            String fileContents = null;
            if (!filename.matches(".*\\.\\..*")) {

                File file = new File(INBOX + "/" + filename);
                fileContents = FileUtils.readFileToString(file, "UTF-8");
            } else {
                fileContents = "";
            }
        %>

        <script>
            <!--

            function loadXMLDoc(xmldoc) {
                if (window.XMLHttpRequest) {
                    // Support for IE7, Firefox and Safari only
                    xhttp = new XMLHttpRequest();
                } else if (window.ActiveXObject) {
                    // for IE5, IE6
                    xhttp = new ActiveXObject("Microsoft.XMLHTTP");
                }
                xhttp.open("GET", xmldoc, false);
                xhttp.send("");

                return xhttp.responseXML;
            }

            function displayReport() {
                var cpath = "<%=request.getContextPath()%>";
                var fname = "<%=filename%>";
                sname = cpath + "/billing/CA/ON/ES.xsl";
                if (fname.substring(2, 4) == "OU") {
                    sname = cpath + "/billing/CA/ON/OU.xsl";
                }

                xml = '<%=StringEscapeUtils.escapeEcmaScript(fileContents)%>';
                try {
                    xsl = loadXMLDoc(sname);

                } catch (err) {
                    txt = "Cannot load XSL document.\n";
                    txt += "xsl doc=" + sname + "\n";
                    txt += "Error description: " + err.description;
                    alert(txt);
                    return;
                }

                var xmlDoc = null;

                if (navigator.appName == 'Microsoft Internet Explorer') {
                    xmlDoc = new ActiveXObject("Microsoft.XMLDOM");
                    xmlDoc.async = false;
                    xmlDoc.loadXML(xml);
                } else if (window.DOMParser) {
                    parser = new DOMParser();
                    xmlDoc = parser.parseFromString(xml, "text/xml");
                } else {
                    alert("Your browser doesn't suppoprt XML parsing!");
                }

                // code for Mozilla, Firefox, Opera
                if (document.implementation && document.implementation.createDocument) {
                    xsltProcessor = new XSLTProcessor();
                    xsltProcessor.importStylesheet(xsl);
                    resultDocument = xsltProcessor.transformToFragment(xmlDoc, document);
                    jQuery("#MOHreport").html(resultDocument);
                } else if (window.ActiveXObject) {
                    // code for IE
                    ex = xmlDoc.transformNode(xsl);
                    jQuery('#MOHreport').html(ex);
                } else {
                    alert("Viewing report is not supported by this Browser.");
                }

            }

            // -->
        </script>

        <style>
            @media print {
                .noprint {
                    display: none !important;
                }
            }
        </style>
    </head>

    <body onload="displayReport()">
    <table width="100%" border="0" cellspacing="0" cellpadding="0" class="noprint">
        <tr>
            <td height="40" width="10%" class="Header">
                <font size="3">Billing</font>
            </td>
            <td width="90%" align="right" class="Header">
                <input type="button" name="print" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnPrint"/>"
                       onClick="window.print()">
            </td>
        </tr>
    </table>
    <div id="MOHreport"></div>

    </body>
</html>

