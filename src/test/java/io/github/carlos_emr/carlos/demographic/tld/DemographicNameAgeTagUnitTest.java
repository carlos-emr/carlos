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
package io.github.carlos_emr.carlos.demographic.tld;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.tagext.Tag;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.github.carlos_emr.carlos.demographic.data.DemographicNameAgeString;
import io.github.carlos_emr.carlos.test.mocks.CapturingPageContext;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Behavioural coverage for {@code <oscar:nameage>}.
 *
 * <p>A patient name is stored, attacker-influenceable data, so these tests execute the tag
 * against an in-memory {@link jakarta.servlet.jsp.JspWriter} and assert on the bytes it
 * actually renders, rather than on the shape of its source. The final test walks the JSP
 * call sites: HTML body encoding does not escape quotes, so a call site that renders the
 * label into a script block or an HTML attribute must select a matching context.
 *
 * @since 2026-09-13
 */
@org.junit.jupiter.api.Tag("unit")
@org.junit.jupiter.api.Tag("demographic")
@org.junit.jupiter.api.Tag("security")
@DisplayName("DemographicNameAgeTag")
class DemographicNameAgeTagUnitTest {

    private static final Integer DEMOGRAPHIC_NO = 4321;

    /** A stored name carrying every character that breaks out of one sink or another. */
    private static final String HOSTILE_LABEL = "</script><img src=x onerror=\"alert('x&y')\">, First M 42 years";

    private DemographicNameAgeTag tag;
    private StringWriter rendered;
    private MockedStatic<DemographicNameAgeString> nameAgeLookup;

    @BeforeEach
    void setUp() {
        tag = new DemographicNameAgeTag();
        rendered = new StringWriter();
        tag.setPageContext(CapturingPageContext.writingTo(rendered, mock(HttpSession.class)));
        tag.setDemographicNo(String.valueOf(DEMOGRAPHIC_NO));

        DemographicNameAgeString lookup = mock(DemographicNameAgeString.class);
        when(lookup.getNameAgeString(any(), eq(DEMOGRAPHIC_NO))).thenReturn(HOSTILE_LABEL);
        nameAgeLookup = mockStatic(DemographicNameAgeString.class);
        nameAgeLookup.when(DemographicNameAgeString::getInstance).thenReturn(lookup);
    }

    @AfterEach
    void tearDown() {
        nameAgeLookup.close();
    }

    @Nested
    @DisplayName("Rendered output")
    class RenderedOutput {

        @Test
        @DisplayName("should HTML-encode the patient label when no context is set")
        void shouldHtmlEncodeLabel_forDefaultContext() throws JspException {
            assertThat(tag.doStartTag()).isEqualTo(Tag.SKIP_BODY);

            assertThat(rendered.toString())
                    .isEqualTo(SafeEncode.forHtmlContent(HOSTILE_LABEL))
                    .doesNotContain("<img")
                    .doesNotContain("</script>")
                    .contains("&lt;img")
                    .contains("&amp;y");
        }

        @Test
        @DisplayName("should JavaScript-encode the patient label when rendered into a script string")
        void shouldJavaScriptEncodeLabel_whenContextIsJavaScript() throws JspException {
            tag.setContext("javaScript");

            tag.doStartTag();

            assertThat(rendered.toString())
                    .isEqualTo(SafeEncode.forJavaScript(HOSTILE_LABEL))
                    .doesNotContain("\"")
                    .doesNotContain("'")
                    .doesNotContain("</script>");
        }

        @Test
        @DisplayName("should escape quotes when the label is rendered into an HTML attribute")
        void shouldEscapeQuotes_whenContextIsHtmlAttribute() throws JspException {
            tag.setContext("htmlAttribute");

            tag.doStartTag();

            assertThat(rendered.toString())
                    .isEqualTo(SafeEncode.forHtmlAttribute(HOSTILE_LABEL))
                    .doesNotContain("\"")
                    .doesNotContain("'");
        }

        @Test
        @DisplayName("should accept context names case-insensitively")
        void shouldAcceptContextName_forMixedCase() throws JspException {
            tag.setContext("JavaScript");

            tag.doStartTag();

            assertThat(rendered.toString()).isEqualTo(SafeEncode.forJavaScript(HOSTILE_LABEL));
        }
    }

    @Nested
    @DisplayName("Failure handling")
    class FailureHandling {

        @Test
        @DisplayName("should render nothing when the demographic number is not numeric")
        void shouldRenderNothing_whenDemographicNoIsNotNumeric() throws JspException {
            tag.setDemographicNo("not-a-number");

            assertThat(tag.doStartTag()).isEqualTo(Tag.SKIP_BODY);
            assertThat(rendered.toString()).isEmpty();
        }

        @Test
        @DisplayName("should fail the render rather than emit the label when the context is unknown")
        void shouldFailRender_whenContextIsUnknown() {
            tag.setContext("bogusContext");

            assertThatThrownBy(() -> tag.doStartTag())
                    .isInstanceOf(JspException.class)
                    .hasMessageContaining("bogusContext");
            assertThat(rendered.toString()).isEmpty();
        }

        @Test
        @DisplayName("should clear attributes when the container recycles the tag")
        void shouldClearAttributes_whenTagIsReleased() {
            tag.setContext("javaScript");

            tag.release();

            assertThat(tag.getContext()).isNull();
            assertThat(tag.getDemographicNo()).isNull();
        }
    }

    @Nested
    @DisplayName("JSP call sites")
    class JspCallSites {

        private static final Pattern NAMEAGE_TAG = Pattern.compile("<oscar:nameage\\b");
        private static final Pattern ATTRIBUTE_START = Pattern.compile("([\\w:-]+)\\s*=\\s*([\"'])");
        private static final Pattern JSP_BLOCK = Pattern.compile("<%--[\\s\\S]*?--%>|<%[\\s\\S]*?%>");

        @Test
        @DisplayName("should select a non-HTML context wherever the label leaves the HTML body")
        void shouldSelectNonHtmlContext_forEveryNonBodyCallSite() {
            for (Path jsp : jspFiles()) {
                String source = readFile(jsp);
                String scanned = maskJspBlocks(source);
                Matcher usages = NAMEAGE_TAG.matcher(scanned);
                while (usages.find()) {
                    int start = usages.start();
                    String sink = sinkAt(scanned, start);
                    if (sink != null) {
                        assertThat(tagElementAt(source, start))
                                .as("<oscar:nameage> rendered into %s at %s offset %s", sink, jsp, start)
                                .contains("context=");
                    }
                }
            }
        }

        /** Name of the non-HTML-body sink containing {@code index}, or {@code null} when it is body text. */
        private String sinkAt(String jsp, int index) {
            if (jsp.lastIndexOf("<script", index) > jsp.lastIndexOf("</script", index)) {
                return "a script block";
            }
            Matcher attributes = ATTRIBUTE_START.matcher(jsp);
            while (attributes.find() && attributes.start() < index) {
                char quote = attributes.group(2).charAt(0);
                int attributeEnd = jsp.indexOf(quote, attributes.end());
                if (attributeEnd >= index) {
                    return "the '" + attributes.group(1) + "' attribute";
                }
            }
            return null;
        }

        private String tagElementAt(String jsp, int start) {
            int end = jsp.indexOf('>', start);
            return end < 0 ? jsp.substring(start) : jsp.substring(start, end + 1);
        }

        /**
         * Blank out scriptlets, expressions, and JSP comments while preserving offsets, so
         * Java string literals such as {@code "&amp;measurement="} are not mistaken for the
         * start of an HTML attribute.
         */
        private String maskJspBlocks(String jsp) {
            StringBuilder masked = new StringBuilder(jsp);
            Matcher blocks = JSP_BLOCK.matcher(jsp);
            while (blocks.find()) {
                for (int i = blocks.start(); i < blocks.end(); i++) {
                    if (masked.charAt(i) != '\n') {
                        masked.setCharAt(i, ' ');
                    }
                }
            }
            return masked.toString();
        }

        private List<Path> jspFiles() {
            Path webapp = resolveProjectPath(Path.of("src", "main", "webapp"));
            try (Stream<Path> files = Files.walk(webapp)) {
                return files.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.endsWith(".jsp") || name.endsWith(".jspf");
                        })
                        .toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /** Decode leniently: a handful of legacy JSPs are not valid UTF-8 and must not fail the scan. */
        private String readFile(Path path) {
            try {
                return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth <= 8 && current != null; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate project path: " + relativePath);
    }
}
