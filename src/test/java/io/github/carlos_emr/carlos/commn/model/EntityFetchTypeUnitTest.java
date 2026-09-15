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
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for high-volume collections that must not be eagerly
 * loaded during parent list queries.
 *
 * @since 2026-05-30
 */
@DisplayName("Entity high-volume collection fetch types")
@Tag("unit")
class EntityFetchTypeUnitTest {

    @Test
    void shouldKeepDocumentReviewsLazy_forBulkDocumentQueries() throws NoSuchFieldException {
        assertOneToManyFetchType(Document.class, "reviews", FetchType.LAZY);
    }

    @Test
    void shouldKeepPreventionExtsLazy_forBulkPreventionQueries() throws NoSuchFieldException {
        assertOneToManyFetchType(Prevention.class, "preventionExts", FetchType.LAZY);
    }

    @Test
    void shouldKeepTicklerCommentsLazy_forTicklerListQueries() throws NoSuchFieldException {
        assertOneToManyFetchType(Tickler.class, "comments", FetchType.LAZY);
    }

    @Test
    void shouldKeepBillingItemsLazy_forBillingListQueries() throws NoSuchFieldException {
        assertOneToManyFetchType(BillingONCHeader1.class, "billingItems", FetchType.LAZY);
    }

    private void assertOneToManyFetchType(Class<?> entityType, String fieldName, FetchType fetchType)
            throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        OneToMany oneToMany = field.getAnnotation(OneToMany.class);

        assertThat(oneToMany)
                .as("%s.%s should be mapped with @OneToMany", entityType.getSimpleName(), fieldName)
                .isNotNull();
        assertThat(oneToMany.fetch())
                .as("%s.%s fetch type", entityType.getSimpleName(), fieldName)
                .isEqualTo(fetchType);
    }
}
