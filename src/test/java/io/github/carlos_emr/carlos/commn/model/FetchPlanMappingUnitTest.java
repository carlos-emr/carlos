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
package io.github.carlos_emr.carlos.commn.model;

import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-association fetch decisions of issue #5307, so a later change to one of
 * them is a deliberate edit of this test and not a side effect of a blanket conversion.
 * The query-count and detached-access behaviour these mappings rely on is covered by
 * {@code CustomFilterDaoIntegrationTest} and {@code BillingServiceDaoIntegrationTest}.
 *
 * @since 2026-10-29
 */
@DisplayName("Fetch plans for CustomFilter and BillingService (issue #5307)")
@Tag("unit")
@Tag("fast")
class FetchPlanMappingUnitTest {

    @Test
    @DisplayName("should load CustomFilter provider sets lazily")
    void shouldLoadProviderSetsLazily_forCustomFilter() throws NoSuchFieldException {
        assertThat(CustomFilter.class.getDeclaredField("providers").getAnnotation(OneToMany.class).fetch())
                .isEqualTo(FetchType.LAZY);
        assertThat(CustomFilter.class.getDeclaredField("assignees").getAnnotation(ManyToMany.class).fetch())
                .isEqualTo(FetchType.LAZY);
    }

    @Test
    @DisplayName("should keep the CustomFilter program optional, which keeps it eager")
    void shouldKeepProgramOptional_forCustomFilter() throws NoSuchFieldException {
        Field program = CustomFilter.class.getDeclaredField("program");

        // programId is free text with no foreign key. Without @NotFound(IGNORE) a filter
        // naming a deleted program would fail to load; Hibernate fetches such an association
        // eagerly whatever FetchType says, so the mapping states it.
        assertThat(program.getAnnotation(NotFound.class).action()).isEqualTo(NotFoundAction.IGNORE);
        assertThat(program.getAnnotation(ManyToOne.class).fetch()).isEqualTo(FetchType.EAGER);
    }

    @Test
    @DisplayName("should map no association on BillingService")
    void shouldMapNoAssociation_onBillingService() {
        assertThat(Stream.of(BillingService.class.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(OneToMany.class)
                        || field.isAnnotationPresent(ManyToOne.class)
                        || field.isAnnotationPresent(ManyToMany.class)
                        || field.isAnnotationPresent(OneToOne.class))
                .map(Field::getName))
                .as("percentage limits are read by service code through BillingPercLimitDao")
                .isEmpty();
    }
}
