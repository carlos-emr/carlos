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
import java.util.regex.Pattern;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;

import io.github.carlos_emr.carlos.utility.SafeEncode;


/**
 * JSP tag handler that rewrites a target route into a host-free URL.
 * <p>
 * A target starting with {@code /} is resolved against the context path; any other target
 * is resolved against the directory of the browser-visible request URL (see
 * {@link #buildRelativeUrl(HttpServletRequest, String)}). New call sites should pass the
 * context-relative route, for example {@code jspPage="/prevention/printPrevention"}.
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

    private static final Pattern LEADING_SEPARATORS = Pattern.compile("^[/\\\\]+");
    private static final Pattern URL_IGNORED_CHARACTERS = Pattern.compile("[\\t\\n\\r]");

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
     * Builds a host-free URL for the configured target.
     * <p>
     * Two target shapes are supported:
     * <ul>
     *   <li><b>Context-relative</b> — a {@code jspPage} starting with {@code /} (for example
     *       {@code /billing/CA/BC/support/Icd9}) is appended to the context path. This is the
     *       preferred form: it does not depend on which URL rendered the page.</li>
     *   <li><b>Page-relative</b> — any other value is resolved against the directory of the
     *       URL the <em>browser</em> requested.</li>
     * </ul>
     * Pages are now rendered by a gate action that forwards to an internal
     * {@code /WEB-INF/jsp/...} view, so after the forward {@link HttpServletRequest#getRequestURI()}
     * is the internal JSP path. Resolving against it produced links such as
     * {@code /carlos/WEB-INF/jsp/prevention/printPrevention}, which the container never serves
     * (issue #4132). The browser-visible URI is therefore taken from the
     * {@link RequestDispatcher#FORWARD_REQUEST_URI} attribute when it is set. As a last line of
     * defence, a base directory that still lies under {@code /WEB-INF/jsp} is mapped to the
     * matching route directory (view JSPs mirror their route paths), and any other
     * {@code /WEB-INF} base falls back to the context root, so the tag never emits a
     * {@code WEB-INF} URL.
     * <p>
     * A null request returns the JSP page unchanged; so does a null request URI for a
     * page-relative target (a context-relative target never reads the URI). A null JSP page
     * is treated as an empty string.
     *
     * @param request the current request, or {@code null}
     * @param jspPage the target route or JSP page, or {@code null}
     * @return a URL without scheme, host, or port, never {@code null}
     */
    static String buildRelativeUrl(HttpServletRequest request, String jspPage) {
        // Browsers delete tab, CR and LF anywhere in a URL before parsing it, so "/\t//host" would
        // become "//host" after the separator collapse below. Remove them first; no route has them.
        String safeJspPage = jspPage == null ? "" : URL_IGNORED_CHARACTERS.matcher(jspPage).replaceAll("");
        if (request == null) {
            return safeJspPage;
        }
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        // Browsers treat "\" as "/" in URLs, so a backslash-led target is context-relative too.
        if (LEADING_SEPARATORS.matcher(safeJspPage).lookingAt()) {
            // Collapse the leading separators: under the root context "//host/x" (or "/\host/x",
            // which browsers treat the same) would otherwise be a protocol-relative URL to
            // another host, breaking the host-free guarantee.
            return contextPath + "/" + LEADING_SEPARATORS.matcher(safeJspPage).replaceFirst("");
        }

        String requestUri = browserRequestUri(request);
        if (requestUri == null) {
            return safeJspPage;
        }
        int last = requestUri.lastIndexOf('/');
        String path = last >= 0 ? requestUri.substring(0, last) : "";

        return withoutWebInf(path, contextPath) + "/" + safeJspPage;
    }

    /**
     * Returns the URI the browser requested: the original URI of a forwarded request, or the
     * request URI itself when the page was not reached through a forward.
     */
    private static String browserRequestUri(HttpServletRequest request) {
        Object forwardUri = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if (forwardUri instanceof String forwarded && !forwarded.isEmpty()) {
            return forwarded;
        }
        return request.getRequestURI();
    }

    /**
     * Maps a base directory inside {@code WEB-INF} (never browser-reachable) to a servable one.
     * {@code <ctx>/WEB-INF/jsp/<dir>} becomes {@code <ctx>/<dir>}; any other {@code WEB-INF}
     * directory becomes the context root.
     */
    private static String withoutWebInf(String path, String contextPath) {
        String webInfJsp = contextPath + "/WEB-INF/jsp";
        if (path.equals(webInfJsp)) {
            return contextPath;
        }
        if (path.startsWith(webInfJsp + "/")) {
            return contextPath + path.substring(webInfJsp.length());
        }
        String webInf = contextPath + "/WEB-INF";
        if (path.equals(webInf) || path.startsWith(webInf + "/")) {
            return contextPath;
        }
        return path;
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
