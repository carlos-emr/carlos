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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import java.io.IOException;
import java.util.function.Consumer;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Separate explicit resource tier: source and decoded values each substantially exceed child heap. */
@Tag("performance")
class CdsXmlParserLargeInputTest {
    @TempDir Path temp;

    @Test
    void shouldKeepValidationAndDecodingBounded_whenOnePatientAndItsTextExceedHeap() throws Exception {
        probe("payload", "BOUNDED_CDS_OK");
    }

    @Test
    void shouldKeepEverySchemaDiagnosticBounded_whenErrorsExceedTheChildHeap() throws Exception {
        probe("diagnostics", "BOUNDED_DIAGNOSTICS_OK");
    }

    @Test
    void shouldBuildOneBoundedDescriptorIndex_whenOneRecordHasOverAMillionResidualFields() throws Exception {
        probe("index", "BOUNDED_INDEX_OK");
    }

    @Test
    void shouldRefuseOversizedLexicalMarkupBoundedly_whenCommentsPiOrAttributesExceedHeap() throws Exception {
        probe("markup", "BOUNDED_MARKUP_OK");
    }

    private void probe(String mode,String success) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path output = temp.resolve("probe.log");
        Process child = new ProcessBuilder(java, "-Xmx64m", "-cp", classpath,
                MemoryProbe.class.getName(), temp.toString(), mode).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(180, TimeUnit.SECONDS)) { child.destroyForcibly(); fail("Bounded parser probe exceeded its deadline"); }
        String evidence;
        try (var input = Files.newInputStream(output)) { evidence = new String(input.readNBytes(8192), StandardCharsets.UTF_8); }
        assertEquals(0, child.exitValue(), evidence);
        assertTrue(evidence.contains(success), evidence);
    }

    /** Invoked in a fresh process, so the test runner's heap cannot hide whole-payload retention. */
    public static final class MemoryProbe {
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]); Path source = directory.resolve("large.xml");
            String fixture = CdsXmlParserUnitTest.fixture();
            if(args.length>1 && args[1].equals("diagnostics")) { diagnostics(directory,source,fixture);return; }
            if(args.length>1 && args[1].equals("index")) { descriptorIndex(directory,source,fixture);return; }
            if(args.length>1 && args[1].equals("markup")) { markup(directory,source,fixture);return; }
            String start = fixture.substring(0, fixture.indexOf("</PatientRecord>"));
            long binaryBytes = 96L << 20;
            try (BufferedWriter xml = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
                xml.write(start); xml.write("<Reports><Format>Binary</Format><Content><cdsd:Media>");
                String encoded = "QUJD".repeat(16384);
                for (int i = 0; i < 2048; i++) xml.write(encoded);
                xml.write("</cdsd:Media></Content><Class>Medical Records Report</Class></Reports>");
                xml.write("<Reports><Format>Text</Format><Content><cdsd:TextContent>");
                String text = "x".repeat(65536);
                for (int i = 0; i < 1536; i++) xml.write(text);
                xml.write("</cdsd:TextContent></Content><Class>Medical Records Report</Class></Reports></PatientRecord></OmdCds>");
            }
            long heap = Runtime.getRuntime().maxMemory();
            if (heap > (80L << 20) || Files.size(source) < 3 * heap) throw new AssertionError("Probe did not constrain heap");
            var parsed = new CdsXmlParser().parse(source, directory.resolve("spool"), CdsInputLimitsDto.defaults(), () -> false, record -> { });
            if (parsed.records() != 6 || parsed.decodedBinaryBytes() != binaryBytes) throw new AssertionError("Incorrect complete output");
            System.out.println("BOUNDED_CDS_OK sourceBytes=" + Files.size(source) + " heap=" + heap + " decoded=" + parsed.decodedBinaryBytes());
        }

        private static void markup(Path directory, Path source, String fixture) throws Exception {
            int afterDeclaration=fixture.indexOf("?>")+2;
            for (String kind:new String[]{"comment","processing-instruction","attribute"}) {
                try (BufferedWriter xml=Files.newBufferedWriter(source,StandardCharsets.UTF_8)) {
                    xml.write(fixture.substring(0,afterDeclaration));
                    xml.write(switch(kind) {case "comment"->"<!--";case "processing-instruction"->"<?operator ";default->"<OmdCds invalid='";});
                    String block="x".repeat(65536);for(int i=0;i<2048;i++)xml.write(block);
                    xml.write(switch(kind) {case "comment"->"-->";case "processing-instruction"->"?>";default->"'/>";});
                    if(!kind.equals("attribute"))xml.write(fixture.substring(afterDeclaration));
                }
                Path spool=directory.resolve("spool-"+kind);
                try {new CdsXmlParser().parse(source,spool,CdsInputLimitsDto.defaults(),()->false,record->{ });
                    throw new AssertionError("Oversized lexical markup unexpectedly sealed");
                } catch(CdsInputException invalid) {if(!invalid.code().equals("CDS_XML_MARKUP_LIMIT"))throw invalid;}
                if(Files.exists(spool.resolve("parse.seal")) || Files.size(source)<=Runtime.getRuntime().maxMemory()) {
                    throw new AssertionError("Markup resource refusal lost source evidence or did not exceed heap");
                }
            }
            System.out.println("BOUNDED_MARKUP_OK heap="+Runtime.getRuntime().maxMemory());
        }

        private static void descriptorIndex(Path directory, Path source, String fixture) throws Exception {
            try (BufferedWriter xml = Files.newBufferedWriter(source, StandardCharsets.UTF_8)) {
                xml.write(fixture.substring(0, fixture.indexOf("<ClinicalNotes>")));
                xml.write("<MedicationsAndTreatments><ResidualInfo>");
                String member = "<cdsd:DataElement><cdsd:Name>field</cdsd:Name><cdsd:DataType>string</cdsd:DataType><cdsd:Content>"
                        + "x".repeat(512) + "</cdsd:Content></cdsd:DataElement>";
                for (int i = 0; i < 400_000; i++) xml.write(member);
                xml.write("</ResidualInfo><DrugName>INDEX TEST</DrugName></MedicationsAndTreatments>");
                xml.write(fixture.substring(fixture.indexOf("<ClinicalNotes>")));
            }
            CdsRecordDto[] medication = {null};
            new CdsXmlParser().parse(source, directory.resolve("spool"), CdsInputLimitsDto.defaults(), () -> false,
                    record -> { if (record.section().equals("MedicationsAndTreatments")) medication[0] = record; });
            if (medication[0] == null || medication[0].fieldCount() != 1_200_001) throw new AssertionError("Incomplete residual record");
            CountingFields fields = new CountingFields();
            for (int i = 0; i < 50; i++) {
                if (!"INDEX TEST".equals(fields.firstText(medication[0], "DrugName", 120))) throw new AssertionError("Missing selected field");
                if (fields.firstText(medication[0], "Missing" + i, 120) != null) throw new AssertionError("Invented absent field");
            }
            CountingFields interrupted = new CountingFields();
            interrupted.interruptAt=32768;
            try {
                interrupted.firstText(medication[0], "DrugName", 120);
                throw new AssertionError("Interrupted giant index unexpectedly returned");
            } catch (CdsInputException stopped) {
                if (!stopped.code().equals("CDS_INPUT_CANCELLED")) throw stopped;
            } finally { Thread.interrupted(); }
            if (interrupted.visited!=32768) throw new AssertionError("Cancellation was not checked during the giant scan");
            long heap = Runtime.getRuntime().maxMemory();
            if (fields.scans != 1 || heap > (80L << 20) || Files.size(source) < 3 * heap) {
                throw new AssertionError("Index did not remain bounded or reread metadata: " + fields.scans);
            }
            System.out.println("BOUNDED_INDEX_OK sourceBytes=" + Files.size(source) + " fields=" + medication[0].fieldCount()
                    + " scans=" + fields.scans + " heap=" + heap);
        }

        private static final class CountingFields extends CdsFieldParser {
            int scans;
            long visited,interruptAt=-1;
            @Override public void forEachField(CdsRecordDto record, Consumer<CdsFieldDto> consumer) throws IOException {
                scans++; super.forEachField(record, field->{
                    consumer.accept(field);
                    if (++visited==interruptAt) Thread.currentThread().interrupt();
                });
            }
        }

        private static void diagnostics(Path directory,Path source,String fixture) throws Exception {
            String allergy=fixture.substring(fixture.indexOf("<AllergiesAndAdverseReactions>"),
                    fixture.indexOf("</AllergiesAndAdverseReactions>")+"</AllergiesAndAdverseReactions>".length())
                    .replace("<Severity>MO</Severity>","<Severity>INVALID</Severity>");
            try(BufferedWriter xml=Files.newBufferedWriter(source,StandardCharsets.UTF_8)) {
                xml.write(fixture.substring(0,fixture.indexOf("</PatientRecord>")));
                for(int i=0;i<120_000;i++) xml.write(allergy);
                xml.write("</PatientRecord></OmdCds>");
            }
            try {
                new CdsXmlParser().parse(source,directory.resolve("spool"),CdsInputLimitsDto.defaults(),()->false,record->{ });
                throw new AssertionError("Invalid schema unexpectedly sealed");
            } catch(CdsInputException invalid) {
                if(!invalid.code().equals("CDS_XML_SCHEMA_INVALID")) throw invalid;
            }
            long bytes=Files.size(directory.resolve("spool/validation-errors.jsonl"));
            long heap=Runtime.getRuntime().maxMemory();
            if(heap>(80L<<20) || bytes<=heap || Files.exists(directory.resolve("spool/parse.seal"))) {
                throw new AssertionError("Diagnostic stress did not exceed bounded heap safely: "+bytes);
            }
            System.out.println("BOUNDED_DIAGNOSTICS_OK bytes="+bytes+" heap="+heap);
        }
    }
}
