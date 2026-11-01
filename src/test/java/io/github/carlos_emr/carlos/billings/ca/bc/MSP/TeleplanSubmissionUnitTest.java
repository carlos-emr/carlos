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
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regression tests for the legacy billing-ID CSV embedded in submission diagnostics. */
@Tag("unit")
@Tag("billing")
class TeleplanSubmissionUnitTest {
    @Test
    void shouldPreserveCsvRendering_withNullElementsAndLiteralNull() {
        ArrayList<String> ids = new ArrayList<>(Arrays.asList("one", null, "null", ""));
        TeleplanSubmission submission = submission(ids, ids);
        assertThat(submission.toString())
                .contains("billing to be marked\none,null,null,\nbillingmasterone,null,null,\n");
    }

    @Test
    void shouldRenderEmptyCsv_withNullOrEmptyLists() {
        assertThat(submission(null, null).toString())
                .contains("billing to be marked\n\nbillingmaster\n");
        assertThat(submission(new ArrayList<>(), new ArrayList<>()).toString())
                .contains("billing to be marked\n\nbillingmaster\n");
    }

    @Test
    void shouldRejectNonStringElements_withLegacyRawBillingmasterList() {
        for (Object value : List.of(123, new StringBuilder("text"))) {
            ArrayList<Object> rawIds = new ArrayList<>(List.of(value));
            assertThatThrownBy(() -> submission(null, rawIds).toString())
                    .isInstanceOf(ClassCastException.class);
        }
    }

    private TeleplanSubmission submission(ArrayList<String> ids, ArrayList<?> masterIds) {
        return new TeleplanSubmission("msp", "html", 0, ids, masterIds,
                BigDecimal.ZERO, new ArrayList<>(), 0);
    }
}
