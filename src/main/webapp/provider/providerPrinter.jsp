<%--

    Copyright (c) 2012- Centre de Medecine Integree

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

    This software was written for
    Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
    as part of the OSCAR McMaster EMR System


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%
    if (session.getAttribute("userrole") == null) {
        response.sendRedirect(request.getContextPath() + "/logout.jsp");
    }
    String curUser_no = (String) session.getAttribute("user");
    UserPropertyDAO propertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);

    OscarProperties oscarProps = OscarProperties.getInstance();
%>

<!DOCTYPE html>
<html lang="en">
    <head>
        <%@ include file="/includes/global-head.jspf" %>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.title"/></title>

        <script>
            function createMessageHandler() {
                var PDFObject = document.getElementById("myPdf");
                PDFObject.messageHandler = {
                    onMessage: function (msg) {
                        var select = document.getElementById("printerList");
                        select.options[select.options.length] = new Option("", 0);
                        for (index in msg) {
                            select.options[select.options.length] = new Option(msg[index], index);
                        }
                    },
                    onError: function (error, msg) {
                        alert(error.message);
                    }
                }
            }

            function setPrinter() {
                var select = document.getElementById("printerList");
                document.getElementById("defaultPrinterName" + $('input[name=labelTypeRadioName]:checked').val()).value = select.options[select.selectedIndex].text;
            }
        </script>
    </head>

    <body onload="createMessageHandler();">
    <div class="container">

        <div class="page-header-bar">
            <h4 class="page-header-title">
                <i class="fas fa-print page-header-icon"></i>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.msgdefaulPrinter"/>
            </h4>
        </div>

        <%if (oscarProps.getProperty("new_label_print") == null || oscarProps.getProperty("new_label_print").equals("false")) { %>
        <div class="alert alert-warning mt-3">
            <strong>Warning:</strong> This feature is currently disabled and requires the
            property "new_label_print" to be enabled. Please contact your support to enable this property.
        </div>
        <%}%>

        <%
            String defaultPrinterNameAppointmentReceipt = "", defaultPrinterNamePDFEnvelope = "", defaultPrinterNamePDFLabel = "", defaultPrinterNamePDFAddressLabel = "";
            String defaultPrinterNamePDFChartLabel = "", defaultPrinterNameClientLabLabel = "";
            Boolean silentPrintAppointmentReceipt = false, silentPrintPDFEnvelope = false, silentPrintPDFLabel = false;
            Boolean silentPrintPDFAddressLabel = false, silentPrintPDFChartLabel = false, silentPrintClientLabLabel = false;

            UserProperty prop = null;
            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_APPOINTMENT_RECEIPT);
            if (prop != null) {
                defaultPrinterNameAppointmentReceipt = prop.getValue();
            }
            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE);
            if (prop != null) {
                defaultPrinterNamePDFEnvelope = prop.getValue();
            }
            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_LABEL);
            if (prop != null) {
                defaultPrinterNamePDFLabel = prop.getValue();
            }
            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ADDRESS_LABEL);
            if (prop != null) {
                defaultPrinterNamePDFAddressLabel = prop.getValue();
            }
            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_CHART_LABEL);
            if (prop != null) {
                defaultPrinterNamePDFChartLabel = prop.getValue();
            }
            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_CLIENT_LAB_LABEL);
            if (prop != null) {
                defaultPrinterNameClientLabLabel = prop.getValue();
            }

            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_APPOINTMENT_RECEIPT_SILENT_PRINT);
            if (prop != null) {
                if (prop.getValue().equalsIgnoreCase("yes")) {
                    silentPrintAppointmentReceipt = true;
                }
            }

            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE_SILENT_PRINT);
            if (prop != null) {
                if (prop.getValue().equalsIgnoreCase("yes")) {
                    silentPrintPDFEnvelope = true;
                }
            }

            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_LABEL_SILENT_PRINT);
            if (prop != null) {
                if (prop.getValue().equalsIgnoreCase("yes")) {
                    silentPrintPDFLabel = true;
                }
            }

            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_ADDRESS_LABEL_SILENT_PRINT);
            if (prop != null) {
                if (prop.getValue().equalsIgnoreCase("yes")) {
                    silentPrintPDFAddressLabel = true;
                }
            }
            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_PDF_CHART_LABEL_SILENT_PRINT);
            if (prop != null) {
                if (prop.getValue().equalsIgnoreCase("yes")) {
                    silentPrintPDFChartLabel = true;
                }
            }
            prop = propertyDao.getProp(curUser_no, UserProperty.DEFAULT_PRINTER_CLIENT_LAB_LABEL_SILENT_PRINT);
            if (prop != null) {
                if (prop.getValue().equalsIgnoreCase("yes")) {
                    silentPrintClientLabLabel = true;
                }
            }

            if (request.getAttribute("status") == null) {
        %>

        <form action="${pageContext.request.contextPath}/EditPrinter.do" method="post" class="mt-3">
            <p class="fw-bold"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.setDefaultPrinterFor"/>:</p>

            <table class="table table-sm table-hover" style="max-width:700px;">
                <thead>
                    <tr>
                        <th style="width:40px;"></th>
                        <th>Type</th>
                        <th>Printer</th>
                        <th style="width:100px;" class="text-center"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.silentPrint"/></th>
                    </tr>
                </thead>
                <tbody>
                    <tr>
                        <td><input type="radio" name="labelTypeRadioName" value="0" checked class="form-check-input"></td>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.appointmentReceipt"/></td>
                        <td><input type="text" id="defaultPrinterName0" name="defaultPrinterNameAppointmentReceipt"
                                   value="<%=Encode.forHtmlAttribute(defaultPrinterNameAppointmentReceipt)%>" class="form-control form-control-sm" readonly></td>
                        <td class="text-center"><input type="checkbox" class="form-check-input"
                                   name="silentPrintAppointmentReceipt" <%=silentPrintAppointmentReceipt ? "checked" : ""%>></td>
                    </tr>
                    <tr>
                        <td><input type="radio" name="labelTypeRadioName" value="1" class="form-check-input"></td>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.PDFEnvelope"/></td>
                        <td><input type="text" id="defaultPrinterName1" name="defaultPrinterNamePDFEnvelope"
                                   value="<%=Encode.forHtmlAttribute(defaultPrinterNamePDFEnvelope)%>" class="form-control form-control-sm" readonly></td>
                        <td class="text-center"><input type="checkbox" class="form-check-input"
                                   name="silentPrintPDFEnvelope" <%=silentPrintPDFEnvelope ? "checked" : ""%>></td>
                    </tr>
                    <tr>
                        <td><input type="radio" name="labelTypeRadioName" value="2" class="form-check-input"></td>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.PDFLabel"/></td>
                        <td><input type="text" id="defaultPrinterName2" name="defaultPrinterNamePDFLabel"
                                   value="<%=Encode.forHtmlAttribute(defaultPrinterNamePDFLabel)%>" class="form-control form-control-sm" readonly></td>
                        <td class="text-center"><input type="checkbox" class="form-check-input"
                                   name="silentPrintPDFLabel" <%=silentPrintPDFLabel ? "checked" : ""%>></td>
                    </tr>
                    <tr>
                        <td><input type="radio" name="labelTypeRadioName" value="3" class="form-check-input"></td>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.PDFAddressLabel"/></td>
                        <td><input type="text" id="defaultPrinterName3" name="defaultPrinterNamePDFAddressLabel"
                                   value="<%=Encode.forHtmlAttribute(defaultPrinterNamePDFAddressLabel)%>" class="form-control form-control-sm" readonly></td>
                        <td class="text-center"><input type="checkbox" class="form-check-input"
                                   name="silentPrintPDFAddressLabel" <%=silentPrintPDFAddressLabel ? "checked" : ""%>></td>
                    </tr>
                    <tr>
                        <td><input type="radio" name="labelTypeRadioName" value="4" class="form-check-input"></td>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.PDFChartLabel"/></td>
                        <td><input type="text" id="defaultPrinterName4" name="defaultPrinterNamePDFChartLabel"
                                   value="<%=Encode.forHtmlAttribute(defaultPrinterNamePDFChartLabel)%>" class="form-control form-control-sm" readonly></td>
                        <td class="text-center"><input type="checkbox" class="form-check-input"
                                   name="silentPrintPDFChartLabel" <%=silentPrintPDFChartLabel ? "checked" : ""%>></td>
                    </tr>
                    <tr>
                        <td><input type="radio" name="labelTypeRadioName" value="5" class="form-check-input"></td>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.ClientLabLabel"/></td>
                        <td><input type="text" id="defaultPrinterName5" name="defaultPrinterNameClientLabLabel"
                                   value="<%=Encode.forHtmlAttribute(defaultPrinterNameClientLabLabel)%>" class="form-control form-control-sm" readonly></td>
                        <td class="text-center"><input type="checkbox" class="form-check-input"
                                   name="silentPrintClientLabLabel" <%=silentPrintClientLabLabel ? "checked" : ""%>></td>
                    </tr>
                </tbody>
            </table>

            <div class="mb-3">
                <select id="printerList" size="5" onclick="setPrinter();" class="form-select" style="max-width:500px;"></select>
            </div>

            <%if (oscarProps.getProperty("new_label_print") != null && oscarProps.getProperty("new_label_print").equals("true")) { %>
            <div class="d-flex gap-2 mb-3">
                <input type="submit" class="btn btn-primary btn-sm"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.btnSave"/>">
            </div>
            <%}%>

            <div class="text-muted" style="font-size:0.85em;">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.requirement"/><br>
                <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.requirementSilentPrint"/>
            </div>

            <div style="visibility: hidden; display:inline;">
                <object id="myPdf" type="application/pdf"
                        data="<%=request.getContextPath()%>/PrinterList.do?method=generatePrinterListInPDF"
                        height="100%" width="100%"></object>
            </div>
        </form>

        <% } else if ("complete".equals((String) request.getAttribute("status"))) { %>
        <div class="alert alert-success mt-3">
            <fmt:setBundle basename="oscarResources"/><fmt:message key="provider.setDefaultPrinter.msgSuccess"/>
        </div>
        <% } %>

    </div>
    </body>
</html>
