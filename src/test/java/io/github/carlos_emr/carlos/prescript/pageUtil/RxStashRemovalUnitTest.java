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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Regression coverage for issue #3870: closing several staged prescription cards could save a
 * drug the prescriber had dismissed, because the closed stash indexes were removed in
 * ascending order and every removal shifted the indexes after it. Also pins the
 * {@code GCN_SEQNO} value-equality fix in {@link RxSessionBean#addStashItem}.
 *
 * @since 2026-09-24
 */
@DisplayName("Rx stash removal and de-duplication")
@Tag("unit")
@Tag("prescript")
class RxStashRemovalUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerStaticCollaborators() {
        // RxWriteScript2Action resolves these two DAOs in static initialisers.
        registerMock(UserPropertyDAO.class, mock(UserPropertyDAO.class));
        registerMock(PartialDateDao.class, mock(PartialDateDao.class));
    }

    private static RxPrescriptionData.Prescription staged(long randomId, String brandName, String gcnSeqNo) {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, "999998", 1);
        rx.setRandomId(randomId);
        rx.setBrandName(brandName);
        rx.setGCN_SEQNO(gcnSeqNo);
        return rx;
    }

    private static RxSessionBean beanWithStash(String... brandNames) {
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(1);
        for (int i = 0; i < brandNames.length; i++) {
            bean.getStashList().add(staged(100 + i, brandNames[i], String.valueOf(i)));
        }
        bean.setStashIndex(brandNames.length - 1);
        return bean;
    }

    private static List<String> stashBrandNames(RxSessionBean bean) {
        List<String> names = new ArrayList<>();
        for (RxPrescriptionData.Prescription rx : bean.getStashList()) {
            names.add(rx.getBrandName());
        }
        return names;
    }

    @Test
    @DisplayName("should keep exactly the submitted cards when two non-adjacent cards were closed")
    void shouldKeepSubmittedCards_whenTwoNonAdjacentCardsClosed() {
        RxSessionBean bean = beanWithStash("A", "B", "C", "D");

        // Prescriber closed A (0) and C (2), kept B (1) and D (3).
        RxWriteScript2Action.removeClosedStashItems(bean, List.of(1, 3));

        assertThat(stashBrandNames(bean)).containsExactly("B", "D");
    }

    @Test
    @DisplayName("should keep the last card when every earlier card was closed")
    void shouldKeepLastCard_whenEveryEarlierCardClosed() {
        RxSessionBean bean = beanWithStash("A", "B", "C");

        RxWriteScript2Action.removeClosedStashItems(bean, List.of(2));

        assertThat(stashBrandNames(bean)).containsExactly("C");
        assertThat(bean.getStashIndex()).isZero();
    }

    @Test
    @DisplayName("should empty the stash when no card was submitted")
    void shouldEmptyStash_whenNoCardSubmitted() {
        RxSessionBean bean = beanWithStash("A", "B", "C");

        RxWriteScript2Action.removeClosedStashItems(bean, List.of());

        assertThat(bean.getStashSize()).isZero();
        assertThat(bean.getStashIndex()).isEqualTo(-1);
    }

    @Test
    @DisplayName("should leave the stash untouched when every card was submitted")
    void shouldLeaveStashUntouched_whenEveryCardSubmitted() {
        RxSessionBean bean = beanWithStash("A", "B", "C");

        RxWriteScript2Action.removeClosedStashItems(bean, List.of(2, 0, 1));

        assertThat(stashBrandNames(bean)).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("should not mutate the caller's kept-index list")
    void shouldNotMutateKeptIndexes_forCallerOwnedList() {
        RxSessionBean bean = beanWithStash("A", "B", "C");
        List<Integer> kept = new ArrayList<>(List.of(1));

        RxWriteScript2Action.removeClosedStashItems(bean, kept);

        assertThat(kept).containsExactly(1);
        assertThat(stashBrandNames(bean)).containsExactly("B");
    }

    @Test
    @DisplayName("should de-duplicate a restaged drug whose GCN_SEQNO is an equal but distinct String")
    void shouldReturnExistingIndex_whenGcnSeqNoIsEqualDistinctString() {
        RxSessionBean bean = new RxSessionBean();
        bean.getStashList().add(staged(1, "DRUG X", new String("12345")));

        // A distinct String instance with the same value: == said "different", which let a
        // second, hidden copy of the same drug into the stash.
        int index = bean.addStashItem(null, staged(2, "DRUG X", new String("12345")));

        assertThat(index).isZero();
        assertThat(bean.getStashSize()).isEqualTo(1);
    }
    @Test
    @DisplayName("should stage copies of two different saved drugs of one product as two cards")
    void shouldStageBothCards_whenSameProductCopiesDifferentSources() {
        // Two active rows of the same product (same brand and GCN, different sig) re-prescribed
        // together used to collapse into one card, leaving the second source ticked for ReRx with
        // no replacement to save (#3908).
        registerMock(io.github.carlos_emr.carlos.commn.dao.DrugDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.DrugDao.class));
        registerMock(io.github.carlos_emr.carlos.managers.DemographicManager.class,
                mock(io.github.carlos_emr.carlos.managers.DemographicManager.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.AllergyDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.AllergyDao.class));
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(1);
        RxPrescriptionData.Prescription first = staged(5, "DRUG A", "111");
        first.setDrugReferenceId(10);
        RxPrescriptionData.Prescription second = staged(6, "DRUG A", "111");
        second.setDrugReferenceId(11);

        assertThat(bean.addStashItem(null, first)).isZero();
        assertThat(bean.addStashItem(null, second)).isEqualTo(1);

        assertThat(bean.getStashSize()).isEqualTo(2);
        // A second copy of the SAME source is still the same card.
        RxPrescriptionData.Prescription again = staged(7, "DRUG A", "111");
        again.setDrugReferenceId(10);
        assertThat(bean.addStashItem(null, again)).isZero();
        assertThat(bean.getStashSize()).isEqualTo(2);
    }

    @Test
    @DisplayName("should re-key a card whose key another staged card already carries")
    void shouldRekeyLaterCard_whenTwoCardsCarryOneKey() {
        // Two windows of one patient can both draw the same key before either card is added
        // (#3908); insertion runs under the bean's monitor and replaces a taken key.
        registerMock(io.github.carlos_emr.carlos.commn.dao.DrugDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.DrugDao.class));
        // addStashItem also preloads allergy warnings through RxPatientData, whose static
        // DemographicManager lookup is a class-init Error the bean does not catch.
        registerMock(io.github.carlos_emr.carlos.managers.DemographicManager.class,
                mock(io.github.carlos_emr.carlos.managers.DemographicManager.class));
        registerMock(io.github.carlos_emr.carlos.commn.dao.AllergyDao.class,
                mock(io.github.carlos_emr.carlos.commn.dao.AllergyDao.class));
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(1);
        RxPrescriptionData.Prescription first = staged(5, "DRUG A", "111");
        RxPrescriptionData.Prescription second = staged(5, "DRUG B", "222");

        bean.addStashItem(null, first);
        bean.addStashItem(null, second);

        assertThat(bean.getStashSize()).isEqualTo(2);
        assertThat(first.getRandomId()).isEqualTo(5L);
        assertThat(second.getRandomId()).isNotEqualTo(5L);
        assertThat(bean.getStashItem2((int) second.getRandomId())).isSameAs(second);
    }

    @Test
    @DisplayName("should allocate keys under the bean's monitor so an insertion in progress blocks a draw")
    void shouldBlockKeyDraw_whileBeanMonitorIsHeld() throws Exception {
        // The allocator and RxSessionBean#addStashItem share the bean's monitor: a draw cannot
        // interleave with another window's insertion (#3908).
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(1);
        java.util.concurrent.CountDownLatch drawn = new java.util.concurrent.CountDownLatch(1);
        Thread other = new Thread(() -> {
            RxStashIds.acceptOrNext(bean, "7", 40);
            drawn.countDown();
        });
        synchronized (bean) {
            other.start();
            assertThat(drawn.await(300, java.util.concurrent.TimeUnit.MILLISECONDS))
                    .as("a draw must wait for the bean's monitor").isFalse();
        }
        assertThat(drawn.await(5, java.util.concurrent.TimeUnit.SECONDS))
                .as("the draw completes once the monitor is released").isTrue();
        other.join(5000);
    }
}
