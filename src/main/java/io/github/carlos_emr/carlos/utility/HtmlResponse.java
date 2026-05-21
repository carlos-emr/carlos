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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
    /** Default HTML Content-Type used when callers have decoded HTML text but no stored content type. */
    public static final String DEFAULT_HTML_CONTENT_TYPE_WITH_CHARSET = "text/html;charset=UTF-8";

    /** Fallback for stored HTML that has no charset declaration or declares an unsupported charset. */
    private static final Charset DEFAULT_HTML_CHARSET = StandardCharsets.UTF_8;
    private static final String DEFAULT_HTML_CONTENT_TYPE = "text/html";
    private static final int BUFFER_SIZE = 4096;

    private HtmlResponse() {
    }

    /**
     * Resolves the charset parameter from a Content-Type header value.
     * Quoted parameter values and escaped quote characters are normalized
     * before lookup. Missing, blank, unknown, or invalid charset declarations
     * intentionally fall back to UTF-8 so stored HTML remains displayable.
     *
     * @param contentType response Content-Type value, optionally including quoted charset parameters
     * @return declared charset, or UTF-8 when the declaration is absent, blank, unknown, or invalid
     */
    public static Charset resolveCharset(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_HTML_CHARSET;
        }
        return resolveCharsetFromParameters(splitContentTypeParameters(contentType));
    }

    /**
     * Resolves the charset from already-parsed Content-Type parameters. See
     * {@link #resolveCharset(String)} for fallback semantics.
     */
    private static Charset resolveCharsetFromParameters(List<String> parameters) {
        for (String parameter : parameters) {
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
     * @param contentType HTML content type used to set response headers and resolve the writer charset
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
     * @param contentType HTML content type used to set response headers and resolve the byte charset
     * @param htmlBytes stored HTML bytes
     * @throws IOException when response writing fails
     */
    public static void writeStoredHtml(HttpServletResponse response, String contentType, byte[] htmlBytes)
            throws IOException {
        if (htmlBytes == null) {
            prepareHtmlResponse(response, contentType);
            // Preserve the writer-backed contract so LogoutBroadcastFilter can append
            // logout listeners even when callers pass a null body.
            response.getWriter();
            return;
        }
        writeStoredHtml(response, contentType, new ByteArrayInputStream(htmlBytes));
    }

    /**
     * Decodes a stored HTML stream using the declared charset and writes through
     * the servlet writer.
     *
     * @param response servlet response to write to
     * @param contentType HTML content type used to set response headers and resolve the stream charset
     * @param htmlStream stored HTML byte stream; closed after the response is written
     * @throws IOException when response writing fails
     */
    @SuppressWarnings({"XSS_SERVLET", "findsecbugs:XSS_SERVLET"})
    public static void writeStoredHtml(HttpServletResponse response, String contentType, InputStream htmlStream)
            throws IOException {
        Charset charset = prepareHtmlResponse(response, contentType);
        if (htmlStream == null) {
            // Preserve the writer-backed contract so LogoutBroadcastFilter can append
            // logout listeners even when callers pass a null stream.
            response.getWriter();
            return;
        }

        char[] buffer = new char[BUFFER_SIZE];
        PrintWriter writer = response.getWriter();
        try (InputStreamReader reader = new InputStreamReader(htmlStream, charset)) {
            int count;
            while ((count = reader.read(buffer)) != -1) {
                // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep, java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- intentional stored HTML rendering; callers must authorize routes before invoking
                writer.write(buffer, 0, count);
            }
        }
    }

    /**
     * Sets the HTML Content-Type header and returns the charset used to decode stored bytes.
     *
     * <p>Parses Content-Type parameters exactly once and reuses the parsed list to
     * resolve the charset and to build the aligned response Content-Type header,
     * avoiding the redundant parsing that previously occurred in
     * {@code resolveCharset} and {@code contentTypeWithCharset}.</p>
     */
    private static Charset prepareHtmlResponse(HttpServletResponse response, String contentType) {
        String effectiveContentType = (contentType == null || contentType.isBlank())
                ? DEFAULT_HTML_CONTENT_TYPE
                : contentType;
        List<String> parameters = splitContentTypeParameters(effectiveContentType);
        Charset charset = resolveCharsetFromParameters(parameters);
        response.setContentType(contentTypeWithCharset(effectiveContentType, parameters, charset));
        response.setCharacterEncoding(charset.name());
        return charset;
    }

    /**
     * Removes matching outer quotes from an HTTP header parameter value and unescapes quoted text.
     */
    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return unescapeQuotedValue(value.substring(1, value.length() - 1));
        }
        return value;
    }

    /**
     * Returns a Content-Type value with an explicit charset aligned to the decoding charset.
     * Accepts a pre-parsed parameter list to avoid re-splitting the header.
     */
    private static String contentTypeWithCharset(String contentType, List<String> parameters, Charset charset) {
        int firstParameter = contentType.indexOf(';');
        if (firstParameter < 0) {
            return contentType + ";charset=" + charset.name();
        }

        StringBuilder alignedContentType = new StringBuilder(contentType.substring(0, firstParameter).trim());
        boolean foundCharset = false;
        for (String parameter : parameters) {
            int equals = parameter.indexOf('=');
            if (equals >= 0 && "charset".equalsIgnoreCase(parameter.substring(0, equals).trim())) {
                if (!foundCharset) {
                    appendContentTypeParameter(alignedContentType, "charset=" + charset.name());
                }
                foundCharset = true;
            } else {
                appendContentTypeParameter(alignedContentType, parameter);
            }
        }
        if (!foundCharset) {
            appendContentTypeParameter(alignedContentType, "charset=" + charset.name());
        }
        return alignedContentType.toString();
    }

    /**
     * Appends a normalized Content-Type parameter to the response header value.
     */
    private static void appendContentTypeParameter(StringBuilder contentType, String parameter) {
        contentType.append(';').append(parameter);
    }

    /**
     * Splits Content-Type parameters while respecting quoted values that contain semicolons.
     */
    private static List<String> splitContentTypeParameters(String contentType) {
        List<String> parameters = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        boolean escaped = false;
        char quote = 0;

        int firstParameter = contentType.indexOf(';');
        if (firstParameter < 0) {
            return List.of();
        }

        for (int i = firstParameter + 1; i < contentType.length(); i++) {
            char c = contentType.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (inQuote && c == '\\') {
                // Preserve the escape marker until stripQuotes() unescapes the full quoted value.
                current.append(c);
                escaped = true;
                continue;
            }
            if (c == '"' || c == '\'') {
                // Track quoted parameter values so semicolons inside quotes do not split params.
                if (!inQuote) {
                    inQuote = true;
                    quote = c;
                } else if (quote == c) {
                    inQuote = false;
                    quote = 0;
                }
                current.append(c);
                continue;
            }
            if (c == ';' && !inQuote) {
                // Only unquoted semicolons delimit Content-Type parameters.
                addParameter(parameters, current);
                continue;
            }
            current.append(c);
        }
        addParameter(parameters, current);
        return parameters;
    }

    /**
     * Adds the accumulated parameter and resets the buffer for the next parameter.
     */
    private static void addParameter(List<String> parameters, StringBuilder current) {
        String parameter = current.toString().trim();
        if (!parameter.isEmpty()) {
            parameters.add(parameter);
        }
        current.setLength(0);
    }

    /**
     * Unescapes backslash escapes inside a quoted HTTP header parameter value.
     */
    private static String unescapeQuotedValue(String value) {
        StringBuilder unescaped = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                unescaped.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                unescaped.append(c);
            }
        }
        if (escaped) {
            // A dangling escape is malformed but preserving it avoids silently changing the value.
            unescaped.append('\\');
        }
        return unescaped.toString();
    }
}
