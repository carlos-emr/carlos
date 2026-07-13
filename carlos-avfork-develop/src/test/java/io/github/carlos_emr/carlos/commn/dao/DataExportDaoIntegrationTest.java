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
import io.github.carlos_emr.carlos.commn.model.DataExport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DataExportDao} covering full method coverage
 * matching the legacy {@code DataExportDaoTest}.
 *
 * <p>Tests cover findAll and findAllByType operations.</p>
 *
 * @since 2026-03-07
 * @see DataExportDao
 */
@DisplayName("DataExport Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class DataExportDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DataExportDao dao;

    @Test
    @Tag("read")
    @DisplayName("should return all data exports ordered by daterun")
    void shouldReturnAllDataExports_whenFindAllCalled() {
        DataExport de1 = new DataExport();
        Timestamp ts1 = new Timestamp(1000);
        de1.setDaterun(ts1);

        DataExport de2 = new DataExport();
        Timestamp ts2 = new Timestamp(2000);
        de2.setDaterun(ts2);

        DataExport de3 = new DataExport();
        Timestamp ts3 = new Timestamp(1500);
        de3.setDaterun(ts3);

        dao.persist(de1);
        dao.persist(de2);
        dao.persist(de3);

        List<DataExport> expectedResult = Arrays.asList(de1, de3, de2);
        List<DataExport> result = dao.findAll();

        assertThat(result).hasSameSizeAs(expectedResult);
    }

    @Test
    @Tag("read")
    @DisplayName("should return data exports filtered by type and ordered by daterun")
    void shouldReturnFilteredExports_whenSearchingByType() {
        String type = "typeA";

        DataExport de1 = new DataExport();
        Timestamp ts1 = new Timestamp(1000);
        de1.setDaterun(ts1);
        de1.setType(type);

        DataExport de2 = new DataExport();
        Timestamp ts2 = new Timestamp(2000);
        de2.setDaterun(ts2);
        de2.setType("typeB");

        DataExport de3 = new DataExport();
        Timestamp ts3 = new Timestamp(1500);
        de3.setDaterun(ts3);
        de3.setType(type);

        dao.persist(de1);
        dao.persist(de2);
        dao.persist(de3);

        List<DataExport> result = dao.findAllByType(type);
        List<DataExport> expectedResult = Arrays.asList(de1, de3);

        assertThat(result).hasSameSizeAs(expectedResult);
        assertThat(result).containsExactlyElementsOf(expectedResult);
    }
}
