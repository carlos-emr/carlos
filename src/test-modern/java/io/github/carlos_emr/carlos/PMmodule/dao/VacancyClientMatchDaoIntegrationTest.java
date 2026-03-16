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
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.PMmodule.model.VacancyClientMatch;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VacancyClientMatchDao}.
 * Migrated from legacy JUnit 4 VacancyClientMatchDaoTest with full method coverage.
 *
 * @since 2026-03-07
 */
@DisplayName("VacancyClientMatchDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class VacancyClientMatchDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private VacancyClientMatchDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist vacancy client match with generated ID")
    void shouldPersistEntity_whenValidDataProvided() throws Exception {
        VacancyClientMatch entity = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return vacancy client matches filtered by client ID")
    void shouldReturnMatches_byClientId() throws Exception {
        int clientId1 = 101, clientId2 = 202;

        VacancyClientMatch vCM1 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM1);
        vCM1.setClient_id(clientId1);
        dao.persist(vCM1);

        VacancyClientMatch vCM2 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM2);
        vCM2.setClient_id(clientId2);
        dao.persist(vCM2);

        VacancyClientMatch vCM3 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM3);
        vCM3.setClient_id(clientId1);
        dao.persist(vCM3);

        VacancyClientMatch vCM4 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM4);
        vCM4.setClient_id(clientId2);
        dao.persist(vCM4);

        VacancyClientMatch vCM5 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM5);
        vCM5.setClient_id(clientId2);
        dao.persist(vCM5);

        List<VacancyClientMatch> result = dao.findByClientId(clientId2);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(vCM2, vCM4, vCM5);
    }

    @Test
    @Tag("read")
    @DisplayName("should return vacancy client matches filtered by status")
    void shouldReturnMatches_byStatus() throws Exception {
        int clientId1 = 101, clientId2 = 202;

        VacancyClientMatch vCM1 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM1);
        vCM1.setClient_id(clientId1);
        dao.persist(vCM1);

        VacancyClientMatch vCM2 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM2);
        vCM2.setClient_id(clientId2);
        dao.persist(vCM2);

        VacancyClientMatch vCM3 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM3);
        vCM3.setClient_id(clientId1);
        dao.persist(vCM3);

        VacancyClientMatch vCM4 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM4);
        vCM4.setClient_id(clientId2);
        dao.persist(vCM4);

        VacancyClientMatch vCM5 = new VacancyClientMatch();
        EntityDataGenerator.generateTestDataForModelClass(vCM5);
        vCM5.setClient_id(clientId2);
        dao.persist(vCM5);

        List<VacancyClientMatch> result = dao.findByClientId(clientId2);

        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(vCM2, vCM4, vCM5);
    }

    @Test
    @Tag("update")
    @DisplayName("should update status of vacancy client match")
    void shouldUpdateStatus_whenValidClientAndVacancyProvided() throws Exception {
        VacancyClientMatch v = new VacancyClientMatch();
        v.setVacancy_id(1);
        v.setClient_id(1);
        v.setContactAttempts(0);
        v.setForm_id(1);
        v.setLast_contact_date(new Date());
        v.setMatchPercentage(0);
        v.setStatus(VacancyClientMatch.ACCEPTED);
        dao.persist(v);

        dao.updateStatus(VacancyClientMatch.REJECTED, 1, 1);

        assertThat(dao.findBystatus(VacancyClientMatch.REJECTED)).hasSize(1);
    }
}
