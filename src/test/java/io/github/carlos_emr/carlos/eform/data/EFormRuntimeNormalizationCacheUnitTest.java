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
package io.github.carlos_emr.carlos.eform.data;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Pins the caching contract of {@link EForm#getFormHtml()}'s DOM normalization pass: it runs once
 * per content generation, and replacing the content re-arms it. Runs with a SpringUtils-mocked
 * base because the DOM pass parses through {@code ConvertToEdoc}, whose static init resolves
 * {@code NioFileManager}; without that registration the pass silently no-ops (its
 * {@code LinkageError} fallback), which is exactly the environment
 * {@code EFormSetContextPathUnitTest} exercises for the string-level rewrites.
 */
@DisplayName("EForm runtime normalization cache")
@Tag("unit")
@Tag("fast")
class EFormRuntimeNormalizationCacheUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerConvertToEdocDependencies() {
        // ConvertToEdoc resolves NioFileManager in a static-final initializer; register the mock
        // BEFORE anything touches that class or its static init fails and the DOM pass no-ops.
        registerMock(NioFileManager.class, Mockito.mock(NioFileManager.class));
    }

    @Test
    @DisplayName("should keep normalized output stable across repeated reads and re-normalize replaced content")
    void shouldRenormalizeContent_afterSetFormHtml() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body><script src=\"jquery-1.12.0.min.js\"></script></body></html>");
        eform.setContextPath("/carlos");
        eform.enableRenderNormalization(); // DOM pass is render-path opt-in; this test pins that pass

        // The DOM pass runs once per content generation and is cached; repeated reads must return
        // the identical normalized output.
        String first = eform.getFormHtml();
        String second = eform.getFormHtml();
        assertThat(first).contains("/carlos/eform/displayImage?imagefile=jquery-1.12.0.min.js");
        assertThat(second).isEqualTo(first);

        // Replacing the content (as the render composer does mid-pipeline) must reset the cache so
        // legacy constructs in the NEW content are normalized on the next read.
        eform.setFormHtml("<html><body><script src=\"/eform/jquery-1.12.0.min.js\"></script></body></html>");
        assertThat(eform.getFormHtml())
                .contains("/carlos/eform/displayImage?imagefile=jquery-1.12.0.min.js")
                .doesNotContain("src=\"/eform/jquery-1.12.0.min.js\"");
    }

    @Test
    @DisplayName("should return content set after a DOM-caching call rather than the stale cached document")
    void shouldReturnReplacedContent_whenSetAfterDocumentWasCached() {
        // Reproduces the composer's ordering against a real EForm: EFormRenderPdfHtmlComposer caches a
        // jsoup Document via addHeadJavascript() BEFORE applyLetterHtml() injects the stored Rich Text
        // Letter body with setFormHtml(). If setFormHtml does not invalidate that cache, getFormHtml()
        // re-serializes the pre-letter template and the clinician's letter is silently dropped from the
        // rendered/faxed/archived PDF while every render gate still passes.
        EForm eform = new EForm();
        eform.setFormHtml("<html><head></head><body>BLANK_EDITOR_TEMPLATE</body></html>");

        eform.addHeadJavascript("/carlos/eform/eform-runtime-compat.js"); // caches the Document

        eform.setFormHtml("<html><body style='width:640px;'>CLINICIAN_LETTER_BODY</body></html>");

        assertThat(eform.getFormHtml())
                .contains("CLINICIAN_LETTER_BODY")
                .doesNotContain("BLANK_EDITOR_TEMPLATE");
    }

    @Test
    @DisplayName("should not throw when applying a context path to an fdid that has no stored HTML")
    void shouldNotThrow_whenContextPathAppliedWithNullFormHtml() {
        // A numeric-but-unknown fdid leaves formHtml null; setContextPath must not NPE ahead of the
        // composer's descriptive IllegalStateException. Empty context path is the root-context case
        // that no longer short-circuits on the blank check.
        EForm eform = new EForm();

        eform.setContextPath("");
        eform.setContextPath("/carlos");

        assertThat(eform.getFormHtml()).isNull();
    }

    @Test
    @DisplayName("should log exactly one WARN across repeated getFormHtml() calls while DOM normalization keeps failing")
    void shouldLogWarnOnce_acrossRepeatedReadsWhenNormalizationKeepsFailing() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body><script src=\"jquery-1.12.0.min.js\"></script></body></html>");
        eform.setContextPath("/carlos");
        eform.enableRenderNormalization(); // DOM pass is render-path opt-in; this test pins that pass

        // ConvertToEdoc.parseDocument is the real forcing point for the RuntimeException/LinkageError
        // catch in getFormHtml(): getDocument() delegates to it directly, so a forced RuntimeException
        // here reliably drives the exact catch block under test - unlike relying on a genuine jsoup
        // parse failure (hard to construct) or on ConvertToEdoc's static-init LinkageError (whose
        // occurrence depends on JVM class-loading order across the whole Surefire fork; see the class
        // javadoc). registerConvertToEdocDependencies() above already registered the NioFileManager
        // mock, so ConvertToEdoc's static initializer has already run successfully before this
        // mockStatic() call - only its instance methods are stubbed to throw here.
        try (LogCapture logCapture = LogCapture.forLogger(EForm.class);
             MockedStatic<ConvertToEdoc> convertToEdoc = Mockito.mockStatic(ConvertToEdoc.class)) {
            convertToEdoc.when(() -> ConvertToEdoc.parseDocument(anyString()))
                    .thenThrow(new IllegalStateException("forced jsoup parse failure for this test"));

            // The DOM pass is retried on every read while it keeps failing (runtimeAssetsNormalized
            // never gets set), so both calls fall back to the string-level HTML.
            String first = eform.getFormHtml();
            String second = eform.getFormHtml();

            assertThat(first).isEqualTo(second);
            long warnCount = logCapture.events().stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .count();
            assertThat(warnCount)
                    .describedAs("WARN must fire exactly once per content generation, not on every retried read")
                    .isEqualTo(1);
            long debugCount = logCapture.events().stream()
                    .filter(event -> event.getLevel() == Level.DEBUG)
                    .count();
            assertThat(debugCount)
                    .describedAs("the full stack trace must still log at DEBUG on every retry")
                    .isEqualTo(2);
            LogEvent warnEvent = logCapture.events().stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .findFirst()
                    .orElseThrow();
            assertThat(warnEvent.getMessage().getFormattedMessage())
                    .contains("DOM-based eForm runtime normalization failed")
                    .contains("IllegalStateException");
        }
    }

    @Test
    @DisplayName("should re-arm the WARN-once flag when setFormHtml replaces the content")
    void shouldRearmWarnOnceFlag_whenSetFormHtmlReplacesContent() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body><script src=\"jquery-1.12.0.min.js\"></script></body></html>");
        eform.setContextPath("/carlos");
        eform.enableRenderNormalization(); // DOM pass is render-path opt-in; this test pins that pass

        try (LogCapture logCapture = LogCapture.forLogger(EForm.class);
             MockedStatic<ConvertToEdoc> convertToEdoc = Mockito.mockStatic(ConvertToEdoc.class)) {
            convertToEdoc.when(() -> ConvertToEdoc.parseDocument(anyString()))
                    .thenThrow(new IllegalStateException("forced jsoup parse failure for this test"));

            // First content generation: exactly one WARN, no matter how many reads follow.
            eform.getFormHtml();
            eform.getFormHtml();

            // setFormHtml resets both runtimeAssetsNormalized AND normalizationFailureLogged, so new
            // content that also fails to normalize earns its OWN WARN rather than staying silent
            // because a previous, unrelated content generation already logged one.
            eform.setFormHtml("<html><body><script src=\"/eform/jquery-1.12.0.min.js\"></script></body></html>");
            eform.getFormHtml();
            eform.getFormHtml();

            long warnCount = logCapture.events().stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .count();
            assertThat(warnCount)
                    .describedAs("a fresh WARN is owed once per content generation, not once per EForm instance")
                    .isEqualTo(2);
        }
    }

}
