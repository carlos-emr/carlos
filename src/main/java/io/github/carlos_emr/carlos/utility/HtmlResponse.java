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
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Centralized response handling for stored HTML content that must remain
 * writer-backed so response filters can append CARLOS logout listeners.
 *
 * <p>Callers must perform route-specific authorization and decide whether
 * rendering stored HTML is appropriate before using this class.</p>
 */
public final class HtmlResponse {
    /** Default HTML Content-Type used when callers have decoded HTML text but no stored content type. */
    public static final String DEFAULT_HTML_CONTENT_TYPE_WITH_CHARSET = "text/html;charset=UTF-8";

    /** Fallback for stored HTML that has no charset declaration or declares an unsupported charset. */
    private static final Charset DEFAULT_HTML_CHARSET = StandardCharsets.UTF_8;
    private static final String DEFAULT_HTML_CONTENT_TYPE = "text/html";
    private static final int BUFFER_SIZE = 4096;
    private static final Logger LOGGER = MiscUtils.getLogger();

    private final String contentType;
    private final String html;
    private final byte[] htmlBytes;
    private final InputStream htmlStream;

    private HtmlResponse(String contentType, String html, byte[] htmlBytes, InputStream htmlStream) {
        this.contentType = contentType;
        this.html = html;
        this.htmlBytes = htmlBytes;
        this.htmlStream = htmlStream;
    }

    /**
     * Creates an HTML response from a character string. A charset parameter in
     * {@code contentType} controls how the string is encoded when written; absent
     * or invalid charsets fall back to UTF-8. A {@code null} body writes no bytes.
     *
     * @param contentType response Content-Type header value, optionally with charset
     * @param html HTML body characters; may be {@code null}
     * @return immutable response value to write to an HTTP response
     * @since 2026-05-21
     */
    public static HtmlResponse of(String contentType, String html) {
        return new HtmlResponse(contentType, html, null, null);
    }

    /**
     * Creates an HTML response from bytes. The byte array is retained by reference;
     * callers should not mutate it after construction. A charset parameter in
     * {@code contentType} is preserved for clients but the bytes are not transcoded.
     *
     * @param contentType response Content-Type header value, optionally with charset
     * @param htmlBytes HTML body bytes; may be {@code null}
     * @return immutable response value to write to an HTTP response
     * @since 2026-05-21
     */
    public static HtmlResponse of(String contentType, byte[] htmlBytes) {
        return new HtmlResponse(contentType, null, htmlBytes, null);
    }

    /**
     * Creates an HTML response from a stream. The stream is consumed once during
     * writing and is not closed by this value; callers remain responsible for the
     * stream lifecycle. A charset parameter in {@code contentType} is preserved for
     * clients but stream bytes are not transcoded.
     *
     * @param contentType response Content-Type header value, optionally with charset
     * @param htmlStream HTML body stream; may be {@code null}
     * @return response value to write once to an HTTP response
     * @since 2026-05-21
     */
    public static HtmlResponse of(String contentType, InputStream htmlStream) {
        return new HtmlResponse(contentType, null, null, htmlStream);
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

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
            } catch (IllegalCharsetNameException e) {
                LOGGER.warn("Stored HTML declared malformed charset {}; falling back to UTF-8", LogSafe.sanitize(charsetName));
                return DEFAULT_HTML_CHARSET;
            } catch (UnsupportedCharsetException e) {
                LOGGER.warn("Stored HTML declared unsupported charset {}; falling back to UTF-8", LogSafe.sanitize(charsetName));
                return DEFAULT_HTML_CHARSET;
            }
        }

        return DEFAULT_HTML_CHARSET;
    }

    /**
     * Writes this stored HTML response through the servlet writer and returns the
     * Struts direct-response result.
     *
     * @param response servlet response to write to
     * @return {@link ActionSupport#NONE}
     * @throws IOException when response writing fails
     */
    // FindSecBugs XSS_SERVLET: intentionally renders stored HTML; callers must provide validated/encoded content.
    @SuppressFBWarnings(value = "XSS_SERVLET", justification = "intentionally renders stored HTML; callers must provide validated/encoded content")
    @SuppressWarnings({"XSS_SERVLET", "findsecbugs:XSS_SERVLET"})
    public String writeTo(HttpServletResponse response) throws IOException {
        Charset charset = prepareHtmlResponse(response, contentType);
        InputStream stream = bodyStream(charset);
        if (stream == null) {
            response.getWriter();
            return ActionSupport.NONE;
        }

        char[] buffer = new char[BUFFER_SIZE];
        PrintWriter writer = response.getWriter();
        try (InputStreamReader reader = new InputStreamReader(new NonClosingInputStream(stream), charset)) {
            int count;
            while ((count = reader.read(buffer)) != -1) {
                // nosemgrep: java.servlets.security.servletresponse-writer-xss.servletresponse-writer-xss, java.servlets.security.servletresponse-writer-xss-deepsemgrep.servletresponse-writer-xss-deepsemgrep, java.lang.security.audit.xss.no-direct-response-writer.no-direct-response-writer -- intentional stored HTML rendering; callers must authorize routes before invoking
                writer.write(buffer, 0, count);
            }
        }
        return ActionSupport.NONE;
    }

    private InputStream bodyStream(Charset charset) {
        if (html != null) {
            return new ByteArrayInputStream(html.getBytes(charset));
        }
        if (htmlBytes != null) {
            return new ByteArrayInputStream(htmlBytes);
        }
        return htmlStream;
    }

    /**
     * Writes already-decoded stored HTML through the servlet writer.
     *
     * @param response servlet response to write to
     * @param contentType HTML content type used to set response headers and resolve the writer charset
     * @param html stored HTML body
     * @throws IOException when response writing fails
     */
    public static void writeStoredHtml(HttpServletResponse response, String contentType, String html)
            throws IOException {
        of(contentType, html == null ? "" : html).writeTo(response);
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
        of(contentType, htmlBytes).writeTo(response);
    }

    /**
     * Decodes a stored HTML stream using the declared charset and writes through
     * the servlet writer.
     *
     * @param response servlet response to write to
     * @param contentType HTML content type used to set response headers and resolve the stream charset
     * @param htmlStream stored HTML byte stream; not closed by this method
     * @throws IOException when response writing fails
     */
    public static void writeStoredHtml(HttpServletResponse response, String contentType, InputStream htmlStream)
            throws IOException {
        of(contentType, htmlStream).writeTo(response);
    }

    /**
     * Sets the HTML Content-Type header and returns the charset used to decode stored bytes.
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
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
                current.append(c);
                escaped = true;
                continue;
            }
            if (c == '"' || c == '\'') {
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
                addParameter(parameters, current);
                continue;
            }
            current.append(c);
        }
        addParameter(parameters, current);
        return parameters;
    }

    private static void addParameter(List<String> parameters, StringBuilder current) {
        String parameter = current.toString().trim();
        if (!parameter.isEmpty()) {
            parameters.add(parameter);
        }
        current.setLength(0);
    }

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
            unescaped.append('\\');
        }
        return unescaped.toString();
    }

    /**
     * Allows closing decoder resources without taking ownership of the caller's stream.
     */
    private static final class NonClosingInputStream extends FilterInputStream {
        private NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // Caller-owned stream is closed by the caller.
        }
    }
}
