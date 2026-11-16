/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** JSON carriers and element text for characters XML 1.0 cannot carry. */
@Tag("unit")
class CdsExportResidualJsonUnitTest {
    /** Every kind of character XML 1.0 refuses, beside ones it accepts. */
    private static final String HOSTILE =
            "bell \u0007 vt \u000b ff \f esc \u001b nul \u0000 fffe \ufffe ffff \uffff"
                    + " high \ud83d low \ude00 pair \ud83d\ude00 tab \t cr \r lf \n é 語";

    private static String json(String value) throws Exception {
        var map = new LinkedHashMap<String, Object>();
        map.put("a key", value);
        map.put("n", 3);
        return JsonMapper.builder().build().writeValueAsString(map);
    }

    private static boolean xmlLegal(String text) {
        return CdsExportNativeFieldAssessments.legal(text, Integer.MAX_VALUE);
    }

    @Test
    void shouldReturnTheSameInstance_whenNothingNeedsAnEscape() throws Exception {
        String json = json("plain");
        assertThat(CdsExportResidualJson.text(json)).isSameAs(json);
        String compact = "{\"a\":\"b\"}";
        assertThat(CdsExportResidualJson.token(compact)).isSameAs(compact);
    }

    @Test
    void shouldEscapeEveryCharacterXmlCannotCarry_andDecodeToTheExactValue() throws Exception {
        String json = json(HOSTILE);
        // Jackson leaves U+FFFE, U+FFFF and unpaired surrogates as they are.
        assertThat(xmlLegal(json)).isFalse();
        for (String safe : new String[] {CdsExportResidualJson.text(json), CdsExportResidualJson.token(json)}) {
            assertThat(xmlLegal(safe)).isTrue();
            var decoded = new ObjectMapper().readTree(safe);
            assertThat(decoded.get("a key").asText()).isEqualTo(HOSTILE);
            assertThat(decoded.get("n").asInt()).isEqualTo(3);
            // A paired surrogate is a legal character and is not escaped.
            assertThat(safe).contains("pair", "\ud83d\ude00", "\u00e9", "\u8a9e");
        }
    }

    @Test
    void shouldEscapeSpacesOnlyInATokenCarrier_soASchemaAwareReaderCannotCollapseThem()
            throws Exception {
        String json = json("two  spaces and a trailing one ");
        String token = CdsExportResidualJson.token(json);
        assertThat(token).doesNotContain(" ").contains("two\\u0020\\u0020spaces");
        assertThat(new ObjectMapper().readTree(token).get("a key").asText())
                .isEqualTo("two  spaces and a trailing one ");
        // Notes and TextContent preserve white space: only illegal characters are escaped there.
        assertThat(CdsExportResidualJson.text(json)).isSameAs(json);
    }

    @Test
    void shouldCarryAnIllegalCharacterThroughEveryTokenCarrier_writtenByTheSharedHelper()
            throws Exception {
        // CdsExportScreeningMapper.tokenSafe is what the residual carriers call.
        String token = CdsExportScreeningMapper.tokenSafe(json(HOSTILE));
        assertThat(xmlLegal(token)).isTrue();
        assertThat(token).doesNotContain(" ");
        assertThat(new ObjectMapper().readTree(token).get("a key").asText()).isEqualTo(HOSTILE);
    }

    @Test
    void shouldReplaceEachIllegalCharacterOfAnElement_oneForOne() {
        String shown = CdsExportXmlText.element(HOSTILE);
        assertThat(xmlLegal(shown)).isTrue();
        assertThat(shown).hasSameSizeAs(HOSTILE);
        assertThat(shown)
                .startsWith("bell \ufffd vt \ufffd ff \ufffd esc \ufffd nul \ufffd fffe \ufffd")
                .contains("high \ufffd low \ufffd pair \ud83d\ude00 tab \t cr \r lf \n é 語");
        assertThat(CdsExportXmlText.carried(HOSTILE)).isFalse();
        String plain = "Ordinary text\r\nwith a tab\t and \ud83d\ude00";
        assertThat(CdsExportXmlText.element(plain)).isSameAs(plain);
        assertThat(CdsExportXmlText.carried(plain)).isTrue();
        assertThat(CdsExportXmlText.carried(null)).isTrue();
    }

    @Test
    void shouldKeepAContextInNotes_untilItPassesTheDictionaryLength() {
        String reference = CdsExportReportContext.id("document", "91");
        assertThat(reference).matches("[0-9a-f]{64}");
        String lead = "A statement a person reads.\n";
        String fits = "[header]\n" + "x".repeat(CdsExportReportContext.NOTES_LENGTH - lead.length() - 9);
        var whole = CdsExportReportContext.notes(lead, fits, reference);
        assertThat(whole.notes()).hasSize(CdsExportReportContext.NOTES_LENGTH).isEqualTo(lead + fits);
        assertThat(whole.companion()).isNull();
        // One character more: Notes keeps its lead and names the reference; nothing is cut.
        var moved = CdsExportReportContext.notes(lead, fits + "y", reference);
        assertThat(moved.notes()).startsWith(lead).contains(reference).doesNotContain("[header]");
        assertThat(moved.notes().length()).isLessThan(1000);
        assertThat(moved.companion()).contains(reference).endsWith(lead + fits + "y");
        // A supplementary character counts once, as the schema counts it.
        String astral = "\ud83d\ude00".repeat(CdsExportReportContext.NOTES_LENGTH);
        assertThat(CdsExportReportContext.notes("", astral, reference).companion()).isNull();
        assertThat(CdsExportReportContext.id("document", "92")).isNotEqualTo(reference);
    }
}
