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
import io.github.carlos_emr.carlos.commn.model.CtlSpecialInstructions;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link CtlSpecialInstructionsDao} covering full method coverage
 * matching the legacy {@code CtlSpecialInstructionsDaoTest}.
 *
 * <p>Tests cover persist (create) and findAll operations.</p>
 *
 * @since 2026-03-07
 * @see CtlSpecialInstructionsDao
 */
@DisplayName("CtlSpecialInstructions Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class CtlSpecialInstructionsDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private CtlSpecialInstructionsDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity and assign generated ID")
    void shouldPersistEntity_withGeneratedId() {
        CtlSpecialInstructions entity = new CtlSpecialInstructions();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return all special instructions in order")
    void shouldReturnAllInstructions_whenFindAllCalled() {
        CtlSpecialInstructions csi1 = new CtlSpecialInstructions();
        EntityDataGenerator.generateTestDataForModelClass(csi1);
        dao.persist(csi1);

        CtlSpecialInstructions csi2 = new CtlSpecialInstructions();
        EntityDataGenerator.generateTestDataForModelClass(csi2);
        dao.persist(csi2);

        CtlSpecialInstructions csi3 = new CtlSpecialInstructions();
        EntityDataGenerator.generateTestDataForModelClass(csi3);
        dao.persist(csi3);

        CtlSpecialInstructions csi4 = new CtlSpecialInstructions();
        EntityDataGenerator.generateTestDataForModelClass(csi4);
        dao.persist(csi4);

        CtlSpecialInstructions csi5 = new CtlSpecialInstructions();
        EntityDataGenerator.generateTestDataForModelClass(csi5);
        dao.persist(csi5);

        List<CtlSpecialInstructions> expectedResult = Arrays.asList(csi1, csi2, csi3, csi4, csi5);
        List<CtlSpecialInstructions> result = dao.findAll();

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }
}
