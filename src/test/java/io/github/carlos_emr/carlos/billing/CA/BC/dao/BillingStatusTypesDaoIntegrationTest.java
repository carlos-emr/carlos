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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.Collections;
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

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager.persist(new BillingStatusTypes('N', "Not Billed", "Not Billed Extended", 1));
        entityManager.persist(new BillingStatusTypes('A', "Approved", "Approved Extended", 2));
        entityManager.persist(new BillingStatusTypes('H', "Hold", "Hold Extended", 3));
        entityManager.persist(new BillingStatusTypes('Z', "Archived", "Archived Extended", 4));
        entityManager.persist(new BillingStatusTypes('T', "Transferred", "Transferred Extended", 5));
        entityManager.flush();
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @Tag("read")
        @DisplayName("should return all billing status types")
        void shouldReturnAllStatusTypes_whenQueried() {
            List<BillingStatusTypes> billingTypes = dao.findAll();
            assertThat(billingTypes).hasSize(5);
            assertThat(billingTypes).extracting(BillingStatusTypes::getId)
                    .containsExactlyInAnyOrder('N', 'A', 'H', 'Z', 'T');
        }

        @Test
        @Tag("read")
        @DisplayName("should return status types with correct display names")
        void shouldReturnStatusTypes_withCorrectDisplayNames() {
            List<BillingStatusTypes> billingTypes = dao.findAll();

            BillingStatusTypes notBilled = billingTypes.stream()
                    .filter(bt -> bt.getId() == 'N')
                    .findFirst()
                    .orElseThrow();
            assertThat(notBilled.getDisplayName()).isEqualTo("Not Billed");
            assertThat(notBilled.getDisplayNameExt()).isEqualTo("Not Billed Extended");
            assertThat(notBilled.getSortOrder()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findByCodes")
    class FindByCodes {

        @Test
        @Tag("read")
        @DisplayName("should return status types matching the provided codes")
        void shouldReturnStatusTypes_whenCodesMatch() {
            List<BillingStatusTypes> results = dao.findByCodes(Arrays.asList("N", "A", "H"));
            assertThat(results).hasSize(3);
            assertThat(results).extracting(BillingStatusTypes::getId)
                    .containsExactlyInAnyOrder('N', 'A', 'H');
        }

        @Test
        @Tag("read")
        @DisplayName("should return all five status types when all codes provided")
        void shouldReturnAllFive_whenAllCodesProvided() {
            List<BillingStatusTypes> results = dao.findByCodes(Arrays.asList("N", "A", "H", "Z", "T"));
            assertThat(results).hasSize(5);
        }

        @Test
        @Tag("read")
        @DisplayName("should return single status type when one code provided")
        void shouldReturnSingleType_whenOneCodeProvided() {
            List<BillingStatusTypes> results = dao.findByCodes(Collections.singletonList("Z"));
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(Character.valueOf('Z'));
            assertThat(results.get(0).getDisplayName()).isEqualTo("Archived");
        }

        @Test
        @Tag("read")
        @DisplayName("should return empty list when no codes match")
        void shouldReturnEmptyList_whenNoCodesMatch() {
            List<BillingStatusTypes> results = dao.findByCodes(Collections.singletonList("X"));
            assertThat(results).isEmpty();
        }

        @Test
        @Tag("read")
        @DisplayName("should return only matching codes ignoring non-existent ones")
        void shouldReturnOnlyMatching_whenMixedCodesProvided() {
            List<BillingStatusTypes> results = dao.findByCodes(Arrays.asList("N", "X", "Y"));
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(Character.valueOf('N'));
        }
    }
}
