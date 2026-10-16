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

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONProcDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONProc;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService.UnbillOutcome;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Issue #4538 against the real persistence layer: {@code unbillClaim} decides from the status
 * stored on {@code billing_on_cheader1}, and a refusal leaves the header, its items and the
 * {@code billing_on_proc} audit trail exactly as they were.
 *
 * <p>The service is built over the real header, item and audit DAOs; the collaborators that the
 * unbill path never touches are mocks.</p>
 */
@DisplayName("BillingCorrectionRecordService.unbillClaim (real persistence)")
@Tag("integration")
@Tag("billing")
@Transactional
class BillingCorrectionRecordServiceUnbillIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER_NO = "999998";

    @Autowired private BillingONCHeader1Dao headerDao;
    @Autowired private BillingONItemDao itemDao;
    @Autowired private BillingONProcDao procDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private BillingCorrectionRecordService service;

    @BeforeEach
    void setUp() {
        BillingOnCorrectionPersister persister = new BillingOnCorrectionPersister(
                headerDao, itemDao, mock(BillingONEAReportDao.class), mock(RaDetailDao.class),
                mock(BillingOnItemPaymentDao.class), mock(BillingONExtDao.class),
                mock(BillingOnTransactionDao.class), new BillingOnAuditLogService(procDao));
        service = new BillingCorrectionRecordService(
                persister, headerDao, itemDao, mock(BillingONExtDao.class),
                mock(BillingOnLookupService.class), mock(BillingThirdPartyService.class),
                mock(ServiceCodeLoader.class), mock(BillingOnClaimPersister.class),
                mock(BillingOnClaimLoader.class), mock(BillingOnTransactionDao.class),
                mock(BillingOnItemPaymentDao.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"B", "S"})
    void shouldLeaveClaimItemsAndAuditTrailUntouched_whenStoredStatusIsBilledOrSettled(String storedStatus) {
        int id = persistClaim(storedStatus, 2);

        UnbillOutcome outcome = service.unbillClaim(id, PROVIDER_NO);

        assertThat(outcome).isEqualTo(UnbillOutcome.REFUSED_BILLED_OR_SETTLED);
        assertThat(storedHeaderStatus(id)).isEqualTo(storedStatus);
        assertThat(storedItemStatuses(id)).containsExactly(storedStatus, storedStatus);
        assertThat(auditActions(id)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"O", "W", "P", "N", "I"})
    void shouldMarkClaimAndItemsDeletedAndAuditIt_whenStoredStatusIsNotBilledOrSettled(String storedStatus) {
        int id = persistClaim(storedStatus, 2);

        UnbillOutcome outcome = service.unbillClaim(id, PROVIDER_NO);

        assertThat(outcome).isEqualTo(UnbillOutcome.DELETED);
        assertThat(storedHeaderStatus(id)).isEqualTo("D");
        assertThat(storedItemStatuses(id)).containsExactly("D", "D");
        assertThat(auditActions(id)).containsExactlyInAnyOrder("updateBillingStatus", "updateBillingStatus-items");
    }

    @Test
    void shouldReportNotFoundAndWriteNothing_whenNoClaimHasTheId() {
        UnbillOutcome outcome = service.unbillClaim(Integer.MAX_VALUE, PROVIDER_NO);

        assertThat(outcome).isEqualTo(UnbillOutcome.NOT_FOUND);
        assertThat(auditActions(Integer.MAX_VALUE)).isEmpty();
    }

    /** Persists a claim header with {@code itemCount} items, all carrying {@code status}. */
    private int persistClaim(String status, int itemCount) {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setHeaderId(0);
        header.setDemographicNo(700052);
        header.setProviderNo(PROVIDER_NO);
        header.setStatus(status);
        header.setBillingDate(new Date());
        header.setBillingTime(new Date());
        header.setPayProgram("HCP");
        header.setVisitType("00");
        header.setTotal(new BigDecimal("50.00"));
        header.setPaid(new BigDecimal("0.00"));
        header.setApptProviderNo(PROVIDER_NO);
        header.setCreator(PROVIDER_NO);
        header.setAppointmentNo(0);
        header.setFaciltyNum("0001");
        for (int i = 0; i < itemCount; i++) {
            BillingONItem item = new BillingONItem();
            item.setServiceCode("A007A");
            item.setServiceCount("1");
            item.setFee("25.00");
            item.setDx("250");
            item.setServiceDate(java.sql.Date.valueOf("2026-03-04"));
            item.setStatus(status);
            header.addBillingItem(item);
        }
        entityManager.persist(header);
        entityManager.flush();
        entityManager.clear();
        return header.getId();
    }

    private String storedHeaderStatus(int id) {
        entityManager.flush();
        entityManager.clear();
        return headerDao.find(id).getStatus();
    }

    private List<String> storedItemStatuses(int id) {
        entityManager.flush();
        entityManager.clear();
        return itemDao.getBillingItemByCh1Id(id).stream().map(BillingONItem::getStatus).toList();
    }

    private List<String> auditActions(int id) {
        entityManager.flush();
        return entityManager
                .createQuery("SELECT p FROM BillingONProc p WHERE p.object = :object", BillingONProc.class)
                .setParameter("object", String.valueOf(id))
                .getResultList().stream().map(BillingONProc::getAction).toList();
    }
}
