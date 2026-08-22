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
package io.github.carlos_emr.carlos.eform.util;

/**
 * PHI-safe redaction for the eForm browser-render diagnostics.
 *
 * <p>The render surfaces (the loopback render-page servlet and the Selenium/CDP renderer) log
 * third-party error text — WebDriver/chromedriver messages, settle-script errors, container
 * exceptions — that can embed the tokenized render URL (which carries the {@code fdid} and the live
 * render token) or a bare filesystem path. All four render surfaces — the Selenium/CDP renderer
 * ({@link EFormBrowserPdfService}), the render-page servlet ({@link EFormBrowserRenderPageServlet}),
 * and the asset-image and signature servlets that stream render assets — route that text through here
 * before it reaches the logs, so every render-surface catch block shares one redaction contract and
 * cannot drift.</p>
 *
 * <p>Static utility, no state; not instantiable.</p>
 */
final class RenderLogRedaction {

    /** Placeholder substituted for filesystem paths in redacted diagnostics (SonarCloud S1192). */
    private static final String REDACTED_PATH = "[redacted-path]";

    private RenderLogRedaction() {
    }

    /** Strips URLs from third-party error text before it reaches logs (PHI-safe diagnostics). */
    static String redactUrls(String text) {
        if (text == null) {
            return null;
        }
        // Strip http(s) plus other schemes and bare filesystem paths (Unix, Windows drive-letter, and
        // UNC) that a WebDriver/settle error could embed, so no URL or local path reaches the logs.
        // Order matters: the scheme://... rule runs first so a c://… URL is consumed before the
        // drive-letter rule can see it.
        return text
                .replaceAll("(?i)[a-z][a-z0-9+.-]*://[^\\s'\"<>]+", "[redacted-url]")
                .replaceAll("\\\\\\\\[^\\s'\"<>]+", REDACTED_PATH)
                .replaceAll("(?i)(?<![\\w:])[a-z]:[\\\\/][^\\s'\"<>]*", REDACTED_PATH)
                .replaceAll("(?<![\\w./])/[\\w./-]{2,}", REDACTED_PATH);
    }

    /**
     * Compact frame-only stack summary of the top frames (class.method:line, innermost first).
     * Stack frames carry no URLs, messages, or PHI, so the summary is safe at ERROR level where
     * the raw WebDriver throwable (whose message can embed the tokenized render URL) is not.
     */
    static String stackSummary(Throwable throwable) {
        StackTraceElement[] frames = throwable.getStackTrace();
        int limit = Math.min(frames.length, 8);
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                summary.append(" < ");
            }
            StackTraceElement frame = frames[i];
            summary.append(frame.getClassName()).append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
        }
        if (frames.length > limit) {
            summary.append(" < ...");
        }
        return summary.toString();
    }

    /**
     * Redacted {@code caused-by} chain: for each nested cause, its type and URL/path-redacted message.
     * {@link #stackSummary} only reports the top throwable's frames, so the root cause that Selenium
     * and WebDriver routinely wrap (e.g. {@code WebDriverException} around a {@code TimeoutException} /
     * {@code ConnectException}) was otherwise unrecoverable at any log level. Cause types and redacted
     * messages are as safe to log as the top-level one. Returns {@code ""} when there is no cause.
     */
    static String causeChain(Throwable throwable) {
        StringBuilder chain = new StringBuilder();
        Throwable cause = throwable.getCause();
        int depth = 0;
        while (cause != null && depth < 8) {
            chain.append(" caused-by ").append(cause.getClass().getName()).append(": ")
                    .append(redactUrls(String.valueOf(cause.getMessage())));
            Throwable next = cause.getCause();
            if (next == cause) {
                break; // self-referential cause guard
            }
            cause = next;
            depth++;
        }
        return chain.toString();
    }
}
