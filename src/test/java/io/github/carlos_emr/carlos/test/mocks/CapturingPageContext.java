/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.test.mocks;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Enumeration;

import jakarta.el.ELContext;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;

/**
 * Minimal {@link PageContext} for tag unit tests: exposes a capturing {@link JspWriter}
 * and, optionally, a session for tags that read {@code LoggedInInfo} off it.
 *
 * <p>Everything a tag has no business touching throws {@link UnsupportedOperationException}
 * so an unexpected container dependency fails loudly instead of silently returning null.
 *
 * @since 2026-09-13
 */
@SuppressWarnings("deprecation")
public final class CapturingPageContext extends PageContext {

    private final JspWriter out;
    private final HttpSession session;

    public CapturingPageContext(JspWriter out) {
        this(out, null);
    }

    public CapturingPageContext(JspWriter out, HttpSession session) {
        this.out = out;
        this.session = session;
    }

    /** Convenience factory: capture rendered output into {@code target}. */
    public static CapturingPageContext writingTo(StringWriter target) {
        return new CapturingPageContext(CapturingJspWriter.into(target));
    }

    /** Convenience factory: capture rendered output and expose {@code session} to the tag. */
    public static CapturingPageContext writingTo(StringWriter target, HttpSession session) {
        return new CapturingPageContext(CapturingJspWriter.into(target), session);
    }

    @Override public JspWriter getOut() { return out; }

    @Override
    public HttpSession getSession() {
        if (session == null) {
            throw new UnsupportedOperationException("no session configured for this CapturingPageContext");
        }
        return session;
    }

    // Everything below is unused by the tags under test and throws if called unexpectedly.
    @Override public void initialize(Servlet servlet, ServletRequest request, ServletResponse response,
                                     String errorPageURL, boolean needsSession, int bufferSize,
                                     boolean autoFlush) { throw new UnsupportedOperationException(); }
    @Override public void release() { /* no-op */ }
    @Override public Object getPage() { throw new UnsupportedOperationException(); }
    @Override public ServletRequest getRequest() { throw new UnsupportedOperationException(); }
    @Override public ServletResponse getResponse() { throw new UnsupportedOperationException(); }
    @Override public Exception getException() { throw new UnsupportedOperationException(); }
    @Override public ServletConfig getServletConfig() { throw new UnsupportedOperationException(); }
    @Override public ServletContext getServletContext() { throw new UnsupportedOperationException(); }
    @Override public void forward(String relativeUrlPath) throws ServletException, IOException { throw new UnsupportedOperationException(); }
    @Override public void include(String relativeUrlPath) throws ServletException, IOException { throw new UnsupportedOperationException(); }
    @Override public void include(String relativeUrlPath, boolean flush) throws ServletException, IOException { throw new UnsupportedOperationException(); }
    @Override public void handlePageException(Exception e) throws ServletException, IOException { throw new UnsupportedOperationException(); }
    @Override public void handlePageException(Throwable t) throws ServletException, IOException { throw new UnsupportedOperationException(); }
    @Override public void setAttribute(String name, Object value) { /* no-op */ }
    @Override public void setAttribute(String name, Object value, int scope) { /* no-op */ }
    @Override public Object getAttribute(String name) { return null; }
    @Override public Object getAttribute(String name, int scope) { return null; }
    @Override public Object findAttribute(String name) { return null; }
    @Override public void removeAttribute(String name) { /* no-op */ }
    @Override public void removeAttribute(String name, int scope) { /* no-op */ }
    @Override public int getAttributesScope(String name) { return 0; }
    @Override public Enumeration<String> getAttributeNamesInScope(int scope) { return null; }
    @Override public ELContext getELContext() { throw new UnsupportedOperationException(); }
}
