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
package io.github.carlos_emr.carlos.demographic.cdsexport.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReportReferenceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlValidationDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportHeaderComposer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportXmlValidatorUnitTest {
    private static final String ID = "E-CDS-CORPUS-202608";
    private static final String VERSION = "2026.08.0-beta7-SNAPSHOT";
    private static final CdsLogicalProfile PROFILE = CdsLogicalProfile.ONTARIO_MD_5_2;
    private final CdsExportXmlValidator validator = new CdsExportXmlValidator();

    @Test
    void shouldValidatePinnedWireAndCountActualSections_forACompleteSyntheticPatient()
            throws Exception {
        byte[] bytes = fixture().getBytes(StandardCharsets.UTF_8);
        var result =
                validate(
                        bytes,
                        EnumSet.allOf(CdsCategory.class),
                        CdsExportXmlLimitsDto.defaults(),
                        new ArrayList<>());
        assertThat(result.occurrences())
                .hasSize(16)
                .containsEntry(CdsCategory.DEMOGRAPHICS, 1L)
                .containsEntry(CdsCategory.REPORTS, 2L);
        assertThat(result.senderPatientId()).isEqualTo(ID);
        assertThat(result.textReports()).isEqualTo(1);
        assertThat(result.binaryReports()).isEqualTo(1);
        assertThat(result.externalReferences()).isZero();
        assertThat(result.xmlBytes()).isEqualTo(bytes.length);
        assertThat(result.xmlSha256())
                .isEqualTo(
                        HexFormat.of()
                                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    @Test
    void shouldRejectSchemaValidReport_withoutTheRequiredBinaryExtension() throws Exception {
        String sourceXml = fixture();
        assertThatThrownBy(
                        () ->
                                validate(
                                        sourceXml.replace(
                                                "<FileExtensionAndVersion>PNG</FileExtensionAndVersion>",
                                                "")))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_REPORT_BINARY_EXTENSION_REQUIRED");
    }

    @Test
    void shouldRejectBinaryRepresentation_withMismatchedTextFormat() throws Exception {
        String sourceXml = fixture();
        assertThatThrownBy(
                        () ->
                                validate(
                                        sourceXml.replace(
                                                "<Format>Binary</Format>",
                                                "<Format>Text</Format>")))
                .hasMessage("CDS_EXPORT_REPORT_FORMAT_MISMATCH");
    }

    @Test
    void shouldIndexExactRelativeReferences_forExternalBinaryReports() throws Exception {
        String xml =
                fixture()
                        .replaceAll("<cdsd:Media>[^<]+</cdsd:Media>", "")
                        .replace(
                                "<FileExtensionAndVersion>PNG</FileExtensionAndVersion>",
                                "<FileExtensionAndVersion>PNG</FileExtensionAndVersion><FilePath>Reports/scan.png</FilePath>")
                        .replaceAll("<Content>\\s*</Content>", "");
        List<CdsExportReportReferenceDto> references = new ArrayList<>();
        var result =
                validate(
                        xml.getBytes(StandardCharsets.UTF_8),
                        EnumSet.allOf(CdsCategory.class),
                        CdsExportXmlLimitsDto.defaults(),
                        references);
        assertThat(result.externalReferences()).isEqualTo(1);
        assertThat(references)
                .containsExactly(new CdsExportReportReferenceDto(2, "Reports/scan.png", "PNG"));
    }

    @Test
    void shouldRejectUnsafeReferences_withoutNormalizingThemToAnotherFile() throws Exception {
        for (String path :
                List.of(
                        "../scan.png",
                        "/scan.png",
                        "https://host/a",
                        "a\\b.png",
                        "a//b",
                        "a/./b",
                        "a./b")) {
            assertThatThrownBy(() -> CdsExportXmlValidator.relativeReference(path))
                    .hasMessage("CDS_EXPORT_REPORT_REFERENCE_INVALID");
        }
    }

    @Test
    void shouldRejectNoncanonicalBinaryPadBits_evenWhenTheJdkWouldDecodeThem() throws Exception {
        String sourceXml = fixture();
        assertThatThrownBy(
                        () ->
                                validate(
                                        sourceXml.replaceAll(
                                                "<cdsd:Media>[^<]+</cdsd:Media>",
                                                "<cdsd:Media>AB==</cdsd:Media>")))
                .hasMessage("CDS_BASE64_PAD_BITS_INVALID");
    }

    @Test
    void shouldRejectMissingOrWrongSchemaHeader_beforeAcceptingThePatientRecord() throws Exception {
        String xml = fixture();
        assertThatThrownBy(() -> validate(xml.replace(header(), "")))
                .hasMessage("CDS_EXPORT_XML_ENVELOPE_INVALID");
        assertThatThrownBy(() -> validate(xml.replace("version 1.0", "version 5.2")))
                .hasMessage("CDS_EXPORT_XML_ENVELOPE_INVALID");
    }

    @Test
    void shouldRejectWrongPatientIdentity_insteadOfCountingAnotherPatientsFile() throws Exception {
        String sourceXml = fixture();
        assertThatThrownBy(() -> validate(sourceXml.replace(ID, "C99")))
                .hasMessage("CDS_EXPORT_XML_PATIENT_ID_MISMATCH");
    }

    @Test
    void shouldRejectUnselectedCategories_evenWhenTheSchemaAllowsThem() throws Exception {
        byte[] bytes = fixture().getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(
                        () ->
                                validate(
                                        bytes,
                                        Set.of(CdsCategory.DEMOGRAPHICS),
                                        CdsExportXmlLimitsDto.defaults(),
                                        new ArrayList<>()))
                .hasMessage("CDS_EXPORT_XML_UNSELECTED_CATEGORY");
    }

    @Test
    void shouldRejectInvalidSchemaValues_withoutReturningPatientContentInTheError()
            throws Exception {
        String sourceXml = fixture();
        assertThatThrownBy(
                        () ->
                                validate(
                                        sourceXml.replace(
                                                "<Gender>F</Gender>",
                                                "<Gender>SECRET-INVALID</Gender>")))
                .hasMessage("CDS_EXPORT_XML_SCHEMA_INVALID")
                .hasNoCause();
    }

    @Test
    void shouldRejectDtdAndExternalEntityInput_beforeAnyResolution() throws Exception {
        String sourceXml = fixture();
        assertThatThrownBy(
                        () ->
                                validate(
                                        sourceXml.replace(
                                                "<OmdCds ",
                                                "<!DOCTYPE OmdCds [<!ENTITY secret SYSTEM 'file:///nonexistent-cds-export-synthetic'>]><OmdCds ")))
                .hasMessage("CDS_EXPORT_XML_SCHEMA_INVALID");
    }

    @Test
    void shouldRequireUtf8Declaration_andRejectOtherEncodings() throws Exception {
        String sourceXml = fixture();
        assertThatThrownBy(
                        () ->
                                validate(
                                        sourceXml.replace(
                                                "encoding=\"UTF-8\"", "encoding=\"UTF-16\"")))
                .hasMessage("CDS_EXPORT_XML_UTF8_DECLARATION_REQUIRED");
        assertThatThrownBy(
                        () ->
                                validate(
                                        sourceXml.getBytes(StandardCharsets.UTF_16),
                                        EnumSet.allOf(CdsCategory.class),
                                        CdsExportXmlLimitsDto.defaults(),
                                        new ArrayList<>()))
                .hasMessage("CDS_EXPORT_XML_UTF8_DECLARATION_REQUIRED");
    }

    @Test
    void shouldRejectMalformedUtf8_withoutReplacingClinicalCharacters() throws Exception {
        byte[] bytes = fixture().getBytes(StandardCharsets.UTF_8);
        bytes[bytes.length - 30] = (byte) 255;
        assertThatThrownBy(
                        () ->
                                validate(
                                        bytes,
                                        EnumSet.allOf(CdsCategory.class),
                                        CdsExportXmlLimitsDto.defaults(),
                                        new ArrayList<>()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldValidateLargeUnboundedText_withoutApplyingAClinicalTruncationLimit()
            throws Exception {
        String xml = fixture().replace("Synthetic source report", "x".repeat(4 << 20));
        assertThat(validate(xml).textReports()).isEqualTo(1);
    }

    @Test
    void shouldRefuseTheAmbiguousDictionaryLengthRange_withoutTruncatingClinicalNotes()
            throws Exception {
        String xml =
                fixture()
                        .replaceAll(
                                "<NoteAboutPatient>[^<]+</NoteAboutPatient>",
                                "<NoteAboutPatient>" + "x".repeat(64001) + "</NoteAboutPatient>");
        assertThatThrownBy(() -> validate(xml))
                .hasMessage("CDS_EXPORT_DICTIONARY_LENGTH_REQUIRES_REVIEW");
        assertThat(validate(xml.replace("x".repeat(64001), "x".repeat(64000))).senderPatientId())
                .isEqualTo(ID);
    }

    @Test
    void shouldRefuseByteElementDepthAndBinaryLimits_withoutReturningPartialSuccess()
            throws Exception {
        byte[] bytes = fixture().getBytes(StandardCharsets.UTF_8);
        List<CdsExportXmlLimitsDto> limits =
                List.of(
                        new CdsExportXmlLimitsDto(100, 1000, 1000, 50, 1000, 10000, 10000),
                        new CdsExportXmlLimitsDto(100000, 1000, 3, 50, 1000, 10000, 10000),
                        new CdsExportXmlLimitsDto(100000, 1000, 1000, 3, 1000, 10000, 10000),
                        new CdsExportXmlLimitsDto(100000, 1, 1000, 50, 1000, 10000, 10000));
        for (var limit : limits) {
            assertThatThrownBy(
                            () ->
                                    validate(
                                            bytes,
                                            EnumSet.allOf(CdsCategory.class),
                                            limit,
                                            new ArrayList<>()))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldPropagateCancellation_andLeaveInputOwnershipWithTheCaller() throws Exception {
        class OwnedInput extends ByteArrayInputStream {
            boolean closed;

            OwnedInput(byte[] bytes) {
                super(bytes);
            }

            @Override
            public void close() {
                closed = true;
            }
        }
        var input = new OwnedInput(fixture().getBytes(StandardCharsets.UTF_8));
        validator.validate(
                input,
                ID,
                EnumSet.allOf(CdsCategory.class),
                VERSION,
                PROFILE,
                CdsExportXmlLimitsDto.defaults(),
                ignored -> {},
                () -> {});
        assertThat(input.closed).isFalse();
        var cancelled = new OwnedInput(fixture().getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        cancelled,
                                        ID,
                                        EnumSet.allOf(CdsCategory.class),
                                        VERSION,
                                        PROFILE,
                                        CdsExportXmlLimitsDto.defaults(),
                                        ignored -> {},
                                        () -> {
                                            throw new IllegalStateException("CANCELLED");
                                        }))
                .hasMessage("CANCELLED");
        assertThat(cancelled.closed).isFalse();
    }

    @Test
    void shouldBindTheActualLegalNameAndCalendarBirthDate_toTheExactPatientPath() throws Exception {
        var result = validate(fixture());
        var file =
                fileIdentity(
                        "Import", "FAKE-CDS-CORPUS-202608", java.time.LocalDate.of(1971, 3, 4));
        var path =
                new io.github.carlos_emr.carlos.demographic.cdsexport.service
                                .CdsExportPathComposer()
                        .compose(file);
        validator.verifyFileBinding(
                result, file, path.physicianFolder() + "/" + path.patientXmlFile());
        assertThat(result.legalFirstName()).isEqualTo("Import");
        assertThat(result.legalLastName()).isEqualTo("FAKE-CDS-CORPUS-202608");
        var zoned =
                validate(
                        fixture()
                                .replace(
                                        "<DateOfBirth>1971-03-04</DateOfBirth>",
                                        "<DateOfBirth>1971-03-04+14:00</DateOfBirth>"));
        validator.verifyFileBinding(
                zoned, file, path.physicianFolder() + "/" + path.patientXmlFile());
    }

    @Test
    void shouldRefuseAValidXmlStoredUnderAnotherPatientsNameOrBirthDate() throws Exception {
        var result = validate(fixture());
        for (var wrong :
                List.of(
                        fileIdentity(
                                "Other",
                                "FAKE-CDS-CORPUS-202608",
                                java.time.LocalDate.of(1971, 3, 4)),
                        fileIdentity("Import", "Other", java.time.LocalDate.of(1971, 3, 4)),
                        fileIdentity(
                                "Import",
                                "FAKE-CDS-CORPUS-202608",
                                java.time.LocalDate.of(1971, 3, 5)))) {
            var path =
                    new io.github.carlos_emr.carlos.demographic.cdsexport.service
                                    .CdsExportPathComposer()
                            .compose(wrong);
            assertThatThrownBy(
                            () ->
                                    validator.verifyFileBinding(
                                            result,
                                            wrong,
                                            path.physicianFolder() + "/" + path.patientXmlFile()))
                    .hasMessage("CDS_EXPORT_XML_FILENAME_IDENTITY_MISMATCH");
        }
        var correct =
                fileIdentity(
                        "Import", "FAKE-CDS-CORPUS-202608", java.time.LocalDate.of(1971, 3, 4));
        assertThatThrownBy(() -> validator.verifyFileBinding(result, correct, "wrong/file.xml"))
                .hasMessage("CDS_EXPORT_XML_FILENAME_IDENTITY_MISMATCH");
    }

    private io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientFileDto
            fileIdentity(String first, String last, java.time.LocalDate birth) {
        return new io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientFileDto(
                "Test", "Provider", "123456", first, last, ID, birth);
    }

    private CdsExportXmlValidationDto validate(String xml) throws IOException {
        return validate(
                xml.getBytes(StandardCharsets.UTF_8),
                EnumSet.allOf(CdsCategory.class),
                CdsExportXmlLimitsDto.defaults(),
                new ArrayList<>());
    }

    private CdsExportXmlValidationDto validate(
            byte[] xml,
            Set<CdsCategory> categories,
            CdsExportXmlLimitsDto limits,
            List<CdsExportReportReferenceDto> references)
            throws IOException {
        return validator.validate(
                new ByteArrayInputStream(xml),
                ID,
                categories,
                VERSION,
                PROFILE,
                limits,
                references::add,
                () -> {});
    }

    private String fixture() throws IOException {
        try (var input =
                getClass().getResourceAsStream("/demographic/cds-import-all-categories.xml")) {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return xml.replace("?>", "?>\n<!--" + header() + "-->");
        }
    }

    private String header() {
        return new CdsExportHeaderComposer().comment(VERSION, PROFILE);
    }
}
