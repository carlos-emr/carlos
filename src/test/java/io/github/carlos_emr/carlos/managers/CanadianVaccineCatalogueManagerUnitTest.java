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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationGTINDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationLotNumberDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMedication;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationLotNumber;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/** The catalogue refresh: download first, then one transaction that replaces the stored catalogue. */
@Tag("unit")
@Tag("fast")
@DisplayName("Vaccine catalogue refresh")
class CanadianVaccineCatalogueManagerUnitTest {

    private final LoggedInInfo loggedInInfo = new LoggedInInfo();
    private CanadianVaccineCatalogueManager manager;
    private PlatformTransactionManager transactionManager;
    private MockedStatic<LogAction> logAction;

    @BeforeEach
    void setUp() {
        manager = new CanadianVaccineCatalogueManager();
        manager.medicationDao = mock(CVCMedicationDao.class);
        manager.lotNumberDao = mock(CVCMedicationLotNumberDao.class);
        manager.gtinDao = mock(CVCMedicationGTINDao.class);
        manager.immunizationDao = mock(CVCImmunizationDao.class);
        manager.userPropertyDao = mock(UserPropertyDAO.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        manager.transactionManager = transactionManager;
        manager.catalogueClient = mock(NationalVaccineCatalogueClient.class);
        logAction = mockStatic(LogAction.class);
    }

    @AfterEach
    void tearDown() {
        logAction.close();
    }

    @Test
    @DisplayName("should replace the stored catalogue in one transaction when the bundle is valid")
    void shouldReplaceCatalogue_whenBundleIsValid() throws IOException {
        when(manager.catalogueClient.fetchBundleJson()).thenReturn(NationalVaccineCatalogueMapperUnitTest.sampleJson());

        manager.update(loggedInInfo);

        InOrder order = inOrder(manager.lotNumberDao, manager.gtinDao, manager.medicationDao, manager.immunizationDao);
        order.verify(manager.lotNumberDao).removeAll();
        order.verify(manager.gtinDao).removeAll();
        order.verify(manager.medicationDao).removeAll();
        order.verify(manager.immunizationDao).removeAll();
        verify(manager.immunizationDao, times(7)).persist(any(CVCImmunization.class));
        verify(manager.medicationDao, times(4)).persist(any(CVCMedication.class));
        verify(manager.lotNumberDao, times(4)).persist(any(CVCMedicationLotNumber.class));
        InOrder medicationFirst = inOrder(manager.medicationDao, manager.lotNumberDao);
        medicationFirst.verify(manager.medicationDao).persist(any(CVCMedication.class));
        medicationFirst.verify(manager.lotNumberDao).persist(any(CVCMedicationLotNumber.class));
        verify(transactionManager, times(1)).commit(any());
        verify(transactionManager, never()).rollback(any());
        ArgumentCaptor<UserProperty> updated = ArgumentCaptor.forClass(UserProperty.class);
        verify(manager.userPropertyDao).saveProp(updated.capture());
        assertThat(updated.getValue().getName()).isEqualTo("cvc.updated");
        logAction.verify(() -> LogAction.addLogSynchronous(eq(loggedInInfo),
                eq("CanadianVaccineCatalogueManager.update"), eq("immunizations=7 medications=4 lotNumbers=4")));
    }

    @Test
    @DisplayName("should keep each vaccine's ISPA flag from the previous catalogue, since V2 has none")
    void shouldKeepIspaFlag_whenVaccineWasIspaBefore() throws IOException {
        CVCImmunization previous = new CVCImmunization();
        previous.setSnomedConceptId("7121000087107");
        previous.setIspa(true);
        when(manager.immunizationDao.findAll(0, 5000)).thenReturn(List.of(previous));
        when(manager.catalogueClient.fetchBundleJson()).thenReturn(NationalVaccineCatalogueMapperUnitTest.sampleJson());

        manager.update(loggedInInfo);

        ArgumentCaptor<CVCImmunization> saved = ArgumentCaptor.forClass(CVCImmunization.class);
        verify(manager.immunizationDao, times(7)).persist(saved.capture());
        assertThat(saved.getAllValues())
                .filteredOn(CVCImmunization::isIspa)
                .extracting(CVCImmunization::getSnomedConceptId)
                .containsExactly("7121000087107");
    }

    @Test
    @DisplayName("should roll back and record nothing when saving the new catalogue fails")
    void shouldRollBack_whenSavingFails() throws IOException {
        when(manager.catalogueClient.fetchBundleJson()).thenReturn(NationalVaccineCatalogueMapperUnitTest.sampleJson());
        doThrow(new IllegalStateException("database unavailable")).when(manager.medicationDao).persist(any(CVCMedication.class));

        assertThatThrownBy(() -> manager.update(loggedInInfo)).isInstanceOf(IllegalStateException.class);

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
        verify(manager.userPropertyDao, never()).saveProp(any());
        logAction.verify(() -> LogAction.addLogSynchronous(any(LoggedInInfo.class), anyString(), anyString()), never());
    }

    @Test
    @DisplayName("should keep the stored catalogue when the download fails")
    void shouldKeepStoredCatalogue_whenDownloadFails() throws IOException {
        when(manager.catalogueClient.fetchBundleJson()).thenThrow(new IOException("National Vaccine Catalogue answered HTTP 503"));

        assertThatThrownBy(() -> manager.update(loggedInInfo)).isInstanceOf(IOException.class);

        verifyNoInteractions(transactionManager, manager.lotNumberDao, manager.gtinDao,
                manager.medicationDao, manager.immunizationDao, manager.userPropertyDao);
    }

    @Test
    @DisplayName("should keep the stored catalogue when the bundle is not valid FHIR")
    void shouldKeepStoredCatalogue_whenBundleIsMalformed() throws IOException {
        when(manager.catalogueClient.fetchBundleJson()).thenReturn("{\"resourceType\":\"Bundle\",\"type\":");

        assertThatThrownBy(() -> manager.update(loggedInInfo))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("could not be read");

        verifyNoInteractions(transactionManager, manager.immunizationDao, manager.medicationDao);
    }

    @Test
    @DisplayName("should keep the stored catalogue when the bundle lacks the vaccine value sets")
    void shouldKeepStoredCatalogue_whenValueSetsMissing() throws IOException {
        when(manager.catalogueClient.fetchBundleJson()).thenReturn("{\"resourceType\":\"Bundle\",\"type\":\"collection\"}");

        assertThatThrownBy(() -> manager.update(loggedInInfo)).isInstanceOf(IOException.class);

        verifyNoInteractions(transactionManager, manager.immunizationDao, manager.medicationDao);
        logAction.verify(() -> LogAction.addLogSynchronous(any(LoggedInInfo.class), anyString(), anyString()), never());
    }

    @Test
    @DisplayName("should report a loaded catalogue once a refresh has been recorded")
    void shouldReportCatalogue_whenUpdatedPropertyExists() {
        when(manager.userPropertyDao.getProp("cvc.updated")).thenReturn(new UserProperty());

        assertThat(manager.hasCatalogue()).isTrue();
    }
}
