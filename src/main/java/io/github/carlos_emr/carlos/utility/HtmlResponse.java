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
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Centralized response handling for stored HTML content that must remain
 * writer-backed so response filters can append CARLOS logout listeners.
 *
 * <p>Callers must perform route-specific authorization and decide whether
 * rendering stored HTML is appropriate before using this class.</p>
 *
 * @since 2026-05-20
 */
public final class HtmlResponse {
    /** Fallback for stored HTML that has no charset declaration or declares an unsupported charset. */
    private static final Charset DEFAULT_HTML_CHARSET = StandardCharsets.UTF_8;
    private static final String DEFAULT_HTML_CONTENT_TYPE = "text/html";
    private static final int BUFFER_SIZE = 4096;

    private HtmlResponse() {
    }

    /**
     * Resolves the charset parameter from a Content-Type header value.
     *
     * @param contentType response Content-Type value, optionally including charset
     * @return declared charset, or UTF-8 when absent or invalid
     */
    public static Charset resolveCharset(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_HTML_CHARSET;
        }

        String[] parameters = contentType.split(";");
        for (int i = 1; i < parameters.length; i++) {
            String parameter = parameters[i].trim();
            int equals = parameter.indexOf('=');
            if (equals < 0 || !"charset".equalsIgnoreCase(parameter.substring(0, equals).trim())) {
                continue;
            }

            String charsetName = stripQuotes(parameter.substring(equals + 1).trim());
            if (charsetName.isBlank()) {
                return DEFAULT_HTML_CHARSET;
            }
            try {
                return Charset.forName(charsetName);
            } catch (IllegalArgumentException e) {
                return DEFAULT_HTML_CHARSET;
            }
        }

        return DEFAULT_HTML_CHARSET;
    }

    /**
     * Writes already-decoded stored HTML through the servlet writer.
     *
     * @param response servlet response to write to
     * @param contentType HTML content type, optionally including charset
     * @param html stored HTML body
     * @throws IOException when response writing fails
     */
    @SuppressWarnings({"XSS_SERVLET", "findsecbugs:XSS_SERVLET"})
    public static void writeStoredHtml(HttpServletResponse response, String contentType, String html)
            throws IOException {
        prepareHtmlResponse(response, contentType);
        PrintWriter writer = response.getWriter();
        // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep, java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- intentional stored HTML rendering; callers must authorize routes before invoking
        writer.write(html == null ? "" : html);
    }

    /**
     * Decodes stored HTML bytes using the declared charset and writes through
     * the servlet writer.
     *
     * @param response servlet response to write to
     * @param contentType HTML content type, optionally including charset
     * @param htmlBytes stored HTML bytes
     * @throws IOException when response writing fails
     */
    public static void writeStoredHtml(HttpServletResponse response, String contentType, byte[] htmlBytes)
            throws IOException {
        Charset charset = prepareHtmlResponse(response, contentType);
        if (htmlBytes == null) {
            return;
        }
        writeDecodedHtml(response, new String(htmlBytes, charset));
    }

    /**
     * Decodes a stored HTML stream using the declared charset and writes through
     * the servlet writer.
     *
     * @param response servlet response to write to
     * @param contentType HTML content type, optionally including charset
     * @param htmlStream stored HTML byte stream
     * @throws IOException when response writing fails
     */
    @SuppressWarnings({"XSS_SERVLET", "findsecbugs:XSS_SERVLET"})
    public static void writeStoredHtml(HttpServletResponse response, String contentType, InputStream htmlStream)
            throws IOException {
        Charset charset = prepareHtmlResponse(response, contentType);
        if (htmlStream == null) {
            return;
        }

        InputStreamReader reader = new InputStreamReader(htmlStream, charset);
        PrintWriter writer = response.getWriter();
        char[] buffer = new char[BUFFER_SIZE];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep, java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- intentional stored HTML rendering; callers must authorize routes before invoking
            writer.write(buffer, 0, count);
        }
    }

    private static Charset prepareHtmlResponse(HttpServletResponse response, String contentType) {
        String effectiveContentType = (contentType == null || contentType.isBlank())
                ? DEFAULT_HTML_CONTENT_TYPE
                : contentType;
        Charset charset = resolveCharset(effectiveContentType);
        response.setContentType(effectiveContentType);
        response.setCharacterEncoding(charset.name());
        return charset;
    }

    @SuppressWarnings({"XSS_SERVLET", "findsecbugs:XSS_SERVLET"})
    private static void writeDecodedHtml(HttpServletResponse response, String html) throws IOException {
        PrintWriter writer = response.getWriter();
        // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep, java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- intentional stored HTML rendering; callers must authorize routes before invoking
        writer.write(html);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
