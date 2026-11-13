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
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.Strings;

import io.github.carlos_emr.carlos.eform.util.ChartRenderFailure;
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
 * page of its own, in the viewer's language, with the same status ({@code Retry-After} on 503) and a
 * message saying what went wrong: the drawing service is busy or not running (with a Retry button), the
 * stored values cannot be drawn and why (422, naming the measurement type or lab test, with no Retry
 * button, since retrying cannot help), or an unexpected problem that has been logged (500). It shows no
 * stored value and nothing else about the patient. The page looks like the CARLOS error page: the Bootstrap copy
 * bundled in the WAR, a light grey background, the message as a warning (503, 500) or an error (422), and Retry as
 * a secondary button.</p>
 *
 * @since 2026-11-12
 */
public final class GraphFailurePage {

    /** The message for stored values the graph cannot be drawn from (422): no retry can help. */
    static final String INVALID_DATA_KEY = "clinical.graph.invalidData";
    static final String RETRY_KEY = "clinical.graph.retry";
    static final String TITLE_KEY = "clinical.graph.alt";
    static final String BUSY_KEY = "clinical.graph.busy";
    static final String SERVICE_DOWN_KEY = "clinical.graph.serviceDown";
    static final String FAILED_KEY = "clinical.graph.failed";
    /** The Bootstrap copy bundled in the WAR, which the CARLOS error page ({@code errorpage.jsp}) also uses. */
    static final String STYLESHEET = "/library/bootstrap/5.3.8/css/bootstrap.min.css";

    /**
     * What the page says for one failure: a bundle key and its English fallback, the measurement type or lab
     * test it names (or {@code null}), and whether a Retry button can help. The keys added for #5853's wording
     * are MessageFormat patterns; the two older ones are plain text.
     */
    record Message(String key, String english, String subject, boolean pattern, boolean retry) {
    }

    private GraphFailurePage() {
    }

    /**
     * Whether the graph was requested as a page (a window or frame) rather than as an image. Browsers
     * say so in {@code Sec-Fetch-Dest}; without it, a request that accepts HTML is a page load, since an
     * image load asks for {@code image/*} only. Both are matched ignoring case: media types are
     * case-insensitive (RFC 9110), though browsers send both in lower case.
     */
    static boolean isPageLoad(HttpServletRequest request) {
        String destination = request.getHeader("Sec-Fetch-Dest");
        if (destination != null && !destination.isBlank()) {
            String kind = destination.trim();
            return Strings.CI.equalsAny(kind, "document", "iframe", "frame");
        }
        return Strings.CI.contains(request.getHeader("Accept"), "text/html");
    }

    /**
     * The message for {@code failure}, from the same classification that picks its status
     * ({@link ChartRenderFailure#code}).
     */
    static Message messageFor(Throwable failure) {
        if (failure instanceof GraphDataException data) {
            GraphDataException.Problem problem = data.problem();
            boolean named = data.subject() != null && !data.subject().isBlank();
            // A prescription problem names no measurement; the others need the name to read well.
            if (named || problem == GraphDataException.Problem.PRESCRIPTION_START) {
                return new Message(problem.key(), problem.english(), data.subject(), true, false);
            }
        }
        return switch (ChartRenderFailure.code(failure)) {
            case "BUSY" -> new Message(BUSY_KEY,
                    "The chart drawing service is busy. Try again in a moment.", null, true, true);
            case "UNAVAILABLE", "INTERRUPTED" -> new Message(SERVICE_DOWN_KEY,
                    "The chart drawing service is not available right now. Try again in a moment, and contact"
                            + " your administrator if this keeps happening.", null, true, true);
            case "INVALID_DATA" -> new Message(INVALID_DATA_KEY,
                    "This graph cannot be drawn from the stored values. Contact your administrator.", null, false,
                    false);
            default -> new Message(FAILED_KEY,
                    "This graph could not be drawn because of an unexpected problem. The problem has been logged;"
                            + " contact your administrator if it keeps happening.", null, true, true);
        };
    }

    /**
     * Answers a graph that could not be drawn: a short page for a page load, the bare status for an
     * image load. The response must not be committed yet.
     *
     * @param failure what went wrong; it picks the status (503 busy or unavailable, 422 invalid data, 500
     *                otherwise) and the message
     * @param imageMessage the error message for an image load, as before
     */
    static void send(HttpServletRequest request, HttpServletResponse response, Throwable failure,
                     String imageMessage) throws IOException {
        int status = ChartRenderFailure.status(failure);
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
        page.write("</title>\n");
        String context = request.getContextPath();
        page.write("<link rel=\"shortcut icon\" href=\"");
        page.write(SafeEncode.forHtmlAttribute(context + "/images/favicon.ico"));
        page.write("\">\n<link rel=\"stylesheet\" href=\"");
        page.write(SafeEncode.forHtmlAttribute(context + STYLESHEET));
        page.write("\">\n<style>body { background-color: #F5F5F5; }</style>\n</head>\n<body>\n");
        page.write("<div class=\"container text-center mt-5\">\n");
        // Stored values that cannot be drawn are an error; a busy or stopped service and the rest, a warning.
        String alert = status == 422 ? "alert alert-danger" : "alert alert-warning";
        page.write("<p role=\"alert\" id=\"graphUnavailable\" class=\"" + alert + "\">");
        Message message = messageFor(failure);
        // The whole sentence is encoded, so a measurement type's or test's name is never markup.
        page.write(SafeEncode.forHtmlContent(text(labels, shown, message)));
        page.write("</p>\n");
        if (message.retry()) {
            page.write("<button type=\"button\" class=\"btn btn-secondary\" onclick=\"window.location.reload()\">");
            page.write(SafeEncode.forHtmlContent(label(labels, RETRY_KEY, "Retry")));
            page.write("</button>\n");
        }
        page.write("</div>\n</body>\n</html>\n");
        page.flush();
    }

    /** The message's sentence in the bundle's language, with the measurement type or test name in it. */
    static String text(ResourceBundle labels, Locale shown, Message message) {
        String wording = label(labels, message.key(), message.english());
        if (!message.pattern()) {
            return wording;
        }
        return new MessageFormat(wording, shown).format(new Object[]{message.subject()});
    }

    static String label(ResourceBundle labels, String key, String fallback) {
        try {
            return labels.getString(key);
        } catch (MissingResourceException missing) {
            return fallback;
        }
    }
}
