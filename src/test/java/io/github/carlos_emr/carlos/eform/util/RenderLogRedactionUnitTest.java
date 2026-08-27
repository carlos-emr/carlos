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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit tests for the extracted {@link RenderLogRedaction} PHI-safe log helper: the render
 * surfaces route third-party error text through it before logging, so a tokenized render URL or a
 * bare filesystem path can never reach the logs.
 */
@DisplayName("RenderLogRedaction unit tests")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class RenderLogRedactionUnitTest {

    @Test
    @DisplayName("should redact URLs from third-party error text before logging")
    void shouldRedactUrls_fromErrorText() {
        String redacted = RenderLogRedaction.redactUrls(
                "timeout navigating to https://127.0.0.1:8443/carlos/EFormViewForPdfGenerationServlet?fdid=9 after 30s");

        assertThat(redacted)
                .doesNotContain("fdid=9")
                .doesNotContain("127.0.0.1")
                .contains("[redacted-url]");
        assertThat(RenderLogRedaction.redactUrls(null)).isNull();
        // Non-http schemes and bare filesystem paths are redacted too.
        assertThat(RenderLogRedaction.redactUrls("open file:///etc/passwd failed"))
                .doesNotContain("/etc/passwd").contains("[redacted-url]");
        assertThat(RenderLogRedaction.redactUrls("cannot read /var/lib/OscarDocument/secret.pdf"))
                .doesNotContain("/var/lib/OscarDocument/secret.pdf").contains("[redacted-path]");
        // Windows drive-letter and UNC paths are redacted too.
        assertThat(RenderLogRedaction.redactUrls("cannot read C:\\Users\\clinic\\secret.pdf"))
                .doesNotContain("Users").doesNotContain("secret.pdf").contains("[redacted-path]");
        assertThat(RenderLogRedaction.redactUrls("chromedriver at C:/tools/chromedriver.exe not found"))
                .doesNotContain("tools").doesNotContain("chromedriver.exe").contains("[redacted-path]");
        assertThat(RenderLogRedaction.redactUrls("cannot reach \\\\fileserver\\share\\doc.pdf"))
                .doesNotContain("fileserver").doesNotContain("doc.pdf").contains("[redacted-path]");
    }

    @Test
    @DisplayName("should summarize the top stack frames without any message or URL text")
    void shouldSummarizeTopFrames_withoutMessageOrUrl() {
        Throwable throwable = new IllegalStateException(
                "navigating to https://127.0.0.1:8443/carlos/EFormViewForPdfGenerationServlet?fdid=9 failed");

        String summary = RenderLogRedaction.stackSummary(throwable);

        // Frame-only: the exception message (which can embed the tokenized render URL) is never
        // included, only class.method:line frames.
        assertThat(summary)
                .doesNotContain("fdid=9")
                .doesNotContain("127.0.0.1")
                .contains(RenderLogRedactionUnitTest.class.getName())
                .contains(":");
    }

    @Test
    @DisplayName("should return an empty summary for a throwable with no stack frames")
    void shouldReturnEmptySummary_whenNoFrames() {
        Throwable throwable = new IllegalStateException("boom");
        throwable.setStackTrace(new StackTraceElement[0]);

        assertThat(RenderLogRedaction.stackSummary(throwable)).isEmpty();
    }

    @Test
    @DisplayName("should redact every cause message in the chain, tokens included")
    void shouldRedactEveryCauseMessage_inTheChain() {
        // causeChain is on the ERROR log path for every session failure. Each nested cause
        // message must pass through redactUrls — a refactor to cause.toString() (the natural
        // spelling) would re-emit a WebDriver cause embedding the render URL and the
        // --url-base bearer token, unredacted, at default log levels.
        String token = "9f3c1a2b4d5e6f70";
        Throwable inner = new IllegalStateException(
                "connect failed to http://127.0.0.1:9515/" + token + "/session");
        Throwable outer = new RuntimeException("session creation failed", inner);

        String chain = RenderLogRedaction.causeChain(outer);

        assertThat(chain).doesNotContain(token).doesNotContain("9515");
        assertThat(chain).contains("IllegalStateException");
    }

    @Test
    @DisplayName("should terminate the cause chain on a two-node cycle")
    void shouldTerminateCauseChain_onTwoNodeCycle() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(5),
                () -> assertThat(RenderLogRedaction.causeChain(first)).isNotBlank());
    }
}
