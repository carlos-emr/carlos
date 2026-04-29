/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONDiskNameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONHeaderDao;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONRepoDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link BillingONClaimPersister}, the rename of legacy
 * {@code JdbcBillingClaimImpl} that writes one OHIP claim header + items +
 * audit-trail rows.
 *
 * <p>Coverage scope:</p>
 * <ul>
 *   <li>Happy path: {@code addOneClaimHeaderRecord}, {@code addItemRecord},
 *       and {@code addItemPaymentRecord} all populate entities and call
 *       {@code persist} once per row.</li>
 *   <li>Date parse swallow at {@code addOneClaimHeaderRecord:154} (admission
 *       date) and {@code addItemRecord:225} (service date) — documents the
 *       legacy preservation: a malformed date string is silently dropped and
 *       the entity persists with whatever default the field holds (admission
 *       date stays {@code null}). This is a known regression target tracked
 *       outside this PR; the test exists so a future fix has an explicit
 *       failing-spec to flip.</li>
 *   <li>Legacy {@code addItemRecord} return-value oddity: the boolean is
 *       initialised to {@code true} and never flipped, so the caller's
 *       {@code if (!ret)} guard is dead. The test pins the contract.</li>
 * </ul>
 *
 * @since 2026-04-29
 */
@DisplayName("BillingONClaimPersister")
@Tag("unit")
@Tag("billing")
class BillingONClaimPersisterUnitTest extends CarlosUnitTestBase {

    @Mock private BillingONHeaderDao headerDao;
    @Mock private BillingONCHeader1Dao cheaderDao;
    @Mock private BillingONItemDao itemDao;
    @Mock private BillingONExtDao extDao;
    @Mock private BillingONDiskNameDao diskNameDao;
    @Mock private BillingONFilenameDao filenameDao;
    @Mock private BillingONRepoDao repoDao;
    @Mock private BillingOnItemPaymentDao billOnItemPaymentDao;
    @Mock private BillingOnTransactionDao billTransDao;
    @Mock private BillingONPaymentDao billingONPaymentDao;
    @Mock private BillingPaymentTypeDao billingPaymentTypeDao;

    private BillingONClaimPersister persister;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        persister = new BillingONClaimPersister(
                headerDao, cheaderDao, itemDao, extDao,
                diskNameDao, filenameDao, repoDao,
                billOnItemPaymentDao, billTransDao,
                billingONPaymentDao, billingPaymentTypeDao);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Nested
    @DisplayName("addOneClaimHeaderRecord")
    class AddOneClaimHeaderRecord {

        @Test
        void shouldPersistHeaderEntity_andReturnGeneratedId() throws Exception {
            BillingClaimHeaderDto dto = headerDto();
            dto.setBilling_date("2026-04-28");
            dto.setBilling_time("12:34:56");
            dto.setAdmission_date("2026-04-15");

            // The DAO assigns the generated id during persist; simulate that
            // by stamping it on the entity Mockito sees.
            ArgumentCaptor<BillingONCHeader1> captor = ArgumentCaptor.forClass(BillingONCHeader1.class);
            doAssignId(captor, 4242);

            int generatedId = persister.addOneClaimHeaderRecord(dto);

            assertThat(generatedId).isEqualTo(4242);
            BillingONCHeader1 persisted = captor.getValue();
            assertThat(persisted.getHin()).isEqualTo(dto.getHin());
            assertThat(persisted.getDemographicNo()).isEqualTo(7);
            assertThat(persisted.getProviderNo()).isEqualTo("999998");
            assertThat(persisted.getTotal()).isEqualByComparingTo("1.50");
            assertThat(persisted.getAdmissionDate())
                    .isEqualTo(new SimpleDateFormat("yyyy-MM-dd").parse("2026-04-15"));
            assertThat(persisted.getStatus()).isEqualTo("O");
            verify(cheaderDao, times(1)).persist(any(BillingONCHeader1.class));
        }

        @Test
        void shouldDefaultMoneyFields_whenTotalAndPaidAreNull() throws Exception {
            BillingClaimHeaderDto dto = headerDto();
            dto.setTotal(null);
            dto.setPaid(null);

            ArgumentCaptor<BillingONCHeader1> captor = ArgumentCaptor.forClass(BillingONCHeader1.class);
            doAssignId(captor, 1);

            persister.addOneClaimHeaderRecord(dto);

            BillingONCHeader1 persisted = captor.getValue();
            assertThat(persisted.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.getPaid()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldSwallowMalformedAdmissionDate_andLeaveFieldNull() throws Exception {
            // KNOWN LEGACY BEHAVIOUR — preserved verbatim from
            // JdbcBillingClaimImpl. A malformed admission_date silently
            // drops to null on the persisted entity instead of failing
            // fast with BillingValidationException. This test pins the
            // current contract; a future fix replacing the empty catch
            // at BillingONClaimPersister:154 should flip this assertion
            // to expect a thrown exception.
            BillingClaimHeaderDto dto = headerDto();
            dto.setAdmission_date("not-a-date");

            ArgumentCaptor<BillingONCHeader1> captor = ArgumentCaptor.forClass(BillingONCHeader1.class);
            doAssignId(captor, 1);

            persister.addOneClaimHeaderRecord(dto);

            assertThat(captor.getValue().getAdmissionDate()).isNull();
            verify(cheaderDao, times(1)).persist(any(BillingONCHeader1.class));
        }
    }

    @Nested
    @DisplayName("addItemRecord")
    class AddItemRecord {

        @Test
        void shouldPersistOneItemPerListEntry_withCh1IdLink() {
            BillingClaimItemDto a = itemDto("A001A", "2026-04-28");
            BillingClaimItemDto b = itemDto("B002B", "2026-04-28");
            doAssignItemId(99);

            boolean ret = persister.addItemRecord(new ArrayList<>(List.of(a, b)), 4242);

            assertThat(ret).isTrue();
            verify(itemDao, times(2)).persist(any(BillingONItem.class));
        }

        @Test
        void shouldSwallowMalformedServiceDate_andLeaveFieldNull() {
            // KNOWN LEGACY BEHAVIOUR — see JdbcBillingClaimImpl preservation
            // note above. The empty catch at BillingONClaimPersister:225
            // silently drops malformed service_date; the item still persists.
            BillingClaimItemDto bad = itemDto("A001A", "not-a-date");
            doAssignItemId(99);

            ArgumentCaptor<BillingONItem> captor = ArgumentCaptor.forClass(BillingONItem.class);
            persister.addItemRecord(new ArrayList<>(List.of(bad)), 4242);
            verify(itemDao).persist(captor.capture());
            assertThat(captor.getValue().getServiceDate()).isNull();
        }

        @Test
        void shouldReturnTrue_evenWhenItemListEmpty() {
            // The retval flag is initialised true and never flipped, so an
            // empty list also returns true. Caller's `if (!ret)` is dead;
            // pinning this contract makes future refactors explicit.
            boolean ret = persister.addItemRecord(new ArrayList<BillingClaimItemDto>(), 4242);

            assertThat(ret).isTrue();
            verify(itemDao, never()).persist(any(BillingONItem.class));
        }
    }

    @Nested
    @DisplayName("addItemPaymentRecord")
    class AddItemPaymentRecord {

        @Test
        void shouldPersistOneItemPaymentPerEntry_withMoneyFieldsParsed() {
            BillingClaimItemDto a = itemDto("A001A", "2026-04-28");
            a.setId("11");
            a.setDiscount("1.25");
            a.setPaid("33.70");
            a.setRefund("0.00");

            doAssignItemPaymentId(55);

            ArgumentCaptor<BillingOnItemPayment> captor =
                    ArgumentCaptor.forClass(BillingOnItemPayment.class);

            persister.addItemPaymentRecord(new ArrayList<>(List.of(a)), 4242, 7, 1);

            verify(billOnItemPaymentDao).persist(captor.capture());
            BillingOnItemPayment persisted = captor.getValue();
            assertThat(persisted.getCh1Id()).isEqualTo(4242);
            assertThat(persisted.getBillingOnPaymentId()).isEqualTo(7);
            assertThat(persisted.getDiscount()).isEqualByComparingTo("1.25");
            assertThat(persisted.getPaid()).isEqualByComparingTo("33.70");
            assertThat(persisted.getRefund()).isEqualByComparingTo("0.00");
        }

        @Test
        void shouldZeroMoneyFields_whenInputsAreMalformed() {
            // BillingMoney.amountOrZero is the consolidated swallowing parser
            // (see BillingONClaimPersister:247-250). Malformed money inputs
            // collapse to zero on the audit-trail row — preserved legacy
            // behaviour from JdbcBillingClaimImpl.
            BillingClaimItemDto a = itemDto("A001A", "2026-04-28");
            a.setId("11");
            a.setDiscount("not-a-number");
            a.setPaid("not-a-number");
            a.setRefund("not-a-number");

            doAssignItemPaymentId(55);

            ArgumentCaptor<BillingOnItemPayment> captor =
                    ArgumentCaptor.forClass(BillingOnItemPayment.class);

            persister.addItemPaymentRecord(new ArrayList<>(List.of(a)), 4242, 7, 1);

            verify(billOnItemPaymentDao).persist(captor.capture());
            BillingOnItemPayment persisted = captor.getValue();
            assertThat(persisted.getDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.getPaid()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.getRefund()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ---- helpers ---------------------------------------------------------

    private static BillingClaimHeaderDto headerDto() {
        BillingClaimHeaderDto h = new BillingClaimHeaderDto();
        h.setHin("1234567890");
        h.setVer("AB");
        h.setDob("1980-01-15");
        h.setPay_program("HCP");
        h.setPayee("P");
        h.setRef_num("");
        h.setFacilty_num("0000");
        h.setRef_lab_num("");
        h.setMan_review("");
        h.setLocation("0000");
        h.setDemographic_no("7");
        h.setProviderNo("999998");
        h.setAppointment_no(null);
        h.setDemographic_name("Doe,Jane");
        h.setSex("F");
        h.setProvince("ON");
        h.setBilling_date("");
        h.setBilling_time("");
        h.setTotal("1.50");
        h.setPaid("0.00");
        h.setStatus("O");
        h.setComment("");
        h.setVisittype("00");
        h.setProvider_ohip_no("999998");
        h.setProvider_rma_no("");
        h.setApptProvider_no("999998");
        h.setAsstProvider_no("");
        h.setCreator("999998");
        h.setClinic("");
        h.setTransc_id("");
        h.setRec_id("");
        h.setAdmission_date("");
        return h;
    }

    private static BillingClaimItemDto itemDto(String code, String serviceDate) {
        BillingClaimItemDto item = new BillingClaimItemDto();
        item.setTransc_id("");
        item.setRec_id("");
        item.setService_code(code);
        item.setFee("33.70");
        item.setSer_num("1");
        item.setService_date(serviceDate);
        item.setDx("401");
        item.setDx1("");
        item.setDx2("");
        item.setStatus("O");
        return item;
    }

    /**
     * BillingONCHeader1 and BillingONItem have no public {@code setId} —
     * the JPA provider sets the {@code @GeneratedValue} field via reflection
     * after INSERT. Tests have to do the same to simulate that handoff.
     */
    private static void reflectSetIdField(Object entity, int id) {
        try {
            java.lang.reflect.Field f = findField(entity.getClass(), "id");
            f.setAccessible(true);
            if (f.getType() == int.class) {
                f.setInt(entity, id);
            } else {
                f.set(entity, Integer.valueOf(id));
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Test helper failed to set id field", e);
        }
    }

    private static java.lang.reflect.Field findField(Class<?> c, String name)
            throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { return k.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { /* climb */ }
        }
        throw new NoSuchFieldException(name);
    }

    /** Stub the persist call so the captured entity ends up with the given id. */
    private void doAssignId(ArgumentCaptor<BillingONCHeader1> captor, int id) {
        org.mockito.Mockito.doAnswer(inv -> {
            BillingONCHeader1 entity = inv.getArgument(0);
            reflectSetIdField(entity, id);
            return null;
        }).when(cheaderDao).persist(captor.capture());
    }

    private void doAssignItemId(int id) {
        org.mockito.Mockito.doAnswer(inv -> {
            BillingONItem entity = inv.getArgument(0);
            reflectSetIdField(entity, id);
            return null;
        }).when(itemDao).persist(any(BillingONItem.class));
    }

    private void doAssignItemPaymentId(int id) {
        org.mockito.Mockito.doAnswer(inv -> {
            BillingOnItemPayment entity = inv.getArgument(0);
            entity.setId(id);
            return null;
        }).when(billOnItemPaymentDao).persist(any(BillingOnItemPayment.class));
    }
}
