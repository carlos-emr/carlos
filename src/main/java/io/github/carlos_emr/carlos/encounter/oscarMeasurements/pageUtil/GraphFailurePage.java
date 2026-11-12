/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * What a graph window shows when its graph cannot be drawn (#5853).
 *
 * <p>Graphs are drawn by the render browser, which is shared with eForm print and fax, so a graph can
 * be refused while that browser is busy or down. An embedded graph ({@code <img>}) gets only the HTTP
 * status, and its page shows a message and a Retry button. Three places instead open the graph URL
 * itself in a window (measurement history, the Health Tracker's full graph, a flowsheet plot); there
 * the container's error page showed only "CARLOS Error: 503". For such a page load this writes a short
 * page of its own: the same status (with {@code Retry-After} on 503), the existing "Graph unavailable"
 * message and a Retry button, in the viewer's language. It shows nothing about the patient.</p>
 *
 * @since 2026-11-12
 */
public final class GraphFailurePage {

    static final String MESSAGE_KEY = "clinical.graph.unavailable";
    static final String RETRY_KEY = "clinical.graph.retry";
    static final String TITLE_KEY = "clinical.graph.alt";

    private GraphFailurePage() {
    }

    /**
     * Whether the graph was requested as a page (a window or frame) rather than as an image. Browsers
     * say so in {@code Sec-Fetch-Dest}; without it, a request that accepts HTML is a page load, since an
     * image load asks for {@code image/*} only. Browsers send both values in lower case, as the Fetch
     * standard and the HTML media type spell them.
     */
    static boolean isPageLoad(HttpServletRequest request) {
        String destination = request.getHeader("Sec-Fetch-Dest");
        if (destination != null && !destination.isBlank()) {
            String kind = destination.trim();
            return kind.equals("document") || kind.equals("iframe") || kind.equals("frame");
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    /**
     * Answers a graph that could not be drawn: a short page for a page load, the bare status for an
     * image load. The response must not be committed yet.
     *
     * @param status the failure's status (503 busy or unavailable, 422 invalid data, 500 otherwise)
     * @param imageMessage the error message for an image load, as before
     */
    static void send(HttpServletRequest request, HttpServletResponse response, int status, String imageMessage)
            throws IOException {
        if (status == HttpServletResponse.SC_SERVICE_UNAVAILABLE) {
            response.setHeader("Retry-After", "5");
        }
        if (!isPageLoad(request)) {
            response.sendError(status, imageMessage);
            return;
        }
        ResourceBundle labels = ResourceBundle.getBundle("oscarResources", LocaleUtils.resolveBundleLocale(request));
        Locale shown = labels.getLocale();
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        PrintWriter page = response.getWriter();
        page.write("<!DOCTYPE html>\n<html lang=\"");
        page.write(SafeEncode.forHtmlAttribute(shown.getLanguage().isEmpty() ? "en" : shown.getLanguage()));
        page.write("\">\n<head><meta charset=\"UTF-8\"><title>");
        page.write(SafeEncode.forHtmlContent(label(labels, TITLE_KEY, "Clinical graph")));
        page.write("</title></head>\n<body>\n<p role=\"alert\" id=\"graphUnavailable\">");
        page.write(SafeEncode.forHtmlContent(label(labels, MESSAGE_KEY,
                "Graph unavailable. Retry or contact your administrator.")));
        page.write("</p>\n<button type=\"button\" onclick=\"window.location.reload()\">");
        page.write(SafeEncode.forHtmlContent(label(labels, RETRY_KEY, "Retry")));
        page.write("</button>\n</body>\n</html>\n");
        page.flush();
    }

    static String label(ResourceBundle labels, String key, String fallback) {
        try {
            return labels.getString(key);
        } catch (MissingResourceException missing) {
            return fallback;
        }
    }
}
