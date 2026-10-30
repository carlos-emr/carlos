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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.junit.jupiter.api.Assertions.*;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.xml.validation.TypeInfoProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** Compare against Xerces' independent PSVI metadata rather than reproducing the cursor algorithm. */
@Tag("unit")
class CdsSchemaTypeCursorUnitTest {
    @TempDir Path temp;

    @Test
    void shouldMatchPsviTypesInEveryFixtureContext_andAllDeclaredAttributes() throws Exception {
        for (String name : new String[] {"/demographic/cds-import-summary.xml", "/demographic/cds-import-all-categories.xml",
                "/cds-spec/choices-01.xml", "/cds-spec/choices-02.xml", "/cds-spec/choices-03.xml", "/cds-spec/choices-04.xml"}) {
            assertMetadata(resource(name));
        }
    }

    @Test
    void shouldMatchDynamicTypes_whenPrefixesAreReboundInSiblingScopes() throws Exception {
        String first = residual("<cdsd:Content xmlns:t='http://www.w3.org/2001/XMLSchema'"
                + " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='t:NCName'>token</cdsd:Content>");
        String second = residual("<cdsd:Content xmlns:t='cds_dt'"
                + " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='t:codeDescription'>next</cdsd:Content>");
        assertMetadata(beforeNotes(resource("/demographic/cds-import-summary.xml"), first + second));
    }

    @Test
    void shouldKeepIdentityValidationEnabled_forDuplicateIdsAndUnresolvedReferences() throws Exception {
        String fixture = resource("/demographic/cds-import-summary.xml");
        String id = residual(typed("ID", "example"));
        String reference = residual(typed("IDREF", "example"));
        parse(beforeNotes(fixture, id + reference));
        for (String invalid : new String[] {id + id, reference}) {
            CdsInputException failure = assertThrows(CdsInputException.class,
                    () -> parse(beforeNotes(fixture, invalid)),
                    invalid.equals(reference) ? "unresolved IDREF" : "duplicate ID");
            assertEquals("CDS_XML_SCHEMA_INVALID", failure.code());
        }
    }

    @Test
    void shouldPreserveImplicitSchemaInstanceAttributeTypes() throws Exception {
        String value = "<Reports><Format>Binary</Format><Content><cdsd:Media"
                + " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xmlns:xs='http://www.w3.org/2001/XMLSchema'"
                + " xsi:type='xs:base64Binary' xsi:nil='false' xsi:schemaLocation='cds example.xsd'"
                + " xsi:noNamespaceSchemaLocation='unused.xsd'>QUJD</cdsd:Media></Content>"
                + "<Class>Medical Records Report</Class></Reports>";
        String xml = resource("/demographic/cds-import-summary.xml").replace("</PatientRecord>", value + "</PatientRecord>");
        // The pinned element is not nillable. Metadata must still match during
        // invalid-input processing, while normal import continues to reject it.
        assertMetadata(xml, false);
        assertEquals("CDS_XML_SCHEMA_INVALID", assertThrows(CdsInputException.class, () -> parse(xml)).code());
    }

    @Test
    void shouldBoundAggregateIdentityCharacters_evenWhenEveryValueAndTokenCountFits() throws Exception {
        StringBuilder records = new StringBuilder();
        String large = "x".repeat(60_000);
        assertTrue(large.length() + 2 < CdsInputLimitsDto.defaults().maxBoundedScalarCharacters());
        assertTrue(18 < CdsInputLimitsDto.defaults().maxSchemaIdentityValues());
        for (int i = 0; i < 18; i++) records.append(residual(typed("ID", large + i)));
        String fixture = beforeNotes(resource("/demographic/cds-import-summary.xml"), records.toString());
        CdsInputException bounded = assertThrows(CdsInputException.class, () -> parse(fixture));
        assertEquals("CDS_XML_IDENTITY_RESOURCE_LIMIT", bounded.code());
    }

    @Test
    void shouldValidateIntrinsicStringDerivedTypes_insteadOfUsingATextWitness() throws Exception {
        String fixture = resource("/demographic/cds-import-summary.xml");
        parse(beforeNotes(fixture, residual(typed("NCName", "valid_name"))));
        CdsInputException invalid = assertThrows(CdsInputException.class,
                () -> parse(beforeNotes(fixture, residual(typed("NCName", "invalid name")))));
        assertEquals("CDS_XML_SCHEMA_INVALID", invalid.code());
    }

    @Test
    void shouldRejectMalformedBuiltInOverrides_withoutUncheckedTypeFailures() throws Exception {
        for (String type : new String[] {"anyType", "anySimpleType", "integer", "base64Binary"}) {
            String clinical = "<ClinicalNotes xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'"
                    + " xmlns:xs='http://www.w3.org/2001/XMLSchema' xsi:type='xs:" + type + "'>"
                    + (type.equals("integer") ? "123" : type.equals("base64Binary") ? "QUJD" : "<unexpected>value</unexpected>")
                    + "</ClinicalNotes>";
            String fixture = resource("/demographic/cds-import-summary.xml");
            int begin = fixture.indexOf("<ClinicalNotes>"), end = fixture.indexOf("</ClinicalNotes>") + "</ClinicalNotes>".length();
            String invalid = fixture.substring(0, begin) + clinical + fixture.substring(end);
            CdsInputException failure = assertThrows(CdsInputException.class, () -> parse(invalid));
            assertEquals("CDS_XML_SCHEMA_INVALID", failure.code());
        }
    }

    private void parse(String xml) throws Exception {
        Path run = Files.createTempDirectory(temp, "parse-");
        Path source = run.resolve("source.xml");
        Files.writeString(source, xml);
        try {
            new CdsXmlParser().parse(source, run.resolve("spool"), CdsInputLimitsDto.defaults(), () -> false, record -> {});
        } catch (CdsInputException failure) {
            assertFalse(Files.exists(run.resolve("spool/parse.seal")));
            assertEquals(xml, Files.readString(source));
            throw failure;
        }
    }

    private static String residual(String value) {
        return "<MedicationsAndTreatments><ResidualInfo><cdsd:DataElement><cdsd:Name>type fixture</cdsd:Name>"
                + "<cdsd:DataType>string</cdsd:DataType>" + value
                + "</cdsd:DataElement></ResidualInfo><DrugName>type fixture</DrugName></MedicationsAndTreatments>";
    }

    private static String beforeNotes(String fixture, String inserted) {
        String marker = "<ClinicalNotes>";
        assertTrue(fixture.contains(marker));
        assertEquals(fixture.indexOf(marker), fixture.lastIndexOf(marker));
        String result = fixture.replace(marker, inserted + marker);
        assertNotEquals(fixture, result);
        assertTrue(result.contains(inserted));
        return result;
    }

    private static String typed(String type, String value) {
        return "<cdsd:Content xmlns:xs='http://www.w3.org/2001/XMLSchema'"
                + " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:type='xs:" + type + "'>" + value + "</cdsd:Content>";
    }

    private static String resource(String name) throws Exception {
        try (var input = CdsSchemaTypeCursorUnitTest.class.getResourceAsStream(name)) {
            assertNotNull(input, name);
            byte[] bytes = input.readNBytes(2 << 20);
            assertEquals(-1, input.read());
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void assertMetadata(String xml) throws Exception {
        assertMetadata(xml, true);
    }

    private static void assertMetadata(String xml, boolean valid) throws Exception {
        var validator = CdsSchemaValidator.newValidator();
        validator.setFeature("http://apache.org/xml/features/validation/schema/augment-psvi", true);
        TypeInfoProvider reference = validator.getTypeInfoProvider();
        CdsSchemaTypeCursor cursor = CdsSchemaValidator.newTypeCursor();
        AtomicInteger elements = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        validator.setErrorHandler(new DefaultHandler() {
            @Override public void error(org.xml.sax.SAXParseException failure) { errors.incrementAndGet(); }
            @Override public void fatalError(org.xml.sax.SAXParseException failure) throws SAXException { throw failure; }
        });
        validator.setContentHandler(new DefaultHandler() {
            @Override public void startElement(String uri, String local, String qualified, Attributes attributes) {
                assertSame(reference.getElementTypeInfo(), cursor.elementType(), uri + ":" + local);
                for (int i = 0; i < attributes.getLength(); i++) {
                    assertSame(reference.getAttributeTypeInfo(i), cursor.attributeType(attributes.getURI(i), attributes.getLocalName(i)),
                            qualified + "/@" + attributes.getQName(i));
                }
                elements.incrementAndGet();
            }
        });
        var reader = CdsXmlParser.xmlReader();
        reader.setContentHandler(new DefaultHandler() {
            @Override public void setDocumentLocator(Locator locator) { validator.setDocumentLocator(locator); }
            @Override public void startDocument() throws SAXException { validator.startDocument(); }
            @Override public void endDocument() throws SAXException { validator.endDocument(); }
            @Override public void startPrefixMapping(String prefix, String uri) throws SAXException {
                cursor.prefix(prefix, uri); validator.startPrefixMapping(prefix, uri);
            }
            @Override public void endPrefixMapping(String prefix) throws SAXException { validator.endPrefixMapping(prefix); }
            @Override public void startElement(String uri, String local, String qualified, Attributes attributes) throws SAXException {
                cursor.start(uri, local, attributes); validator.startElement(uri, local, qualified, attributes);
            }
            @Override public void endElement(String uri, String local, String qualified) throws SAXException {
                validator.endElement(uri, local, qualified); cursor.end();
            }
            @Override public void characters(char[] text, int offset, int length) throws SAXException {
                validator.characters(text, offset, length);
            }
        });
        reader.parse(new InputSource(new StringReader(xml)));
        assertTrue(elements.get() > 20);
        assertEquals(valid, errors.get() == 0);
    }
}
