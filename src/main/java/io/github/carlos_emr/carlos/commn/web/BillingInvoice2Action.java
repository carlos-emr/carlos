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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.commn.service.PdfRecordPrinter;
import io.github.carlos_emr.carlos.managers.BillingONManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * @author mweston4
 */
import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

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
        String actionResult = "failure";

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }


        if (invoiceNo != null) {
            // Sanitize invoiceNo to prevent HTTP Response Splitting
            // Remove any characters that could be used to inject headers
            String sanitizedInvoiceNo = sanitizeInvoiceNumber(invoiceNo);

            // Check if we have a valid invoice number after sanitization
            if (sanitizedInvoiceNo.isEmpty()) {
                MiscUtils.getLogger().error("Invalid invoice number - no digits found: " + invoiceNo);
                return "error";  // or handle appropriately
            }

            try {
                int invoiceId = Integer.parseInt(sanitizedInvoiceNo);

                response.setContentType("application/pdf"); // octet-stream
                response.setHeader("Content-Disposition", "attachment; filename=\"BillingInvoice" + sanitizedInvoiceNo + "_" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf\"");
                
                boolean bResult = processPrintPDF(invoiceId, request.getLocale(), response.getOutputStream());
                
                if (bResult) {
                    actionResult = "success";
                }
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("Invoice number too large or invalid: " + sanitizedInvoiceNo, e);
                return "error";
            }
        }
        return actionResult;
    }

    private String sanitizeInvoiceNumber(String input) {
        if (input == null) return "";
    
        // Remove all non-digits for invoice numbers
        return input.replaceAll("[^0-9]", "").trim();
    }

    public String getListPrintPDF() throws IOException {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String actionResult = "failure";
        String[] invoiceNos = request.getParameterValues("invoiceAction");

        ArrayList<Object> fileList = new ArrayList<Object>();
        OutputStream fos = null;
        if (invoiceNos != null) {
            for (String invoiceNoStr : invoiceNos) {
                try {
                    Integer invoiceNo = Integer.parseInt(invoiceNoStr);
                    String filename = "BillingInvoice" + invoiceNo + "_" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf";
                    String savePath = OscarProperties.getInstance().getProperty("INVOICE_DIR") + "/" + filename;
                    fos = new FileOutputStream(savePath);
                    processPrintPDF(invoiceNo, request.getLocale(), fos);
                    fileList.add(savePath);
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Error", e);
                } finally {
                    if (fos != null) fos.close();
                }
            }
        }
        if (!fileList.isEmpty()) {
            response.setContentType("application/pdf"); // octet-stream
            response.setHeader("Content-Disposition", "attachment; filename=\"BillingInvoices" + "_" + UtilDateUtilities.getToday("yyyy-MM-dd.hh.mm.ss") + ".pdf\"");
            ConcatPDF.concat(fileList, response.getOutputStream());
            actionResult = "listSuccess";
        }

        return actionResult;
    }

    /*
     * The sendInvoiceEmailNotification method in BillingManager is no longer supported.
     * For more details, please refer to the sendInvoiceEmailNotification method.
     */
    @Deprecated
    public String sendEmail() {
        throw new UnsupportedOperationException("This method is no longer supported.");


        // if (invoiceNo != null) {

    }

    /*
     * The sendInvoiceEmailNotification method in BillingManager is no longer supported.
     * For more details, please refer to the sendInvoiceEmailNotification method.
     */
    @Deprecated
    public String sendListEmail() {
        throw new UnsupportedOperationException("This method is no longer supported.");


        // if (invoiceNos != null) {
        //     for (String invoiceNoStr : invoiceNos) {

    }

    private boolean processPrintPDF(Integer invoiceNo, Locale locale, OutputStream os) {

        boolean bResult = false;

        if (invoiceNo != null) {
            //Create PDF of the invoice
            PdfRecordPrinter printer = new PdfRecordPrinter(os);
            printer.printBillingInvoice(invoiceNo, locale);

            BillingONManager billingManager = SpringUtils.getBean(BillingONManager.class);
            billingManager.addPrintedBillingComment(invoiceNo, locale);
            bResult = true;
        }

        return bResult;
    }

}
