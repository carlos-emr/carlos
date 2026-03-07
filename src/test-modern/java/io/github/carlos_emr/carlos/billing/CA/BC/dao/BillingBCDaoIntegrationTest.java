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
package io.github.carlos_emr.carlos.billing.CA.BC.dao;

import io.github.carlos_emr.carlos.commn.dao.BillingBCDao;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingBCDao}.
 * <p>Migrated from legacy JUnit 4 BillingBCDaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("BillingBCDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class BillingBCDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingBCDao dao;

    @Test
    @Tag("read")
    @DisplayName("should find billing services with various parameter combinations")
    void shouldReturnBillingServices_withVariousParameters() {
        List<Object[]> result = dao.findBillingServices("REG", "SVC", "TYPE");
        assertThat(result).isNotNull();

        result = dao.findBillingServices("ON", "SVG", "ST", "2011-09-01");
        assertThat(result).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find billing locations by region")
    void shouldReturnBillingLocations_byRegion() {
        List<Object[]> result = dao.findBillingLocations("ON");
        assertThat(result).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find billing visits by region")
    void shouldReturnBillingVisits_byRegion() {
        List<Object[]> result = dao.findBillingVisits("ON");
        assertThat(result).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find injury locations")
    void shouldReturnInjuryLocations_whenQueried() {
        List<Object[]> result = dao.findInjuryLocations();
        assertThat(result).isNotNull();
    }
}
