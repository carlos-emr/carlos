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
package io.github.carlos_emr.carlos.commn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.data.DemographicMerged;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;

@Tag("unit")
@Tag("fast")
@DisplayName("DemographicSearchResultTransformer Unit Tests")
class DemographicSearchResultTransformerUnitTest extends CarlosUnitTestBase {

    private DemographicSearchResultTransformer transformer;
    private DemographicDao demographicDao;
    private DemographicMerged demographicMerged;

    @BeforeEach
    void setUp() {
        registerMock(DemographicMergedDao.class, mock(DemographicMergedDao.class));
        registerMock(SecObjPrivilegeDao.class, mock(SecObjPrivilegeDao.class));
        registerMock(RecycleBinDao.class, mock(RecycleBinDao.class));

        transformer = new DemographicSearchResultTransformer();
        demographicDao = mock(DemographicDao.class);
        demographicMerged = mock(DemographicMerged.class);

        transformer.setDemographicDao(demographicDao);
        injectDependency(transformer, "dm", demographicMerged);
    }

    @Test
    @DisplayName("transformTuple should map native query columns directly when demographic is not merged")
    void transformTuple_shouldMapTupleValues_whenDemographicIsNotMerged() {
        Object[] tuple = {
            42, "Smith", "Jane", "CH-001", "F", "1001", "ROSTERED", "AC",
            "416-555-0100", "1990", "03", "05", "House", "Gregory", "1234567890", null
        };

        DemographicSearchResult result = transformer.transformTuple(tuple, new String[tuple.length]);

        assertThat(result.getDemographicNo()).isEqualTo(42);
        assertThat(result.getLastName()).isEqualTo("Smith");
        assertThat(result.getFirstName()).isEqualTo("Jane");
        assertThat(result.getChartNo()).isEqualTo("CH-001");
        assertThat(result.getSex()).isEqualTo("F");
        assertThat(result.getProviderNo()).isEqualTo("1001");
        assertThat(result.getProviderName()).isEqualTo("House,Gregory");
        assertThat(result.getRosterStatus()).isEqualTo("ROSTERED");
        assertThat(result.getPatientStatus()).isEqualTo("AC");
        assertThat(result.getPhone()).isEqualTo("416-555-0100");
        assertThat(result.getHin()).isEqualTo("1234567890");
        assertThat(result.getFormattedDOB()).isEqualTo("1990-03-05");
    }

    @Test
    @DisplayName("transformTuple should use the head demographic details when source demographic is merged")
    void transformTuple_shouldUseHeadDemographicValues_whenDemographicIsMerged() {
        Provider provider = new Provider();
        provider.setFirstName("Meredith");
        provider.setLastName("Grey");

        Demographic headDemographic = new Demographic();
        headDemographic.setDemographicNo(99);
        headDemographic.setLastName("Head");
        headDemographic.setFirstName("Patient");
        headDemographic.setChartNo("HEAD-99");
        headDemographic.setSex("X");
        headDemographic.setProviderNo("2002");
        headDemographic.setRosterStatus("ACTIVE");
        headDemographic.setPatientStatus("MO");
        headDemographic.setPhone("647-555-0101");
        headDemographic.setYearOfBirth("1985");
        headDemographic.setMonthOfBirth("07");
        headDemographic.setDateOfBirth("09");
        headDemographic.setHin("9999999999");
        headDemographic.setProvider(provider);

        when(demographicMerged.getHead(42)).thenReturn(99);
        when(demographicDao.getDemographicById(99)).thenReturn(headDemographic);

        Object[] tuple = {
            42, "Child", "Record", "CHILD-42", "F", "1001", "OLD", "AC",
            "416-555-0100", "1990", "03", "05", "House", "Gregory", "1234567890", 99
        };

        DemographicSearchResult result = transformer.transformTuple(tuple, new String[tuple.length]);

        assertThat(result.getDemographicNo()).isEqualTo(99);
        assertThat(result.getLastName()).isEqualTo("Head");
        assertThat(result.getFirstName()).isEqualTo("Patient");
        assertThat(result.getChartNo()).isEqualTo("HEAD-99");
        assertThat(result.getSex()).isEqualTo("X");
        assertThat(result.getProviderNo()).isEqualTo("2002");
        assertThat(result.getProviderName()).isEqualTo("Grey,Meredith");
        assertThat(result.getRosterStatus()).isEqualTo("ACTIVE");
        assertThat(result.getPatientStatus()).isEqualTo("MO");
        assertThat(result.getPhone()).isEqualTo("647-555-0101");
        assertThat(result.getHin()).isEqualTo("9999999999");
        assertThat(result.getFormattedDOB()).isEqualTo("1985-07-09");
    }

    @Test
    @DisplayName("transformList should return the same list instance unchanged")
    void transformList_shouldReturnOriginalCollection() {
        List<DemographicSearchResult> results = List.of(new DemographicSearchResult());

        List<DemographicSearchResult> transformed = transformer.transformList(results);

        assertThat(transformed).isSameAs(results);
    }
}
