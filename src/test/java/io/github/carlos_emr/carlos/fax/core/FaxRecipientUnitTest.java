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
package io.github.carlos_emr.carlos.fax.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link FaxRecipient} JSON carrier constructor. The manager's fail-fast
 * recipient parse decides whether missing fields are acceptable; this carrier must tolerate
 * them instead of NPEing mid-queue (which used to fire only after the preview file had been
 * destructively promoted).
 */
@DisplayName("FaxRecipient JSON constructor unit tests")
@Tag("unit")
@Tag("fast")
class FaxRecipientUnitTest {

    @Test
    @DisplayName("should tolerate missing name and fax fields when built from a JSON node")
    void shouldTolerateMissingFields_whenBuiltFromJsonNode() throws Exception {
        FaxRecipient recipient = new FaxRecipient((ObjectNode) new ObjectMapper().readTree("{}"));

        assertThat(recipient.getName()).isNull();
        assertThat(recipient.getFax()).isNull();
    }

    @Test
    @DisplayName("should keep digits-only fax when built from a fully populated JSON node")
    void shouldNormalizeFaxDigits_whenBuiltFromPopulatedJsonNode() throws Exception {
        FaxRecipient recipient = new FaxRecipient(
                (ObjectNode) new ObjectMapper().readTree("{\"name\":\"Jane Doe\",\"fax\":\"(555) 123-4567\"}"));

        assertThat(recipient.getName()).isEqualTo("Jane Doe");
        assertThat(recipient.getFax()).isEqualTo("5551234567");
    }

    @Test
    @DisplayName("should clear the fax to null when set to a blank value instead of keeping a stale one")
    void shouldClearFaxToNull_whenSetBlank() {
        FaxRecipient recipient = new FaxRecipient("Jane Doe", "5551234567");
        assertThat(recipient.getFax()).isEqualTo("5551234567");

        recipient.setFax("   ");

        assertThat(recipient.getFax()).as("blank input must clear, not retain the previous value").isNull();
        assertThat(recipient.hasUsableFax()).isFalse();
    }

    @Test
    @DisplayName("should store null rather than an empty string when the input has no digits")
    void shouldStoreNull_whenInputHasNoDigits() {
        FaxRecipient recipient = new FaxRecipient();
        recipient.setFax("ext.");

        assertThat(recipient.getFax()).as("digit-less input must not produce an empty-string trap state").isNull();
        assertThat(recipient.hasUsableFax()).isFalse();
    }

    @Test
    @DisplayName("should report a usable fax only when digits are present")
    void shouldReportUsableFax_whenDigitsPresent() {
        assertThat(new FaxRecipient("A", "555-000-1234").hasUsableFax()).isTrue();
        assertThat(new FaxRecipient("A", null).hasUsableFax()).isFalse();
        assertThat(new FaxRecipient("A", "").hasUsableFax()).isFalse();
    }
}
