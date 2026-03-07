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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.commn.model.TicklerTextSuggest;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link TicklerTextSuggestDao} covering active and
 * inactive tickler text suggestion queries.
 *
 * <p>Migrated from legacy {@code TicklerTextSuggestDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see TicklerTextSuggestDao
 */
@DisplayName("TicklerTextSuggestDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("tickler")
@Transactional
public class TicklerTextSuggestDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private TicklerTextSuggestDao ticklerTextSuggestDao;

    private TicklerTextSuggest createSuggest(boolean active, String text) {
        TicklerTextSuggest entity = new TicklerTextSuggest();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setActive(active);
        entity.setSuggestedText(text);
        ticklerTextSuggestDao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("getActiveTicklerTextSuggests")
    class GetActiveTicklerTextSuggests {

        @Test
        @Tag("filter")
        @DisplayName("should return only active tickler text suggestions")
        void shouldReturnOnlyActiveSuggestions_whenActiveAndInactiveExist() {
            TicklerTextSuggest active1 = createSuggest(true, "This tickler is active");
            TicklerTextSuggest active2 = createSuggest(true, "This tickler is also active");
            createSuggest(false, "This tickler is not active");
            hibernateTemplate.flush();

            List<TicklerTextSuggest> result = ticklerTextSuggestDao.getActiveTicklerTextSuggests();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(TicklerTextSuggest::getSuggestedText)
                    .containsExactlyInAnyOrder("This tickler is active", "This tickler is also active");
        }

        @Test
        @Tag("filter")
        @DisplayName("should return empty list when no active suggestions exist")
        void shouldReturnEmptyList_whenNoActiveSuggestionsExist() {
            createSuggest(false, "inactive only");
            hibernateTemplate.flush();

            List<TicklerTextSuggest> result = ticklerTextSuggestDao.getActiveTicklerTextSuggests();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getInactiveTicklerTextSuggests")
    class GetInactiveTicklerTextSuggests {

        @Test
        @Tag("filter")
        @DisplayName("should return only inactive tickler text suggestions")
        void shouldReturnOnlyInactiveSuggestions_whenActiveAndInactiveExist() {
            createSuggest(false, "This tickler is not active");
            createSuggest(false, "This tickler is also not active");
            createSuggest(true, "This tickler is active");
            hibernateTemplate.flush();

            List<TicklerTextSuggest> result = ticklerTextSuggestDao.getInactiveTicklerTextSuggests();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(TicklerTextSuggest::getSuggestedText)
                    .containsExactlyInAnyOrder("This tickler is not active", "This tickler is also not active");
        }

        @Test
        @Tag("filter")
        @DisplayName("should return empty list when no inactive suggestions exist")
        void shouldReturnEmptyList_whenNoInactiveSuggestionsExist() {
            createSuggest(true, "active only");
            hibernateTemplate.flush();

            List<TicklerTextSuggest> result = ticklerTextSuggestDao.getInactiveTicklerTextSuggests();
            assertThat(result).isEmpty();
        }
    }
}
