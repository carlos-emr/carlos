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
package io.github.carlos_emr.carlos.demographic.cdsimport.spec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real pinned-XSD/parser coverage, deliberately separate from native semantic storage tests. */
@Tag("unit")
class CdsSpecificationCrosswalkUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> NAMESPACES = Map.of("cds", "cds", "dt", "cds_dt");
    @TempDir Path temp;
    private final CdsXmlParser parser = new CdsXmlParser();
    private final CdsFieldParser fields = new CdsFieldParser();

    @ParameterizedTest
    @ValueSource(strings = {"choices-01.xml", "choices-02.xml", "choices-03.xml", "choices-04.xml"})
    void shouldValidateAndPreserveEveryFixtureLeafAndAttribute_throughRealParser(String name)
            throws Exception {
        JsonNode fixture = fixture(name);
        byte[] source = resource(name);
        assertEquals(fixture.path("sha256").asText(), sha256(source));
        Map<String, JsonNode> expected = new HashMap<>();
        fixture.path("fields").properties().forEach(entry -> {
            String path = entry.getKey();
            String leaf = path.substring(path.lastIndexOf('/') + 1).replace("@", "");
            int colon = leaf.indexOf(':');
            String namespace = colon < 0 ? "" : NAMESPACES.get(leaf.substring(0, colon));
            assertNotNull(namespace);
            assertEquals(null, expected.put(unprefixed(path) + "\u0000" + namespace, entry.getValue()));
        });
        List<CdsRecordDto> records = new ArrayList<>();
        Path xml = temp.resolve(name);
        Files.write(xml, source);
        var parsed = parser.parse(xml, temp.resolve("parsed"), CdsInputLimitsDto.defaults(), () -> false, records::add);
        assertEquals(16, records.size());
        assertEquals(expected.size(), parsed.fields());
        Set<String> seen = new HashSet<>();
        for (CdsRecordDto record : records) {
            fields.verifyRecord(record);
            fields.forEachField(record, field -> {
                String path = CdsFieldParser.unindexed(record.sourceXPath() + "/" + field.path());
                String key = path + "\u0000" + field.namespace();
                JsonNode specification = expected.get(key);
                assertNotNull(specification, "Unexpected or incorrectly namespaced field: " + path);
                assertTrue(seen.add(key), "Duplicate field was collapsed or emitted twice: " + path);
                String expectedKind = specification.path("kind").asText().equals("attribute") ? "ATTRIBUTE"
                        : specification.path("schemaBase").asText().equals("xs:base64Binary") ? "BINARY" : "TEXT";
                assertEquals(expectedKind, field.kind(), path);
                assertEquals(specification.path("whiteSpace").asText(), field.whitespace(), path);
                assertFalse(field.schemaType().isBlank(), "Source type was lost: " + path);
                byte[] expectedBytes = field.kind().equals("BINARY")
                        ? Base64.getDecoder().decode(specification.path("value").asText())
                        : specification.path("value").asText().getBytes(StandardCharsets.UTF_8);
                try (InputStream value = fields.openValue(record, field)) {
                    assertArrayEquals(expectedBytes, value.readAllBytes(), path);
                    assertEquals(expectedBytes.length, field.length(), path);
                    assertEquals(sha256(expectedBytes), field.sha256(), path);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            });
        }
        assertEquals(expected.keySet(), seen);
    }

    @Test
    void shouldExerciseAllReviewedChoiceArms_withExclusiveWireWitnesses() throws Exception {
        JsonNode manifest = manifest();
        assertEquals(4, manifest.path("fixtures").size());
        assertEquals(130, manifest.path("choices").size());
        Map<String, Set<String>> fixturePaths = new HashMap<>();
        Set<String> allFields = new HashSet<>();
        for (JsonNode fixture : manifest.path("fixtures")) {
            Set<String> paths = new HashSet<>();
            fixture.path("fields").properties().forEach(entry -> paths.add(entry.getKey()));
            fixturePaths.put(fixture.path("file").asText(), paths);
            allFields.addAll(paths);
        }
        assertEquals(407, allFields.size());
        assertEquals(8, allFields.stream().filter(path -> path.contains("/@")).count());
        for (JsonNode choice : manifest.path("choices")) {
            assertFalse(choice.path("witnesses").isEmpty(), choice.path("arm").asText());
            for (JsonNode selected : choice.path("fixtures")) {
                Set<String> actual = fixturePaths.get(selected.asText());
                assertNotNull(actual);
                boolean present = false;
                for (JsonNode witness : choice.path("witnesses")) present |= actual.contains(witness.asText());
                assertTrue(present, choice.path("arm").asText());
            }
        }
    }

    @Test
    void shouldRefuseBothAddressChoiceBranches_withoutPublishingParseSeal() throws Exception {
        String valid = new String(resource("choices-01.xml"), StandardCharsets.UTF_8);
        String first = "<dt:Formatted>SSSS</dt:Formatted>";
        assertTrue(valid.contains(first));
        String invalid = valid.replaceFirst(first, first + "<dt:Structured/>");
        Path xml = temp.resolve("invalid.xml");
        Path spool = temp.resolve("invalid-spool");
        Files.writeString(xml, invalid);
        CdsInputException rejected = assertThrows(CdsInputException.class,
                () -> parser.parse(xml, spool, CdsInputLimitsDto.defaults(), () -> false, record -> { }));
        assertEquals("CDS_XML_SCHEMA_INVALID", rejected.code());
        assertFalse(Files.exists(spool.resolve("parse.seal")));
        assertTrue(Files.readString(spool.resolve("validation-errors.jsonl")).contains("Address"));
    }

    private static String unprefixed(String path) {
        return path.replace("cds:", "").replace("dt:", "");
    }

    private static JsonNode fixture(String name) throws IOException {
        for (JsonNode fixture : manifest().path("fixtures"))
            if (fixture.path("file").asText().equals(name)) return fixture;
        throw new IOException("Missing synthetic crosswalk fixture");
    }

    private static JsonNode manifest() throws IOException {
        return JSON.readTree(resource("coverage.json"));
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = CdsSpecificationCrosswalkUnitTest.class.getResourceAsStream("/cds-spec/" + name)) {
            if (input == null) throw new IOException("Missing synthetic crosswalk resource");
            return input.readNBytes(512 * 1024);
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
