<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
    Copyright (c) 2008-2012 Indivica Inc.

    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; version 2
    of the License.

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
  Purpose: Supports billingLreport in the Ontario billing workflow.
  Expected request model data includes: lreportModel.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
--%>
<%@ page language="java" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>


<%@ page errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%--
    File-read + XSL-name resolution moved to BillingLegacyReport2Action; the
    ${lreportModel} request attribute now exposes filename, xslName, and
    fileContents. _admin.billing w is enforced by the action.
--%>

<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="${pageContext.request.contextPath}/js/global.js"></script>
        <title>MOH Report</title>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/billing.css">
        <link rel="stylesheet" type="text/css" media="all"
              href="${pageContext.request.contextPath}/share/css/extractedFromPages.css"/>

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
                var cpath = "${pageContext.request.contextPath}";
                sname = cpath + "/billing/CA/ON/<carlos:encode value='${lreportModel.xslName}' context='javaScript'/>.xsl";

                xml = '<carlos:encode value="${lreportModel.fileContents}" context="javaScript"/>';
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
                    var mohReport = document.getElementById("MOHreport");
                    mohReport.innerHTML = '';
                    mohReport.appendChild(resultDocument);
                } else if (window.ActiveXObject) {
                    // code for IE - uses transformNode which returns a string
                    ex = xmlDoc.transformNode(xsl);
                    document.getElementById('MOHreport').innerHTML = ex;
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
                <input type="button" name="print" value="<fmt:message key="global.btnPrint"/>"
                       onClick="window.print()">
            </td>
        </tr>
    </table>
    <div id="MOHreport"></div>

    </body>
</html>

