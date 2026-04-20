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
 * <p>All methods mirror {@link Encode} exactly — same name, same argument order,
 * same thrown checked exceptions. Call sites can migrate with a one-line import
 * swap ({@code import org.owasp.encoder.Encode} → {@code import SafeEncode}) and
 * a find-and-replace of the type name.
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
}
