/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.integration.mchcv;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link HCMagneticStripe}.
 *
 * <p>Tests parsing of Ontario health card magnetic stripe data to extract
 * patient identification fields (HIN, name, dates, sex, card version).</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@DisplayName("HCMagneticStripe")
class HCMagneticStripeUnitTest {

    private static final String MAGNETIC_STRIPE_DATA =
            "%b6100549267294685^FOX/AMANDA                ^1501799219800407DKJACOB10010101?5";

    private HCMagneticStripe stripe;

    @BeforeEach
    void setUp() {
        stripe = new HCMagneticStripe(MAGNETIC_STRIPE_DATA);
    }

    @Test
    @DisplayName("should extract health number from magnetic stripe")
    void shouldExtractHealthNumber() {
        assertThat(stripe.getHealthNumber()).isEqualTo("9267294685");
    }

    @Test
    @DisplayName("should extract first name from magnetic stripe")
    void shouldExtractFirstName() {
        assertThat(stripe.getFirstName()).isEqualTo("AMANDA");
    }

    @Test
    @DisplayName("should extract last name from magnetic stripe")
    void shouldExtractLastName() {
        assertThat(stripe.getLastName()).isEqualTo("FOX");
    }

    @Test
    @DisplayName("should extract expiry date in YYYYMMDD format")
    void shouldExtractExpiryDate() {
        assertThat(stripe.getExpiryDate()).isEqualTo("20150107");
    }

    @Test
    @DisplayName("should extract sex from magnetic stripe")
    void shouldExtractSex() {
        assertThat(stripe.getSex()).isEqualTo("F");
    }

    @Test
    @DisplayName("should extract birth date in YYYYMMDD format")
    void shouldExtractBirthDate() {
        assertThat(stripe.getBirthDate()).isEqualTo("19800407");
    }

    @Test
    @DisplayName("should extract card version from magnetic stripe")
    void shouldExtractCardVersion() {
        assertThat(stripe.getCardVersion()).isEqualTo("DK");
    }

    @Test
    @DisplayName("should extract issue date in YYYYMMDD format")
    void shouldExtractIssueDate() {
        assertThat(stripe.getIssueDate()).isEqualTo("20100101");
    }
}
