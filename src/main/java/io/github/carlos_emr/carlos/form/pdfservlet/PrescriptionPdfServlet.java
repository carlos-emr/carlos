/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under the terms of version 2
 * of the License, or (at your option) any later version.
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * HTTP entry point for prescription PDFs, validating satellite-clinic print input before rendering.
 * Fax requests retain the renderer's method, authorization, signature and record-binding checks:
 * their clinic header is selected from server-side records before its address is parsed.
 */
public class PrescriptionPdfServlet extends FrmCustomedPDFServlet {
    private static final long serialVersionUID = 1L;
    private static final int MIN_SATELLITE_FIELDS = 6;

    @Override
    public void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (!"oscarRxFax".equals(request.getParameter("__method"))
                && Boolean.parseBoolean(request.getParameter("useSC"))
                && !hasSatelliteClinicFields(request.getParameter("scAddress"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid satellite clinic address");
            return;
        }
        super.service(request, response);
    }

    private static boolean hasSatelliteClinicFields(String address) {
        if (address == null) {
            return false;
        }
        String[] sections = address.split("</b>", 2);
        // One leading separator, three clinic lines, telephone and fax; empty optional fields count.
        return sections.length == 2 && sections[1].startsWith("<br>")
                && sections[1].split("<br>", -1).length >= MIN_SATELLITE_FIELDS;
    }
}
