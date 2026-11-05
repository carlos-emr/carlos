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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stash keys come from a SecureRandom but keep the ranges the Rx pages parse as {@code int}.
 *
 * @since 2026-09-24
 */
@DisplayName("RxStashIds")
@Tag("unit")
@Tag("prescript")
class RxStashIdsUnitTest {

    @ParameterizedTest(name = "bound {0}")
    @ValueSource(ints = {0, 1, 10_001, RxStashIds.DEFAULT_BOUND})
    @DisplayName("should stay between 0 and the bound inclusive")
    void shouldStayWithinBound_forEveryDraw(int bound) {
        for (int i = 0; i < 20_000; i++) {
            assertThat(RxStashIds.next(bound)).isBetween(0L, (long) bound);
        }
    }

    @Test
    @DisplayName("should reach both ends of a small range")
    void shouldCoverWholeRange_forSmallBound() {
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            seen.add(RxStashIds.next(3));
        }
        assertThat(seen).containsExactlyInAnyOrder(0L, 1L, 2L, 3L);
    }

    @Test
    @DisplayName("should reject a negative bound")
    void shouldRejectBound_whenNegative() {
        assertThatThrownBy(() -> RxStashIds.next(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static RxSessionBean beanWithKeys(long... keys) {
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(1);
        for (long key : keys) {
            io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription rx =
                    new io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData.Prescription(0, "999998", 1);
            rx.setRandomId(key);
            bean.getStashList().add(rx);
        }
        return bean;
    }

    @Test
    @DisplayName("should never return a key a staged card already uses")
    void shouldAvoidKeysInUse_whenRangeIsNearlyFull() {
        // Bound 3 with keys 0, 1 and 3 in use leaves only 2.
        RxSessionBean bean = beanWithKeys(0, 1, 3);
        for (int i = 0; i < 200; i++) {
            assertThat(RxStashIds.nextUnique(bean, 3)).isEqualTo(2L);
        }
    }

    @Test
    @DisplayName("should go above every key in use when the range is exhausted")
    void shouldExceedHighestKey_whenRangeIsFull() {
        assertThat(RxStashIds.nextUnique(beanWithKeys(0, 1), 1)).isEqualTo(2L);
    }

    @Test
    @DisplayName("should keep a well-formed unused client key")
    void shouldAcceptClientKey_whenUnused() {
        assertThat(RxStashIds.acceptOrNext(beanWithKeys(5), "42", 10)).isEqualTo(42L);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"5", "", "-1", "abc", "12345678901"})
    @DisplayName("should replace a client key that is in use or malformed")
    void shouldReplaceClientKey_whenInUseOrMalformed(String clientKey) {
        RxSessionBean bean = beanWithKeys(5);
        long key = RxStashIds.acceptOrNext(bean, clientKey, 10);
        assertThat(key).isBetween(0L, 10L).isNotEqualTo(5L);
    }

    @Test
    @DisplayName("should replace a missing client key")
    void shouldReplaceClientKey_whenMissing() {
        assertThat(RxStashIds.acceptOrNext(beanWithKeys(5), null, 10)).isBetween(0L, 10L).isNotEqualTo(5L);
    }
}
