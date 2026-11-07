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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.openpdf.text.DocumentException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Streams a customized prescription PDF for printing or preview. It is read-only: the document is
 * composed by {@link PrescriptionPdfComposer} and written to the response, nothing else.
 *
 * <p>Faxing used to be handled here as well, on any HTTP method. It moved to the POST-only
 * {@code io.github.carlos_emr.carlos.prescript.pageUtil.RxFaxPrescription2Action} (issue #3108), and a
 * fax request that still reaches this servlet is refused.</p>
 *
 * @see PrescriptionPdfComposer
 * @see FrmPDFServlet
 * @since 2001 (McMaster University)
 */
public class FrmCustomedPDFServlet extends HttpServlet {

    private static final Logger logger = MiscUtils.getLogger();
    private final PrescriptionPdfComposer prescriptionPdfComposer = SpringUtils.getBean(PrescriptionPdfComposer.class);

    /**
     * Streams the prescription PDF described by the request parameters, inline.
     *
     * @param req HttpServletRequest containing the prescription form parameters
     * @param res HttpServletResponse the PDF is written to
     * @throws jakarta.servlet.ServletException if a servlet error occurs
     * @throws java.io.IOException if an I/O error occurs during PDF generation
     */
    @Override
    // FindSecBugs XSS_SERVLET: the refusal and error replies are fixed HTML; the PDF branch writes binary content
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "the refusal and error replies are fixed HTML; the PDF branch writes binary content")
    public void service(HttpServletRequest req, HttpServletResponse res) throws jakarta.servlet.ServletException, java.io.IOException {

        // Faxing moved to the POST-only RxFaxPrescription2Action. A fax request that still arrives here
        // (a prescription page loaded before the move, or a hand-made link) is refused rather than
        // rendered: nothing is written or queued, and the page's fax-failure marker lets it recover.
        if ("oscarRxFax".equals(req.getParameter("__method"))) {
            res.setStatus(HttpServletResponse.SC_GONE);
            res.setContentType("text/html");
            PrintWriter writer = res.getWriter();
            writer.println("<div id='fax-failure'><h3>Error: this page is out of date. Reload it, then fax again.</h3></div>");
            writer.flush();
            return;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(req);
        // The prescription named by scriptId is loaded ONCE and shared by the signature gate and the QR code.
        Prescription prescription = prescriptionPdfComposer.requestedPrescription(req);
        byte[] signatureImage = prescriptionPdfComposer.resolveSignatureImage(req, loggedInInfo, prescription, false);

        boolean responseOutputStreamOpened = false;
        try (ByteArrayOutputStream baosPDF = prescriptionPdfComposer.compose(req, signatureImage, prescription)) {
            StringBuilder sbFilename = new StringBuilder();
            sbFilename.append("filename_");
            sbFilename.append(".pdf");

            // set the Cache-Control header
            res.setHeader("Cache-Control", "max-age=0");
            res.setDateHeader("Expires", 0);

            res.setContentType("application/pdf");

            // The Content-disposition value will be inline
            StringBuilder sbContentDispValue = new StringBuilder();
            sbContentDispValue.append("inline; filename="); // inline - display
            // the pdf file
            // directly rather
            // than open/save
            // selection
            // sbContentDispValue.append("; filename=");
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
            logger.error("PDF generation error in FrmCustomedPDFServlet ({})", dex.getClass().getSimpleName());
            
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
            logger.debug("Signature file not found ({})", dex.getClass().getSimpleName());
            
            res.setContentType("text/html");
            PrintWriter writer = res.getWriter();
            writer.println("<script>alert('Signature not found. Please sign the prescription.');</script>");
        }

    }
}
