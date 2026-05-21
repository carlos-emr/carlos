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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.ContactDao;
import io.github.carlos_emr.carlos.commn.dao.ContactSpecialtyDao;
import io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalContactDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.PharmacyManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Security regression coverage for {@link Contact2Action}.
 *
 * @since 2026-05-21
 */
@DisplayName("Contact2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class Contact2ActionTest extends CarlosWebTestBase {

    private static final String DEMOGRAPHIC_NO = "12345";

    @Mock private ContactDao mockContactDao;
    @Mock private ProfessionalContactDao mockProfessionalContactDao;
    @Mock private DemographicContactDao mockDemographicContactDao;
    @Mock private DemographicDao mockDemographicDao;
    @Mock private DemographicManager mockDemographicManager;
    @Mock private ProviderDao mockProviderDao;
    @Mock private ProfessionalSpecialistDao mockProfessionalSpecialistDao;
    @Mock private ContactSpecialtyDao mockContactSpecialtyDao;
    @Mock private CtlRelationshipsDao mockCtlRelationshipsDao;
    @Mock private PharmacyManager mockPharmacyManager;

    @Test
    @DisplayName("should reject ajax remove when patient record ACL denies read")
    void shouldRejectAjaxRemove_whenPatientRecordAclDeniesRead() {
        registerContactActionBeans();
        addRequestParameter("demographic_no", DEMOGRAPHIC_NO);
        addRequestParameter("postMethod", "ajax");
        when(mockSecurityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("r"), eq(DEMOGRAPHIC_NO)))
                .thenReturn(false);
        Contact2Action action = new Contact2Action();

        assertThatThrownBy(action::removeContact)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("missing required sec object (_demographic)");

        verify(mockSecurityInfoManager).hasPrivilege(
                any(LoggedInInfo.class), eq("_demographic"), eq("r"), eq(DEMOGRAPHIC_NO));
        verifyNoInteractions(mockDemographicContactDao);
    }

    private void registerContactActionBeans() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(ContactDao.class, mockContactDao);
        replaceSpringUtilsBean(ProfessionalContactDao.class, mockProfessionalContactDao);
        replaceSpringUtilsBean(DemographicContactDao.class, mockDemographicContactDao);
        replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);
        replaceSpringUtilsBean(DemographicManager.class, mockDemographicManager);
        replaceSpringUtilsBean(ProviderDao.class, mockProviderDao);
        replaceSpringUtilsBean(ProfessionalSpecialistDao.class, mockProfessionalSpecialistDao);
        replaceSpringUtilsBean(ContactSpecialtyDao.class, mockContactSpecialtyDao);
        replaceSpringUtilsBean(CtlRelationshipsDao.class, mockCtlRelationshipsDao);
        replaceSpringUtilsBean(PharmacyManager.class, mockPharmacyManager);
    }
}
