/*
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.all.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.ExcellerisOntarioHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/** Exercises reference ranges through complete PDF generation, including handler-produced breaks. */
@Tag("unit")
@Tag("lab")
@Tag("pdf")
@DisplayName("Lab PDF reference-range rendering")
class LabPDFCreatorReferenceRangeUnitTest extends CarlosUnitTestBase {

    static Stream<MessageHandler> handlers() {
        return Stream.of(new PATHL7Handler(), new ExcellerisOntarioHandler());
    }

    private static MessageHandler initialize(MessageHandler handler) throws Exception {
        String version = handler instanceof ExcellerisOntarioHandler ? "2.3.1" : "2.3";
        handler.init(String.join("\r\n",
                "MSH|^~\\&|PATHL7|CW|CARLOS|TEST|20261005120000||ORU^R01|TEST4272|P|" + version + "|||ER|AL",
                "PID||9999999999|9999999999||FAKE-RANGE^CHECK||19800101|F",
                "ORC|RE||ACC4272|||||||||99999^FAKE-ORDERING^DOC",
                "OBR|1||ACC4272|CHEM^Chemistry||20261005100000|20261005100000|||||||20261005100000"
                        + "||99999^FAKE-ORDERING^DOC||||||20261005115500||CHEM4|F|||99999^FAKE-ORDERING^DOC",
                "OBX|1|NM|2823-3^Potassium||4.1|mmol/L|Adult 3-5\\.br\\Child 2-4|||||F|||20261005115500"));
        return handler;
    }

    private static String print(MessageHandler handler) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            LabPDFCreator creator = new LabPDFCreator();
            creator.setOs(out);
            creator.setHandler(handler);
            creator.printPdf();
            try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(pdf);
            }
        }
    }

    private static void assertSeparateRangeLines(String text) {
        List<String> lines = text.lines().toList();
        int adult = -1;
        int child = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("Adult 3-5")) adult = i;
            if (lines.get(i).contains("Child 2-4")) child = i;
        }
        assertThat(adult).as("adult range is printed").isNotNegative();
        assertThat(child).as("child range is printed on a subsequent line").isGreaterThan(adult);
        assertThat(text).doesNotContain("<br", "<BR");
    }

    @ParameterizedTest
    @MethodSource("handlers")
    void shouldPrintSeparateRangeLines_whenHandlerDecodesHl7Break(MessageHandler handler) throws Exception {
        initialize(handler);
        assertThat(handler.getOBXReferenceRange(0, 0)).isEqualTo("Adult 3-5<br />Child 2-4");

        assertSeparateRangeLines(print(handler));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<br>", "<br/>", "<br />", "<BR />", "\n", "\r\n"})
    void shouldKeepRangeLinesSeparate_withSupportedBreakVariants(String marker) throws Exception {
        MessageHandler handler = spy(initialize(new PATHL7Handler()));
        doReturn("Adult 3-5" + marker + "Child 2-4").when(handler).getOBXReferenceRange(0, 0);

        assertSeparateRangeLines(print(handler));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.5-5.0", "<5 & >2", "&lt;5", "<brx>", "<br id=x>"})
    void shouldKeepRangeTextLiteral_withoutInterpretingHtml(String range) throws Exception {
        MessageHandler handler = spy(initialize(new PATHL7Handler()));
        doReturn(range).when(handler).getOBXReferenceRange(0, 0);

        assertThat(print(handler)).contains(range);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldPrintReport_whenRangeIsAbsent(String range) throws Exception {
        MessageHandler handler = spy(initialize(new PATHL7Handler()));
        doReturn(range).when(handler).getOBXReferenceRange(0, 0);

        assertThat(print(handler)).contains("Potassium", "4.1", "END OF REPORT");
    }
}
