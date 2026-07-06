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


package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.*;

import java.util.*;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.openpdf.text.*;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Servlet that generates customized prescription PDF documents.
 *
 * <p>PDF layout is composed by {@link PrescriptionPdfComposer}. Mutating Rx fax submission
 * is handled by {@link io.github.carlos_emr.carlos.prescript.pageUtil.RxFaxPrescription2Action}.
 *
 * @see FrmPDFServlet
 * @see io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean
 * @since 2001 (McMaster University)
 */
public class FrmCustomedPDFServlet extends HttpServlet {

    private static Logger logger = MiscUtils.getLogger();
    private final PrescriptionPdfComposer prescriptionPdfComposer = SpringUtils.getBean(PrescriptionPdfComposer.class);

    /**
     * Main entry point for prescription PDF generation.
     * Parses request parameters, generates the PDF, and streams it directly to the response.
     *
     * @param req HttpServletRequest containing prescription form parameters
     * @param res HttpServletResponse to write the PDF or static error response to
     * @throws jakarta.servlet.ServletException if a servlet error occurs
     * @throws java.io.IOException if an I/O error occurs during PDF generation
     */
    @Override
    // FindSecBugs XSS_SERVLET: normal path writes binary PDF; error branches write fixed static HTML/JS.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "normal path writes binary PDF; error branches write fixed static HTML/JS")
    public void service(HttpServletRequest req, HttpServletResponse res) throws jakarta.servlet.ServletException, java.io.IOException {

        boolean responseOutputStreamOpened = false;
        try (ByteArrayOutputStream baosPDF = prescriptionPdfComposer.compose(req, this.getServletContext())) {

            StringBuilder sbFilename = new StringBuilder();
            sbFilename.append("filename_");
            sbFilename.append(".pdf");

            // set the Cache-Control header
            res.setHeader("Cache-Control", "max-age=0");
            res.setDateHeader("Expires", 0);

            res.setContentType("application/pdf");

            StringBuilder sbContentDispValue = new StringBuilder();
            sbContentDispValue.append("inline; filename=");
            sbContentDispValue.append(sbFilename);

            res.setHeader("Content-disposition", sbContentDispValue.toString());
            res.setContentLength(baosPDF.size());
            ServletOutputStream sos = res.getOutputStream();
            responseOutputStreamOpened = true;
            baosPDF.writeTo(sos);

            sos.flush();
        } catch (DocumentException dex) {
            if (responseOutputStreamOpened) {
                throw new IOException("PDF response failed after output stream was opened", dex);
            }
            // Log the detailed error for debugging
            logger.error("PDF generation error in FrmCustomedPDFServlet", dex);
            
            // Return generic error message to user
            res.setContentType("text/html");
            PrintWriter writer = res.getWriter();
            writer.println("<html><body>");
            writer.println("<h3>An error occurred generating the PDF.</h3>");
            writer.println("<p>Please try again or contact support if the problem persists.</p>");
            writer.println("</body></html>");
        } catch (java.io.FileNotFoundException dex) {
            if (responseOutputStreamOpened) {
                throw dex;
            }
            // Log the error
            logger.debug("Signature file not found", dex);
            
            res.setContentType("text/html");
            PrintWriter writer = res.getWriter();
            writer.println("<script>alert('Signature not found. Please sign the prescription.');</script>");
        }

    }

}
