/*
 * Copyright (c) 2026 CARLOS Contributors.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.BillRecipientsDao;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingHistoryDAO;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.entities.Billingmaster;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * {@link MSPReconcile#updateStat(String, String)}: the status a remittance record gives a BC claim.
 *
 * <p>An uploaded MSP remittance calls it once per claim record: an S01 paid line with SETTLED, an S03
 * refusal with REFUSED (genTA.jsp, GenTa2Action). The status chain had no REFUSED branch, so a refused
 * claim was saved with an empty status (issue #4933).</p>
 *
 * <p>MSPReconcile builds its own {@code new BillingHistoryDAO()}, which archives through plain JDBC, so
 * that construction is mocked: the test needs no database, and the archive call is asserted.</p>
 */
@Tag("unit")
@Tag("billing")
@DisplayName("MSPReconcile.updateStat")
class MSPReconcileUpdateStatUnitTest extends CarlosUnitTestBase {

    private static final int CLAIM = 55;

    private BillingmasterDAO billingmasterDao;
    private MockedConstruction<BillingHistoryDAO> histories;

    @BeforeEach
    void registerDaos() {
        billingmasterDao = createAndRegisterMock(BillingmasterDAO.class);
        createAndRegisterMock(BillRecipientsDao.class);
        createAndRegisterMock(BillingDao.class);
        histories = mockConstruction(BillingHistoryDAO.class);
    }

    @AfterEach
    void closeHistories() {
        histories.close();
    }

    /** The archive MSPReconcile made for itself. */
    private BillingHistoryDAO history() {
        assertThat(histories.constructed()).hasSize(1);
        return histories.constructed().get(0);
    }

    private Billingmaster submittedClaim() {
        Billingmaster claim = new Billingmaster();
        claim.setBillingstatus(MSPReconcile.SUBMITTED);
        when(billingmasterDao.getBillingmaster(CLAIM)).thenReturn(claim);
        return claim;
    }

    @Test
    @DisplayName("should mark a submitted claim refused (F) when its remittance refuses it")
    void shouldMarkClaimRefused_whenRemittanceRefusesIt() {
        Billingmaster claim = submittedClaim();

        boolean updated = new MSPReconcile().updateStat(MSPReconcile.REFUSED, String.valueOf(CLAIM));

        assertThat(updated).isTrue();
        assertThat(claim.getBillingstatus()).isEqualTo(MSPReconcile.REFUSED);
        verify(billingmasterDao).update(claim);
        verify(history()).createBillingHistoryArchive(String.valueOf(CLAIM));
    }

    @Test
    @DisplayName("should mark a submitted claim settled (S) when its remittance pays it")
    void shouldMarkClaimSettled_whenRemittancePaysIt() {
        Billingmaster claim = submittedClaim();

        boolean updated = new MSPReconcile().updateStat(MSPReconcile.SETTLED, String.valueOf(CLAIM));

        assertThat(updated).isTrue();
        assertThat(claim.getBillingstatus()).isEqualTo(MSPReconcile.SETTLED);
        verify(billingmasterDao).update(claim);
        verify(history()).createBillingHistoryArchive(String.valueOf(CLAIM));
    }

    @Test
    @DisplayName("should leave a settled claim unchanged when a later record refuses it")
    void shouldLeaveSettledClaimUnchanged_whenRefusedLater() {
        Billingmaster claim = submittedClaim();
        claim.setBillingstatus(MSPReconcile.SETTLED);

        boolean updated = new MSPReconcile().updateStat(MSPReconcile.REFUSED, String.valueOf(CLAIM));

        assertThat(updated).isFalse();
        assertThat(claim.getBillingstatus()).isEqualTo(MSPReconcile.SETTLED);
        verify(billingmasterDao, never()).update(claim);
        verify(history(), never()).createBillingHistoryArchive(anyString());
    }
}
