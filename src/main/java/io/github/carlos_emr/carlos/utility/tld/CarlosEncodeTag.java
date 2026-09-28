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
package io.github.carlos_emr.carlos.utility.tld;

import java.io.IOException;

import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Null-safe OWASP-backed encoder tag. Registered as {@code <carlos:encode>} via
 * {@code carlos-tag.tld} (URI {@code carlos}).
 *
 * <p>Delegates to {@link SafeEncode}, which coalesces {@code null} to {@code ""}
 * before calling {@link org.owasp.encoder.Encode}. Renders non-null input
 * bit-identically to {@code <e:forXxx>} from {@code owasp.encoder.jakarta.advanced};
 * renders {@code null} input as empty (matching JSTL {@code <c:out>}).
 *
 * <h2>Usage</h2>
 * <pre>
 * &lt;%@ taglib uri="carlos" prefix="carlos" %&gt;
 *
 * &lt;carlos:encode value="${x}"/&gt;                              &lt;!-- default: html content --&gt;
 * &lt;carlos:encode value="${x}" context="htmlAttribute"/&gt;
 * &lt;carlos:encode value="${x}" context="javaScript"/&gt;
 * &lt;carlos:encode value="${x}" context="uri"/&gt;
 * </pre>
 *
 * <h2>Supported contexts</h2>
 * Case-insensitive. Missing / blank {@code context} defaults to {@code html}
 * (i.e. {@code forHtmlContent}). The dispatch table lives in
 * {@link SafeEncode#forContext(java.io.Writer, String, String)} so that every
 * context-selecting CARLOS tag accepts exactly the same context names.
 *
 * <ul>
 *   <li>{@code html} / {@code htmlContent} → {@link SafeEncode#forHtmlContent(String)}</li>
 *   <li>{@code htmlAttribute} → {@link SafeEncode#forHtmlAttribute(String)}</li>
 *   <li>{@code htmlUnquotedAttribute} → {@link SafeEncode#forHtmlUnquotedAttribute(String)}</li>
 *   <li>{@code forHtml} (legacy alias) → {@link SafeEncode#forHtml(String)}</li>
 *   <li>{@code javaScript} / {@code js} → {@link SafeEncode#forJavaScript(String)}</li>
 *   <li>{@code javaScriptAttribute} → {@link SafeEncode#forJavaScriptAttribute(String)}</li>
 *   <li>{@code javaScriptBlock} → {@link SafeEncode#forJavaScriptBlock(String)}</li>
 *   <li>{@code javaScriptSource} → {@link SafeEncode#forJavaScriptSource(String)}</li>
 *   <li>{@code uri} → {@link SafeEncode#forUri(String)}</li>
 *   <li>{@code uriComponent} → {@link SafeEncode#forUriComponent(String)}</li>
 *   <li>{@code cssString} / {@code css} → {@link SafeEncode#forCssString(String)}</li>
 *   <li>{@code cssUrl} → {@link SafeEncode#forCssUrl(String)}</li>
 *   <li>{@code xml} → {@link SafeEncode#forXml(String)}</li>
 *   <li>{@code xmlAttribute} → {@link SafeEncode#forXmlAttribute(String)}</li>
 *   <li>{@code xmlContent} → {@link SafeEncode#forXmlContent(String)}</li>
 *   <li>{@code xmlComment} → {@link SafeEncode#forXmlComment(String)}</li>
 *   <li>{@code cdata} → {@link SafeEncode#forCDATA(String)}</li>
 *   <li>{@code java} → {@link SafeEncode#forJava(String)}</li>
 * </ul>
 *
 * <p>Any other {@code context} value throws {@link JspException} at render time.
 *
 * @see SafeEncode
 * @since 2026-04-18
 */
public class CarlosEncodeTag extends TagSupport {

    private String value;
    private String context;

    public void setValue(String value) {
        this.value = value;
    }

    public void setContext(String context) {
        this.context = context;
    }

    @Override
    public int doStartTag() throws JspException {
        JspWriter out = pageContext.getOut();
        try {
            SafeEncode.forContext(out, context, value);
        } catch (IOException e) {
            throw new JspException("carlos:encode failed to write output", e);
        } catch (IllegalArgumentException e) {
            // SafeEncode reports an unknown context name; surface it as a JSP render failure.
            throw new JspException("carlos:encode: " + e.getMessage(), e);
        }
        return SKIP_BODY;
    }

    @Override
    public void release() {
        value = null;
        context = null;
        super.release();
    }
}
