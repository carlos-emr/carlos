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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.*;

@Tag("unit")
class CdsPreparedHistoryDefaultsUnitTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void shouldApplyTheAbsentArchiveDefault_onlyToACopyOfVerifiedHistory() throws Exception {
        var source = json.readTree("{\"content\":\"Original café 😀\"}");
        var result = CdsPreparedHistoryDefaults.apply(source);
        assertThat(result.path("archived").asBoolean()).isFalse();
        assertThat(source.has("archived")).isFalse();
        assertThat(result.path("content").asText()).isEqualTo("Original café 😀");
    }

    @Test
    void shouldRetainBothExplicitBooleanStates_withoutCoercion() throws Exception {
        for (boolean archived : new boolean[] {false, true}) {
            var value = json.readTree("{\"archived\":" + archived + "}");
            assertThat(CdsPreparedHistoryDefaults.apply(value).path("archived").asBoolean())
                    .isEqualTo(archived);
        }
    }

    @Test
    void shouldRejectNullTextNumericAndNonObjectStates_insteadOfDefaultingThemToActive()
            throws Exception {
        for (String value :
                new String[] {
                    "null",
                    "[]",
                    "{\"archived\":null}",
                    "{\"archived\":0}",
                    "{\"archived\":\"false\"}"
                }) {
            var source = json.readTree(value);
            assertThatThrownBy(() -> CdsPreparedHistoryDefaults.apply(source))
                    .hasMessage("CDS_PREPARED_HISTORY_DEFAULTS_INVALID");
        }
    }
}
