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

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link Hl7PidDao}.
 * <p>Migrated from legacy JUnit 4 Hl7PidDaoTest with full method coverage.</p>
 *
 * @since 2026-03-07
 */
@DisplayName("Hl7PidDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("billing-bc")
@Transactional
public class Hl7PidDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private Hl7PidDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity with generated test data")
    void shouldPersistEntity_whenValidDataProvided() {
        Hl7Pid entity = new Hl7Pid();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);
        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find PIDs by message ID")
    void shouldReturnPids_byMessageId() {
        assertThat(dao.findByMessageId(100)).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find PIDs by status")
    void shouldReturnPids_byStatus() {
        assertThat(dao.findPidsByStatus("F")).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should find PIDs and MSH by message ID")
    void shouldReturnPidsAndMsh_byMessageId() {
        assertThat(dao.findPidsAndMshByMessageId(100)).isNotNull();
    }
}
