/**
 * Copyright (c) 2012- Centre de Medecine Integree
 * <p>
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
 * This software was written for
 * Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
 * as part of the OSCAR McMaster EMR System
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.printer;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.openpdf.text.pdf.PdfAction;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Struts2 action that generates a minimal PDF with embedded JavaScript to retrieve the list
 * of available printers from the PDF viewer's host environment.
 *
 * <p>Creates a nearly empty PDF document with an open-action JavaScript that calls
 * {@code app.printerNames} and posts the result back to the host container via
 * {@code hostContainer.postMessage()}. This enables the web application to discover
 * printers available to the client's PDF viewer (e.g., Adobe Acrobat).</p>
 *
 * <p>Uses OpenPDF ({@code org.openpdf.*}) for PDF generation with {@link PdfAction}
 * for JavaScript injection.</p>
 *
 * @since 2015-08-12
 */
public class PrinterList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    /**
     * Delegates to {@link #generatePrinterListInPDF()}.
     *
     * @return String {@code null} (response is written directly)
     * @throws IOException if PDF generation or response writing fails
     */
    public String execute() throws IOException {
        return generatePrinterListInPDF();
    }

    /**
     * Generates a PDF with embedded JavaScript to query available printers and streams it
     * to the HTTP response.
     *
     * <p>The JavaScript uses the PDF viewer's {@code app.printerNames} API and posts
     * the result to the host container. The PDF is served with no-cache headers
     * and {@code application/pdf} content type.</p>
     *
     * @return String {@code null} (response is written directly as PDF)
     * @throws IOException if response output stream writing fails
     */
    public String generatePrinterListInPDF() throws IOException {
        ByteArrayOutputStream baos = null;
        Document document = null;
        PdfWriter writer = null;
        OutputStream os = null;

        try {
            document = new Document();
            baos = new ByteArrayOutputStream();
            writer = PdfWriter.getInstance(document, baos);
            document.open();

            document.add(new Paragraph(" "));

            String javascript
                    = "this.disclosed = true;"
                    + "if (this.external && this.hostContainer) {"
                    + "   try{"
                    + "      this.hostContainer.postMessage(app.printerNames);"
                    + "   }"
                    + "   catch(e){"
                    + "      app.alert(e.message);"
                    + "   }"
                    + "}";

            PdfAction action
                    = PdfAction.javaScript(javascript, writer);

            writer.setOpenAction(action);

            document.close();

            response.setHeader("Expires", "0");
            response.setHeader("Cache-Control",
                    "must-revalidate, post-check=0, pre-check=0");
            response.setHeader("Pragma", "public");
            response.setContentType("application/pdf");

            response.setContentLength(baos.size());
            os = response.getOutputStream();
            baos.writeTo(os);
            os.flush();
        } catch (DocumentException e) {
            throw new IOException(e.getMessage());
        } finally {
            if (os != null) {
                os.close();
            }
            if (document != null) {
                document.close();
            }
            if (writer != null) {
                writer.close();
            }
            if (baos != null) {
                baos.close();
            }
        }
        return null;
    }

}
