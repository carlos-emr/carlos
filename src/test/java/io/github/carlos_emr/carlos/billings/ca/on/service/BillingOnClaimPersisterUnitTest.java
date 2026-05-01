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
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link BillingOnClaimPersister}, the rename of legacy
 * {@code JdbcBillingClaimImpl} that writes one OHIP claim header + items +
 * audit-trail rows.
 *
 * <p>Coverage scope:</p>
 * <ul>
 *   <li>Happy path: {@code addOneClaimHeaderRecord}, {@code addItemRecord},
 *       and {@code addItemPaymentRecord} all populate entities and call
 *       {@code persist} once per row.</li>
 *   <li>Strict-parse contract: malformed admission_date / billing_date /
 *       service_date abort the {@code @Transactional} unit-of-work via
 *       {@link IllegalArgumentException}, so the surrounding transaction
 *       rolls back rather than persisting a row with silently-dropped fields.</li>
 *   <li>Legacy {@code addItemRecord} return-value oddity: the boolean is
 *       initialised to {@code true} and never flipped, so the caller's
 *       {@code if (!ret)} guard is dead. The test pins the contract.</li>
 * </ul>
 *
 * @since 2026-04-29
 */
@DisplayName("BillingOnClaimPersister")
@Tag("unit")
@Tag("billing")
class BillingOnClaimPersisterUnitTest extends CarlosUnitTestBase {

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

    private BillingOnClaimPersister persister;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        persister = new BillingOnClaimPersister(
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
        void shouldThrow_whenAdmissionDateIsMalformed() {
            // Strict-parse contract: a malformed admission_date now aborts
            // the @Transactional unit-of-work via IllegalArgumentException
            // rather than silently nulling the field on the persisted row.
            BillingClaimHeaderDto dto = headerDto();
            dto.setAdmission_date("not-a-date");

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    persister.addOneClaimHeaderRecord(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("admission_date")
                    .hasMessageContaining("not-a-date");

            verify(cheaderDao, never()).persist(any(BillingONCHeader1.class));
        }

        @Test
        void shouldTreatBlankAdmissionDateAsNull_andPersist() throws Exception {
            // Blank/null admission_date is a legitimate "no value" — the
            // legacy contract tolerated this and the strict-parse helper
            // preserves that. Persistence proceeds with admissionDate=null.
            BillingClaimHeaderDto dto = headerDto();
            dto.setAdmission_date("");

            ArgumentCaptor<BillingONCHeader1> captor = ArgumentCaptor.forClass(BillingONCHeader1.class);
            doAssignId(captor, 1);

            persister.addOneClaimHeaderRecord(dto);

            assertThat(captor.getValue().getAdmissionDate()).isNull();
            verify(cheaderDao, times(1)).persist(any(BillingONCHeader1.class));
        }

        // These four tests pin strict-parse on billing_date and billing_time
        // (admission_date is covered by the tests above). Malformed input
        // must not silently persist a row with the field NULLed out — both
        // sites route through {@link BillingDates#parseOptionalIsoDate} and
        // {@link BillingDates#parseOptionalIsoTime}, which accept blank but
        // throw on garbage.

        @Test
        void shouldThrow_whenBillingDateIsMalformed() {
            BillingClaimHeaderDto dto = headerDto();
            dto.setBilling_date("not-a-date");

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    persister.addOneClaimHeaderRecord(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("billing_date")
                    .hasMessageContaining("not-a-date");

            verify(cheaderDao, never()).persist(any(BillingONCHeader1.class));
        }

        @Test
        void shouldTreatBlankBillingDateAsNull_andPersist() {
            BillingClaimHeaderDto dto = headerDto();
            dto.setBilling_date("");

            ArgumentCaptor<BillingONCHeader1> captor = ArgumentCaptor.forClass(BillingONCHeader1.class);
            doAssignId(captor, 1);

            persister.addOneClaimHeaderRecord(dto);

            assertThat(captor.getValue().getBillingDate()).isNull();
            verify(cheaderDao, times(1)).persist(any(BillingONCHeader1.class));
        }

        @Test
        void shouldThrow_whenBillingTimeIsMalformed() {
            BillingClaimHeaderDto dto = headerDto();
            dto.setBilling_time("99:99:99");

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    persister.addOneClaimHeaderRecord(dto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("billing_time")
                    .hasMessageContaining("99:99:99");

            verify(cheaderDao, never()).persist(any(BillingONCHeader1.class));
        }

        @Test
        void shouldPersistRow_whenBillingTimeBlank() {
            // Blank billing_time keeps the BillingONCHeader1 model's default
            // string ("00:00:00") — that's a separate model-layer concern.
            // The persister's contract is just "don't throw, do persist".
            BillingClaimHeaderDto dto = headerDto();
            dto.setBilling_time("");

            ArgumentCaptor<BillingONCHeader1> captor = ArgumentCaptor.forClass(BillingONCHeader1.class);
            doAssignId(captor, 1);

            persister.addOneClaimHeaderRecord(dto);

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

            persister.addItemRecord(new ArrayList<>(List.of(a, b)), 4242);

            verify(itemDao, times(2)).persist(any(BillingONItem.class));
        }

        @Test
        void shouldThrow_whenServiceDateIsMalformed() {
            // Strict-parse contract: a malformed service_date now aborts the
            // @Transactional unit-of-work rather than silently nulling the
            // field and persisting an audit-incorrect billing_on_item row.
            BillingClaimItemDto bad = itemDto("A001A", "not-a-date");

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    persister.addItemRecord(new ArrayList<>(List.of(bad)), 4242))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("service_date")
                    .hasMessageContaining("not-a-date");

            verify(itemDao, never()).persist(any(BillingONItem.class));
        }

        @Test
        void shouldTreatBlankServiceDateAsNull_andPersist() {
            BillingClaimItemDto okay = itemDto("A001A", "");
            doAssignItemId(99);

            ArgumentCaptor<BillingONItem> captor = ArgumentCaptor.forClass(BillingONItem.class);
            persister.addItemRecord(new ArrayList<>(List.of(okay)), 4242);
            verify(itemDao).persist(captor.capture());
            assertThat(captor.getValue().getServiceDate()).isNull();
        }

        @Test
        void shouldNoOp_whenItemListEmpty() {
            persister.addItemRecord(new ArrayList<BillingClaimItemDto>(), 4242);

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
        void shouldThrow_whenInputsAreMalformed() {
            // The legacy code path silently zeroed malformed money fields and
            // persisted the row anyway — that masked typos and could record
            // a $0 payment on a non-zero billing event. The current contract
            // surfaces a typed validation failure so the Struts exception
            // mapping can render the billing validation page and the
            // surrounding @Transactional unit-of-work rolls back.
            BillingClaimItemDto a = itemDto("A001A", "2026-04-28");
            a.setId("11");
            a.setDiscount("not-a-number");
            a.setPaid("33.70");
            a.setRefund("0.00");

            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    persister.addItemPaymentRecord(new ArrayList<>(List.of(a)), 4242, 7, 1))
                    .isInstanceOf(BillingValidationException.class)
                    .hasMessageContaining("malformed discount amount")
                    .hasMessageContaining("billingNo=4242")
                    .hasRootCauseInstanceOf(NumberFormatException.class);

            verify(billOnItemPaymentDao, never()).persist(any(BillingOnItemPayment.class));
        }

        @Test
        void shouldTreatBlankMoneyFieldsAsZero_andPersistRow() {
            // Blank cells are a legitimate user input — the user simply did
            // not enter a value. The strict-but-blank-tolerant parser must
            // accept these and write zero, distinct from malformed input.
            BillingClaimItemDto a = itemDto("A001A", "2026-04-28");
            a.setId("11");
            a.setDiscount("");
            a.setPaid("33.70");
            a.setRefund("");

            doAssignItemPaymentId(55);

            ArgumentCaptor<BillingOnItemPayment> captor =
                    ArgumentCaptor.forClass(BillingOnItemPayment.class);

            persister.addItemPaymentRecord(new ArrayList<>(List.of(a)), 4242, 7, 1);

            verify(billOnItemPaymentDao).persist(captor.capture());
            BillingOnItemPayment persisted = captor.getValue();
            assertThat(persisted.getDiscount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.getPaid()).isEqualByComparingTo("33.70");
            assertThat(persisted.getRefund()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("add3rdBillExt(Map, int, ArrayList) — 3-arg variant")
    class Add3rdBillExt {

        /**
         * Pins the swallow-and-persist fix: earlier shape persisted 9
         * BillingONExt rows then bailed silently on a date-parse failure,
         * leaving orphan ext rows with no parent BillingONPayment. The fix
         * parses the payment date BEFORE writing any ext rows; on failure
         * it throws BillingValidationException so the @Transactional
         * unit-of-work rolls back. This test stubs UtilDateUtilities.getToday
         * to return a malformed string and asserts no extDao.persist or
         * billingONPaymentDao.persist call slipped through.
         */
        @Test
        void shouldThrow_andPersistNoRows_whenPayDateIsMalformed() {
            BillingClaimHeaderDto header = headerDto();
            ArrayList<Object> claimEnvelope = new ArrayList<>();
            claimEnvelope.add(header);
            claimEnvelope.add(new ArrayList<BillingClaimItemDto>());

            Map<String, String> mVal = new HashMap<>();
            mVal.put("demographic_no", "7");
            mVal.put("total_payment", "100.00");
            mVal.put("total_discount", "0.00");
            mVal.put("payMethod", "1");

            try (MockedStatic<UtilDateUtilities> dates = mockStatic(UtilDateUtilities.class)) {
                dates.when(() -> UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss"))
                        .thenReturn("not-a-timestamp");

                assertThatThrownBy(() -> persister.add3rdBillExt(mVal, 4242, claimEnvelope))
                        .isInstanceOf(BillingValidationException.class)
                        .hasMessageContaining("malformed payDate")
                        .hasMessageContaining("billingNo=4242");
            }

            verify(extDao, never()).persist(any(BillingONExt.class));
            verify(billingONPaymentDao, never()).persist(any(BillingONPayment.class));
            verify(billOnItemPaymentDao, never()).persist(any(BillingOnItemPayment.class));
            verify(billTransDao, never()).persist(any(BillingOnTransaction.class));
        }

        /**
         * Happy path: with a valid date and total_payment, all 9 BillingONExt
         * rows AND the BillingONPayment row are persisted in the same call.
         * Pins the post-fix completeness contract.
         */
        @Test
        void shouldPersistAllNineExtRows_andOnePayment_whenInputsAreWellFormed() {
            BillingClaimHeaderDto header = headerDto();
            ArrayList<Object> claimEnvelope = new ArrayList<>();
            claimEnvelope.add(header);
            claimEnvelope.add(new ArrayList<BillingClaimItemDto>());

            Map<String, String> mVal = new HashMap<>();
            mVal.put("demographic_no", "7");
            mVal.put("total_payment", "100.00");
            mVal.put("total_discount", "0.00");
            mVal.put("payMethod", "1");

            doAssignPaymentId(99);

            persister.add3rdBillExt(mVal, 4242, claimEnvelope);

            ArgumentCaptor<BillingONExt> extCaptor = ArgumentCaptor.forClass(BillingONExt.class);
            verify(extDao, times(9)).persist(extCaptor.capture());
            assertThat(extCaptor.getAllValues())
                    .extracting(BillingONExt::getPaymentId)
                    .containsOnly(99);
            verify(billingONPaymentDao, times(1)).persist(any(BillingONPayment.class));
        }

        /**
         * When total_payment is null, the method writes the 9 ext rows but
         * skips the payment record entirely (legacy contract preserved).
         */
        @Test
        void shouldSkipPaymentRow_whenTotalPaymentIsNull() {
            BillingClaimHeaderDto header = headerDto();
            ArrayList<Object> claimEnvelope = new ArrayList<>();
            claimEnvelope.add(header);
            claimEnvelope.add(new ArrayList<BillingClaimItemDto>());

            Map<String, String> mVal = new HashMap<>();
            mVal.put("demographic_no", "7");
            mVal.put("total_discount", "0.00");
            mVal.put("payMethod", "1");
            // total_payment intentionally absent

            persister.add3rdBillExt(mVal, 4242, claimEnvelope);

            verify(extDao, times(9)).persist(any(BillingONExt.class));
            verify(billingONPaymentDao, never()).persist(any(BillingONPayment.class));
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

    private void doAssignPaymentId(int id) {
        org.mockito.Mockito.doAnswer(inv -> {
            BillingONPayment entity = inv.getArgument(0);
            entity.setId(id);
            return null;
        }).when(billingONPaymentDao).persist(any(BillingONPayment.class));
    }
}
