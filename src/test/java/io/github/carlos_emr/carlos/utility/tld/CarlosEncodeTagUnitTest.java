/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility.tld;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import jakarta.el.ELContext;
import jakarta.el.ExpressionFactory;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.owasp.encoder.Encode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CarlosEncodeTag}.
 *
 * <p>Uses a stub {@link PageContext} whose {@link JspWriter} captures output
 * into a {@link StringWriter}. The tag's delegation to {@link io.github.carlos_emr.carlos.utility.SafeEncode}
 * is verified by comparing captured output against direct
 * {@link org.owasp.encoder.Encode} calls.
 *
 * @since 2026-04-18
 */
@Tag("unit")
@Tag("encoding")
@DisplayName("CarlosEncodeTag")
class CarlosEncodeTagUnitTest {

    private CarlosEncodeTag tag;
    private StringWriter captured;

    @BeforeEach
    void setUp() {
        tag = new CarlosEncodeTag();
        captured = new StringWriter();
        CapturingPageContext ctx = new CapturingPageContext(new CapturingJspWriter(captured));
        tag.setPageContext(ctx);
    }

    @Nested
    @DisplayName("Null value handling")
    class NullValue {

        @Test
        @DisplayName("should write empty when value is null and no context is set")
        void shouldWriteEmpty_whenValueIsNullAndNoContext() throws JspException {
            tag.setValue(null);
            tag.doStartTag();
            assertThat(captured.toString()).isEmpty();
        }

        @Test
        @DisplayName("should write empty when value is null for every supported context")
        void shouldWriteEmpty_whenValueIsNull_forEveryContext() throws JspException {
            String[] contexts = {
                    "html", "htmlContent", "htmlAttribute", "htmlUnquotedAttribute", "forHtml",
                    "javaScript", "js", "javaScriptAttribute", "javaScriptBlock", "javaScriptSource",
                    "uri", "uriComponent",
                    "cssString", "css", "cssUrl",
                    "xml", "xmlAttribute", "xmlContent", "xmlComment", "cdata", "java"
            };
            for (String ctx : contexts) {
                tag = new CarlosEncodeTag();
                StringWriter sw = new StringWriter();
                tag.setPageContext(new CapturingPageContext(new CapturingJspWriter(sw)));
                tag.setContext(ctx);
                tag.setValue(null);
                tag.doStartTag();
                assertThat(sw.toString()).as("context=%s", ctx).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Default context (no context attribute)")
    class DefaultContext {

        @Test
        @DisplayName("should default to HTML content encoding")
        void shouldDefaultToHtmlContent_whenContextNotSet() throws JspException {
            tag.setValue("<b>bold</b>");
            tag.doStartTag();
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlContent("<b>bold</b>"));
        }

        @Test
        @DisplayName("should default to HTML content when context is empty string")
        void shouldDefaultToHtmlContent_whenContextIsEmpty() throws JspException {
            tag.setValue("<b>bold</b>");
            tag.setContext("");
            tag.doStartTag();
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlContent("<b>bold</b>"));
        }
    }

    @Nested
    @DisplayName("Context dispatch — each context maps to the matching Encode method")
    class ContextDispatch {

        @Test
        void shouldDispatchToForHtmlContent_whenContextIsHtml() throws JspException {
            run("html", "O'Brien & <b>");
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlContent("O'Brien & <b>"));
        }

        @Test
        void shouldDispatchToForHtmlAttribute_whenContextIsHtmlAttribute() throws JspException {
            run("htmlAttribute", "\" onerror=alert(1)");
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlAttribute("\" onerror=alert(1)"));
        }

        @Test
        void shouldDispatchToForHtmlUnquotedAttribute_whenContextIsSet() throws JspException {
            run("htmlUnquotedAttribute", "value with spaces");
            assertThat(captured.toString()).isEqualTo(Encode.forHtmlUnquotedAttribute("value with spaces"));
        }

        @Test
        void shouldDispatchToForJavaScript_whenContextIsJavaScript() throws JspException {
            run("javaScript", "alert('xss')");
            assertThat(captured.toString()).isEqualTo(Encode.forJavaScript("alert('xss')"));
        }

        @Test
        void shouldDispatchToForJavaScript_whenContextIsJsShorthand() throws JspException {
            run("js", "alert('xss')");
            assertThat(captured.toString()).isEqualTo(Encode.forJavaScript("alert('xss')"));
        }

        @Test
        void shouldDispatchToForJavaScriptAttribute_whenContextIsSet() throws JspException {
            run("javaScriptAttribute", "value");
            assertThat(captured.toString()).isEqualTo(Encode.forJavaScriptAttribute("value"));
        }

        @Test
        void shouldDispatchToForJavaScriptBlock_whenContextIsSet() throws JspException {
            run("javaScriptBlock", "value");
            assertThat(captured.toString()).isEqualTo(Encode.forJavaScriptBlock("value"));
        }

        @Test
        void shouldDispatchToForUri_whenContextIsUri() throws JspException {
            run("uri", "hello world?x=1&y=2");
            assertThat(captured.toString()).isEqualTo(Encode.forUri("hello world?x=1&y=2"));
        }

        @Test
        void shouldDispatchToForUriComponent_whenContextIsUriComponent() throws JspException {
            run("uriComponent", "hello world?x=1&y=2");
            assertThat(captured.toString()).isEqualTo(Encode.forUriComponent("hello world?x=1&y=2"));
        }

        @Test
        void shouldDispatchToForCssString_whenContextIsCss() throws JspException {
            run("css", "attack\"ed");
            assertThat(captured.toString()).isEqualTo(Encode.forCssString("attack\"ed"));
        }

        @Test
        void shouldDispatchToForCssString_whenContextIsCssString() throws JspException {
            run("cssString", "attack\"ed");
            assertThat(captured.toString()).isEqualTo(Encode.forCssString("attack\"ed"));
        }

        @Test
        void shouldDispatchToForCssUrl_whenContextIsCssUrl() throws JspException {
            run("cssUrl", "/path/to/image.png");
            assertThat(captured.toString()).isEqualTo(Encode.forCssUrl("/path/to/image.png"));
        }

        @Test
        void shouldDispatchToForXml_whenContextIsXml() throws JspException {
            run("xml", "<x>ok</x>");
            assertThat(captured.toString()).isEqualTo(Encode.forXml("<x>ok</x>"));
        }

        @Test
        void shouldDispatchToForXmlAttribute_whenContextIsSet() throws JspException {
            run("xmlAttribute", "attr\"value");
            assertThat(captured.toString()).isEqualTo(Encode.forXmlAttribute("attr\"value"));
        }

        @Test
        void shouldDispatchToForCDATA_whenContextIsCdata() throws JspException {
            run("cdata", "section data]]>");
            assertThat(captured.toString()).isEqualTo(Encode.forCDATA("section data]]>"));
        }

        @Test
        void shouldBeCaseInsensitive_forContextNames() throws JspException {
            // HTML / Html / hTmL all route to forHtmlContent
            for (String variant : new String[] {"HTML", "Html", "hTmL", "HtMl"}) {
                CarlosEncodeTag t = new CarlosEncodeTag();
                StringWriter sw = new StringWriter();
                t.setPageContext(new CapturingPageContext(new CapturingJspWriter(sw)));
                t.setContext(variant);
                t.setValue("<x>");
                t.doStartTag();
                assertThat(sw.toString()).as("context=%s", variant)
                        .isEqualTo(Encode.forHtmlContent("<x>"));
            }
        }

        private void run(String context, String value) throws JspException {
            tag.setContext(context);
            tag.setValue(value);
            tag.doStartTag();
        }
    }

    @Nested
    @DisplayName("Invalid context")
    class InvalidContext {

        @Test
        @DisplayName("should throw JspException when context is unknown")
        void shouldThrowJspException_whenContextIsUnknown() {
            tag.setContext("bogusContext");
            tag.setValue("value");
            assertThatThrownBy(() -> tag.doStartTag())
                    .isInstanceOf(JspException.class)
                    .hasMessageContaining("bogusContext");
        }
    }

    @Nested
    @DisplayName("State lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should clear attributes on release")
        void shouldClearAttributes_onRelease() {
            tag.setValue("foo");
            tag.setContext("html");
            tag.release();
            // After release, re-invoking with no setters should produce empty (null value -> empty)
            CarlosEncodeTag sameTag = tag;
            StringWriter sw = new StringWriter();
            sameTag.setPageContext(new CapturingPageContext(new CapturingJspWriter(sw)));
            try {
                sameTag.doStartTag();
            } catch (JspException e) {
                throw new AssertionError(e);
            }
            assertThat(sw.toString()).isEmpty();
        }
    }

    // -------- Test doubles --------

    /** Minimal JspWriter that writes everything through to the underlying {@link Writer}. */
    private static final class CapturingJspWriter extends JspWriter {
        private final Writer delegate;

        CapturingJspWriter(Writer delegate) {
            super(NO_BUFFER, false);
            this.delegate = delegate;
        }

        @Override public void write(int c) throws IOException { delegate.write(c); }
        @Override public void write(char[] cbuf, int off, int len) throws IOException { delegate.write(cbuf, off, len); }
        @Override public void write(String s) throws IOException { delegate.write(s); }
        @Override public void write(String s, int off, int len) throws IOException { delegate.write(s, off, len); }
        @Override public void newLine() throws IOException { delegate.write(System.lineSeparator()); }
        @Override public void print(boolean b) throws IOException { delegate.write(String.valueOf(b)); }
        @Override public void print(char c) throws IOException { delegate.write(String.valueOf(c)); }
        @Override public void print(int i) throws IOException { delegate.write(String.valueOf(i)); }
        @Override public void print(long l) throws IOException { delegate.write(String.valueOf(l)); }
        @Override public void print(float f) throws IOException { delegate.write(String.valueOf(f)); }
        @Override public void print(double d) throws IOException { delegate.write(String.valueOf(d)); }
        @Override public void print(char[] s) throws IOException { delegate.write(s); }
        @Override public void print(String s) throws IOException { delegate.write(s == null ? "null" : s); }
        @Override public void print(Object obj) throws IOException { delegate.write(obj == null ? "null" : obj.toString()); }
        @Override public void println() throws IOException { newLine(); }
        @Override public void println(boolean x) throws IOException { print(x); newLine(); }
        @Override public void println(char x) throws IOException { print(x); newLine(); }
        @Override public void println(int x) throws IOException { print(x); newLine(); }
        @Override public void println(long x) throws IOException { print(x); newLine(); }
        @Override public void println(float x) throws IOException { print(x); newLine(); }
        @Override public void println(double x) throws IOException { print(x); newLine(); }
        @Override public void println(char[] x) throws IOException { print(x); newLine(); }
        @Override public void println(String x) throws IOException { print(x); newLine(); }
        @Override public void println(Object x) throws IOException { print(x); newLine(); }
        @Override public void clear() { /* no-op for tests */ }
        @Override public void clearBuffer() { /* no-op for tests */ }
        @Override public void flush() throws IOException { delegate.flush(); }
        @Override public void close() throws IOException { delegate.close(); }
        @Override public int getRemaining() { return Integer.MAX_VALUE; }
    }

    /** Minimal PageContext that only exposes {@link #getOut()}. Everything else throws. */
    @SuppressWarnings("deprecation")
    private static final class CapturingPageContext extends PageContext {
        private final JspWriter out;

        CapturingPageContext(JspWriter out) {
            this.out = out;
        }

        @Override public JspWriter getOut() { return out; }

        // Everything below is unused by CarlosEncodeTag and throws if called unexpectedly.
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
        @Override public HttpSession getSession() { throw new UnsupportedOperationException(); }
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
        @Override public java.util.Enumeration<String> getAttributeNamesInScope(int scope) { return null; }
        @Override public ELContext getELContext() { throw new UnsupportedOperationException(); }
    }
}
