/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.web;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.service.PdfRecordPrinter;
import io.github.carlos_emr.carlos.managers.BillingONManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * @author mweston4
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LogSafe;

public class BillingInvoice2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("getPrintPDF".equals(method)) {
            return getPrintPDF();
        } else if ("getListPrintPDF".equals(method)) {
            return getListPrintPDF();
        } else if ("sendEmail".equals(method)) {
            return sendEmail();
        } else if ("sendEmail".equals(method)) {
            return sendEmail();
        }
        return getPrintPDF();
    }

    public String getPrintPDF() throws IOException {
        String invoiceNo = request.getParameter("invoiceNo");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }


        if (invoiceNo != null) {
            // Sanitize invoiceNo to prevent HTTP Response Splitting
            // Remove any characters that could be used to inject headers
            String sanitizedInvoiceNo = sanitizeInvoiceNumber(invoiceNo);

            // Check if we have a valid invoice number after sanitization
            if (sanitizedInvoiceNo.isEmpty()) {
                return failPdfResponse("Invalid invoice number - no digits found: " + LogSafe.sanitize(invoiceNo), null);
            }

            try {
                int invoiceId = Integer.parseInt(sanitizedInvoiceNo);

                // Buffer before touching the servlet response: PdfRecordPrinter logs and swallows
                // Jasper failures, so direct streaming can turn Struts error JSPs into corrupt PDF downloads.
                ByteArrayOutputStream pdfBuffer = new ByteArrayOutputStream();
                boolean bResult;
                try {
                    bResult = renderPrintPDF(invoiceId, request.getLocale(), pdfBuffer);
                } catch (RuntimeException e) {
                    return failPdfResponse("Billing invoice PDF generation failed for invoiceNo " + invoiceId, e);
                } catch (LinkageError e) {
                    return failPdfResponse("Billing invoice PDF exporter dependency failure for invoiceNo " + invoiceId, e);
                }
                
                if (bResult && isPdf(pdfBuffer)) {
                    try {
                        response.setContentType("application/pdf"); // octet-stream
                        response.setHeader("Content-Disposition", "attachment; filename=\"BillingInvoice" + sanitizedInvoiceNo + "_" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf\"");
                        pdfBuffer.writeTo(response.getOutputStream());
                        addPrintedBillingComment(invoiceId, request.getLocale());
                    } catch (RuntimeException | IOException e) {
                        return failPdfResponse("Billing invoice PDF response write failed for invoiceNo " + invoiceId, e);
                    }

                    // Direct-response PDF actions must not return JSP result names. See:
                    // https://github.com/carlos-emr/carlos/issues/2064
                    // Returning a named result would forward a JSP into the download and corrupt the PDF.
                    return NONE;
                }
                return failPdfResponse("Billing invoice PDF generation produced no PDF output for invoiceNo " + invoiceId, null);
            } catch (NumberFormatException e) {
                return failPdfResponse("Invoice number too large or invalid: " + sanitizedInvoiceNo, e);
            }
        }
        return failPdfResponse("Billing invoice PDF generation requested without invoiceNo", null);
    }

    private String sanitizeInvoiceNumber(String input) {
        if (input == null) return "";
    
        // Remove all non-digits for invoice numbers
        return input.replaceAll("[^0-9]", "").trim();
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    public String getListPrintPDF() throws IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String[] invoiceNos = request.getParameterValues("invoiceAction");

        ArrayList<Object> fileList = new ArrayList<Object>();
        ArrayList<Integer> renderedInvoiceNos = new ArrayList<Integer>();
        if (invoiceNos != null) {
            for (String invoiceNoStr : invoiceNos) {
                try {
                    Integer invoiceNo = Integer.parseInt(invoiceNoStr);
                    String filename = "BillingInvoice" + invoiceNo + "_" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf";
                    String savePath = CarlosProperties.getInstance().getProperty("INVOICE_DIR") + "/" + filename;
                    try (OutputStream fos = new FileOutputStream(PathValidationUtils.resolveTrustedPath(new File(savePath)))) {
                        if (renderPrintPDF(invoiceNo, request.getLocale(), fos)) {
                            fileList.add(savePath);
                            renderedInvoiceNos.add(invoiceNo);
                        }
                    }
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                }
            }
        }
        if (!fileList.isEmpty()) {
            try {
                // Merge to memory first for the same reason as the single-invoice path above:
                // Struts result handling must only be bypassed after real PDF bytes exist.
                ByteArrayOutputStream pdfBuffer = new ByteArrayOutputStream();
                ConcatPDF.concat(fileList, pdfBuffer);
                if (isPdf(pdfBuffer)) {
                    try {
                        response.setContentType("application/pdf"); // octet-stream
                        response.setHeader("Content-Disposition", "attachment; filename=\"BillingInvoices" + "_" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf\"");
                        pdfBuffer.writeTo(response.getOutputStream());
                        for (Integer invoiceNo : renderedInvoiceNos) {
                            addPrintedBillingComment(invoiceNo, request.getLocale());
                        }
                    } catch (RuntimeException | IOException e) {
                        return failPdfResponse("Billing invoice list PDF response write failed", e);
                    }
                    // Direct-response PDF actions must not return JSP result names. See:
                    // https://github.com/carlos-emr/carlos/issues/2064
                    return NONE;
                }
                return failPdfResponse("Billing invoice list PDF merge produced no PDF output for " + renderedInvoiceNos.size() + " invoice(s)", null);
            } catch (Exception e) {
                return failPdfResponse("Billing invoice list PDF merge failed", e);
            }
        }

        return failPdfResponse("Billing invoice list PDF generation produced no invoices", null);
    }

    /*
     * The sendInvoiceEmailNotification method in BillingManager is no longer supported.
     * For more details, please refer to the sendInvoiceEmailNotification method.
     */
    @Deprecated
    public String sendEmail() {
        throw new UnsupportedOperationException("This method is no longer supported.");
        //  if(!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
        //  	throw new SecurityException("missing required sec object (_billing)");
        //  }

        // String invoiceNoStr = request.getParameter("invoiceNo");
        // Integer invoiceNo = Integer.parseInt(invoiceNoStr);
        // Locale locale = request.getLocale();
        // String actionResult = "failure";

        // if (invoiceNo != null) {
        //     BillingONManager billingManager = (BillingONManager) SpringUtils.getBean(BillingONManager.class);
        //     billingManager.sendInvoiceEmailNotification(invoiceNo, locale);
        //     billingManager.addEmailedBillingComment(invoiceNo, locale); 
        //     actionResult = "success";
        // }

        // ActionRedirect redirect = new ActionRedirect(mapping.findForward(actionResult));
        // redirect.addParameter("billing_no", invoiceNo);
        // return redirect;
    }

    /*
     * The sendInvoiceEmailNotification method in BillingManager is no longer supported.
     * For more details, please refer to the sendInvoiceEmailNotification method.
     */
    @Deprecated
    public String sendListEmail() {
        throw new UnsupportedOperationException("This method is no longer supported.");
        //  if(!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "w", null)) {
        //  	throw new SecurityException("missing required sec object (_billing)");
        //  }

        // String actionResult = "failure";       
        // String[] invoiceNos = request.getParameterValues("invoiceAction");
        // Locale locale = request.getLocale();

        // if (invoiceNos != null) {
        //     for (String invoiceNoStr : invoiceNos) {
        //         Integer invoiceNo = Integer.parseInt(invoiceNoStr);
        //         BillingONManager billingManager = (BillingONManager) SpringUtils.getBean(BillingONManager.class);
        //         billingManager.sendInvoiceEmailNotification(invoiceNo, locale);
        //         billingManager.addEmailedBillingComment(invoiceNo, locale);               
        //     }
        //     actionResult = "listSuccess";
        // }

        // return mapping.findForward(actionResult);
    }

    private boolean renderPrintPDF(Integer invoiceNo, Locale locale, OutputStream os) {

        boolean bResult = false;

        if (invoiceNo != null) {
            //Create PDF of the invoice
            PdfRecordPrinter printer = new PdfRecordPrinter(os);
            printer.printBillingInvoice(invoiceNo, locale);
            bResult = true;
        }

        return bResult;
    }

    private void addPrintedBillingComment(Integer invoiceNo, Locale locale) {
        BillingONManager billingManager = SpringUtils.getBean(BillingONManager.class);
        try {
            billingManager.addPrintedBillingComment(invoiceNo, locale);
        } catch (RuntimeException e) {
            // The PDF response is the user-visible result; a non-critical audit/comment failure
            // must not let Struts render an error JSP over the binary download. See issue #2064.
            MiscUtils.getLogger().error("Failed to record printed billing comment for invoiceNo {}", invoiceNo, e);
        }
    }

    private String failPdfResponse(String logMessage, Throwable e) throws IOException {
        String safeLogMessage = LogSafe.sanitize(logMessage);
        if (e == null) {
            MiscUtils.getLogger().error(safeLogMessage); // NOSONAR javasecurity:S5145 - wrapper message sanitized at helper boundary
        } else {
            MiscUtils.getLogger().error(safeLogMessage, e); // NOSONAR javasecurity:S5145 - wrapper message sanitized at helper boundary
        }
        if (!response.isCommitted()) {
            // Dedicated PDF actions own their error response too. Returning a named result here
            // lets Struts render errorpage.jsp with "CARLOS Error: 0" instead of a real status.
            // See PR #2043 and issue #2064.
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unable to generate billing invoice PDF");
        }
        return NONE;
    }

    private boolean isPdf(ByteArrayOutputStream pdfBuffer) {
        byte[] bytes = pdfBuffer.toByteArray();
        return bytes.length >= 4
                && bytes[0] == '%'
                && bytes[1] == 'P'
                && bytes[2] == 'D'
                && bytes[3] == 'F';
    }

}
