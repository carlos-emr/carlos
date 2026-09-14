/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.util;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.carlos.utility.SafeEncode;


/**
 * JSP tag handler that rewrites a target JSP page into a request-path-relative URL.
 * <p>
 * The tag deliberately avoids request scheme, server name, and server port values so
 * untrusted Host header data cannot affect generated links. Use the optional
 * {@code context} attribute to choose output encoding for the place where the URL is
 * rendered: {@code html}, {@code htmlAttribute}, {@code javaScriptAttribute}, or
 * {@code javaScriptBlock}.
 *
 * @since 2026-06-17
 */
public class FullPathReWrite extends TagSupport {

    private static final String DEFAULT_CONTEXT = "html";

    /**
     * Legacy server attribute retained for tag compatibility.
     */
    protected String server = null;

    /**
     * The target window for this base reference.
     */
    protected String jspPage = null;

    /**
     * Output encoding context. Defaults to legacy HTML encoding.
     */
    protected String context = DEFAULT_CONTEXT;

    public String getJspPage() {
        return (this.jspPage == null) ? "" : this.jspPage;
    }

    public void setJspPage(String jspPage) {
        this.jspPage = jspPage;
    }

    public String getContext() {
        return this.context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    /**
     * Process the start of this tag.
     *
     * @throws JspException if a JSP exception has occurred
     */
    public int doStartTag() throws JspException {
        HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();

        String returnTag = buildRelativeUrl(request, getJspPage());

        JspWriter out = pageContext.getOut();
        try {
            out.write(encodeForContext(returnTag, context));
            out.flush();
        } catch (IOException e) {
            throw new JspException(e.toString());
        }

        return EVAL_BODY_INCLUDE;
    }

    @Override
    public int doEndTag() throws JspException {
        resetAttributes();
        return EVAL_PAGE;
    }

    @Override
    public void release() {
        resetAttributes();
        super.release();
    }

    /**
     * Builds a relative URL from the request URI directory and the configured JSP page.
     * <p>
     * Null request or URI values return the JSP page unchanged. A null JSP page is
     * treated as an empty string. The method intentionally preserves the historical
     * path-joining shape used by legacy JSP call sites.
     *
     * @param request the current request, or {@code null}
     * @param jspPage the JSP page value to append, or {@code null}
     * @return a request-path-relative URL, never {@code null}
     */
    static String buildRelativeUrl(HttpServletRequest request, String jspPage) {
        String safeJspPage = jspPage == null ? "" : jspPage;
        if (request == null) {
            return safeJspPage;
        }
        String temp = request.getRequestURI();
        if (temp == null) {
            return safeJspPage;
        }
        int last = temp.lastIndexOf('/');
        String path = last >= 0 ? temp.substring(0, last) : "";

        return path + "/" + safeJspPage;
    }

    /**
     * Encodes a generated URL for the requested JSP rendering context.
     * <p>
     * Supported contexts are {@code html}, {@code htmlAttribute},
     * {@code javaScriptAttribute}, and {@code javaScriptBlock}. A null or blank context
     * uses the default {@code html} encoding.
     *
     * @param value the URL value to encode
     * @param context the encoding context, or {@code null} for the default
     * @return the encoded URL string
     * @throws JspException if the context value is unsupported
     */
    static String encodeForContext(String value, String context) throws JspException {
        String normalizedContext = (context == null || context.isBlank())
                ? DEFAULT_CONTEXT
                : context;
        switch (normalizedContext) {
            case "html":
                return SafeEncode.forHtml(value);
            case "htmlAttribute":
                return SafeEncode.forHtmlAttribute(value);
            case "javaScriptAttribute":
                return SafeEncode.forJavaScriptAttribute(value);
            case "javaScriptBlock":
                return SafeEncode.forJavaScriptBlock(value);
            default:
                throw new JspException("Unsupported FullPathReWrite encoding context: " + context);
        }
    }

    private void resetAttributes() {
        server = null;
        jspPage = null;
        context = DEFAULT_CONTEXT;
    }


    /**
     * Returns the server.
     *
     * @return String
     */
    public String getServer() {
        return this.server;
    }

    /**
     * Sets the server.
     *
     * @param server The server to set
     */
    public void setServer(String server) {
        this.server = server;
    }

}
