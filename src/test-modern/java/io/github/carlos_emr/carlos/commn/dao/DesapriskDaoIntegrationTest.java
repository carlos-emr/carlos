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
import io.github.carlos_emr.carlos.commn.model.Desaprisk;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link DesapriskDao} covering full method coverage
 * matching the legacy {@code DesapriskDaoTest}.
 *
 * <p>Tests cover persist (create) and search operations.</p>
 *
 * @since 2026-03-07
 * @see DesapriskDao
 */
@DisplayName("Desaprisk Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class DesapriskDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private DesapriskDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Test
    @Tag("create")
    @DisplayName("should persist entity and assign generated ID")
    void shouldPersistEntity_withGeneratedId() throws Exception {
        Desaprisk entity = new Desaprisk();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isNotNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should return most recent desaprisk for given formNo and demographicNo")
    void shouldReturnMostRecentResult_whenSearchingByFormNoAndDemographicNo() throws Exception {
        Date desapriskDate1 = new Date(dfm.parse("20060101").getTime());
        Date desapriskDate2 = new Date(dfm.parse("20050101").getTime());
        Date desapriskDate3 = new Date(dfm.parse("20070101").getTime());

        Date desapriskTime1 = new Date(dfm.parse("20060101").getTime());
        Date desapriskTime2 = new Date(dfm.parse("20050101").getTime());
        Date desapriskTime3 = new Date(dfm.parse("20070101").getTime());

        int formNo1 = 101;
        int formNo2 = 202;

        int demographicNo1 = 111;
        int demographicNo2 = 222;

        Desaprisk desaprisk1 = new Desaprisk();
        EntityDataGenerator.generateTestDataForModelClass(desaprisk1);
        desaprisk1.setFormNo(formNo1);
        desaprisk1.setDemographicNo(demographicNo1);
        desaprisk1.setDesapriskDate(desapriskDate1);
        desaprisk1.setDesapriskTime(desapriskTime1);
        dao.persist(desaprisk1);

        Desaprisk desaprisk2 = new Desaprisk();
        EntityDataGenerator.generateTestDataForModelClass(desaprisk2);
        desaprisk2.setFormNo(formNo2);
        desaprisk2.setDemographicNo(demographicNo2);
        desaprisk2.setDesapriskDate(desapriskDate2);
        desaprisk2.setDesapriskTime(desapriskTime2);
        dao.persist(desaprisk2);

        Desaprisk desaprisk3 = new Desaprisk();
        EntityDataGenerator.generateTestDataForModelClass(desaprisk3);
        desaprisk3.setFormNo(formNo1);
        desaprisk3.setDemographicNo(demographicNo1);
        desaprisk3.setDesapriskDate(desapriskDate3);
        desaprisk3.setDesapriskTime(desapriskTime3);
        dao.persist(desaprisk3);

        Desaprisk expectedResult = desaprisk3;
        Desaprisk result = dao.search(formNo1, demographicNo1);

        assertThat(result).isEqualTo(expectedResult);
    }
}
