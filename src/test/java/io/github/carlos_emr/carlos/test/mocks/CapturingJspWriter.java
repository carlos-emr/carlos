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
import java.io.Writer;

import jakarta.servlet.jsp.JspWriter;

/**
 * Minimal {@link JspWriter} that forwards everything to an in-memory {@link Writer},
 * so a JSP tag can be executed in a plain unit test and its rendered bytes asserted.
 *
 * <p>Pair with {@link CapturingPageContext}. Shared rather than re-declared per test
 * class: the subtle part is that {@code print(null)} must render the literal
 * {@code "null"} exactly as a container's writer does, otherwise a test can pass while
 * hiding a null-handling regression in the tag under test.
 *
 * @since 2026-09-13
 */
public final class CapturingJspWriter extends JspWriter {

    private final Writer delegate;

    public CapturingJspWriter(Writer delegate) {
        super(NO_BUFFER, false);
        this.delegate = delegate;
    }

    /** Convenience factory for the common "capture into a StringWriter" case. */
    public static CapturingJspWriter into(StringWriter target) {
        return new CapturingJspWriter(target);
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
