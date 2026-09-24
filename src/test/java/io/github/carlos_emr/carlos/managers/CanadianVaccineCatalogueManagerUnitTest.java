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
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.CVCImmunizationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationGTINDao;
import io.github.carlos_emr.carlos.commn.dao.CVCMedicationLotNumberDao;
import io.github.carlos_emr.carlos.commn.dao.LookupListItemDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.CVCImmunization;
import io.github.carlos_emr.carlos.commn.model.CVCMedication;
import io.github.carlos_emr.carlos.commn.model.CVCMedicationLotNumber;
import io.github.carlos_emr.carlos.commn.model.LookupList;
import io.github.carlos_emr.carlos.commn.model.LookupListItem;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.prevention.nvc.NvcBundleException;
import io.github.carlos_emr.carlos.prevention.nvc.NvcCatalogue;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for the NVC update path of {@link CanadianVaccineCatalogueManager}: the download is
 * stubbed with a slice of the real NVC bundle, DAOs are mocks, and the transaction manager is a
 * mock so the tests can prove nothing is written outside it or before the bundle parses.
 */
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("prevention")
@DisplayName("CanadianVaccineCatalogueManager NVC update")
class CanadianVaccineCatalogueManagerUnitTest extends CarlosUnitTestBase {

    private static final String FIXTURE = "/prevention/nvc/nvc-bundle-sample.json";

    private final CVCMedicationDao medicationDao = mock(CVCMedicationDao.class);
    private final CVCMedicationLotNumberDao lotNumberDao = mock(CVCMedicationLotNumberDao.class);
    private final CVCMedicationGTINDao gtinDao = mock(CVCMedicationGTINDao.class);
    private final CVCImmunizationDao immunizationDao = mock(CVCImmunizationDao.class);
    private final UserPropertyDAO userPropertyDao = mock(UserPropertyDAO.class);
    private final LookupListManager lookupListManager = mock(LookupListManager.class);
    private final LookupListItemDao lookupListItemDao = mock(LookupListItemDao.class);
    private final SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final LoggedInInfo admin = mock(LoggedInInfo.class);

    private CanadianVaccineCatalogueManager manager;
    private String fixture;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream(FIXTURE)) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        when(securityInfoManager.hasPrivilege(admin, "_admin", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        manager = spy(new CanadianVaccineCatalogueManager(medicationDao, lotNumberDao, gtinDao, immunizationDao,
                userPropertyDao, lookupListManager, lookupListItemDao, securityInfoManager, transactionManager));
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @BeforeEach
        void stubNewLookupLists() {
            when(lookupListManager.addLookupList(eq(admin), any())).thenAnswer(call -> {
                LookupList list = call.getArgument(1);
                list.setId("AnatomicalSite".equals(list.getName()) ? 11 : 12);
                return list;
            });
        }

        @Test
        void shouldClearChildTablesFirst_whenReplacingCatalogue() throws Exception {
            doReturn(fixture).when(manager).fetchBundleJson(anyString());

            manager.update(admin);

            InOrder order = inOrder(transactionManager, lotNumberDao, gtinDao, medicationDao, immunizationDao);
            order.verify(transactionManager).getTransaction(any());
            order.verify(lotNumberDao).removeAll();
            order.verify(gtinDao).removeAll();
            order.verify(medicationDao).removeAll();
            order.verify(immunizationDao).removeAll();
            order.verify(transactionManager).commit(any());
        }

        @Test
        void shouldPersistGenericsTradenamesProductsAndLots_fromBundle() throws Exception {
            doReturn(fixture).when(manager).fetchBundleJson(anyString());

            NvcCatalogue installed = manager.update(admin);

            ArgumentCaptor<CVCImmunization> immunizations = ArgumentCaptor.forClass(CVCImmunization.class);
            verify(immunizationDao, times(4)).persist(immunizations.capture());
            assertThat(immunizations.getAllValues())
                    .extracting(CVCImmunization::getSnomedConceptId, CVCImmunization::isGeneric,
                            CVCImmunization::getParentConceptId)
                    .containsExactly(
                            tuple("33581000087104", true, null),
                            tuple("7691000087100", true, null),
                            tuple("51511000087105", false, "33581000087104"),
                            tuple("19291000087108", false, "7691000087100"));

            ArgumentCaptor<CVCMedication> medications = ArgumentCaptor.forClass(CVCMedication.class);
            verify(medicationDao, times(2)).persist(medications.capture());
            CVCMedication comirnaty = medications.getAllValues().get(0);
            assertThat(comirnaty.getSnomedCode()).isEqualTo("51511000087105");
            assertThat(comirnaty.getDin()).isEqualTo("02541866");
            assertThat(comirnaty.getManufacturerDisplay()).isEqualTo("BIONTECH MANUFACTURING GMBH");
            assertThat(comirnaty.isBrand()).isTrue();

            ArgumentCaptor<CVCMedicationLotNumber> lots = ArgumentCaptor.forClass(CVCMedicationLotNumber.class);
            verify(lotNumberDao, times(2)).persist(lots.capture());
            assertThat(lots.getAllValues()).extracting(CVCMedicationLotNumber::getLotNumber)
                    .containsExactly("HH6775", "HL2950");
            assertThat(lots.getAllValues()).allSatisfy(lot -> {
                assertThat(lot.getMedication()).isSameAs(comirnaty);
                assertThat(lot.getExpiryDate()).isNotNull();
            });
            assertThat(lots.getAllValues().get(0).getExpiryDate().toString()).isEqualTo("2024-10-31");

            assertThat(installed.version()).isEqualTo("1.130");
        }

        @Test
        void shouldRecordInstallBookkeeping_afterReplacingCatalogue() throws Exception {
            doReturn(fixture).when(manager).fetchBundleJson(anyString());

            manager.update(admin);

            verify(userPropertyDao).saveProp(eq("cvc.updated"), anyString());
            verify(userPropertyDao).saveProp("cvc.version", "1.130");
            verify(userPropertyDao).saveProp(eq("cvc.firstdate"), anyString());
            logActionMock.verify(() -> LogAction.addLogSynchronous(
                    eq(admin), eq("CanadianVaccineCatalogueManager.update"), anyString()), times(1));
        }

        @Test
        void shouldKeepFirstInstallDate_whenAlreadyRecorded() throws Exception {
            doReturn(fixture).when(manager).fetchBundleJson(anyString());
            when(userPropertyDao.getProp("cvc.firstdate")).thenReturn(new UserProperty());

            manager.update(admin);

            verify(userPropertyDao, never()).saveProp(eq("cvc.firstdate"), anyString());
        }

        @Test
        void shouldCreateLookupLists_whenAbsent() throws Exception {
            doReturn(fixture).when(manager).fetchBundleJson(anyString());

            manager.update(admin);

            ArgumentCaptor<LookupListItem> items = ArgumentCaptor.forClass(LookupListItem.class);
            verify(lookupListManager, times(4)).addLookupListItem(eq(admin), items.capture());
            assertThat(items.getAllValues())
                    .extracting(LookupListItem::getLookupListId, LookupListItem::getValue, LookupListItem::getLabel)
                    .containsExactly(
                            tuple(11, "1217006009", "Right vastus lateralis muscle"),
                            tuple(11, "1217007000", "Left vastus lateralis muscle"),
                            tuple(12, "718329006", "Infiltrate: INFL"),
                            tuple(12, "372464004", "Intradermal: ID"));
        }

        @Test
        void shouldReactivateMatchingItemsAndRetireOthers_whenLookupListExists() throws Exception {
            doReturn(fixture).when(manager).fetchBundleJson(anyString());
            LookupList sites = new LookupList();
            sites.setId(21);
            sites.setName("AnatomicalSite");
            when(lookupListManager.findLookupListByName(admin, "AnatomicalSite")).thenReturn(sites);
            LookupListItem previouslyRetired = item(31, 21, "1217006009", "Old label", false, "NVC");
            LookupListItem noLongerPublished = item(32, 21, "999999", "Withdrawn site", true, "NVC");
            LookupListItem addedByClinic = item(33, 21, "local-1", "Clinic deltoid site", true, "999998");
            when(lookupListItemDao.findByLookupListId(21, true)).thenReturn(new ArrayList<>(List.of(noLongerPublished, addedByClinic)));
            when(lookupListItemDao.findByLookupListId(21, false)).thenReturn(new ArrayList<>(List.of(previouslyRetired)));

            manager.update(admin);

            assertThat(previouslyRetired.isActive()).isTrue();
            assertThat(previouslyRetired.getLabel()).isEqualTo("Right vastus lateralis muscle");
            assertThat(noLongerPublished.isActive()).isFalse();
            // A value an administrator added locally is not NVC's to retire.
            assertThat(addedByClinic.isActive()).isTrue();
            verify(lookupListManager, never()).updateLookupListItem(admin, addedByClinic);
            verify(lookupListManager).updateLookupListItem(admin, previouslyRetired);
            verify(lookupListManager).updateLookupListItem(admin, noLongerPublished);
            // Only the site that did not exist yet is inserted; no duplicate rows accumulate.
            verify(lookupListManager).addLookupListItem(eq(admin),
                    argThat(i -> "1217007000".equals(i.getValue())));
            verify(lookupListManager, never()).addLookupListItem(eq(admin),
                    argThat(i -> "1217006009".equals(i.getValue())));
        }

        @Test
        void shouldLeaveClinicItem_whenItSharesAnNvcCode() throws Exception {
            doReturn(fixture).when(manager).fetchBundleJson(anyString());
            LookupList sites = new LookupList();
            sites.setId(21);
            sites.setName("AnatomicalSite");
            when(lookupListManager.findLookupListByName(admin, "AnatomicalSite")).thenReturn(sites);
            LookupListItem clinicRetired = item(41, 21, "1217006009", "Clinic label", false, "999998");
            when(lookupListItemDao.findByLookupListId(21, true)).thenReturn(new ArrayList<>());
            when(lookupListItemDao.findByLookupListId(21, false)).thenReturn(new ArrayList<>(List.of(clinicRetired)));

            manager.update(admin);

            assertThat(clinicRetired.isActive()).isFalse();
            assertThat(clinicRetired.getLabel()).isEqualTo("Clinic label");
            verify(lookupListManager, never()).updateLookupListItem(admin, clinicRetired);
            // No NVC duplicate is inserted beside the clinic's item for the same code.
            verify(lookupListManager, never()).addLookupListItem(eq(admin),
                    argThat(i -> "1217006009".equals(i.getValue())));
        }

        @Test
        void shouldLeaveCatalogueUntouched_whenDownloadFails() throws Exception {
            doThrow(new IOException("connect timed out")).when(manager).fetchBundleJson(anyString());

            assertThatThrownBy(() -> manager.update(admin)).isInstanceOf(IOException.class);

            verifyNoInteractions(transactionManager, medicationDao, lotNumberDao, gtinDao, immunizationDao,
                    userPropertyDao, lookupListManager, lookupListItemDao);
        }

        @Test
        void shouldLeaveCatalogueUntouched_whenBundleIsUnusable() throws Exception {
            doReturn("{\"resourceType\":\"OperationOutcome\"}").when(manager).fetchBundleJson(anyString());

            assertThatThrownBy(() -> manager.update(admin)).isInstanceOf(NvcBundleException.class);

            verifyNoInteractions(transactionManager, medicationDao, lotNumberDao, gtinDao, immunizationDao);
        }

        @Test
        void shouldRollBack_whenAWriteFails() throws Exception {
            doReturn(fixture).when(manager).fetchBundleJson(anyString());
            doThrow(new IllegalStateException("constraint violation")).when(medicationDao).persist(any());

            assertThatThrownBy(() -> manager.update(admin)).isInstanceOf(IllegalStateException.class);

            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
        }

        @Test
        void shouldRejectCaller_whenMissingAdminWrite() throws Exception {
            LoggedInInfo readOnly = mock(LoggedInInfo.class);

            assertThatThrownBy(() -> manager.update(readOnly))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin)");

            verify(manager, never()).fetchBundleJson(anyString());
        }

        @Test
        void shouldSerializeUpdates_soAStaleDownloadCannotCommitLast() throws Exception {
            CountDownLatch firstFetching = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicInteger concurrentFetches = new AtomicInteger();
            AtomicInteger maxConcurrent = new AtomicInteger();
            doAnswer(call -> {
                maxConcurrent.accumulateAndGet(concurrentFetches.incrementAndGet(), Math::max);
                firstFetching.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS);
                concurrentFetches.decrementAndGet();
                return fixture;
            }).when(manager).fetchBundleJson(anyString());

            Thread first = new Thread(this::updateQuietly);
            Thread second = new Thread(this::updateQuietly);
            first.start();
            firstFetching.await(5, TimeUnit.SECONDS);
            second.start();
            Thread.sleep(200);
            releaseFirst.countDown();
            first.join(5000);
            second.join(5000);

            assertThat(maxConcurrent.get()).isEqualTo(1);
            verify(manager, times(2)).fetchBundleJson(anyString());
            verify(transactionManager, times(2)).commit(any());
        }

        private void updateQuietly() {
            try {
                manager.update(admin);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Test
        void shouldRequestNvcBundlePath_fromConfiguredBase() throws Exception {
            doReturn(fixture).when(manager).fetchBundleJson(anyString());

            manager.update(admin);

            verify(manager).fetchBundleJson("https://nvc-cnv.canada.ca/fhir/v2/Bundle/NVC");
        }
    }

    @Nested
    @DisplayName("fetchBundleJson()")
    class Fetch {

        @Test
        void shouldSendSingleAcceptMediaType_becauseNvcRejectsLists() {
            HttpGet request = CanadianVaccineCatalogueManager.bundleRequest(
                    URI.create("https://nvc-cnv.canada.ca/fhir/v2/Bundle/NVC"));

            // A packaged install got HTTP 406 for "application/fhir+json, application/json".
            assertThat(request.getHeaders("Accept")).hasSize(1);
            assertThat(request.getFirstHeader("Accept").getValue()).isEqualTo("application/fhir+json");
            assertThat(request.getMethod()).isEqualTo("GET");
        }

        @Test
        void shouldRefuseCleartextUrl_beforeConnecting() {
            assertThatThrownBy(() -> manager.fetchBundleJson("http://nvc.example.invalid/Bundle/NVC"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("https");
        }
    }

    @Nested
    @DisplayName("getCVCURL()")
    class CatalogueUrl {

        private String previous;

        @BeforeEach
        void rememberProperty() {
            previous = CarlosProperties.getInstance().getProperty("cvc.url");
        }

        @AfterEach
        void restoreProperty() {
            if (previous == null) {
                CarlosProperties.getInstance().remove("cvc.url");
            } else {
                CarlosProperties.getInstance().setProperty("cvc.url", previous);
            }
        }

        @Test
        void shouldUseNvcDefault_whenPropertyBlank() {
            CarlosProperties.getInstance().setProperty("cvc.url", "  ");

            assertThat(CanadianVaccineCatalogueManager.getCVCURL()).isEqualTo("https://nvc-cnv.canada.ca/fhir/v2");
        }

        @Test
        void shouldStripTrailingSlashes_fromConfiguredMirror() {
            CarlosProperties.getInstance().setProperty("cvc.url", "https://mirror.example.test/fhir/v2//");

            assertThat(CanadianVaccineCatalogueManager.getCVCURL()).isEqualTo("https://mirror.example.test/fhir/v2");
        }
    }

    @Nested
    @DisplayName("catalogue status")
    class Status {

        @Test
        void shouldReportNotInstalled_whenNoUpdateRecorded() {
            assertThat(manager.isCatalogueInstalled()).isFalse();
            assertThat(manager.getLastUpdated()).isNull();
        }

        @Test
        void shouldReportInstalled_whenUpdateRecorded() {
            UserProperty updated = new UserProperty();
            updated.setValue("2026-09-24 10:15");
            when(userPropertyDao.getProp("cvc.updated")).thenReturn(updated);

            assertThat(manager.isCatalogueInstalled()).isTrue();
            assertThat(manager.getLastUpdated()).isEqualTo("2026-09-24 10:15");
        }
    }

    private static LookupListItem item(int id, int listId, String value, String label, boolean active,
                                       String createdBy) {
        LookupListItem item = new LookupListItem();
        item.setId(id);
        item.setCreatedBy(createdBy);
        item.setLookupListId(listId);
        item.setValue(value);
        item.setLabel(label);
        item.setActive(active);
        return item;
    }
}
