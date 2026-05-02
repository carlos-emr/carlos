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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for correction-review draft PHI redaction. */
@DisplayName("BillingCorrectionReviewDraft")
@Tag("unit")
@Tag("billing")
class BillingCorrectionReviewDraftUnitTest {

    @Test
    void shouldRedactPhi_fromToString() {
        BillingCorrectionReviewDraft draft = new BillingCorrectionReviewDraft(
                true, "content", "42", "1234567890", "19800407",
                "00", "20260401", "O", "clinic", "999998",
                "20260401", "20260402", "12.34", "Jane Doe",
                "123 Main St", "ON", "Hamilton", "A1A1A1", "F",
                "Dr Ref", "123456", "ON", "Manual", "Checked",
                "ROSTERED", "401", List.of());

        String value = draft.toString();

        assertThat(value).contains("hin=<redacted>", "dob=<redacted>", "demoName=<redacted>");
        assertThat(value).doesNotContain("1234567890", "19800407", "Jane Doe", "123 Main St", "A1A1A1");
    }
}
