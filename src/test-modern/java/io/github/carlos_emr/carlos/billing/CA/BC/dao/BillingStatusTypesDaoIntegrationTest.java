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

import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingStatusTypes;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BillingStatusTypesDao}.
 * <p>Migrated from legacy JUnit 4 BillingStatusTypesDaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("BillingStatusTypesDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class BillingStatusTypesDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private BillingStatusTypesDao dao;

    @Test
    @Tag("read")
    @DisplayName("should find all billing status types")
    void shouldReturnAllStatusTypes_whenQueried() {
        List<BillingStatusTypes> billingTypes = dao.findAll();
        assertThat(billingTypes).isNotNull();
        assertThat(billingTypes).isNotEmpty();
    }

    @Test
    @Tag("read")
    @DisplayName("should find billing status types by codes")
    void shouldReturnStatusTypes_byCodes() {
        List<BillingStatusTypes> billingTypes = dao.findByCodes(Arrays.asList("N", "A", "H", "Z", "T"));
        assertThat(billingTypes).isNotEmpty();
    }
}
