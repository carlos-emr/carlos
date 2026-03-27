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

import io.github.carlos_emr.carlos.commn.model.ProviderDefaultProgram;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ProviderDefaultProgramDao} covering
 * persist, getProgramByProviderNo, setDefaultProgram, getProviderSig,
 * saveProviderDefaultProgram, and toggleSig.
 *
 * <p>Migrated from legacy {@code ProviderDefaultProgramDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see ProviderDefaultProgramDao
 */
@DisplayName("ProviderDefaultProgram Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class ProviderDefaultProgramDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private ProviderDefaultProgramDao dao;

    private ProviderDefaultProgram createPdp(String providerNo, int programId, boolean sign) {
        ProviderDefaultProgram entity = new ProviderDefaultProgram();
        entity.setProviderNo(providerNo);
        entity.setProgramId(programId);
        entity.setSign(sign);
        dao.persist(entity);
        return entity;
    }

    @Nested
    @DisplayName("CRUD operations")
    class CrudOperations {

        @Test
        @Tag("create")
        @DisplayName("should persist provider default program with generated ID")
        void shouldPersistProviderDefaultProgram_whenValidDataProvided() {
            ProviderDefaultProgram entity = createPdp("100001", 10, false);

            assertThat(entity.getId()).isPositive();
        }

        @Test
        @Tag("read")
        @DisplayName("should find provider default program by ID")
        void shouldFindProviderDefaultProgram_whenValidIdProvided() {
            ProviderDefaultProgram saved = createPdp("100002", 20, true);

            ProviderDefaultProgram found = dao.find(saved.getId());

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(saved.getId());
            assertThat(found.getProviderNo()).isEqualTo("100002");
            assertThat(found.getProgramId()).isEqualTo(20);
            assertThat(found.isSign()).isTrue();
        }
    }

    @Nested
    @DisplayName("getProgramByProviderNo")
    class GetProgramByProviderNo {

        @Test
        @Tag("query")
        @DisplayName("should return programs for matching provider number")
        void shouldReturnPrograms_whenProviderNoMatches() {
            createPdp("200001", 30, false);
            createPdp("200001", 40, true);
            createPdp("200002", 50, false);

            List<ProviderDefaultProgram> results = dao.getProgramByProviderNo("200001");

            assertThat(results).hasSize(2);
            assertThat(results).allMatch(p -> p.getProviderNo().equals("200001"));
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when no matching provider number")
        void shouldReturnEmptyList_whenNoMatchingProviderNo() {
            List<ProviderDefaultProgram> results = dao.getProgramByProviderNo("999999");

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProviderSig")
    class GetProviderSig {

        @Test
        @Tag("query")
        @DisplayName("should return provider signature settings")
        void shouldReturnProviderSig_whenProviderExists() {
            createPdp("300001", 60, true);

            List<ProviderDefaultProgram> results = dao.getProviderSig("300001");

            assertThat(results).hasSize(1);
            assertThat(results.get(0).isSign()).isTrue();
        }
    }

    @Nested
    @DisplayName("toggleSig")
    class ToggleSig {

        @Test
        @Tag("update")
        @DisplayName("should toggle sign flag for all provider programs")
        void shouldToggleSignFlag_whenProviderHasPrograms() {
            createPdp("400001", 70, false);
            createPdp("400001", 80, false);

            dao.toggleSig("400001");

            List<ProviderDefaultProgram> results = dao.getProgramByProviderNo("400001");
            assertThat(results).hasSize(2);
            assertThat(results).allMatch(ProviderDefaultProgram::isSign);
        }
    }

    @Nested
    @DisplayName("saveProviderDefaultProgram")
    class SaveProviderDefaultProgram {

        @Test
        @Tag("create")
        @DisplayName("should persist new program when ID is null")
        void shouldPersistNewProgram_whenIdIsNull() {
            ProviderDefaultProgram entity = new ProviderDefaultProgram();
            entity.setProviderNo("500001");
            entity.setProgramId(90);
            entity.setSign(false);

            dao.saveProviderDefaultProgram(entity);

            assertThat(entity.getId()).isPositive();
            ProviderDefaultProgram found = dao.find(entity.getId());
            assertThat(found.getProviderNo()).isEqualTo("500001");
        }

        @Test
        @Tag("update")
        @DisplayName("should merge existing program when ID is set")
        void shouldMergeExistingProgram_whenIdIsSet() {
            ProviderDefaultProgram saved = createPdp("500002", 100, false);

            saved.setProgramId(200);
            dao.saveProviderDefaultProgram(saved);

            ProviderDefaultProgram found = dao.find(saved.getId());
            assertThat(found.getProgramId()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("setDefaultProgram")
    class SetDefaultProgram {

        @Test
        @Tag("update")
        @DisplayName("should create new default program when provider has none")
        void shouldCreateNewDefaultProgram_whenProviderHasNone() {
            dao.setDefaultProgram("600001", 110);

            List<ProviderDefaultProgram> results = dao.getProgramByProviderNo("600001");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProgramId()).isEqualTo(110);
        }

        @Test
        @Tag("update")
        @DisplayName("should update existing program when provider already has one")
        void shouldUpdateExistingProgram_whenProviderAlreadyHasOne() {
            createPdp("600002", 120, false);

            dao.setDefaultProgram("600002", 130);

            List<ProviderDefaultProgram> results = dao.getProgramByProviderNo("600002");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getProgramId()).isEqualTo(130);
        }
    }
}
