/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.on.data;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BillingReferralDoctor#fromFamilyDoctor(String)} —
 * locks the legacy "N/A" / "000000" placeholder behavior that
 * Review and Shortcut both depend on.
 */
@Tag("unit")
@Tag("billing")
class BillingReferralDoctorUnitTest {

    @Nested
    @DisplayName("fromFamilyDoctor")
    class FromFamilyDoctor {

        @Test
        @DisplayName("should return N/A and 000000 placeholders when blob is null")
        void shouldReturnPlaceholders_whenBlobNull() {
            BillingReferralDoctor r = BillingReferralDoctor.fromFamilyDoctor(null);
            assertThat(r.name()).isEqualTo("N/A");
            assertThat(r.ohip()).isEqualTo("000000");
            assertThat(r.specialty()).isEmpty();
        }

        @Test
        @DisplayName("should extract rd and rdohip from a populated XML blob")
        void shouldExtractFromXml_whenBlobPopulated() {
            String blob = "<x><rd>Smith, John</rd><rdohip>123456</rdohip></x>";
            BillingReferralDoctor r = BillingReferralDoctor.fromFamilyDoctor(blob);
            assertThat(r.name()).isEqualTo("Smith, John");
            assertThat(r.ohip()).isEqualTo("123456");
            assertThat(r.specialty()).isEmpty();
        }

        @Test
        @DisplayName("should null-coalesce missing rd / rdohip elements to empty")
        void shouldCoalesceMissingElements_toEmpty() {
            String blob = "<x><rd>Smith, John</rd></x>";
            BillingReferralDoctor r = BillingReferralDoctor.fromFamilyDoctor(blob);
            assertThat(r.name()).isEqualTo("Smith, John");
            assertThat(r.ohip()).isEmpty();
        }

        @Test
        @DisplayName("should treat empty blob string as no XML content rather than null placeholder")
        void shouldDistinguishEmptyBlobFromNull() {
            BillingReferralDoctor r = BillingReferralDoctor.fromFamilyDoctor("");
            assertThat(r.name()).isEmpty();
            assertThat(r.ohip()).isEmpty();
        }
    }
}
