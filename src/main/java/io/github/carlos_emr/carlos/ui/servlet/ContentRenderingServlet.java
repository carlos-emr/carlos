/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.ui.servlet;

import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.web.PrescriptionQrCodeUIBean;

import io.github.carlos_emr.carlos.log.LogAction;

/**
 * This servlet requires a parameter called "source" which should signify where to get the image from.
 * An optional parameter is "download" where if the parameter is present it will download the item instead of rendering it. (the value of the parameter is not significant, i.e. download=true and download=false will both cause it to download).
 * <p>
 * Example, source=prescriptionQrCode. Depending on the source, you can optionally add more parameters, as examples a prescriptionQrCode may need a
 * prescriptionId=5&amp;download=true
 */
public final class ContentRenderingServlet extends HttpServlet {
    private static Logger logger = MiscUtils.getLogger();

    public enum Source {
        prescriptionQrCode
    }

    public class Content {
        public String contentType;
        public byte[] data;
    }

    @Override
    public final void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
            Content content = getContent(request, loggedInInfo);

            if (content != null) {
                String download = request.getParameter("download");
                if (download != null) response.setContentType("application/octet-stream");
                else response.setContentType(content.contentType);

                logger.debug("Returning contentType=" + content.contentType + ", download override=" + response.getContentType());
                logger.debug("Returning contentLength=" + content.data.length);

                response.setContentLength(content.data.length);
                OutputStream os = response.getOutputStream();
                os.write(content.data);
                os.flush();
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, Encode.forHtml(request.getRequestURI()));
            }
        } catch (Exception e) {
            if (e.getCause() instanceof SocketException) {
                logger.warn("An error we can't handle that's expected infrequently. " + e.getMessage());
            } else {
                logger.error("Unexpected error. qs=" + LogSanitizer.sanitize(request.getQueryString()), e);
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, Encode.forHtml(request.getRequestURI()));
                }
            }
        }
    }

    private Content getContent(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String source = request.getParameter("source");

        try {
            if (Source.prescriptionQrCode.name().equals(source))
                return (getPrescriptionQrCodeContent(request, loggedInInfo));
        } catch (Exception e) {
            logger.error("Unexpected error.", e);
        }

        return null;
    }

    private Content getPrescriptionQrCodeContent(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        // for prescriptions we need prescriptionId.
        int prescriptionId = Integer.parseInt(request.getParameter("prescriptionId"));

        Content content = new Content();
        content.contentType = getServletContext().getMimeType("prescription_" + prescriptionId + "qr_code.png");
        content.data = PrescriptionQrCodeUIBean.getPrescriptionHl7QrCodeImage(prescriptionId);

        LogAction.addLog(loggedInInfo.getLoggedInProviderNo(), getClass().getSimpleName(), "getPrescriptionQrCodeContent", "prescriptionId=" + prescriptionId);

        return (content);
    }
}
