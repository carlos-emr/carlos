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
package io.github.carlos_emr.carlos.billings.ca.on.support;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the immutability contract of {@link BillingOnConstants} lookup tables.
 *
 * <p>{@code propMonthCode}, {@code propBillingCenter}, and {@code propBillingType}
 * are {@code public static final}. The fields must reject direct mutation and
 * mutation through collection views after static initialization.</p>
 */
@DisplayName("BillingOnConstants immutability")
@Tag("unit")
@Tag("billing")
class BillingOnConstantsUnitTest {

    @Test
    void shouldExposeMonthCode_whenReadOnly() {
        assertThat(BillingOnConstants.propMonthCode.getProperty("1")).isEqualTo("A");
        assertThat(BillingOnConstants.propMonthCode.getProperty("12")).isEqualTo("L");
    }

    @Test
    void shouldExposeBillingCenter_whenReadOnly() {
        assertThat(BillingOnConstants.propBillingCenter.getProperty("N")).isEqualTo("Toronto");
    }

    @Test
    void shouldExposeBillingType_whenReadOnly() {
        assertThat(BillingOnConstants.propBillingType.getProperty("O")).isEqualTo("Bill OHIP");
    }

    @Test
    void shouldRejectSetProperty_onMonthCode() {
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.setProperty("1", "Z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectSetProperty_onBillingCenter() {
        assertThatThrownBy(() -> BillingOnConstants.propBillingCenter.setProperty("N", "Vancouver"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectSetProperty_onBillingType() {
        assertThatThrownBy(() -> BillingOnConstants.propBillingType.setProperty("O", "X"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectClear_forInvalidInput() {
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectRemove_forInvalidInput() {
        assertThatThrownBy(() -> BillingOnConstants.propBillingCenter.remove("N"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectMapMutators_forInvalidInput() {
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.put("13", "M"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.putIfAbsent("13", "M"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.replace("1", "Z"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.replace("1", "A", "Z"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.remove("1", "A"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.compute("1", (key, value) -> "Z"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.computeIfAbsent("13", key -> "M"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.computeIfPresent("1", (key, value) -> "Z"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.merge("1", "Z", (oldValue, newValue) -> "Z"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.replaceAll((key, value) -> "Z"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectCollectionViewMutation_forInvalidInput() {
        Map.Entry<Object, Object> entry =
                BillingOnConstants.propMonthCode.entrySet().iterator().next();

        assertThatThrownBy(() -> entry.setValue("Z"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.entrySet().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.keySet().remove("1"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> BillingOnConstants.propMonthCode.values().remove("A"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
