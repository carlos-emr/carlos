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
import io.github.carlos_emr.carlos.commn.model.FileUploadCheck;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link FileUploadCheckDao} covering persist and findByMd5Sum.
 *
 * @since 2026-03-07
 * @see FileUploadCheckDao
 */
@DisplayName("FileUploadCheck Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class FileUploadCheckDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private FileUploadCheckDao dao;

    @Test
    @Tag("create")
    @DisplayName("should persist entity and assign generated ID")
    void shouldPersistEntity_withGeneratedId() throws Exception {
        FileUploadCheck entity = new FileUploadCheck();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should find entities matching the MD5 sum")
    void shouldFindEntities_whenMatchingMd5SumExists() throws Exception {
        FileUploadCheck entity1 = new FileUploadCheck();
        EntityDataGenerator.generateTestDataForModelClass(entity1);
        entity1.setMd5sum("abc123def456");
        entity1.setFilename("file1.pdf");
        dao.persist(entity1);

        FileUploadCheck entity2 = new FileUploadCheck();
        EntityDataGenerator.generateTestDataForModelClass(entity2);
        entity2.setMd5sum("abc123def456");
        entity2.setFilename("file2.pdf");
        dao.persist(entity2);

        FileUploadCheck entity3 = new FileUploadCheck();
        EntityDataGenerator.generateTestDataForModelClass(entity3);
        entity3.setMd5sum("different789");
        entity3.setFilename("file3.pdf");
        dao.persist(entity3);

        hibernateTemplate.flush();

        List<FileUploadCheck> result = dao.findByMd5Sum("abc123def456");

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(f -> f.getMd5sum().equals("abc123def456"));
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty list when no matching MD5 sum exists")
    void shouldReturnEmptyList_whenNoMatchingMd5SumExists() throws Exception {
        FileUploadCheck entity = new FileUploadCheck();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        entity.setMd5sum("exists123");
        dao.persist(entity);
        hibernateTemplate.flush();

        List<FileUploadCheck> result = dao.findByMd5Sum("nonexistent999");

        assertThat(result).isEmpty();
    }
}
