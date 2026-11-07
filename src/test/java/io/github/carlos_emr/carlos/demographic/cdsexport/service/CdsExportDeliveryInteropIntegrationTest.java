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

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAllergyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPackageMemberDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalNormalizationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSnapshotService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Required independent recipient-tool test; absence of OpenSSL is a failure, never a skip. */
@Tag("integration")
@Tag("cds-export")
class CdsExportDeliveryInteropIntegrationTest {
    @TempDir Path root;
    @Test void shouldDecryptTheFinishedCmsWithOpenSsl_andRecoverTheExactZip() throws Exception {
        byte[] bytes;
        try (var input = getClass().getResourceAsStream("/demographic/cds-import-all-categories.xml")) { bytes = input.readAllBytes(); }
        roundTrip(bytes, "Physician_Test_123456/Patient_Test_C27_04031971.xml", 17, true);
    }
    @Test void shouldDeliverMappedNativeDemographics_toTheRealImporterThroughIndependentRecipientDecryption() throws Exception {
        var fields = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> fields.put(column, null));
        fields.putAll(Map.of("first_name", "Élodie😀", "last_name", "D’Arcy", "year_of_birth", "2000", "month_of_birth", "02",
                "date_of_birth", "29", "sex", "F", "patient_status", "DE", "chart_no", "000027"));
        fields.putAll(Map.of("hin", "0123456789", "hc_type", "ON", "ver", "AB", "phone", "613-555-0101", "official_lang", "French"));
        var mapped = new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, fields));
        assertThat(mapped.baseRowRepresentable()).isTrue();
        var output = new ByteArrayOutputStream();
        var identity = new CdsExportIdentityDto("a".repeat(64),
                CdsCategory.DEMOGRAPHICS, "b".repeat(64));
        var section = new CdsExportXmlComposer.Section(identity, writer -> new CdsExportDemographicXmlWriter().write(mapped, writer));
        new CdsExportXmlComposer().compose(output, "c".repeat(64), "a".repeat(64), "2026.08.0-test",
                CdsLogicalProfile.ONTARIO_MD_5_2,
                Set.of(CdsCategory.DEMOGRAPHICS),
                List.of(section).iterator(), ignored -> { }, 1L << 20, () -> { });
        new CdsExportXmlValidator().validate(
                new ByteArrayInputStream(output.toByteArray()), "27",
                Set.of(CdsCategory.DEMOGRAPHICS), "2026.08.0-test",
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportXmlLimitsDto.defaults(), ignored -> { }, () -> { });
        var record = roundTrip(output.toByteArray(), "Physician_Test_123456/Élodie😀_D’Arcy_27_29022000.xml", 1, false).getFirst();
        var receiving = new CdsFieldParser();
        assertThat(record.section()).isEqualTo("Demographics");
        assertThat(receiving.firstText(record, "Names/LegalName/FirstName/Part", 100)).isEqualTo("Élodie😀");
        assertThat(receiving.firstText(record, "Names/LegalName/LastName/Part", 100)).isEqualTo("D’Arcy");
        assertThat(receiving.firstText(record, "DateOfBirth", 100)).isEqualTo("2000-02-29");
        assertThat(receiving.firstText(record, "HealthCard/Number", 100)).isEqualTo("0123456789");
        assertThat(receiving.firstText(record, "HealthCard/ProvinceCode", 100)).isEqualTo("CA-ON");
        assertThat(receiving.firstText(record, "ChartNumber", 100)).isEqualTo("000027");
        assertThat(receiving.firstText(record, "PersonStatusCode/PersonStatusAsEnum", 100)).isEqualTo("D");
    }
    @Test void shouldKeepBothAllergyArchiveStatesAndPartialDates_throughEncryptionAndTheRealImporter() throws Exception {
        var dates = new CdsExportNativeDateMapper(); var sections = new ArrayList<CdsExportXmlComposer.Section>();
        var demographic = new CdsExportIdentityDto("a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64));
        sections.add(new CdsExportXmlComposer.Section(demographic, writer -> {
            writer.start("cds", "Names"); writer.start("cds_dt", "LegalName"); writer.attribute("", "namePurpose", "L");
            for (String part : List.of("FirstName", "LastName")) {
                writer.start("cds_dt", part); writer.element("cds_dt", "Part", "Synthetic");
                writer.element("cds_dt", "PartType", part.equals("FirstName") ? "GIV" : "FAMC"); writer.end();
            }
            writer.end(); writer.end(); writer.element("cds", "DateOfBirth", "1971-03-04"); writer.element("cds", "Gender", "U");
            writer.element("cds", "UniqueVendorIdSequence", "C27"); writer.start("cds", "PersonStatusCode");
            writer.element("cds", "PersonStatusAsEnum", "A"); writer.end();
        }));
        for (boolean archived : new boolean[]{true, false}) {
            var allergy = new CdsExportAllergyDto("Synthetic agent", "UK", "DIN", "01234567", "AL",
                    dates.map("1965-01-01", List.of("YYYY")), "A", "MI", "Synthetic reaction",
                    dates.map("2024-02-01", List.of("YYYY-MM")), "Synthetic note", archived, "0012", archived ? "0" : "3");
            var identity = new CdsExportIdentityDto("a".repeat(64), CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                    (archived ? "c" : "d").repeat(64));
            sections.add(new CdsExportXmlComposer.Section(identity, writer -> new CdsExportAllergyXmlWriter().write(allergy, writer)));
        }
        var output = new ByteArrayOutputStream(); var categories = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        new CdsExportXmlComposer().compose(output, "e".repeat(64), "a".repeat(64), "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2,
                categories, sections.iterator(), ignored -> { }, 1L << 20, () -> { });
        new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "C27", categories, "2026.08.0-test",
                CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored -> { }, () -> { });
        var records = roundTrip(output.toByteArray(), "Physician_Test_123456/Patient_Test_C27_04031971.xml", 3, false);
        var receiving = new CdsClinicalNormalizationService(new CdsFieldParser());
        for (int index = 1; index <= 2; index++) {
            var source = receiving.allergy(records.get(index));
            assertThat(source.archived()).isEqualTo(index == 1); assertThat(source.property()).isEqualTo("UK");
            assertThat(source.code()).isEqualTo("01234567");
            assertThat(source.ageOfOnset()).isEqualTo("0012"); assertThat(source.onsetOfReaction()).isEqualTo(index == 1 ? "0" : "3");
            assertThat(source.start().precision()).isEqualTo(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.Precision.YEAR);
            assertThat(source.start().original()).isEqualTo("1965");
            assertThat(source.recorded().precision()).isEqualTo(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto.Precision.MONTH);
            assertThat(source.recorded().original()).isEqualTo("2024-02");
        }
    }
    private List<CdsRecordDto> roundTrip(byte[] patientXml,
            String memberPath, long expectedRecords, boolean binary) throws Exception {
        var recipient = new CdsExportRecipientFixture();
        Path stagingRoot = Files.createDirectory(root.resolve("staging"), PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rwx------")));
        var staging = new CdsExportStagingService(stagingRoot, java.util.UUID.randomUUID().toString(), 1,
                new SecretKeySpec(new byte[32], "AES"), 16L << 20, 0, () -> {});
        var xml = staging.stage("PATIENT_XML", output -> output.write(patientXml));
        var readme = staging.stage("README", output -> output.write("SYNTHETIC migration fixture".getBytes(StandardCharsets.UTF_8)));
        var members = List.of(
                new CdsExportPackageMemberDto(memberPath, xml),
                new CdsExportPackageMemberDto("ReadMe.txt", readme));
        var finished = new CdsExportPackageComposer().compose(staging, CdsExportPackageComposerUnitTest.source(members),
                1L << 20, 100, () -> {});
        byte[] plain; try (var input = staging.open(finished.archive())) { plain = input.readAllBytes(); }
        Path encrypted = root.resolve("delivery.cms");
        try (var output = Files.newOutputStream(encrypted)) {
            new CdsExportDeliveryComposer().compose(new ByteArrayInputStream(plain), output, recipient.certificate,
                    recipient.fingerprint, plain.length, CdsExportDeliveryComposerUnitTest.hash(plain),
                    CdsExportRecipientFixture.NOW, 1L << 20, () -> {});
        }
        Path certificate = root.resolve("recipient.pem"), privateKey = root.resolve("synthetic-key.pem");
        try (var writer = new JcaPEMWriter(Files.newBufferedWriter(certificate))) { writer.writeObject(recipient.certificate); }
        try (var writer = new JcaPEMWriter(Files.newBufferedWriter(privateKey))) { writer.writeObject(recipient.keys.getPrivate()); }
        Path recovered = root.resolve("recovered.zip");
        Process process = new ProcessBuilder("openssl", "cms", "-decrypt", "-inform", "DER", "-in", encrypted.toString(),
                "-recip", certificate.toString(), "-inkey", privateKey.toString(), "-out", recovered.toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("openssl.log").toFile()).start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).as("OpenSSL must authenticate and decrypt CMS AES-256-GCM with OAEP SHA-256").isZero();
            assertThat(Files.readAllBytes(recovered)).isEqualTo(plain);
            Path receiver = Files.createDirectory(root.resolve("receiver"), PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
            var snapshots = new CdsSnapshotService();
            var snapshot = snapshots.snapshot(recovered, root, receiver, "synthetic-receipt",
                    CdsInputLimitsDto.defaults(), () -> false);
            assertThat(snapshot.patientXmlFiles()).isEqualTo(1);
            var patients = new ArrayList<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotFileDto>();
            snapshots.forEachXml(snapshot, patients::add);
            var records = new ArrayList<CdsRecordDto>();
            var parsed = new CdsXmlParser().parse(patients.getFirst().path(), receiver.resolve("parsed"),
                    CdsInputLimitsDto.defaults(), () -> false, records::add);
            assertThat(parsed.records()).isEqualTo(expectedRecords);
            if (binary) assertThat(parsed.decodedBinaryBytes()).isPositive();
            else assertThat(parsed.decodedBinaryBytes()).isZero();
            return List.copyOf(records);
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
