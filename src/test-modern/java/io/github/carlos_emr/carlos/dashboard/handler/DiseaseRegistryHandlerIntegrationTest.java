/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 test to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.dashboard.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Integration tests for {@link DiseaseRegistryHandler}.
 *
 * <p>Tests adding ICD9 diagnosis codes to the disease registry for
 * multiple demographics via the handler. Verifies persistence through
 * the DxresearchDAO.
 *
 * <p>Migrated from legacy JUnit 4 DiseaseRegistryHandlerTest.
 *
 * @since 2015-01-27 (original)
 */
@Tag("integration")
@Tag("dashboard")
@DisplayName("DiseaseRegistryHandler integration tests")
class DiseaseRegistryHandlerIntegrationTest extends CarlosTestBase {

    private static DemographicDao demographicDao;
    private static DxresearchDAO dxDao;
    private static DiseaseRegistryHandler diseaseRegistryHandler;
    private static final String PROVIDER_NO = "999998";
    private static List<Integer> demoNos = new ArrayList<>();

    @BeforeAll
    static void setUpBeforeAll() throws Exception {
        demographicDao = SpringUtils.getBean(DemographicDao.class);
        dxDao = SpringUtils.getBean(DxresearchDAO.class);

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER_NO);

        for (int i = 0; i < 12; i++) {
            Demographic demographic = new Demographic();
            EntityDataGenerator.generateTestDataForModelClass(demographic);
            demographic.setDemographicNo(null);
            demographic.setProvider(provider);
            demographicDao.save(demographic);
            demoNos.add(demographic.getDemographicNo());
        }

        loggedInInfo.setLoggedInProvider(provider);
        diseaseRegistryHandler = new DiseaseRegistryHandler();
        diseaseRegistryHandler.setLoggedInInfo(loggedInInfo);
    }

    @Test
    @DisplayName("should add ICD9 code to disease registry for all demographics")
    void shouldAddIcd9Code_toDiseaseRegistryForAllDemographics() {
        String icd9code = "338.2";
        String icd9codesys = "icd9";

        for (Integer demoNo : demoNos) {
            diseaseRegistryHandler.addToDiseaseRegistry(demoNo, icd9code);
        }

        for (Integer demoNo : demoNos) {
            List<Dxresearch> list = dxDao.findByDemographicNoResearchCodeAndCodingSystem(
                    demoNo, icd9code, icd9codesys);
            assertThat(list).isNotEmpty();
            assertThat(list.get(0).getDxresearchCode()).isEqualTo(icd9code);
            assertThat(list.get(0).getCodingSystem()).isEqualTo(icd9codesys);
        }
    }
}
