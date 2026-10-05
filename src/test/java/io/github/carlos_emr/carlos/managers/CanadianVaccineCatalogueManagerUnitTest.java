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
import static org.assertj.core.api.Assertions.tuple;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

import io.github.carlos_emr.carlos.commn.dao.AbstractDao;
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
        when(manager.immunizationDao.findAll(0, AbstractDao.MAX_LIST_RETURN_SIZE)).thenReturn(List.of(previous));
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
    @DisplayName("should leave inactive generics, which have no picklist name, out of catalogue search")
    void shouldOmitUnrecordableGeneric_whenSearching() {
        CVCImmunization inactiveGeneric = new CVCImmunization();
        inactiveGeneric.setSnomedConceptId("108729007");
        inactiveGeneric.setGeneric(true);
        CVCImmunization activeGeneric = new CVCImmunization();
        activeGeneric.setSnomedConceptId("7121000087107");
        activeGeneric.setGeneric(true);
        activeGeneric.setPicklistName("Meningococcal conjugate A + C + Y + W vaccine");
        when(manager.immunizationDao.query("vaccine", true, true)).thenReturn(List.of(inactiveGeneric, activeGeneric));

        List<CVCImmunization> found = manager.query("vaccine", true, true, false, false, null);

        assertThat(found).extracting(CVCImmunization::getSnomedConceptId).containsExactly("7121000087107");
    }

    @Test
    @DisplayName("should keep a generic's earlier type name when NVC rewords or retires it, so its records keep their type")
    void shouldKeepTypeNames_whenCatalogueRenamesOrRetiresAGeneric() throws IOException {
        CVCImmunization renamed = generic("7121000087107", "Meningococcal ACYW (old wording)");
        CVCImmunization retired = generic("999999", "Discontinued vaccine");
        when(manager.immunizationDao.findAllGeneric()).thenReturn(List.of(renamed, retired));
        when(manager.catalogueClient.fetchBundleJson()).thenReturn(NationalVaccineCatalogueMapperUnitTest.sampleJson());

        manager.update(loggedInInfo);

        ArgumentCaptor<CVCImmunization> saved = ArgumentCaptor.forClass(CVCImmunization.class);
        verify(manager.immunizationDao, times(8)).persist(saved.capture());
        assertThat(saved.getAllValues())
                .filteredOn(i -> i.isGeneric() && i.getPicklistName() != null)
                .extracting(CVCImmunization::getSnomedConceptId, CVCImmunization::getPicklistName)
                .contains(tuple("7121000087107", "Meningococcal ACYW (old wording)"),
                        tuple("999999", "Discontinued vaccine"),
                        tuple("7691000087100", "Influenza trivalent vaccine"));
    }

    @Test
    @DisplayName("should keep two vaccines apart when a new name repeats one kept from before")
    void shouldAddCode_whenNewTypeNameRepeatsAKeptOne() {
        CVCImmunization fresh = generic("222", "Shared name");
        Map<String, CVCImmunization> before = new LinkedHashMap<>();
        before.put("111", generic("111", "Shared name"));

        List<CVCImmunization> result = CanadianVaccineCatalogueManager.keepTypeNames(List.of(fresh), before);

        assertThat(result).extracting(CVCImmunization::getSnomedConceptId, CVCImmunization::getPicklistName)
                .containsExactlyInAnyOrder(tuple("222", "Shared name (222)"), tuple("111", "Shared name"));
    }

    @Test
    @DisplayName("should leave out of search a brand with no generic and a generic with no type name")
    void shouldOmitUnrecordableResults_whenSearching() {
        CVCImmunization orphanBrand = new CVCImmunization();
        orphanBrand.setSnomedConceptId("555");
        orphanBrand.setGeneric(false);
        CVCImmunization brand = new CVCImmunization();
        brand.setSnomedConceptId("556");
        brand.setGeneric(false);
        brand.setParentConceptId("7121000087107");
        when(manager.immunizationDao.query("x", true, true)).thenReturn(List.of(orphanBrand, brand));

        assertThat(manager.query("x", true, true, false, false, null))
                .extracting(CVCImmunization::getSnomedConceptId).containsExactly("556");
    }

    private static CVCImmunization generic(String code, String typeName) {
        CVCImmunization generic = new CVCImmunization();
        generic.setSnomedConceptId(code);
        generic.setDisplayName(typeName);
        generic.setPicklistName(typeName);
        generic.setGeneric(true);
        return generic;
    }
}
