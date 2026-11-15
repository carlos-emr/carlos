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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("cds-export")
class CdsExportReportRetainedSourceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111", HASH = "a".repeat(64);
    private final List<CdsExportRetainedFieldDto> fields = new ArrayList<>();
    private final Map<String, String> metadata = new HashMap<>();
    private final Map<String, String> texts = new HashMap<>();
    private final Map<String, byte[]> binaries = new HashMap<>();
    private final List<CdsReportNativeDto.Variant> variants = new ArrayList<>();
    private final Map<String, CdsTemporalDto> dates = new HashMap<>();
    private CdsExportRetainedSectionWriter.Values values;
    private int shift;

    @BeforeEach
    void setup() throws Exception {
        values = mock(CdsExportRetainedSectionWriter.Values.class);
        when(values.text(any()))
                .thenAnswer(
                        call ->
                                new StringReader(
                                        texts.get(
                                                call.getArgument(0, CdsExportRetainedFieldDto.class)
                                                        .id())));
        when(values.binary(any()))
                .thenAnswer(
                        call ->
                                new ByteArrayInputStream(
                                        binaries.get(
                                                call.getArgument(0, CdsExportRetainedFieldDto.class)
                                                        .id())));
        scalar("Format", "Text");
        scalar("Class", "Consultant Report");
    }

    @Test
    void shouldBindLargeUnicodeText_andPreserveEverySourceField() throws Exception {
        String text = "Clinical 漢字 😀\n".repeat(6000);
        embedded(text);
        var result = read();
        assertThat(result.sourceFields()).hasSize(3);
        assertThat(result.variants().get("embedded").length())
                .isEqualTo(text.getBytes(StandardCharsets.UTF_8).length);
        assertThat(result.changes()).isEmpty();
        verify(values).text(fields.getLast());
    }

    @Test
    void shouldKeepReferencedAndEmbeddedVariants_asSeparateRepresentations() throws Exception {
        embedded("Embedded");
        field("FilePath", "reports/original.txt");
        variants.add(variant("referenced", "Referenced bytes".getBytes(StandardCharsets.UTF_8)));
        var result = read();
        assertThat(result.variants()).hasSize(2);
        assertThat(result.originalFilePath()).isEqualTo("reports/original.txt");
        assertThat(result.filePath()).isEqualTo(fields.getLast());
    }

    @Test
    void shouldPermitReferenceOnly_withoutInventingAnEmbeddedCopy() throws Exception {
        field("FilePath", "reports/original.txt");
        variants.add(variant("referenced", new byte[] {1, 2}));
        assertThat(read().variants()).containsOnlyKeys("referenced");
    }

    @Test
    void shouldAuthenticateDecodedMediaBytes_insteadOfBase64Spelling() throws Exception {
        replaceFormat("Binary");
        byte[] bytes = {0, 1, 2, (byte) 255};
        binary("Content/Media", bytes);
        variants.add(variant("embedded", bytes));
        assertThat(read().sourceFields()).hasSize(3);
        verify(values).binary(fields.getLast());
    }

    @Test
    void shouldDecodeExplicitBinaryText_usingStrictSourceContract() throws Exception {
        replaceFormat("Binary");
        field("Content/TextContent", " AAH/\n");
        metadata.put("ContentEncoding", "base64-from-text");
        variants.add(variant("embedded", new byte[] {0, 1, (byte) 255}));
        assertThat(read().sourceFields()).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AB==", "AA==AAAA", "AA=", "AA==é", "AA-="})
    void shouldRefuseInvalidBase64_beforeReturningSourceEvidence(String encoded) {
        replaceFormat("Binary");
        field("Content/TextContent", encoded);
        metadata.put("ContentEncoding", "base64-from-text");
        variants.add(variant("embedded", new byte[] {0}));
        assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"YearOnly", "YearMonth", "FullDate", "FullDateTime"})
    void shouldPreserveAllTemporalChoices_andOriginalClockSpelling(String choice) throws Exception {
        embedded("Report");
        String lexical =
                switch (choice) {
                    case "YearOnly" -> "2020";
                    case "YearMonth" -> "2020-02";
                    case "FullDate" -> "2020-02-28";
                    default -> "2020-02-28T10:20:30.123-05:00";
                };
        date("EventDateTime[1]", choice, lexical);
        var result = read();
        assertThat(result.dates().get("EventDateTime[1]").lexical()).isEqualTo(lexical);
        assertThat(result.changes()).isEmpty();
    }

    @Test
    void shouldShiftAllDates_includingReviewersAndObrObservations() throws Exception {
        shift = 2;
        embedded("Report");
        for (String parent :
                List.of(
                        "EventDateTime[1]",
                        "ReceivedDateTime[1]",
                        "SentDateTime[1]",
                        "ReportReviewed[1]/DateTimeReportReviewed[1]",
                        "OBRContent[1]/ObservationDateTime[1]"))
            date(parent, "FullDate", "2020-02-28");
        field("ReportReviewed[1]/Name[1]/FirstName[1]", "A");
        field("ReportReviewed[1]/Name[1]/LastName[1]", "B");
        var result = read();
        assertThat(result.changes()).hasSize(5);
        assertThat(result.dates().values())
                .allSatisfy(value -> assertThat(value.lexical()).isEqualTo("2020-03-01"));
    }

    @Test
    void shouldBindStructuredAuthor_withoutSubstitutingImportingProvider() throws Exception {
        embedded("Report");
        scalar("SourceAuthorPhysician/AuthorName/FirstName", "First");
        scalar("SourceAuthorPhysician/AuthorName/LastName", "Last");
        metadata.put("SourceAuthorFormat", "structured");
        metadata.put("NativeSourceAuthor", "First Last");
        assertThat(read().sourceFields()).hasSize(5);
    }

    @Test
    void shouldRefuseChangedNativePreparationText_forReportRetainedSourceContract() {
        embedded("Original");
        texts.put(fields.getLast().id(), "Changed");
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_REPORT_SOURCE_PREPARATION_MISMATCH");
    }

    @Test
    void shouldRefuseDuplicateRepresentationNames_forReportRetainedSourceContract() {
        embedded("Report");
        variants.add(variants.getFirst());
        assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseMissingRepresentation_orUnboundReferencedFile() {
        embedded("Report");
        variants.add(variant("referenced", new byte[] {1}));
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_REPORT_SOURCE_PREPARATION_MISMATCH");
    }

    @Test
    void shouldRefuseStalledInput_insteadOfHashingPartialSuccess() throws Exception {
        embedded("Report");
        when(values.text(fields.getLast()))
                .thenReturn(
                        new Reader() {
                            public int read(char[] b, int o, int l) {
                                return 0;
                            }

                            public void close() {}
                        });
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_REPORT_SOURCE_INVALID");
    }

    @Test
    void shouldRefuseOversizedContent_beforeReturningEvidence() {
        embedded("Report");
        assertThatThrownBy(
                        () ->
                                new CdsExportReportRetainedSource()
                                        .readVerified(
                                                fields, options(), prepared(), values, 2, () -> {}))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldPropagateAuthenticatedTailFailure_forReportRetainedSourceContract() throws Exception {
        embedded("Report");
        when(values.text(fields.getLast()))
                .thenReturn(
                        new Reader() {
                            public int read(char[] b, int o, int l) throws IOException {
                                throw new IOException("AUTHENTICATION_FAILED");
                            }

                            public void close() {}
                        });
        assertThatThrownBy(this::read).hasMessage("AUTHENTICATION_FAILED");
    }

    @Test
    void shouldRequireLiveScope_beforeOpeningAnyContent() throws Exception {
        embedded("Report");
        doThrow(new IOException("EXPIRED")).when(values).requireActive();
        assertThatThrownBy(this::read).hasMessage("EXPIRED");
        verify(values, never()).text(any());
    }

    private void embedded(String text) {
        field("Content/TextContent", text);
        variants.add(variant("embedded", text.getBytes(StandardCharsets.UTF_8)));
    }

    private void scalar(String path, String value) {
        field(path, value);
        metadata.put(path, value);
    }

    private void replaceFormat(String format) {
        metadata.put("Format", format);
        var f = fields.getFirst();
        texts.put(f.id(), format);
    }

    private void date(String path, String choice, String lexical) {
        field(path + "/" + choice + "[1]", lexical);
        var date =
                CdsTemporal.shift(
                        CdsTemporal.parse(
                                choice.equals("YearOnly") ? lexical : null,
                                choice.equals("YearMonth") ? lexical : null,
                                choice.equals("FullDate") ? lexical : null,
                                choice.equals("FullDateTime") ? lexical : null,
                                path),
                        shift,
                        path);
        dates.put(path, date);
    }

    private void field(String path, String lexical) {
        add(path, lexical, null);
    }

    private void binary(String path, byte[] bytes) {
        add(path, null, bytes);
    }

    private void add(String path, String lexical, byte[] bytes) {
        String indexed =
                Arrays.stream(path.split("/"))
                        .map(part -> part.contains("[") ? part : part + "[1]")
                        .collect(java.util.stream.Collectors.joining("/"));
        var type =
                CdsWireSchema.schemaModel()
                        .element("cds", "OmdCds")
                        .child("cds", "PatientRecord")
                        .child("cds", "Reports");
        String namespace = "cds";
        for (String segment : indexed.split("/")) {
            String name = segment.substring(0, segment.indexOf('['));
            var child =
                    type.childNames().stream()
                            .filter(candidate -> candidate.local().equals(name))
                            .findFirst()
                            .orElseThrow();
            namespace = child.namespace();
            type = type.child(namespace, name);
        }
        String id = CdsImportKeys.hash(indexed, Integer.toString(fields.size()));
        byte[] body = bytes != null ? bytes : lexical.getBytes(StandardCharsets.UTF_8);
        var payload =
                bytes != null || lexical.length() > 32768
                        ? new CdsExportRetainedFieldDto.Payload(
                                "/private/captured-report", sha(body), 0, body.length)
                        : null;
        fields.add(
                new CdsExportRetainedFieldDto(
                        id,
                        RUN,
                        HASH,
                        HASH,
                        CdsCategory.REPORTS,
                        "/OmdCds[1]/PatientRecord[1]/Reports[1]/" + indexed,
                        1,
                        namespace,
                        "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                        null,
                        payload == null ? lexical : null,
                        payload == null ? lexical : null,
                        payload));
        if (bytes == null) texts.put(id, lexical);
        else binaries.put(id, bytes);
    }

    private CdsReportNativeDto prepared() {
        return new CdsReportNativeDto(
                metadata,
                variants,
                dates.getOrDefault("EventDateTime[1]", CdsTemporalDto.unknown()),
                dates.getOrDefault("ReceivedDateTime[1]", CdsTemporalDto.unknown()),
                dates.getOrDefault("SentDateTime[1]", CdsTemporalDto.unknown()));
    }

    private CdsExportRetainedImportOptionsReader.Options options() {
        return new CdsExportRetainedImportOptionsReader.Options(
                RUN, HASH, HASH, HASH, "omd-5.2", shift != 0, shift);
    }

    private CdsExportReportRetainedSource.Result read() throws Exception {
        return new CdsExportReportRetainedSource()
                .readVerified(fields, options(), prepared(), values, 4L << 20, () -> {});
    }

    private static CdsReportNativeDto.Variant variant(String name, byte[] body) {
        return new CdsReportNativeDto.Variant(
                name, "txt", "text/plain;charset=UTF-8", sha(body), body.length, false, List.of());
    }

    private static String sha(byte[] bytes) {
        return HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes));
    }
}
