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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationServiceDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Behaviour pinned here: a single imperfect legacy row must never blank the patient's whole
 * Consultations tab.
 *
 * <p>{@code ConsultationRequestDaoImpl.getConsults(Integer)} deliberately dropped its
 * {@code Demographic} and {@code Provider} existence joins so that consults whose ordering
 * provider is missing stop being silently filtered out of the chart. That widening only helps
 * if the caller tolerates the rows it now lets through: {@code estConsultationVecByDemographic}
 * shares one {@code try/catch} across its entire loop, so any unguarded dereference turns a
 * single bad row into an empty tab plus {@code verdict == false} — the exact symptom the widened
 * query was meant to cure.</p>
 */
@Tag("unit")
@Tag("consultation")
@DisplayName("EctViewConsultationRequestsUtil - demographic-scoped consult list")
class EctViewConsultationRequestsUtilUnitTest extends CarlosUnitTestBase {

    private static final String DEMO_NO = "42";
    private static final Integer DEMO_ID = 42;

    private ConsultationRequestDao consultationRequestDao;
    private ConsultationRequestExtDao consultationRequestExtDao;
    private ProviderDao providerDao;
    private DemographicManager demographicManager;

    private LoggedInInfo loggedInInfo;
    private EctViewConsultationRequestsUtil util;

    @BeforeEach
    void setUp() {
        consultationRequestDao = mock(ConsultationRequestDao.class);
        consultationRequestExtDao = mock(ConsultationRequestExtDao.class);
        providerDao = mock(ProviderDao.class);
        demographicManager = mock(DemographicManager.class);
        loggedInInfo = mock(LoggedInInfo.class);

        registerMock(ConsultationRequestDao.class, consultationRequestDao);
        registerMock(ConsultationRequestExtDao.class, consultationRequestExtDao);
        registerMock(ProviderDao.class, providerDao);
        registerMock(DemographicManager.class, demographicManager);
        // Resolved by the util even on the serviceId==0 path, so it must be registered.
        registerMock(ConsultationServiceDao.class, mock(ConsultationServiceDao.class));

        // serviceId 0 routes the description and specialist lookups through the extensions table,
        // keeping these tests off the ConsultationServices path they are not about.
        when(consultationRequestExtDao.getConsultationRequestExtsByKey(anyInt(), anyString()))
                .thenReturn("eReferral service");

        util = new EctViewConsultationRequestsUtil();
    }

    /**
     * A real entity rather than a mock, matching the convention in the sibling consultation tests.
     * {@code id} is JPA-generated with no setter, and the util calls {@code getId().toString()},
     * so the identifier is supplied by overriding the getter.
     */
    private ConsultationRequest consultWithNoOrderingProvider() {
        ConsultationRequest consult = new ConsultationRequest() {
            @Override
            public Integer getId() {
                return 7;
            }
        };
        consult.setServiceId(0);
        consult.setDemographicId(DEMO_ID);
        consult.setStatus("1");
        consult.setUrgency("2");
        // The row the widened query newly returns: no ordering provider at all.
        consult.setProviderNo(null);
        consult.setReferralDate(null);
        return consult;
    }

    private Demographic demographicWithMrp(String providerNo) {
        Demographic demographic = new Demographic();
        demographic.setLastName("Doe");
        demographic.setFirstName("Jane");
        demographic.setProviderNo(providerNo);
        return demographic;
    }

    @Test
    @DisplayName("should still list the consult when the demographic's MRP no longer exists")
    void shouldListConsult_whenMrpProviderRowWasDeleted() throws Exception {
        // A non-empty providerNo pointing at a provider row that has since been removed. Guarding
        // only the id (the old behaviour) walks straight into getProvider(...) returning null.
        when(demographicManager.getDemographic(any(LoggedInInfo.class), eq(DEMO_ID)))
                .thenReturn(demographicWithMrp("999"));
        when(providerDao.getProvider("999")).thenReturn(null);
        when(consultationRequestDao.getConsults(DEMO_ID))
                .thenReturn(List.of(consultWithNoOrderingProvider()));

        boolean verdict = util.estConsultationVecByDemographic(loggedInInfo, DEMO_NO);

        assertThat(verdict).isTrue();
        assertThat(util.ids).containsExactly("7");
        // The row survives and degrades to "N/A" rather than taking the whole tab down with it.
        assertThat(util.provider).containsExactly("N/A");
        assertThat(util.patient).containsExactly("Doe, Jane");
    }

    @Test
    @DisplayName("should still list the consult when the demographic itself cannot be resolved")
    void shouldListConsult_whenDemographicCannotBeResolved() throws Exception {
        // getConsults no longer joins Demographic either, so an unresolvable demographic reaches
        // the loop the same way a dangling provider does.
        when(demographicManager.getDemographic(any(LoggedInInfo.class), eq(DEMO_ID)))
                .thenReturn(null);
        when(consultationRequestDao.getConsults(DEMO_ID))
                .thenReturn(List.of(consultWithNoOrderingProvider()));

        boolean verdict = util.estConsultationVecByDemographic(loggedInInfo, DEMO_NO);

        assertThat(verdict).isTrue();
        assertThat(util.ids).containsExactly("7");
        assertThat(util.provider).containsExactly("N/A");
        // Blank rather than a fabricated label: the name is genuinely unknown here.
        assertThat(util.patient).containsExactly("");
    }

    @Test
    @DisplayName("should render the MRP's formatted name when the provider record resolves")
    void shouldRenderFormattedName_whenMrpResolves() throws Exception {
        when(demographicManager.getDemographic(any(LoggedInInfo.class), eq(DEMO_ID)))
                .thenReturn(demographicWithMrp("101"));

        Provider mrp = new Provider();
        mrp.setProviderNo("101");
        mrp.setFirstName("Ada");
        mrp.setLastName("Lovelace");
        when(providerDao.getProvider("101")).thenReturn(mrp);
        when(consultationRequestDao.getConsults(DEMO_ID))
                .thenReturn(List.of(consultWithNoOrderingProvider()));

        boolean verdict = util.estConsultationVecByDemographic(loggedInInfo, DEMO_NO);

        assertThat(verdict).isTrue();
        assertThat(util.provider).containsExactly("Lovelace, Ada");
    }
}
