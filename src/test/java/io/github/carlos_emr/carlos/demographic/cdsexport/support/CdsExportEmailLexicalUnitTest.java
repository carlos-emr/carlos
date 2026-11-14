/*
 * Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.support;

import static org.junit.jupiter.api.Assertions.*;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPersonalContactDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPreferredPharmacyDto;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

/** Verify published syntax equivalence independently, including adversarial invalid input. */
class CdsExportEmailLexicalUnitTest {
    @Test
    void shouldPreserveAsciiDelimiterRules_withoutNormalizingNativeValues() {
        for (String value : List.of("a@b.c", ".@_.-", "A0._-@B0_-.C0_-", "a@b.c.d", "..@-.--")) {
            assertTrue(CdsExportEmailLexical.matches(value), value);
        }
        for (String value : List.of("", "a@", "@b.c", "a@b", "a@b..c", "a@.b.c", "a@b.c.",
                "a@@b.c", "a+b@c.d", "a@b+c.d", "é@b.c", "a@b.é", " a@b.c", "a@b.c ",
                "a@\nb.c", "a@b.c\n", "a@b.c\u0000")) {
            assertFalse(CdsExportEmailLexical.matches(value), value);
        }
        assertFalse(CdsExportEmailLexical.matches(null));
    }

    @Test
    void shouldMatchThePinnedDatatypePattern_forEveryShortSymbolCombination() throws Exception {
        Pattern published = publishedPattern();
        char[] symbols = {'a', '0', '-', '.', '@'};
        int cases = 0;
        for (int length = 0; length <= 7; length++) {
            int combinations = (int) Math.pow(symbols.length, length);
            for (int code = 0; code < combinations; code++) {
                char[] characters = new char[length];
                int remaining = code;
                for (int index = 0; index < length; index++) {
                    characters[index] = symbols[remaining % symbols.length];
                    remaining /= symbols.length;
                }
                String value = new String(characters);
                assertEquals(published.matcher(value).matches(), CdsExportEmailLexical.matches(value), value);
                cases++;
            }
        }
        assertEquals(97656, cases);
    }

    @Test
    void shouldRejectLongAdversarialDomains_andAcceptUnambiguousSyntaxWithinBoundedTime() {
        String invalid = "-@-." + "--.".repeat(100000) + "!";
        String valid = "a@" + "b.".repeat(100000) + "c";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertFalse(CdsExportEmailLexical.matches(invalid));
            assertTrue(CdsExportEmailLexical.matches(valid));
        });
    }

    @Test
    void shouldRetainTheExistingFiftyCharacterMaximum_inNativeContactAndPharmacyDtos() {
        String maximum = "a".repeat(46) + "@b.c";
        String oversized = "a" + maximum;
        assertEquals(50, maximum.length());
        assertTrue(CdsExportEmailLexical.matches(oversized)); // Syntax is distinct from caller capacity.
        assertDoesNotThrow(() -> new CdsExportPersonalContactDto("0".repeat(19) + "1", "Synthetic", null,
                List.of(), List.of(), maximum, null));
        assertDoesNotThrow(() -> new CdsExportPreferredPharmacyDto("Synthetic", null, null, null, maximum));
        assertThrows(IllegalArgumentException.class, () -> new CdsExportPersonalContactDto("0".repeat(19) + "1",
                "Synthetic", null, List.of(), List.of(), oversized, null));
        assertThrows(IllegalArgumentException.class,
                () -> new CdsExportPreferredPharmacyDto("Synthetic", null, null, null, oversized));
    }

    private static Pattern publishedPattern() throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(CdsWireSchema.datatypeBytes()));
        var types = document.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
        String pattern = null;
        for (int i = 0; i < types.getLength(); i++) {
            var type = (org.w3c.dom.Element) types.item(i);
            if (!"emailAddress".equals(type.getAttribute("name"))) continue;
            var patterns = type.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "pattern");
            assertEquals(1, patterns.getLength());
            assertNull(pattern);
            pattern = ((org.w3c.dom.Element) patterns.item(0)).getAttribute("value");
        }
        assertNotNull(pattern);
        assertFalse(pattern.isEmpty());
        return Pattern.compile(pattern); // Only short bounded reference cases use the immutable pinned XSD pattern.
    }
}
