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
package io.github.carlos_emr.carlos.demographic.cdsimport;

import static org.junit.jupiter.api.Assertions.*;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Separate explicit resource tier: source and decoded values each substantially exceed child heap. */
@Tag("performance")
class CdsXmlParserLargeInputUnitTest {
    @TempDir Path temp;

    @Test
    void shouldKeepValidationAndDecodingBounded_whenOnePatientAndItsTextExceedHeap()
            throws Exception {
        probe("payload", "BOUNDED_CDS_OK");
    }

    @Test
    void shouldKeepEverySchemaDiagnosticBounded_whenErrorsExceedTheChildHeap() throws Exception {
        probe("diagnostics", "BOUNDED_DIAGNOSTICS_OK");
    }

    @Test
    void shouldKeepTypedPayloadsBounded_afterManySchemaErrors() throws Exception {
        probe("diagnostics-payload", "BOUNDED_DIAGNOSTIC_PAYLOAD_OK");
    }

    @Test
    void shouldBoundMalformedDynamicTypes_whenTheirPayloadExceedsHeap() throws Exception {
        probe("malformed-types", "BOUNDED_MALFORMED_TYPES_OK");
    }

    @Test
    void shouldRefuseExcessiveSchemaIdentityState_withoutDisablingIdentityValidation() throws Exception {
        probe("identities", "BOUNDED_IDENTITIES_OK");
    }

    @Test
    void shouldBuildOneBoundedDescriptorIndex_whenOneRecordHasOverAMillionResidualFields()
            throws Exception {
        probe("index", "BOUNDED_INDEX_OK");
    }

    @Test
    void shouldRefuseOversizedLexicalMarkupBoundedly_whenCommentsPiOrAttributesExceedHeap()
            throws Exception {
        probe("markup", "BOUNDED_MARKUP_OK");
    }

    @Test
    void shouldBoundInternedPiTargetNames_whenDistinctTargetsInOneSourceExceedHeap()
            throws Exception {
        probe("pi-vocabulary", "BOUNDED_PI_VOCABULARY_OK");
    }

    private void probe(String mode, String success) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath =
                System.getProperty(
                        "surefire.test.class.path", System.getProperty("java.class.path"));
        Path output = temp.resolve("probe.log");
        Process child =
                new ProcessBuilder(
                                java,
                                "-Xmx64m",
                                "-cp",
                                classpath,
                                MemoryProbe.class.getName(),
                                temp.toString(),
                                mode)
                        .redirectErrorStream(true)
                        .redirectOutput(output.toFile())
                        .start();
        if (!child.waitFor(180, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            if (!child.waitFor(10, TimeUnit.SECONDS)) fail("Bounded parser probe could not be reaped after its deadline");
            fail("Bounded parser probe exceeded its deadline");
        }
        String evidence;
        try (var input = Files.newInputStream(output)) {
            evidence = new String(input.readNBytes(8192), StandardCharsets.UTF_8);
        }
        assertEquals(0, child.exitValue(), evidence);
        assertTrue(evidence.contains(success), evidence);
    }

    /** Invoked in a fresh process, so the test runner's heap cannot hide whole-payload retention. */
    public static final class MemoryProbe {
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]);
            Path source = directory.resolve("large.xml");
            String fixture = CdsXmlParserUnitTest.fixture();
            if (args.length > 1 && args[1].equals("diagnostics")) {
                diagnostics(directory, source, fixture, false);
                return;
            }
            if (args.length > 1 && args[1].equals("diagnostics-payload")) {
                diagnostics(directory, source, fixture, true);
                return;
            }
            if (args.length > 1 && args[1].equals("malformed-types")) {
                malformedTypes(directory, source, fixture);
                return;
            }
            if (args.length > 1 && args[1].equals("identities")) {
                identities(directory, source, fixture);
                return;
            }
            if (args.length > 1 && args[1].equals("index")) {
                descriptorIndex(directory, source, fixture);
                return;
            }
            if (args.length > 1 && args[1].equals("markup")) {
                markup(directory, source, fixture);
                return;
            }
            if (args.length > 1 && args[1].equals("pi-vocabulary")) {
                piVocabulary(directory, source, fixture);
                return;
            }
            String start = fixture.substring(0, fixture.indexOf("</PatientRecord>"));
            long binaryBytes = 96L << 20;
            try (BufferedWriter xml = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
                xml.write(start);
                xml.write("<Reports><Format>Binary</Format><Content><cdsd:Media>");
                String encoded = "QUJD".repeat(16384);
                for (int i = 0; i < 2048; i++) xml.write(encoded);
                xml.write("</cdsd:Media></Content><Class>Medical Records Report</Class></Reports>");
                xml.write("<Reports><Format>Text</Format><Content><cdsd:TextContent>");
                String text = "x".repeat(65536);
                for (int i = 0; i < 1536; i++) xml.write(text);
                xml.write(
                        "</cdsd:TextContent></Content><Class>Medical Records"
                            + " Report</Class></Reports></PatientRecord></OmdCds>");
            }
            long heap = Runtime.getRuntime().maxMemory();
            if (heap > (80L << 20) || Files.size(source) < 3 * heap)
                throw new AssertionError("Probe did not constrain heap");
            var parsed =
                    new CdsXmlParser()
                            .parse(
                                    source,
                                    directory.resolve("spool"),
                                    CdsInputLimitsDto.defaults(),
                                    () -> false,
                                    record -> {});
            if (parsed.records() != 6 || parsed.decodedBinaryBytes() != binaryBytes)
                throw new AssertionError("Incorrect complete output");
            System.out.println(
                    "BOUNDED_CDS_OK sourceBytes="
                            + Files.size(source)
                            + " heap="
                            + heap
                            + " decoded="
                            + parsed.decodedBinaryBytes());
        }

        private static void piVocabulary(Path directory, Path source, String fixture)
                throws Exception {
            try (BufferedWriter xml = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
                xml.write(fixture);
                String padding = "x".repeat(160);
                for (int i = 0; i < 600_000; i++)
                    xml.write("<?source_" + i + "_" + padding + " bounded?>");
            }
            Path spool = directory.resolve("pi-vocabulary-spool");
            try {
                new CdsXmlParser()
                        .parse(
                                source,
                                spool,
                                CdsInputLimitsDto.defaults(),
                                () -> false,
                                record -> {});
                throw new AssertionError(
                        "Distinct processing-instruction targets bypassed the vocabulary limit");
            } catch (CdsInputException bounded) {
                if (!bounded.code().equals("CDS_XML_NAME_RESOURCE_LIMIT")) throw bounded;
            }
            if (Files.exists(spool.resolve("parse.seal"))
                    || Files.size(source) <= Runtime.getRuntime().maxMemory())
                throw new AssertionError(
                        "PI vocabulary refusal did not preserve an unsealed source larger than"
                            + " heap");
            System.out.println(
                    "BOUNDED_PI_VOCABULARY_OK sourceBytes="
                            + Files.size(source)
                            + " heap="
                            + Runtime.getRuntime().maxMemory());
        }

        private static void markup(Path directory, Path source, String fixture) throws Exception {
            int afterDeclaration = fixture.indexOf("?>") + 2;
            for (String kind : new String[] {"comment", "processing-instruction", "attribute"}) {
                try (BufferedWriter xml = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
                    xml.write(fixture.substring(0, afterDeclaration));
                    xml.write(
                            switch (kind) {
                                case "comment" -> "<!--";
                                case "processing-instruction" -> "<?operator ";
                                default -> "<OmdCds invalid='";
                            });
                    String block = "x".repeat(65536);
                    for (int i = 0; i < 2048; i++) xml.write(block);
                    xml.write(
                            switch (kind) {
                                case "comment" -> "-->";
                                case "processing-instruction" -> "?>";
                                default -> "'/>";
                            });
                    if (!kind.equals("attribute")) xml.write(fixture.substring(afterDeclaration));
                }
                Path spool = directory.resolve("spool-" + kind);
                try {
                    new CdsXmlParser()
                            .parse(
                                    source,
                                    spool,
                                    CdsInputLimitsDto.defaults(),
                                    () -> false,
                                    record -> {});
                    throw new AssertionError("Oversized lexical markup unexpectedly sealed");
                } catch (CdsInputException invalid) {
                    if (!invalid.code().equals("CDS_XML_MARKUP_LIMIT")) throw invalid;
                }
                if (Files.exists(spool.resolve("parse.seal"))
                        || Files.size(source) <= Runtime.getRuntime().maxMemory()) {
                    throw new AssertionError(
                            "Markup resource refusal lost source evidence or did not exceed heap");
                }
            }
            System.out.println("BOUNDED_MARKUP_OK heap=" + Runtime.getRuntime().maxMemory());
        }

        private static void descriptorIndex(Path directory, Path source, String fixture)
                throws Exception {
            try (BufferedWriter xml = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
                xml.write(fixture.substring(0, fixture.indexOf("<ClinicalNotes>")));
                xml.write("<MedicationsAndTreatments><ResidualInfo>");
                String member =
                        "<cdsd:DataElement><cdsd:Name>field</cdsd:Name><cdsd:DataType>string</cdsd:DataType><cdsd:Content>"
                                + "x".repeat(512)
                                + "</cdsd:Content></cdsd:DataElement>";
                for (int i = 0; i < 400_000; i++) xml.write(member);
                xml.write(
                        "</ResidualInfo><DrugName>INDEX"
                            + " TEST</DrugName></MedicationsAndTreatments>");
                xml.write(fixture.substring(fixture.indexOf("<ClinicalNotes>")));
            }
            CdsRecordDto[] medication = {null};
            new CdsXmlParser()
                    .parse(
                            source,
                            directory.resolve("spool"),
                            CdsInputLimitsDto.defaults(),
                            () -> false,
                            record -> {
                                if (record.section().equals("MedicationsAndTreatments"))
                                    medication[0] = record;
                            });
            if (medication[0] == null || medication[0].fieldCount() != 1_200_001)
                throw new AssertionError("Incomplete residual record");
            CountingFields fields = new CountingFields();
            for (int i = 0; i < 50; i++) {
                if (!"INDEX TEST".equals(fields.firstText(medication[0], "DrugName", 120)))
                    throw new AssertionError("Missing selected field");
                if (fields.firstText(medication[0], "Missing" + i, 120) != null)
                    throw new AssertionError("Invented absent field");
            }
            CountingFields interrupted = new CountingFields();
            interrupted.interruptAt = 32768;
            try {
                interrupted.firstText(medication[0], "DrugName", 120);
                throw new AssertionError("Interrupted giant index unexpectedly returned");
            } catch (CdsInputException stopped) {
                if (!stopped.code().equals("CDS_INPUT_CANCELLED")) throw stopped;
            } finally {
                Thread.interrupted();
            }
            if (interrupted.visited != 32768)
                throw new AssertionError("Cancellation was not checked during the giant scan");
            long heap = Runtime.getRuntime().maxMemory();
            if (fields.scans != 1 || heap > (80L << 20) || Files.size(source) < 3 * heap) {
                throw new AssertionError(
                        "Index did not remain bounded or reread metadata: " + fields.scans);
            }
            System.out.println(
                    "BOUNDED_INDEX_OK sourceBytes="
                            + Files.size(source)
                            + " fields="
                            + medication[0].fieldCount()
                            + " scans="
                            + fields.scans
                            + " heap="
                            + heap);
        }

        private static final class CountingFields extends CdsFieldParser {
            int scans;
            long visited, interruptAt = -1;

            @Override
            public void forEachField(CdsRecordDto record, Consumer<CdsFieldDto> consumer)
                    throws IOException {
                scans++;
                super.forEachField(
                        record,
                        field -> {
                            consumer.accept(field);
                            if (++visited == interruptAt) Thread.currentThread().interrupt();
                        });
            }
        }

        private static void diagnostics(Path directory, Path source, String fixture, boolean payload)
                throws Exception {
            String allergy =
                    fixture.substring(
                                    fixture.indexOf("<AllergiesAndAdverseReactions>"),
                                    fixture.indexOf("</AllergiesAndAdverseReactions>")
                                            + "</AllergiesAndAdverseReactions>".length())
                            .replace("<Severity>MO</Severity>", "<Severity>INVALID</Severity>");
            int repetitions = payload ? 4096 : 120_000;
            int afterAllergy = fixture.indexOf("</AllergiesAndAdverseReactions>") + "</AllergiesAndAdverseReactions>".length();
            try (BufferedWriter xml = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
                // Keep category order valid so every repeated invalid Severity
                // exercises its real enumeration rather than a skipped subtree.
                xml.write(fixture.substring(0, afterAllergy));
                for (int i = 0; i < repetitions; i++) xml.write(allergy);
                xml.write(fixture.substring(afterAllergy, fixture.indexOf("</PatientRecord>")));
                if (payload) {
                    xml.write("<Reports><Format>Binary</Format><Content><cdsd:Media>");
                    block(xml, "QUJD", 1536);
                    xml.write("</cdsd:Media></Content><Class>Medical Records Report</Class></Reports>");
                    xml.write("<Reports><Format>Text</Format><Content><cdsd:TextContent>");
                    block(xml, "x", 1152);
                    xml.write("</cdsd:TextContent></Content><Class>Medical Records Report</Class></Reports>");
                }
                xml.write("</PatientRecord></OmdCds>");
            }
            try {
                new CdsXmlParser()
                        .parse(
                                source,
                                directory.resolve("spool"),
                                CdsInputLimitsDto.defaults(),
                                () -> false,
                                record -> {});
                throw new AssertionError("Invalid schema unexpectedly sealed");
            } catch (CdsInputException invalid) {
                if (!invalid.code().equals("CDS_XML_SCHEMA_INVALID")) throw invalid;
            }
            Path spool = directory.resolve("spool");
            long bytes = Files.size(spool.resolve("validation-errors.jsonl"));
            verifyDiagnostics(spool, repetitions);
            long heap = Runtime.getRuntime().maxMemory();
            if (heap > (80L << 20)
                    || (!payload && bytes <= heap)
                    || (payload && Files.size(spool.resolve("values.bin")) < (144L << 20))
                    || Files.exists(directory.resolve("spool/parse.seal"))) {
                throw new AssertionError(
                        "Diagnostic stress did not exceed bounded heap safely: " + bytes);
            }
            System.out.println((payload ? "BOUNDED_DIAGNOSTIC_PAYLOAD_OK" : "BOUNDED_DIAGNOSTICS_OK")
                    + " bytes=" + bytes + " diagnostics=" + 2L * repetitions + " heap=" + heap);
        }

        private static void verifyDiagnostics(Path spool, int repetitions) throws Exception {
            var json = new com.fasterxml.jackson.databind.ObjectMapper();
            long count = 0;
            try (var reader = Files.newBufferedReader(spool.resolve("validation-errors.jsonl"), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() > 16_384) throw new AssertionError("Unbounded diagnostic row");
                    var row = json.readTree(line);
                    long occurrence = 2 + count / 2;
                    String expected = "/OmdCds[1]/PatientRecord[1]/AllergiesAndAdverseReactions[" + occurrence + "]/Severity[1]";
                    if (!row.path("code").asText().equals("CDS_XML_SCHEMA_INVALID")
                            || !row.path("sourceXPath").asText().equals(expected)) {
                        throw new AssertionError("Missing, duplicated or reordered diagnostic at " + count);
                    }
                    count++;
                }
            }
            if (count != 2L * repetitions) throw new AssertionError("Incomplete diagnostics: " + count);
        }

        private static void block(BufferedWriter xml, String unit, int blocks) throws IOException {
            String value = unit.repeat(65536 / unit.length());
            for (int i = 0; i < blocks; i++) xml.write(value);
        }

        private static void malformedTypes(Path directory, Path source, String fixture) throws Exception {
            int begin = fixture.indexOf("<ClinicalNotes>"), end = fixture.indexOf("</ClinicalNotes>") + "</ClinicalNotes>".length();
            for (String kind : new String[] {"integer-declared", "integer-unknown", "base64Binary"}) {
                Path spool = directory.resolve("malformed-" + kind);
                try (BufferedWriter xml = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
                    xml.write(fixture.substring(0, begin));
                    xml.write("<ClinicalNotes xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:xs='http://www.w3.org/2001/XMLSchema'"
                            + (kind.equals("integer-declared") ? " xsi:type='xs:integer'>" : ">"));
                    if (kind.startsWith("integer")) {
                        if (kind.equals("integer-unknown")) xml.write("<unexpected xsi:type='xs:integer'>");
                        block(xml, "1", 1152);
                        if (kind.equals("integer-unknown")) xml.write("</unexpected>");
                    } else {
                        xml.write("<unexpected xsi:type='xs:base64Binary'>");
                        block(xml, "QUJD", 1536);
                        xml.write("</unexpected>");
                    }
                    xml.write("</ClinicalNotes>");
                    xml.write(fixture.substring(end));
                }
                try {
                    new CdsXmlParser().parse(source, spool, CdsInputLimitsDto.defaults(), () -> false, record -> {});
                    throw new AssertionError("Malformed dynamic type sealed");
                } catch (CdsInputException invalid) {
                    if (!invalid.code().equals("CDS_XML_SCHEMA_INVALID")) throw invalid;
                }
                if (Files.exists(spool.resolve("parse.seal")) || Files.size(source) <= Runtime.getRuntime().maxMemory()) {
                    throw new AssertionError("Dynamic-type probe did not preserve an unsealed source larger than heap");
                }
            }
            System.out.println("BOUNDED_MALFORMED_TYPES_OK");
        }

        private static void identities(Path directory, Path source, String fixture) throws Exception {
            for (String type : new String[] {"ID", "IDREF"}) {
                Path spool = directory.resolve("identities-" + type);
                try (BufferedWriter xml = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
                    int insertion = fixture.indexOf("<ClinicalNotes>");
                    if (insertion < 0 || insertion != fixture.lastIndexOf("<ClinicalNotes>")) {
                        throw new AssertionError("Missing or ambiguous fixture insertion marker");
                    }
                    xml.write(fixture.substring(0, insertion));
                    for (int i = 0; i < 600_000; i++) {
                        xml.write("<MedicationsAndTreatments><ResidualInfo><cdsd:DataElement><cdsd:Name>identity</cdsd:Name>"
                                + "<cdsd:DataType>string</cdsd:DataType><cdsd:Content xmlns:xs='http://www.w3.org/2001/XMLSchema'"
                                + " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='xs:" + type + "'>item_" + i
                                + "</cdsd:Content></cdsd:DataElement></ResidualInfo><DrugName>identity</DrugName></MedicationsAndTreatments>");
                    }
                    xml.write(fixture.substring(insertion));
                }
                try {
                    new CdsXmlParser().parse(source, spool, CdsInputLimitsDto.defaults(), () -> false, record -> {});
                    throw new AssertionError("Unbounded identity table accepted");
                } catch (CdsInputException bounded) {
                    if (!bounded.code().equals("CDS_XML_IDENTITY_RESOURCE_LIMIT")) throw bounded;
                }
                if (Files.exists(spool.resolve("parse.seal")) || Files.size(source) <= Runtime.getRuntime().maxMemory()) {
                    throw new AssertionError("Identity probe did not preserve an unsealed source larger than heap");
                }
            }
            System.out.println("BOUNDED_IDENTITIES_OK");
        }
    }
}
