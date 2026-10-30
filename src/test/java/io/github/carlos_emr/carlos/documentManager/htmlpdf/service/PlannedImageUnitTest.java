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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PlannedImage}: a decision is equal to another with the same payload, whatever array holds
 * it, and it prints what was decided, never the document's markup, a path, the key or the payload.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PlannedImage Unit Tests")
class PlannedImageUnitTest {

    private static final Element IMG = Jsoup.parse("<img src=\"FAKE-patient-scan.png\">").selectFirst("img");

    private static PlannedImage plan(byte[] data, String key) {
        return new PlannedImage(IMG, ImageOutcome.CONVERT, Path.of("/FAKE/secret/scan.png"), data, null, 2, 1, 4_000L, "FAKE reason", key);
    }

    @Test
    @DisplayName("should be equal to a plan with the same fields, comparing the payload by content")
    void shouldBeEqual_whenPayloadHasTheSameContent() {
        byte[] payload = "FAKE-bytes".getBytes(StandardCharsets.UTF_8);

        assertThat(plan(payload, "k")).isEqualTo(plan(payload.clone(), "k")).hasSameHashCodeAs(plan(payload.clone(), "k"));
    }

    @Test
    @DisplayName("should differ from a plan with another payload, key or no payload")
    void shouldDiffer_whenPayloadOrKeyDiffers() {
        byte[] payload = "FAKE-bytes".getBytes(StandardCharsets.UTF_8);

        assertThat(plan(payload, "k")).isNotEqualTo(plan("FAKE-other".getBytes(StandardCharsets.UTF_8), "k"))
                .isNotEqualTo(plan(payload, "other-key"))
                .isNotEqualTo(plan(null, "k"))
                .isNotEqualTo(null);
        assertThat(plan(null, "k")).isEqualTo(plan(null, "k"));
    }

    @Test
    @DisplayName("should print the decision and the payload's size, but not the markup, the path, the key or the bytes")
    void shouldPrintNoContent_inToString() {
        String text = plan("FAKE-bytes".getBytes(StandardCharsets.UTF_8), "FAKE-key-path").toString();

        assertThat(text).isEqualTo("PlannedImage[outcome=CONVERT, subsampling=2, orientation=1, charge=4000, reason=FAKE reason, dataBytes=10]");
        assertThat(text).doesNotContain("FAKE-patient-scan", "FAKE/secret", "FAKE-bytes", "FAKE-key-path");
        assertThat(plan(null, "k").toString()).endsWith("dataBytes=0]");
    }
}
