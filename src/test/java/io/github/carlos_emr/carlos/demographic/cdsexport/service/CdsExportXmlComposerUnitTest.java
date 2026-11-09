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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDispositionDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportXmlComposerUnitTest {
    private static final String PATIENT = "a".repeat(64),
            CAPTURE = "b".repeat(64),
            VERSION = "2026.08.0-test";
    private static final CdsLogicalProfile PROFILE = CdsLogicalProfile.ONTARIO_MD_5_2;
    private final CdsExportXmlComposer composer = new CdsExportXmlComposer();

    @Test
    void shouldProducePinnedValidXmlAndActualByteReceipts_forOnePatient() throws Exception {
        var output = new ByteArrayOutputStream();
        var receipts = new ArrayList<CdsExportDispositionDto>();
        var result =
                compose(
                        output,
                        List.of(section(CdsCategory.DEMOGRAPHICS, this::demographics)),
                        receipts);
        byte[] bytes = output.toByteArray();
        var validation =
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(bytes),
                                "C27",
                                Set.of(CdsCategory.DEMOGRAPHICS),
                                VERSION,
                                PROFILE,
                                CdsExportXmlLimitsDto.defaults(),
                                ignored -> {},
                                () -> {});
        assertThat(validation.senderPatientId()).isEqualTo("C27");
        assertThat(result.xmlBytes()).isEqualTo(bytes.length);
        assertThat(receipts).hasSize(1);
        String xml = output.toString(StandardCharsets.UTF_8);
        String actual =
                xml.substring(
                        xml.indexOf("<Demographics>"),
                        xml.indexOf("</Demographics>") + "</Demographics>".length());
        assertThat(receipts.getFirst().outputDigest())
                .isEqualTo(
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(actual.getBytes(StandardCharsets.UTF_8))));
        assertThat(receipts.getFirst().captureDigest()).isEqualTo(CAPTURE);
    }

    @Test
    void shouldPreserveCarriageReturnsAndSupplementaryCharacters_acrossReaderChunkBoundaries()
            throws Exception {
        String note = "Literal & < > café 😀\r\nSecond\rThird\tFourth";
        Reader singleChars =
                new StringReader(note) {
                    @Override
                    public int read(char[] chars, int offset, int length) throws IOException {
                        return super.read(chars, offset, Math.min(length, 1));
                    }
                };
        var output = new ByteArrayOutputStream();
        compose(
                output,
                List.of(
                        section(
                                CdsCategory.DEMOGRAPHICS,
                                writer -> {
                                    demographicIdentity(writer);
                                    writer.start("cds", "NoteAboutPatient");
                                    writer.text(singleChars);
                                    writer.end();
                                    personStatus(writer);
                                })),
                new ArrayList<>());
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var parsed =
                factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));
        assertThat(
                        parsed.getElementsByTagNameNS("cds", "NoteAboutPatient")
                                .item(0)
                                .getTextContent())
                .isEqualTo(note);
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "C27",
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        VERSION,
                        PROFILE,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
    }

    @Test
    void shouldStreamBinaryReports_withExactBase64AndRequiredMetadata() throws Exception {
        byte[] bytes = new byte[100000];
        new java.util.Random(27).nextBytes(bytes);
        var report =
                section(
                        CdsCategory.REPORTS,
                        writer -> {
                            writer.element("cds", "Format", "Binary");
                            writer.element("cds", "FileExtensionAndVersion", "PNG");
                            writer.start("cds", "Content");
                            writer.start("cds_dt", "Media");
                            writer.binary(new ByteArrayInputStream(bytes));
                            writer.end();
                            writer.end();
                            writer.element("cds", "Class", "Diagnostic Imaging Report");
                        });
        var output = new ByteArrayOutputStream();
        compose(
                output,
                List.of(section(CdsCategory.DEMOGRAPHICS, this::demographics), report),
                new ArrayList<>());
        var validation =
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(output.toByteArray()),
                                "C27",
                                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS),
                                VERSION,
                                PROFILE,
                                CdsExportXmlLimitsDto.defaults(),
                                ignored -> {},
                                () -> {});
        assertThat(validation.binaryReports()).isEqualTo(1);
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains(java.util.Base64.getEncoder().encodeToString(bytes));
    }

    @Test
    void shouldRefuseSectionBoundaryEscapes_withoutProducingAnEmissionReceipt() {
        var receipts = new ArrayList<CdsExportDispositionDto>();
        assertThatThrownBy(
                        () ->
                                compose(
                                        new ByteArrayOutputStream(),
                                        List.of(
                                                section(
                                                        CdsCategory.DEMOGRAPHICS,
                                                        CdsExportXmlComposer.ContentWriter::end)),
                                        receipts))
                .hasMessage("CDS_EXPORT_SECTION_BOUNDARY_REFUSED");
        assertThat(receipts).isEmpty();
    }

    @Test
    void shouldRefuseUnclosedElements_andInvalidateTheScopedWriterAfterReturn() throws Exception {
        assertThatThrownBy(
                        () ->
                                compose(
                                        new ByteArrayOutputStream(),
                                        List.of(
                                                section(
                                                        CdsCategory.DEMOGRAPHICS,
                                                        writer -> writer.start("cds", "Names"))),
                                        new ArrayList<>()))
                .hasMessage("CDS_EXPORT_SECTION_UNCLOSED");
        var retained = new CdsExportXmlComposer.ContentWriter[1];
        var output = new ByteArrayOutputStream();
        compose(
                output,
                List.of(
                        section(
                                CdsCategory.DEMOGRAPHICS,
                                writer -> {
                                    demographics(writer);
                                    retained[0] = writer;
                                })),
                new ArrayList<>());
        int length = output.size();
        assertThatThrownBy(() -> retained[0].start("cds", "Names"))
                .hasMessage("CDS_EXPORT_SECTION_WRITER_CLOSED");
        assertThat(output.size()).isEqualTo(length);
    }

    @Test
    void shouldRefuseInvalidXmlCharacters_insteadOfReplacingOrDroppingThem() {
        for (String value :
                List.of("bad\u0000text", "bad\u000ctext", "bad\ud800text", "bad\udffftext")) {
            assertThatThrownBy(
                            () ->
                                    compose(
                                            new ByteArrayOutputStream(),
                                            List.of(
                                                    section(
                                                            CdsCategory.DEMOGRAPHICS,
                                                            writer -> {
                                                                demographics(writer);
                                                                writer.element(
                                                                        "cds",
                                                                        "NoteAboutPatient",
                                                                        value);
                                                            })),
                                            new ArrayList<>()))
                    .hasMessage("CDS_EXPORT_XML_CHARACTER_INVALID");
        }
    }

    @Test
    void shouldPropagateMapperFailure_withoutReturningACompleteDocument() {
        var receipts = new ArrayList<CdsExportDispositionDto>();
        assertThatThrownBy(
                        () ->
                                compose(
                                        new ByteArrayOutputStream(),
                                        List.of(
                                                section(
                                                        CdsCategory.DEMOGRAPHICS,
                                                        writer -> {
                                                            demographics(writer);
                                                            throw new IOException(
                                                                    "SYNTHETIC_CAPTURE_FAILURE");
                                                        })),
                                        receipts))
                .hasMessage("SYNTHETIC_CAPTURE_FAILURE");
        assertThat(receipts).isEmpty();
    }

    @Test
    void shouldNeverEmitAReceipt_whenAMapperSwallowsABoundaryEncodingOrSourceError() {
        for (int fault = 0; fault < 4; fault++) {
            int mode = fault;
            var receipts = new ArrayList<CdsExportDispositionDto>();
            assertThatThrownBy(
                            () ->
                                    compose(
                                            new ByteArrayOutputStream(),
                                            List.of(
                                                    section(
                                                            CdsCategory.DEMOGRAPHICS,
                                                            writer -> {
                                                                demographics(writer);
                                                                try {
                                                                    if (mode == 0) writer.end();
                                                                    if (mode == 1)
                                                                        writer.element(
                                                                                "unrecognized",
                                                                                "NoteAboutPatient",
                                                                                "hidden");
                                                                    if (mode == 2)
                                                                        writer.element(
                                                                                "cds",
                                                                                "NoteAboutPatient",
                                                                                "partial\u0000hidden");
                                                                    if (mode == 3) {
                                                                        writer.start(
                                                                                "cds",
                                                                                "NoteAboutPatient");
                                                                        writer.text(
                                                                                new Reader() {
                                                                                    @Override
                                                                                    public int read(
                                                                                            char[]
                                                                                                    chars,
                                                                                            int
                                                                                                    offset,
                                                                                            int
                                                                                                    count)
                                                                                            throws
                                                                                                    IOException {
                                                                                        throw new IOException(
                                                                                                "SOURCE_FAILED");
                                                                                    }

                                                                                    @Override
                                                                                    public void
                                                                                            close() {}
                                                                                });
                                                                    }
                                                                } catch (IOException swallowed) {
                                                                    /* deliberately broken mapper */
                                                                }
                                                                try {
                                                                    writer.end();
                                                                } catch (IOException swallowed) {
                                                                    /* cannot repair a failed writer */
                                                                }
                                                            })),
                                            receipts))
                    .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
            assertThat(receipts).isEmpty();
        }
    }

    @Test
    void shouldRefuseForeignThreadUse_withoutReturningAnEmissionReceipt() {
        var receipts = new ArrayList<CdsExportDispositionDto>();
        assertThatThrownBy(
                        () ->
                                compose(
                                        new ByteArrayOutputStream(),
                                        List.of(
                                                section(
                                                        CdsCategory.DEMOGRAPHICS,
                                                        writer -> {
                                                            demographics(writer);
                                                            var failure =
                                                                    new java.util.concurrent.atomic
                                                                                    .AtomicReference<
                                                                            Throwable>();
                                                            Thread thread =
                                                                    new Thread(
                                                                            () -> {
                                                                                try {
                                                                                    writer.start(
                                                                                            "cds",
                                                                                            "NoteAboutPatient");
                                                                                } catch (
                                                                                        Throwable
                                                                                                rejected) {
                                                                                    failure.set(
                                                                                            rejected);
                                                                                }
                                                                            });
                                                            thread.start();
                                                            try {
                                                                thread.join();
                                                            } catch (
                                                                    InterruptedException
                                                                            interrupted) {
                                                                throw new IOException(interrupted);
                                                            }
                                                            assertThat(failure.get())
                                                                    .hasMessage(
                                                                            "CDS_EXPORT_SECTION_WRITER_THREAD_REFUSED");
                                                        })),
                                        receipts))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(receipts).isEmpty();
    }

    @Test
    void shouldInvalidateTheWholeDocument_whenALaterMapperSwallowsAnExpiredWriterError() {
        var prior = new CdsExportXmlComposer.ContentWriter[1];
        var receipts = new ArrayList<CdsExportDispositionDto>();
        assertThatThrownBy(
                        () ->
                                compose(
                                        new ByteArrayOutputStream(),
                                        List.of(
                                                section(
                                                        CdsCategory.DEMOGRAPHICS,
                                                        writer -> {
                                                            demographics(writer);
                                                            prior[0] = writer;
                                                        }),
                                                section(
                                                        CdsCategory.REPORTS,
                                                        writer -> {
                                                            try {
                                                                prior[0].start(
                                                                        "cds", "NoteAboutPatient");
                                                            } catch (IOException swallowed) {
                                                                /* deliberately wrong scope */
                                                            }
                                                            writer.element("cds", "Format", "Text");
                                                        })),
                                        receipts))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(receipts).hasSize(1);
    }

    @Test
    void shouldRefuseStalledTextAndBinarySources_withoutSpinningOrTruncating() {
        for (boolean binary : List.of(false, true)) {
            assertThatThrownBy(
                            () ->
                                    compose(
                                            new ByteArrayOutputStream(),
                                            List.of(
                                                    section(
                                                            CdsCategory.DEMOGRAPHICS,
                                                            writer -> {
                                                                demographics(writer);
                                                                writer.start(
                                                                        "cds", "NoteAboutPatient");
                                                                if (binary)
                                                                    writer.binary(
                                                                            new java.io
                                                                                    .InputStream() {
                                                                                @Override
                                                                                public int read() {
                                                                                    return 0;
                                                                                }

                                                                                @Override
                                                                                public int read(
                                                                                        byte[]
                                                                                                bytes,
                                                                                        int offset,
                                                                                        int count) {
                                                                                    return 0;
                                                                                }
                                                                            });
                                                                else
                                                                    writer.text(
                                                                            new Reader() {
                                                                                @Override
                                                                                public int read(
                                                                                        char[]
                                                                                                chars,
                                                                                        int offset,
                                                                                        int count) {
                                                                                    return 0;
                                                                                }

                                                                                @Override
                                                                                public void
                                                                                        close() {}
                                                                            });
                                                            })),
                                            new ArrayList<>()))
                    .hasMessage("CDS_EXPORT_XML_SOURCE_STALLED");
        }
    }

    @Test
    void shouldSerializePushedRowsSynchronously_withBoundedOccurrenceStateAndActualReceipts()
            throws Exception {
        var output = new ByteArrayOutputStream();
        int[] receipts = {0};
        var result =
                composer.composeFromSource(
                        output,
                        CAPTURE,
                        PATIENT,
                        VERSION,
                        PROFILE,
                        Set.of(
                                CdsCategory.DEMOGRAPHICS,
                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                        emitter -> {
                            emitter.emit(section(CdsCategory.DEMOGRAPHICS, this::demographics));
                            for (int i = 0; i < 2048; i++) {
                                assertThat(receipts[0]).isEqualTo(i + 1);
                                String key = String.format("%064x", i + 1);
                                emitter.emit(
                                        new CdsExportXmlComposer.Section(
                                                new CdsExportIdentityDto(
                                                        PATIENT,
                                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                                        key),
                                                writer -> {
                                                    writer.element(
                                                            "cds",
                                                            "OffendingAgentDescription",
                                                            "Pollen");
                                                    writer.element(
                                                            "cds",
                                                            "PropertyOfOffendingAgent",
                                                            "ND");
                                                }));
                            }
                        },
                        disposition -> {
                            assertThat(disposition.outputDigest()).matches("[0-9a-f]{64}");
                            receipts[0]++;
                        },
                        10L << 20,
                        () -> {});
        assertThat(receipts[0]).isEqualTo(2049);
        assertThat(result.occurrences().get(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS))
                .isEqualTo(2048);
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "C27",
                        Set.of(
                                CdsCategory.DEMOGRAPHICS,
                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                        VERSION,
                        PROFILE,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
    }

    @Test
    void shouldRefuseSwallowedPushFailure_evenWhenTheSourceThenReturnsNormally() {
        for (int fault = 0; fault < 4; fault++) {
            int mode = fault;
            var receipts = new ArrayList<CdsExportDispositionDto>();
            assertThatThrownBy(
                            () ->
                                    composer.composeFromSource(
                                            new ByteArrayOutputStream(),
                                            CAPTURE,
                                            PATIENT,
                                            VERSION,
                                            PROFILE,
                                            Set.of(CdsCategory.DEMOGRAPHICS),
                                            emitter -> {
                                                emitter.emit(
                                                        section(
                                                                CdsCategory.DEMOGRAPHICS,
                                                                this::demographics));
                                                try {
                                                    if (mode == 0)
                                                        emitter.emit(
                                                                section(
                                                                        CdsCategory.DEMOGRAPHICS,
                                                                        this::demographics));
                                                    if (mode == 1) emitter.emit(null);
                                                    if (mode == 2)
                                                        emitter.emit(
                                                                section(
                                                                        CdsCategory.REPORTS,
                                                                        writer -> {}));
                                                    if (mode == 3) {
                                                        Thread foreign =
                                                                new Thread(
                                                                        () -> {
                                                                            try {
                                                                                emitter.emit(
                                                                                        section(
                                                                                                CdsCategory
                                                                                                        .DEMOGRAPHICS,
                                                                                                this
                                                                                                        ::demographics));
                                                                            } catch (
                                                                                    IOException
                                                                                            refused) {
                                                                                assertThat(refused)
                                                                                        .hasMessage(
                                                                                                "CDS_EXPORT_SECTION_EMITTER_THREAD_REFUSED");
                                                                            }
                                                                        });
                                                        foreign.start();
                                                        try {
                                                            foreign.join();
                                                        } catch (InterruptedException interrupted) {
                                                            throw new IOException(interrupted);
                                                        }
                                                    }
                                                } catch (IOException
                                                        | NullPointerException swallowed) {
                                                    /* broken source must not turn failure into success */
                                                }
                                            },
                                            receipts::add,
                                            10L << 20,
                                            () -> {}))
                    .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
            assertThat(receipts).hasSize(1);
        }
    }

    @Test
    void shouldRefuseRecursiveEmission_andExpireTheProducerAfterReturning() throws Exception {
        var receipts = new ArrayList<CdsExportDispositionDto>();
        assertThatThrownBy(
                        () ->
                                composer.composeFromSource(
                                        new ByteArrayOutputStream(),
                                        CAPTURE,
                                        PATIENT,
                                        VERSION,
                                        PROFILE,
                                        Set.of(CdsCategory.DEMOGRAPHICS),
                                        emitter ->
                                                emitter.emit(
                                                        section(
                                                                CdsCategory.DEMOGRAPHICS,
                                                                writer -> {
                                                                    demographics(writer);
                                                                    try {
                                                                        emitter.emit(
                                                                                section(
                                                                                        CdsCategory
                                                                                                .DEMOGRAPHICS,
                                                                                        this
                                                                                                ::demographics));
                                                                    } catch (IOException refused) {
                                                                        assertThat(refused)
                                                                                .hasMessage(
                                                                                        "CDS_EXPORT_SECTION_EMITTER_REENTRANT");
                                                                    }
                                                                })),
                                        receipts::add,
                                        10L << 20,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(receipts).isEmpty();
        var saved = new CdsExportXmlComposer.SectionEmitter[1];
        var output = new ByteArrayOutputStream();
        composer.composeFromSource(
                output,
                CAPTURE,
                PATIENT,
                VERSION,
                PROFILE,
                Set.of(CdsCategory.DEMOGRAPHICS),
                emitter -> {
                    saved[0] = emitter;
                    emitter.emit(section(CdsCategory.DEMOGRAPHICS, this::demographics));
                },
                receipts::add,
                10L << 20,
                () -> {});
        int size = output.size();
        assertThatThrownBy(
                        () -> saved[0].emit(section(CdsCategory.DEMOGRAPHICS, this::demographics)))
                .hasMessage("CDS_EXPORT_SECTION_EMITTER_CLOSED");
        assertThat(output.size()).isEqualTo(size);
    }

    @Test
    void shouldPropagateTheFinalCapturedReadFailure_afterAllRowsWerePushed() {
        var output = new ByteArrayOutputStream();
        var receipts = new ArrayList<CdsExportDispositionDto>();
        assertThatThrownBy(
                        () ->
                                composer.composeFromSource(
                                        output,
                                        CAPTURE,
                                        PATIENT,
                                        VERSION,
                                        PROFILE,
                                        Set.of(CdsCategory.DEMOGRAPHICS),
                                        emitter -> {
                                            emitter.emit(
                                                    section(
                                                            CdsCategory.DEMOGRAPHICS,
                                                            this::demographics));
                                            throw new IOException(
                                                    "CAPTURE_AUTHENTICATED_EOF_FAILED");
                                        },
                                        receipts::add,
                                        10L << 20,
                                        () -> {}))
                .hasMessage("CAPTURE_AUTHENTICATED_EOF_FAILED");
        assertThat(receipts)
                .hasSize(1); // private occurrence receipts are not a complete-document receipt
        assertThat(output.toString(StandardCharsets.UTF_8)).doesNotContain("</PatientRecord>");
    }

    private CdsExportXmlComposer.Result compose(
            ByteArrayOutputStream output,
            List<CdsExportXmlComposer.Section> sections,
            List<CdsExportDispositionDto> dispositions)
            throws IOException {
        return composer.compose(
                output,
                CAPTURE,
                PATIENT,
                VERSION,
                PROFILE,
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS),
                sections.iterator(),
                dispositions::add,
                10L << 20,
                () -> {});
    }

    private CdsExportXmlComposer.Section section(
            CdsCategory category, CdsExportXmlComposer.Content content) {
        return new CdsExportXmlComposer.Section(
                new CdsExportIdentityDto(
                        PATIENT,
                        category,
                        category == CdsCategory.DEMOGRAPHICS ? "c".repeat(64) : "d".repeat(64)),
                content);
    }

    private void demographics(CdsExportXmlComposer.ContentWriter writer) throws IOException {
        demographicIdentity(writer);
        personStatus(writer);
    }

    private void demographicIdentity(CdsExportXmlComposer.ContentWriter writer) throws IOException {
        writer.start("cds", "Names");
        writer.start("cds_dt", "LegalName");
        writer.attribute("", "namePurpose", "L");
        writer.start("cds_dt", "FirstName");
        writer.element("cds_dt", "Part", "Export");
        writer.element("cds_dt", "PartType", "GIV");
        writer.end();
        writer.start("cds_dt", "LastName");
        writer.element("cds_dt", "Part", "Fixture");
        writer.element("cds_dt", "PartType", "FAMC");
        writer.end();
        writer.end();
        writer.end();
        writer.element("cds", "DateOfBirth", "1971-03-04");
        writer.element("cds", "Gender", "F");
        writer.element("cds", "UniqueVendorIdSequence", "C27");
    }

    private void personStatus(CdsExportXmlComposer.ContentWriter writer) throws IOException {
        writer.start("cds", "PersonStatusCode");
        writer.element("cds", "PersonStatusAsEnum", "A");
        writer.end();
    }
}
