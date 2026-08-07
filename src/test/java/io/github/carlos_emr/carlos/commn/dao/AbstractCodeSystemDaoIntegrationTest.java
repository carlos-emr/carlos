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

import static io.github.carlos_emr.carlos.commn.dao.AbstractCodeSystemDao.getDaoName;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link AbstractCodeSystemDao} static utility method.
 *
 * <p>Migrated from legacy {@code AbstractCodeSystemDaoTest} (JUnit 4).</p>
 *
 * @since 2026-03-07
 */
@DisplayName("AbstractCodeSystemDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("clinical")
public class AbstractCodeSystemDaoIntegrationTest {

    @Nested
    @DisplayName("getDaoName resolution")
    class GetDaoName {

        @Test
        @Tag("read")
        @DisplayName("should return Icd9Dao class for icd9 coding system")
        void shouldReturnIcd9DaoClass_forIcd9CodingSystem() {
            assertThat(getDaoName(AbstractCodeSystemDao.codingSystem.valueOf("icd9")))
                    .isEqualTo(Icd9Dao.class);
        }

        @Test
        @Tag("read")
        @DisplayName("should return IchppccodeDao class for ichppccode coding system")
        void shouldReturnIchppccodeDaoClass_forIchppccodeCodingSystem() {
            assertThat(getDaoName(AbstractCodeSystemDao.codingSystem.valueOf("ichppccode")))
                    .isEqualTo(IchppccodeDao.class);
        }

        @Test
        @Tag("read")
        @DisplayName("should return SnomedCoreDao class for SnomedCore coding system")
        void shouldReturnSnomedCoreDaoClass_forSnomedCoreCodingSystem() {
            assertThat(getDaoName(AbstractCodeSystemDao.codingSystem.valueOf("SnomedCore")))
                    .isEqualTo(SnomedCoreDao.class);
        }

        @Test
        @Tag("read")
        @DisplayName("should throw IllegalArgumentException for invalid coding system")
        void shouldThrowIllegalArgumentException_forInvalidCodingSystem() {
            assertThatThrownBy(() -> getDaoName(AbstractCodeSystemDao.codingSystem.valueOf("FAIL")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
