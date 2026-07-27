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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.util;

import java.util.List;

import io.github.carlos_emr.carlos.eform.data.EForm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The response contract between a stored eForm's scrape and the payload the server embeds.
 *
 * <p>These pin a contract with third-party markup, so the failure mode is not an exception: it is a
 * growth chart that plots nothing on a render that reports complete. The regexes below are copied
 * from the form itself rather than paraphrased, because a paraphrase would pass while the form
 * failed.</p>
 */
@DisplayName("LegacyMeasurementHistory")
@Tag("unit")
@Tag("fast")
@Tag("eform")
class LegacyMeasurementHistoryUnitTest {



    /** The exact regexes the WHO growth-chart form applies to xmlhttp.responseText. */
    private final java.util.regex.Pattern formDataPattern =
            java.util.regex.Pattern.compile("<td title=\"data\">([\\d,\\.,/]+)</td>");
    private final java.util.regex.Pattern formDatePattern =
            java.util.regex.Pattern.compile("<td title=\"observed date\">([0-9,-]+)</td>");

    private List<String> matches(java.util.regex.Pattern pattern, String text) {
        List<String> found = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    @DisplayName("should emit cells the form's own regexes match, with data and dates aligned")
    void shouldEmitCells_whenScrapedByTheFormsRegexes() {
        // Row 2 has no height. If it still emitted a date cell, every later height would pair
        // with the wrong date — the arrays are indexed in parallel by the form.
        String series = "2024-03-01|101.5|16.2|48|;2024-02-01||15.8|47|;2024-01-01|99|15.1|47|";

        String markup = LegacyMeasurementHistory.markupFor(series, 1);

        assertThat(matches(formDataPattern, markup)).containsExactly("101.5", "99");
        assertThat(matches(formDatePattern, markup)).containsExactly("2024-03-01", "2024-01-01");
    }

    @Test
    @DisplayName("should select the column matching the requested measurement type")
    void shouldSelectColumn_forRequestedType() {
        String series = "2024-03-01|101.5|16.2|48|";

        assertThat(matches(formDataPattern,
                LegacyMeasurementHistory.markupFor(series, 2)))
                .containsExactly("16.2");
        assertThat(matches(formDataPattern,
                LegacyMeasurementHistory.markupFor(series, 3)))
                .containsExactly("48");
    }

    @Test
    @DisplayName("should place values flush against the tags rather than pretty-printed")
    void shouldPlaceValuesFlushAgainstTags_forDateCells() {
        // DisplayHistory.jsp pretty-prints its date cell — a newline between ">" and the value —
        // which the form's [0-9,-]+ regex does not match. Proxying that page would fill the data
        // array and leave the date array empty, so the spacing here is the contract, not style.
        String markup = LegacyMeasurementHistory.markupFor(
                "2024-03-01|101.5|16.2|48|", 1);

        assertThat(markup).isEqualTo(
                "<td title=\"data\">101.5</td><td title=\"observed date\">2024-03-01</td>");
    }

    @Test
    @DisplayName("should expose every scraped type in the embedded payload")
    void shouldExposeEveryType_inEmbeddedPayload() {
        String element = LegacyMeasurementHistory.payloadElement(
                "2024-03-01|101.5|16.2|48|");

        assertThat(element).contains(LegacyMeasurementHistory.LEGACY_MEASUREMENT_ELEMENT_ID);
        assertThat(element).contains("\"HT\"", "\"WT\"", "\"HEAD\"");
    }

    @Test
    @DisplayName("should map each measurement type to its own column in the payload")
    void shouldMapEachType_toItsOwnColumn() {
        // The row is height|weight|head-circumference, and the type keys are what the form asks
        // for by name. Transposing two of them is silent: every value is a plausible number, the
        // render reports complete, and the chart plots weight against the height percentiles.
        // Asserting only that the keys EXIST cannot catch that, so assert the values.
        String element = LegacyMeasurementHistory.payloadElement(
                "2024-03-01|101.5|16.2|48|");

        // "<" is escaped to < in the payload (see legacyMeasurementPayloadElement), so the
        // expected text is built from the same constant rather than hand-written.
        String dataCell = "\\u003ctd title=\\\"data\\\">";
        assertThat(element)
                .contains("\"HT\":\"" + dataCell + "101.5")
                .contains("\"WT\":\"" + dataCell + "16.2")
                .contains("\"HEAD\":\"" + dataCell + "48");
    }

    @Test
    @DisplayName("should carry the resolved series into the embedded payload")
    void shouldCarrySeries_intoEmbeddedPayload() {
        // Guards the wiring, not the formatting: passing "" (or any other series) to the payload
        // builder would leave the element present and correctly positioned, so the placement
        // assertions above stay green while the chart renders empty.
        String html = "<html><body><script>"
                + "u='oscarMeasurements/SetupDisplayHistory.do?type=HT';</script></body></html>";

        String embedded = LegacyMeasurementHistory.embedSeries(
                html, "2024-03-01|101.5|16.2|48|");

        assertThat(embedded).contains("101.5").contains("2024-03-01");
    }

    @Test
    @DisplayName("should escape angle brackets so the payload cannot close its own script block")
    void shouldEscapeAngleBrackets_forScriptBlockSafety() {
        // The payload is <td> markup inside <script type="application/json">. Script content is
        // raw text, so an unescaped "</script>" in it would terminate the block early and spill
        // the rest of the payload into the document as markup.
        String element = LegacyMeasurementHistory.payloadElement(
                "2024-03-01|101.5|16.2|48|");

        assertThat(element).endsWith("</script>");
        assertThat(element.substring(0, element.length() - "</script>".length()))
                .doesNotContain("<td")
                .doesNotContain("</");
        assertThat(element).contains("\\u003c");
    }

    @Test
    @DisplayName("should leave forms that never fetch the legacy route untouched")
    void shouldLeaveHtmlUntouched_whenRouteAbsent() {
        String html = "<html><body><p>no measurements here</p></body></html>";

        assertThat(LegacyMeasurementHistory.embedSeries(html, "2024-03-01|1|2|3|"))
                .isEqualTo(html);
    }

    @Test
    @DisplayName("should insert the payload inside the body when the route is fetched")
    void shouldInsertPayloadInsideBody_whenRouteFetched() {
        String html = "<html><body><script>"
                + "u='/oscarEncounter/oscarMeasurements/SetupDisplayHistory.do?type=HT';"
                + "</script></body></html>";

        String embedded = LegacyMeasurementHistory.embedSeries(
                html, "2024-03-01|101.5|16.2|48|");

        assertThat(embedded).contains(LegacyMeasurementHistory.LEGACY_MEASUREMENT_ELEMENT_ID);
        assertThat(embedded.indexOf(LegacyMeasurementHistory.LEGACY_MEASUREMENT_ELEMENT_ID))
                .isLessThan(embedded.indexOf("</body>"));
    }

    @Test
    @DisplayName("should still embed an empty payload for a patient with no recorded measurements")
    void shouldEmbedEmptyPayload_whenNoMeasurementsRecorded() {
        // "No measurements" is data. Embedding it lets the shim answer, so the form plots an
        // empty chart; omitting it would send the form to the network and fail the render on a
        // patient whose record is simply empty.
        String html = "<html><body><script>"
                + "u='oscarMeasurements/SetupDisplayHistory.do?type=WT';</script></body></html>";

        String embedded = LegacyMeasurementHistory.embedSeries(html, "");

        assertThat(embedded).contains(LegacyMeasurementHistory.LEGACY_MEASUREMENT_ELEMENT_ID);
        assertThat(embedded).contains("\"HT\":\"\"");
    }

    @Test
    @DisplayName("should embed nothing when the requester may not read measurements")
    void shouldEmbedNothing_whenMeasurementsNotPermitted() {
        // The series is measurement data reached through an eForm. The route this adapter replaces
        // enforces _measurement, while the eForm viewer requires only _eform read — so without this
        // gate an eForm reader received the patient's full dated HT/WT/HEAD history. Refusing leaves
        // the fail-visible path: no payload, the form's fetch fails, and the gate reports it.
        String html = "<html><body><script>"
                + "u='oscarMeasurements/SetupDisplayHistory.do?type=HT';</script></body></html>";
        EForm eForm = mock(EForm.class);

        assertThat(LegacyMeasurementHistory.embed(html, eForm, false))
                .describedAs("no payload element may be present")
                .isEqualTo(html)
                .doesNotContain(LegacyMeasurementHistory.LEGACY_MEASUREMENT_ELEMENT_ID);
    }
}
