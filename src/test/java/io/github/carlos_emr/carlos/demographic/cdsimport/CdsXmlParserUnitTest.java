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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSchemaValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.parsers.SAXParserFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CdsXmlParserUnitTest {
    @TempDir Path temp;
    private final CdsXmlParser parser = new CdsXmlParser();
    private final CdsFieldParser fields = new CdsFieldParser();
    private final AtomicInteger sequence = new AtomicInteger();

    @Test
    void shouldPreserveNamespacedFieldsAndAttributes_whenExistingSyntheticFixtureIsValid() throws Exception {
        List<CdsRecordDto> records = parse(fixture());
        assertEquals(4, records.size());
        CdsRecordDto demographic = records.get(0);
        assertEquals("FAKE-PW0000000000004241", fields.firstText(demographic, "Names/LegalName/LastName/Part", 100));
        assertEquals("M", fields.firstText(demographic, "Address/@addressType", 1));
        assertNull(fields.firstText(demographic, "Names/LegalName/LastName/Part", "wrong-namespace", 100));
        for (CdsRecordDto record : records) fields.verifyRecord(record);
    }

    @Test
    void shouldRefuseGlobalReportsRoot_whenItIsNotAPatientEnvelope() throws Exception {
        CdsInputException failure = assertThrows(CdsInputException.class,
                () -> parse("<Reports xmlns=\"cds\"><Format>Text</Format><Class>Medical Records Report</Class></Reports>"));
        assertEquals("CDS_XML_ENVELOPE_INVALID", failure.code());
    }

    @Test
    void shouldPreserveSchemaDiagnosticsAndWithholdSeal_whenDateIsImpossible() throws Exception {
        String invalid = fixture().replace("1971-03-04", "1971-02-30");
        CdsInputException failure = assertThrows(CdsInputException.class, () -> parse(invalid));
        assertEquals("CDS_XML_SCHEMA_INVALID", failure.code());
        Path spool = temp.resolve("spool-1");
        assertFalse(Files.exists(spool.resolve("parse.seal")));
        String errors = Files.readString(spool.resolve("validation-errors.jsonl"));
        assertTrue(errors.contains("CDS_XML_SCHEMA_INVALID"));
        assertTrue(errors.contains("DateOfBirth"));
    }

    @Test
    void shouldRejectDtdAndExternalEntities_whenInputTriesToReadExternalBytes() throws Exception {
        String input = fixture().replace("<OmdCds", "<!DOCTYPE OmdCds [<!ENTITY stolen SYSTEM 'file:///etc/passwd'>]><OmdCds")
                .replace("FAKE-PW0000000000004241", "&stolen;");
        assertThrows(CdsInputException.class, () -> parse(input));
        assertFalse(Files.exists(temp.resolve("spool-1/parse.seal")));
    }

    @Test
    void shouldMatchTheOriginalXsdBase64Grammar_whenPaddingWhitespaceOrAlphabetIsUnusual() throws Exception {
        String[] values = {"", "TQ==", "TWE=", "TWFu", " T W\nE =\t", "AA==", "AAA=", "AAAA",
                "TR==", "TWF=", "TQ=", "TQ===", "=AAA", "A=AA", "AA=A", "AAA", "A", "TQ==A", "TQ\u00a0==",
                "YWJjZA==", "////", "____", "AA==\nAA==", "A A A A", "\t\n", "AA?=", "AA\u0000=="};
        for (String value : values) {
            String input = report(fixture(), "Binary", "<cdsd:Media>" + value + "</cdsd:Media>");
            boolean expected = xsdAccepts(input);
            try {
                List<CdsRecordDto> records = parse(input);
                assertTrue(expected, "Streaming parser accepted XSD-invalid binary lexical form");
                fields.verifyRecord(records.get(records.size() - 1));
            } catch (CdsInputException rejected) {
                assertFalse(expected, "Streaming parser refused XSD-valid binary lexical form: " + rejected.code());
            }
        }
    }

    @Test
    void shouldReturnDecodedBytes_whenBase64SpansCdataAndXmlTextBoundaries() throws Exception {
        List<CdsRecordDto> records = parse(report(fixture(), "Binary", "<cdsd:Media>T<![CDATA[W]]>Fu</cdsd:Media>"));
        CdsRecordDto report = records.get(records.size() - 1);
        List<CdsFieldDto> binary = new ArrayList<>();
        fields.forEachField(report, value -> { if (value.kind().equals("BINARY")) binary.add(value); });
        assertEquals(1, binary.size());
        try (InputStream bytes = fields.openValue(report, binary.get(0))) {
            assertArrayEquals("Man".getBytes(StandardCharsets.UTF_8), bytes.readNBytes(4));
            assertEquals(-1, bytes.read());
        }
    }

    @Test
    void shouldNormalizeTokensWithinTheirLogicalCapacity_whenRawWhitespaceIsMuchLarger() throws Exception {
        String original = " ".repeat(100_000) + "F" + "\t".repeat(100_000);
        CdsRecordDto demographic = parse(fixture().replace("<Gender>F</Gender>", "<Gender>" + original + "</Gender>")).get(0);
        assertEquals("F", fields.firstToken(demographic, "Gender", 1));
        assertEquals("F", fields.firstText(demographic, "Gender", 1));
        List<CdsFieldDto> selected = new ArrayList<>();
        fields.forEachField(demographic, value -> { if (value.path().equals("Gender[1]")) selected.add(value); });
        assertEquals(original, fields.readText(demographic, selected.get(0), original.length()));
    }

    @Test void shouldKeepExplicitFixedValueOriginal_whenItAlreadyMatchesSchema() throws Exception {
        CdsRecordDto demographic=parse(fixture()).get(0);List<CdsFieldDto> matched=new ArrayList<>();
        fields.forEachField(demographic,field->{if(field.path().equals("Names[1]/LegalName[1]/FirstName[1]/PartType[1]"))matched.add(field);});
        assertEquals("",matched.get(0).schemaDefault());assertEquals("GIV",fields.readText(demographic,matched.get(0),3));
        assertEquals("GIV",fields.firstText(demographic,"Names/LegalName/FirstName/PartType",3));
    }

    @Test void shouldCaptureOnlyValidatorSuppliedFixedDefault_whileRawEmptyFieldStaysEmpty() throws Exception {
        CdsRecordDto demographic=parse(fixture().replace("<cdsd:PartType>GIV</cdsd:PartType>","<cdsd:PartType/>")).get(0);
        List<CdsFieldDto> matched=new ArrayList<>();fields.forEachField(demographic,field->{if(field.path().equals("Names[1]/LegalName[1]/FirstName[1]/PartType[1]"))matched.add(field);});
        assertEquals("GIV",matched.get(0).schemaDefault());assertEquals(0,matched.get(0).length());
        assertEquals("",fields.readText(demographic,matched.get(0),0));
        assertEquals("GIV",fields.firstText(demographic,"Names/LegalName/FirstName/PartType",3));
        List<CdsRecordDto> children=new ArrayList<>();fields.forEachSubrecord(demographic,"Names",children::add);
        assertEquals("GIV",fields.firstToken(children.get(0),"LegalName/FirstName/PartType",3));fields.verifyRecord(demographic);
    }

    @Test void shouldRefuseMissingFixedElementOrWrongExplicitValue_insteadOfInventingAnElement() throws Exception {
        assertThrows(CdsInputException.class,()->parse(fixture().replace("<cdsd:PartType>GIV</cdsd:PartType>","")));
        assertThrows(CdsInputException.class,()->parse(fixture().replace("<cdsd:PartType>GIV</cdsd:PartType>","<cdsd:PartType>FAMC</cdsd:PartType>")));
    }

    @Test
    void shouldCountUnicodeCodepoints_whenMaximumLengthTextContainsAstralCharacters() throws Exception {
        String note = "\ud83d\ude00".repeat(32768);
        String input = fixture().replace("</AllergiesAndAdverseReactions>", "<Notes>" + note + "</Notes></AllergiesAndAdverseReactions>");
        CdsRecordDto allergy = parse(input).stream().filter(r -> r.section().equals("AllergiesAndAdverseReactions")).findFirst().orElseThrow();
        assertEquals(note, fields.firstText(allergy, "Notes", 32768));
        assertThrows(CdsInputException.class, () -> fields.firstText(allergy, "Notes", 32767));
    }

    @Test
    void shouldRejectChangedSpoolBytes_whenOriginalRecordReceiptIsReused() throws Exception {
        CdsRecordDto demographic = parse(fixture()).get(0);
        CdsFieldDto[] first = new CdsFieldDto[1];
        fields.forEachField(demographic, value -> { if (first[0] == null && value.length() > 0) first[0] = value; });
        try (var bytes = java.nio.channels.FileChannel.open(demographic.valuesFile(), java.nio.file.StandardOpenOption.WRITE)) {
            bytes.position(first[0].offset()); bytes.write(java.nio.ByteBuffer.wrap(new byte[]{'X'}));
        }
        assertThrows(CdsInputException.class, () -> fields.verifyRecord(demographic));
    }

    @Test
    void shouldKeepSensitivePathOutOfOrdinaryLogs_whenPrivateContainmentFails() throws Exception {
        Path allowed = Files.createDirectory(temp.resolve("allowed"));
        Path outside = Files.writeString(temp.resolve("FAKE-PATIENT-1971-03-04.xml"), "fixture");
        try (LogCapture logs = LogCapture.forLogger(PathValidationUtils.class)) {
            SecurityException failure = assertThrows(SecurityException.class,
                    () -> PathValidationUtils.validatePrivateImportPath(outside.toFile(), allowed.toFile()));
            assertEquals("CDS_PATH_OUTSIDE_ROOT", failure.getMessage());
            assertNull(failure.getCause());
            assertTrue(logs.events().isEmpty());
        }
    }

    private List<CdsRecordDto> parse(String input) throws Exception {
        int number = sequence.incrementAndGet();
        Path source = Files.writeString(temp.resolve("patient-" + number + ".xml"), input);
        List<CdsRecordDto> result = new ArrayList<>();
        parser.parse(source, temp.resolve("spool-" + number), CdsInputLimitsDto.defaults(), () -> false, result::add);
        return result;
    }

    static String fixture() throws Exception {
        try (InputStream input = CdsXmlParserUnitTest.class.getResourceAsStream("/demographic/cds-import-summary.xml")) {
            return new String(input.readNBytes(65536), StandardCharsets.UTF_8);
        }
    }

    static String report(String input, String format, String content) {
        return input.replace("</PatientRecord>", "<Reports><Format>" + format + "</Format><Content>" + content
                + "</Content><Class>Medical Records Report</Class></Reports></PatientRecord>");
    }

    private static boolean xsdAccepts(String input) throws Exception {
        var validator = CdsSchemaValidator.newValidator();
        validator.setContentHandler(new DefaultHandler());
        validator.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException invalid) throws SAXException { throw invalid; }
            @Override public void fatalError(SAXParseException invalid) throws SAXException { throw invalid; }
        });
        var factory = SAXParserFactory.newInstance(); factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var reader = factory.newSAXParser().getXMLReader(); reader.setContentHandler(validator);
        reader.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException invalid) throws SAXException { throw invalid; }
            @Override public void fatalError(SAXParseException invalid) throws SAXException { throw invalid; }
        });
        try { reader.parse(new InputSource(new StringReader(input))); return true; }
        catch (SAXException invalid) { return false; }
    }
}
