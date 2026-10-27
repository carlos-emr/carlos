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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CdsClinicalReadAccessUnitTest extends CarlosUnitTestBase {
    private CdsImportAccessService access;private SecurityInfoManager security;private LoggedInInfo actor;private Security account;
    @BeforeEach void prepare(){
        security=mock(SecurityInfoManager.class);actor=mock(LoggedInInfo.class);account=new Security();account.setSecurityNo(42);account.setProviderNo("999998");
        when(actor.getLoggedInSecurity()).thenReturn(account);when(actor.getLoggedInProviderNo()).thenReturn("999998");
        EntityManager em=mock(EntityManager.class);when(em.find(Security.class,42)).thenReturn(account);
        Provider provider=new Provider();provider.setProviderNo("999998");provider.setStatus("1");when(em.find(Provider.class,"999998")).thenReturn(provider);
        access=new CdsImportAccessService(security,mock(CdsImportConfigurationService.class),mock(CdsCourseService.class),mock(CdsProgramContextService.class));ReflectionTestUtils.setField(access,"entityManager",em);
        when(security.hasPrivilegeReadOnly(any(),eq("_demographic"),eq("r"),eq("123"))).thenReturn(true);
        when(security.hasPrivilegeReadOnly(any(),eq("_rx"),eq("r"),eq("123"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(),eq(123))).thenReturn(true);
    }
    @Test void shouldPermitChartReader_withoutImportOperatorWritePrivilege(){
        assertThatCode(()->access.requireClinicalRead(actor,123,"MedicationsAndTreatments")).doesNotThrowAnyException();
        verify(security,never()).hasPrivilegeReadOnly(any(),anyString(),eq("w"),any());
    }
    @Test void shouldRefuseForgedPatient_whenPatientRecordAccessFails(){
        when(security.isAllowedAccessToPatientRecord(any(),eq(123))).thenReturn(false);
        assertThatThrownBy(()->access.requireClinicalRead(actor,123,"MedicationsAndTreatments")).isInstanceOf(SecurityException.class);
    }
    @Test void shouldRefuseCategoryWithoutItsDomainReadPrivilege(){
        assertThatThrownBy(()->access.requireClinicalRead(actor,123,"LaboratoryResults")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->access.requireClinicalRead(actor,123,"../../etc/passwd")).hasMessage("IMPORT_CATEGORY_INVALID");
    }
    @Test void shouldRecheckCurrentAccountProviderAndExpiration(){
        when(actor.getLoggedInProviderNo()).thenReturn("999997");
        assertThatThrownBy(()->access.requireClinicalRead(actor,123,"MedicationsAndTreatments")).isInstanceOf(SecurityException.class);
        when(actor.getLoggedInProviderNo()).thenReturn("999998");account.setBExpireset(1);account.setDateExpiredate(new Date(0));
        assertThatThrownBy(()->access.requireClinicalRead(actor,123,"MedicationsAndTreatments")).isInstanceOf(SecurityException.class);
    }
}
