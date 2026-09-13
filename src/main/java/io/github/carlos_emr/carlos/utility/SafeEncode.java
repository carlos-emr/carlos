/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;
import java.io.Writer;

import org.owasp.encoder.Encode;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Null-safe wrapper around {@link org.owasp.encoder.Encode}.
 *
 * <p>The OWASP Encoder renders a {@code null} input as the literal four-character
 * string {@code "null"} in every context ({@code Encode.forHtmlContent(null)}
 * returns {@code "null"}; {@code Encode.forHtmlContent(writer, null)} writes
 * {@code "null"}). JSTL's {@code <c:out>} in contrast renders {@code null} as an
 * empty string. The mass migration away from {@code <c:out>} therefore silently
 * introduced {@code "null"} output in table cells, attribute values, and URL
 * components whenever a nullable expression reached an encoder.
 *
 * <p>{@code SafeEncode} coalesces {@code null} to {@code ""} before delegating.
 * For every non-null input the output is bit-identical to the corresponding
 * {@link Encode} method — this is a strict safety strengthening, not a semantic
 * change.
 *
 * <p>Nearly all methods mirror {@link Encode} exactly — same name, same argument
 * order, same thrown checked exceptions. Call sites can migrate with a one-line
 * import swap ({@code import org.owasp.encoder.Encode} → {@code import SafeEncode})
 * and a find-and-replace of the type name. CARLOS-specific helpers may be added
 * where the view layer needs a safe convenience not provided by OWASP directly,
 * such as newline-to-{@code <br/>} rendering for preformatted table cells.
 *
 * <p>Prefer this class for any encoded output where the source value can be
 * {@code null} (DAO getters, {@code request.getParameter}, session attributes,
 * optional fields). Use {@link Encode} directly only when the value is
 * guaranteed non-null and the intent is to preserve OWASP's literal
 * {@code "null"} rendering (there is no known legitimate use of that rendering
 * in this codebase).
 *
 * @see org.owasp.encoder.Encode
 * @see io.github.carlos_emr.carlos.utility.tld.CarlosEncodeTag
 * @since 2026-04-18
 */
public final class SafeEncode {

    /** Context name assumed when a tag omits {@code context}. */
    private static final String DEFAULT_CONTEXT = "html";

    /** Human-readable context list, reused by callers that report a bad context name. */
    public static final String VALID_CONTEXTS =
            "Valid contexts: html, htmlAttribute, htmlUnquotedAttribute, "
                    + "javaScript, javaScriptAttribute, javaScriptBlock, javaScriptSource, "
                    + "uri, uriComponent, cssString, cssUrl, "
                    + "xml, xmlAttribute, xmlContent, xmlComment, cdata, java.";

    private SafeEncode() {
        // static-only
    }

    /** Coalesce {@code null} to empty string. */
    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // -------- HTML --------

    public static String forHtml(String value) {
        return Encode.forHtml(nz(value));
    }

    public static void forHtml(Writer out, String value) throws IOException {
        Encode.forHtml(out, nz(value));
    }

    public static String forHtmlContent(String value) {
        return Encode.forHtmlContent(nz(value));
    }

    /**
     * Encode HTML content and render line breaks as literal {@code <br/>} tags.
     *
     * <p>User-controlled content is HTML-encoded first, then normalized line
     * endings ({@code \r\n}, {@code \r}) are converted to {@code \n} and finally
     * each newline is replaced with the constant application markup
     * {@code <br/>}. This preserves visible multi-line formatting without
     * allowing user-supplied HTML markup through.
     *
     * @param value String the untrusted HTML content to encode
     * @return String the HTML-encoded content with newline characters rendered as {@code <br/>}
     */
    public static String forHtmlContentWithBreaks(String value) {
        String encoded = forHtmlContent(value);
        StringBuilder builder = null;
        for (int i = 0; i < encoded.length(); i++) {
            char current = encoded.charAt(i);
            if (current == '\r') {
                if (builder == null) {
                    builder = new StringBuilder(encoded.length());
                    builder.append(encoded, 0, i);
                }
                builder.append("<br/>");
                if (i + 1 < encoded.length() && encoded.charAt(i + 1) == '\n') {
                    i++;
                }
                continue;
            }
            if (current == '\n') {
                if (builder == null) {
                    builder = new StringBuilder(encoded.length());
                    builder.append(encoded, 0, i);
                }
                builder.append("<br/>");
                continue;
            }
            if (builder != null) {
                builder.append(current);
            }
        }
        return builder == null ? encoded : builder.toString();
    }

    public static void forHtmlContent(Writer out, String value) throws IOException {
        Encode.forHtmlContent(out, nz(value));
    }

    public static String forHtmlAttribute(String value) {
        return Encode.forHtmlAttribute(nz(value));
    }

    public static void forHtmlAttribute(Writer out, String value) throws IOException {
        Encode.forHtmlAttribute(out, nz(value));
    }

    public static String forHtmlUnquotedAttribute(String value) {
        return Encode.forHtmlUnquotedAttribute(nz(value));
    }

    public static void forHtmlUnquotedAttribute(Writer out, String value) throws IOException {
        Encode.forHtmlUnquotedAttribute(out, nz(value));
    }

    // -------- CSS --------

    public static String forCssString(String value) {
        return Encode.forCssString(nz(value));
    }

    public static void forCssString(Writer out, String value) throws IOException {
        Encode.forCssString(out, nz(value));
    }

    public static String forCssUrl(String value) {
        return Encode.forCssUrl(nz(value));
    }

    public static void forCssUrl(Writer out, String value) throws IOException {
        Encode.forCssUrl(out, nz(value));
    }

    // -------- URI --------

    public static String forUri(String value) {
        return Encode.forUri(nz(value));
    }

    public static void forUri(Writer out, String value) throws IOException {
        Encode.forUri(out, nz(value));
    }

    public static String forUriComponent(String value) {
        return Encode.forUriComponent(nz(value));
    }

    public static void forUriComponent(Writer out, String value) throws IOException {
        Encode.forUriComponent(out, nz(value));
    }

    // -------- XML --------

    public static String forXml(String value) {
        return Encode.forXml(nz(value));
    }

    public static void forXml(Writer out, String value) throws IOException {
        Encode.forXml(out, nz(value));
    }

    public static String forXmlContent(String value) {
        return Encode.forXmlContent(nz(value));
    }

    public static void forXmlContent(Writer out, String value) throws IOException {
        Encode.forXmlContent(out, nz(value));
    }

    public static String forXmlAttribute(String value) {
        return Encode.forXmlAttribute(nz(value));
    }

    public static void forXmlAttribute(Writer out, String value) throws IOException {
        Encode.forXmlAttribute(out, nz(value));
    }

    public static String forXmlComment(String value) {
        return Encode.forXmlComment(nz(value));
    }

    public static void forXmlComment(Writer out, String value) throws IOException {
        Encode.forXmlComment(out, nz(value));
    }

    public static String forXml11(String value) {
        return Encode.forXml11(nz(value));
    }

    public static void forXml11(Writer out, String value) throws IOException {
        Encode.forXml11(out, nz(value));
    }

    public static String forXml11Content(String value) {
        return Encode.forXml11Content(nz(value));
    }

    public static void forXml11Content(Writer out, String value) throws IOException {
        Encode.forXml11Content(out, nz(value));
    }

    public static String forXml11Attribute(String value) {
        return Encode.forXml11Attribute(nz(value));
    }

    public static void forXml11Attribute(Writer out, String value) throws IOException {
        Encode.forXml11Attribute(out, nz(value));
    }

    // -------- CDATA --------

    public static String forCDATA(String value) {
        return Encode.forCDATA(nz(value));
    }

    public static void forCDATA(Writer out, String value) throws IOException {
        Encode.forCDATA(out, nz(value));
    }

    // -------- Java / JavaScript --------

    public static String forJava(String value) {
        return Encode.forJava(nz(value));
    }

    public static void forJava(Writer out, String value) throws IOException {
        Encode.forJava(out, nz(value));
    }

    public static String forJavaScript(String value) {
        return Encode.forJavaScript(nz(value));
    }

    public static void forJavaScript(Writer out, String value) throws IOException {
        Encode.forJavaScript(out, nz(value));
    }

    public static String forJavaScriptAttribute(String value) {
        return Encode.forJavaScriptAttribute(nz(value));
    }

    public static void forJavaScriptAttribute(Writer out, String value) throws IOException {
        Encode.forJavaScriptAttribute(out, nz(value));
    }

    public static String forJavaScriptBlock(String value) {
        return Encode.forJavaScriptBlock(nz(value));
    }

    public static void forJavaScriptBlock(Writer out, String value) throws IOException {
        Encode.forJavaScriptBlock(out, nz(value));
    }

    public static String forJavaScriptSource(String value) {
        return Encode.forJavaScriptSource(nz(value));
    }

    public static void forJavaScriptSource(Writer out, String value) throws IOException {
        Encode.forJavaScriptSource(out, nz(value));
    }

    // -------- Context dispatch --------

    /**
     * Encode {@code value} for the named output context and write it to {@code out}.
     *
     * <p>This is the single dispatch table shared by every CARLOS tag that renders a
     * caller-selected context: {@code <carlos:encode context="...">} and
     * {@code <oscar:nameage context="...">}. Keeping one table means a tag can never
     * silently support a narrower context set than the encoder itself.
     *
     * <p>Context names are matched case-insensitively; {@code null} or blank means
     * {@code html} (HTML body content), which is the safe default for the body-text
     * call sites that dominate the JSP layer.
     *
     * @param out     writer to receive the encoded value
     * @param context context name, e.g. {@code html}, {@code htmlAttribute}, {@code javaScript}
     * @param value   raw value; {@code null} is rendered as empty
     * @throws IOException              if {@code out} rejects the write
     * @throws IllegalArgumentException if {@code context} is not a known context name
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (context name from a JSP tag attribute); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (context name from a JSP tag attribute); not a security or authorization decision")
    public static void forContext(Writer out, String context, String value) throws IOException {
        String ctx = (context == null || context.isBlank()) ? DEFAULT_CONTEXT : context;
        // Lowercase compare makes "html", "Html", "HTML", "hTML" equivalent.
        switch (ctx.toLowerCase()) {
            case "html":
            case "htmlcontent":
                forHtmlContent(out, value);
                return;
            case "forhtml":
                forHtml(out, value);
                return;
            case "htmlattribute":
                forHtmlAttribute(out, value);
                return;
            case "htmlunquotedattribute":
                forHtmlUnquotedAttribute(out, value);
                return;
            case "javascript":
            case "js":
                forJavaScript(out, value);
                return;
            case "javascriptattribute":
                forJavaScriptAttribute(out, value);
                return;
            case "javascriptblock":
                forJavaScriptBlock(out, value);
                return;
            case "javascriptsource":
                forJavaScriptSource(out, value);
                return;
            case "uri":
                forUri(out, value);
                return;
            case "uricomponent":
                forUriComponent(out, value);
                return;
            case "cssstring":
            case "css":
                forCssString(out, value);
                return;
            case "cssurl":
                forCssUrl(out, value);
                return;
            case "xml":
                forXml(out, value);
                return;
            case "xmlattribute":
                forXmlAttribute(out, value);
                return;
            case "xmlcontent":
                forXmlContent(out, value);
                return;
            case "xmlcomment":
                forXmlComment(out, value);
                return;
            case "cdata":
                forCDATA(out, value);
                return;
            case "java":
                forJava(out, value);
                return;
            default:
                throw new IllegalArgumentException("unknown context '" + ctx + "'. " + VALID_CONTEXTS);
        }
    }
}
