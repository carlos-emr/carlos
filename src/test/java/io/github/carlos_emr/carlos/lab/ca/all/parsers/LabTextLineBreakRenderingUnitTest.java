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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Regression coverage for issue #3953: the lab display showed a literal {@code <br />} in HL7
 * result and NTE comment text.
 *
 * <p>The handlers translate the HL7 {@code \.br\} escape into a {@code <br />} marker (a
 * contract the lab PDF, upload splitter and export also rely on), and {@code labDisplay.jsp}
 * HTML-encoded that marker. These tests pin both halves: the handler still emits the marker,
 * and the view-side encoder turns it into a real line break while keeping hostile lab text
 * escaped. The JSP half pins that the lab views route that text through the break-aware
 * context instead of plain {@code html} (which shows the marker) or {@code replaceAll} to a
 * space (which loses the break).
 *
 * <p>Derived from open-osp/Open-O PR #225 (Liam Stanziani), which fixed the same rendering bug
 * in the upstream fork.
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("lab")
@Tag("security")
@DisplayName("HL7 lab text line-break rendering (issue #3953)")
class LabTextLineBreakRenderingUnitTest {

    /**
     * A minimal Excelleris-style result whose OBX-5, OBX-7 and following NTE carry the HL7
     * {@code \.br\} escape (the form real Excelleris results use, e.g.
     * {@code Final\.br\11Sep2013}), plus an OBX whose value is hostile markup that must never
     * reach the page as markup. Segments are CRLF-separated because the generic handler
     * tokenizes on newlines.
     *
     * @param messageType MSH-9. {@link DefaultGenericHandler} walks a flat segment list, which
     *                    HAPI only builds for a structure it does not model (a standard
     *                    {@code ORU^R01} parses into nested groups the generic handler cannot
     *                    read), so the generic case uses a non-standard trigger event.
     */
    private static String hl7(String messageType) {
        return String.join("\r\n",
                "MSH|^~\\&|PATHL7|CW|CARLOS|TEST|20260926120000||" + messageType + "|MSG3953|P|2.3|||ER|AL",
                "PID||9999999999|9999999999||FAKE-LINEBREAK^CHECK||19800101|F",
                "ORC|RE||ACC3953|||||||||99999^FAKE-ORDERING^DOC",
                "OBR|1||ACC3953|CHEM^Chemistry||20260926100000|20260926100000|||||||20260926100000"
                        + "||99999^FAKE-ORDERING^DOC||||||20260926115500||CHEM4|F|||99999^FAKE-ORDERING^DOC",
                "OBX|1|FT|XXX-0006^Report Status||Final\\.br\\11Sep2026||||||F|||20260926115500",
                "OBX|2|NM|2823-3^Potassium||4.1|mmol/L|Adult 3.5-5.0\\.br\\Child 3.4-4.7|||||F|||20260926115500",
                "NTE|1|L|Specimen received warm\\.br\\Repeat collection advised",
                "OBX|3|FT|XXX-0009^Note||<script>alert(3953)</script>\\.br\\<img src=x onerror=alert(1)>||||||F|||");
    }

    static Stream<Arguments> handlers() {
        return Stream.of(
                Arguments.of(Named.of("DefaultGenericHandler",
                        (Supplier<MessageHandler>) () -> parse(new DefaultGenericHandler(), "ORU^Z01"))),
                Arguments.of(Named.of("PATHL7Handler",
                        (Supplier<MessageHandler>) () -> parse(new PATHL7Handler(), "ORU^R01"))));
    }

    private static MessageHandler parse(MessageHandler handler, String messageType) {
        try {
            handler.init(hl7(messageType));
        } catch (Exception e) {
            throw new IllegalStateException("fixture HL7 did not parse", e);
        }
        return handler;
    }

    @Nested
    @DisplayName("Handler contract")
    class HandlerContract {

        @ParameterizedTest
        @MethodSource("io.github.carlos_emr.carlos.lab.ca.all.parsers.LabTextLineBreakRenderingUnitTest#handlers")
        void shouldTranslateHl7BreakEscapeToMarker_inObxResult(Supplier<MessageHandler> factory) {
            assertThat(factory.get().getOBXResult(0, 0)).isEqualTo("Final<br />11Sep2026");
        }

        @ParameterizedTest
        @MethodSource("io.github.carlos_emr.carlos.lab.ca.all.parsers.LabTextLineBreakRenderingUnitTest#handlers")
        void shouldTranslateHl7BreakEscapeToMarker_inObxComment(Supplier<MessageHandler> factory) {
            MessageHandler handler = factory.get();

            assertThat(handler.getOBXCommentCount(0, 1)).isEqualTo(1);
            assertThat(handler.getOBXComment(0, 1, 0))
                    .isEqualTo("Specimen received warm<br />Repeat collection advised");
        }
    }

    @Nested
    @DisplayName("View rendering")
    class ViewRendering {

        @ParameterizedTest
        @MethodSource("io.github.carlos_emr.carlos.lab.ca.all.parsers.LabTextLineBreakRenderingUnitTest#handlers")
        void shouldRenderResultBreakAsLineBreak_whenEncodedForLabView(Supplier<MessageHandler> factory) {
            String html = SafeEncode.forHtmlContentWithBreakMarkers(factory.get().getOBXResult(0, 0));

            assertThat(html).isEqualTo("Final<br/>11Sep2026").doesNotContain("&lt;br");
        }

        @ParameterizedTest
        @MethodSource("io.github.carlos_emr.carlos.lab.ca.all.parsers.LabTextLineBreakRenderingUnitTest#handlers")
        void shouldRenderCommentBreakAsLineBreak_whenEncodedForLabView(Supplier<MessageHandler> factory) {
            String html = SafeEncode.forHtmlContentWithBreakMarkers(factory.get().getOBXComment(0, 1, 0));

            assertThat(html).isEqualTo("Specimen received warm<br/>Repeat collection advised");
        }

        @Test
        void shouldRenderReferenceRangeBreakAsLineBreak_whenEncodedForLabView() {
            MessageHandler handler = parse(new PATHL7Handler(), "ORU^R01");
            String html = SafeEncode.forHtmlContentWithBreakMarkers(handler.getOBXReferenceRange(0, 1));

            assertThat(html).isEqualTo("Adult 3.5-5.0<br/>Child 3.4-4.7");
        }

        @ParameterizedTest
        @MethodSource("io.github.carlos_emr.carlos.lab.ca.all.parsers.LabTextLineBreakRenderingUnitTest#handlers")
        void shouldKeepHostileResultEscaped_whenEncodedForLabView(Supplier<MessageHandler> factory) {
            String html = SafeEncode.forHtmlContentWithBreakMarkers(factory.get().getOBXResult(0, 2));

            assertThat(html)
                    .isEqualTo("&lt;script&gt;alert(3953)&lt;/script&gt;<br/>&lt;img src=x onerror=alert(1)&gt;")
                    .doesNotContain("<script", "<img");
        }

        @Test
        void shouldShowLiteralMarker_whenPlainHtmlEncodingIsUsed() {
            // The defect itself: the pre-fix context renders the marker as visible text.
            assertThat(SafeEncode.forHtmlContent(parse(new PATHL7Handler(), "ORU^R01").getOBXResult(0, 0)))
                    .isEqualTo("Final&lt;br /&gt;11Sep2026");
        }
    }

    @Nested
    @DisplayName("Lab views")
    class LabViews {

        private static final Pattern LINE_TEXT_ENCODE = Pattern.compile(
                "<carlos:encode value='<%=\\s*handler\\.get(?:OBXResult|OBXComment|OBRComment|OBXReferenceRange)"
                        + "\\([^)]*\\)[^']*'\\s+context=\"([^\"]+)\"");

        @ParameterizedTest
        @ValueSource(strings = {
                "src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplay.jsp",
                "src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplayAjax.jsp"})
        void shouldRenderHandlerTextWithBreakMarkers_inLabView(String jsp) throws IOException {
            String source = Files.readString(Path.of(jsp), StandardCharsets.UTF_8);

            assertThat(source)
                    .as("%s must not flatten handler line breaks to spaces", jsp)
                    .doesNotContain("replaceAll(\"<br />\", \" \")");

            Matcher matcher = LINE_TEXT_ENCODE.matcher(source);
            int sites = 0;
            while (matcher.find()) {
                sites++;
                assertThat(matcher.group(1))
                        .as("%s: %s", jsp, matcher.group())
                        .isEqualTo("htmlWithBreakMarkers");
            }
            assertThat(sites).as("%s renders handler result/comment text", jsp).isPositive();
        }
    }
}
