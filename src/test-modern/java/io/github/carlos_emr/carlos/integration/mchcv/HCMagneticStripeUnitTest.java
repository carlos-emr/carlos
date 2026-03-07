/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 HCMagneticStripeTest to JUnit 5 for the CARLOS EMR project (2026).
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
 * <p>Tests parsing of Ontario health card magnetic stripe data including
 * health number, name, dates, sex, and card version extraction.
 * Migrated from legacy JUnit 4 HCMagneticStripeTest.
 *
 * @since 2014-01-01 (original)
 */
@Tag("unit")
@DisplayName("HCMagneticStripe unit tests")
class HCMagneticStripeUnitTest {

    private static final String MAGNETIC_STRIPE_EXAMPLE = "%b6100549267294685^FOX/AMANDA                ^1501799219800407DKJACOB10010101?5";

    private HCMagneticStripe stripe;

    @BeforeEach
    void setUp() {
        stripe = new HCMagneticStripe(MAGNETIC_STRIPE_EXAMPLE);
    }

    @Test
    @DisplayName("should parse health number from stripe data")
    void shouldParseHealthNumber_fromStripeData() {
        assertThat(stripe.getHealthNumber()).isEqualTo("9267294685");
    }

    @Test
    @DisplayName("should parse first name from stripe data")
    void shouldParseFirstName_fromStripeData() {
        assertThat(stripe.getFirstName()).isEqualTo("AMANDA");
    }

    @Test
    @DisplayName("should parse last name from stripe data")
    void shouldParseLastName_fromStripeData() {
        assertThat(stripe.getLastName()).isEqualTo("FOX");
    }

    @Test
    @DisplayName("should parse expiry date from stripe data")
    void shouldParseExpiryDate_fromStripeData() {
        assertThat(stripe.getExpiryDate()).isEqualTo("20150107");
    }

    @Test
    @DisplayName("should parse sex from stripe data")
    void shouldParseSex_fromStripeData() {
        assertThat(stripe.getSex()).isEqualTo("F");
    }

    @Test
    @DisplayName("should parse birth date from stripe data")
    void shouldParseBirthDate_fromStripeData() {
        assertThat(stripe.getBirthDate()).isEqualTo("19800407");
    }

    @Test
    @DisplayName("should parse card version from stripe data")
    void shouldParseCardVersion_fromStripeData() {
        assertThat(stripe.getCardVersion()).isEqualTo("DK");
    }

    @Test
    @DisplayName("should parse issue date from stripe data")
    void shouldParseIssueDate_fromStripeData() {
        assertThat(stripe.getIssueDate()).isEqualTo("20100101");
    }
}
